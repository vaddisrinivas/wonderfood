package com.wonderfood.app.sync

import com.wonderfood.core.data.backend.PostgresConnectionMode
import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.LatestSafetySnapshot
import com.wonderfood.core.model.household.UtcTimestamp
import java.time.Instant

sealed interface DataHomeConnection {
    val remoteId: String

    data class GoogleSheets(
        val accessToken: String,
        val spreadsheetId: String,
    ) : DataHomeConnection {
        override val remoteId: String = spreadsheetId
    }

    data class Notion(
        val token: String,
        val pageId: String,
    ) : DataHomeConnection {
        override val remoteId: String = pageId
    }

    data class Postgres(
        val mode: PostgresConnectionMode,
        val endpoint: String,
        val token: String,
        val householdId: String,
    ) : DataHomeConnection {
        override val remoteId: String = householdId
    }
}

enum class DataHomeKind {
    GOOGLE_SHEETS,
    NOTION,
    POSTGRES,
}

enum class DataHomeStatus {
    OK,
    EMPTY,
    ERROR,
}

interface DataHomeAdapter {
    val kind: DataHomeKind

    fun probe(connection: DataHomeConnection): DataHomeHealth

    fun provision(connection: DataHomeConnection): DataHomeProvisionResult

    fun initialScan(connection: DataHomeConnection): DataHomeInitialScanResult

