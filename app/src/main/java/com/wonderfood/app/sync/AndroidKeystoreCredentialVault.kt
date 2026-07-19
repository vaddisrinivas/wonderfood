package com.wonderfood.app.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.wonderfood.core.data.backend.BackendSecret
import com.wonderfood.core.data.backend.CredentialRef
import com.wonderfood.core.data.backend.CredentialVault
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class AndroidKeystoreCredentialVault(
    context: Context,
    private val keyAlias: String = KEYSTORE_ALIAS,
) : CredentialVault {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun put(ref: CredentialRef, secret: BackendSecret) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putString(ref.preferenceKey, encrypt(CredentialSecretCodec.encode(secret)))
            }
        }
    }

    override suspend fun get(ref: CredentialRef): BackendSecret? =
        withContext(Dispatchers.IO) {
            prefs.getString(ref.preferenceKey, null)
                ?.let(::decrypt)
                ?.let(CredentialSecretCodec::decode)
        }

    override suspend fun delete(ref: CredentialRef) {
        withContext(Dispatchers.IO) {
            prefs.edit { remove(ref.preferenceKey) }
        }
    }

    private fun encrypt(value: String): String {
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

    private fun decrypt(value: String): String {
        require(value.startsWith(ENCRYPTED_PREFIX)) { "Credential payload is not encrypted." }
        val payload = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(payload)
        val ivSize = buffer.get().toInt()
        require(ivSize in 12..16 && buffer.remaining() > ivSize) { "Credential payload is malformed." }
        val iv = ByteArray(ivSize).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
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

    private val CredentialRef.preferenceKey: String
        get() = "${provider.name}:$alias"

    private companion object {
        const val PREFS_NAME = "backend_credentials"
        const val KEYSTORE_ALIAS = "wonderfood_backend_credentials_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENCRYPTED_PREFIX = "enc:v1:"
    }
}

internal object CredentialSecretCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(secret: BackendSecret): String {
        val payload = when (secret) {
            is BackendSecret.ApiToken -> {
                buildJsonObject {
                    put("type", JsonPrimitive("api_token"))
                    put("token", JsonPrimitive(secret.token))
                }
            }
            is BackendSecret.BearerToken -> {
                buildJsonObject {
                    put("type", JsonPrimitive("bearer_token"))
                    put("token", JsonPrimitive(secret.token))
                }
            }
            is BackendSecret.ConnectionString -> {
                buildJsonObject {
                    put("type", JsonPrimitive("connection_string"))
                    put("value", JsonPrimitive(secret.value))
                }
            }
            is BackendSecret.OAuthAccess -> {
                buildJsonObject {
                    put("type", JsonPrimitive("oauth_access"))
                    put("access_token", JsonPrimitive(secret.accessToken))
                    put("refresh_token", JsonPrimitive(secret.refreshToken))
                    put("expires_at", JsonPrimitive(secret.expiresAtEpochMillis))
                }
            }
        }
        return payload.toString()
    }

    fun decode(value: String): BackendSecret {
        val payload = json.parseToJsonElement(value).jsonObject
        return when (payload.requiredString("type")) {
            "api_token" -> BackendSecret.ApiToken(payload.requiredString("token"))
            "bearer_token" -> BackendSecret.BearerToken(payload.requiredString("token"))
            "connection_string" -> BackendSecret.ConnectionString(payload.requiredString("value"))
            "oauth_access" -> BackendSecret.OAuthAccess(
                accessToken = payload.requiredString("access_token"),
                refreshToken = payload.optionalString("refresh_token"),
                expiresAtEpochMillis = payload.optionalLong("expires_at"),
            )
            else -> error("Unsupported credential secret payload.")
        }
    }

    private fun JsonObject.requiredString(name: String): String =
        requireNotNull(this[name]?.jsonPrimitive?.contentOrNull) { "Missing credential field: $name" }

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.optionalLong(name: String): Long? =
        this[name]?.jsonPrimitive?.longOrNull
}
