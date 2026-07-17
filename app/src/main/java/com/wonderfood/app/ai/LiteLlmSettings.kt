package com.wonderfood.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiProvider(val prefValue: String, val label: String) {
    OPENAI_COMPATIBLE("openai_compatible", "OpenAI-compatible"),
    AZURE_OPENAI("azure_openai", "Azure OpenAI"),
    ANTHROPIC("anthropic", "Anthropic"),
    ;

    companion object {
        fun fromPref(value: String?): AiProvider =
            entries.firstOrNull { it.prefValue == value } ?: OPENAI_COMPATIBLE
    }
}

data class LiteLlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val provider: AiProvider = AiProvider.OPENAI_COMPATIBLE,
    val apiVersion: String = "",
) {
    val isUsable: Boolean =
        baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    val statusLabel: String =
        when (provider) {
            AiProvider.AZURE_OPENAI -> "Azure ${model.ifBlank { "deployment" }}"
            AiProvider.ANTHROPIC -> "Anthropic ${model.ifBlank { "model" }}"
            AiProvider.OPENAI_COMPATIBLE -> "${openAiCompatibleProviderLabel()} ${model.ifBlank { LiteLlmSettings.DEFAULT_MODEL }}"
        }

    private fun openAiCompatibleProviderLabel(): String {
        val lower = baseUrl.lowercase()
        return when {
            "api.openai.com" in lower -> "OpenAI"
            "openrouter.ai" in lower -> "OpenRouter"
            "generativelanguage.googleapis.com" in lower -> "Gemini"
            "litellm" in lower || "127.0.0.1" in lower || "localhost" in lower -> "LiteLLM"
            else -> "OpenAI-compatible"
        }
    }
}

class LiteLlmSettings(
    context: Context,
    prefsName: String = PREFS_NAME,
    private val keyAlias: String = KEYSTORE_ALIAS,
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun read(): LiteLlmConfig {
        val storedApiKey = prefs.getString(KEY_API_KEY, "").orEmpty()
        val config = LiteLlmConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
            apiKey = decryptSecret(storedApiKey),
            model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty(),
            provider = AiProvider.fromPref(prefs.getString(KEY_PROVIDER, "")),
            apiVersion = prefs.getString(KEY_API_VERSION, "").orEmpty(),
        )
        if (storedApiKey.isNotBlank() && !storedApiKey.startsWith(ENCRYPTED_PREFIX)) {
            prefs.edit { putString(KEY_API_KEY, encryptSecret(config.apiKey)) }
        }
        return config
    }

    fun readAll(): List<LiteLlmConfig> {
        val configured = buildList {
            add(read())
            addAll(readFallbacks())
        }
        return configured
            .filter { it.isUsable }
            .distinctBy { "${it.provider.prefValue}|${it.baseUrl.trim()}|${it.model.trim()}|${it.apiVersion.trim()}" }
    }

    fun readAllRoundRobin(): List<LiteLlmConfig> {
        val configs = readAll()
        if (configs.size <= 1) return configs
        val startIndex = prefs.getInt(KEY_NEXT_PROVIDER_INDEX, 0).floorMod(configs.size)
        prefs.edit { putInt(KEY_NEXT_PROVIDER_INDEX, (startIndex + 1).floorMod(configs.size)) }
        return configs.drop(startIndex) + configs.take(startIndex)
    }

    fun save(config: LiteLlmConfig) {
        prefs.edit {
            putString(KEY_BASE_URL, config.baseUrl.trim())
            putString(KEY_API_KEY, encryptSecret(config.apiKey.trim()))
            putString(KEY_MODEL, config.model.trim().ifBlank { DEFAULT_MODEL })
            putString(KEY_PROVIDER, config.provider.prefValue)
            putString(KEY_API_VERSION, config.apiVersion.trim())
        }
    }

    fun saveAll(configs: List<LiteLlmConfig>) {
        val usable = configs.filter { it.isUsable }
        val primary = usable.firstOrNull() ?: return
        save(primary)
        prefs.edit { putString(KEY_CONFIGS_JSON, usable.drop(1).toJsonArray().toString()) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private fun readFallbacks(): List<LiteLlmConfig> =
        runCatching {
            val raw = prefs.getString(KEY_CONFIGS_JSON, "").orEmpty()
            if (raw.isBlank()) return@runCatching emptyList()
            val array = JSONArray(raw)
            val configs = buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toConfig()?.let(::add)
                }
            }
            if (ENCRYPTED_PREFIX !in raw && configs.any { it.apiKey.isNotBlank() }) {
                prefs.edit { putString(KEY_CONFIGS_JSON, configs.toJsonArray().toString()) }
            }
            configs
        }.getOrDefault(emptyList())

    private fun List<LiteLlmConfig>.toJsonArray(): JSONArray =
        JSONArray().also { array ->
            forEach { config ->
                array.put(
                    JSONObject()
                        .put(KEY_BASE_URL, config.baseUrl.trim())
                        .put(KEY_API_KEY, encryptSecret(config.apiKey.trim()))
                        .put(KEY_MODEL, config.model.trim())
                        .put(KEY_PROVIDER, config.provider.prefValue)
                        .put(KEY_API_VERSION, config.apiVersion.trim()),
                )
            }
        }

    private fun JSONObject.toConfig(): LiteLlmConfig =
        LiteLlmConfig(
            baseUrl = optString(KEY_BASE_URL),
            apiKey = decryptSecret(optString(KEY_API_KEY)),
            model = optString(KEY_MODEL, DEFAULT_MODEL),
            provider = AiProvider.fromPref(optString(KEY_PROVIDER)),
            apiVersion = optString(KEY_API_VERSION),
        )

    private fun encryptSecret(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return ENCRYPTED_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decryptSecret(value: String): String {
        if (value.isBlank()) return ""
        if (!value.startsWith(ENCRYPTED_PREFIX)) return value
        return runCatching {
            val payload = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(payload)
            val ivSize = buffer.get().toInt()
            require(ivSize in 12..16 && buffer.remaining() > ivSize)
            val iv = ByteArray(ivSize).also(buffer::get)
            val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        const val PREFS_NAME = "litellm"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_PROVIDER = "provider"
        const val KEY_API_VERSION = "api_version"
        const val KEY_CONFIGS_JSON = "configs_json"
        const val KEY_NEXT_PROVIDER_INDEX = "next_provider_index"
        const val DEFAULT_MODEL = "gpt-5.4-mini"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "wonderfood_litellm_api_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENCRYPTED_PREFIX = "enc:v1:"
    }
}

private fun Int.floorMod(modulus: Int): Int =
    ((this % modulus) + modulus) % modulus
