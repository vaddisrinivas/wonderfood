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
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(WonderFoodRoomConverters::class)
abstract class WonderFoodDatabase : RoomDatabase() {
    internal abstract fun wonderFoodDao(): WonderFoodDao

    suspend fun <T> inTransaction(block: suspend () -> T): T =
        withTransaction(block)
}
