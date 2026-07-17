package com.wonderfood.app.testing

import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.GroceryItem
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.LinkActionDraft
import com.wonderfood.app.data.MealLog
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlan
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntry
import com.wonderfood.app.data.MealPlanEntryStatus
import com.wonderfood.app.data.MealPlanStatus
import com.wonderfood.app.data.Recipe
import com.wonderfood.app.data.RecipeDraft
import java.time.LocalDate
import java.time.ZoneOffset

class InMemoryFoodMemoryRepository(
    initialMemory: FoodMemory = FoodMemory(),
    private val clock: DeterministicTestClock = DeterministicTestClock(),
    private val ids: DeterministicLongIdSource = DeterministicLongIdSource(),
) {
    private var memory: FoodMemory = initialMemory

    fun readMemory(): FoodMemory = memory

    fun replaceMemory(next: FoodMemory) {
        memory = next
    }

    fun applyDraft(draft: FoodDraft): FoodMemory {
        val now = clock.millis()
        memory = when (draft) {
            is CompositeDraft -> {
                draft.drafts.forEach { applyDraft(it) }
                memory
            }
            is InventoryDraft -> memory.copy(
                inventory = memory.inventory + draft.items.map { item ->
                    InventoryItem(
                        id = ids.nextId(),
                        name = item.name,
                        quantity = item.quantity,
                        zone = item.zone,
                        category = item.category,
                        servingText = item.servingText,
                        calories = item.calories,
                        proteinGrams = item.proteinGrams,
                        carbsGrams = item.carbsGrams,
                        fatGrams = item.fatGrams,
                        nutritionSource = item.nutritionSource,
                        notes = item.notes,
                        imageUri = item.imageUri,
                        expiresAtMillis = item.expiresAtMillis,
                        source = TestFoodSeeds.TEST_SOURCE,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        imageUrl = item.imageUrl,
                    )
                },
            )
            is GroceryDraft -> memory.copy(
                groceries = memory.groceries + draft.items.map { item ->
                    GroceryItem(
                        id = ids.nextId(),
                        name = item.name,
                        quantity = item.quantity,
                        status = GroceryStatus.NEEDED,
                        category = item.category,
                        servingText = item.servingText,
                        calories = item.calories,
                        proteinGrams = item.proteinGrams,
                        carbsGrams = item.carbsGrams,
                        fatGrams = item.fatGrams,
                        nutritionSource = item.nutritionSource,
                        source = TestFoodSeeds.TEST_SOURCE,
                        imageUri = item.imageUri,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        imageUrl = item.imageUrl,
                    )
                },
            )
            is LinkActionDraft -> memory
            is RecipeDraft -> memory.copy(
                recipes = memory.recipes + Recipe(
                    id = ids.nextId(),
                    title = draft.titleText,
                    ingredients = draft.ingredientsText,
                    steps = draft.stepsText,
                    servings = draft.servings,
                    prepMinutes = draft.prepMinutes,
                    tags = draft.tags,
                    rating = null,
                    imageUri = draft.imageUri,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    imageUrl = draft.imageUrl,
                ),
            )
            is MealLogDraft -> memory.copy(
                mealLogs = memory.mealLogs + MealLog(
                    id = ids.nextId(),
                    title = draft.titleText,
                    calories = draft.calories,
                    proteinGrams = draft.proteinGrams,
                    carbsGrams = draft.carbsGrams,
                    fatGrams = draft.fatGrams,
                    mealSlot = draft.mealSlot,
                    usedItemsText = draft.usedItemsText,
                    loggedDateEpochDay = draft.loggedDateEpochDay ?: todayEpochDay(),
                    source = draft.source,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
            is MealPlanDraft -> {
                val planId = ids.nextId()
                memory.copy(
                    mealPlans = memory.mealPlans + MealPlan(
                        id = planId,
                        title = draft.titleText,
                        daysText = draft.daysText,
                        groceryHint = draft.groceryHint,
                        status = MealPlanStatus.DRAFT,
                        startDateEpochDay = draft.startDateEpochDay ?: todayEpochDay(),
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    ),
                    mealPlanEntries = memory.mealPlanEntries + draft.entries.map { entry ->
                        MealPlanEntry(
                            id = ids.nextId(),
                            planId = planId,
                            dateEpochDay = (draft.startDateEpochDay ?: todayEpochDay()) + entry.dayOffset,
                            slot = entry.slot,
                            title = entry.title,
                            calorieTarget = entry.calorieTarget,
                            status = MealPlanEntryStatus.PLANNED,
                            createdAtMillis = now,
                            updatedAtMillis = now,
                        )
                    },
                )
            }
        }
        return memory
    }

    private fun todayEpochDay(): Long =
        LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toEpochDay()
}
