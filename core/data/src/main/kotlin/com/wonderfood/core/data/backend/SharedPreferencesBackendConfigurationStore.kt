package com.wonderfood.core.data.backend

import android.content.Context
import android.content.SharedPreferences

public class SharedPreferencesBackendConfigurationStore(
    context: Context,
) : BackendConfigurationStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun activeConfiguration(): BackendConfig? =
        when (prefs.getString(KEY_TYPE, null)?.let { runCatching { BackendType.valueOf(it) }.getOrNull() }) {
            BackendType.LOCAL_SQLITE -> LocalSqliteConfig()
            BackendType.GOOGLE_SHEETS -> googleSheetsConfigOrNull()
            BackendType.NOTION -> notionConfigOrNull()
            BackendType.POSTGRES -> postgresConfigOrNull()
            null -> null
        }

    override suspend fun saveActiveConfiguration(config: BackendConfig) {
        updatePrefs {
            putString(KEY_TYPE, config.type.name)
            putInt(KEY_SCHEMA_VERSION, config.schemaVersion)
            when (config) {
                is LocalSqliteConfig -> clearProviderFields()
                is GoogleSheetsConfig -> {
                    putString(KEY_URL, config.spreadsheetUrl)
                    putString(KEY_EXTERNAL_ID, config.spreadsheetId)
                    putString(KEY_ACCOUNT_LABEL, config.accountEmail)
                    putString(KEY_CREDENTIAL_PROVIDER, config.credentialRef.provider.name)
                    putString(KEY_CREDENTIAL_ALIAS, config.credentialRef.alias)
                }
                is NotionConfig -> {
                    putString(KEY_URL, config.pageUrl)
                    putString(KEY_EXTERNAL_ID, config.rootPageId)
                    putString(KEY_ACCOUNT_LABEL, config.workspaceName)
                    putString(KEY_CREDENTIAL_PROVIDER, config.credentialRef.provider.name)
                    putString(KEY_CREDENTIAL_ALIAS, config.credentialRef.alias)
                }
                is PostgresConfig -> {
                    putString(KEY_CONNECTION_MODE, config.connectionMode.name)
                    putString(KEY_URL, config.endpoint)
                    putString(KEY_EXTERNAL_ID, config.householdId)
                    putString(KEY_CREDENTIAL_PROVIDER, config.credentialRef.provider.name)
                    putString(KEY_CREDENTIAL_ALIAS, config.credentialRef.alias)
                }
            }
        }
    }

    override suspend fun clearActiveConfiguration() {
        updatePrefs { clear() }
    }

    public fun onboardingDismissed(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_DISMISSED, false)

    public fun setOnboardingDismissed(dismissed: Boolean) {
        updatePrefs { putBoolean(KEY_ONBOARDING_DISMISSED, dismissed) }
    }

    private fun updatePrefs(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply {
            block()
            apply()
        }
    }

    private fun googleSheetsConfigOrNull(): GoogleSheetsConfig? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val spreadsheetId = prefs.getString(KEY_EXTERNAL_ID, null) ?: return null
        val credentialRef = credentialRefOrNull(BackendType.GOOGLE_SHEETS) ?: return null
        return GoogleSheetsConfig(
            spreadsheetUrl = url,
            spreadsheetId = spreadsheetId,
            accountEmail = prefs.getString(KEY_ACCOUNT_LABEL, null),
            credentialRef = credentialRef,
            schemaVersion = prefs.getInt(KEY_SCHEMA_VERSION, WONDERFOOD_BACKEND_SCHEMA_VERSION),
        )
    }

    private fun notionConfigOrNull(): NotionConfig? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val rootPageId = prefs.getString(KEY_EXTERNAL_ID, null) ?: return null
        val credentialRef = credentialRefOrNull(BackendType.NOTION) ?: return null
        return NotionConfig(
            pageUrl = url,
            rootPageId = rootPageId,
            workspaceName = prefs.getString(KEY_ACCOUNT_LABEL, null),
            credentialRef = credentialRef,
            schemaVersion = prefs.getInt(KEY_SCHEMA_VERSION, WONDERFOOD_BACKEND_SCHEMA_VERSION),
        )
    }

    private fun postgresConfigOrNull(): PostgresConfig? {
        val endpoint = prefs.getString(KEY_URL, null) ?: return null
        val householdId = prefs.getString(KEY_EXTERNAL_ID, null) ?: return null
        val credentialRef = credentialRefOrNull(BackendType.POSTGRES) ?: return null
        val mode = prefs.getString(KEY_CONNECTION_MODE, null)
            ?.let { runCatching { PostgresConnectionMode.valueOf(it) }.getOrNull() }
            ?: return null
        return PostgresConfig(
            connectionMode = mode,
            endpoint = endpoint,
            householdId = householdId,
            credentialRef = credentialRef,
            schemaVersion = prefs.getInt(KEY_SCHEMA_VERSION, WONDERFOOD_BACKEND_SCHEMA_VERSION),
        )
    }

    private fun credentialRefOrNull(expectedType: BackendType): CredentialRef? {
        val provider = prefs.getString(KEY_CREDENTIAL_PROVIDER, null)
            ?.let { runCatching { BackendType.valueOf(it) }.getOrNull() }
            ?: return null
        val alias = prefs.getString(KEY_CREDENTIAL_ALIAS, null) ?: return null
        if (provider != expectedType) return null
        return CredentialRef(provider, alias)
    }

    private fun SharedPreferences.Editor.clearProviderFields() {
        remove(KEY_URL)
        remove(KEY_EXTERNAL_ID)
        remove(KEY_ACCOUNT_LABEL)
        remove(KEY_CREDENTIAL_PROVIDER)
        remove(KEY_CREDENTIAL_ALIAS)
        remove(KEY_CONNECTION_MODE)
    }

    private companion object {
        const val PREFS_NAME = "wonderfood_backend_configuration"
        const val KEY_TYPE = "type"
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_URL = "url"
        const val KEY_EXTERNAL_ID = "external_id"
        const val KEY_ACCOUNT_LABEL = "account_label"
        const val KEY_CREDENTIAL_PROVIDER = "credential_provider"
        const val KEY_CREDENTIAL_ALIAS = "credential_alias"
        const val KEY_CONNECTION_MODE = "connection_mode"
        const val KEY_ONBOARDING_DISMISSED = "onboarding_dismissed"
    }
}
