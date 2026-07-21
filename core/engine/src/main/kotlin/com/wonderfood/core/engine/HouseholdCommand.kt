package com.wonderfood.core.engine

import com.wonderfood.core.model.household.ChangeProposal
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.CookingSession
import com.wonderfood.core.model.household.FoodDetails
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.InventoryEvent
import com.wonderfood.core.model.household.InventoryLot
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.Merchant
import com.wonderfood.core.model.household.MealEntry
import com.wonderfood.core.model.household.MealPlan
import com.wonderfood.core.model.household.NutritionSnapshot
import com.wonderfood.core.model.household.PreparedBatch
import com.wonderfood.core.model.household.Profile
import com.wonderfood.core.model.household.Purchase
import com.wonderfood.core.model.household.PurchaseLine
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeIngredient
import com.wonderfood.core.model.household.RecipeStep
import com.wonderfood.core.model.household.RecoverySnapshot
import com.wonderfood.core.model.household.ConflictRecord
import com.wonderfood.core.model.household.Attachment
import com.wonderfood.core.model.household.LatestSafetySnapshot
import com.wonderfood.core.model.household.RemoteBinding
import com.wonderfood.core.model.household.ShoppingList
import com.wonderfood.core.model.household.ShoppingLine
import com.wonderfood.core.model.household.StorageLocation
import com.wonderfood.core.model.household.SyncBase
import com.wonderfood.core.model.household.SyncCursor
import com.wonderfood.core.model.household.SyncOutboxRecord
import com.wonderfood.core.model.household.TombstoneRecord
import com.wonderfood.core.model.household.WasteEvent

public sealed interface HouseholdCommand {
    public val record: CommandRecord

    public data class UpsertHousehold(
        override val record: CommandRecord,
        val household: Household,
    ) : HouseholdCommand

    public data class UpsertProfile(
        override val record: CommandRecord,
        val profile: Profile,
    ) : HouseholdCommand

    public data class UpsertItem(
        override val record: CommandRecord,
        val item: Item,
    ) : HouseholdCommand

    public data class UpsertFoodDetails(
        override val record: CommandRecord,
        val details: FoodDetails,
    ) : HouseholdCommand

    public data class UpsertStorageLocation(
        override val record: CommandRecord,
        val location: StorageLocation,
    ) : HouseholdCommand

    public data class UpsertInventoryLot(
        override val record: CommandRecord,
        val lot: InventoryLot,
    ) : HouseholdCommand

    public data class StoreInventoryEvent(
        override val record: CommandRecord,
        val event: InventoryEvent,
    ) : HouseholdCommand

    public data class UpsertShoppingLine(
        override val record: CommandRecord,
        val line: ShoppingLine,
    ) : HouseholdCommand

    public data class UpsertShoppingList(
        override val record: CommandRecord,
        val list: ShoppingList,
    ) : HouseholdCommand

    public data class UpsertRecipe(
        override val record: CommandRecord,
        val recipe: Recipe,
    ) : HouseholdCommand

    public data class UpsertRecipeIngredient(
        override val record: CommandRecord,
        val ingredient: RecipeIngredient,
    ) : HouseholdCommand

    public data class UpsertRecipeStep(
        override val record: CommandRecord,
        val step: RecipeStep,
    ) : HouseholdCommand

    public data class UpsertCookingSession(
        override val record: CommandRecord,
        val session: CookingSession,
    ) : HouseholdCommand

    public data class UpsertPreparedBatch(
        override val record: CommandRecord,
        val batch: PreparedBatch,
    ) : HouseholdCommand

    public data class UpsertPurchase(
        override val record: CommandRecord,
        val purchase: Purchase,
    ) : HouseholdCommand

    public data class UpsertMerchant(
        override val record: CommandRecord,
        val merchant: Merchant,
    ) : HouseholdCommand

    public data class UpsertPurchaseLine(
        override val record: CommandRecord,
        val line: PurchaseLine,
    ) : HouseholdCommand

    public data class StoreWasteEvent(
        override val record: CommandRecord,
        val wasteEvent: WasteEvent,
    ) : HouseholdCommand

    public data class UpsertMealEntry(
        override val record: CommandRecord,
        val entry: MealEntry,
    ) : HouseholdCommand

