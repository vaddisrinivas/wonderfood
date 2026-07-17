package com.wonderfood.core.data.room

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wonderfood.core.model.AttachmentKind
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.FoodEventType
import com.wonderfood.core.model.FoodStatus
import com.wonderfood.core.model.FoodUnit
import com.wonderfood.core.model.MealLogStatus
import com.wonderfood.core.model.MealPlanStatus
import com.wonderfood.core.model.MealSlot
import com.wonderfood.core.model.NutritionBasisType
import com.wonderfood.core.model.PageKind
import com.wonderfood.core.model.PlanEntryStatus
import com.wonderfood.core.model.RecipeStatus
import com.wonderfood.core.model.ReceiptStatus
import com.wonderfood.core.model.RelationType
import com.wonderfood.core.model.ShoppingItemStatus
import com.wonderfood.core.model.SourceKind
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.TruthState

internal const val NO_ACTION = ForeignKey.NO_ACTION

data class SourceColumns(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "kind") val kind: SourceKind,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "external_id") val externalId: String?,
    @ColumnInfo(name = "uri") val uri: String?,
    @ColumnInfo(name = "captured_at") val capturedAt: String?,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
)

data class ConfidenceColumns(
    @ColumnInfo(name = "score") val score: Double?,
    @ColumnInfo(name = "state") val state: TruthState,
    @ColumnInfo(name = "rationale") val rationale: String?,
)

data class QuantityColumns(
    @ColumnInfo(name = "quantity_amount") val amount: Double?,
    @ColumnInfo(name = "quantity_unit") val unit: FoodUnit,
    @ColumnInfo(name = "quantity_truth_state") val truthState: TruthState,
)

data class ServingBasisColumns(
    @ColumnInfo(name = "basis_type") val type: NutritionBasisType,
    @Embedded(prefix = "basis_") val quantity: QuantityColumns,
    @ColumnInfo(name = "basis_description") val description: String?,
)

data class MoneyColumns(
    @ColumnInfo(name = "amount") val amount: Double?,
    @ColumnInfo(name = "currency_code") val currencyCode: String?,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
)

data class NutritionValuesColumns(
    @ColumnInfo(name = "energy_kcal") val energyKcal: Double?,
    @ColumnInfo(name = "protein_grams") val proteinGrams: Double?,
    @ColumnInfo(name = "carbohydrate_grams") val carbohydrateGrams: Double?,
    @ColumnInfo(name = "fat_grams") val fatGrams: Double?,
    @ColumnInfo(name = "fiber_grams") val fiberGrams: Double?,
    @ColumnInfo(name = "sugar_grams") val sugarGrams: Double?,
    @ColumnInfo(name = "sodium_milligrams") val sodiumMilligrams: Double?,
)

