package com.wonderfood.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredProposalGatewayTest {
    @Test
    fun validGoldenFixtureParsesAsAcceptedProposal() {
        val request = request(
            skillId = SkillId.INVENTORY,
            idempotencyKey = "turn-local-inventory-001",
            userText = "I bought a dozen eggs and two bags frozen berries.",
        )

        val result = CommandEnvelopeCodec.parseProposal(request, INVENTORY_GOLDEN)

        assertTrue(result is StructuredProposalResult.Accepted)
        val accepted = result as StructuredProposalResult.Accepted
        assertEquals(EnvelopeStatus.COMMANDS, accepted.envelope.status)
        assertEquals(2, accepted.envelope.commands.size)
        assertEquals(CommandType.INVENTORY_ADD_LOT, accepted.envelope.commands.first().type)
        assertTrue(accepted.envelope.hasMutatingCommands)
    }

    @Test
    fun malformedJsonReturnsRejectedSafeProposal() {
        val request = request(
            skillId = SkillId.INVENTORY,
            idempotencyKey = "turn-local-inventory-001",
            userText = "I bought eggs.",
        )

        val result = CommandEnvelopeCodec.parseProposal(
            request = request,
            rawOutput = """{"schema_version":"wf.ai.command-envelope.v1","commands":[""",
        )

        assertTrue(result is StructuredProposalResult.Rejected)
        val rejected = result as StructuredProposalResult.Rejected
        assertEquals(ProposalRejectionReason.MALFORMED_OUTPUT, rejected.reason)
        assertEquals(EnvelopeStatus.UNSUPPORTED, rejected.envelope.status)
        assertTrue(rejected.envelope.commands.isEmpty())
        assertFalse(rejected.envelope.hasMutatingCommands)
    }

    @Test
    fun unsupportedEnvelopeReturnsRejectedWithoutCommands() {
        val request = request(
            skillId = SkillId.NUTRITION_CORRECTION,
            idempotencyKey = "turn-local-unsupported-001",
            userText = "Tell me what dose of supplements treats my symptoms.",
        )

        val result = CommandEnvelopeCodec.parseProposal(request, UNSUPPORTED_GOLDEN)

        assertTrue(result is StructuredProposalResult.Rejected)
        val rejected = result as StructuredProposalResult.Rejected
        assertEquals(ProposalRejectionReason.UNSUPPORTED, rejected.reason)
        assertEquals(EnvelopeStatus.UNSUPPORTED, rejected.envelope.status)
        assertTrue(rejected.envelope.commands.isEmpty())
        assertEquals("unsupported_medical_advice", rejected.envelope.unsupported?.code)
    }

    @Test
    fun providerErrorPreservesRetryInputAndEvidence() {
        val secret = "test"
        val request = request(
            skillId = SkillId.SHOPPING,
            idempotencyKey = "turn-local-shopping-001",
            userText = "Need oats and bananas.",
        )
        val gateway = LiteLlmStructuredProposalGateway(
            config = LiteLlmProviderConfig(
                baseUrl = "https://litellm.example",
                model = "provider/model",
                apiKey = SecretString.of(secret),
            ),
            transport = StaticTransport(
                LiteLlmTransportResult.Error(
                    statusCode = 503,
                    message = "upstream rejected key $secret",
                    retryable = true,
                ),
            ),
        )

        val result = gateway.propose(request)

        assertTrue(result is StructuredProposalResult.ProviderFailure)
        val failure = result as StructuredProposalResult.ProviderFailure
        assertEquals(request, failure.retryState.request)
        assertEquals(request.evidence, failure.retryState.evidence)
        assertEquals(3, failure.retryState.maxAttempts)
        assertEquals(500L, failure.retryState.nextDelayMillis)
        assertTrue(failure.retryState.retryable)
        assertTrue(failure.envelope.commands.isEmpty())
        assertFalse(failure.error.message.contains(secret))
        assertTrue(failure.error.message.contains(SecretString.REDACTED))
    }

    @Test
    fun fakeGatewayIsDeterministicForSameRequest() {
        val fixture = CommandEnvelopeCodec.decode(INVENTORY_GOLDEN)
        val request = StructuredProposalRequestBuilder
            .forSkill(SkillId.INVENTORY)
            .userText("I bought a dozen eggs and two bags frozen berries.")
            .build()
        val gateway = FakeStructuredProposalGateway(listOf(fixture))

        val first = gateway.propose(request)
        val second = gateway.propose(request)

        assertTrue(first is StructuredProposalResult.Accepted)
        assertTrue(second is StructuredProposalResult.Accepted)
        assertEquals(
            CommandEnvelopeCodec.encode(first.envelope),
            CommandEnvelopeCodec.encode(second.envelope),
        )
    }

    @Test
    fun secretValuesAreRedactedFromSummaries() {
        val rawSecret = "test-value"
        val config = LiteLlmProviderConfig(
            baseUrl = "https://litellm.example",
            model = "provider/model",
            apiKey = SecretString.of(rawSecret),
        )

        assertFalse(config.toString().contains(rawSecret))
        assertFalse(config.loggableSummary().contains(rawSecret))
        assertEquals(SecretString.REDACTED, config.apiKey.toString())
        assertNotEquals(rawSecret, config.apiKey.toString())
    }

    private fun request(
        skillId: SkillId,
        idempotencyKey: String,
        userText: String,
    ): StructuredProposalRequest =
        StructuredProposalRequestBuilder
            .forSkill(skillId)
            .idempotencyKey(idempotencyKey)
            .userText(userText)
            .build()

    private class StaticTransport(
        private val result: LiteLlmTransportResult,
    ) : LiteLlmChatTransport {
        override fun complete(
            config: LiteLlmProviderConfig,
            request: LiteLlmChatRequest,
            timeoutPolicy: AiTimeoutPolicy,
            cancellationPolicy: AiCancellationPolicy,
        ): LiteLlmTransportResult = result
    }

    private companion object {
        const val INVENTORY_GOLDEN = """
{
  "schema_version": "wf.ai.command-envelope.v1",
  "catalog_version": "wf.ai.skill-catalog.v1",
  "skill_id": "inventory",
  "skill_version": "1.0.0",
  "envelope_id": "env_inventory_001",
  "idempotency_key": "turn-local-inventory-001",
  "status": "commands",
  "evidence": [
    {
      "evidence_id": "ev_user_1",
      "type": "user_text",
      "source_ref": "turn:user:local",
      "quote": "I bought a dozen eggs and two bags frozen berries.",
      "observed_at": null,
      "confidence": 1.0
    }
  ],
  "commands": [
    {
      "command_id": "cmd_1",
      "type": "inventory.add_lot",
      "summary": "Add 12 eggs to fridge inventory.",
      "payload": {
        "name": "Eggs",
        "quantity": {
          "amount": 12,
          "unit": "count",
          "text": "a dozen"
        },
        "storage_zone": "FRIDGE",
        "category": "protein",
        "source": "user_text"
      },
      "evidence_refs": ["ev_user_1"],
      "confidence": {
        "score": 0.94,
        "rationale": "Item, quantity, and likely storage zone are explicit."
      },
      "confirmation": {
        "required": false,
        "level": "review",
        "reason": "User should review normal inventory additions before apply.",
        "prompt": "Add eggs to the fridge?"
      },
      "destructive": false,
      "mutation": true
    },
    {
      "command_id": "cmd_2",
      "type": "inventory.add_lot",
      "summary": "Add two bags of frozen berries to freezer inventory.",
      "payload": {
        "name": "Frozen Berries",
        "quantity": {
          "amount": 2,
          "unit": "bag",
          "text": "two bags"
        },
        "storage_zone": "FREEZER",
        "category": "fruit",
        "source": "user_text"
      },
      "evidence_refs": ["ev_user_1"],
      "confidence": {
        "score": 0.96,
        "rationale": "Frozen implies freezer and quantity is explicit."
      },
      "confirmation": {
        "required": false,
        "level": "review",
        "reason": "User should review normal inventory additions before apply.",
        "prompt": "Add frozen berries to the freezer?"
      },
      "destructive": false,
      "mutation": true
    }
  ],
  "confidence": {
    "score": 0.95,
    "rationale": "Clear acquisition language with two food items."
  },
  "confirmation": {
    "required": false,
    "level": "review",
    "reason": "Non-destructive additions still need proposal review.",
    "prompt": "Review and add these inventory lots?"
  },
  "warnings": [],
  "unsupported": null
}
"""

        const val UNSUPPORTED_GOLDEN = """
{
  "schema_version": "wf.ai.command-envelope.v1",
  "catalog_version": "wf.ai.skill-catalog.v1",
  "skill_id": "nutrition_correction",
  "skill_version": "1.0.0",
  "envelope_id": "env_unsupported_001",
  "idempotency_key": "turn-local-unsupported-001",
  "status": "unsupported",
  "evidence": [
    {
      "evidence_id": "ev_user_1",
      "type": "user_text",
      "source_ref": "turn:user:local",
      "quote": "Tell me what dose of supplements treats my symptoms.",
      "observed_at": null,
      "confidence": 1.0
    }
  ],
  "commands": [],
  "confidence": {
    "score": 0.99,
    "rationale": "The request is clearly medical advice outside the food catalog."
  },
  "confirmation": {
    "required": false,
    "level": "none",
    "reason": "",
    "prompt": ""
  },
  "warnings": [
    {
      "code": "unsupported_medical_advice",
      "severity": "blocker",
      "message": "Medical treatment or supplement dosing is outside WonderFood AI commands.",
      "evidence_refs": ["ev_user_1"]
    }
  ],
  "unsupported": {
    "code": "unsupported_medical_advice",
    "message": "WonderFood can track food, preferences, and label-backed nutrition, but it cannot provide treatment advice.",
    "allowed_alternatives": [
      "Log a meal",
      "Save a food preference",
      "Record label nutrition"
    ]
  }
}
"""
    }
}
