package com.wonderfood.app.ai

import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.HouseholdUiMemory
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
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.Recipe
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.RecipeIngredientParser
import com.wonderfood.app.data.StorageZone
import java.time.LocalDate
import kotlin.math.roundToInt

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
    val proposalIntent: AiSkillProposalIntent = AiSkillProposalIntent.REVIEW_DRAFT,
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
) {
    init {
        require(confidence in 0.0..1.0) { "AI skill confidence must be between 0 and 1." }
    }
}

data class AiSkillContractMeta(
    val contractVersion: String = "wf.ai.skill-contract.v1",
    val inputVersion: String = "wf.ai.skill-input.v1",
    val outputVersion: String = "wf.ai.skill-output.v1",
)

data class AiSkillProvenance(
    val provider: String = "local_deterministic",
    val providerAvailable: Boolean = true,
    val runMode: String = "offline_first",
    val requestId: String = "in_memory",
    val policy: String = "proposal_only",
)

enum class AiSkillProposalIntent {
    REVIEW_DRAFT,
    ASK_CLARIFICATION,
    BLOCKED,
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
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

data class RecipeImportProposal(
    val draft: RecipeDraft,
    val sourceKind: RecipeSourceKind,
    val sourceUrl: String,
    val parsedIngredients: List<ParsedIngredient>,
    val schemaOrgBoundary: SchemaOrgBoundaryMapping? = null,
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

enum class RecipeSourceKind {
    TEXT,
    GENERIC_URL,
    SCHEMA_ORG_RECIPE,
    SAMSUNG_FOOD_SHARED_LINK,
}

object RecipeImportSkillContract {
    fun propose(input: RecipeImportInput): AiSkillEnvelope<RecipeImportProposal> {
        val effectiveContract = input.contract
        val effectiveProvenance = input.provenance.copy(providerAvailable = input.provenance.providerAvailable)
        if (!effectiveProvenance.providerAvailable) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.RECIPE_IMPORT,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.0,
                warnings = listOf("Recipe import requires upstream parser context; provider is currently unavailable."),
                proposalIntent = AiSkillProposalIntent.BLOCKED,
                contract = effectiveContract,
                provenance = effectiveProvenance.copy(provider = "local_deterministic_unavailable"),
            )
        }

        if (input.text.isBlank() && input.sourceUrl.isBlank()) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.RECIPE_IMPORT,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.0,
                warnings = listOf("No recipe text provided; ask the user for title, ingredients, and steps."),
                proposalIntent = AiSkillProposalIntent.ASK_CLARIFICATION,
                contract = effectiveContract,
                provenance = effectiveProvenance,
            )
        }

        val source = input.sourceUrl.ifBlank { firstUrl(input.text) }
        val schemaOrgBoundary = SchemaOrgBoundaryMapper.extract(input.text)
        val draft = schemaOrgBoundary?.toRecipeDraft()
            ?: RecipeIngredientParser.toRecipeDraft(input.text.ifBlank { source })
        val sourceKind = when {
            source.contains("app.samsungfood.com", ignoreCase = true) || source.contains("samsungfood.com", ignoreCase = true) || source.contains("whisk.com", ignoreCase = true) -> RecipeSourceKind.SAMSUNG_FOOD_SHARED_LINK
            schemaOrgBoundary?.types?.contains("Recipe") == true || input.text.contains("schema.org/Recipe", ignoreCase = true) -> RecipeSourceKind.SCHEMA_ORG_RECIPE
            source.isNotBlank() -> RecipeSourceKind.GENERIC_URL
            else -> RecipeSourceKind.TEXT
        }
        val ingredients = RecipeIngredientParser.parse(draft.ingredientsText)
        val ambiguous = sourceKind == RecipeSourceKind.TEXT && ingredients.size < 2 && input.text.length > 40
        val blocked = input.text.contains("explosive", ignoreCase = true) || input.text.contains("poison", ignoreCase = true)
        if (blocked) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.RECIPE_IMPORT,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.0,
                warnings = listOf(
                    "Safety policy blocks unsafe recipe requests from this channel.",
                    "Ask for a safe replacement recipe instead.",
                ),
                proposalIntent = AiSkillProposalIntent.BLOCKED,
                contract = effectiveContract,
                provenance = effectiveProvenance,
            )
        }
        val warnings = buildList {
            add("Recipe import is a proposal; never overwrite an existing recipe without review.")
            add("No direct-write: imported recipes are proposal-only.")
            if (schemaOrgBoundary != null) add("Schema.org boundary mapping is review-only and not persisted directly.")
            if (schemaOrgBoundary?.products?.isNotEmpty() == true || schemaOrgBoundary?.offers?.isNotEmpty() == true || schemaOrgBoundary?.organizations?.isNotEmpty() == true) {
                add("Schema.org Product, Offer, and Organization data stays at the import boundary.")
            }
            if (sourceKind == RecipeSourceKind.SAMSUNG_FOOD_SHARED_LINK) add("Samsung Food bulk export is unsupported; this handles individual shared links only.")
            if (ingredients.any { it.quantity == null }) add("Some ingredient quantities are unknown and remain visible for review.")
            if (ambiguous) add("Ingredient parsing is uncertain; confirm ingredients list before saving.")
            if (input.text.length < 20 && sourceKind == RecipeSourceKind.TEXT) add("Source text is very short; ask for complete recipe details.")
        }
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.RECIPE_IMPORT,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = RecipeImportProposal(
                draft = draft,
                sourceKind = sourceKind,
                sourceUrl = source,
                parsedIngredients = ingredients,
                schemaOrgBoundary = schemaOrgBoundary,
                contract = effectiveContract,
                provenance = effectiveProvenance,
            ),
            confidence = when {
                ingredients.isEmpty() -> 0.28
                ambiguous -> 0.43
                else -> 0.76
            },
            warnings = warnings,
            contract = effectiveContract,
            provenance = effectiveProvenance,
            proposalIntent = AiSkillProposalIntent.REVIEW_DRAFT,
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
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

