package com.wonderfood.core.data.room

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.FoodEventType
import com.wonderfood.core.model.FoodStatus
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.PageKind
import com.wonderfood.core.model.SourceKind
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.TruthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WonderFoodDatabaseTest {
    private lateinit var database: WonderFoodDatabase
    private lateinit var dao: WonderFoodDao

    @Before
    fun setUp() {
        database = WonderFoodDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        database.openHelper.writableDatabase.setForeignKeyConstraintsEnabled(true)
        dao = database.wonderFoodDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndObserveFoodGraphRoundTrips() = runTest {
        dao.upsertFoodWithPage(
            page = page("page-yogurt", "Greek yogurt", PageKind.FOOD),
            food = food("food-yogurt", "page-yogurt", "Greek yogurt"),
            aliases = listOf(alias("alias-yogurt", "food-yogurt", "Yogurt")),
            lots = listOf(lot("lot-yogurt", "food-yogurt")),
        )

        val observedFood = dao.observeFood("food-yogurt").first()
        val observedAliases = dao.observeAliasesForFood("food-yogurt").first()
        val observedLots = dao.observeStockLotsForFood("food-yogurt").first()

        assertEquals("Greek yogurt", observedFood?.name)
        assertEquals(listOf("alias-yogurt"), observedFood?.aliasIds)
        assertEquals("Yogurt", observedAliases.single().name)
        assertEquals(2.0, observedLots.single().quantity.amount ?: -1.0, 0.0)
    }

    @Test
    fun foreignKeysAndUniqueConstraintsRejectInvalidRows() = runTest {
        assertConstraintFails {
            dao.insertFood(food("food-orphan", "missing-page", "Orphan food"))
        }

        dao.upsertPage(page("page-yogurt", "Greek yogurt", PageKind.FOOD))
        dao.upsertFood(food("food-yogurt", "page-yogurt", "Greek yogurt"))
        dao.insertFoodAlias(alias("alias-yogurt-1", "food-yogurt", "Yogurt", locale = "en"))

        assertConstraintFails {
            dao.insertFoodAlias(alias("alias-yogurt-2", "food-yogurt", "Yogurt", locale = "en"))
        }
    }

    @Test
    fun archiveMarksRowsWithoutHardDeleting() = runTest {
        dao.upsertPage(page("page-yogurt", "Greek yogurt", PageKind.FOOD))
        dao.upsertFood(food("food-yogurt", "page-yogurt", "Greek yogurt"))

        val archived = dao.archiveFood("food-yogurt", "2026-07-16T12:00:00Z")
        val observed = dao.observeFood("food-yogurt").first()

        assertEquals(1, archived)
        assertEquals(FoodStatus.ARCHIVED, observed?.status)
        assertEquals("2026-07-16T12:00:00Z", observed?.archivedAt)
        assertNotNull(dao.observeFoods().first().singleOrNull { it.id == "food-yogurt" })
    }

    @Test
    fun foodEventsAndActionsAreAppendOnly() = runTest {
        val event = foodEvent("event-create", "food-yogurt")
        val action = foodAction("action-create", "idem-create", "food-yogurt")

        dao.insertFoodEvent(event)
        dao.insertFoodAction(action)

        assertConstraintFails { dao.insertFoodEvent(event.copy(note = "duplicate id")) }
        assertConstraintFails { dao.insertFoodAction(action.copy(id = "action-create-2")) }

        assertEquals(listOf(event), dao.observeFoodEventsForSubject("FOOD", "food-yogurt").first())
        assertEquals(listOf(action), dao.observeFoodActionsForSubject("FOOD", "food-yogurt").first())
    }

    @Test
    fun schemaHasExpectedTablesAndIndexes() {
        val tables = database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        val foodIndexes = indexNames("foods")
        val relationIndexes = indexNames("relations")

        assertTrue("pages missing", "pages" in tables)
        assertTrue("foods missing", "foods" in tables)
        assertTrue("food_events missing", "food_events" in tables)
        assertTrue("food_actions missing", "food_actions" in tables)
        assertTrue("foods page FK index missing", "index_foods_page_id" in foodIndexes)
        assertTrue("relations from index missing", "index_relations_from_type_from_id" in relationIndexes)
    }

    private fun indexNames(table: String): Set<String> =
        database.openHelper.readableDatabase.query("PRAGMA index_list(`$table`)").use { cursor ->
            buildSet {
                val nameColumn = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }

    private suspend fun assertConstraintFails(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected SQLiteConstraintException")
        } catch (_: SQLiteConstraintException) {
            // Expected.
        }
    }

    private fun page(id: String, title: String, kind: PageKind) = PageEntity(
        id = id,
        title = title,
        kind = kind,
        entityType = EntityType.FOOD,
        entityId = "food-yogurt",
        aliases = emptyList(),
        relationIds = emptyList(),
        attachmentIds = emptyList(),
        truthState = TruthState.USER_CONFIRMED,
        source = source(),
        confidence = confidence(),
        createdAt = "2026-07-16T10:00:00Z",
        updatedAt = "2026-07-16T10:00:00Z",
    )

    private fun food(id: String, pageId: String, name: String) = FoodEntity(
        id = id,
        pageId = pageId,
        name = name,
        status = FoodStatus.ACTIVE,
        aliasIds = listOf("alias-yogurt"),
        stockLotIds = listOf("lot-yogurt"),
        nutritionSnapshotIds = emptyList(),
        attachmentIds = emptyList(),
        source = source(),
        confidence = confidence(),
        truthState = TruthState.USER_CONFIRMED,
        createdAt = "2026-07-16T10:00:00Z",
        updatedAt = "2026-07-16T10:00:00Z",
    )

    private fun alias(id: String, foodId: String, name: String, locale: String? = null) = FoodAliasEntity(
        id = id,
        foodId = foodId,
        name = name,
        locale = locale,
        source = source(),
        confidence = confidence(),
        truthState = TruthState.USER_CONFIRMED,
        createdAt = "2026-07-16T10:00:00Z",
        updatedAt = "2026-07-16T10:00:00Z",
    )

    private fun lot(id: String, foodId: String) = StockLotEntity(
        id = id,
        foodId = foodId,
        quantity = quantity(),
        purchasedOn = "2026-07-16",
        expiresOn = "2026-07-23",
        location = "fridge",
        status = StockLotStatus.AVAILABLE,
        source = source(),
        confidence = confidence(),
        truthState = TruthState.USER_CONFIRMED,
        createdAt = "2026-07-16T10:00:00Z",
        updatedAt = "2026-07-16T10:00:00Z",
    )

    private fun foodEvent(id: String, subjectId: String) = FoodEventEntity(
        id = id,
        subjectType = EntityType.FOOD,
        subjectId = subjectId,
        type = FoodEventType.CREATED,
        occurredAt = "2026-07-16T10:00:00Z",
        quantity = quantity(),
        note = "created",
        source = source(),
        confidence = confidence(),
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun foodAction(id: String, idempotencyKey: String, subjectId: String) = FoodActionEntity(
        id = id,
        idempotencyKey = idempotencyKey,
        subjectType = EntityType.FOOD,
        subjectId = subjectId,
        actionType = "CreateFood",
        occurredAt = "2026-07-16T10:00:00Z",
        payload = """{"name":"Greek yogurt"}""",
        source = source(),
        confidence = confidence(),
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun quantity() = QuantityColumns(
        amount = 2.0,
        unit = FoodUnit.EACH,
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun source() = SourceColumns(
        id = "source-user",
        kind = SourceKind.USER,
        label = "Manual entry",
        externalId = null,
        uri = null,
        capturedAt = "2026-07-16T10:00:00Z",
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun confidence() = ConfidenceColumns(
        score = 1.0,
        state = TruthState.USER_CONFIRMED,
        rationale = "test",
    )
}
