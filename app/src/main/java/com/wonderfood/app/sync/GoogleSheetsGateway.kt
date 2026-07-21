package com.wonderfood.app.sync

import com.wonderfood.core.model.household.HouseholdSnapshot
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

interface GoogleSheetsSnapshotGateway {
    fun ensureWonderFoodSchema(accessToken: String, spreadsheetId: String): GoogleSheetsBootstrapResult
    fun readWorkspaceRows(accessToken: String, spreadsheetId: String): List<GoogleSheetsWorkspaceRow>
}

class GoogleSheetsGateway : GoogleSheetsSnapshotGateway {
    fun createWonderFoodSpreadsheet(
        accessToken: String,
        title: String = DEFAULT_SPREADSHEET_TITLE,
    ): GoogleSheetsCreatedSpreadsheet {
        require(accessToken.isNotBlank()) { "Google Sheets access token must not be blank." }
        val safeTitle = title.trim().ifBlank { DEFAULT_SPREADSHEET_TITLE }
        val response = request(
            method = "POST",
            url = "$SHEETS_BASE/spreadsheets",
            accessToken = accessToken,
            body = createSpreadsheetRequestBody(safeTitle).toString().toByteArray(StandardCharsets.UTF_8),
        )
        return JSONObject(response.asText()).toCreatedSpreadsheet(safeTitle)
    }

    internal fun exportGraph(
        accessToken: String,
        spreadsheetId: String,
        snapshot: HouseholdSnapshot,
    ): GoogleSheetsExportResult =
        exportGraph(accessToken, spreadsheetId, WorkspaceGraphProjector.project(snapshot))

    internal fun exportGraph(
        accessToken: String,
        spreadsheetId: String,
        projection: WorkspaceGraphProjection,
    ): GoogleSheetsExportResult {
        ensureWonderFoodSchema(accessToken, spreadsheetId)
        writeWorkspaceGraph(accessToken, spreadsheetId, projection)
        return GoogleSheetsExportResult(
            spreadsheetId = spreadsheetId,
            rowCount = projection.rows.values.sumOf { it.size },
            tabs = ALL_TABS.map { it.title },
        )
    }

    override fun readWorkspaceRows(accessToken: String, spreadsheetId: String): List<GoogleSheetsWorkspaceRow> {
        val ranges = IMPORT_WORKSPACE_TABS.joinToString("&") { tab ->
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
                arrays.drop(1).mapIndexedNotNull { index, row ->
                    row.toWorkspaceRow(
                        tab = tab,
                        headers = headers,
                        remoteIdentity = "sheet:$tab:row:${index + 2}",
                    )
                }
            }
    }

