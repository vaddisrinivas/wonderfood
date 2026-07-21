package com.wonderfood.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wonderfood.core.data.HouseholdRepositories
import com.wonderfood.core.data.room.WonderFoodDatabase
import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.engine.HouseholdCommandExecutionResult
import com.wonderfood.core.engine.HouseholdCommandExecutor
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CanonicalKitchenMutationRepositoryTest {
    private var database: WonderFoodDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun addArchiveAndUndoCommandsRoundTripThroughCanonicalRepository() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, WonderFoodDatabase::class.java).build()
        val repository = HouseholdRepositories.room(requireNotNull(database))
        val executor = HouseholdCommandExecutor(repository)
        val item = item()
        val lot = lot(item.metadata.id)

        listOf(
            HouseholdCommand.UpsertHousehold(record("00000000-0000-0000-0000-000000000981", "UpsertHousehold"), household()),
            HouseholdCommand.UpsertItem(record("00000000-0000-0000-0000-000000000982", "UpsertItem", item.metadata.id), item),
            HouseholdCommand.UpsertInventoryLot(record("00000000-0000-0000-0000-000000000983", "UpsertInventoryLot", lot.metadata.id), lot),
        ).forEach { executor.executeApplied(it) }

        val seeded = repository.snapshot(HOUSEHOLD_ID)
        assertEquals(listOf("B05 kitchen proof"), CanonicalKitchenPreviewItem.fromSnapshot(seeded).map { it.title })

        val addCommand = requireNotNull(CanonicalKitchenMutationCommandFactory.addToCart(requireNotNull(seeded), item.metadata.id, UtcTimestamp(10)))
        executor.executeApplied(addCommand)
        assertEquals(listOf("B05 kitchen proof"), CanonicalCartPreviewItem.fromSnapshot(repository.snapshot(HOUSEHOLD_ID)).map { it.title })

        executor.executeApplied(CanonicalCartMutationCommandFactory.archive(addCommand.line, UtcTimestamp(11)))
        assertTrue(CanonicalCartPreviewItem.fromSnapshot(repository.snapshot(HOUSEHOLD_ID)).none { it.title == "B05 kitchen proof" })

        val archiveCommands = CanonicalKitchenMutationCommandFactory.archiveItem(requireNotNull(repository.snapshot(HOUSEHOLD_ID)), item.metadata.id, UtcTimestamp(12))
        archiveCommands.forEach { executor.executeApplied(it) }
        assertTrue(CanonicalKitchenPreviewItem.fromSnapshot(repository.snapshot(HOUSEHOLD_ID)).none { it.title == "B05 kitchen proof" })

        executor.executeApplied(
            HouseholdCommand.UpsertItem(
                record = record("00000000-0000-0000-0000-000000000984", "UndoArchiveKitchenItem", item.metadata.id),
                item = item.copy(metadata = item.metadata.copy(updatedAt = UtcTimestamp(13), revision = 2, archivedAt = null)),
            ),
        )
        executor.executeApplied(
            HouseholdCommand.UpsertInventoryLot(
                record = record("00000000-0000-0000-0000-000000000985", "UndoArchiveInventoryLot", lot.metadata.id),
                lot = lot.copy(
                    metadata = lot.metadata.copy(updatedAt = UtcTimestamp(13), revision = 2, archivedAt = null),
                    status = InventoryLotStatus.AVAILABLE,
                ),
            ),
        )
        assertEquals(listOf("B05 kitchen proof"), CanonicalKitchenPreviewItem.fromSnapshot(repository.snapshot(HOUSEHOLD_ID)).map { it.title })
    }

    private suspend fun HouseholdCommandExecutor.executeApplied(command: HouseholdCommand) {
        val result = execute(command)
        assertTrue("Expected applied result for ${command.record.type}, got $result", result is HouseholdCommandExecutionResult.Applied)
    }

    private fun household(): Household =
        Household(
            id = HOUSEHOLD_ID,
            name = "My household",
            defaultCurrency = "USD",
            timezone = "America/New_York",
            locale = "en-US",
            activeDataHome = DataHomeKind.LOCAL,
            schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
            createdAt = NOW,
            updatedAt = NOW,
            revision = 1,
        )

    private fun item(): Item =
        Item(
            metadata = metadata("00000000-0000-0000-0000-000000000971"),
            name = "B05 kitchen proof",
            kind = ItemKind.CLEANING,
            category = "Cleaning",
            defaultUnit = QuantityUnit.EACH,
        )

    private fun lot(itemId: EntityId): InventoryLot =
        InventoryLot(
            metadata = metadata("00000000-0000-0000-0000-000000000972"),
            itemId = itemId,
            quantity = Quantity(DecimalAmount.of("1"), QuantityUnit.EACH),
            status = InventoryLotStatus.AVAILABLE,
        )

    private fun metadata(id: String): EntityMetadata =
        EntityMetadata(
            id = EntityId(id),
            householdId = HOUSEHOLD_ID,
            createdAt = NOW,
            updatedAt = NOW,
            revision = 1,
            source = SourceRef(SourceKind.MANUAL, "test"),
        )

    private fun record(commandId: String, type: String, affectedId: EntityId? = null): CommandRecord =
        CommandRecord(
            commandId = CommandId(commandId),
            householdId = HOUSEHOLD_ID,
            type = type,
            source = SourceRef(SourceKind.MANUAL, "test"),
            requestedAt = NOW,
            appliedAt = NOW,
            affectedEntityIds = affectedId?.let(::listOf).orEmpty(),
        )

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000105")
        val NOW = UtcTimestamp(1)
    }
}
