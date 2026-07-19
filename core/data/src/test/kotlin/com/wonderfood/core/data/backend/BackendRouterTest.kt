package com.wonderfood.core.data.backend

import com.wonderfood.core.ai.CommandEnvelope
import com.wonderfood.core.model.WonderFoodSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendRouterTest {
    @Test
    fun defaultsToLocalBackendWhenNoActiveConfigurationExists() = runTest {
        val local = FakeBackend(BackendType.LOCAL_SQLITE)
        val router = BackendRouter(
            configurationStore = InMemoryBackendConfigurationStore(),
            backends = setOf(local, FakeBackend(BackendType.GOOGLE_SHEETS)),
        )

        assertEquals(BackendType.LOCAL_SQLITE, router.activeBackend().descriptor.type)
    }

    @Test
    fun connectAndActivateSavesOnlySuccessfulConnections() = runTest {
        val store = InMemoryBackendConfigurationStore()
        val sheets = FakeBackend(BackendType.GOOGLE_SHEETS)
        val router = BackendRouter(
            configurationStore = store,
            backends = setOf(FakeBackend(BackendType.LOCAL_SQLITE), sheets),
        )
        val credentialRef = CredentialRef(BackendType.GOOGLE_SHEETS, "primary")

        val result = router.connectAndActivate(
            GoogleSheetsConfig(
                spreadsheetUrl = "https://docs.google.com/spreadsheets/d/sheet-123/edit",
                spreadsheetId = "sheet-123",
                accountEmail = "user@example.com",
                credentialRef = credentialRef,
            ),
        )

        assertTrue(result is ConnectionResult.Connected)
        assertEquals(BackendType.GOOGLE_SHEETS, router.activeBackend().descriptor.type)
        assertEquals(BackendType.GOOGLE_SHEETS, store.activeConfiguration()?.type)
        assertEquals(1, sheets.connectCount)
    }

    @Test
    fun failedConnectionDoesNotReplaceActiveConfiguration() = runTest {
        val store = InMemoryBackendConfigurationStore(LocalSqliteConfig())
        val sheets = FakeBackend(BackendType.GOOGLE_SHEETS, failConnect = true)
        val router = BackendRouter(
            configurationStore = store,
            backends = setOf(FakeBackend(BackendType.LOCAL_SQLITE), sheets),
        )

        val result = router.connectAndActivate(
            GoogleSheetsConfig(
                spreadsheetUrl = "https://docs.google.com/spreadsheets/d/sheet-123/edit",
                spreadsheetId = "sheet-123",
                accountEmail = null,
                credentialRef = CredentialRef(BackendType.GOOGLE_SHEETS, "primary"),
            ),
        )

        assertTrue(result is ConnectionResult.Failed)
        assertEquals(BackendType.LOCAL_SQLITE, store.activeConfiguration()?.type)
        assertEquals(BackendType.LOCAL_SQLITE, router.activeBackend().descriptor.type)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateBackendAdapters() {
        BackendRouter(
            configurationStore = InMemoryBackendConfigurationStore(),
            backends = setOf(FakeBackend(BackendType.LOCAL_SQLITE), FakeBackend(BackendType.LOCAL_SQLITE)),
        )
    }
}

private class InMemoryBackendConfigurationStore(
    private var active: BackendConfig? = null,
) : BackendConfigurationStore {
    override suspend fun activeConfiguration(): BackendConfig? = active

    override suspend fun saveActiveConfiguration(config: BackendConfig) {
        active = config
    }

    override suspend fun clearActiveConfiguration() {
        active = null
    }
}

private class FakeBackend(
    type: BackendType,
    private val failConnect: Boolean = false,
) : FoodBackend {
    var connectCount: Int = 0
        private set

    override val descriptor: BackendDescriptor = BackendDescriptor(
        type = type,
        displayName = type.name,
        setup = if (type == BackendType.LOCAL_SQLITE) BackendSetupKind.NONE else BackendSetupKind.GOOGLE_OAUTH,
        description = "Fake $type backend",
    )

    override val capabilities: Set<BackendCapability> = setOf(
        BackendCapability.COMMAND_PUSH,
        BackendCapability.EXPORT_SNAPSHOT,
    )

    override suspend fun connect(config: BackendConfig): ConnectionResult {
        connectCount += 1
        return if (failConnect) {
            ConnectionResult.Failed(
                reason = BackendFailureReason.INVALID_CONFIG,
                message = "Fake connection failed.",
                recoverable = true,
            )
        } else {
            ConnectionResult.Connected(descriptor, EMPTY_SNAPSHOT.summary())
        }
    }

    override suspend fun healthCheck(): BackendHealth =
        BackendHealth(
            status = BackendHealthStatus.READY,
            checkedAt = "2026-07-18T00:00:00Z",
            message = "Ready",
            activeConfigType = descriptor.type,
        )

    override suspend fun bootstrap(): WonderFoodSnapshot = EMPTY_SNAPSHOT

    override suspend fun pull(after: ChangeCursor?): ChangePage =
        ChangePage(cursor = after, changes = emptyList(), hasMore = false)

    override suspend fun push(commands: List<CommandEnvelope>): PushResult =
        PushResult(
            accepted = commands.mapIndexed { index, command ->
                PushedCommand(
                    envelopeId = command.envelopeId,
                    idempotencyKey = command.idempotencyKey,
                    version = index.toLong(),
                )
            },
            rejected = emptyList(),
            nextCursor = ChangeCursor("cursor-${commands.size}"),
        )

    override suspend fun exportSnapshot(): WonderFoodSnapshot = EMPTY_SNAPSHOT

    override suspend fun disconnect() = Unit
}

private val EMPTY_SNAPSHOT = WonderFoodSnapshot(
    schemaVersion = 1,
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
