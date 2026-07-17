package com.wonderfood.core.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WonderFoodMigrationInstrumentedTest {
    private lateinit var context: Context
    private val databaseNames = mutableSetOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        databaseNames.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun versionOneFixtureMigratesToCurrentSchemaWithoutDataLoss() = runTest {
        val name = databaseName("v1-to-v2")
        val expected = createCurrentFixtureThenMarkAsVersion(name, version = 1)

        val database = WonderFoodDatabaseFactory.create(context, name)
        try {
            val readable = database.openHelper.readableDatabase
            assertEquals(2, readable.userVersion())
            assertEquals(expected, rowCounts(readable, expected.keys))
            assertNoForeignKeyViolations(readable)
            assertIndexExists(readable, "foods", "index_foods_page_id")
            assertIndexExists(readable, "food_actions", "index_food_actions_idempotency_key")

            val dao = database.wonderFoodDao()
            assertEquals("Greek yogurt", dao.observeFood("food-yogurt").first()?.name)
            assertEquals("Yogurt", dao.observeAliasesForFood("food-yogurt").first().single().name)
            assertEquals(1, dao.observeFoodEventsForSubject("FOOD", "food-yogurt").first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun unsupportedFutureVersionFailsWithoutDestructiveFallback() = runTest {
        val name = databaseName("future-version")
        createCurrentFixtureThenMarkAsVersion(name, version = 99)

        val database = WonderFoodDatabaseFactory.create(context, name)
        try {
            database.openHelper.writableDatabase.query("SELECT 1").close()
            fail("Expected unsupported migration failure")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("Migration") || error.message.orEmpty().contains("version"))
        } finally {
            database.close()
        }

        assertEquals(1, rawRowCount(name, "foods"))
    }

    @Test
    fun corruptedSchemaFailsWithoutDeletingFileOrRemainingRows() = runTest {
        val name = databaseName("corrupt-schema")
        createCurrentFixtureThenMarkAsVersion(name, version = 2)
        corruptSchemaByDroppingFoods(name)
        val file = context.getDatabasePath(name)

        val database = WonderFoodDatabaseFactory.create(context, name)
        try {
            database.openHelper.writableDatabase.query("SELECT 1").close()
            fail("Expected corrupt schema open failure")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("Migration") || error.message.orEmpty().contains("schema"))
            assertTrue(file.exists())
            assertEquals(1, rawRowCount(name, "pages"))
        } finally {
            database.close()
        }
    }

    private suspend fun createCurrentFixtureThenMarkAsVersion(name: String, version: Int): Map<String, Int> {
        val database = WonderFoodDatabaseFactory.create(context, name)
        try {
            val dao = database.wonderFoodDao()
            dao.upsertSource(sourceRecord())
            dao.upsertFoodWithPage(
                page = page("page-yogurt", "Greek yogurt"),
                food = food("food-yogurt", "page-yogurt", "Greek yogurt"),
                aliases = listOf(alias("alias-yogurt", "food-yogurt", "Yogurt")),
                lots = listOf(lot("lot-yogurt", "food-yogurt")),
            )
            dao.insertFoodEvent(foodEvent("event-yogurt", "food-yogurt"))
            dao.insertFoodAction(foodAction("action-yogurt", "idem-yogurt", "food-yogurt"))
            val counts = rowCounts(
                database.openHelper.readableDatabase,
                setOf("sources", "pages", "foods", "food_aliases", "stock_lots", "food_events", "food_actions"),
            )
            database.close()
            forceUserVersion(name, version)
            return counts
        } finally {
            if (database.isOpen) database.close()
        }
    }

    private fun databaseName(suffix: String): String =
        "wf-d04-$suffix.db".also {
            databaseNames += it
            context.deleteDatabase(it)
        }

    private fun forceUserVersion(name: String, version: Int) {
        val raw = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            raw.version = version
        } finally {
            raw.close()
        }
    }

    private fun corruptSchemaByDroppingFoods(name: String) {
        val raw = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            raw.execSQL("PRAGMA foreign_keys=OFF")
            raw.execSQL("DROP TABLE foods")
            raw.execSQL("UPDATE room_master_table SET identity_hash = 'corrupted-schema' WHERE id = 42")
        } finally {
            raw.close()
        }
    }

    private fun rawRowCount(name: String, table: String): Int {
        val raw = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            raw.rawQuery("SELECT COUNT(*) FROM `$table`", emptyArray()).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        } finally {
            raw.close()
        }
    }

    private fun rowCounts(database: androidx.sqlite.db.SupportSQLiteDatabase, tables: Set<String>): Map<String, Int> =
        tables.associateWith { table ->
            database.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.userVersion(): Int =
        query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun assertNoForeignKeyViolations(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals("foreign key violations", 0, cursor.count)
        }
    }

    private fun assertIndexExists(database: androidx.sqlite.db.SupportSQLiteDatabase, table: String, index: String) {
        val names = database.query("PRAGMA index_list(`$table`)").use { cursor ->
            buildSet {
                val nameColumn = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }
        assertTrue("$index missing on $table", index in names)
    }

    private fun sourceRecord() = SourceRecordEntity(
        id = "source-user",
        kind = SourceKind.USER,
        label = "Manual entry",
        externalId = null,
        uri = null,
        capturedAt = NOW,
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun page(id: String, title: String) = PageEntity(
        id = id,
        title = title,
        kind = PageKind.FOOD,
        entityType = EntityType.FOOD,
        entityId = "food-yogurt",
        aliases = listOf("Yogurt"),
        relationIds = emptyList(),
        attachmentIds = emptyList(),
        truthState = TruthState.USER_CONFIRMED,
        source = source(),
        confidence = confidence(),
        createdAt = NOW,
        updatedAt = NOW,
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
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun alias(id: String, foodId: String, name: String) = FoodAliasEntity(
        id = id,
        foodId = foodId,
        name = name,
        locale = "en",
        source = source(),
        confidence = confidence(),
        truthState = TruthState.USER_CONFIRMED,
        createdAt = NOW,
        updatedAt = NOW,
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
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun foodEvent(id: String, subjectId: String) = FoodEventEntity(
        id = id,
        subjectType = EntityType.FOOD,
        subjectId = subjectId,
        type = FoodEventType.CREATED,
        occurredAt = NOW,
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
        actionType = "CreateFoodGraph",
        occurredAt = NOW,
        payload = """{"before.exists":false,"after.exists":true}""",
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
        capturedAt = NOW,
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun confidence() = ConfidenceColumns(
        score = 1.0,
        state = TruthState.USER_CONFIRMED,
        rationale = "test",
    )

    private companion object {
        const val NOW = "2026-07-16T10:00:00Z"
    }
}
