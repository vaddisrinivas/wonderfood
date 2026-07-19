package com.wonderfood.app.sync

import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntryDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import com.wonderfood.core.model.FoodId
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.MealSlot as CoreMealSlot
import com.wonderfood.core.model.NutritionSnapshot
import com.wonderfood.core.model.ShoppingItemStatus
import com.wonderfood.core.model.StockLot
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.WonderFoodSnapshot
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

object LegacySnapshotDraftImporter {
    fun toDraft(snapshot: WonderFoodSnapshot): FoodDraft? {
        val nutritionBySubject = snapshot.nutritionSnapshots
            .filter { it.subject.id.isNotBlank() }
            .associateBy { it.subject.id }
        val pagesById = snapshot.pages.associateBy { it.id }
        val foodsById = snapshot.foods.associateBy { it.id }
        val stockByFood = snapshot.stockLots
            .filter { it.status.importable }
            .groupBy { it.foodId }

        val inventoryCandidates = snapshot.foods
            .filter { food -> food.status.name != "ARCHIVED" }
            .map { food ->
                val primaryStock = stockByFood[food.id]?.firstOrNull()
                val nutrition = nutritionBySubject[food.id.value]
                food.toFoodCandidate(primaryStock, nutrition)
            }

        val groceryCandidates = snapshot.shoppingItems
            .filter { it.status == ShoppingItemStatus.NEEDED || it.status == ShoppingItemStatus.IN_CART }
            .mapNotNull { item ->
                val food = item.foodId?.let(foodsById::get)
                val name = pagesById[item.pageId]?.title
                    ?: food?.name
                    ?: item.reason?.substringBefore(" for ")?.takeIf(String::isNotBlank)
                name?.let {
                    FoodCandidate(
                        name = it,
                        quantity = item.quantity.displayText(),
                        category = categorizeFood(it),
                        confidence = item.confidence.score ?: 0.65,
                        evidence = "Google Sheets import: ${item.id.value}",
                    )
                }
            }

        val recipeDrafts = snapshot.recipes
            .filter { recipe -> recipe.status.name != "ARCHIVED" }
            .map { recipe ->
                RecipeDraft(
                    titleText = recipe.title,
                    ingredientsText = recipe.ingredients.joinToString("\n") { ingredient ->
                        buildList {
                            add(ingredient.quantity.displayText())
                            add(ingredient.displayName)
                            ingredient.preparation?.takeIf(String::isNotBlank)?.let(::add)
                        }.filter(String::isNotBlank).joinToString(" ")
                    }.ifBlank { recipe.description.orEmpty() },
                    stepsText = recipe.steps
                        .sortedBy { it.order }
                        .joinToString("\n") { it.instruction }
                        .ifBlank { recipe.description.orEmpty() },
                    servings = recipe.servings.amount?.roundToInt(),
                    prepMinutes = recipe.prepMinutes,
                )
            }

        val mealLogDrafts = snapshot.mealLogs
            .filter { it.status.name != "ARCHIVED" }
            .map { log ->
                val nutrition = log.nutritionSnapshotIds
                    .firstNotNullOfOrNull { id -> snapshot.nutritionSnapshots.firstOrNull { it.id == id } }
                MealLogDraft(
                    titleText = pagesById[log.pageId]?.title
                        ?: log.recipeIds.firstOrNull()?.value
                        ?.let { recipeId -> snapshot.recipes.firstOrNull { it.id.value == recipeId }?.title }
                        ?: log.foodIds.firstOrNull()?.let { foodId -> foodsById[foodId]?.name }
                        ?: "Imported meal",
                    calories = nutrition?.values?.energyKcal?.roundToInt(),
                    proteinGrams = nutrition?.values?.proteinGrams,
                    carbsGrams = nutrition?.values?.carbohydrateGrams,
                    fatGrams = nutrition?.values?.fatGrams,
                    mealSlot = log.mealSlot.toLegacySlot(),
                    usedItemsText = log.foodIds
                        .mapNotNull { foodId -> foodsById[foodId]?.name }
                        .joinToString(", "),
                    loggedDateEpochDay = log.occurredAt.value.substringBefore("T").toEpochDayOrNull(),
                    source = "google_sheets_import",
                )
            }

        val mealPlanDrafts = snapshot.mealPlans
            .filter { it.status.name != "ARCHIVED" }
            .map { plan ->
                MealPlanDraft(
                    titleText = plan.name,
                    daysText = plan.entries
                        .sortedBy { it.date.value + it.mealSlot.name }
                        .joinToString("\n") { entry ->
                            "${entry.date.value} ${entry.mealSlot.toLegacySlot().label}: ${entry.recipeId?.value?.let { recipeId -> snapshot.recipes.firstOrNull { it.id.value == recipeId }?.title } ?: entry.foodId?.let { foodId -> foodsById[foodId]?.name } ?: "Planned meal"}"
                        },
                    groceryHint = "",
                    entries = plan.entries.map { entry ->
                        MealPlanEntryDraft(
                            dayOffset = 0,
                            slot = entry.mealSlot.toLegacySlot(),
                            title = entry.recipeId?.value
                                ?.let { recipeId -> snapshot.recipes.firstOrNull { it.id.value == recipeId }?.title }
                                ?: entry.foodId?.let { foodId -> foodsById[foodId]?.name }
                                ?: "Imported planned meal",
                            calorieTarget = null,
                        )
                    },
                    startDateEpochDay = plan.startsOn.value.toEpochDayOrNull(),
                )
            }

        val drafts = buildList {
            if (inventoryCandidates.isNotEmpty()) add(InventoryDraft(inventoryCandidates))
            if (groceryCandidates.isNotEmpty()) add(GroceryDraft(groceryCandidates))
            addAll(recipeDrafts)
            addAll(mealLogDrafts)
            addAll(mealPlanDrafts)
        }
        return when (drafts.size) {
            0 -> null
            1 -> drafts.single()
            else -> CompositeDraft(drafts)
        }
    }

