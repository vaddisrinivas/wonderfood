package com.wonderfood.core.ai

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public data class StructuredProposalRequest(
    public val requestId: String,
    public val skillId: SkillId,
    public val skillVersion: String,
    public val catalogVersion: String,
    public val evidence: List<CommandEvidence>,
    public val idempotencyKey: String,
) {
    public fun loggableSummary(): String =
        "StructuredProposalRequest(requestId=$requestId, skill=${skillId.wireName}, " +
            "evidence=${evidence.map { it.evidenceId to it.type.wireName }}, " +
            "idempotencyKey=$idempotencyKey)"
}

public class StructuredProposalRequestBuilder private constructor(
    private val skillId: SkillId,
) {
    private val evidence: MutableList<CommandEvidence> = mutableListOf()
    private var skillVersion: String = DEFAULT_SKILL_VERSION
    private var catalogVersion: String = SKILL_CATALOG_VERSION
    private var idempotencyKey: String? = null

    public fun skillVersion(version: String): StructuredProposalRequestBuilder = apply {
        skillVersion = version
    }

    public fun catalogVersion(version: String): StructuredProposalRequestBuilder = apply {
        catalogVersion = version
    }

    public fun idempotencyKey(value: String): StructuredProposalRequestBuilder = apply {
        idempotencyKey = value
    }

    public fun userText(
        text: String,
        sourceRef: String = "turn:user:local",
        observedAt: String? = null,
        confidence: Double = 1.0,
    ): StructuredProposalRequestBuilder = attachEvidence(
        type = EvidenceType.USER_TEXT,
        quote = text,
        sourceRef = sourceRef,
        observedAt = observedAt,
        confidence = confidence,
    )

    public fun attachEvidence(evidence: CommandEvidence): StructuredProposalRequestBuilder = apply {
        this.evidence += evidence
    }

    public fun attachEvidence(
        type: EvidenceType,
        quote: String,
        sourceRef: String,
        observedAt: String? = null,
        confidence: Double = 1.0,
    ): StructuredProposalRequestBuilder = apply {
        evidence += CommandEvidence(
            evidenceId = nextEvidenceId(type),
            type = type,
            sourceRef = sourceRef,
            quote = quote,
            observedAt = observedAt,
            confidence = confidence.coerceIn(0.0, 1.0),
        )
    }

    public fun build(
        idempotencyKeyGenerator: IdempotencyKeyGenerator = Sha256IdempotencyKeyGenerator,
    ): StructuredProposalRequest {
        require(evidence.isNotEmpty()) { "At least one evidence attachment is required." }
        val evidenceSnapshot = evidence.toList()
        val stableKey = idempotencyKey ?: idempotencyKeyGenerator.generate(
            skillId = skillId,
            skillVersion = skillVersion,
            evidence = evidenceSnapshot,
        )
        return StructuredProposalRequest(
            requestId = "req_${stableKey.takeLast(16).replace(Regex("[^A-Za-z0-9_]"), "_")}",
            skillId = skillId,
            skillVersion = skillVersion,
            catalogVersion = catalogVersion,
            evidence = evidenceSnapshot,
            idempotencyKey = stableKey,
        )
    }

    private fun nextEvidenceId(type: EvidenceType): String {
        val stem = when (type) {
            EvidenceType.USER_TEXT -> "user"
            else -> type.wireName
        }
        val count = evidence.count { it.type == type } + 1
        return "ev_${stem}_$count"
    }

    public companion object {
        public fun forSkill(skillId: SkillId): StructuredProposalRequestBuilder =
            StructuredProposalRequestBuilder(skillId)
    }
}

public object EvidenceAttachments {
    public fun userText(
        quote: String,
        evidenceId: String = "ev_user_1",
        sourceRef: String = "turn:user:local",
        observedAt: String? = null,
        confidence: Double = 1.0,
    ): CommandEvidence = evidence(
        evidenceId = evidenceId,
        type = EvidenceType.USER_TEXT,
        sourceRef = sourceRef,
        quote = quote,
        observedAt = observedAt,
        confidence = confidence,
    )

    public fun appContext(
        quote: String,
        evidenceId: String = "ev_app_context_1",
        sourceRef: String = "app:context",
        observedAt: String? = null,
        confidence: Double = 1.0,
    ): CommandEvidence = evidence(
        evidenceId = evidenceId,
        type = EvidenceType.APP_CONTEXT,
        sourceRef = sourceRef,
        quote = quote,
        observedAt = observedAt,
        confidence = confidence,
    )

    public fun evidence(
        evidenceId: String,
        type: EvidenceType,
        sourceRef: String,
        quote: String,
        observedAt: String? = null,
        confidence: Double = 1.0,
    ): CommandEvidence = CommandEvidence(
        evidenceId = evidenceId,
        type = type,
        sourceRef = sourceRef,
        quote = quote,
        observedAt = observedAt,
        confidence = confidence.coerceIn(0.0, 1.0),
    )
}

public interface IdempotencyKeyGenerator {
    public fun generate(
        skillId: SkillId,
        skillVersion: String,
        evidence: List<CommandEvidence>,
    ): String
}

public object Sha256IdempotencyKeyGenerator : IdempotencyKeyGenerator {
    override fun generate(
        skillId: SkillId,
        skillVersion: String,
        evidence: List<CommandEvidence>,
    ): String {
        val canonical = buildString {
            append(COMMAND_ENVELOPE_SCHEMA_VERSION)
            append('|')
            append(SKILL_CATALOG_VERSION)
            append('|')
            append(skillId.wireName)
            append('|')
            append(skillVersion)
            evidence.forEach { item ->
                append('|')
                append(item.evidenceId)
                append(':')
                append(item.type.wireName)
                append(':')
                append(item.sourceRef)
                append(':')
                append(item.quote)
                append(':')
                append(item.observedAt.orEmpty())
                append(':')
                append(item.confidence)
            }
        }
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return "wf-ai-v1-${digest.toHex().take(32)}"
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }
}
