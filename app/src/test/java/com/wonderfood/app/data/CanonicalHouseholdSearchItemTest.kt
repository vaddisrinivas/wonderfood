package com.wonderfood.app.data

import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalHouseholdSearchItemTest {
    @Test
    fun nonFoodItemSubtitleDoesNotInventFoodDetails() {
        val searchItem = CanonicalHouseholdSearchItem.from(
            Item(
                metadata = EntityMetadata(
                    id = EntityId("00000000-0000-0000-0000-000000000777"),
                    householdId = HouseholdId("00000000-0000-0000-0000-000000000105"),
                    createdAt = UtcTimestamp(1),
                    updatedAt = UtcTimestamp(1),
                    revision = 1,
                    source = SourceRef(SourceKind.MANUAL, "test"),
                ),
                name = "Dish soap",
                kind = ItemKind.CLEANING,
                category = "Cleaning",
                defaultUnit = QuantityUnit.EACH,
                brand = "Seventh Generation",
                foodDetailsId = null,
            ),
        )

        assertEquals("00000000-0000-0000-0000-000000000777", searchItem.id)
        assertEquals("Dish soap", searchItem.name)
        assertEquals("Canonical - Cleaning - Seventh Generation", searchItem.subtitle)
    }
}
