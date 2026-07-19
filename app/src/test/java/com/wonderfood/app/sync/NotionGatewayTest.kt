package com.wonderfood.app.sync

import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotionGatewayTest {
    @Test
    fun snapshotAppendBodyCreatesSummaryAndChunkedJsonBlocks() {
        val body = NotionGateway().snapshotAppendBody(
            snapshot = emptySnapshot(),
            updatedAt = "2026-07-19T12:00:00Z",
            chunks = listOf("""{"schemaVersion":1}"""),
        )

        val children = body.getJSONArray("children")

        assertEquals(3, children.length())
        assertEquals("heading_2", children.getJSONObject(0).getString("type"))
        assertEquals("paragraph", children.getJSONObject(1).getString("type"))
        assertEquals("code", children.getJSONObject(2).getString("type"))
        assertEquals(
            "json",
            children.getJSONObject(2)
                .getJSONObject("code")
                .getString("language"),
        )
        assertTrue(
            children.getJSONObject(1)
                .getJSONObject("paragraph")
                .getJSONArray("rich_text")
                .getJSONObject(0)
                .getJSONObject("text")
                .getString("content")
                .contains("Schema v1"),
        )
    }

    @Test
    fun parseRemoteSnapshotReturnsLatestCompleteSnapshotGroup() {
        val latest = emptySnapshot()
        val blocks = buildList {
            addAll(snapshotBlocks("2026-07-19T10:00:00Z", WonderFoodSnapshotCodec.encode(emptySnapshot()).chunked(10)))
            addAll(snapshotBlocks("2026-07-19T12:00:00Z", WonderFoodSnapshotCodec.encode(latest).chunked(11)))
        }

        val result = NotionGateway().parseRemoteSnapshot("page-1", blocks)

        assertEquals("page-1", result.pageId)
        assertEquals("2026-07-19T12:00:00Z", result.updatedAt)
        assertEquals(latest, result.snapshot)
    }

    @Test
    fun parseRemoteSnapshotReturnsNoSnapshotWhenChunksAreIncomplete() {
        val blocks = snapshotBlocks("2026-07-19T12:00:00Z", listOf("""{"schemaVersion":1}"""))
            .mapIndexed { index, block ->
                if (index == 2) {
                    block.getJSONObject("code")
                        .put("caption", org.json.JSONArray().put(notionText("WonderFood snapshot part 1 of 2")))
                }
                block
            }

        val result = NotionGateway().parseRemoteSnapshot("page-1", blocks)

        assertEquals(null, result.updatedAt)
        assertEquals(null, result.snapshot)
    }

    @Test
    fun databaseCreateBodyCreatesRealWorkspaceDatabaseProperties() {
        val body = NotionGateway().databaseCreateBody(
            pageId = "page-1",
            database = NotionWorkspaceDatabase(
                title = "WonderFood Kitchen",
                properties = listOf(
                    NotionProperty("identifier", "rich_text"),
                    NotionProperty("Food", "title"),
                    NotionProperty("Pantry state", "select"),
                    NotionProperty("Best by", "date"),
                    NotionProperty("Source", "url"),
                    NotionProperty("Optional", "checkbox"),
                ),
            ),
        )

        val properties = body.getJSONObject("properties")

        assertEquals("page-1", body.getJSONObject("parent").getString("page_id"))
        assertTrue(properties.getJSONObject("identifier").has("rich_text"))
        assertTrue(properties.getJSONObject("Food").has("title"))
        assertTrue(properties.getJSONObject("Pantry state").has("select"))
        assertTrue(properties.getJSONObject("Best by").has("date"))
        assertTrue(properties.getJSONObject("Source").has("url"))
        assertTrue(properties.getJSONObject("Optional").has("checkbox"))
    }

    @Test
    fun homeScaffoldBodyCreatesReadableWorkspaceSections() {
        val body = NotionGateway().homeScaffoldBody(
            databaseTitles = listOf(
                "WonderFood Home",
                "WonderFood Kitchen",
                "WonderFood Recipe Ingredients",
            ),
        )

        val children = body.getJSONArray("children")

        assertEquals("heading_1", children.getJSONObject(0).getString("type"))
        assertEquals(
            "WonderFood Home",
            children.getJSONObject(0)
                .getJSONObject("heading_1")
                .getJSONArray("rich_text")
                .getJSONObject(0)
                .getJSONObject("text")
                .getString("content"),
        )
        assertTrue(children.toString().contains("Everyday databases"))
        assertTrue(children.toString().contains("WonderFood Kitchen"))
        assertTrue(children.toString().contains("Managed data"))
        assertTrue(children.toString().contains("WonderFood Recipe Ingredients"))
    }

    @Test
    fun parseWorkspacePageReadsFriendlyNotionProperties() {
        val table = WonderFoodWorkspaceSchema.tables.first { it.title == WonderFoodWorkspaceSchema.KITCHEN }
        val page = JSONObject()
            .put(
                "properties",
                JSONObject()
                    .put("Food", JSONObject().put("title", JSONArray().put(notionText("Eggs"))))
                    .put("On hand", JSONObject().put("number", 12.0))
                    .put("Unit", JSONObject().put("select", JSONObject().put("name", "item")))
                    .put("Pantry state", JSONObject().put("select", JSONObject().put("name", "Available")))
                    .put("Location", JSONObject().put("select", JSONObject().put("name", "Fridge")))
                    .put("Best by", JSONObject().put("date", JSONObject().put("start", "2026-07-25")))
                    .put("Source", JSONObject().put("rich_text", JSONArray().put(notionText("Manual"))))
                    .put("identifier", JSONObject().put("rich_text", JSONArray().put(notionText("lot-eggs")))),
            )

        val row = NotionGateway().parseWorkspacePage(table, page)

        requireNotNull(row)
        assertEquals(WonderFoodWorkspaceSchema.KITCHEN, row.tab)
        assertEquals("lot-eggs", row.identifier)
        assertEquals("Eggs", row.values["Food"])
        assertEquals("12", row.values["On hand"])
        assertEquals("item", row.values["Unit"])
        assertEquals("Fridge", row.values["Location"])
        assertEquals("2026-07-25", row.values["Best by"])
    }

    @Test
    fun mergeWorkspaceRowsAppliesFriendlyNotionRowsToBaseSnapshot() {
        val base = WonderFoodWorkspaceSeedFixture.snapshot()
        val kitchenId = base.stockLots.first().id.value
        val result = NotionGateway().mergeWorkspaceRows(
            pageId = "page-1",
            baseSnapshot = base,
            updatedAt = "2026-07-19T15:00:00Z",
            rows = listOf(
                GoogleSheetsWorkspaceRow(
                    tab = WonderFoodWorkspaceSchema.KITCHEN,
                    identifier = kitchenId,
                    values = mapOf(
                        "Food" to "Notion rice",
                        "On hand" to "7",
                        "Unit" to "kg",
                        "identifier" to kitchenId,
                    ),
                ),
            ),
        )

        val merge = requireNotNull(result.merge)
        val stockLot = merge.snapshot.stockLots.first { it.id.value == kitchenId }
        val food = merge.snapshot.foods.first { it.id == stockLot.foodId }

        assertEquals("page-1", result.pageId)
        assertEquals(1, result.rowCount)
        assertEquals("Notion rice", food.name)
        assertEquals(7.0, stockLot.quantity.amount)
        assertEquals("2026-07-19T15:00:00Z", merge.mergeClock)
        assertTrue(merge.fieldClocks.isNotEmpty())
    }

    private fun snapshotBlocks(updatedAt: String, chunks: List<String>): List<JSONObject> =
        NotionGateway().snapshotAppendBody(
            snapshot = emptySnapshot(),
            updatedAt = updatedAt,
            chunks = chunks,
        ).getJSONArray("children").let { children ->
            List(children.length()) { index -> children.getJSONObject(index) }
        }

    private fun notionText(value: String): JSONObject =
        JSONObject()
            .put("type", "text")
            .put("text", JSONObject().put("content", value))
            .put("plain_text", value)

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
}
