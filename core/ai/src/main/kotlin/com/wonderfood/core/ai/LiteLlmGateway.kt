package com.wonderfood.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public class SecretString private constructor(
    private val value: String,
) {
    public fun reveal(): String = value

    override fun toString(): String = REDACTED

    override fun equals(other: Any?): Boolean =
        other is SecretString && value == other.value

    override fun hashCode(): Int = value.hashCode()

    public companion object {
        public const val REDACTED: String = "<redacted>"

        public fun of(value: String): SecretString = SecretString(value)
    }
}

public data class LiteLlmProviderConfig(
    public val baseUrl: String,
    public val model: String,
    public val apiKey: SecretString,
    public val temperature: Double = 0.1,
) {
    public fun loggableSummary(): String =
        "LiteLlmProviderConfig(baseUrl=$baseUrl, model=$model, apiKey=${SecretString.REDACTED}, temperature=$temperature)"

    override fun toString(): String = loggableSummary()
}

public object SecretRedactor {
    public fun redact(text: String, secrets: Iterable<SecretString>): String =
        secrets.fold(text) { current, secret ->
            val raw = secret.reveal()
            if (raw.isBlank()) current else current.replace(raw, SecretString.REDACTED)
        }
}

public data class AiTimeoutPolicy(
    public val connectTimeoutMillis: Long = 15_000,
    public val readTimeoutMillis: Long = 45_000,
    public val totalTimeoutMillis: Long = 60_000,
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive." }
        require(readTimeoutMillis > 0) { "readTimeoutMillis must be positive." }
        require(totalTimeoutMillis > 0) { "totalTimeoutMillis must be positive." }
    }
}

public data class AiRetryPolicy(
    public val maxAttempts: Int = 3,
    public val initialDelayMillis: Long = 500,
    public val maxDelayMillis: Long = 5_000,
    public val retryableStatusCodes: Set<Int> = setOf(408, 409, 425, 429, 500, 502, 503, 504),
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1." }
        require(initialDelayMillis >= 0) { "initialDelayMillis cannot be negative." }
        require(maxDelayMillis >= initialDelayMillis) { "maxDelayMillis must be >= initialDelayMillis." }
    }

    public fun nextDelayMillis(attempt: Int): Long? {
        if (attempt >= maxAttempts) return null
        val exponent = (attempt - 1).coerceAtLeast(0).coerceAtMost(30)
        val multiplier = 1L shl exponent
        return (initialDelayMillis * multiplier).coerceAtMost(maxDelayMillis)
    }

    public fun isRetryable(statusCode: Int?): Boolean =
        statusCode == null || statusCode in retryableStatusCodes
}

public data class AiCancellationPolicy(
    public val cancelBeforeProviderCall: Boolean = true,
    public val reason: String = "caller_cancelled",
)

@Serializable
public data class LiteLlmChatRequest(
    public val model: String,
    public val messages: List<LiteLlmChatMessage>,
    public val temperature: Double = 0.1,
    @SerialName("response_format")
    public val responseFormat: LiteLlmResponseFormat,
    public val metadata: JsonObject = buildJsonObject {},
)

@Serializable
public data class LiteLlmChatMessage(
    public val role: String,
    public val content: String,
)

@Serializable
public data class LiteLlmResponseFormat(
    public val type: String,
    @SerialName("json_schema")
    public val jsonSchema: LiteLlmJsonSchema? = null,
) {
    public companion object {
        public fun commandEnvelopeJsonSchema(): LiteLlmResponseFormat =
            LiteLlmResponseFormat(
                type = "json_schema",
                jsonSchema = LiteLlmJsonSchema(
                    name = "wonderfood_command_envelope_v1",
                    strict = true,
                    schema = CommandEnvelopeJsonSchema.schema(),
                ),
            )
    }
}

@Serializable
public data class LiteLlmJsonSchema(
    public val name: String,
    public val strict: Boolean,
    public val schema: JsonObject,
)

@Serializable
public data class LiteLlmChatResponse(
    public val id: String? = null,
    public val choices: List<LiteLlmChoice> = emptyList(),
    public val usage: LiteLlmUsage? = null,
    public val error: LiteLlmError? = null,
) {
    public fun firstMessageContent(): String? =
        choices.firstOrNull()?.message?.content
}

@Serializable
public data class LiteLlmChoice(
    public val index: Int = 0,
    public val message: LiteLlmAssistantMessage,
    @SerialName("finish_reason")
    public val finishReason: String? = null,
)

@Serializable
public data class LiteLlmAssistantMessage(
    public val role: String = "assistant",
    public val content: String,
)

@Serializable
public data class LiteLlmUsage(
    @SerialName("prompt_tokens")
    public val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    public val completionTokens: Int? = null,
    @SerialName("total_tokens")
    public val totalTokens: Int? = null,
)

@Serializable
public data class LiteLlmError(
    public val message: String? = null,
    public val type: String? = null,
    public val code: String? = null,
)

public sealed class LiteLlmTransportResult {
    public data class Success(
        public val response: LiteLlmChatResponse,
    ) : LiteLlmTransportResult()

