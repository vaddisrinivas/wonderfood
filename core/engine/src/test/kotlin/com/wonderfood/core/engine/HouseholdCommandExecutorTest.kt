package com.wonderfood.core.engine

import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdEntityType
import com.wonderfood.core.model.household.HouseholdId
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.Item
import com.wonderfood.core.model.household.ItemKind
import com.wonderfood.core.model.household.MealEntry
import com.wonderfood.core.model.household.MealEntryStatus
import com.wonderfood.core.model.household.MealPlan
import com.wonderfood.core.model.household.MealPlanStatus
import com.wonderfood.core.model.household.Purchase
import com.wonderfood.core.model.household.Recipe
import com.wonderfood.core.model.household.RecipeStep
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.TombstoneReason
import com.wonderfood.core.model.household.TombstoneRecord
import com.wonderfood.core.model.household.UtcTimestamp
import com.wonderfood.core.model.household.EntityId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseholdCommandExecutorTest {
    @Test
    fun executorAppliesValidCommandThroughRepository() = runTest {
        val repository = RecordingHouseholdCommandRepository()
        val command = HouseholdCommand.UpsertHousehold(
            record = commandRecord("00000000-0000-0000-0000-000000000101", "UpsertHousehold"),
            household = household(),
        )

        val result = HouseholdCommandExecutor(repository).execute(command)

        assertEquals(HouseholdCommandExecutionResult.Applied(command.record.commandId), result)
        assertEquals(listOf(command), repository.commands)
    }

    @Test
    fun executorRejectsMismatchedHouseholdBeforeRepositoryMutation() = runTest {
        val repository = RecordingHouseholdCommandRepository()
        val command = HouseholdCommand.UpsertItem(
            record = commandRecord("00000000-0000-0000-0000-000000000102", "UpsertItem"),
            item = item(householdId = HouseholdId("00000000-0000-0000-0000-000000000002")),
        )

        val result = HouseholdCommandExecutor(repository).execute(command)

        assertTrue(result is HouseholdCommandExecutionResult.Rejected)
        assertEquals(emptyList<HouseholdCommand>(), repository.commands)
    }

    @Test
    fun executorRejectsMismatchedRecipeHouseholdBeforeRepositoryMutation() = runTest {
        val repository = RecordingHouseholdCommandRepository()
        val command = HouseholdCommand.UpsertRecipe(
            record = commandRecord("00000000-0000-0000-0000-000000000103", "UpsertRecipe"),
            recipe = Recipe(
                metadata = EntityMetadata(
                    id = EntityId("00000000-0000-0000-0000-000000000301"),
                    householdId = HouseholdId("00000000-0000-0000-0000-000000000002"),
                    createdAt = UtcTimestamp(1),
                    updatedAt = UtcTimestamp(1),
                    source = source(),
                ),
                name = "Dal",
            ),
        )

        val result = HouseholdCommandExecutor(repository).execute(command)

        assertTrue(result is HouseholdCommandExecutionResult.Rejected)
        assertEquals(emptyList<HouseholdCommand>(), repository.commands)
    }

    @Test
    fun executorRejectsMismatchedRecipeStepHouseholdBeforeRepositoryMutation() = runTest {
        val repository = RecordingHouseholdCommandRepository()
        val command = HouseholdCommand.UpsertRecipeStep(
            record = commandRecord("00000000-0000-0000-0000-000000000107", "UpsertRecipeStep"),
            step = RecipeStep(
                metadata = EntityMetadata(
                    id = EntityId("00000000-0000-0000-0000-000000000701"),
                    householdId = HouseholdId("00000000-0000-0000-0000-000000000002"),
                    createdAt = UtcTimestamp(1),
                    updatedAt = UtcTimestamp(1),
                    source = source(),
                ),
                recipeId = EntityId("00000000-0000-0000-0000-000000000301"),
                order = 0,
                instruction = "Cook rice.",
            ),
        )

        val result = HouseholdCommandExecutor(repository).execute(command)

        assertTrue(result is HouseholdCommandExecutionResult.Rejected)
        assertEquals(emptyList<HouseholdCommand>(), repository.commands)
    }

    @Test
    fun executorRejectsMismatchedPurchaseHouseholdBeforeRepositoryMutation() = runTest {
        val repository = RecordingHouseholdCommandRepository()
        val command = HouseholdCommand.UpsertPurchase(
            record = commandRecord("00000000-0000-0000-0000-000000000104", "UpsertPurchase"),
            purchase = Purchase(
                metadata = EntityMetadata(
                    id = EntityId("00000000-0000-0000-0000-000000000401"),
                    householdId = HouseholdId("00000000-0000-0000-0000-000000000002"),
                    createdAt = UtcTimestamp(1),
                    updatedAt = UtcTimestamp(1),
                    source = source(),
                ),
                occurredAt = UtcTimestamp(1),
                status = com.wonderfood.core.model.household.PurchaseStatus.REVIEWED,
            ),
        )

        val result = HouseholdCommandExecutor(repository).execute(command)

        assertTrue(result is HouseholdCommandExecutionResult.Rejected)
        assertEquals(emptyList<HouseholdCommand>(), repository.commands)
    }

    @Test
    fun executorRejectsMismatchedMealEntryHouseholdBeforeRepositoryMutation() = runTest {
        val repository = RecordingHouseholdCommandRepository()
        val command = HouseholdCommand.UpsertMealEntry(
            record = commandRecord("00000000-0000-0000-0000-000000000105", "UpsertMealEntry"),
            entry = MealEntry(
                metadata = EntityMetadata(
                    id = EntityId("00000000-0000-0000-0000-000000000501"),
                    householdId = HouseholdId("00000000-0000-0000-0000-000000000002"),
                    createdAt = UtcTimestamp(1),
                    updatedAt = UtcTimestamp(1),
                    source = source(),
                ),
                scheduledAt = UtcTimestamp(1),
                slot = "Lunch",
                title = "Rice bowl",
                status = MealEntryStatus.EATEN,
            ),
        )

        val result = HouseholdCommandExecutor(repository).execute(command)

        assertTrue(result is HouseholdCommandExecutionResult.Rejected)
        assertEquals(emptyList<HouseholdCommand>(), repository.commands)
    }

    @Test
    fun executorRejectsMismatchedMealPlanHouseholdBeforeRepositoryMutation() = runTest {
        val repository = RecordingHouseholdCommandRepository()
        val command = HouseholdCommand.UpsertMealPlan(
            record = commandRecord("00000000-0000-0000-0000-000000000106", "UpsertMealPlan"),
            plan = MealPlan(
                metadata = EntityMetadata(
                    id = EntityId("00000000-0000-0000-0000-000000000601"),
                    householdId = HouseholdId("00000000-0000-0000-0000-000000000002"),
                    createdAt = UtcTimestamp(1),
                    updatedAt = UtcTimestamp(1),
                    source = source(),
                ),
                name = "Week plan",
                startsOn = com.wonderfood.core.model.household.CalendarDate("2026-07-20"),
                endsOn = com.wonderfood.core.model.household.CalendarDate("2026-07-21"),
                status = MealPlanStatus.ACTIVE,
            ),
        )

        val result = HouseholdCommandExecutor(repository).execute(command)

        assertTrue(result is HouseholdCommandExecutionResult.Rejected)
        assertEquals(emptyList<HouseholdCommand>(), repository.commands)
    }

    @Test
    fun executorRejectsTombstoneWithMismatchedCommandIdBeforeRepositoryMutation() = runTest {
        val repository = RecordingHouseholdCommandRepository()
        val command = HouseholdCommand.StoreTombstone(
            record = commandRecord("00000000-0000-0000-0000-000000000108", "StoreTombstone"),
            tombstone = TombstoneRecord(
                metadata = EntityMetadata(
                    id = EntityId("00000000-0000-0000-0000-000000000801"),
                    householdId = householdId(),
                    createdAt = UtcTimestamp(1),
                    updatedAt = UtcTimestamp(1),
                    source = source(),
                ),
                entityType = HouseholdEntityType.ITEM,
                entityId = EntityId("00000000-0000-0000-0000-000000000201"),
                reason = TombstoneReason.ARCHIVED_BY_APP,
                commandId = CommandId("00000000-0000-0000-0000-000000000109"),
            ),
        )

        val result = HouseholdCommandExecutor(repository).execute(command)

        assertTrue(result is HouseholdCommandExecutionResult.Rejected)
        assertEquals(emptyList<HouseholdCommand>(), repository.commands)
    }

    private class RecordingHouseholdCommandRepository : HouseholdCommandRepository {
        val commands = mutableListOf<HouseholdCommand>()

        override suspend fun apply(command: HouseholdCommand): HouseholdCommandExecutionResult {
            commands += command
            return HouseholdCommandExecutionResult.Applied(command.record.commandId)
        }
    }

    private fun household(): Household = Household(
        id = householdId(),
        name = "Test household",
        defaultCurrency = "USD",
        timezone = "America/New_York",
        locale = "en-US",
        activeDataHome = DataHomeKind.LOCAL,
        schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
        createdAt = UtcTimestamp(1),
        updatedAt = UtcTimestamp(1),
        revision = 0,
    )

    private fun item(householdId: HouseholdId = householdId()): Item = Item(
        metadata = EntityMetadata(
            id = EntityId("00000000-0000-0000-0000-000000000201"),
            householdId = householdId,
            createdAt = UtcTimestamp(1),
            updatedAt = UtcTimestamp(1),
            source = source(),
        ),
        name = "Dish soap",
        kind = ItemKind.CLEANING,
    )

    private fun commandRecord(id: String, type: String): CommandRecord = CommandRecord(
        commandId = CommandId(id),
        householdId = householdId(),
        type = type,
        source = source(),
        requestedAt = UtcTimestamp(2),
        appliedAt = UtcTimestamp(3),
        affectedEntityIds = emptyList(),
    )

    private fun source(): SourceRef = SourceRef(SourceKind.MANUAL, "Test")

    private fun householdId(): HouseholdId =
        HouseholdId("00000000-0000-0000-0000-000000000001")
}
