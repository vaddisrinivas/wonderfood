package com.wonderfood.app.data

import com.wonderfood.app.testing.TestFoodSeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodPlanningToolkitTest {
    @Test
    fun nutritionProviderChainPrefersOpenFoodFactsBarcodeThenUsdaNameFallback() {
        val chain = NutritionProviderChain()

        val packaged = chain.enrich(FoodCandidate(name = "Oats"), barcode = "737628064502")
        val generic = chain.enrich(FoodCandidate(name = "chicken breast"))

        assertEquals("open_food_facts", packaged.nutritionSource)
        assertEquals(150, packaged.calories)
        assertTrue(packaged.evidence.contains("openfoodfacts"))
        assertEquals("usda_fooddata_central", generic.nutritionSource)
        assertEquals(165, generic.calories)
    }

    @Test
    fun pantryFirstPlanUsesOnHandRecipesAndCreatesMissingGroceries() {
        val memory = TestFoodSeeds.memory().copy(
            inventory = listOf(
                TestFoodSeeds.inventoryItem(name = "Generic Rice", quantity = "2 cups", zone = StorageZone.PANTRY),
                TestFoodSeeds.inventoryItem(id = 2, name = "Generic Eggs", quantity = "6", zone = StorageZone.FRIDGE),
            ),
            recipes = listOf(
                TestFoodSeeds.recipe(title = "Generic Rice Bowl"),
                TestFoodSeeds.recipe(id = 21, title = "Spinach Eggs",).copy(ingredients = "Eggs\nSpinach"),
            ),
        )

        val draft = FoodPlanningToolkit.pantryFirstPlan(memory, TestFoodSeeds.TODAY_EPOCH_DAY)

        assertEquals(2, draft.drafts.size)
        val plan = draft.drafts.first() as MealPlanDraft
        val groceries = draft.drafts.last() as GroceryDraft
        assertTrue(plan.daysText.contains("on hand"))
        assertTrue(groceries.items.any { it.name.contains("spinach", ignoreCase = true) })
    }

    @Test
    fun recipeImportParsesIngredientBlockWithoutInventingUnknowns() {
        val draft = RecipeIngredientParser.toRecipeDraft(
            """
            Chickpea Wraps
            Ingredients
            2 cups chickpeas
            1/2 cup yogurt
            cilantro
            Instructions
            Mix and wrap.
            """.trimIndent(),
        )

        assertEquals("Chickpea Wraps", draft.titleText)
        assertTrue(draft.ingredientsText.contains("2 cups chickpeas"))
        assertTrue(draft.ingredientsText.contains("0.5 cup yogurt"))
        assertTrue(draft.ingredientsText.contains("cilantro"))
        assertTrue(draft.stepsText.contains("Mix and wrap"))
    }

    @Test
    fun mealPlanShoppingListScalesMissingIngredientsFromPlannedRecipes() {
        val recipe = TestFoodSeeds.recipe().copy(ingredients = "1 cup rice\n2 eggs\nspinach")
        val entry = TestFoodSeeds.mealPlanEntry().copy(title = recipe.title, recipeId = recipe.id)

        val groceryDraft = FoodPlanningToolkit.shoppingForPlan(
            recipes = listOf(recipe),
            entries = listOf(entry),
            inventory = listOf(TestFoodSeeds.inventoryItem(name = "rice")),
        )

        assertTrue(groceryDraft.items.none { it.name == "rice" })
        assertTrue(groceryDraft.items.any { it.name == "eggs" })
        assertTrue(groceryDraft.items.any { it.name == "spinach" })
    }

    @Test
    fun preparedBatchPlanKeepsPortionsNutritionAndFreezerDate() {
        val batch = FoodPlanningToolkit.preparedBatchForRecipe(
            recipe = TestFoodSeeds.recipe(),
            portionCount = 6,
            storageZone = StorageZone.FREEZER,
            todayEpochDay = TestFoodSeeds.TODAY_EPOCH_DAY,
        )

        assertEquals(6, batch.portionCount)
        assertEquals(StorageZone.FREEZER, batch.storageZone)
        assertEquals(TestFoodSeeds.TODAY_EPOCH_DAY + 90, batch.consumeByEpochDay)
        assertNotNull(batch.perServingCalories)
        assertTrue(batch.shoppingDraft.items.isNotEmpty())
    }

    @Test
    fun householdProfilesParseFromHealthNotesOrPreferencesFallback() {
        val preferences = TestFoodSeeds.preferences().copy(
            allergies = "peanut",
            healthNotes = "profile: Adult | shellfish | cilantro | 2200 | 150",
        )

        val profiles = HouseholdProfileParser.parse(preferences)

        assertEquals(1, profiles.size)
        assertEquals("Adult", profiles.single().label)
        assertEquals(listOf("shellfish"), profiles.single().allergies)
        assertEquals(2200, profiles.single().calorieTarget)
    }

    @Test
    fun compatibilityExportNamesPrivacyAndExternalFormats() {
        val json = FoodPlanningToolkit.compatibilityExport(TestFoodSeeds.memory())

        assertTrue(json.contains("wonderfood.compatibility.v1"))
        assertTrue(json.contains("local-first export"))
        assertTrue(json.contains("Waistline"))
        assertTrue(json.contains("OpenNutriTracker"))
    }

    @Test
    fun remixSuggestionsUsePreparedBasesAndMissingItems() {
        val memory = TestFoodSeeds.memory().copy(
            inventory = listOf(
                TestFoodSeeds.inventoryItem(name = "Cooked chicken", category = "protein"),
                TestFoodSeeds.inventoryItem(id = 2, name = "Cooked rice", category = "grain"),
            ),
            mealLogs = emptyList(),
        )

        val suggestions = FoodPlanningToolkit.remixSuggestions(memory)

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.first().usedItems.any { it.contains("chicken", ignoreCase = true) })
        assertEquals("meal_prep_remix", suggestions.first().mealLogDraft.source)
    }
}
