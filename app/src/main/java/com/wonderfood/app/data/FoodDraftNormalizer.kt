package com.wonderfood.app.data

import java.util.Locale

object FoodDraftNormalizer {
    fun normalize(draft: FoodDraft): FoodDraft =
        when (draft) {
            is CompositeDraft -> draft.copy(drafts = draft.drafts.map(::normalize))
            is GroceryDraft -> draft.copy(items = draft.items.map(::normalizeCandidate))
            is InventoryDraft -> draft.copy(items = draft.items.map(::normalizeCandidate))
            is ReceiptDraft -> draft.copy(
                merchant = draft.merchant.trim(),
                storeLocation = draft.storeLocation.trim(),
                currencyCode = draft.currencyCode.trim().uppercase(Locale.US).take(3).ifBlank { "USD" },
                subtotalCents = draft.subtotalCents?.takeIf { it >= 0 },
                taxCents = draft.taxCents?.takeIf { it >= 0 },
                totalCents = draft.totalCents?.takeIf { it >= 0 },
                rawText = draft.rawText.trim(),
                items = draft.items.map { item ->
                    FoodIntakeEnricher.normalizeReceiptItem(
                        item.copy(
                            food = normalizeCandidate(item.food),
                            linePriceCents = item.linePriceCents?.takeIf { it >= 0 },
                        ),
                        draft.purchasedAtMillis,
                    )
                },
            )
            is LinkActionDraft -> draft.copy(
                actionType = draft.actionType.trim().lowercase(Locale.US).replace('-', '_'),
                targetKind = draft.targetKind.trim().lowercase(Locale.US).replace('-', '_'),
                targetRef = draft.targetRef.trim(),
                displayName = draft.displayName.cleanTitle(),
                fields = draft.fields
                    .filterKeys { it.isNotBlank() }
                    .mapKeys { it.key.trim().lowercase(Locale.US).replace('-', '_') }
                    .mapValues { it.value.trim() }
                    .filterValues { it.isNotBlank() },
            )
            is MealLogDraft -> draft.copy(titleText = draft.titleText.cleanTitle())
            is MealPlanDraft -> draft.copy(
                titleText = draft.titleText.cleanTitle().ifBlank { "Meal plan" },
                entries = draft.entries.map { entry -> entry.copy(title = entry.title.cleanTitle()) },
            )
            is RecipeDraft -> draft.copy(titleText = draft.titleText.cleanTitle())
        }

    fun normalizeCandidate(candidate: FoodCandidate): FoodCandidate {
        val cleanName = candidate.name.cleanTitle()
        return FoodIntakeEnricher.normalizeCandidate(candidate.copy(
            name = cleanName,
            quantity = candidate.quantity.trim(),
            evidence = candidate.evidence.trim(),
            zoneSource = candidate.zoneSource.trim(),
            expirySource = candidate.expirySource.trim(),
        ))
    }

    private fun String.cleanTitle(): String =
        trim()
            .replace(Regex("""\s+"""), " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { first ->
                    if (first.isLowerCase()) first.titlecase(Locale.US) else first.toString()
                }
            }
}
