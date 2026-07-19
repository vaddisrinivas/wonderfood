package com.wonderfood.core.data.backend

import com.wonderfood.core.ai.CommandEnvelope
import com.wonderfood.core.model.WonderFoodSnapshot
import kotlinx.serialization.json.JsonObject

public const val WONDERFOOD_BACKEND_SCHEMA_VERSION: Int = 1

public interface FoodBackend {
    public val descriptor: BackendDescriptor
    public val capabilities: Set<BackendCapability>

    public suspend fun connect(config: BackendConfig): ConnectionResult
    public suspend fun healthCheck(): BackendHealth
    public suspend fun bootstrap(): WonderFoodSnapshot
    public suspend fun pull(after: ChangeCursor?): ChangePage
    public suspend fun push(commands: List<CommandEnvelope>): PushResult
    public suspend fun exportSnapshot(): WonderFoodSnapshot
    public suspend fun disconnect()
}

public data class BackendDescriptor(
    val type: BackendType,
    val displayName: String,
    val setup: BackendSetupKind,
    val description: String,
) {
    init {
        require(displayName.isNotBlank()) { "Backend display name must not be blank." }
        require(description.isNotBlank()) { "Backend description must not be blank." }
    }
}

public enum class BackendType {
    LOCAL_SQLITE,
    GOOGLE_SHEETS,
    NOTION,
    POSTGRES,
}

public enum class BackendSetupKind {
    NONE,
    GOOGLE_OAUTH,
    NOTION_TOKEN,
    HOSTED_API,
    DIRECT_CONNECTION_STRING,
}

public enum class BackendCapability {
    LOCAL_AUTHORITY,
    OFFLINE_CACHE,
    REMOTE_AUTHORITY,
    HUMAN_EDITABLE,
    GOOGLE_OAUTH,
    TOKEN_AUTH,
    HOSTED_API,
    CONNECTION_STRING,
    CHANGE_PULL,
    COMMAND_PUSH,
    EXPORT_SNAPSHOT,
}

public sealed interface BackendConfig {
    public val type: BackendType
    public val schemaVersion: Int
}

public data class LocalSqliteConfig(
    override val schemaVersion: Int = WONDERFOOD_BACKEND_SCHEMA_VERSION,
) : BackendConfig {
    override val type: BackendType = BackendType.LOCAL_SQLITE
}

public data class GoogleSheetsConfig(
    val spreadsheetUrl: String,
    val spreadsheetId: String,
    val accountEmail: String?,
    val credentialRef: CredentialRef,
    override val schemaVersion: Int = WONDERFOOD_BACKEND_SCHEMA_VERSION,
) : BackendConfig {
    override val type: BackendType = BackendType.GOOGLE_SHEETS

    init {
        require(spreadsheetUrl.isNotBlank()) { "Google Sheet URL must not be blank." }
        require(spreadsheetId.isNotBlank()) { "Google spreadsheet ID must not be blank." }
    }
}

public data class NotionConfig(
    val pageUrl: String,
    val rootPageId: String,
    val workspaceName: String?,
    val credentialRef: CredentialRef,
    override val schemaVersion: Int = WONDERFOOD_BACKEND_SCHEMA_VERSION,
) : BackendConfig {
    override val type: BackendType = BackendType.NOTION

    init {
        require(pageUrl.isNotBlank()) { "Notion page URL must not be blank." }
        require(rootPageId.isNotBlank()) { "Notion root page ID must not be blank." }
    }
}

public data class PostgresConfig(
    val connectionMode: PostgresConnectionMode,
    val endpoint: String,
    val householdId: String,
    val credentialRef: CredentialRef,
    override val schemaVersion: Int = WONDERFOOD_BACKEND_SCHEMA_VERSION,
) : BackendConfig {
    override val type: BackendType = BackendType.POSTGRES

    init {
        require(endpoint.isNotBlank()) { "Postgres endpoint must not be blank." }
        require(householdId.isNotBlank()) { "Household ID must not be blank." }
    }
}

