package com.wonderfood.core.data.room

import com.wonderfood.core.engine.FoodActionAudit
import com.wonderfood.core.engine.FoodActionId
import com.wonderfood.core.engine.FoodCommand
import com.wonderfood.core.engine.FoodCommandRepository
import com.wonderfood.core.engine.IdempotencyKey
import com.wonderfood.core.engine.SubjectSnapshot
import com.wonderfood.core.engine.foodEvent
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.FoodEventId
import com.wonderfood.core.model.FoodEventType
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.TruthState

public class RoomFoodCommandRepository(
    private val database: WonderFoodDatabase,
) : FoodCommandRepository {
    private val dao: WonderFoodDao = database.wonderFoodDao()

    override suspend fun <T> withTransaction(block: suspend () -> T): T =
        database.inTransaction(block)

    override suspend fun findActionById(actionId: FoodActionId): FoodActionAudit? =
        dao.getFoodAction(actionId.value)?.toAudit()

    override suspend fun findActionByIdempotencyKey(key: IdempotencyKey): FoodActionAudit? =
        dao.getFoodActionByIdempotencyKey(key.value)?.toAudit()

    override suspend fun snapshot(subject: EntityRef): SubjectSnapshot =
        when (subject.type) {
            EntityType.FOOD -> dao.getFood(subject.id)?.toSnapshot() ?: missingSnapshot(subject)
            EntityType.STOCK_LOT -> dao.getStockLot(subject.id)?.toSnapshot() ?: missingSnapshot(subject)
            else -> missingSnapshot(subject)
        }

    override suspend fun createFoodGraph(command: FoodCommand.CreateFoodGraph) {
        val timestamp = command.requestedAt.value
        dao.upsertSource(command.source.toRecordEntity())
        dao.upsertFoodWithPage(
            page = command.page.toEntity(createdAt = timestamp, updatedAt = timestamp),
            food = command.food.toEntity(createdAt = timestamp, updatedAt = timestamp),
            aliases = command.aliases.map { it.toEntity(createdAt = timestamp, updatedAt = timestamp) },
            lots = command.stockLots.map { it.toEntity(createdAt = timestamp, updatedAt = timestamp) },
        )
    }

    override suspend fun addStockLot(command: FoodCommand.AddStockLot) {
        val timestamp = command.requestedAt.value
        dao.upsertSource(command.source.toRecordEntity())
        dao.upsertStockLot(command.stockLot.toEntity(createdAt = timestamp, updatedAt = timestamp))
    }

    override suspend fun archiveFood(command: FoodCommand.ArchiveFood): Boolean =
        dao.archiveFood(command.foodId.value, command.requestedAt.value) == 1

    override suspend fun archiveStockLot(command: FoodCommand.ArchiveStockLot): Boolean =
        dao.archiveStockLot(command.stockLotId.value, command.requestedAt.value) == 1

    override suspend fun moveStockLot(command: FoodCommand.MoveStockLot): Boolean =
        mutateStockLot(command.stockLotId.value, command.requestedAt.value) { lot ->
            lot.copy(location = command.location, updatedAt = command.requestedAt.value)
        }

    override suspend fun openStockLot(command: FoodCommand.OpenStockLot): Boolean =
        mutateStockLot(command.stockLotId.value, command.requestedAt.value) { lot ->
            lot.copy(status = StockLotStatus.OPENED, updatedAt = command.requestedAt.value)
        }

    override suspend fun consumeStockLot(command: FoodCommand.ConsumeStockLot): Boolean {
        val lot = activeStockLot(command.stockLotId.value) ?: return false
        if (!command.exact) return true

        val currentAmount = lot.quantity.amount ?: return false
        val usedAmount = command.quantity.amount ?: return false
        if (lot.quantity.unit != command.quantity.unit) return false
        if (usedAmount > currentAmount) return false

        val remainingAmount = (currentAmount - usedAmount).coerceAtLeast(0.0)
        val nextStatus = if (remainingAmount.isEffectivelyZero()) {
            StockLotStatus.CONSUMED
        } else {
            lot.status
        }
        dao.upsertStockLot(
            lot.copy(
                quantity = lot.quantity.copy(amount = remainingAmount),
                status = nextStatus,
                updatedAt = command.requestedAt.value,
            ),
        )
        return true
    }

    override suspend fun discardStockLot(command: FoodCommand.DiscardStockLot): Boolean =
        mutateStockLot(command.stockLotId.value, command.requestedAt.value) { lot ->
            lot.copy(status = StockLotStatus.DISCARDED, updatedAt = command.requestedAt.value)
        }

    override suspend fun correctStockLot(command: FoodCommand.CorrectStockLot): Boolean =
        mutateStockLot(command.stockLotId.value, command.requestedAt.value) { lot ->
            val correctedQuantity = command.quantity?.toColumns() ?: lot.quantity
            val correctedStatus = command.status
                ?: if (correctedQuantity.amount?.isEffectivelyZero() == true) StockLotStatus.OUT else lot.status
            lot.copy(
                quantity = correctedQuantity,
                status = correctedStatus,
                location = command.location ?: lot.location,
                truthState = TruthState.USER_CONFIRMED,
                updatedAt = command.requestedAt.value,
            )
        }

    override suspend fun markStockLotLow(command: FoodCommand.MarkStockLotLow): Boolean =
        mutateStockLot(command.stockLotId.value, command.requestedAt.value) { lot ->
            lot.copy(status = StockLotStatus.LOW, updatedAt = command.requestedAt.value)
        }

    override suspend fun markStockLotOut(command: FoodCommand.MarkStockLotOut): Boolean =
        mutateStockLot(command.stockLotId.value, command.requestedAt.value) { lot ->
            lot.copy(status = StockLotStatus.OUT, updatedAt = command.requestedAt.value)
        }

    override suspend fun putAwayStockLot(command: FoodCommand.PutAwayStockLot): Boolean =
        mutateStockLot(command.stockLotId.value, command.requestedAt.value) { lot ->
            lot.copy(
                location = command.location,
                status = StockLotStatus.AVAILABLE,
                updatedAt = command.requestedAt.value,
            )
        }

    override suspend fun mergeStockLots(command: FoodCommand.MergeStockLots): Boolean {
        val source = activeStockLot(command.sourceStockLotId.value) ?: return false
        val target = activeStockLot(command.targetStockLotId.value) ?: return false
        if (source.id == target.id) return false
        if (source.foodId != target.foodId) return false

        val sourceAmount = source.quantity.amount ?: return false
        val targetAmount = target.quantity.amount ?: return false
        if (source.quantity.unit != target.quantity.unit) return false

        val timestamp = command.requestedAt.value
        dao.upsertStockLot(
            target.copy(
                quantity = target.quantity.copy(amount = targetAmount + sourceAmount),
                status = mergedStatus(target.status),
                updatedAt = timestamp,
            ),
        )
        dao.upsertStockLot(
            source.copy(
                status = StockLotStatus.ARCHIVED,
                archivedAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return true
    }

    override suspend fun restoreFood(subject: EntityRef, restoredAt: IsoTimestamp): Boolean =
        subject.type == EntityType.FOOD && dao.restoreFood(subject.id, restoredAt.value) == 1

    override suspend fun restoreStockLot(subject: EntityRef, restoredAt: IsoTimestamp): Boolean =
        subject.type == EntityType.STOCK_LOT && dao.restoreStockLot(subject.id, restoredAt.value) == 1

    override suspend fun recordFoodEvent(
        eventId: FoodEventId,
        subject: EntityRef,
        command: FoodCommand,
        type: FoodEventType,
        note: String?,
    ) {
        dao.insertFoodEvent(command.foodEvent(eventId, type, note).copy(subject = subject).toEntity())
    }

    override suspend fun recordAction(audit: FoodActionAudit) {
        dao.insertFoodAction(audit.toEntity())
    }

    private suspend fun mutateStockLot(
        id: String,
        updatedAt: String,
        mutate: (StockLotEntity) -> StockLotEntity,
    ): Boolean {
        val lot = activeStockLot(id) ?: return false
        dao.upsertStockLot(mutate(lot).copy(updatedAt = updatedAt))
        return true
    }

    private suspend fun activeStockLot(id: String): StockLotEntity? =
        dao.getStockLot(id)
            ?.takeIf { it.deletedAt == null && it.archivedAt == null }
            ?.takeIf {
                it.status !in setOf(
                    StockLotStatus.ARCHIVED,
                    StockLotStatus.CONSUMED,
                    StockLotStatus.DISCARDED,
                )
            }

    private fun mergedStatus(current: StockLotStatus): StockLotStatus =
        when (current) {
            StockLotStatus.OPENED -> StockLotStatus.OPENED
            StockLotStatus.RESERVED -> StockLotStatus.RESERVED
            else -> StockLotStatus.AVAILABLE
        }

    private fun Double.isEffectivelyZero(): Boolean =
        this <= 0.000001
}
