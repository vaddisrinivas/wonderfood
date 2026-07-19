package com.wonderfood.core.data.room

import androidx.test.core.app.ApplicationProvider
import com.wonderfood.core.data.backend.BackendHealthStatus
import com.wonderfood.core.data.backend.BackendType
import com.wonderfood.core.data.backend.ConnectionResult
import com.wonderfood.core.data.backend.LocalSqliteConfig
import com.wonderfood.core.data.backend.summary
import com.wonderfood.core.model.Confidence
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.Food
import com.wonderfood.core.model.FoodAlias
import com.wonderfood.core.model.FoodAliasId
import com.wonderfood.core.model.FoodId
import com.wonderfood.core.model.FoodStatus
import com.wonderfood.core.model.FoodUnit
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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalSqliteBackendTest {
    private lateinit var database: WonderFoodDatabase
    private lateinit var dao: WonderFoodDao
    private lateinit var backend: LocalSqliteBackend

    @Before
    fun setUp() {
        database = WonderFoodDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        database.openHelper.writableDatabase.setForeignKeyConstraintsEnabled(true)
        dao = database.wonderFoodDao()
        backend = LocalSqliteBackend(database) { "2026-07-18T00:00:00Z" }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun connectMarksLocalBackendReadyAndSummarizesExistingKitchenData() = runTest {
        insertFoodGraph()

        val result = backend.connect(LocalSqliteConfig())
        val health = backend.healthCheck()

        assertTrue(result is ConnectionResult.Connected)
        assertEquals(BackendType.LOCAL_SQLITE, (result as ConnectionResult.Connected).descriptor.type)
        assertEquals(1, result.snapshotSummary.foods)
        assertEquals(1, result.snapshotSummary.stockLots)
        assertEquals(BackendHealthStatus.READY, health.status)
    }

    @Test
    fun bootstrapExportsCurrentLocalKitchenSnapshot() = runTest {
        insertFoodGraph()

        val snapshot = backend.bootstrap()

        assertEquals(1, snapshot.summary().foods)
        assertEquals("Greek yogurt", snapshot.foods.single().name)
        assertEquals("Yogurt", snapshot.foodAliases.single().name)
        assertEquals("fridge", snapshot.stockLots.single().location)
        assertEquals(PageKind.FOOD, snapshot.pages.single().kind)
    }

    @Test
    fun disconnectMarksHealthDisconnected() = runTest {
        backend.connect(LocalSqliteConfig())
        backend.disconnect()

        assertEquals(BackendHealthStatus.DISCONNECTED, backend.healthCheck().status)
    }

    private suspend fun insertFoodGraph() {
        dao.upsertFoodWithPage(
            page = page().toEntity(TIMESTAMP, TIMESTAMP),
            food = food().toEntity(TIMESTAMP, TIMESTAMP),
            aliases = listOf(alias().toEntity(TIMESTAMP, TIMESTAMP)),
            lots = listOf(stockLot().toEntity(TIMESTAMP, TIMESTAMP)),
        )
    }

    private fun page() = Page(
        id = PageId("page-yogurt"),
        title = "Greek yogurt",
        kind = PageKind.FOOD,
        entity = EntityRef(EntityType.FOOD, "food-yogurt"),
        aliases = emptyList(),
        relationIds = emptyList(),
        attachmentIds = emptyList(),
        truthState = TruthState.USER_CONFIRMED,
        source = source(),
        confidence = confidence(),
    )

    private fun food() = Food(
        id = FoodId("food-yogurt"),
        pageId = PageId("page-yogurt"),
        name = "Greek yogurt",
        status = FoodStatus.ACTIVE,
        aliasIds = listOf(FoodAliasId("alias-yogurt")),
        stockLotIds = listOf(StockLotId("lot-yogurt")),
        nutritionSnapshotIds = emptyList(),
        attachmentIds = emptyList(),
        source = source(),
        confidence = confidence(),
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun alias() = FoodAlias(
        id = FoodAliasId("alias-yogurt"),
        foodId = FoodId("food-yogurt"),
        name = "Yogurt",
        locale = "en",
        source = source(),
        confidence = confidence(),
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun stockLot() = StockLot(
        id = StockLotId("lot-yogurt"),
        foodId = FoodId("food-yogurt"),
        quantity = Quantity(2.0, FoodUnit.EACH, TruthState.USER_CONFIRMED),
        purchasedOn = null,
        expiresOn = null,
        location = "fridge",
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
        capturedAt = IsoTimestamp(TIMESTAMP),
        truthState = TruthState.USER_CONFIRMED,
    )

    private fun confidence() = Confidence(1.0, TruthState.USER_CONFIRMED, "Confirmed by user")

    private companion object {
        const val TIMESTAMP = "2026-07-18T00:00:00Z"
    }
}
