package com.wonderfood.app.data

import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.RecipeStatus

data class CanonicalSavedRecipeItem(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    companion object {
        fun fromSnapshot(snapshot: HouseholdSnapshot?): List<CanonicalSavedRecipeItem> =
            snapshot
                ?.recipes
                .orEmpty()
                .filter { it.metadata.archivedAt == null }
                .filter { it.status != RecipeStatus.ARCHIVED }
                .sortedWith(compareBy({ it.name.lowercase() }, { it.metadata.id.value }))
                .map { recipe ->
                    CanonicalSavedRecipeItem(
                        id = recipe.metadata.id.value,
                        title = recipe.name,
                        subtitle = listOf(
                            recipe.category,
                            recipe.cuisine,
                            recipe.yield.displayText(),
                            recipe.totalMinutes?.let { "$it min" } ?: recipe.prepMinutes?.let { "$it min prep" },
                            recipe.status.name.lowercase(),
                        ).filterNotNull().filter { it.isNotBlank() }.joinToString("  "),
                    )
                }
    }
}

private fun Quantity.displayText(): String =
    buildList {
        amount?.value?.let(::add)
        unit.takeIf { it != QuantityUnit.UNKNOWN }?.code?.replace('_', ' ')?.let(::add)
    }.joinToString(" ")
