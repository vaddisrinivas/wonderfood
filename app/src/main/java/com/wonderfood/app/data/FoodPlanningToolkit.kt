package com.wonderfood.app.data

import java.time.LocalDate

data class NutritionLookupRequest(
    val name: String = "",
    val barcode: String = "",
)

data class NutritionFacts(
    val displayName: String,
    val servingText: String,
    val calories: Int?,
    val proteinGrams: Double?,
    val carbsGrams: Double?,
    val fatGrams: Double?,
    val ingredientsText: String = "",
    val allergensText: String = "",
    val provider: String,
    val providerReference: String,
    val confidence: Double,
)

interface NutritionLookupProvider {
    val providerName: String
    fun lookup(request: NutritionLookupRequest): NutritionFacts?
}

object OpenFoodFactsNutritionProvider : NutritionLookupProvider {
    override val providerName: String = "open_food_facts"

    override fun lookup(request: NutritionLookupRequest): NutritionFacts? {
        val key = request.barcode.ifBlank { request.name }.lowercase()
        val facts = packagedFixtures.firstOrNull { fixture ->
            key == fixture.barcode || fixture.aliases.any { it in key || key in it }
        } ?: return null
        return facts.toNutritionFacts(
            provider = providerName,
            providerReference = "https://world.openfoodfacts.org/api/v2/product/${facts.barcode}.json",
            confidence = if (request.barcode.isNotBlank()) 0.94 else 0.82,
        )
    }

    private val packagedFixtures = listOf(
        NutritionFixture(
            barcode = "737628064502",
            name = "Generic rolled oats",
            serving = "40 g",
            calories = 150,
            protein = 5.0,
            carbs = 27.0,
            fat = 3.0,
            ingredients = "whole grain oats",
            allergens = "",
            aliases = listOf("rolled oats", "oats", "oatmeal"),
        ),
        NutritionFixture(
            barcode = "041303000393",
            name = "Generic greek yogurt",
            serving = "170 g",
            calories = 100,
            protein = 17.0,
            carbs = 6.0,
            fat = 0.0,
            ingredients = "cultured milk",
            allergens = "milk",
            aliases = listOf("greek yogurt", "yogurt"),
        ),
    )
}

object UsdaFoodDataCentralNutritionProvider : NutritionLookupProvider {
    override val providerName: String = "usda_fooddata_central"

    override fun lookup(request: NutritionLookupRequest): NutritionFacts? {
        val key = request.name.lowercase()
        val facts = genericFixtures.firstOrNull { fixture ->
            fixture.aliases.any { it in key || key in it }
        } ?: return null
        return facts.toNutritionFacts(
            provider = providerName,
            providerReference = "https://fdc.nal.usda.gov/fdc-app.html#/food-search?query=${facts.name.replace(" ", "%20")}",
            confidence = 0.78,
        )
    }

    private val genericFixtures = listOf(
        NutritionFixture(
            barcode = "",
            name = "Cooked brown rice",
            serving = "1 cup cooked",
            calories = 216,
            protein = 5.0,
            carbs = 45.0,
            fat = 2.0,
            aliases = listOf("brown rice", "rice"),
        ),
        NutritionFixture(
            barcode = "",
            name = "Egg, whole",
            serving = "1 large",
            calories = 72,
            protein = 6.3,
            carbs = 0.4,
            fat = 4.8,
            aliases = listOf("egg", "eggs"),
        ),
        NutritionFixture(
            barcode = "",
            name = "Chicken breast, cooked",
            serving = "100 g",
            calories = 165,
            protein = 31.0,
            carbs = 0.0,
            fat = 3.6,
            aliases = listOf("chicken breast", "chicken"),
        ),
        NutritionFixture(
            barcode = "",
            name = "Spinach, raw",
            serving = "100 g",
            calories = 23,
            protein = 2.9,
            carbs = 3.6,
            fat = 0.4,
            aliases = listOf("spinach"),
        ),
    )
}

