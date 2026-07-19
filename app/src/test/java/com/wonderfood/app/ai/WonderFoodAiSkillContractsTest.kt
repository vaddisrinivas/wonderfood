package com.wonderfood.app.ai

import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.testing.TestFoodSeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WonderFoodAiSkillContractsTest {
    @Test
    fun recipeImportDetectsSamsungFoodSharedLinkAsReviewOnlyProposal() {
        val envelope = RecipeImportSkillContract.propose(
            RecipeImportInput(
                text = "https://app.samsungfood.com/recipes/abc123\nIngredients\n1 cup rice\ncilantro\nInstructions\nCook it.",
            ),
        )

        assertEquals(WonderFoodAiSkillId.RECIPE_IMPORT, envelope.skill)
        assertEquals(AiSkillStatus.NEEDS_REVIEW, envelope.status)
        assertEquals(RecipeSourceKind.SAMSUNG_FOOD_SHARED_LINK, requireNotNull(envelope.proposal).sourceKind)
        assertTrue(envelope.warnings.any { it.contains("bulk export", ignoreCase = true) })
        assertTrue(requireNotNull(envelope.proposal).parsedIngredients.any { it.name == "rice" })
    }

    @Test
    fun pantryNormalizePreservesOriginalAndAddsConfidenceLabel() {
        val envelope = PantryNormalizeSkillContract.normalize(listOf("MTR Rasam Powder 200g", "Greek Yogurt"))
        val rows = requireNotNull(envelope.proposal)

        assertEquals(AiSkillStatus.NEEDS_REVIEW, envelope.status)
        assertEquals("MTR Rasam Powder 200g", rows.first().originalName)
        assertEquals("pantry", rows.first().category)
        assertTrue(rows.first().confidence > 0.0)
        assertTrue(envelope.warnings.single().contains("Preserve original"))
    }

    @Test
    fun canCookRankingExplainsMatchedAndMissingIngredients() {
        val recipe = TestFoodSeeds.recipe().copy(ingredients = "Rice\nEggs\nSpinach")
        val inventory = listOf(
            TestFoodSeeds.inventoryItem(name = "Generic Rice"),
            TestFoodSeeds.inventoryItem(id = 2, name = "Eggs"),
        )

        val ranking = requireNotNull(CanCookSkillContract.rank(listOf(recipe), inventory).proposal).single()

        assertEquals(RecipeKitchenStatus.ALMOST_CAN_COOK, ranking.status)
        assertTrue(ranking.explanation.contains("2/3"))
        assertEquals(listOf("spinach"), ranking.missing)
    }

    @Test
    fun cartBuilderCreatesSuggestionsWithoutDeletingExistingCart() {
        val recipe = TestFoodSeeds.recipe().copy(ingredients = "Rice\nEggs\nSpinach")
        val entry = TestFoodSeeds.mealPlanEntry().copy(title = recipe.title, recipeId = recipe.id)
        val existing = listOf(TestFoodSeeds.groceryItem(name = "Eggs").copy(status = GroceryStatus.NEEDED))

        val envelope = CartBuilderSkillContract.suggestFromPlan(
            recipes = listOf(recipe),
            entries = listOf(entry),
            inventory = listOf(TestFoodSeeds.inventoryItem(name = "Rice")),
            existingCart = existing,
        )
        val suggestions = requireNotNull(envelope.proposal).draft.items

        assertFalse(suggestions.any { it.name.equals("eggs", ignoreCase = true) })
        assertTrue(suggestions.any { it.name.equals("spinach", ignoreCase = true) })
        assertTrue(envelope.warnings.single().contains("No silent deletion"))
    }

    @Test
    fun mealPlannerAndShareFormatterProduceReviewableHouseholdText() {
        val memory = TestFoodSeeds.memory()
        val planEnvelope = MealPlanSkillContract.propose(memory, LocalDate.of(2026, 1, 15))
        val shared = PlanShareFormatter.plainText(
            plan = memory.mealPlans.single(),
            entries = memory.mealPlanEntries,
            cart = memory.groceries,
        )

        assertEquals(AiSkillStatus.NEEDS_REVIEW, planEnvelope.status)
        assertTrue(shared.contains("WonderFood plan"))
        assertTrue(shared.contains("Shopping"))
        assertFalse(shared.contains("token", ignoreCase = true))
        assertFalse(shared.contains("credential", ignoreCase = true))
    }

    @Test
    fun recipePersonalizationAndCookingCoachStayAsProposals() {
        val recipe = TestFoodSeeds.recipe().copy(
            ingredients = "Rice\nEggs",
            steps = "Cook rice for 12 minutes. Add eggs.",
        )

        val personalized = RecipePersonalizeSkillContract.personalize(recipe, "higher protein")
        val coach = CookingCoachSkillContract.nextStep(recipe, currentStep = 0)

        assertEquals(AiSkillStatus.NEEDS_REVIEW, personalized.status)
        assertTrue(requireNotNull(personalized.proposal).explanation.contains("preserve original", ignoreCase = true))
        assertEquals(12, requireNotNull(coach.proposal).timerMinutes)
    }
}
