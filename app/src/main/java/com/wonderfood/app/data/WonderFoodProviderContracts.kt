package com.wonderfood.app.data

import java.time.Duration

interface RecipeLookupProvider {
    val providerName: String
    fun search(query: RecipeLookupQuery): List<ProviderResult<RecipeDraft>>
}

interface BarcodePackageLookupProvider {
    val providerName: String
    fun lookup(query: BarcodeLookupQuery): ProviderResult<FoodCandidate>?
}

data class RecipeLookupQuery(
    val text: String = "",
    val ingredientNames: List<String> = emptyList(),
    val category: String = "",
    val cuisine: String = "",
)

data class BarcodeLookupQuery(
    val barcode: String,
)

data class ProviderAttribution(
    val provider: String,
    val sourceUrl: String,
    val externalId: String,
    val licenseNote: String,
)

data class ProviderCachePolicy(
    val ttlMillis: Long,
    val offlineReadable: Boolean,
) {
    companion object {
        val LOOKUP_DEFAULT = ProviderCachePolicy(
            ttlMillis = Duration.ofDays(7).toMillis(),
            offlineReadable = true,
        )
    }
}

data class ProviderResult<T>(
    val value: T,
    val attribution: ProviderAttribution,
    val confidence: Double,
    val cachePolicy: ProviderCachePolicy = ProviderCachePolicy.LOOKUP_DEFAULT,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(confidence in 0.0..1.0) { "Provider confidence must be between 0 and 1." }
    }
}

object TheMealDbRecipeLookupProvider : RecipeLookupProvider {
    override val providerName: String = "themealdb"

    override fun search(query: RecipeLookupQuery): List<ProviderResult<RecipeDraft>> {
        val terms = (listOf(query.text, query.category, query.cuisine) + query.ingredientNames)
            .joinToString(" ")
            .lowercase()
        return fixtureMeals
            .filter { meal ->
                terms.isBlank() || meal.searchTerms.any { it in terms || terms in it }
            }
            .map { meal -> meal.toProviderResult() }
            .take(12)
    }

    fun fromMeal(
        id: String,
        name: String,
        category: String,
        area: String,
        instructions: String,
        sourceUrl: String,
        ingredients: List<Pair<String, String>>,
    ): ProviderResult<RecipeDraft> = TheMealDbMeal(
        id = id,
        name = name,
        category = category,
        area = area,
        instructions = instructions,
        sourceUrl = sourceUrl,
        ingredients = ingredients,
        searchTerms = listOf(name.lowercase(), category.lowercase(), area.lowercase()) + ingredients.map { it.first.lowercase() },
    ).toProviderResult()

    private fun TheMealDbMeal.toProviderResult(): ProviderResult<RecipeDraft> {
        val ingredientText = ingredients
            .filter { (ingredient, _) -> ingredient.isNotBlank() }
            .joinToString("\n") { (ingredient, measure) ->
                listOf(measure.trim(), ingredient.trim()).filter { it.isNotBlank() }.joinToString(" ")
            }
        return ProviderResult(
            value = RecipeDraft(
                titleText = name,
                ingredientsText = ingredientText,
                stepsText = instructions,
                tags = listOf(category, area, "themealdb").filter { it.isNotBlank() }.joinToString(", "),
            ),
            attribution = ProviderAttribution(
                provider = providerName,
                sourceUrl = sourceUrl.ifBlank { "https://www.themealdb.com/meal/$id" },
                externalId = id,
                licenseNote = "Free public recipe lookup; verify coverage and terms before large catalog claims.",
            ),
            confidence = 0.84,
            warnings = listOf("Imported recipe is a preview; user must review ingredients, servings, and source before saving."),
        )
    }

    private data class TheMealDbMeal(
        val id: String,
        val name: String,
        val category: String,
        val area: String,
        val instructions: String,
        val sourceUrl: String,
        val ingredients: List<Pair<String, String>>,
        val searchTerms: List<String>,
    )

    private val fixtureMeals = listOf(
        TheMealDbMeal(
            id = "52771",
            name = "Spicy Arrabiata Penne",
            category = "Vegetarian",
            area = "Italian",
            instructions = "Boil pasta. Simmer tomato sauce with garlic and chili. Toss and serve.",
            sourceUrl = "https://www.themealdb.com/meal/52771",
            ingredients = listOf("penne" to "1 lb", "tomatoes" to "1 can", "garlic" to "3 cloves", "chili flakes" to "1 tsp"),
            searchTerms = listOf("arrabiata", "pasta", "penne", "tomatoes", "italian", "vegetarian"),
        ),
        TheMealDbMeal(
            id = "52806",
            name = "Chicken Handi",
            category = "Chicken",
            area = "Indian",
            instructions = "Cook chicken with onion, tomato, yogurt, and spices until tender.",
            sourceUrl = "https://www.themealdb.com/meal/52806",
            ingredients = listOf("chicken" to "500 g", "yogurt" to "1 cup", "tomatoes" to "2", "garam masala" to "1 tsp"),
            searchTerms = listOf("chicken", "indian", "yogurt", "tomato", "handi"),
        ),
    )
}

class OpenFoodFactsPackageLookupProvider(
    private val nutritionProvider: NutritionLookupProvider = OpenFoodFactsNutritionProvider,
) : BarcodePackageLookupProvider {
    override val providerName: String = "open_food_facts"

    override fun lookup(query: BarcodeLookupQuery): ProviderResult<FoodCandidate>? {
        val barcode = TrustedFoodLookup.normalizeBarcode(query.barcode) ?: return null
        val facts = nutritionProvider.lookup(NutritionLookupRequest(barcode = barcode)) ?: return null
        return ProviderResult(
            value = FoodCandidate(
                name = facts.displayName,
                quantity = facts.servingText,
                servingText = facts.servingText,
                calories = facts.calories,
                proteinGrams = facts.proteinGrams,
                carbsGrams = facts.carbsGrams,
                fatGrams = facts.fatGrams,
                nutritionSource = facts.provider,
                evidence = facts.providerReference,
                confidence = facts.confidence,
                warnings = buildList {
                    if (facts.ingredientsText.isNotBlank()) add("Ingredients: ${facts.ingredientsText}")
                    if (facts.allergensText.isNotBlank()) add("Allergens: ${facts.allergensText}")
                    add("Package lookup is provider-sourced; verify label and serving before health export.")
                },
            ),
            attribution = ProviderAttribution(
                provider = providerName,
                sourceUrl = facts.providerReference,
                externalId = barcode,
                licenseNote = "Open Food Facts open product data with incomplete coverage possible.",
            ),
            confidence = facts.confidence,
        )
    }
}