    private fun com.wonderfood.core.model.Food.toFoodCandidate(
        stockLot: StockLot?,
        nutrition: NutritionSnapshot?,
    ): FoodCandidate =
        FoodCandidate(
            name = name,
            quantity = stockLot?.quantity?.displayText().orEmpty(),
            zone = stockLot?.location?.canonicalStorageZone() ?: StorageZone.PANTRY,
            category = categorizeFood(name),
            servingText = nutrition?.basis?.description.orEmpty(),
            calories = nutrition?.values?.energyKcal?.roundToInt(),
            proteinGrams = nutrition?.values?.proteinGrams,
            carbsGrams = nutrition?.values?.carbohydrateGrams,
            fatGrams = nutrition?.values?.fatGrams,
            nutritionSource = nutrition?.source?.label.orEmpty(),
            expiresAtMillis = stockLot?.expiresOn?.value?.toEpochMillisOrNull(),
            confidence = confidence.score ?: 0.65,
            evidence = "Google Sheets import: ${id.value}",
        )

    private val StockLotStatus.importable: Boolean
        get() = this == StockLotStatus.AVAILABLE ||
            this == StockLotStatus.OPENED ||
            this == StockLotStatus.LOW ||
            this == StockLotStatus.RESERVED ||
            this == StockLotStatus.UNKNOWN

    private fun com.wonderfood.core.model.Quantity.displayText(): String =
        buildList {
            amount?.let { add(if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()) }
            unit.takeIf { it != FoodUnit.UNKNOWN }?.let { add(it.name.lowercase().replace('_', ' ')) }
        }.joinToString(" ")

    private fun CoreMealSlot.toLegacySlot(): MealSlot =
        when (this) {
            CoreMealSlot.BREAKFAST -> MealSlot.BREAKFAST
            CoreMealSlot.LUNCH -> MealSlot.LUNCH
            CoreMealSlot.DINNER -> MealSlot.DINNER
            CoreMealSlot.SNACK -> MealSlot.SNACK
            CoreMealSlot.ANYTIME,
            CoreMealSlot.UNKNOWN,
            -> MealSlot.FLEX
        }

    private fun String.toEpochDayOrNull(): Long? =
        runCatching { LocalDate.parse(this).toEpochDay() }.getOrNull()

    private fun String.toEpochMillisOrNull(): Long? =
        runCatching {
            LocalDate.parse(this).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()

    private fun String.canonicalStorageZone(): StorageZone =
        when (trim().lowercase()) {
            "fridge", "refrigerator" -> StorageZone.FRIDGE
            "freezer", "frozen" -> StorageZone.FREEZER
            "pantry", "shelf", "cabinet" -> StorageZone.PANTRY
            else -> classifyStorageZone(this)
        }
}
