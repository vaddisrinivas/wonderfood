package com.wonderfood.core.model.household

private fun requireSyncText(value: String, label: String): String {
    require(value.isNotBlank()) { "$label must not be blank." }
    return value
}

enum class DataHomeAdapterOperation {
    PROVISION,
    PROBE,
    INITIAL_SCAN,
    PULL,
    PUSH,
    HEALTH,
    DISCONNECT,
    REPAIR,
}

enum class SyncOutboxStatus {
    PENDING,
    IN_FLIGHT,
    ACKNOWLEDGED,
    FAILED_RETRYABLE,
    FAILED_NEEDS_REVIEW,
}

enum class TombstoneReason {
    ARCHIVED_BY_APP,
    ARCHIVED_BY_DATA_HOME,
    PROVIDER_DELETE_IMPORTED,
    MERGED_DUPLICATE,
    MAINTENANCE,
}

data class PayloadHash(val value: String) {
    init { requireSyncText(value, "Payload hash") }
}

data class RemoteBinding(
    val connectionId: ConnectionId,
    val entityType: HouseholdEntityType,
    val entityId: EntityId,
    val remoteObjectId: String,
    val remoteParentId: String? = null,
    val remoteSchemaFingerprint: String? = null,
) {
    init { requireSyncText(remoteObjectId, "Remote object ID") }
}

data class SyncRecordEnvelope(
    val householdId: HouseholdId,
    val entityType: HouseholdEntityType,
    val entityId: EntityId,
    val schemaVersion: Int,
    val revision: Long,
    val createdAt: UtcTimestamp,
    val updatedAt: UtcTimestamp,
    val archivedAt: UtcTimestamp? = null,
    val originDeviceId: String,
    val lastCommandId: CommandId,
    val payloadHash: PayloadHash,
) {
    init {
        require(schemaVersion == HouseholdWorkspaceContract.SCHEMA_VERSION) {
            "Sync envelope schema version must match the household workspace schema."
        }
        require(revision >= 0) { "Sync envelope revision must not be negative." }
        require(updatedAt.epochMillis >= createdAt.epochMillis) { "Sync envelope updated time precedes created time." }
        archivedAt?.let {
            require(it.epochMillis >= createdAt.epochMillis) { "Sync envelope archived time precedes created time." }
        }
        requireSyncText(originDeviceId, "Origin device ID")
    }
}

data class SyncBase(
    val binding: RemoteBinding,
    val envelope: SyncRecordEnvelope,
    val localRevision: Long,
    val remoteRevision: String,
    val basePayloadHash: PayloadHash,
    val pulledAt: UtcTimestamp,
) {
    init {
        require(localRevision >= 0) { "Local revision must not be negative." }
        requireSyncText(remoteRevision, "Remote revision")
    }
}

data class SyncCursor(
    val id: EntityId,
    val householdId: HouseholdId,
    val connectionId: ConnectionId,
    val cursor: String,
    val pulledAt: UtcTimestamp,
    val remoteHighWatermark: String? = null,
) {
    init { requireSyncText(cursor, "Sync cursor") }
}

data class SyncOutboxRecord(
    val id: EntityId,
    val connectionId: ConnectionId,
    val commandId: CommandId,
    val operation: DataHomeAdapterOperation,
    val envelope: SyncRecordEnvelope,
    val idempotencyKey: String,
    val status: SyncOutboxStatus,
    val retryCount: Int = 0,
    val lastError: String? = null,
) {
    init {
        require(operation == DataHomeAdapterOperation.PUSH || operation == DataHomeAdapterOperation.REPAIR) {
            "Outbox records may only push or repair remote state."
        }
        requireSyncText(idempotencyKey, "Outbox idempotency key")
        require(retryCount >= 0) { "Retry count must not be negative." }
    }
}

data class TombstoneRecord(
    val metadata: EntityMetadata,
    val entityType: HouseholdEntityType,
    val entityId: EntityId,
    val reason: TombstoneReason,
    val commandId: CommandId,
)

data class ConflictRecord(
    val metadata: EntityMetadata,
    val entityType: HouseholdEntityType,
    val entityId: EntityId,
    val baseHash: PayloadHash,
    val appHash: PayloadHash,
    val dataHomeHash: PayloadHash,
    val decision: SyncDecision,
    val appChangedFields: Set<String>,
    val dataHomeChangedFields: Set<String>,
) {
    init {
        require(decision.action == SyncDecisionAction.NEEDS_REVIEW) {
            "Only review-required decisions are stored as conflict records."
        }
        require(appChangedFields.isNotEmpty() || dataHomeChangedFields.isNotEmpty()) {
            "Conflict records require changed fields."
        }
    }
}

data class LatestSafetySnapshot(
    val id: EntityId,
    val householdId: HouseholdId,
    val reason: String,
    val createdAt: UtcTimestamp,
    val localReplicaHash: PayloadHash,
    val activeDataHome: DataHomeKind,
    val connectionId: ConnectionId? = null,
    val commandId: CommandId? = null,
) {
    init { requireSyncText(reason, "Safety snapshot reason") }
}

data class RecoverySnapshot(
    val id: EntityId,
    val householdId: HouseholdId,
    val reason: String,
    val createdAt: UtcTimestamp,
    val payloadHash: PayloadHash,
    val objectCount: Int,
    val commandId: CommandId? = null,
) {
    init {
        requireSyncText(reason, "Recovery snapshot reason")
        require(objectCount >= 0) { "Recovery snapshot object count must not be negative." }
    }
}

object DataHomeAdapterContract {
    val requiredOperations: Set<DataHomeAdapterOperation> = DataHomeAdapterOperation.entries.toSet()

    val operationsThatMayCreateSafetySnapshot: Set<DataHomeAdapterOperation> = setOf(
        DataHomeAdapterOperation.PROVISION,
        DataHomeAdapterOperation.INITIAL_SCAN,
        DataHomeAdapterOperation.REPAIR,
        DataHomeAdapterOperation.DISCONNECT,
    )
}
