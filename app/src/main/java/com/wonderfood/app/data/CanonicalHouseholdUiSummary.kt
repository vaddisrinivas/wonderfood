package com.wonderfood.app.data

import com.wonderfood.core.model.household.HouseholdSnapshot
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.Money
import com.wonderfood.core.model.household.Purchase
import com.wonderfood.core.model.household.PurchaseLineDisposition
import com.wonderfood.core.model.household.SpendingCategory
import com.wonderfood.core.model.household.calculatedReconciliationDifference
import com.wonderfood.core.model.household.resolvedSpendingCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

data class CanonicalHouseholdUiSummary(
    val items: Int = 0,
    val inventoryLots: Int = 0,
    val shoppingLines: Int = 0,
    val purchases: Int = 0,
    val purchaseLines: Int = 0,
    val thisMonthSpentMinorUnits: Long = 0,
    val lastMonthSpentMinorUnits: Long = 0,
    val weeklyAverageSpentMinorUnits: Long = 0,
    val thisMonthFoodSpentMinorUnits: Long = 0,
    val thisMonthNonFoodSpentMinorUnits: Long = 0,
    val thisMonthTopCategoryLabel: String? = null,
    val thisMonthTopMerchantLabel: String? = null,
    val thisMonthWasteCostMinorUnits: Long = 0,
    val unreconciledPurchaseCount: Int = 0,
    val spendingCurrency: String = "USD",
    val proposals: Int = 0,
) {
    val isEmpty: Boolean
        get() = items == 0 && inventoryLots == 0 && shoppingLines == 0 && purchases == 0 && proposals == 0

    fun label(): String =
        if (isEmpty) {
            "Canonical household repository ready"
        } else {
            buildList {
                add("$items items")
                add("$inventoryLots lots")
                add("$shoppingLines cart lines")
                add("$purchases purchases")
                if (thisMonthSpentMinorUnits != 0L) add("this month ${thisMonthSpentMinorUnits.receiptMoney(spendingCurrency)}")
                if (weeklyAverageSpentMinorUnits != 0L) add("weekly avg ${weeklyAverageSpentMinorUnits.receiptMoney(spendingCurrency)}")
                if (thisMonthFoodSpentMinorUnits != 0L) add("food ${thisMonthFoodSpentMinorUnits.receiptMoney(spendingCurrency)}")
                if (thisMonthNonFoodSpentMinorUnits != 0L) add("household ${thisMonthNonFoodSpentMinorUnits.receiptMoney(spendingCurrency)}")
                thisMonthTopCategoryLabel?.let { add("top category $it") }
                thisMonthTopMerchantLabel?.let { add("top merchant $it") }
                if (thisMonthWasteCostMinorUnits != 0L) add("waste ${thisMonthWasteCostMinorUnits.receiptMoney(spendingCurrency)}")
                if (unreconciledPurchaseCount != 0) add("$unreconciledPurchaseCount unreconciled")
                add("$proposals proposals")
            }.joinToString(" · ")
        }

    fun dashboardLabel(): String? =
        if (isEmpty) {
            null
        } else {
            "$items household · $shoppingLines cart"
        }

    fun spendingDashboardLabel(): String? =
        if (thisMonthSpentMinorUnits == 0L && lastMonthSpentMinorUnits == 0L) {
            null
        } else {
            val thisMonth = thisMonthSpentMinorUnits.receiptMoney(spendingCurrency)
            val lastMonth = lastMonthSpentMinorUnits.receiptMoney(spendingCurrency)
            buildList {
                add("$thisMonth month")
                if (weeklyAverageSpentMinorUnits != 0L) add("weekly avg ${weeklyAverageSpentMinorUnits.receiptMoney(spendingCurrency)}")
                if (thisMonthFoodSpentMinorUnits != 0L) add("food ${thisMonthFoodSpentMinorUnits.receiptMoney(spendingCurrency)}")
                if (thisMonthNonFoodSpentMinorUnits != 0L) add("household ${thisMonthNonFoodSpentMinorUnits.receiptMoney(spendingCurrency)}")
                thisMonthTopCategoryLabel?.let { add("category $it") }
                thisMonthTopMerchantLabel?.let { add("merchant $it") }
                if (thisMonthWasteCostMinorUnits != 0L) add("waste ${thisMonthWasteCostMinorUnits.receiptMoney(spendingCurrency)}")
                if (unreconciledPurchaseCount != 0) add("$unreconciledPurchaseCount unreconciled")
                add("$lastMonth last")
            }.joinToString(" · ")
        }

    companion object {
        fun fromSnapshot(
            snapshot: HouseholdSnapshot?,
            now: Instant = Instant.now(),
        ): CanonicalHouseholdUiSummary {
            if (snapshot == null) return CanonicalHouseholdUiSummary()
            val zone = runCatching { ZoneId.of(snapshot.household.timezone) }.getOrDefault(ZoneId.systemDefault())
            val today = now.atZone(zone).toLocalDate()
            val currentMonth = YearMonth.from(today)
            val previousMonth = currentMonth.minusMonths(1)
            val linesByPurchaseId = snapshot.purchaseLines.groupBy { it.purchaseId }
            val itemsById = snapshot.items.associateBy { it.metadata.id }
            val currency = snapshot.household.defaultCurrency
            val purchaseAmounts = snapshot.purchases
                .mapNotNull { purchase -> purchase.monthAmount(currency, zone, linesByPurchaseId[purchase.metadata.id].orEmpty()) }
            val purchaseMonthById = snapshot.purchases.associate { purchase ->
                purchase.metadata.id to YearMonth.from(Instant.ofEpochMilli(purchase.occurredAt.epochMillis).atZone(zone).toLocalDate())
            }
            val lineAmounts = snapshot.purchaseLines.mapNotNull { line ->
                val month = purchaseMonthById[line.purchaseId] ?: return@mapNotNull null
                line.monthLineAmount(currency, month, line.itemId?.let(itemsById::get)?.kind)
            }
            val merchantAmounts = snapshot.purchases.mapNotNull { purchase ->
                purchase.merchantMonthAmount(currency, zone, linesByPurchaseId[purchase.metadata.id].orEmpty())
            }
            val currentMonthSpent = purchaseAmounts
                .filter { it.month == currentMonth }
                .sumOf { it.money.minorUnits }
            val currentMonthWaste = snapshot.wasteEvents
                .filter { waste -> YearMonth.from(Instant.ofEpochMilli(waste.occurredAt.epochMillis).atZone(zone).toLocalDate()) == currentMonth }
                .mapNotNull { it.estimatedCost?.takeIf { cost -> cost.currencyCode == currency } }
                .sumOf { it.minorUnits }
            return CanonicalHouseholdUiSummary(
                items = snapshot.items.size,
                inventoryLots = snapshot.inventoryLots.size,
                shoppingLines = snapshot.shoppingLines.size,
                purchases = snapshot.purchases.size,
                purchaseLines = snapshot.purchaseLines.size,
                thisMonthSpentMinorUnits = currentMonthSpent,
                lastMonthSpentMinorUnits = purchaseAmounts
                    .filter { it.month == previousMonth }
                    .sumOf { it.money.minorUnits },
                weeklyAverageSpentMinorUnits = currentMonthSpent * 7 / today.dayOfMonth,
                thisMonthFoodSpentMinorUnits = lineAmounts
                    .filter { it.month == currentMonth && it.kind == SpendingLineKind.FOOD }
                    .sumOf { it.money.minorUnits },
                thisMonthNonFoodSpentMinorUnits = lineAmounts
                    .filter { it.month == currentMonth && it.kind == SpendingLineKind.NON_FOOD }
                    .sumOf { it.money.minorUnits },
                thisMonthTopCategoryLabel = lineAmounts
                    .filter { it.month == currentMonth }
                    .topLabelByAmount { it.category },
                thisMonthTopMerchantLabel = merchantAmounts
                    .filter { it.month == currentMonth }
                    .topLabelByAmount { it.merchant },
                thisMonthWasteCostMinorUnits = currentMonthWaste,
                unreconciledPurchaseCount = snapshot.purchases.count { purchase ->
                    purchase.calculatedReconciliationDifference(linesByPurchaseId[purchase.metadata.id].orEmpty())
                        ?.minorUnits
                        ?.let { it != 0L }
                        ?: false
                },
                spendingCurrency = currency,
                proposals = snapshot.proposals.size,
            )
        }
    }
}

