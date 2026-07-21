package com.wonderfood.app.testing

import com.wonderfood.app.data.AiTurn
import com.wonderfood.app.data.HouseholdUiMemory
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntryDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft

data class FakeAiGatewayRequest(
    val text: String,
    val memory: HouseholdUiMemory,
)

class FakeAiGateway(
    private val responsesByText: MutableMap<String, AiTurn> = linkedMapOf(),
    private val fixtureLoader: (String) -> String = TestFixtureResources::readText,
) {
    private val mutableRequests = mutableListOf<FakeAiGatewayRequest>()

    val requests: List<FakeAiGatewayRequest>
        get() = mutableRequests.toList()

    fun enqueue(text: String, turn: AiTurn): FakeAiGateway = apply {
        responsesByText[text] = turn
    }

    fun interpret(text: String, memory: HouseholdUiMemory = HouseholdUiMemory()): AiTurn {
        mutableRequests += FakeAiGatewayRequest(text = text, memory = memory)
        return responsesByText[text] ?: error("No fake AI response queued for: $text")
    }

    fun commandEnvelope(path: String): String = fixtureLoader(path)
}

object FakeAiTurns {
    fun inventoryAdded(vararg names: String): AiTurn =
        AiTurn(
            reply = "Review generic inventory items.",
            draft = InventoryDraft(names.map { TestFoodSeeds.candidate(name = it) }),
        )

    fun groceryAdded(vararg names: String): AiTurn =
        AiTurn(
            reply = "Review generic grocery items.",
            draft = GroceryDraft(names.map { TestFoodSeeds.candidate(name = it) }),
        )

    fun recipeSaved(title: String = "Generic Rice Bowl"): AiTurn =
        AiTurn(
            reply = "Review generic recipe.",
            draft = RecipeDraft(
                titleText = title,
                ingredientsText = "Rice, eggs, spinach",
                stepsText = "Cook rice. Add eggs and spinach. Season to taste.",
                servings = 2,
                prepMinutes = 25,
                tags = "generic, offline",
                imageUri = "🥙",
                imageUrl = TestFoodSeeds.TEST_IMAGE_URL,
            ),
        )

    fun mealLogged(title: String = "Generic Rice Bowl"): AiTurn =
        AiTurn(
            reply = "Review generic meal log.",
            draft = MealLogDraft(
                titleText = title,
                calories = 520,
                proteinGrams = 24.0,
                carbsGrams = 62.0,
                fatGrams = 18.0,
                mealSlot = MealSlot.LUNCH,
                usedItemsText = "Generic Eggs, Generic Rice",
                loggedDateEpochDay = TestFoodSeeds.TODAY_EPOCH_DAY,
                source = TestFoodSeeds.TEST_SOURCE,
            ),
        )

    fun mealPlanSaved(title: String = "Generic Week Plan"): AiTurn =
        AiTurn(
            reply = "Review generic meal plan.",
            draft = MealPlanDraft(
                titleText = title,
                daysText = "Lunch: Generic Rice Bowl",
                groceryHint = "spinach",
                entries = listOf(
                    MealPlanEntryDraft(
                        dayOffset = 0,
                        slot = MealSlot.LUNCH,
                        title = "Generic Rice Bowl",
                        calorieTarget = 520,
                    ),
                ),
                startDateEpochDay = TestFoodSeeds.TODAY_EPOCH_DAY,
            ),
        )
}

data class CommandEnvelopeFixture(
    val path: String,
    val skillId: String,
    val expectedCommandCount: Int,
)

object CommandEnvelopeFixtures {
    val INVENTORY_ADD = CommandEnvelopeFixture(
        path = "fixtures/command-envelopes/inventory-add-generic.json",
        skillId = "inventory",
        expectedCommandCount = 2,
    )
    val SHOPPING_ADD = CommandEnvelopeFixture(
        path = "fixtures/command-envelopes/shopping-add-generic.json",
        skillId = "shopping",
        expectedCommandCount = 2,
    )
    val BULK_LINKS = CommandEnvelopeFixture(
        path = "fixtures/command-envelopes/bulk-links-generic.json",
        skillId = "shopping",
        expectedCommandCount = 2,
    )
    val RECIPE_SAVE = CommandEnvelopeFixture(
        path = "fixtures/command-envelopes/recipe-save-generic.json",
        skillId = "recipes",
        expectedCommandCount = 1,
    )
    val MEAL_LOG = CommandEnvelopeFixture(
        path = "fixtures/command-envelopes/meal-log-generic.json",
        skillId = "meals",
        expectedCommandCount = 1,
    )
    val MEAL_PLAN = CommandEnvelopeFixture(
        path = "fixtures/command-envelopes/meal-plan-generic.json",
        skillId = "planning",
        expectedCommandCount = 1,
    )
    val RECEIPT_PARSE = CommandEnvelopeFixture(
        path = "fixtures/command-envelopes/receipt-parse-generic.json",
        skillId = "receipt_parsing",
        expectedCommandCount = 2,
    )
    val NUTRITION_CORRECTION = CommandEnvelopeFixture(
        path = "fixtures/command-envelopes/nutrition-correction-generic.json",
        skillId = "nutrition_correction",
        expectedCommandCount = 1,
    )

    val all: List<CommandEnvelopeFixture> = listOf(
        INVENTORY_ADD,
        SHOPPING_ADD,
        BULK_LINKS,
        RECIPE_SAVE,
        MEAL_LOG,
        MEAL_PLAN,
        RECEIPT_PARSE,
        NUTRITION_CORRECTION,
    )
}
