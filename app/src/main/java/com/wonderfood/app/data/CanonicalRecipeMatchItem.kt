package com.wonderfood.app.data

import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.InventoryLotStatus
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStatus
import java.math.BigDecimal

data class CanonicalRecipeMatchItem(
    val id: String,
    val title: String,
    val status: String,
    val subtitle: String,
) {
    companion object {
        private val activeLotStatuses = setOf(
            InventoryLotStatus.AVAILABLE,
            InventoryLotStatus.OPENED,
            InventoryLotStatus.RESERVED,
        )

        fun fromSnapshot(snapshot: HouseholdSnapshot?): List<CanonicalRecipeMatchItem> {
            snapshot ?: return emptyList()
            val activeLotsByItem = snapshot.inventoryLots
                .asSequence()
                .filter { it.metadata.archivedAt == null }
                .filter { it.status in activeLotStatuses }
                .groupBy { it.itemId }
            val itemNames = snapshot.items.associate { it.metadata.id to it.name }
            val ingredientsByRecipe = snapshot.recipeIngredients
                .asSequence()
                .filter { it.metadata.archivedAt == null }
                .groupBy { it.recipeId }

            return snapshot.recipes
                .asSequence()
                .filter { it.metadata.archivedAt == null }
                .filter { it.status != RecipeStatus.ARCHIVED }
                .mapNotNull { recipe ->
                    recipe.toMatchItem(ingredientsByRecipe[recipe.metadata.id].orEmpty(), activeLotsByItem, itemNames)
                }
                .sortedWith(
                    compareByDescending<RankedRecipeMatch> { it.rank }
                        .thenBy { it.soonestExpiry ?: "9999-99-99" }
                        .thenBy { it.needCount }
                        .thenBy { it.item.title.lowercase() },
                )
                .map { it.item }
                .toList()
        }

        private fun Recipe.toMatchItem(
            ingredients: List<RecipeIngredient>,
            activeLotsByItem: Map<EntityId, List<InventoryLot>>,
            itemNames: Map<EntityId, String>,
        ): RankedRecipeMatch? {
            val required = ingredients.filterNot { it.optional }
            if (required.isEmpty()) return null
            val evaluations = required.map { ingredient -> ingredient.evaluate(activeLotsByItem) }
            val have = evaluations.filter { it.satisfied }
            val partial = evaluations.filter { !it.satisfied && it.present }
            val need = evaluations.filterNot { it.satisfied }
            val status = when {
                need.isEmpty() -> "Can make"
                have.isNotEmpty() || partial.isNotEmpty() -> "Almost"
                else -> "Need more"
            }
            val needText = need
                .take(3)
                .joinToString { evaluation -> evaluation.ingredient.itemId?.let(itemNames::get) ?: evaluation.ingredient.originalText }
            val soonestExpiry = evaluations.mapNotNull { it.soonestExpiry }.minOrNull()
            val subtitle = buildList {
                add("${have.size}/${required.size} in Kitchen")
                if (needText.isNotBlank()) add("Need $needText")
                soonestExpiry?.let { add("Use first $it") }
                totalMinutes?.let { add("$it min") }
            }.joinToString("  ")
            return RankedRecipeMatch(
                item = CanonicalRecipeMatchItem(
                    id = metadata.id.value,
                    title = name,
                    status = status,
                    subtitle = subtitle,
                ),
                rank = (have.size + partial.size * 0.5) / required.size.toDouble(),
                needCount = need.size,
                soonestExpiry = soonestExpiry,
            )
        }

        private fun RecipeIngredient.evaluate(activeLotsByItem: Map<EntityId, List<InventoryLot>>): IngredientEvaluation {
            val lots = itemId?.let(activeLotsByItem::get).orEmpty()
            val present = lots.isNotEmpty()
            val requiredAmount = quantity.amount?.toBigDecimal()
            val satisfied = present && when {
                requiredAmount == null -> true
                quantity.unit == QuantityUnit.UNKNOWN -> true
                else -> lots.sumKnownQuantity(quantity.unit)?.let { it >= requiredAmount } == true
            }
            return IngredientEvaluation(
                ingredient = this,
                present = present,
                satisfied = satisfied,
                soonestExpiry = lots.mapNotNull { it.expiresOn?.value }.minOrNull(),
            )
        }

        private fun List<InventoryLot>.sumKnownQuantity(unit: QuantityUnit): BigDecimal? {
            val values = filter { it.quantity.unit == unit }
                .mapNotNull { it.quantity.amount?.toBigDecimal() }
            if (values.isEmpty()) return null
            return values.fold(BigDecimal.ZERO, BigDecimal::add)
        }
    }
}

private data class RankedRecipeMatch(
    val item: CanonicalRecipeMatchItem,
    val rank: Double,
    val needCount: Int,
    val soonestExpiry: String?,
)

private data class IngredientEvaluation(
    val ingredient: RecipeIngredient,
    val present: Boolean,
    val satisfied: Boolean,
    val soonestExpiry: String?,
)
