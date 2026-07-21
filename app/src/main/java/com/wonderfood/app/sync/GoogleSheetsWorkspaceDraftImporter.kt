package com.wonderfood.app.sync

import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodIntakeEnricher
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.ReceiptItemDisposition
import com.wonderfood.app.data.ReceiptItemDraft
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency
import kotlin.math.roundToInt

object GoogleSheetsWorkspaceDraftImporter {
    fun toDraft(rows: List<GoogleSheetsWorkspaceRow>): FoodDraft? {
        val drafts = buildList {
            rows.inventoryDraft()?.let(::add)
            rows.groceryDraft()?.let(::add)
            addAll(rows.recipeDrafts())
            addAll(rows.receiptDrafts())
            addAll(rows.mealLogDrafts())
            rows.mealPlanDraft()?.let(::add)
        }
        return when (drafts.size) {
            0 -> null
            1 -> drafts.single()
            else -> CompositeDraft(drafts)
        }
    }

    private fun List<GoogleSheetsWorkspaceRow>.inventoryDraft(): InventoryDraft? {
        val items = filter { it.tab == WorkspaceGraphSurface.KITCHEN.label }
            .filterNot { it.value("Archived").toBooleanLoose() }
            .filterNot { it.value("Item").isBlank() }
            .map { row ->
                val name = row.value("Item")
                FoodCandidate(
                    name = name,
                    quantity = quantityText(row.value("On hand"), row.value("Unit")),
                    zone = row.value("Location").toStorageZone(),
                    category = categorizeFood(name),
                    expiresAtMillis = row.value("Best before").toEpochMillisOrNull(),
                    confidence = 0.72,
                    evidence = "Google Sheets workspace import: ${row.identifier.ifBlank { row.tab }}",
                )
            }
        return items.takeIf { it.isNotEmpty() }?.let(::InventoryDraft)
    }

    private fun List<GoogleSheetsWorkspaceRow>.groceryDraft(): GroceryDraft? {
        val items = filter { it.tab == WorkspaceGraphSurface.SHOPPING.label }
            .filterNot { it.value("Archived").toBooleanLoose() || it.value("Status").equals("Skipped", ignoreCase = true) }
            .filterNot { it.value("Item").isBlank() }
            .map { row ->
                val name = row.value("Item")
                FoodCandidate(
                    name = name,
                    quantity = quantityText(row.value("Amount"), row.value("Unit")),
                    category = categorizeFood(name),
                    confidence = 0.7,
                    evidence = "Google Sheets workspace import: ${row.identifier.ifBlank { row.tab }}",
                )
            }
        return items.takeIf { it.isNotEmpty() }?.let(::GroceryDraft)
    }

    private fun List<GoogleSheetsWorkspaceRow>.recipeDrafts(): List<RecipeDraft> =
        filter { it.tab == WorkspaceGraphSurface.RECIPES.label }
            .filterNot { it.value("Archived").toBooleanLoose() }
            .filterNot { it.value("Recipe").isBlank() }
            .map { row ->
                val ingredients = this
                    .filter { it.tab == WorkspaceGraphSurface.INGREDIENTS.label }
                    .filterNot { it.value("Archived").toBooleanLoose() }
                    .filter { it.value("Recipe") == row.value("Recipe") || it.value("_wf_recipe_id") == row.identifier }
                    .joinToString("\n") { ingredient ->
                        quantityText(ingredient.value("Amount"), ingredient.value("Unit"))
                            .let { quantity -> listOf(quantity, ingredient.value("Ingredient"), ingredient.value("Preparation")).filter(String::isNotBlank).joinToString(" ") }
                    }
                RecipeDraft(
                    titleText = row.value("Recipe"),
                    ingredientsText = ingredients,
                    stepsText = row.value("Instructions"),
                    servings = row.value("Servings").toDoubleOrNull()?.roundToInt(),
                    prepMinutes = row.value("Prep minutes").toDoubleOrNull()?.roundToInt(),
                )
            }

    private fun List<GoogleSheetsWorkspaceRow>.receiptDrafts(): List<ReceiptDraft> {
        val lineRowsByPurchase = filter { it.tab == WorkspaceGraphSurface.PURCHASE_LINES.label }
            .filterNot { it.value("Archived").toBooleanLoose() }
            .filterNot { it.value("Line").isBlank() }
            .groupBy { it.value("_wf_purchase_id").ifBlank { it.value("Purchase") } }
        return filter { it.tab == WorkspaceGraphSurface.SPENDING.label }
            .filterNot { it.value("Archived").toBooleanLoose() }
            .filter { it.value("Purchase").isNotBlank() || it.value("Merchant").isNotBlank() }
            .mapNotNull { purchase ->
                val lineRows = lineRowsByPurchase[purchase.identifier].orEmpty().ifEmpty { lineRowsByPurchase[purchase.value("Purchase")].orEmpty() }
                if (lineRows.isEmpty()) return@mapNotNull null
                val currency = purchase.value("Currency").ifBlank {
                    lineRows.firstNotNullOfOrNull { it.value("Currency").ifBlank { null } } ?: "USD"
                }
                ReceiptDraft(
                    items = lineRows.map { line ->
                        val name = line.value("Line")
                        val quantity = quantityText(line.value("Quantity"), line.value("Unit"))
                        val category = line.value("Category").ifBlank { categorizeFood(name) }
                        ReceiptItemDraft(
                            food = FoodCandidate(
                                name = name,
                                quantity = quantity,
                                category = category,
                                confidence = 0.7,
                                evidence = "Google Sheets workspace purchase line: ${line.identifier.ifBlank { purchase.identifier }}",
                            ),
                            disposition = line.value("Disposition").toReceiptDisposition(name, category),
                            receiptLine = listOf(quantity, name).filter { it.isNotBlank() }.joinToString(" "),
                            linePriceCents = line.value("Subtotal").moneyMinorUnitsOrNull(currency)
                                ?: purchase.value("Entered total").moneyMinorUnitsOrNull(currency).takeIf { lineRows.size == 1 },
                        )
                    },
                    merchant = purchase.value("Merchant"),
                    purchasedAtMillis = purchase.value("Date").toEpochMillisOrNull(),
                    currencyCode = currency,
                    subtotalCents = null,
                    taxCents = purchase.value("Tax").moneyMinorUnitsOrNull(currency),
                    totalCents = purchase.value("Entered total").moneyMinorUnitsOrNull(currency),
                    sourceLabel = "google_sheets_workspace_import",
                )
            }
    }

