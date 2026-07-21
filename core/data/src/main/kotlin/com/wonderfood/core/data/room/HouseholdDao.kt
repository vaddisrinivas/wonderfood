package com.wonderfood.core.data.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
internal abstract class HouseholdDao {
    @Upsert abstract suspend fun upsertHousehold(entity: HouseholdEntity)
    @Upsert abstract suspend fun upsertProfile(entity: HouseholdProfileEntity)
    @Upsert abstract suspend fun upsertItem(entity: HouseholdItemEntity)
    @Upsert abstract suspend fun upsertFoodDetails(entity: HouseholdFoodDetailsEntity)
    @Upsert abstract suspend fun upsertStorageLocation(entity: HouseholdStorageLocationEntity)
    @Upsert abstract suspend fun upsertInventoryLot(entity: HouseholdInventoryLotEntity)
    @Upsert abstract suspend fun upsertInventoryEvent(entity: HouseholdInventoryEventEntity)
    @Upsert abstract suspend fun upsertShoppingList(entity: HouseholdShoppingListEntity)
    @Upsert abstract suspend fun upsertShoppingLine(entity: HouseholdShoppingLineEntity)
    @Upsert abstract suspend fun upsertRecipe(entity: HouseholdRecipeEntity)
    @Upsert abstract suspend fun upsertRecipeIngredient(entity: HouseholdRecipeIngredientEntity)
    @Upsert abstract suspend fun upsertRecipeStep(entity: HouseholdRecipeStepEntity)
    @Upsert abstract suspend fun upsertCookingSession(entity: HouseholdCookingSessionEntity)
    @Upsert abstract suspend fun upsertPreparedBatch(entity: HouseholdPreparedBatchEntity)
    @Upsert abstract suspend fun upsertMerchant(entity: HouseholdMerchantEntity)
    @Upsert abstract suspend fun upsertPurchase(entity: HouseholdPurchaseEntity)
    @Upsert abstract suspend fun upsertPurchaseLine(entity: HouseholdPurchaseLineEntity)
    @Upsert abstract suspend fun upsertWasteEvent(entity: HouseholdWasteEventEntity)
    @Upsert abstract suspend fun upsertMealPlan(entity: HouseholdMealPlanEntity)
    @Upsert abstract suspend fun upsertMealEntry(entity: HouseholdMealEntryEntity)
    @Upsert abstract suspend fun upsertNutritionSnapshot(entity: HouseholdNutritionSnapshotEntity)
    @Upsert abstract suspend fun upsertChangeProposal(entity: HouseholdChangeProposalEntity)
    @Upsert abstract suspend fun upsertSyncOutbox(entity: HouseholdSyncOutboxEntity)
    @Upsert abstract suspend fun upsertRemoteBinding(entity: HouseholdRemoteBindingEntity)
    @Upsert abstract suspend fun upsertSyncBase(entity: HouseholdSyncBaseEntity)
    @Upsert abstract suspend fun upsertSyncCursor(entity: HouseholdSyncCursorEntity)
    @Upsert abstract suspend fun upsertConflict(entity: HouseholdConflictEntity)
    @Upsert abstract suspend fun upsertLatestSafetySnapshot(entity: HouseholdLatestSafetySnapshotEntity)
    @Upsert abstract suspend fun upsertRecoverySnapshot(entity: HouseholdRecoverySnapshotEntity)
    @Upsert abstract suspend fun upsertTombstone(entity: HouseholdTombstoneEntity)
    @Upsert abstract suspend fun upsertCommandRecord(entity: HouseholdCommandRecordEntity)

    @Query("SELECT * FROM households WHERE id = :householdId")
    abstract suspend fun getHousehold(householdId: String): HouseholdEntity?

    @Query("SELECT * FROM household_items WHERE household_id = :householdId ORDER BY name")
    abstract suspend fun getItems(householdId: String): List<HouseholdItemEntity>

    @Query("SELECT * FROM household_profiles WHERE household_id = :householdId ORDER BY display_name")
    abstract suspend fun getProfiles(householdId: String): List<HouseholdProfileEntity>

