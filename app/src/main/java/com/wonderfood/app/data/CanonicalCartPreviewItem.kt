package com.wonderfood.app.data

import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.ShoppingLineStatus

data class CanonicalCartPreviewItem(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    companion object {
        fun fromSnapshot(snapshot: HouseholdSnapshot?): List<CanonicalCartPreviewItem> =
            snapshot?.let { household ->
                val itemsById = household.items.associateBy { it.metadata.id }
                household.shoppingLines
                    .asSequence()
                    .filter { it.metadata.archivedAt == null }
                    .filter { it.status == ShoppingLineStatus.NEEDED || it.status == ShoppingLineStatus.IN_CART }
                    .sortedWith(
                        compareBy(
                            { it.category.orEmpty().lowercase() },
                            { it.status.ordinal },
                            { it.preferredStore.orEmpty().lowercase() },
                            { it.displayName.lowercase() },
                        ),
                    )
                    .map { line ->
                        val item = line.itemId?.let(itemsById::get)
                        CanonicalCartPreviewItem(
                            id = line.metadata.id.value,
                            title = line.displayName,
                            subtitle = listOf(
                                line.quantity.displayText(),
                                item?.kind?.displayText(),
                                line.category,
                                line.preferredStore?.let { "at $it" },
                                line.reason.name.lowercase().replace('_', ' '),
                                line.estimatedPrice?.let { "est ${it.minorUnits.receiptMoney(it.currencyCode)}" },
                            ).filterNotNull().filter { it.isNotBlank() }.joinToString("  "),
                        )
                    }
                    .toList()
            } ?: emptyList()
    }
}

private fun Quantity.displayText(): String =
    buildList {
        amount?.value?.let(::add)
        unit.takeIf { it != QuantityUnit.UNKNOWN }?.code?.replace('_', ' ')?.let(::add)
    }.joinToString(" ")

private fun ItemKind.displayText(): String =
    name.lowercase().replace('_', ' ')
