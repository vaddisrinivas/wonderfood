package com.wonderfood.core.engine

import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.FoodEventId
import com.wonderfood.core.model.FoodEventType
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.TruthState
import java.util.UUID

public fun interface FoodCommandIdProvider {
    public fun newId(prefix: String): String
}

public object UuidFoodCommandIdProvider : FoodCommandIdProvider {
    override fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}

public sealed interface FoodCommandExecutionResult {
    public data class Applied(val audit: FoodActionAudit) : FoodCommandExecutionResult
    public data class Duplicate(val existingAudit: FoodActionAudit) : FoodCommandExecutionResult
    public data class Rejected(val errors: List<String>) : FoodCommandExecutionResult
    public data class NeedsConfirmation(val requirement: ConfirmationRequirement) : FoodCommandExecutionResult
}

public interface FoodCommandRepository {
    public suspend fun <T> withTransaction(block: suspend () -> T): T
    public suspend fun findActionById(actionId: FoodActionId): FoodActionAudit?
    public suspend fun findActionByIdempotencyKey(key: IdempotencyKey): FoodActionAudit?
    public suspend fun snapshot(subject: EntityRef): SubjectSnapshot
    public suspend fun createFoodGraph(command: FoodCommand.CreateFoodGraph)
    public suspend fun addStockLot(command: FoodCommand.AddStockLot)
    public suspend fun archiveFood(command: FoodCommand.ArchiveFood): Boolean
    public suspend fun archiveStockLot(command: FoodCommand.ArchiveStockLot): Boolean
    public suspend fun moveStockLot(command: FoodCommand.MoveStockLot): Boolean
    public suspend fun openStockLot(command: FoodCommand.OpenStockLot): Boolean
    public suspend fun consumeStockLot(command: FoodCommand.ConsumeStockLot): Boolean
    public suspend fun discardStockLot(command: FoodCommand.DiscardStockLot): Boolean
    public suspend fun correctStockLot(command: FoodCommand.CorrectStockLot): Boolean
    public suspend fun markStockLotLow(command: FoodCommand.MarkStockLotLow): Boolean
    public suspend fun markStockLotOut(command: FoodCommand.MarkStockLotOut): Boolean
    public suspend fun putAwayStockLot(command: FoodCommand.PutAwayStockLot): Boolean
    public suspend fun mergeStockLots(command: FoodCommand.MergeStockLots): Boolean
    public suspend fun restoreFood(subject: EntityRef, restoredAt: IsoTimestamp): Boolean
    public suspend fun restoreStockLot(subject: EntityRef, restoredAt: IsoTimestamp): Boolean
    public suspend fun recordFoodEvent(
        eventId: FoodEventId,
        subject: EntityRef,
        command: FoodCommand,
        type: FoodEventType,
        note: String?,
    )
    public suspend fun recordAction(audit: FoodActionAudit)
}

