package com.wonderfood.app.ai

import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.testing.TestFixtureResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandEnvelopeDraftMapperTest {
    @Test
    fun inventoryEnvelopeMapsToReviewDraft() {
        val turn = CommandEnvelopeDraftMapper.tryMap(
            TestFixtureResources.readText("fixtures/command-envelopes/inventory-add-generic.json"),
        )

        val draft = turn?.draft as InventoryDraft
        assertTrue(turn.reply.contains("review", ignoreCase = true))
        assertEquals(listOf("Eggs", "Frozen Berries"), draft.items.map { it.name })
        assertEquals("a dozen", draft.items.first().quantity)
        assertEquals(StorageZone.FRIDGE, draft.items.first().zone)
        assertEquals(StorageZone.FREEZER, draft.items.last().zone)
    }

    @Test
    fun proposalPackageMapsToReviewDraftWithOrigin() {
        val turn = CommandEnvelopeDraftMapper.tryMap(packageJson(inventoryEnvelope()))

        val draft = turn?.draft as InventoryDraft
        assertTrue(turn.reply.contains("WonderFood GPT"))
        assertEquals(listOf("Eggs", "Frozen Berries"), draft.items.map { it.name })
    }

    @Test
    fun shoppingEnvelopeMapsToReviewDraft() {
        val turn = CommandEnvelopeDraftMapper.tryMap(
            TestFixtureResources.readText("fixtures/command-envelopes/shopping-add-generic.json"),
        )

        val draft = turn?.draft as GroceryDraft
        assertTrue(turn.reply.contains("review", ignoreCase = true))
        assertEquals(listOf("Bread", "Olive Oil"), draft.items.map { it.name })
        assertEquals("Bakery", draft.items.first().category)
        assertEquals("Pantry", draft.items[1].category)
    }

    @Test
    fun shoppingAndPackageEnvelopeProduceEquivalentDrafts() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/shopping-add-generic.json")
        val envelope = CommandEnvelopeDraftMapper.tryMap(envelopeJson)
        val packageTurn = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))

        val direct = envelope?.draft as GroceryDraft
        val wrapped = packageTurn?.draft as GroceryDraft

        assertEquals(direct.items.map { it.name }, wrapped.items.map { it.name })
        assertEquals(direct.items.first().category, wrapped.items.first().category)
        assertEquals(direct.items[1].category, wrapped.items[1].category)
    }

    @Test
    fun bulkLinksEnvelopeAndPackageProduceEquivalentCompositeDraft() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/bulk-links-generic.json")
        val envelope = CommandEnvelopeDraftMapper.tryMap(envelopeJson)
        val packageTurn = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))

        val direct = requireNotNull(envelope?.draft)
        val wrapped = requireNotNull(packageTurn?.draft)
        assertEquals(direct, wrapped)
    }

    @Test
    fun receiptEnvelopePackageAndDirectProduceEquivalentDrafts() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/receipt-parse-generic.json")
        val envelope = CommandEnvelopeDraftMapper.tryMap(envelopeJson)
        val packageTurn = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))

        val direct = envelope?.draft as ReceiptDraft
        val wrapped = packageTurn?.draft as ReceiptDraft

        assertEquals(direct.items.map { it.food.name }, wrapped.items.map { it.food.name })
        assertEquals(direct.items.map { it.disposition }, wrapped.items.map { it.disposition })
        assertEquals(direct.items.map { it.linePriceCents }, wrapped.items.map { it.linePriceCents })
    }

    @Test
    fun confirmationRiskEnvelopeAndPackageRemainConsistentOnUnsupportedCommands() {
        val unsupportedEnvelope = TestFixtureResources.readText("fixtures/command-envelopes/nutrition-correction-generic.json")

        val envelopeTurn = CommandEnvelopeDraftMapper.tryMap(unsupportedEnvelope)
        val packageTurn = CommandEnvelopeDraftMapper.tryMap(packageJson(unsupportedEnvelope))

        assertNull(envelopeTurn?.draft)
        assertNull(packageTurn?.draft)
        assertEquals(envelopeTurn?.reply, packageTurn?.reply)
        assertTrue(envelopeTurn?.reply?.contains("cannot import those command types yet") == true)
        assertTrue(packageTurn?.reply?.contains("cannot import those command types yet") == true)
    }

    @Test
    fun expiredProposalPackageIsRejected() {
        val turn = CommandEnvelopeDraftMapper.tryMap(
            packageJson(
                envelope = inventoryEnvelope(),
                expiresAt = "2020-01-01T00:00:00Z",
            ),
        )

        assertNull(turn?.draft)
        assertTrue(requireNotNull(turn).reply.contains("expired", ignoreCase = true))
    }

    @Test
    fun receiptProposalMapsOnlyReviewableFoodItems() {
        val turn = CommandEnvelopeDraftMapper.tryMap(
            TestFixtureResources.readText("fixtures/command-envelopes/receipt-parse-generic.json"),
        )

        val draft = turn?.draft as ReceiptDraft
        assertEquals(listOf("Eggs", "Spinach", "Rice"), draft.items.map { it.food.name })
        assertEquals(StorageZone.PANTRY, draft.items.single { it.food.name == "Rice" }.food.zone)
    }

    @Test
    fun mealEnvelopeKeepsUnknownNutritionNull() {
        val turn = CommandEnvelopeDraftMapper.tryMap(MEAL_LOG_ENVELOPE)

        val draft = turn?.draft as MealLogDraft
        assertEquals("Chicken Rice Bowl", draft.titleText)
        assertEquals(MealSlot.LUNCH, draft.mealSlot)
        assertNull(draft.calories)
        assertNull(draft.proteinGrams)
        assertTrue(draft.source.contains("unknown"))
    }

    @Test
    fun compoundEnvelopeMapsToCompositeDraft() {
        val turn = CommandEnvelopeDraftMapper.tryMap(COMPOUND_ENVELOPE)

        val draft = turn?.draft as CompositeDraft
        assertTrue(draft.drafts.any { it is InventoryDraft })
        val plan = draft.drafts.filterIsInstance<MealPlanDraft>().single()
        assertEquals("Three Pantry Dinners", plan.titleText)
        assertEquals(2, plan.entries.size)
    }

    @Test
    fun mealLogEnvelopeMapsToStableReviewDraftAcrossPackageBoundary() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/meal-log-generic.json")
        val turn = CommandEnvelopeDraftMapper.tryMap(envelopeJson)
        val packageTurn = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))

        val envelopeDraft = turn?.draft as MealLogDraft
        val packageDraft = packageTurn?.draft as MealLogDraft

        assertEquals(envelopeDraft.titleText, packageDraft.titleText)
        assertEquals(envelopeDraft.mealSlot, packageDraft.mealSlot)
        assertEquals(envelopeDraft.calories, packageDraft.calories)
        assertEquals(envelopeDraft.usedItemsText, packageDraft.usedItemsText)
        assertTrue(envelopeDraft.source.contains("unknown"))
        assertEquals(envelopeDraft.source, packageDraft.source)
    }

    @Test
    fun mealPlanEnvelopeMapsToStableReviewDraftAcrossPackageBoundary() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/meal-plan-generic.json")
        val turn = CommandEnvelopeDraftMapper.tryMap(envelopeJson)
        val packageTurn = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))

        val envelopeDraft = turn?.draft as MealPlanDraft
        val packageDraft = packageTurn?.draft as MealPlanDraft

        assertEquals(envelopeDraft.titleText, packageDraft.titleText)
        assertEquals(envelopeDraft.entries.size, packageDraft.entries.size)
        assertEquals(envelopeDraft.entries.single().title, packageDraft.entries.single().title)
        assertEquals(envelopeDraft.entries.single().slot, packageDraft.entries.single().slot)
    }

    @Test
    fun recipeEnvelopeMapsToStableReviewDraftAcrossPackageBoundary() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/recipe-save-generic.json")
        val turn = CommandEnvelopeDraftMapper.tryMap(envelopeJson)
        val packageTurn = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))

        val envelopeDraft = turn?.draft as RecipeDraft
        val packageDraft = packageTurn?.draft as RecipeDraft

        assertEquals(envelopeDraft.titleText, packageDraft.titleText)
        assertEquals(envelopeDraft.ingredientsText, packageDraft.ingredientsText)
        assertEquals(envelopeDraft.servings, packageDraft.servings)
        assertEquals(envelopeDraft.prepMinutes, packageDraft.prepMinutes)
        assertEquals(envelopeDraft.stepsText, packageDraft.stepsText)
    }

    @Test
    fun plainTextIsNotAnEnvelope() {
        assertNull(CommandEnvelopeDraftMapper.tryMap("Need oats and bananas"))
    }

    private companion object {
        fun inventoryEnvelope(): String =
            TestFixtureResources.readText("fixtures/command-envelopes/inventory-add-generic.json")

        fun packageJson(
            envelope: String,
            expiresAt: String = "2099-01-01T00:00:00Z",
        ): String =
            """
{
  "schema_version": "wf.proposal-package.v1",
  "proposal_id": "proposal-test-001",
  "origin": {
    "kind": "chatgpt",
    "producer": "WonderFood GPT"
  },
  "created_at": "2026-01-15T12:00:00Z",
  "expires_at": "$expiresAt",
  "command_envelope": $envelope,
  "signature": null
}
""".trimIndent()

        const val MEAL_LOG_ENVELOPE = """
{
  "schema_version": "wf.ai.command-envelope.v1",
  "catalog_version": "wf.ai.skill-catalog.v1",
  "skill_id": "meals",
  "skill_version": "1.0.0",
  "envelope_id": "env_meal_test",
  "idempotency_key": "meal-test-001",
  "status": "commands",
  "evidence": [
    {
      "evidence_id": "ev_user_1",
      "type": "user_text",
      "source_ref": "turn:test",
      "quote": "Log chicken rice bowl for lunch.",
      "observed_at": null,
      "confidence": 1.0
    }
  ],
  "commands": [
    {
      "command_id": "cmd_1",
      "type": "meal.log",
      "summary": "Log Chicken Rice Bowl for lunch.",
      "payload": {
        "title": "Chicken Rice Bowl",
        "meal_slot": "LUNCH",
        "nutrition": {
          "source": "unknown"
        }
      },
      "evidence_refs": ["ev_user_1"],
      "confidence": {
        "score": 0.9,
        "rationale": "Meal title and slot are explicit."
      },
      "confirmation": {
        "required": false,
        "level": "review",
        "reason": "Meal log is reviewable.",
        "prompt": "Log this lunch?"
      },
      "destructive": false,
      "mutation": true
    }
  ],
  "confidence": {
    "score": 0.9,
    "rationale": "Clear meal log."
  },
  "confirmation": {
    "required": false,
    "level": "review",
    "reason": "Non-destructive.",
    "prompt": "Review meal log?"
  },
  "warnings": [],
  "unsupported": null
}
"""

        const val COMPOUND_ENVELOPE = """
{
  "schema_version": "wf.ai.command-envelope.v1",
  "catalog_version": "wf.ai.skill-catalog.v1",
  "skill_id": "planning",
  "skill_version": "1.0.0",
  "envelope_id": "env_compound_test",
  "idempotency_key": "repeat",
  "status": "commands",
  "evidence": [
    {
      "evidence_id": "ev_user_1",
      "type": "user_text",
      "source_ref": "turn:test",
      "quote": "Add eggs and plan two dinners.",
      "observed_at": null,
      "confidence": 1.0
    }
  ],
  "commands": [
    {
      "command_id": "cmd_1",
      "type": "inventory.add_lot",
      "summary": "Add eggs.",
      "payload": {
        "name": "Eggs",
        "quantity": {
          "amount": 12,
          "unit": "count",
          "text": "12"
        },
        "storage_zone": "FRIDGE",
        "category": "protein"
      },
      "evidence_refs": ["ev_user_1"],
      "confidence": {
        "score": 0.9,
        "rationale": "Explicit."
      },
      "confirmation": {
        "required": false,
        "level": "review",
        "reason": "Review.",
        "prompt": "Add eggs?"
      },
      "destructive": false,
      "mutation": true
    },
    {
      "command_id": "cmd_2",
      "type": "planning.create_meal_plan",
      "summary": "Create two dinners.",
      "payload": {
        "title": "Three Pantry Dinners",
        "entries": [
          {"day_offset": 0, "meal_slot": "DINNER", "title": "Spinach Egg Bowl"},
          {"day_offset": 1, "meal_slot": "DINNER", "title": "Fried Rice With Egg"}
        ],
        "shopping_suggestions": []
      },
      "evidence_refs": ["ev_user_1"],
      "confidence": {
        "score": 0.82,
        "rationale": "Plan entries explicit."
      },
      "confirmation": {
        "required": false,
        "level": "review",
        "reason": "Review.",
        "prompt": "Save plan?"
      },
      "destructive": false,
      "mutation": true
    }
  ],
  "confidence": {
    "score": 0.86,
    "rationale": "Clear compound update."
  },
  "confirmation": {
    "required": false,
    "level": "review",
    "reason": "Review.",
    "prompt": "Review updates?"
  },
  "warnings": [],
  "unsupported": null
}
"""
    }
}