    private fun List<GoogleSheetsWorkspaceRow>.mealLogDrafts(): List<MealLogDraft> =
        filter { it.tab == WorkspaceGraphSurface.MEALS.label }
            .filter { row ->
                val state = row.value("Status")
                state.equals("Eaten", ignoreCase = true) ||
                    state.equals("Confirmed", ignoreCase = true) ||
                    state.equals("Served", ignoreCase = true)
            }
            .filterNot { it.value("Meal").isBlank() }
            .map { row ->
                MealLogDraft(
                    titleText = row.value("Meal"),
                    calories = null,
                    proteinGrams = null,
                    carbsGrams = null,
                    fatGrams = null,
                    mealSlot = row.value("Meal slot").toMealSlot(),
                    usedItemsText = row.value("Recipe"),
                    loggedDateEpochDay = row.value("Date").toEpochDayOrNull(),
                    source = "google_sheets_workspace_import",
                )
            }

    private fun List<GoogleSheetsWorkspaceRow>.mealPlanDraft(): MealPlanDraft? {
        val plannedMeals = filter { it.tab == WorkspaceGraphSurface.MEALS.label }
            .filter { it.value("Status").equals("Planned", ignoreCase = true) }
            .filterNot { it.value("Meal").isBlank() }
        if (plannedMeals.isEmpty()) return null
        val firstDate = plannedMeals.mapNotNull { it.value("Date").toEpochDayOrNull() }.minOrNull()
        return MealPlanDraft(
            titleText = "Imported Google Sheets plan",
            daysText = plannedMeals.joinToString("\n") { row ->
                "${row.value("Date").ifBlank { "Any day" }} ${row.value("Meal slot").ifBlank { "Meal" }}: ${row.value("Meal")}"
            },
            groceryHint = "",
            entries = emptyList(),
            startDateEpochDay = firstDate,
        )
    }

    private fun GoogleSheetsWorkspaceRow.value(name: String): String =
        values[name].orEmpty().trim()

    private fun quantityText(amount: String, unit: String): String =
        listOf(amount, unit).filter { it.isNotBlank() }.joinToString(" ")

    private fun String.toStorageZone(): StorageZone =
        when (trim().lowercase()) {
            "fridge", "refrigerator" -> StorageZone.FRIDGE
            "freezer", "frozen" -> StorageZone.FREEZER
            "pantry", "shelf", "cabinet" -> StorageZone.PANTRY
            else -> classifyStorageZone(this)
        }

    private fun String.toMealSlot(): MealSlot =
        when (trim().lowercase()) {
            "breakfast" -> MealSlot.BREAKFAST
            "lunch" -> MealSlot.LUNCH
            "dinner" -> MealSlot.DINNER
            "snack" -> MealSlot.SNACK
            else -> MealSlot.FLEX
        }

    private fun String.toReceiptDisposition(name: String, category: String): ReceiptItemDisposition =
        when (trim().lowercase()) {
            "inventory", "put away", "food", "kitchen" -> ReceiptItemDisposition.INVENTORY
            "household", "non-food", "non food", "expense" -> ReceiptItemDisposition.HOUSEHOLD
            "ignore", "ignored", "skip", "skipped" -> ReceiptItemDisposition.IGNORE
            else -> if (FoodIntakeEnricher.isNonFood(name, category)) {
                ReceiptItemDisposition.HOUSEHOLD
            } else {
                ReceiptItemDisposition.INVENTORY
            }
        }

    private fun String.toEpochDayOrNull(): Long? =
        parseDate()?.toEpochDay()

    private fun String.toEpochMillisOrNull(): Long? =
        parseDate()?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

    private fun String.parseDate(): LocalDate? =
        runCatching { LocalDate.parse(substringBefore("T").trim()) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(trim()).toLocalDate() }.getOrNull()

    private fun String.moneyMinorUnitsOrNull(currencyCode: String): Long? =
        trim()
            .takeIf { it.isNotBlank() }
            ?.let { value ->
                runCatching {
                    val digits = Currency.getInstance(currencyCode.uppercase()).defaultFractionDigits.coerceAtLeast(0)
                    BigDecimal(value)
                        .movePointRight(digits)
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact()
                }.getOrNull()
            }

    private fun String.toBooleanLoose(): Boolean = equals("true", true) || equals("yes", true) || this == "1"
}
