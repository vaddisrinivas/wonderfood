package com.wonderfood.app.data

import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.EntityReference
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.CalendarDate
import com.wonderfood.core.model.household.NutritionSnapshot
import com.wonderfood.core.model.household.NutritionValues
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.StorageLocation
import com.wonderfood.core.model.household.StorageLocationType
import com.wonderfood.core.model.household.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalKitchenPreviewItemTest {
    @Test
    fun showsOnlyActiveUnarchivedInventoryItems() {
        val apples = item("00000000-0000-0000-0000-000000000101", "Apples", ItemKind.FOOD, "produce")
        val towels = item("00000000-0000-0000-0000-000000000102", "Paper towels", ItemKind.HOUSEHOLD, "home")
        val cereal = item("00000000-0000-0000-0000-000000000103", "Cereal", ItemKind.FOOD, "pantry")

        val preview = CanonicalKitchenPreviewItem.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                items = listOf(apples, towels, cereal),
                inventoryLots = listOf(
                    lot("00000000-0000-0000-0000-000000000201", apples.metadata.id, InventoryLotStatus.AVAILABLE),
                    lot("00000000-0000-0000-0000-000000000202", towels.metadata.id, InventoryLotStatus.CONSUMED),
                    lot("00000000-0000-0000-0000-000000000203", cereal.metadata.id, InventoryLotStatus.ARCHIVED, archived = true),
                ),
            ),
        )

        assertEquals(1, preview.size)
        assertEquals("Apples", preview.single().title)
        assertEquals("3 each  1 lot  food  produce", preview.single().subtitle)
    }

    @Test
    fun foodShowsLotExpiryStorageQuantityAndNutritionWhileNonFoodAvoidsNutrition() {
        val apples = item("00000000-0000-0000-0000-000000000111", "Apples", ItemKind.FOOD, "produce")
        val towels = item("00000000-0000-0000-0000-000000000112", "Paper towels", ItemKind.HOUSEHOLD, "home")
        val pantry = StorageLocation(
            metadata = metadata("00000000-0000-0000-0000-000000000301"),
            name = "Pantry shelf",
            type = StorageLocationType.PANTRY,
        )

        val preview = CanonicalKitchenPreviewItem.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                items = listOf(apples, towels),
                storageLocations = listOf(pantry),
                inventoryLots = listOf(
                    lot(
                        id = "00000000-0000-0000-0000-000000000211",
                        itemId = apples.metadata.id,
                        status = InventoryLotStatus.AVAILABLE,
                        locationId = pantry.metadata.id,
                        expiresOn = "2026-07-23",
                    ),
                    lot(
                        id = "00000000-0000-0000-0000-000000000212",
                        itemId = towels.metadata.id,
                        status = InventoryLotStatus.AVAILABLE,
                        locationId = pantry.metadata.id,
                    ),
                ),
                nutritionSnapshots = listOf(
                    nutrition("00000000-0000-0000-0000-000000000401", apples.metadata.id, kcal = "95", protein = "0.5"),
                    nutrition("00000000-0000-0000-0000-000000000402", towels.metadata.id, kcal = "50", protein = "1"),
                ),
            ),
        )

        assertEquals("Apples", preview[0].title)
        assertEquals("3 each  1 lot  in Pantry shelf  best by 2026-07-23  food  produce  95 kcal  0.5g protein", preview[0].subtitle)
        assertEquals("Paper towels", preview[1].title)
        assertEquals("3 each  1 lot  in Pantry shelf  household  home", preview[1].subtitle)
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

    private fun item(id: String, name: String, kind: ItemKind, category: String): Item =
        Item(
            metadata = metadata(id),
            name = name,
            kind = kind,
            category = category,
            defaultUnit = QuantityUnit.EACH,
        )

    private fun lot(
        id: String,
        itemId: EntityId,
        status: InventoryLotStatus,
        archived: Boolean = false,
        locationId: EntityId? = null,
        expiresOn: String? = null,
    ): InventoryLot =
        InventoryLot(
            metadata = metadata(id, archived = archived),
            itemId = itemId,
            quantity = Quantity(DecimalAmount.of("3"), QuantityUnit.EACH),
            status = status,
            locationId = locationId,
            expiresOn = expiresOn?.let(::CalendarDate),
        )

    private fun nutrition(id: String, itemId: EntityId, kcal: String, protein: String): NutritionSnapshot =
        NutritionSnapshot(
            metadata = metadata(id),
            subject = EntityReference(HouseholdEntityType.ITEM, itemId),
            basis = Quantity(DecimalAmount.of("1"), QuantityUnit.SERVING),
            values = NutritionValues(
                energyKcal = DecimalAmount.of(kcal),
                proteinGrams = DecimalAmount.of(protein),
            ),
            provider = "test",
            capturedAt = NOW,
        )

    private fun metadata(id: String, archived: Boolean = false): EntityMetadata =
        EntityMetadata(
            id = EntityId(id),
            householdId = HOUSEHOLD_ID,
            createdAt = NOW,
            updatedAt = NOW,
            archivedAt = if (archived) NOW else null,
            source = SourceRef(SourceKind.MANUAL, "test"),
        )

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000105")
        val NOW = UtcTimestamp(1)
    }
}
