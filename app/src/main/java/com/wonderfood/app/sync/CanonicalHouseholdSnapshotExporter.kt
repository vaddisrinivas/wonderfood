package com.wonderfood.app.sync

import com.wonderfood.core.model.Confidence
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.Food
import com.wonderfood.core.model.FoodAlias
import com.wonderfood.core.model.FoodAliasId
import com.wonderfood.core.model.FoodId
import com.wonderfood.core.model.FoodStatus
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.IsoDate
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.MealLog
import com.wonderfood.core.model.MealLogId
import com.wonderfood.core.model.MealLogStatus
import com.wonderfood.core.model.MealPlan
import com.wonderfood.core.model.MealPlanId
import com.wonderfood.core.model.MealPlanStatus
import com.wonderfood.core.model.MealSlot
import com.wonderfood.core.model.Money
import com.wonderfood.core.model.NutritionBasisType
import com.wonderfood.core.model.NutritionSnapshot
import com.wonderfood.core.model.NutritionSnapshotId
import com.wonderfood.core.model.NutritionValues
import com.wonderfood.core.model.Page
import com.wonderfood.core.model.PageId
import com.wonderfood.core.model.PageKind
import com.wonderfood.core.model.PlanEntry
import com.wonderfood.core.model.PlanEntryId
import com.wonderfood.core.model.PlanEntryStatus
import com.wonderfood.core.model.Quantity
import com.wonderfood.core.model.Receipt
import com.wonderfood.core.model.ReceiptId
import com.wonderfood.core.model.ReceiptStatus
import com.wonderfood.core.model.Recipe
import com.wonderfood.core.model.RecipeId
import com.wonderfood.core.model.RecipeIngredientId
import com.wonderfood.core.model.RecipeStatus
import com.wonderfood.core.model.RecipeStepId
import com.wonderfood.core.model.RecipeIngredient as LegacyRecipeIngredient
import com.wonderfood.core.model.RecipeStep as LegacyRecipeStep
import com.wonderfood.core.model.ShoppingItem
import com.wonderfood.core.model.ShoppingItemId
import com.wonderfood.core.model.ShoppingItemStatus
import com.wonderfood.core.model.Source
import com.wonderfood.core.model.SourceId
import com.wonderfood.core.model.SourceKind
import com.wonderfood.core.model.ServingBasis
import com.wonderfood.core.model.StockLot
import com.wonderfood.core.model.StockLotId
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.TruthState
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.PurchaseLineDisposition
import com.wonderfood.core.model.household.PurchaseStatus
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.MealEntryStatus as CanonicalMealEntryStatus
import com.wonderfood.core.model.household.MealPlanStatus as CanonicalMealPlanStatus
import java.time.Instant