public enum class PostgresConnectionMode {
    SUPABASE,
    POSTGREST,
    WONDERFOOD_SERVER,
    DIRECT_DSN,
}

public data class CredentialRef(
    val provider: BackendType,
    val alias: String,
) {
    init {
        require(alias.isNotBlank()) { "Credential alias must not be blank." }
    }
}

public sealed interface ConnectionResult {
    public data class Connected(
        val descriptor: BackendDescriptor,
        val snapshotSummary: SnapshotSummary,
    ) : ConnectionResult

    public data class Failed(
        val reason: BackendFailureReason,
        val message: String,
        val recoverable: Boolean,
    ) : ConnectionResult {
        init {
            require(message.isNotBlank()) { "Connection failure message must not be blank." }
        }
    }
}

public data class BackendHealth(
    val status: BackendHealthStatus,
    val checkedAt: String,
    val message: String,
    val activeConfigType: BackendType?,
) {
    init {
        require(checkedAt.isNotBlank()) { "Backend health timestamp must not be blank." }
        require(message.isNotBlank()) { "Backend health message must not be blank." }
    }
}

public enum class BackendHealthStatus {
    READY,
    DEGRADED,
    DISCONNECTED,
}

public enum class BackendFailureReason {
    INVALID_CONFIG,
    AUTH_CANCELLED,
    MISSING_PERMISSION,
    VIEW_ONLY,
    SCHEMA_MISMATCH,
    NETWORK,
    UNSUPPORTED,
    UNKNOWN,
}

public data class ChangeCursor(val token: String) {
    init {
        require(token.isNotBlank()) { "Change cursor token must not be blank." }
    }
}

public data class ChangePage(
    val cursor: ChangeCursor?,
    val changes: List<BackendChange>,
    val hasMore: Boolean,
)

public data class BackendChange(
    val entityType: String,
    val entityId: String,
    val version: Long,
    val operation: BackendChangeOperation,
    val payload: JsonObject,
) {
    init {
        require(entityType.isNotBlank()) { "Change entity type must not be blank." }
        require(entityId.isNotBlank()) { "Change entity ID must not be blank." }
        require(version >= 0L) { "Change version must not be negative." }
    }
}

public enum class BackendChangeOperation {
    UPSERT,
    ARCHIVE,
    TOMBSTONE,
}

public data class PushResult(
    val accepted: List<PushedCommand>,
    val rejected: List<RejectedCommand>,
    val nextCursor: ChangeCursor?,
) {
    val acceptedCount: Int = accepted.size
    val rejectedCount: Int = rejected.size
}

public data class PushedCommand(
    val envelopeId: String,
    val idempotencyKey: String,
    val version: Long,
) {
    init {
        require(envelopeId.isNotBlank()) { "Envelope ID must not be blank." }
        require(idempotencyKey.isNotBlank()) { "Idempotency key must not be blank." }
        require(version >= 0L) { "Pushed command version must not be negative." }
    }
}

public data class RejectedCommand(
    val envelopeId: String,
    val idempotencyKey: String,
    val reason: BackendFailureReason,
    val message: String,
) {
    init {
        require(envelopeId.isNotBlank()) { "Envelope ID must not be blank." }
        require(idempotencyKey.isNotBlank()) { "Idempotency key must not be blank." }
        require(message.isNotBlank()) { "Rejected command message must not be blank." }
    }
}

public data class SnapshotSummary(
    val schemaVersion: Int,
    val foods: Int,
    val stockLots: Int,
    val recipes: Int,
    val mealPlans: Int,
    val shoppingItems: Int,
)

public fun WonderFoodSnapshot.summary(): SnapshotSummary =
    SnapshotSummary(
        schemaVersion = schemaVersion,
        foods = foods.size,
        stockLots = stockLots.size,
        recipes = recipes.size,
        mealPlans = mealPlans.size,
        shoppingItems = shoppingItems.size,
    )
