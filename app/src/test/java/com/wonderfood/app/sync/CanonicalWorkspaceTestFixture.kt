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
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStatus
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp

internal object CanonicalWorkspaceTestFixture {
    private val householdId = HouseholdId("00000000-0000-0000-0000-000000000401")
    private val riceId = EntityId("00000000-0000-0000-0000-000000000402")
    private val recipeId = EntityId("00000000-0000-0000-0000-000000000403")
    private val ingredientId = EntityId("00000000-0000-0000-0000-000000000404")
    private val now = UtcTimestamp(1_784_505_600_000L)

    fun snapshot(): HouseholdSnapshot = HouseholdSnapshot(
        household = Household(
            id = householdId,
            name = "WonderFood Home",
            defaultCurrency = "USD",
            timezone = "America/New_York",
            locale = "en-US",
            activeDataHome = DataHomeKind.LOCAL,
            schemaVersion = 4,
            createdAt = now,
            updatedAt = now,
            revision = 1,
        ),
        items = listOf(
            Item(
                metadata = metadata(riceId),
                name = "Basmati Rice",
                kind = ItemKind.FOOD,
                category = "grain",
                defaultUnit = QuantityUnit.CUP,
                refillThreshold = Quantity(DecimalAmount.of("1"), QuantityUnit.CUP),
            ),
        ),
        inventoryLots = listOf(
            InventoryLot(
                metadata = metadata(EntityId("00000000-0000-0000-0000-000000000405")),
                itemId = riceId,
                quantity = Quantity(DecimalAmount.of("2"), QuantityUnit.CUP),
            ),
        ),
        recipes = listOf(
            Recipe(
                metadata = metadata(recipeId),
                name = "Spinach Rice Bowl",
                yield = Quantity(DecimalAmount.of("2"), QuantityUnit.SERVING),
                status = RecipeStatus.ACTIVE,
                ingredientIds = listOf(ingredientId),
            ),
        ),
        recipeIngredients = listOf(
            RecipeIngredient(
                metadata = metadata(ingredientId),
                recipeId = recipeId,
                itemId = riceId,
                originalText = "1 cup Basmati Rice",
                quantity = Quantity(DecimalAmount.of("1"), QuantityUnit.CUP),
                order = 0,
            ),
        ),
    )

    private fun metadata(id: EntityId) = EntityMetadata(
        id = id,
        householdId = householdId,
        createdAt = now,
        updatedAt = now,
        revision = 1,
        source = SourceRef(SourceKind.MANUAL, "v4-live-proof"),
        confidence = Confidence(10_000),
    )
}
