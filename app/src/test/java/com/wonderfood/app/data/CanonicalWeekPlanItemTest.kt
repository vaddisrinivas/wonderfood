package com.wonderfood.app.data

import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.EntityReference
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.InventoryEvent
import com.wonderfood.core.model.household.InventoryEventType
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.MealEntry
import com.wonderfood.core.model.household.MealEntryStatus
import com.wonderfood.core.model.household.NutritionSnapshot
import com.wonderfood.core.model.household.NutritionValues
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStatus
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.ShoppingReason
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalWeekPlanItemTest {
    @Test
    fun showsCurrentWeekCanonicalMealsAndSkipsArchivedRows() {
        val preview = CanonicalWeekPlanItem.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                mealEntries = listOf(
                    meal(
                        id = "00000000-0000-0000-0000-000000000401",
                        title = "Tomato rasam dinner",
                        scheduledAt = "2026-07-20T23:00:00Z",
                        status = MealEntryStatus.PLANNED,
                        nutritionIds = listOf(EntityId("00000000-0000-0000-0000-000000000501")),
                    ),
                    meal(
                        id = "00000000-0000-0000-0000-000000000402",
                        title = "Skipped leftovers",
                        scheduledAt = "2026-07-21T12:00:00Z",
                        status = MealEntryStatus.SKIPPED,
                    ),
                    meal(
                        id = "00000000-0000-0000-0000-000000000403",
                        title = "Future pulao",
                        scheduledAt = "2026-07-30T23:00:00Z",
                        status = MealEntryStatus.PLANNED,
                    ),
                ),
                nutritionSnapshots = listOf(
                    nutrition(
                        id = "00000000-0000-0000-0000-000000000501",
                        subjectId = EntityId("00000000-0000-0000-0000-000000000401"),
                    ),
                ),
            ),
            now = Instant.parse("2026-07-20T12:00:00Z"),
        )

        assertEquals(1, preview.size)
        assertEquals("Tomato rasam dinner", preview.single().title)
        assertEquals("Mon 7/20  Dinner  4 serving  planned  520 kcal  24g protein  from manual test", preview.single().subtitle)
    }

    @Test
    fun weekProjectionShowsInventorySubtractionReviewedGapsLeftoversAndProvenance() {
        val riceItemId = EntityId("00000000-0000-0000-0000-000000000701")
        val dalItemId = EntityId("00000000-0000-0000-0000-000000000702")
        val spiceItemId = EntityId("00000000-0000-0000-0000-000000000703")
        val recipeId = EntityId("00000000-0000-0000-0000-000000000704")
        val entryId = EntityId("00000000-0000-0000-0000-000000000705")
        val preview = CanonicalWeekPlanItem.fromSnapshot(
            HouseholdSnapshot(
                household = household(),
                items = listOf(
                    item(riceItemId.value, "Rice"),
                    item(dalItemId.value, "Dal"),
                    item(spiceItemId.value, "Spices"),
                ),
                inventoryLots = listOf(
                    lot("00000000-0000-0000-0000-000000000711", riceItemId, "8", QuantityUnit.CUP),
                    lot("00000000-0000-0000-0000-000000000712", dalItemId, "2", QuantityUnit.CUP),
                ),
                recipes = listOf(recipe(recipeId.value, "Dal rice")),
                recipeIngredients = listOf(
                    ingredient("00000000-0000-0000-0000-000000000721", recipeId, riceItemId, "2", QuantityUnit.CUP),
                    ingredient("00000000-0000-0000-0000-000000000722", recipeId, dalItemId, "1", QuantityUnit.CUP),
                    ingredient("00000000-0000-0000-0000-000000000723", recipeId, spiceItemId, "1", QuantityUnit.PACKAGE),
                ),
                mealEntries = listOf(
                    meal(
                        id = entryId.value,
                        title = "Dal rice bowls",
                        scheduledAt = "2026-07-22T23:00:00Z",
                        status = MealEntryStatus.PLANNED,
                        recipeId = recipeId,
                        servings = Quantity(DecimalAmount.of("2"), QuantityUnit.SERVING),
                        leftoverIntent = "pack 2 lunches",
                        sourceKind = SourceKind.AI_PROPOSAL,
                    ),
                ),
                inventoryEvents = listOf(
                    inventoryEvent(
                        id = "00000000-0000-0000-0000-000000000731",
                        itemId = riceItemId,
                        relatedEntityId = entryId,
                        quantity = Quantity(DecimalAmount.of("4"), QuantityUnit.CUP),
                    ),
                ),
                shoppingLines = listOf(
                    shoppingGap(
                        id = "00000000-0000-0000-0000-000000000741",
                        itemId = spiceItemId,
                        sourceEntityIds = listOf(recipeId, entryId),
                    ),
                ),
            ),
            now = Instant.parse("2026-07-20T12:00:00Z"),
        )

        assertEquals(1, preview.size)
        assertEquals("Dal rice bowls", preview.single().title)
        assertEquals(
            "Wed 7/22  Dinner  2 serving  planned  leftovers pack 2 lunches  " +
                "inventory covers 2/3 ingredients  subtracted 4 cup  1 reviewed gap  from ai proposal test",
            preview.single().subtitle,
        )
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

    private fun meal(
        id: String,
        title: String,
        scheduledAt: String,
        status: MealEntryStatus,
        nutritionIds: List<EntityId> = emptyList(),
        recipeId: EntityId? = null,
        servings: Quantity = Quantity(DecimalAmount.of("4"), QuantityUnit.SERVING),
        leftoverIntent: String? = null,
        sourceKind: SourceKind = SourceKind.MANUAL,
    ): MealEntry =
        MealEntry(
            metadata = EntityMetadata(
                id = EntityId(id),
                householdId = HOUSEHOLD_ID,
                createdAt = NOW,
                updatedAt = NOW,
                archivedAt = if (status == MealEntryStatus.ARCHIVED) NOW else null,
                source = SourceRef(sourceKind, "test"),
            ),
            scheduledAt = UtcTimestamp(Instant.parse(scheduledAt).toEpochMilli()),
            slot = "Dinner",
            recipeId = recipeId,
            title = title,
            servings = servings,
            status = status,
            leftoverIntent = leftoverIntent,
            nutritionSnapshotIds = nutritionIds,
        )

    private fun item(id: String, name: String): Item = Item(
        metadata = metadata(id),
        name = name,
        kind = ItemKind.FOOD,
        defaultUnit = QuantityUnit.CUP,
    )

    private fun lot(id: String, itemId: EntityId, amount: String, unit: QuantityUnit): InventoryLot = InventoryLot(
        metadata = metadata(id),
        itemId = itemId,
        quantity = Quantity(DecimalAmount.of(amount), unit),
        status = InventoryLotStatus.AVAILABLE,
    )

    private fun recipe(id: String, name: String): Recipe = Recipe(
        metadata = metadata(id),
        name = name,
        status = RecipeStatus.ACTIVE,
    )

    private fun ingredient(
        id: String,
        recipeId: EntityId,
        itemId: EntityId,
        amount: String,
        unit: QuantityUnit,
    ): RecipeIngredient = RecipeIngredient(
        metadata = metadata(id),
        recipeId = recipeId,
        itemId = itemId,
        originalText = "$amount ${unit.code}",
        quantity = Quantity(DecimalAmount.of(amount), unit),
        order = 0,
    )

    private fun inventoryEvent(
        id: String,
        itemId: EntityId,
        relatedEntityId: EntityId,
        quantity: Quantity,
    ): InventoryEvent = InventoryEvent(
        metadata = metadata(id),
        itemId = itemId,
        type = InventoryEventType.CONSUME,
        quantityDelta = quantity,
        relatedEntityId = relatedEntityId,
        commandId = com.wonderfood.core.model.household.CommandId("00000000-0000-0000-0000-000000000751"),
    )

    private fun shoppingGap(id: String, itemId: EntityId, sourceEntityIds: List<EntityId>): ShoppingLine = ShoppingLine(
        metadata = metadata(id),
        shoppingListId = EntityId("00000000-0000-0000-0000-000000000752"),
        itemId = itemId,
        displayName = "Spices",
        quantity = Quantity(DecimalAmount.of("1"), QuantityUnit.PACKAGE),
        status = ShoppingLineStatus.NEEDED,
        reason = ShoppingReason.RECIPE_GAP,
        sourceEntityIds = sourceEntityIds,
    )

    private fun metadata(id: String): EntityMetadata = EntityMetadata(
        id = EntityId(id),
        householdId = HOUSEHOLD_ID,
        createdAt = NOW,
        updatedAt = NOW,
        source = SourceRef(SourceKind.MANUAL, "test"),
    )

    private fun nutrition(id: String, subjectId: EntityId): NutritionSnapshot =
        NutritionSnapshot(
            metadata = EntityMetadata(
                id = EntityId(id),
                householdId = HOUSEHOLD_ID,
                createdAt = NOW,
                updatedAt = NOW,
                source = SourceRef(SourceKind.AI_PROPOSAL, "test"),
            ),
            subject = EntityReference(HouseholdEntityType.MEAL_ENTRY, subjectId),
            basis = Quantity(DecimalAmount.of("1"), QuantityUnit.SERVING),
            values = NutritionValues(
                energyKcal = DecimalAmount.of("520"),
                proteinGrams = DecimalAmount.of("24"),
            ),
            capturedAt = NOW,
        )

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("00000000-0000-0000-0000-000000000105")
        val NOW = UtcTimestamp(1)
    }
}
