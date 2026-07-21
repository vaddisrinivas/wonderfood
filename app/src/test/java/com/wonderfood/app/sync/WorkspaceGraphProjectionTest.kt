package com.wonderfood.app.sync

import com.wonderfood.core.model.household.Confidence
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.Money
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStatus
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceGraphProjectionTest {
    @Test
    fun projectionKeepsIngredientNameQuantityAndRelationsSeparate() {
        val snapshot = fixture()

        val graph = WorkspaceGraphProjector.project(snapshot)
        val ingredient = graph.rows.getValue(WorkspaceGraphSurface.INGREDIENTS).single()

        assertEquals(WORKSPACE_GRAPH_SCHEMA_VERSION, graph.schemaVersion)
        assertEquals("Basmati Rice", ingredient.text("ingredient"))
        assertEquals(BigDecimal.ONE, ingredient.decimal("amount"))
        assertEquals("cup", ingredient.text("unit"))
        assertEquals(
            listOf(RECIPE_ID.value),
            (ingredient.values.getValue("recipe") as WorkspaceGraphValue.Relation).canonicalIds,
        )
        assertEquals(
            listOf(ITEM_ID.value),
            (ingredient.values.getValue("kitchen_item") as WorkspaceGraphValue.Relation).canonicalIds,
        )
        assertFalse(ingredient.text("ingredient").contains("1 cup", ignoreCase = true))
    }

    @Test
    fun unknownAndIncompatibleQuantitiesNeverBecomeZero() {
        val mixed = fixture().copy(
            inventoryLots = listOf(
                lot("00000000-0000-0000-0000-000000000013", Quantity(DecimalAmount.of("1"), QuantityUnit.CUP)),
                lot("00000000-0000-0000-0000-000000000014", Quantity(DecimalAmount.of("1"), QuantityUnit.EACH)),
            ),
        )

        val kitchen = WorkspaceGraphProjector.project(mixed).rows.getValue(WorkspaceGraphSurface.KITCHEN).single()

        assertNull(kitchen.values["on_hand"])
        assertEquals("cup", kitchen.text("unit"))
    }

    @Test
    fun supportedUnitsAndMoneyPrecisionAreExplicit() {
        assertTrue(listOf("teaspoon", "tablespoon", "fluid_ounce", "pound", "clove") allIn WorkspaceGraphContract.supportedUnits)
        assertEquals(BigDecimal("8.99"), Money(899, "USD").toWorkspaceMajorUnits())
        assertEquals(BigDecimal("899"), Money(899, "JPY").toWorkspaceMajorUnits())
    }

    private fun fixture(): HouseholdSnapshot = HouseholdSnapshot(
        household = Household(
            id = HOUSEHOLD_ID,
            name = "WonderFood Home",
            defaultCurrency = "USD",
            timezone = "America/New_York",
            locale = "en-US",
            activeDataHome = DataHomeKind.LOCAL,
            schemaVersion = 1,
            createdAt = NOW,
            updatedAt = NOW,
            revision = 1,
        ),
        items = listOf(
            Item(
                metadata = metadata(ITEM_ID),
                name = "Basmati Rice",
                kind = ItemKind.FOOD,
                defaultUnit = QuantityUnit.CUP,
            ),
        ),
        inventoryLots = listOf(lot("00000000-0000-0000-0000-000000000012", Quantity(DecimalAmount.of("2"), QuantityUnit.CUP))),
        recipes = listOf(
            Recipe(
                metadata = metadata(RECIPE_ID),
                name = "Spinach Rice Bowl",
                yield = Quantity(DecimalAmount.of("2"), QuantityUnit.SERVING),
                status = RecipeStatus.ACTIVE,
                ingredientIds = listOf(INGREDIENT_ID),
            ),
        ),
        recipeIngredients = listOf(
            RecipeIngredient(
                metadata = metadata(INGREDIENT_ID),
                recipeId = RECIPE_ID,
                itemId = ITEM_ID,
                originalText = "1 cup Basmati Rice",
                quantity = Quantity(DecimalAmount.of("1"), QuantityUnit.CUP),
                order = 0,
            ),
        ),
    )

    private fun lot(id: String, quantity: Quantity) = InventoryLot(
        metadata = metadata(EntityId(id)),
        itemId = ITEM_ID,
        quantity = quantity,
    )

    private fun metadata(id: EntityId) = EntityMetadata(
        id = id,
        householdId = HOUSEHOLD_ID,
        createdAt = NOW,
        updatedAt = NOW,
        revision = 1,
        source = SourceRef(SourceKind.MANUAL, "test"),
        confidence = Confidence(10_000),
    )

    private fun WorkspaceGraphRow.text(key: String): String = (values.getValue(key) as WorkspaceGraphValue.Text).value
    private fun WorkspaceGraphRow.decimal(key: String): BigDecimal = (values.getValue(key) as WorkspaceGraphValue.Decimal).value

    private infix fun <T> Iterable<T>.allIn(other: Collection<T>): Boolean = all { it in other }

    companion object {
        private val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000001")
        private val ITEM_ID = EntityId("00000000-0000-0000-0000-000000000002")
        private val RECIPE_ID = EntityId("00000000-0000-0000-0000-000000000003")
        private val INGREDIENT_ID = EntityId("00000000-0000-0000-0000-000000000004")
        private val NOW = UtcTimestamp(1_784_505_600_000L)
    }
}
