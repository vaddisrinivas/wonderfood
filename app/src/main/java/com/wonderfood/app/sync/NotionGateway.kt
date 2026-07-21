package com.wonderfood.app.sync

import com.wonderfood.core.model.household.HouseholdSnapshot
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

class NotionGateway : NotionWorkspaceGateway {
    override fun retrievePage(token: String, pageId: String): NotionPageAccess {
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

    override fun ensureWorkspaceDatabases(token: String, pageId: String): NotionWorkspaceProvisionResult {
        require(token.isNotBlank()) { "Notion token must not be blank." }
        require(pageId.isNotBlank()) { "Notion page ID must not be blank." }
        val workspace = ensureWorkspaceDataSourcesV4(token, pageId, emptyGraphProjection())
        return NotionWorkspaceProvisionResult(
            pageId = pageId,
            databaseIdsByTitle = workspace.sourcesBySurface.values.associate { it.title to it.databaseId },
            createdDatabases = workspace.createdSourceTitles,
        )
    }

    fun exportWorkspace(
        token: String,
        pageId: String,
        snapshot: HouseholdSnapshot,
        updatedAt: String,
    ): NotionWorkspaceExportResult =
        exportWorkspace(token = token, pageId = pageId, projection = WorkspaceGraphProjector.project(snapshot), updatedAt = updatedAt)

    internal fun exportWorkspace(
        token: String,
        pageId: String,
        projection: WorkspaceGraphProjection,
        updatedAt: String,
    ): NotionWorkspaceExportResult {
        require(token.isNotBlank()) { "Notion token must not be blank." }
        require(pageId.isNotBlank()) { "Notion page ID must not be blank." }
        require(updatedAt.isNotBlank()) { "Notion workspace updated timestamp must not be blank." }
        val workspace = ensureWorkspaceDataSourcesV4(token, pageId, projection)
        val bindingSource = requireNotNull(workspace.sourcesBySurface[WorkspaceGraphSurface.BINDINGS]) {
            "Missing Notion V4 Bindings source."
        }
        val existingBindings = readCanonicalPageBindings(token, bindingSource.dataSourceId)
        val basePages = structuredGraphPages(projection, pageIdsByCanonicalId = emptyMap(), includeRelations = false)
            .filterNot { it.surface == WorkspaceGraphSurface.BINDINGS }
        val pageIdsByCanonicalId = linkedMapOf<String, String>()
        var upserted = 0
        basePages.forEach { page ->
            val source = requireNotNull(workspace.sourcesBySurface[page.surface]) { "Missing Notion V4 source: ${page.surface.label}" }
            val pageIdForCanonical = upsertGraphPage(token, source.dataSourceId, existingBindings[page.canonicalId], page)
            pageIdsByCanonicalId[page.canonicalId] = pageIdForCanonical
            upsertBindingPage(
                token = token,
                dataSourceId = bindingSource.dataSourceId,
                existingBindingPageId = existingBindings["binding:${page.canonicalId}"],
                canonicalId = page.canonicalId,
                pageId = pageIdForCanonical,
                surface = page.surface,
            )
            upserted += 1
        }
        structuredGraphPages(projection, pageIdsByCanonicalId = pageIdsByCanonicalId, includeRelations = true)
            .filterNot { it.surface == WorkspaceGraphSurface.BINDINGS }
            .filter { page -> page.properties.values.any { property -> property is JSONObject && property.has("relation") } }
            .forEach { page ->
                val existingPageId = requireNotNull(pageIdsByCanonicalId[page.canonicalId]) { "Missing Notion page binding for ${page.surface.key}." }
                request(
                    method = "PATCH",
                    url = "$NOTION_BASE/pages/${urlPath(existingPageId)}",
                    token = token,
                    body = JSONObject().put("properties", JSONObject(page.properties.filterValues { it != JSONObject.NULL })),
                )
            }
        return NotionWorkspaceExportResult(
            pageId = pageId,
            createdDatabases = workspace.createdSourceTitles,
            upsertedRows = upserted,
        )
    }

    override fun readWorkspaceRows(token: String, pageId: String): List<GoogleSheetsWorkspaceRow> {
        require(token.isNotBlank()) { "Notion token must not be blank." }
        require(pageId.isNotBlank()) { "Notion page ID must not be blank." }
        val databases = childWorkspaceChildren(token, pageId).databasesByTitle
        val sources = v4Sources().filterNot { it.surface == WorkspaceGraphSurface.HOME }
        val pagesBySurface = sources.associate { source ->
            val databaseId = databases[source.title]
            source.surface to if (databaseId == null) emptyList() else queryDataSourcePages(token, retrieveDatabaseDataSourceId(token, databaseId))
        }
        val titleByPageId = buildMap {
            sources.forEach { source ->
                val titleLabel = source.schema.titleField.label
                pagesBySurface[source.surface].orEmpty().forEach { page ->
                    val title = page.optJSONObject("properties")?.optJSONObject(titleLabel).notionPropertyValue("title")
                    if (title.isNotBlank()) put(page.optString("id"), title)
                }
            }
        }
        val bindingSource = sources.firstOrNull { it.surface == WorkspaceGraphSurface.BINDINGS }
        val canonicalByPageId = bindingSource
            ?.let { source -> databases[source.title]?.let { retrieveDatabaseDataSourceId(token, it) } }
            ?.let { readCanonicalPageBindings(token, it) }
            .orEmpty()
            .filterKeys { !it.startsWith("binding:") }
            .entries
            .associate { (canonicalId, notionPageId) -> notionPageId to canonicalId }
        return sources
            .filter { it.surface !in setOf(WorkspaceGraphSurface.BINDINGS, WorkspaceGraphSurface.SYSTEM) }
            .flatMap { source ->
                pagesBySurface[source.surface].orEmpty().mapNotNull { page ->
                    parseV4WorkspacePage(source.schema, page, canonicalByPageId, titleByPageId)
                }
            }
    }

    private fun queryDataSourcePages(token: String, dataSourceId: String): List<JSONObject> {
        val pages = mutableListOf<JSONObject>()
        var cursor: String? = null
        do {
            val body = JSONObject().put("page_size", 100)
            cursor?.let { body.put("start_cursor", it) }
            val response = JSONObject(request("POST", dataSourceQueryUrl(dataSourceId), token, body))
            pages += response.optJSONArray("results").orEmptyObjects()
            cursor = response.optString("next_cursor").takeIf { response.optBoolean("has_more") && it.isNotBlank() && it != "null" }
        } while (cursor != null)
        return pages
    }

    private fun parseV4WorkspacePage(
        schema: WorkspaceGraphSurfaceSchema,
        page: JSONObject,
        canonicalByPageId: Map<String, String>,
        titleByPageId: Map<String, String>,
    ): GoogleSheetsWorkspaceRow? {
        val properties = page.optJSONObject("properties") ?: return null
        val values = schema.fields.associate { field ->
            val property = properties.optJSONObject(field.label)
            field.label to property.notionV4PropertyValue(field.type, titleByPageId)
        }
        if (values.values.none(String::isNotBlank)) return null
        val notionPageId = page.optString("id")
        return GoogleSheetsWorkspaceRow(
            tab = schema.surface.label,
            identifier = canonicalByPageId[notionPageId].orEmpty().ifBlank { notionPageId },
            values = values,
            remoteIdentity = "notion:page:$notionPageId",
        )
    }

    private fun JSONObject?.notionV4PropertyValue(
        type: WorkspaceGraphValueType,
        titleByPageId: Map<String, String>,
    ): String {
        if (this == null) return ""
        return when (type) {
            WorkspaceGraphValueType.TITLE -> notionPropertyValue("title")
            WorkspaceGraphValueType.TEXT, WorkspaceGraphValueType.LONG_TEXT -> notionPropertyValue("rich_text")
            WorkspaceGraphValueType.DECIMAL, WorkspaceGraphValueType.MONEY -> notionPropertyValue("number")
            WorkspaceGraphValueType.DATE, WorkspaceGraphValueType.DATE_TIME -> notionPropertyValue("date")
            WorkspaceGraphValueType.BOOLEAN -> notionPropertyValue("checkbox")
            WorkspaceGraphValueType.URL -> notionPropertyValue("url")
            WorkspaceGraphValueType.SELECT -> notionPropertyValue("select")
            WorkspaceGraphValueType.MULTI_SELECT -> optJSONArray("multi_select").orEmptyObjects().joinToString(", ") { it.optString("name") }
            WorkspaceGraphValueType.RELATION -> optJSONArray("relation").orEmptyObjects().joinToString(", ") { relation ->
                val id = relation.optString("id")
                titleByPageId[id] ?: id
            }
            WorkspaceGraphValueType.COMPUTED -> optJSONObject("formula").notionComputedValue()
                .ifBlank { optJSONObject("rollup").notionComputedValue() }
        }
    }

    private fun JSONObject?.notionComputedValue(): String {
        if (this == null) return ""
        return when (optString("type")) {
            "number" -> if (isNull("number")) "" else optDouble("number").trimNumber()
            "string" -> optString("string").takeIf { it != "null" }.orEmpty()
            "boolean" -> optBoolean("boolean").toString()
            "date" -> optJSONObject("date")?.optString("start").orEmpty()
            "array" -> optJSONArray("array").orEmptyObjects().joinToString(", ") { item ->
                item.notionComputedValue().ifBlank { item.notionTextProperty() }
            }
            else -> ""
        }
    }

    internal fun v4Sources(projection: WorkspaceGraphProjection = emptyGraphProjection()): List<NotionV4Source> =
        projection.schemas.filterNot { it.surface == WorkspaceGraphSurface.HOME }.map { schema ->
            NotionV4Source(
                surface = schema.surface,
                title = schema.surface.notionTitle(),
                schema = schema,
                primaryNavigation = schema.surface.primary,
            )
        }

    internal fun databaseContainerCreateBody(pageId: String, source: NotionV4Source): JSONObject =
        JSONObject()
            .put("parent", JSONObject().put("type", "page_id").put("page_id", pageId))
            .put("title", JSONArray().put(text(source.title)))
            .put("is_inline", true)
            .put(
                "initial_data_source",
                JSONObject()
                    .put("properties", notionBasePropertySchemas(source.schema)),
            )

    internal fun databaseContainerCreateBody(pageId: String, surface: WorkspaceGraphSurface): JSONObject =
        databaseContainerCreateBody(pageId, requireNotNull(v4Sources().firstOrNull { it.surface == surface }) { "Missing Notion V4 source: ${surface.label}" })

    internal fun dataSourceRelationFormulaPatchBody(
        source: NotionV4Source,
        dataSourceIdsBySurface: Map<WorkspaceGraphSurface, String>,
        includeField: (WorkspaceGraphField) -> Boolean = { true },
    ): JSONObject =
        JSONObject().put(
            "properties",
            JSONObject().apply {
                source.schema.fields
                    .filter { field -> field.type == WorkspaceGraphValueType.RELATION || field.type == WorkspaceGraphValueType.COMPUTED }
                    .filterNot { field -> source.surface.primary && field.hidden }
                    .filter(includeField)
                    .forEach { field ->
                        val schema = when (field.type) {
                            WorkspaceGraphValueType.RELATION -> {
                                val target = requireNotNull(field.relationTarget) { "Relation ${field.key} has no target." }
                                val targetDataSourceId = requireNotNull(dataSourceIdsBySurface[target]) {
                                    "Missing target data source for ${field.label} -> ${target.label}."
                                }
                                JSONObject().put(
                                    "relation",
                                    JSONObject()
                                        .put("data_source_id", targetDataSourceId)
                                        .put("type", "single_property")
                                        .put("single_property", JSONObject()),
                                )
                            }
                            WorkspaceGraphValueType.COMPUTED -> notionComputedPropertySchema(field)
                            else -> null
                        }
                        if (schema != null) put(field.label, schema)
                }
            },
        )

    internal fun dataSourceRelationFormulaPatchBody(surface: WorkspaceGraphSurface, dataSourceIdsBySurface: Map<WorkspaceGraphSurface, String>): JSONObject =
        dataSourceRelationFormulaPatchBody(requireNotNull(v4Sources().firstOrNull { it.surface == surface }) { "Missing Notion V4 source: ${surface.label}" }, dataSourceIdsBySurface)

    internal fun databaseDataSourceId(response: JSONObject): String =
        response.firstDataSourceIdOrNull()
            ?: error("Notion database ${response.optString("id")} has no data_sources[0].id.")

    internal fun dataSourceQueryUrl(dataSourceId: String): String =
        "$NOTION_BASE/data_sources/${urlPath(dataSourceId)}/query"

    internal fun structuredGraphPages(
        projection: WorkspaceGraphProjection,
        pageIdsByCanonicalId: Map<String, String>,
        includeRelations: Boolean = true,
    ): List<NotionGraphPage> =
        projection.schemas.filterNot { it.surface == WorkspaceGraphSurface.HOME }.flatMap { schema ->
            projection.rows[schema.surface].orEmpty().map { row ->
                NotionGraphPage(
                    surface = row.surface,
                    databaseTitle = schema.surface.notionTitle(),
                    canonicalId = row.canonicalId,
                    properties = row.toNotionProperties(schema, pageIdsByCanonicalId, includeRelations),
                )
            }
        }

    internal fun notionVersionHeader(): String = NOTION_VERSION

    internal fun homeScaffoldBody(databaseTitles: List<String>): JSONObject {
        return homeScaffoldBodyV4()
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

    private fun ensureWorkspaceDataSourcesV4(
        token: String,
        pageId: String,
        projection: WorkspaceGraphProjection,
    ): NotionV4WorkspaceProvision {
        val sources = v4Sources(projection)
        val children = childWorkspaceChildren(token, pageId)
        val created = mutableListOf<String>()
        val createdSources = linkedMapOf<WorkspaceGraphSurface, NotionProvisionedV4Source>()
        sources.forEach { source ->
            val existingDatabaseId = children.databasesByTitle[source.title]
            val provisioned = if (existingDatabaseId == null) {
                createDataSourceContainer(token, pageId, source).also { created += source.title }
            } else {
                NotionProvisionedV4Source(source.surface, source.title, existingDatabaseId, retrieveDatabaseDataSourceId(token, existingDatabaseId))
            }
            createdSources[source.surface] = provisioned
        }
        val dataSourceIdsBySurface = createdSources.mapValues { it.value.dataSourceId }
        sources.forEach { source ->
            installV4SchemaFields(
                token = token,
                provisioned = requireNotNull(createdSources[source.surface]),
                source = source,
                dataSourceIdsBySurface = dataSourceIdsBySurface,
                fields = source.schema.fields.filter { it.type == WorkspaceGraphValueType.RELATION },
                stage = "relations",
            )
        }
        sources.flatMap { source ->
            source.schema.fields
                .filter { it.type == WorkspaceGraphValueType.COMPUTED }
                .map { field -> source to field }
        }.sortedBy { (_, field) -> notionComputedDependencyOrder(field.formulaKey) }
            .forEach { (source, field) ->
                installV4SchemaFields(
                    token = token,
                    provisioned = requireNotNull(createdSources[source.surface]),
                    source = source,
                    dataSourceIdsBySurface = dataSourceIdsBySurface,
                    fields = listOf(field),
                    stage = field.label,
                )
            }
        ensureV4HomeScaffold(token, pageId, children.textMarkers)
        return NotionV4WorkspaceProvision(createdSources, created)
    }

    private fun installV4SchemaFields(
        token: String,
        provisioned: NotionProvisionedV4Source,
        source: NotionV4Source,
        dataSourceIdsBySurface: Map<WorkspaceGraphSurface, String>,
        fields: List<WorkspaceGraphField>,
        stage: String,
    ) {
        if (fields.isEmpty()) return
        runCatching {
            request(
                method = "PATCH",
                url = "$NOTION_BASE/data_sources/${urlPath(provisioned.dataSourceId)}",
                token = token,
                body = dataSourceRelationFormulaPatchBody(source, dataSourceIdsBySurface) { it in fields },
            )
        }.getOrElse { error ->
            throw IllegalStateException("Notion V4 source ${source.title} could not install $stage: ${error.message}", error)
        }
    }

    private fun createDataSourceContainer(token: String, pageId: String, source: NotionV4Source): NotionProvisionedV4Source {
        val response = JSONObject(
            request(
                method = "POST",
                url = "$NOTION_BASE/databases",
                token = token,
                body = databaseContainerCreateBody(pageId, source),
            ),
        )
        val databaseId = response.getString("id")
        val dataSourceId = response.firstDataSourceIdOrNull() ?: retrieveDatabaseDataSourceId(token, databaseId)
        return NotionProvisionedV4Source(source.surface, source.title, databaseId, dataSourceId)
    }

    private fun retrieveDatabaseDataSourceId(token: String, databaseId: String): String =
        databaseDataSourceId(JSONObject(request("GET", "$NOTION_BASE/databases/${urlPath(databaseId)}", token)))

    private fun upsertGraphPage(
        token: String,
        dataSourceId: String,
        existingPageId: String?,
        page: NotionGraphPage,
    ): String =
        if (existingPageId == null) {
            createGraphPage(token, dataSourceId, page)
        } else {
            request(
                method = "PATCH",
                url = "$NOTION_BASE/pages/${urlPath(existingPageId)}",
                token = token,
                body = JSONObject().put("properties", JSONObject(page.properties.filterValues { it != JSONObject.NULL })),
            )
            existingPageId
        }

    private fun createGraphPage(token: String, dataSourceId: String, page: NotionGraphPage): String =
        JSONObject(
            request(
                method = "POST",
                url = "$NOTION_BASE/pages",
                token = token,
                body = graphPageCreateBody(dataSourceId, page),
            ),
        ).getString("id")

    internal fun graphPageCreateBody(dataSourceId: String, page: NotionGraphPage): JSONObject =
        JSONObject()
            .put("parent", JSONObject().put("data_source_id", dataSourceId))
            .put("properties", JSONObject(page.properties.filterValues { it != JSONObject.NULL }))

    private fun readCanonicalPageBindings(token: String, dataSourceId: String): Map<String, String> {
        val bindings = linkedMapOf<String, String>()
        var cursor: String? = null
        do {
            val body = JSONObject().put("page_size", 100)
            cursor?.let { body.put("start_cursor", it) }
            val response = JSONObject(request("POST", dataSourceQueryUrl(dataSourceId), token, body))
            response.optJSONArray("results").orEmptyObjects().forEach { page ->
                val properties = page.optJSONObject("properties") ?: return@forEach
                val canonicalId = properties.optJSONObject("Canonical ID").notionTextProperty()
                val pageId = properties.optJSONObject("Page ID").notionTextProperty()
                val bindingPageId = page.optString("id")
                if (canonicalId.isNotBlank() && pageId.isNotBlank()) bindings[canonicalId] = pageId
                if (canonicalId.isNotBlank() && bindingPageId.isNotBlank()) bindings["binding:$canonicalId"] = bindingPageId
            }
            cursor = response.optString("next_cursor").takeIf { response.optBoolean("has_more") && it.isNotBlank() && it != "null" }
        } while (cursor != null)
        return bindings
    }

    private fun upsertBindingPage(
        token: String,
        dataSourceId: String,
        existingBindingPageId: String?,
        canonicalId: String,
        pageId: String,
        surface: WorkspaceGraphSurface,
    ) {
        val properties = JSONObject()
            .put("Binding", title("${surface.key}:$canonicalId".take(MAX_NOTION_TEXT_CHARS)))
            .put("Canonical ID", richText(canonicalId))
            .put("Page ID", richText(pageId))
            .put("Entity type", richText(surface.key))
            .put("Revision", richText(""))
        if (existingBindingPageId == null) {
            request(
                method = "POST",
                url = "$NOTION_BASE/pages",
                token = token,
                body = JSONObject()
                    .put("parent", JSONObject().put("data_source_id", dataSourceId))
                    .put("properties", properties),
            )
        } else {
            request(
                method = "PATCH",
                url = "$NOTION_BASE/pages/${urlPath(existingBindingPageId)}",
                token = token,
                body = JSONObject().put("properties", properties),
            )
        }
    }

    private fun ensureV4HomeScaffold(token: String, pageId: String, textMarkers: Set<String>) {
        if (textMarkers.any { it == V4_HOME_MARKER }) return
        request(
            method = "PATCH",
            url = "$NOTION_BASE/blocks/${urlPath(pageId)}/children",
            token = token,
            body = homeScaffoldBodyV4(),
        )
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

    private fun notionBasePropertySchemas(schema: WorkspaceGraphSurfaceSchema): JSONObject =
        JSONObject().apply {
            schema.fields
                .filterNot { it.type == WorkspaceGraphValueType.RELATION || it.type == WorkspaceGraphValueType.COMPUTED }
                .filterNot { field -> schema.surface.primary && field.hidden }
                .forEach { field -> put(field.label, notionBasePropertySchema(field)) }
            if (schema.surface == WorkspaceGraphSurface.BINDINGS) {
                put("Page ID", JSONObject().put("rich_text", JSONObject()))
            }
        }

    private fun notionBasePropertySchema(field: WorkspaceGraphField): JSONObject =
        when (field.type) {
            WorkspaceGraphValueType.TITLE -> JSONObject().put("title", JSONObject())
            WorkspaceGraphValueType.TEXT, WorkspaceGraphValueType.LONG_TEXT -> JSONObject().put("rich_text", JSONObject())
            WorkspaceGraphValueType.DECIMAL, WorkspaceGraphValueType.MONEY -> JSONObject().put("number", JSONObject())
            WorkspaceGraphValueType.DATE, WorkspaceGraphValueType.DATE_TIME -> JSONObject().put("date", JSONObject())
            WorkspaceGraphValueType.BOOLEAN -> JSONObject().put("checkbox", JSONObject())
            WorkspaceGraphValueType.URL -> JSONObject().put("url", JSONObject())
            WorkspaceGraphValueType.SELECT -> JSONObject().put(
                "select",
                JSONObject().apply {
                    if (field.key == "unit") {
                        put("options", JSONArray().apply {
                            (WorkspaceGraphContract.supportedUnits + "unknown").distinct().forEach { unit ->
                                put(JSONObject().put("name", unit))
                            }
                        })
                    }
                },
            )
            WorkspaceGraphValueType.MULTI_SELECT -> JSONObject().put("multi_select", JSONObject())
            WorkspaceGraphValueType.RELATION, WorkspaceGraphValueType.COMPUTED -> JSONObject()
        }

    private fun notionComputedPropertySchema(field: WorkspaceGraphField): JSONObject =
        when (val formulaKey = field.formulaKey.orEmpty()) {
            "ingredient_on_hand" -> rollup("Kitchen item", "On hand", "sum")
            "ingredient_kitchen_unit" -> rollup("Kitchen item", "Unit", "show_original")
            "recipe_ingredient_count" -> rollup("Ingredients", "Ingredient", "count")
            "recipe_ready_count" -> rollup("Ingredients", "Ready score", "sum")
            "recipe_missing_items" -> rollup("Ingredients", "Status", "show_original")
            "meal_recipe_readiness" -> rollup("Recipe", "Can make %", "show_original")
            "shopping_on_hand" -> rollup("Kitchen item", "On hand", "sum")
            "shopping_kitchen_unit" -> rollup("Kitchen item", "Unit", "show_original")
            "spending_lines_subtotal" -> rollup("Purchase lines", "Final amount", "sum")
            "spending_food_amount" -> rollup("Purchase lines", "Food amount component", "sum")
            "spending_non_food_amount" -> rollup("Purchase lines", "Non-food amount component", "sum")
            "spending_line_count" -> rollup("Purchase lines", "Line", "count")
            "purchase_line_currency" -> rollup("Purchase", "Currency", "show_original")
            else -> JSONObject().put("formula", JSONObject().put("expression", notionFormulaExpression(formulaKey)))
        }

    private fun String?.isNotionRollup(): Boolean = this in setOf(
        "ingredient_on_hand",
        "ingredient_kitchen_unit",
        "recipe_ingredient_count",
        "recipe_ready_count",
        "recipe_missing_items",
        "meal_recipe_readiness",
        "shopping_on_hand",
        "shopping_kitchen_unit",
        "spending_lines_subtotal",
        "spending_food_amount",
        "spending_non_food_amount",
        "spending_line_count",
        "purchase_line_currency",
    )

    private fun notionComputedDependencyOrder(formulaKey: String?): Int = when (formulaKey) {
        "ingredient_on_hand", "ingredient_kitchen_unit", "shopping_on_hand", "shopping_kitchen_unit",
        "purchase_line_currency", "recipe_ingredient_count", "spending_line_count", "kitchen_low_stock" -> 10
        "ingredient_missing_amount", "ingredient_required_score", "purchase_line_final_amount" -> 20
        "purchase_line_food_amount", "purchase_line_non_food_amount" -> 30
        "ingredient_status" -> 30
        "ingredient_ready_score" -> 40
        "recipe_ready_count", "recipe_missing_items", "spending_lines_subtotal", "spending_food_amount", "spending_non_food_amount" -> 50
        "recipe_can_make_percent", "shopping_still_needed", "spending_effective_total", "spending_difference" -> 60
        "meal_recipe_readiness", "meal_missing_items" -> 70
        else -> 100
    }

    private fun rollup(relation: String, property: String, function: String): JSONObject =
        JSONObject().put(
            "rollup",
            JSONObject()
                .put("relation_property_name", relation)
                .put("rollup_property_name", property)
                .put("function", function),
        )

    private fun notionFormulaExpression(formulaKey: String): String = when (formulaKey) {
        "kitchen_low_stock" -> "if(or(empty(prop(\"On hand\")), empty(prop(\"Low at\"))), false, prop(\"On hand\") <= prop(\"Low at\"))"
        "ingredient_missing_amount" -> "if(or(empty(prop(\"Amount\")), empty(prop(\"Kitchen item\"))), 0, if(prop(\"Amount\") > prop(\"On hand\"), prop(\"Amount\") - prop(\"On hand\"), 0))"
        "ingredient_status" -> "if(prop(\"Optional\"), \"Optional\", if(empty(prop(\"Kitchen item\")), \"Unlinked\", if(empty(prop(\"Amount\")), \"Check\", if(prop(\"On hand\") < prop(\"Amount\"), \"Need\", \"Have\"))))"
        "ingredient_ready_score" -> "if(prop(\"Optional\"), 1, if(empty(prop(\"Kitchen item\")), 0, if(empty(prop(\"Amount\")), 0, if(prop(\"On hand\") >= prop(\"Amount\"), 1, 0))))"
        "ingredient_required_score" -> "if(prop(\"Optional\"), 0, 1)"
        "recipe_can_make_percent" -> "if(prop(\"Ingredient count\") == 0, 0, prop(\"Ready count\") / prop(\"Ingredient count\"))"
        "meal_missing_items" -> "if(empty(prop(\"Recipe\")), \"No recipe linked\", \"Open linked Recipe for missing items\")"
        "shopping_still_needed" -> "if(empty(prop(\"Amount\")), 0, if(prop(\"Amount\") > prop(\"On hand\"), prop(\"Amount\") - prop(\"On hand\"), 0))"
        "purchase_line_final_amount" -> "if(empty(prop(\"Subtotal\")), prop(\"Quantity\") * prop(\"Unit price\"), prop(\"Subtotal\")) - if(empty(prop(\"Discount\")), 0, prop(\"Discount\")) + if(empty(prop(\"Tax\")), 0, prop(\"Tax\"))"
        "purchase_line_food_amount" -> "if(format(prop(\"Category\")) == \"food\", if(empty(prop(\"Subtotal\")), prop(\"Quantity\") * prop(\"Unit price\"), prop(\"Subtotal\")) - if(empty(prop(\"Discount\")), 0, prop(\"Discount\")) + if(empty(prop(\"Tax\")), 0, prop(\"Tax\")), 0)"
        "purchase_line_non_food_amount" -> "if(format(prop(\"Category\")) == \"food\", 0, if(empty(prop(\"Subtotal\")), prop(\"Quantity\") * prop(\"Unit price\"), prop(\"Subtotal\")) - if(empty(prop(\"Discount\")), 0, prop(\"Discount\")) + if(empty(prop(\"Tax\")), 0, prop(\"Tax\")))"
        "spending_effective_total" -> "if(empty(prop(\"Entered total\")), prop(\"Lines subtotal\"), prop(\"Entered total\"))"
        "spending_difference" -> "if(empty(prop(\"Entered total\")), 0, prop(\"Entered total\") - prop(\"Lines subtotal\"))"
        else -> "\"${formulaKey.take(80)}\""
    }

    private fun WorkspaceGraphRow.toNotionProperties(
        schema: WorkspaceGraphSurfaceSchema,
        pageIdsByCanonicalId: Map<String, String>,
        includeRelations: Boolean,
    ): Map<String, Any> =
        schema.fields
            .filterNot { field -> schema.surface.primary && field.hidden }
            .mapNotNull { field ->
                val value = values[field.key] ?: return@mapNotNull null
                if (value is WorkspaceGraphValue.Computed) return@mapNotNull null
                if (value is WorkspaceGraphValue.Relation && !includeRelations) return@mapNotNull null
                val property = value.toNotionPropertyValue(field, pageIdsByCanonicalId)
                if (property == JSONObject.NULL) null else field.label to property
            }
            .toMap()

    private fun WorkspaceGraphValue.toNotionPropertyValue(field: WorkspaceGraphField, pageIdsByCanonicalId: Map<String, String>): Any =
        when (this) {
            is WorkspaceGraphValue.Text -> when (field.type) {
                WorkspaceGraphValueType.TITLE -> title(value)
                WorkspaceGraphValueType.SELECT -> select(value)
                WorkspaceGraphValueType.URL -> url(value)
                else -> richText(value)
            }
            is WorkspaceGraphValue.Decimal -> number(value.toDouble())
            is WorkspaceGraphValue.MoneyValue -> number(majorUnits.toDouble())
            is WorkspaceGraphValue.Date -> date(value)
            is WorkspaceGraphValue.DateTime -> dateTime(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC)))
            is WorkspaceGraphValue.BooleanValue -> checkbox(value)
            is WorkspaceGraphValue.TextList -> JSONObject().put("multi_select", JSONArray().apply { values.forEach { put(JSONObject().put("name", it.take(100))) } })
            is WorkspaceGraphValue.Relation -> JSONObject().put(
                "relation",
                JSONArray().apply {
                    canonicalIds.mapNotNull(pageIdsByCanonicalId::get).forEach { pageId -> put(JSONObject().put("id", pageId)) }
                },
            )
            is WorkspaceGraphValue.Computed -> JSONObject.NULL
        }

    private fun homeScaffoldBodyV4(): JSONObject =
        JSONObject().put(
            "children",
            JSONArray()
                .put(heading("heading_1", V4_HOME_MARKER))
                .put(paragraph("Daily dashboard for food, shopping, meals, recipes, and spending."))
                .put(heading("heading_2", "Today"))
                .put(paragraph("Use the linked databases below. Formula and rollup columns update inside Notion from linked rows.")),
        )

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

    private fun JSONObject?.notionTextProperty(): String {
        if (this == null) return ""
        return optJSONArray("title").orEmptyObjects().joinToString(separator = "") { it.richTextValue() }
            .ifBlank { optJSONArray("rich_text").orEmptyObjects().joinToString(separator = "") { it.richTextValue() } }
    }

    private fun Double.trimNumber(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    private fun JSONObject.firstDataSourceIdOrNull(): String? =
        optJSONArray("data_sources")
            .orEmptyObjects()
            .firstOrNull()
            ?.optString("id")
            ?.takeIf { it.isNotBlank() }

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> =
        if (this == null) {
            emptyList()
        } else {
            List(length()) { index -> getJSONObject(index) }
        }

    private companion object {
        const val NOTION_BASE = "https://api.notion.com/v1"
        const val NOTION_VERSION = "2025-09-03"
        const val TIMEOUT_MILLIS = 30_000
        const val MAX_NOTION_TEXT_CHARS = 1_900
        const val V4_HOME_MARKER = "WonderFood Home"
        fun notionUpgradeRequiredMessage(): String =
            "Workspace upgrade required: Notion V3 snapshot export/import is disabled. Create a fresh V4 workspace from canonical HouseholdSnapshot."
    }
}

private fun WorkspaceGraphSurface.notionTitle(): String = when (this) {
    WorkspaceGraphSurface.LISTS_HELP -> "WonderFood Help & Setup"
    WorkspaceGraphSurface.INGREDIENTS -> "WonderFood Recipe Ingredients"
    WorkspaceGraphSurface.STOCK_LOTS -> "WonderFood Stock Lots"
    WorkspaceGraphSurface.BINDINGS -> "WonderFood Bindings"
    else -> "WonderFood $label"
}

private fun emptyGraphProjection(): WorkspaceGraphProjection =
    WorkspaceGraphProjection(
        schemaVersion = WORKSPACE_GRAPH_SCHEMA_VERSION,
        householdId = "00000000-0000-0000-0000-000000000000",
        defaultCurrency = "USD",
        timezone = "UTC",
        locale = "en-US",
        schemas = WorkspaceGraphContract.schemas,
        rows = WorkspaceGraphContract.schemas.associate { it.surface to emptyList() },
    )

private data class NotionWorkspaceChildren(
    val databasesByTitle: Map<String, String>,
    val textMarkers: Set<String>,
)

internal data class NotionV4Source(
    val surface: WorkspaceGraphSurface,
    val title: String,
    val schema: WorkspaceGraphSurfaceSchema,
    val primaryNavigation: Boolean,
)

internal data class NotionGraphPage(
    val surface: WorkspaceGraphSurface,
    val databaseTitle: String,
    val canonicalId: String,
    val properties: Map<String, Any>,
)

private data class NotionProvisionedV4Source(
    val surface: WorkspaceGraphSurface,
    val title: String,
    val databaseId: String,
    val dataSourceId: String,
)

private data class NotionV4WorkspaceProvision(
    val sourcesBySurface: Map<WorkspaceGraphSurface, NotionProvisionedV4Source>,
    val createdSourceTitles: List<String>,
)

data class NotionPageAccess(
    val pageId: String,
    val reachable: Boolean,
    val summary: String,
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
