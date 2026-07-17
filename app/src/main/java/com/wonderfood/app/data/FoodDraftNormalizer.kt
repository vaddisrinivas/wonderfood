package com.wonderfood.app.data

import java.util.Locale

object FoodDraftNormalizer {
    fun normalize(draft: FoodDraft): FoodDraft =
        when (draft) {
            is CompositeDraft -> draft.copy(drafts = draft.drafts.map(::normalize))
            is GroceryDraft -> draft.copy(items = draft.items.map(::normalizeCandidate))
            is InventoryDraft -> draft.copy(items = draft.items.map(::normalizeCandidate))
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
        return candidate.copy(
            name = cleanName,
            quantity = candidate.quantity.trim(),
            category = candidate.category.ifBlank { categorizeFood(cleanName) },
            imageUri = candidate.imageUri ?: foodEmojiForName(cleanName),
        )
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
