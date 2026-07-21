package com.wonderfood.app.data

import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.Money
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.ShoppingReason
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalCartPreviewItemTest {
    @Test
    fun filtersArchivedAndPurchasedLinesForCartPreview() {
        val preview = CanonicalCartPreviewItem.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                shoppingLines = listOf(
                    line("00000000-0000-0000-0000-000000000301", "Paper towels", ShoppingLineStatus.NEEDED, category = "household"),
                    line("00000000-0000-0000-0000-000000000302", "Dish soap", ShoppingLineStatus.PURCHASED, category = "household"),
                    line("00000000-0000-0000-0000-000000000303", "Trash bags", ShoppingLineStatus.ARCHIVED, category = "household"),
                ),
            ),
        )

        assertEquals(1, preview.size)
        assertEquals("Paper towels", preview.single().title)
        assertEquals("2 package  household  manual", preview.single().subtitle)
    }

    @Test
    fun supportsFoodNonFoodStoreCategoryRecipeGapsStaplesAndPriorPriceEstimate() {
        val lentils = item("00000000-0000-0000-0000-000000000101", "Lentils", ItemKind.FOOD)
        val towels = item("00000000-0000-0000-0000-000000000102", "Paper towels", ItemKind.HOUSEHOLD)
        val preview = CanonicalCartPreviewItem.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                items = listOf(lentils, towels),
                shoppingLines = listOf(
                    line(
                        id = "00000000-0000-0000-0000-000000000311",
                        name = "Lentils",
                        status = ShoppingLineStatus.NEEDED,
                        itemId = lentils.metadata.id,
                        category = "Pantry",
                        preferredStore = "Patel Brothers",
                        reason = ShoppingReason.RECIPE_GAP,
                        estimatedPrice = Money(399, "USD"),
                    ),
                    line(
                        id = "00000000-0000-0000-0000-000000000312",
                        name = "Paper towels",
                        status = ShoppingLineStatus.IN_CART,
                        itemId = towels.metadata.id,
                        category = "Household",
                        preferredStore = "Target",
                        reason = ShoppingReason.HOUSEHOLD_STAPLE,
                        estimatedPrice = Money(899, "USD"),
                    ),
                ),
            ),
        )

        assertEquals(listOf("Paper towels", "Lentils"), preview.map { it.title })
        assertEquals("2 package  household  Household  at Target  household staple  est USD 8.99", preview[0].subtitle)
        assertEquals("2 package  food  Pantry  at Patel Brothers  recipe gap  est USD 3.99", preview[1].subtitle)
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

    private fun line(id: String, name: String, status: ShoppingLineStatus): ShoppingLine =
        ShoppingLine(
            metadata = EntityMetadata(
                id = EntityId(id),
                householdId = HOUSEHOLD_ID,
                createdAt = NOW,
                updatedAt = NOW,
                archivedAt = if (status == ShoppingLineStatus.ARCHIVED) NOW else null,
                source = SourceRef(SourceKind.MANUAL, "test"),
            ),
            shoppingListId = EntityId("00000000-0000-0000-0000-000000000501"),
            itemId = null,
            displayName = name,
            quantity = Quantity(DecimalAmount.of("2"), QuantityUnit.PACKAGE),
            category = null,
            status = status,
            reason = ShoppingReason.MANUAL,
        )

    private fun line(
        id: String,
        name: String,
        status: ShoppingLineStatus,
        category: String,
        itemId: EntityId? = null,
        preferredStore: String? = null,
        reason: ShoppingReason = ShoppingReason.MANUAL,
        estimatedPrice: Money? = null,
    ): ShoppingLine =
        ShoppingLine(
            metadata = EntityMetadata(
                id = EntityId(id),
                householdId = HOUSEHOLD_ID,
                createdAt = NOW,
                updatedAt = NOW,
                archivedAt = if (status == ShoppingLineStatus.ARCHIVED) NOW else null,
                source = SourceRef(SourceKind.MANUAL, "test"),
            ),
            shoppingListId = EntityId("00000000-0000-0000-0000-000000000501"),
            itemId = itemId,
            displayName = name,
            quantity = Quantity(DecimalAmount.of("2"), QuantityUnit.PACKAGE),
            category = category,
            preferredStore = preferredStore,
            status = status,
            reason = reason,
            estimatedPrice = estimatedPrice,
        )

    private fun item(id: String, name: String, kind: ItemKind): Item =
        Item(
            metadata = EntityMetadata(
                id = EntityId(id),
                householdId = HOUSEHOLD_ID,
                createdAt = NOW,
                updatedAt = NOW,
                source = SourceRef(SourceKind.MANUAL, "test"),
            ),
            name = name,
            kind = kind,
            category = kind.name.lowercase(),
            defaultUnit = QuantityUnit.PACKAGE,
        )

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000105")
        val NOW = UtcTimestamp(1)
    }
}
