package com.wonderfood.app.data

import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind

data class CanonicalHouseholdSearchItem(
    val id: String,
    val name: String,
    val kind: ItemKind,
    val category: String?,
    val brand: String?,
) {
    val subtitle: String
        get() = buildList {
            add("Canonical")
            val kindLabel = kind.name.lowercase().replaceFirstChar { it.uppercase() }
            add(kindLabel)
            category
                ?.takeIf { it.isNotBlank() && !it.equals(kindLabel, ignoreCase = true) }
                ?.let(::add)
            brand?.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString(" - ")

    companion object {
        fun from(item: Item): CanonicalHouseholdSearchItem =
            CanonicalHouseholdSearchItem(
                id = item.metadata.id.value,
                name = item.name,
                kind = item.kind,
                category = item.category,
                brand = item.brand,
            )
    }
}
