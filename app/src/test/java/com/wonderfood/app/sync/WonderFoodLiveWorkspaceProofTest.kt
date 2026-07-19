package com.wonderfood.app.sync

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
        val snapshot = WonderFoodWorkspaceSeedFixture.snapshot()

        val export = gateway.exportWorkspace(
            token = token,
            pageId = pageId,
            snapshot = snapshot,
            updatedAt = updatedAt,
        )
        val rows = gateway.readWorkspaceRows(token, pageId)
        val merge = gateway.mergeWorkspaceRows(
            pageId = pageId,
            rows = rows,
            baseSnapshot = snapshot,
            updatedAt = updatedAt,
        )

        writeEvidence(
            provider = "notion",
            payload = JSONObject()
                .put("page_id", pageId.redactedId())
                .put("created_databases", JSONArray(export.createdDatabases))
                .put("upserted_rows", export.upsertedRows)
                .put("read_rows", rows.size)
                .put("tables", JSONArray(rows.map { it.tab }.distinct()))
                .put("merge_changes", merge.merge?.changes?.size ?: 0)
                .put("merge_conflicts", merge.merge?.conflicts?.size ?: 0)
                .put(
                    "conflicts",
                    JSONArray(
                        merge.merge?.conflicts.orEmpty().map { conflict ->
                            JSONObject()
                                .put("table", conflict.table)
                                .put("identifier", conflict.identifier.redactedId())
                                .put("field", conflict.field)
                                .put("reason", conflict.reason)
                        },
                    ),
                )
                .put("merge_clock", merge.merge?.mergeClock.orEmpty()),
        )

        check(export.upsertedRows > 0) { "Notion live proof did not upsert rows." }
        check(rows.any { it.tab == WonderFoodWorkspaceSchema.KITCHEN }) { "Notion live proof did not read Kitchen rows." }
        check(rows.any { it.tab == WonderFoodWorkspaceSchema.RECIPES }) { "Notion live proof did not read Recipes rows." }
        check(rows.any { it.tab == WonderFoodWorkspaceSchema.SHOPPING }) { "Notion live proof did not read Shopping rows." }
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
        val updatedAt = now()
        val snapshot = WonderFoodWorkspaceSeedFixture.snapshot()

        val bootstrap = gateway.ensureWonderFoodSchema(token, spreadsheetId)
        val export = gateway.exportSnapshotRows(
            accessToken = token,
            spreadsheetId = spreadsheetId,
            snapshot = snapshot,
            updatedAt = updatedAt,
        )
        val rows = gateway.readWorkspaceRows(token, spreadsheetId)
        val merge = WonderFoodWorkspaceSnapshotMerger.merge(
            snapshot = snapshot,
            rows = rows,
            updatedAt = updatedAt,
        )

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
                .put("merge_changes", merge.changes.size)
                .put("merge_conflicts", merge.conflicts.size)
                .put(
                    "conflicts",
                    JSONArray(
                        merge.conflicts.map { conflict ->
                            JSONObject()
                                .put("table", conflict.table)
                                .put("identifier", conflict.identifier.redactedId())
                                .put("field", conflict.field)
                                .put("reason", conflict.reason)
                        },
                    ),
                )
                .put("merge_clock", merge.mergeClock),
        )

        check(export.rowCount > 0) { "Sheets live proof did not export rows." }
        check(rows.any { it.tab == WonderFoodWorkspaceSchema.KITCHEN }) { "Sheets live proof did not read Kitchen rows." }
        check(rows.any { it.tab == WonderFoodWorkspaceSchema.RECIPES }) { "Sheets live proof did not read Recipes rows." }
        check(rows.any { it.tab == WonderFoodWorkspaceSchema.SHOPPING }) { "Sheets live proof did not read Shopping rows." }
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

    private fun String.redactedId(): String =
        if (length <= 8) "***" else "${take(4)}...${takeLast(4)}"
}
