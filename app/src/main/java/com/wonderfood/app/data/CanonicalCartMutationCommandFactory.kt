package com.wonderfood.app.data

import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import java.nio.charset.StandardCharsets
import java.util.UUID

object CanonicalCartMutationCommandFactory {
    fun markPurchased(line: ShoppingLine, now: UtcTimestamp): HouseholdCommand.UpsertShoppingLine =
        line.toUpsertCommand(
            type = "MarkShoppingLinePurchased",
            now = now,
            status = ShoppingLineStatus.PURCHASED,
            archivedAt = null,
        )

    fun archive(line: ShoppingLine, now: UtcTimestamp): HouseholdCommand.UpsertShoppingLine =
        line.toUpsertCommand(
            type = "ArchiveShoppingLine",
            now = now,
            status = ShoppingLineStatus.ARCHIVED,
            archivedAt = now,
        )

    fun update(
        line: ShoppingLine,
        name: String,
        quantityText: String,
        status: ShoppingLineStatus,
        category: String,
        preferredStore: String,
        now: UtcTimestamp,
    ): HouseholdCommand.UpsertShoppingLine {
        val updated = line.copy(
            metadata = line.metadata.updated(now, archivedAt = if (status == ShoppingLineStatus.ARCHIVED) now else null),
            displayName = name.trim().ifBlank { line.displayName },
            quantity = quantityText.quantity(),
            status = status,
            category = category.ifBlank { line.category },
            preferredStore = preferredStore.ifBlank { line.preferredStore },
        )
        return HouseholdCommand.UpsertShoppingLine(
            record = CommandRecord(
                commandId = CommandId(stableUuid("${line.metadata.id.value}:UpdateShoppingLine:${now.epochMillis}")),
                householdId = line.metadata.householdId,
                type = "UpdateShoppingLine",
                source = SourceRef(SourceKind.MANUAL, "canonical_cart"),
                requestedAt = now,
                appliedAt = now,
                affectedEntityIds = listOf(line.metadata.id),
            ),
            line = updated,
        )
    }

    private fun ShoppingLine.toUpsertCommand(
        type: String,
        now: UtcTimestamp,
        status: ShoppingLineStatus,
        archivedAt: UtcTimestamp?,
    ): HouseholdCommand.UpsertShoppingLine {
        val updated = copy(
            metadata = metadata.updated(now, archivedAt),
            status = status,
        )
        return HouseholdCommand.UpsertShoppingLine(
            record = CommandRecord(
                commandId = CommandId(stableUuid("${metadata.id.value}:$type:${now.epochMillis}")),
                householdId = metadata.householdId,
                type = type,
                source = SourceRef(SourceKind.MANUAL, "canonical_cart"),
                requestedAt = now,
                appliedAt = now,
                affectedEntityIds = listOf(metadata.id),
            ),
            line = updated,
        )
    }

    private fun EntityMetadata.updated(now: UtcTimestamp, archivedAt: UtcTimestamp?): EntityMetadata =
        copy(
            updatedAt = now,
            archivedAt = archivedAt,
            revision = revision + 1,
            source = SourceRef(SourceKind.MANUAL, "canonical_cart"),
        )

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
}
