package com.wonderfood.app.sync

import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotionGatewayTest {
    @Test
    fun homeScaffoldBodyCreatesReadableV4WorkspaceSections() {
        val body = NotionGateway().homeScaffoldBody(
            databaseTitles = listOf(
                "WonderFood Home",
                "WonderFood Kitchen",
                "WonderFood Help & Setup",
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
        assertTrue(children.toString().contains("Daily dashboard"))
        assertTrue(children.toString().contains("Formula and rollup columns"))
        assertFalse(children.toString().contains("Everyday databases"))
        assertFalse(children.toString().contains("Useful views"))
        assertFalse(children.toString().contains("Setup safety"))
        assertFalse(children.toString().contains("integration token"))
        assertFalse(children.toString().contains("raw snapshot"))
    }

    @Test
    fun v4SourcesCreateFreshDataSourceContainersWithoutVisibleInternalIds() {
        val gateway = NotionGateway()
        val sources = gateway.v4Sources()
        val ingredients = sources.single { it.surface == WorkspaceGraphSurface.INGREDIENTS }
        val bindings = sources.single { it.surface == WorkspaceGraphSurface.BINDINGS }

        val body = gateway.databaseContainerCreateBody("page-1", WorkspaceGraphSurface.KITCHEN)
        val bindingsBody = gateway.databaseContainerCreateBody("page-1", WorkspaceGraphSurface.BINDINGS)
        val initialProperties = body.getJSONObject("initial_data_source").getJSONObject("properties")
        val bindingProperties = bindingsBody.getJSONObject("initial_data_source").getJSONObject("properties")

        assertEquals("2025-09-03", gateway.notionVersionHeader())
        assertEquals("page-1", body.getJSONObject("parent").getString("page_id"))
        assertEquals("WonderFood Kitchen", notionPlain(body.getJSONArray("title").getJSONObject(0)))
        assertTrue(sources.none { it.surface == WorkspaceGraphSurface.HOME })
        assertTrue(initialProperties.getJSONObject("Item").has("title"))
        assertTrue(initialProperties.getJSONObject("On hand").has("number"))
        assertTrue(initialProperties.getJSONObject("Unit").has("select"))
        assertFalse(initialProperties.has("Ingredients"))
        assertFalse(initialProperties.has("Stock lots"))
        assertFalse(initialProperties.has("Low stock"))
        assertFalse(initialProperties.has("identifier"))
        assertFalse(initialProperties.has("Canonical ID"))
        assertEquals("WonderFood Recipe Ingredients", ingredients.title)
        assertEquals("WonderFood Bindings", bindings.title)
        assertTrue(bindings.primaryNavigation.not())
        assertTrue(bindingProperties.getJSONObject("Canonical ID").has("rich_text"))
        assertTrue(bindingProperties.getJSONObject("Page ID").has("rich_text"))
    }

    @Test
    fun v4SecondPassInstallsRelationsRollupsAndFormulasByDataSourceId() {
        val gateway = NotionGateway()
        val dataSourceIds = WorkspaceGraphSurface.entries.associateWith { surface -> "ds-${surface.key}" }

        val kitchenPatch = gateway.dataSourceRelationFormulaPatchBody(WorkspaceGraphSurface.KITCHEN, dataSourceIds)
        val ingredientPatch = gateway.dataSourceRelationFormulaPatchBody(WorkspaceGraphSurface.INGREDIENTS, dataSourceIds)
        val mealPatch = gateway.dataSourceRelationFormulaPatchBody(WorkspaceGraphSurface.MEALS, dataSourceIds)
        val shoppingPatch = gateway.dataSourceRelationFormulaPatchBody(WorkspaceGraphSurface.SHOPPING, dataSourceIds)
        val spendingPatch = gateway.dataSourceRelationFormulaPatchBody(WorkspaceGraphSurface.SPENDING, dataSourceIds)
        val purchaseLinePatch = gateway.dataSourceRelationFormulaPatchBody(WorkspaceGraphSurface.PURCHASE_LINES, dataSourceIds)
        val kitchenProperties = kitchenPatch.getJSONObject("properties")
        val ingredientProperties = ingredientPatch.getJSONObject("properties")
        val mealProperties = mealPatch.getJSONObject("properties")
        val shoppingProperties = shoppingPatch.getJSONObject("properties")
        val spendingProperties = spendingPatch.getJSONObject("properties")
        val purchaseLineProperties = purchaseLinePatch.getJSONObject("properties")

        assertEquals(
            "ds-ingredients",
            kitchenProperties.getJSONObject("Ingredients")
                .getJSONObject("relation")
                .getString("data_source_id"),
        )
        val lowStockFormula = kitchenProperties.getJSONObject("Low stock").getJSONObject("formula").getString("expression")
        assertTrue(lowStockFormula.contains("On hand"))
        assertTrue(lowStockFormula.contains("Low at"))
        assertEquals(
            "ds-recipes",
            ingredientProperties.getJSONObject("Recipe")
                .getJSONObject("relation")
                .getString("data_source_id"),
        )
        assertEquals(
            "sum",
            ingredientProperties.getJSONObject("On hand")
                .getJSONObject("rollup")
                .getString("function"),
        )
        assertEquals("show_original", ingredientProperties.getJSONObject("Kitchen unit").getJSONObject("rollup").getString("function"))
        val ingredientStatusFormula = ingredientProperties.getJSONObject("Status").getJSONObject("formula").getString("expression")
        val ingredientMissingFormula = ingredientProperties.getJSONObject("Missing amount").getJSONObject("formula").getString("expression")
        assertTrue(ingredientStatusFormula.contains("Unlinked"))
        assertTrue(ingredientMissingFormula.contains("Amount"))
        assertTrue(ingredientMissingFormula.contains("Kitchen item"))
        assertEquals("show_original", mealProperties.getJSONObject("Recipe readiness").getJSONObject("rollup").getString("function"))
        assertTrue(mealProperties.getJSONObject("Missing items").getJSONObject("formula").getString("expression").contains("linked Recipe"))
        assertEquals("sum", shoppingProperties.getJSONObject("On hand").getJSONObject("rollup").getString("function"))
        assertEquals("show_original", shoppingProperties.getJSONObject("Kitchen unit").getJSONObject("rollup").getString("function"))
        assertEquals("show_original", purchaseLineProperties.getJSONObject("Currency").getJSONObject("rollup").getString("function"))
        assertTrue(purchaseLineProperties.getJSONObject("Food amount component").getJSONObject("formula").getString("expression").contains("Category"))
        assertTrue(purchaseLineProperties.getJSONObject("Non-food amount component").getJSONObject("formula").getString("expression").contains("Subtotal"))
        assertEquals("sum", spendingProperties.getJSONObject("Food amount").getJSONObject("rollup").getString("function"))
        assertEquals("sum", spendingProperties.getJSONObject("Non-food amount").getJSONObject("rollup").getString("function"))
        assertEquals("Food amount component", spendingProperties.getJSONObject("Food amount").getJSONObject("rollup").getString("rollup_property_name"))
        assertEquals("Non-food amount component", spendingProperties.getJSONObject("Non-food amount").getJSONObject("rollup").getString("rollup_property_name"))
        assertFalse(shoppingPatch.toString().contains("prop(\"Kitchen item\")"))
    }

    @Test
    fun v4DatabaseAndPageRequestsUseDataSourceIdsForRows() {
        val gateway = NotionGateway()
        val databaseResponse = JSONObject()
            .put("id", "database-1")
            .put("data_sources", JSONArray().put(JSONObject().put("id", "data-source-1").put("name", "Kitchen")))
        val page = NotionGraphPage(
            surface = WorkspaceGraphSurface.KITCHEN,
            databaseTitle = "WonderFood Kitchen",
            canonicalId = "item-1",
            properties = mapOf("Item" to JSONObject().put("title", JSONArray().put(notionText("Rice")))),
        )
        val createBody = gateway.graphPageCreateBody("data-source-1", page)

        assertEquals("data-source-1", gateway.databaseDataSourceId(databaseResponse))
        assertTrue(gateway.dataSourceQueryUrl("data-source-1").endsWith("/data_sources/data-source-1/query"))
        assertEquals("data-source-1", createBody.getJSONObject("parent").getString("data_source_id"))
        assertFalse(createBody.getJSONObject("parent").has("database_id"))
    }

    @Test
    fun structuredGraphPagesWriteBaseRowsBeforeRelationPageIdsAndNeverExposeCanonicalIds() {
        val projection = WorkspaceGraphProjection(
            schemaVersion = WORKSPACE_GRAPH_SCHEMA_VERSION,
            householdId = "household-1",
            defaultCurrency = "USD",
            timezone = "UTC",
            locale = "en-US",
            schemas = WorkspaceGraphContract.schemas,
            rows = WorkspaceGraphSurface.entries.associateWith { surface ->
                when (surface) {
                    WorkspaceGraphSurface.KITCHEN -> listOf(
                        WorkspaceGraphRow(
                            surface = surface,
                            canonicalId = "kitchen-item-1",
                            revision = 7,
                            archived = false,
                            updatedAt = 1_785_000_000_000,
                            values = mapOf(
                                "item" to WorkspaceGraphValue.Text("Basmati Rice"),
                                "on_hand" to WorkspaceGraphValue.Decimal(BigDecimal("2")),
                                "unit" to WorkspaceGraphValue.Text("cup"),
                                "ingredients" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.INGREDIENTS, listOf("ingredient-1")),
                            ),
                        ),
                    )
                    WorkspaceGraphSurface.INGREDIENTS -> listOf(
                        WorkspaceGraphRow(
                            surface = surface,
                            canonicalId = "ingredient-1",
                            revision = 2,
                            archived = false,
                            updatedAt = 1_785_000_000_000,
                            values = mapOf(
                                "ingredient" to WorkspaceGraphValue.Text("Basmati Rice"),
                                "amount" to WorkspaceGraphValue.Decimal(BigDecimal("1")),
                                "unit" to WorkspaceGraphValue.Text("cup"),
                                "recipe" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.RECIPES, listOf("recipe-1")),
                                "kitchen_item" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.KITCHEN, listOf("kitchen-item-1")),
                            ),
                        ),
                    )
                    WorkspaceGraphSurface.RECIPES -> listOf(
                        WorkspaceGraphRow(
                            surface = surface,
                            canonicalId = "recipe-1",
                            revision = 3,
                            archived = false,
                            updatedAt = 1_785_000_000_000,
                            values = mapOf(
                                "recipe" to WorkspaceGraphValue.Text("Spinach Rice Bowl"),
                                "ingredients" to WorkspaceGraphValue.Relation(WorkspaceGraphSurface.INGREDIENTS, listOf("ingredient-1")),
                            ),
                        ),
                    )
                    else -> emptyList()
                }
            },
        )

        val basePages = NotionGateway().structuredGraphPages(projection, emptyMap(), includeRelations = false)
        val linkedPages = NotionGateway().structuredGraphPages(
            projection,
            pageIdsByCanonicalId = mapOf("ingredient-1" to "page-a", "kitchen-item-1" to "page-b", "recipe-1" to "page-c"),
        )
        val kitchenBase = basePages.single { it.canonicalId == "kitchen-item-1" }
        val ingredientLinked = linkedPages.single { it.canonicalId == "ingredient-1" }
        val serialized = linkedPages.joinToString("\n") { JSONObject(it.properties).toString() }

        assertFalse(kitchenBase.properties.containsKey("Ingredients"))
        assertEquals("Basmati Rice", notionTitleValue(ingredientLinked.properties.getValue("Ingredient") as JSONObject))
        assertEquals(1.0, (ingredientLinked.properties.getValue("Amount") as JSONObject).getDouble("number"), 0.0)
        assertEquals(
            "page-b",
            (ingredientLinked.properties.getValue("Kitchen item") as JSONObject)
                .getJSONArray("relation")
                .getJSONObject(0)
                .getString("id"),
        )
        assertFalse(serialized.contains("legacy:"))
        assertFalse(serialized.contains("kitchen-item-1"))
        assertFalse(serialized.contains("ingredient-1"))
        assertFalse(serialized.contains("revision", ignoreCase = true))
        assertFalse(serialized.contains("identifier", ignoreCase = true))
    }

    private fun notionText(value: String): JSONObject =
        JSONObject()
            .put("type", "text")
            .put("text", JSONObject().put("content", value))
            .put("plain_text", value)

    private fun notionPlain(value: JSONObject): String =
        value.optString("plain_text").ifBlank {
            value.optJSONObject("text")?.optString("content").orEmpty()
        }

    private fun notionTitleValue(value: JSONObject): String =
        value.getJSONArray("title")
            .getJSONObject(0)
            .let(::notionPlain)

}
