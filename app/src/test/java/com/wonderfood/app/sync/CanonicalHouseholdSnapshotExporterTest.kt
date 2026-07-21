package com.wonderfood.app.sync

import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.MealLogStatus
import com.wonderfood.core.model.MealSlot
import com.wonderfood.core.model.PlanEntryStatus
import com.wonderfood.core.model.ShoppingItemStatus
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.TruthState
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
import com.wonderfood.core.model.household.MealPlan
import com.wonderfood.core.model.household.MealPlanStatus
import com.wonderfood.core.model.household.Money
import com.wonderfood.core.model.household.NutritionSnapshot
import com.wonderfood.core.model.household.NutritionValues
import com.wonderfood.core.model.household.Purchase
import com.wonderfood.core.model.household.PurchaseLine
import com.wonderfood.core.model.household.PurchaseLineDisposition
import com.wonderfood.core.model.household.PurchaseStatus
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStep
import com.wonderfood.core.model.household.RecipeStatus
import com.wonderfood.core.model.household.ReviewState
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalHouseholdSnapshotExporterTest {
    @Test
    fun projectsCanonicalHouseholdInventoryAndShoppingForProviderSnapshots() {
        val snapshot = CanonicalHouseholdSnapshotExporter.toSnapshot(canonicalSnapshot())

        val food = snapshot.foods.single()
        val stockLot = snapshot.stockLots.single()
        val shoppingItem = snapshot.shoppingItems.single { it.status == ShoppingItemStatus.NEEDED }
        val receiptLineItem = snapshot.shoppingItems.single { it.status == ShoppingItemStatus.PURCHASED }
        val recipe = snapshot.recipes.single()
        val receipt = snapshot.receipts.single()
        val mealPlan = snapshot.mealPlans.single()
        val plannedMeal = mealPlan.entries.single()
        val mealLog = snapshot.mealLogs.single()
        val foodNutrition = snapshot.nutritionSnapshots.single { it.subject.id == food.id.value }
        val recipeNutrition = snapshot.nutritionSnapshots.single { it.subject.id == recipe.id.value }
        val mealNutrition = snapshot.nutritionSnapshots.single { it.subject.id == mealLog.id.value }

        assertEquals("canonical:item:00000000-0000-0000-0000-000000000777", food.id.value)
        assertEquals("Dish soap", food.name)
        assertEquals(TruthState.INFERRED, food.truthState)
        assertEquals(listOf(stockLot.id), food.stockLotIds)
        assertEquals(listOf(foodNutrition.id), food.nutritionSnapshotIds)
        assertEquals(120.0, foodNutrition.values.energyKcal)
        assertEquals(18.0, foodNutrition.values.proteinGrams)
        assertEquals(FoodUnit.SERVING, foodNutrition.basis.quantity.unit)

        assertEquals("canonical:inventory_lot:00000000-0000-0000-0000-000000000778", stockLot.id.value)
        assertEquals(FoodUnit.PACKAGE, stockLot.quantity.unit)
        assertEquals(2.0, stockLot.quantity.amount)
        assertEquals(StockLotStatus.AVAILABLE, stockLot.status)

        assertEquals("Paper towels", snapshot.pages.single { it.id == shoppingItem.pageId }.title)
        assertEquals("canonical:shopping_line:00000000-0000-0000-0000-000000000779", shoppingItem.id.value)
        assertEquals(FoodUnit.EACH, shoppingItem.quantity.unit)
        assertEquals(ShoppingItemStatus.NEEDED, shoppingItem.status)
        assertEquals("canonical:recipe:00000000-0000-0000-0000-000000000780", recipe.id.value)
        assertEquals("Sambar", recipe.title)
        assertEquals(4.0, recipe.servings.amount)
        assertEquals(FoodUnit.SERVING, recipe.servings.unit)
        assertEquals(com.wonderfood.core.model.RecipeStatus.ACTIVE, recipe.status)
        assertEquals("South Indian dinner", recipe.description)
        assertEquals("Sambar", snapshot.pages.single { it.id == recipe.pageId }.title)
        assertEquals(listOf("1 cup toor dal", "2 cups bhindi"), recipe.ingredients.map { it.displayName })
        assertEquals(FoodUnit.CUP, recipe.ingredients.first().quantity.unit)
        assertEquals(1.0, recipe.ingredients.first().quantity.amount)
        assertEquals(listOf(recipeNutrition.id), recipe.nutritionSnapshotIds)
        assertEquals(310.0, recipeNutrition.values.energyKcal)
        assertEquals(listOf("Boil dal until soft.", "Simmer with bhindi."), recipe.steps.map { it.instruction })
        assertEquals(12, recipe.steps.first().durationMinutes)

        assertEquals("canonical:purchase:00000000-0000-0000-0000-000000000781", receipt.id.value)
        assertEquals("Target", receipt.merchantName)
        assertEquals(listOf(receiptLineItem.id), receipt.itemIds)
        assertEquals("canonical:shopping_line:00000000-0000-0000-0000-000000000782", receiptLineItem.id.value)
        assertEquals("Dish soap", snapshot.pages.single { it.id == receiptLineItem.pageId }.title)
        assertEquals(FoodUnit.PACKAGE, receiptLineItem.quantity.unit)
        assertEquals(2.0, receiptLineItem.quantity.amount)
        assertEquals("cleaning", receiptLineItem.reason)
        assertEquals(8.49, receipt.subtotal?.amount)
        assertEquals(0.5, receipt.tax?.amount)
        assertEquals(8.99, receipt.total?.amount)

        assertEquals("canonical:meal_plan:00000000-0000-0000-0000-000000000783", mealPlan.id.value)
        assertEquals("Week plan", mealPlan.name)
        assertEquals("2026-07-20", mealPlan.startsOn.value)
        assertEquals(com.wonderfood.core.model.MealPlanStatus.ACTIVE, mealPlan.status)
        assertEquals("canonical:meal_entry:00000000-0000-0000-0000-000000000784", plannedMeal.id.value)
        assertEquals(mealPlan.id, plannedMeal.mealPlanId)
        assertEquals("2026-07-20", plannedMeal.date.value)
        assertEquals(MealSlot.DINNER, plannedMeal.mealSlot)
        assertEquals(recipe.id, plannedMeal.recipeId)
        assertEquals(PlanEntryStatus.PLANNED, plannedMeal.status)
        assertEquals("Week plan", snapshot.pages.single { it.id == mealPlan.pageId }.title)

        assertEquals("canonical:meal_log:00000000-0000-0000-0000-000000000785", mealLog.id.value)
        assertEquals("Rice bowl lunch", snapshot.pages.single { it.id == mealLog.pageId }.title)
        assertEquals(MealSlot.LUNCH, mealLog.mealSlot)
        assertEquals(listOf(mealNutrition.id), mealLog.nutritionSnapshotIds)
        assertEquals(520.0, mealNutrition.values.energyKcal)
        assertEquals(24.0, mealNutrition.values.proteinGrams)
        assertEquals(MealLogStatus.CONFIRMED, mealLog.status)
    }

    private fun canonicalSnapshot(): HouseholdSnapshot {
        val householdId = HouseholdId("00000000-0000-0000-0000-000000000105")
        val itemId = EntityId("00000000-0000-0000-0000-000000000777")
        val lotId = EntityId("00000000-0000-0000-0000-000000000778")
        val lineId = EntityId("00000000-0000-0000-0000-000000000779")
        val recipeId = EntityId("00000000-0000-0000-0000-000000000780")
        val dalIngredientId = EntityId("00000000-0000-0000-0000-000000000788")
        val bhindiIngredientId = EntityId("00000000-0000-0000-0000-000000000789")
        val boilStepId = EntityId("00000000-0000-0000-0000-000000000791")
        val simmerStepId = EntityId("00000000-0000-0000-0000-000000000792")
        val purchaseId = EntityId("00000000-0000-0000-0000-000000000781")
        val purchaseLineId = EntityId("00000000-0000-0000-0000-000000000782")
        val mealPlanId = EntityId("00000000-0000-0000-0000-000000000783")
        val plannedMealId = EntityId("00000000-0000-0000-0000-000000000784")
        val mealLogId = EntityId("00000000-0000-0000-0000-000000000785")
        val itemNutritionId = EntityId("00000000-0000-0000-0000-000000000786")
        val mealNutritionId = EntityId("00000000-0000-0000-0000-000000000787")
        val recipeNutritionId = EntityId("00000000-0000-0000-0000-000000000790")
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
                Recipe(
                    metadata = metadata(recipeId, householdId, now),
                    name = "Sambar",
                    description = "South Indian dinner",
                    tags = setOf("dal", "weeknight"),
                    yield = Quantity(DecimalAmount.of("4"), QuantityUnit.SERVING),
                    prepMinutes = 20,
                    status = RecipeStatus.ACTIVE,
                    ingredientIds = listOf(dalIngredientId, bhindiIngredientId),
                    nutritionSnapshotIds = listOf(recipeNutritionId),
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
                MealPlan(
                    metadata = metadata(mealPlanId, householdId, now),
                    name = "Week plan",
                    startsOn = com.wonderfood.core.model.household.CalendarDate("2026-07-20"),
                    endsOn = com.wonderfood.core.model.household.CalendarDate("2026-07-21"),
                    status = MealPlanStatus.ACTIVE,
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
                    servings = Quantity(DecimalAmount.of("4"), QuantityUnit.SERVING),
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
                    provider = "test",
                    capturedAt = now,
                ),
                NutritionSnapshot(
                    metadata = metadata(mealNutritionId, householdId, now),
                    subject = EntityReference(HouseholdEntityType.MEAL_ENTRY, mealLogId),
                    basis = Quantity(DecimalAmount.of("1"), QuantityUnit.SERVING),
                    values = NutritionValues(
                        energyKcal = DecimalAmount.of("520"),
                        proteinGrams = DecimalAmount.of("24"),
                    ),
                    provider = "test",
                    capturedAt = now,
                ),
                NutritionSnapshot(
                    metadata = metadata(recipeNutritionId, householdId, now),
                    subject = EntityReference(HouseholdEntityType.RECIPE, recipeId),
                    basis = Quantity(DecimalAmount.of("1"), QuantityUnit.SERVING),
                    values = NutritionValues(
                        energyKcal = DecimalAmount.of("310"),
                        proteinGrams = DecimalAmount.of("16"),
                    ),
                    provider = "test",
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
