package com.wonderfood.app.ai

import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.testing.TestFixtureResources
import com.wonderfood.app.testing.TestFoodSeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Assert.assertNull
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
        assertTrue(envelope.warnings.any { it.contains("Preserve original") })
    }

    @Test
    fun recipeImportRejectsMalformedInputWhenBlank() {
        val envelope = RecipeImportSkillContract.propose(
            RecipeImportInput(text = "", sourceUrl = ""),
        )

        assertEquals(AiSkillStatus.NEEDS_CLARIFICATION, envelope.status)
        assertEquals(AiSkillProposalIntent.ASK_CLARIFICATION, envelope.proposalIntent)
        assertNull(envelope.proposal)
        assertTrue(envelope.warnings.any { it.contains("No recipe text provided", ignoreCase = true) })
    }

    @Test
    fun recipeImportFailsClosedWhenProviderUnavailable() {
        val envelope = RecipeImportSkillContract.propose(
            RecipeImportInput(
                text = "https://example.com/recipe",
                provenance = AiSkillProvenance(providerAvailable = false),
            ),
        )

        assertEquals(AiSkillStatus.NEEDS_CLARIFICATION, envelope.status)
        assertEquals(AiSkillProposalIntent.BLOCKED, envelope.proposalIntent)
        assertNull(envelope.proposal)
    }

    @Test
    fun recipeImportMapsSchemaOrgOnlyAtAiBoundary() {
        val envelope = RecipeImportSkillContract.propose(RecipeImportInput(text = SCHEMA_ORG_RECIPE_JSON))
        val proposal = requireNotNull(envelope.proposal)
        val boundary = requireNotNull(proposal.schemaOrgBoundary)

        assertEquals(RecipeSourceKind.SCHEMA_ORG_RECIPE, proposal.sourceKind)
        assertEquals("Tomato Rice Bowl", proposal.draft.titleText)
        assertTrue(proposal.draft.ingredientsText.contains("1 cup rice"))
        assertTrue(proposal.draft.stepsText.contains("Cook rice"))
        assertEquals(4, proposal.draft.servings)
        assertEquals(25, proposal.draft.prepMinutes)
        assertTrue(boundary.types.containsAll(listOf("Recipe", "HowToStep", "NutritionInformation", "Product", "Offer", "Organization")))
        assertEquals("320 calories", requireNotNull(boundary.nutrition).calories)
        assertEquals("Basmati Rice", boundary.products.single().name)
        assertEquals("6.99", boundary.offers.single().price)
        assertEquals("WonderFood Test Kitchen", boundary.organizations.single().name)
        assertTrue(envelope.warnings.any { it.contains("Schema.org boundary", ignoreCase = true) })
        assertTrue(envelope.warnings.any { it.contains("not persisted directly", ignoreCase = true) })
    }

    @Test
    fun pantryNormalizeFlagsMalformedAndUnsafeInput() {
        val envelope = PantryNormalizeSkillContract.normalize(listOf("explosive chemical"))

        assertEquals(AiSkillStatus.NEEDS_REVIEW, envelope.status)
        assertTrue(envelope.warnings.any { it.contains("filtered out", ignoreCase = true) })
        assertEquals(0, requireNotNull(envelope.proposal).size)
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
    fun canCookNeedsClarificationWhenNoRecipesGiven() {
        val envelope = CanCookSkillContract.rank(emptyList(), emptyList())

        assertEquals(AiSkillStatus.NEEDS_CLARIFICATION, envelope.status)
        assertEquals(AiSkillProposalIntent.ASK_CLARIFICATION, envelope.proposalIntent)
        assertTrue(envelope.warnings.any { it.contains("No recipes were provided", ignoreCase = true) })
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
        assertTrue(envelope.warnings.any { it.contains("No silent deletion", ignoreCase = true) })
    }

    @Test
    fun cartBuilderAsksForPlanContextWhenMissingPlanInputs() {
        val envelope = CartBuilderSkillContract.suggestFromPlan(
            recipes = emptyList(),
            entries = emptyList(),
            inventory = emptyList(),
        )

        assertEquals(AiSkillStatus.NEEDS_CLARIFICATION, envelope.status)
        assertEquals(AiSkillProposalIntent.ASK_CLARIFICATION, envelope.proposalIntent)
        assertNull(envelope.proposal)
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

    @Test
    fun recipePersonalizationBlocksUnsafeGoals() {
        val envelope = RecipePersonalizeSkillContract.personalize(
            TestFoodSeeds.recipe(),
            goal = "use drug-like additives",
        )

        assertEquals(AiSkillStatus.NEEDS_CLARIFICATION, envelope.status)
        assertEquals(AiSkillProposalIntent.BLOCKED, envelope.proposalIntent)
        assertNull(envelope.proposal)
    }

    @Test
    fun cookingCoachRequiresRecipeSteps() {
        val envelope = CookingCoachSkillContract.nextStep(
            TestFoodSeeds.recipe().copy(steps = ""),
            currentStep = 0,
        )

        assertEquals(AiSkillStatus.NEEDS_CLARIFICATION, envelope.status)
        assertEquals(AiSkillProposalIntent.ASK_CLARIFICATION, envelope.proposalIntent)
        assertNull(envelope.proposal)
    }

    @Test
    fun receiptParseContractUsesDeterministicParserForKnownReceiptText() {
        val rawReceipt = TestFixtureResources.readText("fixtures/receipts/generic-market-receipt.json")
            .let { payload ->
                Regex("\"raw_text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(payload)?.groupValues?.get(1)
                    ?.replace("\\n", "\n")
                    .orEmpty()
            }

        val envelope = ReceiptParseSkillContract.parse(ReceiptParseInput(rawReceipt))
        val proposal = requireNotNull(envelope.proposal)

        assertEquals(AiSkillStatus.NEEDS_REVIEW, envelope.status)
        assertEquals(AiSkillProposalIntent.REVIEW_DRAFT, envelope.proposalIntent)
        assertTrue(proposal.cleanedLines.isNotEmpty())
        assertEquals("receipt_text", proposal.draft.sourceLabel)
        assertTrue(proposal.draft.items.isNotEmpty())
    }

    @Test
    fun receiptParseOfflineProviderFailureShowsBlockedState() {
        val envelope = ReceiptParseSkillContract.parse(
            ReceiptParseInput(
                rawText = "Receipt\nRice 5.99\nTOTAL 5.99",
                provenance = AiSkillProvenance(providerAvailable = false),
            ),
        )

        assertEquals(AiSkillStatus.NEEDS_CLARIFICATION, envelope.status)
        assertEquals(AiSkillProposalIntent.BLOCKED, envelope.proposalIntent)
        assertNull(envelope.proposal)
    }

    @Test
    fun nutritionEstimateContractIsSafeAndReviewableWhenFoodIsGiven() {
        val envelope = NutritionEstimateSkillContract.estimate(NutritionEstimateInput("Meal: chicken rice bowl"))

        assertEquals(AiSkillStatus.NEEDS_REVIEW, envelope.status)
        assertEquals(AiSkillProposalIntent.REVIEW_DRAFT, envelope.proposalIntent)
        assertNotNull(envelope.proposal)
        assertTrue(envelope.proposal!!.calories > 0)
        assertTrue(envelope.warnings.first().contains("confirm"))
    }

    @Test
    fun nutritionEstimateAmbiguousPortionRequestsClarification() {
        val envelope = NutritionEstimateSkillContract.estimate(NutritionEstimateInput("meal, any amount, maybe a bit"))

        assertEquals(AiSkillStatus.NEEDS_CLARIFICATION, envelope.status)
        assertEquals(AiSkillProposalIntent.ASK_CLARIFICATION, envelope.proposalIntent)
        assertNull(envelope.proposal)
    }

    @Test
    fun mealPlanBlocksWhenAllRecipesContainAllergies() {
        val memory = TestFoodSeeds.memory().copy(
            preferences = TestFoodSeeds.preferences().copy(allergies = "egg"),
            recipes = listOf(TestFoodSeeds.recipe().copy(title = "Egg Curry", ingredients = "egg, milk")),
        )

        val envelope = MealPlanSkillContract.propose(memory, LocalDate.of(2026, 1, 15))

        assertEquals(AiSkillStatus.NEEDS_CLARIFICATION, envelope.status)
        assertEquals(AiSkillProposalIntent.BLOCKED, envelope.proposalIntent)
        assertNull(envelope.proposal)
        assertTrue(envelope.warnings.any { it.contains("allergy", ignoreCase = true) })
    }

    @Test
    fun receiptParseContractReturnsUnsafeLinesAsHouseholdDispositionOnly() {
        val envelope = ReceiptParseSkillContract.parse(ReceiptParseInput("Receipt\nOVEN CLEANER FOAM 4.99\nMINI CUCUMBERS 3.49\nTOTAL 8.48"))
        val proposal = requireNotNull(envelope.proposal)
        val unsafeCount = proposal.draft.items.count { it.food.name.contains("Cucumbers", ignoreCase = false) }

        assertEquals(AiSkillStatus.NEEDS_REVIEW, envelope.status)
        assertEquals(AiSkillProposalIntent.REVIEW_DRAFT, envelope.proposalIntent)
        assertTrue(unsafeCount > 0)
        assertTrue(envelope.warnings.any { it.contains("review", ignoreCase = true) })
    }

    @Test
    fun nutritionEstimateContractAsksForClarificationForEmptyInput() {
        val envelope = NutritionEstimateSkillContract.estimate(NutritionEstimateInput("hello"))

        assertEquals(AiSkillStatus.NEEDS_CLARIFICATION, envelope.status)
        assertEquals(AiSkillProposalIntent.ASK_CLARIFICATION, envelope.proposalIntent)
        assertNull(envelope.proposal)
    }

    @Test
    fun typedContractIntentsMatchProposalSurfaces() {
        val envelopes = listOf(
            RecipeImportSkillContract.propose(RecipeImportInput("recipe for rice eggs")),
            PantryNormalizeSkillContract.normalize(listOf("Eggs")),
            CanCookSkillContract.rank(listOf(TestFoodSeeds.recipe()), listOf(TestFoodSeeds.inventoryItem())),
            CartBuilderSkillContract.suggestFromPlan(emptyList(), emptyList(), emptyList()),
            RecipePersonalizeSkillContract.personalize(TestFoodSeeds.recipe(), "higher protein"),
            CookingCoachSkillContract.nextStep(TestFoodSeeds.recipe(), 0),
            MealPlanSkillContract.propose(TestFoodSeeds.memory()),
            ReceiptParseSkillContract.parse(ReceiptParseInput("Receipt\nOVEN CLEANER FOAM 4.99\nMINI CUCUMBERS 3.49\nTOTAL 8.48")),
            NutritionEstimateSkillContract.estimate(NutritionEstimateInput("A smoothie and sandwich")),
        )

        assertTrue(envelopes.all { it.proposalIntent == AiSkillProposalIntent.REVIEW_DRAFT || it.proposalIntent == AiSkillProposalIntent.ASK_CLARIFICATION })
        assertFalse(
            envelopes.any {
                it.status == AiSkillStatus.NEEDS_REVIEW && it.proposal == null &&
                    it.proposalIntent == AiSkillProposalIntent.REVIEW_DRAFT
            },
        )
    }

    @Test
    fun typedContractsPropagateMetadataAndProposalOnlyBehavior() {
        val contract = AiSkillContractMeta()
        val provenance = AiSkillProvenance(provider = "test-provenance")
        val envelopes = listOf(
            RecipeImportSkillContract.propose(RecipeImportInput("recipe for rice eggs", contract = contract, provenance = provenance)),
            PantryNormalizeSkillContract.normalize(listOf("Eggs"), contract = contract, provenance = provenance),
            CanCookSkillContract.rank(listOf(TestFoodSeeds.recipe()), listOf(TestFoodSeeds.inventoryItem()), contract = contract, provenance = provenance),
            CartBuilderSkillContract.suggestFromPlan(listOf(TestFoodSeeds.recipe()), listOf(TestFoodSeeds.mealPlanEntry()), listOf(TestFoodSeeds.inventoryItem()), contract = contract, provenance = provenance),
            RecipePersonalizeSkillContract.personalize(TestFoodSeeds.recipe(), "higher protein", contract = contract, provenance = provenance),
            CookingCoachSkillContract.nextStep(TestFoodSeeds.recipe(), 0, contract = contract, provenance = provenance),
            MealPlanSkillContract.propose(TestFoodSeeds.memory(), LocalDate.of(2026, 1, 15), contract = contract, provenance = provenance),
            ReceiptParseSkillContract.parse(ReceiptParseInput("Receipt\nOVEN CLEANER FOAM 4.99\nTOTAL 4.99", contract = contract, provenance = provenance)),
            NutritionEstimateSkillContract.estimate(NutritionEstimateInput("A smoothie and sandwich", contract = contract, provenance = provenance)),
        )

        assertTrue(envelopes.all { it.contract == contract && it.provenance.provider == provenance.provider })
        assertTrue(envelopes.all { it.proposal == null || it.proposalIntent != AiSkillProposalIntent.REVIEW_DRAFT || it.warnings.isNotEmpty() })
        assertTrue(envelopes.any { it.warnings.any { warning -> warning.contains("No direct-write", ignoreCase = true) } })
        envelopes.forEach { envelope ->
            val proposal = envelope.proposal ?: return@forEach
            when (proposal) {
                is RecipeImportProposal -> assertEquals(contract, proposal.contract)
                is List<*> -> assertListProposalsCarryContract(proposal)
                is CartSuggestionSet -> assertEquals(contract, proposal.contract)
                is RecipePersonalizationProposal -> assertEquals(contract, proposal.contract)
                is CookingCoachStep -> assertEquals(contract, proposal.contract)
                is ReceiptParseProposal -> assertEquals(contract, proposal.contract)
                is NutritionEstimateProposal -> assertEquals(contract, proposal.contract)
                is CompositeDraft -> assertTrue(true)
                else -> fail("Unexpected typed proposal ${proposal::class.java.name}")
            }
        }
    }

    private fun assertListProposalsCarryContract(rows: List<*>) {
        rows.forEach { row ->
            when (row) {
                is PantryNormalization -> assertEquals("wf.ai.skill-contract.v1", row.contract.contractVersion)
                is RecipeKitchenRanking -> assertEquals("wf.ai.skill-contract.v1", row.contract.contractVersion)
                else -> fail("Unexpected row proposal type ${row?.javaClass?.name}")
            }
        }
    }

    private companion object {
        const val SCHEMA_ORG_RECIPE_JSON = """
{
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "Recipe",
      "name": "Tomato Rice Bowl",
      "recipeYield": "4 servings",
      "prepTime": "PT25M",
      "recipeIngredient": ["1 cup rice", "2 tomatoes", "1 tbsp oil"],
      "recipeInstructions": [
        {"@type": "HowToStep", "text": "Cook rice."},
        {"@type": "HowToStep", "text": "Simmer tomatoes with oil."}
      ],
      "nutrition": {
        "@type": "NutritionInformation",
        "calories": "320 calories",
        "proteinContent": "9 g",
        "carbohydrateContent": "58 g",
        "fatContent": "6 g"
      }
    },
    {
      "@type": "Product",
      "name": "Basmati Rice",
      "sku": "rice-001",
      "brand": {"@type": "Organization", "name": "WonderFood Test Kitchen", "url": "https://example.test"},
      "offers": {"@type": "Offer", "price": "6.99", "priceCurrency": "USD", "availability": "https://schema.org/InStock"}
    }
  ]
}
"""
    }
}
