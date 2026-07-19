package com.wonderfood.app.sync

import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.MealLogStatus
import com.wonderfood.core.model.MealSlot
import com.wonderfood.core.model.PlanEntryStatus
import com.wonderfood.core.model.ShoppingItemStatus
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WonderFoodWorkspaceSnapshotMergerTest {
    @Test
    fun mergesFriendlyKitchenShoppingRecipeMealAndPurchaseRowsByIdentifier() {
        val snapshot = WonderFoodWorkspaceSeedFixture.snapshot()
        val kitchenId = snapshot.stockLots.first().id.value
        val shoppingId = snapshot.shoppingItems.first().id.value
        val recipeId = snapshot.recipes.first().id.value
        val planEntryId = snapshot.mealPlans.single().entries.first().id.value
        val mealLogId = snapshot.mealLogs.single().id.value
        val receiptId = snapshot.receipts.single().id.value

        val result = WonderFoodWorkspaceSnapshotMerger.merge(
            snapshot = snapshot,
            updatedAt = "2026-07-19T13:00:00Z",
            rows = listOf(
                row(
                    WonderFoodWorkspaceSchema.KITCHEN,
                    kitchenId,
                    "Food" to "Aged Basmati Rice",
                    "On hand" to "1.5",
                    "Unit" to "kg",
                    "Pantry state" to "Low",
                    "Location" to "Pantry shelf",
                    "Best by" to "2026-08-30",
                ),
                row(
                    WonderFoodWorkspaceSchema.SHOPPING,
                    shoppingId,
                    "Item" to "Large Eggs",
                    "Needed" to "18",
                    "Unit" to "item",
                    "Cart state" to "In Cart",
                    "Reason" to "Weekend cooking",
                ),
                row(
                    WonderFoodWorkspaceSchema.RECIPES,
                    recipeId,
                    "Recipe" to "Spinach Rice Bowl Deluxe",
                    "Recipe state" to "Regular",
                    "Servings" to "3",
                    "Prep" to "15",
                    "Cook" to "20",
                    "Ingredients" to "rice\nspinach\nyogurt",
                    "Directions" to "Cook rice\nAdd spinach\nServe with yogurt",
                ),
                row(
                    WonderFoodWorkspaceSchema.MEALS,
                    planEntryId,
                    "When" to "2026-07-22",
                    "Slot" to "Dinner",
                    "Meal state" to "Eaten",
                    "Servings" to "2",
                ),
                row(
                    WonderFoodWorkspaceSchema.MEALS,
                    mealLogId,
                    "Meal" to "Yogurt snack logged",
                    "When" to "2026-07-19T11:00:00Z",
                    "Slot" to "Snack",
                    "Meal state" to "Confirmed",
                ),
                row(
                    WonderFoodWorkspaceSchema.PURCHASES,
                    receiptId,
                    "Purchase" to "Trader Joe's receipt",
                    "Merchant" to "Trader Joe's",
                    "Purchased" to "2026-07-18T18:45:00Z",
                    "Subtotal" to "15.99",
                    "Total" to "16.97",
                    "Currency" to "USD",
                    "Purchase state" to "Reviewed",
                ),
            ),
        )

        val merged = result.snapshot
        val stockLot = merged.stockLots.first { it.id.value == kitchenId }
        val food = merged.foods.first { it.id == stockLot.foodId }
        val shopping = merged.shoppingItems.first { it.id.value == shoppingId }
        val recipe = merged.recipes.first { it.id.value == recipeId }
        val planEntry = merged.mealPlans.single().entries.first { it.id.value == planEntryId }
        val mealLog = merged.mealLogs.single { it.id.value == mealLogId }
        val receipt = merged.receipts.single { it.id.value == receiptId }

        assertEquals("Aged Basmati Rice", food.name)
        assertEquals(1.5, stockLot.quantity.amount)
        assertEquals(FoodUnit.KILOGRAM, stockLot.quantity.unit)
        assertEquals(StockLotStatus.LOW, stockLot.status)
        assertEquals("Large Eggs", merged.pages.first { it.id == shopping.pageId }.title)
        assertEquals(18.0, shopping.quantity.amount)
        assertEquals(ShoppingItemStatus.IN_CART, shopping.status)
        assertEquals("Spinach Rice Bowl Deluxe", recipe.title)
        assertEquals(3.0, recipe.servings.amount)
        assertEquals(3, recipe.ingredients.size)
        assertEquals(3, recipe.steps.size)
        assertEquals(MealSlot.DINNER, planEntry.mealSlot)
        assertEquals(PlanEntryStatus.EATEN, planEntry.status)
        assertEquals(2.0, planEntry.quantity.amount)
        assertEquals("Yogurt snack logged", merged.pages.first { it.id == mealLog.pageId }.title)
        assertEquals(MealLogStatus.CONFIRMED, mealLog.status)
        assertEquals("Trader Joe's", receipt.merchantName)
        assertEquals(16.97, receipt.total?.amount)
        assertTrue(result.changes.any { it.table == WonderFoodWorkspaceSchema.KITCHEN && it.field == "Food" })
        assertEquals(emptyList<WorkspaceMergeConflict>(), result.conflicts)
    }

    @Test
    fun createsCanonicalRecordsForNewFriendlyWorkspaceRows() {
        val result = WonderFoodWorkspaceSnapshotMerger.merge(
            snapshot = emptySnapshot(),
            updatedAt = "2026-07-19T13:00:00Z",
            rows = listOf(
                row(
                    WonderFoodWorkspaceSchema.KITCHEN,
                    "",
                    "Food" to "Paneer",
                    "On hand" to "400",
                    "Unit" to "g",
                    "Pantry state" to "Available",
                    "Location" to "Fridge",
                    "Best by" to "2026-07-27",
                ),
                row(
                    WonderFoodWorkspaceSchema.SHOPPING,
                    "workspace:shopping:tomatoes",
                    "Item" to "Tomatoes",
                    "Needed" to "6",
                    "Unit" to "item",
                    "Cart state" to "Need",
                    "Reason" to "Paneer curry",
                ),
                row(
                    WonderFoodWorkspaceSchema.RECIPES,
                    "workspace:recipe:paneer_curry",
                    "Recipe" to "Paneer Curry",
                    "Recipe state" to "Regular",
                    "Servings" to "4",
                    "Ingredients" to "paneer\ntomatoes",
                    "Directions" to "Saute tomatoes\nAdd paneer",
                ),
                row(
                    WonderFoodWorkspaceSchema.MEALS,
                    "workspace:meal:paneer_monday",
                    "Meal" to "Paneer Curry",
                    "When" to "2026-07-20",
                    "Slot" to "Dinner",
                    "Meal state" to "Planned",
                    "Servings" to "4",
                ),
                row(
                    WonderFoodWorkspaceSchema.PURCHASES,
                    "workspace:purchase:indian_store",
                    "Purchase" to "Indian store receipt",
                    "Merchant" to "Indian Store",
                    "Purchased" to "2026-07-19T12:30:00Z",
                    "Total" to "12.50",
                    "Currency" to "USD",
                    "Purchase state" to "Reviewed",
                ),
            ),
        )

        val merged = result.snapshot

        assertEquals(1, merged.foods.size)
        assertEquals("Paneer", merged.foods.single().name)
        assertEquals(400.0, merged.stockLots.single().quantity.amount)
        assertEquals(FoodUnit.GRAM, merged.stockLots.single().quantity.unit)
        assertEquals(1, merged.shoppingItems.size)
        assertEquals("Tomatoes", merged.pages.first { it.id == merged.shoppingItems.single().pageId }.title)
        assertEquals(1, merged.recipes.size)
        assertEquals("Paneer Curry", merged.recipes.single().title)
        assertEquals(2, merged.recipes.single().ingredients.size)
        assertEquals(2, merged.recipes.single().steps.size)
        assertEquals(1, merged.mealPlans.size)
        assertEquals("Imported workspace meals", merged.mealPlans.single().name)
        assertEquals(PlanEntryStatus.PLANNED, merged.mealPlans.single().entries.single().status)
        assertEquals(1, merged.receipts.size)
        assertEquals("Indian Store", merged.receipts.single().merchantName)
        assertEquals(12.5, merged.receipts.single().total?.amount)
        assertTrue(result.changes.any { it.after == "created" && it.table == WonderFoodWorkspaceSchema.KITCHEN })
        assertEquals(emptyList<WorkspaceMergeConflict>(), result.conflicts)
    }

    @Test
    fun detectsDuplicateRowsAndKeepsMergeDeterministic() {
        val snapshot = WonderFoodWorkspaceSeedFixture.snapshot()
        val kitchenId = snapshot.stockLots.first().id.value

        val result = WonderFoodWorkspaceSnapshotMerger.merge(
            snapshot = snapshot,
            updatedAt = "2026-07-19T14:00:00Z",
            rows = listOf(
                row(
                    WonderFoodWorkspaceSchema.KITCHEN,
                    kitchenId,
                    "Food" to "First rice edit",
                    "On hand" to "2",
                    "Unit" to "kg",
                ),
                row(
                    WonderFoodWorkspaceSchema.KITCHEN,
                    kitchenId,
                    "Food" to "Final rice edit",
                    "On hand" to "3",
                    "Unit" to "kg",
                ),
            ),
        )

        val stockLot = result.snapshot.stockLots.first { it.id.value == kitchenId }
        val food = result.snapshot.foods.first { it.id == stockLot.foodId }

        assertEquals("Final rice edit", food.name)
        assertEquals(3.0, stockLot.quantity.amount)
        assertEquals("2026-07-19T14:00:00Z", result.mergeClock)
        assertTrue(result.fieldClocks.any { it.identifier == kitchenId && it.field == "Food" })
        assertTrue(result.conflicts.any { it.identifier == kitchenId && it.field == "identifier" })
    }

    @Test
    fun reportsInvalidFriendlyValuesWithoutOverwritingCanonicalFields() {
        val snapshot = WonderFoodWorkspaceSeedFixture.snapshot()
        val original = snapshot.stockLots.first()

        val result = WonderFoodWorkspaceSnapshotMerger.merge(
            snapshot = snapshot,
            updatedAt = "2026-07-19T14:15:00Z",
            rows = listOf(
                row(
                    WonderFoodWorkspaceSchema.KITCHEN,
                    original.id.value,
                    "On hand" to "many",
                    "Pantry state" to "Rotten maybe",
                    "Best by" to "soon",
                ),
            ),
        )

        val merged = result.snapshot.stockLots.first { it.id == original.id }
        val conflictFields = result.conflicts.map { it.field }.toSet()

        assertEquals(original.quantity, merged.quantity)
        assertEquals(original.status, merged.status)
        assertEquals(original.expiresOn, merged.expiresOn)
        assertTrue("On hand" in conflictFields)
        assertTrue("Pantry state" in conflictFields)
        assertTrue("Best by" in conflictFields)
    }

    private fun row(tab: String, identifier: String, vararg values: Pair<String, String>): GoogleSheetsWorkspaceRow =
        GoogleSheetsWorkspaceRow(
            tab = tab,
            identifier = identifier,
            values = values.toMap() + ("identifier" to identifier),
        )

    private fun emptySnapshot() = WonderFoodSnapshot(
        schemaVersion = WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION,
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
