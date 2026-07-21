package com.wonderfood.app.sync

import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleSheetsGatewayTest {
    @Test
    fun v4SchemaUsesApprovedVisibleAndSupportTabsOnly() {
        assertEquals(
            listOf("Home", "Kitchen", "Shopping", "Meals", "Recipes", "Ingredients", "Spending", "Purchase Lines", "Lists & Help"),
            GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.map { it.title },
        )
        assertEquals(
            listOf("_wf_lots", "_wf_bindings", "_wf_meta", "_wf_conflicts"),
            GoogleSheetsGateway.WONDERFOOD_TABS.map { it.title },
        )

        val allNames = GoogleSheetsGateway.ALL_TABS.map { it.title }
        assertFalse("_meta" in allNames)
        assertFalse("_wf_foods" in allNames)
        assertFalse("Purchases" in allNames)
        assertFalse("Recipe Ingredients" in allNames)
    }

    @Test
    fun v4KitchenAndIngredientHeadersHideBindingsWithoutShowingLegacyIds() {
        val kitchen = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.first { it.title == "Kitchen" }
        val ingredients = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.first { it.title == "Ingredients" }

        assertEquals(
            listOf(
                "Item", "Kind", "Category", "On hand", "Unit", "Location", "Best before", "Opened", "Low at", "Low stock",
                "Buy next", "Buy quantity", "Preferred store", "Notes", "Archived", "Ingredients", "Shopping", "Stock lots",
                "Ingredients ID", "Shopping ID", "Stock lots ID",
                "_wf_id", "_wf_revision", "_wf_archived", "_wf_updated_at",
            ),
            kitchen.headers,
        )
        assertEquals("Ingredient", ingredients.headers.first())
        assertTrue("Amount" in ingredients.headers)
        assertTrue("Unit" in ingredients.headers)
        assertTrue("Kitchen item" in ingredients.headers)
        assertTrue("_wf_id" in ingredients.headers)
        assertFalse("Food ID" in kitchen.headers)
        assertFalse("Lot ID" in kitchen.headers)
        assertFalse("identifier" in kitchen.headers)
    }

    @Test
    fun presentationCreatesTablesValidationMetadataHiddenBindingsAndFormulaProtection() {
        val spreadsheet = GoogleSheetsSpreadsheet(
            spreadsheetId = "sheet-1",
            title = "WonderFood",
            sheets = GoogleSheetsGateway.ALL_TABS.mapIndexed { index, tab ->
                GoogleSheetsSheet(sheetId = index + 1, title = tab.title, rowCount = 1, columnCount = tab.headers.size)
            },
        )

        val requests = GoogleSheetsGateway().workspacePresentationRequests(
            spreadsheet = spreadsheet,
            provisionSystemForTitles = GoogleSheetsGateway.ALL_TABS.map { it.title }.toSet(),
            createTablesForTitles = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.map { it.title }.toSet(),
        )
        val entries = List(requests.length()) { requests.getJSONObject(it) }

        assertTrue(entries.any { it.requestType() == "addTable" })
        assertTrue(entries.any { it.requestType() == "setDataValidation" })
        assertTrue(entries.any { it.requestType() == "updateDimensionProperties" })
        assertTrue(
            entries
                .filter { it.requestType() == "addProtectedRange" }
                .any { it.requestBody("addProtectedRange").getJSONObject("protectedRange").getString("description").contains("formula columns") },
        )
        val tableColumns = entries
            .filter { it.requestType() == "addTable" }
            .flatMap { request ->
                request.requestBody("addTable").getJSONObject("table").getJSONArray("columnProperties").let { columns ->
                    List(columns.length()) { index -> columns.getJSONObject(index) }
                }
            }
        assertTrue(
            tableColumns
                .filter { it.optString("columnType") == "DROPDOWN" }
                .all { it.getJSONObject("dataValidationRule").getJSONObject("condition").getString("type") == "ONE_OF_LIST" },
        )
        assertTrue(tableColumns.any { it.getString("columnName") == "Kitchen item" && !it.has("columnType") })
        val unitColumn = tableColumns.first { it.getString("columnName") == "Unit" && it.optString("columnType") == "DROPDOWN" }
        assertTrue(unitColumn.getJSONObject("dataValidationRule").toString().contains("fluid_ounce"))

        val hiddenLotUpdate = entries
            .first { it.requestType() == "updateSheetProperties" && it.requestBody("updateSheetProperties").getJSONObject("properties").getInt("sheetId") == 10 }
            .requestBody("updateSheetProperties")
        assertTrue(hiddenLotUpdate.getJSONObject("properties").getBoolean("hidden"))

        val homeMetadata = entries
            .filter { it.requestType() == "createDeveloperMetadata" }
            .map { it.requestBody("createDeveloperMetadata").getJSONObject("developerMetadata") }
            .first { JSONObject(it.getString("metadataValue")).getString("title") == "Home" }
        val metadataValue = JSONObject(homeMetadata.getString("metadataValue"))
        assertEquals("workspace", metadataValue.getString("kind"))
        assertEquals(WORKSPACE_GRAPH_SCHEMA_VERSION, metadataValue.getInt("schemaVersion"))
        assertEquals("home", metadataValue.getString("surface"))
    }

    @Test
    fun relationColumnsUseStrictOneOfRangeValidationBackedByNamedLabels() {
        val spreadsheet = GoogleSheetsSpreadsheet(
            spreadsheetId = "sheet-1",
            title = "WonderFood",
            sheets = GoogleSheetsGateway.ALL_TABS.mapIndexed { index, tab ->
                GoogleSheetsSheet(sheetId = index + 1, title = tab.title, rowCount = 100, columnCount = tab.headers.size)
            },
        )

        val requests = GoogleSheetsGateway().workspacePresentationRequests(
            spreadsheet = spreadsheet,
            provisionSystemForTitles = emptySet(),
            createTablesForTitles = emptySet(),
            includeCharts = false,
        )
        val entries = List(requests.length()) { requests.getJSONObject(it) }
        val relationValidation = entries
            .filter { it.requestType() == "setDataValidation" }
            .map { it.requestBody("setDataValidation") }
            .first { request ->
                request.getJSONObject("rule").getJSONObject("condition").optString("type") == "ONE_OF_RANGE" &&
                    request.getJSONObject("rule").getJSONObject("condition").getJSONArray("values").toString().contains("Kitchen_labels")
            }

        assertTrue(relationValidation.getJSONObject("rule").getBoolean("strict"))
        assertTrue(
            entries
                .filter { it.requestType() == "addNamedRange" }
                .map { it.requestBody("addNamedRange").getJSONObject("namedRange").getString("name") }
                .contains("Kitchen_labels"),
        )
    }

    @Test
    fun presentationRepairsExistingTablesNamedRangesMetadataAndProtectionsWithoutDuplicating() {
        val kitchen = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.first { it.title == "Kitchen" }
        val metadataValue = JSONObject()
            .put("title", kitchen.title)
            .put("kind", "workspace")
            .put("schemaVersion", WORKSPACE_GRAPH_SCHEMA_VERSION)
            .put("surface", kitchen.surface.key)
            .put("headers", JSONArray(kitchen.headers))
            .toString()
        val spreadsheet = GoogleSheetsSpreadsheet(
            spreadsheetId = "sheet-1",
            title = "WonderFood",
            namedRanges = listOf(
                GoogleSheetsNamedRange("named-kitchen-data", "Kitchen_data"),
                GoogleSheetsNamedRange("named-kitchen-labels", "Kitchen_labels"),
            ),
            sheets = listOf(
                GoogleSheetsSheet(
                    sheetId = 7,
                    title = "Kitchen",
                    rowCount = 50,
                    columnCount = kitchen.headers.size,
                    developerMetadata = listOf(GoogleSheetsDeveloperMetadata(11, "wonderfood.table", metadataValue)),
                    protectedRanges = listOf(
                        GoogleSheetsProtectedRange(21, "WonderFood system columns. Edit only if you know what you are changing."),
                        GoogleSheetsProtectedRange(22, "WonderFood formula columns are automatically repaired."),
                    ),
                    tables = listOf(GoogleSheetsTable("tbl-kitchen", "Kitchen")),
                ),
            ),
        )

        val requests = GoogleSheetsGateway().workspacePresentationRequests(
            spreadsheet = spreadsheet,
            provisionSystemForTitles = setOf("Kitchen"),
            createTablesForTitles = setOf("Kitchen"),
            includeCharts = false,
        )
        val entries = List(requests.length()) { requests.getJSONObject(it) }

        assertFalse(entries.any { it.requestType() == "addTable" })
        assertFalse(entries.any { it.requestType() == "addProtectedRange" })
        assertFalse(entries.any { it.requestType() == "createDeveloperMetadata" })
        assertTrue(entries.any { it.requestType() == "updateTable" })
        assertEquals(2, entries.count { it.requestType() == "updateNamedRange" })
        assertTrue(entries.any { it.requestType() == "updateProtectedRange" })
    }

    @Test
    fun presentationDoesNotDuplicateExistingSpendingChart() {
        val home = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.first { it.title == "Home" }
        val spreadsheet = GoogleSheetsSpreadsheet(
            spreadsheetId = "sheet-1",
            title = "WonderFood",
            sheets = listOf(
                GoogleSheetsSheet(
                    sheetId = 1,
                    title = "Home",
                    rowCount = 100,
                    columnCount = home.headers.size,
                    charts = listOf(GoogleSheetsChart(42, "Spending Trend")),
                ),
            ),
        )

        val requests = GoogleSheetsGateway().workspacePresentationRequests(
            spreadsheet = spreadsheet,
            provisionSystemForTitles = emptySet(),
            createTablesForTitles = emptySet(),
        )
        val entries = List(requests.length()) { requests.getJSONObject(it) }

        assertFalse(entries.any { it.requestType() == "addChart" })
    }

    @Test
    fun tableRangeRepairExpandsExistingTablesToWrittenRows() {
        val projection = sampleProjection()
        val spreadsheet = GoogleSheetsSpreadsheet(
            spreadsheetId = "sheet-1",
            title = "WonderFood",
            sheets = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.mapIndexed { index, tab ->
                GoogleSheetsSheet(
                    sheetId = index + 1,
                    title = tab.title,
                    rowCount = 2,
                    columnCount = tab.headers.size,
                    tables = listOf(GoogleSheetsTable("table-${tab.title}", tab.title.replace(" ", "_").replace("&", "_").replace(Regex("_+"), "_").trim('_'))),
                )
            },
        )

        val requests = GoogleSheetsGateway().tableRangeRepairRequests(spreadsheet, projection)
        val entries = List(requests.length()) { requests.getJSONObject(it) }
            .filter { it.requestType() == "updateTable" }
            .map { it.requestBody("updateTable") }
        val ingredients = entries.first { it.getJSONObject("table").getString("tableId") == "table-Ingredients" }
        val range = ingredients.getJSONObject("table").getJSONObject("range")

        assertEquals(0, range.getInt("startRowIndex"))
        assertEquals(2, range.getInt("endRowIndex"))
        assertEquals("range", ingredients.getString("fields"))
    }

    @Test
    fun graphRowsUseStructuredIngredientQuantitiesRelationsAndBoundedFormulas() {
        val rows = GoogleSheetsGateway().workspaceRows(sampleProjection())
        val ingredientHeaders = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.first { it.title == "Ingredients" }.headers
        val recipeHeaders = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.first { it.title == "Recipes" }.headers
        val spendingHeaders = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.first { it.title == "Spending" }.headers

        val ingredient = rows.getValue("Ingredients").single()
        assertEquals("Basmati Rice", ingredient[ingredientHeaders.indexOf("Ingredient")])
        assertEquals("Spinach Rice Bowl", ingredient[ingredientHeaders.indexOf("Recipe")])
        assertEquals("Basmati Rice", ingredient[ingredientHeaders.indexOf("Kitchen item")])
        assertEquals("1", ingredient[ingredientHeaders.indexOf("Amount")])
        assertEquals("cup", ingredient[ingredientHeaders.indexOf("Unit")])
        assertFalse(ingredient.joinToString("|").contains("1 cup 1 cup"))
        assertFalse(ingredient.joinToString("|").contains("legacy:"))
        assertEquals("recipe-bowl", ingredient[ingredientHeaders.indexOf("Recipe ID")])
        assertEquals("kitchen-rice", ingredient[ingredientHeaders.indexOf("Kitchen item ID")])

        val statusFormula = ingredient[ingredientHeaders.indexOf("Status")]
        assertTrue(statusFormula.startsWith("=IF("))
        assertTrue(statusFormula.contains("Unlinked"))
        assertTrue(statusFormula.contains("Convert"))
        assertTrue(statusFormula.contains("Have"))

        val recipe = rows.getValue("Recipes").single()
        val ingredientRecipeIdColumn = GoogleSheetsGateway.columnName(ingredientHeaders.indexOf("Recipe ID") + 1)
        assertTrue(recipe[recipeHeaders.indexOf("Missing items")].contains("TEXTJOIN"))
        assertTrue(recipe[recipeHeaders.indexOf("Missing items")].contains("'Ingredients'!$${ingredientRecipeIdColumn}$2:"))

        val spending = rows.getValue("Spending").single()
        val purchaseLineHeaders = GoogleSheetsGateway.HUMAN_WORKSPACE_TABS.first { it.title == "Purchase Lines" }.headers
        val purchaseIdColumn = GoogleSheetsGateway.columnName(purchaseLineHeaders.indexOf("Purchase ID") + 1)
        assertTrue(spending[spendingHeaders.indexOf("Lines subtotal")].contains("SUMIFS"))
        assertTrue(spending[spendingHeaders.indexOf("Lines subtotal")].contains("'Purchase Lines'!$${purchaseIdColumn}$2:"))
        assertFalse(rows.values.flatten().flatten().any { it == "=SUM(Purchases!F2:F)" })
    }

    @Test
    fun batchUpdateUsesAllV4TabsAndNeverClearsBeyondSchemaColumns() {
        val gateway = GoogleSheetsGateway()
        val clearRanges = gateway.clearRangesFor(GoogleSheetsGateway.ALL_TABS)
        val data = gateway.workspaceBatchUpdateData(sampleProjection())

        assertEquals(GoogleSheetsGateway.ALL_TABS.size, data.length())
        assertTrue(clearRanges.any { it.startsWith("'Purchase Lines'!A2:") })
        assertTrue(clearRanges.any { it.startsWith("'_wf_bindings'!A2:") })
        assertFalse(clearRanges.any { it.contains("Z") || it.contains("AA") })

        val ingredientsData = List(data.length()) { data.getJSONObject(it) }
            .first { it.getString("range").startsWith("'Ingredients'!") }
        assertTrue(ingredientsData.getString("range").contains("A2:"))
        assertEquals("ROWS", ingredientsData.getString("majorDimension"))
    }

    @Test
    fun visibleIdentifierColumnFailsAsUpgradeRequired() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            GoogleSheetsGateway().run {
                JSONArray(listOf("Milk", "legacy:stock_lot:inventory:1"))
                    .toWorkspaceRow("Kitchen", listOf("Item", "identifier"))
            }
        }

        assertTrue(error.message.orEmpty().contains("Workspace upgrade required"))
    }

    @Test
    fun workspaceRowImportReadsHiddenV4BindingAfterColumnMoveAndKeepsUserColumns() {
        val headers = listOf("Notes", "Item", "_wf_id", "Household Notes")
        val movedRow = JSONArray(listOf("Use first", "Milk", "kitchen-milk", "Aisle 3"))

        val row = GoogleSheetsGateway().run { movedRow.toWorkspaceRow("Kitchen", headers) }

        requireNotNull(row)
        assertEquals("Kitchen", row.tab)
        assertEquals("kitchen-milk", row.identifier)
        assertEquals("Milk", row.values.getValue("Item"))
        assertEquals("Aisle 3", row.values.getValue("Household Notes"))
    }

    @Test
    fun columnNameSupportsWideHeaderRanges() {
        assertEquals("A", GoogleSheetsGateway.columnName(1))
        assertEquals("Z", GoogleSheetsGateway.columnName(26))
        assertEquals("AA", GoogleSheetsGateway.columnName(27))
    }

    @Test
    fun createSpreadsheetRequestUsesWonderFoodTitle() {
        val body = GoogleSheetsGateway().createSpreadsheetRequestBody(" WonderFood Family ")

        assertEquals("WonderFood Family", body.getJSONObject("properties").getString("title"))
        assertEquals("Home", body.getJSONArray("sheets").getJSONObject(0).getJSONObject("properties").getString("title"))
    }

    private fun sampleProjection(): WorkspaceGraphProjection {
        val updatedAt = 1_784_572_800_000L
        fun row(surface: WorkspaceGraphSurface, id: String, values: Map<String, WorkspaceGraphValue>) =
            WorkspaceGraphRow(surface, id, revision = 7, archived = false, updatedAt = updatedAt, values = values)

        val kitchen = row(
            WorkspaceGraphSurface.KITCHEN,
            "kitchen-rice",
            mapOf(
                "item" to WorkspaceGraphValue.Text("Basmati Rice"),
                "kind" to WorkspaceGraphValue.Text("Food"),
                "category" to WorkspaceGraphValue.Text("grain"),
                "on_hand" to WorkspaceGraphValue.Decimal(BigDecimal("2")),
                "unit" to WorkspaceGraphValue.Text("cup"),
                "low_at" to WorkspaceGraphValue.Decimal(BigDecimal("1")),
                "low_stock" to WorkspaceGraphValue.Computed("kitchen_low_stock"),
                "archived" to WorkspaceGraphValue.BooleanValue(false),
                "ingredients" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.INGREDIENTS, listOf("ingredient-rice")),
            ),
        )
        val ingredient = row(
            WorkspaceGraphSurface.INGREDIENTS,
            "ingredient-rice",
            mapOf(
                "ingredient" to WorkspaceGraphValue.Text("Basmati Rice"),
                "recipe" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.RECIPES, listOf("recipe-bowl")),
                "kitchen_item" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.KITCHEN, listOf("kitchen-rice")),
                "amount" to WorkspaceGraphValue.Decimal(BigDecimal("1")),
                "unit" to WorkspaceGraphValue.Text("cup"),
                "optional" to WorkspaceGraphValue.BooleanValue(false),
                "on_hand" to WorkspaceGraphValue.Computed("ingredient_on_hand"),
                "kitchen_unit" to WorkspaceGraphValue.Computed("ingredient_kitchen_unit"),
                "missing_amount" to WorkspaceGraphValue.Computed("ingredient_missing_amount"),
                "status" to WorkspaceGraphValue.Computed("ingredient_status"),
                "archived" to WorkspaceGraphValue.BooleanValue(false),
            ),
        )
        val recipe = row(
            WorkspaceGraphSurface.RECIPES,
            "recipe-bowl",
            mapOf(
                "recipe" to WorkspaceGraphValue.Text("Spinach Rice Bowl"),
                "servings" to WorkspaceGraphValue.Decimal(BigDecimal("2")),
                "instructions" to WorkspaceGraphValue.Text("Cook rice. Fold in spinach."),
                "archived" to WorkspaceGraphValue.BooleanValue(false),
                "ingredients" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.INGREDIENTS, listOf("ingredient-rice")),
                "ingredient_count" to WorkspaceGraphValue.Computed("recipe_ingredient_count"),
                "ready_count" to WorkspaceGraphValue.Computed("recipe_ready_count"),
                "can_make_percent" to WorkspaceGraphValue.Computed("recipe_can_make_percent"),
                "missing_items" to WorkspaceGraphValue.Computed("recipe_missing_items"),
            ),
        )
        val spending = row(
            WorkspaceGraphSurface.SPENDING,
            "purchase-1",
            mapOf(
                "purchase" to WorkspaceGraphValue.Text("Market purchase"),
                "merchant" to WorkspaceGraphValue.Text("Market"),
                "currency" to WorkspaceGraphValue.Text("USD"),
                "entered_total" to WorkspaceGraphValue.MoneyValue(BigDecimal("7.50"), "USD"),
                "archived" to WorkspaceGraphValue.BooleanValue(false),
                "lines" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.PURCHASE_LINES, listOf("line-1")),
                "lines_subtotal" to WorkspaceGraphValue.Computed("spending_lines_subtotal"),
                "food_amount" to WorkspaceGraphValue.Computed("spending_food_amount"),
                "non_food_amount" to WorkspaceGraphValue.Computed("spending_non_food_amount"),
                "line_count" to WorkspaceGraphValue.Computed("spending_line_count"),
                "effective_total" to WorkspaceGraphValue.Computed("spending_effective_total"),
                "difference" to WorkspaceGraphValue.Computed("spending_difference"),
            ),
        )
        val line = row(
            WorkspaceGraphSurface.PURCHASE_LINES,
            "line-1",
            mapOf(
                "line" to WorkspaceGraphValue.Text("Basmati Rice"),
                "purchase" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.SPENDING, listOf("purchase-1")),
                "kitchen_item" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.KITCHEN, listOf("kitchen-rice")),
                "quantity" to WorkspaceGraphValue.Decimal(BigDecimal("1")),
                "unit" to WorkspaceGraphValue.Text("package"),
                "unit_price" to WorkspaceGraphValue.MoneyValue(BigDecimal("7.50"), "USD"),
                "subtotal" to WorkspaceGraphValue.MoneyValue(BigDecimal("7.50"), "USD"),
                "discount" to WorkspaceGraphValue.MoneyValue(BigDecimal.ZERO, "USD"),
                "tax" to WorkspaceGraphValue.MoneyValue(BigDecimal.ZERO, "USD"),
                "final_amount" to WorkspaceGraphValue.Computed("purchase_line_final_amount"),
                "currency" to WorkspaceGraphValue.Computed("purchase_line_currency"),
                "category" to WorkspaceGraphValue.Text("food"),
                "archived" to WorkspaceGraphValue.BooleanValue(false),
            ),
        )

        return WorkspaceGraphProjection(
            schemaVersion = WORKSPACE_GRAPH_SCHEMA_VERSION,
            householdId = "household-1",
            defaultCurrency = "USD",
            timezone = "America/New_York",
            locale = "en-US",
            schemas = WorkspaceGraphContract.schemas,
            rows = mapOf(
                WorkspaceGraphSurface.HOME to listOf(row(WorkspaceGraphSurface.HOME, "home-1", mapOf("dashboard" to WorkspaceGraphValue.Text("WonderFood Home")))),
                WorkspaceGraphSurface.KITCHEN to listOf(kitchen),
                WorkspaceGraphSurface.RECIPES to listOf(recipe),
                WorkspaceGraphSurface.INGREDIENTS to listOf(ingredient),
                WorkspaceGraphSurface.SPENDING to listOf(spending),
                WorkspaceGraphSurface.PURCHASE_LINES to listOf(line),
                WorkspaceGraphSurface.LISTS_HELP to listOf(row(WorkspaceGraphSurface.LISTS_HELP, "help-1", mapOf("guide" to WorkspaceGraphValue.Text("Units"), "details" to WorkspaceGraphValue.Text("Use supported units.")))),
                WorkspaceGraphSurface.SHOPPING to emptyList(),
                WorkspaceGraphSurface.MEALS to emptyList(),
                WorkspaceGraphSurface.STOCK_LOTS to emptyList(),
                WorkspaceGraphSurface.BINDINGS to emptyList(),
                WorkspaceGraphSurface.NEEDS_REVIEW to emptyList(),
                WorkspaceGraphSurface.SYSTEM to emptyList(),
            ),
        )
    }

    private fun JSONObject.requestType(): String = keys().asSequence().single()

    private fun JSONObject.requestBody(name: String): JSONObject = getJSONObject(name)
}
