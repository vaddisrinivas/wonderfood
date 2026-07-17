package com.wonderfood.app.ai

import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.FoodPreferences
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.testing.TestFoodSeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicMealPlannerTest {
    @Test
    fun expiringPantryCoverageRanksRecipeFirst() {
        val now = TestFoodSeeds.NOW_MILLIS
        val memory = FoodMemory(
            inventory = listOf(
                TestFoodSeeds.inventoryItem(id = 1, name = "Spinach", zone = StorageZone.FRIDGE, category = "produce")
                    .copy(expiresAtMillis = now + ONE_DAY_MILLIS),
                TestFoodSeeds.inventoryItem(id = 2, name = "Eggs", zone = StorageZone.FRIDGE, category = "protein"),
                TestFoodSeeds.inventoryItem(id = 3, name = "Rice", zone = StorageZone.PANTRY, category = "grain"),
            ),
            recipes = listOf(
                TestFoodSeeds.recipe(id = 10, title = "Spinach Egg Bowl").copy(ingredients = "spinach, eggs"),
                TestFoodSeeds.recipe(id = 11, title = "Plain Rice").copy(ingredients = "rice"),
            ),
            preferences = FoodPreferences(preferredStaples = "eggs, rice", calorieGoal = "550"),
        )

        val plan = DeterministicMealPlanner.plan(memory, nowMillis = now)

        assertEquals("Spinach Egg Bowl", plan.draft.entries.first().title)
        assertTrue(plan.draft.daysText.contains("score"))
        assertTrue(plan.draft.daysText.contains("expiring 1"))
    }

    @Test
    fun allergiesAndDislikesBlockCandidates() {
        val now = TestFoodSeeds.NOW_MILLIS
        val memory = FoodMemory(
            inventory = listOf(
                TestFoodSeeds.inventoryItem(id = 1, name = "Spinach", zone = StorageZone.FRIDGE, category = "produce"),
                TestFoodSeeds.inventoryItem(id = 2, name = "Rice", zone = StorageZone.PANTRY, category = "grain"),
            ),
            recipes = listOf(
                TestFoodSeeds.recipe(id = 10, title = "Spinach Bowl").copy(ingredients = "spinach, rice"),
                TestFoodSeeds.recipe(id = 11, title = "Rice Plate").copy(ingredients = "rice"),
            ),
            preferences = FoodPreferences(dislikes = "spinach"),
        )

        val plan = DeterministicMealPlanner.plan(memory, nowMillis = now)

        assertFalse(plan.draft.entries.any { it.title.contains("spinach", ignoreCase = true) })
        assertTrue(plan.draft.entries.any { it.title == "Rice Plate" })
    }

    private companion object {
        const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