    fun pull(
        connection: DataHomeConnection,
        baseSnapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomePullResult

    fun push(
        connection: DataHomeConnection,
        snapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomePushResult

    fun health(connection: DataHomeConnection): DataHomeHealth = probe(connection)

    fun repair(connection: DataHomeConnection): DataHomeRepairResult

    fun disconnect(connection: DataHomeConnection): DataHomeDisconnectResult =
        DataHomeDisconnectResult(kind = kind, remoteId = connection.remoteId, localCredentialsCleared = true)
}

data class DataHomeRetryPolicy(
    val maxAttempts: Int = 3,
) {
    init {
        require(maxAttempts >= 1) { "Data-home retry attempts must be at least 1." }
    }
}

class RetryingDataHomeAdapter(
    private val delegate: DataHomeAdapter,
    private val retryPolicy: DataHomeRetryPolicy = DataHomeRetryPolicy(),
) : DataHomeAdapter {
    override val kind: DataHomeKind = delegate.kind

    override fun probe(connection: DataHomeConnection): DataHomeHealth =
        retryValue(
            action = { delegate.probe(connection) },
            shouldRetry = { it.status == DataHomeStatus.ERROR },
        )

    override fun provision(connection: DataHomeConnection): DataHomeProvisionResult =
        retry { delegate.provision(connection) }

    override fun initialScan(connection: DataHomeConnection): DataHomeInitialScanResult =
        retry { delegate.initialScan(connection) }

    override fun pull(
        connection: DataHomeConnection,
        baseSnapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomePullResult =
        retry { delegate.pull(connection, baseSnapshot, updatedAt) }

    override fun push(
        connection: DataHomeConnection,
        snapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomePushResult =
        retry { delegate.push(connection, snapshot, updatedAt) }

    override fun health(connection: DataHomeConnection): DataHomeHealth =
        probe(connection)

    override fun repair(connection: DataHomeConnection): DataHomeRepairResult =
        retry { delegate.repair(connection) }

    override fun disconnect(connection: DataHomeConnection): DataHomeDisconnectResult =
        delegate.disconnect(connection)

    private fun <T> retry(action: () -> T): T =
        retryValue(action = action, shouldRetry = { false })

    private fun <T> retryValue(action: () -> T, shouldRetry: (T) -> Boolean): T {
        var lastFailure: Throwable? = null
        repeat(retryPolicy.maxAttempts) { attempt ->
            try {
                val value = action()
                if (!shouldRetry(value) || attempt == retryPolicy.maxAttempts - 1) return value
            } catch (error: Throwable) {
                lastFailure = error
                if (attempt == retryPolicy.maxAttempts - 1) throw error
            }
        }
        throw requireNotNull(lastFailure) { "Retry loop ended without a value or failure." }
    }
}

class DataHomeSyncCoordinator(
    private val adapter: DataHomeAdapter,
) {
    fun sync(
        connection: DataHomeConnection,
        localSnapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomeSyncResult {
        val pull = adapter.pull(connection, localSnapshot, updatedAt)
        val snapshotToPush = pull.canonicalSnapshot ?: localSnapshot
        val push = adapter.push(connection, snapshotToPush, updatedAt)
        return DataHomeSyncResult(
            kind = adapter.kind,
            remoteId = connection.remoteId,
            pull = pull,
            push = push,
            pushedSnapshot = snapshotToPush,
        )
    }
}

class GoogleSheetsDataHomeAdapter(
    private val gateway: GoogleSheetsSnapshotGateway,
) : DataHomeAdapter {
    override val kind: DataHomeKind = DataHomeKind.GOOGLE_SHEETS

    override fun probe(connection: DataHomeConnection): DataHomeHealth {
        val credentials = connection.requireGoogleSheets()
        return runCatching {
            val rows = gateway.readWorkspaceRows(credentials.accessToken, credentials.spreadsheetId)
            DataHomeHealth(
                kind = kind,
                remoteId = credentials.spreadsheetId,
                status = if (rows.isEmpty()) DataHomeStatus.EMPTY else DataHomeStatus.OK,
                rowCount = rows.size,
                summary = "Read ${rows.size} workspace rows.",
            )
        }.getOrElse { error ->
            DataHomeHealth(
                kind = kind,
                remoteId = credentials.spreadsheetId,
                status = DataHomeStatus.ERROR,
                rowCount = 0,
                summary = error.message.orEmpty().redactSecrets(credentials.accessToken),
            )
        }
    }

    override fun provision(connection: DataHomeConnection): DataHomeProvisionResult {
        val credentials = connection.requireGoogleSheets()
        val result = withRedactedProviderFailure(credentials.accessToken) {
            gateway.ensureWonderFoodSchema(credentials.accessToken, credentials.spreadsheetId)
        }
        return DataHomeProvisionResult(
            kind = kind,
            remoteId = result.spreadsheetId,
            createdCount = result.createdTabs.size,
            repairedCount = result.initializedHeaders.size,
            summary = "Provisioned ${result.totalTabs} tabs.",
        )
    }

    override fun initialScan(connection: DataHomeConnection): DataHomeInitialScanResult {
        val credentials = connection.requireGoogleSheets()
        val rows = withRedactedProviderFailure(credentials.accessToken) {
            gateway.readWorkspaceRows(credentials.accessToken, credentials.spreadsheetId)
        }
        return DataHomeInitialScanResult(
            kind = kind,
            remoteId = credentials.spreadsheetId,
            rowCount = rows.size,
            surfaceCount = rows.map { it.tab }.distinct().size,
            hasRemoteSnapshot = false,
            summary = "Scanned ${rows.size} workspace rows across ${rows.map { it.tab }.distinct().size} tabs.",
        )
    }

    override fun pull(
        connection: DataHomeConnection,
        baseSnapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomePullResult {
        val credentials = connection.requireGoogleSheets()
        val rows = withRedactedProviderFailure(credentials.accessToken) {
            gateway.readWorkspaceRows(credentials.accessToken, credentials.spreadsheetId)
        }
        val inbound = GoogleSheetsV4InboundWorkspaceImporter.importRows(
            rows = rows,
            householdId = baseSnapshot.household.id,
            now = updatedAt.toProviderImportTimestamp(),
            defaultCurrency = baseSnapshot.household.defaultCurrency,
            providerKey = "google_sheets",
            baseSnapshot = baseSnapshot,
        )
        return DataHomePullResult(
            kind = kind,
            remoteId = credentials.spreadsheetId,
            rowCount = rows.size,
            workspaceRows = rows,
            canonicalCommands = inbound.commands,
            needsReviewDiagnostics = inbound.diagnostics,
            needsReviewCount = inbound.diagnostics.size,
        )
    }

    override fun push(
        connection: DataHomeConnection,
        snapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomePushResult {
        val credentials = connection.requireGoogleSheets()
        val result = withRedactedProviderFailure(credentials.accessToken) {
            gateway.exportCanonicalWorkspace(credentials.accessToken, credentials.spreadsheetId, snapshot)
        }
        return DataHomePushResult(kind = kind, remoteId = result.spreadsheetId, rowCount = result.rowCount, surfaceCount = result.tabs.size)
    }

    override fun repair(connection: DataHomeConnection): DataHomeRepairResult {
        val provision = provision(connection)
        return DataHomeRepairResult(
            kind = kind,
            remoteId = provision.remoteId,
            repairedCount = provision.repairedCount,
            summary = provision.summary,
        )
    }
}

internal interface GoogleSheetsCanonicalWorkspaceGateway {
    fun exportGraph(
        accessToken: String,
        spreadsheetId: String,
        snapshot: HouseholdSnapshot,
    ): GoogleSheetsExportResult
}

interface NotionWorkspaceGateway {
    fun retrievePage(token: String, pageId: String): NotionPageAccess

    fun ensureWorkspaceDatabases(token: String, pageId: String): NotionWorkspaceProvisionResult

    fun readWorkspaceRows(token: String, pageId: String): List<GoogleSheetsWorkspaceRow>
}

internal interface NotionCanonicalWorkspaceGateway {
    fun exportWorkspace(
        token: String,
        pageId: String,
        snapshot: HouseholdSnapshot,
        updatedAt: String,
    ): NotionWorkspaceExportResult
}

class NotionDataHomeAdapter(
    private val gateway: NotionWorkspaceGateway,
) : DataHomeAdapter {
    override val kind: DataHomeKind = DataHomeKind.NOTION

    override fun probe(connection: DataHomeConnection): DataHomeHealth {
        val credentials = connection.requireNotion()
        return runCatching {
            val page = gateway.retrievePage(credentials.token, credentials.pageId)
            DataHomeHealth(
                kind = kind,
                remoteId = page.pageId,
                status = if (page.reachable) DataHomeStatus.OK else DataHomeStatus.ERROR,
                rowCount = 0,
                summary = page.summary.redactSecrets(credentials.token),
            )
        }.getOrElse { error ->
            DataHomeHealth(
                kind = kind,
                remoteId = credentials.pageId,
                status = DataHomeStatus.ERROR,
                rowCount = 0,
                summary = error.message.orEmpty().redactSecrets(credentials.token),
            )
        }
    }

    override fun provision(connection: DataHomeConnection): DataHomeProvisionResult {
        val credentials = connection.requireNotion()
        val result = withRedactedProviderFailure(credentials.token) {
            gateway.ensureWorkspaceDatabases(credentials.token, credentials.pageId)
        }
        return DataHomeProvisionResult(
            kind = kind,
            remoteId = result.pageId,
            createdCount = result.createdDatabases.size,
            repairedCount = result.databaseIdsByTitle.size,
            summary = "Provisioned ${result.databaseIdsByTitle.size} databases.",
        )
    }

    override fun initialScan(connection: DataHomeConnection): DataHomeInitialScanResult {
        val credentials = connection.requireNotion()
        val rows = withRedactedProviderFailure(credentials.token) {
            gateway.readWorkspaceRows(credentials.token, credentials.pageId)
        }
        return DataHomeInitialScanResult(
            kind = kind,
            remoteId = credentials.pageId,
            rowCount = rows.size,
            surfaceCount = rows.map { it.tab }.distinct().size,
            hasRemoteSnapshot = false,
            summary = "Scanned ${rows.size} workspace rows across ${rows.map { it.tab }.distinct().size} databases.",
        )
    }

    override fun pull(
        connection: DataHomeConnection,
        baseSnapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomePullResult {
        val credentials = connection.requireNotion()
        val rows = withRedactedProviderFailure(credentials.token) {
            gateway.readWorkspaceRows(credentials.token, credentials.pageId)
        }
        val inbound = GoogleSheetsV4InboundWorkspaceImporter.importRows(
            rows = rows,
            householdId = baseSnapshot.household.id,
            now = updatedAt.toProviderImportTimestamp(),
            defaultCurrency = baseSnapshot.household.defaultCurrency,
            providerKey = "notion",
            baseSnapshot = baseSnapshot,
        )
        return DataHomePullResult(
            kind = kind,
            remoteId = credentials.pageId,
            rowCount = rows.size,
            workspaceRows = rows,
            canonicalCommands = inbound.commands,
            needsReviewDiagnostics = inbound.diagnostics,
            needsReviewCount = inbound.diagnostics.size,
        )
    }

    override fun push(
        connection: DataHomeConnection,
        snapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomePushResult {
        val credentials = connection.requireNotion()
        val result = withRedactedProviderFailure(credentials.token) {
            gateway.exportCanonicalWorkspace(credentials.token, credentials.pageId, snapshot, updatedAt)
        }
        return DataHomePushResult(kind = kind, remoteId = result.pageId, rowCount = result.upsertedRows, surfaceCount = result.createdDatabases.size)
    }

    override fun repair(connection: DataHomeConnection): DataHomeRepairResult {
        val provision = provision(connection)
        return DataHomeRepairResult(
            kind = kind,
            remoteId = provision.remoteId,
            repairedCount = provision.repairedCount,
            summary = provision.summary,
        )
    }
}

interface PostgresHostedGateway {
    fun validateHostedApi(
        mode: PostgresConnectionMode,
        endpoint: String,
        token: String,
    ): PostgresGatewayConnection

    fun readRemoteSnapshot(
        mode: PostgresConnectionMode,
        endpoint: String,
        token: String,
        householdId: String,
    ): PostgresRemoteSnapshotResult

    fun exportSnapshot(
        mode: PostgresConnectionMode,
        endpoint: String,
        token: String,
        householdId: String,
        snapshot: WonderFoodSnapshot,
        updatedAt: String,
    ): PostgresSnapshotExportResult
}

class PostgresDataHomeAdapter(
    private val gateway: PostgresHostedGateway,
) : DataHomeAdapter {
    override val kind: DataHomeKind = DataHomeKind.POSTGRES

    override fun probe(connection: DataHomeConnection): DataHomeHealth {
        val credentials = connection.requirePostgres()
        return runCatching {
            val result = gateway.validateHostedApi(credentials.mode, credentials.endpoint, credentials.token)
            DataHomeHealth(
                kind = kind,
                remoteId = credentials.householdId,
                status = if (result.reachable) DataHomeStatus.OK else DataHomeStatus.ERROR,
                rowCount = 0,
                summary = result.summary.redactSecrets(credentials.token),
            )
        }.getOrElse { error ->
            DataHomeHealth(
                kind = kind,
                remoteId = credentials.householdId,
                status = DataHomeStatus.ERROR,
                rowCount = 0,
                summary = error.message.orEmpty().redactSecrets(credentials.token),
            )
        }
    }

    override fun provision(connection: DataHomeConnection): DataHomeProvisionResult {
        val credentials = connection.requirePostgres()
        val health = withRedactedProviderFailure(credentials.token) {
            gateway.validateHostedApi(credentials.mode, credentials.endpoint, credentials.token)
        }
        return DataHomeProvisionResult(
            kind = kind,
            remoteId = credentials.householdId,
            createdCount = 0,
            repairedCount = 0,
            summary = health.summary.redactSecrets(credentials.token),
        )
    }

    override fun initialScan(connection: DataHomeConnection): DataHomeInitialScanResult {
        val credentials = connection.requirePostgres()
        val result = withRedactedProviderFailure(credentials.token) {
            gateway.readRemoteSnapshot(
                mode = credentials.mode,
                endpoint = credentials.endpoint,
                token = credentials.token,
                householdId = credentials.householdId,
            )
        }
        return DataHomeInitialScanResult(
            kind = kind,
            remoteId = result.householdId,
            rowCount = if (result.snapshot == null) 0 else 1,
            surfaceCount = if (result.snapshot == null) 0 else 1,
            hasRemoteSnapshot = result.snapshot != null,
            summary = result.updatedAt?.let { "Found current snapshot updated at $it." } ?: "No current household snapshot found.",
        )
    }

    override fun pull(
        connection: DataHomeConnection,
        baseSnapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomePullResult {
        val credentials = connection.requirePostgres()
        val result = withRedactedProviderFailure(credentials.token) {
            gateway.readRemoteSnapshot(
                mode = credentials.mode,
                endpoint = credentials.endpoint,
                token = credentials.token,
                householdId = credentials.householdId,
            )
        }
        return DataHomePullResult(
            kind = kind,
            remoteId = result.householdId,
            rowCount = if (result.snapshot == null) 0 else 1,
            snapshot = result.snapshot,
            needsReviewCount = 0,
        )
    }

    override fun push(
        connection: DataHomeConnection,
        snapshot: HouseholdSnapshot,
        updatedAt: String,
    ): DataHomePushResult {
        val credentials = connection.requirePostgres()
        val result = withRedactedProviderFailure(credentials.token) {
            gateway.exportSnapshot(
                mode = credentials.mode,
                endpoint = credentials.endpoint,
                token = credentials.token,
                householdId = credentials.householdId,
                snapshot = CanonicalHouseholdSnapshotExporter.toSnapshot(snapshot),
                updatedAt = updatedAt,
            )
        }
        return DataHomePushResult(kind = kind, remoteId = result.householdId, rowCount = 1, surfaceCount = 1)
    }

    override fun repair(connection: DataHomeConnection): DataHomeRepairResult {
        val provision = provision(connection)
        return DataHomeRepairResult(
            kind = kind,
            remoteId = provision.remoteId,
            repairedCount = provision.repairedCount,
            summary = provision.summary,
        )
    }
}

data class DataHomeHealth(
    val kind: DataHomeKind,
    val remoteId: String,
    val status: DataHomeStatus,
    val rowCount: Int,
    val summary: String,
)

data class DataHomeProvisionResult(
    val kind: DataHomeKind,
    val remoteId: String,
    val createdCount: Int,
    val repairedCount: Int,
    val summary: String,
)

data class DataHomeInitialScanResult(
    val kind: DataHomeKind,
    val remoteId: String,
    val rowCount: Int,
    val surfaceCount: Int,
    val hasRemoteSnapshot: Boolean,
    val summary: String,
)

data class DataHomePullResult(
    val kind: DataHomeKind,
    val remoteId: String,
    val rowCount: Int,
    val workspaceRows: List<GoogleSheetsWorkspaceRow> = emptyList(),
    val canonicalCommands: List<HouseholdCommand> = emptyList(),
    val needsReviewDiagnostics: List<V4InboundNeedsReviewDiagnostic> = emptyList(),
    val canonicalSnapshot: HouseholdSnapshot? = null,
    val snapshot: WonderFoodSnapshot? = null,
    val needsReviewCount: Int = 0,
)

data class DataHomePushResult(
    val kind: DataHomeKind,
    val remoteId: String,
    val rowCount: Int,
    val surfaceCount: Int,
)

data class DataHomeSyncResult(
    val kind: DataHomeKind,
    val remoteId: String,
    val pull: DataHomePullResult,
    val push: DataHomePushResult,
    val pushedSnapshot: HouseholdSnapshot,
)

data class DataHomeRepairResult(
    val kind: DataHomeKind,
    val remoteId: String,
    val repairedCount: Int,
    val summary: String,
)

data class DataHomeDisconnectResult(
    val kind: DataHomeKind,
    val remoteId: String,
    val localCredentialsCleared: Boolean,
)

enum class DataHomeSafetyOperation(val reasonToken: String) {
    ATTACH("attach"),
    SWITCH("switch"),
    REMOTE_REPLACE("remote replace"),
    BULK_RESOLUTION("bulk resolution"),
}

data class DataHomeSafetyProof(
    val operation: DataHomeSafetyOperation,
    val safetySnapshot: LatestSafetySnapshot,
)

data class DataHomeDisconnectLocalReplicaResult(
    val disconnect: DataHomeDisconnectResult,
    val localReplica: HouseholdSnapshot,
)

class DataHomeSafetyGate {
    fun <T> runAfterLatestSafety(
        operation: DataHomeSafetyOperation,
        safetySnapshot: LatestSafetySnapshot?,
        action: (DataHomeSafetyProof) -> T,
    ): T {
        val safety = requireNotNull(safetySnapshot) {
            "latest-safety is required before ${operation.reasonToken}."
        }
        require(safety.reason.contains(operation.reasonToken, ignoreCase = true)) {
            "latest-safety reason must name ${operation.reasonToken}."
        }
        return action(DataHomeSafetyProof(operation, safety))
    }

    fun disconnectPreservingLocalReplica(
        adapter: DataHomeAdapter,
        connection: DataHomeConnection,
        localReplica: HouseholdSnapshot,
    ): DataHomeDisconnectLocalReplicaResult =
        DataHomeDisconnectLocalReplicaResult(
            disconnect = adapter.disconnect(connection),
            localReplica = localReplica,
        )
}

private fun DataHomeConnection.requireGoogleSheets(): DataHomeConnection.GoogleSheets =
    this as? DataHomeConnection.GoogleSheets
        ?: error("Expected Google Sheets data-home credentials, got ${this::class.simpleName}.")

private fun DataHomeConnection.requireNotion(): DataHomeConnection.Notion =
    this as? DataHomeConnection.Notion
        ?: error("Expected Notion data-home credentials, got ${this::class.simpleName}.")

private fun DataHomeConnection.requirePostgres(): DataHomeConnection.Postgres =
    this as? DataHomeConnection.Postgres
        ?: error("Expected Postgres data-home credentials, got ${this::class.simpleName}.")

private fun GoogleSheetsSnapshotGateway.exportCanonicalWorkspace(
    accessToken: String,
    spreadsheetId: String,
    snapshot: HouseholdSnapshot,
): GoogleSheetsExportResult =
    when (this) {
        is GoogleSheetsGateway -> exportGraph(accessToken, spreadsheetId, snapshot)
        is GoogleSheetsCanonicalWorkspaceGateway -> exportGraph(accessToken, spreadsheetId, snapshot)
        else -> error("Google Sheets V4 adapter requires a canonical graph export gateway.")
    }

private fun NotionWorkspaceGateway.exportCanonicalWorkspace(
    token: String,
    pageId: String,
    snapshot: HouseholdSnapshot,
    updatedAt: String,
): NotionWorkspaceExportResult =
    when (this) {
        is NotionGateway -> exportWorkspace(token, pageId, snapshot, updatedAt)
        is NotionCanonicalWorkspaceGateway -> exportWorkspace(token, pageId, snapshot, updatedAt)
        else -> error("Notion V4 adapter requires a canonical workspace export gateway.")
    }

private fun String.redactSecrets(vararg secrets: String): String =
    secrets
        .filter { it.length >= MIN_SECRET_REDACTION_LENGTH }
        .fold(this) { text, secret -> text.replace(secret, SECRET_REDACTION) }

private fun <T> withRedactedProviderFailure(
    vararg secrets: String,
    operation: () -> T,
): T {
    return try {
        operation()
    } catch (error: Throwable) {
        val sanitizedMessage = error.message.orEmpty().redactSecrets(*secrets)
        throw IllegalStateException(sanitizedMessage, error)
    }
}

private const val MIN_SECRET_REDACTION_LENGTH = 4
private const val SECRET_REDACTION = "****"

private fun String.toProviderImportTimestamp(): UtcTimestamp =
    UtcTimestamp(
        runCatching { Instant.parse(this).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis()),
    )
