package com.wonderfood.app

import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class WonderFoodVoiceCommand(
    val id: Long = System.nanoTime(),
    val idempotencyKey: String = "",
    val action: WonderFoodVoiceAction,
    val section: String = "",
    val text: String = "",
    val amount: Double? = null,
    val unit: String = "",
    val recipeName: String = "",
    val itemName: String = "",
    val quantity: String = "",
    val zone: String = "",
    val category: String = "",
    val templateNotionUrl: String = "",
    val templateSheetsUrl: String = "",
    val linkActions: List<WonderFoodLinkAction> = emptyList(),
)

enum class WonderFoodVoiceAction {
    OPEN_SECTION,
    LOG_WATER,
    START_SHOPPING,
    DONE_SHOPPING,
    START_COOKING,
    DONE_COOKING,
    LOG_MEAL,
    ADD_GROCERY,
    ADD_INVENTORY,
    PLAN_MEALS,
    SHOW_NUMBERS,
    PROOF_PACK,
    AI_REVIEW,
    LINK_ACTION,
}

data class WonderFoodLinkAction(
    val type: String,
    val targetKind: String,
    val targetRef: String = "",
    val displayName: String = "",
    val fields: Map<String, String> = emptyMap(),
    val destructive: Boolean = false,
    val sensitive: Boolean = false,
)

object WonderFoodDeepLink {
    fun from(intent: Intent?): WonderFoodVoiceCommand? {
        if (intent == null) return null
        return runCatching {
            when (intent.action) {
                WonderFoodCommandContract.ACTION_COMMAND -> fromCommandIntent(intent)
                Intent.ACTION_VIEW -> fromViewIntent(intent)
                Intent.ACTION_SEND -> fromShareIntent(intent)
                else -> null
            }
        }.getOrNull()
    }

    private fun fromViewIntent(intent: Intent): WonderFoodVoiceCommand? {
        val uri = intent.data ?: return null
        if (uri.scheme.equals("https", ignoreCase = true)) return appLinkCommand(uri)
        if (!uri.scheme.equals("wonderfood", ignoreCase = true)) return null
        return when (uri.host.orEmpty().lowercase()) {
            "add" -> prefillAddCommand(uri)
            "action" -> linkActionCommand(uri)
            "open" -> openCommand(uri)
            "proof-pack" -> proofPackCommand(uri)
            "voice", "quick" -> voiceCommand(uri)
            else -> null
        }
    }

    private fun proofPackCommand(uri: Uri): WonderFoodVoiceCommand? {
        val notionUrl = uri.getQueryParameter("notion").orEmpty()
            .ifBlank { uri.getQueryParameter("notion_url").orEmpty() }
            .cleanText(600)
        val sheetsUrl = uri.getQueryParameter("sheets").orEmpty()
            .ifBlank { uri.getQueryParameter("sheets_url").orEmpty() }
            .ifBlank { uri.getQueryParameter("sheet").orEmpty() }
            .cleanText(600)
        if (notionUrl.isBlank() && sheetsUrl.isBlank()) return null
        return WonderFoodVoiceCommand(
            idempotencyKey = uri.explicitIdempotencyKey(),
            action = WonderFoodVoiceAction.PROOF_PACK,
            section = "data_home",
            text = uri.toString().cleanText(WonderFoodCommandContract.MAX_LINK_TEXT_LENGTH),
            templateNotionUrl = notionUrl,
            templateSheetsUrl = sheetsUrl,
        )
    }

    private fun appLinkCommand(uri: Uri): WonderFoodVoiceCommand? {
        val host = uri.host.orEmpty().lowercase()
        if (host !in TRUSTED_APP_LINK_HOSTS) return null
        val first = uri.pathSegments.getOrNull(0).orEmpty().cleanToken()
        if (first == "add") return prefillAddCommand(uri)
        if (first == "action") return linkActionCommand(uri)
        if (first == "proof-pack") return proofPackCommand(uri)
        return null
    }

