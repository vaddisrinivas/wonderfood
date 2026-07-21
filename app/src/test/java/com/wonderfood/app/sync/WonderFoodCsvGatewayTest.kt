package com.wonderfood.app.sync

import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.StorageZone
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
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.MealEntry
import com.wonderfood.core.model.household.MealEntryStatus
import com.wonderfood.core.model.household.MealPlan as HouseholdMealPlan
import com.wonderfood.core.model.household.MealPlanStatus as HouseholdMealPlanStatus
import com.wonderfood.core.model.household.Money
import com.wonderfood.core.model.household.NutritionSnapshot
import com.wonderfood.core.model.household.NutritionValues
import com.wonderfood.core.model.household.Purchase
import com.wonderfood.core.model.household.PurchaseLine
import com.wonderfood.core.model.household.PurchaseLineDisposition
import com.wonderfood.core.model.household.PurchaseStatus
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStep
import com.wonderfood.core.model.household.RecipeStatus
import com.wonderfood.core.model.household.ReviewState
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import com.wonderfood.core.model.household.Recipe as HouseholdRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WonderFoodCsvGatewayTest {
    @Test
    fun parsesSeedNutritionEmbeddedInNotesWithoutAnLlm() {
        val csv = """
            record_type,id,name,quantity,zone,category,nutrition_source,notes
            inventory,wf-1,Basmati rice,1 kg,PANTRY,grain,seed_estimate,"Nutrition per 100g_or_100ml: kcal=356; protein_g=7; carbs_g=78; fat_g=1 | Source: generic estimate"
        """.trimIndent()

        val item = WonderFoodCsvGateway.parse(csv).inventory.single()

        assertEquals("100g or 100ml", item.servingText)
        assertEquals(356, item.calories)
        assertEquals(7.0, item.proteinGrams)
        assertEquals(78.0, item.carbsGrams)
        assertEquals(1.0, item.fatGrams)
    }

    @Test
    fun classifiesSupportedCsvRowsAsCanonicalDirectImport() {
        val supported = WonderFoodCsvGateway.parse(
            """
            record_type,id,parent_id,name,title,quantity,zone,category,merchant,purchased_at_millis,currency,amount_cents,disposition
            inventory,1,,Dish soap,,2 each,PANTRY,cleaning,,,,,
            grocery,2,,Paper towels,,1 each,,household,,,,,
            recipe,3,,,Sambar,,,,,,,,
            receipt,4,,,,,,,,Target,1784342400000,USD,899,
            receipt_line,5,4,Paper towels,,2 pack,,household,,,USD,899,HOUSEHOLD
            meal_log,6,,,Sambar lunch,,,,,,,,,
            meal_plan,7,,,Week plan,,,,,,,,,
            """.trimIndent(),
        )
        val preferences = WonderFoodCsvGateway.parse(
            """
            record_type,preference_key,preference_value
            preference,diet_style,high protein
            """.trimIndent(),
        )

        assertTrue(supported.canImportDirectlyToCanonicalHousehold())
        assertFalse(preferences.canImportDirectlyToCanonicalHousehold())
    }

    @Test
    fun exportsCanonicalHouseholdItemsAndShoppingRows() {
        val csv = WonderFoodCsvGateway.export(canonicalSnapshot())

        assertTrue(csv.startsWith("record_type,id,parent_id"))
        assertTrue(csv.contains("inventory,00000000-0000-0000-0000-000000000778,00000000-0000-0000-0000-000000000777,Dish soap,,2 package"))
        assertTrue(csv.contains("grocery,00000000-0000-0000-0000-000000000779,,Paper towels,,1 each"))
        assertTrue(csv.contains("recipe,00000000-0000-0000-0000-000000000780,,,Sambar"))
        assertTrue(csv.contains("meal_plan,00000000-0000-0000-0000-000000000783,,,Week plan"))
        assertTrue(csv.contains("meal_plan_entry,00000000-0000-0000-0000-000000000784,00000000-0000-0000-0000-000000000783,,Sambar"))
        assertTrue(csv.contains("meal_log,00000000-0000-0000-0000-000000000785,,,Rice bowl lunch"))
        assertTrue(csv.contains("receipt,00000000-0000-0000-0000-000000000781"))
        assertTrue(csv.contains("Target"))
        assertTrue(csv.contains("receipt_line,00000000-0000-0000-0000-000000000782,00000000-0000-0000-0000-000000000781,Dish soap,,2 package"))

        val parsed = WonderFoodCsvGateway.parse(csv)

        assertEquals("Dish soap", parsed.inventory.first().name)
        assertEquals("2 package", parsed.inventory.first().quantity)
        assertEquals("cleaning", parsed.inventory.first().category)
        assertEquals(120, parsed.inventory.first().calories)
        assertEquals(18.0, parsed.inventory.first().proteinGrams)
        assertEquals("manual", parsed.inventory.first().nutritionSource)
        assertEquals("Paper towels", parsed.groceries.single().name)
        assertEquals("1 each", parsed.groceries.single().quantity)
        assertEquals("Sambar", parsed.recipes.single().titleText)
        assertEquals("1 cup toor dal\n2 cups bhindi", parsed.recipes.single().ingredientsText)
        assertEquals("Boil dal until soft.\nSimmer with bhindi.", parsed.recipes.single().stepsText)
        assertEquals(4, parsed.recipes.single().servings)
        assertEquals(20, parsed.recipes.single().prepMinutes)
        assertEquals("dinner, south-indian, dal, weeknight", parsed.recipes.single().tags)
        assertEquals("Rice bowl lunch", parsed.mealLogs.single().titleText)
        assertEquals(MealSlot.LUNCH, parsed.mealLogs.single().mealSlot)
        assertEquals(520, parsed.mealLogs.single().calories)
        assertEquals(24.0, parsed.mealLogs.single().proteinGrams)
        assertEquals(61.0, parsed.mealLogs.single().carbsGrams)
        assertEquals(18.0, parsed.mealLogs.single().fatGrams)
        assertEquals("Week plan", parsed.mealPlans.single().titleText)
        assertEquals("Sambar", parsed.mealPlans.single().entries.single().title)
        assertEquals(MealSlot.DINNER, parsed.mealPlans.single().entries.single().slot)
        assertEquals("Dish soap", parsed.receipts.single().items.single().food.name)
        assertEquals("Target", parsed.receipts.single().merchant)
        assertEquals(899L, parsed.receipts.single().items.single().linePriceCents)
        assertEquals("USD", parsed.receipts.single().currencyCode)
        assertEquals(849L, parsed.receipts.single().subtotalCents)
        assertEquals(50L, parsed.receipts.single().taxCents)
        assertEquals(899L, parsed.receipts.single().totalCents)
    }

    private fun canonicalSnapshot(): HouseholdSnapshot {
        val householdId = HouseholdId("00000000-0000-0000-0000-000000000105")
        val itemId = EntityId("00000000-0000-0000-0000-000000000777")
        val lotId = EntityId("00000000-0000-0000-0000-000000000778")
        val lineId = EntityId("00000000-0000-0000-0000-000000000779")
        val recipeId = EntityId("00000000-0000-0000-0000-000000000780")
        val dalIngredientId = EntityId("00000000-0000-0000-0000-000000000788")
        val bhindiIngredientId = EntityId("00000000-0000-0000-0000-000000000789")
        val boilStepId = EntityId("00000000-0000-0000-0000-000000000790")
        val simmerStepId = EntityId("00000000-0000-0000-0000-000000000791")
        val purchaseId = EntityId("00000000-0000-0000-0000-000000000781")
        val purchaseLineId = EntityId("00000000-0000-0000-0000-000000000782")
        val mealPlanId = EntityId("00000000-0000-0000-0000-000000000783")
        val plannedMealId = EntityId("00000000-0000-0000-0000-000000000784")
        val mealLogId = EntityId("00000000-0000-0000-0000-000000000785")
        val itemNutritionId = EntityId("00000000-0000-0000-0000-000000000786")
        val mealNutritionId = EntityId("00000000-0000-0000-0000-000000000787")
        val now = UtcTimestamp(1)
        return HouseholdSnapshot(
            household = Household(
                id = householdId,
                name = "My household",
                defaultCurrency = "USD",
                timezone = "America/New_York",
                locale = "en-US",
                activeDataHome = DataHomeKind.LOCAL,
                schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
                createdAt = now,
                updatedAt = now,
                revision = 1,
            ),
            items = listOf(
                Item(
                    metadata = metadata(itemId, householdId, now),
                    name = "Dish soap",
                    kind = ItemKind.CLEANING,
                    category = "cleaning",
                    defaultUnit = QuantityUnit.PACKAGE,
                    brand = "Seventh Generation",
                ),
            ),
            inventoryLots = listOf(
                InventoryLot(
                    metadata = metadata(lotId, householdId, now),
                    itemId = itemId,
                    quantity = Quantity(DecimalAmount.of("2"), QuantityUnit.PACKAGE),
                ),
            ),
            shoppingLines = listOf(
                ShoppingLine(
                    metadata = metadata(lineId, householdId, now),
                    shoppingListId = EntityId("00000000-0000-0000-0000-000000000501"),
                    displayName = "Paper towels",
                    quantity = Quantity(DecimalAmount.of("1"), QuantityUnit.EACH),
                    status = ShoppingLineStatus.NEEDED,
                ),
            ),
            recipes = listOf(
                HouseholdRecipe(
                    metadata = metadata(recipeId, householdId, now),
                    name = "Sambar",
                    description = "South Indian dinner",
                    cuisine = "south-indian",
                    category = "dinner",
                    tags = setOf("dal", "weeknight"),
                    yield = Quantity(DecimalAmount.of("4"), QuantityUnit.SERVING),
                    prepMinutes = 20,
                    status = RecipeStatus.ACTIVE,
                    ingredientIds = listOf(dalIngredientId, bhindiIngredientId),
                ),
            ),
            recipeIngredients = listOf(
                RecipeIngredient(
                    metadata = metadata(dalIngredientId, householdId, now),
                    recipeId = recipeId,
                    originalText = "1 cup toor dal",
                    quantity = Quantity(DecimalAmount.of("1"), QuantityUnit.CUP),
                    order = 0,
                ),
                RecipeIngredient(
                    metadata = metadata(bhindiIngredientId, householdId, now),
                    recipeId = recipeId,
                    originalText = "2 cups bhindi",
                    quantity = Quantity(DecimalAmount.of("2"), QuantityUnit.CUP),
                    order = 1,
                ),
            ),
            recipeSteps = listOf(
                RecipeStep(
                    metadata = metadata(boilStepId, householdId, now),
                    recipeId = recipeId,
                    order = 0,
                    instruction = "Boil dal until soft.",
                    durationMinutes = 12,
                ),
                RecipeStep(
                    metadata = metadata(simmerStepId, householdId, now),
                    recipeId = recipeId,
                    order = 1,
                    instruction = "Simmer with bhindi.",
                ),
            ),
            purchases = listOf(
                Purchase(
                    metadata = metadata(purchaseId, householdId, now),
                    occurredAt = now,
                    subtotal = Money(849, "USD"),
                    tax = Money(50, "USD"),
                    total = Money(899, "USD"),
                    paymentNote = "Merchant: Target\nLocation: Austin",
                    status = PurchaseStatus.REVIEWED,
                ),
            ),
            purchaseLines = listOf(
                PurchaseLine(
                    metadata = metadata(purchaseLineId, householdId, now),
                    purchaseId = purchaseId,
                    itemId = itemId,
                    displayName = "Dish soap",
                    quantity = Quantity(DecimalAmount.of("2"), QuantityUnit.PACKAGE),
                    finalAmount = Money(899, "USD"),
                    spendCategory = "cleaning",
                    disposition = PurchaseLineDisposition.INVENTORY,
                    inventoryLotId = lotId,
                    reviewState = ReviewState.ACCEPTED,
                ),
            ),
            mealPlans = listOf(
                HouseholdMealPlan(
                    metadata = metadata(mealPlanId, householdId, now),
                    name = "Week plan",
                    startsOn = com.wonderfood.core.model.household.CalendarDate("2026-07-20"),
                    endsOn = com.wonderfood.core.model.household.CalendarDate("2026-07-21"),
                    status = HouseholdMealPlanStatus.ACTIVE,
                ),
            ),
            mealEntries = listOf(
                MealEntry(
                    metadata = metadata(plannedMealId, householdId, now),
                    mealPlanId = mealPlanId,
                    scheduledAt = UtcTimestamp(1_784_505_600_000L),
                    slot = "Dinner",
                    recipeId = recipeId,
                    title = "Sambar",
                    status = MealEntryStatus.PLANNED,
                ),
                MealEntry(
                    metadata = metadata(mealLogId, householdId, now),
                    scheduledAt = UtcTimestamp(1_784_635_200_000L),
                    slot = "Lunch",
                    title = "Rice bowl lunch",
                    nutritionSnapshotIds = listOf(mealNutritionId),
                    status = MealEntryStatus.EATEN,
                ),
            ),
            nutritionSnapshots = listOf(
                NutritionSnapshot(
                    metadata = metadata(itemNutritionId, householdId, now),
                    subject = EntityReference(HouseholdEntityType.ITEM, itemId),
                    basis = Quantity(DecimalAmount.of("1"), QuantityUnit.SERVING),
                    values = NutritionValues(
                        energyKcal = DecimalAmount.of("120"),
                        proteinGrams = DecimalAmount.of("18"),
                    ),
                    provider = "manual",
                    capturedAt = now,
                ),
                NutritionSnapshot(
                    metadata = metadata(mealNutritionId, householdId, now),
                    subject = EntityReference(HouseholdEntityType.MEAL_ENTRY, mealLogId),
                    basis = Quantity(DecimalAmount.of("1"), QuantityUnit.SERVING),
                    values = NutritionValues(
                        energyKcal = DecimalAmount.of("520"),
                        proteinGrams = DecimalAmount.of("24"),
                        carbohydrateGrams = DecimalAmount.of("61"),
                        fatGrams = DecimalAmount.of("18"),
                    ),
                    provider = "manual",
                    capturedAt = now,
                ),
            ),
        )
    }

    private fun metadata(id: EntityId, householdId: HouseholdId, now: UtcTimestamp): EntityMetadata =
        EntityMetadata(
            id = id,
            householdId = householdId,
            createdAt = now,
            updatedAt = now,
            revision = 1,
            source = SourceRef(SourceKind.MANUAL, "test"),
        )
}
