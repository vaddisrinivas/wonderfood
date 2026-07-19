package com.wonderfood.app.sync

import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.GroceryItem
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.MealLog
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlan
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanStatus
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.Recipe
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySnapshotDraftImporterTest {
    @Test
    fun mapsCanonicalSnapshotToAdditiveLegacyDrafts() {
        val snapshot = LegacyFoodMemorySnapshotExporter.toSnapshot(
            memory = FoodMemory(
                inventory = listOf(inventoryItem()),
                groceries = listOf(groceryItem()),
                recipes = listOf(recipe()),
                mealLogs = listOf(mealLog()),
                mealPlans = listOf(mealPlan()),
            ),
            now = Instant.parse("2026-07-18T12:00:00Z"),
        )

        val draft = LegacySnapshotDraftImporter.toDraft(snapshot) as CompositeDraft

        val inventory = draft.drafts.filterIsInstance<InventoryDraft>().single()
        val grocery = draft.drafts.filterIsInstance<GroceryDraft>().single()
        val recipe = draft.drafts.filterIsInstance<RecipeDraft>().single()
        val mealLog = draft.drafts.filterIsInstance<MealLogDraft>().single()
        val mealPlan = draft.drafts.filterIsInstance<MealPlanDraft>().single()

        assertEquals("Greek Yogurt", inventory.items.single().name)
        assertEquals(StorageZone.FRIDGE, inventory.items.single().zone)
        assertEquals("Spinach", grocery.items.single().name)
        assertEquals("Rice Bowl", recipe.titleText)
        assertTrue(recipe.ingredientsText.contains("Rice"))
        assertEquals("Rice lunch", mealLog.titleText)
        assertEquals(MealSlot.LUNCH, mealLog.mealSlot)
        assertEquals("Next week", mealPlan.titleText)
    }

    private fun inventoryItem() = InventoryItem(
        id = 1,
        name = "Greek Yogurt",
        quantity = "2 cups",
        zone = StorageZone.FRIDGE,
        category = "Food",
        servingText = "1 serving",
        calories = 120,
        proteinGrams = 18.0,
        carbsGrams = null,
        fatGrams = null,
        nutritionSource = "manual",
        notes = "",
        imageUri = null,
        expiresAtMillis = null,
        source = "manual",
        createdAtMillis = MILLIS,
        updatedAtMillis = MILLIS,
    )

    private fun groceryItem() = GroceryItem(
        id = 2,
        name = "Spinach",
        quantity = "1 bunch",
        status = GroceryStatus.NEEDED,
        category = "Produce",
        source = "manual",
        imageUri = null,
        createdAtMillis = MILLIS,
        updatedAtMillis = MILLIS,
    )

    private fun recipe() = Recipe(
        id = 3,
        title = "Rice Bowl",
        ingredients = "Rice\nSpinach",
        steps = "Cook rice\nTop with spinach",
        servings = 2,
        prepMinutes = 10,
        tags = "quick",
        rating = null,
        imageUri = null,
        createdAtMillis = MILLIS,
        updatedAtMillis = MILLIS,
    )

    private fun mealLog() = MealLog(
        id = 4,
        title = "Rice lunch",
        calories = null,
        proteinGrams = null,
        carbsGrams = null,
        fatGrams = null,
        mealSlot = MealSlot.LUNCH,
        usedItemsText = "Rice",
        loggedDateEpochDay = EPOCH_DAY,
        source = "manual",
        createdAtMillis = MILLIS,
        updatedAtMillis = MILLIS,
    )

    private fun mealPlan() = MealPlan(
        id = 5,
        title = "Next week",
        daysText = "Mon",
        groceryHint = "",
        status = MealPlanStatus.ACCEPTED,
        startDateEpochDay = EPOCH_DAY,
        createdAtMillis = MILLIS,
        updatedAtMillis = MILLIS,
    )

    private companion object {
        const val MILLIS = 1_784_342_400_000L
        const val EPOCH_DAY = 20_653L
    }
}