    private fun prefillAddCommand(uri: Uri): WonderFoodVoiceCommand? {
        val kind = uri.pathSegments.getOrNull(1).orEmpty()
            .ifBlank { uri.getQueryParameter("kind").orEmpty() }
            .ifBlank { uri.getQueryParameter("type").orEmpty() }
            .ifBlank { uri.getQueryParameter("domain").orEmpty() }
            .cleanToken()
        val text = uri.getQueryParameter("text").orEmpty()
            .ifBlank { uri.getQueryParameter("items").orEmpty() }
            .ifBlank { uri.getQueryParameter("q").orEmpty() }
            .cleanText(WonderFoodCommandContract.MAX_LINK_TEXT_LENGTH)
        val name = uri.getQueryParameter("name").orEmpty()
            .ifBlank { uri.getQueryParameter("item").orEmpty() }
            .ifBlank { uri.getQueryParameter("title").orEmpty() }
            .cleanText()
        val quantity = uri.getQueryParameter("quantity").orEmpty()
            .ifBlank { uri.getQueryParameter("qty").orEmpty() }
            .cleanText(48)
        val packedFood = text.toPackedFoodText()
        val itemName = name.ifBlank { packedFood.itemName }.cleanText()
        val packedQuantity = quantity.ifBlank { packedFood.quantity }.cleanText(48)
        val idempotencyKey = uri.explicitIdempotencyKey()

        return when (kind) {
            "grocery", "groceries", "shopping", "shop" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.ADD_GROCERY,
                text = text,
                itemName = itemName,
                quantity = packedQuantity,
                category = uri.getQueryParameter("category").orEmpty().cleanText(48),
            )
            "pantry", "inventory", "kitchen", "fridge", "freezer" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.ADD_INVENTORY,
                text = text,
                itemName = itemName,
                quantity = packedQuantity,
                zone = uri.getQueryParameter("zone").orEmpty()
                    .ifBlank { if (kind in listOf("pantry", "fridge", "freezer")) kind else "" }
                    .cleanText(48),
                category = uri.getQueryParameter("category").orEmpty().cleanText(48),
            )
            "meal", "meals", "meal-log", "meal_log", "log" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.LOG_MEAL,
                text = uri.getQueryParameter("meal").orEmpty()
                    .ifBlank { uri.getQueryParameter("food").orEmpty() }
                    .ifBlank { name }
                    .ifBlank { text }
                    .cleanText(),
                amount = uri.getQueryParameter("calories")?.toDoubleOrNull(),
                unit = "kcal",
            )
            "recipe", "recipes" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.AI_REVIEW,
                text = text.ifBlank { "Save recipe $name" }.cleanText(WonderFoodCommandContract.MAX_LINK_TEXT_LENGTH),
            )
            "plan", "planning", "meal-plan", "meal_plan" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.AI_REVIEW,
                text = text.ifBlank { "Plan meals" }.cleanText(WonderFoodCommandContract.MAX_LINK_TEXT_LENGTH),
            )
            "" -> unsupportedVoiceCommand(uri, idempotencyKey)
            else -> null
        }
    }

    private fun linkActionCommand(uri: Uri): WonderFoodVoiceCommand? {
        val actions = uri.linkActions()
        if (actions.isEmpty()) return null
        return WonderFoodVoiceCommand(
            idempotencyKey = uri.explicitIdempotencyKey(),
            action = WonderFoodVoiceAction.LINK_ACTION,
            section = actions.first().targetKind,
            text = uri.toString().cleanText(WonderFoodCommandContract.MAX_LINK_TEXT_LENGTH),
            linkActions = actions,
        )
    }

    private fun Uri.linkActions(): List<WonderFoodLinkAction> {
        getQueryParameter("actions")?.let { raw ->
            return raw
                .cleanText(WonderFoodCommandContract.MAX_ACTIONS_JSON_LENGTH)
                .takeIf { it.isNotBlank() }
                ?.let(::jsonLinkActions)
                .orEmpty()
        }

        val indexed = indexedLinkActions()
        if (indexed.isNotEmpty()) return indexed

        val single = singleLinkAction(index = null)
        return listOfNotNull(single)
    }

    private fun jsonLinkActions(raw: String): List<WonderFoodLinkAction> =
        runCatching {
            val array = JSONArray(raw)
            if (array.length() !in 1..WonderFoodCommandContract.MAX_BULK_ACTIONS) return@runCatching emptyList()
            val parsed = (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.toLinkAction()
            }
            parsed.takeIf { it.size == array.length() }.orEmpty()
        }.getOrDefault(emptyList())

    private fun JSONObject.toLinkAction(): WonderFoodLinkAction? {
        val type = optString("type")
            .ifBlank { optString("action") }
            .cleanActionType()
        if (type.isBlank()) return null
        val fields = mutableMapOf<String, String>()
        optJSONObject("fields")?.let { nested ->
            nested.keys().forEach { key ->
                key.cleanFieldKey().takeIf { it in WonderFoodCommandContract.LINK_ACTION_FIELD_KEYS }?.let { safeKey ->
                    nested.optString(key).cleanText(WonderFoodCommandContract.MAX_LINK_FIELD_LENGTH).takeIf { it.isNotBlank() }?.let { fields[safeKey] = it }
                }
            }
        }
        WonderFoodCommandContract.LINK_ACTION_FIELD_KEYS.forEach { key ->
            optString(key).cleanText(WonderFoodCommandContract.MAX_LINK_FIELD_LENGTH).takeIf { it.isNotBlank() }?.let { fields[key] = it }
        }
        return type.toWonderFoodLinkAction(
            targetRef = optString("id")
                .ifBlank { optString("targetId") }
                .ifBlank { optString("target_id") }
                .cleanText(96),
            displayName = optString("target")
                .ifBlank { optString("targetName") }
                .ifBlank { optString("target_name") }
                .ifBlank { optString("name") }
                .ifBlank { optString("title") }
                .ifBlank { optString("item") }
                .cleanText(),
            fields = fields,
        )
    }

    private fun Uri.indexedLinkActions(): List<WonderFoodLinkAction> =
        buildList {
            for (index in 1..WonderFoodCommandContract.MAX_BULK_ACTIONS) {
                singleLinkAction(index)?.let(::add)
            }
        }

    private fun Uri.singleLinkAction(index: Int?): WonderFoodLinkAction? {
        val suffixes = if (index == null) listOf("") else listOf("$index", "_$index", "${index}_")
        val type = firstNotBlankQuery(suffixes, "type", "action")
            .ifBlank {
                if (index == null) {
                    pathSegments.getOrNull(if (scheme.equals("wonderfood", ignoreCase = true)) 0 else 1).orEmpty()
                } else {
                    ""
                }
            }
            .cleanActionType()
        if (type.isBlank()) return null
        val fields = safeLinkFields(index)
        return type.toWonderFoodLinkAction(
            targetRef = firstNotBlankQuery(suffixes, "id", "targetId", "target_id").cleanText(96),
            displayName = firstNotBlankQuery(suffixes, "target", "targetName", "target_name", "name", "title", "item").cleanText(),
            fields = fields,
        )
    }

    private fun Uri.safeLinkFields(index: Int?): Map<String, String> =
        WonderFoodCommandContract.LINK_ACTION_FIELD_KEYS.mapNotNull { key ->
            val raw = if (index == null) {
                getQueryParameter(key)
            } else {
                getQueryParameter("${key}$index")
                    ?: getQueryParameter("${key}_$index")
                    ?: getQueryParameter("action${index}_$key")
                    ?: getQueryParameter("a${index}_$key")
            }
            val value = raw.orEmpty().cleanText(WonderFoodCommandContract.MAX_LINK_FIELD_LENGTH)
            key.takeIf { value.isNotBlank() }?.let { it to value }
        }.toMap()

    private fun Uri.firstNotBlankQuery(
        suffixes: List<String>,
        vararg baseKeys: String,
    ): String =
        suffixes.firstNotNullOfOrNull { suffix ->
            baseKeys.firstNotNullOfOrNull { base ->
                val names = if (suffix.isBlank()) {
                    listOf(base)
                } else {
                    listOf("$base$suffix", "${base}_$suffix", "action$suffix${base.replaceFirstChar { it.uppercase() }}", "a${suffix}_$base")
                }
                names.firstNotNullOfOrNull { name -> getQueryParameter(name)?.takeIf { it.isNotBlank() } }
            }
        }.orEmpty()

    private fun fromShareIntent(intent: Intent): WonderFoodVoiceCommand? {
        val sharedText = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT) }.getOrNull()
            ?.toString()
            .orEmpty()
            .cleanText(WonderFoodCommandContract.MAX_SHARED_TEXT_LENGTH)
        if (sharedText.isBlank()) return null
        return WonderFoodVoiceCommand(
            action = WonderFoodVoiceAction.AI_REVIEW,
            text = sharedText,
            idempotencyKey = intent.explicitIdempotencyKey(),
        )
    }

    private fun fromCommandIntent(intent: Intent): WonderFoodVoiceCommand? {
        intent.data?.let { uri ->
            fromViewIntent(Intent(Intent.ACTION_VIEW, uri))?.let { command ->
                return command.copy(idempotencyKey = command.idempotencyKey.ifBlank { intent.explicitIdempotencyKey() })
            }
        }

        val actions = intent.commandLinkActions()
        if (actions.isNotEmpty()) {
            return WonderFoodVoiceCommand(
                idempotencyKey = intent.explicitIdempotencyKey(),
                action = WonderFoodVoiceAction.LINK_ACTION,
                section = actions.first().targetKind,
                text = intent.firstStringExtra(WonderFoodCommandContract.EXTRA_TEXT, Intent.EXTRA_TEXT)
                    .ifBlank { actions.joinToString("; ") { it.type } }
                    .cleanText(WonderFoodCommandContract.MAX_LINK_TEXT_LENGTH),
                linkActions = actions,
            )
        }

        val text = intent.firstStringExtra(
            WonderFoodCommandContract.EXTRA_TEXT,
            WonderFoodCommandContract.EXTRA_QUERY,
            WonderFoodCommandContract.EXTRA_UTTERANCE,
            Intent.EXTRA_TEXT,
        ).cleanText(WonderFoodCommandContract.MAX_SHARED_TEXT_LENGTH)
        if (text.isBlank()) return null
        return WonderFoodVoiceCommand(
            idempotencyKey = intent.explicitIdempotencyKey(),
            action = WonderFoodVoiceAction.AI_REVIEW,
            text = text,
        )
    }

    private fun Intent.commandLinkActions(): List<WonderFoodLinkAction> {
        if (hasExtra(WonderFoodCommandContract.EXTRA_ACTIONS_JSON)) {
            return firstStringExtra(WonderFoodCommandContract.EXTRA_ACTIONS_JSON)
                .cleanText(WonderFoodCommandContract.MAX_ACTIONS_JSON_LENGTH)
                .takeIf { it.isNotBlank() }
                ?.let(::jsonLinkActions)
                .orEmpty()
        }

        val type = firstStringExtra(
            WonderFoodCommandContract.EXTRA_ACTION_TYPE,
            WonderFoodCommandContract.EXTRA_ACTION,
        ).cleanActionType()
        if (type.isBlank()) return emptyList()
        val fields = safeIntentFields()
        return listOfNotNull(
            type.toWonderFoodLinkAction(
                targetRef = firstStringExtra(
                    WonderFoodCommandContract.EXTRA_TARGET_ID,
                    WonderFoodCommandContract.EXTRA_TARGET_ID_CAMEL,
                    WonderFoodCommandContract.EXTRA_TARGET_ID_SNAKE,
                ).cleanText(96),
                displayName = firstStringExtra(
                    WonderFoodCommandContract.EXTRA_TARGET_NAME,
                    WonderFoodCommandContract.EXTRA_TARGET_NAME_CAMEL,
                    WonderFoodCommandContract.EXTRA_TARGET_NAME_SNAKE,
                    WonderFoodCommandContract.EXTRA_NAME,
                    WonderFoodCommandContract.EXTRA_TITLE,
                    WonderFoodCommandContract.EXTRA_ITEM,
                ).cleanText(),
                fields = fields,
            ),
        )
    }

    private fun Intent.safeIntentFields(): Map<String, String> =
        WonderFoodCommandContract.LINK_ACTION_FIELD_KEYS.mapNotNull { key ->
            val value = firstStringExtra(key, "field_$key")
                .cleanText(WonderFoodCommandContract.MAX_LINK_FIELD_LENGTH)
            key.takeIf { value.isNotBlank() }?.let { it to value }
        }.toMap()

    private fun openCommand(uri: Uri): WonderFoodVoiceCommand {
        val feature = uri.pathSegments.firstOrNull().orEmpty()
            .ifBlank { uri.getQueryParameter("feature").orEmpty() }
            .ifBlank { uri.getQueryParameter("featureParam").orEmpty() }
        return WonderFoodVoiceCommand(
            idempotencyKey = uri.explicitIdempotencyKey(),
            action = if (feature.equals("numbers", ignoreCase = true)) {
                WonderFoodVoiceAction.SHOW_NUMBERS
            } else {
                WonderFoodVoiceAction.OPEN_SECTION
            },
            section = feature.cleanToken(),
        )
    }

    private fun voiceCommand(uri: Uri): WonderFoodVoiceCommand? {
        val path = uri.pathSegments
        val first = path.getOrNull(0).orEmpty().cleanToken()
        val second = path.getOrNull(1).orEmpty().cleanToken()
        val idempotencyKey = uri.explicitIdempotencyKey()
        val packedFood = uri.packedFoodText()
        return when {
            first == "water" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.LOG_WATER,
                amount = (
                    uri.getQueryParameter("ml")?.toDoubleOrNull()
                        ?: uri.getQueryParameter("amount")?.toDoubleOrNull()
                        ?: 250.0
                    ).coerceIn(1.0, 5_000.0),
                unit = "ml",
            )
            first == "shopping" && second == "start" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.START_SHOPPING,
            )
            first == "shopping" && second == "done" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.DONE_SHOPPING,
            )
            first == "cooking" && second == "start" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.START_COOKING,
                recipeName = uri.getQueryParameter("recipe").orEmpty()
                    .ifBlank { uri.getQueryParameter("name").orEmpty() }
                    .cleanText(),
            )
            first == "cooking" && second == "done" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.DONE_COOKING,
                recipeName = uri.getQueryParameter("recipe").orEmpty()
                    .ifBlank { uri.getQueryParameter("name").orEmpty() }
                    .cleanText(),
            )
            first == "meal" && second == "log" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.LOG_MEAL,
                text = uri.getQueryParameter("meal").orEmpty()
                    .ifBlank { uri.getQueryParameter("food").orEmpty() }
                    .cleanText(),
                amount = uri.getQueryParameter("calories")?.toDoubleOrNull(),
                unit = "kcal",
            )
            first == "grocery" && second == "add" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.ADD_GROCERY,
                text = packedFood.raw,
                itemName = uri.getQueryParameter("item").orEmpty()
                    .ifBlank { uri.getQueryParameter("name").orEmpty() }
                    .ifBlank { packedFood.itemName }
                    .cleanText(),
                quantity = uri.getQueryParameter("quantity").orEmpty()
                    .ifBlank { packedFood.quantity }
                    .cleanText(48),
                category = uri.getQueryParameter("category").orEmpty().cleanText(48),
            )
            first in listOf("kitchen", "inventory", "pantry", "fridge", "freezer") && second == "add" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.ADD_INVENTORY,
                text = packedFood.raw,
                itemName = uri.getQueryParameter("item").orEmpty()
                    .ifBlank { uri.getQueryParameter("name").orEmpty() }
                    .ifBlank { packedFood.itemName }
                    .cleanText(),
                quantity = uri.getQueryParameter("quantity").orEmpty()
                    .ifBlank { packedFood.quantity }
                    .cleanText(48),
                zone = uri.getQueryParameter("zone").orEmpty()
                    .ifBlank { if (first in listOf("pantry", "fridge", "freezer")) first else "" }
                    .cleanText(48),
                category = uri.getQueryParameter("category").orEmpty().cleanText(48),
            )
            first == "numbers" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.SHOW_NUMBERS,
            )
            first == "plan" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.PLAN_MEALS,
            )
            else -> unsupportedVoiceCommand(uri, idempotencyKey)
        }
    }

    private fun unsupportedVoiceCommand(uri: Uri, idempotencyKey: String): WonderFoodVoiceCommand? {
        val text = uri.getQueryParameter("text").orEmpty()
            .ifBlank { uri.getQueryParameter("q").orEmpty() }
            .ifBlank { uri.getQueryParameter("utterance").orEmpty() }
            .ifBlank { uri.pathSegments.joinToString(" ") }
            .cleanText()
        if (text.isBlank()) return null
        return WonderFoodVoiceCommand(
            idempotencyKey = idempotencyKey,
            action = WonderFoodVoiceAction.AI_REVIEW,
            text = text,
        )
    }

    private fun Uri.explicitIdempotencyKey(): String =
        getQueryParameter("requestId").orEmpty()
            .ifBlank { getQueryParameter("request_id").orEmpty() }
            .ifBlank { getQueryParameter("idempotencyKey").orEmpty() }
            .cleanText(96)

    private fun Intent.explicitIdempotencyKey(): String =
        firstStringExtra(
            WonderFoodCommandContract.EXTRA_REQUEST_ID,
            WonderFoodCommandContract.EXTRA_REQUEST_ID_SNAKE,
            WonderFoodCommandContract.EXTRA_IDEMPOTENCY_KEY,
        ).cleanText(96)

    private fun Intent.firstStringExtra(vararg names: String): String =
        names.firstNotNullOfOrNull { name ->
            runCatching { getStringExtra(name) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
        }.orEmpty()

    private fun Uri.packedFoodText(): PackedFoodText =
        getQueryParameter("text").orEmpty()
            .ifBlank { getQueryParameter("q").orEmpty() }
            .cleanText()
            .toPackedFoodText()

    private fun String.cleanToken(): String =
        cleanText(48).lowercase()

    private fun String.cleanActionType(): String =
        WonderFoodCommandContract.normalizeActionType(this).orEmpty()

    private fun String.cleanFieldKey(): String =
        cleanText(80)
            .lowercase()
            .replace('-', '_')

    private fun String.toWonderFoodLinkAction(
        targetRef: String,
        displayName: String,
        fields: Map<String, String>,
    ): WonderFoodLinkAction? {
        val spec = WonderFoodCommandContract.actionSpec(this) ?: return null
        val normalizedFields = fields
            .mapKeys { it.key.cleanFieldKey() }
            .filterKeys { it in WonderFoodCommandContract.LINK_ACTION_FIELD_KEYS }
            .mapValues { it.value.cleanText(WonderFoodCommandContract.MAX_LINK_FIELD_LENGTH) }
            .filterValues { it.isNotBlank() }
            .toSortedMap()
        val allowedFields = WonderFoodCommandContract.allowedFields(spec.targetKind)
        if (normalizedFields.keys.any { it !in allowedFields }) return null
        return WonderFoodLinkAction(
            type = this,
            targetKind = spec.targetKind,
            targetRef = targetRef,
            displayName = displayName,
            fields = normalizedFields,
            destructive = spec.operation == LinkActionOperation.DELETE,
            sensitive = spec.targetKind == "preferences",
        )
    }

    private fun String.cleanText(maxLength: Int = 160): String =
        filter { !it.isISOControl() }
            .trim()
            .take(maxLength)

    private fun String.toPackedFoodText(): PackedFoodText {
        val raw = trim()
        if (raw.isBlank()) return PackedFoodText()
        val cleaned = raw
            .replace(Regex("""(?i)^(add|put|buy|need|needs|picked up|got)\s+"""), "")
            .replace(Regex("""(?i)\s+(to|into|in|on)\s+(my\s+)?(wonderfood\s+)?(shopping list|groceries|grocery list|kitchen|pantry|fridge|freezer)$"""), "")
            .trim()
        val match = PACKED_QUANTITY_PREFIX.find(cleaned)
        return if (match == null) {
            PackedFoodText(raw = raw, itemName = cleaned)
        } else {
            PackedFoodText(
                raw = raw,
                quantity = match.groupValues[1].trim(),
                itemName = match.groupValues[2].trim(),
            )
        }
    }

    private data class PackedFoodText(
        val raw: String = "",
        val quantity: String = "",
        val itemName: String = "",
    )

    private val PACKED_QUANTITY_PREFIX = Regex(
        pattern = """(?i)^\s*((?:\d+(?:\.\d+)?|one|two|three|four|five|six|seven|eight|nine|ten|half|a|an)\s*(?:bags?|boxes?|cans?|cups?|packs?|packets?|lbs?|pounds?|oz|ounces?|grams?|g|kg|bunch(?:es)?|dozen|count|ct)?|a\s+dozen|one\s+dozen|dozen)\s+(.+)$""",
    )

    private val TRUSTED_APP_LINK_HOSTS = setOf("wonderfood.app", "www.wonderfood.app")
}
