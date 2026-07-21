package com.wonderfood.app.sync

import com.wonderfood.core.data.backend.PostgresConnectionMode
import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.engine.HouseholdCommandExecutionResult
import com.wonderfood.core.engine.HouseholdCommandExecutor
import com.wonderfood.core.engine.HouseholdCommandRepository
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.ConnectionId
import com.wonderfood.core.model.household.DataHomeKind as HouseholdDataHomeKind
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.LatestSafetySnapshot
import com.wonderfood.core.model.household.PayloadHash
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DataHomeAdapterTest {
    @Test
    fun googleSheetsAdapterWrapsProvisionPushPullHealthAndDisconnect() {
        val gateway = FakeSheetsGateway()
        val adapter = GoogleSheetsDataHomeAdapter(gateway)
        val connection = DataHomeConnection.GoogleSheets(accessToken = "token", spreadsheetId = "sheet-123")
        val base = CanonicalWorkspaceTestFixture.snapshot()
        val kitchenId = base.items.first().metadata.id.value
        gateway.remoteWorkspaceRows = listOf(
            GoogleSheetsWorkspaceRow(
                tab = WorkspaceGraphSurface.KITCHEN.label,
                identifier = kitchenId,
                values = mapOf(
                    "Item" to "Adapter rice",
                    "Kind" to "Food",
                    "On hand" to "4",
                    "Unit" to "cup",
                ),
            ),
            GoogleSheetsWorkspaceRow(
                tab = WorkspaceGraphSurface.KITCHEN.label,
                identifier = "bad-lot",
                values = mapOf(
                    "Item" to "Bad quantity",
                    "Kind" to "Food",
                    "On hand" to "many",
                    "Unit" to "cup",
                ),
            ),
        )

        val provision = adapter.provision(connection)
        val scan = adapter.initialScan(connection)
        val push = adapter.push(connection, base, TIMESTAMP)
        val health = adapter.health(connection)
        val pull = adapter.pull(connection, base, TIMESTAMP)
        val disconnect = adapter.disconnect(connection)

        assertEquals(DataHomeKind.GOOGLE_SHEETS, provision.kind)
        assertEquals("sheet-123", provision.remoteId)
        assertEquals(1, provision.createdCount)
        assertEquals(1, provision.repairedCount)
        assertEquals(2, scan.rowCount)
        assertEquals(1, scan.surfaceCount)
        assertEquals(false, scan.hasRemoteSnapshot)
        assertEquals(WorkspaceGraphProjector.project(base).rows.values.sumOf { it.size }, push.rowCount)
        assertEquals(DataHomeStatus.OK, health.status)
        assertEquals(2, health.rowCount)
        assertEquals(1, pull.needsReviewCount)
        assertEquals(2, pull.workspaceRows.size)
        assertTrue(pull.canonicalCommands.isNotEmpty())
        assertEquals(1, pull.needsReviewDiagnostics.size)
        assertEquals(1, gateway.exportGraphCalls)
        assertEquals(base, gateway.lastExportedSnapshot)
        assertTrue(disconnect.localCredentialsCleared)
    }

    @Test
    fun notionAdapterWrapsProvisionPushPullHealthAndDisconnect() {
        val gateway = FakeNotionGateway()
        val adapter = NotionDataHomeAdapter(gateway)
        val connection = DataHomeConnection.Notion(token = "token", pageId = "page-123")
        val base = CanonicalWorkspaceTestFixture.snapshot()
        val kitchenId = base.items.first().metadata.id.value
        gateway.remoteWorkspaceRows = listOf(
            GoogleSheetsWorkspaceRow(
                tab = WorkspaceGraphSurface.KITCHEN.label,
                identifier = kitchenId,
                values = mapOf(
                    "Item" to "Notion adapter rice",
                    "Kind" to "Food",
                    "On hand" to "5",
                    "Unit" to "cup",
                ),
            ),
            GoogleSheetsWorkspaceRow(
                tab = WorkspaceGraphSurface.SPENDING.label,
                identifier = "bad-receipt",
                values = mapOf(
                    "Purchase" to "Bad receipt",
                    "Entered total" to "more",
                ),
            ),
        )

        val health = adapter.probe(connection)
        val provision = adapter.provision(connection)
        val scan = adapter.initialScan(connection)
        val push = adapter.push(connection, base, TIMESTAMP)
        val pull = adapter.pull(connection, base, TIMESTAMP)
        val repair = adapter.repair(connection)

        assertEquals(DataHomeStatus.OK, health.status)
        assertEquals("page-123", provision.remoteId)
        assertEquals(2, provision.createdCount)
        assertEquals(2, provision.repairedCount)
        assertEquals(2, scan.rowCount)
        assertEquals(2, scan.surfaceCount)
        assertEquals(false, scan.hasRemoteSnapshot)
        assertEquals(3, push.rowCount)
        assertEquals(2, push.surfaceCount)
        assertEquals(2, pull.workspaceRows.size)
        assertTrue(pull.canonicalCommands.isNotEmpty())
        assertEquals(1, pull.needsReviewCount)
        assertEquals(base, gateway.lastExportedSnapshot)
        assertEquals(2, repair.repairedCount)
    }

    @Test
    fun postgresAdapterWrapsHostedProbeProvisionPushPullAndRepair() {
        val gateway = FakePostgresGateway()
        val adapter = PostgresDataHomeAdapter(gateway)
        val snapshot = WonderFoodWorkspaceSeedFixture.snapshot()
        gateway.remoteSnapshot = snapshot
        val connection = DataHomeConnection.Postgres(
            mode = PostgresConnectionMode.POSTGREST,
            endpoint = "https://api.example.com/rest/v1",
            token = "postgres-token",
            householdId = "home",
        )

        val health = adapter.health(connection)
        val provision = adapter.provision(connection)
        val scan = adapter.initialScan(connection)
        val local = CanonicalWorkspaceTestFixture.snapshot()
        val push = adapter.push(connection, local, TIMESTAMP)
        val pull = adapter.pull(connection, local, TIMESTAMP)
        val repair = adapter.repair(connection)

        assertEquals(DataHomeKind.POSTGRES, health.kind)
        assertEquals(DataHomeStatus.OK, health.status)
        assertEquals("home", provision.remoteId)
        assertEquals(0, provision.createdCount)
        assertEquals(1, scan.rowCount)
        assertEquals(1, scan.surfaceCount)
        assertEquals(true, scan.hasRemoteSnapshot)
        assertEquals(1, push.rowCount)
        assertEquals(1, push.surfaceCount)
        assertEquals(snapshot, pull.snapshot)
        assertEquals(0, pull.needsReviewCount)
        assertEquals(0, repair.repairedCount)
        assertEquals(PostgresConnectionMode.POSTGREST, gateway.lastMode)
    }

    @Test
    fun syncCoordinatorPullsDraftAndPushesCanonicalGoogleSheetsWorkspaceGraph() {
        val gateway = FakeSheetsGateway()
        val adapter = GoogleSheetsDataHomeAdapter(gateway)
        val base = CanonicalWorkspaceTestFixture.snapshot()
        val kitchenId = base.items.first().metadata.id.value
        gateway.remoteWorkspaceRows = listOf(
            GoogleSheetsWorkspaceRow(
                tab = WorkspaceGraphSurface.KITCHEN.label,
                identifier = kitchenId,
                values = mapOf(
                    "Item" to "Sheets draft rice",
                    "Kind" to "Food",
                    "On hand" to "6",
                    "Unit" to "cup",
                ),
            ),
        )

        val result = DataHomeSyncCoordinator(adapter).sync(
            connection = DataHomeConnection.GoogleSheets(accessToken = "token", spreadsheetId = "sheet-123"),
            localSnapshot = base,
            updatedAt = TIMESTAMP,
        )

        assertEquals(1, gateway.readWorkspaceRowsCalls)
        assertEquals(1, gateway.exportGraphCalls)
        assertTrue(result.pull.canonicalCommands.isNotEmpty())
        assertEquals(result.pushedSnapshot, gateway.lastExportedSnapshot)
        assertFirstKitchenItem("Basmati Rice", requireNotNull(gateway.lastExportedSnapshot))
    }

    @Test
    fun syncCoordinatorPullsDraftAndPushesCanonicalNotionWorkspaceGraph() {
        val gateway = FakeNotionGateway()
        val adapter = NotionDataHomeAdapter(gateway)
        val base = CanonicalWorkspaceTestFixture.snapshot()
        val kitchenId = base.items.first().metadata.id.value
        gateway.remoteWorkspaceRows = listOf(
            GoogleSheetsWorkspaceRow(
                tab = WorkspaceGraphSurface.KITCHEN.label,
                identifier = kitchenId,
                values = mapOf(
                    "Item" to "Notion draft rice",
                    "Kind" to "Food",
                    "On hand" to "7",
                    "Unit" to "cup",
                ),
            ),
        )

        val result = DataHomeSyncCoordinator(adapter).sync(
            connection = DataHomeConnection.Notion(token = "token", pageId = "page-123"),
            localSnapshot = base,
            updatedAt = TIMESTAMP,
        )

        assertEquals(1, gateway.readWorkspaceRowsCalls)
        assertEquals(1, gateway.exportWorkspaceCalls)
        assertTrue(result.pull.canonicalCommands.isNotEmpty())
        assertEquals(result.pushedSnapshot, gateway.lastExportedSnapshot)
        assertFirstKitchenItem("Basmati Rice", requireNotNull(gateway.lastExportedSnapshot))
    }

    @Test
    fun syncCoordinatorReadsAndPushesCurrentPostgresSnapshot() {
        val gateway = FakePostgresGateway()
        val adapter = PostgresDataHomeAdapter(gateway)
        val remote = renameFirstKitchenFood(WonderFoodWorkspaceSeedFixture.snapshot(), "Postgres current rice")
        gateway.remoteSnapshot = remote
        val connection = DataHomeConnection.Postgres(
            mode = PostgresConnectionMode.POSTGREST,
            endpoint = "https://api.example.com/rest/v1",
            token = "postgres-token",
            householdId = "home",
        )

        val result = DataHomeSyncCoordinator(adapter).sync(
            connection = connection,
            localSnapshot = CanonicalWorkspaceTestFixture.snapshot(),
            updatedAt = TIMESTAMP,
        )

        assertEquals(1, gateway.readRemoteSnapshotCalls)
        assertEquals(1, gateway.exportSnapshotCalls)
        assertEquals(CanonicalHouseholdSnapshotExporter.toSnapshot(result.pushedSnapshot), gateway.lastExportedSnapshot)
        assertEquals(remote, result.pull.snapshot)
    }

    @Test
    fun adaptersRedactCredentialSecretsFromProviderSummaries() {
        val sheetsToken = "sheets-token-secret"
        val sheetsGateway = FakeSheetsGateway().also { it.failReadWithToken = true }
        val sheetsHealth = GoogleSheetsDataHomeAdapter(sheetsGateway).health(
            DataHomeConnection.GoogleSheets(accessToken = sheetsToken, spreadsheetId = "sheet-123"),
        )

        val notionToken = "notion-token-secret"
        val notionGateway = FakeNotionGateway().also { it.echoTokenInPageSummary = true }
        val notionHealth = NotionDataHomeAdapter(notionGateway).health(
            DataHomeConnection.Notion(token = notionToken, pageId = "page-123"),
        )

        val postgresToken = "postgres-token-secret"
        val postgresGateway = FakePostgresGateway().also { it.echoTokenInSummary = true }
        val postgresConnection = DataHomeConnection.Postgres(
            mode = PostgresConnectionMode.POSTGREST,
            endpoint = "https://api.example.com/rest/v1",
            token = postgresToken,
            householdId = "home",
        )
        val postgresHealth = PostgresDataHomeAdapter(postgresGateway).health(postgresConnection)
        val postgresProvision = PostgresDataHomeAdapter(postgresGateway).provision(postgresConnection)

        listOf(
            sheetsToken to sheetsHealth.summary,
            notionToken to notionHealth.summary,
            postgresToken to postgresHealth.summary,
            postgresToken to postgresProvision.summary,
        ).forEach { (token, summary) ->
            assertFalse(summary, summary.contains(token))
            assertTrue(summary, summary.contains("****"))
        }
    }

    @Test
    fun adaptersRedactCredentialSecretsFromProviderFailures() {
        val sheetsToken = "sheets-token-secret"
        assertFailureRedactsToken(sheetsToken) {
            GoogleSheetsDataHomeAdapter(
                FakeSheetsGateway().apply { failProvisionWithToken = true }
            ).provision(
                DataHomeConnection.GoogleSheets(accessToken = sheetsToken, spreadsheetId = "sheet-123"),
            )
        }
        assertFailureRedactsToken(sheetsToken) {
            GoogleSheetsDataHomeAdapter(
                FakeSheetsGateway().apply { failExportWithToken = true }
            ).push(
                connection = DataHomeConnection.GoogleSheets(accessToken = sheetsToken, spreadsheetId = "sheet-123"),
                snapshot = CanonicalWorkspaceTestFixture.snapshot(),
                updatedAt = TIMESTAMP,
            )
        }

        val notionToken = "notion-token-secret"
        assertFailureRedactsToken(notionToken) {
            NotionDataHomeAdapter(
                FakeNotionGateway().apply { failRetrieveWithToken = true }
            ).provision(
                DataHomeConnection.Notion(token = notionToken, pageId = "page-123"),
            )
        }
        assertFailureRedactsToken(notionToken) {
            NotionDataHomeAdapter(
                FakeNotionGateway().apply { failExportWithToken = true }
            ).push(
                connection = DataHomeConnection.Notion(token = notionToken, pageId = "page-123"),
                snapshot = CanonicalWorkspaceTestFixture.snapshot(),
                updatedAt = TIMESTAMP,
            )
        }

        val postgresToken = "postgres-token-secret"
        assertFailureRedactsToken(postgresToken) {
            PostgresDataHomeAdapter(
                FakePostgresGateway().apply { failReadWithToken = true }
            ).initialScan(
                DataHomeConnection.Postgres(
                    mode = PostgresConnectionMode.POSTGREST,
                    endpoint = "https://api.example.com/rest/v1",
                    token = postgresToken,
                    householdId = "home",
                ),
            )
        }
        assertFailureRedactsToken(postgresToken) {
            PostgresDataHomeAdapter(
                FakePostgresGateway().apply { failExportWithToken = true }
            ).push(
                connection = DataHomeConnection.Postgres(
                    mode = PostgresConnectionMode.POSTGREST,
                    endpoint = "https://api.example.com/rest/v1",
                    token = postgresToken,
                    householdId = "home",
                ),
                snapshot = CanonicalWorkspaceTestFixture.snapshot(),
                updatedAt = TIMESTAMP,
            )
        }
    }

    @Test
    fun retryingAdapterRetriesErrorHealthAndThrownProviderOperations() {
        val delegate = FakeFlakyAdapter(failuresBeforeSuccess = 1)
        val adapter = RetryingDataHomeAdapter(delegate, DataHomeRetryPolicy(maxAttempts = 2))
        val connection = DataHomeConnection.GoogleSheets(accessToken = "token", spreadsheetId = "sheet-123")

        val health = adapter.health(connection)
        val push = adapter.push(connection, CanonicalWorkspaceTestFixture.snapshot(), TIMESTAMP)

        assertEquals(DataHomeStatus.OK, health.status)
        assertEquals(2, delegate.probeAttempts)
        assertEquals(1, push.rowCount)
        assertEquals(2, delegate.pushAttempts)
    }

    @Test
    fun retryingAdapterStopsAfterConfiguredAttempts() {
        val delegate = FakeFlakyAdapter(failuresBeforeSuccess = 3)
        val adapter = RetryingDataHomeAdapter(delegate, DataHomeRetryPolicy(maxAttempts = 2))
        val connection = DataHomeConnection.GoogleSheets(accessToken = "token", spreadsheetId = "sheet-123")

        val health = adapter.health(connection)
        try {
            adapter.push(connection, CanonicalWorkspaceTestFixture.snapshot(), TIMESTAMP)
            fail("Expected push to fail after configured retry attempts.")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("Transient push failure"))
        }

        assertEquals(DataHomeStatus.ERROR, health.status)
        assertEquals(2, delegate.probeAttempts)
        assertEquals(2, delegate.pushAttempts)
    }

    @Test
    fun dataHomeSafetyGateRequiresLatestSafetyBeforeDangerousOperations() {
        val gate = DataHomeSafetyGate()
        val operations = listOf(
            DataHomeSafetyOperation.ATTACH,
            DataHomeSafetyOperation.SWITCH,
            DataHomeSafetyOperation.REMOTE_REPLACE,
            DataHomeSafetyOperation.BULK_RESOLUTION,
        )

        operations.forEach { operation ->
            var ran = false
            try {
                gate.runAfterLatestSafety(operation, safetySnapshot = null) {
                    ran = true
                }
                fail("Expected ${operation.name} to require latest-safety.")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message.orEmpty().contains("latest-safety"))
            }
            assertFalse(ran)

            val result = gate.runAfterLatestSafety(operation, safety(operation)) { proof ->
                ran = true
                proof.operation
            }

            assertEquals(operation, result)
            assertTrue(ran)
        }
    }

    @Test
    fun dataHomeSafetyGateRejectsMismatchedLatestSafetyReason() {
        val gate = DataHomeSafetyGate()
        var ran = false

        try {
            gate.runAfterLatestSafety(DataHomeSafetyOperation.REMOTE_REPLACE, safety(DataHomeSafetyOperation.SWITCH)) {
                ran = true
            }
            fail("Expected mismatched latest-safety reason to fail closed.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("remote replace"))
        }

        assertFalse(ran)
    }

    @Test
    fun dataHomeDisconnectPreservesLocalReplica() {
        val gate = DataHomeSafetyGate()
        val adapter = GoogleSheetsDataHomeAdapter(FakeSheetsGateway())
        val localReplica = CanonicalWorkspaceTestFixture.snapshot()
        val connection = DataHomeConnection.GoogleSheets(accessToken = "token", spreadsheetId = "sheet-123")

        val result = gate.disconnectPreservingLocalReplica(adapter, connection, localReplica)

        assertTrue(result.disconnect.localCredentialsCleared)
        assertEquals(localReplica, result.localReplica)
    }

    @Test
    fun providerPushFailureDoesNotBlockLocalCanonicalWrite() = runTest {
        val adapter = RetryingDataHomeAdapter(FakeFlakyAdapter(failuresBeforeSuccess = 3), DataHomeRetryPolicy(maxAttempts = 1))
        val connection = DataHomeConnection.GoogleSheets(accessToken = "token", spreadsheetId = "sheet-123")

        try {
            adapter.push(connection, CanonicalWorkspaceTestFixture.snapshot(), TIMESTAMP)
            fail("Expected provider push failure.")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("Transient push failure"))
        }
        val localRepository = RecordingHouseholdCommandRepository()
        val localCommand = HouseholdCommand.UpsertHousehold(
            record = CommandRecord(
                commandId = CommandId("00000000-0000-0000-0000-000000000901"),
                householdId = HOUSEHOLD_ID,
                type = "UpsertHousehold",
                source = SourceRef(SourceKind.MANUAL, "local"),
                requestedAt = UtcTimestamp(1),
                appliedAt = UtcTimestamp(2),
                affectedEntityIds = emptyList(),
            ),
            household = Household(
                id = HOUSEHOLD_ID,
                name = "Local household",
                defaultCurrency = "USD",
                timezone = "America/New_York",
                locale = "en-US",
                activeDataHome = HouseholdDataHomeKind.LOCAL,
                schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
                createdAt = UtcTimestamp(1),
                updatedAt = UtcTimestamp(2),
                revision = 0,
            ),
        )

        val result = HouseholdCommandExecutor(localRepository).execute(localCommand)

        assertEquals(HouseholdCommandExecutionResult.Applied(localCommand.record.commandId), result)
        assertEquals(listOf(localCommand), localRepository.commands)
    }

    private class FakeSheetsGateway : GoogleSheetsSnapshotGateway, GoogleSheetsCanonicalWorkspaceGateway {
        var remoteWorkspaceRows: List<GoogleSheetsWorkspaceRow> = emptyList()
        var failReadWithToken: Boolean = false
        var failProvisionWithToken: Boolean = false
        var failExportWithToken: Boolean = false
        var readWorkspaceRowsCalls: Int = 0
        var exportGraphCalls: Int = 0
        var lastExportedSnapshot: HouseholdSnapshot? = null

        override fun ensureWonderFoodSchema(accessToken: String, spreadsheetId: String): GoogleSheetsBootstrapResult =
            if (failProvisionWithToken) {
                error("Failed provisioning token=$accessToken")
            } else {
                GoogleSheetsBootstrapResult(
                    spreadsheetId = spreadsheetId,
                    title = "WonderFood",
                    createdTabs = listOf(WorkspaceGraphSurface.KITCHEN.label),
                    initializedHeaders = listOf(WorkspaceGraphSurface.KITCHEN.label),
                    totalTabs = 1,
                )
            }

        override fun exportGraph(
            accessToken: String,
            spreadsheetId: String,
            snapshot: HouseholdSnapshot,
        ): GoogleSheetsExportResult {
            if (failExportWithToken) {
                error("Failed export token=$accessToken")
            }
            exportGraphCalls += 1
            lastExportedSnapshot = snapshot
            return GoogleSheetsExportResult(
                spreadsheetId = spreadsheetId,
                rowCount = WorkspaceGraphProjector.project(snapshot).rows.values.sumOf { it.size },
                tabs = WorkspaceGraphSurface.entries.filter { it.visibleInSheets }.map { it.label },
            )
        }

        override fun readWorkspaceRows(accessToken: String, spreadsheetId: String): List<GoogleSheetsWorkspaceRow> {
            readWorkspaceRowsCalls += 1
            if (failReadWithToken) {
                error("Provider rejected token $accessToken")
            }
            return remoteWorkspaceRows
        }
    }

    private class FakeNotionGateway : NotionWorkspaceGateway, NotionCanonicalWorkspaceGateway {
        var remoteWorkspaceRows: List<GoogleSheetsWorkspaceRow> = emptyList()
        var echoTokenInPageSummary: Boolean = false
        var failRetrieveWithToken: Boolean = false
        var failExportWithToken: Boolean = false
        var exportWorkspaceCalls: Int = 0
        var readWorkspaceRowsCalls: Int = 0
        var lastExportedSnapshot: HouseholdSnapshot? = null

        override fun retrievePage(token: String, pageId: String): NotionPageAccess =
            if (failRetrieveWithToken) {
                error("Could not retrieve page token=$token")
            } else {
                NotionPageAccess(
                    pageId = pageId,
                    reachable = true,
                    summary = if (echoTokenInPageSummary) "notion ok for $token" else "ok",
                )
            }

        override fun ensureWorkspaceDatabases(token: String, pageId: String): NotionWorkspaceProvisionResult =
            if (failRetrieveWithToken) {
                error("Failed database setup token=$token")
            } else {
                NotionWorkspaceProvisionResult(
                    pageId = pageId,
                    databaseIdsByTitle = mapOf(
                        "WonderFood Kitchen" to "db-kitchen",
                        "WonderFood Shopping" to "db-shopping",
                    ),
                    createdDatabases = listOf("WonderFood Kitchen", "WonderFood Shopping"),
                )
            }

        override fun exportWorkspace(
            token: String,
            pageId: String,
            snapshot: HouseholdSnapshot,
            updatedAt: String,
        ): NotionWorkspaceExportResult {
            if (failExportWithToken) {
                error("Could not export token=$token")
            }
            exportWorkspaceCalls += 1
            lastExportedSnapshot = snapshot
            return NotionWorkspaceExportResult(
                pageId = pageId,
                createdDatabases = listOf("WonderFood Kitchen", "WonderFood Shopping"),
                upsertedRows = 3,
            )
        }

        override fun readWorkspaceRows(token: String, pageId: String): List<GoogleSheetsWorkspaceRow> {
            readWorkspaceRowsCalls += 1
            return remoteWorkspaceRows
        }

    }

    private class FakePostgresGateway : PostgresHostedGateway {
        var remoteSnapshot: WonderFoodSnapshot? = null
        var lastMode: PostgresConnectionMode? = null
        var echoTokenInSummary: Boolean = false
        var failValidateWithToken: Boolean = false
        var failReadWithToken: Boolean = false
        var failExportWithToken: Boolean = false
        var readRemoteSnapshotCalls: Int = 0
        var exportSnapshotCalls: Int = 0
        var lastExportedSnapshot: WonderFoodSnapshot? = null

        override fun validateHostedApi(
            mode: PostgresConnectionMode,
            endpoint: String,
            token: String,
        ): PostgresGatewayConnection {
            if (failValidateWithToken) {
                error("Failed validate hosted token=$token")
            }
            lastMode = mode
            return PostgresGatewayConnection(
                mode = mode,
                endpoint = endpoint,
                reachable = true,
                summary = if (echoTokenInSummary) "hosted ok for $token" else "hosted ok",
            )
        }

        override fun readRemoteSnapshot(
            mode: PostgresConnectionMode,
            endpoint: String,
            token: String,
            householdId: String,
        ): PostgresRemoteSnapshotResult {
            if (failReadWithToken) {
                error("Failed read snapshot with token=$token")
            }
            readRemoteSnapshotCalls += 1
            lastMode = mode
            return PostgresRemoteSnapshotResult(
                mode = mode,
                endpoint = endpoint,
                householdId = householdId,
                updatedAt = TIMESTAMP,
                snapshot = remoteSnapshot,
            )
        }

        override fun exportSnapshot(
            mode: PostgresConnectionMode,
            endpoint: String,
            token: String,
            householdId: String,
            snapshot: WonderFoodSnapshot,
            updatedAt: String,
        ): PostgresSnapshotExportResult {
            if (failExportWithToken) {
                error("Failed export snapshot with token=$token")
            }
            exportSnapshotCalls += 1
            lastExportedSnapshot = snapshot
            lastMode = mode
            return PostgresSnapshotExportResult(
                mode = mode,
                endpoint = endpoint,
                householdId = householdId,
                updatedAt = updatedAt,
                byteCount = WonderFoodSnapshotCodec.encode(snapshot).length,
                summary = "exported",
            )
        }

    }

    private fun assertFailureRedactsToken(token: String, block: () -> Any?) {
        try {
            block()
            fail("Expected provider failure.")
        } catch (error: Throwable) {
            val message = error.message.orEmpty()
            assertFalse(message, message.contains(token))
            assertTrue(message, message.contains("****"))
        }
    }

    private class FakeFlakyAdapter(
        private val failuresBeforeSuccess: Int,
    ) : DataHomeAdapter {
        override val kind: DataHomeKind = DataHomeKind.GOOGLE_SHEETS
        var probeAttempts = 0
        var pushAttempts = 0

        override fun probe(connection: DataHomeConnection): DataHomeHealth {
            probeAttempts += 1
            return DataHomeHealth(
                kind = kind,
                remoteId = connection.remoteId,
                status = if (probeAttempts <= failuresBeforeSuccess) DataHomeStatus.ERROR else DataHomeStatus.OK,
                rowCount = 0,
                summary = "probe attempt $probeAttempts",
            )
        }

        override fun provision(connection: DataHomeConnection): DataHomeProvisionResult =
            DataHomeProvisionResult(kind = kind, remoteId = connection.remoteId, createdCount = 0, repairedCount = 0, summary = "ok")

        override fun initialScan(connection: DataHomeConnection): DataHomeInitialScanResult =
            DataHomeInitialScanResult(
                kind = kind,
                remoteId = connection.remoteId,
                rowCount = 0,
                surfaceCount = 0,
                hasRemoteSnapshot = false,
                summary = "ok",
            )

        override fun pull(
            connection: DataHomeConnection,
            baseSnapshot: HouseholdSnapshot,
            updatedAt: String,
        ): DataHomePullResult =
            DataHomePullResult(kind = kind, remoteId = connection.remoteId, rowCount = 0)

        override fun push(
            connection: DataHomeConnection,
            snapshot: HouseholdSnapshot,
            updatedAt: String,
        ): DataHomePushResult {
            pushAttempts += 1
            if (pushAttempts <= failuresBeforeSuccess) {
                error("Transient push failure $pushAttempts")
            }
            return DataHomePushResult(kind = kind, remoteId = connection.remoteId, rowCount = 1, surfaceCount = 1)
        }

        override fun repair(connection: DataHomeConnection): DataHomeRepairResult =
            DataHomeRepairResult(kind = kind, remoteId = connection.remoteId, repairedCount = 0, summary = "ok")
    }

    private class RecordingHouseholdCommandRepository : HouseholdCommandRepository {
        val commands = mutableListOf<HouseholdCommand>()

        override suspend fun apply(command: HouseholdCommand): HouseholdCommandExecutionResult {
            commands += command
            return HouseholdCommandExecutionResult.Applied(command.record.commandId)
        }
    }

    private fun safety(operation: DataHomeSafetyOperation): LatestSafetySnapshot =
        LatestSafetySnapshot(
            id = EntityId("00000000-0000-0000-0000-000000000991"),
            householdId = HOUSEHOLD_ID,
            reason = "latest-safety before ${operation.reasonToken}",
            createdAt = UtcTimestamp(10),
            localReplicaHash = PayloadHash("local-replica-${operation.name.lowercase()}"),
            activeDataHome = HouseholdDataHomeKind.GOOGLE_SHEETS,
            connectionId = ConnectionId("00000000-0000-0000-0000-000000000992"),
            commandId = CommandId("00000000-0000-0000-0000-000000000993"),
        )

    private fun emptySnapshot() = WonderFoodSnapshot(
        schemaVersion = WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION,
        pages = emptyList(),
        foods = emptyList(),
        foodAliases = emptyList(),
        stockLots = emptyList(),
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

    private fun renameFirstKitchenFood(snapshot: WonderFoodSnapshot, name: String): WonderFoodSnapshot {
        val foodId = snapshot.stockLots.first().foodId
        return snapshot.copy(
            foods = snapshot.foods.map { food ->
                if (food.id == foodId) food.copy(name = name) else food
            },
        )
    }

    private fun assertFirstKitchenFood(expected: String, snapshot: WonderFoodSnapshot) {
        val foodId = snapshot.stockLots.first().foodId
        assertEquals(expected, snapshot.foods.first { it.id == foodId }.name)
    }

    private fun assertFirstKitchenItem(expected: String, snapshot: HouseholdSnapshot) {
        assertEquals(expected, snapshot.items.first().name)
    }

    private companion object {
        const val TIMESTAMP = "2026-07-20T10:00:00Z"
        val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000900")
    }
}
