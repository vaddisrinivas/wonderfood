package com.wonderfood.core.model

import java.util.UUID

private fun requireStableId(value: String): String {
    require(value.isNotBlank()) { "Stable IDs must not be blank." }
    return value
}

private fun newStableId(): String = UUID.randomUUID().toString()

@JvmInline
value class PageId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): PageId = PageId(newStableId())
    }
}

@JvmInline
value class FoodId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): FoodId = FoodId(newStableId())
    }
}

@JvmInline
value class FoodAliasId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): FoodAliasId = FoodAliasId(newStableId())
    }
}

@JvmInline
value class StockLotId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): StockLotId = StockLotId(newStableId())
    }
}

@JvmInline
value class NutritionSnapshotId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): NutritionSnapshotId = NutritionSnapshotId(newStableId())
    }
}

@JvmInline
value class RecipeId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): RecipeId = RecipeId(newStableId())
    }
}

@JvmInline
value class RecipeIngredientId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): RecipeIngredientId = RecipeIngredientId(newStableId())
    }
}

@JvmInline
value class RecipeStepId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): RecipeStepId = RecipeStepId(newStableId())
    }
}

@JvmInline
value class MealPlanId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): MealPlanId = MealPlanId(newStableId())
    }
}

@JvmInline
value class PlanEntryId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): PlanEntryId = PlanEntryId(newStableId())
    }
}

@JvmInline
value class MealLogId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): MealLogId = MealLogId(newStableId())
    }
}

@JvmInline
value class ShoppingItemId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): ShoppingItemId = ShoppingItemId(newStableId())
    }
}

@JvmInline
value class ReceiptId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): ReceiptId = ReceiptId(newStableId())
    }
}

@JvmInline
value class FoodEventId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): FoodEventId = FoodEventId(newStableId())
    }
}

@JvmInline
value class RelationId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): RelationId = RelationId(newStableId())
    }
}

@JvmInline
value class AttachmentId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): AttachmentId = AttachmentId(newStableId())
    }
}

@JvmInline
value class SourceId(val value: String) {
    init {
        requireStableId(value)
    }

    companion object {
        fun new(): SourceId = SourceId(newStableId())
    }
}

@JvmInline
value class IsoDate(val value: String) {
    init {
        require(value.isNotBlank()) { "ISO date must not be blank." }
    }
}

@JvmInline
value class IsoTimestamp(val value: String) {
    init {
        require(value.isNotBlank()) { "ISO timestamp must not be blank." }
    }
}

enum class TruthState {
    UNKNOWN,
    USER_CONFIRMED,
    PROVIDER_CONFIRMED,
    ESTIMATED,
    INFERRED,
    CONFLICTED,
    REJECTED,
}

enum class EntityType {
    UNKNOWN,
    PAGE,
    FOOD,
    FOOD_ALIAS,
    STOCK_LOT,
    NUTRITION_SNAPSHOT,
    RECIPE,
    RECIPE_INGREDIENT,
    RECIPE_STEP,
    MEAL_PLAN,
    PLAN_ENTRY,
    MEAL_LOG,
    SHOPPING_ITEM,
    RECEIPT,
    FOOD_EVENT,
    RELATION,
    ATTACHMENT,
    SOURCE,
}

enum class PageKind {
    UNKNOWN,
    FOOD,
    RECIPE,
    MEAL_PLAN,
    MEAL_LOG,
    SHOPPING_ITEM,
    RECEIPT,
    ATTACHMENT,
}

enum class SourceKind {
    UNKNOWN,
    USER,
    AI,
    BARCODE,
    RECEIPT,
    NUTRITION_PROVIDER,
    USDA,
    OPEN_FOOD_FACTS,
    HEALTH_CONNECT,
    SYSTEM,
    IMPORT,
}

enum class FoodUnit {
    UNKNOWN,
    EACH,
    GRAM,
    KILOGRAM,
    MILLILITER,
    LITER,
    CUP,
    TABLESPOON,
    TEASPOON,
    OUNCE,
    POUND,
    SERVING,
    PACKAGE,
    CAN,
    BOTTLE,
    SLICE,
    PINCH,
}

enum class NutritionBasisType {
    UNKNOWN,
    PER_100_GRAMS,
    PER_SERVING,
    PER_CONTAINER,
    PER_RECIPE,
    PER_PORTION,
}

enum class FoodStatus {
    UNKNOWN,
    ACTIVE,
    ARCHIVED,
}

enum class StockLotStatus {
    UNKNOWN,
    AVAILABLE,
    OPENED,
    LOW,
    OUT,
    RESERVED,
    CONSUMED,
    EXPIRED,
    DISCARDED,
    ARCHIVED,
}

enum class RecipeStatus {
    UNKNOWN,
    DRAFT,
    ACTIVE,
    ARCHIVED,
}

enum class MealSlot {
    UNKNOWN,
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
    ANYTIME,
}

enum class MealPlanStatus {
    UNKNOWN,
    DRAFT,
    ACCEPTED,
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}