private data class MonthlyPurchaseAmount(
    val month: YearMonth,
    val money: Money,
)

private enum class SpendingLineKind { FOOD, NON_FOOD }

private data class MonthlyLineAmount(
    val month: YearMonth,
    val money: Money,
    val kind: SpendingLineKind,
    val category: String?,
)

private data class MonthlyMerchantAmount(
    val month: YearMonth,
    val money: Money,
    val merchant: String,
)

private fun Purchase.monthAmount(
    currency: String,
    zone: ZoneId,
    lines: List<com.wonderfood.core.model.household.PurchaseLine>,
): MonthlyPurchaseAmount? {
    val amount = listOfNotNull(total, subtotal)
        .firstOrNull { it.currencyCode == currency }
        ?: lines
            .filter { it.disposition != PurchaseLineDisposition.IGNORED }
            .mapNotNull { it.finalAmount ?: it.lineSubtotal }
            .filter { it.currencyCode == currency }
            .takeIf { it.isNotEmpty() }
            ?.let { amounts -> Money(amounts.sumOf { it.minorUnits }, currency) }
        ?: return null
    return MonthlyPurchaseAmount(
        month = YearMonth.from(Instant.ofEpochMilli(occurredAt.epochMillis).atZone(zone).toLocalDate()),
        money = amount,
    )
}

