package com.wonderfood.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wonderfood.app.ai.CommandEnvelopeDraftMapper
import com.wonderfood.app.testing.TestFixtureResources
import com.wonderfood.core.data.HouseholdRepositories
import com.wonderfood.core.data.room.WonderFoodDatabase
import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.engine.HouseholdCommandExecutor
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.MealEntryStatus
import com.wonderfood.core.model.household.MealPlanStatus
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.PurchaseLineDisposition
import com.wonderfood.core.model.household.PurchaseStatus
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.RecipeStatus
import com.wonderfood.core.model.household.ReviewState
import com.wonderfood.core.model.household.ShoppingReason
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HouseholdDraftCommandMapperTest {
    private var database: WonderFoodDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
    }

    private val mapper = HouseholdDraftCommandMapper(
        householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID,
        clockMillis = { 1_784_520_000_000L },
    )

    @Test
    fun inventoryDraftCreatesCanonicalItemAndLotCommands() {
        val commands = mapper.toCommands(
            InventoryDraft(
                listOf(
                    FoodCandidate(
                        name = "Rolled oats",
                        quantity = "2 bags",
                        category = "Breakfast",
                        notes = "Pantry shelf",
                    ),
                ),
            ),
            FoodDraftCommandOrigin.MANUAL_SAVE,
        )

        val item = commands[0] as HouseholdCommand.UpsertItem
        val lot = commands[1] as HouseholdCommand.UpsertInventoryLot
        assertEquals("Rolled oats", item.item.name)
        assertEquals(ItemKind.FOOD, item.item.kind)
        assertEquals("Breakfast", item.item.category)
        assertEquals("2", lot.lot.quantity.amount?.value)
        assertEquals(QuantityUnit.PACKAGE, lot.lot.quantity.unit)
        assertEquals(item.item.metadata.id, lot.lot.itemId)
    }

    @Test
    fun inventoryDraftWithNutritionCreatesItemLinkedNutritionSnapshot() {
        val commands = mapper.toCommands(
            InventoryDraft(
                listOf(
                    FoodCandidate(
                        name = "Greek yogurt",
                        quantity = "2 cups",
                        servingText = "1 serving",
                        calories = 120,
                        proteinGrams = 18.0,
                        carbsGrams = 7.0,
                        fatGrams = 2.5,
                        nutritionSource = "google_sheets_workspace_import",
                    ),
                ),
            ),
            FoodDraftCommandOrigin.CSV_IMPORT,
        )

        val item = commands[0] as HouseholdCommand.UpsertItem
        val nutrition = commands[2] as HouseholdCommand.UpsertNutritionSnapshot
        assertEquals(3, commands.size)
        assertEquals(HouseholdEntityType.ITEM, nutrition.snapshot.subject.type)
        assertEquals(item.item.metadata.id, nutrition.snapshot.subject.id)
        assertEquals("1", nutrition.snapshot.basis.amount?.value)
        assertEquals(QuantityUnit.SERVING, nutrition.snapshot.basis.unit)
        assertEquals("120", nutrition.snapshot.values.energyKcal?.value)
        assertEquals("18", nutrition.snapshot.values.proteinGrams?.value)
        assertEquals("7", nutrition.snapshot.values.carbohydrateGrams?.value)
        assertEquals("2.5", nutrition.snapshot.values.fatGrams?.value)
        assertEquals("google_sheets_workspace_import", nutrition.snapshot.provider)
    }

    @Test
    fun groceryDraftCreatesCanonicalShoppingLine() {
        val commands = mapper.toCommands(
            GroceryDraft(listOf(FoodCandidate(name = "Dish soap", quantity = "1 bottle", category = "Cleaning"))),
            FoodDraftCommandOrigin.AI_REVIEW,
        )

        val line = commands.single() as HouseholdCommand.UpsertShoppingLine
        assertEquals("Dish soap", line.line.displayName)
        assertEquals("Cleaning", line.line.category)
        assertEquals("1", line.line.quantity.amount?.value)
        assertEquals(QuantityUnit.EACH, line.line.quantity.unit)
    }

    @Test
    fun receiptHouseholdLineCreatesNonFoodInventoryWithoutFoodDetails() {
        val commands = mapper.toCommands(
            ReceiptDraft(
                items = listOf(
                    ReceiptItemDraft(
                        food = FoodCandidate(name = "Paper towels", quantity = "2 pack"),
                        disposition = ReceiptItemDisposition.HOUSEHOLD,
                        linePriceCents = 899,
                    ),
                ),
                merchant = "Patel Brothers",
                storeLocation = "Edison",
                purchasedAtMillis = 1_784_342_400_000L,
                currencyCode = "USD",
                subtotalCents = 899,
                taxCents = 63,
                totalCents = 962,
                sourceLabel = "google_sheets_workspace_import",
            ),
            FoodDraftCommandOrigin.RECEIPT,
        )

        val purchase = commands[0] as HouseholdCommand.UpsertPurchase
        val item = commands[1] as HouseholdCommand.UpsertItem
        val lot = commands[2] as HouseholdCommand.UpsertInventoryLot
        val line = commands[3] as HouseholdCommand.UpsertPurchaseLine
        assertEquals(PurchaseStatus.REVIEWED, purchase.purchase.status)
        assertEquals(1_784_342_400_000L, purchase.purchase.occurredAt.epochMillis)
        assertEquals(899L, purchase.purchase.subtotal?.minorUnits)
        assertEquals(63L, purchase.purchase.tax?.minorUnits)
        assertEquals(962L, purchase.purchase.total?.minorUnits)
        assertTrue(purchase.purchase.paymentNote.orEmpty().contains("Merchant: Patel Brothers"))
        assertTrue(purchase.purchase.paymentNote.orEmpty().contains("Location: Edison"))
        assertEquals(ItemKind.HOUSEHOLD, item.item.kind)
        assertEquals(null, item.item.foodDetailsId)
        assertEquals(899L, lot.lot.unitCost?.minorUnits)
        assertEquals(purchase.purchase.metadata.id, line.line.purchaseId)
        assertEquals(item.item.metadata.id, line.line.itemId)
        assertEquals(lot.lot.metadata.id, line.line.inventoryLotId)
        assertEquals(899L, line.line.finalAmount?.minorUnits)
        assertEquals(PurchaseLineDisposition.INVENTORY, line.line.disposition)
        assertEquals(ReviewState.ACCEPTED, line.line.reviewState)
    }

    @Test
    fun manualReceiptDraftMapsCorrectionsRefundsUncertainCategoryAndReconciliation() {
        val commands = mapper.toCommands(
            ReceiptDraft(
                items = listOf(
                    ReceiptItemDraft(
                        food = FoodCandidate(name = "Milk", quantity = "1 carton", category = "food"),
                        disposition = ReceiptItemDisposition.INVENTORY,
                        linePriceCents = 499,
                    ),
                    ReceiptItemDraft(
                        food = FoodCandidate(name = "Return bottle deposit", quantity = "", category = "other"),
                        disposition = ReceiptItemDisposition.RETURN_REFUND,
                        linePriceCents = -100,
                    ),
                    ReceiptItemDraft(
                        food = FoodCandidate(name = "Manual price correction", quantity = "", category = "uncertain"),
                        disposition = ReceiptItemDisposition.CORRECTION,
                        linePriceCents = -50,
                    ),
                ),
                merchant = "Corner Market",
                purchasedAtMillis = 1_784_342_400_000L,
                subtotalCents = 499,
                taxCents = 40,
                discountCents = 150,
                totalCents = 389,
                currencyCode = "USD",
                sourceLabel = "manual",
            ),
            FoodDraftCommandOrigin.MANUAL_SAVE,
        )

        val purchase = commands[0] as HouseholdCommand.UpsertPurchase
        val lines = commands.filterIsInstance<HouseholdCommand.UpsertPurchaseLine>().map { it.line }
        val refund = lines.single { it.displayName == "Return bottle deposit" }
        val correction = lines.single { it.displayName == "Manual price correction" }

        assertEquals("manual", purchase.record.source.label)
        assertEquals(499L, purchase.purchase.subtotal?.minorUnits)
        assertEquals(40L, purchase.purchase.tax?.minorUnits)
        assertEquals(150L, purchase.purchase.discount?.minorUnits)
        assertEquals(389L, purchase.purchase.total?.minorUnits)
        assertEquals(0L, purchase.purchase.reconciliationDifference?.minorUnits)
        assertEquals(PurchaseLineDisposition.SERVICE, refund.disposition)
        assertEquals(-100L, refund.finalAmount?.minorUnits)
        assertEquals(null, refund.itemId)
        assertEquals(PurchaseLineDisposition.SERVICE, correction.disposition)
        assertEquals(-50L, correction.finalAmount?.minorUnits)
        assertEquals("uncertain", correction.spendCategory)
        assertEquals(null, correction.inventoryLotId)
    }

    @Test
    fun externalProposalSupportedDraftsCreateCanonicalCommands() {
        val commands = mapper.toCommands(
            CompositeDraft(
                listOf(
                    InventoryDraft(listOf(FoodCandidate(name = "Rice", quantity = "2 kg"))),
                    GroceryDraft(listOf(FoodCandidate(name = "Dish soap", quantity = "1 bottle"))),
                    ReceiptDraft(
                        items = listOf(
                            ReceiptItemDraft(
                                food = FoodCandidate(name = "Paper towels", quantity = "2 pack"),
                                disposition = ReceiptItemDisposition.HOUSEHOLD,
                            ),
                        ),
                    ),
                ),
            ),
            FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
        )

        assertEquals(7, commands.size)
        assertTrue(commands.all { it.record.source.label == "external_proposal" })
    }

    @Test
    fun externalRecipeProposalUsesSameCanonicalCommandSurfaceAsManualDraft() {
        val turn = CommandEnvelopeDraftMapper.tryMap(
            TestFixtureResources.readText("fixtures/command-envelopes/recipe-save-generic.json"),
        )
        val externalDraft = turn?.draft as RecipeDraft
        val manualDraft = RecipeDraft(
            titleText = "Tomato Peanut Curry",
            ingredientsText = "2 cups Tomatoes\n1/2 cup Peanut Butter",
            stepsText = "1. Soak peanuts and blend into a smooth paste.\n2. Saut\u00e9 onions, tomatoes, then cook with spices and peanut paste.\n3. Simmer until thick.",
            servings = 4,
            prepMinutes = 20,
            tags = "indian, dinner, vegan",
        )

        val externalCommands = mapper.toCommands(externalDraft, FoodDraftCommandOrigin.EXTERNAL_PROPOSAL)
        val manualCommands = mapper.toCommands(manualDraft, FoodDraftCommandOrigin.MANUAL_SAVE)

        assertEquals(manualCommands.map { it::class }, externalCommands.map { it::class })
        assertEquals("external_proposal", externalCommands.first().record.source.label)
        assertEquals("manual", manualCommands.first().record.source.label)
        val externalRecipe = externalCommands[0] as HouseholdCommand.UpsertRecipe
        val manualRecipe = manualCommands[0] as HouseholdCommand.UpsertRecipe
        assertEquals(manualRecipe.recipe.name, externalRecipe.recipe.name)
        assertEquals(manualRecipe.recipe.yield, externalRecipe.recipe.yield)
        assertEquals(manualRecipe.recipe.prepMinutes, externalRecipe.recipe.prepMinutes)
        assertEquals(
            manualCommands.filterIsInstance<HouseholdCommand.UpsertRecipeIngredient>().map { it.ingredient.originalText },
            externalCommands.filterIsInstance<HouseholdCommand.UpsertRecipeIngredient>().map { it.ingredient.originalText },
        )
        assertEquals(
            manualCommands.filterIsInstance<HouseholdCommand.UpsertRecipeStep>().map { it.step.instruction },
            externalCommands.filterIsInstance<HouseholdCommand.UpsertRecipeStep>().map { it.step.instruction },
        )
    }

    @Test
    fun ignoredReceiptLineCreatesReviewedIgnoredPurchaseLineWithoutInventory() {
        val commands = mapper.toCommands(
            ReceiptDraft(
                items = listOf(
                    ReceiptItemDraft(
                        food = FoodCandidate(name = "Cash back", quantity = ""),
                        disposition = ReceiptItemDisposition.IGNORE,
                        linePriceCents = 2000,
                    ),
                ),
                currencyCode = "USD",
            ),
            FoodDraftCommandOrigin.RECEIPT,
        )

        val purchase = commands[0] as HouseholdCommand.UpsertPurchase
        val line = commands[1] as HouseholdCommand.UpsertPurchaseLine
        assertEquals(2, commands.size)
        assertEquals(purchase.purchase.metadata.id, line.line.purchaseId)
        assertEquals(null, line.line.itemId)
        assertEquals(null, line.line.inventoryLotId)
        assertEquals(PurchaseLineDisposition.IGNORED, line.line.disposition)
        assertEquals(2000L, line.line.finalAmount?.minorUnits)
    }

    @Test
    fun mixedReceiptAtomicallyPersistsFoodHouseholdIgnoredExpenseAndLinks() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, WonderFoodDatabase::class.java).build()
        val repository = HouseholdRepositories.room(requireNotNull(database))
        val executor = HouseholdCommandExecutor(repository)
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        val now = UtcTimestamp(1_784_520_000_000L)
        executor.executeTestHousehold(householdId, now)

        mapper.toCommands(
            ReceiptDraft(
                items = listOf(
                    ReceiptItemDraft(
                        food = FoodCandidate(name = "Greek yogurt", quantity = "2 cups", category = "Dairy"),
                        disposition = ReceiptItemDisposition.INVENTORY,
                        linePriceCents = 599,
                    ),
                    ReceiptItemDraft(
                        food = FoodCandidate(name = "Paper towels", quantity = "2 pack", category = "Household"),
                        disposition = ReceiptItemDisposition.HOUSEHOLD,
                        linePriceCents = 899,
                    ),
                    ReceiptItemDraft(
                        food = FoodCandidate(name = "Coupon", quantity = ""),
                        disposition = ReceiptItemDisposition.IGNORE,
                        linePriceCents = 200,
                    ),
                ),
                merchant = "Target",
                purchasedAtMillis = 1_784_428_800_000L,
                subtotalCents = 1498,
                taxCents = 96,
                totalCents = 1594,
                currencyCode = "USD",
                receiptId = 42,
            ),
            FoodDraftCommandOrigin.RECEIPT,
        ).forEach { executor.execute(it) }

        val snapshot = repository.snapshot(householdId)
        val purchase = snapshot?.purchases?.single()
        val lines = snapshot?.purchaseLines.orEmpty().sortedBy { it.displayName }
        val lotsByLine = snapshot?.inventoryLots.orEmpty().associateBy { it.purchaseLineId }
        val itemsById = snapshot?.items.orEmpty().associateBy { it.metadata.id }

        assertEquals(PurchaseStatus.REVIEWED, purchase?.status)
        assertEquals(1498L, purchase?.subtotal?.minorUnits)
        assertEquals(96L, purchase?.tax?.minorUnits)
        assertEquals(1594L, purchase?.total?.minorUnits)
        assertEquals(1, purchase?.receiptAttachmentIds?.size)
        assertEquals(3, lines.size)
        assertEquals(2, snapshot?.inventoryLots?.size)
        assertEquals(2, snapshot?.items?.size)

        val ignored = lines.single { it.displayName == "Coupon" }
        assertEquals(PurchaseLineDisposition.IGNORED, ignored.disposition)
        assertEquals(null, ignored.itemId)
        assertEquals(null, ignored.inventoryLotId)

        val foodLine = lines.single { it.displayName == "Greek yogurt" }
        val foodLot = lotsByLine[foodLine.metadata.id]
        assertEquals(PurchaseLineDisposition.INVENTORY, foodLine.disposition)
        assertEquals(foodLot?.metadata?.id, foodLine.inventoryLotId)
        assertEquals(ItemKind.FOOD, foodLine.itemId?.let(itemsById::get)?.kind)
        assertEquals("Dairy", foodLine.spendCategory)
        assertEquals(599L, foodLine.finalAmount?.minorUnits)

        val householdLine = lines.single { it.displayName == "Paper towels" }
        val householdLot = lotsByLine[householdLine.metadata.id]
        assertEquals(PurchaseLineDisposition.INVENTORY, householdLine.disposition)
        assertEquals(householdLot?.metadata?.id, householdLine.inventoryLotId)
        assertEquals(ItemKind.HOUSEHOLD, householdLine.itemId?.let(itemsById::get)?.kind)
        assertEquals("Household", householdLine.spendCategory)
        assertEquals(899L, householdLine.finalAmount?.minorUnits)
    }

    @Test
    fun sameReceiptLinesOnDifferentTripsCreateDistinctCanonicalPurchaseIdentities() {
        val first = mapper.toCommands(
            receiptTripDraft(merchant = "Patel Brothers", purchasedAtMillis = 1_784_342_400_000L),
            FoodDraftCommandOrigin.RECEIPT,
        )
        val second = mapper.toCommands(
            receiptTripDraft(merchant = "Costco", purchasedAtMillis = 1_784_428_800_000L),
            FoodDraftCommandOrigin.RECEIPT,
        )

        val firstPurchase = first[0] as HouseholdCommand.UpsertPurchase
        val secondPurchase = second[0] as HouseholdCommand.UpsertPurchase
        val firstLine = first[3] as HouseholdCommand.UpsertPurchaseLine
        val secondLine = second[3] as HouseholdCommand.UpsertPurchaseLine

        assertTrue(firstPurchase.purchase.metadata.id != secondPurchase.purchase.metadata.id)
        assertTrue(firstPurchase.record.commandId != secondPurchase.record.commandId)
        assertTrue(firstLine.line.metadata.id != secondLine.line.metadata.id)
        assertTrue(firstLine.record.commandId != secondLine.record.commandId)
    }

    @Test
    fun supportedReviewedDraftOriginsCarryCanonicalSourceLabels() {
        val draft = GroceryDraft(listOf(FoodCandidate(name = "Batteries", quantity = "4 pack")))

        val manual = mapper.toCommands(draft, FoodDraftCommandOrigin.MANUAL_SAVE).single()
        val voice = mapper.toCommands(draft, FoodDraftCommandOrigin.VOICE_AUTO_ACCEPT).single()

        assertEquals("manual", manual.record.source.label)
        assertEquals("voice_auto_accept", voice.record.source.label)
    }

    @Test
    fun recipeDraftCreatesCanonicalRecipeAndIngredientCommands() {
        val commands = mapper.toCommands(
            RecipeDraft(
                titleText = "Dal",
                ingredientsText = "1 cup lentils\n2 cloves garlic",
                stepsText = "cook",
                servings = 4,
                prepMinutes = 10,
                tags = "Dinner, vegetarian",
            ),
            FoodDraftCommandOrigin.AI_REVIEW,
        )

        val recipe = commands[0] as HouseholdCommand.UpsertRecipe
        val lentils = commands[1] as HouseholdCommand.UpsertRecipeIngredient
        val garlic = commands[2] as HouseholdCommand.UpsertRecipeIngredient
        val step = commands[3] as HouseholdCommand.UpsertRecipeStep
        assertEquals("Dal", recipe.recipe.name)
        assertEquals("Ingredients: 1 cup lentils\n2 cloves garlic\nSteps: cook", recipe.recipe.description)
        assertEquals(setOf("dinner", "vegetarian"), recipe.recipe.tags)
        assertEquals("4", recipe.recipe.yield.amount?.value)
        assertEquals(QuantityUnit.SERVING, recipe.recipe.yield.unit)
        assertEquals(10, recipe.recipe.prepMinutes)
        assertEquals(RecipeStatus.ACTIVE, recipe.recipe.status)
        assertEquals(listOf(lentils.ingredient.metadata.id, garlic.ingredient.metadata.id), recipe.recipe.ingredientIds)
        assertEquals(listOf(step.step.metadata.id), recipe.recipe.stepIds)
        assertEquals(recipe.recipe.metadata.id, lentils.ingredient.recipeId)
        assertEquals("1 cup lentils", lentils.ingredient.originalText)
        assertEquals("1", lentils.ingredient.quantity.amount?.value)
        assertEquals(QuantityUnit.CUP, lentils.ingredient.quantity.unit)
        assertEquals("2 cloves garlic", garlic.ingredient.originalText)
        assertEquals("2", garlic.ingredient.quantity.amount?.value)
        assertEquals(QuantityUnit("clove"), garlic.ingredient.quantity.unit)
        assertEquals("UpsertRecipeIngredient", garlic.record.type)
        assertEquals(recipe.recipe.metadata.id, step.step.recipeId)
        assertEquals("cook", step.step.instruction)
        assertEquals(0, step.step.order)
        assertEquals("UpsertRecipeStep", step.record.type)
    }

    @Test
    fun acceptedRecipeDraftCommandsPersistRecipeIngredientsInCanonicalRepository() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, WonderFoodDatabase::class.java).build()
        val repository = HouseholdRepositories.room(requireNotNull(database))
        val executor = HouseholdCommandExecutor(repository)
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        val now = UtcTimestamp(1_784_520_000_000L)

        executor.execute(
            HouseholdCommand.UpsertHousehold(
                record = CommandRecord(
                    commandId = CommandId("00000000-0000-0000-0000-000000000601"),
                    householdId = householdId,
                    type = "UpsertHousehold",
                    source = SourceRef(SourceKind.SYSTEM, "test"),
                    requestedAt = now,
                    appliedAt = now,
                    affectedEntityIds = emptyList(),
                ),
                household = Household(
                    id = householdId,
                    name = "Test",
                    defaultCurrency = "USD",
                    timezone = "America/New_York",
                    locale = "en-US",
                    activeDataHome = DataHomeKind.LOCAL,
                    schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
                    createdAt = now,
                    updatedAt = now,
                    revision = 1,
                ),
            ),
        )
        mapper.toCommands(
            RecipeDraft(
                titleText = "Dal",
                ingredientsText = "1 cup lentils\n2 cloves garlic",
                stepsText = "cook",
            ),
            FoodDraftCommandOrigin.AI_REVIEW,
        ).forEach { executor.execute(it) }

        val snapshot = repository.snapshot(householdId)

        assertEquals("Dal", snapshot?.recipes?.single()?.name)
        assertEquals(2, snapshot?.recipeIngredients?.size)
        assertEquals(1, snapshot?.recipeSteps?.size)
        assertEquals(
            snapshot?.recipeIngredients?.map { it.metadata.id },
            snapshot?.recipes?.single()?.ingredientIds,
        )
        assertEquals(
            snapshot?.recipeSteps?.map { it.metadata.id },
            snapshot?.recipes?.single()?.stepIds,
        )
        assertEquals(listOf("1 cup lentils", "2 cloves garlic"), snapshot?.recipeIngredients?.map { it.originalText })
        assertEquals(listOf("cook"), snapshot?.recipeSteps?.map { it.instruction })
    }

    @Test
    fun mealLogDraftCreatesCanonicalMealEntryCommand() {
        val commands = mapper.toCommands(
            MealLogDraft(
                titleText = "Rice bowl lunch",
                calories = 520,
                proteinGrams = 24.0,
                mealSlot = MealSlot.LUNCH,
                usedItemsText = "rice, spinach",
                loggedDateEpochDay = 20_652L,
                source = "google_sheets_workspace_import",
            ),
            FoodDraftCommandOrigin.AI_REVIEW,
        )

        val entry = commands[0] as HouseholdCommand.UpsertMealEntry
        val nutrition = commands[1] as HouseholdCommand.UpsertNutritionSnapshot
        assertEquals(2, commands.size)
        assertEquals("Rice bowl lunch", entry.entry.title)
        assertEquals("Lunch", entry.entry.slot)
        assertEquals(MealEntryStatus.EATEN, entry.entry.status)
        assertEquals(20_652L, entry.entry.scheduledAt.epochMillis / 86_400_000L)
        assertEquals(QuantityUnit.SERVING, entry.entry.servings.unit)
        assertEquals(listOf(nutrition.snapshot.metadata.id), entry.entry.nutritionSnapshotIds)
        assertEquals(HouseholdEntityType.MEAL_ENTRY, nutrition.snapshot.subject.type)
        assertEquals(entry.entry.metadata.id, nutrition.snapshot.subject.id)
        assertEquals("520", nutrition.snapshot.values.energyKcal?.value)
        assertEquals("24", nutrition.snapshot.values.proteinGrams?.value)
        assertEquals("google_sheets_workspace_import", nutrition.snapshot.provider)
        assertTrue(entry.entry.notes.orEmpty().contains("Calories: 520"))
        assertTrue(entry.entry.notes.orEmpty().contains("Used: rice, spinach"))
    }

    @Test
    fun acceptedMealLogDraftPersistsNutritionSnapshotInCanonicalRepository() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, WonderFoodDatabase::class.java).build()
        val repository = HouseholdRepositories.room(requireNotNull(database))
        val executor = HouseholdCommandExecutor(repository)
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        val now = UtcTimestamp(1_784_520_000_000L)

        executor.executeTestHousehold(householdId, now)
        mapper.toCommands(
            MealLogDraft(
                titleText = "Rice bowl lunch",
                calories = 520,
                proteinGrams = 24.0,
                carbsGrams = 61.0,
                fatGrams = 18.0,
                mealSlot = MealSlot.LUNCH,
                loggedDateEpochDay = 20_652L,
                source = "ai_estimate_local",
            ),
            FoodDraftCommandOrigin.AI_REVIEW,
        ).forEach { executor.execute(it) }

        val snapshot = repository.snapshot(householdId)
        val entry = snapshot?.mealEntries?.single()
        val nutrition = snapshot?.nutritionSnapshots?.single()

        assertEquals(listOf(nutrition?.metadata?.id), entry?.nutritionSnapshotIds)
        assertEquals(HouseholdEntityType.MEAL_ENTRY, nutrition?.subject?.type)
        assertEquals(entry?.metadata?.id, nutrition?.subject?.id)
        assertEquals("520", nutrition?.values?.energyKcal?.value)
        assertEquals("24", nutrition?.values?.proteinGrams?.value)
        assertEquals("61", nutrition?.values?.carbohydrateGrams?.value)
        assertEquals("18", nutrition?.values?.fatGrams?.value)
        assertEquals("ai_estimate_local", nutrition?.provider)
    }

    @Test
    fun sameMealTitleOnDifferentDatesCreatesDistinctCanonicalMealEntries() {
        val first = mapper.toCommands(
            MealLogDraft(
                titleText = "Rice bowl lunch",
                mealSlot = MealSlot.LUNCH,
                loggedDateEpochDay = 20_652L,
            ),
            FoodDraftCommandOrigin.AI_REVIEW,
        ).single() as HouseholdCommand.UpsertMealEntry
        val second = mapper.toCommands(
            MealLogDraft(
                titleText = "Rice bowl lunch",
                mealSlot = MealSlot.LUNCH,
                loggedDateEpochDay = 20_653L,
            ),
            FoodDraftCommandOrigin.AI_REVIEW,
        ).single() as HouseholdCommand.UpsertMealEntry

        assertTrue(first.entry.metadata.id != second.entry.metadata.id)
        assertTrue(first.record.commandId != second.record.commandId)
        assertEquals(20_652L, first.entry.scheduledAt.epochMillis / 86_400_000L)
        assertEquals(20_653L, second.entry.scheduledAt.epochMillis / 86_400_000L)
    }

    @Test
    fun repeatedMealLogDraftTitlesPersistAsSeparateCanonicalEntries() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, WonderFoodDatabase::class.java).build()
        val repository = HouseholdRepositories.room(requireNotNull(database))
        val executor = HouseholdCommandExecutor(repository)
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        val now = UtcTimestamp(1_784_520_000_000L)

        executor.executeTestHousehold(householdId, now)
        listOf(20_652L, 20_653L).forEach { epochDay ->
            mapper.toCommands(
                MealLogDraft(
                    titleText = "Rice bowl lunch",
                    mealSlot = MealSlot.LUNCH,
                    loggedDateEpochDay = epochDay,
                ),
                FoodDraftCommandOrigin.AI_REVIEW,
            ).forEach { executor.execute(it) }
        }

        val snapshot = repository.snapshot(householdId)

        assertEquals(2, snapshot?.mealEntries?.size)
        assertEquals(listOf(20_652L, 20_653L), snapshot?.mealEntries?.map { it.scheduledAt.epochMillis / 86_400_000L }?.sorted())
        assertEquals(2, snapshot?.commandRecords?.filter { it.type == "UpsertMealEntry" }?.size)
    }

    @Test
    fun unsupportedDraftsDoNotMutateCanonicalDomainDirectly() {
        val commands = mapper.toCommands(
            LinkActionDraft(actionType = "archive", targetKind = "inventory", targetRef = "lot-1"),
            FoodDraftCommandOrigin.AI_REVIEW,
        )

        assertTrue(commands.isEmpty())
    }

    @Test
    fun mealPlanDraftCreatesCanonicalPlanAndLinkedEntries() {
        val commands = mapper.toCommands(
            MealPlanDraft(
                titleText = "Week plan",
                daysText = "Monday dinner: dal",
                groceryHint = "lentils",
                entries = listOf(
                    MealPlanEntryDraft(dayOffset = 0, slot = MealSlot.DINNER, title = "Dal", calorieTarget = 650),
                    MealPlanEntryDraft(dayOffset = 1, slot = MealSlot.LUNCH, title = "Rice bowl"),
                ),
                startDateEpochDay = 20_654L,
            ),
            FoodDraftCommandOrigin.AI_REVIEW,
        )

        val plan = commands[0] as HouseholdCommand.UpsertMealPlan
        val firstEntry = commands[1] as HouseholdCommand.UpsertMealEntry
        val secondEntry = commands[2] as HouseholdCommand.UpsertMealEntry
        val groceryGap = commands[3] as HouseholdCommand.UpsertShoppingLine
        assertEquals(4, commands.size)
        assertEquals("Week plan", plan.plan.name)
        assertEquals("2026-07-20", plan.plan.startsOn.value)
        assertEquals("2026-07-21", plan.plan.endsOn.value)
        assertEquals(MealPlanStatus.ACTIVE, plan.plan.status)
        assertEquals(plan.plan.metadata.id, firstEntry.entry.mealPlanId)
        assertEquals("Dinner", firstEntry.entry.slot)
        assertEquals("Dal", firstEntry.entry.title)
        assertEquals(MealEntryStatus.PLANNED, firstEntry.entry.status)
        assertTrue(firstEntry.entry.notes.orEmpty().contains("Calorie target: 650"))
        assertEquals(plan.plan.metadata.id, secondEntry.entry.mealPlanId)
        assertEquals(20_655L, secondEntry.entry.scheduledAt.epochMillis / 86_400_000L)
        assertEquals("lentils", groceryGap.line.displayName)
        assertEquals("Meal plan", groceryGap.line.category)
        assertEquals(ShoppingReason.RECIPE_GAP, groceryGap.line.reason)
        assertEquals(
            listOf(plan.plan.metadata.id, firstEntry.entry.metadata.id, secondEntry.entry.metadata.id),
            groceryGap.line.sourceEntityIds,
        )
    }

    @Test
    fun acceptedMealPlanDraftPersistsRecipeGapForCanonicalCartPreview() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, WonderFoodDatabase::class.java).build()
        val repository = HouseholdRepositories.room(requireNotNull(database))
        val executor = HouseholdCommandExecutor(repository)
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        val now = UtcTimestamp(1_784_520_000_000L)

        executor.execute(
            HouseholdCommand.UpsertHousehold(
                record = CommandRecord(
                    commandId = CommandId("00000000-0000-0000-0000-000000000602"),
                    householdId = householdId,
                    type = "UpsertHousehold",
                    source = SourceRef(SourceKind.SYSTEM, "test"),
                    requestedAt = now,
                    appliedAt = now,
                    affectedEntityIds = emptyList(),
                ),
                household = Household(
                    id = householdId,
                    name = "Test",
                    defaultCurrency = "USD",
                    timezone = "America/New_York",
                    locale = "en-US",
                    activeDataHome = DataHomeKind.LOCAL,
                    schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
                    createdAt = now,
                    updatedAt = now,
                    revision = 1,
                ),
            ),
        )
        mapper.toCommands(
            MealPlanDraft(
                titleText = "Week plan",
                daysText = "Monday dinner: dal",
                groceryHint = "1 cup lentils",
                entries = listOf(MealPlanEntryDraft(dayOffset = 0, slot = MealSlot.DINNER, title = "Dal")),
                startDateEpochDay = 20_654L,
            ),
            FoodDraftCommandOrigin.AI_REVIEW,
        ).forEach { executor.execute(it) }

        val snapshot = repository.snapshot(householdId)
        val line = snapshot?.shoppingLines?.single()
        val preview = CanonicalCartPreviewItem.fromSnapshot(snapshot)

        assertEquals("lentils", line?.displayName)
        assertEquals("1", line?.quantity?.amount?.value)
        assertEquals(QuantityUnit.CUP, line?.quantity?.unit)
        assertEquals(ShoppingReason.RECIPE_GAP, line?.reason)
        assertEquals(
            listOf(snapshot?.mealPlans?.single()?.metadata?.id, snapshot?.mealEntries?.single()?.metadata?.id),
            line?.sourceEntityIds,
        )
        assertEquals("lentils", preview.single().title)
        assertEquals("1 cup  Meal plan  recipe gap", preview.single().subtitle)
    }

    @Test
    fun sameMealPlanTitleOnDifferentWeeksCreatesDistinctCanonicalPlans() {
        val first = mapper.toCommands(
            MealPlanDraft(
                titleText = "Week plan",
                daysText = "Monday dinner: dal",
                groceryHint = "lentils",
                entries = listOf(MealPlanEntryDraft(dayOffset = 0, slot = MealSlot.DINNER, title = "Dal")),
                startDateEpochDay = 20_654L,
            ),
            FoodDraftCommandOrigin.AI_REVIEW,
        )
        val second = mapper.toCommands(
            MealPlanDraft(
                titleText = "Week plan",
                daysText = "Monday dinner: dal",
                groceryHint = "lentils",
                entries = listOf(MealPlanEntryDraft(dayOffset = 0, slot = MealSlot.DINNER, title = "Dal")),
                startDateEpochDay = 20_661L,
            ),
            FoodDraftCommandOrigin.AI_REVIEW,
        )

        val firstPlan = first[0] as HouseholdCommand.UpsertMealPlan
        val firstEntry = first[1] as HouseholdCommand.UpsertMealEntry
        val firstGap = first[2] as HouseholdCommand.UpsertShoppingLine
        val secondPlan = second[0] as HouseholdCommand.UpsertMealPlan
        val secondEntry = second[1] as HouseholdCommand.UpsertMealEntry
        val secondGap = second[2] as HouseholdCommand.UpsertShoppingLine

        assertTrue(firstPlan.plan.metadata.id != secondPlan.plan.metadata.id)
        assertTrue(firstPlan.record.commandId != secondPlan.record.commandId)
        assertTrue(firstEntry.entry.metadata.id != secondEntry.entry.metadata.id)
        assertTrue(firstGap.line.metadata.id != secondGap.line.metadata.id)
        assertEquals(firstPlan.plan.metadata.id, firstEntry.entry.mealPlanId)
        assertEquals(secondPlan.plan.metadata.id, secondEntry.entry.mealPlanId)
        assertEquals(listOf(firstPlan.plan.metadata.id, firstEntry.entry.metadata.id), firstGap.line.sourceEntityIds)
        assertEquals(listOf(secondPlan.plan.metadata.id, secondEntry.entry.metadata.id), secondGap.line.sourceEntityIds)
    }

    @Test
    fun repeatedMealPlanDraftTitlesPersistAsSeparateCanonicalWeeks() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, WonderFoodDatabase::class.java).build()
        val repository = HouseholdRepositories.room(requireNotNull(database))
        val executor = HouseholdCommandExecutor(repository)
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        val now = UtcTimestamp(1_784_520_000_000L)

        executor.executeTestHousehold(householdId, now)
        listOf(20_654L, 20_661L).forEach { startEpochDay ->
            mapper.toCommands(
                MealPlanDraft(
                    titleText = "Week plan",
                    daysText = "Monday dinner: dal",
                    groceryHint = "lentils",
                    entries = listOf(MealPlanEntryDraft(dayOffset = 0, slot = MealSlot.DINNER, title = "Dal")),
                    startDateEpochDay = startEpochDay,
                ),
                FoodDraftCommandOrigin.AI_REVIEW,
            ).forEach { executor.execute(it) }
        }

        val snapshot = repository.snapshot(householdId)

        assertEquals(2, snapshot?.mealPlans?.size)
        assertEquals(2, snapshot?.mealEntries?.size)
        assertEquals(2, snapshot?.shoppingLines?.size)
        assertEquals(2, snapshot?.mealEntries?.map { it.mealPlanId }?.distinct()?.size)
        assertEquals(2, snapshot?.commandRecords?.filter { it.type == "UpsertMealPlan" }?.size)
    }

    private fun receiptTripDraft(merchant: String, purchasedAtMillis: Long) = ReceiptDraft(
        items = listOf(
            ReceiptItemDraft(
                food = FoodCandidate(name = "Paper towels", quantity = "2 pack"),
                disposition = ReceiptItemDisposition.HOUSEHOLD,
                linePriceCents = 899,
            ),
        ),
        merchant = merchant,
        purchasedAtMillis = purchasedAtMillis,
        currencyCode = "USD",
        totalCents = 962,
    )

    private suspend fun HouseholdCommandExecutor.executeTestHousehold(
        householdId: com.wonderfood.core.model.household.HouseholdId,
        now: UtcTimestamp,
    ) {
        execute(
            HouseholdCommand.UpsertHousehold(
                record = CommandRecord(
                    commandId = CommandId("00000000-0000-0000-0000-000000000603"),
                    householdId = householdId,
                    type = "UpsertHousehold",
                    source = SourceRef(SourceKind.SYSTEM, "test"),
                    requestedAt = now,
                    appliedAt = now,
                    affectedEntityIds = emptyList(),
                ),
                household = Household(
                    id = householdId,
                    name = "Test",
                    defaultCurrency = "USD",
                    timezone = "America/New_York",
                    locale = "en-US",
                    activeDataHome = DataHomeKind.LOCAL,
                    schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
                    createdAt = now,
                    updatedAt = now,
                    revision = 1,
                ),
            ),
        )
    }
}
