package com.wonderfood.app.testing

import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodEvent
import com.wonderfood.app.data.FoodEventConfidence
import com.wonderfood.app.data.FoodEventType
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.FoodPreferences
import com.wonderfood.app.data.GroceryItem
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.MealLog
import com.wonderfood.app.data.MealPlan
import com.wonderfood.app.data.MealPlanEntry
import com.wonderfood.app.data.MealPlanEntryStatus
import com.wonderfood.app.data.MealPlanStatus
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.Recipe
import com.wonderfood.app.data.ReceiptCapture
import com.wonderfood.app.data.ReceiptStatus
import com.wonderfood.app.data.StorageZone
import java.time.LocalDate

object TestFoodSeeds {
    const val NOW_MILLIS: Long = 1_768_478_400_000L
    val TODAY_EPOCH_DAY: Long = LocalDate.of(2026, 1, 15).toEpochDay()

    fun candidate(
        name: String = "Generic Eggs",
        quantity: String = "12 count",
        zone: StorageZone = StorageZone.FRIDGE,
        category: String = "protein",
        imageUri: String? = "🥚",
        imageUrl: String = TEST_IMAGE_URL,
    ): FoodCandidate =
        FoodCandidate(
            name = name,
            quantity = quantity,
            zone = zone,
            category = category,
            servingText = "1 item",
            calories = null,
            proteinGrams = null,
            carbsGrams = null,
            fatGrams = null,
            nutritionSource = "",
            notes = "generic offline fixture",
            imageUri = imageUri,
            imageUrl = imageUrl,
            expiresAtMillis = null,
        )

    fun inventoryItem(
        id: Long = 1L,
        name: String = "Generic Eggs",
        quantity: String = "12 count",
        zone: StorageZone = StorageZone.FRIDGE,
        category: String = "protein",
    ): InventoryItem =
        InventoryItem(
            id = id,
            name = name,
            quantity = quantity,
            zone = zone,
            category = category,
            servingText = "1 item",
            calories = null,
            proteinGrams = null,
            carbsGrams = null,
            fatGrams = null,
            nutritionSource = "",
            notes = "generic offline fixture",
            imageUri = "🥚",
            expiresAtMillis = null,
            source = TEST_SOURCE,
            createdAtMillis = NOW_MILLIS,
            updatedAtMillis = NOW_MILLIS,
            imageUrl = TEST_IMAGE_URL,
        )

    fun groceryItem(
        id: Long = 10L,
        name: String = "Generic Rice",
        quantity: String = "1 bag",
    ): GroceryItem =
        GroceryItem(
            id = id,
            name = name,
            quantity = quantity,
            status = GroceryStatus.NEEDED,
            category = "grain",
            servingText = "",
            calories = null,
            proteinGrams = null,
            carbsGrams = null,
            fatGrams = null,
            nutritionSource = "",
            source = TEST_SOURCE,
            imageUri = "🍚",
            createdAtMillis = NOW_MILLIS,
            updatedAtMillis = NOW_MILLIS,
            imageUrl = TEST_IMAGE_URL,
        )

    fun recipe(
        id: Long = 20L,
        title: String = "Generic Rice Bowl",
    ): Recipe =
        Recipe(
            id = id,
            title = title,
            ingredients = "Rice, eggs, spinach",
            steps = "Cook rice. Add eggs and spinach. Season to taste.",
            servings = 2,
            prepMinutes = 25,
            tags = "generic, offline",
            rating = null,
            imageUri = "🥙",
            createdAtMillis = NOW_MILLIS,
            updatedAtMillis = NOW_MILLIS,
            imageUrl = TEST_IMAGE_URL,
        )

    fun mealLog(
        id: Long = 30L,
        title: String = "Generic Rice Bowl",
        slot: MealSlot = MealSlot.LUNCH,
    ): MealLog =
        MealLog(
            id = id,
            title = title,
            calories = 520,
            proteinGrams = 24.0,
            carbsGrams = 62.0,
            fatGrams = 18.0,
            mealSlot = slot,
            usedItemsText = "Generic Eggs, Generic Rice",
            loggedDateEpochDay = TODAY_EPOCH_DAY,
            source = TEST_SOURCE,
            createdAtMillis = NOW_MILLIS,
            updatedAtMillis = NOW_MILLIS,
        )

    fun mealPlan(
        id: Long = 40L,
        title: String = "Generic Week Plan",
    ): MealPlan =
        MealPlan(
            id = id,
            title = title,
            daysText = "Lunch: Generic Rice Bowl",
            groceryHint = "spinach",
            status = MealPlanStatus.DRAFT,
            startDateEpochDay = TODAY_EPOCH_DAY,
            createdAtMillis = NOW_MILLIS,
            updatedAtMillis = NOW_MILLIS,
        )

    fun mealPlanEntry(
        id: Long = 41L,
        planId: Long = 40L,
        title: String = "Generic Rice Bowl",
    ): MealPlanEntry =
        MealPlanEntry(
            id = id,
            planId = planId,
            dateEpochDay = TODAY_EPOCH_DAY,
            slot = MealSlot.LUNCH,
            title = title,
            calorieTarget = 520,
            status = MealPlanEntryStatus.PLANNED,
            createdAtMillis = NOW_MILLIS,
            updatedAtMillis = NOW_MILLIS,
        )

    fun receiptCapture(id: Long = 50L): ReceiptCapture =
        ReceiptCapture(
            id = id,
            imageUri = "content://wonderfood-test/receipt/generic",
            rawText = "GENERIC EGGS 3.99\nGENERIC RICE 5.99",
            status = ReceiptStatus.EXTRACTED,
            createdAtMillis = NOW_MILLIS,
        )

    fun event(id: Long = 60L): FoodEvent =
        FoodEvent(
            id = id,
            type = FoodEventType.MEAL,
            startedAtMillis = NOW_MILLIS,
            endedAtMillis = null,
            durationMinutes = null,
            amount = null,
            unit = "",
            source = TEST_SOURCE,
            confidence = FoodEventConfidence.ESTIMATED,
            relatedRecipeId = 20L,
            mealLogId = 30L,
            shoppingTripId = null,
            inventoryItemId = null,
            note = "generic offline event",
            createdAtMillis = NOW_MILLIS,
        )

    fun preferences(): FoodPreferences =
        FoodPreferences(
            dietStyle = "balanced",
            allergies = "",
            dislikes = "",
            preferredStaples = "rice, eggs",
            preferredCuisines = "generic",
            preferredStores = "",
            calorieGoal = "",
            proteinGoal = "",
            healthNotes = "",
            customAiInstructions = "",
        )

    fun memory(): FoodMemory =
        FoodMemory(
            events = listOf(event()),
            inventory = listOf(inventoryItem()),
            groceries = listOf(groceryItem()),
            recipes = listOf(recipe()),
            mealLogs = listOf(mealLog()),
            mealPlans = listOf(mealPlan()),
            mealPlanEntries = listOf(mealPlanEntry()),
            receipts = listOf(receiptCapture()),
            preferences = preferences(),
        )

    const val TEST_SOURCE: String = "test_fixture"
    const val TEST_IMAGE_URL: String = "https://example.invalid/wonderfood/generic-food.png"
}