class NutritionProviderChain(
    private val providers: List<NutritionLookupProvider> = listOf(
        OpenFoodFactsNutritionProvider,
        UsdaFoodDataCentralNutritionProvider,
    ),
) {
    fun lookup(request: NutritionLookupRequest): NutritionFacts? =
        providers.firstNotNullOfOrNull { it.lookup(request) }

    fun enrich(candidate: FoodCandidate, barcode: String = ""): FoodCandidate {
        val facts = lookup(NutritionLookupRequest(name = candidate.name, barcode = barcode)) ?: return candidate
        return candidate.copy(
            name = if (candidate.name.isBlank()) facts.displayName else candidate.name,
            servingText = candidate.servingText.ifBlank { facts.servingText },
            calories = candidate.calories ?: facts.calories,
            proteinGrams = candidate.proteinGrams ?: facts.proteinGrams,
            carbsGrams = candidate.carbsGrams ?: facts.carbsGrams,
            fatGrams = candidate.fatGrams ?: facts.fatGrams,
            nutritionSource = facts.provider,
            evidence = facts.providerReference,
            confidence = maxOf(candidate.confidence, facts.confidence),
            warnings = candidate.warnings + buildList {
                if (facts.allergensText.isNotBlank()) add("Allergens from ${facts.provider}: ${facts.allergensText}.")
                add("Nutrition imported from ${facts.provider}; verify serving size before health export.")
            },
        )
    }
}

data class ParsedIngredient(
    val rawText: String,
    val quantity: Double?,
    val unit: String,
    val name: String,
)

object RecipeIngredientParser {
    private val linePrefix = Regex("""^\s*[-*]?\s*""")
    private val ingredientPattern = Regex(
        """^(?:(\d+(?:\.\d+)?|\d+/\d+)\s*)?([a-zA-Z]+|cups?|tbsp|tsp|g|kg|oz|lb|cloves?)?\s+(.+)$""",
    )

    fun parse(text: String): List<ParsedIngredient> =
        text.lineSequence()
            .map { it.replace(linePrefix, "").trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.endsWith(":") || it.startsWith("http", ignoreCase = true) }
            .map(::parseLine)
            .toList()

    fun toRecipeDraft(raw: String): RecipeDraft {
        val clean = raw.trim()
        val lines = clean.lines().map { it.trim() }.filter { it.isNotBlank() }
        val title = lines.firstOrNull { !it.startsWith("http", ignoreCase = true) }
            ?.take(80)
            ?.ifBlank { null }
            ?: "Imported recipe"
        val ingredientBlock = clean.substringAfter("Ingredients", clean)
            .substringBefore("Instructions")
            .substringBefore("Directions")
            .trim()
        val ingredients = parse(ingredientBlock).joinToString("\n") { ingredient ->
            listOfNotNull(
                ingredient.quantity?.let { value -> if (value % 1.0 == 0.0) value.toInt().toString() else value.toString() },
                ingredient.unit.takeIf { it.isNotBlank() },
                ingredient.name,
            ).joinToString(" ")
        }.ifBlank { ingredientBlock.ifBlank { clean } }
        val steps = clean.substringAfter("Instructions", "")
            .ifBlank { clean.substringAfter("Directions", "") }
            .ifBlank { "Imported for review. Add cooking steps before relying on this recipe." }
        return RecipeDraft(
            titleText = title,
            ingredientsText = ingredients,
            stepsText = steps.trim(),
            tags = "recipe_import",
        )
    }

    private fun parseLine(line: String): ParsedIngredient {
        val match = ingredientPattern.find(line)
        val quantity = match?.groupValues?.getOrNull(1).orEmpty().toQuantityOrNull()
        val unit = match?.groupValues?.getOrNull(2).orEmpty().trim()
        val name = match?.groupValues?.getOrNull(3).orEmpty()
            .ifBlank { line }
            .trim(',', '.', ';', ' ')
            .cleanIngredientName()
        return ParsedIngredient(line, quantity, unit, name)
    }
}

data class HouseholdProfile(
    val label: String,
    val allergies: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val calorieTarget: Int? = null,
    val proteinTargetGrams: Int? = null,
)

