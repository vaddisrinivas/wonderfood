package com.wonderfood.app.data

import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.PurchaseLineDisposition
import com.wonderfood.core.model.household.SpendingCategory
import com.wonderfood.core.model.household.resolvedSpendingCategory

data class CanonicalRecentSpendingItem(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    companion object {
        fun fromSnapshot(snapshot: HouseholdSnapshot?, limit: Int = 5): List<CanonicalRecentSpendingItem> {
            snapshot ?: return emptyList()
            val purchasesById = snapshot.purchases.associateBy { it.metadata.id }
            val itemsById = snapshot.items.associateBy { it.metadata.id }
            return snapshot.purchaseLines
                .asSequence()
                .filter { it.metadata.archivedAt == null }
                .filter { it.disposition != PurchaseLineDisposition.IGNORED }
                .mapNotNull { line ->
                    val purchase = purchasesById[line.purchaseId] ?: return@mapNotNull null
                    val amount = line.finalAmount ?: line.lineSubtotal ?: line.unitPrice
                    CanonicalRecentSpendingItem(
                        id = line.metadata.id.value,
                        title = line.displayName,
                        subtitle = listOfNotNull(
                            amount?.minorUnits?.receiptMoney(amount.currencyCode),
                            line.spendingCategoryLabel(line.itemId?.let(itemsById::get)?.kind),
                            purchase.paymentNote.merchantLabel(),
                        ).joinToString("  "),
                    ) to purchase.occurredAt.epochMillis
                }
                .sortedWith(
                    compareByDescending<Pair<CanonicalRecentSpendingItem, Long>> { it.second }
                        .thenBy { it.first.title.lowercase() },
                )
                .take(limit)
                .map { it.first }
                .toList()
        }
    }
}

private fun String?.merchantLabel(): String? =
    this
        ?.lineSequence()
        ?.firstNotNullOfOrNull { line ->
            line.substringAfter("Merchant:", missingDelimiterValue = "")
                .trim()
                .takeIf { it.isNotBlank() }
        }

private fun com.wonderfood.core.model.household.PurchaseLine.spendingCategoryLabel(itemKind: ItemKind?): String? =
    spendCategory
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: resolvedSpendingCategory(itemKind).takeIf { it == SpendingCategory.UNCERTAIN }?.label

private val SpendingCategory.label: String
    get() = name.lowercase().replace('_', ' ')