object PantryNormalizeSkillContract {
    fun normalize(
        rawNames: List<String>,
        contract: AiSkillContractMeta = AiSkillContractMeta(),
        provenance: AiSkillProvenance = AiSkillProvenance(),
    ): AiSkillEnvelope<List<PantryNormalization>> {
        if (rawNames.none { it.isNotBlank() }) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.PANTRY_NORMALIZE,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.0,
                warnings = listOf("No pantry labels were provided for normalization."),
                proposalIntent = AiSkillProposalIntent.ASK_CLARIFICATION,
                contract = contract,
                provenance = provenance,
            )
        }
        val rows = rawNames.mapNotNull { raw ->
            val clean = raw.trim().replace(Regex("\\s+"), " ")
            if (clean.isBlank()) return@mapNotNull null
            val lower = clean.lowercase()
            if (lower.contains("poison") || lower.contains("explosive")) return@mapNotNull null
            PantryNormalization(
                originalName = clean,
                canonicalName = clean.removePackSize().split(" ").joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) },
                category = categoryFor(clean),
                likelyUnit = likelyUnitFor(clean),
                aliases = listOf(clean.lowercase()).distinct(),
                confidence = if (clean.any(Char::isDigit)) 0.72 else 0.64,
                contract = contract,
                provenance = provenance,
            )
        }
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.PANTRY_NORMALIZE,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = rows,
            confidence = rows.map { it.confidence }.average().takeIf { !it.isNaN() } ?: 0.0,
            warnings = if (rawNames.isNotEmpty() && rows.isEmpty()) {
                listOf(
                    "Unsafe or malformed pantry input was filtered out.",
                    "No direct-write: all pantry suggestions are review-only proposals.",
                )
            } else {
                listOf(
                    "Preserve original labels; canonical names are suggestions until accepted.",
                    "No direct-write: all pantry suggestions are review-only proposals.",
                )
            },
            contract = contract,
            provenance = provenance,
            proposalIntent = AiSkillProposalIntent.REVIEW_DRAFT,
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
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

enum class RecipeKitchenStatus {
    CAN_COOK,
    ALMOST_CAN_COOK,
    MISSING_KEY_ITEMS,
}

