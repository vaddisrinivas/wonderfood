package com.wonderfood.app.ai

import com.wonderfood.app.data.AiTurn
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanEntryDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import kotlin.math.roundToInt

class FoodInterpreter {
    fun interpret(text: String, memory: FoodMemory): AiTurn {
        val lower = text.lowercase()
        return when {
            lower.looksLikeMealPlanRequest() -> mealPlanTurn(memory)
            lower.looksLikeMealLog() -> mealLogTurn(text, memory)
            lower.looksLikeRecipe() -> recipeTurn(text, memory)
            lower.looksLikeGrocery() -> groceryTurn(text)
            lower.looksLikeInventory() -> inventoryTurn(text)
            lower.contains("what can i make") || lower.contains("what should i eat") -> suggestionTurn(memory)
            else -> AiTurn(
                reply = "Got it. I can turn this into pantry items, groceries, recipes, meal logs, or meal plans. Say it naturally and I will draft the database change.",
                draft = null,
            )
        }
    }

    private fun inventoryTurn(text: String): AiTurn {
        val items = extractFoodCandidates(text)
        return if (items.isEmpty()) {
            AiTurn("I heard an inventory update, but I could not confidently split the items. Try commas: `I bought eggs, spinach, rice`.", null)
        } else {
            AiTurn(
                reply = "I found ${items.size} item${items.size.plural} for your food memory. Review, then accept to write them into local SQLite.",
                draft = InventoryDraft(items),
            )
        }
    }

    private fun groceryTurn(text: String): AiTurn {
        val items = extractFoodCandidates(text).map { it.copy(zone = classifyStorageZone(it.name)) }
        return if (items.isEmpty()) {
            AiTurn("I can update the grocery list, but I need item names. Try: `Need oats, bananas and chicken thighs`.", null)
        } else {
            AiTurn(
                reply = "I made a grocery draft. Accept it and the app will add these to your to-buy list.",
                draft = GroceryDraft(items),
            )
        }
    }

    private fun mealLogTurn(text: String, memory: FoodMemory): AiTurn {
        val estimate = estimateNutrition(text)
        val lower = text.lowercase()
        val title = text.cleanTitle(
            prefixes = listOf("i ate", "ate", "i had", "had", "log", "logged", "for breakfast", "for lunch", "for dinner"),
        ).ifBlank { "Meal from chat" }
        return AiTurn(
            reply = "I estimated nutrition from the meal description. This is good enough for planning, and you can correct it later.",
            draft = MealLogDraft(
                titleText = title.toDisplayName(),
                calories = estimate.calories,
                proteinGrams = estimate.protein,
                carbsGrams = estimate.carbs,
                fatGrams = estimate.fat,
                mealSlot = lower.detectMealSlot(),
                usedItemsText = memory.inventory
                    .filter { item -> lower.contains(item.name.lowercase()) }
                    .take(6)
                    .joinToString(", ") { it.name },
            ),
        )
    }

    private fun recipeTurn(text: String, memory: FoodMemory): AiTurn {
        val clean = text.cleanTitle(prefixes = listOf("recipe for", "save recipe", "make", "cook", "i made"))
        val title = clean.takeIf { it.isNotBlank() }?.toDisplayName() ?: "House recipe"
        val inventoryNames = memory.inventory.take(8).joinToString(", ") { it.name }.ifBlank { "what you have on hand" }
        return AiTurn(
            reply = "I drafted a personal recipe from the chat. This is yours, not a bundled recipe database.",
            draft = RecipeDraft(
                titleText = title,
                ingredientsText = "From chat plus pantry context: $inventoryNames",
                stepsText = "1. Prep ingredients. 2. Cook until done. 3. Taste, adjust seasoning, and save your edits after cooking.",
                servings = 2,
                prepMinutes = 25,
                tags = "chat-derived, personal",
            ),
        )
    }

