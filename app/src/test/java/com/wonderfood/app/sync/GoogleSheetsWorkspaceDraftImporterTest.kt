package com.wonderfood.app.sync

import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
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
                    WonderFoodWorkspaceSchema.KITCHEN,
                    "lot-rice",
                    "Food" to "Basmati Rice",
                    "On hand" to "2",
                    "Unit" to "kg",
                    "Location" to "Pantry",
                    "Best by" to "2026-08-20",
                ),
                row(
                    WonderFoodWorkspaceSchema.SHOPPING,
                    "shop-spinach",
                    "Item" to "Spinach",
                    "Needed" to "1",
                    "Unit" to "bunch",
                    "Cart state" to "Need",
                ),
                row(
                    WonderFoodWorkspaceSchema.RECIPES,
                    "recipe-bowl",
                    "Recipe" to "Rice Bowl",
                    "Recipe state" to "Regular",
                    "Servings" to "2",
                    "Prep" to "10",
                    "Ingredients" to "rice\nspinach",
                    "Directions" to "Cook rice\nTop with spinach",
                ),
                row(
                    WonderFoodWorkspaceSchema.MEALS,
                    "meal-eaten",
                    "Meal" to "Rice Bowl lunch",
                    "When" to "2026-07-19",
                    "Slot" to "Lunch",
                    "Meal state" to "Eaten",
                    "Food IDs" to "food-rice",
                ),
                row(
                    WonderFoodWorkspaceSchema.MEALS,
                    "meal-planned",
                    "Meal" to "Dosa night",
                    "When" to "2026-07-20",
                    "Slot" to "Dinner",
                    "Meal state" to "Planned",
                ),
            ),
        ) as CompositeDraft

        val inventory = draft.drafts.filterIsInstance<InventoryDraft>().single()
        val grocery = draft.drafts.filterIsInstance<GroceryDraft>().single()
        val recipe = draft.drafts.filterIsInstance<RecipeDraft>().single()
        val mealLog = draft.drafts.filterIsInstance<MealLogDraft>().single()
        val mealPlan = draft.drafts.filterIsInstance<MealPlanDraft>().single()

        assertEquals("Basmati Rice", inventory.items.single().name)
        assertEquals("2 kg", inventory.items.single().quantity)
        assertEquals(StorageZone.PANTRY, inventory.items.single().zone)
        assertEquals("Spinach", grocery.items.single().name)
        assertEquals("Rice Bowl", recipe.titleText)
        assertEquals(2, recipe.servings)
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