enum class PlanEntryStatus {
    UNKNOWN,
    PLANNED,
    ACCEPTED,
    SKIPPED,
    EATEN,
    ARCHIVED,
}

enum class MealLogStatus {
    UNKNOWN,
    ESTIMATED,
    CONFIRMED,
    ARCHIVED,
}

enum class ShoppingItemStatus {
    UNKNOWN,
    NEEDED,
    IN_CART,
    PURCHASED,
    PUT_AWAY,
    SKIPPED,
    ARCHIVED,
}

enum class ReceiptStatus {
    UNKNOWN,
    CAPTURED,
    REVIEWED,
    RECONCILED,
    ARCHIVED,
}

enum class FoodEventType {
    UNKNOWN,
    CREATED,
    UPDATED,
    STOCK_ADDED,
    STOCK_DEDUCTED,
    STOCK_MOVED,
    STOCK_OPENED,
    STOCK_CONSUMED,
    STOCK_DISCARDED,
    STOCK_CORRECTED,
    STOCK_MARKED_LOW,
    STOCK_MARKED_OUT,
    STOCK_PUT_AWAY,
    STOCK_MERGED,
    STOCK_USAGE_PROPOSED,
    COOKED,
    PLANNED,
    EATEN,
    SHOPPING_NEEDED,
    PURCHASED,
    RECEIPT_IMPORTED,
    ARCHIVED,
}

enum class RelationType {
    UNKNOWN,
    ALIAS_OF,
    CONTAINS,
    SUBSTITUTE_FOR,
    DERIVED_FROM,
    PLANNED_FOR,
    LOGGED_FROM,
    ATTACHED_TO,
    SOURCE_FOR,
}

enum class AttachmentKind {
    UNKNOWN,
    IMAGE,
    DOCUMENT,
    BARCODE,
    RECEIPT_PHOTO,
    VOICE_NOTE,
    LINK,
}

data class EntityRef(
    val type: EntityType,
    val id: String,
) {
    init {
        requireStableId(id)
    }
}

data class Source(
    val id: SourceId,
    val kind: SourceKind,
    val label: String,
    val externalId: String?,
    val uri: String?,
    val capturedAt: IsoTimestamp?,
    val truthState: TruthState,
) {
    init {
        require(label.isNotBlank()) { "Source label must not be blank." }
    }
}

data class Confidence(
    val score: Double?,
    val state: TruthState,
    val rationale: String?,
) {
    init {
        score?.let {
            require(it in 0.0..1.0) { "Confidence score must be between 0.0 and 1.0." }
        }
        require(state != TruthState.UNKNOWN || score == null) {
            "Unknown confidence must not carry a numeric score."
        }
    }

    companion object {
        val UNKNOWN = Confidence(
            score = null,
            state = TruthState.UNKNOWN,
            rationale = "Unknown",
        )
    }
}

data class Quantity(
    val amount: Double?,
    val unit: FoodUnit,
    val truthState: TruthState,
) {
    init {
        amount?.let {
            require(it >= 0.0) { "Quantity amount must not be negative." }
        }
    }
}

data class ServingBasis(
    val type: NutritionBasisType,
    val quantity: Quantity,
    val description: String?,
)

data class Money(
    val amount: Double?,
    val currencyCode: String?,
    val truthState: TruthState,
) {
    init {
        amount?.let {
            require(it >= 0.0) { "Money amount must not be negative." }
        }
        currencyCode?.let {
            require(it.length == 3) { "Currency code must use ISO 4217 format." }
        }
    }
}

data class NutritionValues(
    val energyKcal: Double?,
    val proteinGrams: Double?,
    val carbohydrateGrams: Double?,
    val fatGrams: Double?,
    val fiberGrams: Double?,
    val sugarGrams: Double?,
    val sodiumMilligrams: Double?,
)

data class Page(
    val id: PageId,
    val title: String,
    val kind: PageKind,
    val entity: EntityRef?,
    val aliases: List<String>,
    val relationIds: List<RelationId>,
    val attachmentIds: List<AttachmentId>,
    val truthState: TruthState,
    val source: Source,
    val confidence: Confidence,
) {
    init {
        require(title.isNotBlank()) { "Page title must not be blank." }
    }
}

data class Food(
    val id: FoodId,
    val pageId: PageId,
    val name: String,
    val status: FoodStatus,
    val aliasIds: List<FoodAliasId>,
    val stockLotIds: List<StockLotId>,
    val nutritionSnapshotIds: List<NutritionSnapshotId>,
    val attachmentIds: List<AttachmentId>,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
) {
    init {
        require(name.isNotBlank()) { "Food name must not be blank." }
    }
}

data class FoodAlias(
    val id: FoodAliasId,
    val foodId: FoodId,
    val name: String,
    val locale: String?,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
) {
    init {
        require(name.isNotBlank()) { "Food alias must not be blank." }
    }
}

