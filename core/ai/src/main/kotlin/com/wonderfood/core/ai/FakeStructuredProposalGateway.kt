package com.wonderfood.core.ai

public class FakeStructuredProposalGateway(
    fixtures: List<CommandEnvelope>,
) : StructuredProposalGateway {
    private val fixturesByIdempotencyKey: Map<String, CommandEnvelope> =
        fixtures.associateBy { it.idempotencyKey }
    private val fixturesBySkill: Map<SkillId, List<CommandEnvelope>> =
        fixtures.groupBy { it.skillId }

    override fun propose(request: StructuredProposalRequest): StructuredProposalResult {
        val fixture = fixturesByIdempotencyKey[request.idempotencyKey]
            ?: fixturesBySkill[request.skillId]
                ?.firstOrNull { fixture ->
                    fixture.evidence.any { fixtureEvidence ->
                        request.evidence.any { requestEvidence ->
                            requestEvidence.quote.equals(fixtureEvidence.quote, ignoreCase = true)
                        }
                    }
                }
            ?: fixturesBySkill[request.skillId]?.firstOrNull()
            ?: return StructuredProposalResult.Rejected(
                request = request,
                envelope = SafeFailureEnvelopeFactory.fromRequest(
                    request = request,
                    code = "fake_fixture_missing",
                    message = "No fake proposal fixture is registered for this skill.",
                ),
                reason = ProposalRejectionReason.UNSUPPORTED,
                rawOutput = null,
            )

        val deterministic = fixture.copy(
            idempotencyKey = request.idempotencyKey,
            evidence = fixture.evidence.ifEmpty { request.evidence },
        )
        return CommandEnvelopeCodec.parseProposal(
            request = request,
            rawOutput = CommandEnvelopeCodec.encode(deterministic),
        )
    }
}
