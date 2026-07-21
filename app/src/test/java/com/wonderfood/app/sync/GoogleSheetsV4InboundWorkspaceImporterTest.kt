package com.wonderfood.app.sync

import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.MealEntryStatus
import com.wonderfood.core.model.household.Money
import com.wonderfood.core.model.household.PurchaseLineDisposition
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.ReviewState
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSheetsV4InboundWorkspaceImporterTest {
    @Test
    fun importsV4WorkspaceRowsIntoCanonicalCommands() {
        val result = GoogleSheetsV4InboundWorkspaceImporter.importRows(
            rows = listOf(
                row(
                    WorkspaceGraphSurface.KITCHEN,
                    "kitchen-rice",
                    "Item" to "Basmati Rice",
                    "Kind" to "Food",
                    "Category" to "grain",
                    "On hand" to "2",
                    "Unit" to "cup",
                    "Archived" to "FALSE",
                ),
                row(
                    WorkspaceGraphSurface.RECIPES,
                    "recipe-bowl",
                    "Recipe" to "Spinach Rice Bowl",
                    "Servings" to "2",
                    "Instructions" to "Cook rice. Fold spinach.",
                    "Archived" to "FALSE",
                ),
                row(
                    WorkspaceGraphSurface.INGREDIENTS,
                    "ingredient-rice",
                    "Ingredient" to "Basmati Rice",
                    "Recipe" to "Spinach Rice Bowl",
                    "Kitchen item" to "Basmati Rice",
                    "Amount" to "1",
                    "Unit" to "cup",
                    "Optional" to "FALSE",
                ),
                row(
                    WorkspaceGraphSurface.MEALS,
                    "meal-bowl",
                    "Meal" to "Bowl dinner",
                    "Date" to "2026-07-20T23:00:00Z",
                    "Meal slot" to "Dinner",
                    "Recipe" to "Spinach Rice Bowl",
                    "Servings" to "2",
                    "Status" to "Planned",
                ),
                row(
                    WorkspaceGraphSurface.SHOPPING,
                    "shopping-rice",
                    "Item" to "Basmati Rice",
                    "Kitchen item" to "Basmati Rice",
                    "Amount" to "1",
                    "Unit" to "cup",
                    "Needed for recipes" to "Spinach Rice Bowl",
                    "Needed for meals" to "Bowl dinner",
                    "Status" to "Needed",
                    "Reason" to "Recipe gap",
                ),
                row(
                    WorkspaceGraphSurface.SPENDING,
                    "purchase-grocery",
                    "Purchase" to "Grocery receipt",
                    "Date" to "2026-07-20",
                    "Merchant" to "Trader Joe's",
                    "Currency" to "USD",
                    "Entered total" to "10.49",
                    "Tax" to "0.50",
                    "Status" to "Reviewed",
                ),
                row(
                    WorkspaceGraphSurface.PURCHASE_LINES,
                    "line-rice",
                    "Line" to "Basmati Rice",
                    "Purchase" to "Grocery receipt",
                    "Kitchen item" to "Basmati Rice",
                    "Shopping line" to "Basmati Rice",
                    "Quantity" to "1",
                    "Unit" to "cup",
                    "Unit price" to "9.99",
                    "Tax" to "0.50",
                    "Category" to "food",
                    "Disposition" to "Inventory",
                ),
            ),
            householdId = HOUSEHOLD_ID,
            now = NOW,
            defaultCurrency = "USD",
        )

        assertEquals(emptyList<V4InboundNeedsReviewDiagnostic>(), result.diagnostics)
        assertEquals(
            listOf(
                "UpsertShoppingList",
                "UpsertItem",
                "UpsertInventoryLot",
                "UpsertRecipe",
                "UpsertRecipeIngredient",
                "UpsertMealEntry",
                "UpsertShoppingLine",
                "UpsertMerchant",
                "UpsertPurchase",
                "UpsertPurchaseLine",
            ),
            result.commands.map { it.record.type },
        )

        val item = result.commands.filterIsInstance<HouseholdCommand.UpsertItem>().single().item
        assertEquals("Basmati Rice", item.name)
        assertEquals(ItemKind.FOOD, item.kind)
        assertEquals(QuantityUnit.CUP, item.defaultUnit)

        val lot = result.commands.filterIsInstance<HouseholdCommand.UpsertInventoryLot>().single().lot
        assertEquals(item.metadata.id, lot.itemId)
        assertEquals(Quantity(DecimalAmount.of("2"), QuantityUnit.CUP), lot.quantity)

        val recipe = result.commands.filterIsInstance<HouseholdCommand.UpsertRecipe>().single().recipe
        val ingredient = result.commands.filterIsInstance<HouseholdCommand.UpsertRecipeIngredient>().single().ingredient
        assertEquals(recipe.metadata.id, ingredient.recipeId)
        assertEquals(item.metadata.id, ingredient.itemId)
        assertEquals(Quantity(DecimalAmount.of("1"), QuantityUnit.CUP), ingredient.quantity)

        val meal = result.commands.filterIsInstance<HouseholdCommand.UpsertMealEntry>().single().entry
        assertEquals(recipe.metadata.id, meal.recipeId)
        assertEquals(1_784_588_400_000L, meal.scheduledAt.epochMillis)
        assertEquals(Quantity(DecimalAmount.of("2"), QuantityUnit.SERVING), meal.servings)
        assertEquals(MealEntryStatus.PLANNED, meal.status)

        val shopping = result.commands.filterIsInstance<HouseholdCommand.UpsertShoppingLine>().single().line
        assertEquals(item.metadata.id, shopping.itemId)
        assertEquals(ShoppingLineStatus.NEEDED, shopping.status)
        assertEquals(listOf(recipe.metadata.id, meal.metadata.id), shopping.sourceEntityIds)

        val purchase = result.commands.filterIsInstance<HouseholdCommand.UpsertPurchase>().single().purchase
        val purchaseLine = result.commands.filterIsInstance<HouseholdCommand.UpsertPurchaseLine>().single().line
        assertEquals(purchase.metadata.id, purchaseLine.purchaseId)
        assertEquals(item.metadata.id, purchaseLine.itemId)
        assertEquals(shopping.metadata.id, purchaseLine.shoppingLineId)
        assertEquals(Money(999, "USD"), purchaseLine.unitPrice)
        assertEquals(Money(999, "USD"), purchaseLine.lineSubtotal)
        assertEquals(Money(1049, "USD"), purchaseLine.finalAmount)
        assertEquals(PurchaseLineDisposition.INVENTORY, purchaseLine.disposition)
        assertEquals(ReviewState.ACCEPTED, purchaseLine.reviewState)
    }

    @Test
    fun invalidV4RowsProduceNeedsReviewDiagnosticsWithoutGuesses() {
        val result = GoogleSheetsV4InboundWorkspaceImporter.importRows(
            rows = listOf(
                row(
                    WorkspaceGraphSurface.KITCHEN,
                    "kitchen-rice",
                    "Item" to "Basmati Rice",
                    "Kind" to "Food",
                    "On hand" to "2",
                    "Unit" to "cup",
                ),
                row(
                    WorkspaceGraphSurface.RECIPES,
                    "recipe-bowl",
                    "Recipe" to "Spinach Rice Bowl",
                    "Servings" to "2",
                ),
                row(
                    WorkspaceGraphSurface.INGREDIENTS,
                    "ingredient-missing-recipe",
                    "Ingredient" to "Rice",
                    "Kitchen item" to "Basmati Rice",
                    "Amount" to "1",
                    "Unit" to "cup",
                ),
                row(
                    WorkspaceGraphSurface.INGREDIENTS,
                    "ingredient-incompatible",
                    "Ingredient" to "Rice",
                    "Recipe" to "Spinach Rice Bowl",
                    "Kitchen item" to "Basmati Rice",
                    "Amount" to "1",
                    "Unit" to "kilogram",
                ),
                row(
                    WorkspaceGraphSurface.SHOPPING,
                    "shopping-bad-relation",
                    "Item" to "Mystery scoop",
                    "Kitchen item" to "Spinach Rice Bowl",
                    "Amount" to "1",
                    "Unit" to "scoop",
                ),
                row(
                    WorkspaceGraphSurface.PURCHASE_LINES,
                    "line-no-purchase",
                    "Line" to "Rice",
                    "Quantity" to "1",
                    "Unit" to "cup",
                    "Subtotal" to "9.99",
                ),
            ),
            householdId = HOUSEHOLD_ID,
            now = NOW,
            defaultCurrency = "USD",
        )

        assertTrue(result.diagnostics.any { it.identifier == "ingredient-missing-recipe" && it.code == "missing_required_relation" })
        assertTrue(result.diagnostics.any { it.identifier == "ingredient-incompatible" && it.code == "incompatible_quantity_unit" })
        assertTrue(result.diagnostics.any { it.identifier == "shopping-bad-relation" && it.code == "unresolved_relation" })
        assertTrue(result.diagnostics.any { it.identifier == "shopping-bad-relation" && it.code == "unknown_unit" })
        assertTrue(result.diagnostics.any { it.identifier == "line-no-purchase" && it.code == "missing_required_relation" })

        assertEquals(
            listOf("UpsertShoppingList", "UpsertItem", "UpsertInventoryLot", "UpsertRecipe"),
            result.commands.map { it.record.type },
        )
    }

    @Test
    fun linkedStockLotIsImportedOnceWithProviderProvenance() {
        val result = GoogleSheetsV4InboundWorkspaceImporter.importRows(
            rows = listOf(
                row(
                    WorkspaceGraphSurface.KITCHEN,
                    "kitchen-rice",
                    "Item" to "Basmati Rice",
                    "Kind" to "Food",
                    "On hand" to "2",
                    "Unit" to "cup",
                ),
                GoogleSheetsWorkspaceRow(
                    tab = "_wf_lots",
                    identifier = "lot-rice",
                    values = mapOf(
                        "Lot" to "Basmati Rice lot",
                        "Kitchen item" to "Basmati Rice",
                        "Quantity" to "2",
                        "Unit" to "cup",
                        "Opened" to "TRUE",
                    ),
                ),
            ),
            householdId = HOUSEHOLD_ID,
            now = NOW,
            defaultCurrency = "USD",
            providerKey = "notion",
        )

        assertEquals(emptyList<V4InboundNeedsReviewDiagnostic>(), result.diagnostics)
        assertEquals(1, result.commands.filterIsInstance<HouseholdCommand.UpsertInventoryLot>().size)
        val lot = result.commands.filterIsInstance<HouseholdCommand.UpsertInventoryLot>().single().lot
        assertEquals(Quantity(DecimalAmount.of("2"), QuantityUnit.CUP), lot.quantity)
        assertEquals(NOW, lot.openedAt)
        assertEquals("notion_v4_inbound", lot.metadata.source.label)
    }

    @Test
    fun blankProviderIdsAreMintedUniquelyAndKeepRowProvenance() {
        val result = GoogleSheetsV4InboundWorkspaceImporter.importRows(
            rows = listOf(
                GoogleSheetsWorkspaceRow(
                    tab = "Kitchen",
                    identifier = "",
                    values = mapOf("Item" to "Rice", "Kind" to "Food", "On hand" to "1", "Unit" to "cup"),
                    remoteIdentity = "sheet:Kitchen:row:2",
                ),
                GoogleSheetsWorkspaceRow(
                    tab = "Kitchen",
                    identifier = "",
                    values = mapOf("Item" to "Beans", "Kind" to "Food", "On hand" to "2", "Unit" to "can"),
                    remoteIdentity = "sheet:Kitchen:row:3",
                ),
            ),
            householdId = HOUSEHOLD_ID,
            now = NOW,
            defaultCurrency = "USD",
        )

        val items = result.commands.filterIsInstance<HouseholdCommand.UpsertItem>().map { it.item }
        assertEquals(2, items.size)
        assertEquals(2, items.map { it.metadata.id }.toSet().size)
        assertEquals(
            setOf("sheet:Kitchen:row:2", "sheet:Kitchen:row:3"),
            items.mapNotNull { it.metadata.source.externalReference }.toSet(),
        )
    }

    @Test
    fun staleHighRiskQuantityEditRequiresReviewInsteadOfOverwrite() {
        val fixture = CanonicalWorkspaceTestFixture.snapshot()
        val local = fixture.copy(
            items = fixture.items.map { item -> item.copy(metadata = item.metadata.copy(revision = 7)) },
        )
        val itemId = local.items.single().metadata.id.value
        val result = GoogleSheetsV4InboundWorkspaceImporter.importRows(
            rows = listOf(
                GoogleSheetsWorkspaceRow(
                    tab = "Kitchen",
                    identifier = itemId,
                    values = mapOf(
                        "Item" to "Basmati Rice",
                        "Kind" to "Food",
                        "On hand" to "3",
                        "Unit" to "cup",
                        "_wf_revision" to "1",
                    ),
                    remoteIdentity = "sheet:Kitchen:row:2",
                ),
            ),
            householdId = local.household.id,
            now = NOW,
            defaultCurrency = "USD",
            baseSnapshot = local,
        )

        assertTrue(result.commands.none { it is HouseholdCommand.UpsertItem || it is HouseholdCommand.UpsertInventoryLot })
        assertTrue(result.diagnostics.any { it.field == "On hand" && it.code == "concurrent_high_risk_edit" })
    }

    private fun row(
        surface: WorkspaceGraphSurface,
        identifier: String,
        vararg values: Pair<String, String>,
    ): GoogleSheetsWorkspaceRow =
        GoogleSheetsWorkspaceRow(
            tab = surface.label,
            identifier = identifier,
            values = values.toMap(),
        )

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000105")
        val NOW = UtcTimestamp(1_784_520_000_000L)
    }
}
