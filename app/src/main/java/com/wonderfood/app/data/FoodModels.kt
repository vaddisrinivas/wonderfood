package com.wonderfood.app.data

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val body: String,
    val createdAtMillis: Long,
)

enum class ChatRole {
    USER,
    ASSISTANT,
}

data class InventoryItem(
    val id: Long,
    val name: String,
    val quantity: String,
    val zone: StorageZone,
    val category: String,
    val servingText: String = "",
    val calories: Int? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val nutritionSource: String = "",
    val notes: String,
    val imageUri: String?,
    val expiresAtMillis: Long?,
    val source: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

enum class StorageZone(val label: String) {
    FRIDGE("Fridge"),
    FREEZER("Freezer"),
    PANTRY("Pantry"),
}

data class GroceryItem(
    val id: Long,
    val name: String,
    val quantity: String,
    val status: GroceryStatus,
    val category: String,
    val servingText: String = "",
    val calories: Int? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val nutritionSource: String = "",
    val source: String,
    val imageUri: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

enum class GroceryStatus {
    NEEDED,
    BOUGHT,
}

data class Recipe(
    val id: Long,
    val title: String,
    val ingredients: String,
    val steps: String,
    val servings: Int?,
    val prepMinutes: Int?,
    val tags: String,
    val rating: Int?,
    val imageUri: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class MealLog(
    val id: Long,
    val title: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val mealSlot: MealSlot,
    val usedItemsText: String,
    val loggedDateEpochDay: Long,
    val source: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class InventoryTransaction(
    val id: Long,
    val inventoryItemId: Long?,
    val itemName: String,
    val quantityText: String,
    val zone: StorageZone,
    val action: InventoryAction,
    val reason: String,
    val relatedRecipeId: Long?,
    val relatedMealLogId: Long?,
    val occurredDateEpochDay: Long,
    val source: String,
    val createdAtMillis: Long,
)

data class FoodEvent(
    val id: Long,
    val type: FoodEventType,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val durationMinutes: Int?,
    val amount: Double?,
    val unit: String,
    val source: String,
    val confidence: FoodEventConfidence,
    val relatedRecipeId: Long?,
    val mealLogId: Long?,
    val shoppingTripId: Long?,
    val inventoryItemId: Long?,
    val note: String,
    val createdAtMillis: Long,
)

enum class FoodEventType(val label: String) {
    WATER("Water"),
    MEAL("Meal"),
    COOK("Cook"),
    SHOP("Shop"),
    PREP("Prep"),
    GROCERY_PURCHASE("Grocery purchase"),
    PANTRY_USE("Pantry use"),
    OUTSIDE_FOOD("Outside food"),
}

enum class FoodEventConfidence(val label: String) {
    EXACT("exact"),
    ESTIMATED("estimated"),
    AI_ESTIMATED("ai estimated"),
}

enum class InventoryAction(val label: String) {
    ADDED("Added"),
    UPDATED("Updated"),
    BOUGHT("Bought"),
    USED("Used"),
    REMOVED("Removed"),
}

enum class MealSlot(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack"),
    FLEX("Flexible"),
}

data class MealPlan(
    val id: Long,
    val title: String,
    val daysText: String,
    val groceryHint: String,
    val status: MealPlanStatus,
    val startDateEpochDay: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

enum class MealPlanStatus {
    DRAFT,
    ACCEPTED,
}

data class MealPlanEntry(
    val id: Long,
    val planId: Long,
    val dateEpochDay: Long,
    val slot: MealSlot,
    val title: String,
    val calorieTarget: Int?,
    val status: MealPlanEntryStatus,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

enum class MealPlanEntryStatus {
    DRAFT,
    PLANNED,
    EATEN,
    SKIPPED,
}

data class ReceiptCapture(
    val id: Long,
    val imageUri: String,
    val rawText: String,
    val status: ReceiptStatus,
    val createdAtMillis: Long,
)

data class ChatAction(
    val id: Long,
    val title: String,
    val summary: String,
    val rowsText: String,
    val operation: FoodOperation,
    val status: ChatActionStatus,
    val source: DraftSource,
    val confidence: Double,
    val sourceMessageId: Long?,
    val createdAtMillis: Long,
    val resolvedAtMillis: Long?,
)

enum class ChatActionStatus {
    ACCEPTED,
    REJECTED,
}

enum class ReceiptStatus {
    SAVED,
    EXTRACTED,
    NEEDS_TEXT,
}

data class FoodPreferences(
    val dietStyle: String = "",
    val allergies: String = "",
    val dislikes: String = "",
    val preferredStaples: String = "",
    val preferredCuisines: String = "",
    val preferredStores: String = "",
    val calorieGoal: String = "",
    val proteinGoal: String = "",
    val healthNotes: String = "",
    val customAiInstructions: String = "",
) {
    val isEmpty: Boolean
        get() = listOf(
            dietStyle,
            allergies,
            dislikes,
            preferredStaples,
            preferredCuisines,
            preferredStores,
            calorieGoal,
            proteinGoal,
            healthNotes,
            customAiInstructions,
        ).all { it.isBlank() }
}

data class FoodMemory(
    val messages: List<ChatMessage> = emptyList(),
    val actions: List<ChatAction> = emptyList(),
    val events: List<FoodEvent> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val inventoryTransactions: List<InventoryTransaction> = emptyList(),
    val groceries: List<GroceryItem> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val mealLogs: List<MealLog> = emptyList(),
    val mealPlans: List<MealPlan> = emptyList(),
    val mealPlanEntries: List<MealPlanEntry> = emptyList(),
    val receipts: List<ReceiptCapture> = emptyList(),
    val preferences: FoodPreferences = FoodPreferences(),
)

data class FoodCandidate(
    val name: String,
    val quantity: String = "",
    val zone: StorageZone = StorageZone.PANTRY,
    val category: String = "",
    val servingText: String = "",
    val calories: Int? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val nutritionSource: String = "",
    val notes: String = "",
    val imageUri: String? = null,
    val expiresAtMillis: Long? = null,
)

sealed interface FoodDraft {
    val operation: FoodOperation
        get() = FoodOperation.CREATE
    val draftSource: DraftSource
        get() = DraftSource.CHAT
    val confidence: Double
        get() = 0.75
    val title: String
    val summary: String
    val rows: List<String>
}

enum class FoodOperation {
    CREATE,
    UPDATE,
    DELETE,
    LOG,
    PLAN,
    IMPORT,
}

enum class DraftSource {
    CHAT,
    RECEIPT_TEXT,
    RECEIPT_PHOTO,
    LOCAL_FALLBACK,
}

data class InventoryDraft(
    val items: List<FoodCandidate>,
) : FoodDraft {
    override val title: String = "Add to food memory"
    override val summary: String = "Pantry/fridge/freezer items extracted from chat."
    override val rows: List<String> = items.map { item ->
        listOf(item.quantity, item.name, item.zone.label).filter { it.isNotBlank() }.joinToString(" - ")
    }
}

data class GroceryDraft(
    val items: List<FoodCandidate>,
) : FoodDraft {
    override val title: String = "Update grocery list"
    override val summary: String = "Shopping items extracted from chat."
    override val rows: List<String> = items.map { item ->
        listOf(item.quantity, item.name).filter { it.isNotBlank() }.joinToString(" - ")
    }
}

data class RecipeDraft(
    val titleText: String,
    val ingredientsText: String,
    val stepsText: String,
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val tags: String = "",
) : FoodDraft {
    override val title: String = "Save recipe"
    override val summary: String = titleText
    override val rows: List<String> = listOf("Ingredients: $ingredientsText", "Steps: $stepsText")
}

data class MealLogDraft(
    val titleText: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val mealSlot: MealSlot = MealSlot.FLEX,
    val usedItemsText: String = "",
    val loggedDateEpochDay: Long? = null,
    val source: String = "ai_estimate_local",
) : FoodDraft {
    override val operation: FoodOperation = FoodOperation.LOG
    override val title: String = "Log meal"
    override val summary: String = "$calories kcal estimated"
    override val rows: List<String> = listOf(
        "Slot: ${mealSlot.label}",
        "${proteinGrams.toInt()}g protein",
        "${carbsGrams.toInt()}g carbs",
        "${fatGrams.toInt()}g fat",
        "Used: ${usedItemsText.ifBlank { "unknown" }}",
        "Source: $source",
    )
}

data class MealPlanEntryDraft(
    val dayOffset: Int,
    val slot: MealSlot,
    val title: String,
    val calorieTarget: Int? = null,
)

data class MealPlanDraft(
    val titleText: String,
    val daysText: String,
    val groceryHint: String,
    val entries: List<MealPlanEntryDraft> = emptyList(),
    val startDateEpochDay: Long? = null,
) : FoodDraft {
    override val operation: FoodOperation = FoodOperation.PLAN
    override val title: String = "Save meal plan"
    override val summary: String = titleText
    override val rows: List<String> = buildList {
        if (entries.isNotEmpty()) {
            addAll(entries.map { "${it.slot.label}: ${it.title}" })
        } else {
            add(daysText)
        }
        add("Groceries: $groceryHint")
    }
}

data class AiTurn(
    val reply: String,
    val draft: FoodDraft?,
)
