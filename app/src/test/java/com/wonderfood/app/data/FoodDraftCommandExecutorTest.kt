package com.wonderfood.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodDraftCommandExecutorTest {
    @Test
    fun executeRoutesDraftThroughSinkWithOriginSource() {
        val sink = FakeSink()
        val executor = FoodDraftCommandExecutor { sink }
        val result = executor.execute(
            FoodDraftCommand(
                draft = InventoryDraft(listOf(FoodCandidate(name = "Eggs", quantity = "12", zone = StorageZone.FRIDGE))),
                sourceMessageId = 42L,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Applied)
        assertEquals("external_proposal", sink.applied.single().writeSource)
        assertEquals(42L, sink.applied.single().sourceMessageId)
    }

    @Test
    fun executeRejectsInvalidDraftBeforeSinkWrite() {
        val sink = FakeSink()
        val executor = FoodDraftCommandExecutor { sink }
        val result = executor.execute(
            FoodDraftCommand(
                draft = GroceryDraft(listOf(FoodCandidate(name = ""))),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Rejected)
        assertTrue(sink.applied.isEmpty())
        assertTrue((result as FoodDraftExecutionResult.Rejected).errors.single().contains("needs a name"))
    }

    @Test
    fun executeAcceptsReviewableLinkActionDraft() {
        val sink = FakeSink()
        val executor = FoodDraftCommandExecutor { sink }
        val result = executor.execute(
            FoodDraftCommand(
                draft = LinkActionDraft(
                    actionType = "inventory.edit",
                    targetKind = "inventory",
                    targetRef = "7",
                    fields = mapOf("quantity" to "6"),
                ),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Applied)
        assertEquals("external_proposal", sink.applied.single().writeSource)
        assertTrue(sink.applied.single().draft is LinkActionDraft)
    }

    @Test
    fun executeRejectsLinkActionWithoutTarget() {
        val sink = FakeSink()
        val executor = FoodDraftCommandExecutor { sink }
        val result = executor.execute(
            FoodDraftCommand(
                draft = LinkActionDraft(
                    actionType = "inventory.edit",
                    targetKind = "inventory",
                    fields = mapOf("quantity" to "6"),
                ),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Rejected)
        assertTrue(sink.applied.isEmpty())
        assertTrue((result as FoodDraftExecutionResult.Rejected).errors.any { it.contains("target id") })
    }

    @Test
    fun executeRejectsUnknownLinkActionBeforeSinkWrite() {
        val sink = FakeSink()
        val result = FoodDraftCommandExecutor { sink }.execute(
            FoodDraftCommand(
                draft = LinkActionDraft(
                    actionType = "inventory.overwrite",
                    targetKind = "inventory",
                    targetRef = "7",
                    fields = mapOf("quantity" to "6"),
                ),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Rejected)
        assertTrue(sink.applied.isEmpty())
        assertTrue((result as FoodDraftExecutionResult.Rejected).errors.any { it.contains("not supported") })
    }

    @Test
    fun executeRejectsCreateWithoutRequiredName() {
        val sink = FakeSink()
        val result = FoodDraftCommandExecutor { sink }.execute(
            FoodDraftCommand(
                draft = LinkActionDraft(
                    actionType = "inventory.add",
                    targetKind = "inventory",
                    fields = mapOf("quantity" to "6"),
                ),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Rejected)
        assertTrue(sink.applied.isEmpty())
        assertTrue((result as FoodDraftExecutionResult.Rejected).errors.any { it.contains("needs a name") })
    }

    @Test
    fun executeRejectsInvalidTypedField() {
        val sink = FakeSink()
        val result = FoodDraftCommandExecutor { sink }.execute(
            FoodDraftCommand(
                draft = LinkActionDraft(
                    actionType = "meal_log.edit",
                    targetKind = "meal_log",
                    targetRef = "7",
                    fields = mapOf("calories" to "many"),
                ),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Rejected)
        assertTrue(sink.applied.isEmpty())
        assertTrue((result as FoodDraftExecutionResult.Rejected).errors.any { it.contains("non-negative whole number") })
    }

    @Test
    fun executeTurnsApplyFailureIntoSafeRejection() {
        val executor = FoodDraftCommandExecutor {
            object : FoodDraftCommandSink {
                override fun applyDraft(draft: FoodDraft, sourceMessageId: Long?, writeSource: String): String =
                    throw FoodDraftApplyException("Target disappeared. No changes were applied.")

                override fun rejectDraft(draft: FoodDraft, sourceMessageId: Long?, writeSource: String): String = "rejected"
            }
        }
        val result = executor.execute(
            FoodDraftCommand(
                draft = LinkActionDraft(
                    actionType = "inventory.edit",
                    targetKind = "inventory",
                    targetRef = "7",
                    fields = mapOf("quantity" to "6"),
                ),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Rejected)
        assertTrue((result as FoodDraftExecutionResult.Rejected).errors.single().contains("No changes were applied"))
    }

    @Test
    fun executeRejectsOversizedExternalCompositeBeforeSinkWrite() {
        val sink = FakeSink()
        val executor = FoodDraftCommandExecutor { sink }
        val result = executor.execute(
            FoodDraftCommand(
                draft = CompositeDraft((1..13).map { index ->
                    InventoryDraft(listOf(FoodCandidate(name = "Item $index")))
                }),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Rejected)
        assertTrue(sink.applied.isEmpty())
        assertTrue((result as FoodDraftExecutionResult.Rejected).errors.any { it.contains("limited to 12") })
    }

    @Test
    fun executeAllowsLargeCsvCompositeAfterValidation() {
        val sink = FakeSink()
        val executor = FoodDraftCommandExecutor { sink }
        val result = executor.execute(
            FoodDraftCommand(
                draft = CompositeDraft((1..13).map { index ->
                    InventoryDraft(listOf(FoodCandidate(name = "Seed item $index")))
                }),
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.CSV_IMPORT,
            ),
        )

        assertTrue(result is FoodDraftExecutionResult.Applied)
        assertTrue(sink.applied.single().draft is CompositeDraft)
    }

    @Test
    fun mutationExecutorRecordsAppliedMutation() {
        val sink = FakeMutationSink()
        val executor = FoodMutationCommandExecutor { sink }
        val result = executor.execute(
            FoodMutationCommand(
                type = FoodMutationCommandType.UPDATE_INVENTORY,
                label = "Update pantry item",
                origin = FoodDraftCommandOrigin.MANUAL_SAVE,
                payload = mapOf("id" to "7", "name" to "Eggs"),
            ),
        ) {
            "Kitchen page updated."
        }

        assertTrue(result is FoodMutationExecutionResult.Applied)
        assertEquals("APPLIED", sink.recorded.single().status)
        assertEquals(FoodMutationCommandType.UPDATE_INVENTORY, sink.recorded.single().command.type)
        assertEquals("manual", sink.recorded.single().command.origin.writeSource)
    }

    @Test
    fun mutationExecutorRejectsExternalDestructiveMutationBeforeWrite() {
        val sink = FakeMutationSink()
        val executor = FoodMutationCommandExecutor { sink }
        var wrote = false
        val result = executor.execute(
            FoodMutationCommand(
                type = FoodMutationCommandType.DELETE_RECIPE,
                label = "Archive recipe",
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
                payload = mapOf("id" to "9"),
            ),
        ) {
            wrote = true
            "Archived."
        }

        assertTrue(result is FoodMutationExecutionResult.Rejected)
        assertTrue(!wrote)
        assertEquals("REJECTED", sink.recorded.single().status)
        assertTrue((result as FoodMutationExecutionResult.Rejected).errors.single().contains("external_proposal"))
    }

    @Test
    fun mutationExecutorRejectsAssistantDestructiveMutationBeforeWrite() {
        val sink = FakeMutationSink()
        val executor = FoodMutationCommandExecutor { sink }
        var wrote = false
        val result = executor.execute(
            FoodMutationCommand(
                type = FoodMutationCommandType.DELETE_GROCERY,
                label = "Delete grocery from assistant",
                origin = FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
                payload = mapOf("id" to "12"),
            ),
        ) {
            wrote = true
            "Deleted."
        }

        assertTrue(result is FoodMutationExecutionResult.Rejected)
        assertTrue(!wrote)
        assertEquals("REJECTED", sink.recorded.single().status)
        assertTrue((result as FoodMutationExecutionResult.Rejected).errors.single().contains("google_assistant"))
    }

    private class FakeSink : FoodDraftCommandSink {
        val applied = mutableListOf<AppliedCall>()

        override fun applyDraft(draft: FoodDraft, sourceMessageId: Long?, writeSource: String): String {
            applied += AppliedCall(draft, sourceMessageId, writeSource)
            return "applied"
        }

        override fun rejectDraft(draft: FoodDraft, sourceMessageId: Long?, writeSource: String): String =
            "rejected"
    }

    private data class AppliedCall(
        val draft: FoodDraft,
        val sourceMessageId: Long?,
        val writeSource: String,
    )

    private class FakeMutationSink : FoodMutationCommandSink {
        val recorded = mutableListOf<RecordedMutation>()

        override fun recordMutationCommand(command: FoodMutationCommand, status: String, summary: String) {
            recorded += RecordedMutation(command, status, summary)
        }
    }

    private data class RecordedMutation(
        val command: FoodMutationCommand,
        val status: String,
        val summary: String,
    )
}