    @Query("SELECT * FROM household_food_details WHERE household_id = :householdId ORDER BY updated_at DESC")
    abstract suspend fun getFoodDetails(householdId: String): List<HouseholdFoodDetailsEntity>

    @Query("SELECT * FROM household_storage_locations WHERE household_id = :householdId ORDER BY sort_order, name")
    abstract suspend fun getStorageLocations(householdId: String): List<HouseholdStorageLocationEntity>

    @Query(
        """
        SELECT * FROM household_items
        WHERE household_id = :householdId
          AND archived_at IS NULL
          AND (
            LOWER(name) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(category, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(brand, '')) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(COALESCE(notes, '')) LIKE '%' || LOWER(:query) || '%'
          )
        ORDER BY name
        """,
    )
    abstract suspend fun searchItems(householdId: String, query: String): List<HouseholdItemEntity>

    @Query("SELECT * FROM household_inventory_lots WHERE household_id = :householdId ORDER BY updated_at DESC")
    abstract suspend fun getInventoryLots(householdId: String): List<HouseholdInventoryLotEntity>

    @Query("SELECT * FROM household_inventory_events WHERE household_id = :householdId ORDER BY updated_at ASC")
    abstract suspend fun getInventoryEvents(householdId: String): List<HouseholdInventoryEventEntity>

    @Query("SELECT * FROM household_shopping_lists WHERE household_id = :householdId ORDER BY updated_at DESC")
    abstract suspend fun getShoppingLists(householdId: String): List<HouseholdShoppingListEntity>

    @Query("SELECT * FROM household_shopping_lines WHERE household_id = :householdId ORDER BY updated_at DESC")
    abstract suspend fun getShoppingLines(householdId: String): List<HouseholdShoppingLineEntity>

    @Query("SELECT * FROM household_recipes WHERE household_id = :householdId ORDER BY updated_at DESC")
    abstract suspend fun getRecipes(householdId: String): List<HouseholdRecipeEntity>

    @Query("SELECT * FROM household_recipe_ingredients WHERE household_id = :householdId ORDER BY recipe_id, sort_order")
    abstract suspend fun getRecipeIngredients(householdId: String): List<HouseholdRecipeIngredientEntity>

    @Query("SELECT * FROM household_recipe_steps WHERE household_id = :householdId ORDER BY recipe_id, sort_order")
    abstract suspend fun getRecipeSteps(householdId: String): List<HouseholdRecipeStepEntity>

    @Query("SELECT * FROM household_cooking_sessions WHERE household_id = :householdId ORDER BY updated_at DESC")
    abstract suspend fun getCookingSessions(householdId: String): List<HouseholdCookingSessionEntity>

    @Query("SELECT * FROM household_prepared_batches WHERE household_id = :householdId ORDER BY prepared_at DESC")
    abstract suspend fun getPreparedBatches(householdId: String): List<HouseholdPreparedBatchEntity>

    @Query("SELECT * FROM household_merchants WHERE household_id = :householdId ORDER BY name")
    abstract suspend fun getMerchants(householdId: String): List<HouseholdMerchantEntity>

    @Query("SELECT * FROM household_purchases WHERE household_id = :householdId ORDER BY occurred_at DESC")
    abstract suspend fun getPurchases(householdId: String): List<HouseholdPurchaseEntity>

    @Query("SELECT * FROM household_purchase_lines WHERE household_id = :householdId ORDER BY updated_at DESC")
    abstract suspend fun getPurchaseLines(householdId: String): List<HouseholdPurchaseLineEntity>

    @Query("SELECT * FROM household_waste_events WHERE household_id = :householdId ORDER BY occurred_at DESC")
    abstract suspend fun getWasteEvents(householdId: String): List<HouseholdWasteEventEntity>

    @Query("SELECT * FROM household_meal_plans WHERE household_id = :householdId ORDER BY starts_on DESC")
    abstract suspend fun getMealPlans(householdId: String): List<HouseholdMealPlanEntity>

