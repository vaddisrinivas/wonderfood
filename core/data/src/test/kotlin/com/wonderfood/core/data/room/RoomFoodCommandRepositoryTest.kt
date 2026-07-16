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
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.Page
import com.wonderfood.core.model.PageId
import com.wonderfood.core.model.PageKind
import com.wonderfood.core.model.Source
import com.wonderfood.core.model.SourceId
import com.wonderfood.core.model.SourceKind
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
