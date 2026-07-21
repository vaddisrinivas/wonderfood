package com.wonderfood.app.sync

import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.ReceiptItemDisposition
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSheetsWorkspaceDraftImporterTest {
    @Test
    fun friendlyWorkspaceRowsBecomeReviewableDrafts() {
        val draft = GoogleSheetsWorkspaceDraftImporter.toDraft(
            listOf(
                row(
                    WorkspaceGraphSurface.KITCHEN.label,
                    "lot-rice",
                    "Item" to "Basmati Rice",
                    "On hand" to "2",
                    "Unit" to "kg",
                    "Location" to "Pantry",
                    "Best before" to "2026-08-20",
                    "Archived" to "FALSE",
                ),
                row(
                    WorkspaceGraphSurface.SHOPPING.label,
                    "shop-spinach",
                    "Item" to "Spinach",
                    "Amount" to "1",
                    "Unit" to "bunch",
                    "Status" to "Needed",
                ),
                row(
                    WorkspaceGraphSurface.RECIPES.label,
                    "recipe-bowl",
                    "Recipe" to "Rice Bowl",
                    "Servings" to "2",
                    "Prep minutes" to "10",
                    "Instructions" to "Cook rice\nTop with spinach",
                ),
                row(
                    WorkspaceGraphSurface.INGREDIENTS.label,
                    "ingredient-rice",
                    "Ingredient" to "Basmati Rice",
                    "Recipe" to "Rice Bowl",
                    "Amount" to "1",
                    "Unit" to "cup",
                ),
                row(
                    WorkspaceGraphSurface.SPENDING.label,
                    "purchase-trader-joes",
                    "Purchase" to "Trader Joe's receipt",
                    "Merchant" to "Trader Joe's",
                    "Date" to "2026-07-18",
                    "Tax" to "0.98",
                    "Entered total" to "16.97",
                    "Currency" to "USD",
                    "Status" to "Reviewed",
                ),
                row(
                    WorkspaceGraphSurface.PURCHASE_LINES.label,
                    "line-rice",
                    "Line" to "Basmati Rice",
                    "_wf_purchase_id" to "purchase-trader-joes",
                    "Quantity" to "1",
                    "Unit" to "bag",
                    "Category" to "grain",
                    "Disposition" to "Inventory",
                    "Subtotal" to "8.99",
                    "Final amount" to "999.99",
                ),
                row(
                    WorkspaceGraphSurface.PURCHASE_LINES.label,
                    "line-soap",
                    "Line" to "Dish soap",
                    "_wf_purchase_id" to "purchase-trader-joes",
                    "Quantity" to "1",
                    "Unit" to "bottle",
                    "Category" to "cleaning",
                    "Disposition" to "Household",
                    "Subtotal" to "3.99",
                ),
                row(
                    WorkspaceGraphSurface.MEALS.label,
                    "meal-eaten",
                    "Meal" to "Rice Bowl lunch",
                    "Date" to "2026-07-19",
                    "Meal slot" to "Lunch",
                    "Status" to "Eaten",
                    "Recipe" to "Rice Bowl",
                ),
                row(
                    WorkspaceGraphSurface.MEALS.label,
                    "meal-planned",
                    "Meal" to "Dosa night",
                    "Date" to "2026-07-20",
                    "Meal slot" to "Dinner",
                    "Status" to "Planned",
                ),
            ),
        ) as CompositeDraft

        val inventory = draft.drafts.filterIsInstance<InventoryDraft>().single()
        val grocery = draft.drafts.filterIsInstance<GroceryDraft>().single()
        val recipe = draft.drafts.filterIsInstance<RecipeDraft>().single()
        val receipt = draft.drafts.filterIsInstance<ReceiptDraft>().single()
        val mealLog = draft.drafts.filterIsInstance<MealLogDraft>().single()
        val mealPlan = draft.drafts.filterIsInstance<MealPlanDraft>().single()

        assertEquals("Basmati Rice", inventory.items.single().name)
        assertEquals("2 kg", inventory.items.single().quantity)
        assertEquals(StorageZone.PANTRY, inventory.items.single().zone)
        assertEquals("Spinach", grocery.items.single().name)
        assertEquals("Rice Bowl", recipe.titleText)
        assertEquals("1 cup Basmati Rice", recipe.ingredientsText)
        assertEquals(2, recipe.servings)
        assertEquals("Trader Joe's", receipt.merchant)
        assertEquals(20_652L, receipt.purchasedAtMillis?.div(86_400_000L))
        assertEquals(null, receipt.subtotalCents)
        assertEquals(98L, receipt.taxCents)
        assertEquals(1_697L, receipt.totalCents)
        val receiptItemsByName = receipt.items.associateBy { it.food.name }
        assertEquals("1 bag", receiptItemsByName.getValue("Basmati Rice").food.quantity)
        assertEquals(ReceiptItemDisposition.INVENTORY, receiptItemsByName.getValue("Basmati Rice").disposition)
        assertEquals(899L, receiptItemsByName.getValue("Basmati Rice").linePriceCents)
        assertEquals("1 bottle", receiptItemsByName.getValue("Dish soap").food.quantity)
        assertEquals("cleaning", receiptItemsByName.getValue("Dish soap").food.category)
        assertEquals(ReceiptItemDisposition.HOUSEHOLD, receiptItemsByName.getValue("Dish soap").disposition)
        assertEquals(399L, receiptItemsByName.getValue("Dish soap").linePriceCents)
        assertEquals("Rice Bowl lunch", mealLog.titleText)
        assertEquals(MealSlot.LUNCH, mealLog.mealSlot)
        assertEquals("Imported Google Sheets plan", mealPlan.titleText)
        assertEquals(20_654L, mealPlan.startDateEpochDay)
    }

    private fun row(tab: String, identifier: String, vararg values: Pair<String, String>): GoogleSheetsWorkspaceRow =
        GoogleSheetsWorkspaceRow(
            tab = tab,
            identifier = identifier,
            values = values.toMap() + ("identifier" to identifier),
        )
}