    override fun ensureWonderFoodSchema(accessToken: String, spreadsheetId: String): GoogleSheetsBootstrapResult {
        require(accessToken.isNotBlank()) { "Google Sheets access token must not be blank." }
        require(spreadsheetId.isNotBlank()) { "Google spreadsheet ID must not be blank." }
        val before = spreadsheet(accessToken, spreadsheetId)
        val existingTitles = before.sheets.map { it.title }.toSet()
        val allTabs = ALL_TABS
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
            provisionSystemForTitles = ALL_TABS.map { it.title }.toSet(),
            createTablesForTitles = HUMAN_WORKSPACE_TABS.map { it.title }.toSet(),
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
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}?fields=${url(SPREADSHEET_FIELDS)}",
            accessToken = accessToken,
        )
        return JSONObject(response.asText()).toSpreadsheet()
    }

    internal fun workspaceRows(snapshot: HouseholdSnapshot): Map<String, List<List<String>>> =
        workspaceRows(WorkspaceGraphProjector.project(snapshot))

    internal fun workspaceRows(projection: WorkspaceGraphProjection): Map<String, List<List<String>>> {
        require(projection.schemaVersion == WORKSPACE_GRAPH_SCHEMA_VERSION) {
            "Workspace upgrade required: expected Google Sheets V4 graph."
        }
        return ALL_TABS.associate { tab ->
            val rows = projection.rows[tab.surface].orEmpty()
            tab.title to rows.mapIndexed { index, row ->
                sheetRow(tab, row, index + 2, projection)
            }
        }
    }

    internal fun createSpreadsheetRequestBody(title: String): JSONObject =
        JSONObject()
            .put("properties", JSONObject().put("title", title.trim().ifBlank { DEFAULT_SPREADSHEET_TITLE }))
            .put(
                "sheets",
                JSONArray().put(JSONObject().put("properties", JSONObject().put("title", WorkspaceGraphSurface.HOME.label))),
            )

    internal fun workspacePresentationRequests(
        spreadsheet: GoogleSheetsSpreadsheet,
        provisionSystemForTitles: Set<String>,
        createTablesForTitles: Set<String>,
        includeCharts: Boolean = true,
    ): JSONArray {
        val schemaByTitle = ALL_TABS.associateBy { it.title }
        val requests = JSONArray()
        spreadsheet.sheets.forEach { sheet ->
            val schema = schemaByTitle[sheet.title] ?: return@forEach
            val isRawSyncTab = schema.hidden
            val isEverydayTab = schema.surface.primary
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
                                        .put("columnCount", sheet.columnCount.coerceAtLeast(schema.headers.size))
                                        .put("frozenRowCount", 1),
                                ),
                        )
                        .put("fields", "hidden,tabColor,gridProperties.rowCount,gridProperties.columnCount,gridProperties.frozenRowCount"),
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
            val namedRangeRequests = namedRangePresentationRequests(spreadsheet, sheet, schema)
            for (i in 0 until namedRangeRequests.length()) {
                requests.put(namedRangeRequests.getJSONObject(i))
            }
            if (sheet.title in createTablesForTitles && sheet.tables.none { it.name == sheet.title.tableName() }) {
                requests.put(filterViewPresentationRequest(sheet, schema))
            }
            val validationRequests = dataValidationPresentationRequests(sheet, schema)
            for (i in 0 until validationRequests.length()) {
                requests.put(validationRequests.getJSONObject(i))
            }
            if (
                includeCharts &&
                sheet.title == "Home" &&
                sheet.columnCount >= SPENDING_CHART_END_COLUMN_INDEX &&
                sheet.charts.none { it.title == SPENDING_CHART_TITLE }
            ) {
                requests.put(spendingChartPresentationRequest(sheet))
            }
            if (sheet.title in createTablesForTitles) {
                requests.put(tablePresentationRequest(sheet, schema))
            }
            if (sheet.title in provisionSystemForTitles) {
                systemColumnsProtectionRequest(sheet, schema)?.let { requests.put(it) }
                sheetDeveloperMetadataRequest(sheet, schema, isRawSyncTab)?.let { requests.put(it) }
            }
            schema.hiddenColumnStartIndex?.let {
                requests.put(hiddenSystemColumnsRequest(sheet, schema))
            }
            if (schema.formulaColumnIndexes.isNotEmpty()) {
                formulaColumnsProtectionRequest(sheet, schema)?.let { requests.put(it) }
            }
        }
        return requests
    }

    private fun dataValidationPresentationRequests(sheet: GoogleSheetsSheet, schema: GoogleSheetsTabSchema): JSONArray {
        val requests = JSONArray()
        schema.fields.forEachIndexed { index, field ->
            val request = when (field.type) {
                WorkspaceGraphValueType.RELATION -> workspaceRelationValidationRequest(sheet, index, field)
                else -> null
            }
            request?.let { requests.put(it) }
        }
        return requests
    }

    private fun workspaceRelationValidationRequest(
        sheet: GoogleSheetsSheet,
        columnIndex: Int,
        field: WorkspaceGraphField,
    ): JSONObject? {
        val target = field.relationTarget ?: return null
        return JSONObject().put(
            "setDataValidation",
            JSONObject()
                .put("range", validationRange(sheet, columnIndex))
                .put(
                    "rule",
                    JSONObject().put(
                        "condition",
                        JSONObject()
                            .put("type", "ONE_OF_RANGE")
                            .put("values", JSONArray().put(JSONObject().put("userEnteredValue", "=${relationNamedRangeName(target)}"))),
                    ).put("strict", true).put("showCustomUi", true),
                ),
        )
    }

    private fun workspaceSelectValidationRequest(
        sheet: GoogleSheetsSheet,
        columnIndex: Int,
        columnName: String,
    ): JSONObject? {
        val options = workspaceSelectOptions(columnName)
        if (options.isEmpty()) return null
        return JSONObject().put(
            "setDataValidation",
            JSONObject()
                .put(
                    "range",
                    validationRange(sheet, columnIndex),
                ).put(
                    "rule",
                    JSONObject().put(
                        "condition",
                        JSONObject()
                            .put("type", "ONE_OF_LIST")
                            .put(
                                "values",
                                JSONArray().apply {
                                    options.forEach { put(JSONObject().put("userEnteredValue", it)) }
                                },
                            ),
                    ).put("strict", true).put("showCustomUi", true),
                ),
        )
    }

    private fun workspaceCheckboxValidationRequest(
        sheet: GoogleSheetsSheet,
        columnIndex: Int,
    ): JSONObject =
        JSONObject().put(
            "setDataValidation",
            JSONObject()
                .put(
                    "range",
                    validationRange(sheet, columnIndex),
                ).put(
                    "rule",
                    JSONObject()
                        .put("condition", JSONObject().put("type", "BOOLEAN"))
                        .put("strict", true)
                        .put("showCustomUi", true),
                ),
        )

    private fun workspaceNumberValidationRequest(
        sheet: GoogleSheetsSheet,
        columnIndex: Int,
    ): JSONObject =
        JSONObject().put(
            "setDataValidation",
            JSONObject()
                .put(
                    "range",
                    validationRange(sheet, columnIndex),
                ).put(
                    "rule",
                    JSONObject()
                        .put(
                            "condition",
                            JSONObject()
                                .put("type", "NUMBER_GREATER_THAN_EQ")
                                .put("values", JSONArray().put(JSONObject().put("userEnteredValue", "0"))),
                        ).put("strict", false).put("showCustomUi", false),
                ),
        )

    private fun workspaceDateValidationRequest(
        sheet: GoogleSheetsSheet,
        columnIndex: Int,
    ): JSONObject =
        JSONObject().put(
            "setDataValidation",
            JSONObject()
                .put(
                    "range",
                    validationRange(sheet, columnIndex),
                ).put("rule", JSONObject().put("condition", JSONObject().put("type", "DATE_IS_VALID"))),
        )

    private fun validationRange(sheet: GoogleSheetsSheet, columnIndex: Int): JSONObject =
        JSONObject()
            .put("sheetId", sheet.sheetId)
            .put("startRowIndex", 1)
            .put("endRowIndex", WORKSPACE_VALIDATION_ROW_LIMIT)
            .put("startColumnIndex", columnIndex)
            .put("endColumnIndex", columnIndex + 1)

    private fun namedRangePresentationRequests(
        spreadsheet: GoogleSheetsSpreadsheet,
        sheet: GoogleSheetsSheet,
        schema: GoogleSheetsTabSchema,
    ): JSONArray {
        val requests = JSONArray()
        requests.put(namedRangeRepairRequest(spreadsheet, "${sheet.title.tableName()}_data", tableRange(sheet, schema, WORKSPACE_VALIDATION_ROW_LIMIT)))
        if (schema.surface.visibleInSheets || schema.surface == WorkspaceGraphSurface.STOCK_LOTS) {
            requests.put(
                namedRangeRepairRequest(
                    spreadsheet,
                    relationNamedRangeName(schema.surface),
                    JSONObject()
                        .put("sheetId", sheet.sheetId)
                        .put("startRowIndex", 1)
                        .put("endRowIndex", WORKSPACE_VALIDATION_ROW_LIMIT)
                        .put("startColumnIndex", 0)
                        .put("endColumnIndex", 1),
                ),
            )
        }
        return requests
    }

    private fun namedRangeRepairRequest(
        spreadsheet: GoogleSheetsSpreadsheet,
        name: String,
        range: JSONObject,
    ): JSONObject {
        val existing = spreadsheet.namedRanges.firstOrNull { it.name == name }
        val namedRange = JSONObject().put("name", name).put("range", range)
        return if (existing == null) {
            JSONObject().put("addNamedRange", JSONObject().put("namedRange", namedRange))
        } else {
            JSONObject().put(
                "updateNamedRange",
                JSONObject()
                    .put("namedRange", namedRange.put("namedRangeId", existing.namedRangeId))
                    .put("fields", "range"),
            )
        }
    }

    private fun filterViewPresentationRequest(sheet: GoogleSheetsSheet, schema: GoogleSheetsTabSchema): JSONObject =
        JSONObject().put(
            "addFilterView",
            JSONObject().put(
                "filter",
                JSONObject()
                    .put("title", "${sheet.title} view")
                    .put(
                        "range",
                        JSONObject()
                            .put("sheetId", sheet.sheetId)
                            .put("startRowIndex", 0)
                            .put("endRowIndex", 1000)
                            .put("startColumnIndex", 0)
                            .put("endColumnIndex", schema.headers.size),
                    ),
            ),
        )

    private fun spendingChartPresentationRequest(sheet: GoogleSheetsSheet): JSONObject =
        JSONObject().put(
            "addChart",
            JSONObject().put(
                "chart",
                JSONObject()
                    .put(
                        "spec",
                        JSONObject()
                            .put("title", "Spending Trend")
                            .put(
                                "basicChart",
                                JSONObject()
                                    .put("chartType", "LINE")
                                    .put("legendPosition", "BOTTOM_LEGEND")
                                    .put(
                                        "axis",
                                        JSONArray()
                                            .put(JSONObject().put("position", "BOTTOM_AXIS").put("title", "Metric"))
                                            .put(JSONObject().put("position", "LEFT_AXIS").put("title", "Value")),
                                    ).put(
                                        "domains",
                                        JSONArray()
                                            .put(
                                                JSONObject().put(
                                                    "domain",
                                                    JSONObject().put(
                                                        "sourceRange",
                                                        JSONObject().put(
                                                            "sources",
                                                            JSONArray().put(
                                                                JSONObject()
                                                                    .put("sheetId", sheet.sheetId)
                                                                    .put("startRowIndex", 0)
                                                                    .put("endRowIndex", 2)
                                                                    .put("startColumnIndex", 0)
                                                                    .put("endColumnIndex", 1),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                    ).put(
                                        "series",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put(
                                                        "series",
                                                        JSONObject()
                                                            .put(
                                                                "sourceRange",
                                                                JSONObject().put(
                                                                    "sources",
                                                                    JSONArray().put(
                                                                        JSONObject()
                                                                            .put("sheetId", sheet.sheetId)
                                                                            .put("startRowIndex", 1)
                                                                            .put("endRowIndex", 1000)
                                                                            .put("startColumnIndex", 1)
                                                                            .put("endColumnIndex", 2),
                                                                    ),
                                                                ),
                                                            ),
                                                    ).put("targetAxis", "LEFT_AXIS"),
                                            ),
                                    ).put("headerCount", 1),
                            ),
                    ).put(
                        "position",
                        JSONObject()
                            .put(
                                "overlayPosition",
                                JSONObject()
                                    .put(
                                        "anchorCell",
                                        JSONObject().put("sheetId", sheet.sheetId).put("rowIndex", 1).put("columnIndex", 8),
                                    ),
                            ),
                    ),
            ),
        )

    private fun workspaceSelectOptions(columnName: String): List<String> = when (columnName) {
        "Unit", "Kitchen unit" -> WorkspaceGraphContract.supportedUnits + "unknown"
        "Kind" -> listOf("Food", "Household", "Cleaning", "Personal care", "Medicine", "Pet", "Other")
        "Pantry state" -> listOf("Available", "Opened", "Reserved", "Low", "Out", "Archived")
        "Location" -> listOf("Pantry", "Fridge", "Freezer")
        "Meal slot" -> listOf("Breakfast", "Lunch", "Dinner", "Snack", "Anytime", "Unknown")
        "Status" -> listOf("Draft", "Active", "Planned", "Cooked", "Eaten", "Needed", "In cart", "Purchased", "Reviewed", "Reconciled", "Archived")
        "Reason" -> listOf("Manual", "Low stock", "Recipe gap", "Household staple", "Receipt reorder", "Ai suggestion")
        "Category" -> listOf("grain", "vegetable", "fruit", "dairy", "cleaning", "household", "other")
        "Disposition" -> listOf("Inventory", "Household", "Consumed", "Service", "Ignored")
        "Currency" -> listOf("USD", "EUR", "GBP")
        else -> if (columnName.contains("state", ignoreCase = true)) listOf("Unknown", "Active", "Archived") else emptyList()
    }

    private fun tablePresentationRequest(sheet: GoogleSheetsSheet, schema: GoogleSheetsTabSchema): JSONObject {
        val table = sheet.tables.firstOrNull { it.name == sheet.title.tableName() }
        val tableObject = JSONObject()
            .put("name", sheet.title.tableName())
            .put("range", tableRange(sheet, schema, sheet.rowCount.coerceAtLeast(WORKSPACE_VALIDATION_ROW_LIMIT)))
            .put("columnProperties", tableColumnProperties(schema))
        return if (table == null) {
            JSONObject().put("addTable", JSONObject().put("table", tableObject))
        } else {
            JSONObject().put(
                "updateTable",
                JSONObject()
                    .put("table", tableObject.put("tableId", table.tableId))
                    .put("fields", "range,columnProperties"),
            )
        }
    }

    private fun tableRange(sheet: GoogleSheetsSheet, schema: GoogleSheetsTabSchema, rowCount: Int): JSONObject =
        JSONObject()
            .put("sheetId", sheet.sheetId)
            .put("startRowIndex", 0)
            .put("endRowIndex", rowCount.coerceAtLeast(2))
            .put("startColumnIndex", 0)
            .put("endColumnIndex", schema.headers.size)

    private fun tableColumnProperties(schema: GoogleSheetsTabSchema): JSONArray =
        JSONArray().apply {
            schema.headers.forEachIndexed { index, header ->
                val field = schema.fields.getOrNull(index)
                val dropdownOptions = if (field?.type in setOf(WorkspaceGraphValueType.SELECT, WorkspaceGraphValueType.MULTI_SELECT)) {
                    workspaceSelectOptions(header)
                } else {
                    emptyList()
                }
                put(
                    JSONObject()
                        .put("columnIndex", index)
                        .put("columnName", header)
                        .apply {
                            field.toTableColumnType(dropdownOptions.isNotEmpty())?.let { type -> put("columnType", type) }
                            if (dropdownOptions.isNotEmpty()) {
                                put(
                                    "dataValidationRule",
                                    JSONObject().put(
                                        "condition",
                                        JSONObject()
                                            .put("type", "ONE_OF_LIST")
                                            .put("values", JSONArray().apply {
                                                dropdownOptions.forEach { option -> put(JSONObject().put("userEnteredValue", option)) }
                                            }),
                                    ),
                                )
                            }
                        },
                )
            }
        }

    private fun WorkspaceGraphField?.toTableColumnType(hasDropdownOptions: Boolean): String? = when (this?.type) {
        WorkspaceGraphValueType.DECIMAL -> "DOUBLE"
        WorkspaceGraphValueType.MONEY -> "CURRENCY"
        WorkspaceGraphValueType.DATE -> "DATE"
        WorkspaceGraphValueType.DATE_TIME -> "DATE_TIME"
        WorkspaceGraphValueType.BOOLEAN -> "BOOLEAN"
        WorkspaceGraphValueType.SELECT, WorkspaceGraphValueType.MULTI_SELECT -> if (hasDropdownOptions) "DROPDOWN" else null
        WorkspaceGraphValueType.RELATION -> null
        else -> null
    }

    private fun systemColumnsProtectionRequest(sheet: GoogleSheetsSheet, schema: GoogleSheetsTabSchema): JSONObject? {
        val protectedColumnNames = listOf("_wf_id", "_wf_revision", "_wf_archived", "_wf_updated_at", "Value") +
            schema.relationIdFields.map { it.relationIdHeader }
        val protectedIndexes = schema.headers.mapIndexedNotNull { index, header ->
            index.takeIf { header in protectedColumnNames }
        }
        val startColumn = protectedIndexes.minOrNull() ?: 0
        val endColumn = (protectedIndexes.maxOrNull() ?: 0) + 1
        return protectionRepairRequest(
            sheet = sheet,
            description = "WonderFood system columns. Edit only if you know what you are changing.",
            startRowIndex = 0,
            startColumnIndex = startColumn,
            endColumnIndex = endColumn,
        )
    }

    private fun formulaColumnsProtectionRequest(sheet: GoogleSheetsSheet, schema: GoogleSheetsTabSchema): JSONObject? {
        val startColumn = schema.formulaColumnIndexes.minOrNull() ?: 0
        val endColumn = (schema.formulaColumnIndexes.maxOrNull() ?: 0) + 1
        return protectionRepairRequest(
            sheet = sheet,
            description = "WonderFood formula columns are automatically repaired.",
            startRowIndex = 1,
            startColumnIndex = startColumn,
            endColumnIndex = endColumn,
        )
    }

    private fun protectionRepairRequest(
        sheet: GoogleSheetsSheet,
        description: String,
        startRowIndex: Int,
        startColumnIndex: Int,
        endColumnIndex: Int,
    ): JSONObject? {
        if (endColumnIndex <= startColumnIndex) return null
        val protectedRange = JSONObject()
            .put(
                "range",
                JSONObject()
                    .put("sheetId", sheet.sheetId)
                    .put("startRowIndex", startRowIndex)
                    .put("startColumnIndex", startColumnIndex)
                    .put("endColumnIndex", endColumnIndex),
            )
            .put("description", description)
            .put("warningOnly", true)
        val existing = sheet.protectedRanges.firstOrNull { it.description == description }
        return if (existing == null) {
            JSONObject().put("addProtectedRange", JSONObject().put("protectedRange", protectedRange))
        } else {
            JSONObject().put(
                "updateProtectedRange",
                JSONObject()
                    .put("protectedRange", protectedRange.put("protectedRangeId", existing.protectedRangeId))
                    .put("fields", "range,warningOnly,description"),
            )
        }
    }

    private fun hiddenSystemColumnsRequest(sheet: GoogleSheetsSheet, schema: GoogleSheetsTabSchema): JSONObject =
        JSONObject().put(
            "updateDimensionProperties",
            JSONObject()
                .put(
                    "range",
                    JSONObject()
                        .put("sheetId", sheet.sheetId)
                        .put("dimension", "COLUMNS")
                        .put("startIndex", schema.hiddenColumnStartIndex)
                        .put("endIndex", schema.headers.size),
                )
                .put("properties", JSONObject().put("hiddenByUser", true))
                .put("fields", "hiddenByUser"),
        )

    private fun sheetDeveloperMetadataRequest(
        sheet: GoogleSheetsSheet,
        schema: GoogleSheetsTabSchema,
        isRawSyncTab: Boolean,
    ): JSONObject? {
        val value = JSONObject()
            .put("title", schema.title)
            .put("kind", if (isRawSyncTab) "support" else "workspace")
            .put("schemaVersion", WORKSPACE_GRAPH_SCHEMA_VERSION)
            .put("surface", schema.surface.key)
            .put("headers", JSONArray(schema.headers))
            .toString()
        val metadata = JSONObject()
            .put("metadataKey", "wonderfood.table")
            .put("metadataValue", value)
            .put("location", JSONObject().put("sheetId", sheet.sheetId))
            .put("visibility", "DOCUMENT")
        val existing = sheet.developerMetadata.firstOrNull { it.metadataKey == "wonderfood.table" }
        return if (existing == null) {
            JSONObject().put("createDeveloperMetadata", JSONObject().put("developerMetadata", metadata))
        } else if (existing.metadataValue == value) {
            null
        } else {
            JSONObject().put(
                "updateDeveloperMetadata",
                JSONObject()
                    .put(
                        "dataFilters",
                        JSONArray().put(
                            JSONObject().put(
                                "developerMetadataLookup",
                                JSONObject().put("metadataId", existing.metadataId),
                            ),
                        ),
                    )
                    .put("developerMetadata", metadata.put("metadataId", existing.metadataId))
                    .put("fields", "metadataValue,visibility,location"),
            )
        }
    }

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

    private fun repairWorkspaceTableRanges(
        accessToken: String,
        spreadsheetId: String,
        projection: WorkspaceGraphProjection,
    ) {
        val requests = tableRangeRepairRequests(spreadsheet(accessToken, spreadsheetId), projection)
        if (requests.length() > 0) {
            batchUpdate(accessToken, spreadsheetId, JSONObject().put("requests", requests))
        }
    }

    internal fun tableRangeRepairRequests(
        spreadsheet: GoogleSheetsSpreadsheet,
        projection: WorkspaceGraphProjection,
    ): JSONArray {
        val requests = JSONArray()
        val rowsBySurface = projection.rows.mapValues { (_, rows) -> rows.size }
        HUMAN_WORKSPACE_TABS.forEach { schema ->
            val sheet = spreadsheet.sheets.firstOrNull { it.title == schema.title } ?: return@forEach
            val table = sheet.tables.firstOrNull { it.name == schema.title.tableName() } ?: return@forEach
            val desiredRows = (rowsBySurface[schema.surface] ?: 0) + 1
            requests.put(
                JSONObject().put(
                    "updateTable",
                    JSONObject()
                        .put(
                            "table",
                            JSONObject()
                                .put("tableId", table.tableId)
                                .put("range", tableRange(sheet, schema, desiredRows)),
                        )
                        .put("fields", "range"),
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
        }
        return requests
    }

    private fun clearTabBodies(accessToken: String, spreadsheetId: String, tabs: List<GoogleSheetsTabSchema>) {
        val ranges = JSONArray(clearRangesFor(tabs))
        request(
            method = "POST",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}/values:batchClear",
            accessToken = accessToken,
            body = JSONObject().put("ranges", ranges).toString().toByteArray(StandardCharsets.UTF_8),
        )
    }

    internal fun clearRangesFor(tabs: List<GoogleSheetsTabSchema>): List<String> =
        tabs.map { tab -> "'${tab.title.escapeSheetName()}'!A2:${columnName(tab.headers.size)}" }

    internal fun workspaceBatchUpdateData(snapshot: HouseholdSnapshot): JSONArray =
        workspaceBatchUpdateData(WorkspaceGraphProjector.project(snapshot))

    internal fun workspaceBatchUpdateData(projection: WorkspaceGraphProjection): JSONArray {
        val rowsByTab = workspaceRows(projection)
        val data = JSONArray()
        ALL_TABS.forEach { tab ->
            val rows = rowsByTab[tab.title].orEmpty()
            val values = JSONArray()
            rows.forEach { row -> values.put(JSONArray(row)) }
            data.put(
                JSONObject()
                    .put("range", "'${tab.title.escapeSheetName()}'!A2:${columnName(tab.headers.size)}${(rows.size + 1).coerceAtLeast(2)}")
                    .put("majorDimension", "ROWS")
                    .put("values", values),
            )
        }
        return data
    }

    private fun writeWorkspaceGraph(
        accessToken: String,
        spreadsheetId: String,
        projection: WorkspaceGraphProjection,
    ) {
        clearTabBodies(accessToken, spreadsheetId, ALL_TABS)
        val body = JSONObject()
            .put("valueInputOption", "USER_ENTERED")
            .put("data", workspaceBatchUpdateData(projection))
        request(
            method = "POST",
            url = "$SHEETS_BASE/spreadsheets/${urlPath(spreadsheetId)}/values:batchUpdate",
            accessToken = accessToken,
            body = body.toString().toByteArray(StandardCharsets.UTF_8),
        )
        repairWorkspaceTableRanges(accessToken, spreadsheetId, projection)
    }

    private fun sheetRow(
        tab: GoogleSheetsTabSchema,
        row: WorkspaceGraphRow,
        rowNumber: Int,
        projection: WorkspaceGraphProjection,
    ): List<String> {
        val visibleCells = tab.fields.map { field ->
            when {
                field.formulaKey != null -> formulaFor(field.formulaKey, tab, rowNumber, projection.defaultCurrency)
                field.type == WorkspaceGraphValueType.RELATION -> relationLabels(row.values[field.key], projection)
                else -> row.values[field.key].toSheetCell()
            }
        }
        val relationIdCells = tab.relationIdFields.map { field ->
            relationIds(row.values[field.key])
        }
        return visibleCells + relationIdCells + listOf(row.canonicalId, row.revision.toString(), row.archived.toString(), isoTimestamp(row.updatedAt))
    }

    private fun relationLabels(value: WorkspaceGraphValue?, projection: WorkspaceGraphProjection): String {
        val relation = value as? WorkspaceGraphValue.Relation ?: return ""
        val targetRows = projection.rows[relation.target].orEmpty().associateBy { it.canonicalId }
        return relation.canonicalIds.mapNotNull { id ->
            val targetRow = targetRows[id] ?: return@mapNotNull null
            val titleKey = WorkspaceGraphContract.schema(relation.target).titleField.key
            targetRow.values[titleKey].toSheetCell()
        }.joinToString(", ")
    }

    private fun relationIds(value: WorkspaceGraphValue?): String {
        val relation = value as? WorkspaceGraphValue.Relation ?: return ""
        return relation.canonicalIds.joinToString(", ")
    }

    private fun formulaFor(key: String, tab: GoogleSheetsTabSchema, row: Int, defaultCurrency: String): String {
        fun column(label: String, sheet: GoogleSheetsTabSchema = tab): String = columnName(sheet.headers.indexOf(label) + 1)
        fun cell(label: String): String = "${column(label)}$row"
        fun range(sheet: GoogleSheetsTabSchema, label: String): String {
            val col = column(label, sheet)
            return "'${sheet.title.escapeSheetName()}'!$${col}$2:$${col}$1000"
        }
        val kitchen = HUMAN_WORKSPACE_TABS.first { it.title == "Kitchen" }
        val ingredients = HUMAN_WORKSPACE_TABS.first { it.title == "Ingredients" }
        val recipes = HUMAN_WORKSPACE_TABS.first { it.title == "Recipes" }
        val spending = HUMAN_WORKSPACE_TABS.first { it.title == "Spending" }
        val purchaseLines = HUMAN_WORKSPACE_TABS.first { it.title == "Purchase Lines" }
        return when (key) {
            "kitchen_low_stock" -> "=IF(OR(${cell("On hand")}=\"\",${cell("Low at")}=\"\"),FALSE,${cell("On hand")}<=${cell("Low at")})"
            "ingredient_on_hand" -> "=IFERROR(XLOOKUP(${cell("Kitchen item ID")},${range(kitchen, "_wf_id")},${range(kitchen, "On hand")},\"\"),\"\")"
            "ingredient_kitchen_unit" -> "=IFERROR(XLOOKUP(${cell("Kitchen item ID")},${range(kitchen, "_wf_id")},${range(kitchen, "Unit")},\"\"),\"\")"
            "ingredient_missing_amount" -> "=IF(OR(${cell("Optional")} = TRUE,${cell("Amount")}=\"\"),0,IF(${cell("Status")}=\"Need\",MAX(${cell("Amount")}-${cell("On hand")},0),0))"
            "ingredient_status" -> "=IF(${cell("Optional")} = TRUE,\"Optional\",IF(${cell("Kitchen item ID")}=\"\",\"Unlinked\",IF(${cell("On hand")}=\"\",\"Check\",IF(${cell("Unit")}<>${cell("Kitchen unit")},\"Convert\",IF(${cell("On hand")}>=${cell("Amount")},\"Have\",\"Need\")))))"
            "ingredient_ready_score" -> "=IF(OR(${cell("Status")}=\"Have\",${cell("Status")}=\"Optional\"),1,0)"
            "ingredient_required_score" -> "=IF(${cell("Optional")} = TRUE,0,1)"
            "recipe_ingredient_count" -> "=COUNTIF(${range(ingredients, "Recipe ID")},${cell("_wf_id")})"
            "recipe_ready_count" -> "=SUMIF(${range(ingredients, "Recipe ID")},${cell("_wf_id")},${range(ingredients, "Ready score")})"
            "recipe_can_make_percent" -> "=IF(${cell("Ingredient count")}=0,0,${cell("Ready count")}/${cell("Ingredient count")})"
            "recipe_missing_items" -> "=TEXTJOIN(\", \",TRUE,FILTER(${range(ingredients, "Ingredient")},${range(ingredients, "Recipe ID")}=${cell("_wf_id")},${range(ingredients, "Status")}<>\"Have\",${range(ingredients, "Status")}<>\"Optional\"))"
            "meal_recipe_readiness" -> "=IFERROR(XLOOKUP(${cell("Recipe ID")},${range(recipes, "_wf_id")},${range(recipes, "Can make %")},\"\"),\"\")"
            "meal_missing_items" -> "=IFERROR(XLOOKUP(${cell("Recipe ID")},${range(recipes, "_wf_id")},${range(recipes, "Missing items")},\"\"),\"\")"
            "shopping_on_hand" -> "=IFERROR(XLOOKUP(${cell("Kitchen item ID")},${range(kitchen, "_wf_id")},${range(kitchen, "On hand")},\"\"),\"\")"
            "shopping_kitchen_unit" -> "=IFERROR(XLOOKUP(${cell("Kitchen item ID")},${range(kitchen, "_wf_id")},${range(kitchen, "Unit")},\"\"),\"\")"
            "shopping_still_needed" -> "=IF(OR(${cell("Amount")}=\"\",${cell("On hand")}=\"\"),${cell("Amount")},IF(${cell("Unit")}=${cell("Kitchen unit")},MAX(${cell("Amount")}-${cell("On hand")},0),${cell("Amount")}))"
            "purchase_line_final_amount" -> "=IF(${cell("Subtotal")}<>\"\",${cell("Subtotal")},IF(AND(${cell("Quantity")}<>\"\",${cell("Unit price")}<>\"\"),${cell("Quantity")}*${cell("Unit price")},0))-${cell("Discount")}+${cell("Tax")}"
            "purchase_line_currency" -> "=IFERROR(XLOOKUP(${cell("Purchase ID")},${range(spending, "_wf_id")},${range(spending, "Currency")},\"$defaultCurrency\"),\"$defaultCurrency\")"
            "spending_lines_subtotal" -> "=SUMIFS(${range(purchaseLines, "Final amount")},${range(purchaseLines, "Purchase ID")},${cell("_wf_id")})"
            "spending_food_amount" -> "=SUMIFS(${range(purchaseLines, "Final amount")},${range(purchaseLines, "Purchase ID")},${cell("_wf_id")},${range(purchaseLines, "Category")},\"food\")"
            "spending_non_food_amount" -> "=SUMIFS(${range(purchaseLines, "Final amount")},${range(purchaseLines, "Purchase ID")},${cell("_wf_id")},${range(purchaseLines, "Category")},\"<>food\")"
            "spending_line_count" -> "=COUNTIF(${range(purchaseLines, "Purchase ID")},${cell("_wf_id")})"
            "spending_effective_total" -> "=IF(${cell("Entered total")}<>\"\",${cell("Entered total")},${cell("Lines subtotal")})"
            "spending_difference" -> "=IF(${cell("Entered total")}=\"\",0,${cell("Entered total")}-${cell("Lines subtotal")})"
            else -> ""
        }
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
        val requests = workspacePresentationRequests(
            spreadsheet = spreadsheet,
            provisionSystemForTitles = provisionSystemForTitles,
            createTablesForTitles = createTablesForTitles,
            includeCharts = false,
        )
        if (requests.length() > 0) {
            batchUpdate(accessToken, spreadsheetId, JSONObject().put("requests", requests))
        }
        val chartRequests = workspacePresentationRequests(
            spreadsheet = spreadsheet(accessToken, spreadsheetId),
            provisionSystemForTitles = emptySet(),
            createTablesForTitles = emptySet(),
            includeCharts = true,
        ).filterRequests("addChart")
        if (chartRequests.length() == 0) return
        batchUpdate(accessToken, spreadsheetId, JSONObject().put("requests", chartRequests))
    }

    private fun JSONArray.filterRequests(requestType: String): JSONArray {
        val filtered = JSONArray()
        for (index in 0 until length()) {
            val request = getJSONObject(index)
            if (request.has(requestType)) filtered.put(request)
        }
        return filtered
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
            namedRanges = optJSONArray("namedRanges").orEmptyObjects().map { range ->
                GoogleSheetsNamedRange(
                    namedRangeId = range.optString("namedRangeId"),
                    name = range.optString("name"),
                )
            },
            sheets = optJSONArray("sheets").orEmptyObjects().map { sheet ->
                val properties = sheet.optJSONObject("properties") ?: JSONObject()
                val grid = properties.optJSONObject("gridProperties") ?: JSONObject()
                GoogleSheetsSheet(
                    sheetId = properties.optInt("sheetId"),
                    title = properties.optString("title"),
                    rowCount = grid.optInt("rowCount"),
                    columnCount = grid.optInt("columnCount"),
                    developerMetadata = sheet.optJSONArray("developerMetadata").orEmptyObjects().map { metadata ->
                        GoogleSheetsDeveloperMetadata(
                            metadataId = metadata.optInt("metadataId"),
                            metadataKey = metadata.optString("metadataKey"),
                            metadataValue = metadata.optString("metadataValue"),
                        )
                    },
                    protectedRanges = sheet.optJSONArray("protectedRanges").orEmptyObjects().map { protectedRange ->
                        GoogleSheetsProtectedRange(
                            protectedRangeId = protectedRange.optInt("protectedRangeId"),
                            description = protectedRange.optString("description"),
                        )
                    },
                    tables = sheet.optJSONArray("tables").orEmptyObjects().map { table ->
                        GoogleSheetsTable(
                            tableId = table.optString("tableId"),
                            name = table.optString("name"),
                        )
                    },
                    charts = sheet.optJSONArray("charts").orEmptyObjects().map { chart ->
                        GoogleSheetsChart(
                            chartId = chart.optInt("chartId"),
                            title = chart.optJSONObject("spec")?.optString("title").orEmpty(),
                        )
                    },
                )
            },
        )

    private fun JSONObject.toCreatedSpreadsheet(requestedTitle: String): GoogleSheetsCreatedSpreadsheet {
        val spreadsheetId = getString("spreadsheetId")
        return GoogleSheetsCreatedSpreadsheet(
            spreadsheetId = spreadsheetId,
            spreadsheetUrl = optString("spreadsheetUrl")
                .ifBlank { "https://docs.google.com/spreadsheets/d/$spreadsheetId/edit" },
            title = optJSONObject("properties")?.optString("title").orEmpty().ifBlank { requestedTitle },
        )
    }

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return List(length()) { index -> getJSONObject(index) }
    }

    private fun JSONArray?.orEmptyArrays(): List<JSONArray> {
        if (this == null) return emptyList()
        return List(length()) { index -> getJSONArray(index) }
    }

    internal fun JSONArray.toWorkspaceRow(
        tab: String,
        headers: List<String>,
        remoteIdentity: String? = null,
    ): GoogleSheetsWorkspaceRow? {
        if (headers.isEmpty()) return null
        require("_wf_id" in headers || "identifier" !in headers) {
            "Workspace upgrade required: Google Sheets V4 requires hidden _wf_id bindings instead of visible identifier columns."
        }
        val values = headers.mapIndexedNotNull { index, header ->
            header.takeIf { it.isNotBlank() }?.let { it to optString(index).trim() }
        }.toMap()
        val identifier = values["_wf_id"].orEmpty()
        val hasHumanValue = values.any { (key, value) -> key != "_wf_id" && value.isNotBlank() }
        if (identifier.isBlank() && !hasHumanValue) return null
        return GoogleSheetsWorkspaceRow(
            tab = tab,
            identifier = identifier,
            values = values,
            remoteIdentity = remoteIdentity,
        )
    }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { index -> optString(index) }

    private fun JSONArray.addAll(other: JSONArray) {
        for (index in 0 until other.length()) put(other.get(index))
    }

    private fun ByteArray.asText(): String = toString(StandardCharsets.UTF_8)

    private fun url(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun urlPath(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    companion object {
        internal val HUMAN_WORKSPACE_TABS: List<GoogleSheetsTabSchema> =
            WorkspaceGraphContract.schemas
                .filter { it.surface.visibleInSheets }
                .map { schema -> schema.toSheetSchema(hidden = false) }

        internal val IMPORT_WORKSPACE_TABS: List<GoogleSheetsTabSchema> =
            HUMAN_WORKSPACE_TABS + WorkspaceGraphContract.schema(WorkspaceGraphSurface.STOCK_LOTS)
                .toSheetSchema(title = "_wf_lots", hidden = true)

        internal val WONDERFOOD_TABS: List<GoogleSheetsTabSchema> = listOf(
            WorkspaceGraphSurface.STOCK_LOTS to "_wf_lots",
            WorkspaceGraphSurface.BINDINGS to "_wf_bindings",
            WorkspaceGraphSurface.SYSTEM to "_wf_meta",
            WorkspaceGraphSurface.NEEDS_REVIEW to "_wf_conflicts",
        ).map { (surface, title) ->
            WorkspaceGraphContract.schema(surface).toSheetSchema(title = title, hidden = true)
        }

        internal val ALL_TABS: List<GoogleSheetsTabSchema> = HUMAN_WORKSPACE_TABS + WONDERFOOD_TABS

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
        private const val DEFAULT_SPREADSHEET_TITLE = "WonderFood Household"
        private const val TIMEOUT_MILLIS = 30_000
        private const val SPENDING_CHART_END_COLUMN_INDEX = 9
        private const val SPENDING_CHART_TITLE = "Spending Trend"
        private const val WORKSPACE_VALIDATION_ROW_LIMIT = 1000
        private const val SPREADSHEET_FIELDS =
            "spreadsheetId,properties(title),namedRanges(namedRangeId,name,range),sheets(properties(sheetId,title,gridProperties(rowCount,columnCount)),developerMetadata(metadataId,metadataKey,metadataValue),protectedRanges(protectedRangeId,description,range,warningOnly),tables(tableId,name,range,columnProperties),charts(chartId,spec(title)))"
    }
}

data class GoogleSheetsCreatedSpreadsheet(
    val spreadsheetId: String,
    val spreadsheetUrl: String,
    val title: String,
)

data class GoogleSheetsSpreadsheet(
    val spreadsheetId: String,
    val title: String,
    val sheets: List<GoogleSheetsSheet>,
    val namedRanges: List<GoogleSheetsNamedRange> = emptyList(),
)

data class GoogleSheetsSheet(
    val sheetId: Int,
    val title: String,
    val rowCount: Int,
    val columnCount: Int,
    val developerMetadata: List<GoogleSheetsDeveloperMetadata> = emptyList(),
    val protectedRanges: List<GoogleSheetsProtectedRange> = emptyList(),
    val tables: List<GoogleSheetsTable> = emptyList(),
    val charts: List<GoogleSheetsChart> = emptyList(),
)

data class GoogleSheetsChart(
    val chartId: Int,
    val title: String,
)

data class GoogleSheetsNamedRange(
    val namedRangeId: String,
    val name: String,
)

data class GoogleSheetsDeveloperMetadata(
    val metadataId: Int,
    val metadataKey: String,
    val metadataValue: String,
)

data class GoogleSheetsProtectedRange(
    val protectedRangeId: Int,
    val description: String,
)

data class GoogleSheetsTable(
    val tableId: String,
    val name: String,
)

internal data class GoogleSheetsTabSchema(
    val title: String,
    val logicalTitle: String = title,
    val headers: List<String>,
    val surface: WorkspaceGraphSurface = WorkspaceGraphSurface.SYSTEM,
    val fields: List<WorkspaceGraphField> = emptyList(),
    val hidden: Boolean = false,
) {
    val relationIdFields: List<WorkspaceGraphField> = fields.filter { it.type == WorkspaceGraphValueType.RELATION }
    val hiddenColumnStartIndex: Int? = listOf(
        headers.indexOf(relationIdFields.firstOrNull()?.relationIdHeader.orEmpty()).takeIf { it >= 0 },
        headers.indexOf("_wf_id").takeIf { it >= 0 },
    ).filterNotNull().minOrNull()
    val formulaColumnIndexes: List<Int> = fields.mapIndexedNotNull { index, field ->
        index.takeIf { field.type == WorkspaceGraphValueType.COMPUTED }
    }
}

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
    val remoteIdentity: String? = null,
)

private fun String.escapeSheetName(): String = replace("'", "''")

private fun String.tableName(): String =
    replace(Regex("[^A-Za-z0-9_]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
        .let { if (it.firstOrNull()?.isLetter() == true) it else "WonderFood_$it" }

private fun relationNamedRangeName(surface: WorkspaceGraphSurface): String =
    "${surface.label.tableName()}_labels"

private fun WorkspaceGraphSurfaceSchema.toSheetSchema(
    title: String = surface.label,
    hidden: Boolean,
): GoogleSheetsTabSchema {
    val relationIdHeaders = fields
        .filter { it.type == WorkspaceGraphValueType.RELATION }
        .map { it.relationIdHeader }
    return GoogleSheetsTabSchema(
        title = title,
        logicalTitle = surface.key,
        headers = fields.map { it.label } + relationIdHeaders + listOf("_wf_id", "_wf_revision", "_wf_archived", "_wf_updated_at"),
        surface = surface,
        fields = fields,
        hidden = hidden,
    )
}

private val WorkspaceGraphField.relationIdHeader: String
    get() = "$label ID"

private fun Any?.toSheetCell(): String = when (this) {
    null -> ""
    is Double -> toString()
    is WorkspaceGraphValue.Text -> value
    is WorkspaceGraphValue.Decimal -> value.stripTrailingZeros().toPlainString()
    is WorkspaceGraphValue.MoneyValue -> majorUnits.stripTrailingZeros().toPlainString()
    is WorkspaceGraphValue.Date -> value
    is WorkspaceGraphValue.DateTime -> isoTimestamp(epochMillis)
    is WorkspaceGraphValue.BooleanValue -> value.toString()
    is WorkspaceGraphValue.TextList -> values.joinToString(", ")
    else -> toString()
}

private fun isoTimestamp(epochMillis: Long): String =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC))