object CanCookSkillContract {
    fun rank(
        recipes: List<Recipe>,
        inventory: List<InventoryItem>,
        contract: AiSkillContractMeta = AiSkillContractMeta(),
        provenance: AiSkillProvenance = AiSkillProvenance(),
    ): AiSkillEnvelope<List<RecipeKitchenRanking>> {
        val pantryNames = inventory.map { it.name.cleanFoodToken() }.filter { it.isNotBlank() }
        val rankings = recipes.map { recipe ->
            val ingredients = RecipeIngredientParser.parse(recipe.ingredients).map { it.name }.ifEmpty { recipe.ingredients.split(',', '\n').map { it.trim() } }
            val matched = ingredients.filter { ingredient ->
                pantryNames.any { pantry -> pantry.foodOverlaps(ingredient.cleanFoodToken()) }
            }.distinct()
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
                contract = contract,
                provenance = provenance,
            )
        }.sortedWith(compareBy<RecipeKitchenRanking> { it.status.ordinal }.thenByDescending { it.matched.size })
        if (recipes.isEmpty()) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.CAN_COOK,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = emptyList(),
                confidence = 0.3,
                warnings = listOf("No recipes were provided; ask for recipe candidates before ranking."),
                proposalIntent = AiSkillProposalIntent.ASK_CLARIFICATION,
                contract = contract,
                provenance = provenance,
            )
        }
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.CAN_COOK,
            status = AiSkillStatus.PROPOSED,
            proposal = rankings,
            confidence = 0.82,
            warnings = listOf("No direct-write: ranking is advisory and not applied automatically."),
            contract = contract,
            provenance = provenance,
            proposalIntent = AiSkillProposalIntent.REVIEW_DRAFT,
        )
    }
}

data class CartSuggestionSet(
    val draft: GroceryDraft,
    val reasons: Map<String, String>,
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

object CartBuilderSkillContract {
    fun suggestFromPlan(
        recipes: List<Recipe>,
        entries: List<MealPlanEntry>,
        inventory: List<InventoryItem>,
        existingCart: List<GroceryItem> = emptyList(),
        contract: AiSkillContractMeta = AiSkillContractMeta(),
        provenance: AiSkillProvenance = AiSkillProvenance(),
    ): AiSkillEnvelope<CartSuggestionSet> {
        val deterministic = FoodPlanningToolkit.shoppingForPlan(recipes, entries, inventory)
        if (recipes.isEmpty() || entries.isEmpty()) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.CART_BUILDER,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.2,
                warnings = listOf("No recipe plan context was provided; ask for a meal plan first."),
                proposalIntent = AiSkillProposalIntent.ASK_CLARIFICATION,
                contract = contract,
                provenance = provenance,
            )
        }
        val existingNames = existingCart.filter { it.status != GroceryStatus.BOUGHT }.map { it.name.cleanFoodToken() }
        val merged = deterministic.items.filterNot { item -> existingNames.any { it.foodOverlaps(item.name.cleanFoodToken()) } }
        val draft = GroceryDraft(merged)
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.CART_BUILDER,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = CartSuggestionSet(
                draft = draft,
                reasons = merged.associate { it.name to (it.notes.ifBlank { "Missing ingredient from meal plan." }) },
                contract = contract,
                provenance = provenance,
            ),
            confidence = 0.8,
            warnings = listOf(
                "No silent deletion: existing cart rows are preserved and duplicates are omitted from suggestions only.",
                "No direct-write: all cart suggestions remain proposals.",
            ),
            contract = contract,
            provenance = provenance,
            proposalIntent = AiSkillProposalIntent.REVIEW_DRAFT,
        )
    }
}

data class RecipePersonalizationProposal(
    val originalTitle: String,
    val personalizedTitle: String,
    val changedIngredients: String,
    val explanation: String,
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

object RecipePersonalizeSkillContract {
    fun personalize(
        recipe: Recipe,
        goal: String,
        contract: AiSkillContractMeta = AiSkillContractMeta(),
        provenance: AiSkillProvenance = AiSkillProvenance(),
    ): AiSkillEnvelope<RecipePersonalizationProposal> {
        if (goal.contains("dangerous", ignoreCase = true) || goal.contains("drug", ignoreCase = true)) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.RECIPE_PERSONALIZE,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.0,
                warnings = listOf(
                    "Unsafe recipe request blocked.",
                    "Offer a safe substitution instead.",
                ),
                proposalIntent = AiSkillProposalIntent.BLOCKED,
                contract = contract,
                provenance = provenance,
            )
        }
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
                contract = contract,
                provenance = provenance,
            ),
            confidence = 0.62,
            warnings = if (goal.isBlank()) listOf(
                "Goal was empty; fallback variant keeps ingredients unchanged for safety review.",
                "No direct-write: personalize changes are proposals.",
            ) else listOf(
                "Nutrition remains estimated until USDA/Open Food Facts/provider lookup verifies it.",
                "No direct-write: personalize changes are proposals.",
            ),
            contract = contract,
            provenance = provenance,
            proposalIntent = AiSkillProposalIntent.REVIEW_DRAFT,
        )
    }
}

