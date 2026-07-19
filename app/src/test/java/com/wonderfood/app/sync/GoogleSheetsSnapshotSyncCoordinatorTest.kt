package com.wonderfood.app.sync

import androidx.test.core.app.ApplicationProvider
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.core.data.room.LocalSqliteBackend
import com.wonderfood.core.data.room.WonderFoodDatabase
import com.wonderfood.core.data.room.WonderFoodDatabaseFactory
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec
import com.wonderfood.core.model.WonderFoodSnapshotRow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleSheetsSnapshotSyncCoordinatorTest {
    private lateinit var database: WonderFoodDatabase
    private lateinit var gateway: FakeSheetsGateway
    private lateinit var coordinator: GoogleSheetsSnapshotSyncCoordinator

    @Before
    fun setUp() {
        database = WonderFoodDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        gateway = FakeSheetsGateway()
        coordinator = GoogleSheetsSnapshotSyncCoordinator(
            localBackend = LocalSqliteBackend(database) { TIMESTAMP },
            sheetsGateway = gateway,
            clock = { TIMESTAMP },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportLocalSnapshotWritesRowsToSheetsGateway() = runTest {
        val result = coordinator.exportLocalSnapshot("token", "sheet-123")

        assertEquals("sheet-123", result.spreadsheetId)
        assertEquals(1, result.rowCount)
        assertEquals(listOf("_meta"), result.tabs)
        assertEquals("snapshot", gateway.exportedRows.single().id)
    }

    @Test
    fun exportSnapshotWritesProvidedSnapshotWithoutLocalBackend() {
        val coordinator = GoogleSheetsSnapshotSyncCoordinator(
            sheetsGateway = gateway,
            clock = { TIMESTAMP },
        )

        val result = coordinator.exportSnapshot("token", "sheet-123", emptySnapshot())

        assertEquals("sheet-123", result.spreadsheetId)
        assertEquals(1, result.rowCount)
        assertEquals("snapshot", gateway.exportedRows.single().id)
    }

    @Test
    fun readRemoteSnapshotDecodesMetaSnapshotRow() {
        gateway.remoteRows = WonderFoodSnapshotCodec.rows(emptySnapshot(), TIMESTAMP)

        val result = coordinator.readRemoteSnapshot("token", "sheet-123")

        assertTrue(result is GoogleSheetsSnapshotSyncResult.RemoteSnapshot)
        result as GoogleSheetsSnapshotSyncResult.RemoteSnapshot
        assertEquals(WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION, result.snapshot.schemaVersion)
    }

    @Test
    fun readRemoteWorkspaceDraftConvertsFriendlyRowsForReview() {
        gateway.remoteWorkspaceRows = listOf(
            GoogleSheetsWorkspaceRow(
                tab = WonderFoodWorkspaceSchema.KITCHEN,
                identifier = "lot-eggs",
                values = mapOf(
                    "Food" to "Eggs",
                    "On hand" to "12",
                    "Unit" to "item",
                    "Location" to "Fridge",
                    "identifier" to "lot-eggs",
                ),
            ),
        )

        val result = coordinator.readRemoteWorkspaceDraft("token", "sheet-123")

        assertTrue(result is GoogleSheetsSnapshotSyncResult.RemoteWorkspaceDraft)
        result as GoogleSheetsSnapshotSyncResult.RemoteWorkspaceDraft
        val inventory = result.draft as InventoryDraft
        assertEquals("Eggs", inventory.items.single().name)
        assertEquals("12 item", inventory.items.single().quantity)
    }

    @Test
    fun readRemoteWorkspaceMergeAppliesFriendlyRowsToBaseSnapshot() {
        val base = WonderFoodWorkspaceSeedFixture.snapshot()
        val kitchenId = base.stockLots.first().id.value
        gateway.remoteWorkspaceRows = listOf(
            GoogleSheetsWorkspaceRow(
                tab = WonderFoodWorkspaceSchema.KITCHEN,
                identifier = kitchenId,
                values = mapOf(
                    "Food" to "Merged rice",
                    "On hand" to "9",
                    "Unit" to "kg",
                    "identifier" to kitchenId,
                ),
            ),
        )

        val result = coordinator.readRemoteWorkspaceMerge("token", "sheet-123", base)

        assertTrue(result is GoogleSheetsSnapshotSyncResult.RemoteWorkspaceMerge)
        result as GoogleSheetsSnapshotSyncResult.RemoteWorkspaceMerge
        val stockLot = result.merge.snapshot.stockLots.first { it.id.value == kitchenId }
        val food = result.merge.snapshot.foods.first { it.id == stockLot.foodId }
        assertEquals("Merged rice", food.name)
        assertEquals(9.0, stockLot.quantity.amount)
        assertEquals(TIMESTAMP, result.merge.mergeClock)
        assertTrue(result.merge.fieldClocks.isNotEmpty())
    }

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

    private class FakeSheetsGateway : GoogleSheetsSnapshotGateway {
        var exportedRows: List<WonderFoodSnapshotRow> = emptyList()
        var remoteRows: List<WonderFoodSnapshotRow> = emptyList()
        var remoteWorkspaceRows: List<GoogleSheetsWorkspaceRow> = emptyList()

        override fun ensureWonderFoodSchema(accessToken: String, spreadsheetId: String): GoogleSheetsBootstrapResult =
            GoogleSheetsBootstrapResult(spreadsheetId, "Test", emptyList(), emptyList(), 1)

        override fun exportSnapshotRows(
            accessToken: String,
            spreadsheetId: String,
            snapshot: WonderFoodSnapshot,
            updatedAt: String,
        ): GoogleSheetsExportResult {
            exportedRows = WonderFoodSnapshotCodec.rows(snapshot, updatedAt)
            return GoogleSheetsExportResult(spreadsheetId, exportedRows.size, exportedRows.map { it.tab }.distinct())
        }

        override fun readSnapshotRows(accessToken: String, spreadsheetId: String): List<WonderFoodSnapshotRow> =
            remoteRows

        override fun readWorkspaceRows(accessToken: String, spreadsheetId: String): List<GoogleSheetsWorkspaceRow> =
            remoteWorkspaceRows
    }

    private companion object {
        const val TIMESTAMP = "2026-07-18T12:00:00Z"
    }
}
