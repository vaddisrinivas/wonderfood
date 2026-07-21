package com.wonderfood.app.sync

import com.wonderfood.app.data.FoodEventConfidence
import com.wonderfood.app.data.HouseholdUiMemory
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.MealPlanEntryStatus
import com.wonderfood.app.data.StorageZone
import com.wonderfood.core.model.Attachment
import com.wonderfood.core.model.AttachmentId
import com.wonderfood.core.model.Confidence
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.Food
import com.wonderfood.core.model.FoodAlias
import com.wonderfood.core.model.FoodAliasId
import com.wonderfood.core.model.FoodEvent
import com.wonderfood.core.model.FoodEventId
import com.wonderfood.core.model.FoodEventType
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
import com.wonderfood.core.model.RecipeIngredient
import com.wonderfood.core.model.RecipeIngredientId
import com.wonderfood.core.model.RecipeStatus
import com.wonderfood.core.model.RecipeStep
import com.wonderfood.core.model.RecipeStepId
import com.wonderfood.core.model.Relation
import com.wonderfood.core.model.ShoppingItem
import com.wonderfood.core.model.ShoppingItemId
import com.wonderfood.core.model.ShoppingItemStatus
import com.wonderfood.core.model.Source
import com.wonderfood.core.model.SourceId
import com.wonderfood.core.model.SourceKind
import com.wonderfood.core.model.StockLot
import com.wonderfood.core.model.StockLotId
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.TruthState
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.model.WonderFoodSnapshotCodec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object TestHouseholdUiSnapshotExporter {
    fun toSnapshot(
        memory: HouseholdUiMemory,
        now: Instant = Instant.now(),
    ): WonderFoodSnapshot {
        val pages = mutableListOf<Page>()
        val foods = mutableListOf<Food>()
        val aliases = mutableListOf<FoodAlias>()
        val stockLots = mutableListOf<StockLot>()
        val nutrition = mutableListOf<NutritionSnapshot>()

        memory.inventory.forEach { item ->
            val source = item.source.source("inventory:${item.id}", item.updatedAtMillis)
            val pageId = PageId("legacy:page:inventory:${item.id}")
            val foodId = FoodId("legacy:food:inventory:${item.id}")
            val aliasId = FoodAliasId("legacy:alias:inventory:${item.id}")
            val stockLotId = StockLotId("legacy:stock_lot:inventory:${item.id}")
            val nutritionId = NutritionSnapshotId("legacy:nutrition:inventory:${item.id}")
            val hasNutrition = item.hasNutrition

            pages += Page(
                id = pageId,
                title = item.name,
                kind = PageKind.FOOD,
                entity = EntityRef(EntityType.FOOD, foodId.value),
                aliases = listOf(item.name),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = source.confidence(),
            )
            aliases += FoodAlias(
                id = aliasId,
                foodId = foodId,
                name = item.name,
                locale = null,
                source = source,
                confidence = source.confidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
            foods += Food(
                id = foodId,
                pageId = pageId,
                name = item.name,
                status = FoodStatus.ACTIVE,
                aliasIds = listOf(aliasId),
                stockLotIds = listOf(stockLotId),
                nutritionSnapshotIds = if (hasNutrition) listOf(nutritionId) else emptyList(),
                attachmentIds = emptyList(),
                source = source,
                confidence = source.confidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
            stockLots += StockLot(
                id = stockLotId,
                foodId = foodId,
                quantity = item.quantity.toQuantity(),
                purchasedOn = item.purchaseDateEpochDay?.let { IsoDate(LocalDate.ofEpochDay(it).toString()) },
                expiresOn = item.expiresAtMillis?.toIsoDate(),
                location = item.zone.backendLocation,
                status = item.stockStatus,
                source = source,
                confidence = source.confidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
            if (hasNutrition) {
                nutrition += NutritionSnapshot(
                    id = nutritionId,
                    subject = EntityRef(EntityType.FOOD, foodId.value),
                    basis = com.wonderfood.core.model.ServingBasis(
                        type = NutritionBasisType.PER_SERVING,
                        quantity = item.servingText.toQuantity(),
                        description = item.servingText.ifBlank { null },
                    ),
                    values = NutritionValues(
                        energyKcal = item.calories?.toDouble(),
                        proteinGrams = item.proteinGrams,
                        carbohydrateGrams = item.carbsGrams,
                        fatGrams = item.fatGrams,
                        fiberGrams = null,
                        sugarGrams = null,
                        sodiumMilligrams = null,
                    ),
                    source = item.nutritionSource.ifBlank { item.source }.source("nutrition:inventory:${item.id}", item.updatedAtMillis),
                    confidence = Confidence(0.75, TruthState.ESTIMATED, "Imported from legacy nutrition fields."),
                    capturedAt = item.updatedAtMillis.toIsoTimestamp(),
                    truthState = TruthState.ESTIMATED,
                )
            }
        }

        val recipes = memory.recipes.map { recipe ->
            val source = "legacy_recipe".source("recipe:${recipe.id}", recipe.updatedAtMillis)
            val pageId = PageId("legacy:page:recipe:${recipe.id}")
            val recipeId = RecipeId("legacy:recipe:${recipe.id}")
            pages += Page(
                id = pageId,
                title = recipe.title,
                kind = PageKind.RECIPE,
                entity = EntityRef(EntityType.RECIPE, recipeId.value),
                aliases = recipe.tags.csvTokens(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = source.confidence(),
            )
            Recipe(
                id = recipeId,
                pageId = pageId,
                title = recipe.title,
                description = recipe.tags.ifBlank { null },
                status = RecipeStatus.ACTIVE,
                servings = Quantity(recipe.servings?.toDouble(), FoodUnit.SERVING, recipe.servings.truthState),
                prepMinutes = recipe.prepMinutes,
                cookMinutes = null,
                ingredients = recipe.ingredients.linesOrSentences().mapIndexed { index, line ->
                    RecipeIngredient(
                        id = RecipeIngredientId("legacy:recipe_ingredient:${recipe.id}:$index"),
                        recipeId = recipeId,
                        foodId = null,
                        displayName = line,
                        quantity = line.toQuantity(),
                        preparation = null,
                        optional = false,
                        substituteFoodIds = emptyList(),
                        source = source,
                        confidence = source.confidence(),
                        truthState = TruthState.USER_CONFIRMED,
                    )
                },
                steps = recipe.steps.linesOrSentences().mapIndexed { index, line ->
                    RecipeStep(
                        id = RecipeStepId("legacy:recipe_step:${recipe.id}:$index"),
                        recipeId = recipeId,
                        order = index,
                        instruction = line,
                        durationMinutes = null,
                        attachmentIds = emptyList(),
                        source = source,
                        confidence = source.confidence(),
                        truthState = TruthState.USER_CONFIRMED,
                    )
                },
                nutritionSnapshotIds = emptyList(),
                attachmentIds = emptyList(),
                source = source,
                confidence = source.confidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val shopping = memory.groceries.map { item ->
            val source = item.source.source("grocery:${item.id}", item.updatedAtMillis)
            val pageId = PageId("legacy:page:grocery:${item.id}")
            val shoppingId = ShoppingItemId("legacy:shopping_item:grocery:${item.id}")
            pages += Page(
                id = pageId,
                title = item.name,
                kind = PageKind.SHOPPING_ITEM,
                entity = EntityRef(EntityType.SHOPPING_ITEM, shoppingId.value),
                aliases = listOf(item.name),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = source.confidence(),
            )
            ShoppingItem(
                id = shoppingId,
                pageId = pageId,
                foodId = memory.inventory.matchFoodId(item.name),
                recipeId = null,
                quantity = item.quantity.toQuantity(),
                reason = item.category.ifBlank { null },
                status = if (item.status == GroceryStatus.BOUGHT) ShoppingItemStatus.PURCHASED else ShoppingItemStatus.NEEDED,
                source = source,
                confidence = source.confidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val mealPlanEntriesByPlan = memory.mealPlanEntries.groupBy { it.planId }
        val mealPlans = memory.mealPlans.map { plan ->
            val source = "legacy_plan".source("meal_plan:${plan.id}", plan.updatedAtMillis)
            val pageId = PageId("legacy:page:meal_plan:${plan.id}")
            val planId = MealPlanId("legacy:meal_plan:${plan.id}")
            val entries = mealPlanEntriesByPlan[plan.id].orEmpty()
            val start = plan.startDateEpochDay
                ?: entries.minOfOrNull { it.dateEpochDay }
                ?: now.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
            val end = entries.maxOfOrNull { it.dateEpochDay } ?: start
            pages += Page(
                id = pageId,
                title = plan.title,
                kind = PageKind.MEAL_PLAN,
                entity = EntityRef(EntityType.MEAL_PLAN, planId.value),
                aliases = emptyList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = source.confidence(),
            )
            MealPlan(
                id = planId,
                pageId = pageId,
                name = plan.title,
                startsOn = IsoDate(LocalDate.ofEpochDay(start).toString()),
                endsOn = IsoDate(LocalDate.ofEpochDay(end).toString()),
                status = if (plan.status.name == "ACCEPTED") MealPlanStatus.ACCEPTED else MealPlanStatus.DRAFT,
                entries = entries.map { entry ->
                    PlanEntry(
                        id = PlanEntryId("legacy:plan_entry:${entry.id}"),
                        mealPlanId = planId,
                        date = IsoDate(LocalDate.ofEpochDay(entry.dateEpochDay).toString()),
                        mealSlot = entry.slot.toCoreMealSlot(),
                        recipeId = entry.recipeId?.let { RecipeId("legacy:recipe:$it") },
                        foodId = memory.inventory.matchFoodId(entry.title),
                        quantity = Quantity(null, FoodUnit.UNKNOWN, TruthState.UNKNOWN),
                        status = entry.status.toCoreStatus(),
                        source = source,
                        confidence = source.confidence(),
                        truthState = TruthState.USER_CONFIRMED,
                    )
                },
                source = source,
                confidence = source.confidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val mealLogs = memory.mealLogs.map { log ->
            val source = log.source.source("meal_log:${log.id}", log.updatedAtMillis)
            val pageId = PageId("legacy:page:meal_log:${log.id}")
            val logId = MealLogId("legacy:meal_log:${log.id}")
            pages += Page(
                id = pageId,
                title = log.title,
                kind = PageKind.MEAL_LOG,
                entity = EntityRef(EntityType.MEAL_LOG, logId.value),
                aliases = emptyList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = source.confidence(),
            )
            MealLog(
                id = logId,
                pageId = pageId,
                occurredAt = LocalDate.ofEpochDay(log.loggedDateEpochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toString().let(::IsoTimestamp),
                mealSlot = log.mealSlot.toCoreMealSlot(),
                planEntryId = null,
                foodIds = memory.inventory.matchFoodIds(log.usedItemsText),
                recipeIds = emptyList(),
                nutritionSnapshotIds = emptyList(),
                status = MealLogStatus.ESTIMATED,
                source = source,
                confidence = source.confidence(),
                truthState = TruthState.ESTIMATED,
            )
        }

        val receipts = memory.receipts.map { receipt ->
            val source = "legacy_receipt".source("receipt:${receipt.id}", receipt.createdAtMillis)
            val pageId = PageId("legacy:page:receipt:${receipt.id}")
            val receiptId = ReceiptId("legacy:receipt:${receipt.id}")
            pages += Page(
                id = pageId,
                title = "Receipt ${receipt.id}",
                kind = PageKind.RECEIPT,
                entity = EntityRef(EntityType.RECEIPT, receiptId.value),
                aliases = emptyList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = source.confidence(),
            )
            Receipt(
                id = receiptId,
                pageId = pageId,
                merchantName = null,
                purchasedAt = receipt.createdAtMillis.toIsoTimestamp(),
                itemIds = emptyList(),
                subtotal = null,
                tax = null,
                total = null,
                attachmentIds = emptyList(),
                status = when (receipt.status.name) {
                    "EXTRACTED" -> ReceiptStatus.REVIEWED
                    else -> ReceiptStatus.CAPTURED
                },
                source = source,
                confidence = source.confidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val events = memory.events.map { event ->
            val source = event.source.source("event:${event.id}", event.createdAtMillis)
            FoodEvent(
                id = FoodEventId("legacy:food_event:${event.id}"),
                subject = event.subjectRef(),
                type = event.type.name.toCoreFoodEventType(),
                occurredAt = event.startedAtMillis.toIsoTimestamp(),
                quantity = event.amount?.let { Quantity(it, event.unit.toFoodUnit(), TruthState.ESTIMATED) },
                note = event.note.ifBlank { null },
                source = source,
                confidence = event.confidence.toCoreConfidence(),
                truthState = TruthState.ESTIMATED,
            )
        }

        return WonderFoodSnapshot(
            schemaVersion = WonderFoodSnapshotCodec.CURRENT_SCHEMA_VERSION,
            pages = pages.distinctBy { it.id.value },
            foods = foods,
            foodAliases = aliases,
            stockLots = stockLots,
            nutritionSnapshots = nutrition,
            recipes = recipes,
            mealPlans = mealPlans,
            mealLogs = mealLogs,
            shoppingItems = shopping,
            receipts = receipts,
            foodEvents = events,
            relations = emptyList<Relation>(),
            attachments = emptyList<Attachment>(),
        )
    }

    private val InventoryItem.hasNutrition: Boolean
        get() = calories != null || proteinGrams != null || carbsGrams != null || fatGrams != null || servingText.isNotBlank()

    private val InventoryItem.stockStatus: StockLotStatus
        get() = when {
            quantity.trim() == "0" -> StockLotStatus.OUT
            quantity.contains("low", ignoreCase = true) -> StockLotStatus.LOW
            else -> StockLotStatus.AVAILABLE
        }

    private val StorageZone.backendLocation: String
        get() = when (this) {
            StorageZone.FRIDGE -> "fridge"
            StorageZone.FREEZER -> "freezer"
            StorageZone.PANTRY -> "pantry"
        }

    private fun String.source(externalId: String, millis: Long): Source =
        Source(
            id = SourceId("legacy:source:${externalId.replace(':', '-')}"),
            kind = when {
                contains("ai", ignoreCase = true) -> SourceKind.AI
                contains("receipt", ignoreCase = true) -> SourceKind.RECEIPT
                contains("csv", ignoreCase = true) || contains("import", ignoreCase = true) -> SourceKind.IMPORT
                contains("assistant", ignoreCase = true) -> SourceKind.USER
                else -> SourceKind.USER
            },
            label = ifBlank { "legacy" },
            externalId = "legacy:$externalId",
            uri = null,
            capturedAt = millis.toIsoTimestamp(),
            truthState = TruthState.USER_CONFIRMED,
        )

    private fun Source.confidence(): Confidence =
        Confidence(0.9, truthState, "Imported from WonderFood seed fixture.")

    private fun Long.toIsoTimestamp(): IsoTimestamp =
        IsoTimestamp(Instant.ofEpochMilli(this).toString())

    private fun Long.toIsoDate(): IsoDate =
        IsoDate(Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().toString())

    private fun String.toQuantity(): Quantity =
        Quantity(
            amount = firstNumber(),
            unit = toFoodUnit(),
            truthState = if (isBlank()) TruthState.UNKNOWN else TruthState.USER_CONFIRMED,
        )

    private fun String.firstNumber(): Double? =
        Regex("""\d+(\.\d+)?""").find(this)?.value?.toDoubleOrNull()

    private fun String.toFoodUnit(): FoodUnit {
        val lower = lowercase()
        return when {
            lower.isBlank() -> FoodUnit.UNKNOWN
            "kg" in lower || "kilogram" in lower -> FoodUnit.KILOGRAM
            Regex("""\bg\b|gram""").containsMatchIn(lower) -> FoodUnit.GRAM
            "ml" in lower || "milliliter" in lower -> FoodUnit.MILLILITER
            Regex("""\bl\b|liter""").containsMatchIn(lower) -> FoodUnit.LITER
            "cup" in lower -> FoodUnit.CUP
            "tbsp" in lower || "tablespoon" in lower -> FoodUnit.TABLESPOON
            "tsp" in lower || "teaspoon" in lower -> FoodUnit.TEASPOON
            "oz" in lower || "ounce" in lower -> FoodUnit.OUNCE
            "lb" in lower || "pound" in lower -> FoodUnit.POUND
            "serv" in lower -> FoodUnit.SERVING
            "pack" in lower -> FoodUnit.PACKAGE
            "can" in lower -> FoodUnit.CAN
            "bottle" in lower -> FoodUnit.BOTTLE
            "slice" in lower -> FoodUnit.SLICE
            else -> FoodUnit.EACH
        }
    }

    private val Int?.truthState: TruthState
        get() = if (this == null) TruthState.UNKNOWN else TruthState.USER_CONFIRMED

    private fun String.csvTokens(): List<String> =
        split(',', '#').map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun String.linesOrSentences(): List<String> =
        lineSequence()
            .flatMap { it.split(';').asSequence() }
            .map { it.trim().trim('-', '*') }
            .filter { it.isNotBlank() }
            .toList()
            .ifEmpty { listOf(trim()).filter { it.isNotBlank() } }

    private fun List<InventoryItem>.matchFoodId(name: String): FoodId? =
        firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { FoodId("legacy:food:inventory:${it.id}") }

    private fun List<InventoryItem>.matchFoodIds(text: String): List<FoodId> {
        val lower = text.lowercase()
        return filter { it.name.lowercase() in lower }.map { FoodId("legacy:food:inventory:${it.id}") }
    }

    private fun com.wonderfood.app.data.MealSlot.toCoreMealSlot(): com.wonderfood.core.model.MealSlot =
        when (this) {
            com.wonderfood.app.data.MealSlot.BREAKFAST -> com.wonderfood.core.model.MealSlot.BREAKFAST
            com.wonderfood.app.data.MealSlot.LUNCH -> com.wonderfood.core.model.MealSlot.LUNCH
            com.wonderfood.app.data.MealSlot.DINNER -> com.wonderfood.core.model.MealSlot.DINNER
            com.wonderfood.app.data.MealSlot.SNACK -> com.wonderfood.core.model.MealSlot.SNACK
            com.wonderfood.app.data.MealSlot.FLEX -> com.wonderfood.core.model.MealSlot.ANYTIME
        }

    private fun MealPlanEntryStatus.toCoreStatus(): PlanEntryStatus =
        when (this) {
            MealPlanEntryStatus.DRAFT -> PlanEntryStatus.PLANNED
            MealPlanEntryStatus.PLANNED -> PlanEntryStatus.PLANNED
            MealPlanEntryStatus.EATEN -> PlanEntryStatus.EATEN
            MealPlanEntryStatus.SKIPPED -> PlanEntryStatus.SKIPPED
        }

    private fun String.toCoreFoodEventType(): FoodEventType =
        when (this) {
            "MEAL" -> FoodEventType.EATEN
            "COOK" -> FoodEventType.COOKED
            "SHOP" -> FoodEventType.SHOPPING_NEEDED
            "GROCERY_PURCHASE" -> FoodEventType.PURCHASED
            "PANTRY_USE" -> FoodEventType.STOCK_CONSUMED
            else -> FoodEventType.UPDATED
        }

    private fun com.wonderfood.app.data.FoodEvent.subjectRef(): EntityRef =
        when {
            inventoryItemId != null -> EntityRef(EntityType.STOCK_LOT, "legacy:stock_lot:inventory:$inventoryItemId")
            mealLogId != null -> EntityRef(EntityType.MEAL_LOG, "legacy:meal_log:$mealLogId")
            relatedRecipeId != null -> EntityRef(EntityType.RECIPE, "legacy:recipe:$relatedRecipeId")
            else -> EntityRef(EntityType.FOOD_EVENT, "legacy:food_event:$id")
        }

    private fun FoodEventConfidence.toCoreConfidence(): Confidence =
        when (this) {
            FoodEventConfidence.EXACT -> Confidence(1.0, TruthState.USER_CONFIRMED, "Exact legacy event.")
            FoodEventConfidence.ESTIMATED -> Confidence(0.7, TruthState.ESTIMATED, "Estimated legacy event.")
            FoodEventConfidence.AI_ESTIMATED -> Confidence(0.6, TruthState.ESTIMATED, "AI-estimated legacy event.")
        }

    private fun Long?.toMoney(currencyCode: String): Money? =
        this?.let { Money(it / 100.0, currencyCode, TruthState.USER_CONFIRMED) }
}
