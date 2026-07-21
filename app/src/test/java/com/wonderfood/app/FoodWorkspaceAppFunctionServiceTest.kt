package com.wonderfood.app

import android.content.Context
import androidx.core.content.edit
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.wonderfood.app.data.HouseholdDraftCommandMapper
import com.wonderfood.core.data.HouseholdRepositories
import com.wonderfood.core.data.room.WonderFoodDatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodWorkspaceAppFunctionServiceTest {
    private lateinit var context: Context
    private lateinit var service: FoodWorkspaceAppFunctionService
    private lateinit var controller: org.robolectric.android.controller.ServiceController<FoodWorkspaceAppFunctionService>

    @Before
    fun setUp() {
        controller = Robolectric.buildService(FoodWorkspaceAppFunctionService::class.java).create()
        service = controller.get()
        context = service.applicationContext
        resetPersistence()
    }

    @After
    fun tearDown() {
        controller.destroy()
        resetPersistence()
    }

    @Test
    fun executeRequestMarksUnknownActionsAsFailed() {
        val request = FoodWorkspaceActionRequest(
            requestId = "wf-af-action-fail",
            action = null,
            actions = listOf(
                FoodWorkspaceAction(
                    type = "inventory.delete_all",
                    targetKind = "inventory",
                    targetRef = "1",
                    displayName = "Old stock",
                    fields = listOf(),
                    idempotencyKey = "",
                ),
            ),
        )

        val response = service.executeRequest(request, executeChanges = true)

        assertEquals(1, response.totalCount)
        assertEquals(0, response.appliedCount)
        assertEquals(1, response.failedCount)
        val attempt = response.attempts.single()
        assertEquals("FAILED", attempt.status)
        assertEquals("inventory", attempt.targetKind)
        assertTrue(attempt.message.contains("not supported", ignoreCase = true))
    }

    @Test
    fun executeRequestReturnsReviewOnlyForPreferencesUpdates() {
        val request = FoodWorkspaceActionRequest(
            requestId = "wf-af-sensitive",
            action = null,
            actions = listOf(
                FoodWorkspaceAction(
                    type = "preferences.update",
                    targetKind = "preferences",
                    targetRef = "",
                    displayName = "Pantry prefs",
                    fields = listOf(FoodWorkspaceActionField("allergies", "peanuts")),
                    idempotencyKey = "",
                ),
            ),
        )

        val response = service.executeRequest(request, executeChanges = true)

        assertEquals(1, response.totalCount)
        assertEquals(0, response.appliedCount)
        val attempt = response.attempts.single()
        assertEquals("REVIEW_REQUIRED", attempt.status)
        assertEquals("preferences", attempt.targetKind)
        assertEquals(false, attempt.idempotentReplay)
        assertTrue(response.summary.contains("review-only", ignoreCase = true))
    }

    @Test
    fun executeRequestUsesProposeModeWhenExecuteDisabled() {
        val request = FoodWorkspaceActionRequest(
            requestId = "wf-af-propose",
            action = FoodWorkspaceAction(
                type = "inventory.add",
                targetKind = "inventory",
                targetRef = "",
                displayName = "Gala apple",
                fields = listOf(FoodWorkspaceActionField("quantity", "4")),
                idempotencyKey = "",
            ),
            actions = emptyList(),
        )

        val response = service.executeRequest(request, executeChanges = false)

        assertEquals(1, response.totalCount)
        assertEquals(0, response.appliedCount)
        val attempt = response.attempts.single()
        assertEquals("REVIEW_REQUIRED", attempt.status)
        assertEquals("inventory", attempt.targetKind)
        assertTrue(response.summary.contains("prepared for app review", ignoreCase = true))
    }

    @Test
    fun executeRequestWritesCanonicalInventoryRows() {
        val request = FoodWorkspaceActionRequest(
            requestId = "wf-af-inventory-canonical",
            action = FoodWorkspaceAction(
                type = "inventory.add",
                targetKind = "inventory",
                targetRef = "",
                displayName = "Canned beans",
                fields = listOf(
                    FoodWorkspaceActionField("quantity", "2"),
                    FoodWorkspaceActionField("notes", "canonical write test"),
                ),
                idempotencyKey = "",
            ),
            actions = emptyList(),
        )

        val response = service.executeRequest(request, executeChanges = true)
        val firstSnapshot = currentCanonicalSnapshot()

        assertEquals(1, response.totalCount)
        assertEquals(1, response.appliedCount)
        assertEquals("APPLIED", response.attempts.single().status)
        assertEquals("Canned beans", firstSnapshot?.items?.singleOrNull()?.name)
        assertEquals(
            1,
            currentCanonicalSnapshot()?.items?.count { it.name.equals("Canned beans", ignoreCase = true) },
        )
    }

    @Test
    fun executeRequestReplaysCanonicalWritesByIdempotencyKey() {
        val request = FoodWorkspaceActionRequest(
            requestId = "wf-af-inventory-replay",
            action = FoodWorkspaceAction(
                type = "meal_plan.add",
                targetKind = "meal_plan",
                targetRef = "",
                displayName = "One-pan chicken",
                fields = listOf(
                    FoodWorkspaceActionField("title", "One-pan chicken"),
                    FoodWorkspaceActionField("days", "3"),
                ),
                idempotencyKey = "",
            ),
            actions = emptyList(),
        )

        val first = service.executeRequest(request, executeChanges = true)
        val afterFirst = currentCanonicalSnapshot()
        val second = service.executeRequest(request, executeChanges = true)

        assertEquals(1, first.appliedCount)
        assertEquals("APPLIED", first.attempts.single().status)
        assertEquals(1, second.replayedCount)
        assertEquals("REPLAYED", second.attempts.single().status)
        assertEquals(1, second.attempts.size)
        assertEquals(
            afterFirst?.mealPlans?.count { it.name == "One-pan chicken" },
            currentCanonicalSnapshot()?.mealPlans?.count { it.name == "One-pan chicken" },
        )
    }

    @Test
    fun executeRequestReplaysIdenticalRequestIdWithBoundedWrites() {
        val request = FoodWorkspaceActionRequest(
            requestId = "wf-af-replay",
            action = FoodWorkspaceAction(
                type = "inventory.add",
                targetKind = "inventory",
                targetRef = "",
                displayName = "Canned beans",
                fields = listOf(FoodWorkspaceActionField("quantity", "2")),
                idempotencyKey = "",
            ),
            actions = emptyList(),
        )

        val first = service.executeRequest(request, executeChanges = true)
        val second = service.executeRequest(request, executeChanges = true)

        assertEquals(1, first.appliedCount)
        assertEquals("APPLIED", first.attempts.single().status)
        assertEquals(1, second.replayedCount)
        val replay = second.attempts.single()
        assertEquals("REPLAYED", replay.status)
        assertEquals(true, replay.idempotentReplay)
    }

    @Test(expected = AppFunctionInvalidArgumentException::class)
    fun executeRequestRejectsLargeRequestsImmediately() {
        val request = FoodWorkspaceActionRequest(
            requestId = "wf-af-limit",
            action = null,
            actions = (0..13).map { index ->
                FoodWorkspaceAction(
                    type = "inventory.add",
                    targetKind = "inventory",
                    targetRef = "",
                    displayName = "Item $index",
                    fields = listOf(FoodWorkspaceActionField("quantity", "1")),
                    idempotencyKey = "",
                )
            },
        )

        service.executeRequest(request, executeChanges = true)
    }

    private fun resetPersistence() {
        context.deleteDatabase("wonderfood.db")
        context.getSharedPreferences("wonderfood_app_functions", Context.MODE_PRIVATE).edit {
            clear()
        }
    }

    private fun currentCanonicalSnapshot() =
        runCatching {
            val repository = HouseholdRepositories.room(WonderFoodDatabaseFactory.create(context))
            runBlocking { repository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID) }
        }.getOrNull()
}
