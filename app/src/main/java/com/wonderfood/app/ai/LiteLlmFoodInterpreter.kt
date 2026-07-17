package com.wonderfood.app.ai

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.wonderfood.app.data.AiTurn
import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodDraftNormalizer
import com.wonderfood.app.data.FoodDraftValidator
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealPlanEntryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.foodEmojiForName
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

sealed interface LiteLlmInterpretation {
    val diagnostic: String

    data class Success(
        val turn: AiTurn,
        override val diagnostic: String,
    ) : LiteLlmInterpretation

    data class Failure(
        override val diagnostic: String,
    ) : LiteLlmInterpretation
}

private data class ProviderJsonResult(
    val json: JSONObject?,
    val diagnostic: String,
)

class LiteLlmFoodInterpreter {
    fun isConfigured(config: LiteLlmConfig): Boolean = config.isUsable

    fun interpret(text: String, memory: FoodMemory, configs: List<LiteLlmConfig>): AiTurn? =
        configs.firstNotNullOfOrNull { config -> interpret(text, memory, config) }

    fun testConnection(config: LiteLlmConfig): Result<String> =
        runCatching {
            require(config.isUsable) { "Provider URL, model, and API key are required." }
            val body = JSONObject()
                .put("model", config.model)
                .put("temperature", 0)
                .put("max_tokens", 12)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "Reply with a short connection check."))
                        .put(JSONObject().put("role", "user").put("content", "Say ok.")),
                )
            val response = postJson(config, body) ?: error("No provider response.")
            val content = response
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
            if (content.isBlank()) "Connected: ${config.statusLabel}" else "Connected: ${config.statusLabel}"
        }

    fun interpret(text: String, memory: FoodMemory, config: LiteLlmConfig): AiTurn? {
        return (interpretWithDiagnostics(text, memory, config) as? LiteLlmInterpretation.Success)?.turn
    }

    fun interpretWithDiagnostics(text: String, memory: FoodMemory, config: LiteLlmConfig): LiteLlmInterpretation {
        if (!config.isUsable) return LiteLlmInterpretation.Failure("Provider is not configured.")
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.1)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", buildUserPrompt(text, memory))),
            )

        val response = postJsonWithDiagnostics(config, body)
        val json = response.json ?: return LiteLlmInterpretation.Failure(response.diagnostic)
        val content = json
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()
        if (content.isBlank()) {
            return LiteLlmInterpretation.Failure("${response.diagnostic}; empty assistant content")
        }
        val turn = parseTurn(content, text, memory)
            ?: return LiteLlmInterpretation.Failure("${response.diagnostic}; invalid WonderFood JSON")
        return validateProviderTurn(turn, response.diagnostic)
    }

    fun interpretReceiptPhoto(context: Context, uri: Uri, memory: FoodMemory, configs: List<LiteLlmConfig>): AiTurn? {
        if (configs.none { it.isUsable }) return null
        val dataUri = context.contentResolver.openInputStream(uri)?.use { stream ->
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val base64 = Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
            "data:$mimeType;base64,$base64"
        } ?: return null
        return configs.firstNotNullOfOrNull { config ->
            interpretReceiptDataUri(dataUri, memory, config)
        }
    }

    fun interpretReceiptPhoto(context: Context, uri: Uri, memory: FoodMemory, config: LiteLlmConfig): AiTurn? {
        if (!config.isUsable) return null
        val dataUri = context.contentResolver.openInputStream(uri)?.use { stream ->
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val base64 = Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
            "data:$mimeType;base64,$base64"
        } ?: return null
        return interpretReceiptDataUri(dataUri, memory, config)
    }

    private fun interpretReceiptDataUri(dataUri: String, memory: FoodMemory, config: LiteLlmConfig): AiTurn? {
        if (!config.isUsable) return null
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.1)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
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
                                                    text = "Extract grocery or pantry items from this receipt photo. Prefer grocery draft unless the user clearly already bought these for inventory.",
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
        val response = postJson(config, body) ?: return null
        val content = response
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()
        if (content.isBlank()) return null
        return parseTurn(content, "receipt photo", memory)
            ?.let { turn -> (validateProviderTurn(turn, "receipt photo provider") as? LiteLlmInterpretation.Success)?.turn }
    }

    private fun validateProviderTurn(turn: AiTurn, diagnostic: String): LiteLlmInterpretation {
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
        )
    }

    private fun postJson(config: LiteLlmConfig, body: JSONObject): JSONObject? {
        if (config.provider == AiProvider.ANTHROPIC) return postAnthropicJson(config, body)
        val endpoint = config.chatCompletionsEndpoint()
        val requestBody = body.requestBodyFor(config.provider)
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
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            JSONObject(text)
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun postJsonWithDiagnostics(config: LiteLlmConfig, body: JSONObject): ProviderJsonResult {
        if (config.provider == AiProvider.ANTHROPIC) return postAnthropicJsonWithDiagnostics(config, body)
        val endpoint = config.chatCompletionsEndpoint()
        val requestBody = body.requestBodyFor(config.provider)
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

    private fun postAnthropicJson(config: LiteLlmConfig, body: JSONObject): JSONObject? {
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
            if (connection.responseCode !in 200..299) return@runCatching null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject()
                .put(
                    "choices",
                    JSONArray().put(
                        JSONObject().put(
                            "message",
                            JSONObject().put("content", JSONObject(text).anthropicTextContent()),
                        ),
                    ),
                )
        }.getOrNull().also {
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

    private fun LiteLlmConfig.chatCompletionsEndpoint(): String =
        when (provider) {
            AiProvider.OPENAI_COMPATIBLE -> baseUrl.trimEnd('/') + "/chat/completions"
            AiProvider.AZURE_OPENAI -> azureChatCompletionsEndpoint()
            AiProvider.ANTHROPIC -> baseUrl.trimEnd('/') + "/v1/messages"
        }

    private fun LiteLlmConfig.azureChatCompletionsEndpoint(): String {
        val trimmed = baseUrl.trim()
        val endpoint = if (trimmed.contains("/chat/completions")) {
            trimmed
        } else {
            val encodedDeployment = URLEncoder.encode(model.trim(), StandardCharsets.UTF_8.name())
            trimmed.trimEnd('/') + "/openai/deployments/$encodedDeployment/chat/completions"
        }
        if (apiVersion.isBlank() || endpoint.contains("api-version=")) return endpoint
        val separator = if (endpoint.contains("?")) "&" else "?"
        val encodedVersion = URLEncoder.encode(apiVersion.trim(), StandardCharsets.UTF_8.name())
        return "$endpoint${separator}api-version=$encodedVersion"
    }

    private fun JSONObject.requestBodyFor(provider: AiProvider): JSONObject {
        val copy = JSONObject(toString())
        if (provider == AiProvider.AZURE_OPENAI) {
            copy.remove("model")
            copy.remove("temperature")
            if (copy.has("max_tokens") && !copy.has("max_completion_tokens")) {
                copy.put("max_completion_tokens", copy.optInt("max_tokens"))
                copy.remove("max_tokens")
            }
        }
        return copy
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

    private fun parseTurn(raw: String, userText: String, memory: FoodMemory): AiTurn? =
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
                    ),
                )
            }
        }.take(20)
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

    private fun buildUserPrompt(text: String, memory: FoodMemory): String {
        val inventory = memory.inventory.take(20).joinToString { "${it.name} (${it.zone.label})" }
        val groceries = memory.groceries.take(20).joinToString { it.name }
        val recipes = memory.recipes.take(12).joinToString { it.title }
        val plans = memory.mealPlanEntries.take(16).joinToString { entry ->
            "${entry.id}@${entry.dateEpochDay}:${entry.slot.label}:${entry.status.name.lowercase()}:${entry.title}:${entry.calorieTarget ?: "?"}kcal source=${entry.source.ifBlank { "unknown" }}"
        }
        val recentMessages = memory.messages.takeLast(40).joinToString("\n") { message ->
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

    private companion object {
        const val SYSTEM_PROMPT = """
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
Allowed draft types: inventory, grocery, recipe, meal_log, meal_plan.
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

Schema:
{
  "reply": "short natural response",
  "draft": null | one draft object,
  "drafts": [optional list of draft objects for compound requests]
}

Draft object:
{
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
Use null for nutrition fields unless the user provided exact values, a label/provider result exists, or the user explicitly asked for an estimate. If estimating, say so in reply.
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

private fun String.safeProviderExcerpt(apiKey: String): String =
    (if (apiKey.isBlank()) this else replace(apiKey, "[redacted]"))
        .promptClip(220)

private fun JSONObject.optNullableInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

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
