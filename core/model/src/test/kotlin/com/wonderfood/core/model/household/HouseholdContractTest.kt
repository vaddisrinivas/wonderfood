package com.wonderfood.core.model.household

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseholdContractTest {
    @Test
    fun unknownQuantityIsNotZero() {
        val unknown = Quantity.unknown(QuantityUnit.EACH)
        val zero = Quantity.zero(QuantityUnit.EACH)

        assertFalse(unknown.isKnown)
        assertFalse(unknown.isZero)
        assertTrue(zero.isKnown)
        assertTrue(zero.isZero)
        assertNotEquals(unknown, zero)
    }

    @Test
    fun nonFoodItemsNeverCarryFoodDetails() {
        val normal = item(kind = ItemKind.HOUSEHOLD, foodDetailsId = null)

        assertNull(normal.foodDetailsId)
        assertThrows(IllegalArgumentException::class.java) {
            item(kind = ItemKind.CLEANING, foodDetailsId = entityId(90))
        }
    }

    @Test
    fun purchaseLinesAreTheSpendingSourceAndRefundsRemainSigned() {
        val household = household()
        val purchaseId = entityId(20)
        val lines = listOf(
            purchaseLine(21, purchaseId, Money(1_299, "USD"), PurchaseLineDisposition.INVENTORY),
            purchaseLine(22, purchaseId, Money(499, "USD"), PurchaseLineDisposition.CONSUMED),
            purchaseLine(23, purchaseId, Money(-299, "USD"), PurchaseLineDisposition.SERVICE),
            purchaseLine(24, purchaseId, Money(9_999, "USD"), PurchaseLineDisposition.IGNORED),
        )

        val totals = HouseholdSnapshot(household = household, purchaseLines = lines).spendingMinorUnitsByCurrency()

        assertEquals(1_499L, totals["USD"])
    }

    @Test
    fun purchaseDomainSupportsB10CategoryVocabularyAndUncertainReviewState() {
        val purchaseId = entityId(25)
        val categories = listOf(
            "food" to SpendingCategory.FOOD,
            "household" to SpendingCategory.HOUSEHOLD,
            "cleaning" to SpendingCategory.CLEANING,
            "personal-care" to SpendingCategory.PERSONAL_CARE,
            "medicine" to SpendingCategory.MEDICINE,
            "pet" to SpendingCategory.PET,
            "other" to SpendingCategory.OTHER,
            "uncertain" to SpendingCategory.UNCERTAIN,
        )

        categories.forEachIndexed { index, (rawCategory, category) ->
            val line = purchaseLine(
                index = 30 + index,
                purchaseId = purchaseId,
                amount = Money(100, "USD"),
                disposition = PurchaseLineDisposition.SERVICE,
                spendCategory = rawCategory,
                reviewState = if (category == SpendingCategory.UNCERTAIN) ReviewState.PENDING else ReviewState.ACCEPTED,
            )

            assertEquals(category, line.resolvedSpendingCategory())
            if (category == SpendingCategory.UNCERTAIN) {
                assertEquals(ReviewState.PENDING, line.reviewState)
            }
        }
    }

    @Test
    fun purchaseReconciliationUsesSubtotalTaxDiscountTipAndTotal() {
        val purchase = Purchase(
            metadata = metadata(60),
            occurredAt = UtcTimestamp(1),
            subtotal = Money(2_000, "USD"),
            tax = Money(160, "USD"),
            discount = Money(250, "USD"),
            tip = Money(100, "USD"),
            total = Money(2_010, "USD"),
            status = PurchaseStatus.RECONCILED,
        )

        assertEquals(0L, purchase.calculatedReconciliationDifference()?.minorUnits)
        assertTrue(purchase.isReconciled())
        assertEquals(
            -5L,
            purchase.copy(total = Money(2_005, "USD")).calculatedReconciliationDifference()?.minorUnits,
        )
    }

    @Test
    fun workspaceKeepsSixDailyHouseholdSurfacesAndTechnicalTablesHidden() {
        val visibleLabels = HouseholdWorkspaceContract.visibleSurfaces.map { it.label }

        assertEquals(listOf("Home", "Kitchen", "Shopping", "Meals", "Recipes", "Spending", "Lists & Help"), visibleLabels)
        assertTrue(HouseholdWorkspaceContract.supportSurfaces.all { !it.visible })
        assertEquals(
            WorkspaceFieldOwner.APP_DERIVED,
            HouseholdWorkspaceContract.field("recipes.can_make_percent")?.owner,
        )
        assertEquals(
            ConflictRisk.HIGH,
            HouseholdWorkspaceContract.field("kitchen.on_hand")?.conflictRisk,
        )
    }

    @Test
    fun ordinaryRemoteEditsPullWithoutConflict() {
        val decision = HouseholdConflictPolicy.decide(
            app = RecordDelta(),
            dataHome = RecordDelta(mapOf("kitchen.notes" to FieldDelta("", "Use upstairs"))),
        )

        assertEquals(SyncDecisionAction.PULL_DATA_HOME, decision.action)
    }

    @Test
    fun overlappingLowRiskTextUsesHumanDataHome() {
        val decision = HouseholdConflictPolicy.decide(
            app = RecordDelta(mapOf("kitchen.notes" to FieldDelta("", "App note"))),
            dataHome = RecordDelta(mapOf("kitchen.notes" to FieldDelta("", "Notion note"))),
        )

        assertEquals(SyncDecisionAction.DATA_HOME_WINS_OVERLAP, decision.action)
        assertEquals(setOf("kitchen.notes"), decision.fields)
        assertEquals("App note", decision.recoveryHistory.single().displacedAppValue)
        assertEquals("Notion note", decision.recoveryHistory.single().selectedDataHomeValue)
        assertEquals("", decision.recoveryHistory.single().base)
    }

    @Test
    fun overlappingQuantityNeedsReview() {
        val decision = HouseholdConflictPolicy.decide(
            app = RecordDelta(mapOf("kitchen.on_hand" to FieldDelta("1", "2"))),
            dataHome = RecordDelta(mapOf("kitchen.on_hand" to FieldDelta("1", "4"))),
        )

        assertEquals(SyncDecisionAction.NEEDS_REVIEW, decision.action)
        assertEquals(setOf("kitchen.on_hand"), decision.fields)
    }

    @Test
    fun baseLocalRemoteDecisionMatrixHandlesOneSidedDisjointConvergedAndInvalidChanges() {
        assertEquals(
            SyncDecisionAction.NO_OP,
            HouseholdConflictPolicy.decide(RecordDelta(), RecordDelta()).action,
        )
        assertEquals(
            SyncDecisionAction.PULL_DATA_HOME,
            HouseholdConflictPolicy.decide(
                app = RecordDelta(),
                dataHome = RecordDelta(mapOf("shopping.store" to FieldDelta("Costco", "Patel Brothers"))),
            ).action,
        )
        assertEquals(
            SyncDecisionAction.PUSH_APP,
            HouseholdConflictPolicy.decide(
                app = RecordDelta(mapOf("shopping.store" to FieldDelta("Costco", "Patel Brothers"))),
                dataHome = RecordDelta(),
            ).action,
        )
        assertEquals(
            SyncDecisionAction.MERGE,
            HouseholdConflictPolicy.decide(
                app = RecordDelta(mapOf("shopping.notes" to FieldDelta("", "organic"))),
                dataHome = RecordDelta(mapOf("shopping.store" to FieldDelta("", "Patel Brothers"))),
            ).action,
        )
        assertEquals(
            SyncDecisionAction.MERGE,
            HouseholdConflictPolicy.decide(
                app = RecordDelta(mapOf("shopping.notes" to FieldDelta("", "organic"))),
                dataHome = RecordDelta(mapOf("shopping.notes" to FieldDelta("", "organic"))),
            ).action,
        )
        assertEquals(
            SyncDecisionAction.NEEDS_REVIEW,
            HouseholdConflictPolicy.decide(
                app = RecordDelta(invalidFields = setOf("spending.total")),
                dataHome = RecordDelta(),
            ).action,
        )
    }

    @Test
    fun highRiskWorkspaceFieldsNeedReviewAcrossFoodMoneyArchiveRecipeAndMealChanges() {
        val highRiskPaths = listOf(
            "kitchen.on_hand",
            "shopping.actual_price",
            "kitchen.archived",
            "recipes.ingredients",
            "meals.scheduled_at",
            "meals.servings",
            "spending.total",
        )

        highRiskPaths.forEach { path ->
            val decision = HouseholdConflictPolicy.decide(
                app = RecordDelta(mapOf(path to FieldDelta("base", "app"))),
                dataHome = RecordDelta(mapOf(path to FieldDelta("base", "data-home"))),
            )

            assertEquals(path, SyncDecisionAction.NEEDS_REVIEW, decision.action)
            assertEquals(path, decision.fields.single())
        }
    }

    @Test
    fun archiveAgainstAnyOtherEditNeedsReview() {
        val decision = HouseholdConflictPolicy.decide(
            app = RecordDelta(mapOf("kitchen.archived" to FieldDelta("false", "true"))),
            dataHome = RecordDelta(mapOf("kitchen.notes" to FieldDelta("", "Keep this"))),
        )

        assertEquals(SyncDecisionAction.NEEDS_REVIEW, decision.action)
    }

    @Test
    fun phaseZeroInventoryNamesEveryAffectedRuntimeSurface() {
        assertEquals(
            RuntimeSurfaceArea.entries.toSet(),
            HouseholdRuntimeContract.legacyReplacementInventory.map { it.area }.toSet(),
        )
        assertTrue(
            HouseholdRuntimeContract.legacyReplacementInventory.any {
                it.name.contains("Retired app memory store") &&
                    it.disposition == RuntimeSurfaceDisposition.DELETE_AFTER_CALLERS_MIGRATE
            },
        )
        assertEquals(
            HouseholdRuntimeContract.commandBoundary,
            HouseholdRuntimeContract.mutationBoundaryFor(SourceKind.DATA_HOME_HUMAN),
        )
    }

    @Test
    fun dataHomeAdapterContractRequiresEveryLifecycleOperation() {
        assertEquals(DataHomeAdapterOperation.entries.toSet(), DataHomeAdapterContract.requiredOperations)
        assertTrue(DataHomeAdapterOperation.PULL in DataHomeAdapterContract.requiredOperations)
        assertTrue(DataHomeAdapterOperation.PUSH in DataHomeAdapterContract.requiredOperations)
        assertTrue(DataHomeAdapterOperation.REPAIR in DataHomeAdapterContract.requiredOperations)
    }

    @Test
    fun syncEnvelopeCarriesCanonicalIdentityAndRejectsSchemaDrift() {
        val envelope = syncEnvelope(entityType = HouseholdEntityType.ITEM)

        assertEquals(HouseholdWorkspaceContract.SCHEMA_VERSION, envelope.schemaVersion)
        assertEquals(HouseholdEntityType.ITEM, envelope.entityType)
        assertThrows(IllegalArgumentException::class.java) {
            envelope.copy(schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION + 1)
        }
    }

    @Test
    fun outboxOnlyRepresentsRemotePushOrRepairWork() {
        val push = SyncOutboxRecord(
            id = entityId(200),
            connectionId = connectionId(),
            commandId = commandId(),
            operation = DataHomeAdapterOperation.PUSH,
            envelope = syncEnvelope(entityType = HouseholdEntityType.SHOPPING_LINE),
            idempotencyKey = "push-shopping-line-1",
            status = SyncOutboxStatus.PENDING,
        )

        assertEquals(SyncOutboxStatus.PENDING, push.status)
        assertThrows(IllegalArgumentException::class.java) {
            push.copy(operation = DataHomeAdapterOperation.PULL)
        }
    }

    @Test
    fun reviewConflictsRetainBaseAppAndDataHomeHashes() {
        val decision = SyncDecision(
            action = SyncDecisionAction.NEEDS_REVIEW,
            fields = setOf("shopping.amount"),
            reason = "Both sides changed amount.",
        )
        val conflict = ConflictRecord(
            metadata = metadata(300),
            entityType = HouseholdEntityType.SHOPPING_LINE,
            entityId = entityId(301),
            baseHash = PayloadHash("base"),
            appHash = PayloadHash("app"),
            dataHomeHash = PayloadHash("data-home"),
            decision = decision,
            appChangedFields = setOf("shopping.amount"),
            dataHomeChangedFields = setOf("shopping.amount"),
        )

        assertEquals(PayloadHash("base"), conflict.baseHash)
        assertEquals(PayloadHash("app"), conflict.appHash)
        assertEquals(PayloadHash("data-home"), conflict.dataHomeHash)
        assertThrows(IllegalArgumentException::class.java) {
            conflict.copy(decision = decision.copy(action = SyncDecisionAction.MERGE))
        }
    }

    private fun household(): Household = Household(
        id = householdId(),
        name = "Test household",
        defaultCurrency = "USD",
        timezone = "America/New_York",
        locale = "en-US",
        activeDataHome = DataHomeKind.LOCAL,
        schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
        createdAt = UtcTimestamp(1),
        updatedAt = UtcTimestamp(1),
        revision = 0,
    )

    private fun item(kind: ItemKind, foodDetailsId: EntityId?): Item = Item(
        metadata = metadata(10),
        name = "Test item",
        kind = kind,
        foodDetailsId = foodDetailsId,
    )

    private fun purchaseLine(
        index: Int,
        purchaseId: EntityId,
        amount: Money,
        disposition: PurchaseLineDisposition,
        spendCategory: String? = null,
        reviewState: ReviewState = ReviewState.ACCEPTED,
    ): PurchaseLine = PurchaseLine(
        metadata = metadata(index),
        purchaseId = purchaseId,
        displayName = "Line $index",
        finalAmount = amount,
        spendCategory = spendCategory,
        disposition = disposition,
        reviewState = reviewState,
    )

    private fun metadata(index: Int): EntityMetadata = EntityMetadata(
        id = entityId(index),
        householdId = householdId(),
        createdAt = UtcTimestamp(1),
        updatedAt = UtcTimestamp(1),
        source = SourceRef(SourceKind.MANUAL, "Test"),
    )

    private fun householdId(): HouseholdId = HouseholdId("00000000-0000-0000-0000-000000000001")

    private fun entityId(index: Int): EntityId =
        EntityId("00000000-0000-0000-0000-${index.toString().padStart(12, '0')}")

    private fun commandId(): CommandId =
        CommandId("00000000-0000-0000-0000-000000000090")

    private fun connectionId(): ConnectionId =
        ConnectionId("00000000-0000-0000-0000-000000000091")

    private fun syncEnvelope(entityType: HouseholdEntityType): SyncRecordEnvelope = SyncRecordEnvelope(
        householdId = householdId(),
        entityType = entityType,
        entityId = entityId(100),
        schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
        revision = 1,
        createdAt = UtcTimestamp(1),
        updatedAt = UtcTimestamp(2),
        originDeviceId = "test-device",
        lastCommandId = commandId(),
        payloadHash = PayloadHash("hash"),
    )
}
