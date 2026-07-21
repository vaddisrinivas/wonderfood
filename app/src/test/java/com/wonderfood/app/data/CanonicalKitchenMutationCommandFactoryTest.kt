package com.wonderfood.app.data

import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalKitchenMutationCommandFactoryTest {
    @Test
    fun archiveItemArchivesItemAndActiveLots() {
        val item = item()
        val commands = CanonicalKitchenMutationCommandFactory.archiveItem(
            snapshot = HouseholdSnapshot(
                household = household(),
                items = listOf(item),
                inventoryLots = listOf(
                    lot("00000000-0000-0000-0000-000000000201", InventoryLotStatus.AVAILABLE),
                    lot("00000000-0000-0000-0000-000000000202", InventoryLotStatus.CONSUMED),
                ),
            ),
            itemId = item.metadata.id,
            now = UtcTimestamp(10),
        )

        assertEquals(listOf("ArchiveKitchenItem", "ArchiveInventoryLot"), commands.map { it.record.type })
        val archivedItem = (commands[0] as HouseholdCommand.UpsertItem).item
        val archivedLot = (commands[1] as HouseholdCommand.UpsertInventoryLot).lot
        assertEquals(10L, archivedItem.metadata.archivedAt?.epochMillis)
        assertEquals(InventoryLotStatus.ARCHIVED, archivedLot.status)
        assertEquals("canonical_kitchen", archivedLot.metadata.source.label)
    }

    @Test
    fun addToCartCreatesCanonicalShoppingLineForItem() {
        val item = item(kind = ItemKind.HOUSEHOLD, category = "home")
        val command = CanonicalKitchenMutationCommandFactory.addToCart(
            snapshot = HouseholdSnapshot(household = household(), items = listOf(item)),
            itemId = item.metadata.id,
            now = UtcTimestamp(12),
        )

        requireNotNull(command)
        assertEquals("AddKitchenItemToCart", command.record.type)
        assertEquals("Paper towels", command.line.displayName)
        assertEquals(item.metadata.id, command.line.itemId)
        assertEquals(ShoppingLineStatus.NEEDED, command.line.status)
        assertEquals("home", command.line.category)
        assertTrue(command.line.quantity.amount == null)
        assertEquals(QuantityUnit.PACKAGE, command.line.quantity.unit)
    }

    private fun household(): Household = Household(
        id = HOUSEHOLD_ID,
        name = "Test",
        defaultCurrency = "USD",
        timezone = "America/New_York",
        locale = "en-US",
        activeDataHome = DataHomeKind.LOCAL,
        schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
        createdAt = NOW,
        updatedAt = NOW,
        revision = 0,
    )

    private fun item(kind: ItemKind = ItemKind.FOOD, category: String = "produce"): Item =
        Item(
            metadata = metadata("00000000-0000-0000-0000-000000000101"),
            name = if (kind == ItemKind.FOOD) "Apples" else "Paper towels",
            kind = kind,
            category = category,
            defaultUnit = QuantityUnit.PACKAGE,
        )

    private fun lot(id: String, status: InventoryLotStatus): InventoryLot =
        InventoryLot(
            metadata = metadata(id),
            itemId = EntityId("00000000-0000-0000-0000-000000000101"),
            quantity = Quantity(DecimalAmount.of("3"), QuantityUnit.PACKAGE),
            status = status,
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

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000105")
        val NOW = UtcTimestamp(1)
    }
}
