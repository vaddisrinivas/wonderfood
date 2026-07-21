package com.wonderfood.core.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction

@Database(
    entities = [
        SourceRecordEntity::class,
        PageEntity::class,
        FoodEntity::class,
        FoodAliasEntity::class,
        StockLotEntity::class,
        NutritionSnapshotEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        RecipeIngredientSubstituteEntity::class,
        RecipeStepEntity::class,
        MealPlanEntity::class,
        PlanEntryEntity::class,
        MealLogEntity::class,
        MealLogFoodEntity::class,
        MealLogRecipeEntity::class,
        MealLogNutritionSnapshotEntity::class,
        ShoppingItemEntity::class,
        ReceiptEntity::class,
        ReceiptItemEntity::class,
        FoodEventEntity::class,
        FoodActionEntity::class,
        RelationEntity::class,
        AttachmentEntity::class,
        HouseholdEntity::class,
        HouseholdProfileEntity::class,
        HouseholdItemEntity::class,
        HouseholdFoodDetailsEntity::class,
        HouseholdStorageLocationEntity::class,
        HouseholdInventoryLotEntity::class,
        HouseholdInventoryEventEntity::class,
        HouseholdShoppingListEntity::class,
        HouseholdShoppingLineEntity::class,
        HouseholdRecipeEntity::class,
        HouseholdRecipeIngredientEntity::class,
        HouseholdRecipeStepEntity::class,
        HouseholdCookingSessionEntity::class,
        HouseholdPreparedBatchEntity::class,
        HouseholdMerchantEntity::class,
        HouseholdPurchaseEntity::class,
        HouseholdPurchaseLineEntity::class,
        HouseholdWasteEventEntity::class,
        HouseholdMealPlanEntity::class,
        HouseholdMealEntryEntity::class,
        HouseholdNutritionSnapshotEntity::class,
        HouseholdChangeProposalEntity::class,
        HouseholdCommandRecordEntity::class,
        HouseholdSyncOutboxEntity::class,
        HouseholdRemoteBindingEntity::class,
        HouseholdSyncBaseEntity::class,
        HouseholdSyncCursorEntity::class,
        HouseholdConflictEntity::class,
        HouseholdLatestSafetySnapshotEntity::class,
        HouseholdRecoverySnapshotEntity::class,
        HouseholdTombstoneEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(WonderFoodRoomConverters::class)
abstract class WonderFoodDatabase : RoomDatabase() {
    internal abstract fun wonderFoodDao(): WonderFoodDao
    internal abstract fun householdDao(): HouseholdDao

    suspend fun <T> inTransaction(block: suspend () -> T): T =
        withTransaction(block)

    companion object {
        const val SCHEMA_VERSION = 10
    }
}
