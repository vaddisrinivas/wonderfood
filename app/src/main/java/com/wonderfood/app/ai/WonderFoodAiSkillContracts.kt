package com.wonderfood.app.ai

import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.FoodPlanningToolkit
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.GroceryItem
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.HouseholdProfileParser
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlan
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntry
import com.wonderfood.app.data.ParsedIngredient
import com.wonderfood.app.data.Recipe
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.RecipeIngredientParser
import com.wonderfood.app.data.StorageZone
import java.net.URI
import java.time.LocalDate

enum class WonderFoodAiSkillId(val wireName: String) {
    RECIPE_IMPORT("recipe_import"),
    PANTRY_NORMALIZE("pantry_normalize"),
    CAN_COOK("can_cook"),
    MEAL_PLAN("meal_plan"),
    CART_BUILDER("cart_builder"),
    RECIPE_PERSONALIZE("recipe_personalize"),
    COOKING_COACH("cooking_coach"),
    RECEIPT_PARSE("receipt_parse"),
    NUTRITION_ESTIMATE("nutrition_estimate"),
}

data class AiSkillEnvelope<T>(
    val skill: WonderFoodAiSkillId,
    val status: AiSkillStatus,
    val proposal: T?,
    val confidence: Double,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(confidence in 0.0..1.0) { "AI skill confidence must be between 0 and 1." }
    }
}

enum class AiSkillStatus {
    PROPOSED,
    NEEDS_REVIEW,
    NEEDS_CLARIFICATION,
    UNSUPPORTED,
}

data class RecipeImportInput(
    val text: String,
    val sourceUrl: String = "",
)

data class RecipeImportProposal(
    val draft: RecipeDraft,
    val sourceKind: RecipeSourceKind,
    val sourceUrl: String,
    val parsedIngredients: List<ParsedIngredient>,
)

enum class RecipeSourceKind {
    TEXT,
    GENERIC_URL,
    SCHEMA_ORG_RECIPE,
    SAMSUNG_FOOD_SHARED_LINK,
}

object RecipeImportSkillContract {
    fun propose(input: RecipeImportInput): AiSkillEnvelope<RecipeImportProposal> {
        val source = input.sourceUrl.ifBlank { firstUrl(input.text) }
        val draft = RecipeIngredientParser.toRecipeDraft(input.text.ifBlank { source })
        val sourceKind = when {
            source.contains("app.samsungfood.com", ignoreCase = true) || source.contains("samsungfood.com", ignoreCase = true) || source.contains("whisk.com", ignoreCase = true) -> RecipeSourceKind.SAMSUNG_FOOD_SHARED_LINK
            input.text.contains("schema.org/Recipe", ignoreCase = true) || input.text.contains("\"@type\"", ignoreCase = true) && input.text.contains("Recipe", ignoreCase = true) -> RecipeSourceKind.SCHEMA_ORG_RECIPE
            source.isNotBlank() -> RecipeSourceKind.GENERIC_URL
            else -> RecipeSourceKind.TEXT
        }
        val ingredients = RecipeIngredientParser.parse(draft.ingredientsText)
        val warnings = buildList {
            add("Recipe import is a proposal; never overwrite an existing recipe without review.")
            if (sourceKind == RecipeSourceKind.SAMSUNG_FOOD_SHARED_LINK) add("Samsung Food bulk export is unsupported; this handles individual shared links only.")
            if (ingredients.any { it.quantity == null }) add("Some ingredient quantities are unknown and remain visible for review.")
        }
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.RECIPE_IMPORT,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = RecipeImportProposal(draft, sourceKind, source, ingredients),
            confidence = if (ingredients.isNotEmpty()) 0.76 else 0.48,
            warnings = warnings,
        )
    }

    private fun firstUrl(text: String): String =
        text.split(Regex("\\s+")).firstOrNull { token ->
            token.startsWith("https://", ignoreCase = true) || token.startsWith("http://", ignoreCase = true)
        }?.trimEnd('.', ',', ')').orEmpty()
}

data class PantryNormalization(
    val originalName: String,
    val canonicalName: String,
    val category: String,
    val likelyUnit: String,
    val aliases: List<String>,
    val confidence: Double,
)

