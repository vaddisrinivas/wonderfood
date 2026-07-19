package com.wonderfood.core.data.room

import com.wonderfood.core.ai.CommandEnvelope
import com.wonderfood.core.data.backend.BackendCapability
import com.wonderfood.core.data.backend.BackendDescriptor
import com.wonderfood.core.data.backend.BackendFailureReason
import com.wonderfood.core.data.backend.BackendHealth
import com.wonderfood.core.data.backend.BackendHealthStatus
import com.wonderfood.core.data.backend.BackendSetupKind
import com.wonderfood.core.data.backend.BackendType
import com.wonderfood.core.data.backend.ChangeCursor
import com.wonderfood.core.data.backend.ChangePage
import com.wonderfood.core.data.backend.ConnectionResult
import com.wonderfood.core.data.backend.FoodBackend
import com.wonderfood.core.data.backend.LocalSqliteConfig
import com.wonderfood.core.data.backend.PushResult
import com.wonderfood.core.data.backend.RejectedCommand
import com.wonderfood.core.data.backend.summary
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec

public class LocalSqliteBackend(
    database: WonderFoodDatabase,
    private val clock: () -> String,
) : FoodBackend {
    private val dao = database.wonderFoodDao()
    private var connected: Boolean = false

    override val descriptor: BackendDescriptor = BackendDescriptor(
        type = BackendType.LOCAL_SQLITE,
        displayName = "On this phone",
        setup = BackendSetupKind.NONE,
        description = "Private local WonderFood database on this Android device.",
    )

    override val capabilities: Set<BackendCapability> = setOf(
        BackendCapability.LOCAL_AUTHORITY,
        BackendCapability.OFFLINE_CACHE,
        BackendCapability.EXPORT_SNAPSHOT,
    )

    override suspend fun connect(config: com.wonderfood.core.data.backend.BackendConfig): ConnectionResult {
        if (config !is LocalSqliteConfig) {
            return ConnectionResult.Failed(
                reason = BackendFailureReason.INVALID_CONFIG,
                message = "Local SQLite backend requires local configuration.",
                recoverable = true,
            )
        }
        connected = true
        return ConnectionResult.Connected(descriptor, exportSnapshot().summary())
    }

    override suspend fun healthCheck(): BackendHealth =
        BackendHealth(
            status = if (connected) BackendHealthStatus.READY else BackendHealthStatus.DISCONNECTED,
            checkedAt = clock(),
            message = if (connected) "Local food database is ready." else "Local food database is not connected.",
            activeConfigType = BackendType.LOCAL_SQLITE,
        )

    override suspend fun bootstrap(): WonderFoodSnapshot = exportSnapshot()

    override suspend fun pull(after: ChangeCursor?): ChangePage =
        ChangePage(cursor = after, changes = emptyList(), hasMore = false)

    override suspend fun push(commands: List<CommandEnvelope>): PushResult =
        PushResult(
            accepted = emptyList(),
            rejected = commands.map { command ->
                RejectedCommand(
                    envelopeId = command.envelopeId,
                    idempotencyKey = command.idempotencyKey,
                    reason = BackendFailureReason.UNSUPPORTED,
                    message = "Local SQLite command-envelope execution is not wired yet.",
                )
            },
            nextCursor = null,
        )

    override suspend fun exportSnapshot(): WonderFoodSnapshot =
        WonderFoodSnapshot(
            schemaVersion = WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION,
            pages = dao.getPages().map { it.toPage() },
            foods = dao.getFoods().map { it.toFood() },
            foodAliases = dao.getFoodAliases().map { it.toFoodAlias() },
            stockLots = dao.getStockLots().map { it.toStockLot() },
            nutritionSnapshots = emptyList(),
            recipes = emptyList(),
            mealPlans = emptyList(),
            mealLogs = emptyList(),
            shoppingItems = emptyList(),
            receipts = emptyList(),
            foodEvents = emptyList(),
            relations = emptyList(),
            attachments = emptyList(),
        )

    override suspend fun disconnect() {
        connected = false
    }
}