object HouseholdProfileParser {
    fun parse(preferences: FoodPreferences): List<HouseholdProfile> {
        val profileLines = preferences.healthNotes.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("profile:", ignoreCase = true) }
            .toList()
        if (profileLines.isEmpty()) {
            return listOf(
                HouseholdProfile(
                    label = "Household",
                    allergies = preferences.allergies.terms(),
                    dislikes = preferences.dislikes.terms(),
                    calorieTarget = preferences.calorieGoal.filter(Char::isDigit).toIntOrNull(),
                    proteinTargetGrams = preferences.proteinGoal.filter(Char::isDigit).toIntOrNull(),
                ),
            )
        }
        return profileLines.mapIndexed { index, line ->
            val fields = line.removePrefix("profile:").split("|").map { it.trim() }
            HouseholdProfile(
                label = fields.getOrElse(0) { "Profile ${index + 1}" }.ifBlank { "Profile ${index + 1}" },
                allergies = fields.getOrElse(1) { "" }.terms(),
                dislikes = fields.getOrElse(2) { "" }.terms(),
                calorieTarget = fields.getOrElse(3) { "" }.filter(Char::isDigit).toIntOrNull(),
                proteinTargetGrams = fields.getOrElse(4) { "" }.filter(Char::isDigit).toIntOrNull(),
            )
        }
    }
}

data class PreparedBatchPlan(
    val title: String,
    val sourceRecipeId: Long?,
    val portionCount: Int,
    val storageZone: StorageZone,
    val perServingCalories: Int?,
    val perServingProteinGrams: Double?,
    val consumeByEpochDay: Long,
    val shoppingDraft: GroceryDraft,
)

data class MealPrepRemixSuggestion(
    val title: String,
    val usedItems: List<String>,
    val missingItems: List<String>,
    val mealLogDraft: MealLogDraft,
)

object FoodPlanningToolkit {
    fun pantryFirstPlan(memory: HouseholdUiMemory, todayEpochDay: Long = LocalDate.now().toEpochDay()): CompositeDraft {
        val profiles = HouseholdProfileParser.parse(memory.preferences)
        val hardBlocks = profiles.flatMap { it.allergies }.toSet()
        val rankedRecipes = memory.recipes
            .filterNot { recipe -> hardBlocks.any { recipe.containsTerm(it) } }
            .map { recipe -> recipe to recipeCoverage(recipe, memory.inventory) }
            .sortedWith(compareByDescending<Pair<Recipe, RecipeCoverage>> { it.second.matched.size }.thenBy { it.second.missing.size })
            .take(5)
        val entries = rankedRecipes.take(3).mapIndexed { index, pair ->
            MealPlanEntryDraft(
                dayOffset = index,
                slot = if (index == 0) MealSlot.DINNER else MealSlot.LUNCH,
                title = pair.first.title,
                calorieTarget = pair.first.estimatedCalories(),
            )
        }
        val missing = rankedRecipes.flatMap { (_, coverage) -> coverage.missing }
            .distinctBy { it.lowercase() }
            .take(12)
        val expiring = memory.inventory
            .filter { it.expiresAtMillis != null }
            .sortedBy { it.expiresAtMillis }
            .take(5)
            .map { it.name }
        val daysText = buildString {
            appendLine("Pantry-first plan")
            if (expiring.isNotEmpty()) appendLine("Use soon: ${expiring.joinToString()}")
            rankedRecipes.forEachIndexed { index, pair ->
                appendLine("Day ${index + 1}: ${pair.first.title} (${pair.second.matched.size} on hand, ${pair.second.missing.size} missing)")
            }
        }.trim()
        val plan = MealPlanDraft(
            titleText = "Pantry-first ${todayEpochDay.weekLabel()} plan",
            daysText = daysText,
            groceryHint = missing.joinToString(),
            entries = entries,
            startDateEpochDay = todayEpochDay,
        )
        val groceries = GroceryDraft(missing.map { name ->
            FoodCandidate(
                name = name,
                quantity = "for pantry-first plan",
                zone = classifyStorageZone(name),
                category = categorizeFood(name),
                notes = "Missing ingredient for pantry-first meal plan.",
                confidence = 0.72,
                evidence = "pantry_first_plan",
            )
        })
        return CompositeDraft(listOf(plan, groceries))
    }

