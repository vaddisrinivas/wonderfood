package com.wonderfood.app.ai

import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.FoodPreferences
import com.wonderfood.app.data.ChatMessage
import com.wonderfood.app.data.ChatRole
import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun weeklyCostcoTemplateCreatesReviewableInventoryDraftInKitchenContext() {
        val turn = interpreter.interpret(
            text = "Stock weekly Costco",
            memory = FoodMemory(),
            promptContext = "Current WonderFood section: Kitchen.",
        )

        val draft = turn.draft as InventoryDraft
        assertTrue(turn.reply.contains("template", ignoreCase = true))
        assertEquals(
            listOf("Eggs", "Greek Yogurt", "Spinach", "Frozen Berries", "Rolled Oats", "Milk"),
            draft.items.map { it.name },
        )
        assertEquals(StorageZone.FREEZER, draft.items.single { it.name == "Frozen Berries" }.zone)
        assertTrue(draft.items.all { it.notes == "purchase_template" })
    }

    @Test
    fun indianGroceriesTemplateCreatesReviewableGroceryDraftInShopContext() {
        val turn = interpreter.interpret(
            text = "Need Indian groceries",
            memory = FoodMemory(),
            promptContext = "Current WonderFood section: Shop.",
        )

        val draft = turn.draft as GroceryDraft
        assertTrue(turn.reply.contains("shopping list", ignoreCase = true))
        assertEquals(
            listOf("Sona Masoori Rice", "Toor Dal", "Onions", "Tomatoes", "Green Chilies", "Cilantro", "Curd"),
            draft.items.map { it.name },
        )
        assertEquals(StorageZone.PANTRY, draft.items.first().zone)
    }

    @Test
    fun preferredStaplesTemplateUsesUserSettingsWithoutLlm() {
        val turn = interpreter.interpret(
            text = "Need preferred staples",
            memory = FoodMemory(
                preferences = FoodPreferences(preferredStaples = "Ragi, rice, Greek yogurt"),
            ),
            promptContext = "Current WonderFood section: Shop.",
        )

        val draft = turn.draft as GroceryDraft
        assertEquals(listOf("Ragi", "Rice", "Greek Yogurt"), draft.items.map { it.name })
        assertEquals(StorageZone.FRIDGE, draft.items.single { it.name == "Greek Yogurt" }.zone)
    }

    @Test
    fun kitchenContextFoodListCreatesInventoryDraft() {
        val turn = interpreter.interpret(
            text = "mixed vegetables 2 bags 1dollar pouch from Walmart, orgain protein powder, frozen berries costco-3 servings left, frozen broccoli 2 bags Walmart",
            memory = FoodMemory(),
            promptContext = "Current WonderFood section: Kitchen. Infer the smallest food-memory operation.",
        )

        val draft = turn.draft as InventoryDraft
        assertEquals(
            listOf("Mixed Vegetables", "Orgain Protein Powder", "Frozen Berries", "Frozen Broccoli"),
            draft.items.map { it.name },
        )
        assertEquals("2 bags", draft.items.first().quantity)
        assertEquals(StorageZone.FREEZER, draft.items.single { it.name == "Frozen Broccoli" }.zone)
    }

    @Test
    fun pantryParserHandlesChecklistExamplesWithoutLlm() {
        val one = interpreter.interpret("12 eggs", FoodMemory(), "Current WonderFood section: Kitchen.")
            .draft as InventoryDraft
        val two = interpreter.interpret("milk 2 gallons fridge", FoodMemory(), "Current WonderFood section: Kitchen.")
            .draft as InventoryDraft
        val three = interpreter.interpret("rice, lentils, onions", FoodMemory(), "Current WonderFood section: Kitchen.")
            .draft as InventoryDraft
        val four = interpreter.interpret("add 3 frozen pizzas", FoodMemory(), "Current WonderFood section: Kitchen.")
            .draft as InventoryDraft

        assertEquals("Eggs", one.items.single().name)
        assertEquals("12", one.items.single().quantity)
        assertEquals("Milk", two.items.single().name)
        assertEquals("2 gallons", two.items.single().quantity)
        assertEquals(StorageZone.FRIDGE, two.items.single().zone)
        assertEquals(listOf("Rice", "Lentils", "Onions"), three.items.map { it.name })
        assertEquals("Frozen Pizzas", four.items.single().name)
        assertEquals("3", four.items.single().quantity)
        assertEquals(StorageZone.FREEZER, four.items.single().zone)
    }

    @Test
    fun followUpThemCanReusePreviousFoodListForGroceries() {
        val previousList = "onions, tomatoes, carrots"
        val turn = interpreter.interpret(
            text = "add them to groceries",
            memory = FoodMemory(
                messages = listOf(
                    ChatMessage(1, ChatRole.USER, previousList, 0),
                    ChatMessage(2, ChatRole.ASSISTANT, "What should I do with those?", 0),
                    ChatMessage(3, ChatRole.USER, "add them to groceries", 0),
                ),
            ),
            promptContext = "Current WonderFood section: Shop. Infer the smallest food-memory operation.",
        )

        val draft = turn.draft as GroceryDraft
        assertEquals(listOf("Onions", "Tomatoes", "Carrots"), draft.items.map { it.name })
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
        assertTrue(requireNotNull(draft.calories) > 0)
        assertTrue(requireNotNull(draft.proteinGrams) > 0.0)
    }

    @Test
    fun logMealUsesExplicitCaloriesAndProtein() {
        val turn = interpreter.interpret(
            text = "Log breakfast oatmeal banana 320 calories 10g protein",
            memory = FoodMemory(),
        )

        val draft = turn.draft as MealLogDraft
        assertEquals("Breakfast Oatmeal Banana", draft.titleText)
        assertEquals(320, draft.calories)
        assertEquals(10.0, draft.proteinGrams ?: 0.0, 0.01)
        assertEquals(MealSlot.BREAKFAST, draft.mealSlot)
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

    @Test
    fun tomorrowLunchRecipeWithoutIngredientsAsksBeforeDrafting() {
        val turn = interpreter.interpret(
            text = "Plan tomato peanut curry for tomorrow lunch and save the recepie",
            memory = FoodMemory(),
        )

        assertNull(turn.draft)
        assertTrue(turn.reply.contains("ingredients", ignoreCase = true))
    }

    @Test
    fun tomorrowLunchRecipeWithIngredientsCreatesRecipeAndMealPlanDraft() {
        val turn = interpreter.interpret(
            text = "Plan tomato peanut curry with tomatoes, peanuts and onion for tomorrow lunch and save the recepie",
            memory = FoodMemory(),
        )

        val draft = turn.draft as CompositeDraft
        val recipe = draft.drafts.filterIsInstance<RecipeDraft>().single()
        val plan = draft.drafts.filterIsInstance<MealPlanDraft>().single()
        assertEquals("Tomato Peanut Curry", recipe.titleText)
        assertEquals("tomatoes, peanuts and onion", recipe.ingredientsText)
        assertEquals("🍛", recipe.imageUri)
        assertEquals("Tomato Peanut Curry", plan.entries.single().title)
        assertEquals(1, plan.entries.single().dayOffset)
        assertEquals(MealSlot.LUNCH, plan.entries.single().slot)
    }

    @Test
    fun nutritionWithoutServingAsksForPortionBeforeDrafting() {
        val turn = interpreter.interpret(
            text = "How many calories in tomato peanut curry?",
            memory = FoodMemory(),
        )

        assertNull(turn.draft)
        assertTrue(turn.reply.contains("portion", ignoreCase = true) || turn.reply.contains("how much", ignoreCase = true))
    }

    @Test
    fun tomorrowLunchWithoutRecipeIsPlanNotMealLog() {
        val turn = interpreter.interpret(
            text = "Add tofu bowl for tomorrow lunch",
            memory = FoodMemory(),
        )

        val draft = turn.draft as MealPlanDraft
        assertEquals("Tofu Bowl", draft.entries.single().title)
        assertEquals(1, draft.entries.single().dayOffset)
        assertEquals(MealSlot.LUNCH, draft.entries.single().slot)
    }
}