public class FoodCommandExecutor(
    private val repository: FoodCommandRepository,
    private val idProvider: FoodCommandIdProvider = UuidFoodCommandIdProvider,
) {
    public suspend fun execute(command: FoodCommand, confirmed: Boolean = false): FoodCommandExecutionResult {
        val validationErrors = command.validate()
        if (validationErrors.isNotEmpty()) return FoodCommandExecutionResult.Rejected(validationErrors)

        val requirement = command.confirmationRequirement()
        if (requirement.required && !confirmed) {
            return FoodCommandExecutionResult.NeedsConfirmation(requirement)
        }

        return repository.withTransaction {
            repository.findActionByIdempotencyKey(command.idempotencyKey)?.let {
                return@withTransaction FoodCommandExecutionResult.Duplicate(it)
            }

            if (command is FoodCommand.UndoFoodAction) {
                return@withTransaction undoAndAudit(command)
            }

            val before = repository.snapshot(command.subject)
            val applyResult = apply(command)
            if (applyResult != null) return@withTransaction applyResult
            val after = repository.snapshot(command.subject)
            val audit = FoodActionAudit(
                id = FoodActionId(idProvider.newId("action")),
                idempotencyKey = command.idempotencyKey,
                actionType = command.actionType(),
                subject = command.subject,
                occurredAt = command.requestedAt,
                payload = command.auditPayload(before = before, after = after),
                before = before,
                after = after,
                source = command.source,
                confidence = command.confidence,
                truthState = TruthState.USER_CONFIRMED,
            )
            repository.recordAction(audit)
            FoodCommandExecutionResult.Applied(audit)
        }
    }

    private suspend fun apply(command: FoodCommand): FoodCommandExecutionResult? =
        when (command) {
            is FoodCommand.CreateFoodGraph -> {
                repository.createFoodGraph(command)
                repository.recordFoodEvent(FoodEventId(idProvider.newId("event")), command.subject, command, FoodEventType.CREATED, command.summary)
                null
            }

            is FoodCommand.AddStockLot -> {
                repository.addStockLot(command)
                repository.recordFoodEvent(FoodEventId(idProvider.newId("event")), command.subject, command, FoodEventType.STOCK_ADDED, command.summary)
                null
            }

            is FoodCommand.ArchiveFood -> {
                if (!repository.archiveFood(command)) {
                    FoodCommandExecutionResult.Rejected(listOf("Food ${command.foodId.value} does not exist."))
                } else {
                    repository.recordFoodEvent(FoodEventId(idProvider.newId("event")), command.subject, command, FoodEventType.ARCHIVED, command.reason)
                    null
                }
            }

            is FoodCommand.ArchiveStockLot -> {
                if (!repository.archiveStockLot(command)) {
                    FoodCommandExecutionResult.Rejected(listOf("Stock lot ${command.stockLotId.value} does not exist."))
                } else {
                    repository.recordFoodEvent(FoodEventId(idProvider.newId("event")), command.subject, command, FoodEventType.ARCHIVED, command.reason)
                    null
                }
            }

            is FoodCommand.MoveStockLot -> stockLotMutation(command, FoodEventType.STOCK_MOVED) {
                repository.moveStockLot(command)
            }

            is FoodCommand.OpenStockLot -> stockLotMutation(command, FoodEventType.STOCK_OPENED) {
                repository.openStockLot(command)
            }

            is FoodCommand.ConsumeStockLot -> {
                val changed = repository.consumeStockLot(command)
                if (!changed) {
                    FoodCommandExecutionResult.Rejected(listOf("Stock lot ${command.stockLotId.value} cannot be consumed from current state."))
                } else {
                    repository.recordFoodEvent(
                        FoodEventId(idProvider.newId("event")),
                        command.subject,
                        command,
                        if (command.exact) FoodEventType.STOCK_CONSUMED else FoodEventType.STOCK_USAGE_PROPOSED,
                        command.reason,
                    )
                    null
                }
            }

            is FoodCommand.DiscardStockLot -> stockLotMutation(command, FoodEventType.STOCK_DISCARDED) {
                repository.discardStockLot(command)
            }

            is FoodCommand.CorrectStockLot -> stockLotMutation(command, FoodEventType.STOCK_CORRECTED) {
                repository.correctStockLot(command)
            }

            is FoodCommand.MarkStockLotLow -> stockLotMutation(command, FoodEventType.STOCK_MARKED_LOW) {
                repository.markStockLotLow(command)
            }

            is FoodCommand.MarkStockLotOut -> stockLotMutation(command, FoodEventType.STOCK_MARKED_OUT) {
                repository.markStockLotOut(command)
            }

            is FoodCommand.PutAwayStockLot -> stockLotMutation(command, FoodEventType.STOCK_PUT_AWAY) {
                repository.putAwayStockLot(command)
            }

            is FoodCommand.MergeStockLots -> stockLotMutation(command, FoodEventType.STOCK_MERGED) {
                repository.mergeStockLots(command)
            }

            is FoodCommand.UndoFoodAction -> error("Undo commands are applied by undoAndAudit.")
        }

    private suspend fun stockLotMutation(
        command: FoodCommand,
        eventType: FoodEventType,
        mutate: suspend () -> Boolean,
    ): FoodCommandExecutionResult? =
        if (!mutate()) {
            FoodCommandExecutionResult.Rejected(listOf("Stock lot ${command.subject.id} cannot be changed from current state."))
        } else {
            repository.recordFoodEvent(FoodEventId(idProvider.newId("event")), command.subject, command, eventType, command.summary)
            null
        }

    private suspend fun undoAndAudit(command: FoodCommand.UndoFoodAction): FoodCommandExecutionResult {
        val target = repository.findActionById(command.actionId)
            ?: return FoodCommandExecutionResult.Rejected(listOf("Action ${command.actionId.value} does not exist."))
        val before = repository.snapshot(target.subject)
        val restored = when (target.actionType) {
            "CreateFoodGraph" -> repository.archiveFood(
                FoodCommand.ArchiveFood(
                    idempotencyKey = IdempotencyKey("${command.idempotencyKey.value}:archive-created"),
                    requestedAt = command.requestedAt,
                    source = command.source,
                    confidence = command.confidence,
                    foodId = com.wonderfood.core.model.FoodId(target.subject.id),
                    reason = "Undo created food.",
                ),
            )

            "ArchiveFood" -> repository.restoreFood(target.subject, command.requestedAt)
            "AddStockLot" -> repository.archiveStockLot(
                FoodCommand.ArchiveStockLot(
                    idempotencyKey = IdempotencyKey("${command.idempotencyKey.value}:archive-lot"),
                    requestedAt = command.requestedAt,
                    source = command.source,
                    confidence = command.confidence,
                    stockLotId = com.wonderfood.core.model.StockLotId(target.subject.id),
                    reason = "Undo added stock lot.",
                ),
            )

            "ArchiveStockLot" -> repository.restoreStockLot(target.subject, command.requestedAt)
            else -> false
        }
        return if (restored) {
            repository.recordFoodEvent(FoodEventId(idProvider.newId("event")), target.subject, command, FoodEventType.UPDATED, command.reason)
            val after = repository.snapshot(target.subject)
            val audit = FoodActionAudit(
                id = FoodActionId(idProvider.newId("action")),
                idempotencyKey = command.idempotencyKey,
                actionType = command.actionType(),
                subject = target.subject,
                occurredAt = command.requestedAt,
                payload = command.auditPayload(before = before, after = after),
                before = before,
                after = after,
                source = command.source,
                confidence = command.confidence,
                truthState = TruthState.USER_CONFIRMED,
            )
            repository.recordAction(audit)
            FoodCommandExecutionResult.Applied(audit)
        } else {
            FoodCommandExecutionResult.Rejected(listOf("Action ${target.id.value} cannot be undone from current state."))
        }
    }
}

private fun FoodCommand.auditPayload(before: SubjectSnapshot, after: SubjectSnapshot): String =
    buildString {
        appendLine("command=${actionType()}")
        appendLine("summary=$summary")
        appendLine("before.exists=${before.exists}")
        appendLine("before.status=${before.status.orEmpty()}")
        appendLine("before.summary=${before.summary.orEmpty()}")
        appendLine("after.exists=${after.exists}")
        appendLine("after.status=${after.status.orEmpty()}")
        appendLine("after.summary=${after.summary.orEmpty()}")
    }