@Entity(
    tableName = "sources",
    indices = [
        Index("kind"),
        Index(value = ["kind", "external_id"], unique = true),
    ],
)
data class SourceRecordEntity(
    @PrimaryKey val id: String,
    val kind: SourceKind,
    val label: String,
    @ColumnInfo(name = "external_id") val externalId: String?,
    val uri: String?,
    @ColumnInfo(name = "captured_at") val capturedAt: String?,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "pages",
    indices = [
        Index("kind"),
        Index(value = ["entity_type", "entity_id"]),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: PageKind,
    @ColumnInfo(name = "entity_type") val entityType: EntityType?,
    @ColumnInfo(name = "entity_id") val entityId: String?,
    val aliases: List<String>,
    @ColumnInfo(name = "relation_ids") val relationIds: List<String>,
    @ColumnInfo(name = "attachment_ids") val attachmentIds: List<String>,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "foods",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["page_id"],
            onDelete = NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["page_id"], unique = true),
        Index("name"),
        Index("status"),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class FoodEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "page_id") val pageId: String,
    val name: String,
    val status: FoodStatus,
    @ColumnInfo(name = "alias_ids") val aliasIds: List<String>,
    @ColumnInfo(name = "stock_lot_ids") val stockLotIds: List<String>,
    @ColumnInfo(name = "nutrition_snapshot_ids") val nutritionSnapshotIds: List<String>,
    @ColumnInfo(name = "attachment_ids") val attachmentIds: List<String>,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "food_aliases",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = NO_ACTION,
        ),
    ],
    indices = [
        Index("food_id"),
        Index(value = ["food_id", "name", "locale"], unique = true),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class FoodAliasEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    val name: String,
    val locale: String?,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "stock_lots",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = NO_ACTION,
        ),
    ],
    indices = [
        Index("food_id"),
        Index("status"),
        Index("expires_on"),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class StockLotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    @Embedded val quantity: QuantityColumns,
    @ColumnInfo(name = "purchased_on") val purchasedOn: String?,
    @ColumnInfo(name = "expires_on") val expiresOn: String?,
    val location: String?,
    val status: StockLotStatus,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "nutrition_snapshots",
    indices = [
        Index(value = ["subject_type", "subject_id"]),
        Index("captured_at"),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class NutritionSnapshotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "subject_type") val subjectType: EntityType,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    @Embedded val basis: ServingBasisColumns,
    @Embedded(prefix = "nutrition_") val values: NutritionValuesColumns,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "captured_at") val capturedAt: String?,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "recipes",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["page_id"],
            onDelete = NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["page_id"], unique = true),
        Index("title"),
        Index("status"),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class RecipeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "page_id") val pageId: String,
    val title: String,
    val description: String?,
    val status: RecipeStatus,
    @Embedded(prefix = "servings_") val servings: QuantityColumns,
    @ColumnInfo(name = "prep_minutes") val prepMinutes: Int?,
    @ColumnInfo(name = "cook_minutes") val cookMinutes: Int?,
    @ColumnInfo(name = "nutrition_snapshot_ids") val nutritionSnapshotIds: List<String>,
    @ColumnInfo(name = "attachment_ids") val attachmentIds: List<String>,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipe_id"], onDelete = NO_ACTION),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = NO_ACTION),
    ],
    indices = [
        Index("recipe_id"),
        Index("food_id"),
        Index(value = ["recipe_id", "display_name"], unique = true),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class RecipeIngredientEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "food_id") val foodId: String?,
    @ColumnInfo(name = "display_name") val displayName: String,
    @Embedded val quantity: QuantityColumns,
    val preparation: String?,
    val optional: Boolean,
    @ColumnInfo(name = "substitute_food_ids") val substituteFoodIds: List<String>,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "recipe_ingredient_substitutes",
    primaryKeys = ["recipe_ingredient_id", "food_id"],
    foreignKeys = [
        ForeignKey(entity = RecipeIngredientEntity::class, parentColumns = ["id"], childColumns = ["recipe_ingredient_id"], onDelete = NO_ACTION),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = NO_ACTION),
    ],
    indices = [Index("recipe_ingredient_id"), Index("food_id")],
)
data class RecipeIngredientSubstituteEntity(
    @ColumnInfo(name = "recipe_ingredient_id") val recipeIngredientId: String,
    @ColumnInfo(name = "food_id") val foodId: String,
)

