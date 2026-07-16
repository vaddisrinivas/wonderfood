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
}
