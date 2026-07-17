package com.wonderfood.app.ai

import com.wonderfood.app.data.AiTurn
import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntryDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import com.wonderfood.app.data.foodEmojiForName
import com.wonderfood.core.ai.COMMAND_ENVELOPE_SCHEMA_VERSION
import com.wonderfood.core.ai.Command
import com.wonderfood.core.ai.CommandEnvelope
import com.wonderfood.core.ai.CommandEnvelopeCodec
import com.wonderfood.core.ai.CommandEnvelopeValidator
import com.wonderfood.core.ai.CommandType
import com.wonderfood.core.ai.EnvelopeStatus
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

object CommandEnvelopeDraftMapper {
    fun tryMap(raw: String): AiTurn? {
        if (!raw.contains(COMMAND_ENVELOPE_SCHEMA_VERSION) && !raw.contains(PROPOSAL_PACKAGE_SCHEMA_VERSION)) return null
        val decoded = decodeEnvelope(raw)
        if (decoded.rejection != null) {
            return AiTurn(
                reply = decoded.rejection,
                draft = null,
            )
        }
        val envelope = decoded.envelope ?: return null
        val validation = CommandEnvelopeValidator.validate(envelope)
        if (validation.errors.isNotEmpty()) {
            return AiTurn(
                reply = "I found a WonderFood proposal, but it failed validation: ${validation.errors.take(3).joinToString("; ")}",
                draft = null,
            )
        }
        if (envelope.status == EnvelopeStatus.UNSUPPORTED || envelope.status == EnvelopeStatus.NEEDS_CLARIFICATION) {
            return AiTurn(
                reply = envelope.unsupported?.message
                    ?: "This WonderFood proposal needs clarification before it can become a draft.",
                draft = null,
            )
        }

        val drafts = envelope.commands.toDrafts()
        if (drafts.isEmpty()) {
            return AiTurn(
                reply = "I found a valid WonderFood proposal, but this app version cannot import those command types yet. Pantry, shopping, recipe, meal log, receipt items, and meal-plan commands are supported.",
                draft = null,
            )
        }
        val warningText = envelope.warnings
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = " Warnings: ") { it.message }
            .orEmpty()
        val originText = decoded.originLabel.takeIf { it.isNotBlank() }?.let { " from $it" }.orEmpty()
        return AiTurn(
            reply = "Imported external WonderFood proposal$originText for review. Nothing is saved until you accept.$warningText",
            draft = drafts.singleOrComposite(),
        )
    }

    private fun decodeEnvelope(raw: String): DecodedEnvelope {
        val root = runCatching {
            CommandEnvelopeCodec.json.parseToJsonElement(extractJsonObject(raw)) as? JsonObject
        }.getOrNull()
        if (root?.cleanString("schema_version") == PROPOSAL_PACKAGE_SCHEMA_VERSION) {
            return decodeProposalPackage(root)
        }
        val envelope = runCatching { CommandEnvelopeCodec.decode(raw) }.getOrElse {
            return DecodedEnvelope(rejection = "I found a WonderFood proposal, but its JSON was malformed. Nothing changed.")
        }
        return DecodedEnvelope(envelope = envelope)
    }

    private fun decodeProposalPackage(packageJson: JsonObject): DecodedEnvelope {
        if (packageJson.cleanString("proposal_id").isBlank()) {
            return DecodedEnvelope(rejection = "I found a WonderFood proposal package, but proposal_id was missing. Nothing changed.")
        }
        val origin = packageJson.obj("origin")
        if (origin == null || origin.cleanString("kind").isBlank()) {
            return DecodedEnvelope(rejection = "I found a WonderFood proposal package, but origin.kind was missing. Nothing changed.")
        }
        val expiresAt = packageJson.cleanString("expires_at")
        if (expiresAt.isBlank()) {
            return DecodedEnvelope(rejection = "I found a WonderFood proposal package, but expires_at was missing. Nothing changed.")
        }
        val expiry = runCatching { Instant.parse(expiresAt) }.getOrElse {
            return DecodedEnvelope(rejection = "I found a WonderFood proposal package, but expires_at was invalid. Nothing changed.")
        }
        if (expiry.isBefore(Instant.now())) {
            return DecodedEnvelope(rejection = "I found a WonderFood proposal package, but it expired. Nothing changed.")
        }
        val envelopeElement = packageJson["command_envelope"]
            ?: return DecodedEnvelope(rejection = "I found a WonderFood proposal package, but command_envelope was missing. Nothing changed.")
        val envelopeRaw = when (envelopeElement) {
            is JsonObject -> envelopeElement.toString()
            is JsonPrimitive -> envelopeElement.content
            else -> return DecodedEnvelope(rejection = "I found a WonderFood proposal package, but command_envelope was not an object. Nothing changed.")
        }
        val envelope = runCatching { CommandEnvelopeCodec.decode(envelopeRaw) }.getOrElse {
            return DecodedEnvelope(rejection = "I found a WonderFood proposal package, but command_envelope was malformed. Nothing changed.")
        }
        val originLabel = origin.cleanString("producer")
            .ifBlank { origin.cleanString("kind") }
        return DecodedEnvelope(envelope = envelope, originLabel = originLabel)
    }

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

    private fun List<Command>.toDrafts(): List<FoodDraft> {
        val inventoryItems = mutableListOf<FoodCandidate>()
        val groceryItems = mutableListOf<FoodCandidate>()
        val drafts = mutableListOf<FoodDraft>()

        forEach { command ->
            val payload = command.payload
            when (command.type) {
                CommandType.INVENTORY_ADD_LOT -> inventoryItems += payload.toFoodCandidate()
                CommandType.SHOPPING_ADD_ITEM -> groceryItems += payload.toFoodCandidate()
                CommandType.RECEIPT_PROPOSE_ITEMS -> {
                    val items = payload.array("items").toFoodCandidates()
                    if (payload.cleanString("destination").contains("shop", ignoreCase = true)) {
                        groceryItems += items
                    } else {
                        inventoryItems += items
                    }
                }
                CommandType.RECIPE_SAVE_STRUCTURED,
                CommandType.RECIPE_UPDATE_STRUCTURED,
                -> payload.toRecipeDraft()?.let(drafts::add)
                CommandType.MEAL_LOG -> payload.toMealLogDraft()?.let(drafts::add)
                CommandType.PLANNING_CREATE_MEAL_PLAN,
                CommandType.PLANNING_UPDATE_MEAL_PLAN_ENTRY,
                -> payload.toMealPlanDraft()?.let(drafts::add)
                else -> Unit
            }
        }

        if (inventoryItems.isNotEmpty()) drafts += InventoryDraft(inventoryItems.take(MAX_ITEMS))
        if (groceryItems.isNotEmpty()) drafts += GroceryDraft(groceryItems.take(MAX_ITEMS))
        return drafts
    }

    private fun JsonObject.toFoodCandidate(): FoodCandidate {
        val name = cleanString("name", "description", "title").ifBlank { "Food item" }
        return FoodCandidate(
            name = name,
            quantity = quantityText("quantity", "serving"),
            zone = storageZone("storage_zone", "zone"),
            category = cleanString("category").ifBlank { categorizeFood(name) },
            servingText = quantityText("serving"),
            calories = int("calories"),
            proteinGrams = double("protein_g"),
            carbsGrams = double("carbs_g"),
            fatGrams = double("fat_g"),
            nutritionSource = cleanString("nutrition_source", "source"),
            notes = cleanString("notes", "reason"),
            imageUri = cleanString("emoji", "image_uri").ifBlank { foodEmojiForName(name) },
            imageUrl = cleanString("image_url").takeIf { it.isRemoteImageUrl() }.orEmpty(),
        )
    }

    private fun JsonObject.toRecipeDraft(): RecipeDraft? {
        val title = cleanString("title", "name").ifBlank { return null }
        return RecipeDraft(
            titleText = title,
            ingredientsText = ingredientsText(),
            stepsText = stepsText(),
            servings = int("servings"),
            prepMinutes = int("prep_minutes"),
            tags = array("tags").toStringList().joinToString(", "),
            imageUri = cleanString("emoji", "image_uri").ifBlank { foodEmojiForName(title) },
            imageUrl = cleanString("image_url").takeIf { it.isRemoteImageUrl() }.orEmpty(),
        )
    }

    private fun JsonObject.toMealLogDraft(): MealLogDraft? {
        val title = cleanString("title", "meal_title", "name").ifBlank { return null }
        val nutrition = obj("nutrition")
        return MealLogDraft(
            titleText = title,
            calories = int("calories") ?: nutrition?.int("calories"),
            proteinGrams = double("protein_g") ?: nutrition?.double("protein_g"),
            carbsGrams = double("carbs_g") ?: nutrition?.double("carbs_g"),
            fatGrams = double("fat_g") ?: nutrition?.double("fat_g"),
            mealSlot = mealSlot("meal_slot", "slot"),
            usedItemsText = array("used_items").toStringList().joinToString(", "),
            source = "external_proposal:${nutrition?.cleanString("source").orEmpty().ifBlank { "unknown" }}",
        )
    }

    private fun JsonObject.toMealPlanDraft(): MealPlanDraft? {
        val entries = array("entries").toMealPlanEntries()
        val title = cleanString("title", "plan_title").ifBlank { "External meal plan" }
        val groceryHint = array("shopping_suggestions").toStringList().joinToString(", ")
            .ifBlank { cleanString("grocery_hint") }
        val days = entries.joinToString("\n") { "${it.slot.label}: ${it.title}" }
            .ifBlank { cleanString("days") }
        if (entries.isEmpty() && days.isBlank()) return null
        return MealPlanDraft(
            titleText = title,
            daysText = days,
            groceryHint = groceryHint,
            entries = entries,
        )
    }

    private fun JsonObject.ingredientsText(): String =
        arrayOrNull("ingredients")
            ?.objects()
            ?.joinToString("\n") { ingredient ->
                listOf(
                    ingredient.quantityText("quantity"),
                    ingredient.cleanString("name", "description"),
                    ingredient.cleanString("preparation").takeIf { it.isNotBlank() }?.let { "($it)" }.orEmpty(),
                ).filter { it.isNotBlank() }.joinToString(" ")
            }
            ?.ifBlank { cleanString("ingredients") }
            ?: cleanString("ingredients")

    private fun JsonObject.stepsText(): String =
        arrayOrNull("steps")
            ?.toStringList()
            ?.mapIndexed { index, step -> "${index + 1}. $step" }
            ?.joinToString("\n")
            ?.ifBlank { cleanString("steps", "cooking_process") }
            ?: cleanString("steps", "cooking_process")

    private fun JsonArray.toMealPlanEntries(): List<MealPlanEntryDraft> =
        objects().mapIndexedNotNull { index, entry ->
            val title = entry.cleanString("title", "meal_title", "name")
            if (title.isBlank()) {
                null
            } else {
                MealPlanEntryDraft(
                    dayOffset = entry.int("day_offset") ?: index,
                    slot = entry.mealSlot("meal_slot", "slot"),
                    title = title,
                    calorieTarget = entry.int("calorie_target", "calories"),
                )
            }
        }.take(MAX_PLAN_ENTRIES)

    private fun JsonArray.toFoodCandidates(): List<FoodCandidate> =
        objects().map { it.toFoodCandidate() }.take(MAX_ITEMS)

    private fun JsonArray.objects(): List<JsonObject> =
        mapNotNull { it as? JsonObject }

    private fun JsonArray.toStringList(): List<String> =
        mapNotNull { element ->
            when (element) {
                is JsonObject -> element.cleanString("name", "description", "title").ifBlank { element.toString() }
                is JsonPrimitive -> element.contentOrNullTrimmed()
                else -> null
            }
        }

    private fun JsonObject.array(key: String): JsonArray =
        arrayOrNull(key) ?: JsonArray(emptyList())

    private fun JsonObject.arrayOrNull(key: String): JsonArray? =
        get(key) as? JsonArray

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key) as? JsonObject

    private fun JsonObject.cleanString(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> get(key).stringOrNullTrimmed() }.orEmpty()

    private fun JsonObject.int(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key -> get(key).doubleOrNullValue()?.toInt() }

    private fun JsonObject.double(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key -> get(key).doubleOrNullValue() }

    private fun JsonObject.quantityText(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            when (val raw = get(key)) {
                is JsonObject -> raw.cleanString("text").ifBlank {
                    listOf(raw.cleanString("amount"), raw.cleanString("unit"))
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
                else -> raw.stringOrNullTrimmed()
            }?.takeIf { it.isNotBlank() }
        }.orEmpty()

    private fun JsonObject.storageZone(vararg keys: String): StorageZone {
        val value = cleanString(*keys)
        return runCatching { StorageZone.valueOf(value.uppercase()) }
            .getOrElse { classifyStorageZone(cleanString("name", "description", "title")) }
    }

    private fun JsonObject.mealSlot(vararg keys: String): MealSlot {
        val value = cleanString(*keys)
        return runCatching { MealSlot.valueOf(value.uppercase()) }.getOrDefault(MealSlot.FLEX)
    }

    private fun JsonElement?.stringOrNullTrimmed(): String? =
        when (this) {
            null, JsonNull -> null
            is JsonPrimitive -> contentOrNullTrimmed()
            else -> toString()
        }?.trim()?.takeIf { it.isNotBlank() }

    private fun JsonPrimitive.contentOrNullTrimmed(): String? =
        if (isString) content.trim().takeIf { it.isNotBlank() } else content.trim().takeIf { it.isNotBlank() }

    private fun JsonElement?.doubleOrNullValue(): Double? =
        when (this) {
            null, JsonNull -> null
            is JsonPrimitive -> doubleOrNull
            else -> null
        }

    private data class DecodedEnvelope(
        val envelope: CommandEnvelope? = null,
        val originLabel: String = "",
        val rejection: String? = null,
    )

    private fun List<FoodDraft>.singleOrComposite(): FoodDraft =
        if (size == 1) single() else CompositeDraft(this)

    private fun String.isRemoteImageUrl(): Boolean =
        startsWith("https://", ignoreCase = true)

    private const val MAX_ITEMS = 40
    private const val MAX_PLAN_ENTRIES = 31
    private const val PROPOSAL_PACKAGE_SCHEMA_VERSION = "wf.proposal-package.v1"
}
