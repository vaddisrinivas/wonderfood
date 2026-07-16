package com.wonderfood.app.ai

import android.content.Context

data class LiteLlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    val isUsable: Boolean =
        baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

class LiteLlmSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): LiteLlmConfig =
        LiteLlmConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty(),
        )

    fun save(config: LiteLlmConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.model.trim().ifBlank { DEFAULT_MODEL })
            .apply()
    }

    companion object {
        const val PREFS_NAME = "litellm"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val DEFAULT_MODEL = "gpt-5.4-mini"
    }
}
