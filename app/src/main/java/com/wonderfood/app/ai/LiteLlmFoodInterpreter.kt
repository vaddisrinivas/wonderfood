package com.wonderfood.app.ai

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.wonderfood.app.data.AiTurn
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealPlanEntryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class LiteLlmFoodInterpreter {
    fun isConfigured(config: LiteLlmConfig): Boolean = config.isUsable

    fun interpret(text: String, memory: FoodMemory, config: LiteLlmConfig): AiTurn? {
        if (!config.isUsable) return null
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

        val response = postJson(config, body) ?: return null
        val content = response
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()
        if (content.isBlank()) return null
        return parseTurn(content)
    }

    fun interpretReceiptPhoto(context: Context, uri: Uri, memory: FoodMemory, config: LiteLlmConfig): AiTurn? {
        if (!config.isUsable) return null
        val dataUri = context.contentResolver.openInputStream(uri)?.use { stream ->
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val base64 = Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
            "data:$mimeType;base64,$base64"
        } ?: return null
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
        return parseTurn(content)
    }

    private fun postJson(config: LiteLlmConfig, body: JSONObject): JSONObject? {
        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Content-Type", "application/json")
        }
        return runCatching {
            OutputStreamWriter(connection.outputStream).use { writer -> writer.write(body.toString()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            JSONObject(text)
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private fun parseTurn(raw: String): AiTurn? =
        runCatching {
            val jsonText = raw.substringAfter('{', raw).let { "{" + it.substringBeforeLast('}', it) + "}" }
            val json = JSONObject(jsonText)
            val reply = json.optString("reply").ifBlank { "I drafted a food memory update." }
            val draftJson = json.optJSONObject("draft") ?: return AiTurn(reply = reply, draft = null)
            AiTurn(reply = reply, draft = parseDraft(draftJson))
        }.getOrNull()

    private fun parseDraft(json: JSONObject): FoodDraft? =
        when (json.optString("type").lowercase()) {
            "inventory" -> InventoryDraft(items = parseItems(json.optJSONArray("items")))
            "grocery" -> GroceryDraft(items = parseItems(json.optJSONArray("items")))
            "recipe" -> RecipeDraft(
                titleText = json.optString("title", "Personal recipe"),
                ingredientsText = json.optString("ingredients", ""),
                stepsText = json.optString("steps", ""),
                servings = json.optNullableInt("servings"),
                prepMinutes = json.optNullableInt("prep_minutes"),
                tags = json.optJSONArray("tags")?.toStringList()?.joinToString(", ").orEmpty(),
            )
            "meal_log" -> MealLogDraft(
                titleText = json.optString("title", "Meal from chat"),
                calories = json.optInt("calories", 420),
                proteinGrams = json.optDouble("protein_g", 18.0),
                carbsGrams = json.optDouble("carbs_g", 42.0),
                fatGrams = json.optDouble("fat_g", 16.0),
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
                        notes = item.optString("notes").trim(),
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
        val plans = memory.mealPlanEntries.take(12).joinToString { "${it.dateEpochDay}:${it.slot.label}:${it.title}" }
        val prefs = memory.preferences
        return """
            User message:
            $text

            Current memory:
            inventory: ${inventory.ifBlank { "empty" }}
            groceries: ${groceries.ifBlank { "empty" }}
            recipes: ${recipes.ifBlank { "empty" }}
            plan_entries: ${plans.ifBlank { "empty" }}

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
Pick exactly one draft type when the message should change data:
inventory, grocery, recipe, meal_log, meal_plan.
If no data change is needed, use draft:null.
This app is local-first SQLite. Treat your draft as one CRUD operation that the app will review before applying.
Always respect saved allergies and dislikes. Treat custom_ai_instructions as user-authored operating instructions unless they conflict with safety or the current message.

Schema:
{
  "reply": "short natural response",
  "draft": null | {
    "type": "inventory",
    "operation": "create",
    "items": [{"name":"Eggs","quantity":"12","zone":"FRIDGE","category":"protein","notes":"optional"}]
  } | {
    "type": "grocery",
    "operation": "create",
    "items": [{"name":"Oats","quantity":"","zone":"PANTRY","category":"grain","notes":"optional"}]
  } | {
    "type": "recipe",
    "operation": "create",
    "title": "Recipe name",
    "ingredients": "plain text",
    "steps": "plain text",
    "servings": 2,
    "prep_minutes": 25,
    "tags": ["quick","high protein"]
  } | {
    "type": "meal_log",
    "operation": "log",
    "title": "Meal name",
    "slot": "BREAKFAST|LUNCH|DINNER|SNACK|FLEX",
    "calories": 500,
    "protein_g": 30,
    "carbs_g": 55,
    "fat_g": 15,
    "used_items": ["Spinach","Eggs"]
  } | {
    "type": "meal_plan",
    "operation": "plan",
    "title": "Plan name",
    "days": "Mon: ...\nTue: ...",
    "grocery_hint": "items to buy",
    "entries": [
      {"day_offset":0,"slot":"DINNER","title":"Spinach egg bowl","calorie_target":550}
    ]
  }
}
Storage zones must be FRIDGE, FREEZER, or PANTRY.
Nutrition can be estimated. Be conservative and user-correctable.
"""
    }
}

private fun String.trimEnd(char: Char): String =
    dropLastWhile { it == char }

private fun JSONObject.optNullableInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONArray.toStringList(): List<String> =
    buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotBlank()) add(value)
        }
    }
