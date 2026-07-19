package com.wonderfood.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleSheetsGatewayTest {
    @Test
    fun wonderFoodSchemaIncludesPlanTabs() {
        val tabNames = GoogleSheetsGateway.WONDERFOOD_TABS.map { it.title }
        val logicalNames = GoogleSheetsGateway.WONDERFOOD_TABS.map { it.logicalTitle }

        assertTrue("_meta" in tabNames)
        assertTrue("_wf_foods" in tabNames)
        assertTrue("_wf_stock_lots" in tabNames)
        assertTrue("_wf_recipes" in tabNames)
        assertTrue("_wf_meal_plans" in tabNames)
        assertTrue("_wf_shopping_items" in tabNames)
        assertTrue("foods" in logicalNames)
        assertTrue("stock_lots" in logicalNames)
        assertTrue("recipes" in logicalNames)
        assertTrue("meal_plans" in logicalNames)
        assertTrue("shopping_items" in logicalNames)
    }

    @Test
    fun humanWorkspaceSchemaIncludesUsefulFamilyTabs() {
        val tabNames = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.map { it.title }

        assertEquals(
            listOf(
                "Home",
                "Kitchen",
                "Recipes",
                "Meals",
                "Plans",
                "Shopping",
                "Purchases",
                "Goals",
                "Foods",
                "Products",
                "Recipe Ingredients",
                "Recipe Revisions",
                "Inventory Activity",
                "Shopping Demand",
                "Purchase Lines",
                "Nutrition Facts",
                "Members",
                "Activity",
                "Workspace",
            ),
            tabNames,
        )
        assertEquals(
            listOf("Food", "On hand", "Unit", "Pantry state", "Location", "Best by", "Food ID", "Product ID", "Lot ID", "Source", "Updated", "identifier"),
            GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.first { it.title == "Kitchen" }.headers,
        )
        assertEquals(
            listOf("Profile", "Subject type", "Subject ID", "Basis", "Basis quantity", "Basis unit", "Calories", "Protein g", "Carbs g", "Fat g", "Fiber g", "Sugar g", "Sodium mg", "Source", "Confidence", "Updated", "identifier"),
            GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.first { it.title == "Nutrition Facts" }.headers,
        )
    }

    @Test
    fun tabSchemasUseStableSystemColumns() {
        val headers = GoogleSheetsGateway.WONDERFOOD_TABS.first { it.logicalTitle == "foods" }.headers

        assertEquals(listOf("id", "version", "updated_at", "archived_at", "payload_json"), headers)
    }

    @Test
    fun schemaIncludesSnapshotRelationAndAttachmentTabs() {
        val tabNames = GoogleSheetsGateway.WONDERFOOD_TABS.map { it.title }
        val logicalNames = GoogleSheetsGateway.WONDERFOOD_TABS.map { it.logicalTitle }

        assertTrue("_meta" in tabNames)
        assertTrue("_wf_relations" in tabNames)
        assertTrue("_wf_attachments" in tabNames)
        assertTrue("relations" in logicalNames)
        assertTrue("attachments" in logicalNames)
    }

    @Test
    fun columnNameSupportsWideHeaderRanges() {
        assertEquals("A", GoogleSheetsGateway.columnName(1))
        assertEquals("Z", GoogleSheetsGateway.columnName(26))
        assertEquals("AA", GoogleSheetsGateway.columnName(27))
    }

    @Test
    fun workspacePresentationHidesRawSyncTabsAndPolishesHumanTabs() {
        val spreadsheet = GoogleSheetsSpreadsheet(
            spreadsheetId = "sheet-1",
            title = "WonderFood",
            sheets = listOf(
                GoogleSheetsSheet(sheetId = 1, title = "_meta", rowCount = 1, columnCount = 5),
                GoogleSheetsSheet(sheetId = 2, title = "Home", rowCount = 1, columnCount = 5),
            ),
        )

        val requests = GoogleSheetsGateway().workspacePresentationRequests(
            spreadsheet = spreadsheet,
            provisionSystemForTitles = setOf("_meta", "Home"),
            createTablesForTitles = setOf("Home"),
        )

        val rawSheetUpdate = requests.getJSONObject(0).getJSONObject("updateSheetProperties")
        val homeSheetUpdate = requests.getJSONObject(6).getJSONObject("updateSheetProperties")

        assertTrue(rawSheetUpdate.getJSONObject("properties").getBoolean("hidden"))
        assertEquals(1, rawSheetUpdate.getJSONObject("properties").getJSONObject("gridProperties").getInt("frozenRowCount"))
        assertEquals(false, homeSheetUpdate.getJSONObject("properties").getBoolean("hidden"))
        assertTrue(requests.toString().contains("\"setBasicFilter\""))
        assertTrue(requests.toString().contains("\"autoResizeDimensions\""))
        assertTrue(requests.toString().contains("\"addTable\""))
        assertTrue(requests.toString().contains("\"name\":\"Home\""))
        assertTrue(requests.toString().contains("\"addProtectedRange\""))
        assertTrue(requests.toString().contains("\"warningOnly\":true"))
        assertTrue(requests.toString().contains("\"createDeveloperMetadata\""))
        assertTrue(requests.toString().contains("\"metadataKey\":\"wonderfood.table\""))
        val homeMetadata = requests.getJSONObject(12)
            .getJSONObject("createDeveloperMetadata")
            .getJSONObject("developerMetadata")
        assertEquals("wonderfood.table", homeMetadata.getString("metadataKey"))
        assertEquals("workspace", JSONObject(homeMetadata.getString("metadataValue")).getString("kind"))
    }

    @Test
    fun emptyWorkspaceRowsStillProvideDashboardMetrics() {
        val rows = GoogleSheetsGateway().workspaceRows(emptySnapshot(), "2026-07-19T12:00:00Z")

        assertEquals(emptyList<List<String>>(), rows.getValue("Kitchen"))
        assertEquals("Kitchen items", rows.getValue("Home").first()[0])
        assertEquals("0", rows.getValue("Home").first()[1])
        assertEquals("Current", rows.getValue("Home").first()[2])
        assertEquals("home-kitchen-items", rows.getValue("Home").first()[4])
        assertEquals("=SUM(Purchases!E2:E)", rows.getValue("Home")[5][1])
        assertEquals("3", rows.getValue("Workspace").first()[1])
    }

    private fun emptySnapshot() = com.wonderfood.core.model.WonderFoodSnapshot(
        schemaVersion = com.wonderfood.core.model.WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION,
        pages = emptyList(),
        foods = emptyList(),
        foodAliases = emptyList(),
        stockLots = emptyList(),
        nutritionSnapshots = emptyList(),
        recipes = emptyList(),
        mealPlans = emptyList(),
        mealLogs = emptyList(),
        shoppingItems = emptyList(),
        receipts = emptyList(),
        foodEvents = emptyList(),
        relations = emptyList(),
        attachments = emptyList(),
    )
}
