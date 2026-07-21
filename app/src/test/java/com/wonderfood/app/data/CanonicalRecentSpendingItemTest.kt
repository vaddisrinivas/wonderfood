package com.wonderfood.app.data

import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.Money
import com.wonderfood.core.model.household.Purchase
import com.wonderfood.core.model.household.PurchaseLine
import com.wonderfood.core.model.household.PurchaseLineDisposition
import com.wonderfood.core.model.household.PurchaseStatus
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.ReviewState
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalRecentSpendingItemTest {
    @Test
    fun showsRecentCanonicalPurchaseLinesWithAmountCategoryAndMerchant() {
        val olderPurchaseId = EntityId("00000000-0000-0000-0000-000000000301")
        val newerPurchaseId = EntityId("00000000-0000-0000-0000-000000000302")
        val preview = CanonicalRecentSpendingItem.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                purchases = listOf(
                    purchase(olderPurchaseId, "2026-07-10T12:00:00Z", "Merchant: Grocery Town"),
                    purchase(newerPurchaseId, "2026-07-20T12:00:00Z", "Merchant: Target"),
                ),
                purchaseLines = listOf(
                    purchaseLine(olderPurchaseId, "00000000-0000-0000-0000-000000000401", "Milk", 499, "Groceries"),
                    purchaseLine(newerPurchaseId, "00000000-0000-0000-0000-000000000402", "Dish soap", 899, "Cleaning"),
                    purchaseLine(
                        newerPurchaseId,
                        "00000000-0000-0000-0000-000000000403",
                        "Coupon",
                        -100,
                        "Discounts",
                        disposition = PurchaseLineDisposition.IGNORED,
                    ),
                ),
            ),
        )

        assertEquals(2, preview.size)
        assertEquals("Dish soap", preview[0].title)
        assertEquals("USD 8.99  Cleaning  Target", preview[0].subtitle)
        assertEquals("Milk", preview[1].title)
        assertEquals("USD 4.99  Groceries  Grocery Town", preview[1].subtitle)
    }

    private fun household(): Household = Household(
        id = householdId(),
        name = "Test",
        defaultCurrency = "USD",
        timezone = "America/New_York",
        locale = "en-US",
        activeDataHome = DataHomeKind.LOCAL,
        schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
        createdAt = UtcTimestamp(1),
        updatedAt = UtcTimestamp(1),
        revision = 0,
    )

    private fun purchase(id: EntityId, occurredAt: String, paymentNote: String): Purchase =
        Purchase(
            metadata = metadata(id),
            occurredAt = UtcTimestamp(Instant.parse(occurredAt).toEpochMilli()),
            paymentNote = paymentNote,
            status = PurchaseStatus.REVIEWED,
        )

    private fun purchaseLine(
        purchaseId: EntityId,
        id: String,
        displayName: String,
        finalMinorUnits: Long,
        spendCategory: String,
        disposition: PurchaseLineDisposition = PurchaseLineDisposition.INVENTORY,
    ): PurchaseLine =
        PurchaseLine(
            metadata = metadata(EntityId(id)),
            purchaseId = purchaseId,
            displayName = displayName,
            quantity = Quantity.unknown(),
            finalAmount = Money(finalMinorUnits, "USD"),
            spendCategory = spendCategory,
            disposition = disposition,
            reviewState = ReviewState.ACCEPTED,
        )

    private fun metadata(id: EntityId): EntityMetadata =
        EntityMetadata(
            id = id,
            householdId = householdId(),
            createdAt = UtcTimestamp(1),
            updatedAt = UtcTimestamp(1),
            source = SourceRef(SourceKind.RECEIPT, "Test"),
        )

    private fun householdId(): HouseholdId =
        HouseholdId("00000000-0000-0000-0000-000000000105")
}
