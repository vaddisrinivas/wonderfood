package com.wonderfood.app

import android.content.Intent
import android.net.Uri

data class WonderFoodVoiceCommand(
    val id: Long = System.nanoTime(),
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
}

object WonderFoodDeepLink {
    fun from(intent: Intent?): WonderFoodVoiceCommand? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "wonderfood") return null
        return when (uri.host.orEmpty()) {
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
            action = if (feature.equals("numbers", ignoreCase = true)) {
                WonderFoodVoiceAction.SHOW_NUMBERS
            } else {
                WonderFoodVoiceAction.OPEN_SECTION
            },
            section = feature,
        )
    }

    private fun voiceCommand(uri: Uri): WonderFoodVoiceCommand? {
        val path = uri.pathSegments
        val first = path.getOrNull(0).orEmpty()
        val second = path.getOrNull(1).orEmpty()
        return when {
            first == "water" -> WonderFoodVoiceCommand(
                action = WonderFoodVoiceAction.LOG_WATER,
                amount = uri.getQueryParameter("ml")?.toDoubleOrNull()
                    ?: uri.getQueryParameter("amount")?.toDoubleOrNull()
                    ?: 250.0,
                unit = "ml",
            )
            first == "shopping" && second == "start" -> WonderFoodVoiceCommand(action = WonderFoodVoiceAction.START_SHOPPING)
            first == "shopping" && second == "done" -> WonderFoodVoiceCommand(action = WonderFoodVoiceAction.DONE_SHOPPING)
            first == "cooking" && second == "start" -> WonderFoodVoiceCommand(
                action = WonderFoodVoiceAction.START_COOKING,
                recipeName = uri.getQueryParameter("recipe").orEmpty()
                    .ifBlank { uri.getQueryParameter("name").orEmpty() },
            )
            first == "cooking" && second == "done" -> WonderFoodVoiceCommand(
                action = WonderFoodVoiceAction.DONE_COOKING,
                recipeName = uri.getQueryParameter("recipe").orEmpty()
                    .ifBlank { uri.getQueryParameter("name").orEmpty() },
            )
            first == "meal" && second == "log" -> WonderFoodVoiceCommand(
                action = WonderFoodVoiceAction.LOG_MEAL,
                text = uri.getQueryParameter("meal").orEmpty()
                    .ifBlank { uri.getQueryParameter("food").orEmpty() }
                    .ifBlank { "Meal from Google" },
                amount = uri.getQueryParameter("calories")?.toDoubleOrNull(),
                unit = "kcal",
            )
            first == "grocery" && second == "add" -> WonderFoodVoiceCommand(
                action = WonderFoodVoiceAction.ADD_GROCERY,
                itemName = uri.getQueryParameter("item").orEmpty()
                    .ifBlank { uri.getQueryParameter("name").orEmpty() },
                quantity = uri.getQueryParameter("quantity").orEmpty(),
            )
            first == "numbers" -> WonderFoodVoiceCommand(action = WonderFoodVoiceAction.SHOW_NUMBERS)
            else -> null
        }
    }
}
