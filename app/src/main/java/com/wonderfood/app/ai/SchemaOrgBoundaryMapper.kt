package com.wonderfood.app.ai

import com.wonderfood.app.data.RecipeDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SchemaOrgBoundaryMapping(
    val types: Set<String>,
    val recipe: SchemaOrgRecipeBoundary? = null,
    val howToSteps: List<String> = emptyList(),
    val nutrition: SchemaOrgNutritionBoundary? = null,
    val products: List<SchemaOrgProductBoundary> = emptyList(),
    val offers: List<SchemaOrgOfferBoundary> = emptyList(),
    val organizations: List<SchemaOrgOrganizationBoundary> = emptyList(),
) {
    fun toRecipeDraft(): RecipeDraft? {
        val recipe = recipe ?: return null
        return RecipeDraft(
            titleText = recipe.name,
            ingredientsText = recipe.ingredients.joinToString("\n"),
            stepsText = howToSteps.joinToString("\n"),
            servings = recipe.yieldText.firstNumber(),
            prepMinutes = recipe.prepTimeIso.durationMinutes(),
            tags = "schema-org",
        )
    }
}

data class SchemaOrgRecipeBoundary(
    val name: String,
    val ingredients: List<String>,
    val yieldText: String,
    val prepTimeIso: String,
)

data class SchemaOrgNutritionBoundary(
    val calories: String,
    val protein: String,
    val carbohydrates: String,
    val fat: String,
)

data class SchemaOrgProductBoundary(
    val name: String,
    val sku: String,
    val brand: String,
)

data class SchemaOrgOfferBoundary(
    val price: String,
    val currency: String,
    val availability: String,
)

data class SchemaOrgOrganizationBoundary(
    val name: String,
    val url: String,
)

private fun String.firstNumber(): Int? =
    Regex("""\d+""").find(this)?.value?.toIntOrNull()

private fun String.durationMinutes(): Int? {
    val hours = Regex("""(\d+)H""").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val minutes = Regex("""(\d+)M""").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    return (hours * 60 + minutes).takeIf { it > 0 }
}

object SchemaOrgBoundaryMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun extract(raw: String): SchemaOrgBoundaryMapping? {
        val root = parseJsonLd(raw) ?: return null
        val objects = root.flattenObjects()
        val types = objects.flatMap { it.schemaTypes() }.toSet()
        if (types.none { it in supportedTypes }) return null

        val recipeObject = objects.firstOrNull { "Recipe" in it.schemaTypes() }
        val nutritionObject = recipeObject?.obj("nutrition")
            ?: objects.firstOrNull { "NutritionInformation" in it.schemaTypes() }
        val stepObjects = recipeObject?.array("recipeInstructions").orEmpty()
            .mapNotNull { it as? JsonObject } +
            objects.filter { "HowToStep" in it.schemaTypes() }

        return SchemaOrgBoundaryMapping(
            types = types,
            recipe = recipeObject?.toRecipeBoundary(),
            howToSteps = recipeObject?.array("recipeInstructions").orEmpty().mapNotNull { it.stepText() }
                .ifEmpty { stepObjects.mapNotNull { it.stepText() } },
            nutrition = nutritionObject?.toNutritionBoundary(),
            products = objects.filter { "Product" in it.schemaTypes() }.map { it.toProductBoundary() },
            offers = objects.filter { "Offer" in it.schemaTypes() }.map { it.toOfferBoundary() },
            organizations = objects.filter { "Organization" in it.schemaTypes() }.map { it.toOrganizationBoundary() },
        )
    }

    private fun parseJsonLd(raw: String): JsonElement? {
        val scriptRegex = Regex(
            """<script[^>]+application/ld\+json[^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val payload = scriptRegex.find(raw)?.groupValues?.getOrNull(1)
            ?: raw.substringAfterJsonStart()
            ?: return null
        return runCatching { json.parseToJsonElement(payload.trim()) }.getOrNull()
    }

    private fun String.substringAfterJsonStart(): String? {
        val startObject = indexOf('{')
        val startArray = indexOf('[')
        val start = listOf(startObject, startArray).filter { it >= 0 }.minOrNull() ?: return null
        val end = if (this[start] == '{') lastIndexOf('}') else lastIndexOf(']')
        if (end <= start) return null
        return substring(start, end + 1)
    }

    private fun JsonElement.flattenObjects(): List<JsonObject> =
        when (this) {
            is JsonObject -> {
                val graph = array("@graph")
                listOf(this) + graph.flatMap { it.flattenObjects() } + values.flatMap { it.flattenObjects() }
            }
            is JsonArray -> flatMap { it.flattenObjects() }
            else -> emptyList()
        }.distinctBy { it.toString() }

    private fun JsonObject.toRecipeBoundary(): SchemaOrgRecipeBoundary? {
        val name = text("name", "headline").ifBlank { return null }
        val ingredients = array("recipeIngredient", "ingredients")
            .mapNotNull { it.textValue() }
            .ifEmpty { text("recipeIngredient", "ingredients").lines().map { it.trim() }.filter { it.isNotBlank() } }
        return SchemaOrgRecipeBoundary(
            name = name,
            ingredients = ingredients,
            yieldText = text("recipeYield", "yield"),
            prepTimeIso = text("prepTime", "totalTime"),
        )
    }

    private fun JsonObject.toNutritionBoundary(): SchemaOrgNutritionBoundary =
        SchemaOrgNutritionBoundary(
            calories = text("calories"),
            protein = text("proteinContent"),
            carbohydrates = text("carbohydrateContent"),
            fat = text("fatContent"),
        )

    private fun JsonObject.toProductBoundary(): SchemaOrgProductBoundary =
        SchemaOrgProductBoundary(
            name = text("name"),
            sku = text("sku", "gtin", "gtin13"),
            brand = obj("brand")?.text("name") ?: text("brand"),
        )

    private fun JsonObject.toOfferBoundary(): SchemaOrgOfferBoundary =
        SchemaOrgOfferBoundary(
            price = text("price"),
            currency = text("priceCurrency"),
            availability = text("availability"),
        )

    private fun JsonObject.toOrganizationBoundary(): SchemaOrgOrganizationBoundary =
        SchemaOrgOrganizationBoundary(
            name = text("name"),
            url = text("url"),
        )

    private fun JsonObject.schemaTypes(): List<String> =
        when (val type = this["@type"]) {
            is JsonArray -> type.mapNotNull { it.textValue()?.schemaTypeName() }
            else -> listOfNotNull(type?.textValue()?.schemaTypeName())
        }

    private fun JsonElement.stepText(): String? =
        when (this) {
            is JsonObject -> text("text", "name", "description").takeIf { it.isNotBlank() }
            else -> textValue()
        }

    private fun JsonObject.text(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> get(key)?.textValue()?.trim()?.takeIf { it.isNotBlank() } }.orEmpty()

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.array(vararg keys: String): List<JsonElement> =
        keys.firstNotNullOfOrNull { key ->
            when (val value = get(key)) {
                is JsonArray -> value.jsonArray.toList()
                is JsonObject -> listOf(value.jsonObject)
                is JsonPrimitive -> listOf(value)
                else -> null
            }
        }.orEmpty()

    private fun JsonElement.textValue(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonObject -> text("name", "text", "description")
            JsonNull -> null
            else -> null
        }

    private fun String.schemaTypeName(): String =
        substringAfterLast('/').substringAfterLast('#').trim()

    private val supportedTypes = setOf(
        "Recipe",
        "HowToStep",
        "NutritionInformation",
        "Product",
        "Offer",
        "Organization",
    )
}
