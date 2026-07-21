package com.wonderfood.app.sync

import com.wonderfood.core.data.backend.PostgresConnectionMode
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WonderFoodLiveWorkspaceProofTest {
    @Test
    fun liveNotionWorkspaceExportsSeedRowsAndReadsThemBack() {
        val token = env("NOTION_TOKEN").ifBlank { env("NOTION_API_KEY") }
        val pageId = env("NOTION_TEST_PAGE_ID")
        assumeTrue("Set NOTION_TOKEN and NOTION_TEST_PAGE_ID for live Notion proof.", token.isNotBlank() && pageId.isNotBlank())

        val gateway = NotionGateway()
        val updatedAt = now()
        val snapshot = CanonicalWorkspaceTestFixture.snapshot()

        val export = gateway.exportWorkspace(
            token = token,
            pageId = pageId,
            snapshot = snapshot,
            updatedAt = updatedAt,
        )
        val rows = gateway.readWorkspaceRows(token, pageId)

        writeEvidence(
            provider = "notion",
            payload = JSONObject()
                .put("page_id", pageId.redactedId())
                .put("created_databases", JSONArray(export.createdDatabases))
                .put("upserted_rows", export.upsertedRows)
                .put("read_rows", rows.size)
                .put("tables", JSONArray(rows.map { it.tab }.distinct()))
                .put("schema_version", WORKSPACE_GRAPH_SCHEMA_VERSION),
        )

        check(export.upsertedRows > 0) { "Notion live proof did not upsert rows." }
        check(rows.any { it.tab == "Kitchen" && it.values["Item"] == "Basmati Rice" }) { "Notion live proof did not read Kitchen rows." }
        check(rows.any { it.tab == "Recipes" && it.values["Recipe"] == "Spinach Rice Bowl" }) { "Notion live proof did not read Recipes rows." }
        check(rows.any { it.tab == "Ingredients" && it.values["Ingredient"] == "Basmati Rice" }) { "Notion live proof did not read linked Ingredients rows." }
    }

    @Test
    fun liveGoogleSheetsWorkspaceExportsSeedRowsAndReadsThemBack() {
        val token = env("GOOGLE_SHEETS_ACCESS_TOKEN")
        val spreadsheetId = env("GOOGLE_SHEETS_TEST_SPREADSHEET_ID")
        assumeTrue(
            "Set GOOGLE_SHEETS_ACCESS_TOKEN and GOOGLE_SHEETS_TEST_SPREADSHEET_ID for live Sheets proof.",
            token.isNotBlank() && spreadsheetId.isNotBlank(),
        )

        val gateway = GoogleSheetsGateway()
        val snapshot = CanonicalWorkspaceTestFixture.snapshot()

        val bootstrap = gateway.ensureWonderFoodSchema(token, spreadsheetId)
        val export = gateway.exportGraph(
            accessToken = token,
            spreadsheetId = spreadsheetId,
            snapshot = snapshot,
        )
        val rows = gateway.readWorkspaceRows(token, spreadsheetId)

        writeEvidence(
            provider = "google_sheets",
            payload = JSONObject()
                .put("spreadsheet_id", spreadsheetId.redactedId())
                .put("spreadsheet_title", bootstrap.title)
                .put("created_tabs", JSONArray(bootstrap.createdTabs))
                .put("initialized_tabs", bootstrap.initializedCount)
                .put("export_rows", export.rowCount)
                .put("read_rows", rows.size)
                .put("tables", JSONArray(rows.map { it.tab }.distinct()))
                .put("schema_version", WORKSPACE_GRAPH_SCHEMA_VERSION),
        )

        check(export.rowCount > 0) { "Sheets live proof did not export rows." }
        check(rows.any { it.tab == "Kitchen" && it.values["Item"] == "Basmati Rice" }) { "Sheets live proof did not read Kitchen rows." }
        check(rows.any { it.tab == "Recipes" && it.values["Recipe"] == "Spinach Rice Bowl" }) { "Sheets live proof did not read Recipes rows." }
        check(rows.any { it.tab == "Ingredients" && it.values["Ingredient"] == "Basmati Rice" }) { "Sheets live proof did not read Ingredients rows." }
    }

    @Test
    fun livePostgresWorkspaceExportsSeedSnapshotAndReadsItBack() {
        val endpoint = env("POSTGRES_TEST_API_ROOT").ifBlank { env("WONDERFOOD_POSTGRES_API_ROOT") }
        val token = env("POSTGRES_TEST_API_TOKEN").ifBlank { env("WONDERFOOD_POSTGRES_API_TOKEN") }
        val householdId = env("POSTGRES_TEST_HOUSEHOLD_ID").ifBlank { env("WONDERFOOD_POSTGRES_HOUSEHOLD_ID") }
        assumeTrue(
            "Set POSTGRES_TEST_API_ROOT, POSTGRES_TEST_API_TOKEN, and POSTGRES_TEST_HOUSEHOLD_ID for live Postgres proof.",
            endpoint.isNotBlank() && token.isNotBlank() && householdId.isNotBlank(),
        )

        val mode = postgresMode(endpoint)
        val gateway = PostgresGateway()
        val updatedAt = now()
        val snapshot = WonderFoodWorkspaceSeedFixture.snapshot()

        val validation = gateway.validateHostedApi(mode, endpoint, token)
        val export = gateway.exportSnapshot(
            mode = mode,
            endpoint = endpoint,
            token = token,
            householdId = householdId,
            snapshot = snapshot,
            updatedAt = updatedAt,
        )
        val remote = gateway.readRemoteSnapshot(
            mode = mode,
            endpoint = endpoint,
            token = token,
            householdId = householdId,
        )

        writeEvidence(
            provider = "postgres",
            payload = JSONObject()
                .put("mode", mode.name)
                .put("endpoint", endpoint.redactedEndpoint())
                .put("household_id", householdId.redactedId())
                .put("reachable", validation.reachable)
                .put("export_bytes", export.byteCount)
                .put("remote_updated_at", remote.updatedAt.orEmpty())
                .put("remote_schema_version", remote.snapshot?.schemaVersion ?: -1)
                .put("remote_food_count", remote.snapshot?.foods?.size ?: -1)
                .put("remote_recipe_count", remote.snapshot?.recipes?.size ?: -1)
                .put("remote_shopping_item_count", remote.snapshot?.shoppingItems?.size ?: -1)
                .put("schema_check_url", gateway.schemaCheckUrl(mode, endpoint).redactedEndpoint()),
        )

        check(validation.reachable) { "Postgres live proof did not reach the API." }
        check(export.byteCount > 0) { "Postgres live proof did not export a snapshot body." }
        val remoteSnapshot = checkNotNull(remote.snapshot) { "Postgres live proof did not read a snapshot back." }
        check(remoteSnapshot.schemaVersion == snapshot.schemaVersion) { "Postgres live proof read the wrong schema version." }
    }

    private fun writeEvidence(provider: String, payload: JSONObject) {
        val directory = File("build/evidence/live-workspace")
        directory.mkdirs()
        File(directory, "$provider-${System.currentTimeMillis()}.json").writeText(
            payload.put("provider", provider).put("captured_at", now()).toString(2),
        )
    }

    private fun env(name: String): String = System.getenv(name).orEmpty().trim()

    private fun now(): String = java.time.Instant.now().toString()

    private fun postgresMode(endpoint: String): PostgresConnectionMode {
        val configured = env("POSTGRES_TEST_CONNECTION_MODE").ifBlank { env("WONDERFOOD_POSTGRES_CONNECTION_MODE") }
        return when {
            configured.equals("POSTGREST", ignoreCase = true) -> PostgresConnectionMode.POSTGREST
            configured.equals("WONDERFOOD_SERVER", ignoreCase = true) -> PostgresConnectionMode.WONDERFOOD_SERVER
            endpoint.trimEnd('/').endsWith("/rest/v1") -> PostgresConnectionMode.POSTGREST
            else -> PostgresConnectionMode.WONDERFOOD_SERVER
        }
    }

    private fun String.redactedId(): String =
        if (length <= 8) "***" else "${take(4)}...${takeLast(4)}"

    private fun String.redactedEndpoint(): String =
        replace(Regex("://([^/@:]+):([^/@]+)@"), "://***:***@")
            .substringBefore('?')
}
