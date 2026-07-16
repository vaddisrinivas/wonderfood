package com.wonderfood.app

import android.content.Intent
import android.net.Uri

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
    SHOW_NUMBERS,
    AI_REVIEW,
}

object WonderFoodDeepLink {
    fun from(intent: Intent?): WonderFoodVoiceCommand? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (!uri.scheme.equals("wonderfood", ignoreCase = true)) return null
        return when (uri.host.orEmpty().lowercase()) {
            "open" -> openCommand(uri)
            "voice", "quick" -> voiceCommand(uri)
            else -> null
        }
    }

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
        return when {
            first == "water" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.LOG_WATER,
                amount = uri.getQueryParameter("ml")?.toDoubleOrNull()
                    ?: uri.getQueryParameter("amount")?.toDoubleOrNull()
                    ?: 250.0,
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
                    .ifBlank { "Meal from Google" }
                    .cleanText(),
                amount = uri.getQueryParameter("calories")?.toDoubleOrNull(),
                unit = "kcal",
            )
            first == "grocery" && second == "add" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.ADD_GROCERY,
                itemName = uri.getQueryParameter("item").orEmpty()
                    .ifBlank { uri.getQueryParameter("name").orEmpty() }
                    .cleanText(),
                quantity = uri.getQueryParameter("quantity").orEmpty().cleanText(48),
            )
            first == "numbers" -> WonderFoodVoiceCommand(
                idempotencyKey = idempotencyKey,
                action = WonderFoodVoiceAction.SHOW_NUMBERS,
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

    private fun String.cleanToken(): String =
        cleanText(48).lowercase()

    private fun String.cleanText(maxLength: Int = 160): String =
        filter { !it.isISOControl() }
            .trim()
            .take(maxLength)
}
