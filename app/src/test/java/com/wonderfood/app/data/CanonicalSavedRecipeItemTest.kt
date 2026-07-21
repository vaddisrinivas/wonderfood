package com.wonderfood.app.data

import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeStatus
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalSavedRecipeItemTest {
    @Test
    fun showsActiveCanonicalRecipesAndSkipsArchivedRows() {
        val preview = CanonicalSavedRecipeItem.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                recipes = listOf(
                    recipe(
                        id = "00000000-0000-0000-0000-000000000401",
                        name = "Tomato rasam",
                        status = RecipeStatus.ACTIVE,
                    ),
                    recipe(
                        id = "00000000-0000-0000-0000-000000000402",
                        name = "Archived pulao",
                        status = RecipeStatus.ARCHIVED,
                    ),
                ),
            ),
        )

        assertEquals(1, preview.size)
        assertEquals("Tomato rasam", preview.single().title)
        assertEquals("Dinner  South Indian  4 serving  25 min  active", preview.single().subtitle)
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

    private fun recipe(
        id: String,
        name: String,
        status: RecipeStatus,
    ): Recipe =
        Recipe(
            metadata = EntityMetadata(
                id = EntityId(id),
                householdId = HOUSEHOLD_ID,
                createdAt = NOW,
                updatedAt = NOW,
                archivedAt = if (status == RecipeStatus.ARCHIVED) NOW else null,
                source = SourceRef(SourceKind.MANUAL, "test"),
            ),
            name = name,
            cuisine = "South Indian",
            category = "Dinner",
            yield = Quantity(DecimalAmount.of("4"), QuantityUnit.SERVING),
            totalMinutes = 25,
            status = status,
        )

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000105")
        val NOW = UtcTimestamp(1)
    }
}
