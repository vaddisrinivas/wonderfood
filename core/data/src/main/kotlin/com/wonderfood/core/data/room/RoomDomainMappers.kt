package com.wonderfood.core.data.room

import com.wonderfood.core.engine.FoodActionAudit
import com.wonderfood.core.engine.FoodActionId
import com.wonderfood.core.engine.IdempotencyKey
import com.wonderfood.core.engine.SubjectSnapshot
import com.wonderfood.core.model.Confidence
import com.wonderfood.core.model.AttachmentId
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.Food
import com.wonderfood.core.model.FoodAliasId
import com.wonderfood.core.model.FoodAlias
import com.wonderfood.core.model.FoodEvent
import com.wonderfood.core.model.FoodId
import com.wonderfood.core.model.IsoDate
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.NutritionSnapshotId
import com.wonderfood.core.model.Page
import com.wonderfood.core.model.PageId
import com.wonderfood.core.model.Quantity
import com.wonderfood.core.model.Source
import com.wonderfood.core.model.SourceId
import com.wonderfood.core.model.StockLot
import com.wonderfood.core.model.StockLotId
import com.wonderfood.core.model.StockLotStatus
import com.wonderfood.core.model.TruthState

internal fun Source.toRecordEntity(): SourceRecordEntity =
    SourceRecordEntity(
        id = id.value,
        kind = kind,
        label = label,
        externalId = externalId,
        uri = uri,
        capturedAt = capturedAt?.value,
        truthState = truthState,
    )

internal fun Source.toColumns(): SourceColumns =
    SourceColumns(
        id = id.value,
        kind = kind,
        label = label,
        externalId = externalId,
        uri = uri,
        capturedAt = capturedAt?.value,
        truthState = truthState,
    )

internal fun SourceColumns.toDomain(): Source =
    Source(
        id = SourceId(id),
        kind = kind,
        label = label,
        externalId = externalId,
        uri = uri,
        capturedAt = capturedAt?.let(::IsoTimestamp),
        truthState = truthState,
    )

internal fun Confidence.toColumns(): ConfidenceColumns =
    ConfidenceColumns(
        score = score,
        state = state,
        rationale = rationale,
    )

internal fun ConfidenceColumns.toDomain(): Confidence =
    Confidence(
        score = score,
        state = state,
        rationale = rationale,
    )

internal fun Quantity.toColumns(): QuantityColumns =
    QuantityColumns(
        amount = amount,
        unit = unit,
        truthState = truthState,
    )