    private fun mealPlanTurn(memory: FoodMemory): AiTurn {
        val inventory = memory.inventory.take(8).map { it.name }
        val preferred = memory.preferences.preferredStaples
            .split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val dislikes = memory.preferences.dislikes.lowercase()
        val base = (inventory + preferred).filterNot { item -> dislikes.contains(item.lowercase()) }
        val foods = if (base.isEmpty()) {
            listOf("eggs", "rice", "beans", "greens").filterNot { dislikes.contains(it) }
        } else {
            base
        }.ifEmpty { listOf("pantry item") }
        val planPrefix = memory.preferences.dietStyle.takeIf { it.isNotBlank() }?.let { "$it " }.orEmpty()
        val entries = listOf(
            MealPlanEntryDraft(0, MealSlot.DINNER, "$planPrefix${foods.pick(0)} bowl", 550),
            MealPlanEntryDraft(1, MealSlot.LUNCH, "$planPrefix${foods.pick(1)} stir fry", 600),
            MealPlanEntryDraft(2, MealSlot.DINNER, "leftovers plus ${foods.pick(2)}", 500),
            MealPlanEntryDraft(3, MealSlot.BREAKFAST, "quick ${foods.pick(3)} plate", 450),
            MealPlanEntryDraft(4, MealSlot.DINNER, "flexible dinner from remaining perishables", 600),
        )
        val days = entries.joinToString("\n") { "${it.slot.label}: ${it.title}" }
        val groceries = listOf("fresh greens", "protein backup", "fruit", "yogurt").joinToString(", ")
        return AiTurn(
            reply = "I drafted a lean meal plan from your current food memory. Accept it when it feels close; otherwise keep chatting and we revise.",
            draft = MealPlanDraft(
                titleText = "Next 5 meals",
                daysText = days,
                groceryHint = groceries,
                entries = entries,
            ),
        )
    }

    private fun suggestionTurn(memory: FoodMemory): AiTurn {
        val fridge = memory.inventory.filter { it.zone.label == "Fridge" }.take(3).joinToString(", ") { it.name }
        val pantry = memory.inventory.filter { it.zone.label == "Pantry" }.take(3).joinToString(", ") { it.name }
        val allergies = memory.preferences.allergies.takeIf { it.isNotBlank() }?.let { " Avoid: $it." }.orEmpty()
        val suggestion = when {
            fridge.isNotBlank() && pantry.isNotBlank() -> "Use $fridge with $pantry. I would make a fast bowl and log it after.$allergies"
            memory.inventory.isNotEmpty() -> "Use ${memory.inventory.take(4).joinToString(", ") { it.name }}. Keep it simple and log the meal after.$allergies"
            else -> "Your local food memory is empty. Tell me what you bought or upload receipt text first."
        }
        return AiTurn(reply = suggestion, draft = null)
    }
}

