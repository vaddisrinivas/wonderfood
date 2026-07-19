package com.wonderfood.core.data.backend

public class BackendRouter(
    private val configurationStore: BackendConfigurationStore,
    backends: Set<FoodBackend>,
) {
    private val backendsByType: Map<BackendType, FoodBackend> = backends.associateBy { it.descriptor.type }

    init {
        require(backendsByType.size == backends.size) { "Only one backend adapter may be registered per type." }
    }

    public fun availableBackends(): List<BackendDescriptor> =
        backendsByType.values
            .map { it.descriptor }
            .sortedBy { it.type.ordinal }

    public suspend fun activeBackend(): FoodBackend {
        val config = configurationStore.activeConfiguration()
            ?: return requireBackend(BackendType.LOCAL_SQLITE)
        return requireBackend(config.type)
    }

    public suspend fun connectAndActivate(config: BackendConfig): ConnectionResult {
        val backend = requireBackend(config.type)
        val result = backend.connect(config)
        if (result is ConnectionResult.Connected) {
            configurationStore.saveActiveConfiguration(config)
        }
        return result
    }

    public suspend fun switchToLocal(): ConnectionResult =
        connectAndActivate(LocalSqliteConfig())

    public suspend fun clearActiveConfiguration() {
        configurationStore.clearActiveConfiguration()
    }

    private fun requireBackend(type: BackendType): FoodBackend =
        backendsByType[type] ?: error("No WonderFood backend registered for $type.")
}

public interface BackendConfigurationStore {
    public suspend fun activeConfiguration(): BackendConfig?
    public suspend fun saveActiveConfiguration(config: BackendConfig)
    public suspend fun clearActiveConfiguration()
}

public interface CredentialVault {
    public suspend fun put(ref: CredentialRef, secret: BackendSecret)
    public suspend fun get(ref: CredentialRef): BackendSecret?
    public suspend fun delete(ref: CredentialRef)
}

public sealed interface BackendSecret {
    public data class OAuthAccess(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtEpochMillis: Long?,
    ) : BackendSecret {
        init {
            require(accessToken.isNotBlank()) { "OAuth access token must not be blank." }
        }
    }

    public data class BearerToken(val token: String) : BackendSecret {
        init {
            require(token.isNotBlank()) { "Bearer token must not be blank." }
        }
    }

    public data class ApiToken(val token: String) : BackendSecret {
        init {
            require(token.isNotBlank()) { "API token must not be blank." }
        }
    }

    public data class ConnectionString(val value: String) : BackendSecret {
        init {
            require(value.isNotBlank()) { "Connection string must not be blank." }
        }
    }
}
