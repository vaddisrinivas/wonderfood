package com.wonderfood.app.sync

import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.model.household.CalendarDate
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.MealEntry
import com.wonderfood.core.model.household.MealEntryStatus
import com.wonderfood.core.model.household.Merchant
import com.wonderfood.core.model.household.Money
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
import com.wonderfood.core.model.household.ShoppingList
import com.wonderfood.core.model.household.ShoppingListStatus
import com.wonderfood.core.model.household.ShoppingReason
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency
import java.util.Locale
import java.util.UUID

internal object GoogleSheetsV4InboundWorkspaceImporter {
    fun importRows(
        rows: List<GoogleSheetsWorkspaceRow>,
        householdId: HouseholdId,
        now: UtcTimestamp,
        defaultCurrency: String,
        providerKey: String = "google_sheets",
        baseSnapshot: HouseholdSnapshot? = null,
    ): V4InboundWorkspaceImportResult {
        val context = ImportContext(rows, householdId, now, defaultCurrency.uppercase(Locale.US), providerKey, baseSnapshot)
        return context.import()
    }
}

data class V4InboundWorkspaceImportResult(
    val commands: List<HouseholdCommand>,
    val diagnostics: List<V4InboundNeedsReviewDiagnostic>,
)

data class V4InboundNeedsReviewDiagnostic(
    val surface: WorkspaceGraphSurface,
    val identifier: String,
    val field: String,
    val code: String,
    val message: String,
)

