package com.wonderfood.app.data

import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.CalendarDate
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStatus
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalRecipeMatchItemTest {
    @Test
    fun ranksCanonicalRecipesFromKitchenInventory() {
        val rice = item("00000000-0000-0000-0000-000000000201", "Rice")
        val lentils = item("00000000-0000-0000-0000-000000000202", "Lentils")
        val tamarind = item("00000000-0000-0000-0000-000000000203", "Tamarind")
        val rasam = recipe("00000000-0000-0000-0000-000000000301", "Tomato rasam", 25)
        val pulao = recipe("00000000-0000-0000-0000-000000000302", "Vegetable pulao", 35)
        val stew = recipe("00000000-0000-0000-0000-000000000304", "Bean stew", 40)
        val chutney = recipe("00000000-0000-0000-0000-000000000305", "Tamarind chutney", 15)
        val archived = recipe("00000000-0000-0000-0000-000000000303", "Archived dinner", 10, RecipeStatus.ARCHIVED)

        val matches = CanonicalRecipeMatchItem.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                items = listOf(rice, lentils, tamarind),
                inventoryLots = listOf(
                    lot("00000000-0000-0000-0000-000000000401", rice.metadata.id, quantity = "2", expiresOn = "2026-07-21"),
                    lot("00000000-0000-0000-0000-000000000402", lentils.metadata.id, quantity = "0.5", expiresOn = "2026-07-25"),
                ),
                recipes = listOf(rasam, pulao, stew, chutney, archived),
                recipeIngredients = listOf(
                    ingredient("00000000-0000-0000-0000-000000000501", rasam.metadata.id, lentils.metadata.id, "lentils", 0),
                    ingredient("00000000-0000-0000-0000-000000000502", rasam.metadata.id, tamarind.metadata.id, "tamarind", 1),
                    ingredient("00000000-0000-0000-0000-000000000503", pulao.metadata.id, rice.metadata.id, "rice", 0, quantity = "1"),
                    ingredient("00000000-0000-0000-0000-000000000505", stew.metadata.id, tamarind.metadata.id, "tamarind", 0),
                    ingredient("00000000-0000-0000-0000-000000000506", stew.metadata.id, lentils.metadata.id, "lentils", 1, quantity = "2"),
                    ingredient("00000000-0000-0000-0000-000000000507", chutney.metadata.id, tamarind.metadata.id, "tamarind", 0),
                    ingredient("00000000-0000-0000-0000-000000000504", archived.metadata.id, rice.metadata.id, "rice", 0),
                ),
            ),
        )

        assertEquals(listOf("Vegetable pulao", "Tomato rasam", "Bean stew", "Tamarind chutney"), matches.map { it.title })
        assertEquals("Can make", matches[0].status)
        assertEquals("1/1 in Kitchen  Use first 2026-07-21  35 min", matches[0].subtitle)
        assertEquals("Almost", matches[1].status)
        assertEquals("1/2 in Kitchen  Need Tamarind  Use first 2026-07-25  25 min", matches[1].subtitle)
        assertEquals("Almost", matches[2].status)
        assertEquals("0/2 in Kitchen  Need Tamarind, Lentils  Use first 2026-07-25  40 min", matches[2].subtitle)
        assertEquals("Need more", matches[3].status)
        assertEquals("0/1 in Kitchen  Need Tamarind  15 min", matches[3].subtitle)
    }

    private fun household(): Household = Household(
        id = HOUSEHOLD_ID,
        name = "Test",
        defaultCurrency = "USD",
        timezone = "America/New_York",
        locale = "en-US",
        activeDataHome = DataHomeKind.LOCAL,
        schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
        createdAt = NOW,
        updatedAt = NOW,
        revision = 0,
    )

    private fun item(id: String, name: String): Item = Item(
        metadata = metadata(id),
        name = name,
        kind = ItemKind.FOOD,
        category = "Food",
        defaultUnit = QuantityUnit.EACH,
    )

    private fun lot(id: String, itemId: EntityId, quantity: String, expiresOn: String): InventoryLot = InventoryLot(
        metadata = metadata(id),
        itemId = itemId,
        quantity = Quantity(DecimalAmount.of(quantity), QuantityUnit.EACH),
        status = InventoryLotStatus.AVAILABLE,
        expiresOn = CalendarDate(expiresOn),
    )

    private fun recipe(id: String, name: String, totalMinutes: Int, status: RecipeStatus = RecipeStatus.ACTIVE): Recipe =
        Recipe(
            metadata = metadata(id, archived = status == RecipeStatus.ARCHIVED),
            name = name,
            totalMinutes = totalMinutes,
            status = status,
        )

    private fun ingredient(
        id: String,
        recipeId: EntityId,
        itemId: EntityId,
        text: String,
        order: Int,
        quantity: String? = null,
    ): RecipeIngredient =
        RecipeIngredient(
            metadata = metadata(id),
            recipeId = recipeId,
            itemId = itemId,
            originalText = text,
            quantity = quantity?.let { Quantity(DecimalAmount.of(it), QuantityUnit.EACH) } ?: Quantity.unknown(),
            order = order,
        )

    private fun metadata(id: String, archived: Boolean = false): EntityMetadata = EntityMetadata(
        id = EntityId(id),
        householdId = HOUSEHOLD_ID,
        createdAt = NOW,
        updatedAt = NOW,
        archivedAt = if (archived) NOW else null,
        source = SourceRef(SourceKind.MANUAL, "test"),
    )

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000105")
        val NOW = UtcTimestamp(1)
    }
}
