package com.wonderfood.app.sync

import com.wonderfood.app.data.FoodEvent
import com.wonderfood.app.data.FoodEventConfidence
import com.wonderfood.app.data.FoodEventType
import com.wonderfood.app.data.HouseholdUiMemory
import com.wonderfood.app.data.GroceryItem
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.InventoryAction
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.InventoryTransaction
import com.wonderfood.app.data.MealLog
import com.wonderfood.app.data.MealPlan
import com.wonderfood.app.data.MealPlanEntry
import com.wonderfood.app.data.MealPlanEntryStatus
import com.wonderfood.app.data.MealPlanStatus
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.ReceiptCapture
import com.wonderfood.app.data.ReceiptStatus
import com.wonderfood.app.data.Recipe
import com.wonderfood.app.data.StorageZone
import com.wonderfood.core.model.WonderFoodSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object WonderFoodWorkspaceSeedFixture {
    private val now: Instant = Instant.parse("2026-07-19T12:00:00Z")

    fun snapshot(): WonderFoodSnapshot =
        TestHouseholdUiSnapshotExporter.toSnapshot(memory(), now)

    fun memory(): HouseholdUiMemory =
        HouseholdUiMemory(
            inventory = listOf(
                inventory(
                    id = 1,
                    name = "Basmati Rice",
                    quantity = "2 kg",
                    zone = StorageZone.PANTRY,
                    category = "Grain",
                    servingText = "45 g dry",
                    calories = 160,
                    protein = 3.0,
                    carbs = 36.0,
                    fat = 0.4,
                    expires = "2026-09-15",
                    priceCents = 899,
                    store = "Costco",
                ),
                inventory(
                    id = 2,
                    name = "Greek Yogurt",
                    quantity = "3 cups",
                    zone = StorageZone.FRIDGE,
                    category = "Dairy",
                    servingText = "170 g",
                    calories = 120,
                    protein = 18.0,
                    carbs = 7.0,
                    fat = 0.0,
                    expires = "2026-07-25",
                    priceCents = 549,
                    store = "Trader Joe's",
                ),
                inventory(
                    id = 3,
                    name = "Frozen Spinach",
                    quantity = "1 package",
                    zone = StorageZone.FREEZER,
                    category = "Produce",
                    servingText = "85 g",
                    calories = 30,
                    protein = 3.0,
                    carbs = 4.0,
                    fat = 0.0,
                    expires = "2026-10-01",
                    priceCents = 249,
                    store = "Whole Foods",
                ),
            ),
            groceries = listOf(
                grocery(10, "Eggs", "12 item", GroceryStatus.NEEDED, "Protein"),
                grocery(11, "Cilantro", "1 bunch", GroceryStatus.NEEDED, "Produce"),
                grocery(12, "Greek Yogurt", "1 cup", GroceryStatus.BOUGHT, "Dairy"),
            ),
            recipes = listOf(
                recipe(
                    id = 20,
                    title = "Spinach Rice Bowl",
                    ingredients = "1 cup Basmati Rice\n1 cup Frozen Spinach\n1 cup Greek Yogurt\nCilantro to finish",
                    steps = "Cook rice until fluffy\nWarm spinach with spices\nTop rice with spinach and yogurt\nFinish with cilantro",
                    servings = 2,
                    prepMinutes = 12,
                    tags = "vegetarian, pantry-match, lunch",
                ),
                recipe(
                    id = 21,
                    title = "Yogurt Breakfast Bowl",
                    ingredients = "1 cup Greek Yogurt\nFruit or nuts\nHoney",
                    steps = "Spoon yogurt into a bowl\nAdd toppings\nServe cold",
                    servings = 1,
                    prepMinutes = 5,
                    tags = "breakfast, quick",
                ),
            ),
            mealPlans = listOf(
                MealPlan(
                    id = 30,
                    title = "Next week simple dinners",
                    daysText = "Mon: Spinach Rice Bowl\nTue: Yogurt Breakfast Bowl prep\nWed: Flexible leftovers",
                    groceryHint = "Eggs and cilantro are missing.",
                    status = MealPlanStatus.ACCEPTED,
                    startDateEpochDay = date("2026-07-20").toEpochDay(),
                    createdAtMillis = millis("2026-07-19T09:00:00Z"),
                    updatedAtMillis = millis("2026-07-19T09:15:00Z"),
                ),
            ),
            mealPlanEntries = listOf(
                mealPlanEntry(31, 30, "2026-07-20", MealSlot.DINNER, "Spinach Rice Bowl", 20),
                mealPlanEntry(32, 30, "2026-07-21", MealSlot.BREAKFAST, "Yogurt Breakfast Bowl", 21),
            ),
            mealLogs = listOf(
                MealLog(
                    id = 40,
                    title = "Greek yogurt snack",
                    calories = 120,
                    proteinGrams = 18.0,
                    carbsGrams = 7.0,
                    fatGrams = 0.0,
                    mealSlot = MealSlot.SNACK,
                    usedItemsText = "Greek Yogurt",
                    loggedDateEpochDay = date("2026-07-19").toEpochDay(),
                    source = "seed",
                    createdAtMillis = millis("2026-07-19T10:15:00Z"),
                    updatedAtMillis = millis("2026-07-19T10:15:00Z"),
                ),
            ),
            events = listOf(
                FoodEvent(
                    id = 50,
                    type = FoodEventType.PANTRY_USE,
                    startedAtMillis = millis("2026-07-19T10:15:00Z"),
                    endedAtMillis = null,
                    durationMinutes = null,
                    amount = 1.0,
                    unit = "serving",
                    source = "seed",
                    confidence = FoodEventConfidence.EXACT,
                    relatedRecipeId = null,
                    mealLogId = 40,
                    shoppingTripId = null,
                    inventoryItemId = 2,
                    note = "Used yogurt for snack.",
                    createdAtMillis = millis("2026-07-19T10:15:00Z"),
                ),
                FoodEvent(
                    id = 51,
                    type = FoodEventType.GROCERY_PURCHASE,
                    startedAtMillis = millis("2026-07-18T18:30:00Z"),
                    endedAtMillis = null,
                    durationMinutes = null,
                    amount = 16.97,
                    unit = "USD",
                    source = "seed",
                    confidence = FoodEventConfidence.EXACT,
                    relatedRecipeId = null,
                    mealLogId = null,
                    shoppingTripId = null,
                    inventoryItemId = null,
                    note = "Bought rice, yogurt, and spinach.",
                    createdAtMillis = millis("2026-07-18T18:30:00Z"),
                ),
            ),
            inventoryTransactions = listOf(
                InventoryTransaction(
                    id = 60,
                    inventoryItemId = 2,
                    itemName = "Greek Yogurt",
                    quantityText = "1 serving",
                    zone = StorageZone.FRIDGE,
                    action = InventoryAction.USED,
                    reason = "Snack log",
                    relatedRecipeId = null,
                    relatedMealLogId = 40,
                    occurredDateEpochDay = date("2026-07-19").toEpochDay(),
                    source = "seed",
                    createdAtMillis = millis("2026-07-19T10:15:00Z"),
                ),
            ),
            receipts = listOf(
                ReceiptCapture(
                    id = 70,
                    imageUri = "content://wonderfood/seed/receipt-70.jpg",
                    rawText = "Trader Joe's\nBasmati Rice 8.99\nGreek Yogurt 5.49\nFrozen Spinach 2.49\nTotal 16.97",
                    status = ReceiptStatus.EXTRACTED,
                    createdAtMillis = millis("2026-07-18T18:30:00Z"),
                ),
            ),
        )

    private fun inventory(
        id: Long,
        name: String,
        quantity: String,
        zone: StorageZone,
        category: String,
        servingText: String,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        expires: String,
        priceCents: Long,
        store: String,
    ): InventoryItem =
        InventoryItem(
            id = id,
            name = name,
            quantity = quantity,
            zone = zone,
            category = category,
            servingText = servingText,
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            nutritionSource = "seed label",
            notes = "Seeded for V4 workspace review.",
            imageUri = null,
            expiresAtMillis = date(expires).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            source = "seed",
            createdAtMillis = millis("2026-07-18T18:30:00Z"),
            updatedAtMillis = millis("2026-07-19T12:00:00Z"),
            purchaseDateEpochDay = date("2026-07-18").toEpochDay(),
            purchasePriceCents = priceCents,
            currencyCode = "USD",
            storeName = store,
        )

    private fun grocery(id: Long, name: String, quantity: String, status: GroceryStatus, category: String): GroceryItem =
        GroceryItem(
            id = id,
            name = name,
            quantity = quantity,
            status = status,
            category = category,
            source = "seed",
            imageUri = null,
            createdAtMillis = millis("2026-07-19T08:00:00Z"),
            updatedAtMillis = millis("2026-07-19T12:00:00Z"),
        )

    private fun recipe(
        id: Long,
        title: String,
        ingredients: String,
        steps: String,
        servings: Int,
        prepMinutes: Int,
        tags: String,
    ): Recipe =
        Recipe(
            id = id,
            title = title,
            ingredients = ingredients,
            steps = steps,
            servings = servings,
            prepMinutes = prepMinutes,
            tags = tags,
            rating = 5,
            imageUri = null,
            createdAtMillis = millis("2026-07-19T07:00:00Z"),
            updatedAtMillis = millis("2026-07-19T12:00:00Z"),
        )

    private fun mealPlanEntry(
        id: Long,
        planId: Long,
        date: String,
        slot: MealSlot,
        title: String,
        recipeId: Long,
    ): MealPlanEntry =
        MealPlanEntry(
            id = id,
            planId = planId,
            dateEpochDay = date(date).toEpochDay(),
            slot = slot,
            title = title,
            calorieTarget = null,
            status = MealPlanEntryStatus.PLANNED,
            notes = "Seeded from V4 fixture.",
            source = "seed",
            recipeId = recipeId,
            createdAtMillis = millis("2026-07-19T09:00:00Z"),
            updatedAtMillis = millis("2026-07-19T12:00:00Z"),
        )

    private fun millis(value: String): Long =
        Instant.parse(value).toEpochMilli()

    private fun date(value: String): LocalDate =
        LocalDate.parse(value)
}