data class ReceiptParseInput(
    val rawText: String,
    val promptContext: String = "",
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

data class ReceiptParseProposal(
    val draft: ReceiptDraft,
    val cleanedLines: List<String>,
    val merchantHint: String,
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

object ReceiptParseSkillContract {
    fun parse(input: ReceiptParseInput): AiSkillEnvelope<ReceiptParseProposal> {
        if (!input.provenance.providerAvailable) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.RECEIPT_PARSE,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.0,
                warnings = listOf("Receipt parse provider unavailable; accept upload fallback or retry when online."),
                proposalIntent = AiSkillProposalIntent.BLOCKED,
                contract = input.contract,
                provenance = input.provenance,
            )
        }
        if (input.rawText.isBlank()) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.RECEIPT_PARSE,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.0,
                warnings = listOf("Receipt text was empty; ask for a clearer picture or pasted lines."),
                proposalIntent = AiSkillProposalIntent.ASK_CLARIFICATION,
                contract = input.contract,
                provenance = input.provenance,
            )
        }

        val parseResult = DeterministicReceiptParser.tryParse(
            raw = input.rawText,
            promptContext = input.promptContext.ifBlank { null },
        )
        if (parseResult == null) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.RECEIPT_PARSE,
                status = AiSkillStatus.NEEDS_REVIEW,
                proposal = null,
                confidence = 0.0,
                warnings = listOf("Could not parse receipt text with current deterministic rules; ask for clearer visible lines."),
                proposalIntent = AiSkillProposalIntent.ASK_CLARIFICATION,
            )
        }

        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.RECEIPT_PARSE,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = ReceiptParseProposal(
                draft = parseResult.draft,
                cleanedLines = parseResult.cleanedLines,
                merchantHint = parseResult.draft.merchant.ifBlank { "receipt_text" },
                contract = input.contract,
                provenance = input.provenance,
            ),
            confidence = (0.6 + (parseResult.cleanedLines.size / 20.0)).coerceAtMost(0.95),
            warnings = listOf(
                "Deterministic parser output; review before applying.",
                "No direct-write: parsed items are reviewable proposals only.",
            ),
            contract = input.contract,
            provenance = input.provenance,
            proposalIntent = AiSkillProposalIntent.REVIEW_DRAFT,
        )
    }
}

data class NutritionEstimateInput(
    val text: String,
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

data class NutritionEstimateProposal(
    val sourceText: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

object NutritionEstimateSkillContract {
    fun estimate(input: NutritionEstimateInput): AiSkillEnvelope<NutritionEstimateProposal> {
        val normalized = input.text.trim()
        if (!input.provenance.providerAvailable) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.NUTRITION_ESTIMATE,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.0,
                warnings = listOf("Nutrition estimation is blocked offline; ask for a clearer serving phrase."),
                proposalIntent = AiSkillProposalIntent.BLOCKED,
                contract = input.contract,
                provenance = input.provenance,
            )
        }
        if (normalized.isBlank() || !containsNutritionTrigger(normalized)) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.NUTRITION_ESTIMATE,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.0,
                warnings = listOf("Need a food/portion phrase to generate a safe estimate."),
                proposalIntent = AiSkillProposalIntent.ASK_CLARIFICATION,
                contract = input.contract,
                provenance = input.provenance,
            )
        }
        val ambiguous = normalized.contains(Regex("\\b(how much|estimate|rough|maybe|guess|any|several)\\b", RegexOption.IGNORE_CASE))
        if (ambiguous) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.NUTRITION_ESTIMATE,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.18,
                warnings = listOf("Portion is ambiguous; ask for quantity before estimating."),
                proposalIntent = AiSkillProposalIntent.ASK_CLARIFICATION,
                contract = input.contract,
                provenance = input.provenance,
            )
        }

        val estimate = estimateNutrition(normalized)
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.NUTRITION_ESTIMATE,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = NutritionEstimateProposal(
                sourceText = normalized,
                calories = estimate.calories,
                proteinGrams = estimate.protein,
                carbsGrams = estimate.carbs,
                fatGrams = estimate.fat,
                contract = input.contract,
                provenance = input.provenance,
            ),
            confidence = 0.72,
            warnings = listOf(
                "Estimate is heuristic; confirm before saving.",
                "No direct-write: estimated nutrition is a review-only proposal.",
            ),
            contract = input.contract,
            provenance = input.provenance,
            proposalIntent = AiSkillProposalIntent.REVIEW_DRAFT,
        )
    }

    private fun containsNutritionTrigger(raw: String): Boolean =
        listOf("meal", "eat", "recipe", "bowl", "chicken", "egg", "rice", "salad", "pizza", "yogurt", "smoothie", "sandwich", "beans", "pasta").any { it in raw.lowercase() }

    private fun estimateNutrition(raw: String): NutritionEstimate {
        val lower = raw.lowercase()
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

        return NutritionEstimate(
            calories = (calories.coerceIn(120, 1400) / 10.0).roundToInt() * 10,
            protein = protein.coerceIn(0.0, 90.0),
            carbs = carbs.coerceIn(0.0, 180.0),
            fat = fat.coerceIn(0.0, 80.0),
        )
    }

    private data class NutritionEstimate(
        val calories: Int,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
    )
}

