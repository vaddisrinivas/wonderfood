package com.wonderfood.core.ai

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

public object CommandEnvelopeCodec {
    public val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = true
        encodeDefaults = true
    }

    public fun encode(envelope: CommandEnvelope): String =
        json.encodeToString(envelope)

    public fun decode(raw: String): CommandEnvelope =
        json.decodeFromString(extractJsonObject(raw))

    public fun parseProposal(
        request: StructuredProposalRequest,
        rawOutput: String,
        providerResponseId: String? = null,
    ): StructuredProposalResult =
        try {
            val envelope = decode(rawOutput)
            val validation = CommandEnvelopeValidator.validate(envelope, request)
            when {
                validation.errors.isNotEmpty() -> StructuredProposalResult.Rejected(
                    request = request,
                    envelope = SafeFailureEnvelopeFactory.fromRequest(
                        request = request,
                        code = "invalid_command_envelope",
                        message = "Provider returned a command envelope that failed validation.",
                    ),
                    reason = ProposalRejectionReason.INVALID_ENVELOPE,
                    rawOutput = rawOutput,
                    validationErrors = validation.errors,
                )

                envelope.status == EnvelopeStatus.UNSUPPORTED -> StructuredProposalResult.Rejected(
                    request = request,
                    envelope = envelope.copy(commands = emptyList()),
                    reason = ProposalRejectionReason.UNSUPPORTED,
                    rawOutput = rawOutput,
                )

                envelope.status == EnvelopeStatus.NEEDS_CLARIFICATION -> StructuredProposalResult.Rejected(
                    request = request,
                    envelope = envelope.copy(commands = emptyList()),
                    reason = ProposalRejectionReason.NEEDS_CLARIFICATION,
                    rawOutput = rawOutput,
                )

                else -> StructuredProposalResult.Accepted(
                    request = request,
                    envelope = envelope,
                    rawOutput = rawOutput,
                    providerResponseId = providerResponseId,
                )
            }
        } catch (error: IllegalArgumentException) {
            malformed(request, rawOutput, error.message.orEmpty())
        } catch (error: SerializationException) {
            malformed(request, rawOutput, error.message.orEmpty())
        }

    private fun malformed(
        request: StructuredProposalRequest,
        rawOutput: String,
        detail: String,
    ): StructuredProposalResult.Rejected =
        StructuredProposalResult.Rejected(
            request = request,
            envelope = SafeFailureEnvelopeFactory.fromRequest(
                request = request,
                code = "malformed_provider_output",
                message = "Provider returned malformed JSON; no commands are accepted.",
            ),
            reason = ProposalRejectionReason.MALFORMED_OUTPUT,
            rawOutput = rawOutput,
            validationErrors = listOf(detail).filter { it.isNotBlank() },
        )

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "No complete JSON object found." }
        return trimmed.substring(start, end + 1)
    }
}

public data class EnvelopeValidation(
    public val errors: List<String>,
)

public object CommandEnvelopeValidator {
    public fun validate(
        envelope: CommandEnvelope,
        request: StructuredProposalRequest? = null,
    ): EnvelopeValidation {
        val errors = mutableListOf<String>()
        if (envelope.schemaVersion != COMMAND_ENVELOPE_SCHEMA_VERSION) {
            errors += "Unsupported schema_version: ${envelope.schemaVersion}"
        }
        if (envelope.catalogVersion != SKILL_CATALOG_VERSION) {
            errors += "Unsupported catalog_version: ${envelope.catalogVersion}"
        }
        if (envelope.skillVersion != DEFAULT_SKILL_VERSION) {
            errors += "Unsupported skill_version: ${envelope.skillVersion}"
        }
        if (request != null && envelope.skillId != request.skillId) {
            errors += "Envelope skill_id ${envelope.skillId.wireName} does not match request ${request.skillId.wireName}."
        }
        if (request != null && envelope.idempotencyKey != request.idempotencyKey) {
            errors += "Envelope idempotency_key does not match request."
        }
        if (envelope.evidence.isEmpty()) {
            errors += "Envelope must include at least one evidence item."
        }
        if (
            (envelope.status == EnvelopeStatus.COMMANDS ||
                envelope.status == EnvelopeStatus.NEEDS_CONFIRMATION) &&
            envelope.commands.isEmpty()
        ) {
            errors += "${envelope.status.wireName} status requires at least one command."
        }
        if (
            (envelope.status == EnvelopeStatus.UNSUPPORTED ||
                envelope.status == EnvelopeStatus.NEEDS_CLARIFICATION) &&
            envelope.commands.isNotEmpty()
        ) {
            errors += "${envelope.status.wireName} status cannot include commands."
        }
        if (envelope.status == EnvelopeStatus.UNSUPPORTED && envelope.unsupported == null) {
            errors += "unsupported status requires unsupported details."
        }

        val evidenceIds = envelope.evidence.map { it.evidenceId }.toSet()
        envelope.commands.forEach { command ->
            if (command.summary.isBlank()) {
                errors += "${command.commandId} must include a summary."
            }
            if (command.evidenceRefs.isEmpty()) {
                errors += "${command.commandId} must reference evidence."
            }
            command.evidenceRefs
                .filterNot { it in evidenceIds }
                .forEach { ref -> errors += "${command.commandId} references unknown evidence $ref." }
            if (
                command.destructive &&
                (!command.confirmation.required || command.confirmation.level == ConfirmationLevel.NONE)
            ) {
                errors += "${command.commandId} is destructive but does not require confirmation."
            }
        }
        return EnvelopeValidation(errors = errors)
    }
}

public object SafeFailureEnvelopeFactory {
    public fun fromRequest(
        request: StructuredProposalRequest,
        code: String,
        message: String,
    ): CommandEnvelope {
        val evidence = request.evidence.ifEmpty {
            listOf(EvidenceAttachments.appContext(quote = "No provider evidence accepted."))
        }
        val evidenceRefs = evidence.map { it.evidenceId }
        return CommandEnvelope(
            schemaVersion = COMMAND_ENVELOPE_SCHEMA_VERSION,
            catalogVersion = SKILL_CATALOG_VERSION,
            skillId = request.skillId,
            skillVersion = request.skillVersion,
            envelopeId = "env_failed_${request.requestId}",
            idempotencyKey = request.idempotencyKey,
            status = EnvelopeStatus.UNSUPPORTED,
            evidence = evidence,
            commands = emptyList(),
            confidence = Confidence(
                score = 0.0,
                rationale = "Provider output was not accepted by the structured gateway.",
            ),
            confirmation = Confirmation(
                required = false,
                level = ConfirmationLevel.NONE,
                reason = "",
                prompt = "",
            ),
            warnings = listOf(
                CommandWarning(
                    code = code,
                    severity = WarningSeverity.BLOCKER,
                    message = message,
                    evidenceRefs = evidenceRefs,
                ),
            ),
            unsupported = UnsupportedReason(
                code = code,
                message = message,
                allowedAlternatives = listOf("Retry the same request", "Ask for clarification"),
            ),
        )
    }
}