    public data class Error(
        public val statusCode: Int?,
        public val message: String,
        public val retryable: Boolean = true,
    ) : LiteLlmTransportResult()
}

public interface LiteLlmChatTransport {
    public fun complete(
        config: LiteLlmProviderConfig,
        request: LiteLlmChatRequest,
        timeoutPolicy: AiTimeoutPolicy,
        cancellationPolicy: AiCancellationPolicy,
    ): LiteLlmTransportResult
}

public class LiteLlmStructuredChatRequestBuilder {
    public fun build(
        config: LiteLlmProviderConfig,
        request: StructuredProposalRequest,
    ): LiteLlmChatRequest =
        LiteLlmChatRequest(
            model = config.model,
            temperature = config.temperature,
            responseFormat = LiteLlmResponseFormat.commandEnvelopeJsonSchema(),
            messages = listOf(
                LiteLlmChatMessage(
                    role = "system",
                    content = SYSTEM_PROMPT,
                ),
                LiteLlmChatMessage(
                    role = "user",
                    content = buildUserContent(request),
                ),
            ),
            metadata = buildJsonObject {
                put("schema_version", COMMAND_ENVELOPE_SCHEMA_VERSION)
                put("catalog_version", request.catalogVersion)
                put("skill_id", request.skillId.wireName)
                put("skill_version", request.skillVersion)
                put("idempotency_key", request.idempotencyKey)
            },
        )

    private fun buildUserContent(request: StructuredProposalRequest): String =
        buildString {
            appendLine("Return exactly one $COMMAND_ENVELOPE_SCHEMA_VERSION object.")
            appendLine("Requested skill_id: ${request.skillId.wireName}")
            appendLine("Required skill_version: ${request.skillVersion}")
            appendLine("Required idempotency_key: ${request.idempotencyKey}")
            appendLine("Evidence JSON:")
            appendLine(CommandEnvelopeCodec.json.encodeToString(EVIDENCE_LIST_SERIALIZER, request.evidence))
        }

    private companion object {
        val EVIDENCE_LIST_SERIALIZER = ListSerializer(CommandEvidence.serializer())

        const val SYSTEM_PROMPT = """
You are WonderFood's structured proposal gateway.
Return JSON only.
Emit the command-envelope contract exactly.
Never emit SQL, database, DAO, Room, CRUD, or persistence instructions.
Use only catalog command types.
If output is uncertain, unsupported, or needs clarification, return no commands.
Destructive commands must require confirmation.
Do not include provider metadata or secrets in payloads.
"""
    }
}

public class LiteLlmStructuredProposalGateway(
    private val config: LiteLlmProviderConfig,
    private val transport: LiteLlmChatTransport,
    private val chatRequestBuilder: LiteLlmStructuredChatRequestBuilder = LiteLlmStructuredChatRequestBuilder(),
    private val retryPolicy: AiRetryPolicy = AiRetryPolicy(),
    private val timeoutPolicy: AiTimeoutPolicy = AiTimeoutPolicy(),
    private val cancellationPolicy: AiCancellationPolicy = AiCancellationPolicy(),
) : StructuredProposalGateway {
    override fun propose(request: StructuredProposalRequest): StructuredProposalResult {
        val chatRequest = chatRequestBuilder.build(config, request)
        val transportResult = try {
            transport.complete(config, chatRequest, timeoutPolicy, cancellationPolicy)
        } catch (error: Throwable) {
            LiteLlmTransportResult.Error(
                statusCode = null,
                message = error.message ?: error.javaClass.simpleName,
                retryable = true,
            )
        }

        return when (transportResult) {
            is LiteLlmTransportResult.Success -> {
                val content = transportResult.response.firstMessageContent()
                if (content.isNullOrBlank()) {
                    CommandEnvelopeCodec.parseProposal(
                        request = request,
                        rawOutput = "",
                        providerResponseId = transportResult.response.id,
                    )
                } else {
                    CommandEnvelopeCodec.parseProposal(
                        request = request,
                        rawOutput = content,
                        providerResponseId = transportResult.response.id,
                    )
                }
            }

            is LiteLlmTransportResult.Error -> providerFailure(
                request = request,
                statusCode = transportResult.statusCode,
                message = transportResult.message,
                retryable = transportResult.retryable && retryPolicy.isRetryable(transportResult.statusCode),
            )
        }
    }

    private fun providerFailure(
        request: StructuredProposalRequest,
        statusCode: Int?,
        message: String,
        retryable: Boolean,
    ): StructuredProposalResult.ProviderFailure {
        val redactedMessage = SecretRedactor.redact(message, listOf(config.apiKey))
        val error = ProposalProviderError(
            statusCode = statusCode,
            message = redactedMessage,
            retryable = retryable,
        )
        return StructuredProposalResult.ProviderFailure(
            request = request,
            envelope = SafeFailureEnvelopeFactory.fromRequest(
                request = request,
                code = "provider_error",
                message = "Provider failed before a command envelope was accepted.",
            ),
            error = error,
            retryState = ProposalRetryState(
                request = request,
                attempt = 1,
                maxAttempts = retryPolicy.maxAttempts,
                nextDelayMillis = retryPolicy.nextDelayMillis(1),
                retryable = retryable,
            ),
        )
    }
}

