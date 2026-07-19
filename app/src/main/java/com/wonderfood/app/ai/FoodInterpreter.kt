package com.wonderfood.app.ai

import com.wonderfood.app.data.AiTurn
import com.wonderfood.app.data.ChatRole
import com.wonderfood.app.data.CompositeDraft
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
import com.wonderfood.app.data.foodEmojiForName
import kotlin.math.roundToInt

class FoodInterpreter {
    fun interpret(text: String, memory: FoodMemory, promptContext: String? = null): AiTurn {
        CommandEnvelopeDraftMapper.tryMap(text)?.let { return it }
        val lower = text.lowercase()
        val contextLower = promptContext.orEmpty().lowercase()
        DeterministicPurchaseTemplates.tryTurn(text, memory, promptContext)?.let { return it }
        val effectiveFoodText = text.effectiveFoodListText(memory)
        val foodItems = extractFoodCandidates(effectiveFoodText)
        val looksLikeFoodList = effectiveFoodText.looksLikeFoodList(foodItems)
        val explicitGrocery = lower.looksLikeGrocery()
        val explicitInventory = lower.looksLikeInventory()
        return when {
            lower.looksLikePlannedRecipeRequest() && !text.hasRecipeDetails() &&
                memory.recipes.none { recipe -> recipe.title.sameFoodTitle(text.extractPlannedMealTitle(lower.detectMealSlot())) } ->
                recipeClarificationTurn(text.extractPlannedMealTitle(lower.detectMealSlot()))
            lower.looksLikePlannedRecipeRequest() -> plannedMealTurn(text, memory, includeRecipe = true)
            lower.looksLikeSpecificMealPlanRequest() -> plannedMealTurn(text, memory, includeRecipe = false)
            lower.looksLikeMealPlanRequest() -> mealPlanTurn(memory)
            lower.looksLikeNutritionQuestion() && !text.hasNutritionQuantity() -> nutritionClarificationTurn(text)
            lower.looksLikeMealLog() -> mealLogTurn(text, memory)
            lower.looksLikeRecipe() && !text.hasRecipeDetails() -> recipeClarificationTurn(
                text.cleanTitle(prefixes = listOf("recipe for", "recepie for", "reciepe for", "save recipe", "save recepie", "make", "cook", "i made"))
                    .substringBeforeRecipeDetails()
                    .ifBlank { "that recipe" }
                    .toDisplayName(),
            )
            lower.looksLikeRecipe() -> recipeTurn(text, memory)
            explicitGrocery && foodItems.isNotEmpty() -> groceryTurn(effectiveFoodText)
            explicitInventory && foodItems.isNotEmpty() -> inventoryTurn(effectiveFoodText)
            contextLower.looksLikeGroceryContext() && looksLikeFoodList -> groceryTurn(effectiveFoodText)
            contextLower.looksLikeInventoryContext() && looksLikeFoodList -> inventoryTurn(effectiveFoodText)
            looksLikeFoodList && lower.looksLikeOwnedFoodList() -> inventoryTurn(effectiveFoodText)
            explicitGrocery -> groceryTurn(effectiveFoodText)
            explicitInventory -> inventoryTurn(effectiveFoodText)
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
        val explicit = text.extractExplicitNutrition()
        val lower = text.lowercase()
        val title = text.cleanTitle(
            prefixes = listOf("i ate", "ate", "i had", "had", "log", "logged", "for breakfast", "for lunch", "for dinner"),
        ).stripExplicitNutritionFacts().stripTrailingMealSlot().ifBlank { "Meal from chat" }
        return AiTurn(
            reply = if (explicit.hasAny) {
                "I used the nutrition numbers you wrote and estimated any missing macros. Review before saving."
            } else {
                "I estimated nutrition from the meal description. Confirm or correct calories and macros before accepting."
            },
            draft = MealLogDraft(
                titleText = title.toDisplayName(),
                calories = explicit.calories ?: estimate.calories,
                proteinGrams = explicit.protein ?: estimate.protein,
                carbsGrams = explicit.carbs ?: estimate.carbs,
                fatGrams = explicit.fat ?: estimate.fat,
                mealSlot = lower.detectMealSlot(),
                usedItemsText = memory.inventory
                    .filter { item -> lower.contains(item.name.lowercase()) }
                    .take(6)
                    .joinToString(", ") { it.name },
            ),
        )
    }

    private fun recipeClarificationTurn(title: String): AiTurn =
        AiTurn(
            reply = "I can save $title, but I need the ingredients first. What goes into it, and roughly how do you cook it?",
            draft = null,
        )

    private fun plannedMealTurn(text: String, memory: FoodMemory, includeRecipe: Boolean): AiTurn {
        val lower = text.lowercase()
        val slot = lower.detectMealSlot()
        val dayOffset = lower.detectDayOffset()
        val title = text.extractPlannedMealTitle(slot)
        val estimate = estimateNutrition(title)
        val plan = MealPlanDraft(
            titleText = when (dayOffset) {
                0 -> "Today ${slot.label.lowercase()} plan"
                1 -> "Tomorrow ${slot.label.lowercase()} plan"
                else -> "${slot.label} plan"
            }.toDisplayName(),
            daysText = "${slot.label}: $title",
            groceryHint = "Review missing ingredients for $title.",
            entries = listOf(
                MealPlanEntryDraft(
                    dayOffset = dayOffset,
                    slot = slot,
                    title = title,
                    calorieTarget = estimate.calories,
                ),
            ),
        )
        val shouldCreateRecipe = includeRecipe && memory.recipes.none { recipe -> recipe.title.sameFoodTitle(title) }
        val draft = if (shouldCreateRecipe) {
            CompositeDraft(
                drafts = listOf(
                    recipeDraftForPlannedMeal(title, memory, text.extractRecipeDetails()),
                    plan,
                ),
            )
        } else {
            plan
        }
        return AiTurn(
            reply = if (shouldCreateRecipe) {
                "I drafted the missing recipe and scheduled it for ${if (dayOffset == 1) "tomorrow" else "the selected day"} ${slot.label.lowercase()}."
            } else {
                "I drafted the ${slot.label.lowercase()} plan. Accept it to put it on the calendar."
            },
            draft = draft,
        )
    }

    private fun recipeTurn(text: String, memory: FoodMemory): AiTurn {
        val clean = text.cleanTitle(prefixes = listOf("recipe for", "recepie for", "reciepe for", "save recipe", "save recepie", "make", "cook", "i made"))
        val title = clean.substringBeforeRecipeDetails().takeIf { it.isNotBlank() }?.toDisplayName() ?: "House recipe"
        return AiTurn(
            reply = "I drafted a personal recipe from the chat. This is yours, not a bundled recipe database.",
            draft = recipeDraftForPlannedMeal(title, memory, text.extractRecipeDetails()),
        )
    }

    private fun recipeDraftForPlannedMeal(title: String, memory: FoodMemory, detailsText: String = ""): RecipeDraft {
        val inventoryNames = memory.inventory.take(8).joinToString(", ") { it.name }.ifBlank { "what you have on hand" }
        val details = detailsText.trim()
        return RecipeDraft(
            titleText = title,
            ingredientsText = details.ifBlank { "From chat plus pantry context: $inventoryNames" },
            stepsText = "1. Prep ingredients. 2. Cook until done. 3. Taste, adjust seasoning, and save your edits after cooking.",
            servings = 2,
            prepMinutes = 25,
            tags = "chat-derived, personal",
            imageUri = foodEmojiForName(title),
        )
    }

    private fun mealPlanTurn(memory: FoodMemory): AiTurn {
        val plan = DeterministicMealPlanner.plan(memory)
        return AiTurn(
            reply = "I drafted an explainable no-LLM meal plan from pantry coverage, expiry urgency, preferences, nutrition targets, variety, missing ingredients, and repetition. Review before saving.",
            draft = plan.draft,
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

private data class ExplicitNutrition(
    val calories: Int? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
) {
    val hasAny: Boolean
        get() = calories != null || protein != null || carbs != null || fat != null
}

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

private fun String.extractExplicitNutrition(): ExplicitNutrition =
    ExplicitNutrition(
        calories = Regex("""(?i)\b(\d+(?:\.\d+)?)\s*(?:kcal|calories?|cals?)\b""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.roundToInt(),
        protein = macroValue("protein"),
        carbs = macroValue("carbs?|carbohydrates?"),
        fat = macroValue("fat|fats"),
    )

private fun String.macroValue(labelPattern: String): Double? {
    val beforeLabel = Regex("""(?i)\b(\d+(?:\.\d+)?)\s*(?:g|grams?)?\s*(?:of\s+)?(?:$labelPattern)\b""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
    if (beforeLabel != null) return beforeLabel
    return Regex("""(?i)\b(?:$labelPattern)\s*:?\s*(\d+(?:\.\d+)?)\s*(?:g|grams?)?\b""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()
}

private fun extractFoodCandidates(raw: String): List<FoodCandidate> {
    val cleaned = raw
        .replace(Regex("(?i)^(i\\s+)?(bought|got|picked up|added|add|need|needs|buy|to buy|receipt says|receipt|we have|i have)\\s+"), "")
        .replace(Regex("(?i)\\b(to my|to the|to|into|in the|for the)\\s+(groceries|grocery|shopping|pantry|fridge|freezer|inventory|list)\\b"), "")
        .replace(Regex("(?i)\\b(from|at)\\s+(the\\s+)?store\\b.*$"), "")
        .replace(Regex("(?i)\\b(from|at)\\s+[a-z0-9' -]+$"), "")
        .replace(";", ",")
        .replace("\n", ",")

    return cleaned
        .split(",", " and ")
        .map { it.trim(' ', '.', '!', '?', '-') }
        .map { it.removePrefix("some ").removePrefix("a ").removePrefix("an ") }
        .filter { it.length >= 2 }
        .filterNot { it.isControlOnlyFoodToken() }
        .filterNot { token -> listOf("please", "thanks", "today").any { token.equals(it, ignoreCase = true) } }
        .map { it.toFoodCandidate() }
        .filterNot { it.name.isControlOnlyFoodToken() }
        .distinctBy { it.name.lowercase() }
        .take(20)
}

private fun String.effectiveFoodListText(memory: FoodMemory): String {
    if (!referencesPreviousFoodItemsForChange() && !isBareFoodListTargetCommand()) return this
    return memory.messages
        .asReversed()
        .filter { it.role == ChatRole.USER }
        .map { it.body.removePrefix("Voice note:").trim() }
        .dropWhile { it.equals(trim(), ignoreCase = true) }
        .firstOrNull { candidate ->
            candidate.looksLikeFoodList(extractFoodCandidates(candidate))
        } ?: this
}

private fun String.referencesPreviousFoodItemsForChange(): Boolean {
    val lower = lowercase()
    if (lower.isQuestionAboutPastAction()) return false
    val references = listOf("them", "these", "those", "it").any { Regex("""\b$it\b""").containsMatchIn(lower) }
    val action = listOf("add", "save", "put", "move", "turn", "make").any { it in lower }
    return references && action
}

private fun String.isBareFoodListTargetCommand(): Boolean {
    val lower = lowercase()
    if (lower.isQuestionAboutPastAction()) return false
    val action = listOf("add", "save", "put", "move").any { Regex("""\b$it\b""").containsMatchIn(lower) }
    val target = lower.looksLikeInventory() || lower.looksLikeGrocery()
    return action && target && extractFoodCandidates(this).isEmpty()
}

private fun String.isQuestionAboutPastAction(): Boolean {
    val lower = trim().lowercase()
    return lower.endsWith("?") ||
        lower.startsWith("did ") ||
        lower.startsWith("do ") ||
        lower.startsWith("does ") ||
        lower.startsWith("was ") ||
        lower.startsWith("were ")
}

private fun String.looksLikeFoodList(items: List<FoodCandidate>): Boolean =
    items.size >= 2 ||
        (items.size == 1 && (contains(",") || lowercase().looksLikeOwnedFoodList() || hasQuantitySignal()))

private fun String.looksLikeGroceryContext(): Boolean =
    listOf("section: shop", "shopping", "grocery item", "to-buy", "to buy").any { it in this }

private fun String.looksLikeInventoryContext(): Boolean =
    listOf("section: kitchen", "kitchen:", "kitchen item", "pantry, fridge, freezer", "food memory").any { it in this }

private fun String.looksLikeOwnedFoodList(): Boolean =
    listOf("left", "remaining", "serving", "servings", "walmart", "costco", "bag", "bags", "pouch", "frozen", "canned").any { it in this }

private fun String.hasQuantitySignal(): Boolean =
    Regex("""(?i)(?:^|\s|\()\d+(?:\.\d+)?\s*(?:x|lb|lbs|g|kg|oz|dozen|cans?|packs?|bags?|bunch(?:es)?|cartons?|boxes?|servings?|pouches?|packets?|containers?|jars?|heads?|cups?|pieces?|items?|gallons?|liters?|litres?|quarts?)?\b""")
        .containsMatchIn(this)

private fun String.isControlOnlyFoodToken(): Boolean {
    val normalized = lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    return normalized in setOf(
        "add",
        "add them",
        "them",
        "these",
        "those",
        "it",
        "to pantry",
        "to fridge",
        "to freezer",
        "to inventory",
        "to groceries",
        "to grocery",
        "pantry",
        "fridge",
        "freezer",
        "inventory",
        "grocery",
        "groceries",
        "shopping",
        "shopping list",
        "list",
    )
}

private fun String.toFoodCandidate(): FoodCandidate {
    val normalized = cleanFoodToken()
    val leadingQuantity = Regex("^(${quantityPattern()})\\s+(.+)$", RegexOption.IGNORE_CASE).find(normalized)
    val trailingQuantity = Regex("^(.+?)\\s*[-(]?\\s*(${quantityPattern()})(?:\\s*(?:left|remaining))?\\)?$", RegexOption.IGNORE_CASE).find(normalized)
    val embeddedQuantity = Regex("^(.+?)\\s+(${quantityPattern()})(?:\\s+.*)?$", RegexOption.IGNORE_CASE).find(normalized)
    val quantity = when {
        leadingQuantity != null -> leadingQuantity.groupValues[1]
        trailingQuantity != null -> trailingQuantity.groupValues[2]
        embeddedQuantity != null -> embeddedQuantity.groupValues[2]
        else -> ""
    }.trim()
    val name = when {
        leadingQuantity != null -> leadingQuantity.groupValues[2]
        trailingQuantity != null -> trailingQuantity.groupValues[1]
        embeddedQuantity != null -> embeddedQuantity.groupValues[1]
        else -> normalized
    }.trim().toDisplayName()
    return FoodCandidate(
        name = name,
        quantity = quantity,
        zone = classifyStorageZone(name),
        category = categorizeFood(name),
        imageUri = foodEmojiForName(name),
    )
}

private fun String.cleanFoodToken(): String =
    trim(' ', '.', '!', '?', '-', ':', ';')
        .replace(Regex("""(?i)\b(?:from|at)?\s*(walmart|costco|trader joe'?s|whole foods|aldi|target|sam'?s club)\b"""), " ")
        .replace(Regex("""(?i)(?:\$\s*\d+(?:\.\d+)?|\b\d+\s*dollars?\b|\b\d+dollar\b)"""), " ")
        .replace(Regex("""(?i)\b(?:left|remaining)\b$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '.', '!', '?', '-', ':', ';', '(', ')')

private fun quantityPattern(): String =
    """(?:\d+(?:\.\d+)?|one|two|three|four|five|six|seven|eight|nine|ten|half|quarter)\s*(?:x|lb|lbs|g|kg|oz|dozen|cans?|packs?|bags?|bunch(?:es)?|cartons?|boxes?|servings?|pouches?|packets?|containers?|jars?|heads?|cups?|pieces?|items?|gallons?|liters?|litres?|quarts?)?"""

private fun nutritionClarificationTurn(text: String): AiTurn =
    AiTurn(
        reply = if (text.hasNamedFoodForNutrition()) {
            "I can estimate that, but portion changes everything. Roughly how much did you eat or plan to eat?"
        } else {
            "I can estimate nutrition, but I need the food and rough portion first. What was it, and how much?"
        },
        draft = null,
    )

private fun String.detectMealSlot(): MealSlot =
    when {
        "breakfast" in this -> MealSlot.BREAKFAST
        "lunch" in this -> MealSlot.LUNCH
        "dinner" in this -> MealSlot.DINNER
        "snack" in this -> MealSlot.SNACK
        else -> MealSlot.FLEX
    }

private fun String.detectDayOffset(): Int =
    when {
        "day after tomorrow" in this -> 2
        "tomorrow" in this || "tmrw" in this || "tomorrow's" in this || "tomorrows" in this -> 1
        else -> 0
    }

private fun String.looksLikeInventory(): Boolean =
    listOf("bought", "picked up", "got ", "receipt", "we have", "i have", "stocked", "pantry", "fridge", "freezer", "inventory").any { it in this }

private fun String.looksLikeGrocery(): Boolean =
    listOf("grocery", "groceries", "shopping", "to buy", "need ", "buy ").any { it in this }

private fun String.looksLikeMealLog(): Boolean =
    listOf("i ate", "ate ", "i had", "had ", "log ", "breakfast", "lunch", "dinner", "snack").any { it in this }

private fun String.looksLikeNutritionQuestion(): Boolean =
    listOf("calorie", "calories", "kcal", "protein", "carb", "carbs", "fat", "macro", "macros", "nutrition").any { it in this }

private fun String.looksLikeRecipe(): Boolean =
    listOf("recipe", "recepie", "reciepe", "make ", "cook ", "i made").any { it in this }

private fun String.looksLikeMealPlanRequest(): Boolean =
    listOf("meal plan", "plan meals", "next week", "this week", "prep").any { it in this }

private fun String.looksLikeSpecificMealPlanRequest(): Boolean =
    detectMealSlot() != MealSlot.FLEX &&
        listOf("tomorrow", "tomorrows", "tomorrow's", "tmrw", "plan", "schedule", "prep", "next ").any { it in this }

private fun String.looksLikePlannedRecipeRequest(): Boolean =
    looksLikeSpecificMealPlanRequest() && looksLikeRecipe()

private fun String.extractPlannedMealTitle(slot: MealSlot): String {
    val cleaned = substringBeforeRecipeDetails()
        .replace(Regex("""(?i)\b(day after tomorrow|tomorrow'?s?|tomorrows|tmrw|today|tonight)\b"""), " ")
        .replace(Regex("""(?i)\b(breakfast|lunch|dinner|snack|flex)\b"""), " ")
        .replace(Regex("""(?i)\b(plan|schedule|add|make|cook|save|create|recipe|recepie|reciepe|for|my|me|please|and|also|too|a|an|the)\b"""), " ")
        .replace(Regex("""[^\p{L}\p{N}' -]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '.')
    return cleaned.takeIf { it.isNotBlank() }?.toDisplayName() ?: "Planned ${slot.label}"
}

private fun String.sameFoodTitle(other: String): Boolean =
    normalizeFoodTitle() == other.normalizeFoodTitle()

private fun String.normalizeFoodTitle(): String =
    lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun String.hasRecipeDetails(): Boolean =
    extractRecipeDetails().isNotBlank()

private fun String.hasNutritionQuantity(): Boolean =
    Regex("""(?i)\b(\d+(?:\.\d+)?|one|two|three|four|five|half|quarter)\s*(cup|cups|tbsp|tsp|tablespoons?|teaspoons?|g|gram|grams|kg|oz|ounce|ounces|lb|lbs|serving|servings|slice|slices|piece|pieces|bowl|plate|packet|packets|can|cans)\b""")
        .containsMatchIn(this) ||
        Regex("""(?i)\b(small|medium|large|half|full)\s+(bowl|plate|serving|portion)\b""").containsMatchIn(this)

private fun String.hasNamedFoodForNutrition(): Boolean =
    lowercase()
        .replace(Regex("""\b(calories?|kcal|protein|carbs?|fat|macros?|nutrition|how|many|much|estimate|for|in|is|are|the|a|an|my)\b"""), " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()
        .length >= 3

private fun String.extractRecipeDetails(): String {
    val match = Regex("""(?i)\b(?:with|using|ingredients?|steps?|method|recipe details?)\b\s*:?\s*(.+)""").find(this)
    val value = match?.groupValues?.getOrNull(1)
        ?.replace(Regex("""(?i)\b(?:for\s+)?(?:today|tomorrow'?s?|tomorrows|tmrw|breakfast|lunch|dinner|snack)\b.*$"""), "")
        ?.trim(' ', '.', '-', ':')
        .orEmpty()
    return value.takeIf { detail ->
        detail.length >= 3 && detail.split(",", " and ", "\n").any { it.trim().length >= 3 }
    }.orEmpty()
}

private fun String.substringBeforeRecipeDetails(): String =
    replace(Regex("""(?i)\b(?:with|using|ingredients?|steps?|method|recipe details?)\b.*$"""), "")
        .trim(' ', '.', '-', ':')

private fun String.cleanTitle(prefixes: List<String>): String {
    var out = trim()
    prefixes.forEach { prefix ->
        out = out.replace(Regex("^$prefix\\s*", RegexOption.IGNORE_CASE), "")
    }
    return out.trim(' ', '.', '!', '?', '-')
}

private fun String.stripExplicitNutritionFacts(): String =
    replace(Regex("""(?i)\b\d+(?:\.\d+)?\s*(?:kcal|calories?|cals?)\b"""), " ")
        .replace(Regex("""(?i)\b\d+(?:\.\d+)?\s*(?:g|grams?)?\s*(?:of\s+)?(?:protein|carbs?|carbohydrates?|fat|fats)\b"""), " ")
        .replace(Regex("""(?i)\b(?:protein|carbs?|carbohydrates?|fat|fats)\s*:?\s*\d+(?:\.\d+)?\s*(?:g|grams?)?\b"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '.', '!', '?', '-')

private fun String.stripTrailingMealSlot(): String =
    replace(Regex("""(?i)\s+for\s+(breakfast|lunch|dinner|snack)$"""), "")
        .trim(' ', '.', '!', '?', '-')

private fun String.toDisplayName(): String =
    split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

private fun List<String>.pick(index: Int): String = getOrElse(index % size) { "pantry item" }

private val Int.plural: String
    get() = if (this == 1) "" else "s"
