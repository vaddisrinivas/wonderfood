package com.wonderfood.app.sync

import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.FoodPreferences
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WonderFoodCsvGatewayTest {
    @Test
    fun exportsAndParsesCsvRoundTripRows() {
        val memory = FoodMemory(
            inventory = listOf(
                InventoryItem(
                    id = 1,
                    name = "Toor dal",
                    quantity = "2 kg",
                    zone = StorageZone.PANTRY,
                    category = "protein",
                    servingText = "100 g",
                    calories = 350,
                    proteinGrams = 22.0,
                    carbsGrams = 63.0,
                    fatGrams = 2.0,
                    nutritionSource = "test",
                    notes = "quote \" comma, newline\nok",
                    imageUri = null,
                    expiresAtMillis = null,
                    source = "test",
                    createdAtMillis = 1,
                    updatedAtMillis = 1,
                ),
            ),
            groceries = listOf(
                GroceryItem(
                    id = 2,
                    name = "Dahi",
                    quantity = "1 cup",
                    status = GroceryStatus.NEEDED,
                    category = "dairy",
                    servingText = "",
                    calories = null,
                    proteinGrams = null,
                    carbsGrams = null,
                    fatGrams = null,
                    nutritionSource = "",
                    source = "test",
                    imageUri = null,
                    createdAtMillis = 1,
                    updatedAtMillis = 1,
                ),
            ),
            recipes = listOf(
                Recipe(
                    id = 3,
                    title = "Sambar",
                    ingredients = "toor dal, bhindi",
                    steps = "Boil. Simmer.",
                    servings = 4,
                    prepMinutes = 45,
                    tags = "South Indian",
                    rating = null,
                    imageUri = null,
                    createdAtMillis = 1,
                    updatedAtMillis = 1,
                ),
            ),
            mealLogs = listOf(
                MealLog(
                    id = 4,
                    title = "Sambar lunch",
                    calories = 300,
                    proteinGrams = 14.0,
                    carbsGrams = 42.0,
                    fatGrams = 8.0,
                    mealSlot = MealSlot.LUNCH,
                    usedItemsText = "toor dal, bhindi",
                    loggedDateEpochDay = 20_000,
                    source = "test",
                    createdAtMillis = 1,
                    updatedAtMillis = 1,
                ),
            ),
            mealPlans = listOf(
                MealPlan(
                    id = 5,
                    title = "Week plan",
                    daysText = "Lunch: Sambar",
                    groceryHint = "okra",
                    status = MealPlanStatus.ACCEPTED,
                    startDateEpochDay = 20_000,
                    createdAtMillis = 1,
                    updatedAtMillis = 1,
                ),
            ),
            mealPlanEntries = listOf(
                MealPlanEntry(
                    id = 6,
                    planId = 5,
                    dateEpochDay = 20_001,
                    slot = MealSlot.LUNCH,
                    title = "Sambar",
                    calorieTarget = 300,
                    status = MealPlanEntryStatus.PLANNED,
                    createdAtMillis = 1,
                    updatedAtMillis = 1,
                ),
            ),
            preferences = FoodPreferences(
                dietStyle = "high protein",
                preferredCuisines = "South Indian",
                customAiInstructions = "prefer pantry first",
            ),
        )

        val csv = WonderFoodCsvGateway.export(memory)
        assertTrue(csv.startsWith("record_type,id,parent_id"))
        assertTrue(csv.contains("\"quote \"\" comma, newline\nok\""))

        val parsed = WonderFoodCsvGateway.parse(csv)

        assertEquals("Toor dal", parsed.inventory.single().name)
        assertEquals("2 kg", parsed.inventory.single().quantity)
        assertEquals(StorageZone.PANTRY, parsed.inventory.single().zone)
        assertEquals("Dahi", parsed.groceries.single().name)
        assertEquals("Sambar", parsed.recipes.single().titleText)
        assertEquals("Sambar lunch", parsed.mealLogs.single().titleText)
        assertEquals(MealSlot.LUNCH, parsed.mealLogs.single().mealSlot)
        assertEquals("Week plan", parsed.mealPlans.single().titleText)
        assertEquals(1, parsed.mealPlans.single().entries.single().dayOffset)
        assertEquals("high protein", parsed.preferences?.dietStyle)
        assertEquals("prefer pantry first", parsed.preferences?.customAiInstructions)
    }
}
