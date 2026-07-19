package com.wonderfood.app.sync

import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec
import com.wonderfood.core.model.WonderFoodSnapshotRow
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

interface GoogleSheetsSnapshotGateway {
    fun ensureWonderFoodSchema(accessToken: String, spreadsheetId: String): GoogleSheetsBootstrapResult
    fun exportSnapshotRows(
        accessToken: String,
        spreadsheetId: String,
        snapshot: WonderFoodSnapshot,
        updatedAt: String,
    ): GoogleSheetsExportResult
    fun readSnapshotRows(accessToken: String, spreadsheetId: String): List<WonderFoodSnapshotRow>
    fun readWorkspaceRows(accessToken: String, spreadsheetId: String): List<GoogleSheetsWorkspaceRow>
}

class GoogleSheetsGateway : GoogleSheetsSnapshotGateway {
    override fun exportSnapshotRows(
        accessToken: String,
        spreadsheetId: String,
        snapshot: WonderFoodSnapshot,
        updatedAt: String,
    ): GoogleSheetsExportResult {
        ensureWonderFoodSchema(accessToken, spreadsheetId)
        val rows = WonderFoodSnapshotCodec.rows(snapshot, updatedAt)
        writeRows(accessToken, spreadsheetId, rows)
        writeWorkspaceRows(accessToken, spreadsheetId, snapshot, updatedAt)
        return GoogleSheetsExportResult(
            spreadsheetId = spreadsheetId,
            rowCount = rows.size,
            tabs = (rows.map { it.tab }.distinct() + HUMAN_WORKSPACE_TABS.map { it.title }).distinct(),
        )
    }

