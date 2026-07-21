package com.wonderfood.app.data

import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
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
import com.wonderfood.core.model.household.WasteEvent
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalHouseholdUiSummaryTest {
    @Test
    fun emptySnapshotReportsRepositoryReady() {
        assertEquals(
            "Canonical household repository ready",
            CanonicalHouseholdUiSummary.fromSnapshot(null).label(),
        )
    }

    @Test
    fun populatedSnapshotReportsCanonicalCounts() {
        val summary = CanonicalHouseholdUiSummary.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                items = listOf(item("00000000-0000-0000-0000-000000000201")),
                shoppingLines = emptyList(),
            ),
        )

        assertEquals("1 items · 0 lots · 0 cart lines · 0 purchases · 0 proposals", summary.label())
    }

    @Test
    fun dashboardLabelOnlyAppearsForPopulatedCanonicalState() {
        assertEquals(null, CanonicalHouseholdUiSummary().dashboardLabel())

        assertEquals(
            "3 household · 2 cart",
            CanonicalHouseholdUiSummary(items = 3, inventoryLots = 1, shoppingLines = 2).dashboardLabel(),
        )
    }

    @Test
    fun spendingLabelsUseCanonicalPurchasesForCurrentAndPreviousMonth() {
        val thisMonthPurchaseId = EntityId("00000000-0000-0000-0000-000000000301")
        val lastMonthPurchaseId = EntityId("00000000-0000-0000-0000-000000000302")
        val summary = CanonicalHouseholdUiSummary.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                purchases = listOf(
                    purchase(thisMonthPurchaseId, "2026-07-10T12:00:00Z", totalMinorUnits = 1299),
                    purchase(lastMonthPurchaseId, "2026-06-15T12:00:00Z", totalMinorUnits = null),
                ),
                purchaseLines = listOf(
                    purchaseLine(lastMonthPurchaseId, "00000000-0000-0000-0000-000000000401", 499),
                    purchaseLine(lastMonthPurchaseId, "00000000-0000-0000-0000-000000000402", 301),
                ),
            ),
            now = Instant.parse("2026-07-20T12:00:00Z"),
        )

        assertEquals(2, summary.purchases)
        assertEquals(2, summary.purchaseLines)
        assertEquals(1299L, summary.thisMonthSpentMinorUnits)
        assertEquals(800L, summary.lastMonthSpentMinorUnits)
        assertEquals(454L, summary.weeklyAverageSpentMinorUnits)
        assertEquals("USD 12.99 month · weekly avg USD 4.54 · USD 8.00 last", summary.spendingDashboardLabel())
        assertEquals("0 items · 0 lots · 0 cart lines · 2 purchases · this month USD 12.99 · weekly avg USD 4.54 · 0 proposals", summary.label())
    }

    @Test
    fun spendingFallbackIgnoresIgnoredReceiptLines() {
        val purchaseId = EntityId("00000000-0000-0000-0000-000000000303")
        val summary = CanonicalHouseholdUiSummary.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                purchases = listOf(purchase(purchaseId, "2026-07-10T12:00:00Z", totalMinorUnits = null)),
                purchaseLines = listOf(
                    purchaseLine(
                        purchaseId = purchaseId,
                        id = "00000000-0000-0000-0000-000000000403",
                        finalMinorUnits = 2000,
                        disposition = PurchaseLineDisposition.IGNORED,
                    ),
                ),
            ),
            now = Instant.parse("2026-07-20T12:00:00Z"),
        )

        assertEquals(0L, summary.thisMonthSpentMinorUnits)
        assertEquals(null, summary.spendingDashboardLabel())
    }

    @Test
    fun spendingLabelsSplitCurrentMonthFoodAndNonFoodLines() {
        val purchaseId = EntityId("00000000-0000-0000-0000-000000000304")
        val foodItemId = EntityId("00000000-0000-0000-0000-000000000204")
        val householdItemId = EntityId("00000000-0000-0000-0000-000000000205")
        val summary = CanonicalHouseholdUiSummary.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                items = listOf(
                    item(foodItemId.value, kind = ItemKind.FOOD),
                    item(householdItemId.value, kind = ItemKind.HOUSEHOLD),
                ),
                purchases = listOf(
                    purchase(
                        purchaseId,
                        "2026-07-10T12:00:00Z",
                        totalMinorUnits = 1650,
                        paymentNote = "Merchant: Patel Brothers\nLocation: Edison",
                    ),
                ),
                purchaseLines = listOf(
                    purchaseLine(
                        purchaseId = purchaseId,
                        id = "00000000-0000-0000-0000-000000000404",
                        finalMinorUnits = 1050,
                        itemId = foodItemId,
                        spendCategory = "Groceries",
                    ),
                    purchaseLine(
                        purchaseId = purchaseId,
                        id = "00000000-0000-0000-0000-000000000405",
                        finalMinorUnits = 600,
                        itemId = householdItemId,
                        spendCategory = "Paper goods",
                    ),
                ),
                wasteEvents = listOf(
                    wasteEvent("00000000-0000-0000-0000-000000000501", "2026-07-15T12:00:00Z", 250),
                ),
            ),
            now = Instant.parse("2026-07-20T12:00:00Z"),
        )

        assertEquals(1650L, summary.thisMonthSpentMinorUnits)
        assertEquals(1050L, summary.thisMonthFoodSpentMinorUnits)
        assertEquals(600L, summary.thisMonthNonFoodSpentMinorUnits)
        assertEquals("Groceries", summary.thisMonthTopCategoryLabel)
        assertEquals("Patel Brothers", summary.thisMonthTopMerchantLabel)
        assertEquals(577L, summary.weeklyAverageSpentMinorUnits)
        assertEquals(250L, summary.thisMonthWasteCostMinorUnits)
        assertEquals(
            "USD 16.50 month · weekly avg USD 5.77 · food USD 10.50 · household USD 6.00 · category Groceries · merchant Patel Brothers · waste USD 2.50 · USD 0.00 last",
            summary.spendingDashboardLabel(),
        )
        assertEquals(
            "2 items · 0 lots · 0 cart lines · 1 purchases · this month USD 16.50 · weekly avg USD 5.77 · food USD 10.50 · household USD 6.00 · top category Groceries · top merchant Patel Brothers · waste USD 2.50 · 0 proposals",
            summary.label(),
        )
    }

    @Test
    fun spendingProjectionKeepsRefundsCorrectionsUncertainCategoryAndReconciliationEvidence() {
        val purchaseId = EntityId("00000000-0000-0000-0000-000000000305")
        val summary = CanonicalHouseholdUiSummary.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                purchases = listOf(
                    Purchase(
                        metadata = EntityMetadata(
                            id = purchaseId,
                            householdId = householdId(),
                            createdAt = UtcTimestamp(Instant.parse("2026-07-10T12:00:00Z").toEpochMilli()),
                            updatedAt = UtcTimestamp(Instant.parse("2026-07-10T12:00:00Z").toEpochMilli()),
                            source = SourceRef(SourceKind.MANUAL, "Test"),
                        ),
                        occurredAt = UtcTimestamp(Instant.parse("2026-07-10T12:00:00Z").toEpochMilli()),
                        subtotal = Money(1_000, "USD"),
                        tax = Money(80, "USD"),
                        discount = Money(200, "USD"),
                        total = Money(875, "USD"),
                        status = PurchaseStatus.REVIEWED,
                    ),
                ),
                purchaseLines = listOf(
                    purchaseLine(
                        purchaseId = purchaseId,
                        id = "00000000-0000-0000-0000-000000000406",
                        finalMinorUnits = 1_000,
                        spendCategory = "food",
                    ),
                    purchaseLine(
                        purchaseId = purchaseId,
                        id = "00000000-0000-0000-0000-000000000407",
                        finalMinorUnits = -200,
                        disposition = PurchaseLineDisposition.SERVICE,
                        spendCategory = "pet",
                    ),
                    purchaseLine(
                        purchaseId = purchaseId,
                        id = "00000000-0000-0000-0000-000000000408",
                        finalMinorUnits = -5,
                        disposition = PurchaseLineDisposition.SERVICE,
                        spendCategory = "uncertain",
                    ),
                ),
            ),
            now = Instant.parse("2026-07-20T12:00:00Z"),
        )

        assertEquals(875L, summary.thisMonthSpentMinorUnits)
        assertEquals(1_000L, summary.thisMonthFoodSpentMinorUnits)
        assertEquals(-205L, summary.thisMonthNonFoodSpentMinorUnits)
        assertEquals("food", summary.thisMonthTopCategoryLabel)
        assertEquals(1, summary.unreconciledPurchaseCount)
        assertEquals(
            "USD 8.75 month · weekly avg USD 3.06 · food USD 10.00 · household -USD 2.05 · category food · 1 unreconciled · USD 0.00 last",
            summary.spendingDashboardLabel(),
        )
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

    private fun item(id: String, kind: ItemKind = ItemKind.FOOD): Item = Item(
        metadata = EntityMetadata(
            id = EntityId(id),
            householdId = householdId(),
            createdAt = UtcTimestamp(1),
            updatedAt = UtcTimestamp(1),
            source = SourceRef(SourceKind.MANUAL, "Test"),
        ),
        name = "Oats",
        kind = kind,
    )

    private fun purchase(
        id: EntityId,
        occurredAt: String,
        totalMinorUnits: Long?,
        paymentNote: String? = null,
    ): Purchase =
        Purchase(
            metadata = EntityMetadata(
                id = id,
                householdId = householdId(),
                createdAt = UtcTimestamp(Instant.parse(occurredAt).toEpochMilli()),
                updatedAt = UtcTimestamp(Instant.parse(occurredAt).toEpochMilli()),
                source = SourceRef(SourceKind.RECEIPT, "Test"),
            ),
            occurredAt = UtcTimestamp(Instant.parse(occurredAt).toEpochMilli()),
            total = totalMinorUnits?.let { Money(it, "USD") },
            paymentNote = paymentNote,
            status = PurchaseStatus.REVIEWED,
        )

    private fun purchaseLine(
        purchaseId: EntityId,
        id: String,
        finalMinorUnits: Long,
        disposition: PurchaseLineDisposition = PurchaseLineDisposition.INVENTORY,
        itemId: EntityId? = null,
        spendCategory: String? = null,
    ): PurchaseLine =
        PurchaseLine(
            metadata = EntityMetadata(
                id = EntityId(id),
                householdId = householdId(),
                createdAt = UtcTimestamp(1),
                updatedAt = UtcTimestamp(1),
                source = SourceRef(SourceKind.RECEIPT, "Test"),
            ),
            purchaseId = purchaseId,
            itemId = itemId,
            displayName = "Receipt line",
            quantity = Quantity.unknown(),
            finalAmount = Money(finalMinorUnits, "USD"),
            spendCategory = spendCategory,
            disposition = disposition,
            reviewState = ReviewState.ACCEPTED,
        )

    private fun wasteEvent(id: String, occurredAt: String, estimatedCostMinorUnits: Long): WasteEvent =
        WasteEvent(
            metadata = EntityMetadata(
                id = EntityId(id),
                householdId = householdId(),
                createdAt = UtcTimestamp(Instant.parse(occurredAt).toEpochMilli()),
                updatedAt = UtcTimestamp(Instant.parse(occurredAt).toEpochMilli()),
                source = SourceRef(SourceKind.MANUAL, "Test"),
            ),
            inventoryLotId = EntityId("00000000-0000-0000-0000-000000000901"),
            quantity = Quantity.unknown(),
            reason = "Spoiled",
            estimatedCost = Money(estimatedCostMinorUnits, "USD"),
            occurredAt = UtcTimestamp(Instant.parse(occurredAt).toEpochMilli()),
        )

    private fun householdId(): HouseholdId =
        HouseholdId("00000000-0000-0000-0000-000000000105")
}