data class CookingCoachStep(
    val recipeTitle: String,
    val currentStep: Int,
    val instruction: String,
    val nextAction: String,
    val timerMinutes: Int?,
    val contract: AiSkillContractMeta = AiSkillContractMeta(),
    val provenance: AiSkillProvenance = AiSkillProvenance(),
)

object CookingCoachSkillContract {
    fun nextStep(
        recipe: Recipe,
        currentStep: Int,
        question: String = "",
        contract: AiSkillContractMeta = AiSkillContractMeta(),
        provenance: AiSkillProvenance = AiSkillProvenance(),
    ): AiSkillEnvelope<CookingCoachStep> {
        val steps = recipe.steps.split('.', '\n').map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf(recipe.steps.ifBlank { "Review recipe before cooking." }) }
        if (recipe.steps.isBlank()) {
            return AiSkillEnvelope(
                skill = WonderFoodAiSkillId.COOKING_COACH,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                proposal = null,
                confidence = 0.0,
                warnings = listOf("Recipe has no actionable steps; ask for a structured recipe or safe substitutes."),
                proposalIntent = AiSkillProposalIntent.ASK_CLARIFICATION,
                contract = contract,
                provenance = provenance,
            )
        }
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
                contract = contract,
                provenance = provenance,
            ),
            confidence = 0.7,
            contract = contract,
            provenance = provenance,
            proposalIntent = AiSkillProposalIntent.REVIEW_DRAFT,
            warnings = listOf("No direct-write: coaching suggestions are not persisted automatically."),
        )
    }
}

object MealPlanSkillContract {
    fun propose(
        memory: HouseholdUiMemory,
        today: LocalDate = LocalDate.now(),
        contract: AiSkillContractMeta = AiSkillContractMeta(),
        provenance: AiSkillProvenance = AiSkillProvenance(),
    ): AiSkillEnvelope<CompositeDraft> {
        val profiles = HouseholdProfileParser.parse(memory.preferences)
        val allergyTerms = profiles.flatMap { it.allergies.map { allergy -> allergy.lowercase() } }.toSet()
        if (allergyTerms.isNotEmpty()) {
            val safeRecipeCount = memory.recipes.count { recipe ->
                allergyTerms.none { allergy ->
                    listOf(recipe.title, recipe.ingredients, recipe.tags).any { it.contains(allergy, ignoreCase = true) }
                }
            }
            if (safeRecipeCount == 0) {
                return AiSkillEnvelope(
                    skill = WonderFoodAiSkillId.MEAL_PLAN,
                status = AiSkillStatus.NEEDS_CLARIFICATION,
                    proposal = null,
                    confidence = 0.0,
                    warnings = listOf(
                        "Hard allergy exclusions removed all candidate recipes.",
                        "No direct-write: blocked until user updates recipes or preference constraints.",
                    ),
                    proposalIntent = AiSkillProposalIntent.BLOCKED,
                    contract = contract,
                    provenance = provenance,
                )
            }
        }
        return AiSkillEnvelope(
            skill = WonderFoodAiSkillId.MEAL_PLAN,
            status = AiSkillStatus.NEEDS_REVIEW,
            proposal = FoodPlanningToolkit.pantryFirstPlan(memory, today.toEpochDay()),
            confidence = 0.74,
            contract = contract,
            provenance = provenance,
            proposalIntent = AiSkillProposalIntent.REVIEW_DRAFT,
            warnings = buildList {
                add("Planner output is reviewable and biased toward pantry/on-hand foods.")
                if (profiles.any { it.allergies.isNotEmpty() }) add("Hard allergy exclusions must be enforced before saving.")
                add("No direct-write: plan output is review-only.")
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