    override fun readSnapshotRows(accessToken: String, spreadsheetId: String): List<WonderFoodSnapshotRow> {
        val ranges = WONDERFOOD_TABS.joinToString("&") { tab ->
            "ranges=${url("'${tab.title.escapeSheetName()}'!A2:E")}"
        }
        val response = request(
            method = "GET",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}/values:batchGet?$ranges&majorDimension=ROWS",
            accessToken = accessToken,
        )
        return JSONObject(response.asText())
            .optJSONArray("valueRanges")
            .orEmptyObjects()
            .flatMap { range ->
                val tab = range.optString("range").substringBefore("!").trim('\'')
                val logicalTab = WONDERFOOD_TABS.firstOrNull { it.title == tab }?.logicalTitle ?: tab
                range.optJSONArray("values").orEmptyArrays().mapNotNull { row -> row.toSnapshotRow(logicalTab) }
            }
    }

    override fun readWorkspaceRows(accessToken: String, spreadsheetId: String): List<GoogleSheetsWorkspaceRow> {
        val ranges = HUMAN_WORKSPACE_TABS.joinToString("&") { tab ->
            "ranges=${url("'${tab.title.escapeSheetName()}'!A1:${columnName(tab.headers.size)}")}"
        }
        val response = request(
            method = "GET",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}/values:batchGet?$ranges&majorDimension=ROWS",
            accessToken = accessToken,
        )
        return JSONObject(response.asText())
            .optJSONArray("valueRanges")
            .orEmptyObjects()
            .flatMap { range ->
                val tab = range.optString("range").substringBefore("!").trim('\'')
                val arrays = range.optJSONArray("values").orEmptyArrays()
                val headers = arrays.firstOrNull()?.toStringList().orEmpty()
                arrays.drop(1).mapNotNull { row -> row.toWorkspaceRow(tab, headers) }
            }
    }

    override fun ensureWonderFoodSchema(accessToken: String, spreadsheetId: String): GoogleSheetsBootstrapResult {
        require(accessToken.isNotBlank()) { "Google Sheets access token must not be blank." }
        require(spreadsheetId.isNotBlank()) { "Google spreadsheet ID must not be blank." }
        val before = spreadsheet(accessToken, spreadsheetId)
        val existingTitles = before.sheets.map { it.title }.toSet()
        val allTabs = WONDERFOOD_TABS + HUMAN_WORKSPACE_TABS
        val missingTabs = allTabs.filterNot { it.title in existingTitles }
        if (missingTabs.isNotEmpty()) {
            createTabs(accessToken, spreadsheetId, missingTabs)
        }
        val afterCreate = if (missingTabs.isEmpty()) before else spreadsheet(accessToken, spreadsheetId)
        val currentHeadersByTitle = readHeaders(accessToken, spreadsheetId, allTabs)
        val tabsNeedingHeaders = allTabs.filter { tab ->
            val grid = afterCreate.sheets.firstOrNull { it.title == tab.title } ?: return@filter true
            val currentHeaders = currentHeadersByTitle[tab.title].orEmpty()
            grid.rowCount == 0 || grid.columnCount < tab.headers.size || currentHeaders != tab.headers
        }
        if (tabsNeedingHeaders.isNotEmpty()) {
            writeHeaders(accessToken, spreadsheetId, tabsNeedingHeaders)
        }
        val after = if (missingTabs.isEmpty() && tabsNeedingHeaders.isEmpty()) before else spreadsheet(accessToken, spreadsheetId)
        applyWorkspacePresentation(
            accessToken = accessToken,
            spreadsheetId = spreadsheetId,
            spreadsheet = after,
            provisionSystemForTitles = missingTabs.map { it.title }.toSet(),
            createTablesForTitles = missingTabs
                .filter { created -> HUMAN_WORKSPACE_TABS.any { it.title == created.title } }
                .map { it.title }
                .toSet(),
        )
        return GoogleSheetsBootstrapResult(
            spreadsheetId = spreadsheetId,
            title = after.title,
            createdTabs = missingTabs.map { it.title },
            initializedHeaders = tabsNeedingHeaders.map { it.title },
            totalTabs = after.sheets.size,
        )
    }

    fun spreadsheet(accessToken: String, spreadsheetId: String): GoogleSheetsSpreadsheet {
        val response = request(
            method = "GET",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}?fields=${url("spreadsheetId,properties(title),sheets(properties(sheetId,title,gridProperties(rowCount,columnCount)))")}",
            accessToken = accessToken,
        )
        return JSONObject(response.asText()).toSpreadsheet()
    }

    internal fun workspaceRows(snapshot: WonderFoodSnapshot, updatedAt: String): Map<String, List<List<String>>> {
        val schemaRows = WonderFoodWorkspaceSchema.rows(snapshot, updatedAt)
        return WonderFoodWorkspaceSchema.tables.associate { table ->
            table.title to schemaRows.getValue(table.title).map { row ->
                table.fields.map { field -> row.values[field.name].toSheetCell() }
            }
        }
    }

    internal fun workspacePresentationRequests(
        spreadsheet: GoogleSheetsSpreadsheet,
        provisionSystemForTitles: Set<String>,
        createTablesForTitles: Set<String>,
    ): JSONArray {
        val schemaByTitle = (WONDERFOOD_TABS + HUMAN_WORKSPACE_TABS).associateBy { it.title }
        val requests = JSONArray()
        spreadsheet.sheets.forEach { sheet ->
            val schema = schemaByTitle[sheet.title] ?: return@forEach
            val isRawSyncTab = WONDERFOOD_TABS.any { it.title == sheet.title }
            val isEverydayTab = WonderFoodWorkspaceSchema.everydayTables.any { it.title == sheet.title }
            val tabColor = when {
                isRawSyncTab -> JSONObject().put("red", 0.86).put("green", 0.86).put("blue", 0.86)
                isEverydayTab -> JSONObject().put("red", 0.82).put("green", 0.91).put("blue", 0.78)
                else -> JSONObject().put("red", 0.78).put("green", 0.86).put("blue", 0.94)
            }
            requests.put(
                JSONObject().put(
                    "updateSheetProperties",
                    JSONObject()
                        .put(
                            "properties",
                            JSONObject()
                                .put("sheetId", sheet.sheetId)
                                .put("hidden", isRawSyncTab)
                                .put("tabColor", tabColor)
                                .put(
                                    "gridProperties",
                                    JSONObject()
                                        .put("rowCount", sheet.rowCount.coerceAtLeast(100))
                                        .put("frozenRowCount", 1),
                                ),
                        )
                        .put("fields", "hidden,tabColor,gridProperties.rowCount,gridProperties.frozenRowCount"),
                ),
            )
            requests.put(
                JSONObject().put(
                    "repeatCell",
                    JSONObject()
                        .put(
                            "range",
                            JSONObject()
                                .put("sheetId", sheet.sheetId)
                                .put("startRowIndex", 0)
                                .put("endRowIndex", 1)
                                .put("startColumnIndex", 0)
                                .put("endColumnIndex", schema.headers.size),
                        )
                        .put(
                            "cell",
                            JSONObject().put(
                                "userEnteredFormat",
                                JSONObject()
                                    .put("backgroundColor", JSONObject().put("red", 0.95).put("green", 0.96).put("blue", 0.92))
                                    .put("horizontalAlignment", "LEFT")
                                    .put("textFormat", JSONObject().put("bold", true)),
                            ),
                        )
                        .put("fields", "userEnteredFormat(backgroundColor,horizontalAlignment,textFormat)"),
                ),
            )
            requests.put(
                JSONObject().put(
                    "setBasicFilter",
                    JSONObject().put(
                        "filter",
                        JSONObject().put(
                            "range",
                            JSONObject()
                                .put("sheetId", sheet.sheetId)
                                .put("startRowIndex", 0)
                                .put("startColumnIndex", 0)
                                .put("endColumnIndex", schema.headers.size),
                        ),
                    ),
                ),
            )
            requests.put(
                JSONObject().put(
                    "autoResizeDimensions",
                    JSONObject().put(
                        "dimensions",
                        JSONObject()
                            .put("sheetId", sheet.sheetId)
                            .put("dimension", "COLUMNS")
                            .put("startIndex", 0)
                            .put("endIndex", schema.headers.size),
                    ),
                ),
            )
            if (sheet.title in createTablesForTitles) {
                requests.put(
                    JSONObject().put(
                        "addTable",
                        JSONObject().put(
                            "table",
                            JSONObject()
                                .put("name", sheet.title.tableName())
                                .put(
                                    "range",
                                    JSONObject()
                                        .put("sheetId", sheet.sheetId)
                                        .put("startRowIndex", 0)
                                        .put("endRowIndex", 1)
                                        .put("startColumnIndex", 0)
                                        .put("endColumnIndex", schema.headers.size),
                                ),
                        ),
                    ),
                )
            }
            if (sheet.title in provisionSystemForTitles) {
                requests.put(systemColumnsProtectionRequest(sheet, schema))
                requests.put(sheetDeveloperMetadataRequest(sheet, schema, isRawSyncTab))
            }
        }
        return requests
    }

    private fun systemColumnsProtectionRequest(sheet: GoogleSheetsSheet, schema: GoogleSheetsTabSchema): JSONObject {
        val protectedColumnNames = if (WONDERFOOD_TABS.any { it.title == sheet.title }) {
            listOf("id", "version", "updated_at", "archived_at", "payload_json")
        } else {
            listOf("identifier")
        }
        val protectedIndexes = schema.headers.mapIndexedNotNull { index, header ->
            index.takeIf { header in protectedColumnNames }
        }
        val startColumn = protectedIndexes.minOrNull() ?: 0
        val endColumn = (protectedIndexes.maxOrNull() ?: 0) + 1
        return JSONObject().put(
            "addProtectedRange",
            JSONObject().put(
                "protectedRange",
                JSONObject()
                    .put(
                        "range",
                        JSONObject()
                            .put("sheetId", sheet.sheetId)
                            .put("startRowIndex", 0)
                            .put("startColumnIndex", startColumn)
                            .put("endColumnIndex", endColumn),
                    )
                    .put("description", "WonderFood system columns. Edit only if you know what you are changing.")
                    .put("warningOnly", true),
            ),
        )
    }

    private fun sheetDeveloperMetadataRequest(
        sheet: GoogleSheetsSheet,
        schema: GoogleSheetsTabSchema,
        isRawSyncTab: Boolean,
    ): JSONObject =
        JSONObject().put(
            "createDeveloperMetadata",
            JSONObject().put(
                "developerMetadata",
                JSONObject()
                    .put("metadataKey", "wonderfood.table")
                    .put(
                        "metadataValue",
                        JSONObject()
                            .put("title", schema.title)
                            .put("kind", if (isRawSyncTab) "raw_sync" else "workspace")
                            .put("schemaVersion", WonderFoodWorkspaceSchema.WORKSPACE_SCHEMA_VERSION)
                            .put("headers", JSONArray(schema.headers))
                            .toString(),
                    )
                    .put(
                        "location",
                        JSONObject().put(
                            "sheetId",
                            sheet.sheetId,
                        ),
                    )
                    .put("visibility", "DOCUMENT"),
            ),
        )

    private fun createTabs(accessToken: String, spreadsheetId: String, tabs: List<GoogleSheetsTabSchema>) {
        val requests = JSONArray()
        tabs.forEach { tab ->
            requests.put(
                JSONObject()
                    .put(
                        "addSheet",
                        JSONObject()
                            .put(
                                "properties",
                                JSONObject()
                                    .put("title", tab.title)
                                    .put(
                                        "gridProperties",
                                        JSONObject()
                                            .put("rowCount", 1)
                                            .put("columnCount", tab.headers.size),
                                    ),
                            ),
                    ),
            )
        }
        batchUpdate(accessToken, spreadsheetId, JSONObject().put("requests", requests))
    }

    private fun readHeaders(
        accessToken: String,
        spreadsheetId: String,
        tabs: List<GoogleSheetsTabSchema>,
    ): Map<String, List<String>> {
        if (tabs.isEmpty()) return emptyMap()
        val ranges = tabs.joinToString("&") { tab ->
            "ranges=${url("'${tab.title.escapeSheetName()}'!A1:${columnName(tab.headers.size)}1")}"
        }
        val response = request(
            method = "GET",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}/values:batchGet?$ranges&majorDimension=ROWS",
            accessToken = accessToken,
        )
        return JSONObject(response.asText())
            .optJSONArray("valueRanges")
            .orEmptyObjects()
            .associate { range ->
                val title = range.optString("range").substringBefore("!").trim('\'')
                val headers = range.optJSONArray("values").orEmptyArrays().firstOrNull()?.toStringList().orEmpty()
                title to headers
            }
    }

    private fun writeHeaders(accessToken: String, spreadsheetId: String, tabs: List<GoogleSheetsTabSchema>) {
        val data = JSONArray()
        tabs.forEach { tab ->
            data.put(
                JSONObject()
                    .put("range", "'${tab.title.escapeSheetName()}'!A1:${columnName(tab.headers.size)}1")
                    .put("majorDimension", "ROWS")
                    .put("values", JSONArray().put(JSONArray(tab.headers))),
            )
        }
        val body = JSONObject()
            .put("valueInputOption", "RAW")
            .put("data", data)
        request(
            method = "POST",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}/values:batchUpdate",
            accessToken = accessToken,
            body = body.toString().toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun writeRows(accessToken: String, spreadsheetId: String, rows: List<WonderFoodSnapshotRow>) {
        val rowsByTab = rows.groupBy { it.tab }
        val data = JSONArray()
        clearTabBodies(accessToken, spreadsheetId, WONDERFOOD_TABS)
        WONDERFOOD_TABS.forEach { tab ->
            val tabRows = rowsByTab[tab.logicalTitle].orEmpty()
            val values = JSONArray()
            tabRows.forEach { row ->
                values.put(
                    JSONArray()
                        .put(row.id)
                        .put(row.version)
                        .put(row.updatedAt)
                        .put(row.archivedAt.orEmpty())
                        .put(row.payloadJson),
                )
            }
            data.put(
                JSONObject()
                    .put("range", "'${tab.title.escapeSheetName()}'!A2:E${(tabRows.size + 1).coerceAtLeast(2)}")
                    .put("majorDimension", "ROWS")
                    .put("values", values),
            )
        }
        val body = JSONObject()
            .put("valueInputOption", "RAW")
            .put("data", data)
        request(
            method = "POST",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}/values:batchUpdate",
            accessToken = accessToken,
            body = body.toString().toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun writeWorkspaceRows(
        accessToken: String,
        spreadsheetId: String,
        snapshot: WonderFoodSnapshot,
        updatedAt: String,
    ) {
        val rowsByTab = workspaceRows(snapshot, updatedAt)
        val data = JSONArray()
        clearTabBodies(accessToken, spreadsheetId, HUMAN_WORKSPACE_TABS)
        HUMAN_WORKSPACE_TABS.forEach { tab ->
            val rows = rowsByTab[tab.title].orEmpty()
            val values = JSONArray()
            rows.forEach { row ->
                values.put(JSONArray(row))
            }
            data.put(
                JSONObject()
                    .put("range", "'${tab.title.escapeSheetName()}'!A2:${columnName(tab.headers.size)}${(rows.size + 1).coerceAtLeast(2)}")
                    .put("majorDimension", "ROWS")
                    .put("values", values),
            )
        }
        val body = JSONObject()
            .put("valueInputOption", "USER_ENTERED")
            .put("data", data)
        request(
            method = "POST",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}/values:batchUpdate",
            accessToken = accessToken,
            body = body.toString().toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun clearTabBodies(accessToken: String, spreadsheetId: String, tabs: List<GoogleSheetsTabSchema>) {
        val ranges = JSONArray()
        tabs.forEach { tab ->
            ranges.put("'${tab.title.escapeSheetName()}'!A2:${columnName(tab.headers.size)}")
        }
        request(
            method = "POST",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}/values:batchClear",
            accessToken = accessToken,
            body = JSONObject().put("ranges", ranges).toString().toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun batchUpdate(accessToken: String, spreadsheetId: String, body: JSONObject) {
        request(
            method = "POST",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}:batchUpdate",
            accessToken = accessToken,
            body = body.toString().toByteArray(StandardCharsets.UTF_8),
        )
    }

    private fun applyWorkspacePresentation(
        accessToken: String,
        spreadsheetId: String,
        spreadsheet: GoogleSheetsSpreadsheet,
        provisionSystemForTitles: Set<String>,
        createTablesForTitles: Set<String>,
    ) {
        val requests = workspacePresentationRequests(spreadsheet, provisionSystemForTitles, createTablesForTitles)
        if (requests.length() == 0) return
        batchUpdate(accessToken, spreadsheetId, JSONObject().put("requests", requests))
    }

    private fun request(
        method: String,
        url: String,
        accessToken: String,
        body: ByteArray? = null,
    ): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Content-Length", body.size.toString())
            }
        }
        if (body != null) {
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.use { it.readBytes() } ?: ByteArray(0)
        connection.disconnect()
        if (code !in 200..299) {
            val message = response.asText().ifBlank { "HTTP $code" }
            error("Google Sheets request failed: $message")
        }
        return response
    }

    private fun JSONObject.toSpreadsheet(): GoogleSheetsSpreadsheet =
        GoogleSheetsSpreadsheet(
            spreadsheetId = getString("spreadsheetId"),
            title = optJSONObject("properties")?.optString("title").orEmpty(),
            sheets = optJSONArray("sheets").orEmptyObjects().map { sheet ->
                val properties = sheet.optJSONObject("properties") ?: JSONObject()
                val grid = properties.optJSONObject("gridProperties") ?: JSONObject()
                GoogleSheetsSheet(
                    sheetId = properties.optInt("sheetId"),
                    title = properties.optString("title"),
                    rowCount = grid.optInt("rowCount"),
                    columnCount = grid.optInt("columnCount"),
                )
            },
        )

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return List(length()) { index -> getJSONObject(index) }
    }

    private fun JSONArray?.orEmptyArrays(): List<JSONArray> {
        if (this == null) return emptyList()
        return List(length()) { index -> getJSONArray(index) }
    }

    private fun JSONArray.toSnapshotRow(tab: String): WonderFoodSnapshotRow? {
        if (length() < 5) return null
        val id = optString(0).takeIf { it.isNotBlank() } ?: return null
        val version = optString(1).toLongOrNull() ?: optLong(1, 0L)
        val updatedAt = optString(2).takeIf { it.isNotBlank() } ?: return null
        val archivedAt = optString(3).takeIf { it.isNotBlank() }
        val payloadJson = optString(4).takeIf { it.isNotBlank() } ?: return null
        return WonderFoodSnapshotRow(
            tab = tab,
            id = id,
            version = version,
            updatedAt = updatedAt,
            archivedAt = archivedAt,
            payloadJson = payloadJson,
        )
    }

    private fun JSONArray.toWorkspaceRow(tab: String, headers: List<String>): GoogleSheetsWorkspaceRow? {
        if (headers.isEmpty()) return null
        val values = headers.mapIndexedNotNull { index, header ->
            header.takeIf { it.isNotBlank() }?.let { it to optString(index).trim() }
        }.toMap()
        val identifier = values["identifier"].orEmpty()
        val hasHumanValue = values.any { (key, value) -> key != "identifier" && value.isNotBlank() }
        if (identifier.isBlank() && !hasHumanValue) return null
        return GoogleSheetsWorkspaceRow(
            tab = tab,
            identifier = identifier,
            values = values,
        )
    }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { index -> optString(index) }

    private fun ByteArray.asText(): String = toString(StandardCharsets.UTF_8)

    private fun url(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun urlPath(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    companion object {
        val WONDERFOOD_TABS: List<GoogleSheetsTabSchema> = listOf(
            "_meta" to "_meta",
            "_changes" to "_changes",
            "_mutations" to "_mutations",
            "_wf_pages" to "pages",
            "_wf_foods" to "foods",
            "_wf_food_aliases" to "food_aliases",
            "_wf_stock_lots" to "stock_lots",
            "_wf_nutrition_snapshots" to "nutrition_snapshots",
            "_wf_recipes" to "recipes",
            "_wf_recipe_ingredients" to "recipe_ingredients",
            "_wf_recipe_steps" to "recipe_steps",
            "_wf_meal_plans" to "meal_plans",
            "_wf_plan_entries" to "plan_entries",
            "_wf_meal_logs" to "meal_logs",
            "_wf_shopping_items" to "shopping_items",
            "_wf_receipts" to "receipts",
            "_wf_receipt_lines" to "receipt_lines",
            "_wf_preferences" to "preferences",
            "_wf_events" to "events",
            "_wf_relations" to "relations",
            "_wf_attachments" to "attachments",
        ).map { (title, logicalTitle) ->
            GoogleSheetsTabSchema(
                title = title,
                logicalTitle = logicalTitle,
                headers = listOf("id", "version", "updated_at", "archived_at", "payload_json"),
            )
        }

        val HUMAN_WORKSPACE_TABS: List<GoogleSheetsTabSchema> =
            WonderFoodWorkspaceSchema.tables.map { table ->
                GoogleSheetsTabSchema(
                    title = table.title,
                    headers = table.fields.map { it.name },
                )
            }

        internal fun columnName(columnCount: Int): String {
            require(columnCount > 0) { "Column count must be positive." }
            var value = columnCount
            val name = StringBuilder()
            while (value > 0) {
                value -= 1
                name.append(('A'.code + (value % 26)).toChar())
                value /= 26
            }
            return name.reverse().toString()
        }

        private const val SHEETS_BASE = "https://sheets.googleapis.com/v4"
        private const val TIMEOUT_MILLIS = 30_000
    }
}

data class GoogleSheetsSpreadsheet(
    val spreadsheetId: String,
    val title: String,
    val sheets: List<GoogleSheetsSheet>,
)

data class GoogleSheetsSheet(
    val sheetId: Int,
    val title: String,
    val rowCount: Int,
    val columnCount: Int,
)

data class GoogleSheetsTabSchema(
    val title: String,
    val logicalTitle: String = title,
    val headers: List<String>,
)

data class GoogleSheetsBootstrapResult(
    val spreadsheetId: String,
    val title: String,
    val createdTabs: List<String>,
    val initializedHeaders: List<String>,
    val totalTabs: Int,
) {
    val createdCount: Int = createdTabs.size
    val initializedCount: Int = initializedHeaders.size
}

data class GoogleSheetsExportResult(
    val spreadsheetId: String,
    val rowCount: Int,
    val tabs: List<String>,
)

data class GoogleSheetsWorkspaceRow(
    val tab: String,
    val identifier: String,
    val values: Map<String, String>,
)

private fun String.escapeSheetName(): String = replace("'", "''")

private fun String.tableName(): String =
    replace(Regex("[^A-Za-z0-9_]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
        .let { if (it.firstOrNull()?.isLetter() == true) it else "WonderFood_$it" }

private fun Any?.toSheetCell(): String = when (this) {
    null -> ""
    is Double -> toString()
    else -> toString()
}
