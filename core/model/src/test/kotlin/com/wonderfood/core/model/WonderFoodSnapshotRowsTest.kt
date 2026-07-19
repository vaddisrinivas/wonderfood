package com.wonderfood.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WonderFoodSnapshotRowsTest {
    @Test
    fun emptySnapshotAlwaysExportsMetaRow() {
        val snapshot = emptySnapshot()

        val rows = WonderFoodSnapshotCodec.rows(snapshot, "2026-07-18T12:00:00Z")

        assertEquals(listOf("_meta"), rows.map { it.tab })
        assertEquals("snapshot", rows.single().id)
        assertNotNull(WonderFoodSnapshotCodec.decodeSnapshotRow(rows.single()))
    }

    @Test
    fun foodSnapshotExportsEntityRows() {
        val food = Food(
            id = FoodId("food-rice"),
            pageId = PageId("page-rice"),
            name = "Rice",
            status = FoodStatus.ACTIVE,
            aliasIds = emptyList(),
            stockLotIds = emptyList(),
            nutritionSnapshotIds = emptyList(),
            attachmentIds = emptyList(),
            source = source(),
            confidence = Confidence.UNKNOWN,
            truthState = TruthState.USER_CONFIRMED,
        )
        val snapshot = emptySnapshot().copy(foods = listOf(food))

        val rows = WonderFoodSnapshotCodec.rows(snapshot, "2026-07-18T12:00:00Z")

        assertTrue(rows.any { it.tab == "foods" && it.id == "food-rice" })
        assertTrue(rows.first { it.tab == "foods" }.payloadJson.contains("Rice"))
    }

    private fun emptySnapshot() = WonderFoodSnapshot(
        schemaVersion = WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION,
        pages = emptyList(),
        foods = emptyList(),
        foodAliases = emptyList(),
        stockLots = emptyList(),
        nutritionSnapshots = emptyList(),
        recipes = emptyList(),
        mealPlans = emptyList(),
        mealLogs = emptyList(),
        shoppingItems = emptyList(),
        receipts = emptyList(),
        foodEvents = emptyList(),
        relations = emptyList(),
        attachments = emptyList(),
    )

    private fun source() = Source(
        id = SourceId("source-test"),
        kind = SourceKind.USER,
        label = "Test",
        externalId = null,
        uri = null,
        capturedAt = null,
        truthState = TruthState.USER_CONFIRMED,
    )
}