    fun preparedBatchForRecipe(
        recipe: Recipe,
        portionCount: Int,
        storageZone: StorageZone = StorageZone.FREEZER,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): PreparedBatchPlan {
        val safePortions = portionCount.coerceAtLeast(1)
        val calories = recipe.estimatedCalories()
        val ingredientGroceries = RecipeIngredientParser.parse(recipe.ingredients).take(12).map {
            FoodCandidate(
                name = it.name,
                quantity = it.rawText,
                zone = classifyStorageZone(it.name),
                category = categorizeFood(it.name),
                notes = "Ingredient for ${recipe.title} batch prep.",
            )
        }
        return PreparedBatchPlan(
            title = "${recipe.title} batch",
            sourceRecipeId = recipe.id,
            portionCount = safePortions,
            storageZone = storageZone,
            perServingCalories = calories,
            perServingProteinGrams = null,
            consumeByEpochDay = todayEpochDay + if (storageZone == StorageZone.FREEZER) 90 else 4,
            shoppingDraft = GroceryDraft(ingredientGroceries),
        )
    }

    fun shoppingForPlan(
        recipes: List<Recipe>,
        entries: List<MealPlanEntry>,
        inventory: List<InventoryItem>,
    ): GroceryDraft {
        val ingredients = entries.flatMap { entry ->
            val recipe = entry.recipeId?.let { id -> recipes.firstOrNull { it.id == id } }
                ?: recipes.firstOrNull { it.title.foodMatches(entry.title) || entry.title.foodMatches(it.title) }
            recipe?.let { RecipeIngredientParser.parse(it.ingredients) }.orEmpty()
        }
        val missing = ingredients
            .filterNot { ingredient -> inventory.any { item -> item.name.foodMatches(ingredient.name) || ingredient.name.foodMatches(item.name) } }
            .groupBy { it.name.lowercase() }
            .map { (_, rows) ->
                val first = rows.first()
                FoodCandidate(
                    name = first.name,
                    quantity = rows.joinToString(" + ") { it.rawText }.take(120),
                    zone = classifyStorageZone(first.name),
                    category = categorizeFood(first.name),
                    notes = "Scaled from ${rows.size} planned meal${if (rows.size == 1) "" else "s"}.",
                    evidence = "meal_plan_scaled_shopping",
                )
            }
            .take(24)
        return GroceryDraft(missing)
    }

    fun remixSuggestions(memory: HouseholdUiMemory): List<MealPrepRemixSuggestion> {
        val inventoryNames = memory.inventory.map { it.name }
        val bases = inventoryNames.filter { name ->
            listOf("chicken", "rice", "beans", "lentil", "tofu", "veg", "potato").any { it in name.lowercase() }
        }.ifEmpty { inventoryNames.take(3) }
        if (bases.isEmpty()) return emptyList()
        val recentMeals = memory.mealLogs.take(8).map { it.title.lowercase() }
        val templates = listOf(
            "Bowl" to listOf("sauce", "greens"),
            "Wrap" to listOf("tortilla", "yogurt sauce"),
            "Soup" to listOf("broth", "herbs"),
            "Skillet" to listOf("eggs", "spice mix"),
        )
        return templates.mapNotNull { (style, missing) ->
            val title = "${bases.first()} $style"
            if (recentMeals.any { title.lowercase() in it || it in title.lowercase() }) return@mapNotNull null
            MealPrepRemixSuggestion(
                title = title,
                usedItems = bases.take(3),
                missingItems = missing.filterNot { need -> inventoryNames.any { it.foodMatches(need) || need.foodMatches(it) } },
                mealLogDraft = MealLogDraft(
                    titleText = title,
                    usedItemsText = bases.take(3).joinToString(),
                    source = "meal_prep_remix",
                ),
            )
        }.take(4)
    }

