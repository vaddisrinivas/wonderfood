package com.wonderfood.app.sync

import com.wonderfood.core.model.Confidence
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.Food
import com.wonderfood.core.model.FoodId
import com.wonderfood.core.model.FoodStatus
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.IsoDate
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.MealLogStatus
import com.wonderfood.core.model.MealSlot
import com.wonderfood.core.model.Page
import com.wonderfood.core.model.PageId
import com.wonderfood.core.model.PageKind
import com.wonderfood.core.model.PlanEntry
import com.wonderfood.core.model.PlanEntryId
import com.wonderfood.core.model.Money
import com.wonderfood.core.model.MealLog
import com.wonderfood.core.model.MealLogId
import com.wonderfood.core.model.MealPlan
import com.wonderfood.core.model.MealPlanId
import com.wonderfood.core.model.MealPlanStatus
import com.wonderfood.core.model.PlanEntryStatus
import com.wonderfood.core.model.Quantity
import com.wonderfood.core.model.RecipeIngredient
import com.wonderfood.core.model.RecipeIngredientId
import com.wonderfood.core.model.Recipe
import com.wonderfood.core.model.RecipeId
import com.wonderfood.core.model.RecipeStatus
import com.wonderfood.core.model.RecipeStep
import com.wonderfood.core.model.RecipeStepId
import com.wonderfood.core.model.Receipt
import com.wonderfood.core.model.ReceiptId
import com.wonderfood.core.model.ReceiptStatus
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