private data class NutritionEstimate(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

private fun estimateNutrition(text: String): NutritionEstimate {
    val lower = text.lowercase()
    var calories = 420
    var protein = 18.0
    var carbs = 42.0
    var fat = 16.0

    fun add(kcal: Int, p: Double, c: Double, f: Double) {
        calories += kcal
        protein += p
        carbs += c
        fat += f
    }

    if ("chicken" in lower) add(180, 32.0, 0.0, 4.0)
    if ("egg" in lower) add(140, 12.0, 1.0, 10.0)
    if ("rice" in lower) add(220, 4.0, 45.0, 1.0)
    if ("pasta" in lower) add(260, 8.0, 52.0, 2.0)
    if ("beans" in lower) add(180, 11.0, 32.0, 1.0)
    if ("yogurt" in lower) add(120, 14.0, 12.0, 2.0)
    if ("salad" in lower) add(-80, 3.0, 10.0, -4.0)
    if ("pizza" in lower) add(380, 16.0, 42.0, 16.0)
    if ("smoothie" in lower) add(120, 4.0, 28.0, 1.0)
    if ("sandwich" in lower) add(220, 14.0, 28.0, 8.0)

    calories = calories.coerceIn(120, 1400)
    protein = protein.coerceIn(0.0, 90.0)
    carbs = carbs.coerceIn(0.0, 180.0)
    fat = fat.coerceIn(0.0, 80.0)

    return NutritionEstimate(
        calories = (calories / 10.0).roundToInt() * 10,
        protein = protein,
        carbs = carbs,
        fat = fat,
    )
}

private fun extractFoodCandidates(raw: String): List<FoodCandidate> {
    val cleaned = raw
        .replace(Regex("(?i)^(i\\s+)?(bought|got|picked up|added|add|need|needs|buy|to buy|receipt says|receipt|we have|i have)\\s+"), "")
        .replace(Regex("(?i)\\b(to my|to the|in the|for the)\\s+(grocery|shopping|pantry|fridge|freezer|list)\\b"), "")
        .replace(Regex("(?i)\\b(from|at)\\s+[a-z0-9' -]+$"), "")
        .replace(";", ",")
        .replace("\n", ",")

    return cleaned
        .split(",", " and ")
        .map { it.trim(' ', '.', '!', '?', '-') }
        .map { it.removePrefix("some ").removePrefix("a ").removePrefix("an ") }
        .filter { it.length >= 2 }
        .filterNot { token -> listOf("please", "thanks", "today").any { token.equals(it, ignoreCase = true) } }
        .map { it.toFoodCandidate() }
        .distinctBy { it.name.lowercase() }
        .take(12)
}

private fun String.toFoodCandidate(): FoodCandidate {
    val match = Regex("^((?:\\d+(?:\\.\\d+)?|one|two|three|four|five)\\s*(?:x|lb|lbs|g|kg|oz|dozen|cans?|packs?|bags?|bunch(?:es)?|cartons?|boxes?)?)\\s+(.+)$", RegexOption.IGNORE_CASE)
        .find(this)
    val quantity = match?.groupValues?.getOrNull(1).orEmpty()
    val name = (match?.groupValues?.getOrNull(2) ?: this).trim().toDisplayName()
    return FoodCandidate(
        name = name,
        quantity = quantity,
        zone = classifyStorageZone(name),
        category = categorizeFood(name),
    )
}

private fun String.detectMealSlot(): MealSlot =
    when {
        "breakfast" in this -> MealSlot.BREAKFAST
        "lunch" in this -> MealSlot.LUNCH
        "dinner" in this -> MealSlot.DINNER
        "snack" in this -> MealSlot.SNACK
        else -> MealSlot.FLEX
    }

private fun String.looksLikeInventory(): Boolean =
    listOf("bought", "picked up", "got ", "receipt", "we have", "i have", "stocked").any { it in this }

private fun String.looksLikeGrocery(): Boolean =
    listOf("grocery", "shopping", "to buy", "need ", "buy ").any { it in this }

private fun String.looksLikeMealLog(): Boolean =
    listOf("i ate", "ate ", "i had", "had ", "log ", "breakfast", "lunch", "dinner", "snack").any { it in this }

private fun String.looksLikeRecipe(): Boolean =
    listOf("recipe", "make ", "cook ", "i made").any { it in this }

private fun String.looksLikeMealPlanRequest(): Boolean =
    listOf("meal plan", "plan meals", "next week", "this week", "prep").any { it in this }

private fun String.cleanTitle(prefixes: List<String>): String {
    var out = trim()
    prefixes.forEach { prefix ->
        out = out.replace(Regex("^$prefix\\s*", RegexOption.IGNORE_CASE), "")
    }
    return out.trim(' ', '.', '!', '?', '-')
}

private fun String.toDisplayName(): String =
    split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

private fun List<String>.pick(index: Int): String = getOrElse(index % size) { "pantry item" }

private val Int.plural: String
    get() = if (this == 1) "" else "s"
