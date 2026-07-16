package com.wonderfood.core.data.room

import androidx.test.core.app.ApplicationProvider
import com.wonderfood.core.engine.FoodCommand
import com.wonderfood.core.engine.FoodCommandExecutionResult
import com.wonderfood.core.engine.FoodCommandExecutor
import com.wonderfood.core.engine.FoodCommandIdProvider
import com.wonderfood.core.engine.IdempotencyKey
import com.wonderfood.core.model.Confidence
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.Food
import com.wonderfood.core.model.FoodAlias
import com.wonderfood.core.model.FoodAliasId
import com.wonderfood.core.model.FoodEventType
import com.wonderfood.core.model.FoodId
import com.wonderfood.core.model.FoodStatus
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.IsoDate
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.Page
import com.wonderfood.core.model.PageId
import com.wonderfood.core.model.PageKind
import com.wonderfood.core.model.Quantity
import com.wonderfood.core.model.Source
import com.wonderfood.core.model.SourceId
import com.wonderfood.core.model.SourceKind
import com.wonderfood.core.model.StockLot
import com.wonderfood.core.model.StockLotId
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.TruthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomFoodCommandRepositoryTest {
    private lateinit var database: WonderFoodDatabase
    private lateinit var dao: WonderFoodDao
    private lateinit var executor: FoodCommandExecutor

    @Before
    fun setUp() {
        database = WonderFoodDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        database.openHelper.writableDatabase.setForeignKeyConstraintsEnabled(true)
        dao = database.wonderFoodDao()
        executor = FoodCommandExecutor(RoomFoodCommandRepository(database), SequenceIds())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createFoodGraphPersistsFoodEventAndBeforeAfterAudit() = runTest {
        val result = executor.execute(createFoodCommand("idem-create"))

        assertTrue(result is FoodCommandExecutionResult.Applied)
        assertEquals("Oats", dao.observeFood("food-oats").first()?.name)
        assertEquals("oatmeal", dao.observeAliasesForFood("food-oats").first().single().name)

        val action = dao.observeFoodActionsForSubject("FOOD", "food-oats").first().single()
        val event = dao.observeFoodEventsForSubject("FOOD", "food-oats").first().single()
        assertEquals("CreateFoodGraph", action.actionType)
        assertTrue(action.payload.contains("before.exists=false"))
        assertTrue(action.payload.contains("after.exists=true"))
        assertTrue(action.payload.contains("after.status=ACTIVE"))
        assertEquals(FoodEventType.CREATED, event.type)
    }

    @Test
    fun duplicateIdempotencyKeyDoesNotWriteSecondEventOrAction() = runTest {
        val command = createFoodCommand("idem-create")

        executor.execute(command)
        val second = executor.execute(command)

        assertTrue(second is FoodCommandExecutionResult.Duplicate)
        assertEquals(1, dao.observeFoodActionsForSubject("FOOD", "food-oats").first().size)
        assertEquals(1, dao.observeFoodEventsForSubject("FOOD", "food-oats").first().size)
    }

    @Test
    fun archiveThenUndoRestoresFoodWithNewEvent() = runTest {
        executor.execute(createFoodCommand("idem-create"))
        val archive = FoodCommand.ArchiveFood(
            idempotencyKey = IdempotencyKey("idem-archive"),
            requestedAt = IsoTimestamp("2026-07-16T11:00:00Z"),
            source = source(),
            confidence = confidence(),
            foodId = FoodId("food-oats"),
            reason = "duplicate",
        )

        val archiveResult = executor.execute(archive, confirmed = true) as FoodCommandExecutionResult.Applied
        assertEquals(FoodStatus.ARCHIVED, dao.observeFood("food-oats").first()?.status)

        val undo = FoodCommand.UndoFoodAction(
            idempotencyKey = IdempotencyKey("idem-undo"),
            requestedAt = IsoTimestamp("2026-07-16T12:00:00Z"),
            source = source(),
            confidence = confidence(),
            actionId = archiveResult.audit.id,
            reason = "keep it",
        )
        val undoResult = executor.execute(undo, confirmed = true)

        assertTrue(undoResult is FoodCommandExecutionResult.Applied)
        assertEquals(FoodStatus.ACTIVE, dao.observeFood("food-oats").first()?.status)
        assertEquals(
            listOf(FoodEventType.CREATED, FoodEventType.ARCHIVED, FoodEventType.UPDATED),
            dao.observeFoodEventsForSubject("FOOD", "food-oats").first().map { it.type },
        )
        assertEquals(
            listOf("CreateFoodGraph", "ArchiveFood", "UndoFoodAction"),
            dao.observeFoodActionsForSubject("FOOD", "food-oats").first().map { it.actionType },
        )
        assertNotNull(dao.getFoodActionByIdempotencyKey("idem-undo"))
    }

    @Test
    fun addedLotsRetainPurchaseExpirySourceAndSeparateRows() = runTest {
        executor.execute(createFoodCommand("idem-create"))

        executor.execute(addStockLotCommand("idem-lot-a", stockLot("lot-oats-a", amount = 2.0, purchasedOn = "2026-07-14", expiresOn = "2026-08-01")))
        executor.execute(addStockLotCommand("idem-lot-b", stockLot("lot-oats-b", amount = 1.0, purchasedOn = "2026-07-15", expiresOn = "2026-08-15")))

        val first = dao.getStockLot("lot-oats-a")
        val second = dao.getStockLot("lot-oats-b")
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(2.0, first?.quantity?.amount)
        assertEquals(1.0, second?.quantity?.amount)
        assertEquals("2026-07-14", first?.purchasedOn)
        assertEquals("2026-08-01", first?.expiresOn)
        assertEquals("source-user", first?.source?.id)
        assertEquals("2026-07-15", second?.purchasedOn)
        assertEquals("2026-08-15", second?.expiresOn)
        assertEquals("source-user", second?.source?.id)
    }

    @Test
    fun exactConsumptionToZeroKeepsLotAsConsumedHistory() = runTest {
        executor.execute(createFoodCommand("idem-create"))
        executor.execute(addStockLotCommand("idem-lot", stockLot("lot-oats", amount = 1.0)))

        val result = executor.execute(consumeStockLotCommand("idem-consume-exact", exact = true), confirmed = true)

        assertTrue(result is FoodCommandExecutionResult.Applied)
        val lot = dao.getStockLot("lot-oats")
        assertEquals(0.0, lot?.quantity?.amount)
        assertEquals(StockLotStatus.CONSUMED, lot?.status)
        assertEquals(null, lot?.deletedAt)
        assertEquals(null, lot?.archivedAt)
        assertEquals(
            listOf(FoodEventType.STOCK_ADDED, FoodEventType.STOCK_CONSUMED),
            dao.observeFoodEventsForSubject("STOCK_LOT", "lot-oats").first().map { it.type },
        )
    }

    @Test
    fun uncertainConsumptionRecordsProposalWithoutDecrementingStock() = runTest {
        executor.execute(createFoodCommand("idem-create"))
        executor.execute(addStockLotCommand("idem-lot", stockLot("lot-oats", amount = 5.0)))

        val result = executor.execute(consumeStockLotCommand("idem-consume-uncertain", exact = false))

        assertTrue(result is FoodCommandExecutionResult.Applied)
        val lot = dao.getStockLot("lot-oats")
        assertEquals(5.0, lot?.quantity?.amount)
        assertEquals(StockLotStatus.AVAILABLE, lot?.status)
        assertEquals(
            listOf(FoodEventType.STOCK_ADDED, FoodEventType.STOCK_USAGE_PROPOSED),
            dao.observeFoodEventsForSubject("STOCK_LOT", "lot-oats").first().map { it.type },
        )
    }

    @Test
    fun mergeStockLotsCombinesTargetAndArchivesSourceWithoutDeletingHistory() = runTest {
        executor.execute(createFoodCommand("idem-create"))
        executor.execute(addStockLotCommand("idem-target", stockLot("lot-target", amount = 2.0, purchasedOn = "2026-07-14")))
        executor.execute(addStockLotCommand("idem-source", stockLot("lot-source", amount = 3.0, purchasedOn = "2026-07-15")))

        val result = executor.execute(
            FoodCommand.MergeStockLots(
                idempotencyKey = IdempotencyKey("idem-merge"),
                requestedAt = IsoTimestamp("2026-07-16T13:00:00Z"),
                source = source(),
                confidence = confidence(),
                sourceStockLotId = StockLotId("lot-source"),
                targetStockLotId = StockLotId("lot-target"),
                reason = "same oats container",
            ),
            confirmed = true,
        )

        assertTrue(result is FoodCommandExecutionResult.Applied)
        val target = dao.getStockLot("lot-target")
        val source = dao.getStockLot("lot-source")
        assertEquals(5.0, target?.quantity?.amount)
        assertEquals(StockLotStatus.AVAILABLE, target?.status)
        assertEquals(StockLotStatus.ARCHIVED, source?.status)
        assertEquals("2026-07-15", source?.purchasedOn)
        assertEquals("source-user", source?.source?.id)
        assertEquals("2026-07-16T13:00:00Z", source?.archivedAt)
        assertEquals(null, source?.deletedAt)
        assertEquals(
            listOf(FoodEventType.STOCK_ADDED, FoodEventType.STOCK_MERGED),
            dao.observeFoodEventsForSubject("STOCK_LOT", "lot-target").first().map { it.type },
        )
    }

    private fun createFoodCommand(key: String): FoodCommand.CreateFoodGraph {
        val pageId = PageId("page-oats")
        val foodId = FoodId("food-oats")
        return FoodCommand.CreateFoodGraph(
            idempotencyKey = IdempotencyKey(key),
            requestedAt = IsoTimestamp("2026-07-16T10:00:00Z"),
            source = source(),
            confidence = confidence(),
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
                confidence = confidence(),
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
                confidence = confidence(),
                truthState = TruthState.USER_CONFIRMED,
            ),
            aliases = listOf(
                FoodAlias(
                    id = FoodAliasId("alias-oats"),
                    foodId = foodId,
                    name = "oatmeal",
                    locale = "en-US",
                    source = source(),
                    confidence = confidence(),
                    truthState = TruthState.USER_CONFIRMED,
                ),
            ),
            stockLots = emptyList(),
        )
    }

    private fun addStockLotCommand(key: String, stockLot: StockLot) =
        FoodCommand.AddStockLot(
            idempotencyKey = IdempotencyKey(key),
            requestedAt = IsoTimestamp("2026-07-16T10:30:00Z"),
            source = source(),
            confidence = confidence(),
            stockLot = stockLot,
        )

    private fun consumeStockLotCommand(key: String, exact: Boolean) =
        FoodCommand.ConsumeStockLot(
            idempotencyKey = IdempotencyKey(key),
            requestedAt = IsoTimestamp("2026-07-16T11:00:00Z"),
            source = source(),
            confidence = confidence(),
            stockLotId = StockLotId("lot-oats"),
            quantity = Quantity(
                amount = if (exact) 1.0 else null,
                unit = FoodUnit.EACH,
                truthState = if (exact) TruthState.USER_CONFIRMED else TruthState.ESTIMATED,
            ),
            exact = exact,
            reason = "breakfast",
        )

    private fun stockLot(
        id: String,
        amount: Double,
        purchasedOn: String = "2026-07-14",
        expiresOn: String = "2026-08-01",
    ) = StockLot(
        id = StockLotId(id),
        foodId = FoodId("food-oats"),
        quantity = Quantity(amount = amount, unit = FoodUnit.EACH, truthState = TruthState.USER_CONFIRMED),
        purchasedOn = IsoDate(purchasedOn),
        expiresOn = IsoDate(expiresOn),
        location = "pantry shelf",
        status = StockLotStatus.AVAILABLE,
        source = source(),
        confidence = confidence(),
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun source() = Source(
        id = SourceId("source-user"),
        kind = SourceKind.USER,
        label = "Manual entry",
        externalId = null,
        uri = null,
        capturedAt = IsoTimestamp("2026-07-16T10:00:00Z"),
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun confidence() = Confidence(
        score = 1.0,
        state = TruthState.USER_CONFIRMED,
        rationale = "test",
    )

    private class SequenceIds : FoodCommandIdProvider {
        private var next = 0
        override fun newId(prefix: String): String {
            next += 1
            return "$prefix-$next"
        }
    }
}