data class StockLot(
    val id: StockLotId,
    val foodId: FoodId,
    val quantity: Quantity,
    val purchasedOn: IsoDate?,
    val expiresOn: IsoDate?,
    val location: String?,
    val status: StockLotStatus,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
)

data class NutritionSnapshot(
    val id: NutritionSnapshotId,
    val subject: EntityRef,
    val basis: ServingBasis,
    val values: NutritionValues,
    val source: Source,
    val confidence: Confidence,
    val capturedAt: IsoTimestamp?,
    val truthState: TruthState,
)

data class Recipe(
    val id: RecipeId,
    val pageId: PageId,
    val title: String,
    val description: String?,
    val status: RecipeStatus,
    val servings: Quantity,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
    val nutritionSnapshotIds: List<NutritionSnapshotId>,
    val attachmentIds: List<AttachmentId>,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
) {
    init {
        require(title.isNotBlank()) { "Recipe title must not be blank." }
        prepMinutes?.let {
            require(it >= 0) { "Prep minutes must not be negative." }
        }
        cookMinutes?.let {
            require(it >= 0) { "Cook minutes must not be negative." }
        }
    }
}

data class RecipeIngredient(
    val id: RecipeIngredientId,
    val recipeId: RecipeId,
    val foodId: FoodId?,
    val displayName: String,
    val quantity: Quantity,
    val preparation: String?,
    val optional: Boolean,
    val substituteFoodIds: List<FoodId>,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
) {
    init {
        require(displayName.isNotBlank()) { "Ingredient display name must not be blank." }
    }
}

data class RecipeStep(
    val id: RecipeStepId,
    val recipeId: RecipeId,
    val order: Int,
    val instruction: String,
    val durationMinutes: Int?,
    val attachmentIds: List<AttachmentId>,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
) {
    init {
        require(order >= 0) { "Recipe step order must not be negative." }
        require(instruction.isNotBlank()) { "Recipe step instruction must not be blank." }
        durationMinutes?.let {
            require(it >= 0) { "Recipe step duration must not be negative." }
        }
    }
}

data class MealPlan(
    val id: MealPlanId,
    val pageId: PageId,
    val name: String,
    val startsOn: IsoDate,
    val endsOn: IsoDate,
    val status: MealPlanStatus,
    val entries: List<PlanEntry>,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
) {
    init {
        require(name.isNotBlank()) { "Meal plan name must not be blank." }
    }
}

data class PlanEntry(
    val id: PlanEntryId,
    val mealPlanId: MealPlanId,
    val date: IsoDate,
    val mealSlot: MealSlot,
    val recipeId: RecipeId?,
    val foodId: FoodId?,
    val quantity: Quantity,
    val status: PlanEntryStatus,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
)

data class MealLog(
    val id: MealLogId,
    val pageId: PageId,
    val occurredAt: IsoTimestamp,
    val mealSlot: MealSlot,
    val planEntryId: PlanEntryId?,
    val foodIds: List<FoodId>,
    val recipeIds: List<RecipeId>,
    val nutritionSnapshotIds: List<NutritionSnapshotId>,
    val status: MealLogStatus,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
)

data class ShoppingItem(
    val id: ShoppingItemId,
    val pageId: PageId,
    val foodId: FoodId?,
    val recipeId: RecipeId?,
    val quantity: Quantity,
    val reason: String?,
    val status: ShoppingItemStatus,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
)

data class Receipt(
    val id: ReceiptId,
    val pageId: PageId,
    val merchantName: String?,
    val purchasedAt: IsoTimestamp?,
    val itemIds: List<ShoppingItemId>,
    val subtotal: Money?,
    val tax: Money?,
    val total: Money?,
    val attachmentIds: List<AttachmentId>,
    val status: ReceiptStatus,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
)

data class FoodEvent(
    val id: FoodEventId,
    val subject: EntityRef,
    val type: FoodEventType,
    val occurredAt: IsoTimestamp,
    val quantity: Quantity?,
    val note: String?,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
)

data class Relation(
    val id: RelationId,
    val from: EntityRef,
    val to: EntityRef,
    val type: RelationType,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
)

data class Attachment(
    val id: AttachmentId,
    val kind: AttachmentKind,
    val uri: String,
    val label: String?,
    val checksum: String?,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
) {
    init {
        require(uri.isNotBlank()) { "Attachment URI must not be blank." }
    }
}

data class WonderFoodSnapshot(
    val schemaVersion: Int,
    val pages: List<Page>,
    val foods: List<Food>,
    val foodAliases: List<FoodAlias>,
    val stockLots: List<StockLot>,
    val nutritionSnapshots: List<NutritionSnapshot>,
    val recipes: List<Recipe>,
    val mealPlans: List<MealPlan>,
    val mealLogs: List<MealLog>,
    val shoppingItems: List<ShoppingItem>,
    val receipts: List<Receipt>,
    val foodEvents: List<FoodEvent>,
    val relations: List<Relation>,
    val attachments: List<Attachment>,
)