@Entity(
    tableName = "recipe_steps",
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipe_id"], onDelete = NO_ACTION),
    ],
    indices = [
        Index("recipe_id"),
        Index(value = ["recipe_id", "step_order"], unique = true),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class RecipeStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "step_order") val stepOrder: Int,
    val instruction: String,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int?,
    @ColumnInfo(name = "attachment_ids") val attachmentIds: List<String>,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "meal_plans",
    foreignKeys = [
        ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["page_id"], onDelete = NO_ACTION),
    ],
    indices = [
        Index(value = ["page_id"], unique = true),
        Index(value = ["starts_on", "ends_on"]),
        Index("status"),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class MealPlanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "page_id") val pageId: String,
    val name: String,
    @ColumnInfo(name = "starts_on") val startsOn: String,
    @ColumnInfo(name = "ends_on") val endsOn: String,
    val status: MealPlanStatus,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "plan_entries",
    foreignKeys = [
        ForeignKey(entity = MealPlanEntity::class, parentColumns = ["id"], childColumns = ["meal_plan_id"], onDelete = NO_ACTION),
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipe_id"], onDelete = NO_ACTION),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = NO_ACTION),
    ],
    indices = [
        Index("meal_plan_id"),
        Index("recipe_id"),
        Index("food_id"),
        Index(value = ["date", "meal_slot"]),
        Index("status"),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class PlanEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "meal_plan_id") val mealPlanId: String,
    val date: String,
    @ColumnInfo(name = "meal_slot") val mealSlot: MealSlot,
    @ColumnInfo(name = "recipe_id") val recipeId: String?,
    @ColumnInfo(name = "food_id") val foodId: String?,
    @Embedded val quantity: QuantityColumns,
    val status: PlanEntryStatus,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "meal_logs",
    foreignKeys = [
        ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["page_id"], onDelete = NO_ACTION),
        ForeignKey(entity = PlanEntryEntity::class, parentColumns = ["id"], childColumns = ["plan_entry_id"], onDelete = NO_ACTION),
    ],
    indices = [
        Index("page_id"),
        Index("plan_entry_id"),
        Index("occurred_at"),
        Index("meal_slot"),
        Index("status"),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class MealLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "page_id") val pageId: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: String,
    @ColumnInfo(name = "meal_slot") val mealSlot: MealSlot,
    @ColumnInfo(name = "plan_entry_id") val planEntryId: String?,
    @ColumnInfo(name = "food_ids") val foodIds: List<String>,
    @ColumnInfo(name = "recipe_ids") val recipeIds: List<String>,
    @ColumnInfo(name = "nutrition_snapshot_ids") val nutritionSnapshotIds: List<String>,
    val status: MealLogStatus,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "meal_log_foods",
    primaryKeys = ["meal_log_id", "food_id"],
    foreignKeys = [
        ForeignKey(entity = MealLogEntity::class, parentColumns = ["id"], childColumns = ["meal_log_id"], onDelete = NO_ACTION),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = NO_ACTION),
    ],
    indices = [Index("meal_log_id"), Index("food_id")],
)
data class MealLogFoodEntity(
    @ColumnInfo(name = "meal_log_id") val mealLogId: String,
    @ColumnInfo(name = "food_id") val foodId: String,
)

@Entity(
    tableName = "meal_log_recipes",
    primaryKeys = ["meal_log_id", "recipe_id"],
    foreignKeys = [
        ForeignKey(entity = MealLogEntity::class, parentColumns = ["id"], childColumns = ["meal_log_id"], onDelete = NO_ACTION),
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipe_id"], onDelete = NO_ACTION),
    ],
    indices = [Index("meal_log_id"), Index("recipe_id")],
)
data class MealLogRecipeEntity(
    @ColumnInfo(name = "meal_log_id") val mealLogId: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
)