private class ImportContext(
    rows: List<GoogleSheetsWorkspaceRow>,
    private val householdId: HouseholdId,
    private val now: UtcTimestamp,
    private val defaultCurrency: String,
    private val providerKey: String,
    private val baseSnapshot: HouseholdSnapshot?,
) {
    private val defaultSource = SourceRef(SourceKind.DATA_HOME_HUMAN, "${providerKey}_v4_inbound")
    private val diagnostics = mutableListOf<V4InboundNeedsReviewDiagnostic>()
    private val rowsBySurface: Map<WorkspaceGraphSurface, List<GoogleSheetsWorkspaceRow>> =
        rows.mapIndexedNotNull { index, row ->
            surfaceFor(row.tab)?.let { surface ->
                val normalized = if (row.identifier.isBlank()) {
                    val identity = row.remoteIdentity ?: "${row.tab}:row:$index:${row.values.toSortedMap()}"
                    row.copy(identifier = stableUuid("${householdId.value}:$providerKey:v4:new:$identity"))
                } else {
                    row
                }
                surface to normalized
            }
        }.groupBy({ it.first }, { it.second })
    private val surfaceByIdentifier: Map<String, Set<WorkspaceGraphSurface>> =
        rowsBySurface.flatMap { (surface, surfaceRows) -> surfaceRows.map { it.identifier.trim() to surface } }
            .filter { it.first.isNotBlank() }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
    private val rowsByTitle: Map<WorkspaceGraphSurface, Map<String, List<GoogleSheetsWorkspaceRow>>> =
        WorkspaceGraphSurface.entries.associateWith { surface ->
            rowsBySurface[surface].orEmpty().groupBy { it.titleFor(surface).relationKey() }.filterKeys { it.isNotBlank() }
        }
    private val baseProjection = baseSnapshot?.let(WorkspaceGraphProjector::project)
    private val baseMetadataById: Map<EntityId, EntityMetadata> = baseSnapshot?.let { snapshot ->
        buildList {
            addAll(snapshot.items.map { it.metadata })
            addAll(snapshot.inventoryLots.map { it.metadata })
            addAll(snapshot.recipes.map { it.metadata })
            addAll(snapshot.recipeIngredients.map { it.metadata })
            addAll(snapshot.mealEntries.map { it.metadata })
            addAll(snapshot.shoppingLists.map { it.metadata })
            addAll(snapshot.shoppingLines.map { it.metadata })
            addAll(snapshot.merchants.map { it.metadata })
            addAll(snapshot.purchases.map { it.metadata })
            addAll(snapshot.purchaseLines.map { it.metadata })
        }.associateBy { it.id }
    }.orEmpty()

    fun import(): V4InboundWorkspaceImportResult {
        rowsBySurface.forEach { (surface, rows) -> rows.forEach { detectConcurrentHighRiskEdit(surface, it) } }
        val commands = buildList {
            val shoppingRows = rowsBySurface[WorkspaceGraphSurface.SHOPPING].orEmpty()
            if (shoppingRows.isNotEmpty()) add(defaultShoppingListCommand())
            rowsBySurface[WorkspaceGraphSurface.KITCHEN].orEmpty().forEach { addAll(kitchenCommands(it)) }
            rowsBySurface[WorkspaceGraphSurface.STOCK_LOTS].orEmpty().forEach { addAll(stockLotCommands(it)) }
            rowsBySurface[WorkspaceGraphSurface.RECIPES].orEmpty().forEach { addAll(recipeCommands(it)) }
            rowsBySurface[WorkspaceGraphSurface.INGREDIENTS].orEmpty().forEach { addAll(ingredientCommands(it)) }
            rowsBySurface[WorkspaceGraphSurface.MEALS].orEmpty().forEach { addAll(mealCommands(it)) }
            shoppingRows.forEach { addAll(shoppingCommands(it)) }
            rowsBySurface[WorkspaceGraphSurface.SPENDING].orEmpty().forEach { addAll(spendingCommands(it)) }
            rowsBySurface[WorkspaceGraphSurface.PURCHASE_LINES].orEmpty().forEach { addAll(purchaseLineCommands(it)) }
        }
        return V4InboundWorkspaceImportResult(commands = commands, diagnostics = diagnostics)
    }

    private fun kitchenCommands(row: GoogleSheetsWorkspaceRow): List<HouseholdCommand> {
        val surface = WorkspaceGraphSurface.KITCHEN
        val itemName = row.requiredText(surface, "Item") ?: return emptyList()
        val kind = row.enumValue(surface, "Kind", ItemKind.entries, required = true) { it.name.humanize() } ?: return emptyList()
        val quantity = row.quantity(surface, "On hand", "Unit") ?: return emptyList()
        val lowAt = row.quantity(surface, "Low at", "Unit", amountRequired = false)
        val itemId = entityId(surface, row.identifier)
        if (row.hasDiagnostics(surface)) return emptyList()

        val archivedAt = row.boolean("Archived").takeIf { it }?.let { now }
        val item = Item(
            metadata = metadata(itemId, row, archivedAt),
            name = itemName,
            kind = kind,
            category = row.text("Category"),
            defaultUnit = quantity.unit,
            preferredStore = row.text("Preferred store"),
            refillThreshold = lowAt,
            notes = row.text("Notes"),
        )
        val itemCommand = HouseholdCommand.UpsertItem(commandRecord("UpsertItem", itemId, row), item)
        if (hasLinkedStockLot(row, itemId, itemName)) return listOf(itemCommand)
        val lotId = entityId(WorkspaceGraphSurface.STOCK_LOTS, "${row.identifier}:stock_lot")
        val lot = InventoryLot(
            metadata = metadata(lotId, row, archivedAt),
            itemId = itemId,
            quantity = quantity,
            expiresOn = row.date(surface, "Best before"),
            status = if (archivedAt == null) InventoryLotStatus.AVAILABLE else InventoryLotStatus.ARCHIVED,
        )
        return listOf(itemCommand, HouseholdCommand.UpsertInventoryLot(commandRecord("UpsertInventoryLot", lotId, row), lot))
    }

    private fun stockLotCommands(row: GoogleSheetsWorkspaceRow): List<HouseholdCommand> {
        val surface = WorkspaceGraphSurface.STOCK_LOTS
        row.requiredText(surface, "Lot") ?: return emptyList()
        val itemId = row.relation(surface, "Kitchen item", WorkspaceGraphSurface.KITCHEN, required = true).singleOrNull()
        val quantity = row.quantity(surface, "Quantity", "Unit") ?: return emptyList()
        val lotId = entityId(surface, row.identifier)
        if (row.hasDiagnostics(surface) || itemId == null) return emptyList()
        val archivedAt = row.boolean("Archived").takeIf { it }?.let { now }
        val opened = row.boolean("Opened")
        val lot = InventoryLot(
            metadata = metadata(lotId, row, archivedAt),
            itemId = itemId,
            quantity = quantity,
            expiresOn = row.date(surface, "Best before"),
            openedAt = opened.takeIf { it }?.let { now },
            status = when {
                archivedAt != null -> InventoryLotStatus.ARCHIVED
                opened -> InventoryLotStatus.OPENED
                else -> InventoryLotStatus.AVAILABLE
            },
        )
        return listOf(HouseholdCommand.UpsertInventoryLot(commandRecord("UpsertInventoryLot", lotId, row), lot))
    }

    private fun hasLinkedStockLot(row: GoogleSheetsWorkspaceRow, itemId: EntityId, itemName: String): Boolean =
        rowsBySurface[WorkspaceGraphSurface.STOCK_LOTS].orEmpty().any { lotRow ->
            val reference = lotRow.firstPresent("Kitchen item", "_wf_kitchen_id").orEmpty()
            reference.split(Regex("[,;\\n]")).map(String::trim).any { token ->
                token == row.identifier || token == itemId.value || token.equals(itemName, ignoreCase = true)
            }
        }

    private fun recipeCommands(row: GoogleSheetsWorkspaceRow): List<HouseholdCommand> {
        val surface = WorkspaceGraphSurface.RECIPES
        val recipeName = row.requiredText(surface, "Recipe") ?: return emptyList()
        val yield = row.quantity(surface, "Servings", unitOverride = QuantityUnit.SERVING, amountRequired = false)
        val recipeId = entityId(surface, row.identifier)
        if (row.hasDiagnostics(surface)) return emptyList()
        val recipe = Recipe(
            metadata = metadata(recipeId, row, row.boolean("Archived").takeIf { it }?.let { now }),
            name = recipeName,
            sourceUrl = row.text("Source"),
            cuisine = row.text("Cuisine"),
            tags = row.textList("Tags").toSet(),
            yield = yield ?: Quantity.unknown(QuantityUnit.SERVING),
            prepMinutes = row.int(surface, "Prep minutes"),
            cookMinutes = row.int(surface, "Cook minutes"),
            description = row.text("Instructions"),
            status = if (row.boolean("Archived")) RecipeStatus.ARCHIVED else RecipeStatus.ACTIVE,
        )
        return listOf(HouseholdCommand.UpsertRecipe(commandRecord("UpsertRecipe", recipeId, row), recipe))
    }

    private fun ingredientCommands(row: GoogleSheetsWorkspaceRow): List<HouseholdCommand> {
        val surface = WorkspaceGraphSurface.INGREDIENTS
        val name = row.requiredText(surface, "Ingredient") ?: return emptyList()
        val recipeId = row.relation(surface, "Recipe", WorkspaceGraphSurface.RECIPES, required = true).singleOrNull()
        val itemId = row.relation(surface, "Kitchen item", WorkspaceGraphSurface.KITCHEN, required = false).singleOrNull()
        val quantity = row.quantity(surface, "Amount", "Unit") ?: return emptyList()
        itemId?.let { row.requireCompatibleWithItem(surface, "Kitchen item", quantity, it) }
        val ingredientId = entityId(surface, row.identifier)
        if (row.hasDiagnostics(surface) || recipeId == null) return emptyList()
        val ingredient = RecipeIngredient(
            metadata = metadata(ingredientId, row, row.boolean("Archived").takeIf { it }?.let { now }),
            recipeId = recipeId,
            itemId = itemId,
            originalText = name,
            quantity = quantity,
            preparation = row.text("Preparation"),
            optional = row.boolean("Optional"),
            order = rowsBySurface[surface].orEmpty().indexOf(row).coerceAtLeast(0),
        )
        return listOf(HouseholdCommand.UpsertRecipeIngredient(commandRecord("UpsertRecipeIngredient", ingredientId, row), ingredient))
    }

    private fun mealCommands(row: GoogleSheetsWorkspaceRow): List<HouseholdCommand> {
        val surface = WorkspaceGraphSurface.MEALS
        val title = row.requiredText(surface, "Meal") ?: return emptyList()
        val scheduledAt = row.timestamp(surface, "Date", required = true) ?: return emptyList()
        val recipeId = row.relation(surface, "Recipe", WorkspaceGraphSurface.RECIPES, required = false).singleOrNull()
        val servings = row.quantity(surface, "Servings", unitOverride = QuantityUnit.SERVING, amountRequired = false)
        val status = row.enumValue(surface, "Status", MealEntryStatus.entries, required = false) { it.name.humanize() }
            ?: MealEntryStatus.PLANNED
        val mealId = entityId(surface, row.identifier)
        if (row.hasDiagnostics(surface)) return emptyList()
        val meal = MealEntry(
            metadata = metadata(mealId, row, row.boolean("Archived").takeIf { it }?.let { now }),
            scheduledAt = scheduledAt,
            slot = row.text("Meal slot") ?: "Meal",
            recipeId = recipeId,
            title = title,
            servings = servings ?: Quantity.unknown(QuantityUnit.SERVING),
            status = if (row.boolean("Archived")) MealEntryStatus.ARCHIVED else status,
            leftoverIntent = row.text("Leftovers"),
            notes = row.text("Notes"),
        )
        return listOf(HouseholdCommand.UpsertMealEntry(commandRecord("UpsertMealEntry", mealId, row), meal))
    }

    private fun shoppingCommands(row: GoogleSheetsWorkspaceRow): List<HouseholdCommand> {
        val surface = WorkspaceGraphSurface.SHOPPING
        val name = row.requiredText(surface, "Item") ?: return emptyList()
        val itemId = row.relation(surface, "Kitchen item", WorkspaceGraphSurface.KITCHEN, required = false).singleOrNull()
        val recipeIds = row.relation(surface, "Needed for recipes", WorkspaceGraphSurface.RECIPES, required = false)
        val mealIds = row.relation(surface, "Needed for meals", WorkspaceGraphSurface.MEALS, required = false)
        val quantity = row.quantity(surface, "Amount", "Unit") ?: return emptyList()
        itemId?.let { row.requireCompatibleWithItem(surface, "Kitchen item", quantity, it) }
        val status = row.enumValue(surface, "Status", ShoppingLineStatus.entries, required = false) { it.name.humanize() }
            ?: ShoppingLineStatus.NEEDED
        val reason = row.enumValue(surface, "Reason", ShoppingReason.entries, required = false) { it.name.humanize() }
            ?: ShoppingReason.MANUAL
        val lineId = entityId(surface, row.identifier)
        if (row.hasDiagnostics(surface)) return emptyList()
        val line = ShoppingLine(
            metadata = metadata(lineId, row, row.boolean("Archived").takeIf { it }?.let { now }),
            shoppingListId = DEFAULT_SHOPPING_LIST_ID,
            itemId = itemId,
            displayName = name,
            quantity = quantity,
            category = row.text("Category"),
            preferredStore = row.text("Store"),
            status = if (row.boolean("Archived")) ShoppingLineStatus.ARCHIVED else status,
            reason = reason,
            sourceEntityIds = recipeIds + mealIds,
        )
        return listOf(HouseholdCommand.UpsertShoppingLine(commandRecord("UpsertShoppingLine", lineId, row), line))
    }

    private fun spendingCommands(row: GoogleSheetsWorkspaceRow): List<HouseholdCommand> {
        val surface = WorkspaceGraphSurface.SPENDING
        val title = row.requiredText(surface, "Purchase") ?: return emptyList()
        val occurredAt = row.timestamp(surface, "Date", required = true) ?: return emptyList()
        val currency = row.currency(surface, required = true) ?: return emptyList()
        val merchantName = row.text("Merchant")
        val merchantId = merchantName?.let { entityId(WorkspaceGraphSurface.SPENDING, "${row.identifier}:merchant") }
        val purchaseId = entityId(surface, row.identifier)
        val status = row.enumValue(surface, "Status", PurchaseStatus.entries, required = false) { it.name.humanize() }
            ?: PurchaseStatus.REVIEWED
        if (row.hasDiagnostics(surface)) return emptyList()
        val merchantCommand = merchantName?.let {
            val merchant = Merchant(metadata = metadata(requireNotNull(merchantId), row), name = it)
            HouseholdCommand.UpsertMerchant(commandRecord("UpsertMerchant", merchant.metadata.id, row), merchant)
        }
        val purchase = Purchase(
            metadata = metadata(purchaseId, row, row.boolean("Archived").takeIf { it }?.let { now }),
            merchantId = merchantId,
            occurredAt = occurredAt,
            tax = row.money(surface, "Tax", currency),
            discount = row.money(surface, "Discount", currency),
            total = row.money(surface, "Entered total", currency),
            paymentNote = row.text("Notes") ?: title,
            status = if (row.boolean("Archived")) PurchaseStatus.ARCHIVED else status,
        )
        return listOfNotNull(
            merchantCommand,
            HouseholdCommand.UpsertPurchase(commandRecord("UpsertPurchase", purchaseId, row), purchase),
        )
    }

    private fun purchaseLineCommands(row: GoogleSheetsWorkspaceRow): List<HouseholdCommand> {
        val surface = WorkspaceGraphSurface.PURCHASE_LINES
        val name = row.requiredText(surface, "Line") ?: return emptyList()
        val purchaseId = row.relation(surface, "Purchase", WorkspaceGraphSurface.SPENDING, required = true).singleOrNull()
        val itemId = row.relation(surface, "Kitchen item", WorkspaceGraphSurface.KITCHEN, required = false).singleOrNull()
        val shoppingId = row.relation(surface, "Shopping line", WorkspaceGraphSurface.SHOPPING, required = false).singleOrNull()
        val quantity = row.quantity(surface, "Quantity", "Unit") ?: return emptyList()
        itemId?.let { row.requireCompatibleWithItem(surface, "Kitchen item", quantity, it) }
        val currency = row.currency(surface, required = false) ?: purchaseId?.let { purchaseCurrency(it) } ?: defaultCurrency
        val unitPrice = row.money(surface, "Unit price", currency)
        val subtotal = row.money(surface, "Subtotal", currency)
            ?: unitPrice?.let { price -> quantity.amount?.toBigDecimal()?.let { price.multiply(it, currency) } }
        val discount = row.money(surface, "Discount", currency)
        val tax = row.money(surface, "Tax", currency)
        val disposition = row.enumValue(surface, "Disposition", PurchaseLineDisposition.entries, required = false) { it.name.humanize() }
            ?: PurchaseLineDisposition.INVENTORY
        val lineId = entityId(surface, row.identifier)
        if (row.hasDiagnostics(surface) || purchaseId == null) return emptyList()
        val finalAmount = subtotal.plusOrNull(tax)?.minusOrNull(discount) ?: subtotal
        val line = PurchaseLine(
            metadata = metadata(lineId, row, row.boolean("Archived").takeIf { it }?.let { now }),
            purchaseId = purchaseId,
            itemId = itemId,
            shoppingLineId = shoppingId,
            displayName = name,
            quantity = quantity,
            unitPrice = unitPrice,
            lineSubtotal = subtotal,
            discount = discount,
            taxAllocation = tax,
            finalAmount = finalAmount,
            spendCategory = row.text("Category"),
            disposition = if (row.boolean("Archived")) PurchaseLineDisposition.IGNORED else disposition,
            reviewState = ReviewState.ACCEPTED,
        )
        return listOf(HouseholdCommand.UpsertPurchaseLine(commandRecord("UpsertPurchaseLine", lineId, row), line))
    }

    private fun defaultShoppingListCommand(): HouseholdCommand.UpsertShoppingList =
        HouseholdCommand.UpsertShoppingList(
            record = commandRecord("UpsertShoppingList", DEFAULT_SHOPPING_LIST_ID, GoogleSheetsWorkspaceRow("Shopping", "default-shopping-list", emptyMap())),
            list = ShoppingList(
                metadata = metadata(DEFAULT_SHOPPING_LIST_ID),
                name = "WonderFood cart",
                status = ShoppingListStatus.ACTIVE,
            ),
        )

    private fun GoogleSheetsWorkspaceRow.relation(
        sourceSurface: WorkspaceGraphSurface,
        field: String,
        targetSurface: WorkspaceGraphSurface,
        required: Boolean,
    ): List<EntityId> {
        val raw = firstPresent(
            field,
            "_wf_${targetSurface.key}_id",
            "_wf_${targetSurface.key}_ids",
            "_wf_${field.fieldKey()}_id",
            "_wf_${field.fieldKey()}_ids",
        )
        if (raw.isNullOrBlank()) {
            if (required) diagnostic(sourceSurface, this, field, "missing_required_relation", "$field must link to ${targetSurface.label}.")
            return emptyList()
        }
        val ids = mutableListOf<EntityId>()
        raw.split(Regex("[,;\\n]")).map { it.trim() }.filter { it.isNotBlank() }.forEach { token ->
            val directSurface = surfaceByIdentifier[token]
            when {
                directSurface?.contains(targetSurface) == true -> ids += entityId(targetSurface, token)
                directSurface != null && targetSurface !in directSurface -> {
                    diagnostic(sourceSurface, this, field, "incompatible_relation", "$field points to ${directSurface.joinToString { it.label }} instead of ${targetSurface.label}.")
                }
                else -> {
                    val matches = rowsByTitle[targetSurface].orEmpty()[token.relationKey()].orEmpty()
                    when (matches.size) {
                        1 -> ids += entityId(targetSurface, matches.single().identifier)
                        0 -> diagnostic(sourceSurface, this, field, "unresolved_relation", "$field could not resolve '$token' in ${targetSurface.label}.")
                        else -> diagnostic(sourceSurface, this, field, "ambiguous_relation", "$field label '$token' matches multiple ${targetSurface.label} rows.")
                    }
                }
            }
        }
        return ids.distinct()
    }

    private fun GoogleSheetsWorkspaceRow.requireCompatibleWithItem(
        sourceSurface: WorkspaceGraphSurface,
        field: String,
        quantity: Quantity,
        itemId: EntityId,
    ) {
        val itemRow = rowsBySurface[WorkspaceGraphSurface.KITCHEN].orEmpty().firstOrNull {
            entityId(WorkspaceGraphSurface.KITCHEN, it.identifier) == itemId
        } ?: return
        val itemUnit = itemRow.unit(sourceSurface, "Unit") ?: return
        if (!quantity.unit.compatibleWith(itemUnit)) {
            diagnostic(sourceSurface, this, field, "incompatible_quantity_unit", "$field uses ${quantity.unit.code} but linked Kitchen item uses ${itemUnit.code}.")
        }
    }

    private fun GoogleSheetsWorkspaceRow.quantity(
        surface: WorkspaceGraphSurface,
        amountField: String,
        unitField: String,
        amountRequired: Boolean = true,
    ): Quantity? {
        val amountText = text(amountField)
        val unit = unit(surface, unitField)
        val amount = amountText?.let {
            it.toBigDecimalOrNull()?.takeIf { value -> value >= BigDecimal.ZERO }?.let { value ->
                DecimalAmount.of(value.stripTrailingZeros().toPlainString())
            } ?: run {
                diagnostic(surface, this, amountField, "invalid_decimal", "$amountField must be a non-negative decimal.")
                null
            }
        }
        if (amountText.isNullOrBlank() && amountRequired) {
            diagnostic(surface, this, amountField, "missing_amount", "$amountField is required.")
        }
        if (amount != null && unit == QuantityUnit.UNKNOWN) {
            diagnostic(surface, this, unitField, "unknown_unit_with_amount", "$unitField must be a supported unit when amount is present.")
        }
        val resolvedUnit = unit ?: return null
        return if (hasDiagnostics(surface)) null else Quantity(amount, resolvedUnit)
    }

    private fun GoogleSheetsWorkspaceRow.quantity(
        surface: WorkspaceGraphSurface,
        amountField: String,
        unitOverride: QuantityUnit,
        amountRequired: Boolean = true,
    ): Quantity? {
        val amountText = text(amountField)
        val amount = amountText?.let {
            it.toBigDecimalOrNull()?.takeIf { value -> value >= BigDecimal.ZERO }?.let { value ->
                DecimalAmount.of(value.stripTrailingZeros().toPlainString())
            } ?: run {
                diagnostic(surface, this, amountField, "invalid_decimal", "$amountField must be a non-negative decimal.")
                null
            }
        }
        if (amountText.isNullOrBlank() && amountRequired) {
            diagnostic(surface, this, amountField, "missing_amount", "$amountField is required.")
        }
        return if (hasDiagnostics(surface)) null else Quantity(amount, unitOverride)
    }

    private fun GoogleSheetsWorkspaceRow.unit(surface: WorkspaceGraphSurface, field: String): QuantityUnit? {
        val raw = text(field)?.lowercase(Locale.US)?.replace(' ', '_') ?: return QuantityUnit.UNKNOWN
        if (raw == "unknown") return QuantityUnit.UNKNOWN
        val unit = WorkspaceGraphContract.supportedUnits.firstOrNull { it == raw }
        if (unit == null) {
            diagnostic(surface, this, field, "unknown_unit", "$field '$raw' is not a supported V4 unit.")
            return null
        }
        return QuantityUnit(unit)
    }

    private fun GoogleSheetsWorkspaceRow.money(surface: WorkspaceGraphSurface, field: String, currency: String): Money? {
        val raw = text(field) ?: return null
        val cleaned = raw.trim().removePrefix("$").trim()
        val amount = cleaned.toBigDecimalOrNull() ?: run {
            diagnostic(surface, this, field, "invalid_money", "$field must be a decimal major-unit amount.")
            return null
        }
        return amount.toMoney(surface, this, field, currency)
    }

    private fun BigDecimal.toMoney(
        surface: WorkspaceGraphSurface,
        row: GoogleSheetsWorkspaceRow,
        field: String,
        currency: String,
    ): Money? {
        val fractionDigits = runCatching { Currency.getInstance(currency).defaultFractionDigits }
            .getOrElse {
                diagnostic(surface, row, field, "invalid_currency", "$currency is not an ISO-4217 currency.")
                return null
            }
            .coerceAtLeast(0)
        return try {
            Money(setScale(fractionDigits, RoundingMode.UNNECESSARY).movePointRight(fractionDigits).longValueExact(), currency)
        } catch (_: ArithmeticException) {
            diagnostic(surface, row, field, "invalid_money_precision", "$field has too many decimals for $currency.")
            null
        }
    }

    private fun GoogleSheetsWorkspaceRow.currency(surface: WorkspaceGraphSurface, required: Boolean): String? {
        val currency = text("Currency")?.uppercase(Locale.US)
        if (currency.isNullOrBlank()) {
            if (required) diagnostic(surface, this, "Currency", "missing_currency", "Currency is required.")
            return null
        }
        if (!Regex("[A-Z]{3}").matches(currency) || runCatching { Currency.getInstance(currency) }.isFailure) {
            diagnostic(surface, this, "Currency", "invalid_currency", "Currency must be ISO-4217.")
            return null
        }
        return currency
    }

    private fun purchaseCurrency(purchaseId: EntityId): String? {
        val row = rowsBySurface[WorkspaceGraphSurface.SPENDING].orEmpty().firstOrNull {
            entityId(WorkspaceGraphSurface.SPENDING, it.identifier) == purchaseId
        }
        return row?.text("Currency")?.uppercase(Locale.US)
    }

    private fun GoogleSheetsWorkspaceRow.timestamp(surface: WorkspaceGraphSurface, field: String, required: Boolean): UtcTimestamp? {
        val raw = text(field)
        if (raw.isNullOrBlank()) {
            if (required) diagnostic(surface, this, field, "missing_datetime", "$field is required.")
            return null
        }
        val millis = runCatching { Instant.parse(raw).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .recoverCatching { LocalDate.parse(raw).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrElse {
                diagnostic(surface, this, field, "invalid_datetime", "$field must be ISO date or timestamp.")
                return null
            }
        return UtcTimestamp(millis)
    }

    private fun GoogleSheetsWorkspaceRow.date(surface: WorkspaceGraphSurface, field: String): CalendarDate? {
        val raw = text(field) ?: return null
        return runCatching { CalendarDate(LocalDate.parse(raw).toString()) }.getOrElse {
            diagnostic(surface, this, field, "invalid_date", "$field must use YYYY-MM-DD.")
            null
        }
    }

    private fun <T : Enum<T>> GoogleSheetsWorkspaceRow.enumValue(
        surface: WorkspaceGraphSurface,
        field: String,
        entries: List<T>,
        required: Boolean,
        label: (T) -> String,
    ): T? {
        val raw = text(field)
        if (raw.isNullOrBlank()) {
            if (required) diagnostic(surface, this, field, "missing_enum", "$field is required.")
            return null
        }
        val key = raw.relationKey()
        return entries.firstOrNull { it.name.relationKey() == key || label(it).relationKey() == key } ?: run {
            diagnostic(surface, this, field, "invalid_enum", "$field '$raw' is not supported.")
            null
        }
    }

    private fun GoogleSheetsWorkspaceRow.int(surface: WorkspaceGraphSurface, field: String): Int? =
        text(field)?.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }?.toInt() ?: text(field)?.let {
            diagnostic(surface, this, field, "invalid_integer", "$field must be a non-negative number.")
            null
        }

    private fun GoogleSheetsWorkspaceRow.requiredText(surface: WorkspaceGraphSurface, field: String): String? =
        text(field) ?: run {
            diagnostic(surface, this, field, "missing_text", "$field is required.")
            null
        }

    private fun GoogleSheetsWorkspaceRow.text(field: String): String? =
        firstPresent(field)?.trim()?.takeIf { it.isNotBlank() }

    private fun GoogleSheetsWorkspaceRow.textList(field: String): List<String> =
        text(field)?.split(',', ';', '\n')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()

    private fun GoogleSheetsWorkspaceRow.boolean(field: String): Boolean =
        when (text(field)?.lowercase(Locale.US)) {
            "true", "yes", "y", "1", "checked" -> true
            else -> false
        }

    private fun GoogleSheetsWorkspaceRow.firstPresent(vararg labels: String): String? =
        labels.asSequence().mapNotNull { label ->
            values[label] ?: values[label.fieldKey()] ?: values[label.lowercase(Locale.US)]
        }.firstOrNull()

    private fun GoogleSheetsWorkspaceRow.titleFor(surface: WorkspaceGraphSurface): String =
        text(WorkspaceGraphContract.schema(surface).titleField.label).orEmpty()

    private fun GoogleSheetsWorkspaceRow.hasDiagnostics(surface: WorkspaceGraphSurface): Boolean =
        diagnostics.any { it.surface == surface && it.identifier == identifier }

    private fun diagnostic(
        surface: WorkspaceGraphSurface,
        row: GoogleSheetsWorkspaceRow,
        field: String,
        code: String,
        message: String,
    ) {
        diagnostics += V4InboundNeedsReviewDiagnostic(surface, row.identifier, field, code, message)
    }

    private fun metadata(
        id: EntityId,
        row: GoogleSheetsWorkspaceRow? = null,
        archivedAt: UtcTimestamp? = null,
    ): EntityMetadata =
        EntityMetadata(
            id = id,
            householdId = householdId,
            createdAt = baseMetadataById[id]?.createdAt ?: now,
            updatedAt = now,
            archivedAt = archivedAt,
            revision = (baseMetadataById[id]?.revision ?: 0L) + 1L,
            source = row?.let(::sourceFor) ?: defaultSource,
        )

    private fun commandRecord(type: String, affectedId: EntityId, row: GoogleSheetsWorkspaceRow): CommandRecord =
        CommandRecord(
            commandId = CommandId(stableUuid("${householdId.value}:${providerKey}_v4:${row.tab}:${row.identifier}:$type:command")),
            householdId = householdId,
            type = type,
            source = sourceFor(row),
            requestedAt = now,
            appliedAt = now,
            affectedEntityIds = listOf(affectedId),
        )

    private fun entityId(surface: WorkspaceGraphSurface, identifier: String): EntityId =
        EntityId(identifier.takeIfUuid() ?: stableUuid("${householdId.value}:v4:${surface.key}:$identifier"))

    private fun sourceFor(row: GoogleSheetsWorkspaceRow): SourceRef =
        SourceRef(
            kind = SourceKind.DATA_HOME_HUMAN,
            label = "${providerKey}_v4_inbound",
            externalReference = row.remoteIdentity ?: "$providerKey:${row.tab}:${row.identifier}",
        )

    private fun detectConcurrentHighRiskEdit(
        surface: WorkspaceGraphSurface,
        row: GoogleSheetsWorkspaceRow,
    ) {
        val projection = baseProjection ?: return
        val providerRevision = row.values["_wf_revision"]?.trim()?.toLongOrNull() ?: return
        val canonicalId = entityId(surface, row.identifier).value
        val baseRow = projection.rows[surface].orEmpty().firstOrNull { it.canonicalId == canonicalId } ?: return
        if (providerRevision >= baseRow.revision) return
        val schema = WorkspaceGraphContract.schema(surface)
        schema.fields
            .filter { it.risk == WorkspaceGraphConflictRisk.HIGH && it.owner != WorkspaceGraphFieldOwner.APP_DERIVED }
            .forEach { field ->
                val remote = row.values[field.label] ?: row.values[field.key] ?: return@forEach
                val local = baseRow.values[field.key].toComparableProviderText(projection)
                if (!providerValuesEqual(remote, local, field.type)) {
                    diagnostic(
                        surface = surface,
                        row = row,
                        field = field.label,
                        code = "concurrent_high_risk_edit",
                        message = "${field.label} changed remotely from revision $providerRevision while local revision is ${baseRow.revision}.",
                    )
                }
            }
    }

    private val DEFAULT_SHOPPING_LIST_ID = EntityId(stableUuid("${householdId.value}:v4:shopping:default-list"))
}

private fun WorkspaceGraphValue?.toComparableProviderText(projection: WorkspaceGraphProjection): String =
    when (this) {
        null -> ""
        is WorkspaceGraphValue.Text -> value
        is WorkspaceGraphValue.Decimal -> value.stripTrailingZeros().toPlainString()
        is WorkspaceGraphValue.MoneyValue -> majorUnits.stripTrailingZeros().toPlainString()
        is WorkspaceGraphValue.Date -> value
        is WorkspaceGraphValue.DateTime -> Instant.ofEpochMilli(epochMillis).toString()
        is WorkspaceGraphValue.BooleanValue -> value.toString()
        is WorkspaceGraphValue.TextList -> values.joinToString(", ")
        is WorkspaceGraphValue.Relation -> {
            val targetRows = projection.rows[target].orEmpty().associateBy { it.canonicalId }
            val titleKey = WorkspaceGraphContract.schema(target).titleField.key
            canonicalIds.mapNotNull { id ->
                (targetRows[id]?.values?.get(titleKey) as? WorkspaceGraphValue.Text)?.value
            }.joinToString(", ")
        }
        is WorkspaceGraphValue.Computed -> ""
    }

private fun providerValuesEqual(
    remote: String,
    local: String,
    type: WorkspaceGraphValueType,
): Boolean =
    when (type) {
        WorkspaceGraphValueType.DECIMAL, WorkspaceGraphValueType.MONEY ->
            remote.trim().toBigDecimalOrNull()?.compareTo(local.trim().toBigDecimalOrNull()) == 0
        WorkspaceGraphValueType.BOOLEAN ->
            remote.trim().lowercase(Locale.US).toBooleanStrictOrNull() ==
                local.trim().lowercase(Locale.US).toBooleanStrictOrNull()
        WorkspaceGraphValueType.DATE_TIME ->
            runCatching { Instant.parse(remote.trim()).toEpochMilli() }.getOrNull() ==
                runCatching { Instant.parse(local.trim()).toEpochMilli() }.getOrNull()
        WorkspaceGraphValueType.MULTI_SELECT, WorkspaceGraphValueType.RELATION ->
            remote.split(',', ';', '\n').map { it.trim().lowercase(Locale.US) }.filter(String::isNotBlank).toSet() ==
                local.split(',', ';', '\n').map { it.trim().lowercase(Locale.US) }.filter(String::isNotBlank).toSet()
        else -> remote.trim().equals(local.trim(), ignoreCase = true)
    }

private enum class QuantityDimension { COUNT, MASS, VOLUME, UNKNOWN }

private fun QuantityUnit.dimension(): QuantityDimension =
    when (code) {
        "gram", "kilogram", "ounce", "pound" -> QuantityDimension.MASS
        "milliliter", "liter", "teaspoon", "tablespoon", "cup", "fluid_ounce", "pint", "quart", "gallon" -> QuantityDimension.VOLUME
        "unknown" -> QuantityDimension.UNKNOWN
        else -> QuantityDimension.COUNT
    }

private fun QuantityUnit.compatibleWith(other: QuantityUnit): Boolean =
    when {
        this == QuantityUnit.UNKNOWN || other == QuantityUnit.UNKNOWN -> true
        this == other -> true
        dimension() in setOf(QuantityDimension.MASS, QuantityDimension.VOLUME) && dimension() == other.dimension() -> true
        else -> false
    }

private fun Money.multiply(multiplier: BigDecimal, currency: String): Money =
    Money(BigDecimal(minorUnits).multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValueExact(), currency)

private fun Money?.plusOrNull(other: Money?): Money? =
    when {
        this == null -> other
        other == null -> this
        currencyCode == other.currencyCode -> Money(minorUnits + other.minorUnits, currencyCode)
        else -> null
    }

private fun Money.minusOrNull(other: Money?): Money? =
    when {
        other == null -> this
        currencyCode == other.currencyCode -> Money(minorUnits - other.minorUnits, currencyCode)
        else -> null
    }

private fun surfaceFor(tab: String): WorkspaceGraphSurface? {
    val normalized = tab.trim()
    if (normalized.equals("_wf_lots", ignoreCase = true)) return WorkspaceGraphSurface.STOCK_LOTS
    return WorkspaceGraphSurface.entries.firstOrNull {
        it.label.equals(normalized, ignoreCase = true) || it.key.equals(normalized, ignoreCase = true)
    }
}

private fun String.fieldKey(): String =
    lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')

private fun String.relationKey(): String =
    lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()

private fun String.humanize(): String =
    lowercase(Locale.US).replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun String.takeIfUuid(): String? =
    try {
        UUID.fromString(this).toString()
    } catch (_: IllegalArgumentException) {
        null
    }

private fun stableUuid(seed: String): String =
    UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