    @Query("SELECT * FROM household_meal_entries WHERE household_id = :householdId ORDER BY scheduled_at DESC")
    abstract suspend fun getMealEntries(householdId: String): List<HouseholdMealEntryEntity>

    @Query("SELECT * FROM household_nutrition_snapshots WHERE household_id = :householdId ORDER BY captured_at DESC")
    abstract suspend fun getNutritionSnapshots(householdId: String): List<HouseholdNutritionSnapshotEntity>

    @Query("SELECT * FROM household_change_proposals WHERE household_id = :householdId ORDER BY updated_at DESC")
    abstract suspend fun getChangeProposals(householdId: String): List<HouseholdChangeProposalEntity>

    @Query("SELECT * FROM household_sync_outbox WHERE household_id = :householdId ORDER BY updated_at ASC")
    abstract suspend fun getSyncOutbox(householdId: String): List<HouseholdSyncOutboxEntity>

    @Query("SELECT * FROM household_remote_bindings WHERE household_id = :householdId ORDER BY entity_type, entity_id")
    abstract suspend fun getRemoteBindings(householdId: String): List<HouseholdRemoteBindingEntity>

    @Query("SELECT * FROM household_sync_bases WHERE household_id = :householdId ORDER BY pulled_at ASC")
    abstract suspend fun getSyncBases(householdId: String): List<HouseholdSyncBaseEntity>

    @Query("SELECT * FROM household_sync_cursors WHERE household_id = :householdId ORDER BY pulled_at ASC")
    abstract suspend fun getSyncCursors(householdId: String): List<HouseholdSyncCursorEntity>

    @Query("SELECT * FROM household_conflicts WHERE household_id = :householdId ORDER BY updated_at ASC")
    abstract suspend fun getConflicts(householdId: String): List<HouseholdConflictEntity>

    @Query("SELECT * FROM household_latest_safety_snapshots WHERE household_id = :householdId ORDER BY created_at DESC")
    abstract suspend fun getLatestSafetySnapshots(householdId: String): List<HouseholdLatestSafetySnapshotEntity>

    @Query("SELECT * FROM household_recovery_snapshots WHERE household_id = :householdId ORDER BY created_at DESC")
    abstract suspend fun getRecoverySnapshots(householdId: String): List<HouseholdRecoverySnapshotEntity>

    @Query("SELECT * FROM household_tombstones WHERE household_id = :householdId ORDER BY updated_at ASC")
    abstract suspend fun getTombstones(householdId: String): List<HouseholdTombstoneEntity>

    @Query("SELECT * FROM household_command_records WHERE id = :commandId")
    abstract suspend fun getCommandRecord(commandId: String): HouseholdCommandRecordEntity?

    @Query("SELECT * FROM household_command_records WHERE household_id = :householdId ORDER BY requested_at ASC")
    abstract suspend fun getCommandRecords(householdId: String): List<HouseholdCommandRecordEntity>

    @Transaction
    open suspend fun upsertHouseholdAndRecord(household: HouseholdEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertHousehold(household)
        }

    @Transaction
    open suspend fun upsertProfileAndRecord(profile: HouseholdProfileEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertProfile(profile)
        }

    @Transaction
    open suspend fun upsertItemAndRecord(item: HouseholdItemEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertItem(item)
        }

    @Transaction
    open suspend fun upsertFoodDetailsAndRecord(details: HouseholdFoodDetailsEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertFoodDetails(details)
        }

    @Transaction
    open suspend fun upsertStorageLocationAndRecord(
        location: HouseholdStorageLocationEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertStorageLocation(location)
    }

    @Transaction
    open suspend fun upsertInventoryLotAndRecord(lot: HouseholdInventoryLotEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertInventoryLot(lot)
        }

    @Transaction
    open suspend fun upsertInventoryEventAndRecord(
        event: HouseholdInventoryEventEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertInventoryEvent(event)
    }

    @Transaction
    open suspend fun upsertShoppingListAndRecord(list: HouseholdShoppingListEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertShoppingList(list)
        }