internal fun Page.toEntity(createdAt: String, updatedAt: String): PageEntity =
    PageEntity(
        id = id.value,
        title = title,
        kind = kind,
        entityType = entity?.type,
        entityId = entity?.id,
        aliases = aliases,
        relationIds = relationIds.map { it.value },
        attachmentIds = attachmentIds.map { it.value },
        truthState = truthState,
        source = source.toColumns(),
        confidence = confidence.toColumns(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = null,
        deletedAt = null,
    )

internal fun Food.toEntity(createdAt: String, updatedAt: String): FoodEntity =
    FoodEntity(
        id = id.value,
        pageId = pageId.value,
        name = name,
        status = status,
        aliasIds = aliasIds.map { it.value },
        stockLotIds = stockLotIds.map { it.value },
        nutritionSnapshotIds = nutritionSnapshotIds.map { it.value },
        attachmentIds = attachmentIds.map { it.value },
        source = source.toColumns(),
        confidence = confidence.toColumns(),
        truthState = truthState,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = null,
        deletedAt = null,
    )

internal fun FoodAlias.toEntity(createdAt: String, updatedAt: String): FoodAliasEntity =
    FoodAliasEntity(
        id = id.value,
        foodId = foodId.value,
        name = name,
        locale = locale,
        source = source.toColumns(),
        confidence = confidence.toColumns(),
        truthState = truthState,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = null,
        deletedAt = null,
    )

internal fun StockLot.toEntity(createdAt: String, updatedAt: String): StockLotEntity =
    StockLotEntity(
        id = id.value,
        foodId = foodId.value,
        quantity = quantity.toColumns(),
        purchasedOn = purchasedOn?.value,
        expiresOn = expiresOn?.value,
        location = location,
        status = status,
        source = source.toColumns(),
        confidence = confidence.toColumns(),
        truthState = truthState,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = null,
        deletedAt = null,
    )

internal fun FoodEvent.toEntity(): FoodEventEntity =
    FoodEventEntity(
        id = id.value,
        subjectType = subject.type,
        subjectId = subject.id,
        type = type,
        occurredAt = occurredAt.value,
        quantity = quantity?.toColumns(),
        note = note,
        source = source.toColumns(),
        confidence = confidence.toColumns(),
        truthState = truthState,
    )

internal fun FoodActionAudit.toEntity(): FoodActionEntity =
    FoodActionEntity(
        id = id.value,
        idempotencyKey = idempotencyKey.value,
        subjectType = subject.type,
        subjectId = subject.id,
        actionType = actionType,
        occurredAt = occurredAt.value,
        payload = payload,
        source = source.toColumns(),
        confidence = confidence.toColumns(),
        truthState = truthState,
    )

internal fun FoodActionEntity.toAudit(): FoodActionAudit =
    FoodActionAudit(
        id = FoodActionId(id),
        idempotencyKey = IdempotencyKey(idempotencyKey),
        actionType = actionType,
        subject = EntityRef(subjectType, subjectId),
        occurredAt = IsoTimestamp(occurredAt),
        payload = payload,
        before = null,
        after = null,
        source = source.toDomain(),
        confidence = confidence.toDomain(),
        truthState = truthState,
    )

internal fun missingSnapshot(subject: EntityRef): SubjectSnapshot =
    SubjectSnapshot(
        subject = subject,
        exists = false,
        status = null,
        summary = null,
    )

internal fun FoodEntity.toSnapshot(): SubjectSnapshot =
    SubjectSnapshot(
        subject = EntityRef(EntityType.FOOD, id),
        exists = deletedAt == null,
        status = status.name,
        summary = name,
    )

internal fun StockLotEntity.toSnapshot(): SubjectSnapshot =
    SubjectSnapshot(
        subject = EntityRef(EntityType.STOCK_LOT, id),
        exists = deletedAt == null,
        status = status.name,
        summary = listOfNotNull(
            quantity.amount?.toString(),
            quantity.unit.name,
            location,
        ).joinToString(" "),
    )

internal fun SourceRecordEntity.toDomain(): Source =
    Source(
        id = SourceId(id),
        kind = kind,
        label = label,
        externalId = externalId,
        uri = uri,
        capturedAt = capturedAt?.let(::IsoTimestamp),
        truthState = truthState,
    )

internal fun PageEntity.toPageEntityRef(): EntityRef? =
    entityId?.let { id -> EntityRef(entityType ?: EntityType.UNKNOWN, id) }

internal fun FoodEntity.toFood(): Food =
    Food(
        id = FoodId(id),
        pageId = PageId(pageId),
        name = name,
        status = status,
        aliasIds = aliasIds.map(::FoodAliasId),
        stockLotIds = stockLotIds.map(::StockLotId),
        nutritionSnapshotIds = nutritionSnapshotIds.map(::NutritionSnapshotId),
        attachmentIds = attachmentIds.map(::AttachmentId),
        source = source.toDomain(),
        confidence = confidence.toDomain(),
        truthState = truthState,
    )

internal fun StockLotEntity.toStockLot(): StockLot =
    StockLot(
        id = StockLotId(id),
        foodId = FoodId(foodId),
        quantity = Quantity(
            amount = quantity.amount,
            unit = quantity.unit,
            truthState = quantity.truthState,
        ),
        purchasedOn = purchasedOn?.let(::IsoDate),
        expiresOn = expiresOn?.let(::IsoDate),
        location = location,
        status = if (archivedAt == null) status else StockLotStatus.ARCHIVED,
        source = source.toDomain(),
        confidence = confidence.toDomain(),
        truthState = truthState,
    )
