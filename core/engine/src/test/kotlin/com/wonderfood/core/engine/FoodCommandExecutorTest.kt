package com.wonderfood.core.engine

import com.wonderfood.core.model.Confidence
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.Food
import com.wonderfood.core.model.FoodAlias
import com.wonderfood.core.model.FoodAliasId
import com.wonderfood.core.model.FoodEventId
import com.wonderfood.core.model.FoodEventType
import com.wonderfood.core.model.FoodId
import com.wonderfood.core.model.FoodStatus
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.Page
import com.wonderfood.core.model.PageId
import com.wonderfood.core.model.PageKind
import com.wonderfood.core.model.Quantity
import com.wonderfood.core.model.Source
import com.wonderfood.core.model.SourceId
import com.wonderfood.core.model.SourceKind
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.StockLot
import com.wonderfood.core.model.StockLotId
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.TruthState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodCommandExecutorTest {
    private val ids = SequenceIds()
    private val repository = FakeFoodCommandRepository()
    private val executor = FoodCommandExecutor(repository, ids)

    @Test
    fun duplicateIdempotencyKeyIsNoOp() = runTest {
        val command = createFoodCommand("idem-food")
        val existing = audit("existing-action", "idem-food", command.subject, "CreateFoodGraph")
        repository.recordAction(existing)

        val result = executor.execute(command)

        assertTrue(result is FoodCommandExecutionResult.Duplicate)
        assertEquals(0, repository.createFoodCalls)
        assertEquals(listOf(existing), repository.actions.values.toList())
    }

    @Test
    fun rejectedCommandMakesNoChanges() = runTest {
        val command = createFoodCommand("idem-bad").let {
            it.copy(page = it.page.copy(entity = EntityRef(EntityType.RECIPE, "recipe-not-food")))
        }

        val result = executor.execute(command)

        assertTrue(result is FoodCommandExecutionResult.Rejected)
        assertEquals(emptyMap<String, String>(), repository.foodStatuses)
        assertEquals(0, repository.actions.size)
    }

    @Test
    fun destructiveCommandRequiresConfirmation() = runTest {
        repository.foodStatuses["food-oats"] = FoodStatus.ACTIVE.name
        val command = FoodCommand.ArchiveFood(
            idempotencyKey = IdempotencyKey("idem-archive"),
            requestedAt = timestamp(),
            source = source(),
            confidence = Confidence(score = 1.0, state = TruthState.USER_CONFIRMED, rationale = "test"),
            foodId = FoodId("food-oats"),
            reason = "duplicate",
        )

        val result = executor.execute(command, confirmed = false)

        assertTrue(result is FoodCommandExecutionResult.NeedsConfirmation)
        assertEquals(FoodStatus.ACTIVE.name, repository.foodStatuses["food-oats"])
        assertEquals(0, repository.actions.size)
    }

    @Test
    fun undoRestoresArchivedFoodThroughNewEvent() = runTest {
        repository.foodStatuses["food-oats"] = FoodStatus.ACTIVE.name
        val archive = FoodCommand.ArchiveFood(
            idempotencyKey = IdempotencyKey("idem-archive"),
            requestedAt = timestamp(),
            source = source(),
            confidence = Confidence(score = 1.0, state = TruthState.USER_CONFIRMED, rationale = "test"),
            foodId = FoodId("food-oats"),
            reason = "duplicate",
        )

        val archiveResult = executor.execute(archive, confirmed = true) as FoodCommandExecutionResult.Applied
        assertEquals(FoodStatus.ARCHIVED.name, repository.foodStatuses["food-oats"])

        val undo = FoodCommand.UndoFoodAction(
            idempotencyKey = IdempotencyKey("idem-undo"),
            requestedAt = IsoTimestamp("2026-07-16T11:00:00Z"),
            source = source(),
            confidence = Confidence(score = 1.0, state = TruthState.USER_CONFIRMED, rationale = "test"),
            actionId = archiveResult.audit.id,
            reason = "not duplicate",
        )
        val undoResult = executor.execute(undo, confirmed = true)

        assertTrue(undoResult is FoodCommandExecutionResult.Applied)
        assertEquals(FoodStatus.ACTIVE.name, repository.foodStatuses["food-oats"])
        assertEquals(listOf(FoodEventType.ARCHIVED, FoodEventType.UPDATED), repository.eventTypes)
        assertEquals(2, repository.actions.size)
    }

    @Test
    fun exactConsumptionRequiresConfirmationBeforeStockMutation() = runTest {
        repository.lotStatuses["lot-oats"] = StockLotStatus.AVAILABLE.name
        val command = consumeStockLotCommand(key = "idem-consume-exact", exact = true)

        val result = executor.execute(command, confirmed = false)

        assertTrue(result is FoodCommandExecutionResult.NeedsConfirmation)
        assertEquals(StockLotStatus.AVAILABLE.name, repository.lotStatuses["lot-oats"])
        assertEquals(emptyList<FoodEventType>(), repository.eventTypes)
        assertEquals(0, repository.actions.size)
    }

    @Test
    fun uncertainConsumptionRecordsUsageProposalWithoutChangingStock() = runTest {
        repository.lotStatuses["lot-oats"] = StockLotStatus.AVAILABLE.name
        val command = consumeStockLotCommand(key = "idem-consume-uncertain", exact = false)

        val result = executor.execute(command)

        assertTrue(result is FoodCommandExecutionResult.Applied)
        assertEquals(StockLotStatus.AVAILABLE.name, repository.lotStatuses["lot-oats"])
        assertEquals(listOf(FoodEventType.STOCK_USAGE_PROPOSED), repository.eventTypes)
        assertEquals(listOf("ConsumeStockLot"), repository.actions.values.map { it.actionType })
    }

    private fun createFoodCommand(key: String): FoodCommand.CreateFoodGraph {
        val pageId = PageId("page-oats")
        val foodId = FoodId("food-oats")
        return FoodCommand.CreateFoodGraph(
            idempotencyKey = IdempotencyKey(key),
            requestedAt = timestamp(),
            source = source(),
            confidence = Confidence(score = 1.0, state = TruthState.USER_CONFIRMED, rationale = "test"),
            page = Page(
                id = pageId,
                title = "Oats",
                kind = PageKind.FOOD,
                entity = EntityRef(EntityType.FOOD, foodId.value),
                aliases = listOf("oatmeal"),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source(),
                confidence = Confidence(score = 1.0, state = TruthState.USER_CONFIRMED, rationale = "test"),
            ),
            food = Food(
                id = foodId,
                pageId = pageId,
                name = "Oats",
                status = FoodStatus.ACTIVE,
                aliasIds = listOf(FoodAliasId("alias-oats")),
                stockLotIds = emptyList(),
                nutritionSnapshotIds = emptyList(),
                attachmentIds = emptyList(),
                source = source(),
                confidence = Confidence(score = 1.0, state = TruthState.USER_CONFIRMED, rationale = "test"),
                truthState = TruthState.USER_CONFIRMED,
            ),
            aliases = listOf(
                FoodAlias(
                    id = FoodAliasId("alias-oats"),
                    foodId = foodId,
                    name = "oatmeal",
                    locale = "en-US",
                    source = source(),
                    confidence = Confidence(score = 1.0, state = TruthState.USER_CONFIRMED, rationale = "test"),
                    truthState = TruthState.USER_CONFIRMED,
                ),
            ),
            stockLots = emptyList<StockLot>(),
        )
    }

    private fun consumeStockLotCommand(key: String, exact: Boolean) =
        FoodCommand.ConsumeStockLot(
            idempotencyKey = IdempotencyKey(key),
            requestedAt = timestamp(),
            source = source(),
            confidence = Confidence(score = 1.0, state = TruthState.USER_CONFIRMED, rationale = "test"),
            stockLotId = StockLotId("lot-oats"),
            quantity = Quantity(
                amount = if (exact) 1.0 else null,
                unit = FoodUnit.EACH,
                truthState = if (exact) TruthState.USER_CONFIRMED else TruthState.ESTIMATED,
            ),
            exact = exact,
            reason = "used for breakfast",
        )

    private fun audit(id: String, key: String, subject: EntityRef, actionType: String) =
        FoodActionAudit(
            id = FoodActionId(id),
            idempotencyKey = IdempotencyKey(key),
            actionType = actionType,
            subject = subject,
            occurredAt = timestamp(),
            payload = "before.exists=false\nafter.exists=true",
            before = null,
            after = null,
            source = source(),
            confidence = Confidence(score = 1.0, state = TruthState.USER_CONFIRMED, rationale = "test"),
            truthState = TruthState.USER_CONFIRMED,
        )

    private fun timestamp() = IsoTimestamp("2026-07-16T10:00:00Z")

    private fun source() = Source(
        id = SourceId("source-user"),
        kind = SourceKind.USER,
        label = "Manual entry",
        externalId = null,
        uri = null,
        capturedAt = timestamp(),
        truthState = TruthState.USER_CONFIRMED,
    )

    private class SequenceIds : FoodCommandIdProvider {
        private var next = 0
        override fun newId(prefix: String): String {
            next += 1
            return "$prefix-$next"
        }
    }

    private class FakeFoodCommandRepository : FoodCommandRepository {
        val actions = linkedMapOf<FoodActionId, FoodActionAudit>()
        val foodStatuses = linkedMapOf<String, String>()
        val lotStatuses = linkedMapOf<String, String>()
        val eventTypes = mutableListOf<FoodEventType>()
        var createFoodCalls = 0

        override suspend fun <T> withTransaction(block: suspend () -> T): T = block()

        override suspend fun findActionById(actionId: FoodActionId): FoodActionAudit? = actions[actionId]

        override suspend fun findActionByIdempotencyKey(key: IdempotencyKey): FoodActionAudit? =
            actions.values.firstOrNull { it.idempotencyKey == key }

        override suspend fun snapshot(subject: EntityRef): SubjectSnapshot =
            when (subject.type) {
                EntityType.FOOD -> SubjectSnapshot(
                    subject = subject,
                    exists = foodStatuses.containsKey(subject.id),
                    status = foodStatuses[subject.id],
                    summary = subject.id,
                )

                EntityType.STOCK_LOT -> SubjectSnapshot(
                    subject = subject,
                    exists = lotStatuses.containsKey(subject.id),
                    status = lotStatuses[subject.id],
                    summary = subject.id,
                )

                else -> SubjectSnapshot(subject, exists = false, status = null, summary = null)
            }

        override suspend fun createFoodGraph(command: FoodCommand.CreateFoodGraph) {
            createFoodCalls += 1
            foodStatuses[command.food.id.value] = command.food.status.name
        }

        override suspend fun addStockLot(command: FoodCommand.AddStockLot) {
            lotStatuses[command.stockLot.id.value] = command.stockLot.status.name
        }

        override suspend fun archiveFood(command: FoodCommand.ArchiveFood): Boolean {
            if (!foodStatuses.containsKey(command.foodId.value)) return false
            foodStatuses[command.foodId.value] = FoodStatus.ARCHIVED.name
            return true
        }

        override suspend fun archiveStockLot(command: FoodCommand.ArchiveStockLot): Boolean {
            if (!lotStatuses.containsKey(command.stockLotId.value)) return false
            lotStatuses[command.stockLotId.value] = StockLotStatus.ARCHIVED.name
            return true
        }

        override suspend fun moveStockLot(command: FoodCommand.MoveStockLot): Boolean =
            lotStatuses.containsKey(command.stockLotId.value)

        override suspend fun openStockLot(command: FoodCommand.OpenStockLot): Boolean {
            if (!lotStatuses.containsKey(command.stockLotId.value)) return false
            lotStatuses[command.stockLotId.value] = StockLotStatus.OPENED.name
            return true
        }

        override suspend fun consumeStockLot(command: FoodCommand.ConsumeStockLot): Boolean =
            lotStatuses.containsKey(command.stockLotId.value)

        override suspend fun discardStockLot(command: FoodCommand.DiscardStockLot): Boolean {
            if (!lotStatuses.containsKey(command.stockLotId.value)) return false
            lotStatuses[command.stockLotId.value] = StockLotStatus.DISCARDED.name
            return true
        }

        override suspend fun correctStockLot(command: FoodCommand.CorrectStockLot): Boolean {
            if (!lotStatuses.containsKey(command.stockLotId.value)) return false
            command.status?.let { lotStatuses[command.stockLotId.value] = it.name }
            return true
        }

        override suspend fun markStockLotLow(command: FoodCommand.MarkStockLotLow): Boolean {
            if (!lotStatuses.containsKey(command.stockLotId.value)) return false
            lotStatuses[command.stockLotId.value] = StockLotStatus.LOW.name
            return true
        }

        override suspend fun markStockLotOut(command: FoodCommand.MarkStockLotOut): Boolean {
            if (!lotStatuses.containsKey(command.stockLotId.value)) return false
            lotStatuses[command.stockLotId.value] = StockLotStatus.OUT.name
            return true
        }

        override suspend fun putAwayStockLot(command: FoodCommand.PutAwayStockLot): Boolean {
            if (!lotStatuses.containsKey(command.stockLotId.value)) return false
            lotStatuses[command.stockLotId.value] = StockLotStatus.AVAILABLE.name
            return true
        }

        override suspend fun mergeStockLots(command: FoodCommand.MergeStockLots): Boolean {
            if (!lotStatuses.containsKey(command.sourceStockLotId.value)) return false
            if (!lotStatuses.containsKey(command.targetStockLotId.value)) return false
            lotStatuses[command.sourceStockLotId.value] = StockLotStatus.ARCHIVED.name
            return true
        }

        override suspend fun restoreFood(subject: EntityRef, restoredAt: IsoTimestamp): Boolean {
            if (!foodStatuses.containsKey(subject.id)) return false
            foodStatuses[subject.id] = FoodStatus.ACTIVE.name
            return true
        }

        override suspend fun restoreStockLot(subject: EntityRef, restoredAt: IsoTimestamp): Boolean {
            if (!lotStatuses.containsKey(subject.id)) return false
            lotStatuses[subject.id] = StockLotStatus.AVAILABLE.name
            return true
        }

        override suspend fun recordFoodEvent(
            eventId: FoodEventId,
            subject: EntityRef,
            command: FoodCommand,
            type: FoodEventType,
            note: String?,
        ) {
            eventTypes += type
        }

        override suspend fun recordAction(audit: FoodActionAudit) {
            actions[audit.id] = audit
        }
    }
}
