package com.wonderfood.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FoodChatStoreTest {
    private val testDbName = "wonderfood-test.db"
    private lateinit var context: Context
    private lateinit var store: FoodChatStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
        store = FoodChatStore(context, testDbName)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(testDbName)
    }

    @Test
    fun appliesDraftsIntoStructuredMemory() {
        store.applyDraft(
            InventoryDraft(
                listOf(FoodCandidate(name = "Spinach", zone = StorageZone.FRIDGE, category = "produce")),
            ),
            sourceMessageId = null,
        )
        store.applyDraft(
            MealLogDraft(
                titleText = "Spinach Eggs",
                calories = 420,
                proteinGrams = 28.0,
                carbsGrams = 18.0,
                fatGrams = 22.0,
                mealSlot = MealSlot.BREAKFAST,
                usedItemsText = "Spinach",
            ),
            sourceMessageId = null,
        )
        store.applyDraft(
            MealPlanDraft(
                titleText = "Two meals",
                daysText = "Breakfast: Spinach Eggs",
                groceryHint = "eggs",
                entries = listOf(MealPlanEntryDraft(0, MealSlot.BREAKFAST, "Spinach Eggs", 420)),
            ),
            sourceMessageId = null,
        )

        val memory = store.readMemory()
        assertEquals("produce", memory.inventory.single().category)
        assertEquals(MealSlot.BREAKFAST, memory.mealLogs.single().mealSlot)
        assertEquals("Spinach", memory.mealLogs.single().usedItemsText)
        assertEquals("Spinach Eggs", memory.mealPlanEntries.single().title)
        assertTrue(memory.mealPlanEntries.single().calorieTarget == 420)
    }
}