    public data class UpsertMealPlan(
        override val record: CommandRecord,
        val plan: MealPlan,
    ) : HouseholdCommand

    public data class UpsertNutritionSnapshot(
        override val record: CommandRecord,
        val snapshot: NutritionSnapshot,
    ) : HouseholdCommand

    public data class StoreProposal(
        override val record: CommandRecord,
        val proposal: ChangeProposal,
    ) : HouseholdCommand

    public data class UpsertAttachment(
        override val record: CommandRecord,
        val attachment: Attachment,
    ) : HouseholdCommand

    public data class StoreTombstone(
        override val record: CommandRecord,
        val tombstone: TombstoneRecord,
    ) : HouseholdCommand

    public data class StoreRemoteBinding(
        override val record: CommandRecord,
        val binding: RemoteBinding,
    ) : HouseholdCommand

    public data class StoreSyncBase(
        override val record: CommandRecord,
        val base: SyncBase,
    ) : HouseholdCommand

    public data class StoreSyncCursor(
        override val record: CommandRecord,
        val cursor: SyncCursor,
    ) : HouseholdCommand

    public data class StoreConflict(
        override val record: CommandRecord,
        val conflict: ConflictRecord,
    ) : HouseholdCommand

    public data class StoreLatestSafetySnapshot(
        override val record: CommandRecord,
        val safetySnapshot: LatestSafetySnapshot,
    ) : HouseholdCommand

    public data class StoreRecoverySnapshot(
        override val record: CommandRecord,
        val recoverySnapshot: RecoverySnapshot,
    ) : HouseholdCommand

    public data class EnqueueOutbox(
        override val record: CommandRecord,
        val outbox: SyncOutboxRecord,
    ) : HouseholdCommand
}

public sealed interface HouseholdCommandExecutionResult {
    public data class Applied(val commandId: CommandId) : HouseholdCommandExecutionResult
    public data class Duplicate(val commandId: CommandId) : HouseholdCommandExecutionResult
    public data class Rejected(val errors: List<String>) : HouseholdCommandExecutionResult
}

public interface HouseholdCommandRepository {
    public suspend fun apply(command: HouseholdCommand): HouseholdCommandExecutionResult
}

public class HouseholdCommandExecutor(
    private val repository: HouseholdCommandRepository,
) {
    public suspend fun execute(command: HouseholdCommand): HouseholdCommandExecutionResult {
        val errors = command.validationErrors()
        if (errors.isNotEmpty()) return HouseholdCommandExecutionResult.Rejected(errors)
        return repository.apply(command)
    }
}

