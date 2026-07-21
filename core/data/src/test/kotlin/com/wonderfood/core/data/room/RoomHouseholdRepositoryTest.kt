package com.wonderfood.core.data.room

import androidx.test.core.app.ApplicationProvider
import com.wonderfood.core.data.HouseholdRepositories
import com.wonderfood.core.data.HouseholdRepository
import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.engine.HouseholdCommandExecutionResult
import com.wonderfood.core.model.household.ChangeProposal
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandIntent
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.ConnectionId
import com.wonderfood.core.model.household.ConflictRecord
import com.wonderfood.core.model.household.Attachment
import com.wonderfood.core.model.household.AttachmentKind
import com.wonderfood.core.model.household.DataHomeAdapterOperation
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.InventoryEvent
import com.wonderfood.core.model.household.InventoryEventType
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.MealEntry
import com.wonderfood.core.model.household.MealEntryStatus
import com.wonderfood.core.model.household.MealPlan
import com.wonderfood.core.model.household.MealPlanStatus
import com.wonderfood.core.model.household.NutritionSnapshot
import com.wonderfood.core.model.household.NutritionValues
import com.wonderfood.core.model.household.PayloadHash
import com.wonderfood.core.model.household.Purchase
import com.wonderfood.core.model.household.PurchaseLine
import com.wonderfood.core.model.household.PurchaseLineDisposition
import com.wonderfood.core.model.household.PurchaseStatus
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStep
import com.wonderfood.core.model.household.RecipeStatus
import com.wonderfood.core.model.household.RecoverySnapshot
import com.wonderfood.core.model.household.RemoteBinding
import com.wonderfood.core.model.household.ReviewState
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.SyncBase
import com.wonderfood.core.model.household.SyncCursor
import com.wonderfood.core.model.household.SyncDecision
import com.wonderfood.core.model.household.SyncDecisionAction
import com.wonderfood.core.model.household.SyncOutboxRecord
import com.wonderfood.core.model.household.SyncOutboxStatus
import com.wonderfood.core.model.household.SyncRecordEnvelope
import com.wonderfood.core.model.household.LatestSafetySnapshot
import com.wonderfood.core.model.household.TombstoneReason
import com.wonderfood.core.model.household.TombstoneRecord
import com.wonderfood.core.model.household.UtcTimestamp
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomHouseholdRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: WonderFoodDatabase
    private lateinit var repository: HouseholdRepository

    @Before
    fun setUp() {
        database = WonderFoodDatabaseFactory.createInMemory(context)
        repository = HouseholdRepositories.room(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
    }

    @Test
    fun upsertHouseholdIsIdempotentByCommandId() = runTest {
        val command = HouseholdCommand.UpsertHousehold(
            record = commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"),
            household = household(),
        )

        val first = repository.apply(command)
        val second = repository.apply(command)

        assertEquals(HouseholdCommandExecutionResult.Applied(command.record.commandId), first)
        assertEquals(HouseholdCommandExecutionResult.Duplicate(command.record.commandId), second)
        assertEquals(listOf("UpsertHousehold"), repository.snapshot(householdId())?.commandRecords?.map { it.type })
    }

    @Test
    fun nonFoodItemAndUnknownQuantitySurviveDatabaseRestart() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.UpsertItem(
                record = commandRecord("00000000-0000-0000-0000-000000000102", "UpsertItem"),
                item = item(
                    id = "00000000-0000-0000-0000-000000000201",
                    name = "AA batteries",
                    kind = ItemKind.HOUSEHOLD,
                ),
            ),
        )
        repository.apply(
            HouseholdCommand.UpsertInventoryLot(
                record = commandRecord("00000000-0000-0000-0000-000000000103", "UpsertInventoryLot"),
                lot = InventoryLot(
                    metadata = metadata("00000000-0000-0000-0000-000000000301"),
                    itemId = EntityId("00000000-0000-0000-0000-000000000201"),
                    quantity = Quantity.unknown(QuantityUnit.PACKAGE),
                    status = InventoryLotStatus.AVAILABLE,
                ),
            ),
        )
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val snapshot = repository.snapshot(householdId())

        assertEquals(ItemKind.HOUSEHOLD, snapshot?.items?.single()?.kind)
        assertNull(snapshot?.items?.single()?.foodDetailsId)
        assertFalse(snapshot?.inventoryLots?.single()?.quantity?.isKnown ?: true)
        assertFalse(snapshot?.inventoryLots?.single()?.quantity?.isZero ?: true)
    }

    @Test
    fun proposalsAndOutboxStageWithoutMutatingDomainTables() = runTest {
        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.StoreProposal(
                record = commandRecord("00000000-0000-0000-0000-000000000104", "StoreProposal"),
                proposal = ChangeProposal(
                    metadata = metadata("00000000-0000-0000-0000-000000000401", SourceKind.AI_PROPOSAL),
                    sourcePayloadReference = "fixture://receipt",
                    requestedCommands = listOf(CommandIntent("UpsertShoppingLine", "hash-shopping")),
                    warnings = listOf("merchant uncertain"),
                    status = ReviewState.PENDING,
                ),
            ),
        )
        repository.apply(
            HouseholdCommand.EnqueueOutbox(
                record = commandRecord("00000000-0000-0000-0000-000000000105", "EnqueueOutbox"),
                outbox = outbox(),
            ),
        )

        val snapshot = repository.snapshot(householdId())
        val rawOutbox = database.householdDao().getSyncOutbox(householdId().value)

        assertTrue(snapshot?.items?.isEmpty() == true)
        assertTrue(snapshot?.shoppingLines?.isEmpty() == true)
        assertEquals(ReviewState.PENDING, snapshot?.proposals?.single()?.status)
        assertEquals("UpsertShoppingLine", snapshot?.proposals?.single()?.requestedCommands?.single()?.type)
        assertEquals(SyncOutboxStatus.PENDING.name, rawOutbox.single().status)
        assertEquals(HouseholdEntityType.SHOPPING_LINE.name, rawOutbox.single().entityType)
    }

    @Test
    fun pendingOutboxSurvivesDatabaseRestartAndReplayIsIdempotent() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val command = HouseholdCommand.EnqueueOutbox(
            record = commandRecord("00000000-0000-0000-0000-000000000105", "EnqueueOutbox"),
            outbox = outbox(),
        )

        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        assertEquals(HouseholdCommandExecutionResult.Applied(command.record.commandId), repository.apply(command))
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val afterRestart = database.householdDao().getSyncOutbox(householdId().value)
        val replay = repository.apply(command)

        assertEquals(1, afterRestart.size)
        assertEquals("push-shopping-line-701", afterRestart.single().idempotencyKey)
        assertEquals(SyncOutboxStatus.PENDING.name, afterRestart.single().status)
        assertEquals(0, afterRestart.single().retryCount)
        assertEquals(HouseholdCommandExecutionResult.Duplicate(command.record.commandId), replay)
        assertEquals(1, database.householdDao().getSyncOutbox(householdId().value).size)
    }

    @Test
    fun attachmentCaptureCommandStoresAttachmentAndIsIdempotentByCommandId() = runTest {
        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        val attachment = Attachment(
            metadata = metadata("00000000-0000-0000-0000-000000000601", SourceKind.RECEIPT),
            kind = AttachmentKind.RECEIPT,
            localUri = "content://app/receipt-image.jpg",
            checksum = "sha256:abcdef",
            label = "Receipt photo",
            capturedAt = UtcTimestamp(3_000_001L),
        )
        val command = HouseholdCommand.UpsertAttachment(
            record = commandRecord("00000000-0000-0000-0000-000000000602", "UpsertAttachment"),
            attachment = attachment,
        )

        assertEquals(HouseholdCommandExecutionResult.Applied(command.record.commandId), repository.apply(command))
        assertEquals(HouseholdCommandExecutionResult.Duplicate(command.record.commandId), repository.apply(command))
        assertNotNull(database.wonderFoodDao().observeAttachment(attachment.metadata.id.value).first())
        assertEquals(AttachmentKind.RECEIPT, repository.snapshot(householdId())?.attachments?.single()?.kind)
        assertEquals("content://app/receipt-image.jpg", repository.snapshot(householdId())?.attachments?.single()?.localUri)
        assertEquals("sha256:abcdef", repository.snapshot(householdId())?.attachments?.single()?.checksum)
        assertEquals(
            1,
            repository.snapshot(householdId())?.commandRecords?.count { it.type == "UpsertAttachment" },
        )
    }

    @Test
    fun archiveTombstoneSurvivesRestartAndReplayIsIdempotent() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        val itemId = EntityId("00000000-0000-0000-0000-000000000211")
        val tombstoneCommandId = CommandId("00000000-0000-0000-0000-000000000121")
        val tombstone = TombstoneRecord(
            metadata = metadata("00000000-0000-0000-0000-000000000221", SourceKind.SYSTEM),
            entityType = HouseholdEntityType.ITEM,
            entityId = itemId,
            reason = TombstoneReason.ARCHIVED_BY_APP,
            commandId = tombstoneCommandId,
        )
        val command = HouseholdCommand.StoreTombstone(
            record = commandRecord(tombstoneCommandId.value, "StoreTombstone"),
            tombstone = tombstone,
        )

        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.UpsertItem(
                record = commandRecord("00000000-0000-0000-0000-000000000120", "ArchiveItem"),
                item = item(
                    id = itemId.value,
                    name = "Expired cereal",
                    kind = ItemKind.FOOD,
                ).copy(
                    metadata = metadata(itemId.value).copy(
                        updatedAt = UtcTimestamp(10),
                        archivedAt = UtcTimestamp(10),
                        revision = 2,
                    ),
                ),
            ),
        )
        assertEquals(HouseholdCommandExecutionResult.Applied(command.record.commandId), repository.apply(command))
        assertEquals(HouseholdCommandExecutionResult.Duplicate(command.record.commandId), repository.apply(command))
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val snapshot = repository.snapshot(householdId())

        assertEquals(UtcTimestamp(10), snapshot?.items?.single()?.metadata?.archivedAt)
        assertEquals(tombstone.entityId, snapshot?.tombstones?.single()?.entityId)
        assertEquals(HouseholdEntityType.ITEM, snapshot?.tombstones?.single()?.entityType)
        assertEquals(TombstoneReason.ARCHIVED_BY_APP, snapshot?.tombstones?.single()?.reason)
        assertEquals(tombstoneCommandId, snapshot?.tombstones?.single()?.commandId)
        assertEquals(1, snapshot?.commandRecords?.count { it.type == "StoreTombstone" })
    }

    @Test
    fun syncBindingsBasesConflictsAndLatestSafetySurviveRestartAndReplayIsIdempotent() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        val connectionId = ConnectionId("00000000-0000-0000-0000-000000000631")
        val itemId = EntityId("00000000-0000-0000-0000-000000000231")
        val binding = RemoteBinding(
            connectionId = connectionId,
            entityType = HouseholdEntityType.ITEM,
            entityId = itemId,
            remoteObjectId = "notion-page-231",
            remoteParentId = "notion-db-kitchen",
            remoteSchemaFingerprint = "schema-v3",
        )
        val base = SyncBase(
            binding = binding,
            envelope = syncEnvelope(
                entityId = itemId,
                lastCommandId = CommandId("00000000-0000-0000-0000-000000000131"),
                payloadHash = PayloadHash("base-hash-1"),
            ),
            localRevision = 4,
            remoteRevision = "remote-rev-7",
            basePayloadHash = PayloadHash("base-hash-0"),
            pulledAt = UtcTimestamp(8),
        )
        val conflict = ConflictRecord(
            metadata = metadata("00000000-0000-0000-0000-000000000232", SourceKind.DATA_HOME_HUMAN),
            entityType = HouseholdEntityType.ITEM,
            entityId = itemId,
            baseHash = PayloadHash("base-hash-0"),
            appHash = PayloadHash("app-hash-1"),
            dataHomeHash = PayloadHash("data-home-hash-1"),
            decision = SyncDecision(
                action = SyncDecisionAction.NEEDS_REVIEW,
                fields = setOf("item.quantity", "item.archived"),
                reason = "Quantity/archive conflict needs review.",
            ),
            appChangedFields = setOf("item.quantity"),
            dataHomeChangedFields = setOf("item.archived"),
        )
        val safetyCommandId = CommandId("00000000-0000-0000-0000-000000000134")
        val safety = LatestSafetySnapshot(
            id = EntityId("00000000-0000-0000-0000-000000000233"),
            householdId = householdId(),
            reason = "latest-safety before remote replace",
            createdAt = UtcTimestamp(9),
            localReplicaHash = PayloadHash("local-replica-before-remote-replace"),
            activeDataHome = DataHomeKind.NOTION,
            connectionId = connectionId,
            commandId = safetyCommandId,
        )
        val commands = listOf(
            HouseholdCommand.StoreRemoteBinding(
                record = commandRecord("00000000-0000-0000-0000-000000000131", "StoreRemoteBinding"),
                binding = binding,
            ),
            HouseholdCommand.StoreSyncBase(
                record = commandRecord("00000000-0000-0000-0000-000000000132", "StoreSyncBase"),
                base = base,
            ),
            HouseholdCommand.StoreConflict(
                record = commandRecord("00000000-0000-0000-0000-000000000133", "StoreConflict"),
                conflict = conflict,
            ),
            HouseholdCommand.StoreLatestSafetySnapshot(
                record = commandRecord(safetyCommandId.value, "StoreLatestSafetySnapshot"),
                safetySnapshot = safety,
            ),
        )

        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        commands.forEach { command ->
            assertEquals(HouseholdCommandExecutionResult.Applied(command.record.commandId), repository.apply(command))
        }
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val snapshot = repository.snapshot(householdId())
        commands.forEach { command ->
            assertEquals(HouseholdCommandExecutionResult.Duplicate(command.record.commandId), repository.apply(command))
        }

        assertEquals(binding, snapshot?.remoteBindings?.single())
        assertEquals("remote-rev-7", snapshot?.syncBases?.single()?.remoteRevision)
        assertEquals(PayloadHash("base-hash-0"), snapshot?.syncBases?.single()?.basePayloadHash)
        assertEquals(SyncDecisionAction.NEEDS_REVIEW, snapshot?.conflicts?.single()?.decision?.action)
        assertEquals(setOf("item.quantity", "item.archived"), snapshot?.conflicts?.single()?.decision?.fields)
        assertEquals(setOf("item.quantity"), snapshot?.conflicts?.single()?.appChangedFields)
        assertEquals("latest-safety before remote replace", snapshot?.latestSafetySnapshots?.single()?.reason)
        assertEquals(DataHomeKind.NOTION, snapshot?.latestSafetySnapshots?.single()?.activeDataHome)
        assertEquals(1, repository.snapshot(householdId())?.commandRecords?.count { it.type == "StoreLatestSafetySnapshot" })
    }

    @Test
    fun inventoryEventsSyncCursorsAndRecoverySnapshotsSurviveRestartAndReplayIsIdempotent() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        val itemId = EntityId("00000000-0000-0000-0000-000000000241")
        val lotId = EntityId("00000000-0000-0000-0000-000000000341")
        val eventCommandId = CommandId("00000000-0000-0000-0000-000000000141")
        val recoveryCommandId = CommandId("00000000-0000-0000-0000-000000000143")
        val connectionId = ConnectionId("00000000-0000-0000-0000-000000000641")
        val event = InventoryEvent(
            metadata = metadata("00000000-0000-0000-0000-000000000441", SourceKind.SYSTEM),
            itemId = itemId,
            lotId = lotId,
            type = InventoryEventType.ADJUST,
            quantityDelta = Quantity(com.wonderfood.core.model.household.DecimalAmount.of("2"), QuantityUnit.PACKAGE),
            reason = "Cycle count",
            relatedEntityId = EntityId("00000000-0000-0000-0000-000000000541"),
            commandId = eventCommandId,
        )
        val cursor = SyncCursor(
            id = EntityId("00000000-0000-0000-0000-000000000642"),
            householdId = householdId(),
            connectionId = connectionId,
            cursor = "notion-cursor-42",
            pulledAt = UtcTimestamp(42),
            remoteHighWatermark = "remote-watermark-43",
        )
        val recovery = RecoverySnapshot(
            id = EntityId("00000000-0000-0000-0000-000000000643"),
            householdId = householdId(),
            reason = "latest-safety before bulk resolution",
            createdAt = UtcTimestamp(43),
            payloadHash = PayloadHash("recovery-hash-43"),
            objectCount = 7,
            commandId = recoveryCommandId,
        )
        val commands = listOf(
            HouseholdCommand.StoreInventoryEvent(
                record = commandRecord(eventCommandId.value, "StoreInventoryEvent"),
                event = event,
            ),
            HouseholdCommand.StoreSyncCursor(
                record = commandRecord("00000000-0000-0000-0000-000000000142", "StoreSyncCursor"),
                cursor = cursor,
            ),
            HouseholdCommand.StoreRecoverySnapshot(
                record = commandRecord(recoveryCommandId.value, "StoreRecoverySnapshot"),
                recoverySnapshot = recovery,
            ),
        )

        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        commands.forEach { command ->
            assertEquals(HouseholdCommandExecutionResult.Applied(command.record.commandId), repository.apply(command))
        }
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val snapshot = repository.snapshot(householdId())
        commands.forEach { command ->
            assertEquals(HouseholdCommandExecutionResult.Duplicate(command.record.commandId), repository.apply(command))
        }

        assertEquals(InventoryEventType.ADJUST, snapshot?.inventoryEvents?.single()?.type)
        assertEquals("2", snapshot?.inventoryEvents?.single()?.quantityDelta?.amount?.value)
        assertEquals(eventCommandId, snapshot?.inventoryEvents?.single()?.commandId)
        assertEquals("notion-cursor-42", snapshot?.syncCursors?.single()?.cursor)
        assertEquals("remote-watermark-43", snapshot?.syncCursors?.single()?.remoteHighWatermark)
        assertEquals("latest-safety before bulk resolution", snapshot?.recoverySnapshots?.single()?.reason)
        assertEquals(PayloadHash("recovery-hash-43"), snapshot?.recoverySnapshots?.single()?.payloadHash)
        assertEquals(7, snapshot?.recoverySnapshots?.single()?.objectCount)
        assertEquals(1, repository.snapshot(householdId())?.commandRecords?.count { it.type == "StoreRecoverySnapshot" })
    }

    @Test
    fun searchFindsFoodAndNonFoodFromCanonicalItemsOnly() = runTest {
        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.UpsertItem(
                record = commandRecord("00000000-0000-0000-0000-000000000106", "UpsertItem"),
                item = item(
                    id = "00000000-0000-0000-0000-000000000202",
                    name = "Rolled oats",
                    kind = ItemKind.FOOD,
                    category = "Breakfast",
                    notes = "Pantry staple",
                ),
            ),
        )
        repository.apply(
            HouseholdCommand.UpsertItem(
                record = commandRecord("00000000-0000-0000-0000-000000000107", "UpsertItem"),
                item = item(
                    id = "00000000-0000-0000-0000-000000000203",
                    name = "Dish soap",
                    kind = ItemKind.CLEANING,
                    category = "Cleaning",
                    notes = "Sink refill",
                ),
            ),
        )

        assertEquals(listOf("Rolled oats"), repository.searchItems(householdId(), "pantry").map { it.name })
        assertEquals(listOf("Dish soap"), repository.searchItems(householdId(), "clean").map { it.name })
        assertEquals(emptyList<Item>(), repository.searchItems(householdId(), "   "))
    }

    @Test
    fun recipeRootPersistsAcrossRestart() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.UpsertRecipe(
                record = commandRecord("00000000-0000-0000-0000-000000000108", "UpsertRecipe"),
                recipe = Recipe(
                    metadata = metadata("00000000-0000-0000-0000-000000000801"),
                    name = "Dal",
                    description = "Weeknight lentils",
                    sourceUrl = "https://example.com/dal",
                    cuisine = "Indian",
                    category = "Dinner",
                    tags = setOf("quick", "vegetarian"),
                    yield = Quantity(com.wonderfood.core.model.household.DecimalAmount.of("4"), QuantityUnit.SERVING),
                    prepMinutes = 10,
                    cookMinutes = 25,
                    totalMinutes = 35,
                    difficulty = "easy",
                    status = RecipeStatus.ACTIVE,
                    ingredientIds = listOf(EntityId("00000000-0000-0000-0000-000000000901")),
                    stepIds = listOf(EntityId("00000000-0000-0000-0000-000000000902")),
                ),
            ),
        )
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val recipe = repository.snapshot(householdId())?.recipes?.single()

        assertEquals("Dal", recipe?.name)
        assertEquals(RecipeStatus.ACTIVE, recipe?.status)
        assertEquals(setOf("quick", "vegetarian"), recipe?.tags)
        assertEquals("4", recipe?.yield?.amount?.value)
        assertEquals(QuantityUnit.SERVING, recipe?.yield?.unit)
        assertEquals("00000000-0000-0000-0000-000000000901", recipe?.ingredientIds?.single()?.value)
    }

    @Test
    fun recipeIngredientPersistsAcrossRestart() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        val recipeId = EntityId("00000000-0000-0000-0000-000000000811")
        val itemId = EntityId("00000000-0000-0000-0000-000000000812")
        val ingredientId = EntityId("00000000-0000-0000-0000-000000000813")
        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.UpsertItem(
                record = commandRecord("00000000-0000-0000-0000-000000000114", "UpsertItem"),
                item = item(
                    id = itemId.value,
                    name = "Lentils",
                    kind = ItemKind.FOOD,
                    category = "Pantry",
                ),
            ),
        )
        repository.apply(
            HouseholdCommand.UpsertRecipe(
                record = commandRecord("00000000-0000-0000-0000-000000000115", "UpsertRecipe"),
                recipe = Recipe(
                    metadata = metadata(recipeId.value),
                    name = "Dal",
                    status = RecipeStatus.ACTIVE,
                    ingredientIds = listOf(ingredientId),
                ),
            ),
        )
        repository.apply(
            HouseholdCommand.UpsertRecipeIngredient(
                record = commandRecord("00000000-0000-0000-0000-000000000116", "UpsertRecipeIngredient"),
                ingredient = RecipeIngredient(
                    metadata = metadata(ingredientId.value),
                    recipeId = recipeId,
                    itemId = itemId,
                    originalText = "1 cup lentils",
                    quantity = Quantity(com.wonderfood.core.model.household.DecimalAmount.of("1"), QuantityUnit.CUP),
                    preparation = "rinsed",
                    order = 0,
                ),
            ),
        )
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val snapshot = repository.snapshot(householdId())
        val ingredient = snapshot?.recipeIngredients?.single()

        assertEquals("Dal", snapshot?.recipes?.single()?.name)
        assertEquals(recipeId, ingredient?.recipeId)
        assertEquals(itemId, ingredient?.itemId)
        assertEquals("1 cup lentils", ingredient?.originalText)
        assertEquals("1", ingredient?.quantity?.amount?.value)
        assertEquals(QuantityUnit.CUP, ingredient?.quantity?.unit)
        assertEquals("rinsed", ingredient?.preparation)
    }

    @Test
    fun recipeStepPersistsAcrossRestart() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        val recipeId = EntityId("00000000-0000-0000-0000-000000000821")
        val stepId = EntityId("00000000-0000-0000-0000-000000000822")
        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.UpsertRecipe(
                record = commandRecord("00000000-0000-0000-0000-000000000117", "UpsertRecipe"),
                recipe = Recipe(
                    metadata = metadata(recipeId.value),
                    name = "Dal",
                    status = RecipeStatus.ACTIVE,
                    stepIds = listOf(stepId),
                ),
            ),
        )
        repository.apply(
            HouseholdCommand.UpsertRecipeStep(
                record = commandRecord("00000000-0000-0000-0000-000000000118", "UpsertRecipeStep"),
                step = RecipeStep(
                    metadata = metadata(stepId.value),
                    recipeId = recipeId,
                    order = 0,
                    instruction = "Simmer lentils until soft.",
                    durationMinutes = 25,
                    timerLabel = "Lentils",
                ),
            ),
        )
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val snapshot = repository.snapshot(householdId())
        val step = snapshot?.recipeSteps?.single()

        assertEquals(listOf(stepId), snapshot?.recipes?.single()?.stepIds)
        assertEquals(recipeId, step?.recipeId)
        assertEquals("Simmer lentils until soft.", step?.instruction)
        assertEquals(25, step?.durationMinutes)
        assertEquals("Lentils", step?.timerLabel)
    }

    @Test
    fun purchaseAndLinePersistAcrossRestartWithMinorUnitMoney() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        val purchaseId = EntityId("00000000-0000-0000-0000-000000000a01")
        val lineId = EntityId("00000000-0000-0000-0000-000000000a02")
        val lotId = EntityId("00000000-0000-0000-0000-000000000a03")
        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.UpsertPurchase(
                record = commandRecord("00000000-0000-0000-0000-000000000109", "UpsertPurchase"),
                purchase = Purchase(
                    metadata = metadata(purchaseId.value, SourceKind.RECEIPT),
                    occurredAt = UtcTimestamp(10),
                    total = com.wonderfood.core.model.household.Money(899, "USD"),
                    status = PurchaseStatus.REVIEWED,
                ),
            ),
        )
        repository.apply(
            HouseholdCommand.UpsertPurchaseLine(
                record = commandRecord("00000000-0000-0000-0000-000000000110", "UpsertPurchaseLine"),
                line = PurchaseLine(
                    metadata = metadata(lineId.value, SourceKind.RECEIPT),
                    purchaseId = purchaseId,
                    displayName = "Paper towels",
                    quantity = Quantity(com.wonderfood.core.model.household.DecimalAmount.of("2"), QuantityUnit.PACKAGE),
                    finalAmount = com.wonderfood.core.model.household.Money(899, "USD"),
                    spendCategory = "household",
                    disposition = PurchaseLineDisposition.INVENTORY,
                    inventoryLotId = lotId,
                    reviewState = ReviewState.ACCEPTED,
                ),
            ),
        )
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val snapshot = repository.snapshot(householdId())

        assertEquals(899L, snapshot?.purchases?.single()?.total?.minorUnits)
        assertEquals(PurchaseStatus.REVIEWED, snapshot?.purchases?.single()?.status)
        assertEquals("Paper towels", snapshot?.purchaseLines?.single()?.displayName)
        assertEquals(899L, snapshot?.purchaseLines?.single()?.finalAmount?.minorUnits)
        assertEquals(PurchaseLineDisposition.INVENTORY, snapshot?.purchaseLines?.single()?.disposition)
        assertEquals(lotId, snapshot?.purchaseLines?.single()?.inventoryLotId)
    }

    @Test
    fun mealEntryPersistsAcrossRestart() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.UpsertMealEntry(
                record = commandRecord("00000000-0000-0000-0000-000000000111", "UpsertMealEntry"),
                entry = MealEntry(
                    metadata = metadata("00000000-0000-0000-0000-000000000b01"),
                    scheduledAt = UtcTimestamp(1_784_462_400_000L),
                    slot = "Lunch",
                    title = "Rice bowl lunch",
                    servings = Quantity(com.wonderfood.core.model.household.DecimalAmount.of("1"), QuantityUnit.SERVING),
                    status = MealEntryStatus.EATEN,
                    notes = "Used: rice, spinach",
                ),
            ),
        )
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val entry = repository.snapshot(householdId())?.mealEntries?.single()

        assertEquals("Rice bowl lunch", entry?.title)
        assertEquals("Lunch", entry?.slot)
        assertEquals(MealEntryStatus.EATEN, entry?.status)
        assertEquals("1", entry?.servings?.amount?.value)
        assertEquals(QuantityUnit.SERVING, entry?.servings?.unit)
        assertEquals("Used: rice, spinach", entry?.notes)
    }

    @Test
    fun nutritionSnapshotPersistsAcrossRestartAndLinksToMealEntry() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        val entryId = EntityId("00000000-0000-0000-0000-000000000b11")
        val nutritionId = EntityId("00000000-0000-0000-0000-000000000b12")
        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.UpsertMealEntry(
                record = commandRecord("00000000-0000-0000-0000-000000000117", "UpsertMealEntry"),
                entry = MealEntry(
                    metadata = metadata(entryId.value),
                    scheduledAt = UtcTimestamp(1_784_462_400_000L),
                    slot = "Lunch",
                    title = "Rice bowl lunch",
                    status = MealEntryStatus.EATEN,
                    nutritionSnapshotIds = listOf(nutritionId),
                ),
            ),
        )
        repository.apply(
            HouseholdCommand.UpsertNutritionSnapshot(
                record = commandRecord("00000000-0000-0000-0000-000000000118", "UpsertNutritionSnapshot"),
                snapshot = NutritionSnapshot(
                    metadata = metadata(nutritionId.value, SourceKind.AI_PROPOSAL),
                    subject = com.wonderfood.core.model.household.EntityReference(HouseholdEntityType.MEAL_ENTRY, entryId),
                    basis = Quantity(com.wonderfood.core.model.household.DecimalAmount.of("1"), QuantityUnit.SERVING),
                    values = NutritionValues(
                        energyKcal = com.wonderfood.core.model.household.DecimalAmount.of("520"),
                        proteinGrams = com.wonderfood.core.model.household.DecimalAmount.of("24"),
                        carbohydrateGrams = com.wonderfood.core.model.household.DecimalAmount.of("61"),
                        fatGrams = com.wonderfood.core.model.household.DecimalAmount.of("18"),
                    ),
                    provider = "ai_estimate_local",
                    capturedAt = UtcTimestamp(1_784_520_000_000L),
                    warnings = listOf("User-reviewed meal-log estimate"),
                ),
            ),
        )
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val snapshot = repository.snapshot(householdId())
        val nutrition = snapshot?.nutritionSnapshots?.single()

        assertEquals(listOf(nutritionId), snapshot?.mealEntries?.single()?.nutritionSnapshotIds)
        assertEquals(HouseholdEntityType.MEAL_ENTRY, nutrition?.subject?.type)
        assertEquals(entryId, nutrition?.subject?.id)
        assertEquals("1", nutrition?.basis?.amount?.value)
        assertEquals(QuantityUnit.SERVING, nutrition?.basis?.unit)
        assertEquals("520", nutrition?.values?.energyKcal?.value)
        assertEquals("24", nutrition?.values?.proteinGrams?.value)
        assertEquals("61", nutrition?.values?.carbohydrateGrams?.value)
        assertEquals("18", nutrition?.values?.fatGrams?.value)
        assertEquals("ai_estimate_local", nutrition?.provider)
        assertEquals(listOf("User-reviewed meal-log estimate"), nutrition?.warnings)
    }

    @Test
    fun mealPlanAndLinkedEntryPersistAcrossRestart() = runTest {
        database.close()
        context.deleteDatabase(FILE_DB_NAME)
        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)

        val planId = EntityId("00000000-0000-0000-0000-000000000c01")
        repository.apply(HouseholdCommand.UpsertHousehold(commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"), household()))
        repository.apply(
            HouseholdCommand.UpsertMealPlan(
                record = commandRecord("00000000-0000-0000-0000-000000000112", "UpsertMealPlan"),
                plan = MealPlan(
                    metadata = metadata(planId.value),
                    name = "Week plan",
                    startsOn = com.wonderfood.core.model.household.CalendarDate("2026-07-20"),
                    endsOn = com.wonderfood.core.model.household.CalendarDate("2026-07-21"),
                    status = MealPlanStatus.ACTIVE,
                ),
            ),
        )
        repository.apply(
            HouseholdCommand.UpsertMealEntry(
                record = commandRecord("00000000-0000-0000-0000-000000000113", "UpsertMealEntry"),
                entry = MealEntry(
                    metadata = metadata("00000000-0000-0000-0000-000000000c02"),
                    mealPlanId = planId,
                    scheduledAt = UtcTimestamp(1_784_635_200_000L),
                    slot = "Dinner",
                    title = "Dal",
                    status = MealEntryStatus.PLANNED,
                ),
            ),
        )
        database.close()

        database = WonderFoodDatabaseFactory.create(context, FILE_DB_NAME)
        repository = HouseholdRepositories.room(database)
        val snapshot = repository.snapshot(householdId())

        assertEquals("Week plan", snapshot?.mealPlans?.single()?.name)
        assertEquals("2026-07-20", snapshot?.mealPlans?.single()?.startsOn?.value)
        assertEquals(MealPlanStatus.ACTIVE, snapshot?.mealPlans?.single()?.status)
        assertEquals(planId, snapshot?.mealEntries?.single()?.mealPlanId)
        assertEquals(MealEntryStatus.PLANNED, snapshot?.mealEntries?.single()?.status)
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

    private fun item(
        id: String,
        name: String,
        kind: ItemKind,
        category: String? = null,
        notes: String? = null,
    ): Item = Item(
        metadata = metadata(id),
        name = name,
        kind = kind,
        category = category,
        notes = notes,
        defaultUnit = QuantityUnit.PACKAGE,
    )

    private fun commandRecord(id: String, type: String): CommandRecord = CommandRecord(
        commandId = CommandId(id),
        householdId = householdId(),
        type = type,
        source = source(),
        requestedAt = UtcTimestamp(2),
        appliedAt = UtcTimestamp(3),
        affectedEntityIds = emptyList(),
    )

    private fun metadata(
        id: String,
        sourceKind: SourceKind = SourceKind.MANUAL,
    ): EntityMetadata = EntityMetadata(
        id = EntityId(id),
        householdId = householdId(),
        createdAt = UtcTimestamp(1),
        updatedAt = UtcTimestamp(2),
        revision = 1,
        source = source(sourceKind),
    )

    private fun outbox(): SyncOutboxRecord = SyncOutboxRecord(
        id = EntityId("00000000-0000-0000-0000-000000000501"),
        connectionId = ConnectionId("00000000-0000-0000-0000-000000000601"),
        commandId = CommandId("00000000-0000-0000-0000-000000000105"),
        operation = DataHomeAdapterOperation.PUSH,
        envelope = syncEnvelope(
            entityType = HouseholdEntityType.SHOPPING_LINE,
            entityId = EntityId("00000000-0000-0000-0000-000000000701"),
            lastCommandId = CommandId("00000000-0000-0000-0000-000000000105"),
            payloadHash = PayloadHash("hash"),
        ),
        idempotencyKey = "push-shopping-line-701",
        status = SyncOutboxStatus.PENDING,
    )

    private fun syncEnvelope(
        entityType: HouseholdEntityType = HouseholdEntityType.ITEM,
        entityId: EntityId,
        lastCommandId: CommandId,
        payloadHash: PayloadHash,
    ): SyncRecordEnvelope = SyncRecordEnvelope(
        householdId = householdId(),
        entityType = entityType,
        entityId = entityId,
        schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
        revision = 1,
        createdAt = UtcTimestamp(1),
        updatedAt = UtcTimestamp(2),
        originDeviceId = "test-device",
        lastCommandId = lastCommandId,
        payloadHash = payloadHash,
    )

    private fun source(kind: SourceKind = SourceKind.MANUAL): SourceRef =
        SourceRef(kind = kind, label = "Test", deviceId = "test-device")

    private fun householdId(): HouseholdId =
        HouseholdId("00000000-0000-0000-0000-000000000001")

    private companion object {
        const val FILE_DB_NAME = "room-household-repository-test.db"
    }
}
