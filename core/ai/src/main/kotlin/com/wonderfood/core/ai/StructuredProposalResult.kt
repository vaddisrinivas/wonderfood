package com.wonderfood.core.ai

public interface StructuredProposalGateway {
    public fun propose(request: StructuredProposalRequest): StructuredProposalResult
}

public sealed class StructuredProposalResult {
    public abstract val request: StructuredProposalRequest
    public abstract val envelope: CommandEnvelope

    public data class Accepted(
        override val request: StructuredProposalRequest,
        override val envelope: CommandEnvelope,
        public val rawOutput: String,
        public val providerResponseId: String? = null,
    ) : StructuredProposalResult()

    public data class Rejected(
        override val request: StructuredProposalRequest,
        override val envelope: CommandEnvelope,
        public val reason: ProposalRejectionReason,
        public val rawOutput: String?,
        public val validationErrors: List<String> = emptyList(),
    ) : StructuredProposalResult()

    public data class ProviderFailure(
        override val request: StructuredProposalRequest,
        override val envelope: CommandEnvelope,
        public val error: ProposalProviderError,
        public val retryState: ProposalRetryState,
    ) : StructuredProposalResult()
}

public enum class ProposalRejectionReason {
    MALFORMED_OUTPUT,
    INVALID_ENVELOPE,
    NEEDS_CLARIFICATION,
    UNSUPPORTED,
}

public data class ProposalProviderError(
    public val statusCode: Int?,
    public val message: String,
    public val retryable: Boolean,
)

public data class ProposalRetryState(
    public val request: StructuredProposalRequest,
    public val attempt: Int,
    public val maxAttempts: Int,
    public val nextDelayMillis: Long?,
    public val retryable: Boolean,
) {
    public val evidence: List<CommandEvidence>
        get() = request.evidence
}
