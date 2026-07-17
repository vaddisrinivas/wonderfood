package com.wonderfood.app.ai

import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraftNormalizer
import com.wonderfood.app.data.FoodDraftValidator
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.StorageZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryCrossChannelGoldenTest {
    @Test
    fun samePantryRequestNormalizesAcrossManualParserGoogleInAppAndChatGpt() {
        val manual = InventoryDraft(listOf(FoodCandidate(name = "eggs", quantity = "12", zone = StorageZone.FRIDGE)))
        val localParser = FoodInterpreter().interpret(
            text = "12 eggs",
            memory = FoodMemory(),
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

    private fun FoodCandidate.toCanonicalItem(): CanonicalItem =
        CanonicalItem(
            name = name,
            quantity = quantity,
            zone = zone,
            category = category,
        )

    private data class CanonicalItem(
        val name: String,
        val quantity: String,
        val zone: StorageZone,
        val category: String,
    )

    private companion object {
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