object CanonicalHouseholdSnapshotExporter {
    fun toSnapshot(snapshot: HouseholdSnapshot): WonderFoodSnapshot {
        val source = Source(
            id = SourceId("canonical:household:${snapshot.household.id.value}"),
            kind = SourceKind.SYSTEM,
            label = "Canonical household store",
            externalId = snapshot.household.id.value,
            uri = null,
            capturedAt = IsoTimestamp(Instant.ofEpochMilli(snapshot.household.updatedAt.epochMillis).toString()),
            truthState = TruthState.USER_CONFIRMED,
        )
        val confidence = Confidence(1.0, TruthState.USER_CONFIRMED, "Projected from canonical household repository.")
        val pages = mutableListOf<Page>()
        val aliases = mutableListOf<FoodAlias>()
        val lotsByItemId = snapshot.inventoryLots.groupBy { it.itemId }
        val nutritionSnapshots = snapshot.nutritionSnapshots.mapNotNull { it.toLegacyNutritionSnapshot(source, confidence) }

        val foods = snapshot.items.map { item ->
            val foodId = item.metadata.id.toFoodId()
            val pageId = item.metadata.id.toPageId("item")
            val aliasId = item.metadata.id.toAliasId()
            val nutritionIds = snapshot.nutritionSnapshots
                .filter { it.subject.type == HouseholdEntityType.ITEM && it.subject.id == item.metadata.id }
                .map { it.metadata.id.toNutritionSnapshotId() }
            pages += Page(
                id = pageId,
                title = item.name,
                kind = PageKind.FOOD,
                entity = EntityRef(EntityType.FOOD, foodId.value),
                aliases = item.aliases.toList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = confidence,
            )
            aliases += FoodAlias(
                id = aliasId,
                foodId = foodId,
                name = item.name,
                locale = null,
                source = source,
                confidence = confidence,
                truthState = TruthState.USER_CONFIRMED,
            )
            Food(
                id = foodId,
                pageId = pageId,
                name = item.name,
                status = if (item.metadata.archivedAt == null) FoodStatus.ACTIVE else FoodStatus.ARCHIVED,
                aliasIds = listOf(aliasId),
                stockLotIds = lotsByItemId[item.metadata.id].orEmpty().map { it.metadata.id.toStockLotId() },
                nutritionSnapshotIds = nutritionIds,
                attachmentIds = emptyList(),
                source = source,
                confidence = confidence,
                truthState = if (item.kind == ItemKind.FOOD) TruthState.USER_CONFIRMED else TruthState.INFERRED,
            )
        }

        val stockLots = snapshot.inventoryLots.map { lot ->
            StockLot(
                id = lot.metadata.id.toStockLotId(),
                foodId = lot.itemId.toFoodId(),
                quantity = lot.quantity.toLegacyQuantity(),
                purchasedOn = lot.purchasedAt?.let { IsoDate(Instant.ofEpochMilli(it.epochMillis).toString().take(10)) },
                expiresOn = lot.expiresOn?.let { IsoDate(it.value) },
                location = lot.locationId?.value,
                status = lot.status.toLegacyStatus(),
                source = source,
                confidence = confidence,
                truthState = if (lot.quantity.isKnown) TruthState.USER_CONFIRMED else TruthState.UNKNOWN,
            )
        }

        val shoppingItems = snapshot.shoppingLines.map { line ->
            val pageId = line.metadata.id.toPageId("shopping")
            pages += Page(
                id = pageId,
                title = line.displayName,
                kind = PageKind.SHOPPING_ITEM,
                entity = EntityRef(EntityType.SHOPPING_ITEM, line.metadata.id.toShoppingItemId().value),
                aliases = emptyList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = confidence,
            )
            ShoppingItem(
                id = line.metadata.id.toShoppingItemId(),
                pageId = pageId,
                foodId = line.itemId?.toFoodId(),
                recipeId = null,
                quantity = line.quantity.toLegacyQuantity(),
                reason = line.reason.name.lowercase(),
                status = line.status.toLegacyStatus(),
                source = source,
                confidence = confidence,
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val ingredientsByRecipeId = snapshot.recipeIngredients.groupBy { it.recipeId }
        val stepsByRecipeId = snapshot.recipeSteps.groupBy { it.recipeId }
        val recipes = snapshot.recipes.map { recipe ->
            val recipeId = recipe.metadata.id.toRecipeId()
            val pageId = recipe.metadata.id.toPageId("recipe")
            val nutritionIds = snapshot.nutritionSnapshots
                .filter { it.subject.type == HouseholdEntityType.RECIPE && it.subject.id == recipe.metadata.id }
                .map { it.metadata.id.toNutritionSnapshotId() }
            pages += Page(
                id = pageId,
                title = recipe.name,
                kind = PageKind.RECIPE,
                entity = EntityRef(EntityType.RECIPE, recipeId.value),
                aliases = recipe.tags.toList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = confidence,
            )
            Recipe(
                id = recipeId,
                pageId = pageId,
                title = recipe.name,
                description = recipe.description,
                status = recipe.status.toLegacyStatus(),
                servings = recipe.yield.toLegacyQuantity(),
                prepMinutes = recipe.prepMinutes,
                cookMinutes = recipe.cookMinutes,
                ingredients = ingredientsByRecipeId[recipe.metadata.id].orEmpty()
                    .sortedBy { it.order }
                    .map { it.toLegacyRecipeIngredient(recipeId, source, confidence) },
                steps = stepsByRecipeId[recipe.metadata.id].orEmpty()
                    .sortedBy { it.order }
                    .map { it.toLegacyRecipeStep(recipeId, source, confidence) },
                nutritionSnapshotIds = nutritionIds,
                attachmentIds = emptyList(),
                source = source,
                confidence = confidence,
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val receiptLineShoppingItems = snapshot.purchaseLines.map { line ->
            val pageId = line.metadata.id.toPageId("purchase_line")
            pages += Page(
                id = pageId,
                title = line.displayName,
                kind = PageKind.SHOPPING_ITEM,
                entity = EntityRef(EntityType.SHOPPING_ITEM, line.metadata.id.toShoppingItemId().value),
                aliases = emptyList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = confidence,
            )
            ShoppingItem(
                id = line.metadata.id.toShoppingItemId(),
                pageId = pageId,
                foodId = line.itemId?.toFoodId(),
                recipeId = null,
                quantity = line.quantity.toLegacyQuantity(),
                reason = line.spendCategory ?: line.disposition.name.lowercase(),
                status = line.disposition.toLegacyShoppingStatus(),
                source = source,
                confidence = confidence,
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val purchaseLinesByPurchaseId = snapshot.purchaseLines.groupBy { it.purchaseId }
        val merchantsById = snapshot.merchants.associateBy { it.metadata.id }
        val receipts = snapshot.purchases.map { purchase ->
            val pageId = purchase.metadata.id.toPageId("purchase")
            val merchantName = purchase.merchantId?.let { merchantsById[it]?.name }
                ?: purchase.paymentNote.extractMerchant()
            val title = merchantName ?: "Purchase ${purchase.metadata.id.value.take(8)}"
            pages += Page(
                id = pageId,
                title = title,
                kind = PageKind.RECEIPT,
                entity = EntityRef(EntityType.RECEIPT, purchase.metadata.id.toReceiptId().value),
                aliases = emptyList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = confidence,
            )
            Receipt(
                id = purchase.metadata.id.toReceiptId(),
                pageId = pageId,
                merchantName = merchantName,
                purchasedAt = IsoTimestamp(Instant.ofEpochMilli(purchase.occurredAt.epochMillis).toString()),
                itemIds = purchaseLinesByPurchaseId[purchase.metadata.id].orEmpty().map { it.metadata.id.toShoppingItemId() },
                subtotal = purchase.subtotal?.toLegacyMoney(),
                tax = purchase.tax?.toLegacyMoney(),
                total = purchase.total?.toLegacyMoney(),
                attachmentIds = emptyList(),
                status = purchase.status.toLegacyStatus(),
                source = source,
                confidence = confidence,
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val mealEntriesByPlanId = snapshot.mealEntries
            .filter { it.mealPlanId != null }
            .groupBy { requireNotNull(it.mealPlanId) }
        val mealPlans = snapshot.mealPlans.map { plan ->
            val planId = plan.metadata.id.toMealPlanId()
            val pageId = plan.metadata.id.toPageId("meal_plan")
            pages += Page(
                id = pageId,
                title = plan.name,
                kind = PageKind.MEAL_PLAN,
                entity = EntityRef(EntityType.MEAL_PLAN, planId.value),
                aliases = emptyList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = confidence,
            )
            MealPlan(
                id = planId,
                pageId = pageId,
                name = plan.name,
                startsOn = IsoDate(plan.startsOn.value),
                endsOn = IsoDate(plan.endsOn.value),
                status = plan.status.toLegacyStatus(),
                entries = mealEntriesByPlanId[plan.metadata.id].orEmpty().map { it.toLegacyPlanEntry(planId, source, confidence) },
                source = source,
                confidence = confidence,
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val mealLogs = snapshot.mealEntries
            .filter { it.mealPlanId == null || it.status == CanonicalMealEntryStatus.EATEN }
            .map { entry ->
                val mealLogId = entry.metadata.id.toMealLogId()
                val pageId = entry.metadata.id.toPageId("meal_log")
                val nutritionIds = entry.nutritionSnapshotIds.map { it.toNutritionSnapshotId() }
                pages += Page(
                    id = pageId,
                    title = entry.title,
                    kind = PageKind.MEAL_LOG,
                    entity = EntityRef(EntityType.MEAL_LOG, mealLogId.value),
                    aliases = emptyList(),
                    relationIds = emptyList(),
                    attachmentIds = emptyList(),
                    truthState = TruthState.USER_CONFIRMED,
                    source = source,
                    confidence = confidence,
                )
                MealLog(
                    id = mealLogId,
                    pageId = pageId,
                    occurredAt = IsoTimestamp(Instant.ofEpochMilli(entry.scheduledAt.epochMillis).toString()),
                    mealSlot = entry.slot.toLegacyMealSlot(),
                    planEntryId = entry.mealPlanId?.let { entry.metadata.id.toPlanEntryId() },
                    foodIds = emptyList(),
                    recipeIds = entry.recipeId?.let { listOf(it.toRecipeId()) }.orEmpty(),
                    nutritionSnapshotIds = nutritionIds,
                    status = entry.status.toLegacyMealLogStatus(),
                    source = source,
                    confidence = confidence,
                    truthState = TruthState.USER_CONFIRMED,
                )
            }

        return WonderFoodSnapshot(
            schemaVersion = WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION,
            pages = pages,
            foods = foods,
            foodAliases = aliases,
            stockLots = stockLots,
            nutritionSnapshots = nutritionSnapshots,
            recipes = recipes,
            mealPlans = mealPlans,
            mealLogs = mealLogs,
            shoppingItems = shoppingItems + receiptLineShoppingItems,
            receipts = receipts,
            foodEvents = emptyList(),
            relations = emptyList(),
            attachments = emptyList(),
        )
    }
}

private fun com.wonderfood.core.model.household.EntityId.toPageId(prefix: String): PageId = PageId("canonical:page:$prefix:$value")
private fun com.wonderfood.core.model.household.EntityId.toFoodId(): FoodId = FoodId("canonical:item:$value")
private fun com.wonderfood.core.model.household.EntityId.toAliasId(): FoodAliasId = FoodAliasId("canonical:item_alias:$value")
private fun com.wonderfood.core.model.household.EntityId.toStockLotId(): StockLotId = StockLotId("canonical:inventory_lot:$value")
private fun com.wonderfood.core.model.household.EntityId.toNutritionSnapshotId(): NutritionSnapshotId = NutritionSnapshotId("canonical:nutrition:$value")
private fun com.wonderfood.core.model.household.EntityId.toRecipeIngredientId(): RecipeIngredientId = RecipeIngredientId("canonical:recipe_ingredient:$value")
private fun com.wonderfood.core.model.household.EntityId.toShoppingItemId(): ShoppingItemId = ShoppingItemId("canonical:shopping_line:$value")
private fun com.wonderfood.core.model.household.EntityId.toReceiptId(): ReceiptId = ReceiptId("canonical:purchase:$value")
private fun com.wonderfood.core.model.household.EntityId.toRecipeId(): RecipeId = RecipeId("canonical:recipe:$value")
private fun com.wonderfood.core.model.household.EntityId.toRecipeStepId(): RecipeStepId = RecipeStepId("canonical:recipe_step:$value")
private fun com.wonderfood.core.model.household.EntityId.toMealPlanId(): MealPlanId = MealPlanId("canonical:meal_plan:$value")
private fun com.wonderfood.core.model.household.EntityId.toPlanEntryId(): PlanEntryId = PlanEntryId("canonical:meal_entry:$value")
private fun com.wonderfood.core.model.household.EntityId.toMealLogId(): MealLogId = MealLogId("canonical:meal_log:$value")

private fun com.wonderfood.core.model.household.NutritionSnapshot.toLegacyNutritionSnapshot(
    source: Source,
    confidence: Confidence,
): NutritionSnapshot? {
    val subject = when (subject.type) {
        HouseholdEntityType.ITEM -> EntityRef(EntityType.FOOD, subject.id.toFoodId().value)
        HouseholdEntityType.MEAL_ENTRY -> EntityRef(EntityType.MEAL_LOG, subject.id.toMealLogId().value)
        HouseholdEntityType.RECIPE -> EntityRef(EntityType.RECIPE, subject.id.toRecipeId().value)
        else -> return null
    }
    return NutritionSnapshot(
        id = metadata.id.toNutritionSnapshotId(),
        subject = subject,
        basis = ServingBasis(
            type = if (basis.unit == QuantityUnit.SERVING) NutritionBasisType.PER_SERVING else NutritionBasisType.UNKNOWN,
            quantity = basis.toLegacyQuantity(),
            description = null,
        ),
        values = NutritionValues(
            energyKcal = values.energyKcal?.value?.toDoubleOrNull(),
            proteinGrams = values.proteinGrams?.value?.toDoubleOrNull(),
            carbohydrateGrams = values.carbohydrateGrams?.value?.toDoubleOrNull(),
            fatGrams = values.fatGrams?.value?.toDoubleOrNull(),
            fiberGrams = values.fiberGrams?.value?.toDoubleOrNull(),
            sugarGrams = values.sugarGrams?.value?.toDoubleOrNull(),
            sodiumMilligrams = values.sodiumMilligrams?.value?.toDoubleOrNull(),
        ),
        source = source,
        confidence = confidence,
        capturedAt = IsoTimestamp(Instant.ofEpochMilli(capturedAt.epochMillis).toString()),
        truthState = TruthState.USER_CONFIRMED,
    )
}

private fun com.wonderfood.core.model.household.RecipeIngredient.toLegacyRecipeIngredient(
    recipeId: RecipeId,
    source: Source,
    confidence: Confidence,
): LegacyRecipeIngredient =
    LegacyRecipeIngredient(
        id = metadata.id.toRecipeIngredientId(),
        recipeId = recipeId,
        foodId = itemId?.toFoodId(),
        displayName = originalText,
        quantity = quantity.toLegacyQuantity(),
        preparation = preparation,
        optional = optional,
        substituteFoodIds = substituteItemIds.map { it.toFoodId() },
        source = source,
        confidence = confidence,
        truthState = TruthState.USER_CONFIRMED,
    )

private fun com.wonderfood.core.model.household.RecipeStep.toLegacyRecipeStep(
    recipeId: RecipeId,
    source: Source,
    confidence: Confidence,
): LegacyRecipeStep =
    LegacyRecipeStep(
        id = metadata.id.toRecipeStepId(),
        recipeId = recipeId,
        order = order,
        instruction = instruction,
        durationMinutes = durationMinutes,
        attachmentIds = emptyList(),
        source = source,
        confidence = confidence,
        truthState = TruthState.USER_CONFIRMED,
    )

private fun com.wonderfood.core.model.household.MealEntry.toLegacyPlanEntry(
    mealPlanId: MealPlanId,
    source: Source,
    confidence: Confidence,
): PlanEntry =
    PlanEntry(
        id = metadata.id.toPlanEntryId(),
        mealPlanId = mealPlanId,
        date = IsoDate(Instant.ofEpochMilli(scheduledAt.epochMillis).toString().take(10)),
        mealSlot = slot.toLegacyMealSlot(),
        recipeId = recipeId?.toRecipeId(),
        foodId = null,
        quantity = servings.toLegacyQuantity(),
        status = status.toLegacyPlanEntryStatus(),
        source = source,
        confidence = confidence,
        truthState = TruthState.USER_CONFIRMED,
    )

private fun com.wonderfood.core.model.household.Quantity.toLegacyQuantity(): Quantity =
    Quantity(
        amount = amount?.toBigDecimal()?.toDouble(),
        unit = unit.toLegacyUnit(),
        truthState = if (isKnown) TruthState.USER_CONFIRMED else TruthState.UNKNOWN,
    )

private fun QuantityUnit.toLegacyUnit(): FoodUnit =
    when (this) {
        QuantityUnit.EACH -> FoodUnit.EACH
        QuantityUnit.GRAM -> FoodUnit.GRAM
        QuantityUnit.KILOGRAM -> FoodUnit.KILOGRAM
        QuantityUnit.MILLILITER -> FoodUnit.MILLILITER
        QuantityUnit.LITER -> FoodUnit.LITER
        QuantityUnit.CUP -> FoodUnit.CUP
        QuantityUnit.SERVING -> FoodUnit.SERVING
        QuantityUnit.PACKAGE -> FoodUnit.PACKAGE
        else -> FoodUnit.UNKNOWN
    }

private fun InventoryLotStatus.toLegacyStatus(): StockLotStatus =
    when (this) {
        InventoryLotStatus.AVAILABLE -> StockLotStatus.AVAILABLE
        InventoryLotStatus.OPENED -> StockLotStatus.OPENED
        InventoryLotStatus.RESERVED -> StockLotStatus.RESERVED
        InventoryLotStatus.CONSUMED -> StockLotStatus.CONSUMED
        InventoryLotStatus.DISCARDED -> StockLotStatus.DISCARDED
        InventoryLotStatus.ARCHIVED -> StockLotStatus.ARCHIVED
    }

private fun ShoppingLineStatus.toLegacyStatus(): ShoppingItemStatus =
    when (this) {
        ShoppingLineStatus.NEEDED -> ShoppingItemStatus.NEEDED
        ShoppingLineStatus.IN_CART -> ShoppingItemStatus.IN_CART
        ShoppingLineStatus.PURCHASED -> ShoppingItemStatus.PURCHASED
        ShoppingLineStatus.SKIPPED -> ShoppingItemStatus.SKIPPED
        ShoppingLineStatus.ARCHIVED -> ShoppingItemStatus.ARCHIVED
    }

private fun PurchaseLineDisposition.toLegacyShoppingStatus(): ShoppingItemStatus =
    when (this) {
        PurchaseLineDisposition.INVENTORY,
        PurchaseLineDisposition.CONSUMED,
        PurchaseLineDisposition.SERVICE,
        -> ShoppingItemStatus.PURCHASED
        PurchaseLineDisposition.IGNORED -> ShoppingItemStatus.SKIPPED
    }

private fun String?.extractMerchant(): String? =
    this
        ?.lineSequence()
        ?.firstNotNullOfOrNull { line ->
            line.substringAfter("Merchant:", missingDelimiterValue = "")
                .trim()
                .takeIf { it.isNotBlank() }
        }

private fun com.wonderfood.core.model.household.Money.toLegacyMoney(): Money =
    Money(
        amount = minorUnits.toDouble() / 100.0,
        currencyCode = currencyCode,
        truthState = TruthState.USER_CONFIRMED,
    )

private fun PurchaseStatus.toLegacyStatus(): ReceiptStatus =
    when (this) {
        PurchaseStatus.DRAFT -> ReceiptStatus.CAPTURED
        PurchaseStatus.REVIEWED -> ReceiptStatus.REVIEWED
        PurchaseStatus.RECONCILED -> ReceiptStatus.RECONCILED
        PurchaseStatus.REFUNDED -> ReceiptStatus.REVIEWED
        PurchaseStatus.ARCHIVED -> ReceiptStatus.ARCHIVED
    }

private fun CanonicalMealPlanStatus.toLegacyStatus(): MealPlanStatus =
    when (this) {
        CanonicalMealPlanStatus.DRAFT -> MealPlanStatus.DRAFT
        CanonicalMealPlanStatus.ACTIVE -> MealPlanStatus.ACTIVE
        CanonicalMealPlanStatus.COMPLETED -> MealPlanStatus.COMPLETED
        CanonicalMealPlanStatus.ARCHIVED -> MealPlanStatus.ARCHIVED
        CanonicalMealPlanStatus.TEMPLATE -> MealPlanStatus.DRAFT
    }

private fun CanonicalMealEntryStatus.toLegacyPlanEntryStatus(): PlanEntryStatus =
    when (this) {
        CanonicalMealEntryStatus.PROPOSED -> PlanEntryStatus.PLANNED
        CanonicalMealEntryStatus.PLANNED -> PlanEntryStatus.PLANNED
        CanonicalMealEntryStatus.COOKED -> PlanEntryStatus.ACCEPTED
        CanonicalMealEntryStatus.EATEN -> PlanEntryStatus.EATEN
        CanonicalMealEntryStatus.SKIPPED -> PlanEntryStatus.SKIPPED
        CanonicalMealEntryStatus.ARCHIVED -> PlanEntryStatus.ARCHIVED
    }

private fun CanonicalMealEntryStatus.toLegacyMealLogStatus(): MealLogStatus =
    when (this) {
        CanonicalMealEntryStatus.EATEN,
        CanonicalMealEntryStatus.COOKED,
        -> MealLogStatus.CONFIRMED
        CanonicalMealEntryStatus.ARCHIVED -> MealLogStatus.ARCHIVED
        CanonicalMealEntryStatus.PROPOSED,
        CanonicalMealEntryStatus.PLANNED,
        CanonicalMealEntryStatus.SKIPPED,
        -> MealLogStatus.ESTIMATED
    }

private fun String.toLegacyMealSlot(): MealSlot =
    when (trim().lowercase()) {
        "breakfast" -> MealSlot.BREAKFAST
        "lunch" -> MealSlot.LUNCH
        "dinner" -> MealSlot.DINNER
        "snack" -> MealSlot.SNACK
        "anytime", "flexible", "flex" -> MealSlot.ANYTIME
        else -> MealSlot.UNKNOWN
    }

private fun com.wonderfood.core.model.household.RecipeStatus.toLegacyStatus(): RecipeStatus =
    when (this) {
        com.wonderfood.core.model.household.RecipeStatus.DRAFT -> RecipeStatus.DRAFT
        com.wonderfood.core.model.household.RecipeStatus.ACTIVE -> RecipeStatus.ACTIVE
        com.wonderfood.core.model.household.RecipeStatus.ARCHIVED -> RecipeStatus.ARCHIVED
    }
