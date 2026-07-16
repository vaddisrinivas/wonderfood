package com.wonderfood.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WonderFoodSnapshotCodecTest {
    @Test
    fun snapshotCodecRoundTripsDomainModelsThroughExplicitDtos() {
        val snapshot = sampleSnapshot()

        val encoded = WonderFoodSnapshotCodec.encode(snapshot)
        val decoded = WonderFoodSnapshotCodec.decode(encoded)

        assertTrue(encoded.contains("\"energyKcal\":null"))
        assertEquals(snapshot, decoded)
    }

    private fun sampleSnapshot(): WonderFoodSnapshot {
        val source = Source(
            id = SourceId("source-user-1"),
            kind = SourceKind.USER,
            label = "Manual entry",
            externalId = "note-1",
            uri = null,
            capturedAt = IsoTimestamp("2026-07-16T12:00:00Z"),
            truthState = TruthState.USER_CONFIRMED,
        )
        val confidence = Confidence(
            score = 0.94,
            state = TruthState.USER_CONFIRMED,
            rationale = "User confirmed during capture",
        )
        val unknownQuantity = Quantity(
            amount = null,
            unit = FoodUnit.UNKNOWN,
            truthState = TruthState.UNKNOWN,
        )

        val foodPageId = PageId("page-food-1")
        val recipePageId = PageId("page-recipe-1")
        val planPageId = PageId("page-plan-1")
        val logPageId = PageId("page-log-1")
        val shopPageId = PageId("page-shop-1")
        val receiptPageId = PageId("page-receipt-1")
        val foodId = FoodId("food-greek-yogurt")
        val aliasId = FoodAliasId("alias-yogurt")
        val stockLotId = StockLotId("stock-lot-yogurt")
        val nutritionSnapshotId = NutritionSnapshotId("nutrition-yogurt")
        val recipeId = RecipeId("recipe-yogurt-bowl")
        val ingredientId = RecipeIngredientId("ingredient-yogurt")
        val stepId = RecipeStepId("step-combine")
        val mealPlanId = MealPlanId("meal-plan-week-1")
        val planEntryId = PlanEntryId("plan-entry-breakfast")
        val mealLogId = MealLogId("meal-log-breakfast")
        val shoppingItemId = ShoppingItemId("shopping-yogurt")
        val receiptId = ReceiptId("receipt-market")
        val eventId = FoodEventId("event-stock-added")
        val relationId = RelationId("relation-page-food")
        val attachmentId = AttachmentId("attachment-label")

        val attachment = Attachment(
            id = attachmentId,
            kind = AttachmentKind.IMAGE,
            uri = "file://label.jpg",
            label = "Nutrition label",
            checksum = "sha256:abc",
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val relation = Relation(
            id = relationId,
            from = EntityRef(EntityType.PAGE, foodPageId.value),
            to = EntityRef(EntityType.FOOD, foodId.value),
            type = RelationType.ATTACHED_TO,
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val foodPage = Page(
            id = foodPageId,
            title = "Greek Yogurt",
            kind = PageKind.FOOD,
            entity = EntityRef(EntityType.FOOD, foodId.value),
            aliases = listOf("Yogurt"),
            relationIds = listOf(relationId),
            attachmentIds = listOf(attachmentId),
            truthState = TruthState.USER_CONFIRMED,
            source = source,
            confidence = confidence,
        )
        val recipePage = foodPage.copy(
            id = recipePageId,
            title = "Yogurt Bowl",
            kind = PageKind.RECIPE,
            entity = EntityRef(EntityType.RECIPE, recipeId.value),
            aliases = emptyList(),
        )
        val planPage = foodPage.copy(
            id = planPageId,
            title = "Week Plan",
            kind = PageKind.MEAL_PLAN,
            entity = EntityRef(EntityType.MEAL_PLAN, mealPlanId.value),
            aliases = emptyList(),
            relationIds = emptyList(),
        )
        val logPage = foodPage.copy(
            id = logPageId,
            title = "Breakfast Log",
            kind = PageKind.MEAL_LOG,
            entity = EntityRef(EntityType.MEAL_LOG, mealLogId.value),
            aliases = emptyList(),
            relationIds = emptyList(),
        )
        val shopPage = foodPage.copy(
            id = shopPageId,
            title = "Buy Greek Yogurt",
            kind = PageKind.SHOPPING_ITEM,
            entity = EntityRef(EntityType.SHOPPING_ITEM, shoppingItemId.value),
            aliases = emptyList(),
            relationIds = emptyList(),
        )
        val receiptPage = foodPage.copy(
            id = receiptPageId,
            title = "Market Receipt",
            kind = PageKind.RECEIPT,
            entity = EntityRef(EntityType.RECEIPT, receiptId.value),
            aliases = emptyList(),
            relationIds = emptyList(),
        )
        val food = Food(
            id = foodId,
            pageId = foodPageId,
            name = "Greek Yogurt",
            status = FoodStatus.ACTIVE,
            aliasIds = listOf(aliasId),
            stockLotIds = listOf(stockLotId),
            nutritionSnapshotIds = listOf(nutritionSnapshotId),
            attachmentIds = listOf(attachmentId),
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val alias = FoodAlias(
            id = aliasId,
            foodId = foodId,
            name = "Yogurt",
            locale = "en-US",
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val stockLot = StockLot(
            id = stockLotId,
            foodId = foodId,
            quantity = Quantity(
                amount = 1.0,
                unit = FoodUnit.PACKAGE,
                truthState = TruthState.USER_CONFIRMED,
            ),
            purchasedOn = IsoDate("2026-07-16"),
            expiresOn = IsoDate("2026-07-25"),
            location = "Fridge",
            status = StockLotStatus.AVAILABLE,
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val nutrition = NutritionSnapshot(
            id = nutritionSnapshotId,
            subject = EntityRef(EntityType.FOOD, foodId.value),
            basis = ServingBasis(
                type = NutritionBasisType.PER_SERVING,
                quantity = Quantity(
                    amount = 170.0,
                    unit = FoodUnit.GRAM,
                    truthState = TruthState.PROVIDER_CONFIRMED,
                ),
                description = "one container",
            ),
            values = NutritionValues(
                energyKcal = null,
                proteinGrams = null,
                carbohydrateGrams = null,
                fatGrams = null,
                fiberGrams = null,
                sugarGrams = null,
                sodiumMilligrams = null,
            ),
            source = source,
            confidence = Confidence.UNKNOWN,
            capturedAt = null,
            truthState = TruthState.UNKNOWN,
        )
        val ingredient = RecipeIngredient(
            id = ingredientId,
            recipeId = recipeId,
            foodId = foodId,
            displayName = "Greek yogurt",
            quantity = Quantity(
                amount = 1.0,
                unit = FoodUnit.SERVING,
                truthState = TruthState.USER_CONFIRMED,
            ),
            preparation = "chilled",
            optional = false,
            substituteFoodIds = emptyList(),
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val step = RecipeStep(
            id = stepId,
            recipeId = recipeId,
            order = 0,
            instruction = "Combine yogurt with toppings.",
            durationMinutes = 2,
            attachmentIds = listOf(attachmentId),
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val recipe = Recipe(
            id = recipeId,
            pageId = recipePageId,
            title = "Yogurt Bowl",
            description = "Simple breakfast bowl",
            status = RecipeStatus.ACTIVE,
            servings = Quantity(
                amount = 1.0,
                unit = FoodUnit.SERVING,
                truthState = TruthState.USER_CONFIRMED,
            ),
            prepMinutes = 5,
            cookMinutes = null,
            ingredients = listOf(ingredient),
            steps = listOf(step),
            nutritionSnapshotIds = listOf(nutritionSnapshotId),
            attachmentIds = listOf(attachmentId),
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val planEntry = PlanEntry(
            id = planEntryId,
            mealPlanId = mealPlanId,
            date = IsoDate("2026-07-17"),
            mealSlot = MealSlot.BREAKFAST,
            recipeId = recipeId,
            foodId = null,
            quantity = unknownQuantity,
            status = PlanEntryStatus.PLANNED,
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val mealPlan = MealPlan(
            id = mealPlanId,
            pageId = planPageId,
            name = "Week Plan",
            startsOn = IsoDate("2026-07-17"),
            endsOn = IsoDate("2026-07-23"),
            status = MealPlanStatus.ACCEPTED,
            entries = listOf(planEntry),
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val mealLog = MealLog(
            id = mealLogId,
            pageId = logPageId,
            occurredAt = IsoTimestamp("2026-07-17T08:00:00Z"),
            mealSlot = MealSlot.BREAKFAST,
            planEntryId = planEntryId,
            foodIds = listOf(foodId),
            recipeIds = listOf(recipeId),
            nutritionSnapshotIds = listOf(nutritionSnapshotId),
            status = MealLogStatus.CONFIRMED,
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val shoppingItem = ShoppingItem(
            id = shoppingItemId,
            pageId = shopPageId,
            foodId = foodId,
            recipeId = recipeId,
            quantity = Quantity(
                amount = 2.0,
                unit = FoodUnit.PACKAGE,
                truthState = TruthState.USER_CONFIRMED,
            ),
            reason = "Needed for breakfast plan",
            status = ShoppingItemStatus.NEEDED,
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val receipt = Receipt(
            id = receiptId,
            pageId = receiptPageId,
            merchantName = "Neighborhood Market",
            purchasedAt = IsoTimestamp("2026-07-16T18:00:00Z"),
            itemIds = listOf(shoppingItemId),
            subtotal = Money(
                amount = 4.99,
                currencyCode = "USD",
                truthState = TruthState.USER_CONFIRMED,
            ),
            total = Money(
                amount = 5.39,
                currencyCode = "USD",
                truthState = TruthState.USER_CONFIRMED,
            ),
            attachmentIds = listOf(attachmentId),
            status = ReceiptStatus.CAPTURED,
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )
        val event = FoodEvent(
            id = eventId,
            subject = EntityRef(EntityType.STOCK_LOT, stockLotId.value),
            type = FoodEventType.STOCK_ADDED,
            occurredAt = IsoTimestamp("2026-07-16T18:05:00Z"),
            quantity = stockLot.quantity,
            note = "Added after receipt capture",
            source = source,
            confidence = confidence,
            truthState = TruthState.USER_CONFIRMED,
        )

        return WonderFoodSnapshot(
            schemaVersion = WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION,
            pages = listOf(foodPage, recipePage, planPage, logPage, shopPage, receiptPage),
            foods = listOf(food),
            foodAliases = listOf(alias),
            stockLots = listOf(stockLot),
            nutritionSnapshots = listOf(nutrition),
            recipes = listOf(recipe),
            mealPlans = listOf(mealPlan),
            mealLogs = listOf(mealLog),
            shoppingItems = listOf(shoppingItem),
            receipts = listOf(receipt),
            foodEvents = listOf(event),
            relations = listOf(relation),
            attachments = listOf(attachment),
        )
    }
}
