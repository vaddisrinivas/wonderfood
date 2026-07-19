package com.wonderfood.app.sync

import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodEventConfidence
import com.wonderfood.app.data.FoodEventType
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.GroceryItem
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.MealLog
import com.wonderfood.app.data.MealPlan
import com.wonderfood.app.data.MealPlanEntry
import com.wonderfood.app.data.MealPlanEntryStatus
import com.wonderfood.app.data.MealPlanStatus
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.Recipe
import com.wonderfood.app.data.StorageZone
import com.wonderfood.core.model.FoodEventType as CoreFoodEventType
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.MealSlot as CoreMealSlot
import com.wonderfood.core.model.ShoppingItemStatus
import com.wonderfood.core.model.StockLotStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LegacyFoodMemorySnapshotExporterTest {
    @Test
    fun exportsVisibleKitchenItemsAsCanonicalFoodStockAndNutrition() {
        val snapshot = LegacyFoodMemorySnapshotExporter.toSnapshot(
            memory = FoodMemory(
                inventory = listOf(
                    inventoryItem(
                        id = 7,
                        name = "Greek Yogurt",
                        quantity = "2 cups",
                        zone = StorageZone.FRIDGE,
                        calories = 120,
                        proteinGrams = 18.0,
                    ),
                ),
            ),
            now = Instant.parse("2026-07-18T12:00:00Z"),
        )

        assertEquals("Greek Yogurt", snapshot.pages.single().title)
        assertEquals("legacy:food:inventory:7", snapshot.foods.single().id.value)
        assertEquals("legacy:stock_lot:inventory:7", snapshot.stockLots.single().id.value)
        assertEquals(2.0, snapshot.stockLots.single().quantity.amount)
        assertEquals(FoodUnit.CUP, snapshot.stockLots.single().quantity.unit)
        assertEquals("fridge", snapshot.stockLots.single().location)
        assertEquals(StockLotStatus.AVAILABLE, snapshot.stockLots.single().status)
        assertEquals(120.0, snapshot.nutritionSnapshots.single().values.energyKcal)
    }

    @Test
    fun exportsGroceriesRecipesPlansMealLogsAndEvents() {
        val snapshot = LegacyFoodMemorySnapshotExporter.toSnapshot(
            memory = FoodMemory(
                inventory = listOf(inventoryItem(id = 1, name = "Rice")),
                groceries = listOf(groceryItem(id = 2, name = "Spinach", status = GroceryStatus.NEEDED)),
                recipes = listOf(recipe(id = 3, title = "Rice Bowl")),
                mealPlans = listOf(mealPlan(id = 4, title = "Next week")),
                mealPlanEntries = listOf(mealPlanEntry(id = 5, planId = 4, title = "Rice Bowl")),
                mealLogs = listOf(mealLog(id = 6, title = "Rice lunch", usedItemsText = "Rice")),
                events = listOf(
                    com.wonderfood.app.data.FoodEvent(
                        id = 8,
                        type = FoodEventType.MEAL,
                        startedAtMillis = MILLIS,
                        endedAtMillis = null,
                        durationMinutes = null,
                        amount = 450.0,
                        unit = "kcal",
                        source = "manual",
                        confidence = FoodEventConfidence.ESTIMATED,
                        relatedRecipeId = null,
                        mealLogId = 6,
                        shoppingTripId = null,
                        inventoryItemId = null,
                        note = "Lunch",
                        createdAtMillis = MILLIS,
                    ),
                ),
            ),
            now = Instant.parse("2026-07-18T12:00:00Z"),
        )

        assertEquals(1, snapshot.foods.size)
        assertEquals(ShoppingItemStatus.NEEDED, snapshot.shoppingItems.single().status)
        assertEquals("Rice Bowl", snapshot.recipes.single().title)
        assertEquals(CoreMealSlot.LUNCH, snapshot.mealPlans.single().entries.single().mealSlot)
        assertEquals("legacy:food:inventory:1", snapshot.mealLogs.single().foodIds.single().value)
        assertEquals(CoreFoodEventType.EATEN, snapshot.foodEvents.single().type)
        assertNotNull(snapshot.pages.firstOrNull { it.id.value == "legacy:page:grocery:2" })
    }

    private fun inventoryItem(
        id: Long,
        name: String,
        quantity: String = "1",
        zone: StorageZone = StorageZone.PANTRY,
        calories: Int? = null,
        proteinGrams: Double? = null,
    ) = InventoryItem(
        id = id,
        name = name,
        quantity = quantity,
        zone = zone,
        category = "Food",
        servingText = "1 serving",
        calories = calories,
        proteinGrams = proteinGrams,
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

    private fun groceryItem(id: Long, name: String, status: GroceryStatus) = GroceryItem(
        id = id,
        name = name,
        quantity = "1 bunch",
        status = status,
        category = "Produce",
        servingText = "",
        calories = null,
        proteinGrams = null,
        carbsGrams = null,
        fatGrams = null,
        nutritionSource = "",
        source = "manual",
        imageUri = null,
        createdAtMillis = MILLIS,
        updatedAtMillis = MILLIS,
    )

    private fun recipe(id: Long, title: String) = Recipe(
        id = id,
        title = title,
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

    private fun mealPlan(id: Long, title: String) = MealPlan(
        id = id,
        title = title,
        daysText = "Mon",
        groceryHint = "",
        status = MealPlanStatus.ACCEPTED,
        startDateEpochDay = EPOCH_DAY,
        createdAtMillis = MILLIS,
        updatedAtMillis = MILLIS,
    )

    private fun mealPlanEntry(id: Long, planId: Long, title: String) = MealPlanEntry(
        id = id,
        planId = planId,
        dateEpochDay = EPOCH_DAY,
        slot = MealSlot.LUNCH,
        title = title,
        calorieTarget = null,
        status = MealPlanEntryStatus.PLANNED,
        createdAtMillis = MILLIS,
        updatedAtMillis = MILLIS,
    )

    private fun mealLog(id: Long, title: String, usedItemsText: String) = MealLog(
        id = id,
        title = title,
        calories = null,
        proteinGrams = null,
        carbsGrams = null,
        fatGrams = null,
        mealSlot = MealSlot.LUNCH,
        usedItemsText = usedItemsText,
        loggedDateEpochDay = EPOCH_DAY,
        source = "manual",
        createdAtMillis = MILLIS,
        updatedAtMillis = MILLIS,
    )

    private companion object {
        const val MILLIS = 1_784_342_400_000L
        const val EPOCH_DAY = 20_653L
    }
}