object PantryNormalizeSkillContract {
    fun normalize(rawNames: List<String>): AiSkillEnvelope<List<PantryNormalization>> {
        val rows = rawNames.mapNotNull { raw ->
            val clean = raw.trim().replace(Regex("\\s+"), " ")
            if (clean.isBlank()) return@mapNotNull null
            PantryNormalization(
                originalName = clean,
                canonicalName = clean.removePackSize().split(" ").joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) },
                category = categoryFor(clean),
                likelyUnit = likelyUnitFor(clean),
                aliases = listOf(clean.lowercase()).distinct(),
                confidence = if (clean.any(Char::isDigit)) 0.72 else 0.64,
            )
        }
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.PANTRY_NORMALIZE,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = rows,
            confidence = rows.map { it.confidence }.average().takeIf { !it.isNaN() } ?: 0.0,
            warnings = listOf("Preserve original labels; canonical names are suggestions until accepted."),
        )
    }
}

data class RecipeKitchenRanking(
    val recipeId: Long,
    val title: String,
    val status: RecipeKitchenStatus,
    val matched: List<String>,
    val missing: List<String>,
    val explanation: String,
)

enum class RecipeKitchenStatus {
    CAN_COOK,
    ALMOST_CAN_COOK,
    MISSING_KEY_ITEMS,
}

object CanCookSkillContract {
    fun rank(recipes: List<Recipe>, inventory: List<InventoryItem>): AiSkillEnvelope<List<RecipeKitchenRanking>> {
        val pantryNames = inventory.map { it.name.cleanFoodToken() }.filter { it.isNotBlank() }
        val rankings = recipes.map { recipe ->
            val ingredients = RecipeIngredientParser.parse(recipe.ingredients).map { it.name }.ifEmpty { recipe.ingredients.split(',', '\n').map { it.trim() } }
            val matched = ingredients.filter { ingredient -> pantryNames.any { pantry -> pantry.foodOverlaps(ingredient.cleanFoodToken()) } }.distinct()
            val missing = ingredients.filterNot { it in matched }.filter { it.isNotBlank() }.distinct()
            val status = when {
                missing.isEmpty() -> RecipeKitchenStatus.CAN_COOK
                matched.size >= missing.size -> RecipeKitchenStatus.ALMOST_CAN_COOK
                else -> RecipeKitchenStatus.MISSING_KEY_ITEMS
            }
            RecipeKitchenRanking(
                recipeId = recipe.id,
                title = recipe.title,
                status = status,
                matched = matched,
                missing = missing,
                explanation = "You have ${matched.size}/${ingredients.size.coerceAtLeast(1)} ingredients; missing ${missing.take(3).joinToString().ifBlank { "nothing obvious" }}.",
            )
        }.sortedWith(compareBy<RecipeKitchenRanking> { it.status.ordinal }.thenByDescending { it.matched.size })
        return AiSkillEnvelope(WonderFoodAiSkillId.CAN_COOK, AiSkillStatus.PROPOSED, rankings, 0.82)
    }
}

data class CartSuggestionSet(
    val draft: GroceryDraft,
    val reasons: Map<String, String>,
)

object CartBuilderSkillContract {
    fun suggestFromPlan(
        recipes: List<Recipe>,
        entries: List<MealPlanEntry>,
        inventory: List<InventoryItem>,
        existingCart: List<GroceryItem> = emptyList(),
    ): AiSkillEnvelope<CartSuggestionSet> {
        val deterministic = FoodPlanningToolkit.shoppingForPlan(recipes, entries, inventory)
        val existingNames = existingCart.filter { it.status != GroceryStatus.BOUGHT }.map { it.name.cleanFoodToken() }
        val merged = deterministic.items.filterNot { item -> existingNames.any { it.foodOverlaps(item.name.cleanFoodToken()) } }
        val draft = GroceryDraft(merged)
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.CART_BUILDER,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = CartSuggestionSet(
                draft = draft,
                reasons = merged.associate { it.name to (it.notes.ifBlank { "Missing ingredient from meal plan." }) },
            ),
            confidence = 0.8,
            warnings = listOf("No silent deletion: existing cart rows are preserved and duplicates are omitted from suggestions only."),
        )
    }
}

data class RecipePersonalizationProposal(
    val originalTitle: String,
    val personalizedTitle: String,
    val changedIngredients: String,
    val explanation: String,
)

object RecipePersonalizeSkillContract {
    fun personalize(recipe: Recipe, goal: String): AiSkillEnvelope<RecipePersonalizationProposal> {
        val lowerGoal = goal.lowercase()
        val changed = when {
            "protein" in lowerGoal -> recipe.ingredients + "\nAdd Greek yogurt or cooked lentils for extra protein"
            "budget" in lowerGoal -> recipe.ingredients.replace(Regex("(?i)chicken breast"), "beans or lentils")
            "spicy" in lowerGoal -> recipe.ingredients + "\nChili flakes to taste"
            else -> recipe.ingredients
        }
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.RECIPE_PERSONALIZE,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = RecipePersonalizationProposal(
                originalTitle = recipe.title,
                personalizedTitle = "${recipe.title} (${goal.ifBlank { "personalized" }})".take(120),
                changedIngredients = changed,
                explanation = "Variant proposal only; preserve original recipe and review nutrition as estimated.",
            ),
            confidence = 0.62,
            warnings = listOf("Nutrition remains estimated until USDA/Open Food Facts/provider lookup verifies it."),
        )
    }
}

