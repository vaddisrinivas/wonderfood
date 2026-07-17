package com.wonderfood.core.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class WonderFoodDao {
    @Upsert abstract suspend fun upsertSource(entity: SourceRecordEntity)
    @Query("SELECT * FROM sources WHERE id = :id") abstract fun observeSource(id: String): Flow<SourceRecordEntity?>
    @Query("SELECT * FROM sources ORDER BY label") abstract fun observeSources(): Flow<List<SourceRecordEntity>>
    @Query("UPDATE sources SET archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveSource(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertPage(entity: PageEntity)
    @Query("SELECT * FROM pages WHERE id = :id") abstract suspend fun getPage(id: String): PageEntity?
    @Query("SELECT * FROM pages WHERE id = :id") abstract fun observePage(id: String): Flow<PageEntity?>
    @Query("SELECT * FROM pages WHERE deleted_at IS NULL ORDER BY updated_at DESC") abstract fun observePages(): Flow<List<PageEntity>>
    @Query("UPDATE pages SET archived_at = :archivedAt WHERE id = :id") abstract suspend fun archivePage(id: String, archivedAt: String): Int
    @Query("UPDATE pages SET deleted_at = :deletedAt WHERE id = :id") abstract suspend fun tombstonePage(id: String, deletedAt: String): Int

    @Upsert abstract suspend fun upsertFood(entity: FoodEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertFood(entity: FoodEntity)
    @Query("SELECT * FROM foods WHERE id = :id") abstract suspend fun getFood(id: String): FoodEntity?
    @Query("SELECT * FROM foods WHERE id = :id") abstract fun observeFood(id: String): Flow<FoodEntity?>
    @Query("SELECT * FROM foods WHERE deleted_at IS NULL ORDER BY name") abstract fun observeFoods(): Flow<List<FoodEntity>>
    @Query("SELECT * FROM foods WHERE page_id = :pageId") abstract fun observeFoodForPage(pageId: String): Flow<FoodEntity?>
    @Query("UPDATE foods SET status = 'ARCHIVED', archived_at = :archivedAt, updated_at = :archivedAt WHERE id = :id") abstract suspend fun archiveFood(id: String, archivedAt: String): Int
    @Query("UPDATE foods SET status = 'ACTIVE', archived_at = NULL, updated_at = :updatedAt WHERE id = :id") abstract suspend fun restoreFood(id: String, updatedAt: String): Int
    @Query("UPDATE foods SET deleted_at = :deletedAt WHERE id = :id") abstract suspend fun tombstoneFood(id: String, deletedAt: String): Int

    @Upsert abstract suspend fun upsertFoodAlias(entity: FoodAliasEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertFoodAlias(entity: FoodAliasEntity)
    @Query("SELECT * FROM food_aliases WHERE id = :id") abstract fun observeFoodAlias(id: String): Flow<FoodAliasEntity?>
    @Query("SELECT * FROM food_aliases WHERE food_id = :foodId AND deleted_at IS NULL ORDER BY name") abstract fun observeAliasesForFood(foodId: String): Flow<List<FoodAliasEntity>>
    @Query("UPDATE food_aliases SET archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveFoodAlias(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertStockLot(entity: StockLotEntity)
    @Query("SELECT * FROM stock_lots WHERE id = :id") abstract suspend fun getStockLot(id: String): StockLotEntity?
    @Query("SELECT * FROM stock_lots WHERE id = :id") abstract fun observeStockLot(id: String): Flow<StockLotEntity?>
    @Query("SELECT * FROM stock_lots WHERE food_id = :foodId AND deleted_at IS NULL ORDER BY expires_on") abstract fun observeStockLotsForFood(foodId: String): Flow<List<StockLotEntity>>
    @Query("UPDATE stock_lots SET status = 'ARCHIVED', archived_at = :archivedAt, updated_at = :archivedAt WHERE id = :id") abstract suspend fun archiveStockLot(id: String, archivedAt: String): Int
    @Query("UPDATE stock_lots SET status = 'AVAILABLE', archived_at = NULL, updated_at = :updatedAt WHERE id = :id") abstract suspend fun restoreStockLot(id: String, updatedAt: String): Int

    @Upsert abstract suspend fun upsertNutritionSnapshot(entity: NutritionSnapshotEntity)
    @Query("SELECT * FROM nutrition_snapshots WHERE id = :id") abstract fun observeNutritionSnapshot(id: String): Flow<NutritionSnapshotEntity?>
    @Query("SELECT * FROM nutrition_snapshots WHERE subject_type = :subjectType AND subject_id = :subjectId AND deleted_at IS NULL ORDER BY captured_at DESC") abstract fun observeNutritionForSubject(subjectType: String, subjectId: String): Flow<List<NutritionSnapshotEntity>>
    @Query("UPDATE nutrition_snapshots SET archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveNutritionSnapshot(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertRecipe(entity: RecipeEntity)
    @Query("SELECT * FROM recipes WHERE id = :id") abstract fun observeRecipe(id: String): Flow<RecipeEntity?>
    @Query("SELECT * FROM recipes WHERE deleted_at IS NULL ORDER BY title") abstract fun observeRecipes(): Flow<List<RecipeEntity>>
    @Query("UPDATE recipes SET status = 'ARCHIVED', archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveRecipe(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertRecipeIngredient(entity: RecipeIngredientEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertRecipeIngredientSubstitute(entity: RecipeIngredientSubstituteEntity)
    @Query("SELECT * FROM recipe_ingredients WHERE id = :id") abstract fun observeRecipeIngredient(id: String): Flow<RecipeIngredientEntity?>
    @Query("SELECT * FROM recipe_ingredients WHERE recipe_id = :recipeId AND deleted_at IS NULL ORDER BY display_name") abstract fun observeIngredientsForRecipe(recipeId: String): Flow<List<RecipeIngredientEntity>>
    @Query("SELECT * FROM recipe_ingredient_substitutes WHERE recipe_ingredient_id = :ingredientId ORDER BY food_id") abstract fun observeSubstitutesForIngredient(ingredientId: String): Flow<List<RecipeIngredientSubstituteEntity>>
    @Query("UPDATE recipe_ingredients SET archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveRecipeIngredient(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertRecipeStep(entity: RecipeStepEntity)
    @Query("SELECT * FROM recipe_steps WHERE id = :id") abstract fun observeRecipeStep(id: String): Flow<RecipeStepEntity?>
    @Query("SELECT * FROM recipe_steps WHERE recipe_id = :recipeId AND deleted_at IS NULL ORDER BY step_order") abstract fun observeStepsForRecipe(recipeId: String): Flow<List<RecipeStepEntity>>
    @Query("UPDATE recipe_steps SET archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveRecipeStep(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertMealPlan(entity: MealPlanEntity)
    @Query("SELECT * FROM meal_plans WHERE id = :id") abstract fun observeMealPlan(id: String): Flow<MealPlanEntity?>
    @Query("SELECT * FROM meal_plans WHERE deleted_at IS NULL ORDER BY starts_on DESC") abstract fun observeMealPlans(): Flow<List<MealPlanEntity>>
    @Query("UPDATE meal_plans SET status = 'ARCHIVED', archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveMealPlan(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertPlanEntry(entity: PlanEntryEntity)
    @Query("SELECT * FROM plan_entries WHERE id = :id") abstract fun observePlanEntry(id: String): Flow<PlanEntryEntity?>
    @Query("SELECT * FROM plan_entries WHERE meal_plan_id = :mealPlanId AND deleted_at IS NULL ORDER BY date, meal_slot") abstract fun observeEntriesForMealPlan(mealPlanId: String): Flow<List<PlanEntryEntity>>
    @Query("UPDATE plan_entries SET status = 'ARCHIVED', archived_at = :archivedAt WHERE id = :id") abstract suspend fun archivePlanEntry(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertMealLog(entity: MealLogEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertMealLogFood(entity: MealLogFoodEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertMealLogRecipe(entity: MealLogRecipeEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertMealLogNutritionSnapshot(entity: MealLogNutritionSnapshotEntity)
    @Query("SELECT * FROM meal_logs WHERE id = :id") abstract fun observeMealLog(id: String): Flow<MealLogEntity?>
    @Query("SELECT * FROM meal_logs WHERE deleted_at IS NULL ORDER BY occurred_at DESC") abstract fun observeMealLogs(): Flow<List<MealLogEntity>>
    @Query("UPDATE meal_logs SET status = 'ARCHIVED', archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveMealLog(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertShoppingItem(entity: ShoppingItemEntity)
    @Query("SELECT * FROM shopping_items WHERE id = :id") abstract fun observeShoppingItem(id: String): Flow<ShoppingItemEntity?>
    @Query("SELECT * FROM shopping_items WHERE deleted_at IS NULL ORDER BY updated_at DESC") abstract fun observeShoppingItems(): Flow<List<ShoppingItemEntity>>
    @Query("UPDATE shopping_items SET status = 'ARCHIVED', archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveShoppingItem(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertReceipt(entity: ReceiptEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertReceiptItem(entity: ReceiptItemEntity)
    @Query("SELECT * FROM receipts WHERE id = :id") abstract fun observeReceipt(id: String): Flow<ReceiptEntity?>
    @Query("SELECT * FROM receipts WHERE deleted_at IS NULL ORDER BY purchased_at DESC") abstract fun observeReceipts(): Flow<List<ReceiptEntity>>
    @Query("UPDATE receipts SET status = 'ARCHIVED', archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveReceipt(id: String, archivedAt: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertFoodEvent(entity: FoodEventEntity)
    @Query("SELECT * FROM food_events WHERE id = :id") abstract fun observeFoodEvent(id: String): Flow<FoodEventEntity?>
    @Query("SELECT * FROM food_events WHERE subject_type = :subjectType AND subject_id = :subjectId ORDER BY occurred_at") abstract fun observeFoodEventsForSubject(subjectType: String, subjectId: String): Flow<List<FoodEventEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertFoodAction(entity: FoodActionEntity)
    @Query("SELECT * FROM food_actions WHERE id = :id") abstract suspend fun getFoodAction(id: String): FoodActionEntity?
    @Query("SELECT * FROM food_actions WHERE idempotency_key = :idempotencyKey") abstract suspend fun getFoodActionByIdempotencyKey(idempotencyKey: String): FoodActionEntity?
    @Query("SELECT * FROM food_actions WHERE id = :id") abstract fun observeFoodAction(id: String): Flow<FoodActionEntity?>
    @Query("SELECT * FROM food_actions WHERE subject_type = :subjectType AND subject_id = :subjectId ORDER BY occurred_at") abstract fun observeFoodActionsForSubject(subjectType: String, subjectId: String): Flow<List<FoodActionEntity>>

    @Upsert abstract suspend fun upsertRelation(entity: RelationEntity)
    @Query("SELECT * FROM relations WHERE id = :id") abstract fun observeRelation(id: String): Flow<RelationEntity?>
    @Query("SELECT * FROM relations WHERE from_type = :fromType AND from_id = :fromId AND deleted_at IS NULL ORDER BY type") abstract fun observeRelationsFrom(fromType: String, fromId: String): Flow<List<RelationEntity>>
    @Query("SELECT * FROM relations WHERE to_type = :toType AND to_id = :toId AND deleted_at IS NULL ORDER BY type") abstract fun observeRelationsTo(toType: String, toId: String): Flow<List<RelationEntity>>
    @Query("UPDATE relations SET archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveRelation(id: String, archivedAt: String): Int

    @Upsert abstract suspend fun upsertAttachment(entity: AttachmentEntity)
    @Query("SELECT * FROM attachments WHERE id = :id") abstract fun observeAttachment(id: String): Flow<AttachmentEntity?>
    @Query("SELECT * FROM attachments WHERE deleted_at IS NULL ORDER BY updated_at DESC") abstract fun observeAttachments(): Flow<List<AttachmentEntity>>
    @Query("UPDATE attachments SET archived_at = :archivedAt WHERE id = :id") abstract suspend fun archiveAttachment(id: String, archivedAt: String): Int

    @Transaction
    open suspend fun upsertFoodWithPage(page: PageEntity, food: FoodEntity, aliases: List<FoodAliasEntity>, lots: List<StockLotEntity>) {
        upsertPage(page)
        upsertFood(food)
        aliases.forEach { upsertFoodAlias(it) }
        lots.forEach { upsertStockLot(it) }
    }

    @Transaction
    open suspend fun upsertRecipeGraph(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>, steps: List<RecipeStepEntity>) {
        upsertRecipe(recipe)
        ingredients.forEach { upsertRecipeIngredient(it) }
        steps.forEach { upsertRecipeStep(it) }
    }

    @Transaction
    open suspend fun upsertMealLogGraph(
        mealLog: MealLogEntity,
        foods: List<MealLogFoodEntity>,
        recipes: List<MealLogRecipeEntity>,
        nutritionSnapshots: List<MealLogNutritionSnapshotEntity>,
    ) {
        upsertMealLog(mealLog)
        foods.forEach { insertMealLogFood(it) }
        recipes.forEach { insertMealLogRecipe(it) }
        nutritionSnapshots.forEach { insertMealLogNutritionSnapshot(it) }
    }
}