public object CommandEnvelopeJsonSchema {
    public fun schema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put(
            "required",
            buildJsonArray {
                listOf(
                    "schema_version",
                    "catalog_version",
                    "skill_id",
                    "skill_version",
                    "envelope_id",
                    "idempotency_key",
                    "status",
                    "evidence",
                    "commands",
                    "confidence",
                    "confirmation",
                    "warnings",
                    "unsupported",
                ).forEach { add(JsonPrimitive(it)) }
            },
        )
        put(
            "properties",
            buildJsonObject {
                constString("schema_version", COMMAND_ENVELOPE_SCHEMA_VERSION)
                constString("catalog_version", SKILL_CATALOG_VERSION)
                enumStrings("skill_id", SkillId.entries.map { it.wireName })
                constString("skill_version", DEFAULT_SKILL_VERSION)
                string("envelope_id")
                string("idempotency_key")
                enumStrings("status", EnvelopeStatus.entries.map { it.wireName })
                array("evidence", ref("#/\$defs/evidence"))
                array("commands", ref("#/\$defs/command"))
                put("confidence", ref("#/\$defs/confidence"))
                put("confirmation", ref("#/\$defs/confirmation"))
                array("warnings", ref("#/\$defs/warning"))
                put(
                    "unsupported",
                    buildJsonObject {
                        put("anyOf", buildJsonArray {
                            add(ref("#/\$defs/unsupported"))
                            add(buildJsonObject { put("type", "null") })
                        })
                    },
                )
            },
        )
        put(
            "\$defs",
            buildJsonObject {
                put("evidence", evidenceSchema())
                put("confidence", confidenceSchema())
                put("confirmation", confirmationSchema())
                put("warning", warningSchema())
                put("unsupported", unsupportedSchema())
                put("command", commandSchema())
            },
        )
    }

    private fun evidenceSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        required("evidence_id", "type", "source_ref", "quote", "observed_at", "confidence")
        put("properties", buildJsonObject {
            string("evidence_id")
            enumStrings("type", EvidenceType.entries.map { it.wireName })
            string("source_ref")
            string("quote")
            put("observed_at", nullableString())
            number("confidence")
        })
    }

    private fun confidenceSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        required("score", "rationale")
        put("properties", buildJsonObject {
            number("score")
            string("rationale")
        })
    }

    private fun confirmationSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        required("required", "level", "reason", "prompt")
        put("properties", buildJsonObject {
            put("required", buildJsonObject { put("type", "boolean") })
            enumStrings("level", ConfirmationLevel.entries.map { it.wireName })
            string("reason")
            string("prompt")
        })
    }

    private fun warningSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        required("code", "severity", "message", "evidence_refs")
        put("properties", buildJsonObject {
            string("code")
            enumStrings("severity", WarningSeverity.entries.map { it.wireName })
            string("message")
            array("evidence_refs", buildJsonObject { put("type", "string") })
        })
    }

    private fun unsupportedSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        required("code", "message", "allowed_alternatives")
        put("properties", buildJsonObject {
            string("code")
            string("message")
            array("allowed_alternatives", buildJsonObject { put("type", "string") })
        })
    }

    private fun commandSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        required(
            "command_id",
            "type",
            "summary",
            "payload",
            "evidence_refs",
            "confidence",
            "confirmation",
            "destructive",
            "mutation",
        )
        put("properties", buildJsonObject {
            string("command_id")
            enumStrings("type", CommandType.entries.map { it.wireName })
            string("summary")
            put("payload", buildJsonObject { put("type", "object") })
            array("evidence_refs", buildJsonObject { put("type", "string") })
            put("confidence", ref("#/\$defs/confidence"))
            put("confirmation", ref("#/\$defs/confirmation"))
            put("destructive", buildJsonObject { put("type", "boolean") })
            put("mutation", buildJsonObject { put("type", "boolean") })
        })
    }

    private fun JsonObjectBuilder.required(vararg names: String) {
        put("required", buildJsonArray { names.forEach { add(JsonPrimitive(it)) } })
    }

    private fun JsonObjectBuilder.string(name: String) {
        put(name, buildJsonObject { put("type", "string") })
    }

    private fun JsonObjectBuilder.number(name: String) {
        put(name, buildJsonObject { put("type", "number") })
    }

    private fun JsonObjectBuilder.constString(name: String, value: String) {
        put(name, buildJsonObject { put("const", value) })
    }

    private fun JsonObjectBuilder.enumStrings(name: String, values: List<String>) {
        put(name, buildJsonObject {
            put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
        })
    }

    private fun JsonObjectBuilder.array(name: String, itemSchema: JsonElement) {
        put(name, buildJsonObject {
            put("type", "array")
            put("items", itemSchema)
        })
    }

    private fun ref(path: String): JsonObject = buildJsonObject {
        put("\$ref", path)
    }

    private fun nullableString(): JsonObject = buildJsonObject {
        put("type", buildJsonArray {
            add(JsonPrimitive("string"))
            add(JsonPrimitive("null"))
        })
    }
}