data class CookingCoachStep(
    val recipeTitle: String,
    val currentStep: Int,
    val instruction: String,
    val nextAction: String,
    val timerMinutes: Int?,
)

object CookingCoachSkillContract {
    fun nextStep(recipe: Recipe, currentStep: Int, question: String = ""): AiSkillEnvelope<CookingCoachStep> {
        val steps = recipe.steps.split('.', '\n').map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf(recipe.steps.ifBlank { "Review recipe before cooking." }) }
        val index = currentStep.coerceIn(0, steps.lastIndex)
        val timer = Regex("(\\d+)\\s*(minute|min)", RegexOption.IGNORE_CASE).find(steps[index])?.groupValues?.getOrNull(1)?.toIntOrNull()
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.COOKING_COACH,
            status = AiSkillStatus.PROPOSED,
            proposal = CookingCoachStep(
                recipeTitle = recipe.title,
                currentStep = index,
                instruction = steps[index],
                nextAction = if (question.isBlank()) steps.getOrNull(index + 1) ?: "Finish, taste, and log the meal." else "Answer as cooking help, then keep edits as proposals unless saved.",
                timerMinutes = timer,
            ),
            confidence = 0.7,
        )
    }
}

object MealPlanSkillContract {
    fun propose(memory: FoodMemory, today: LocalDate = LocalDate.now()): AiSkillEnvelope<CompositeDraft> {
        val profiles = HouseholdProfileParser.parse(memory.preferences)
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.MEAL_PLAN,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = FoodPlanningToolkit.pantryFirstPlan(memory, today.toEpochDay()),
            confidence = 0.74,
            warnings = buildList {
                add("Planner output is reviewable and biased toward pantry/on-hand foods.")
                if (profiles.any { it.allergies.isNotEmpty() }) add("Hard allergy exclusions must be enforced before saving.")
            },
        )
    }
}

object PlanShareFormatter {
    fun plainText(plan: MealPlan, entries: List<MealPlanEntry>, cart: List<GroceryItem>): String = buildString {
        appendLine("WonderFood plan: ${plan.title}")
        if (plan.daysText.isNotBlank()) appendLine(plan.daysText)
        if (entries.isNotEmpty()) {
            appendLine()
            appendLine("Meals")
            entries.forEach { entry -> appendLine("- ${entry.title} (${entry.slot.name.lowercase()}, ${entry.status.name.lowercase()})") }
        }
        val activeCart = cart.filter { it.status != GroceryStatus.BOUGHT  }
        if (activeCart.isNotEmpty()) {
            appendLine()
            appendLine("Shopping")
            activeCart.forEach { item -> appendLine("- ${item.name}${item.quantity.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()}") }
        }
        appendLine()
        appendLine("Shared from WonderFood. Secrets are excluded.")
    }.trim()
}

private fun String.removePackSize(): String =
    replace(Regex("\\b\\d+(?:\\.\\d+)?\\s*(g|kg|ml|l|oz|lb|ct|count|pack|packs|packet|bag|bags|can|cans)\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun categoryFor(name: String): String = when {
    name.containsAny("rice", "pasta", "oats", "flour", "bread") -> "grain"
    name.containsAny("milk", "yogurt", "cheese", "paneer") -> "dairy"
    name.containsAny("egg", "chicken", "fish", "tofu", "dal", "beans", "lentil") -> "protein"
    name.containsAny("spinach", "tomato", "cilantro", "onion", "apple", "banana") -> "produce"
    name.containsAny("powder", "spice", "masala", "oil") -> "pantry"
    else -> "other"
}

private fun likelyUnitFor(name: String): String = when {
    name.containsAny("milk", "oil", "juice") -> "ml"
    name.containsAny("rice", "flour", "oats", "dal") -> "g"
    name.containsAny("egg") -> "item"
    else -> "item"
}

private fun String.containsAny(vararg terms: String): Boolean = terms.any { contains(it, ignoreCase = true) }

private fun String.cleanFoodToken(): String =
    lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

private fun String.foodOverlaps(other: String): Boolean =
    isNotBlank() && other.isNotBlank() && (this == other || this in other || other in this)