private fun com.wonderfood.core.model.household.PurchaseLine.monthLineAmount(
    currency: String,
    month: YearMonth,
    itemKind: ItemKind?,
): MonthlyLineAmount? {
    if (disposition == PurchaseLineDisposition.IGNORED) return null
    val amount = (finalAmount ?: lineSubtotal)?.takeIf { it.currencyCode == currency } ?: return null
    val spendingCategory = resolvedSpendingCategory(itemKind)
    val spendingKind = if (spendingCategory == SpendingCategory.FOOD) SpendingLineKind.FOOD else SpendingLineKind.NON_FOOD
    return MonthlyLineAmount(
        month = month,
        money = amount,
        kind = spendingKind,
        category = spendCategory?.trim()?.takeIf { it.isNotBlank() } ?: spendingCategory.label,
    )
}

private fun Purchase.merchantMonthAmount(
    currency: String,
    zone: ZoneId,
    lines: List<com.wonderfood.core.model.household.PurchaseLine>,
): MonthlyMerchantAmount? {
    val merchant = paymentNote.extractMerchant() ?: return null
    val amount = monthAmount(currency, zone, lines)?.money ?: return null
    return MonthlyMerchantAmount(
        month = YearMonth.from(Instant.ofEpochMilli(occurredAt.epochMillis).atZone(zone).toLocalDate()),
        money = amount,
        merchant = merchant,
    )
}

private fun String?.extractMerchant(): String? =
    this
        ?.lineSequence()
        ?.firstNotNullOfOrNull { line ->
            line.substringAfter("Merchant:", missingDelimiterValue = "")
                .trim()
                .takeIf { it.isNotBlank() }
        }

private fun <T> List<T>.topLabelByAmount(labelFor: (T) -> String?): String? =
    groupBy { labelFor(it)?.trim()?.takeIf(String::isNotBlank) }
        .filterKeys { it != null }
        .mapValues { (_, values) ->
            values.sumOf {
                when (it) {
                    is MonthlyLineAmount -> it.money.minorUnits
                    is MonthlyMerchantAmount -> it.money.minorUnits
                    else -> 0L
                }
            }
        }
        .maxWithOrNull(compareBy<Map.Entry<String?, Long>> { it.value }.thenBy { it.key.orEmpty() })
        ?.key

private val SpendingCategory.label: String
    get() = name.lowercase().replace('_', ' ')
