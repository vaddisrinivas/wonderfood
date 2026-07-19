package com.wonderfood.app.sync

import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

object GoogleSheetsWorkspaceDraftImporter {
    fun toDraft(rows: List<GoogleSheetsWorkspaceRow>): FoodDraft? {
        val drafts = buildList {
            rows.inventoryDraft()?.let(::add)
            rows.groceryDraft()?.let(::add)
            addAll(rows.recipeDrafts())
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
        val items = filter { it.tab == WonderFoodWorkspaceSchema.KITCHEN }
            .filterNot { it.value("Pantry state").equals("Archived", ignoreCase = true) }
            .filterNot { it.value("Food").isBlank() }
            .map { row ->
                val name = row.value("Food")
                FoodCandidate(
                    name = name,
                    quantity = quantityText(row.value("On hand"), row.value("Unit")),
                    zone = row.value("Location").toStorageZone(),
                    category = categorizeFood(name),
                    expiresAtMillis = row.value("Best by").toEpochMillisOrNull(),
                    confidence = 0.72,
                    evidence = "Google Sheets workspace import: ${row.identifier.ifBlank { row.tab }}",
                )
            }
        return items.takeIf { it.isNotEmpty() }?.let(::InventoryDraft)
    }

    private fun List<GoogleSheetsWorkspaceRow>.groceryDraft(): GroceryDraft? {
        val items = filter { it.tab == WonderFoodWorkspaceSchema.SHOPPING }
            .filterNot { it.value("Cart state").equals("Archived", ignoreCase = true) || it.value("Cart state").equals("Skipped", ignoreCase = true) }
            .filterNot { it.value("Item").isBlank() }
            .map { row ->
                val name = row.value("Item")
                FoodCandidate(
                    name = name,
                    quantity = quantityText(row.value("Needed"), row.value("Unit")),
                    category = categorizeFood(name),
                    confidence = 0.7,
                    evidence = "Google Sheets workspace import: ${row.identifier.ifBlank { row.tab }}",
                )
            }
        return items.takeIf { it.isNotEmpty() }?.let(::GroceryDraft)
    }

    private fun List<GoogleSheetsWorkspaceRow>.recipeDrafts(): List<RecipeDraft> =
        filter { it.tab == WonderFoodWorkspaceSchema.RECIPES }
            .filterNot { it.value("Recipe state").equals("Archived", ignoreCase = true) }
            .filterNot { it.value("Recipe").isBlank() }
            .map { row ->
                RecipeDraft(
                    titleText = row.value("Recipe"),
                    ingredientsText = row.value("Ingredients"),
                    stepsText = row.value("Directions"),
                    servings = row.value("Servings").toDoubleOrNull()?.roundToInt(),
                    prepMinutes = row.value("Prep").toDoubleOrNull()?.roundToInt(),
                )
            }

    private fun List<GoogleSheetsWorkspaceRow>.mealLogDrafts(): List<MealLogDraft> =
        filter { it.tab == WonderFoodWorkspaceSchema.MEALS }
            .filter { row ->
                val state = row.value("Meal state")
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
                    mealSlot = row.value("Slot").toMealSlot(),
                    usedItemsText = row.value("Food IDs"),
                    loggedDateEpochDay = row.value("When").toEpochDayOrNull(),
                    source = "google_sheets_workspace_import",
                )
            }

    private fun List<GoogleSheetsWorkspaceRow>.mealPlanDraft(): MealPlanDraft? {
        val plannedMeals = filter { it.tab == WonderFoodWorkspaceSchema.MEALS }
            .filter { it.value("Meal state").equals("Planned", ignoreCase = true) }
            .filterNot { it.value("Meal").isBlank() }
        if (plannedMeals.isEmpty()) return null
        val firstDate = plannedMeals.mapNotNull { it.value("When").toEpochDayOrNull() }.minOrNull()
        return MealPlanDraft(
            titleText = "Imported Google Sheets plan",
            daysText = plannedMeals.joinToString("\n") { row ->
                "${row.value("When").ifBlank { "Any day" }} ${row.value("Slot").ifBlank { "Meal" }}: ${row.value("Meal")}"
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

    private fun String.toEpochDayOrNull(): Long? =
        parseDate()?.toEpochDay()

    private fun String.toEpochMillisOrNull(): Long? =
        parseDate()?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

    private fun String.parseDate(): LocalDate? =
        runCatching { LocalDate.parse(substringBefore("T").trim()) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(trim()).toLocalDate() }.getOrNull()
}