    fun compatibilityExport(memory: HouseholdUiMemory): String =
        buildString {
            appendLine("{")
            appendLine("  \"format\": \"wonderfood.compatibility.v1\",")
            appendLine("  \"privacy\": \"local-first export; API keys and credentials excluded\",")
            appendLine("  \"counts\": {")
            appendLine("    \"inventory\": ${memory.inventory.size},")
            appendLine("    \"groceries\": ${memory.groceries.size},")
            appendLine("    \"recipes\": ${memory.recipes.size},")
            appendLine("    \"meal_logs\": ${memory.mealLogs.size},")
            appendLine("    \"meal_plans\": ${memory.mealPlans.size},")
            appendLine("    \"prepared_remix_suggestions\": ${remixSuggestions(memory).size}")
            appendLine("  },")
            appendLine("  \"nutrition_sources\": ${memory.inventory.map { it.nutritionSource }.filter { it.isNotBlank() }.distinct().toJsonArray()},")
            appendLine("  \"external_formats_considered\": ${listOf("Waistline CSV/JSON", "Food You exports", "OpenNutriTracker exports").toJsonArray()}")
            appendLine("}")
        }
}

private data class NutritionFixture(
    val barcode: String,
    val name: String,
    val serving: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val ingredients: String = "",
    val allergens: String = "",
    val aliases: List<String>,
) {
    fun toNutritionFacts(provider: String, providerReference: String, confidence: Double): NutritionFacts =
        NutritionFacts(
            displayName = name,
            servingText = serving,
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            ingredientsText = ingredients,
            allergensText = allergens,
            provider = provider,
            providerReference = providerReference,
            confidence = confidence,
        )
}

private data class RecipeCoverage(
    val matched: List<String>,
    val missing: List<String>,
)

private fun recipeCoverage(recipe: Recipe, inventory: List<InventoryItem>): RecipeCoverage {
    val ingredients = RecipeIngredientParser.parse(recipe.ingredients).map { it.name }
    val matched = ingredients.filter { ingredient ->
        inventory.any { item -> item.name.foodMatches(ingredient) || ingredient.foodMatches(item.name) }
    }
    val missing = ingredients.filterNot { it in matched }
    return RecipeCoverage(matched = matched, missing = missing)
}

private fun Recipe.containsTerm(term: String): Boolean =
    term.isNotBlank() && listOf(title, ingredients, tags).any { it.contains(term, ignoreCase = true) }

private fun Recipe.estimatedCalories(): Int? {
    val serving = servings?.takeIf { it > 0 } ?: return null
    val roughTotal = RecipeIngredientParser.parse(ingredients).sumOf { ingredient ->
        when (categorizeFood(ingredient.name)) {
            "protein" -> 180
            "grain" -> 220
            "fat" -> 120
            "fruit", "produce" -> 50
            else -> 80
        }
    }
    return (roughTotal / serving).takeIf { it > 0 }
}

private fun String.toQuantityOrNull(): Double? =
    when {
        "/" in this -> {
            val parts = split("/")
            val numerator = parts.getOrNull(0)?.toDoubleOrNull()
            val denominator = parts.getOrNull(1)?.toDoubleOrNull()
            if (numerator != null && denominator != null && denominator != 0.0) numerator / denominator else null
        }
        else -> toDoubleOrNull()
    }

private fun String.terms(): List<String> =
    split(",", ";", "\n")
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }

private fun Long.weekLabel(): String = "week ${this / 7}"

private fun List<String>.toJsonArray(): String =
    joinToString(prefix = "[", postfix = "]") { "\"${it.jsonEscaped()}\"" }

private fun String.jsonEscaped(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

private fun String.cleanIngredientName(): String =
    lowercase()
        .replace(Regex("""\([^)]*\)"""), "")
        .replace(Regex("""\b(chopped|diced|minced|sliced|cooked|raw|fresh|frozen|optional|large|small|medium)\b"""), "")
        .replace(Regex("""[^a-z0-9 ]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun String.foodMatches(other: String): Boolean {
    val left = cleanIngredientName()
    val right = other.cleanIngredientName()
    if (left.isBlank() || right.isBlank()) return false
    return left == right || left in right || right in left
}