private fun HouseholdCommand.validationErrors(): List<String> = buildList {
    if (record.appliedAt == null) add("Household commands must record the applied timestamp.")
    when (this@validationErrors) {
        is HouseholdCommand.UpsertHousehold -> {
            if (record.householdId != household.id) add("Command household must match household.")
        }
        is HouseholdCommand.UpsertProfile -> {
            if (record.householdId != profile.metadata.householdId) add("Command household must match profile household.")
        }
        is HouseholdCommand.UpsertItem -> {
            if (record.householdId != item.metadata.householdId) add("Command household must match item household.")
            if (item.kind != com.wonderfood.core.model.household.ItemKind.FOOD && item.foodDetailsId != null) {
                add("Only food items may reference food details.")
            }
        }
        is HouseholdCommand.UpsertFoodDetails -> {
            if (record.householdId != details.metadata.householdId) add("Command household must match food details household.")
        }
        is HouseholdCommand.UpsertStorageLocation -> {
            if (record.householdId != location.metadata.householdId) add("Command household must match storage location household.")
        }
        is HouseholdCommand.UpsertInventoryLot -> {
            if (record.householdId != lot.metadata.householdId) add("Command household must match inventory lot household.")
        }
        is HouseholdCommand.StoreInventoryEvent -> {
            if (record.householdId != event.metadata.householdId) add("Command household must match inventory event household.")
            if (record.commandId != event.commandId) add("Command ID must match inventory event command ID.")
        }
        is HouseholdCommand.UpsertShoppingLine -> {
            if (record.householdId != line.metadata.householdId) add("Command household must match shopping line household.")
        }
        is HouseholdCommand.UpsertShoppingList -> {
            if (record.householdId != list.metadata.householdId) add("Command household must match shopping list household.")
        }
        is HouseholdCommand.UpsertRecipe -> {
            if (record.householdId != recipe.metadata.householdId) add("Command household must match recipe household.")
        }
        is HouseholdCommand.UpsertRecipeIngredient -> {
            if (record.householdId != ingredient.metadata.householdId) {
                add("Command household must match recipe ingredient household.")
            }
        }
        is HouseholdCommand.UpsertRecipeStep -> {
            if (record.householdId != step.metadata.householdId) add("Command household must match recipe step.")
        }
        is HouseholdCommand.UpsertCookingSession -> {
            if (record.householdId != session.metadata.householdId) add("Command household must match cooking session household.")
        }
        is HouseholdCommand.UpsertPreparedBatch -> {
            if (record.householdId != batch.metadata.householdId) add("Command household must match prepared batch household.")
        }
        is HouseholdCommand.UpsertPurchase -> {
            if (record.householdId != purchase.metadata.householdId) add("Command household must match purchase household.")
        }
        is HouseholdCommand.UpsertMerchant -> {
            if (record.householdId != merchant.metadata.householdId) add("Command household must match merchant household.")
        }
        is HouseholdCommand.UpsertPurchaseLine -> {
            if (record.householdId != line.metadata.householdId) add("Command household must match purchase line household.")
        }
        is HouseholdCommand.StoreWasteEvent -> {
            if (record.householdId != wasteEvent.metadata.householdId) add("Command household must match waste event household.")
        }
        is HouseholdCommand.UpsertMealEntry -> {
            if (record.householdId != entry.metadata.householdId) add("Command household must match meal entry household.")
        }
        is HouseholdCommand.UpsertMealPlan -> {
            if (record.householdId != plan.metadata.householdId) add("Command household must match meal plan household.")
        }
        is HouseholdCommand.UpsertNutritionSnapshot -> {
            if (record.householdId != snapshot.metadata.householdId) {
                add("Command household must match nutrition snapshot household.")
            }
        }
        is HouseholdCommand.StoreProposal -> {
            if (record.householdId != proposal.metadata.householdId) add("Command household must match proposal household.")
        }
        is HouseholdCommand.UpsertAttachment -> {
            if (record.householdId != attachment.metadata.householdId) add("Command household must match attachment household.")
        }
        is HouseholdCommand.StoreTombstone -> {
            if (record.householdId != tombstone.metadata.householdId) add("Command household must match tombstone household.")
            if (record.commandId != tombstone.commandId) add("Command ID must match tombstone command ID.")
        }
        is HouseholdCommand.StoreRemoteBinding -> Unit
        is HouseholdCommand.StoreSyncBase -> {
            if (record.householdId != base.envelope.householdId) add("Command household must match sync base household.")
            if (base.binding.entityType != base.envelope.entityType || base.binding.entityId != base.envelope.entityId) {
                add("Sync base binding must match its envelope entity.")
            }
        }
        is HouseholdCommand.StoreSyncCursor -> {
            if (record.householdId != cursor.householdId) add("Command household must match sync cursor household.")
        }
        is HouseholdCommand.StoreConflict -> {
            if (record.householdId != conflict.metadata.householdId) add("Command household must match conflict household.")
        }
        is HouseholdCommand.StoreLatestSafetySnapshot -> {
            if (record.householdId != safetySnapshot.householdId) add("Command household must match safety snapshot household.")
            if (safetySnapshot.commandId != null && safetySnapshot.commandId != record.commandId) {
                add("Command ID must match safety snapshot command ID.")
            }
        }
        is HouseholdCommand.StoreRecoverySnapshot -> {
            if (record.householdId != recoverySnapshot.householdId) add("Command household must match recovery snapshot household.")
            if (recoverySnapshot.commandId != null && recoverySnapshot.commandId != record.commandId) {
                add("Command ID must match recovery snapshot command ID.")
            }
        }
        is HouseholdCommand.EnqueueOutbox -> {
            if (record.householdId != outbox.envelope.householdId) add("Command household must match outbox household.")
        }
    }
}
