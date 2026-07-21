package com.wonderfood.app.data

import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.InventoryEventType
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.MealEntryStatus
import com.wonderfood.core.model.household.NutritionSnapshot
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.ShoppingLineStatus
import com.wonderfood.core.model.household.ShoppingReason
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class CanonicalWeekPlanItem(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    companion object {
        fun fromSnapshot(snapshot: HouseholdSnapshot?, now: Instant = Instant.now()): List<CanonicalWeekPlanItem> {
            snapshot ?: return emptyList()
            val zone = runCatching { ZoneId.of(snapshot.household.timezone) }.getOrDefault(ZoneId.systemDefault())
            val today = now.atZone(zone).toLocalDate()
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val weekEnd = weekStart.plusDays(6)
            val nutritionById = snapshot.nutritionSnapshots.associateBy { it.metadata.id }
            val lotsByItemId = snapshot.inventoryLots
                .filter { it.metadata.archivedAt == null && it.status in activeLotStatuses }
                .groupBy { it.itemId }
            val recipeIngredientsByRecipeId = snapshot.recipeIngredients
                .filter { it.metadata.archivedAt == null && !it.optional }
                .groupBy { it.recipeId }
            return snapshot.mealEntries
                .asSequence()
                .filter { it.metadata.archivedAt == null }
                .filter { it.status != MealEntryStatus.ARCHIVED && it.status != MealEntryStatus.SKIPPED }
                .mapNotNull { entry ->
                    val day = Instant.ofEpochMilli(entry.scheduledAt.epochMillis).atZone(zone).toLocalDate()
                    if (day < weekStart || day > weekEnd) return@mapNotNull null
                    CanonicalWeekPlanItem(
                        id = entry.metadata.id.value,
                        title = entry.title,
                        subtitle = listOfNotNull(
                            "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${day.monthValue}/${day.dayOfMonth}",
                            entry.slot,
                            entry.servings.displayText(),
                            entry.status.name.lowercase(),
                            entry.nutritionSnapshotIds
                                .asSequence()
                                .mapNotNull(nutritionById::get)
                                .firstOrNull()
                                ?.displayText(),
                            entry.leftoverIntent?.takeIf { it.isNotBlank() }?.let { "leftovers $it" },
                            entry.recipeId
                                ?.let(recipeIngredientsByRecipeId::get)
                                ?.inventoryCoverageText(lotsByItemId, entry.servings),
                            snapshot.subtractedInventoryText(entry.metadata.id.value),
                            snapshot.reviewedGapText(entry.metadata.id.value, entry.recipeId?.value),
                            "from ${entry.metadata.source.kind.name.lowercase().replace('_', ' ')} ${entry.metadata.source.label}",
                        ).filter { it.isNotBlank() }.joinToString("  "),
                    ) to entry.scheduledAt.epochMillis
                }
                .sortedWith(
                    compareBy<Pair<CanonicalWeekPlanItem, Long>> { it.second }
                        .thenBy { it.first.title.lowercase() },
                )
                .map { it.first }
                .toList()
        }
    }
}

private val activeLotStatuses = setOf(
    InventoryLotStatus.AVAILABLE,
    InventoryLotStatus.OPENED,
    InventoryLotStatus.RESERVED,
)

private fun Quantity.displayText(): String =
    buildList {
        amount?.value?.let(::add)
        unit.takeIf { it != QuantityUnit.UNKNOWN }?.code?.replace('_', ' ')?.let(::add)
    }.joinToString(" ")

private fun List<com.wonderfood.core.model.household.RecipeIngredient>.inventoryCoverageText(
    lotsByItemId: Map<com.wonderfood.core.model.household.EntityId, List<com.wonderfood.core.model.household.InventoryLot>>,
    servings: Quantity,
): String? {
    val linkedIngredients = filter { it.itemId != null }
    if (linkedIngredients.isEmpty()) return null
    val multiplier = servings.amount?.toBigDecimal()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
    val covered = linkedIngredients.count { ingredient ->
        val itemId = ingredient.itemId ?: return@count false
        val needed = ingredient.quantity.amount?.toBigDecimal()?.multiply(multiplier)
        val available = lotsByItemId[itemId]
            .orEmpty()
            .filter { it.quantity.unit == ingredient.quantity.unit }
            .mapNotNull { it.quantity.amount?.toBigDecimal() }
            .fold(BigDecimal.ZERO, BigDecimal::add)
        needed == null || available >= needed
    }
    return "inventory covers $covered/${linkedIngredients.size} ingredients"
}

private fun HouseholdSnapshot.subtractedInventoryText(entryId: String): String? {
    val consumed = inventoryEvents
        .filter { it.metadata.archivedAt == null }
        .filter { it.type == InventoryEventType.CONSUME && it.relatedEntityId?.value == entryId }
    if (consumed.isEmpty()) return null
    val byUnit = consumed
        .mapNotNull { it.quantityDelta }
        .filter { it.amount != null }
        .groupBy { it.unit }
        .mapValues { (_, quantities) ->
            quantities
                .mapNotNull { it.amount?.toBigDecimal() }
                .fold(BigDecimal.ZERO, BigDecimal::add)
        }
    if (byUnit.isEmpty()) return "inventory subtracted"
    return byUnit.entries.joinToString(", ") { (unit, amount) ->
        "subtracted ${amount.stripTrailingZeros().toPlainString()} ${unit.code.replace('_', ' ')}"
    }
}

private fun HouseholdSnapshot.reviewedGapText(entryId: String, recipeId: String?): String? {
    val sourceIds = setOfNotNull(entryId, recipeId)
    val gaps = shoppingLines.count { line ->
        line.metadata.archivedAt == null &&
            line.status != ShoppingLineStatus.ARCHIVED &&
            line.status != ShoppingLineStatus.SKIPPED &&
            line.reason == ShoppingReason.RECIPE_GAP &&
            line.sourceEntityIds.any { it.value in sourceIds }
    }
    return when (gaps) {
        0 -> null
        1 -> "1 reviewed gap"
        else -> "$gaps reviewed gaps"
    }
}

private fun NutritionSnapshot.displayText(): String =
    listOfNotNull(
        values.energyKcal?.value?.let { "$it kcal" },
        values.proteinGrams?.value?.let { "${it}g protein" },
    ).joinToString("  ")
