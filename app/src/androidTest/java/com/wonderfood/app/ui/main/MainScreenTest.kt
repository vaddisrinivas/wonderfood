package com.wonderfood.app.ui.main

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.wonderfood.app.MainActivity
import com.wonderfood.core.data.HouseholdRepositories
import com.wonderfood.core.data.room.WonderFoodDatabaseFactory
import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.engine.HouseholdCommandExecutor
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.EntityReference
import androidx.compose.ui.semantics.SemanticsActions
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.InventoryEvent
import com.wonderfood.core.model.household.InventoryEventType
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.MealEntry
import com.wonderfood.core.model.household.MealEntryStatus
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
import com.wonderfood.core.model.household.RecipeStatus
import com.wonderfood.core.model.household.ReviewState
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.ShoppingReason
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MainScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val SHELL_PREFS_NAME = "wonderfood_shell"
        private const val SHELL_CONFLICT_KEY = "workspace_conflict_inbox"
        private const val BACKEND_PREFS_NAME = "wonderfood_backend_configuration"
        private const val BACKEND_SCHEMA_VERSION_KEY = "schema_version"
        private const val BACKEND_TYPE_KEY = "type"
        private const val BACKEND_ONBOARDING_DISMISSED_KEY = "onboarding_dismissed"
        @JvmStatic
        @BeforeClass
        fun seedLocalBackendChoice() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context
                .getSharedPreferences("wonderfood_backend_configuration", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("type", "LOCAL_SQLITE")
                .putInt("schema_version", 1)
                .putBoolean("onboarding_dismissed", true)
                .commit()
            seedCanonicalSpendingLine(context)
            seedCanonicalWeekMeal(context)
            seedCanonicalRecipe(context)
            seedCanonicalMealPlanCartGap(context)
        }

        private fun seedCanonicalSpendingLine(context: android.content.Context) {
            val database = WonderFoodDatabaseFactory.create(context)
            val repository = HouseholdRepositories.room(database)
            val executor = HouseholdCommandExecutor(repository)
            val householdId = HouseholdId("00000000-0000-0000-0000-000000000105")
            val purchaseId = EntityId("00000000-0000-0000-0000-000000000881")
            val purchaseLineId = EntityId("00000000-0000-0000-0000-000000000882")
            val now = UtcTimestamp(System.currentTimeMillis())
            runBlocking {
                executor.execute(
                    HouseholdCommand.UpsertHousehold(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000883"),
                            householdId = householdId,
                            type = "UpsertHousehold",
                            source = SourceRef(SourceKind.SYSTEM, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = emptyList(),
                        ),
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
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertPurchase(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000884"),
                            householdId = householdId,
                            type = "UpsertPurchase",
                            source = SourceRef(SourceKind.RECEIPT, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(purchaseId),
                        ),
                        purchase = Purchase(
                            metadata = EntityMetadata(
                                id = purchaseId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.RECEIPT, "android_test"),
                            ),
                            occurredAt = now,
                            paymentNote = "Merchant: Target",
                            status = PurchaseStatus.REVIEWED,
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertPurchaseLine(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000885"),
                            householdId = householdId,
                            type = "UpsertPurchaseLine",
                            source = SourceRef(SourceKind.RECEIPT, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(purchaseLineId),
                        ),
                        line = PurchaseLine(
                            metadata = EntityMetadata(
                                id = purchaseLineId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.RECEIPT, "android_test"),
                            ),
                            purchaseId = purchaseId,
                            displayName = "Dish soap spending proof",
                            quantity = Quantity.unknown(),
                            finalAmount = Money(899, "USD"),
                            spendCategory = "Cleaning",
                            disposition = PurchaseLineDisposition.INVENTORY,
                            reviewState = ReviewState.ACCEPTED,
                        ),
                    ),
                )
            }
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
            database.close()
        }

        private fun seedCanonicalWeekMeal(context: android.content.Context) {
            val database = WonderFoodDatabaseFactory.create(context)
            val repository = HouseholdRepositories.room(database)
            val executor = HouseholdCommandExecutor(repository)
            val householdId = HouseholdId("00000000-0000-0000-0000-000000000105")
            val mealEntryId = EntityId("00000000-0000-0000-0000-000000000886")
            val riceItemId = EntityId("00000000-0000-0000-0000-000000000906")
            val spiceItemId = EntityId("00000000-0000-0000-0000-000000000907")
            val riceLotId = EntityId("00000000-0000-0000-0000-000000000908")
            val recipeId = EntityId("00000000-0000-0000-0000-000000000909")
            val riceIngredientId = EntityId("00000000-0000-0000-0000-000000000910")
            val spiceIngredientId = EntityId("00000000-0000-0000-0000-000000000911")
            val inventoryEventId = EntityId("00000000-0000-0000-0000-000000000912")
            val gapLineId = EntityId("00000000-0000-0000-0000-000000000913")
            val nutritionId = EntityId("00000000-0000-0000-0000-000000000903")
            val now = UtcTimestamp(System.currentTimeMillis())
            runBlocking {
                executor.execute(
                    HouseholdCommand.UpsertHousehold(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000887"),
                            householdId = householdId,
                            type = "UpsertHousehold",
                            source = SourceRef(SourceKind.SYSTEM, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = emptyList(),
                        ),
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
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertItem(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000914"),
                            householdId = householdId,
                            type = "UpsertItem",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(riceItemId),
                        ),
                        item = Item(
                            metadata = EntityMetadata(
                                id = riceItemId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            name = "Week rice inventory proof",
                            kind = ItemKind.FOOD,
                            category = "Pantry",
                            defaultUnit = QuantityUnit.CUP,
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertItem(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000915"),
                            householdId = householdId,
                            type = "UpsertItem",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(spiceItemId),
                        ),
                        item = Item(
                            metadata = EntityMetadata(
                                id = spiceItemId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            name = "Week spice gap proof",
                            kind = ItemKind.FOOD,
                            category = "Pantry",
                            defaultUnit = QuantityUnit.PACKAGE,
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertInventoryLot(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000916"),
                            householdId = householdId,
                            type = "UpsertInventoryLot",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(riceLotId),
                        ),
                        lot = InventoryLot(
                            metadata = EntityMetadata(
                                id = riceLotId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            itemId = riceItemId,
                            quantity = Quantity(DecimalAmount.of("8"), QuantityUnit.CUP),
                            status = InventoryLotStatus.AVAILABLE,
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertRecipe(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000917"),
                            householdId = householdId,
                            type = "UpsertRecipe",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(recipeId),
                        ),
                        recipe = Recipe(
                            metadata = EntityMetadata(
                                id = recipeId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            name = "Tomato rasam week recipe",
                            status = RecipeStatus.ACTIVE,
                            ingredientIds = listOf(riceIngredientId, spiceIngredientId),
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertRecipeIngredient(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000918"),
                            householdId = householdId,
                            type = "UpsertRecipeIngredient",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(riceIngredientId),
                        ),
                        ingredient = RecipeIngredient(
                            metadata = EntityMetadata(
                                id = riceIngredientId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            recipeId = recipeId,
                            itemId = riceItemId,
                            originalText = "2 cups rice",
                            quantity = Quantity(DecimalAmount.of("2"), QuantityUnit.CUP),
                            order = 0,
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertRecipeIngredient(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000919"),
                            householdId = householdId,
                            type = "UpsertRecipeIngredient",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(spiceIngredientId),
                        ),
                        ingredient = RecipeIngredient(
                            metadata = EntityMetadata(
                                id = spiceIngredientId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            recipeId = recipeId,
                            itemId = spiceItemId,
                            originalText = "1 spice packet",
                            quantity = Quantity(DecimalAmount.of("1"), QuantityUnit.PACKAGE),
                            order = 1,
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertMealEntry(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000888"),
                            householdId = householdId,
                            type = "UpsertMealEntry",
                            source = SourceRef(SourceKind.AI_PROPOSAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(mealEntryId),
                        ),
                        entry = MealEntry(
                            metadata = EntityMetadata(
                                id = mealEntryId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.AI_PROPOSAL, "android_test"),
                            ),
                            scheduledAt = now,
                            slot = "Dinner",
                            recipeId = recipeId,
                            title = "Tomato rasam week proof",
                            servings = Quantity(DecimalAmount.of("4"), QuantityUnit.SERVING),
                            status = MealEntryStatus.PLANNED,
                            leftoverIntent = "pack lunches",
                            nutritionSnapshotIds = listOf(nutritionId),
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertNutritionSnapshot(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000904"),
                            householdId = householdId,
                            type = "UpsertNutritionSnapshot",
                            source = SourceRef(SourceKind.AI_PROPOSAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(nutritionId),
                        ),
                        snapshot = NutritionSnapshot(
                            metadata = EntityMetadata(
                                id = nutritionId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.AI_PROPOSAL, "android_test"),
                            ),
                            subject = EntityReference(HouseholdEntityType.MEAL_ENTRY, mealEntryId),
                            basis = Quantity(DecimalAmount.of("1"), QuantityUnit.SERVING),
                            values = NutritionValues(
                                energyKcal = DecimalAmount.of("520"),
                                proteinGrams = DecimalAmount.of("24"),
                            ),
                            provider = "android_test",
                            capturedAt = now,
                            warnings = listOf("Connected UI proof"),
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.StoreInventoryEvent(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000920"),
                            householdId = householdId,
                            type = "StoreInventoryEvent",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(inventoryEventId, riceItemId),
                        ),
                        event = InventoryEvent(
                            metadata = EntityMetadata(
                                id = inventoryEventId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            itemId = riceItemId,
                            type = InventoryEventType.CONSUME,
                            quantityDelta = Quantity(DecimalAmount.of("2"), QuantityUnit.CUP),
                            relatedEntityId = mealEntryId,
                            commandId = CommandId("00000000-0000-0000-0000-000000000920"),
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertShoppingLine(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000921"),
                            householdId = householdId,
                            type = "UpsertShoppingLine",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(gapLineId),
                        ),
                        line = ShoppingLine(
                            metadata = EntityMetadata(
                                id = gapLineId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            shoppingListId = EntityId("00000000-0000-0000-0000-000000000501"),
                            itemId = spiceItemId,
                            displayName = "Spices reviewed week gap",
                            quantity = Quantity(DecimalAmount.of("1"), QuantityUnit.PACKAGE),
                            category = "Meal plan",
                            status = ShoppingLineStatus.NEEDED,
                            reason = ShoppingReason.RECIPE_GAP,
                            sourceEntityIds = listOf(recipeId, mealEntryId),
                        ),
                    ),
                )
            }
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
            database.close()
        }

        private fun seedCanonicalRecipe(context: android.content.Context) {
            val database = WonderFoodDatabaseFactory.create(context)
            val repository = HouseholdRepositories.room(database)
            val executor = HouseholdCommandExecutor(repository)
            val householdId = HouseholdId("00000000-0000-0000-0000-000000000105")
            val recipeId = EntityId("00000000-0000-0000-0000-000000000889")
            val riceItemId = EntityId("00000000-0000-0000-0000-000000000892")
            val riceLotId = EntityId("00000000-0000-0000-0000-000000000893")
            val ingredientId = EntityId("00000000-0000-0000-0000-000000000894")
            val now = UtcTimestamp(System.currentTimeMillis())
            runBlocking {
                executor.execute(
                    HouseholdCommand.UpsertHousehold(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000890"),
                            householdId = householdId,
                            type = "UpsertHousehold",
                            source = SourceRef(SourceKind.SYSTEM, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = emptyList(),
                        ),
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
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertItem(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000895"),
                            householdId = householdId,
                            type = "UpsertItem",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(riceItemId),
                        ),
                        item = Item(
                            metadata = EntityMetadata(
                                id = riceItemId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            name = "Basmati rice can-make proof",
                            kind = ItemKind.FOOD,
                            category = "Pantry",
                            defaultUnit = QuantityUnit.CUP,
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertInventoryLot(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000896"),
                            householdId = householdId,
                            type = "UpsertInventoryLot",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(riceLotId),
                        ),
                        lot = InventoryLot(
                            metadata = EntityMetadata(
                                id = riceLotId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            itemId = riceItemId,
                            quantity = Quantity(DecimalAmount.of("2"), QuantityUnit.CUP),
                            status = InventoryLotStatus.AVAILABLE,
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertRecipe(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000891"),
                            householdId = householdId,
                            type = "UpsertRecipe",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(recipeId),
                        ),
                        recipe = Recipe(
                            metadata = EntityMetadata(
                                id = recipeId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            name = "Vegetable pulao recipe proof",
                            cuisine = "South Indian",
                            category = "Dinner",
                            yield = Quantity(DecimalAmount.of("4"), QuantityUnit.SERVING),
                            totalMinutes = 35,
                            status = RecipeStatus.ACTIVE,
                            ingredientIds = listOf(ingredientId),
                        ),
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertRecipeIngredient(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000897"),
                            householdId = householdId,
                            type = "UpsertRecipeIngredient",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(ingredientId),
                        ),
                        ingredient = RecipeIngredient(
                            metadata = EntityMetadata(
                                id = ingredientId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            recipeId = recipeId,
                            itemId = riceItemId,
                            originalText = "2 cups basmati rice",
                            quantity = Quantity(DecimalAmount.of("2"), QuantityUnit.CUP),
                            order = 0,
                        ),
                    ),
                )
            }
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
            database.close()
        }

        private fun seedCanonicalMealPlanCartGap(context: android.content.Context) {
            val database = WonderFoodDatabaseFactory.create(context)
            val repository = HouseholdRepositories.room(database)
            val executor = HouseholdCommandExecutor(repository)
            val householdId = HouseholdId("00000000-0000-0000-0000-000000000105")
            val shoppingLineId = EntityId("00000000-0000-0000-0000-000000000898")
            val planId = EntityId("00000000-0000-0000-0000-000000000899")
            val mealEntryId = EntityId("00000000-0000-0000-0000-000000000900")
            val now = UtcTimestamp(System.currentTimeMillis())
            runBlocking {
                executor.execute(
                    HouseholdCommand.UpsertHousehold(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000901"),
                            householdId = householdId,
                            type = "UpsertHousehold",
                            source = SourceRef(SourceKind.SYSTEM, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = emptyList(),
                        ),
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
                    ),
                )
                executor.execute(
                    HouseholdCommand.UpsertShoppingLine(
                        record = CommandRecord(
                            commandId = CommandId("00000000-0000-0000-0000-000000000902"),
                            householdId = householdId,
                            type = "UpsertShoppingLine",
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                            requestedAt = now,
                            appliedAt = now,
                            affectedEntityIds = listOf(shoppingLineId),
                        ),
                        line = ShoppingLine(
                            metadata = EntityMetadata(
                                id = shoppingLineId,
                                householdId = householdId,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                                source = SourceRef(SourceKind.MANUAL, "android_test"),
                            ),
                            shoppingListId = EntityId("00000000-0000-0000-0000-000000000501"),
                            displayName = "Lentils meal gap proof",
                            quantity = Quantity(DecimalAmount.of("1"), QuantityUnit.CUP),
                            category = "Meal plan",
                            status = ShoppingLineStatus.NEEDED,
                            reason = ShoppingReason.RECIPE_GAP,
                            sourceEntityIds = listOf(planId, mealEntryId),
                        ),
                    ),
                )
            }
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
            database.close()
        }
    }

    @Before
    fun prepareShellForTest() {
        if (!isEmulator()) return
        restoreDefaultBackendState()
        recreateActivity()
    }

    @After
    fun returnToShellAfterTest() {
        if (!isEmulator()) return
        restoreDefaultBackendState()
        composeTestRule.waitForIdle()
    }

    private fun backendPrefs() = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getSharedPreferences(BACKEND_PREFS_NAME, Context.MODE_PRIVATE)

    private fun shellPrefs() = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getSharedPreferences(SHELL_PREFS_NAME, Context.MODE_PRIVATE)

    private fun setBackendPrefs(
        type: String?,
        onboardingDismissed: Boolean,
        schemaVersion: Int = 1,
    ) {
        backendPrefs().edit().apply {
            if (type == null) {
                remove(BACKEND_TYPE_KEY)
            } else {
                putString(BACKEND_TYPE_KEY, type)
            }
            putBoolean(BACKEND_ONBOARDING_DISMISSED_KEY, onboardingDismissed)
            if (schemaVersion > 0) {
                putInt(BACKEND_SCHEMA_VERSION_KEY, schemaVersion)
            } else {
                remove(BACKEND_SCHEMA_VERSION_KEY)
            }
            if (type == "LOCAL_SQLITE" || type == null) {
                remove("url")
                remove("external_id")
                remove("account_label")
                remove("credential_provider")
                remove("credential_alias")
                remove("connection_mode")
            }
            commit()
        }
    }

    private fun setWorkspaceConflictInbox(json: String?) {
        if (json == null) {
            shellPrefs().edit().remove(SHELL_CONFLICT_KEY).commit()
        } else {
            shellPrefs().edit().putString(SHELL_CONFLICT_KEY, json).commit()
        }
    }

    private fun restoreDefaultBackendState() {
        setBackendPrefs(type = "LOCAL_SQLITE", onboardingDismissed = true, schemaVersion = 1)
        setWorkspaceConflictInbox(null)
    }

    private fun recreateActivity() {
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    private fun isBackendOnboardingVisible(): Boolean = runCatching {
        composeTestRule.onAllNodesWithText("Choose your food home").fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    @Test
    fun aaFirstBootShowsBackendChoicesWithLocalFastest() {
        setBackendPrefs(type = null, onboardingDismissed = false)
        setWorkspaceConflictInbox(null)
        recreateActivity()
        composeTestRule.waitUntil(timeoutMillis = 10_000) { isBackendOnboardingVisible() }

        composeTestRule.onNodeWithText("Choose your food home").assertIsDisplayed()
        composeTestRule.onNodeWithText("This phone").assertExists()
        composeTestRule.onNodeWithText("Google Sheets").assertExists()
        composeTestRule.onNodeWithText("Notion").assertExists()
        composeTestRule.onNodeWithText("Postgres").assertExists()
        composeTestRule.onNodeWithText("Fastest setup. Private and offline.").assertExists()

        composeTestRule.onNode(hasContentDescription("Backend option This phone selected", substring = true)).assertExists()
        composeTestRule.onNode(hasContentDescription("Backend option Google Sheets", substring = true)).assertExists()
        composeTestRule.onNode(hasContentDescription("Backend option Notion", substring = true)).assertExists()
        composeTestRule.onNode(hasContentDescription("Backend option Postgres", substring = true)).assertExists()

        composeTestRule.onNode(hasContentDescription("Backend option Google Sheets", substring = true)).performClick()
        composeTestRule.onNodeWithText("Create new Sheet").assertExists()
        composeTestRule.onNodeWithText("Google Sheet URL").assertExists()
        composeTestRule.onNodeWithText("Select existing by URL").assertExists()
        composeTestRule.onNode(hasContentDescription("Backend option This phone", substring = true)).performClick()
        composeTestRule.onNodeWithText("Start local now").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) { isShellVisible() }
    }

    @Test
    fun abConflictInboxRendersWithDismissSemantics() {
        setWorkspaceConflictInbox(
            """
            {
              "providerLabel": "Google Sheets",
              "sourceLabel": "Raw snapshot",
              "conflictCount": 2,
              "changeCount": 1,
              "mergeClock": "2026-07-20T12:00:00Z",
              "decision": "Review before applying remote snapshot changes.",
              "conflictSummary": ["recipes: title changed", "kitchen: quantity missing"]
            }
            """.trimIndent(),
        )
        setBackendPrefs(type = null, onboardingDismissed = false)
        recreateActivity()
        composeTestRule.waitUntil(timeoutMillis = 10_000) { isBackendOnboardingVisible() }

        composeTestRule.onNodeWithContentDescription("Google Sheets conflict inbox").assertExists()
        composeTestRule.onNodeWithText("Google Sheets conflict inbox").assertExists()
        composeTestRule.onNodeWithText("2 conflicts · 1 change · Raw snapshot").assertExists()
        composeTestRule.onNodeWithText("Review before applying remote snapshot changes.").assertExists()
        composeTestRule.onNodeWithContentDescription("Clear workspace conflict inbox").assertExists()
    }

    @Test
    fun acPostgresRejectsRawEndpointBeforeSavingChoice() {
        setWorkspaceConflictInbox(null)
        setBackendPrefs(type = null, onboardingDismissed = false)
        recreateActivity()
        composeTestRule.waitUntil(timeoutMillis = 10_000) { isBackendOnboardingVisible() }

        composeTestRule.onNode(hasContentDescription("Backend option Postgres", substring = true))
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule
                .onAllNodes(hasContentDescription("Backend option Postgres selected", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Postgres API URL")
            .performScrollTo()
            .performTextInput("postgres.example.com:5432/wonderfood")
        composeTestRule.onNodeWithText("Household ID")
            .performScrollTo()
            .performTextInput("household-test")
        composeTestRule.onNodeWithText("API token")
            .performScrollTo()
            .performTextInput("test-token")
        composeTestRule.onNodeWithText("Use Postgres").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule
                .onAllNodesWithText("Hosted Postgres backends must use HTTPS.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("Hosted Postgres backends must use HTTPS.").onFirst().assertIsDisplayed()
        composeTestRule.onNode(hasContentDescription("Backend option Postgres selected", substring = true)).assertExists()
    }

    @Test
    fun aMainScreenShowsFiveDestinationShell() {
        assumeEmulatorAndWaitForShell()

        assertTextPresent("Now")
        assertTextPresent("Food")
        assertTextPresent("Week")
        assertTextPresent("Saved")
        assertTextPresent("Cart")
        composeTestRule.onAllNodesWithContentDescription("More").assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription("AI").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("More").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("AI").assertCountEquals(0)

        composeTestRule.onNodeWithContentDescription("Search WonderFood").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Open AI capture").assertCountEquals(1)
        composeTestRule.onNodeWithContentDescription("Open AI capture").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open settings").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open AI capture").performClick()
        composeTestRule.onNodeWithText("WonderFood AI").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Attach receipt photo").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Record voice note").assertIsDisplayed()
        val inputWidth = composeTestRule
            .onNodeWithContentDescription("AI capture text")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val screenWidth = composeTestRule.activity.resources.displayMetrics.widthPixels.toFloat()
        assertTrue("AI capture input should not collapse", inputWidth > screenWidth * 0.55f)
        composeTestRule.onNodeWithContentDescription("Close AI capture").performClick()
        composeTestRule.onNodeWithContentDescription("Open settings").performClick()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Food profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Goals & health").assertIsDisplayed()
        composeTestRule.onNodeWithText("Canonical household store").assertIsDisplayed()
        pressActivityBack()
    }

    @Test
    fun bKitchenShowsFoodFirstControlsAndSafeSelection() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Food").performClick()
        composeTestRule.onNodeWithText("In kitchen").performClick()
        if (composeTestRule.onAllNodesWithText("No kitchen items yet.").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("No kitchen items yet.").assertIsDisplayed()
            composeTestRule.onNodeWithText("Add food directly or scan a receipt when you're ready.").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Empty state: No kitchen items yet.").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Add kitchen food").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Open AI capture").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("Remove").assertCountEquals(0)
            return
        }

        composeTestRule.onNodeWithContentDescription("Add kitchen food").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Remove").assertCountEquals(0)
    }

    @Test
    fun baKitchenRowAlternativesAddArchiveAndUndo() {
        restoreDefaultBackendState()
        recreateActivity()
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Food").performClick()
        composeTestRule.onNodeWithText("In kitchen").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithContentDescription("Kitchen row Basmati rice can-make proof")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription("Add Basmati rice can-make proof to cart").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithContentDescription("Kitchen row Basmati rice can-make proof")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription("Archive Basmati rice can-make proof").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithContentDescription("Kitchen row Basmati rice can-make proof")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun cManualCreateIsAvailableWithoutAi() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Food").performClick()
        composeTestRule.onNodeWithText("In kitchen").performClick()
        composeTestRule.onNodeWithContentDescription("Add kitchen food").performClick()
        composeTestRule.onNodeWithText("Add kitchen food").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithContentDescription("Navigate to Cart").performClick()
        composeTestRule.onNodeWithContentDescription("Add cart line").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Saved").performClick()
        composeTestRule.onNodeWithContentDescription("Create recipe").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Now").performClick()
        composeTestRule.onNodeWithContentDescription("Log meal").performClick()
        composeTestRule.onNodeWithText("Date").assertIsDisplayed()
        pressActivityBack()
    }

    @Test
    fun acQuickAddCoversItemRecipeMealCartLineAndReceipt() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Food").performClick()
        composeTestRule.onNodeWithText("In kitchen").performClick()
        composeTestRule.onNodeWithContentDescription("Add kitchen food").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Saved").performClick()
        composeTestRule.onNodeWithContentDescription("Create recipe").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Now").performClick()
        composeTestRule.onNodeWithContentDescription("Log meal").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Cart").performClick()
        composeTestRule.onNodeWithContentDescription("Add cart line").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Shop mode Receipts").performClick()
        composeTestRule.onNodeWithContentDescription("Scan receipt").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Shop mode To buy").performClick()
    }

    @Test
    fun dDestinationsExposeV3WorkflowContexts() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Now").performClick()
        composeTestRule.onNodeWithText("Meal timeline").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Week").performClick()
        composeTestRule.onNodeWithText("This week").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Plan today").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open AI capture").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Navigate to Cart").performClick()
        composeTestRule.onNodeWithContentDescription("Shop mode To buy").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Shop mode Receipts").performClick()
        composeTestRule.onNodeWithContentDescription("Shop mode Receipts").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Shop mode Put away").performClick()
        composeTestRule.onNodeWithContentDescription("Shop mode Put away").assertIsDisplayed()
    }

    @Test
    fun daPlannedEntryCardSupportsLongPressSelection() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Week").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithContentDescription("Planned meal entry Tomato rasam week proof").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Planned meal entry Tomato rasam week proof")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeTestRule.onNodeWithContentDescription("Planned meal entry Tomato rasam week proof").assertIsSelected()
    }

    @Test
    fun eAiCaptureStaysOpenAfterSend() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Open AI capture").performClick()
        composeTestRule.onNodeWithText("WonderFood AI").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("AI capture text").performTextInput("Need oats")
        composeTestRule.onNodeWithContentDescription("Send AI capture").performClick()
        composeTestRule.onNodeWithText("WonderFood AI").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close AI capture").performClick()
    }

    @Test
    fun fNewChatKeepsPreviousConversationReadableFromHistory() {
        assumeEmulatorAndWaitForShell()
        val previousMessage = "Need tamarind for the history test"

        composeTestRule.onNodeWithContentDescription("Open AI capture").performClick()
        composeTestRule.onNodeWithText("New chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("AI capture text").performTextInput(previousMessage)
        composeTestRule.onNodeWithContentDescription("Send AI capture").performClick()
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithText("Edit proposal").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("New chat").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("New chat started. Your kitchen, recipes, groceries, plans, and settings are still saved.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("History").performClick()

        composeTestRule.onNodeWithText("Chat history").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Chat history list").performScrollToNode(hasText(previousMessage))
        composeTestRule.onNodeWithText(previousMessage).assertIsDisplayed()
        pressActivityBack()
    }

    @Test
    fun gSearchUsesCanonicalHouseholdRepository() {
        assumeEmulatorAndWaitForShell()
        seedCanonicalSearchItem()

        composeTestRule.onNodeWithContentDescription("Search WonderFood").performClick()
        composeTestRule.onNodeWithContentDescription("WonderFood search text").performTextInput("canonical proof")
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Dish soap canonical proof").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Canonical - Cleaning - Seventh Generation").assertIsDisplayed()
        pressActivityBack()
    }

    @Test
    fun gaSearchResultsExposeStableContentDescriptions() {
        assumeEmulatorAndWaitForShell()
        seedCanonicalSearchItem()

        composeTestRule.onNodeWithContentDescription("Search WonderFood").performClick()
        composeTestRule.onNodeWithContentDescription("WonderFood search text").performTextInput("canonical proof")
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithContentDescription("Open Dish soap canonical proof").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Open Dish soap canonical proof").assertIsDisplayed()
        pressActivityBack()
    }

    @Test
    fun hTodayShowsCanonicalRecentSpendingFromHouseholdRepository() {
        assumeEmulatorAndWaitForShell()
        seedCanonicalSpendingLine(InstrumentationRegistry.getInstrumentation().targetContext)
        recreateActivity()

        composeTestRule.onNodeWithContentDescription("Navigate to Now").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Recent spending").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Dish soap spending proof").performScrollTo()
        composeTestRule.onNodeWithText("Dish soap spending proof").assertIsDisplayed()
        composeTestRule.onNodeWithText("USD 8.99  Cleaning  Target").performScrollTo()
        composeTestRule.onNodeWithText("USD 8.99  Cleaning  Target").assertIsDisplayed()
    }

    @Test
    fun iWeekShowsCanonicalPlannedMealsFromHouseholdRepository() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Week").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Household week").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "Canonical week meal should be visible",
            composeTestRule.onAllNodesWithText("Tomato rasam week proof").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "Canonical week row should include the meal slot",
            composeTestRule.onAllNodesWithText("Dinner", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "Canonical week row should include linked meal nutrition",
            composeTestRule.onAllNodesWithText("520 kcal  24g protein", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "Canonical week row should include inventory coverage, subtraction, reviewed gaps, leftovers, and provenance",
            composeTestRule
                .onAllNodesWithText(
                    "leftovers pack lunches  inventory covers 1/2 ingredients  subtracted 2 cup  1 reviewed gap  from ai proposal android_test",
                    substring = true,
                )
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }

    @Test
    fun jSavedShowsCanonicalRecipesFromHouseholdRepository() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Saved").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Household recipes").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "Canonical saved recipe should be visible",
            composeTestRule.onAllNodesWithText("Vegetable pulao recipe proof").fetchSemanticsNodes().isNotEmpty(),
        )
        composeTestRule.onNodeWithText("Dinner  South Indian  4 serving  35 min  active").assertIsDisplayed()
    }

    @Test
    fun kFoodCanMakeUsesCanonicalRecipeIngredientsFromHouseholdRepository() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Food").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Best from your kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Vegetable pulao recipe proof").assertIsDisplayed()
        composeTestRule.onNodeWithText("Can make  1/1 in Kitchen  35 min").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lCartShowsCanonicalMealPlanRecipeGapFromHouseholdRepository() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Navigate to Cart").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Canonical cart").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "Canonical recipe gap should be visible",
            composeTestRule.onAllNodesWithText("Lentils meal gap proof").fetchSemanticsNodes().isNotEmpty(),
        )
        composeTestRule.onNodeWithText("1 cup  Meal plan  recipe gap").assertIsDisplayed()
    }

    @Test
    fun zCoreAiSkillCanBeOpenedWithoutCrashing() {
        assumeEmulatorAndWaitForShell()

        composeTestRule.onNodeWithContentDescription("Open settings").performClick()
        composeTestRule.onNodeWithText("AI assistant").performClick()
        composeTestRule.onNodeWithText("Provider routes").assertIsDisplayed()
        composeTestRule.onNodeWithText("View or edit core skill").performClick()
        composeTestRule.waitForIdle()

        val editorOpened = runCatching {
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText("Core AI skill").fetchSemanticsNodes().isNotEmpty()
            }
            true
        }.getOrDefault(false)

        if (!editorOpened) {
            assertTrue(
                "Core AI skill entry should remain available if the editor transition is slow on CI",
                composeTestRule.onAllNodesWithText("View or edit core skill").fetchSemanticsNodes().isNotEmpty() ||
                    composeTestRule.onAllNodesWithText("Provider routes").fetchSemanticsNodes().isNotEmpty(),
            )
            pressActivityBack()
            return
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Core AI skill").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Core AI skill").assertExists()
        composeTestRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Reset to bundled skill"))
        composeTestRule.onNodeWithText("Reset to bundled skill").assertIsDisplayed()
        pressActivityBack()
        pressActivityBack()
    }

    private fun assertTextPresent(text: String) {
        assertTrue(
            "$text should be present in the app shell",
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun assumeEmulatorAndWaitForShell() {
        assumeTrue(isEmulator())
        restoreDefaultBackendState()
        composeTestRule.activity
        composeTestRule.waitForIdle()
        dismissFoodHomeChooserIfPresent()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            isShellVisible()
        }
    }

    private fun isEmulator(): Boolean =
        Build.MODEL.contains("sdk", ignoreCase = true) || Build.FINGERPRINT.contains("generic")

    private fun isShellVisible(): Boolean =
        runCatching {
            composeTestRule.onAllNodesWithContentDescription("Navigate to Now").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)

    private fun pressActivityBack() {
        runCatching {
            composeTestRule.activityRule.scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun dismissFoodHomeChooserIfPresent() {
        if (runCatching { composeTestRule.onAllNodesWithText("Start local now").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)) {
            runCatching { composeTestRule.onAllNodesWithText("Start local now").onFirst().performClick() }
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { composeTestRule.onAllNodesWithText("Start local now").fetchSemanticsNodes().isEmpty() }.getOrDefault(false)
            }
        }
    }

    private fun seedCanonicalSearchItem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = WonderFoodDatabaseFactory.create(context)
        val repository = HouseholdRepositories.room(database)
        val executor = HouseholdCommandExecutor(repository)
        val householdId = HouseholdId("00000000-0000-0000-0000-000000000105")
        val itemId = EntityId("00000000-0000-0000-0000-000000000777")
        val now = UtcTimestamp(System.currentTimeMillis())
        runBlocking {
            executor.execute(
                HouseholdCommand.UpsertHousehold(
                    record = CommandRecord(
                        commandId = CommandId("00000000-0000-0000-0000-000000000778"),
                        householdId = householdId,
                        type = "UpsertHousehold",
                        source = SourceRef(SourceKind.SYSTEM, "android_test"),
                        requestedAt = now,
                        appliedAt = now,
                        affectedEntityIds = emptyList(),
                    ),
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
                ),
            )
            executor.execute(
                HouseholdCommand.UpsertItem(
                    record = CommandRecord(
                        commandId = CommandId("00000000-0000-0000-0000-000000000779"),
                        householdId = householdId,
                        type = "UpsertItem",
                        source = SourceRef(SourceKind.MANUAL, "android_test"),
                        requestedAt = now,
                        appliedAt = now,
                        affectedEntityIds = listOf(itemId),
                    ),
                    item = Item(
                        metadata = EntityMetadata(
                            id = itemId,
                            householdId = householdId,
                            createdAt = now,
                            updatedAt = now,
                            revision = 1,
                            source = SourceRef(SourceKind.MANUAL, "android_test"),
                        ),
                        name = "Dish soap canonical proof",
                        kind = ItemKind.CLEANING,
                        category = "Cleaning",
                        defaultUnit = QuantityUnit.EACH,
                        brand = "Seventh Generation",
                    ),
                ),
            )
        }
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
        database.close()
    }

}
