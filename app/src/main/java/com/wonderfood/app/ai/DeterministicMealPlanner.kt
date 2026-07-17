package com.wonderfood.app.ai

import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntryDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.Recipe
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object DeterministicMealPlanner {
    fun plan(memory: FoodMemory, nowMillis: Long = System.currentTimeMillis(), mealCount: Int = 5): DeterministicMealPlan {
        val candidates = recipeCandidates(memory, nowMillis) + inventoryCandidates(memory, nowMillis)
        val ranked = candidates
            .filterNot { it.blocked }
            .sortedWith(compareByDescending<PlanCandidate> { it.score }.thenBy { it.title })
            .distinctBy { it.title.normalized() }
            .ifEmpty { fallbackCandidates(memory) }
            .take(mealCount)

        val slots = listOf(MealSlot.DINNER, MealSlot.LUNCH, MealSlot.DINNER, MealSlot.BREAKFAST, MealSlot.DINNER)
        val entries = ranked.mapIndexed { index, candidate ->
            MealPlanEntryDraft(
                dayOffset = index,
                slot = slots.getOrElse(index % slots.size) { MealSlot.DINNER },
                title = candidate.title,
                calorieTarget = candidate.calories,
            )
        }
        val daysText = entries.zip(ranked).joinToString("\n") { (entry, candidate) ->
            "${entry.slot.label}: ${entry.title} — ${candidate.explanation}"
        }
        val groceries = ranked
            .flatMap { it.missingIngredients }
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
            .joinToString(", ")
            .ifBlank { "No required groceries; pantry-first plan." }

        return DeterministicMealPlan(
            draft = MealPlanDraft(
                titleText = "Deterministic pantry-first plan",
                daysText = daysText,
                groceryHint = groceries,
                entries = entries,
            ),
            explanations = ranked.map { it.explanation },
        )
    }

    private fun recipeCandidates(memory: FoodMemory, nowMillis: Long): List<PlanCandidate> =
        memory.recipes.map { recipe ->
            val ingredients = recipe.ingredientTokens()
            val inventoryMatches = memory.inventory.filter { item ->
                ingredients.any { ingredient -> ingredient.matchesFood(item.name) }
            }
            val missing = ingredients
                .filterNot { ingredient -> memory.inventory.any { item -> ingredient.matchesFood(item.name) } }
                .take(8)
            val score = 30 +
                pantryCoverageScore(inventoryMatches, ingredients.size) +
                expiryScore(inventoryMatches, nowMillis) +
                preferenceScore(recipe.title, recipe.ingredients, memory) +
                nutritionScore(estimateCalories(recipe.title), memory) -
                missing.size * 4 -
                repetitionPenalty(recipe.title, memory)
            PlanCandidate(
                title = recipe.title,
                calories = estimateCalories(recipe.title),
                score = score,
                missingIngredients = missing,
                explanation = explanation(
                    score = score,
                    coverage = inventoryMatches.size,
                    missing = missing.size,
                    expiring = inventoryMatches.count { it.isExpiringSoon(nowMillis) },
                ),
                blocked = blockedByPreferences(recipe.title, recipe.ingredients, memory),
            )
        }

    private fun inventoryCandidates(memory: FoodMemory, nowMillis: Long): List<PlanCandidate> {
        val preferred = memory.preferences.preferredStaples.tokens()
        val items = memory.inventory
            .sortedWith(compareByDescending<InventoryItem> { if (it.isExpiringSoon(nowMillis)) 1 else 0 }.thenBy { it.name })
            .take(12)
            .ifEmpty {
                preferred.mapIndexed { index, name ->
                    InventoryItem(
                        id = -index.toLong() - 1L,
                        name = name,
                        quantity = "",
                        zone = com.wonderfood.app.data.StorageZone.PANTRY,
                        category = "",
                        notes = "",
                        imageUri = null,
                        expiresAtMillis = null,
                        source = "preference",
                        createdAtMillis = nowMillis,
                        updatedAtMillis = nowMillis,
                    )
                }
            }
        return items.mapIndexed { index, item ->
            val title = when (index % 4) {
                0 -> "${item.name} bowl"
                1 -> "${item.name} stir fry"
                2 -> "Leftovers with ${item.name}"
                else -> "Quick ${item.name} plate"
            }.toDisplayName()
            val missing = if (item.category.contains("produce", ignoreCase = true)) {
                listOf("protein backup")
            } else {
                listOf("fresh greens")
            }
            val score = 20 +
                pantryCoverageScore(listOf(item), 1) +
                expiryScore(listOf(item), nowMillis) +
                preferenceScore(title, item.category, memory) +
                nutritionScore(estimateCalories(title), memory) -
                repetitionPenalty(title, memory)
            PlanCandidate(
                title = title,
                calories = estimateCalories(title),
                score = score,
                missingIngredients = missing,
                explanation = explanation(
                    score = score,
                    coverage = 1,
                    missing = missing.size,
                    expiring = if (item.isExpiringSoon(nowMillis)) 1 else 0,
                ),
                blocked = blockedByPreferences(title, item.category, memory),
            )
        }
    }

    private fun fallbackCandidates(memory: FoodMemory): List<PlanCandidate> {
        val dislikes = memory.preferences.dislikes.lowercase(Locale.US)
        return listOf("Eggs rice bowl", "Beans and greens", "Yogurt fruit plate", "Pantry pasta", "Flexible leftovers")
            .filterNot { dislikes.contains(it.lowercase(Locale.US)) }
            .ifEmpty { listOf("Flexible pantry meal") }
            .mapIndexed { index, title ->
                PlanCandidate(
                    title = title,
                    calories = estimateCalories(title),
                    score = 1 - index,
                    missingIngredients = listOf("fresh greens"),
                    explanation = "fallback pantry template; add inventory for better scoring",
                    blocked = false,
                )
            }
    }

    private fun pantryCoverageScore(matches: List<InventoryItem>, ingredientCount: Int): Int =
        if (ingredientCount <= 0) matches.size * 8 else ((matches.size.toDouble() / max(1, ingredientCount)) * 30).roundToInt()

    private fun expiryScore(items: List<InventoryItem>, nowMillis: Long): Int =
        items.sumOf { item ->
            when {
                item.expiresAtMillis == null -> 0
                item.expiresAtMillis <= nowMillis -> 18
                item.expiresAtMillis <= nowMillis + THREE_DAYS_MILLIS -> 14
                item.expiresAtMillis <= nowMillis + SEVEN_DAYS_MILLIS -> 6
                else -> 0
            }
        }

    private fun preferenceScore(title: String, details: String, memory: FoodMemory): Int {
        val haystack = "$title $details".lowercase(Locale.US)
        val preferred = (
            memory.preferences.preferredStaples.tokens() +
                memory.preferences.preferredCuisines.tokens() +
                memory.preferences.dietStyle.tokens()
            ).count { it in haystack }
        return preferred * 7
    }

    private fun nutritionScore(calories: Int, memory: FoodMemory): Int {
        val target = memory.preferences.calorieGoal.tokens()
            .firstNotNullOfOrNull { it.toIntOrNull() }
            ?: return 0
        return (12 - abs(target - calories) / 50).coerceAtLeast(-8)
    }

    private fun repetitionPenalty(title: String, memory: FoodMemory): Int {
        val normalized = title.normalized()
        val mealLogPenalty = memory.mealLogs.count { it.title.normalized().overlaps(normalized) } * 12
        val planPenalty = memory.mealPlanEntries.count { it.title.normalized().overlaps(normalized) } * 8
        return mealLogPenalty + planPenalty
    }

    private fun blockedByPreferences(title: String, details: String, memory: FoodMemory): Boolean {
        val haystack = "$title $details".lowercase(Locale.US)
        val blocked = memory.preferences.dislikes.tokens() + memory.preferences.allergies.tokens()
        return blocked.any { token -> token.length >= 3 && token in haystack }
    }

    private fun explanation(score: Int, coverage: Int, missing: Int, expiring: Int): String =
        "score $score: pantry coverage $coverage, expiring $expiring, missing $missing"

    private fun Recipe.ingredientTokens(): List<String> =
        ingredients
            .lines()
            .flatMap { it.split(",", ";", " and ") }
            .map { line ->
                line.lowercase(Locale.US)
                    .replace(Regex("""\b(\d+(\.\d+)?|cups?|tbsp|tsp|grams?|g|kg|lbs?|oz|cans?|pinch|small|large|medium)\b"""), " ")
                    .replace(Regex("""[^a-z\s]"""), " ")
                    .trim()
                    .split(Regex("""\s+"""))
                    .takeLast(2)
                    .joinToString(" ")
            }
            .filter { it.length >= 3 }
            .distinct()

    private fun String.matchesFood(foodName: String): Boolean {
        val ingredient = normalized()
        val food = foodName.normalized()
        return ingredient in food || food in ingredient || ingredient.split(" ").any { it.length >= 4 && it in food }
    }

    private fun InventoryItem.isExpiringSoon(nowMillis: Long): Boolean =
        expiresAtMillis != null && expiresAtMillis <= nowMillis + THREE_DAYS_MILLIS

    private fun String.tokens(): List<String> =
        split(",", "\n", ";", "/", "|", " ")
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.length >= 3 }

    private fun String.normalized(): String =
        lowercase(Locale.US).replace(Regex("""[^a-z0-9\s]"""), " ").replace(Regex("""\s+"""), " ").trim()

    private fun String.overlaps(other: String): Boolean =
        isNotBlank() && other.isNotBlank() && (this in other || other in this)

    private fun String.toDisplayName(): String =
        split(Regex("""\s+""")).joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }

    private fun estimateCalories(title: String): Int {
        val lower = title.lowercase(Locale.US)
        var calories = 420
        if ("rice" in lower || "bowl" in lower) calories += 120
        if ("beans" in lower || "lentil" in lower || "chicken" in lower || "tofu" in lower || "paneer" in lower) calories += 120
        if ("yogurt" in lower || "fruit" in lower || "breakfast" in lower) calories -= 80
        if ("leftover" in lower) calories += 40
        return calories.coerceIn(250, 850)
    }

    private const val THREE_DAYS_MILLIS = 3L * 24L * 60L * 60L * 1000L
    private const val SEVEN_DAYS_MILLIS = 7L * 24L * 60L * 60L * 1000L
}

data class DeterministicMealPlan(
    val draft: MealPlanDraft,
    val explanations: List<String>,
)

private data class PlanCandidate(
    val title: String,
    val calories: Int,
    val score: Int,
    val missingIngredients: List<String>,
    val explanation: String,
    val blocked: Boolean,
)