object WonderFoodWorkspaceSnapshotMerger {
    fun merge(
        snapshot: WonderFoodSnapshot,
        rows: List<GoogleSheetsWorkspaceRow>,
        updatedAt: String,
    ): WorkspaceMergeResult {
        require(updatedAt.isNotBlank()) { "Merge timestamp must not be blank." }
        val changes = mutableListOf<WorkspaceMergeChange>()
        val conflicts = mutableListOf<WorkspaceMergeConflict>()
        val normalizedRows = rows.map { row -> row.withStableIdentifier() }
        val dedupedRows = normalizedRows.groupBy { row -> row.tab to row.identifier }
            .map { (_, groupedRows) ->
                if (groupedRows.size > 1) {
                    val row = groupedRows.first()
                    conflicts += WorkspaceMergeConflict(
                        table = row.tab,
                        identifier = row.identifier,
                        field = "identifier",
                        reason = "Duplicate workspace rows share this identifier; the last visible row was applied.",
                    )
                }
                groupedRows.last()
            }
        dedupedRows.forEach { row -> validateFriendlyRow(row, conflicts) }
        val rowsByTable = dedupedRows.groupBy { it.tab }

        val foodNamesById = snapshot.foods.associate { it.id.value to it.name }.toMutableMap()
        val mergedFoods = snapshot.foods.toMutableList()

        val mergedStockLots = snapshot.stockLots.map { lot ->
            val row = rowsByTable[WonderFoodWorkspaceSchema.KITCHEN].orEmpty().firstOrNull { it.identifier == lot.id.value }
                ?: return@map lot
            var next = lot
            val foodName = row.value("Food")
            if (foodName.isNotBlank()) {
                val foodIndex = mergedFoods.indexOfFirst { it.id == lot.foodId }
                if (foodIndex >= 0 && mergedFoods[foodIndex].name != foodName) {
                    mergedFoods[foodIndex] = mergedFoods[foodIndex].copy(name = foodName)
                    foodNamesById[lot.foodId.value] = foodName
                    changes += change(row, "Food", mergedFoods[foodIndex].name, foodName)
                }
            }
            row.value("On hand").toDoubleOrNull()?.let { amount ->
                val unit = row.value("Unit").toFoodUnit(next.quantity.unit)
                val quantity = Quantity(amount, unit, TruthState.USER_CONFIRMED)
                if (next.quantity != quantity) {
                    changes += change(row, "On hand", next.quantity.amount?.toString().orEmpty(), amount.toString())
                    next = next.copy(quantity = quantity)
                }
            }
            row.value("Pantry state").toStockLotStatusOrNull()?.let { status ->
                if (next.status != status) {
                    changes += change(row, "Pantry state", next.status.name, status.name)
                    next = next.copy(status = status)
                }
            }
            row.value("Location").takeIf { it.isNotBlank() }?.let { location ->
                if (next.location != location) {
                    changes += change(row, "Location", next.location.orEmpty(), location)
                    next = next.copy(location = location)
                }
            }
            row.value("Best by").takeIf { it.isNotBlank() }?.let { bestBy ->
                val dateText = bestBy.substringBefore("T")
                val date = if (dateText.isIsoDateLike()) IsoDate(dateText) else null
                if (date != null && next.expiresOn != date) {
                    changes += change(row, "Best by", next.expiresOn?.value.orEmpty(), date.value)
                    next = next.copy(expiresOn = date)
                }
            }
            next
        }

        val pagesById = snapshot.pages.associateBy { it.id.value }.toMutableMap()
        val mergedShopping = snapshot.shoppingItems.map { item ->
            val row = rowsByTable[WonderFoodWorkspaceSchema.SHOPPING].orEmpty().firstOrNull { it.identifier == item.id.value }
                ?: return@map item
            var next = item
            row.value("Item").takeIf { it.isNotBlank() }?.let { label ->
                val page = pagesById[item.pageId.value]
                if (page != null && page.title != label) {
                    pagesById[item.pageId.value] = page.copy(title = label)
                    changes += change(row, "Item", page.title, label)
                }
            }
            row.value("Needed").toDoubleOrNull()?.let { amount ->
                val unit = row.value("Unit").toFoodUnit(next.quantity.unit)
                val quantity = Quantity(amount, unit, TruthState.USER_CONFIRMED)
                if (next.quantity != quantity) {
                    changes += change(row, "Needed", next.quantity.amount?.toString().orEmpty(), amount.toString())
                    next = next.copy(quantity = quantity)
                }
            }
            row.value("Cart state").toShoppingStatusOrNull()?.let { status ->
                if (next.status != status) {
                    changes += change(row, "Cart state", next.status.name, status.name)
                    next = next.copy(status = status)
                }
            }
            row.value("Reason").let { reason ->
                if (reason.isNotBlank() && next.reason != reason) {
                    changes += change(row, "Reason", next.reason.orEmpty(), reason)
                    next = next.copy(reason = reason)
                }
            }
            next
        }

        val mergedRecipes = snapshot.recipes.map { recipe ->
            val row = rowsByTable[WonderFoodWorkspaceSchema.RECIPES].orEmpty().firstOrNull { it.identifier == recipe.id.value }
                ?: return@map recipe
            var next = recipe
            row.value("Recipe").takeIf { it.isNotBlank() }?.let { title ->
                if (next.title != title) {
                    changes += change(row, "Recipe", next.title, title)
                    next = next.copy(title = title)
                }
            }
            row.value("Recipe state").toRecipeStatusOrNull()?.let { status ->
                if (next.status != status) {
                    changes += change(row, "Recipe state", next.status.name, status.name)
                    next = next.copy(status = status)
                }
            }
            row.value("Servings").toDoubleOrNull()?.let { servings ->
                val quantity = next.servings.copy(amount = servings, truthState = TruthState.USER_CONFIRMED)
                if (next.servings != quantity) {
                    changes += change(row, "Servings", next.servings.amount?.toString().orEmpty(), servings.toString())
                    next = next.copy(servings = quantity)
                }
            }
            row.value("Prep").toIntOrNull()?.let { prep ->
                if (next.prepMinutes != prep) {
                    changes += change(row, "Prep", next.prepMinutes?.toString().orEmpty(), prep.toString())
                    next = next.copy(prepMinutes = prep)
                }
            }
            row.value("Cook").toIntOrNull()?.let { cook ->
                if (next.cookMinutes != cook) {
                    changes += change(row, "Cook", next.cookMinutes?.toString().orEmpty(), cook.toString())
                    next = next.copy(cookMinutes = cook)
                }
            }
            row.value("Ingredients").takeIf { it.isNotBlank() }?.let { text ->
                val ingredients = text.lines().filter(String::isNotBlank).mapIndexed { index, line ->
                    RecipeIngredient(
                        id = RecipeIngredientId("${recipe.id.value}:workspace_ingredient:$index"),
                        recipeId = recipe.id,
                        foodId = null,
                        displayName = line.trim(),
                        quantity = Quantity(null, FoodUnit.UNKNOWN, TruthState.UNKNOWN),
                        preparation = null,
                        optional = false,
                        substituteFoodIds = emptyList(),
                        source = recipe.source,
                        confidence = recipe.confidence,
                        truthState = TruthState.USER_CONFIRMED,
                    )
                }
                if (ingredients.map { it.displayName } != next.ingredients.map { it.displayName }) {
                    changes += change(row, "Ingredients", next.ingredients.joinToString("\n") { it.displayName }, text)
                    next = next.copy(ingredients = ingredients)
                }
            }
            row.value("Directions").takeIf { it.isNotBlank() }?.let { text ->
                val steps = text.lines().filter(String::isNotBlank).mapIndexed { index, line ->
                    RecipeStep(
                        id = RecipeStepId("${recipe.id.value}:workspace_step:$index"),
                        recipeId = recipe.id,
                        order = index,
                        instruction = line.trim().removePrefix("${index + 1}.").trim(),
                        durationMinutes = null,
                        attachmentIds = emptyList(),
                        source = recipe.source,
                        confidence = recipe.confidence,
                        truthState = TruthState.USER_CONFIRMED,
                    )
                }
                if (steps.map { it.instruction } != next.steps.map { it.instruction }) {
                    changes += change(row, "Directions", next.steps.joinToString("\n") { it.instruction }, text)
                    next = next.copy(steps = steps)
                }
            }
            next
        }

        val mergedMealPlans = snapshot.mealPlans.map { plan ->
            val entries = plan.entries.map { entry ->
                val row = rowsByTable[WonderFoodWorkspaceSchema.MEALS].orEmpty().firstOrNull { it.identifier == entry.id.value }
                    ?: return@map entry
                var next = entry
                row.value("When").takeIf { it.isNotBlank() }?.substringBefore("T")?.let { dateText ->
                    val date = if (dateText.isIsoDateLike()) IsoDate(dateText) else null
                    if (date != null && next.date != date) {
                        changes += change(row, "When", next.date.value, date.value)
                        next = next.copy(date = date)
                    }
                }
                row.value("Slot").toMealSlotOrNull()?.let { slot ->
                    if (next.mealSlot != slot) {
                        changes += change(row, "Slot", next.mealSlot.name, slot.name)
                        next = next.copy(mealSlot = slot)
                    }
                }
                row.value("Meal state").toPlanEntryStatusOrNull()?.let { status ->
                    if (next.status != status) {
                        changes += change(row, "Meal state", next.status.name, status.name)
                        next = next.copy(status = status)
                    }
                }
                row.value("Servings").toDoubleOrNull()?.let { servings ->
                    val quantity = next.quantity.copy(amount = servings, truthState = TruthState.USER_CONFIRMED)
                    if (next.quantity != quantity) {
                        changes += change(row, "Servings", next.quantity.amount?.toString().orEmpty(), servings.toString())
                        next = next.copy(quantity = quantity)
                    }
                }
                next
            }
            plan.copy(entries = entries)
        }

        val mergedMealLogs = snapshot.mealLogs.map { log ->
            val row = rowsByTable[WonderFoodWorkspaceSchema.MEALS].orEmpty().firstOrNull { it.identifier == log.id.value }
                ?: return@map log
            var next = log
            row.value("Meal").takeIf { it.isNotBlank() }?.let { title ->
                val page = pagesById[log.pageId.value]
                if (page != null && page.title != title) {
                    pagesById[log.pageId.value] = page.copy(title = title)
                    changes += change(row, "Meal", page.title, title)
                }
            }
            row.value("When").takeIf { it.isNotBlank() }?.let { whenText ->
                val timestamp = whenText.toIsoTimestamp()
                if (timestamp != null && next.occurredAt != timestamp) {
                    changes += change(row, "When", next.occurredAt.value, timestamp.value)
                    next = next.copy(occurredAt = timestamp)
                }
            }
            row.value("Slot").toMealSlotOrNull()?.let { slot ->
                if (next.mealSlot != slot) {
                    changes += change(row, "Slot", next.mealSlot.name, slot.name)
                    next = next.copy(mealSlot = slot)
                }
            }
            row.value("Meal state").toMealLogStatusOrNull()?.let { status ->
                if (next.status != status) {
                    changes += change(row, "Meal state", next.status.name, status.name)
                    next = next.copy(status = status)
                }
            }
            next
        }

        val mergedReceipts = snapshot.receipts.map { receipt ->
            val row = rowsByTable[WonderFoodWorkspaceSchema.PURCHASES].orEmpty().firstOrNull { it.identifier == receipt.id.value }
                ?: return@map receipt
            var next = receipt
            row.value("Purchase").takeIf { it.isNotBlank() }?.let { title ->
                val page = pagesById[receipt.pageId.value]
                if (page != null && page.title != title) {
                    pagesById[receipt.pageId.value] = page.copy(title = title)
                    changes += change(row, "Purchase", page.title, title)
                }
            }
            row.value("Merchant").takeIf { it.isNotBlank() }?.let { merchant ->
                if (next.merchantName != merchant) {
                    changes += change(row, "Merchant", next.merchantName.orEmpty(), merchant)
                    next = next.copy(merchantName = merchant)
                }
            }
            row.value("Purchased").takeIf { it.isNotBlank() }?.let { purchased ->
                val timestamp = purchased.toIsoTimestamp()
                if (timestamp != null && next.purchasedAt != timestamp) {
                    changes += change(row, "Purchased", next.purchasedAt?.value.orEmpty(), timestamp.value)
                    next = next.copy(purchasedAt = timestamp)
                }
            }
            val currency = row.value("Currency").ifBlank { next.total?.currencyCode ?: next.subtotal?.currencyCode ?: "USD" }
            row.value("Subtotal").toDoubleOrNull()?.let { amount ->
                val money = Money(amount, currency, TruthState.USER_CONFIRMED)
                if (next.subtotal != money) {
                    changes += change(row, "Subtotal", next.subtotal?.amount?.toString().orEmpty(), amount.toString())
                    next = next.copy(subtotal = money)
                }
            }
            row.value("Total").toDoubleOrNull()?.let { amount ->
                val money = Money(amount, currency, TruthState.USER_CONFIRMED)
                if (next.total != money) {
                    changes += change(row, "Total", next.total?.amount?.toString().orEmpty(), amount.toString())
                    next = next.copy(total = money)
                }
            }
            row.value("Purchase state").toReceiptStatusOrNull()?.let { status ->
                if (next.status != status) {
                    changes += change(row, "Purchase state", next.status.name, status.name)
                    next = next.copy(status = status)
                }
            }
            next
        }

        val knownKitchenIds = snapshot.stockLots.map { it.id.value }.toSet()
        val newKitchenRows = rowsByTable[WonderFoodWorkspaceSchema.KITCHEN].orEmpty()
            .filterNot { it.identifier in knownKitchenIds }
            .filter { it.value("Food").isNotBlank() }
        val createdFoods = mutableListOf<Food>()
        val createdStockLots = mutableListOf<StockLot>()
        val createdPages = mutableListOf<Page>()
        newKitchenRows.forEach { row ->
            val foodId = FoodId("${row.identifier}:food")
            val pageId = PageId("${row.identifier}:page")
            val stockLotId = StockLotId(row.identifier)
            val source = row.source(updatedAt)
            createdPages += Page(
                id = pageId,
                title = row.value("Food"),
                kind = PageKind.FOOD,
                entity = EntityRef(EntityType.FOOD, foodId.value),
                aliases = listOf(row.value("Food")),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = workspaceConfidence(),
            )
            createdFoods += Food(
                id = foodId,
                pageId = pageId,
                name = row.value("Food"),
                status = FoodStatus.ACTIVE,
                aliasIds = emptyList(),
                stockLotIds = listOf(stockLotId),
                nutritionSnapshotIds = emptyList(),
                attachmentIds = emptyList(),
                source = source,
                confidence = workspaceConfidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
            createdStockLots += StockLot(
                id = stockLotId,
                foodId = foodId,
                quantity = Quantity(row.value("On hand").toDoubleOrNull(), row.value("Unit").toFoodUnit(FoodUnit.UNKNOWN), TruthState.USER_CONFIRMED),
                purchasedOn = null,
                expiresOn = row.value("Best by").takeIf { it.isNotBlank() }?.substringBefore("T")?.let { if (it.isIsoDateLike()) IsoDate(it) else null },
                location = row.value("Location").ifBlank { null },
                status = row.value("Pantry state").toStockLotStatusOrNull() ?: StockLotStatus.AVAILABLE,
                source = source,
                confidence = workspaceConfidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
            changes += createChange(row, "Kitchen item")
        }

        val knownShoppingIds = snapshot.shoppingItems.map { it.id.value }.toSet()
        val newShoppingRows = rowsByTable[WonderFoodWorkspaceSchema.SHOPPING].orEmpty()
            .filterNot { it.identifier in knownShoppingIds }
            .filter { it.value("Item").isNotBlank() }
        val createdShopping = newShoppingRows.map { row ->
            val pageId = PageId("${row.identifier}:page")
            val itemId = ShoppingItemId(row.identifier)
            val source = row.source(updatedAt)
            createdPages += Page(
                id = pageId,
                title = row.value("Item"),
                kind = PageKind.SHOPPING_ITEM,
                entity = EntityRef(EntityType.SHOPPING_ITEM, itemId.value),
                aliases = listOf(row.value("Item")),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = workspaceConfidence(),
            )
            changes += createChange(row, "Shopping item")
            ShoppingItem(
                id = itemId,
                pageId = pageId,
                foodId = null,
                recipeId = null,
                quantity = Quantity(row.value("Needed").toDoubleOrNull(), row.value("Unit").toFoodUnit(FoodUnit.UNKNOWN), TruthState.USER_CONFIRMED),
                reason = row.value("Reason").ifBlank { null },
                status = row.value("Cart state").toShoppingStatusOrNull() ?: ShoppingItemStatus.NEEDED,
                source = source,
                confidence = workspaceConfidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val knownRecipeIds = snapshot.recipes.map { it.id.value }.toSet()
        val newRecipeRows = rowsByTable[WonderFoodWorkspaceSchema.RECIPES].orEmpty()
            .filterNot { it.identifier in knownRecipeIds }
            .filter { it.value("Recipe").isNotBlank() }
        val createdRecipes = newRecipeRows.map { row ->
            val recipeId = RecipeId(row.identifier)
            val pageId = PageId("${row.identifier}:page")
            val source = row.source(updatedAt)
            createdPages += Page(
                id = pageId,
                title = row.value("Recipe"),
                kind = PageKind.RECIPE,
                entity = EntityRef(EntityType.RECIPE, recipeId.value),
                aliases = emptyList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = workspaceConfidence(),
            )
            changes += createChange(row, "Recipe")
            Recipe(
                id = recipeId,
                pageId = pageId,
                title = row.value("Recipe"),
                description = null,
                status = row.value("Recipe state").toRecipeStatusOrNull() ?: RecipeStatus.ACTIVE,
                servings = Quantity(row.value("Servings").toDoubleOrNull(), FoodUnit.SERVING, TruthState.USER_CONFIRMED),
                prepMinutes = row.value("Prep").toIntOrNull(),
                cookMinutes = row.value("Cook").toIntOrNull(),
                ingredients = row.value("Ingredients").lines().filter(String::isNotBlank).mapIndexed { index, line ->
                    RecipeIngredient(
                        id = RecipeIngredientId("${recipeId.value}:workspace_ingredient:$index"),
                        recipeId = recipeId,
                        foodId = null,
                        displayName = line.trim(),
                        quantity = Quantity(null, FoodUnit.UNKNOWN, TruthState.UNKNOWN),
                        preparation = null,
                        optional = false,
                        substituteFoodIds = emptyList(),
                        source = source,
                        confidence = workspaceConfidence(),
                        truthState = TruthState.USER_CONFIRMED,
                    )
                },
                steps = row.value("Directions").lines().filter(String::isNotBlank).mapIndexed { index, line ->
                    RecipeStep(
                        id = RecipeStepId("${recipeId.value}:workspace_step:$index"),
                        recipeId = recipeId,
                        order = index,
                        instruction = line.trim().removePrefix("${index + 1}.").trim(),
                        durationMinutes = null,
                        attachmentIds = emptyList(),
                        source = source,
                        confidence = workspaceConfidence(),
                        truthState = TruthState.USER_CONFIRMED,
                    )
                },
                nutritionSnapshotIds = emptyList(),
                attachmentIds = emptyList(),
                source = source,
                confidence = workspaceConfidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val knownMealLogIds = snapshot.mealLogs.map { it.id.value }.toSet()
        val knownPlanEntryIds = snapshot.mealPlans.flatMap { it.entries }.map { it.id.value }.toSet()
        val newMealRows = rowsByTable[WonderFoodWorkspaceSchema.MEALS].orEmpty()
            .filterNot { it.identifier in knownMealLogIds || it.identifier in knownPlanEntryIds }
            .filter { it.value("Meal").isNotBlank() }
        val createdMealLogs = mutableListOf<MealLog>()
        val createdPlanEntries = mutableListOf<PlanEntry>()
        val importedPlanId = MealPlanId("workspace:imported_meal_plan")
        newMealRows.forEach { row ->
            val source = row.source(updatedAt)
            val state = row.value("Meal state").normalizedEnumName()
            if (state == "PLANNED" || state == "IDEA" || state.isBlank()) {
                createdPlanEntries += PlanEntry(
                    id = PlanEntryId(row.identifier),
                    mealPlanId = importedPlanId,
                    date = row.value("When").takeIf { it.isNotBlank() }?.substringBefore("T")?.let { if (it.isIsoDateLike()) IsoDate(it) else null } ?: IsoDate(updatedAt.substringBefore("T")),
                    mealSlot = row.value("Slot").toMealSlotOrNull() ?: MealSlot.ANYTIME,
                    recipeId = null,
                    foodId = null,
                    quantity = Quantity(row.value("Servings").toDoubleOrNull(), FoodUnit.SERVING, TruthState.USER_CONFIRMED),
                    status = row.value("Meal state").toPlanEntryStatusOrNull() ?: PlanEntryStatus.PLANNED,
                    source = source,
                    confidence = workspaceConfidence(),
                    truthState = TruthState.USER_CONFIRMED,
                )
                changes += createChange(row, "Meal plan entry")
            } else {
                val logId = MealLogId(row.identifier)
                val pageId = PageId("${row.identifier}:page")
                createdPages += Page(
                    id = pageId,
                    title = row.value("Meal"),
                    kind = PageKind.MEAL_LOG,
                    entity = EntityRef(EntityType.MEAL_LOG, logId.value),
                    aliases = emptyList(),
                    relationIds = emptyList(),
                    attachmentIds = emptyList(),
                    truthState = TruthState.USER_CONFIRMED,
                    source = source,
                    confidence = workspaceConfidence(),
                )
                createdMealLogs += MealLog(
                    id = logId,
                    pageId = pageId,
                    occurredAt = row.value("When").toIsoTimestamp() ?: IsoTimestamp(updatedAt),
                    mealSlot = row.value("Slot").toMealSlotOrNull() ?: MealSlot.ANYTIME,
                    planEntryId = null,
                    foodIds = emptyList(),
                    recipeIds = emptyList(),
                    nutritionSnapshotIds = emptyList(),
                    status = row.value("Meal state").toMealLogStatusOrNull() ?: MealLogStatus.CONFIRMED,
                    source = source,
                    confidence = workspaceConfidence(),
                    truthState = TruthState.USER_CONFIRMED,
                )
                changes += createChange(row, "Meal log")
            }
        }
        val createdMealPlans = if (createdPlanEntries.isEmpty() || snapshot.mealPlans.any { it.id == importedPlanId }) {
            emptyList()
        } else {
            val source = Source(
                id = SourceId("workspace:source:imported_meal_plan"),
                kind = SourceKind.IMPORT,
                label = "Workspace import",
                externalId = null,
                uri = null,
                capturedAt = IsoTimestamp(updatedAt),
                truthState = TruthState.USER_CONFIRMED,
            )
            val pageId = PageId("workspace:imported_meal_plan:page")
            createdPages += Page(
                id = pageId,
                title = "Imported workspace meals",
                kind = PageKind.MEAL_PLAN,
                entity = EntityRef(EntityType.MEAL_PLAN, importedPlanId.value),
                aliases = emptyList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = workspaceConfidence(),
            )
            listOf(
                MealPlan(
                    id = importedPlanId,
                    pageId = pageId,
                    name = "Imported workspace meals",
                    startsOn = createdPlanEntries.minBy { it.date.value }.date,
                    endsOn = createdPlanEntries.maxBy { it.date.value }.date,
                    status = MealPlanStatus.ACCEPTED,
                    entries = createdPlanEntries,
                    source = source,
                    confidence = workspaceConfidence(),
                    truthState = TruthState.USER_CONFIRMED,
                ),
            )
        }

        val knownReceiptIds = snapshot.receipts.map { it.id.value }.toSet()
        val newPurchaseRows = rowsByTable[WonderFoodWorkspaceSchema.PURCHASES].orEmpty()
            .filterNot { it.identifier in knownReceiptIds }
            .filter { it.value("Purchase").isNotBlank() || it.value("Merchant").isNotBlank() }
        val createdReceipts = newPurchaseRows.map { row ->
            val receiptId = ReceiptId(row.identifier)
            val pageId = PageId("${row.identifier}:page")
            val source = row.source(updatedAt)
            val title = row.value("Purchase").ifBlank { row.value("Merchant").ifBlank { row.identifier } }
            createdPages += Page(
                id = pageId,
                title = title,
                kind = PageKind.RECEIPT,
                entity = EntityRef(EntityType.RECEIPT, receiptId.value),
                aliases = emptyList(),
                relationIds = emptyList(),
                attachmentIds = emptyList(),
                truthState = TruthState.USER_CONFIRMED,
                source = source,
                confidence = workspaceConfidence(),
            )
            val currency = row.value("Currency").ifBlank { "USD" }
            changes += createChange(row, "Purchase")
            Receipt(
                id = receiptId,
                pageId = pageId,
                merchantName = row.value("Merchant").ifBlank { null },
                purchasedAt = row.value("Purchased").toIsoTimestamp(),
                itemIds = emptyList(),
                subtotal = row.value("Subtotal").toDoubleOrNull()?.let { Money(it, currency, TruthState.USER_CONFIRMED) },
                total = row.value("Total").toDoubleOrNull()?.let { Money(it, currency, TruthState.USER_CONFIRMED) },
                attachmentIds = emptyList(),
                status = row.value("Purchase state").toReceiptStatusOrNull() ?: ReceiptStatus.CAPTURED,
                source = source,
                confidence = workspaceConfidence(),
                truthState = TruthState.USER_CONFIRMED,
            )
        }

        val finalMealPlans = if (createdPlanEntries.isNotEmpty() && snapshot.mealPlans.any { it.id == importedPlanId }) {
            mergedMealPlans.map { plan ->
                if (plan.id == importedPlanId) {
                    plan.copy(entries = plan.entries + createdPlanEntries)
                } else {
                    plan
                }
            }
        } else {
            mergedMealPlans + createdMealPlans
        }

        return WorkspaceMergeResult(
            snapshot = snapshot.copy(
                pages = snapshot.pages.map { page -> pagesById[page.id.value] ?: page } + createdPages,
                foods = mergedFoods + createdFoods,
                stockLots = mergedStockLots + createdStockLots,
                recipes = mergedRecipes + createdRecipes,
                mealPlans = finalMealPlans,
                mealLogs = mergedMealLogs + createdMealLogs,
                shoppingItems = mergedShopping + createdShopping,
                receipts = mergedReceipts + createdReceipts,
            ),
            changes = changes,
            conflicts = conflicts,
            mergeClock = updatedAt,
            fieldClocks = changes.mapIndexed { index, change ->
                WorkspaceFieldClock(
                    table = change.table,
                    identifier = change.identifier,
                    field = change.field,
                    version = "$updatedAt#${index + 1}",
                )
            },
        )
    }

    private fun validateFriendlyRow(
        row: GoogleSheetsWorkspaceRow,
        conflicts: MutableList<WorkspaceMergeConflict>,
    ) {
        when (row.tab) {
            WonderFoodWorkspaceSchema.KITCHEN -> {
                row.requireDouble("On hand", conflicts)
                row.requireStatus("Pantry state", conflicts) { toStockLotStatusOrNull() }
                row.requireDate("Best by", conflicts)
            }
            WonderFoodWorkspaceSchema.SHOPPING -> {
                row.requireDouble("Needed", conflicts)
                row.requireStatus("Cart state", conflicts) { toShoppingStatusOrNull() }
            }
            WonderFoodWorkspaceSchema.RECIPES -> {
                row.requireStatus("Recipe state", conflicts) { toRecipeStatusOrNull() }
                row.requireDouble("Servings", conflicts)
                row.requireInt("Prep", conflicts)
                row.requireInt("Cook", conflicts)
            }
            WonderFoodWorkspaceSchema.MEALS -> {
                row.requireFlexibleWhen("When", conflicts)
                row.requireStatus("Slot", conflicts) { toMealSlotOrNull() }
                row.requireStatus("Meal state", conflicts) { toPlanEntryStatusOrNull() ?: toMealLogStatusOrNull() }
                row.requireDouble("Servings", conflicts)
            }
            WonderFoodWorkspaceSchema.PURCHASES -> {
                row.requireTimestamp("Purchased", conflicts)
                row.requireDouble("Subtotal", conflicts)
                row.requireDouble("Total", conflicts)
                row.requireStatus("Purchase state", conflicts) { toReceiptStatusOrNull() }
            }
        }
    }

    private fun GoogleSheetsWorkspaceRow.requireDouble(
        field: String,
        conflicts: MutableList<WorkspaceMergeConflict>,
    ) {
        val raw = value(field)
        if (raw.isNotBlank() && raw.toDoubleOrNull() == null) {
            conflicts += invalid(field, "Expected a number, but found '$raw'.")
        }
    }

    private fun GoogleSheetsWorkspaceRow.requireInt(
        field: String,
        conflicts: MutableList<WorkspaceMergeConflict>,
    ) {
        val raw = value(field)
        if (raw.isNotBlank() && raw.toIntOrNull() == null) {
            conflicts += invalid(field, "Expected a whole number of minutes, but found '$raw'.")
        }
    }

    private fun GoogleSheetsWorkspaceRow.requireDate(
        field: String,
        conflicts: MutableList<WorkspaceMergeConflict>,
    ) {
        val raw = value(field)
        if (raw.isNotBlank() && !raw.substringBefore("T").isIsoDateLike()) {
            conflicts += invalid(field, "Expected an ISO date like 2026-07-19, but found '$raw'.")
        }
    }

    private fun GoogleSheetsWorkspaceRow.requireTimestamp(
        field: String,
        conflicts: MutableList<WorkspaceMergeConflict>,
    ) {
        val raw = value(field)
        if (raw.isNotBlank() && raw.toIsoTimestamp() == null) {
            conflicts += invalid(field, "Expected an ISO timestamp or date, but found '$raw'.")
        }
    }

    private fun GoogleSheetsWorkspaceRow.requireFlexibleWhen(
        field: String,
        conflicts: MutableList<WorkspaceMergeConflict>,
    ) {
        val raw = value(field)
        if (raw.isBlank()) return
        val valid = if ("T" in raw) raw.toIsoTimestamp() != null else raw.isIsoDateLike()
        if (!valid) {
            conflicts += invalid(field, "Expected an ISO date or timestamp, but found '$raw'.")
        }
    }

    private fun <T> GoogleSheetsWorkspaceRow.requireStatus(
        field: String,
        conflicts: MutableList<WorkspaceMergeConflict>,
        parser: String.() -> T?,
    ) {
        val raw = value(field)
        if (raw.isNotBlank() && raw.parser() == null) {
            conflicts += invalid(field, "Unknown workspace option '$raw'.")
        }
    }

    private fun GoogleSheetsWorkspaceRow.invalid(field: String, reason: String): WorkspaceMergeConflict =
        WorkspaceMergeConflict(
            table = tab,
            identifier = identifier,
            field = field,
            reason = reason,
        )

    private fun change(row: GoogleSheetsWorkspaceRow, field: String, before: String, after: String): WorkspaceMergeChange =
        WorkspaceMergeChange(row.tab, row.identifier, field, before, after)

    private fun createChange(row: GoogleSheetsWorkspaceRow, subject: String): WorkspaceMergeChange =
        WorkspaceMergeChange(row.tab, row.identifier, subject, "", "created")

    private fun GoogleSheetsWorkspaceRow.withStableIdentifier(): GoogleSheetsWorkspaceRow {
        if (identifier.isNotBlank()) return this
        val title = when (tab) {
            WonderFoodWorkspaceSchema.KITCHEN -> value("Food")
            WonderFoodWorkspaceSchema.SHOPPING -> value("Item")
            WonderFoodWorkspaceSchema.RECIPES -> value("Recipe")
            WonderFoodWorkspaceSchema.MEALS -> value("Meal")
            WonderFoodWorkspaceSchema.PURCHASES -> value("Purchase").ifBlank { value("Merchant") }
            else -> ""
        }
        val stable = "workspace:${tab.slug()}:${title.slug()}"
        return copy(identifier = stable, values = values + ("identifier" to stable))
    }

    private fun String.slug(): String =
        trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "row" }

    private fun GoogleSheetsWorkspaceRow.source(updatedAt: String): Source =
        Source(
            id = SourceId("workspace:source:${identifier.slug()}"),
            kind = SourceKind.IMPORT,
            label = "Workspace import",
            externalId = identifier,
            uri = null,
            capturedAt = IsoTimestamp(updatedAt),
            truthState = TruthState.USER_CONFIRMED,
        )

    private fun workspaceConfidence(): Confidence =
        Confidence(0.72, TruthState.USER_CONFIRMED, "Imported from editable WonderFood workspace.")

    private fun GoogleSheetsWorkspaceRow.value(name: String): String =
        values[name].orEmpty().trim()

    private fun String.toFoodUnit(fallback: FoodUnit): FoodUnit =
        when (trim().lowercase()) {
            "item", "each", "ea" -> FoodUnit.EACH
            "g", "gram", "grams" -> FoodUnit.GRAM
            "kg", "kilogram", "kilograms" -> FoodUnit.KILOGRAM
            "ml", "milliliter", "milliliters" -> FoodUnit.MILLILITER
            "l", "liter", "liters" -> FoodUnit.LITER
            "cup", "cups" -> FoodUnit.CUP
            "tbsp", "tablespoon", "tablespoons" -> FoodUnit.TABLESPOON
            "tsp", "teaspoon", "teaspoons" -> FoodUnit.TEASPOON
            "oz", "ounce", "ounces" -> FoodUnit.OUNCE
            "lb", "pound", "pounds" -> FoodUnit.POUND
            "serving", "servings" -> FoodUnit.SERVING
            "package", "packages" -> FoodUnit.PACKAGE
            "can", "cans" -> FoodUnit.CAN
            "bottle", "bottles" -> FoodUnit.BOTTLE
            "slice", "slices" -> FoodUnit.SLICE
            "pinch" -> FoodUnit.PINCH
            "" -> fallback
            else -> fallback
        }

    private fun String.toStockLotStatusOrNull(): StockLotStatus? =
        enumValueOrNull(normalizedEnumName())

    private fun String.toShoppingStatusOrNull(): ShoppingItemStatus? =
        when (normalizedEnumName()) {
            "NEED" -> ShoppingItemStatus.NEEDED
            "BOUGHT" -> ShoppingItemStatus.PURCHASED
            else -> enumValueOrNull(normalizedEnumName())
        }

    private fun String.toRecipeStatusOrNull(): RecipeStatus? =
        when (normalizedEnumName()) {
            "REGULAR", "FAVORITE", "WANT_TO_TRY" -> RecipeStatus.ACTIVE
            "INBOX" -> RecipeStatus.DRAFT
            else -> enumValueOrNull(normalizedEnumName())
        }

    private fun String.toMealSlotOrNull(): MealSlot? =
        enumValueOrNull(normalizedEnumName())

    private fun String.toPlanEntryStatusOrNull(): PlanEntryStatus? =
        when (normalizedEnumName()) {
            "IDEA" -> PlanEntryStatus.PLANNED
            "COOKING" -> PlanEntryStatus.ACCEPTED
            "SERVED", "CONFIRMED" -> PlanEntryStatus.EATEN
            else -> enumValueOrNull(normalizedEnumName())
        }

    private fun String.toMealLogStatusOrNull(): MealLogStatus? =
        when (normalizedEnumName()) {
            "EATEN", "SERVED" -> MealLogStatus.CONFIRMED
            else -> enumValueOrNull(normalizedEnumName())
        }

    private fun String.toReceiptStatusOrNull(): ReceiptStatus? =
        when (normalizedEnumName()) {
            "DRAFT" -> ReceiptStatus.CAPTURED
            "NEEDS_REVIEW", "CONFIRMED" -> ReceiptStatus.REVIEWED
            "RETURNED" -> ReceiptStatus.RECONCILED
            else -> enumValueOrNull(normalizedEnumName())
        }

    private fun String.normalizedEnumName(): String =
        trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')

    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private fun String.toIsoTimestamp(): IsoTimestamp? =
        runCatching {
            IsoTimestamp(if ("T" in this) this else "${substringBefore("T")}T00:00:00Z")
        }.getOrNull()

    private fun String.isIsoDateLike(): Boolean =
        matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
}

data class WorkspaceMergeResult(
    val snapshot: WonderFoodSnapshot,
    val changes: List<WorkspaceMergeChange>,
    val conflicts: List<WorkspaceMergeConflict>,
    val mergeClock: String = "",
    val fieldClocks: List<WorkspaceFieldClock> = emptyList(),
)

data class WorkspaceMergeChange(
    val table: String,
    val identifier: String,
    val field: String,
    val before: String,
    val after: String,
)

data class WorkspaceMergeConflict(
    val table: String,
    val identifier: String,
    val field: String,
    val reason: String,
)

data class WorkspaceFieldClock(
    val table: String,
    val identifier: String,
    val field: String,
    val version: String,
)
