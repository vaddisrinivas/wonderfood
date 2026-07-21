package com.wonderfood.app.data

import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.model.household.CalendarDate
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.ShoppingReason
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import java.time.Instant
import java.time.ZoneOffset
import java.nio.charset.StandardCharsets
import java.util.UUID

object CanonicalKitchenMutationCommandFactory {
    private val activeLotStatuses = setOf(
        InventoryLotStatus.AVAILABLE,
        InventoryLotStatus.OPENED,
        InventoryLotStatus.RESERVED,
    )

    fun archiveItem(snapshot: HouseholdSnapshot, itemId: EntityId, now: UtcTimestamp): List<HouseholdCommand> {
        val item = snapshot.items.firstOrNull { it.metadata.id == itemId } ?: return emptyList()
        val activeLots = snapshot.inventoryLots.filter {
            it.itemId == itemId && it.metadata.archivedAt == null && it.status in activeLotStatuses
        }
        return buildList {
            add(
                HouseholdCommand.UpsertItem(
                    record = commandRecord(item.metadata.id, item.metadata.householdId, "ArchiveKitchenItem", now),
                    item = item.copy(metadata = item.metadata.updated(now, archivedAt = now)),
                ),
            )
            activeLots.forEach { lot ->
                add(
                    HouseholdCommand.UpsertInventoryLot(
                        record = commandRecord(lot.metadata.id, lot.metadata.householdId, "ArchiveInventoryLot", now),
                        lot = lot.copy(
                            metadata = lot.metadata.updated(now, archivedAt = now),
                            status = InventoryLotStatus.ARCHIVED,
                        ),
                    ),
                )
            }
        }
    }

    fun addToCart(snapshot: HouseholdSnapshot, itemId: EntityId, now: UtcTimestamp): HouseholdCommand.UpsertShoppingLine? {
        val item = snapshot.items.firstOrNull { it.metadata.id == itemId } ?: return null
        val lineId = EntityId(stableUuid("${item.metadata.id.value}:AddKitchenItemToCart:${now.epochMillis}"))
        val line = ShoppingLine(
            metadata = EntityMetadata(
                id = lineId,
                householdId = item.metadata.householdId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                source = source(),
            ),
            shoppingListId = HouseholdDraftCommandMapper.DEFAULT_SHOPPING_LIST_ID,
            itemId = item.metadata.id,
            displayName = item.name,
            quantity = Quantity.unknown(item.defaultUnit),
            category = item.category,
            preferredStore = item.preferredStore,
            status = ShoppingLineStatus.NEEDED,
            reason = ShoppingReason.MANUAL,
        )
        return HouseholdCommand.UpsertShoppingLine(
            record = commandRecord(lineId, item.metadata.householdId, "AddKitchenItemToCart", now),
            line = line,
        )
    }

    fun updateItem(
        snapshot: HouseholdSnapshot,
        itemId: EntityId,
        name: String,
        quantityText: String,
        category: String,
        notes: String,
        expiresAtMillis: Long?,
        now: UtcTimestamp,
    ): List<HouseholdCommand> {
        val item = snapshot.items.firstOrNull { it.metadata.id == itemId } ?: return emptyList()
        val lot = snapshot.inventoryLots
            .filter { it.itemId == itemId && it.metadata.archivedAt == null && it.status in activeLotStatuses }
            .maxByOrNull { it.metadata.updatedAt.epochMillis }
        return buildList {
            val updatedItem = item.copy(
                metadata = item.metadata.updated(now, archivedAt = null),
                name = name.trim().ifBlank { item.name },
                category = category.ifBlank { item.category },
                notes = notes.ifBlank { null },
                defaultUnit = quantityText.quantityUnit().takeUnless { it == QuantityUnit.UNKNOWN } ?: item.defaultUnit,
            )
            add(
                HouseholdCommand.UpsertItem(
                    record = commandRecord(item.metadata.id, item.metadata.householdId, "UpdateKitchenItem", now),
                    item = updatedItem,
                ),
            )
            lot?.let { activeLot ->
                add(
                    HouseholdCommand.UpsertInventoryLot(
                        record = commandRecord(activeLot.metadata.id, activeLot.metadata.householdId, "UpdateInventoryLot", now),
                        lot = activeLot.copy(
                            metadata = activeLot.metadata.updated(now, archivedAt = null),
                            quantity = quantityText.quantity(),
                            expiresOn = expiresAtMillis?.toCalendarDate(),
                        ),
                    ),
                )
            }
        }
    }

    private fun commandRecord(
        affectedId: EntityId,
        householdId: com.wonderfood.core.model.household.HouseholdId,
        type: String,
        now: UtcTimestamp,
    ): CommandRecord =
        CommandRecord(
            commandId = CommandId(stableUuid("${affectedId.value}:$type:${now.epochMillis}")),
            householdId = householdId,
            type = type,
            source = source(),
            requestedAt = now,
            appliedAt = now,
            affectedEntityIds = listOf(affectedId),
        )

    private fun EntityMetadata.updated(now: UtcTimestamp, archivedAt: UtcTimestamp?): EntityMetadata =
        copy(
            updatedAt = now,
            archivedAt = archivedAt,
            revision = revision + 1,
            source = source(),
        )

    private fun source(): SourceRef = SourceRef(SourceKind.MANUAL, "canonical_kitchen")

    private fun stableUuid(input: String): String =
        UUID.nameUUIDFromBytes(input.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun String.quantity(): Quantity =
        Quantity(amount = quantityAmount(), unit = quantityUnit())

    private fun String.quantityAmount(): DecimalAmount? {
        val match = Regex("""^\s*(\d+(?:\.\d+)?)""").find(this) ?: return null
        return DecimalAmount.of(match.groupValues[1])
    }

    private fun String.quantityUnit(): QuantityUnit {
        val text = lowercase()
        return when {
            text.isBlank() -> QuantityUnit.UNKNOWN
            "cup" in text -> QuantityUnit.CUP
            "kg" in text || "kilogram" in text -> QuantityUnit.KILOGRAM
            "serving" in text -> QuantityUnit.SERVING
            "g " in "$text " || "gram" in text -> QuantityUnit.GRAM
            "liter" in text -> QuantityUnit.LITER
            "ml" in text || "milliliter" in text -> QuantityUnit.MILLILITER
            "pack" in text || "bag" in text || "box" in text -> QuantityUnit.PACKAGE
            else -> QuantityUnit.EACH
        }
    }

    private fun Long.toCalendarDate(): CalendarDate =
        CalendarDate(Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().toString())
}
