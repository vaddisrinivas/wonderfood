package com.wonderfood.app.ai

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.wonderfood.app.data.AiTurn
import com.wonderfood.app.data.ChatSourceRef
import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodDraftNormalizer
import com.wonderfood.app.data.FoodDraftValidator
import com.wonderfood.app.data.HouseholdUiMemory
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealPlanEntryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.ReceiptItemDisposition
import com.wonderfood.app.data.ReceiptItemDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.foodEmojiForName
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

sealed interface LiteLlmInterpretation {
    val diagnostic: String

    data class Success(
        val turn: AiTurn,
        override val diagnostic: String,
        val sources: List<ChatSourceRef> = emptyList(),
    ) : LiteLlmInterpretation

    data class Failure(
        override val diagnostic: String,
    ) : LiteLlmInterpretation
}

private data class ProviderJsonResult(
    val json: JSONObject?,
    val diagnostic: String,
)

class LiteLlmFoodInterpreter(
    private val systemPrompt: String = SYSTEM_PROMPT,
    private val bundledSkill: String = "",
) {
    fun isConfigured(config: LiteLlmConfig): Boolean = config.isUsable

    fun interpret(text: String, memory: HouseholdUiMemory, configs: List<LiteLlmConfig>): AiTurn? =
        configs.firstNotNullOfOrNull { config -> interpret(text, memory, config) }

    fun testConnection(config: LiteLlmConfig): Result<String> =
        runCatching {
            require(config.isUsable) { "Provider URL, model, and API key are required." }
            val body = JSONObject()
                .put("model", config.model)
                .put("temperature", 0)
                .put("max_tokens", 32)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "Reply with a short connection check."))
                        .put(JSONObject().put("role", "user").put("content", "Say ok.")),
                )
            val response = postJsonWithDiagnostics(config, body)
            val json = response.json ?: error(response.diagnostic)
            require(json.assistantContent(config).isNotBlank()) {
                "${response.diagnostic}; empty assistant content"
            }
            "Connected: ${config.statusLabel}"
        }

    fun interpret(text: String, memory: HouseholdUiMemory, config: LiteLlmConfig): AiTurn? {
        return (interpretWithDiagnostics(text, memory, config) as? LiteLlmInterpretation.Success)?.turn
    }

    fun interpretWithDiagnostics(
        text: String,
        memory: HouseholdUiMemory,
        config: LiteLlmConfig,
        sourceContext: String = "",
    ): LiteLlmInterpretation {
        if (!config.isUsable) return LiteLlmInterpretation.Failure("Provider is not configured.")
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.1)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", effectiveSystemPrompt(memory)))
                    .put(JSONObject().put("role", "user").put("content", buildUserPrompt(text, memory, sourceContext))),
            )

        val response = postJsonWithDiagnostics(config, body)
        val json = response.json ?: return LiteLlmInterpretation.Failure(response.diagnostic)
        val content = json.assistantContent(config)
        if (content.isBlank()) {
            return LiteLlmInterpretation.Failure("${response.diagnostic}; empty assistant content")
        }
        val turn = parseTurn(content, text, memory)
            ?: return LiteLlmInterpretation.Failure("${response.diagnostic}; invalid WonderFood JSON")
        return validateProviderTurn(turn, response.diagnostic, json.responseSourceRefs(config))
    }

    fun interpretReceiptPhoto(
        context: Context,
        uri: Uri,
        memory: HouseholdUiMemory,
        configs: List<LiteLlmConfig>,
        userNote: String = "",
    ): AiTurn? {
        if (configs.none { it.isUsable }) return null
        val dataUri = context.contentResolver.openInputStream(uri)?.use { stream ->
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val base64 = Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
            "data:$mimeType;base64,$base64"
        } ?: return null
        return configs.firstNotNullOfOrNull { config ->
            interpretReceiptDataUri(dataUri, memory, config, userNote)
        }
    }

    fun interpretReceiptPhoto(
        context: Context,
        uri: Uri,
        memory: HouseholdUiMemory,
        config: LiteLlmConfig,
        userNote: String = "",
    ): AiTurn? {
        if (!config.isUsable) return null
        val dataUri = context.contentResolver.openInputStream(uri)?.use { stream ->
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val base64 = Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
            "data:$mimeType;base64,$base64"
        } ?: return null
        return interpretReceiptDataUri(dataUri, memory, config, userNote)
    }

    private fun interpretReceiptDataUri(
        dataUri: String,
        memory: HouseholdUiMemory,
        config: LiteLlmConfig,
        userNote: String,
    ): AiTurn? {
        if (!config.isUsable) return null
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.1)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", effectiveSystemPrompt(memory)))
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("type", "text")
                                            .put(
                                                "text",
                                                buildUserPrompt(
                                                    text = buildString {
                                                        append(
                                                            "Read this as a purchased receipt. Account for every visible line. " +
                                                                "Return one receipt draft. Purchased food defaults to INVENTORY, " +
                                                                "household/non-food lines use HOUSEHOLD, and tax/discount/payment lines use IGNORE. " +
                                                                "Never put receipt purchases on the grocery/to-buy list. For each food infer the " +
                                                                "best storage zone, canonical category, emoji, serving and nutrition when defensible, " +
                                                                "and a conservative best-before date. Mark estimates as estimates, preserve the visible " +
                                                                "receipt line as evidence, add confidence and warnings, and use null rather than inventing facts. " +
                                                                "Capture merchant, store location, purchase date, currency, each line price, subtotal, tax, and total " +
                                                                "when visible. Prices must be integer minor units such as cents; never infer unreadable prices.",
                                                        )
                                                        userNote.trim().takeIf { it.isNotBlank() }?.let { note ->
                                                            append("\nUser-supplied receipt context: ")
                                                            append(note)
                                                        }
                                                    },
                                                    memory = memory,
                                                ),
                                            ),
                                    )
                                    .put(
                                        JSONObject()
                                            .put("type", "image_url")
                                            .put("image_url", JSONObject().put("url", dataUri)),
                                    ),
                            ),
                    ),
            )
        val response = postJsonWithDiagnostics(config, body).json ?: return null
        val content = response.assistantContent(config)
        if (content.isBlank()) return null
        return parseTurn(content, userNote.ifBlank { "receipt photo" }, memory)
            ?.let { turn -> turn.copy(draft = turn.draft.asReceiptDraft()) }
            ?.let { turn -> (validateProviderTurn(turn, "receipt photo provider") as? LiteLlmInterpretation.Success)?.turn }
    }

    private fun FoodDraft?.asReceiptDraft(): FoodDraft? =
        when (this) {
            null -> null
            is ReceiptDraft -> this
            is InventoryDraft -> ReceiptDraft(
                items = items.map { food -> ReceiptItemDraft(food = food) },
                sourceLabel = "receipt_photo_ai_legacy_inventory",
            )
            is GroceryDraft -> ReceiptDraft(
                items = items.map { food -> ReceiptItemDraft(food = food) },
                sourceLabel = "receipt_photo_ai_legacy_grocery_corrected",
            )
            is CompositeDraft -> {
                val receiptItems = drafts.flatMap { child ->
                    when (val converted = child.asReceiptDraft()) {
                        is ReceiptDraft -> converted.items
                        else -> emptyList()
                    }
                }
                ReceiptDraft(items = receiptItems, sourceLabel = "receipt_photo_ai_composite")
            }
            else -> null
        }

    private fun validateProviderTurn(
        turn: AiTurn,
        diagnostic: String,
        sources: List<ChatSourceRef> = emptyList(),
    ): LiteLlmInterpretation {
        val normalizedDraft = turn.draft?.let(FoodDraftNormalizer::normalize)
        val errors = normalizedDraft?.let(FoodDraftValidator::validate).orEmpty()
        if (errors.isNotEmpty()) {
            return LiteLlmInterpretation.Failure(
                "$diagnostic; invalid draft: ${errors.joinToString("; ")}",
            )
        }
        return LiteLlmInterpretation.Success(
            turn.copy(draft = normalizedDraft),
            "$diagnostic; parsed WonderFood proposal",
            sources = sources,
        )
    }

    private fun postJsonWithDiagnostics(config: LiteLlmConfig, body: JSONObject): ProviderJsonResult {
        if (config.provider == AiProvider.ANTHROPIC) return postAnthropicJsonWithDiagnostics(config, body)
        val endpoint = config.requestEndpoint()
        val requestBody = body.requestBodyFor(config)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            when (config.provider) {
                AiProvider.AZURE_OPENAI -> setRequestProperty("api-key", config.apiKey)
                AiProvider.ANTHROPIC -> setRequestProperty("x-api-key", config.apiKey)
                AiProvider.OPENAI_COMPATIBLE -> setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            setRequestProperty("Content-Type", "application/json")
        }
        return runCatching {
            OutputStreamWriter(connection.outputStream).use { writer -> writer.write(requestBody.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                ProviderJsonResult(
                    json = null,
                    diagnostic = "${config.statusLabel} HTTP $code: ${text.safeProviderExcerpt(config.apiKey)}",
                )
            } else {
                ProviderJsonResult(
                    json = JSONObject(text),
                    diagnostic = "${config.statusLabel} HTTP $code",
                )
            }
        }.getOrElse { error ->
            ProviderJsonResult(
                json = null,
                diagnostic = "${config.statusLabel} ${error::class.java.simpleName}: ${error.message.orEmpty().safeProviderExcerpt(config.apiKey)}",
            )
        }.also {
            connection.disconnect()
        }
    }

    private fun postAnthropicJsonWithDiagnostics(config: LiteLlmConfig, body: JSONObject): ProviderJsonResult {
        val endpoint = config.baseUrl.trimEnd('/') + "/v1/messages"
        val requestBody = body.toAnthropicRequest(config)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("x-api-key", config.apiKey)
            setRequestProperty("anthropic-version", config.apiVersion.ifBlank { "2023-06-01" })
            setRequestProperty("Content-Type", "application/json")
        }
        return runCatching {
            OutputStreamWriter(connection.outputStream).use { writer -> writer.write(requestBody.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                ProviderJsonResult(
                    json = null,
                    diagnostic = "${config.statusLabel} HTTP $code: ${text.safeProviderExcerpt(config.apiKey)}",
                )
            } else {
                ProviderJsonResult(
                    json = JSONObject()
                        .put(
                            "choices",
                            JSONArray().put(
                                JSONObject().put(
                                    "message",
                                    JSONObject().put("content", JSONObject(text).anthropicTextContent()),
                                ),
                            ),
                        ),
                    diagnostic = "${config.statusLabel} HTTP $code",
                )
            }
        }.getOrElse { error ->
            ProviderJsonResult(
                json = null,
                diagnostic = "${config.statusLabel} ${error::class.java.simpleName}: ${error.message.orEmpty().safeProviderExcerpt(config.apiKey)}",
            )
        }.also {
            connection.disconnect()
        }
    }

    private fun LiteLlmConfig.requestEndpoint(): String =
        when (provider) {
            AiProvider.OPENAI_COMPATIBLE -> openAiCompatibleEndpoint()
            AiProvider.AZURE_OPENAI -> azureOpenAiEndpoint()
            AiProvider.ANTHROPIC -> baseUrl.trimEnd('/') + "/v1/messages"
        }

    private fun LiteLlmConfig.openAiCompatibleEndpoint(): String {
        val trimmed = baseUrl.trim()
        return if (usesExplicitOpenAiEndpoint()) trimmed else trimmed.appendUrlPath("chat/completions")
    }

    private fun LiteLlmConfig.azureOpenAiEndpoint(): String {
        val trimmed = baseUrl.trim()
        val endpoint = when {
            usesExplicitOpenAiEndpoint() -> trimmed
            usesAzureV1Api() -> trimmed.appendUrlPath("chat/completions")
            else -> {
                val encodedDeployment = URLEncoder.encode(model.trim(), StandardCharsets.UTF_8.name())
                trimmed.appendUrlPath("openai/deployments/$encodedDeployment/chat/completions")
            }
        }
        return endpoint.withAzureApiVersion(apiVersion)
    }

    private fun LiteLlmConfig.usesExplicitOpenAiEndpoint(): Boolean {
        val path = baseUrl.substringBefore('?').trimEnd('/').lowercase()
        return path.endsWith("/chat/completions") || path.endsWith("/responses")
    }

    private fun LiteLlmConfig.usesResponsesApi(): Boolean =
        baseUrl.substringBefore('?').trimEnd('/').endsWith("/responses", ignoreCase = true)

    private fun LiteLlmConfig.usesAzureV1Api(): Boolean =
        baseUrl.substringBefore('?').trimEnd('/').lowercase().let { path ->
            path.endsWith("/openai/v1") || "/openai/v1/" in path
        }

    private fun LiteLlmConfig.usesOfficialOpenAiResponsesApi(): Boolean =
        provider == AiProvider.OPENAI_COMPATIBLE &&
            usesResponsesApi() &&
            "api.openai.com" in baseUrl.lowercase()

    private fun String.appendUrlPath(path: String): String {
        val base = substringBefore('?').trimEnd('/')
        val query = substringAfter('?', "")
        return "$base/$path" + query.takeIf(String::isNotBlank)?.let { "?$it" }.orEmpty()
    }

    private fun String.withAzureApiVersion(apiVersion: String): String {
        if (apiVersion.isBlank() || contains("api-version=", ignoreCase = true)) return this
        val separator = if (contains("?")) "&" else "?"
        val encodedVersion = URLEncoder.encode(apiVersion.trim(), StandardCharsets.UTF_8.name())
        return "$this${separator}api-version=$encodedVersion"
    }

    private fun JSONObject.requestBodyFor(config: LiteLlmConfig): JSONObject {
        if (config.usesResponsesApi()) return toResponsesRequest(config)
        val copy = JSONObject(toString())
        if (config.provider == AiProvider.AZURE_OPENAI) {
            if (!config.usesAzureV1Api()) copy.remove("model")
            copy.remove("temperature")
            if (copy.has("max_tokens") && !copy.has("max_completion_tokens")) {
                copy.put("max_completion_tokens", copy.optInt("max_tokens"))
                copy.remove("max_tokens")
            }
        }
        return copy
    }

    private fun JSONObject.toResponsesRequest(config: LiteLlmConfig): JSONObject {
        val request = JSONObject().put("model", config.model)
        if (config.provider != AiProvider.AZURE_OPENAI && has("temperature")) {
            request.put("temperature", opt("temperature"))
        }
        when {
            has("max_output_tokens") -> request.put("max_output_tokens", opt("max_output_tokens"))
            has("max_completion_tokens") -> request.put("max_output_tokens", opt("max_completion_tokens"))
            has("max_tokens") -> request.put("max_output_tokens", opt("max_tokens"))
        }
        optJSONObject("response_format")?.let { format ->
            request.put("text", JSONObject().put("format", JSONObject(format.toString())))
        }
        if (config.usesOfficialOpenAiResponsesApi()) {
            request
                .put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
                .put("include", JSONArray().put("web_search_call.action.sources"))
        }

        val instructions = mutableListOf<String>()
        val input = JSONArray()
        val sourceMessages = optJSONArray("messages") ?: JSONArray()
        for (index in 0 until sourceMessages.length()) {
            val message = sourceMessages.optJSONObject(index) ?: continue
            val role = message.optString("role")
            val content = message.opt("content")
            if (role == "system") {
                content.promptText().takeIf(String::isNotBlank)?.let(instructions::add)
                continue
            }
            val responseContent = content.toResponsesContent()
            if (responseContent.length() > 0) {
                input.put(
                    JSONObject()
                        .put("role", role.ifBlank { "user" })
                        .put("content", responseContent),
                )
            }
        }
        if (instructions.isNotEmpty()) request.put("instructions", instructions.joinToString("\n\n"))
        request.put("input", input)
        return request
    }

    private fun Any?.promptText(): String =
        when (this) {
            is String -> this
            is JSONArray -> buildList {
                for (index in 0 until length()) {
                    val part = optJSONObject(index) ?: continue
                    part.optString("text").takeIf(String::isNotBlank)?.let(::add)
                }
            }.joinToString("\n")
            else -> ""
        }.trim()

    private fun Any?.toResponsesContent(): JSONArray {
        val result = JSONArray()
        when (this) {
            is String -> result.put(JSONObject().put("type", "input_text").put("text", this))
            is JSONArray -> {
                for (index in 0 until length()) {
                    val part = optJSONObject(index) ?: continue
                    when (part.optString("type")) {
                        "text", "input_text" -> result.put(
                            JSONObject()
                                .put("type", "input_text")
                                .put("text", part.optString("text")),
                        )
                        "image_url", "input_image" -> {
                            val imageUrl = when (val raw = part.opt("image_url")) {
                                is JSONObject -> raw.optString("url")
                                is String -> raw
                                else -> ""
                            }
                            if (imageUrl.isNotBlank()) {
                                result.put(
                                    JSONObject()
                                        .put("type", "input_image")
                                        .put("image_url", imageUrl),
                                )
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    private fun JSONObject.assistantContent(config: LiteLlmConfig): String =
        if (config.usesResponsesApi()) {
            responsesTextContent()
        } else {
            optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
        }

    private fun JSONObject.responsesTextContent(): String {
        optString("output_text").trim().takeIf(String::isNotBlank)?.let { return it }
        val output = optJSONArray("output") ?: return ""
        return buildList {
            for (outputIndex in 0 until output.length()) {
                val message = output.optJSONObject(outputIndex) ?: continue
                val content = message.optJSONArray("content") ?: continue
                for (contentIndex in 0 until content.length()) {
                    val part = content.optJSONObject(contentIndex) ?: continue
                    if (part.optString("type") != "output_text") continue
                    when (val text = part.opt("text")) {
                        is String -> text
                        is JSONObject -> text.optString("value")
                        else -> ""
                    }.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.joinToString("\n").trim()
    }

    private fun JSONObject.responseSourceRefs(config: LiteLlmConfig): List<ChatSourceRef> {
        if (!config.usesResponsesApi()) return emptyList()
        val output = optJSONArray("output") ?: return emptyList()
        return buildList {
            for (outputIndex in 0 until output.length()) {
                val item = output.optJSONObject(outputIndex) ?: continue
                if (item.optString("type") == "web_search_call") {
                    item.optJSONObject("action")
                        ?.optJSONArray("sources")
                        ?.let { sources ->
                            for (sourceIndex in 0 until sources.length()) {
                                val source = sources.optJSONObject(sourceIndex) ?: continue
                                val url = source.optString("url").trim()
                                val title = source.optString("title").trim().ifBlank { url }
                                if (title.isBlank()) continue
                                add(
                                    ChatSourceRef(
                                        title = title.take(80),
                                        detail = "OpenAI web search source",
                                        quote = source.optString("snippet").trim().take(240),
                                        uri = url.take(512),
                                    ),
                                )
                            }
                        }
                }
                val content = item.optJSONArray("content") ?: continue
                for (contentIndex in 0 until content.length()) {
                    val part = content.optJSONObject(contentIndex) ?: continue
                    val text = part.outputTextValue()
                    val annotations = part.optJSONArray("annotations") ?: continue
                    for (annotationIndex in 0 until annotations.length()) {
                        val annotation = annotations.optJSONObject(annotationIndex) ?: continue
                        when (annotation.optString("type")) {
                            "url_citation" -> {
                                val url = annotation.optString("url").trim()
                                val title = annotation.optString("title").trim().ifBlank { url }
                                if (title.isBlank()) continue
                                add(
                                    ChatSourceRef(
                                        title = title.take(80),
                                        detail = "OpenAI URL citation",
                                        quote = text.citationSnippet(
                                            annotation.optInt("start_index", -1),
                                            annotation.optInt("end_index", -1),
                                        ),
                                        uri = url.take(512),
                                    ),
                                )
                            }
                            "file_citation" -> {
                                val filename = annotation.optString("filename").trim()
                                if (filename.isNotBlank()) {
                                    add(ChatSourceRef(filename.take(80), "OpenAI file citation"))
                                }
                            }
                            "container_file_citation" -> {
                                val filename = annotation.optString("filename").trim()
                                if (filename.isNotBlank()) {
                                    add(ChatSourceRef(filename.take(80), "OpenAI container file citation"))
                                }
                            }
                        }
                    }
                }
            }
        }.distinctBy { "${it.title}|${it.uri}" }.take(6)
    }

    private fun JSONObject.outputTextValue(): String =
        when (val text = opt("text")) {
            is String -> text
            is JSONObject -> text.optString("value")
            else -> ""
        }

    private fun JSONObject.toAnthropicRequest(config: LiteLlmConfig): JSONObject {
        val sourceMessages = optJSONArray("messages") ?: JSONArray()
        var system = ""
        val messages = JSONArray()
        for (index in 0 until sourceMessages.length()) {
            val message = sourceMessages.optJSONObject(index) ?: continue
            when (message.optString("role")) {
                "system" -> system = message.optString("content")
                "user", "assistant" -> messages.put(
                    JSONObject()
                        .put("role", message.optString("role"))
                        .put("content", message.opt("content")),
                )
            }
        }
        return JSONObject()
            .put("model", config.model)
            .put("max_tokens", optInt("max_tokens", 1800))
            .put("system", system)
            .put("messages", messages)
    }

    private fun JSONObject.anthropicTextContent(): String {
        val content = optJSONArray("content") ?: return ""
        return buildString {
            for (index in 0 until content.length()) {
                val part = content.optJSONObject(index) ?: continue
                if (part.optString("type") == "text") append(part.optString("text"))
            }
        }.trim()
    }

    private fun parseTurn(raw: String, userText: String, memory: HouseholdUiMemory): AiTurn? =
        runCatching {
            CommandEnvelopeDraftMapper.tryMap(raw)?.let { return@runCatching it }
            val jsonText = raw.substringAfter('{', raw).let { "{" + it.substringBeforeLast('}', it) + "}" }
            val json = JSONObject(jsonText)
            val reply = json.optString("reply").ifBlank { "I drafted a food memory update." }
            val draft = when {
                json.optJSONArray("drafts") != null -> parseDrafts(json.optJSONArray("drafts"))
                json.opt("draft") is JSONArray -> parseDrafts(json.optJSONArray("draft"))
                json.optJSONObject("draft") != null -> parseDraft(json.optJSONObject("draft")!!)
                else -> null
            }
            recipeClarificationReply(userText)?.let { clarification ->
                AiTurn(reply = clarification, draft = null)
            } ?: nutritionClarificationReply(userText)?.let { clarification ->
                AiTurn(reply = clarification, draft = null)
            } ?: AiTurn(reply = reply, draft = draft)
        }.getOrNull()

    private fun parseDrafts(array: JSONArray?): FoodDraft? {
        if (array == null) return null
        val drafts = buildList {
            for (index in 0 until array.length()) {
                val draft = array.optJSONObject(index)?.let { parseDraft(it) } ?: continue
                add(draft)
            }
        }
        return when (drafts.size) {
            0 -> null
            1 -> drafts.first()
            else -> CompositeDraft(drafts)
        }
    }

    private fun parseDraft(json: JSONObject): FoodDraft? =
        when (json.optString("type").lowercase()) {
            "inventory" -> InventoryDraft(items = parseItems(json.optJSONArray("items")))
            "grocery" -> GroceryDraft(items = parseItems(json.optJSONArray("items")))
            "receipt" -> parseReceiptDraft(json)
            "recipe" -> RecipeDraft(
                titleText = json.optString("title", "Personal recipe"),
                ingredientsText = json.optString("ingredients", ""),
                stepsText = json.optString("steps").ifBlank { json.optString("cooking_process") },
                servings = json.optNullableInt("servings"),
                prepMinutes = json.optNullableInt("prep_minutes"),
                tags = json.optJSONArray("tags")?.toStringList()?.joinToString(", ").orEmpty(),
                imageUri = json.optDisplayImage(json.optString("title", "Personal recipe")),
                imageUrl = json.optRemoteImageUrl(),
            )
            "meal_log" -> MealLogDraft(
                titleText = json.optString("title", "Meal from chat"),
                calories = json.optNullableInt("calories"),
                proteinGrams = json.optNullableDouble("protein_g"),
                carbsGrams = json.optNullableDouble("carbs_g"),
                fatGrams = json.optNullableDouble("fat_g"),
                mealSlot = parseMealSlot(json.optString("slot")),
                usedItemsText = json.optJSONArray("used_items")?.toStringList()?.joinToString(", ").orEmpty(),
                source = "litellm_ai_estimate",
            )
            "meal_plan" -> MealPlanDraft(
                titleText = json.optString("title", "Meal plan"),
                daysText = json.optString("days", ""),
                groceryHint = json.optString("grocery_hint", ""),
                entries = parseMealPlanEntries(json.optJSONArray("entries")),
            )
            else -> null
        }

    private fun parseItems(array: JSONArray?): List<FoodCandidate> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                add(
                    FoodCandidate(
                        name = name,
                        quantity = item.optString("quantity").trim(),
                        zone = parseZone(item.optString("zone")),
                        category = item.optString("category").trim(),
                        servingText = item.optString("serving_text").trim(),
                        calories = item.optNullableInt("calories"),
                        proteinGrams = item.optNullableDouble("protein_g"),
                        carbsGrams = item.optNullableDouble("carbs_g"),
                        fatGrams = item.optNullableDouble("fat_g"),
                        nutritionSource = item.optString("nutrition_source").trim(),
                        notes = item.optString("notes").trim(),
                        imageUri = item.optDisplayImage(name),
                        imageUrl = item.optRemoteImageUrl(),
                        expiresAtMillis = item.optExpiryMillis(),
                        confidence = item.optDouble("confidence", 0.75),
                        evidence = item.optString("evidence").ifBlank { item.optString("receipt_line") }.trim(),
                        zoneSource = item.optString("zone_source").trim(),
                        expirySource = item.optString("expiry_source").trim(),
                        warnings = item.optJSONArray("warnings")?.toStringList().orEmpty(),
                    ),
                )
            }
        }.take(20)
    }

    private fun parseReceiptDraft(json: JSONObject): ReceiptDraft {
        val array = json.optJSONArray("items")
        val items = buildList {
            if (array != null) {
                for (index in 0 until array.length()) {
                    val rawItem = array.optJSONObject(index) ?: continue
                    val food = parseItems(JSONArray().put(rawItem)).firstOrNull() ?: continue
                    val disposition = when (rawItem.optString("disposition").uppercase()) {
                        "HOUSEHOLD", "NON_FOOD", "NONFOOD" -> ReceiptItemDisposition.HOUSEHOLD
                        "IGNORE", "DISCOUNT", "PAYMENT", "TAX" -> ReceiptItemDisposition.IGNORE
                        else -> ReceiptItemDisposition.INVENTORY
                    }
                    add(
                        ReceiptItemDraft(
                            food = food.copy(
                                zoneSource = food.zoneSource.ifBlank {
                                    if (rawItem.optString("zone").isBlank()) "" else "ai_receipt_inference"
                                },
                                expirySource = food.expirySource.ifBlank {
                                    if (food.expiresAtMillis == null) "" else "ai_shelf_life_estimate"
                                },
                            ),
                            disposition = disposition,
                            receiptLine = rawItem.optString("receipt_line").ifBlank { rawItem.optString("evidence") }.trim(),
                            linePriceCents = rawItem.optMoneyCents("line_price_cents", "line_total", "price"),
                        ),
                    )
                }
            }
        }.take(40)
        return ReceiptDraft(
            items = items,
            merchant = json.optString("merchant").trim(),
            storeLocation = json.optString("store_location").ifBlank { json.optString("location") }.trim(),
            purchasedAtMillis = json.optPurchasedAtMillis(),
            currencyCode = json.optString("currency_code").ifBlank { json.optString("currency") }.trim().uppercase().ifBlank { "USD" },
            subtotalCents = json.optMoneyCents("subtotal_cents", "subtotal"),
            taxCents = json.optMoneyCents("tax_cents", "tax"),
            totalCents = json.optMoneyCents("total_cents", "total"),
            rawText = json.optString("raw_text").trim(),
            sourceLabel = "receipt_photo_ai",
        )
    }

    private fun parseMealPlanEntries(array: JSONArray?): List<MealPlanEntryDraft> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val title = entry.optString("title").trim()
                if (title.isBlank()) continue
                add(
                    MealPlanEntryDraft(
                        dayOffset = entry.optInt("day_offset", index),
                        slot = parseMealSlot(entry.optString("slot")),
                        title = title,
                        calorieTarget = entry.optNullableInt("calorie_target"),
                    ),
                )
            }
        }.take(21)
    }

    private fun parseZone(value: String): StorageZone =
        runCatching { StorageZone.valueOf(value.trim().uppercase()) }.getOrDefault(StorageZone.PANTRY)

    private fun parseMealSlot(value: String): MealSlot =
        runCatching { MealSlot.valueOf(value.trim().uppercase()) }.getOrDefault(MealSlot.FLEX)

    private fun buildUserPrompt(text: String, memory: HouseholdUiMemory, sourceContext: String = ""): String {
        val inventory = memory.inventory.take(32).joinToString("\n") { item ->
            "id=${item.id} name=${item.name} quantity=${item.quantity.ifBlank { "unknown" }} zone=${item.zone.name} " +
                "category=${item.category.ifBlank { "unknown" }} nutrition=${item.calories?.let { "$it kcal/${item.servingText.ifBlank { "serving unknown" }}" } ?: "unknown"} " +
                "nutrition_source=${item.nutritionSource.ifBlank { "unknown" }} expires_at=${item.expiresAtMillis ?: "unknown"} source=${item.source}"
        }
        val groceries = memory.groceries.take(32).joinToString("\n") { item ->
            "id=${item.id} name=${item.name} quantity=${item.quantity.ifBlank { "unknown" }} status=${item.status.name} category=${item.category.ifBlank { "unknown" }}"
        }
        val recipes = memory.recipes.take(20).joinToString("\n") { recipe ->
            "id=${recipe.id} title=${recipe.title} tags=${recipe.tags.ifBlank { "none" }}"
        }
        val plans = memory.mealPlanEntries.take(16).joinToString { entry ->
            "${entry.id}@${entry.dateEpochDay}:${entry.slot.label}:${entry.status.name.lowercase()}:${entry.title}:${entry.calorieTarget ?: "?"}kcal source=${entry.source.ifBlank { "unknown" }}"
        }
        val historyMessages = memory.currentChatMessages.let { messages ->
            if (messages.lastOrNull()?.role == com.wonderfood.app.data.ChatRole.USER) messages.dropLast(1) else messages
        }
        val recentMessages = historyMessages.takeLast(40).joinToString("\n") { message ->
            "${message.role.name.lowercase()}: ${message.body.promptClip(420)}"
        }
        val recentActions = memory.actions.take(20).joinToString("\n") { action ->
            "${action.status.name.lowercase()}: ${action.title} / ${action.summary.promptClip(260)}"
        }
        val recentEvents = memory.events.take(20).joinToString("\n") { event ->
            "${event.type.name.lowercase()}: ${event.note.promptClip(220)} / ${event.source} / ${event.confidence.name.lowercase()}"
        }
        val recentLedger = memory.inventoryTransactions.take(20).joinToString("\n") { tx ->
            "${tx.action.name.lowercase()}: ${tx.itemName} ${tx.quantityText} ${tx.zone.name} / ${tx.reason.promptClip(220)}"
        }
        val recentMeals = memory.mealLogs.take(12).joinToString("\n") { meal ->
            "${meal.loggedDateEpochDay}:${meal.mealSlot.label}:${meal.title} ${meal.calories}kcal used=${meal.usedItemsText.ifBlank { "unknown" }}"
        }
        val prefs = memory.preferences
        return """
            User message:
            $text

            Current database snapshot read immediately before this AI turn:
            Snapshot values are authoritative. Say when a requested field is absent; do not silently fill it from memory or guess.
            Source handles available in the Android UI after this turn: [App snapshot], [LifeOS Notion], [LifeOS Sheets], [MCP schema], [Template health], and any provider web/file citations.
            LifeOS source pack context:
            ${sourceContext.ifBlank { "No external LifeOS source-pack details were injected for this turn; use the local app snapshot and visible source cards only." }}
            When answering questions, tables, comparisons, or recommendations, mention the source handle(s) you used in the reply. If no source supports a claim, say it is an estimate or ask to connect/refresh the missing source.
            counts: inventory=${memory.inventory.size}, groceries=${memory.groceries.size}, recipes=${memory.recipes.size}, meal_logs=${memory.mealLogs.size}, meal_plans=${memory.mealPlans.size}, plan_entries=${memory.mealPlanEntries.size}, receipts=${memory.receipts.size}, actions=${memory.actions.size}, events=${memory.events.size}
            inventory: ${inventory.ifBlank { "empty" }}
            groceries: ${groceries.ifBlank { "empty" }}
            recipes: ${recipes.ifBlank { "empty" }}
            plan_entries: ${plans.ifBlank { "empty" }}
            recent_meals:
            ${recentMeals.ifBlank { "empty" }}

            Recent conversation history, oldest to newest:
            ${recentMessages.ifBlank { "empty" }}

            Recent review decisions and AI proposals, newest first:
            ${recentActions.ifBlank { "empty" }}

            Recent app/food events, newest first:
            ${recentEvents.ifBlank { "empty" }}

            Recent pantry/fridge/freezer ledger, newest first:
            ${recentLedger.ifBlank { "empty" }}

            User food preferences:
            diet_style: ${prefs.dietStyle.ifBlank { "unset" }}
            allergies: ${prefs.allergies.ifBlank { "none saved" }}
            dislikes: ${prefs.dislikes.ifBlank { "none saved" }}
            preferred_staples: ${prefs.preferredStaples.ifBlank { "unset" }}
            preferred_cuisines: ${prefs.preferredCuisines.ifBlank { "unset" }}
            preferred_stores: ${prefs.preferredStores.ifBlank { "unset" }}
            calorie_goal: ${prefs.calorieGoal.ifBlank { "unset" }}
            protein_goal: ${prefs.proteinGoal.ifBlank { "unset" }}
            health_notes: ${prefs.healthNotes.ifBlank { "unset" }}
            custom_ai_instructions: ${prefs.customAiInstructions.ifBlank { "unset" }}
        """.trimIndent()
    }

    private fun effectiveSystemPrompt(memory: HouseholdUiMemory): String {
        val selectedSkill = memory.preferences.aiSkillOverride.trim().ifBlank { bundledSkill.trim() }
        if (selectedSkill.isBlank()) return systemPrompt
        return listOf(selectedSkill, RUNTIME_CONTRACT, systemPrompt).joinToString("\n\n")
    }

    companion object {
        fun withBundledSkill(context: Context): LiteLlmFoodInterpreter {
            val skill = runCatching {
                context.assets.open("ai/wonderfood_food_skill.md").bufferedReader().use { it.readText() }
            }.getOrDefault("")
            return LiteLlmFoodInterpreter(
                bundledSkill = skill,
            )
        }

        private const val RUNTIME_CONTRACT = "Runtime contract version: wf.android.intake.v2"

        private const val SYSTEM_PROMPT = """
You are WonderFood, a food-memory agent inside a private Android app.
Return JSON only. No markdown.
Preferred output is the canonical WonderFood command envelope:
schema_version="wf.ai.command-envelope.v1", catalog_version="wf.ai.skill-catalog.v1",
status="commands" for reviewable changes, status="needs_clarification" for missing details,
and typed command objects such as inventory.add_lot, shopping.add_item, meal.log,
meal_plan.create, recipe.create. If you cannot emit that envelope, use the legacy
draft JSON below as a temporary compatibility fallback.
Be conversational first and CRUD second.
Use draft:null when no data change is needed.
Use draft:{...} for one data change.
Use drafts:[...] for compound requests. Do not drop secondary intents.
Allowed draft types: inventory, grocery, receipt, recipe, meal_log, meal_plan.
This app is local-first SQLite. Drafts are reviewable proposals, not automatic writes.
Prefer one useful clarification over a confident guess.
Ask a follow-up with draft:null when important fields are missing or ambiguous.
If the user asks to create/save a recipe but does not provide ingredients, steps, or cooking details, ask for ingredients and return draft:null. Do not invent a recipe from title alone.
If a recipe draft has ingredients but weak or missing steps, the reply must ask the user to confirm the ingredient list and edit/add steps before accepting.
If the user asks to plan/schedule a meal and also asks for a recipe that does not appear in Current memory recipes, include both only when the user provided enough recipe details:
1. a recipe draft for the new recipe
2. a meal_plan draft with an entry for the requested day/slot.
If the recipe details are missing, ask what goes into it and return draft:null.
If the user edits an existing planned meal from a calendar/day/plan context, return a meal_plan draft that preserves the plan and changes only the requested entry when possible.
If the user asks for calories, macros, or nutrition without a serving size or quantity, ask a short follow-up and return draft:null.
If logging a clearly named meal, a rough meal_log draft is allowed, but the reply must tell the user to confirm/correct calories and macros before accepting.
For "tomorrow", use day_offset:1. For today, use day_offset:0.
Always respect saved allergies and dislikes. Treat custom_ai_instructions as user-authored operating instructions unless they conflict with safety or the current message.
When answering without a draft, still be useful: use bullets/tables when helpful and cite source handles like [App snapshot], [LifeOS Notion], [LifeOS Sheets], [MCP schema], [Template health], or provider web/file citations. Do not invent citations.

Schema:
{
  "reply": "short natural response",
  "draft": null | one draft object,
  "drafts": [optional list of draft objects for compound requests]
}

Draft object:
{
    "type": "receipt",
    "merchant": "Store name or empty",
    "store_location": "Store address/location or empty",
    "purchased_at": "YYYY-MM-DD or null",
    "currency_code": "ISO 4217 code, for example USD",
    "subtotal_cents": 1047,
    "tax_cents": 84,
    "total_cents": 1131,
    "raw_text": "visible non-sensitive receipt text",
    "items": [{
      "name":"Roma Tomatoes",
      "receipt_line":"ROMA TOMATO 2.49",
      "line_price_cents":249,
      "quantity":"1 package",
      "disposition":"INVENTORY|HOUSEHOLD|IGNORE",
      "zone":"FRIDGE|FREEZER|PANTRY",
      "zone_source":"receipt_label|ai_receipt_inference",
      "category":"produce",
      "emoji":"🍅",
      "serving_text":"1 medium tomato",
      "calories":22,
      "protein_g":1,
      "carbs_g":5,
      "fat_g":0,
      "nutrition_source":"nutrition_label|barcode_provider|ai_estimate|unknown",
      "best_before_date":"YYYY-MM-DD or null",
      "expiry_source":"printed_label|ai_shelf_life_estimate|unknown",
      "confidence":0.82,
      "warnings":["Best-before is estimated; check package"],
      "notes":"optional"
    }]
  } OR {
    "type": "inventory",
    "operation": "create",
    "items": [{
      "name":"Eggs",
      "quantity":"12",
      "zone":"FRIDGE",
      "category":"protein",
      "emoji":"🥚",
      "image_url":"",
      "serving_text":"1 large egg",
      "calories":70,
      "protein_g":6,
      "carbs_g":1,
      "fat_g":5,
      "nutrition_source":"ai_estimate",
      "notes":"optional"
    }]
  } OR {
    "type": "grocery",
    "operation": "create",
    "items": [{"name":"Oats","quantity":"","zone":"PANTRY","category":"grain","emoji":"🌾","image_url":"","notes":"optional"}]
  } OR {
    "type": "recipe",
    "operation": "create",
    "title": "Recipe name",
    "emoji": "🍛",
    "image_url": "",
    "ingredients": "plain text",
    "steps": "plain text cooking process",
    "cooking_process": "optional alias for steps",
    "servings": 2,
    "prep_minutes": 25,
    "tags": ["quick","high protein"]
  } OR {
    "type": "meal_log",
    "operation": "log",
    "title": "Meal name",
    "slot": "BREAKFAST|LUNCH|DINNER|SNACK|FLEX",
      "calories": null,
      "protein_g": null,
      "carbs_g": null,
      "fat_g": null,
      "used_items": ["Spinach","Eggs"]
  } OR {
    "type": "meal_plan",
    "operation": "plan",
    "title": "Plan name",
    "days": "Mon: ...\nTue: ...",
    "grocery_hint": "items to buy",
    "entries": [
      {"day_offset":0,"slot":"DINNER","title":"Spinach egg bowl","calorie_target":550,"emoji":"🍳","notes":"uses spinach first"}
    ]
  }
}
Storage zones must be FRIDGE, FREEZER, or PANTRY.
Return emoji for every item, recipe, and planned meal entry when practical. Return image_url only if the user supplied one or it is a reliable known URL; otherwise leave it empty. Do not invent image URLs.
For receipts, treat purchased food as inventory; never convert it to a grocery/to-buy draft. Keep household products out of food inventory and keep payment, tax, discount, and subtotal lines ignored but visible in review.
Use null for nutrition fields unless the user provided exact values, a label/provider result exists, the user explicitly asked for an estimate, or this is receipt enrichment. Every estimate must use source=ai_estimate and a warning. Never present an estimated best-before as a printed expiry date.
"""
    }
}

private fun String.trimEnd(char: Char): String =
    dropLastWhile { it == char }

private fun String.asksForRecipeCreation(): Boolean =
    listOf("recipe", "recepie", "reciepe", "save this", "save it").any { it in this }

private fun String.allowsPlaceholderRecipe(): Boolean =
    listOf("rough placeholder", "placeholder", "stub", "draft from title", "invent it", "make it up").any { it in this }

private fun recipeClarificationReply(userText: String): String? {
    val lower = userText.lowercase()
    if (!lower.asksForRecipeCreation() || userText.hasRecipeDetails() || lower.allowsPlaceholderRecipe()) return null
    return "I can save that recipe, but I need the ingredients first. What goes into it, and roughly how do you cook it?"
}

private fun nutritionClarificationReply(userText: String): String? {
    val lower = userText.lowercase()
    if (!lower.asksForNutrition() || userText.hasNutritionQuantity()) return null
    return if (userText.hasNamedFoodForNutrition()) {
        "I can estimate that, but portion changes everything. Roughly how much did you eat or plan to eat?"
    } else {
        "I can estimate nutrition, but I need the food and rough portion first. What was it, and how much?"
    }
}

private fun String.asksForNutrition(): Boolean =
    listOf("calorie", "calories", "kcal", "protein", "carb", "carbs", "fat", "macro", "macros", "nutrition").any { it in this }

private fun String.hasNutritionQuantity(): Boolean =
    Regex("""(?i)\b(\d+(?:\.\d+)?|one|two|three|four|five|half|quarter)\s*(cup|cups|tbsp|tsp|tablespoons?|teaspoons?|g|gram|grams|kg|oz|ounce|ounces|lb|lbs|serving|servings|slice|slices|piece|pieces|bowl|plate|packet|packets|can|cans)\b""")
        .containsMatchIn(this) ||
        Regex("""(?i)\b(small|medium|large|half|full)\s+(bowl|plate|serving|portion)\b""").containsMatchIn(this)

private fun String.hasNamedFoodForNutrition(): Boolean =
    lowercase()
        .replace(Regex("""\b(calories?|kcal|protein|carbs?|fat|macros?|nutrition|how|many|much|estimate|for|in|is|are|the|a|an|my)\b"""), " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()
        .length >= 3

private fun String.hasRecipeDetails(): Boolean =
    extractRecipeDetails().isNotBlank()

private fun String.extractRecipeDetails(): String {
    val match = Regex("""(?i)\b(?:with|using|ingredients?|steps?|method|recipe details?)\b\s*:?\s*(.+)""").find(this)
    val value = match?.groupValues?.getOrNull(1)
        ?.replace(Regex("""(?i)\b(?:for\s+)?(?:today|tomorrow'?s?|tomorrows|tmrw|breakfast|lunch|dinner|snack)\b.*$"""), "")
        ?.trim(' ', '.', '-', ':')
        .orEmpty()
    return value.takeIf { detail ->
        detail.length >= 3 && detail.split(",", " and ", "\n").any { it.trim().length >= 3 }
    }.orEmpty()
}

private fun String.promptClip(maxLength: Int): String =
    replace("\n", " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength - 1) + "…" }

private fun String.citationSnippet(startIndex: Int, endIndex: Int): String {
    if (isBlank()) return ""
    val start = startIndex.coerceIn(0, length)
    val end = endIndex.coerceIn(start, length)
    val citation = substring(start, end).trim().ifBlank { "" }
    return citation.ifBlank { promptClip(180) }.take(240)
}

private fun String.safeProviderExcerpt(apiKey: String): String =
    (if (apiKey.isBlank()) this else replace(apiKey, "[redacted]"))
        .promptClip(220)

private fun JSONObject.optNullableInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

private fun JSONObject.optExpiryMillis(): Long? {
    val numeric = listOf("expires_at", "best_before_at")
        .firstNotNullOfOrNull { key -> if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0L } else null }
    if (numeric != null) return if (numeric < 10_000_000_000L) numeric * 1_000L else numeric
    val date = listOf("best_before_date", "expiry_date", "expires_on")
        .firstNotNullOfOrNull { key -> optString(key).trim().takeIf(String::isNotBlank) }
        ?: return null
    return runCatching {
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}

private fun JSONObject.optPurchasedAtMillis(): Long? {
    val raw = optString("purchased_at").trim().ifBlank { optString("purchase_date").trim() }
    if (raw.isBlank()) return null
    raw.toLongOrNull()?.let { value -> return if (value < 10_000_000_000L) value * 1_000L else value }
    return runCatching {
        LocalDate.parse(raw.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}

private fun JSONObject.optMoneyCents(centsKey: String, vararg decimalKeys: String): Long? {
    if (has(centsKey) && !isNull(centsKey)) {
        opt(centsKey)?.toString()?.trim()?.toLongOrNull()?.let { return it }
    }
    val raw = decimalKeys.firstNotNullOfOrNull { key ->
        opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.trim()?.takeIf(String::isNotBlank)
    } ?: return null
    val amount = raw.replace(Regex("[^0-9.\\-]"), "").toBigDecimalOrNull() ?: return null
    return runCatching { amount.movePointRight(2).longValueExact() }.getOrNull()
}

private fun JSONObject.optDisplayImage(name: String): String? {
    val explicit = optString("image_uri").trim()
        .ifBlank { optString("image").trim().takeUnless { it.isRemoteImageUrl() }.orEmpty() }
        .ifBlank { optString("emoji").trim() }
        .ifBlank { foodEmojiForName(name) }
    return explicit.ifBlank { null }
}

private fun JSONObject.optRemoteImageUrl(): String {
    val explicit = optString("image_url").trim()
        .ifBlank { optString("url").trim() }
        .ifBlank { optString("image").trim().takeIf { it.isRemoteImageUrl() }.orEmpty() }
    return explicit.takeIf { it.isRemoteImageUrl() }.orEmpty()
}

private fun String.isRemoteImageUrl(): Boolean =
    startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

private fun JSONArray.toStringList(): List<String> =
    buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotBlank()) add(value)
        }
    }
