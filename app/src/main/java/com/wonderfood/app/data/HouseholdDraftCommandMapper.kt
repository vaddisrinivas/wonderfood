package com.wonderfood.app.data

import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.model.household.CalendarDate
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.EntityReference
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
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
import com.wonderfood.core.model.household.ShoppingReason
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import com.wonderfood.core.model.household.calculatedReconciliationDifference
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class HouseholdDraftCommandMapper(
    private val householdId: HouseholdId,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    fun toCommands(draft: FoodDraft, origin: FoodDraftCommandOrigin): List<HouseholdCommand> =
        when (draft) {
            is CompositeDraft -> draft.drafts.flatMap { toCommands(it, origin) }
            is InventoryDraft -> draft.items.flatMapIndexed { index, candidate ->
                inventoryCommands(candidate, origin, "inventory:$index")
            }
            is GroceryDraft -> draft.items.mapIndexed { index, candidate ->
                shoppingCommand(candidate, origin, "grocery:$index")
            }
            is ReceiptDraft -> receiptCommands(draft, origin)
            is RecipeDraft -> recipeCommands(draft, origin)
            is MealLogDraft -> mealEntryCommands(draft, origin)
            is MealPlanDraft -> mealPlanCommands(draft, origin)
            is LinkActionDraft,
            -> emptyList()
        }

    private fun inventoryCommands(
        candidate: FoodCandidate,
        origin: FoodDraftCommandOrigin,
        scope: String,
        kind: ItemKind = ItemKind.FOOD,
        unitCost: Money? = null,
        purchaseLineId: EntityId? = null,
    ): List<HouseholdCommand> {
        val now = UtcTimestamp(clockMillis())
        val itemId = entityId(scope, candidate.name, "item")
        val lotId = entityId(scope, candidate.name, "lot")
        val item = Item(
            metadata = metadata(itemId, now, origin),
            name = candidate.name.trim(),
            kind = kind,
            category = candidate.category.ifBlank { null },
            defaultUnit = candidate.quantity.quantityUnit(),
            notes = candidate.notes.ifBlank { null },
        )
        val lot = InventoryLot(
            metadata = metadata(lotId, now, origin),
            itemId = itemId,
            quantity = candidate.quantity.quantity(),
            expiresOn = candidate.expiresAtMillis?.toUtcDate(),
            purchaseLineId = purchaseLineId,
            unitCost = unitCost,
            status = InventoryLotStatus.AVAILABLE,
        )
        val nutrition = nutritionSnapshotForInventory(candidate, origin, scope, itemId, now)
        return listOf(
            HouseholdCommand.UpsertItem(
                record = commandRecord(scope, "UpsertItem", now, origin, itemId),
                item = item,
            ),
            HouseholdCommand.UpsertInventoryLot(
                record = commandRecord(scope, "UpsertInventoryLot", now, origin, lotId),
                lot = lot,
            ),
        ) + listOfNotNull(nutrition)
    }

    private fun receiptInventoryCommands(
        item: ReceiptItemDraft,
        draft: ReceiptDraft,
        origin: FoodDraftCommandOrigin,
        scope: String,
        purchaseId: EntityId,
    ): List<HouseholdCommand> {
        val inventory = inventoryCommands(
            candidate = item.food,
            origin = origin,
            scope = scope,
            kind = if (item.disposition == ReceiptItemDisposition.HOUSEHOLD) ItemKind.HOUSEHOLD else ItemKind.FOOD,
            unitCost = item.linePriceCents?.let { Money(it, draft.currencyCode.uppercase()) },
            purchaseLineId = entityId(scope, item.food.name, "purchase_line"),
        )
        val upsertItem = inventory[0] as HouseholdCommand.UpsertItem
        val upsertLot = inventory[1] as HouseholdCommand.UpsertInventoryLot
        return inventory + purchaseLineCommand(item, draft, origin, scope, purchaseId, upsertItem.item.metadata.id, upsertLot.lot.metadata.id)
    }

    private fun receiptCommands(draft: ReceiptDraft, origin: FoodDraftCommandOrigin): List<HouseholdCommand> {
        val now = UtcTimestamp(clockMillis())
        val receiptIdentity = receiptIdentity(draft)
        val receiptScope = "receipt:$receiptIdentity"
        val purchaseId = entityId(receiptScope, "purchase", "purchase")
        val purchasableItems = draft.items.filterNot { it.disposition == ReceiptItemDisposition.IGNORE }
        val lineTotal = purchasableItems.mapNotNull { it.linePriceCents }.takeIf { it.isNotEmpty() }?.sum()
        val purchase = Purchase(
            metadata = metadata(purchaseId, now, origin),
            occurredAt = draft.purchasedAtMillis?.let(::UtcTimestamp) ?: now,
            receiptAttachmentIds = draft.receiptId?.let { listOf(receiptAttachmentId(it)) }.orEmpty(),
            subtotal = draft.subtotalCents?.let { Money(it, draft.currencyCode.uppercase()) },
            tax = draft.taxCents?.let { Money(it, draft.currencyCode.uppercase()) },
            discount = draft.discountCents?.let { Money(it, draft.currencyCode.uppercase()) },
            total = (draft.totalCents ?: lineTotal)?.let { Money(it, draft.currencyCode.uppercase()) },
            paymentNote = buildPurchasePaymentNote(draft),
            status = PurchaseStatus.REVIEWED,
        )
        val purchaseLines = draft.items.mapIndexed { index, item ->
            val scope = "$receiptScope:$index"
            when (item.disposition) {
                ReceiptItemDisposition.INVENTORY,
                ReceiptItemDisposition.HOUSEHOLD,
                -> receiptInventoryCommands(item, draft, origin, scope, purchaseId)
                ReceiptItemDisposition.RETURN_REFUND,
                ReceiptItemDisposition.CORRECTION,
                ReceiptItemDisposition.IGNORE,
                -> listOf(
                    purchaseLineCommand(item, draft, origin, scope, purchaseId, itemId = null, inventoryLotId = null),
                )
            }
        }.flatten()
        val reconciledPurchase = purchase.copy(
            reconciliationDifference = purchase.calculatedReconciliationDifference(
                purchaseLines.filterIsInstance<HouseholdCommand.UpsertPurchaseLine>().map { it.line },
            ),
        )
        return listOf(
            HouseholdCommand.UpsertPurchase(
                record = commandRecord(receiptScope, "UpsertPurchase", now, origin, purchaseId),
                purchase = reconciledPurchase,
            ),
        ) + purchaseLines
    }

    private fun purchaseLineCommand(
        item: ReceiptItemDraft,
        draft: ReceiptDraft,
        origin: FoodDraftCommandOrigin,
        scope: String,
        purchaseId: EntityId,
        itemId: EntityId?,
        inventoryLotId: EntityId?,
    ): HouseholdCommand {
        val now = UtcTimestamp(clockMillis())
        val lineId = entityId(scope, item.food.name, "purchase_line")
        val price = item.linePriceCents?.let { Money(it, draft.currencyCode.uppercase()) }
        val line = PurchaseLine(
            metadata = metadata(lineId, now, origin),
            purchaseId = purchaseId,
            itemId = itemId,
            displayName = item.food.name.trim(),
            quantity = item.food.quantity.quantity(),
            lineSubtotal = price,
            finalAmount = price,
            spendCategory = item.food.category.ifBlank { null },
            disposition = item.disposition.toCanonicalDisposition(),
            inventoryLotId = inventoryLotId,
            reviewState = ReviewState.ACCEPTED,
        )
        return HouseholdCommand.UpsertPurchaseLine(
            record = commandRecord(scope, "UpsertPurchaseLine", now, origin, lineId),
            line = line,
        )
    }

    private fun shoppingCommand(
        candidate: FoodCandidate,
        origin: FoodDraftCommandOrigin,
        scope: String,
    ): HouseholdCommand {
        val now = UtcTimestamp(clockMillis())
        val lineId = entityId(scope, candidate.name, "shopping")
        val line = ShoppingLine(
            metadata = metadata(lineId, now, origin),
            shoppingListId = DEFAULT_SHOPPING_LIST_ID,
            displayName = candidate.name.trim(),
            quantity = candidate.quantity.quantity(),
            category = candidate.category.ifBlank { null },
            status = ShoppingLineStatus.NEEDED,
            reason = ShoppingReason.MANUAL,
        )
        return HouseholdCommand.UpsertShoppingLine(
            record = commandRecord(scope, "UpsertShoppingLine", now, origin, lineId),
            line = line,
        )
    }

    private fun recipeCommands(draft: RecipeDraft, origin: FoodDraftCommandOrigin): List<HouseholdCommand> {
        val now = UtcTimestamp(clockMillis())
        val title = draft.titleText.trim()
        val recipeScope = "recipe:$title"
        val recipeId = entityId(recipeScope, title, "root")
        val ingredients = RecipeIngredientParser.parse(draft.ingredientsText)
            .mapIndexed { index, ingredient ->
                val ingredientId = entityId("$recipeScope:ingredient:$index", ingredient.rawText, "ingredient")
                RecipeIngredient(
                    metadata = metadata(ingredientId, now, origin),
                    recipeId = recipeId,
                    originalText = ingredient.rawText,
                    quantity = ingredient.toQuantity(),
                    order = index,
                )
            }
        val steps = draft.stepsText.stepLines().mapIndexed { index, instruction ->
            val stepId = entityId("$recipeScope:step:$index", instruction, "step")
            RecipeStep(
                metadata = metadata(stepId, now, origin),
                recipeId = recipeId,
                order = index,
                instruction = instruction,
            )
        }
        val recipe = Recipe(
            metadata = metadata(recipeId, now, origin),
            name = title,
            description = buildRecipeDescription(draft),
            sourceUrl = draft.imageUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") },
            sourceProvider = draft.imageUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { "image_url" },
            category = draft.tags.splitTags().firstOrNull(),
            tags = draft.tags.splitTags().toSet(),
            yield = draft.servings?.takeIf { it > 0 }?.let {
                Quantity(DecimalAmount.of(it.toString()), QuantityUnit.SERVING)
            } ?: Quantity.unknown(QuantityUnit.SERVING),
            prepMinutes = draft.prepMinutes?.takeIf { it >= 0 },
            status = RecipeStatus.ACTIVE,
            ingredientIds = ingredients.map { it.metadata.id },
            stepIds = steps.map { it.metadata.id },
        )
        return listOf(
            HouseholdCommand.UpsertRecipe(
                record = commandRecord(recipeScope, "UpsertRecipe", now, origin, recipeId),
                recipe = recipe,
            ),
        ) + ingredients.mapIndexed { index, ingredient ->
            HouseholdCommand.UpsertRecipeIngredient(
                record = commandRecord("$recipeScope:ingredient:$index", "UpsertRecipeIngredient", now, origin, ingredient.metadata.id),
                ingredient = ingredient,
            )
        } + steps.mapIndexed { index, step ->
            HouseholdCommand.UpsertRecipeStep(
                record = commandRecord("$recipeScope:step:$index", "UpsertRecipeStep", now, origin, step.metadata.id),
                step = step,
            )
        }
    }

    private fun mealEntryCommands(draft: MealLogDraft, origin: FoodDraftCommandOrigin): List<HouseholdCommand> {
        val now = UtcTimestamp(clockMillis())
        val title = draft.titleText.trim()
        val scope = "meal_log:${mealLogIdentity(draft, now)}"
        val entryId = entityId(scope, title, "entry")
        val nutrition = nutritionSnapshotForMealLog(draft, origin, scope, entryId, now)
        val entry = MealEntry(
            metadata = metadata(entryId, now, origin),
            scheduledAt = draft.loggedDateEpochDay?.toStartOfDayUtc() ?: now,
            slot = draft.mealSlot.label,
            title = title,
            servings = Quantity.unknown(QuantityUnit.SERVING),
            status = MealEntryStatus.EATEN,
            nutritionSnapshotIds = nutrition?.snapshot?.metadata?.id?.let(::listOf).orEmpty(),
            notes = buildMealLogNotes(draft),
        )
        return listOfNotNull(
            HouseholdCommand.UpsertMealEntry(
            record = commandRecord(scope, "UpsertMealEntry", now, origin, entryId),
            entry = entry,
            ),
            nutrition,
        )
    }

    private fun mealPlanCommands(draft: MealPlanDraft, origin: FoodDraftCommandOrigin): List<HouseholdCommand> {
        val now = UtcTimestamp(clockMillis())
        val title = draft.titleText.trim()
        val startEpochDay = draft.startDateEpochDay ?: now.toUtcEpochDay()
        val planScope = "meal_plan:${mealPlanIdentity(draft, startEpochDay)}"
        val planId = entityId(planScope, title, "plan")
        val lastOffset = draft.entries.maxOfOrNull { it.dayOffset.coerceAtLeast(0) } ?: 0
        val plan = MealPlan(
            metadata = metadata(planId, now, origin),
            name = title,
            startsOn = startEpochDay.toCalendarDate(),
            endsOn = (startEpochDay + lastOffset).toCalendarDate(),
            status = MealPlanStatus.ACTIVE,
        )
        val entryCommands = draft.entries.mapIndexed { index, entryDraft ->
            val entryScope = "$planScope:entry:$index"
            val entryId = entityId(entryScope, "${title}:${entryDraft.title}", "entry")
            val scheduledAt = (startEpochDay + entryDraft.dayOffset.coerceAtLeast(0)).toStartOfDayUtc()
            HouseholdCommand.UpsertMealEntry(
                record = commandRecord(entryScope, "UpsertMealEntry", now, origin, entryId),
                entry = MealEntry(
                    metadata = metadata(entryId, now, origin),
                    mealPlanId = planId,
                    scheduledAt = scheduledAt,
                    slot = entryDraft.slot.label,
                    title = entryDraft.title.trim(),
                    servings = Quantity.unknown(QuantityUnit.SERVING),
                    status = MealEntryStatus.PLANNED,
                    notes = buildMealPlanEntryNotes(draft, entryDraft),
                ),
            )
        }
        val sourceEntityIds = listOf(planId) + entryCommands.map { it.entry.metadata.id }
        val groceryCommands = draft.groceryHint.groceryGapCandidates().mapIndexed { index, ingredient ->
            val groceryScope = "$planScope:grocery:$index"
            val lineId = entityId(groceryScope, "${title}:${ingredient.rawText}", "shopping")
            HouseholdCommand.UpsertShoppingLine(
                record = commandRecord(groceryScope, "UpsertShoppingLine", now, origin, lineId),
                line = ShoppingLine(
                    metadata = metadata(lineId, now, origin),
                    shoppingListId = DEFAULT_SHOPPING_LIST_ID,
                    displayName = ingredient.name,
                    quantity = ingredient.toQuantity(),
                    category = "Meal plan",
                    status = ShoppingLineStatus.NEEDED,
                    reason = ShoppingReason.RECIPE_GAP,
                    sourceEntityIds = sourceEntityIds,
                ),
            )
        }
        return listOf(
            HouseholdCommand.UpsertMealPlan(
                record = commandRecord(planScope, "UpsertMealPlan", now, origin, planId),
                plan = plan,
            ),
        ) + entryCommands + groceryCommands
    }

    private fun metadata(id: EntityId, now: UtcTimestamp, origin: FoodDraftCommandOrigin): EntityMetadata =
        EntityMetadata(
            id = id,
            householdId = householdId,
            createdAt = now,
            updatedAt = now,
            revision = 1,
            source = SourceRef(SourceKind.MANUAL, origin.writeSource),
        )

    private fun commandRecord(
        scope: String,
        type: String,
        now: UtcTimestamp,
        origin: FoodDraftCommandOrigin,
        affectedId: EntityId,
    ): CommandRecord =
        CommandRecord(
            commandId = CommandId(uuid("command:${origin.writeSource}:$scope:$type")),
            householdId = householdId,
            type = type,
            source = SourceRef(SourceKind.MANUAL, origin.writeSource),
            requestedAt = now,
            appliedAt = now,
            affectedEntityIds = listOf(affectedId),
        )

    private fun entityId(scope: String, name: String, suffix: String): EntityId =
        EntityId(uuid("${householdId.value}:$scope:${name.trim().lowercase()}:$suffix"))

    private fun receiptAttachmentId(receiptId: Long): EntityId =
        EntityId(uuid("${householdId.value}:receipt_attachment:$receiptId:attachment"))

    private fun String.quantity(): Quantity =
        Quantity(amount = quantityAmount(), unit = quantityUnit())

    private fun String.quantityAmount(): DecimalAmount? {
        val match = Regex("""^\s*(\d+(?:\.\d+)?)""").find(this) ?: return null
        return DecimalAmount.of(match.groupValues[1])
    }

    private fun String.quantityUnit(): QuantityUnit {
        val text = lowercase()
        return when {
            text.isBlank() -> QuantityUnit.UNKNOWN
            "cup" in text -> QuantityUnit.CUP
            "kg" in text || "kilogram" in text -> QuantityUnit.KILOGRAM
            "serving" in text -> QuantityUnit.SERVING
            "g " in "$text " || "gram" in text -> QuantityUnit.GRAM
            "liter" in text -> QuantityUnit.LITER
            "ml" in text || "milliliter" in text -> QuantityUnit.MILLILITER
            "pack" in text || "bag" in text || "box" in text -> QuantityUnit.PACKAGE
            else -> QuantityUnit.EACH
        }
    }

    private fun ParsedIngredient.toQuantity(): Quantity =
        Quantity(
            amount = quantity?.let { value ->
                DecimalAmount.of(if (value % 1.0 == 0.0) value.toInt().toString() else value.toString())
            },
            unit = unit.toIngredientQuantityUnit(),
        )

    private fun String.groceryGapCandidates(): List<ParsedIngredient> =
        split('\n', ',', ';')
            .map { it.trim().trimStart('-', '*', ' ') }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .map { RecipeIngredientParser.parse(it).singleOrNull() ?: ParsedIngredient(it, null, "", it) }

    private fun String.toIngredientQuantityUnit(): QuantityUnit =
        when (lowercase().trim().removeSuffix("s")) {
            "" -> QuantityUnit.UNKNOWN
            "cup" -> QuantityUnit.CUP
            "tbsp", "tablespoon" -> QuantityUnit("tablespoon")
            "tsp", "teaspoon" -> QuantityUnit("teaspoon")
            "g", "gram" -> QuantityUnit.GRAM
            "kg", "kilogram" -> QuantityUnit.KILOGRAM
            "oz", "ounce" -> QuantityUnit("ounce")
            "lb", "pound" -> QuantityUnit("pound")
            "clove" -> QuantityUnit("clove")
            else -> QuantityUnit.UNKNOWN
        }

    private fun buildRecipeDescription(draft: RecipeDraft): String? =
        listOf(
            draft.ingredientsText.trim().takeIf { it.isNotBlank() }?.let { "Ingredients: $it" },
            draft.stepsText.trim().takeIf { it.isNotBlank() }?.let { "Steps: $it" },
        ).filterNotNull().joinToString("\n").ifBlank { null }

    private fun String.stepLines(): List<String> =
        lineSequence()
            .flatMap { line -> line.splitToSequence(';') }
            .map { it.trim().trimStart('-', '*', ' ') }
            .filter { it.isNotBlank() }
            .toList()

    private fun String.splitTags(): List<String> =
        split(',', ';', '#')
            .map { it.trim().lowercase().replace(Regex("\\s+"), "-") }
            .filter { it.isNotBlank() }
            .distinct()

    private fun Long.toUtcDate(): CalendarDate =
        CalendarDate(Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().toString())

    private fun UtcTimestamp.toUtcEpochDay(): Long =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    private fun Long.toCalendarDate(): CalendarDate =
        CalendarDate(java.time.LocalDate.ofEpochDay(this).toString())

    private fun Long.toStartOfDayUtc(): UtcTimestamp =
        UtcTimestamp(java.time.LocalDate.ofEpochDay(this).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())

    private fun buildMealLogNotes(draft: MealLogDraft): String? =
        buildList {
            draft.calories?.let { add("Calories: $it") }
            draft.proteinGrams?.let { add("Protein g: $it") }
            draft.carbsGrams?.let { add("Carbs g: $it") }
            draft.fatGrams?.let { add("Fat g: $it") }
            draft.usedItemsText.trim().takeIf { it.isNotBlank() }?.let { add("Used: $it") }
            draft.source.trim().takeIf { it.isNotBlank() }?.let { add("Source: $it") }
        }.joinToString("\n").ifBlank { null }

    private fun nutritionSnapshotForMealLog(
        draft: MealLogDraft,
        origin: FoodDraftCommandOrigin,
        scope: String,
        entryId: EntityId,
        now: UtcTimestamp,
    ): HouseholdCommand.UpsertNutritionSnapshot? {
        if (draft.calories == null && draft.proteinGrams == null && draft.carbsGrams == null && draft.fatGrams == null) {
            return null
        }
        val snapshotId = entityId(scope, draft.titleText, "nutrition")
        val snapshot = NutritionSnapshot(
            metadata = metadata(snapshotId, now, origin),
            subject = EntityReference(HouseholdEntityType.MEAL_ENTRY, entryId),
            basis = Quantity(DecimalAmount.of("1"), QuantityUnit.SERVING),
            values = NutritionValues(
                energyKcal = draft.calories?.takeIf { it >= 0 }?.let { DecimalAmount.of(it.toString()) },
                proteinGrams = draft.proteinGrams?.takeIf { it >= 0.0 }?.let { DecimalAmount.of(it.toString()) },
                carbohydrateGrams = draft.carbsGrams?.takeIf { it >= 0.0 }?.let { DecimalAmount.of(it.toString()) },
                fatGrams = draft.fatGrams?.takeIf { it >= 0.0 }?.let { DecimalAmount.of(it.toString()) },
            ),
            provider = draft.source.trim().takeIf { it.isNotBlank() },
            capturedAt = now,
            warnings = listOf("User-reviewed meal-log estimate"),
        )
        return HouseholdCommand.UpsertNutritionSnapshot(
            record = commandRecord(scope, "UpsertNutritionSnapshot", now, origin, snapshotId),
            snapshot = snapshot,
        )
    }

    private fun nutritionSnapshotForInventory(
        candidate: FoodCandidate,
        origin: FoodDraftCommandOrigin,
        scope: String,
        itemId: EntityId,
        now: UtcTimestamp,
    ): HouseholdCommand.UpsertNutritionSnapshot? {
        if (candidate.calories == null &&
            candidate.proteinGrams == null &&
            candidate.carbsGrams == null &&
            candidate.fatGrams == null
        ) {
            return null
        }
        val snapshotId = entityId(scope, candidate.name, "nutrition")
        val servingBasis = candidate.servingText.trim()
        val snapshot = NutritionSnapshot(
            metadata = metadata(snapshotId, now, origin),
            subject = EntityReference(HouseholdEntityType.ITEM, itemId),
            basis = if (servingBasis.isBlank()) {
                Quantity(DecimalAmount.of("1"), QuantityUnit.SERVING)
            } else {
                servingBasis.quantity()
            },
            values = NutritionValues(
                energyKcal = candidate.calories?.takeIf { it >= 0 }?.let { DecimalAmount.of(it.toString()) },
                proteinGrams = candidate.proteinGrams?.takeIf { it >= 0.0 }?.let { DecimalAmount.of(it.toString()) },
                carbohydrateGrams = candidate.carbsGrams?.takeIf { it >= 0.0 }?.let { DecimalAmount.of(it.toString()) },
                fatGrams = candidate.fatGrams?.takeIf { it >= 0.0 }?.let { DecimalAmount.of(it.toString()) },
            ),
            provider = candidate.nutritionSource.trim().ifBlank { origin.writeSource },
            capturedAt = now,
            warnings = candidate.warnings.ifEmpty { listOf("User-reviewed inventory nutrition estimate") },
        )
        return HouseholdCommand.UpsertNutritionSnapshot(
            record = commandRecord(scope, "UpsertNutritionSnapshot", now, origin, snapshotId),
            snapshot = snapshot,
        )
    }

    private fun buildMealPlanEntryNotes(draft: MealPlanDraft, entry: MealPlanEntryDraft): String? =
        buildList {
            entry.calorieTarget?.let { add("Calorie target: $it") }
            draft.groceryHint.trim().takeIf { it.isNotBlank() }?.let { add("Groceries: $it") }
            draft.daysText.trim().takeIf { it.isNotBlank() }?.let { add("Plan text: $it") }
        }.joinToString("\n").ifBlank { null }

    private fun mealLogIdentity(draft: MealLogDraft, now: UtcTimestamp): String =
        listOf(
            draft.loggedDateEpochDay?.let { "date:$it" } ?: "created_at:${now.epochMillis}",
            "slot:${draft.mealSlot.label.trim().lowercase()}",
            "title:${draft.titleText.trim().lowercase()}",
            draft.calories?.let { "calories:$it" },
            draft.proteinGrams?.let { "protein:$it" },
            draft.carbsGrams?.let { "carbs:$it" },
            draft.fatGrams?.let { "fat:$it" },
            draft.usedItemsText.trim().lowercase().takeIf { it.isNotBlank() }?.let { "used:$it" },
            draft.source.trim().lowercase().takeIf { it.isNotBlank() }?.let { "source:$it" },
        ).filterNotNull().joinToString("|")

    private fun mealPlanIdentity(draft: MealPlanDraft, startEpochDay: Long): String =
        listOf(
            "start:$startEpochDay",
            "title:${draft.titleText.trim().lowercase()}",
            draft.daysText.trim().lowercase().takeIf { it.isNotBlank() }?.let { "days:$it" },
            draft.groceryHint.trim().lowercase().takeIf { it.isNotBlank() }?.let { "groceries:$it" },
            draft.entries.joinToString("|", prefix = "entries:") { entry ->
                listOf(
                    entry.dayOffset.toString(),
                    entry.slot.label.trim().lowercase(),
                    entry.title.trim().lowercase(),
                    entry.calorieTarget?.toString().orEmpty(),
                ).joinToString(":")
            },
        ).joinToString("|")

    private fun receiptIdentity(draft: ReceiptDraft): String =
        listOf(
            draft.receiptId?.let { "receipt_id:$it" },
            draft.purchasedAtMillis?.let { "purchased_at:$it" },
            draft.merchant.trim().lowercase().takeIf { it.isNotBlank() }?.let { "merchant:$it" },
            draft.storeLocation.trim().lowercase().takeIf { it.isNotBlank() }?.let { "location:$it" },
            draft.currencyCode.trim().uppercase().takeIf { it.isNotBlank() }?.let { "currency:$it" },
            draft.subtotalCents?.let { "subtotal:$it" },
            draft.taxCents?.let { "tax:$it" },
            draft.discountCents?.let { "discount:$it" },
            draft.totalCents?.let { "total:$it" },
            draft.items.joinToString("|") {
                "${it.food.name.trim().lowercase()}:${it.food.quantity.trim().lowercase()}:${it.linePriceCents}:${it.disposition.name}"
            },
        ).filterNotNull().joinToString("|").ifBlank { "empty" }

    private fun buildPurchasePaymentNote(draft: ReceiptDraft): String? =
        buildList {
            draft.merchant.trim().takeIf { it.isNotBlank() }?.let { add("Merchant: $it") }
            draft.storeLocation.trim().takeIf { it.isNotBlank() }?.let { add("Location: $it") }
            draft.sourceLabel.trim().takeIf { it.isNotBlank() }?.let { add("Source: $it") }
        }.joinToString("\n").ifBlank { null }

    private fun ReceiptItemDisposition.toCanonicalDisposition(): PurchaseLineDisposition =
        when (this) {
            ReceiptItemDisposition.INVENTORY,
            ReceiptItemDisposition.HOUSEHOLD,
            -> PurchaseLineDisposition.INVENTORY
            ReceiptItemDisposition.RETURN_REFUND,
            ReceiptItemDisposition.CORRECTION,
            -> PurchaseLineDisposition.SERVICE
            ReceiptItemDisposition.IGNORE -> PurchaseLineDisposition.IGNORED
        }

    private fun uuid(input: String): String =
        UUID.nameUUIDFromBytes(input.toByteArray(StandardCharsets.UTF_8)).toString()

    companion object {
        val DEFAULT_HOUSEHOLD_ID: HouseholdId =
            HouseholdId("00000000-0000-0000-0000-000000000105")

        val DEFAULT_SHOPPING_LIST_ID: EntityId =
            EntityId("00000000-0000-0000-0000-000000000501")
    }
}
