package com.wonderfood.app.data

import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.ShoppingReason
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalCartMutationCommandFactoryTest {
    @Test
    fun markPurchasedCreatesCanonicalShoppingLineCommand() {
        val command = CanonicalCartMutationCommandFactory.markPurchased(line(), UtcTimestamp(10))

        assertEquals("MarkShoppingLinePurchased", command.record.type)
        assertEquals("canonical_cart", command.record.source.label)
        assertEquals(ShoppingLineStatus.PURCHASED, command.line.status)
        assertNull(command.line.metadata.archivedAt)
        assertEquals(2, command.line.metadata.revision)
    }

    @Test
    fun archiveCreatesArchivedCanonicalShoppingLineCommand() {
        val command = CanonicalCartMutationCommandFactory.archive(line(), UtcTimestamp(12))

        assertEquals("ArchiveShoppingLine", command.record.type)
        assertEquals(ShoppingLineStatus.ARCHIVED, command.line.status)
        assertEquals(12L, command.line.metadata.archivedAt?.epochMillis)
    }

    private fun line(): ShoppingLine =
        ShoppingLine(
            metadata = EntityMetadata(
                id = EntityId("00000000-0000-0000-0000-000000000301"),
                householdId = HOUSEHOLD_ID,
                createdAt = UtcTimestamp(1),
                updatedAt = UtcTimestamp(2),
                revision = 1,
                source = SourceRef(SourceKind.MANUAL, "test"),
            ),
            shoppingListId = EntityId("00000000-0000-0000-0000-000000000501"),
            displayName = "Dish soap",
            quantity = Quantity(DecimalAmount.of("1"), QuantityUnit.EACH),
            category = "cleaning",
            status = ShoppingLineStatus.NEEDED,
            reason = ShoppingReason.MANUAL,
        )

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000105")
    }
}
