package com.wonderfood.app.sync

import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class NotionGateway {
    fun retrievePage(token: String, pageId: String): NotionPageAccess {
        require(token.isNotBlank()) { "Notion token must not be blank." }
        require(pageId.isNotBlank()) { "Notion page ID must not be blank." }
        val response = request(
            method = "GET",
            url = "$NOTION_BASE/pages/${urlPath(pageId)}",
            token = token,
        )
        return NotionPageAccess(
            pageId = pageId,
            reachable = true,
            summary = response.take(2_000),
        )
    }

    fun exportSnapshot(
        token: String,
        pageId: String,
        snapshot: WonderFoodSnapshot,
        updatedAt: String,
    ): NotionSnapshotExportResult {
        require(updatedAt.isNotBlank()) { "Notion snapshot updated timestamp must not be blank." }
        val encoded = WonderFoodSnapshotCodec.encode(snapshot)
        val chunks = encoded.chunked(MAX_RICH_TEXT_CHARS)
        require(chunks.size <= MAX_APPEND_BLOCKS - 2) {
            "Notion snapshot is too large for one no-deployment export. Export ${chunks.size} chunks needs a paged sync adapter."
        }
        request(
            method = "PATCH",
            url = "$NOTION_BASE/blocks/${urlPath(pageId)}/children",
            token = token,
            body = snapshotAppendBody(snapshot, updatedAt, chunks),
        )
        return NotionSnapshotExportResult(
            pageId = pageId,
            updatedAt = updatedAt,
            chunkCount = chunks.size,
            byteCount = encoded.toByteArray(StandardCharsets.UTF_8).size,
        )
    }

    fun ensureWorkspaceDatabases(token: String, pageId: String): NotionWorkspaceProvisionResult {
        require(token.isNotBlank()) { "Notion token must not be blank." }
        require(pageId.isNotBlank()) { "Notion page ID must not be blank." }
        val children = childWorkspaceChildren(token, pageId)
        val existing = children.databasesByTitle
        val created = mutableListOf<String>()
        val databases = linkedMapOf<String, String>()
        NOTION_DATABASES.forEach { database ->
            val existingId = existing[database.title]
            val id = existingId ?: createDatabase(token, pageId, database).also {
                created += database.title
            }
            if (existingId != null) {
                repairDatabaseSchema(token, existingId, database)
            }
            databases[database.title] = id
        }
        ensureWorkspaceHomeScaffold(token, pageId, children.textMarkers, databases)
        return NotionWorkspaceProvisionResult(pageId = pageId, databaseIdsByTitle = databases, createdDatabases = created)
    }

    fun exportWorkspace(
        token: String,
        pageId: String,
        snapshot: WonderFoodSnapshot,
        updatedAt: String,
    ): NotionWorkspaceExportResult {
        val workspace = ensureWorkspaceDatabases(token, pageId)
        var upserted = 0
        structuredPages(snapshot, updatedAt).forEach { page ->
            val databaseId = requireNotNull(workspace.databaseIdsByTitle[page.databaseTitle]) {
                "Missing Notion database: ${page.databaseTitle}"
            }
            upsertWorkspacePage(token, databaseId, page)
            upserted += 1
        }
        return NotionWorkspaceExportResult(
            pageId = pageId,
            createdDatabases = workspace.createdDatabases,
            upsertedRows = upserted,
        )
    }

    fun readRemoteSnapshot(token: String, pageId: String): NotionRemoteSnapshotResult {
        require(token.isNotBlank()) { "Notion token must not be blank." }
        require(pageId.isNotBlank()) { "Notion page ID must not be blank." }
        val blocks = mutableListOf<JSONObject>()
        var cursor: String? = null
        do {
            val url = buildString {
                append("$NOTION_BASE/blocks/${urlPath(pageId)}/children?page_size=100")
                cursor?.let { append("&start_cursor=").append(urlPath(it)) }
            }
            val body = JSONObject(
                request(
                    method = "GET",
                    url = url,
                    token = token,
                ),
            )
            body.optJSONArray("results").orEmptyObjects().forEach(blocks::add)
            cursor = body.optString("next_cursor").takeIf { body.optBoolean("has_more") && it.isNotBlank() && it != "null" }
        } while (cursor != null)
        return parseRemoteSnapshot(pageId = pageId, blocks = blocks)
    }

    fun readWorkspaceRows(token: String, pageId: String): List<GoogleSheetsWorkspaceRow> {
        require(token.isNotBlank()) { "Notion token must not be blank." }
        require(pageId.isNotBlank()) { "Notion page ID must not be blank." }
        val databases = childWorkspaceChildren(token, pageId).databasesByTitle
        return WonderFoodWorkspaceSchema.tables.flatMap { table ->
            val databaseTitle = notionDatabaseTitle(table.title)
            val databaseId = databases[databaseTitle] ?: return@flatMap emptyList()
            queryWorkspaceDatabaseRows(token, databaseId, table)
        }
    }

    fun readRemoteWorkspaceDraft(token: String, pageId: String): NotionRemoteWorkspaceDraftResult {
        val rows = readWorkspaceRows(token, pageId)
        val draft = GoogleSheetsWorkspaceDraftImporter.toDraft(rows)
            ?: return NotionRemoteWorkspaceDraftResult(pageId = pageId, rowCount = rows.size, draft = null)
        return NotionRemoteWorkspaceDraftResult(pageId = pageId, rowCount = rows.size, draft = draft)
    }

    fun readRemoteWorkspaceMerge(
        token: String,
        pageId: String,
        baseSnapshot: WonderFoodSnapshot,
        updatedAt: String,
    ): NotionRemoteWorkspaceMergeResult {
        val rows = readWorkspaceRows(token, pageId)
        return mergeWorkspaceRows(pageId = pageId, rows = rows, baseSnapshot = baseSnapshot, updatedAt = updatedAt)
    }

    internal fun mergeWorkspaceRows(
        pageId: String,
        rows: List<GoogleSheetsWorkspaceRow>,
        baseSnapshot: WonderFoodSnapshot,
        updatedAt: String,
    ): NotionRemoteWorkspaceMergeResult {
        if (rows.isEmpty()) return NotionRemoteWorkspaceMergeResult(pageId = pageId, rowCount = rows.size, merge = null)
        val merge = WonderFoodWorkspaceSnapshotMerger.merge(
            snapshot = baseSnapshot,
            rows = rows,
            updatedAt = updatedAt,
        )
        if (merge.changes.isEmpty() && merge.conflicts.isEmpty()) {
            return NotionRemoteWorkspaceMergeResult(pageId = pageId, rowCount = rows.size, merge = null)
        }
        return NotionRemoteWorkspaceMergeResult(pageId = pageId, rowCount = rows.size, merge = merge)
    }

    internal fun snapshotAppendBody(
        snapshot: WonderFoodSnapshot,
        updatedAt: String,
        chunks: List<String> = WonderFoodSnapshotCodec.encode(snapshot).chunked(MAX_RICH_TEXT_CHARS),
    ): JSONObject {
        val children = JSONArray()
            .put(
                JSONObject()
                    .put("object", "block")
                    .put("type", "heading_2")
                    .put(
                        "heading_2",
                        JSONObject().put(
                            "rich_text",
                            JSONArray().put(text("WonderFood snapshot $updatedAt")),
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("object", "block")
                    .put("type", "paragraph")
                    .put(
                        "paragraph",
                        JSONObject().put(
                            "rich_text",
                            JSONArray().put(
                                text(
                                    "Schema v${snapshot.schemaVersion}; ${snapshot.foods.size} foods, ${snapshot.shoppingItems.size} shopping items, ${snapshot.recipes.size} recipes, ${snapshot.mealPlans.size} meal plans.",
                                ),
                            ),
                        ),
                    ),
            )
        chunks.forEachIndexed { index, chunk ->
            children.put(
                JSONObject()
                    .put("object", "block")
                    .put("type", "code")
                    .put(
                        "code",
                        JSONObject()
                            .put("language", "json")
                            .put("caption", JSONArray().put(text("WonderFood snapshot part ${index + 1} of ${chunks.size}")))
                            .put("rich_text", JSONArray().put(text(chunk))),
                    ),
            )
        }
        return JSONObject().put("children", children)
    }

    internal fun databaseCreateBody(pageId: String, database: NotionWorkspaceDatabase): JSONObject =
        JSONObject()
            .put("parent", JSONObject().put("type", "page_id").put("page_id", pageId))
            .put("title", JSONArray().put(text(database.title)))
            .put(
                "properties",
                JSONObject().apply {
                    database.properties.forEach { property ->
                        put(property.name, JSONObject().put(property.type, property.config))
                    }
                },
            )

    internal fun homeScaffoldBody(databaseTitles: List<String>): JSONObject {
        val children = JSONArray()
            .put(heading("heading_1", HOME_SCAFFOLD_MARKER))
            .put(paragraph("WonderFood is the fast app layer. This page is the household workspace: planning, cooking, shopping, purchases, and the managed data that keeps automation honest."))
            .put(heading("heading_2", "Today"))
            .put(bulleted("Open Home for current metrics, Kitchen for what you have, Shopping for the cart, and Recipes for what can be cooked from pantry matches."))
            .put(heading("heading_2", "Everyday databases"))
        WonderFoodWorkspaceSchema.everydayTables.forEach { table ->
            children.put(bulleted(notionDatabaseTitle(table.title)))
        }
        children.put(heading("heading_2", "Managed data"))
        databaseTitles
            .filterNot { title -> WonderFoodWorkspaceSchema.everydayTables.any { notionDatabaseTitle(it.title) == title } }
            .forEach { title -> children.put(bulleted(title)) }
        return JSONObject().put("children", children)
    }

    internal fun structuredPages(snapshot: WonderFoodSnapshot, updatedAt: String): List<NotionWorkspacePage> {
        val tablesByTitle = WonderFoodWorkspaceSchema.tables.associateBy { it.title }
        return WonderFoodWorkspaceSchema.rows(snapshot, updatedAt)
            .flatMap { (tableTitle, rows) ->
                val table = requireNotNull(tablesByTitle[tableTitle])
                rows.map { row ->
                    NotionWorkspacePage(
                        databaseTitle = notionDatabaseTitle(table.title),
                        externalId = row.identifier,
                        properties = table.fields.associate { field ->
                            field.name to row.values[field.name].toNotionProperty(field)
                        },
                    )
                }
            }
        }

    internal fun parseRemoteSnapshot(pageId: String, blocks: List<JSONObject>): NotionRemoteSnapshotResult {
        data class SnapshotGroup(
            val updatedAt: String,
            val parts: MutableMap<Int, String> = linkedMapOf(),
            var expectedParts: Int = 0,
        )

        val groups = mutableListOf<SnapshotGroup>()
        var current: SnapshotGroup? = null
        blocks.forEach { block ->
            when (block.optString("type")) {
                "heading_2" -> {
                    val heading = block.richText("heading_2")
                    val updatedAt = heading.removePrefix("WonderFood snapshot").trim()
                    current = if (heading.startsWith("WonderFood snapshot") && updatedAt.isNotBlank()) {
                        SnapshotGroup(updatedAt = updatedAt).also(groups::add)
                    } else {
                        null
                    }
                }
                "code" -> {
                    val group = current ?: return@forEach
                    val caption = block.optJSONObject("code")
                        ?.optJSONArray("caption")
                        .orEmptyObjects()
                        .joinToString(separator = "") { it.richTextValue() }
                    val match = SNAPSHOT_PART_CAPTION.matchEntire(caption.trim()) ?: return@forEach
                    val part = match.groupValues[1].toInt()
                    val total = match.groupValues[2].toInt()
                    group.expectedParts = maxOf(group.expectedParts, total)
                    group.parts[part] = block.richText("code")
                }
            }
        }

        val complete = groups.asReversed().firstOrNull { group ->
            group.expectedParts > 0 && (1..group.expectedParts).all { group.parts[it] != null }
        } ?: return NotionRemoteSnapshotResult(pageId = pageId, blockCount = blocks.size, updatedAt = null, snapshot = null)
        val encoded = (1..complete.expectedParts).joinToString(separator = "") { requireNotNull(complete.parts[it]) }
        return NotionRemoteSnapshotResult(
            pageId = pageId,
            blockCount = blocks.size,
            updatedAt = complete.updatedAt,
            snapshot = WonderFoodSnapshotCodec.decode(encoded),
        )
    }

    internal fun parseWorkspacePage(table: WorkspaceTable, page: JSONObject): GoogleSheetsWorkspaceRow? {
        val properties = page.optJSONObject("properties") ?: return null
        val values = table.fields.associate { field ->
            field.name to properties.optJSONObject(field.name).notionPropertyValue(field.notionType)
        }
        val identifier = values["identifier"].orEmpty()
        val hasHumanValue = values.any { (key, value) -> key != "identifier" && value.isNotBlank() }
        if (identifier.isBlank() && !hasHumanValue) return null
        return GoogleSheetsWorkspaceRow(
            tab = table.title,
            identifier = identifier,
            values = values,
        )
    }

    private fun request(
        method: String,
        url: String,
        token: String,
        body: JSONObject? = null,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            runCatching { setRequestMethod(method) }
                .recoverCatching { error ->
                    if (method == "PATCH" && error is ProtocolException) {
                        return requestPatchWithCurl(url = url, token = token, body = body)
                    } else {
                        throw error
                    }
                }
                .getOrThrow()
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Notion-Version", NOTION_VERSION)
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.use { it.readBytes() }?.toString(StandardCharsets.UTF_8).orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            val message = response.ifBlank { "HTTP $code" }
            error("Notion page check failed: $message")
        }
        return response
    }

    private fun requestPatchWithCurl(url: String, token: String, body: JSONObject?): String {
        val curl = runCatching {
            ProcessBuilder("bash", "-lc", "command -v curl").start()
                .inputStream.bufferedReader().readText().trim()
        }.getOrDefault("")
        require(curl.isNotBlank()) { "PATCH is unsupported on this runtime and curl is unavailable." }
        val bodyFile = File.createTempFile("wonderfood-notion-patch", ".json")
        bodyFile.writeText(body?.toString().orEmpty())
        return try {
            val process = ProcessBuilder(
                curl,
                "--silent",
                "--show-error",
                "--request",
                "PATCH",
                "--url",
                url,
                "--header",
                "Authorization: Bearer $token",
                "--header",
                "Notion-Version: $NOTION_VERSION",
                "--header",
                "Accept: application/json",
                "--header",
                "Content-Type: application/json",
                "--data-binary",
                "@${bodyFile.absolutePath}",
                "--write-out",
                "\n%{http_code}",
            ).start()
            val output = process.inputStream.use { it.readBytes() }.toString(StandardCharsets.UTF_8)
            val error = process.errorStream.use { it.readBytes() }.toString(StandardCharsets.UTF_8)
            val exit = process.waitFor()
            check(exit == 0) { error.ifBlank { "curl PATCH failed with exit $exit." } }
            val response = output.substringBeforeLast("\n")
            val code = output.substringAfterLast("\n").trim().toIntOrNull() ?: 0
            if (code !in 200..299) {
                val message = response.ifBlank { "HTTP $code" }
                error("Notion page check failed: $message")
            }
            response
        } finally {
            bodyFile.delete()
        }
    }

    private fun childWorkspaceChildren(token: String, pageId: String): NotionWorkspaceChildren {
        val databases = linkedMapOf<String, String>()
        val textMarkers = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val url = buildString {
                append("$NOTION_BASE/blocks/${urlPath(pageId)}/children?page_size=100")
                cursor?.let { append("&start_cursor=").append(urlPath(it)) }
            }
            val body = JSONObject(request("GET", url, token))
            body.optJSONArray("results").orEmptyObjects()
                .filter { it.optString("type") == "child_database" }
                .forEach { block ->
                    val title = block.optJSONObject("child_database")?.optString("title").orEmpty()
                    val id = block.optString("id")
                    if (title.isNotBlank() && id.isNotBlank()) databases[title] = id
                }
            body.optJSONArray("results").orEmptyObjects()
                .filterNot { it.optString("type") == "child_database" }
                .mapNotNullTo(textMarkers) { block -> block.blockPlainText().takeIf { it.isNotBlank() } }
            cursor = body.optString("next_cursor").takeIf { body.optBoolean("has_more") && it.isNotBlank() && it != "null" }
        } while (cursor != null)
        return NotionWorkspaceChildren(databasesByTitle = databases, textMarkers = textMarkers)
    }

    private fun ensureWorkspaceHomeScaffold(
        token: String,
        pageId: String,
        textMarkers: Set<String>,
        databaseIdsByTitle: Map<String, String>,
    ) {
        if (textMarkers.any { it == HOME_SCAFFOLD_MARKER }) return
        request(
            method = "PATCH",
            url = "$NOTION_BASE/blocks/${urlPath(pageId)}/children",
            token = token,
            body = homeScaffoldBody(databaseIdsByTitle.keys.toList()),
        )
    }

    private fun createDatabase(token: String, pageId: String, database: NotionWorkspaceDatabase): String =
        JSONObject(
            request(
                method = "POST",
                url = "$NOTION_BASE/databases",
                token = token,
                body = databaseCreateBody(pageId, database),
            ),
        ).getString("id")

    private fun repairDatabaseSchema(token: String, databaseId: String, database: NotionWorkspaceDatabase) {
        val current = JSONObject(request("GET", "$NOTION_BASE/databases/${urlPath(databaseId)}", token))
            .optJSONObject("properties") ?: JSONObject()
        val titleProperty = database.properties.firstOrNull { it.type == "title" }?.name
        val renameRequests = JSONObject()
        if (titleProperty != null && !current.has(titleProperty)) {
            val currentTitleProperty = legacyTitleField(database.title)
                ?.takeIf(current::has)
                ?: current.propertyNameByType("title")
            currentTitleProperty?.let { existingTitle ->
                renameRequests.put(existingTitle, JSONObject().put("name", titleProperty))
            }
        }
        if (renameRequests.length() > 0) {
            request(
                method = "PATCH",
                url = "$NOTION_BASE/databases/${urlPath(databaseId)}",
                token = token,
                body = JSONObject().put("properties", renameRequests),
            )
        }
        val refreshed = if (renameRequests.length() > 0) {
            JSONObject(request("GET", "$NOTION_BASE/databases/${urlPath(databaseId)}", token))
                .optJSONObject("properties") ?: JSONObject()
        } else {
            current
        }
        val missing = JSONObject()
        database.properties.forEach { property ->
            if (!refreshed.has(property.name)) {
                if (property.type != "title" || !refreshed.hasPropertyType("title")) {
                    missing.put(property.name, JSONObject().put(property.type, property.config))
                }
            } else if (property.type != "title" && refreshed.optJSONObject(property.name)?.has(property.type) != true) {
                missing.put(property.name, JSONObject().put(property.type, property.config))
            }
        }
        if (missing.length() > 0) {
            request(
                method = "PATCH",
                url = "$NOTION_BASE/databases/${urlPath(databaseId)}",
                token = token,
                body = JSONObject().put("properties", missing),
            )
        }
    }

    private fun upsertWorkspacePage(token: String, databaseId: String, page: NotionWorkspacePage) {
        val existingPageId = queryWorkspacePage(token, databaseId, page.externalId)
        val properties = JSONObject(page.properties.filterValues { it != JSONObject.NULL })
        if (existingPageId == null) {
            request(
                method = "POST",
                url = "$NOTION_BASE/pages",
                token = token,
                body = JSONObject()
                    .put("parent", JSONObject().put("database_id", databaseId))
                    .put("properties", properties),
            )
        } else {
            request(
                method = "PATCH",
                url = "$NOTION_BASE/pages/${urlPath(existingPageId)}",
                token = token,
                body = JSONObject().put("properties", properties),
            )
        }
    }

    private fun queryWorkspacePage(token: String, databaseId: String, externalId: String): String? {
        val response = JSONObject(
            request(
                method = "POST",
                url = "$NOTION_BASE/databases/${urlPath(databaseId)}/query",
                token = token,
                body = JSONObject()
                    .put("page_size", 1)
                    .put(
                        "filter",
                        JSONObject()
                            .put("property", "identifier")
                            .put("rich_text", JSONObject().put("equals", externalId)),
                    ),
            ),
        )
        return response.optJSONArray("results").orEmptyObjects().firstOrNull()?.optString("id")?.takeIf { it.isNotBlank() }
    }

    private fun queryWorkspaceDatabaseRows(
        token: String,
        databaseId: String,
        table: WorkspaceTable,
    ): List<GoogleSheetsWorkspaceRow> {
        val rows = mutableListOf<GoogleSheetsWorkspaceRow>()
        var cursor: String? = null
        do {
            val body = JSONObject().put("page_size", 100)
            cursor?.let { body.put("start_cursor", it) }
            val response = JSONObject(
                request(
                    method = "POST",
                    url = "$NOTION_BASE/databases/${urlPath(databaseId)}/query",
                    token = token,
                    body = body,
                ),
            )
            response.optJSONArray("results").orEmptyObjects()
                .mapNotNullTo(rows) { page -> parseWorkspacePage(table, page) }
            cursor = response.optString("next_cursor").takeIf { response.optBoolean("has_more") && it.isNotBlank() && it != "null" }
        } while (cursor != null)
        return rows
    }

    private fun urlPath(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun text(value: String): JSONObject =
        JSONObject()
            .put("type", "text")
            .put("text", JSONObject().put("content", value))

    private fun paragraph(value: String): JSONObject =
        JSONObject()
            .put("object", "block")
            .put("type", "paragraph")
            .put("paragraph", JSONObject().put("rich_text", JSONArray().put(text(value))))

    private fun bulleted(value: String): JSONObject =
        JSONObject()
            .put("object", "block")
            .put("type", "bulleted_list_item")
            .put("bulleted_list_item", JSONObject().put("rich_text", JSONArray().put(text(value))))

    private fun heading(type: String, value: String): JSONObject =
        JSONObject()
            .put("object", "block")
            .put("type", type)
            .put(type, JSONObject().put("rich_text", JSONArray().put(text(value))))

    private fun title(value: String): JSONObject =
        JSONObject().put("title", JSONArray().put(text(value.take(MAX_NOTION_TEXT_CHARS))))

    private fun richText(value: String): JSONObject =
        JSONObject().put("rich_text", JSONArray().put(text(value.take(MAX_NOTION_TEXT_CHARS))))

    private fun select(value: String): JSONObject =
        JSONObject().put("select", JSONObject().put("name", value.ifBlank { "UNKNOWN" }.take(100)))

    private fun number(value: Double?): Any =
        value?.let { JSONObject().put("number", it) } ?: JSONObject.NULL

    private fun date(value: String?): Any =
        value?.takeIf { it.isNotBlank() }?.let { JSONObject().put("date", JSONObject().put("start", it)) } ?: JSONObject.NULL

    private fun dateTime(value: String?): Any = date(value)

    private fun url(value: String?): Any =
        value?.takeIf { it.isNotBlank() }?.let { JSONObject().put("url", it.take(MAX_NOTION_TEXT_CHARS)) } ?: JSONObject.NULL

    private fun checkbox(value: Boolean): JSONObject =
        JSONObject().put("checkbox", value)

    private fun Any?.toNotionProperty(field: WorkspaceField): Any = when (field.notionType) {
        "title" -> title(toStringOrEmpty())
        "rich_text" -> richText(toStringOrEmpty())
        "select" -> select(toStringOrEmpty())
        "number" -> number(asDoubleOrNull())
        "date" -> date(toStringOrEmpty().takeIf { it.isNotBlank() })
        "url" -> url(toStringOrEmpty())
        "checkbox" -> checkbox(asBoolean())
        else -> richText(toStringOrEmpty())
    }

    private fun Any?.toStringOrEmpty(): String = this?.toString().orEmpty()

    private fun Any?.asBoolean(): Boolean = when (this) {
        is Boolean -> this
        is Number -> toInt() != 0
        else -> toStringOrEmpty().equals("true", ignoreCase = true)
    }

    private fun Any?.asDoubleOrNull(): Double? = when (this) {
        is Double -> this
        is Number -> toDouble()
        else -> toStringOrEmpty().toDoubleOrNull()
    }

    private fun JSONObject.richText(type: String): String =
        optJSONObject(type)
            ?.optJSONArray("rich_text")
            .orEmptyObjects()
            .joinToString(separator = "") { it.richTextValue() }

    private fun JSONObject.richTextValue(): String =
        optString("plain_text").ifBlank {
            optJSONObject("text")?.optString("content").orEmpty()
        }

    private fun JSONObject.blockPlainText(): String {
        val type = optString("type")
        val content = optJSONObject(type) ?: return ""
        return content.optJSONArray("rich_text")
            .orEmptyObjects()
            .joinToString(separator = "") { it.richTextValue() }
    }

    private fun JSONObject?.notionPropertyValue(type: String): String {
        if (this == null) return ""
        return when (type) {
            "title" -> optJSONArray("title").orEmptyObjects().joinToString(separator = "") { it.richTextValue() }
            "rich_text" -> optJSONArray("rich_text").orEmptyObjects().joinToString(separator = "") { it.richTextValue() }
            "select" -> optJSONObject("select")?.optString("name").orEmpty()
            "number" -> if (isNull("number")) "" else optDouble("number").trimNumber()
            "date" -> optJSONObject("date")?.optString("start").orEmpty()
            "url" -> optString("url").takeIf { it != "null" }.orEmpty()
            "checkbox" -> optBoolean("checkbox", false).toString()
            else -> optString(type).takeIf { it != "null" }.orEmpty()
        }
    }

    private fun Double.trimNumber(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    private fun JSONObject.hasPropertyType(type: String): Boolean =
        keys().asSequence().any { key -> optJSONObject(key)?.has(type) == true }

    private fun JSONObject.propertyNameByType(type: String): String? =
        keys().asSequence().firstOrNull { key -> optJSONObject(key)?.has(type) == true }

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> =
        if (this == null) {
            emptyList()
        } else {
            List(length()) { index -> getJSONObject(index) }
        }

    private companion object {
        const val NOTION_BASE = "https://api.notion.com/v1"
        const val NOTION_VERSION = "2022-06-28"
        const val TIMEOUT_MILLIS = 30_000
        const val MAX_RICH_TEXT_CHARS = 1_800
        const val MAX_APPEND_BLOCKS = 100
        const val MAX_NOTION_TEXT_CHARS = 1_900
        const val HOME_SCAFFOLD_MARKER = "WonderFood Home"
        fun notionDatabaseTitle(tableTitle: String): String = "WonderFood $tableTitle"
        fun legacyTitleField(databaseTitle: String): String? = when (databaseTitle) {
            "WonderFood Kitchen" -> "Item"
            "WonderFood Shopping" -> "Item"
            "WonderFood Meals" -> "Meal"
            "WonderFood Plans" -> "Plan"
            "WonderFood Recipes" -> "Recipe"
            "WonderFood Purchases" -> "Purchase"
            else -> null
        }
        val SNAPSHOT_PART_CAPTION = Regex("""WonderFood snapshot part (\d+) of (\d+)""")
        val NOTION_DATABASES = WonderFoodWorkspaceSchema.tables.map { table ->
            NotionWorkspaceDatabase(
                title = notionDatabaseTitle(table.title),
                properties = table.fields.map { field -> NotionProperty(field.name, field.notionType) },
            )
        }
    }
}

private data class NotionWorkspaceChildren(
    val databasesByTitle: Map<String, String>,
    val textMarkers: Set<String>,
)

data class NotionProperty(
    val name: String,
    val type: String,
    val config: JSONObject = JSONObject(),
)

data class NotionWorkspaceDatabase(
    val title: String,
    val properties: List<NotionProperty>,
)

data class NotionWorkspacePage(
    val databaseTitle: String,
    val externalId: String,
    val properties: Map<String, Any>,
)

data class NotionPageAccess(
    val pageId: String,
    val reachable: Boolean,
    val summary: String,
)

data class NotionSnapshotExportResult(
    val pageId: String,
    val updatedAt: String,
    val chunkCount: Int,
    val byteCount: Int,
)

data class NotionRemoteSnapshotResult(
    val pageId: String,
    val blockCount: Int,
    val updatedAt: String?,
    val snapshot: WonderFoodSnapshot?,
)

data class NotionRemoteWorkspaceDraftResult(
    val pageId: String,
    val rowCount: Int,
    val draft: com.wonderfood.app.data.FoodDraft?,
)

data class NotionRemoteWorkspaceMergeResult(
    val pageId: String,
    val rowCount: Int,
    val merge: WorkspaceMergeResult?,
)

data class NotionWorkspaceProvisionResult(
    val pageId: String,
    val databaseIdsByTitle: Map<String, String>,
    val createdDatabases: List<String>,
)

data class NotionWorkspaceExportResult(
    val pageId: String,
    val createdDatabases: List<String>,
    val upsertedRows: Int,
)
