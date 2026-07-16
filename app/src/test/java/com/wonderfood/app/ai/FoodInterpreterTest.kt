package com.wonderfood.app.ai

import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.StorageZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodInterpreterTest {
    private val interpreter = FoodInterpreter()

    @Test
    fun boughtTextCreatesInventoryDraft() {
        val turn = interpreter.interpret(
            text = "I bought eggs, Greek yogurt, spinach and frozen berries",
            memory = FoodMemory(),
        )

        val draft = turn.draft as InventoryDraft
        assertEquals(listOf("Eggs", "Greek Yogurt", "Spinach", "Frozen Berries"), draft.items.map { it.name })
    }

    @Test
    fun needTextCreatesGroceryDraft() {
        val turn = interpreter.interpret(
            text = "Need oats, bananas and chicken thighs",
            memory = FoodMemory(),
        )

        val draft = turn.draft as GroceryDraft
        assertEquals(listOf("Oats", "Bananas", "Chicken Thighs"), draft.items.map { it.name })
    }

    @Test
    fun logMealCreatesNutritionDraft() {
        val turn = interpreter.interpret(
            text = "Log chicken rice bowl for lunch",
            memory = FoodMemory(
                inventory = listOf(
                    InventoryItem(
                        id = 1,
                        name = "Chicken",
                        quantity = "",
                        zone = StorageZone.FRIDGE,
                        category = "protein",
                        notes = "",
                        imageUri = null,
                        expiresAtMillis = null,
                        source = "test",
                        createdAtMillis = 0,
                        updatedAtMillis = 0,
                    ),
                ),
            ),
        )

        val draft = turn.draft as MealLogDraft
        assertEquals("Chicken Rice Bowl For Lunch", draft.titleText)
        assertEquals(MealSlot.LUNCH, draft.mealSlot)
        assertEquals("Chicken", draft.usedItemsText)
        assertTrue(draft.calories > 0)
        assertTrue(draft.proteinGrams > 0.0)
    }

    @Test
    fun mealPlanCreatesStructuredEntries() {
        val turn = interpreter.interpret(
            text = "Plan meals this week",
            memory = FoodMemory(),
        )

        val draft = turn.draft as MealPlanDraft
        assertEquals(5, draft.entries.size)
        assertEquals(0, draft.entries.first().dayOffset)
        assertTrue(draft.entries.first().title.isNotBlank())
    }
}
