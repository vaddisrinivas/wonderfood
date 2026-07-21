package com.wonderfood.core.ai

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandEnvelopeGoldenStabilityTest {
    @Test
    fun encodedInventoryCommandEnvelopeMatchesGoldenWireJson() {
        val envelope = CommandEnvelope(
            schemaVersion = COMMAND_ENVELOPE_SCHEMA_VERSION,
            catalogVersion = SKILL_CATALOG_VERSION,
            skillId = SkillId.INVENTORY,
            skillVersion = DEFAULT_SKILL_VERSION,
            envelopeId = "env_golden_stability_001",
            idempotencyKey = "repeat",
            status = EnvelopeStatus.COMMANDS,
            evidence = listOf(
                CommandEvidence(
                    evidenceId = "ev_user_1",
                    type = EvidenceType.USER_TEXT,
                    sourceRef = "turn:test:local",
                    quote = "Add one carton of generic eggs.",
                    observedAt = "2026-01-15T12:00:00Z",
                    confidence = 1.0,
                ),
            ),
            commands = listOf(
                Command(
                    commandId = "cmd_1",
                    type = CommandType.INVENTORY_ADD_LOT,
                    summary = "Add one carton of generic eggs.",
                    payload = CommandEnvelopeCodec.json.parseToJsonElement(
                        """
                        {
                          "name": "Generic Eggs",
                          "quantity": {
                            "amount": 1,
                            "unit": "carton",
                            "text": "one carton"
                          },
                          "storage_zone": "FRIDGE",
                          "category": "protein",
                          "source": "user_text"
                        }
                        """.trimIndent(),
                    ).jsonObject,
                    evidenceRefs = listOf("ev_user_1"),
                    confidence = Confidence(
                        score = 0.93,
                        rationale = "Food and package count are explicit.",
                    ),
                    confirmation = Confirmation(
                        required = false,
                        level = ConfirmationLevel.REVIEW,
                        reason = "Normal inventory addition still receives proposal review.",
                        prompt = "Add generic eggs to the fridge?",
                    ),
                    destructive = false,
                    mutation = true,
                ),
            ),
            confidence = Confidence(
                score = 0.93,
                rationale = "Clear user request with one generic item.",
            ),
            confirmation = Confirmation(
                required = false,
                level = ConfirmationLevel.REVIEW,
                reason = "Non-destructive additions still need proposal review.",
                prompt = "Review and add this inventory lot?",
            ),
            warnings = emptyList(),
            unsupported = null,
        )

        val encoded = CommandEnvelopeCodec.encode(envelope)

        assertEquals(INVENTORY_ADD_GOLDEN, encoded)
        assertEquals(envelope, CommandEnvelopeCodec.decode(encoded))
        assertTrue(CommandEnvelopeValidator.validate(envelope).errors.isEmpty())
    }

    @Test
    fun nutritionCorrectionGoldenLeavesUnobservedMacrosNull() {
        val envelope = CommandEnvelopeCodec.decode(NUTRITION_CORRECTION_GOLDEN)

        val encoded = CommandEnvelopeCodec.encode(envelope)

        assertEquals(NUTRITION_CORRECTION_GOLDEN, encoded)
        assertTrue(encoded.contains("\"carbs_g\":null"))
        assertTrue(encoded.contains("\"fat_g\":null"))
        assertEquals(CommandType.NUTRITION_CORRECT_INVENTORY_ITEM, envelope.commands.single().type)
        assertTrue(CommandEnvelopeValidator.validate(envelope).errors.isEmpty())
    }

    private companion object {
        const val INVENTORY_ADD_GOLDEN =
            """{"schema_version":"wf.ai.command-envelope.v1","catalog_version":"wf.ai.skill-catalog.v1","skill_id":"inventory","skill_version":"1.0.0","envelope_id":"env_golden_stability_001","idempotency_key":"repeat","status":"commands","evidence":[{"evidence_id":"ev_user_1","type":"user_text","source_ref":"turn:test:local","quote":"Add one carton of generic eggs.","observed_at":"2026-01-15T12:00:00Z","confidence":1.0}],"commands":[{"command_id":"cmd_1","type":"inventory.add_lot","summary":"Add one carton of generic eggs.","payload":{"name":"Generic Eggs","quantity":{"amount":1,"unit":"carton","text":"one carton"},"storage_zone":"FRIDGE","category":"protein","source":"user_text"},"evidence_refs":["ev_user_1"],"confidence":{"score":0.93,"rationale":"Food and package count are explicit."},"confirmation":{"required":false,"level":"review","reason":"Normal inventory addition still receives proposal review.","prompt":"Add generic eggs to the fridge?"},"destructive":false,"mutation":true}],"confidence":{"score":0.93,"rationale":"Clear user request with one generic item."},"confirmation":{"required":false,"level":"review","reason":"Non-destructive additions still need proposal review.","prompt":"Review and add this inventory lot?"},"warnings":[],"unsupported":null}"""

        const val NUTRITION_CORRECTION_GOLDEN =
            """{"schema_version":"wf.ai.command-envelope.v1","catalog_version":"wf.ai.skill-catalog.v1","skill_id":"nutrition_correction","skill_version":"1.0.0","envelope_id":"env_golden_nutrition_001","idempotency_key":"repeat","status":"needs_confirmation","evidence":[{"evidence_id":"ev_user_1","type":"user_text","source_ref":"turn:test:local","quote":"This generic yogurt label says 140 calories and 18g protein.","observed_at":"2026-01-15T12:00:00Z","confidence":1.0},{"evidence_id":"ev_label_1","type":"nutrition_label","source_ref":"label:test:generic-yogurt","quote":"140 calories, protein 18g","observed_at":"2026-01-15T12:00:00Z","confidence":0.9}],"commands":[{"command_id":"cmd_1","type":"nutrition.correct_inventory_item","summary":"Correct generic yogurt calories and protein from label evidence.","payload":{"inventory_item_ref":"inventory:item:generic-yogurt","serving":{"amount":1,"unit":"container","text":"one container"},"calories":140,"protein_g":18,"carbs_g":null,"fat_g":null,"source":"label","confidence":0.9,"evidence_label":"ev_label_1"},"evidence_refs":["ev_user_1","ev_label_1"],"confidence":{"score":0.9,"rationale":"Label-backed values are present for calories and protein only."},"confirmation":{"required":true,"level":"confirm","reason":"Nutrition correction may replace existing values.","prompt":"Update generic yogurt nutrition from this label?"},"destructive":true,"mutation":true}],"confidence":{"score":0.9,"rationale":"Label evidence supports the correction."},"confirmation":{"required":true,"level":"confirm","reason":"Existing nutrition values may be overwritten.","prompt":"Confirm nutrition correction?"},"warnings":[{"code":"nutrition_unverified","severity":"info","message":"Carbs and fat remain unknown because no values were provided.","evidence_refs":["ev_user_1","ev_label_1"]}],"unsupported":null}"""
    }
}
