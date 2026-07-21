package com.wonderfood.app.ai

import android.content.Intent
import android.net.Uri
import com.wonderfood.app.WonderFoodCommandContract
import com.wonderfood.app.WonderFoodDeepLink
import com.wonderfood.app.WonderFoodVoiceAction
import com.wonderfood.app.WonderFoodVoiceCommand
import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraftNormalizer
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodDraftValidator
import com.wonderfood.app.data.HouseholdUiMemory
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.LinkActionDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.ReceiptItemDraft
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import com.wonderfood.app.testing.TestFixtureResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PantryCrossChannelGoldenTest {
    @Test
    fun samePantryRequestNormalizesAcrossManualParserGoogleInAppAndChatGpt() {
        val manual = InventoryDraft(listOf(FoodCandidate(name = "eggs", quantity = "12", zone = StorageZone.FRIDGE)))
        val localParser = FoodInterpreter().interpret(
            text = "12 eggs",
            memory = HouseholdUiMemory(),
            promptContext = "Current WonderFood section: Kitchen.",
        ).draft as InventoryDraft
        val googleAssistant = InventoryDraft(listOf(FoodCandidate(name = "eggs", quantity = "12", zone = StorageZone.FRIDGE)))
        val inAppEnvelope = CommandEnvelopeDraftMapper.tryMap(INVENTORY_ENVELOPE_12_EGGS)?.draft as InventoryDraft
        val chatGptPackage = CommandEnvelopeDraftMapper.tryMap(CHATGPT_PACKAGE_12_EGGS)?.draft as InventoryDraft

        val canonical = listOf(manual, localParser, googleAssistant, inAppEnvelope, chatGptPackage)
            .map { draft ->
                val normalized = FoodDraftNormalizer.normalize(draft) as InventoryDraft
                assertTrue(FoodDraftValidator.validate(normalized).isEmpty())
                normalized.items.single().toCanonicalItem()
            }

        assertEquals(1, canonical.distinct().size)
        assertEquals(CanonicalItem("Eggs", "12", StorageZone.FRIDGE, "protein"), canonical.first())
    }

    @Test
    fun sameMealLogRequestNormalizesAcrossInAppPackageChannels() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/meal-log-generic.json")
        val manual = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))?.draft as MealLogDraft
        val envelope = CommandEnvelopeDraftMapper.tryMap(envelopeJson)?.draft as MealLogDraft

        val canonical = listOf(manual, envelope)
            .map { draft ->
                val normalized = FoodDraftNormalizer.normalize(draft) as MealLogDraft
                assertTrue(FoodDraftValidator.validate(normalized).isEmpty())
                normalized.toCanonicalLog()
            }

        assertEquals(1, canonical.distinct().size)
        assertEquals("Chicken Rice Bowl", canonical.first().title)
        assertEquals("LUNCH", canonical.first().mealSlot.name)
    }

    @Test
    fun sameMealPlanRequestNormalizesAcrossInAppPackageChannels() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/meal-plan-generic.json")
        val manual = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))?.draft as MealPlanDraft
        val envelope = CommandEnvelopeDraftMapper.tryMap(envelopeJson)?.draft as MealPlanDraft

        val canonical = listOf(manual, envelope)
            .map { draft ->
                val normalized = FoodDraftNormalizer.normalize(draft) as MealPlanDraft
                assertTrue(FoodDraftValidator.validate(normalized).isEmpty())
                normalized.toCanonicalPlan()
            }

        assertEquals(1, canonical.distinct().size)
        assertEquals("Tomorrow tofu dinner plan", canonical.first().title)
        assertEquals(1, canonical.first().entryCount)
    }

    @Test
    fun sameShoppingRequestNormalizesAcrossManualParserGoogleInAppAndChatGpt() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/shopping-add-generic.json")
        val manual = FoodInterpreter().interpret(
            text = "Need bread and olive oil from the store.",
            memory = HouseholdUiMemory(),
            promptContext = "Current WonderFood section: Shopping.",
        ).draft as GroceryDraft
        val googleAssistant = CommandEnvelopeDraftMapper.tryMap(envelopeJson)?.draft as GroceryDraft
        val chatGptPackage = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))?.draft as GroceryDraft

        val canonical = listOf(manual, googleAssistant, chatGptPackage)
            .map { draft ->
                val normalized = FoodDraftNormalizer.normalize(draft) as GroceryDraft
                assertTrue(FoodDraftValidator.validate(normalized).isEmpty())
                normalized.toCanonicalShopping()
            }

        assertEquals(1, canonical.distinct().size)
        assertEquals(listOf("bread", "olive oil"), canonical.first().items)
    }

    @Test
    fun sameGroceryRequestNormalizesAcrossShareCommandIntentDeepLinkAndManualParser() {
        val text = "Need 2 oats"
        val share = WonderFoodDeepLink.from(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text),
        )
        val commandIntent = WonderFoodDeepLink.from(
            Intent(WonderFoodCommandContract.ACTION_COMMAND)
                .putExtra(WonderFoodCommandContract.EXTRA_REQUEST_ID, "route-equivalence-1")
                .putExtra(WonderFoodCommandContract.EXTRA_ACTION_TYPE, "grocery.add")
                .putExtra(WonderFoodCommandContract.EXTRA_NAME, "Oats")
                .putExtra("quantity", "2")
                .putExtra("category", "grain")
                .putExtra(WonderFoodCommandContract.EXTRA_TEXT, text),
        )
        val deepLink = WonderFoodDeepLink.from(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "wonderfood://action?type=grocery.add&name=Oats&quantity=2&category=grain&requestId=route-equivalence-1",
                ),
            ),
        )

        val fromManual = FoodInterpreter().interpret(
            text = text,
            memory = HouseholdUiMemory(),
            promptContext = "Current WonderFood section: Shop.",
        ).draft as GroceryDraft

        val drafts = listOfNotNull(
            fromManual,
            commandToReviewedDraft(share),
            commandToReviewedDraft(commandIntent),
            commandToReviewedDraft(deepLink),
        )

        val canonical = drafts.map { draft ->
            val normalized = FoodDraftNormalizer.normalize(draft) as GroceryDraft
            assertTrue(FoodDraftValidator.validate(normalized).isEmpty())
            normalized.toCanonicalShopping()
        }

        assertEquals(1, canonical.distinct().size)
        assertEquals(listOf("oats"), canonical.first().items)
    }

    @Test
    fun sameBulkLinksRequestNormalizesAcrossInAppPackageChannels() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/bulk-links-generic.json")
        val packageDraft = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))?.draft
        val envelopeDraft = CommandEnvelopeDraftMapper.tryMap(envelopeJson)?.draft

        val canonical = listOf(packageDraft, envelopeDraft).map {
            it?.let { draft -> draft.toCanonicalSignals() } ?: emptyList()
        }

        assertEquals(1, canonical.distinct().size)
        assertEquals(canonical.first().size, canonical.last().size)
        assertEquals(2, canonical.first().size)
        assertEquals(1, canonical.first().count { it.startsWith("inventory:") })
        assertEquals(1, canonical.first().count { it.startsWith("grocery:") })
    }

    @Test
    fun sameRecipeRequestNormalizesAcrossInAppPackageChannels() {
        val envelopeJson = TestFixtureResources.readText("fixtures/command-envelopes/recipe-save-generic.json")
        val manual = CommandEnvelopeDraftMapper.tryMap(packageJson(envelopeJson))?.draft as RecipeDraft
        val envelope = CommandEnvelopeDraftMapper.tryMap(envelopeJson)?.draft as RecipeDraft

        val canonical = listOf(manual, envelope)
            .map { draft ->
                val normalized = FoodDraftNormalizer.normalize(draft) as RecipeDraft
                assertTrue(FoodDraftValidator.validate(normalized).isEmpty())
                normalized.toCanonicalRecipe()
            }

        assertEquals(1, canonical.distinct().size)
        assertEquals("Tomato Peanut Curry", canonical.first().title)
        assertEquals(4, canonical.first().servings)
        assertEquals(20, canonical.first().prepMinutes)
    }

    private fun FoodCandidate.toCanonicalItem(): CanonicalItem =
        CanonicalItem(
            name = name,
            quantity = quantity,
            zone = zone,
            category = category,
        )

    private fun MealLogDraft.toCanonicalLog(): CanonicalMealLog =
        CanonicalMealLog(
            title = titleText,
            mealSlot = mealSlot,
            calories = calories,
            source = source,
        )

    private fun MealPlanDraft.toCanonicalPlan(): CanonicalMealPlan =
        CanonicalMealPlan(
            title = titleText,
            entryCount = entries.size,
            daysText = daysText,
            groceryHint = groceryHint,
        )

    private fun RecipeDraft.toCanonicalRecipe(): CanonicalRecipe =
        CanonicalRecipe(
            title = titleText,
            servings = servings,
            prepMinutes = prepMinutes,
            ingredientsText = ingredientsText,
            stepsText = stepsText,
        )

    private fun GroceryDraft.toCanonicalShopping(): CanonicalShopping =
        CanonicalShopping(
            items = items
                .map { item -> item.name.lowercase().trim() }
                .sorted()
        )

    private fun commandToReviewedDraft(command: WonderFoodVoiceCommand?): GroceryDraft? {
        val source = command ?: return null
        return when (source.action) {
            WonderFoodVoiceAction.ADD_GROCERY -> source.toDirectGroceryDraft()
            WonderFoodVoiceAction.AI_REVIEW -> FoodInterpreter().interpret(
                text = source.text.ifBlank { "Need groceries" },
                memory = HouseholdUiMemory(),
                promptContext = "Current WonderFood section: Shop.",
            ).draft as? GroceryDraft
            else -> null
        }
    }

    private fun WonderFoodVoiceCommand.toDirectGroceryDraft(): GroceryDraft? {
        if (itemName.isBlank()) return null
        return GroceryDraft(
            items = listOf(
                FoodCandidate(
                    name = itemName.trim(),
                    quantity = quantity.takeIf { it.isNotBlank() } ?: "1",
                    zone = classifyStorageZone(itemName),
                    category = category.takeIf { it.isNotBlank() } ?: categorizeFood(itemName),
                ),
            ),
        )
    }

    private fun FoodDraft.toCanonicalSignals(): List<String> = when (this) {
        is CompositeDraft -> drafts.flatMap { it.toCanonicalSignals() }
        is InventoryDraft -> items.map { item ->
            val normalized = item.toCanonicalInventorySignal()
            "inventory:$normalized"
        }
        is GroceryDraft -> items.map { item ->
            val normalized = item.toCanonicalShoppingSignal()
            "grocery:$normalized"
        }
        is ReceiptDraft -> items.map { it.toCanonicalReceiptSignal() }
        is RecipeDraft -> listOf("recipe:${titleText.lowercase()}|$servings|$prepMinutes")
        is MealLogDraft -> listOf("meal_log:${titleText.lowercase()}|${mealSlot.name}|$calories")
        is MealPlanDraft -> listOf(
            "meal_plan:${titleText.lowercase()}|${entries.size}|${groceryHint.lowercase()}|${daysText.lowercase()}",
        )
        is LinkActionDraft -> listOf("link:${actionType}:${targetKind}")
    }

    private fun FoodCandidate.toCanonicalInventorySignal(): String =
        listOf(
            name.lowercase(),
            quantity,
            zone.label.lowercase(),
            category.lowercase(),
        ).joinToString("|")

    private fun FoodCandidate.toCanonicalShoppingSignal(): String =
        listOf(
            name.lowercase(),
            quantity,
            category.lowercase(),
        ).joinToString("|")

    private fun ReceiptItemDraft.toCanonicalReceiptSignal(): String =
        "receipt:${food.toCanonicalShoppingSignal()}|${disposition.name.lowercase()}|${linePriceCents ?: -1}"

    private data class CanonicalItem(
        val name: String,
        val quantity: String,
        val zone: StorageZone,
        val category: String,
    )

    private data class CanonicalMealLog(
        val title: String,
        val mealSlot: com.wonderfood.app.data.MealSlot,
        val calories: Int?,
        val source: String,
    )

    private data class CanonicalMealPlan(
        val title: String,
        val entryCount: Int,
        val daysText: String,
        val groceryHint: String,
    )

    private data class CanonicalRecipe(
        val title: String,
        val servings: Int?,
        val prepMinutes: Int?,
        val ingredientsText: String,
        val stepsText: String,
    )

    private data class CanonicalShopping(
        val items: List<String>,
    )

    private companion object {
        private fun packageJson(
            envelope: String,
            expiresAt: String = "2099-01-01T00:00:00Z",
        ): String =
            """
{
  "schema_version": "wf.proposal-package.v1",
  "proposal_id": "proposal-cross-channel-001",
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

        const val INVENTORY_ENVELOPE_12_EGGS = """
{
  "schema_version": "wf.ai.command-envelope.v1",
  "catalog_version": "wf.ai.skill-catalog.v1",
  "skill_id": "inventory",
  "skill_version": "1.0.0",
  "envelope_id": "env_cross_channel_eggs",
  "idempotency_key": "cross-channel-eggs-001",
  "status": "commands",
  "evidence": [
    {
      "evidence_id": "ev_user_1",
      "type": "user_text",
      "source_ref": "turn:test",
      "quote": "12 eggs",
      "observed_at": null,
      "confidence": 1.0
    }
  ],
  "commands": [
    {
      "command_id": "cmd_1",
      "type": "inventory.add_lot",
      "summary": "Add 12 eggs.",
      "payload": {
        "name": "Eggs",
        "quantity": {
          "amount": 12,
          "unit": "count",
          "text": "12"
        },
        "storage_zone": "FRIDGE",
        "category": "protein",
        "source": "user_text"
      },
      "evidence_refs": ["ev_user_1"],
      "confidence": {
        "score": 0.95,
        "rationale": "Item and count are explicit."
      },
      "confirmation": {
        "required": false,
        "level": "review",
        "reason": "Pantry additions are reviewable.",
        "prompt": "Add 12 eggs to the fridge?"
      },
      "destructive": false,
      "mutation": true
    }
  ],
  "confidence": {
    "score": 0.95,
    "rationale": "Clear pantry request."
  },
  "confirmation": {
    "required": false,
    "level": "review",
    "reason": "Review pantry additions.",
    "prompt": "Review and add?"
  },
  "warnings": [],
  "unsupported": null
}
"""

        const val CHATGPT_PACKAGE_12_EGGS = """
{
  "schema_version": "wf.proposal-package.v1",
  "proposal_id": "proposal-cross-channel-eggs",
  "origin": {
    "kind": "chatgpt",
    "producer": "WonderFood GPT"
  },
  "created_at": "2026-07-17T12:00:00Z",
  "expires_at": "2099-01-01T00:00:00Z",
  "command_envelope": $INVENTORY_ENVELOPE_12_EGGS,
  "signature": null
}
"""
    }
}