@Entity(
    tableName = "meal_log_nutrition_snapshots",
    primaryKeys = ["meal_log_id", "nutrition_snapshot_id"],
    foreignKeys = [
        ForeignKey(entity = MealLogEntity::class, parentColumns = ["id"], childColumns = ["meal_log_id"], onDelete = NO_ACTION),
        ForeignKey(entity = NutritionSnapshotEntity::class, parentColumns = ["id"], childColumns = ["nutrition_snapshot_id"], onDelete = NO_ACTION),
    ],
    indices = [Index("meal_log_id"), Index("nutrition_snapshot_id")],
)
data class MealLogNutritionSnapshotEntity(
    @ColumnInfo(name = "meal_log_id") val mealLogId: String,
    @ColumnInfo(name = "nutrition_snapshot_id") val nutritionSnapshotId: String,
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["page_id"], onDelete = NO_ACTION),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = NO_ACTION),
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipe_id"], onDelete = NO_ACTION),
    ],
    indices = [
        Index("page_id"),
        Index("food_id"),
        Index("recipe_id"),
        Index("status"),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "page_id") val pageId: String,
    @ColumnInfo(name = "food_id") val foodId: String?,
    @ColumnInfo(name = "recipe_id") val recipeId: String?,
    @Embedded val quantity: QuantityColumns,
    val reason: String?,
    val status: ShoppingItemStatus,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["page_id"], onDelete = NO_ACTION),
    ],
    indices = [
        Index("page_id"),
        Index("purchased_at"),
        Index("status"),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "page_id") val pageId: String,
    @ColumnInfo(name = "merchant_name") val merchantName: String?,
    @ColumnInfo(name = "purchased_at") val purchasedAt: String?,
    @ColumnInfo(name = "item_ids") val itemIds: List<String>,
    @Embedded(prefix = "subtotal_") val subtotal: MoneyColumns?,
    @Embedded(prefix = "total_") val total: MoneyColumns?,
    @ColumnInfo(name = "attachment_ids") val attachmentIds: List<String>,
    val status: ReceiptStatus,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "receipt_items",
    primaryKeys = ["receipt_id", "shopping_item_id"],
    foreignKeys = [
        ForeignKey(entity = ReceiptEntity::class, parentColumns = ["id"], childColumns = ["receipt_id"], onDelete = NO_ACTION),
        ForeignKey(entity = ShoppingItemEntity::class, parentColumns = ["id"], childColumns = ["shopping_item_id"], onDelete = NO_ACTION),
    ],
    indices = [Index("receipt_id"), Index("shopping_item_id")],
)
data class ReceiptItemEntity(
    @ColumnInfo(name = "receipt_id") val receiptId: String,
    @ColumnInfo(name = "shopping_item_id") val shoppingItemId: String,
)

@Entity(
    tableName = "food_events",
    indices = [
        Index(value = ["subject_type", "subject_id"]),
        Index("type"),
        Index("occurred_at"),
    ],
)
data class FoodEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "subject_type") val subjectType: EntityType,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    val type: FoodEventType,
    @ColumnInfo(name = "occurred_at") val occurredAt: String,
    @Embedded(prefix = "event_quantity_") val quantity: QuantityColumns?,
    val note: String?,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
)

@Entity(
    tableName = "food_actions",
    indices = [
        Index(value = ["subject_type", "subject_id"]),
        Index(value = ["idempotency_key"], unique = true),
        Index("action_type"),
        Index("occurred_at"),
    ],
)
data class FoodActionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "subject_type") val subjectType: EntityType,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: String,
    val payload: String,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
)

@Entity(
    tableName = "relations",
    indices = [
        Index(value = ["from_type", "from_id"]),
        Index(value = ["to_type", "to_id"]),
        Index("type"),
        Index(value = ["from_type", "from_id", "to_type", "to_id", "type"], unique = true),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class RelationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "from_type") val fromType: EntityType,
    @ColumnInfo(name = "from_id") val fromId: String,
    @ColumnInfo(name = "to_type") val toType: EntityType,
    @ColumnInfo(name = "to_id") val toId: String,
    val type: RelationType,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "attachments",
    indices = [
        Index("kind"),
        Index(value = ["uri"], unique = true),
        Index("checksum"),
        Index("archived_at"),
        Index("deleted_at"),
    ],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val kind: AttachmentKind,
    val uri: String,
    val label: String?,
    val checksum: String?,
    @Embedded(prefix = "source_") val source: SourceColumns,
    @Embedded(prefix = "confidence_") val confidence: ConfidenceColumns,
    @ColumnInfo(name = "truth_state") val truthState: TruthState,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)