    @Transaction
    open suspend fun upsertShoppingLineAndRecord(line: HouseholdShoppingLineEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertShoppingLine(line)
        }

    @Transaction
    open suspend fun upsertRecipeAndRecord(recipe: HouseholdRecipeEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertRecipe(recipe)
        }

    @Transaction
    open suspend fun upsertRecipeIngredientAndRecord(
        ingredient: HouseholdRecipeIngredientEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertRecipeIngredient(ingredient)
    }

    @Transaction
    open suspend fun upsertRecipeStepAndRecord(
        step: HouseholdRecipeStepEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertRecipeStep(step)
    }

    @Transaction
    open suspend fun upsertCookingSessionAndRecord(
        session: HouseholdCookingSessionEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertCookingSession(session)
    }

    @Transaction
    open suspend fun upsertPreparedBatchAndRecord(
        batch: HouseholdPreparedBatchEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertPreparedBatch(batch)
    }

    @Transaction
    open suspend fun upsertMerchantAndRecord(merchant: HouseholdMerchantEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertMerchant(merchant)
        }

    @Transaction
    open suspend fun upsertPurchaseAndRecord(purchase: HouseholdPurchaseEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertPurchase(purchase)
        }

    @Transaction
    open suspend fun upsertPurchaseLineAndRecord(
        line: HouseholdPurchaseLineEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertPurchaseLine(line)
    }

    @Transaction
    open suspend fun upsertWasteEventAndRecord(event: HouseholdWasteEventEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertWasteEvent(event)
        }

    @Transaction
    open suspend fun upsertMealPlanAndRecord(
        plan: HouseholdMealPlanEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertMealPlan(plan)
    }

    @Transaction
    open suspend fun upsertMealEntryAndRecord(
        entry: HouseholdMealEntryEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertMealEntry(entry)
    }

    @Transaction
    open suspend fun upsertNutritionSnapshotAndRecord(
        snapshot: HouseholdNutritionSnapshotEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertNutritionSnapshot(snapshot)
    }

    @Transaction
    open suspend fun upsertChangeProposalAndRecord(
        proposal: HouseholdChangeProposalEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertChangeProposal(proposal)
    }

    @Transaction
    open suspend fun upsertSyncOutboxAndRecord(outbox: HouseholdSyncOutboxEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertSyncOutbox(outbox)
        }

    @Transaction
    open suspend fun upsertRemoteBindingAndRecord(
        binding: HouseholdRemoteBindingEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertRemoteBinding(binding)
    }

    @Transaction
    open suspend fun upsertSyncBaseAndRecord(base: HouseholdSyncBaseEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertSyncBase(base)
        }

    @Transaction
    open suspend fun upsertSyncCursorAndRecord(cursor: HouseholdSyncCursorEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertSyncCursor(cursor)
        }

    @Transaction
    open suspend fun upsertConflictAndRecord(conflict: HouseholdConflictEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertConflict(conflict)
        }

    @Transaction
    open suspend fun upsertLatestSafetySnapshotAndRecord(
        safety: HouseholdLatestSafetySnapshotEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertLatestSafetySnapshot(safety)
    }

    @Transaction
    open suspend fun upsertRecoverySnapshotAndRecord(
        snapshot: HouseholdRecoverySnapshotEntity,
        record: HouseholdCommandRecordEntity,
    ): Boolean = recordOnce(record) {
        upsertRecoverySnapshot(snapshot)
    }

    @Transaction
    open suspend fun upsertTombstoneAndRecord(tombstone: HouseholdTombstoneEntity, record: HouseholdCommandRecordEntity): Boolean =
        recordOnce(record) {
            upsertTombstone(tombstone)
        }

    private suspend fun recordOnce(record: HouseholdCommandRecordEntity, apply: suspend () -> Unit): Boolean {
        if (getCommandRecord(record.id) != null) return false
        apply()
        upsertCommandRecord(record)
        return true
    }
}
