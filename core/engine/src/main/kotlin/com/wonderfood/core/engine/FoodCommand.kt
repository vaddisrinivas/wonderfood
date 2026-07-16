package com.wonderfood.core.engine

import com.wonderfood.core.model.Confidence
import com.wonderfood.core.model.EntityRef
import com.wonderfood.core.model.EntityType
import com.wonderfood.core.model.Food
import com.wonderfood.core.model.FoodAlias
import com.wonderfood.core.model.FoodEvent
import com.wonderfood.core.model.FoodEventId
import com.wonderfood.core.model.FoodEventType
import com.wonderfood.core.model.FoodId
import com.wonderfood.core.model.IsoTimestamp
import com.wonderfood.core.model.Page
import com.wonderfood.core.model.PageKind
import com.wonderfood.core.model.Source
import com.wonderfood.core.model.StockLot
import com.wonderfood.core.model.StockLotId
import com.wonderfood.core.model.TruthState

@JvmInline
public value class IdempotencyKey(public val value: String) {
    init {
        require(value.isNotBlank()) { "Idempotency key must not be blank." }
    }
}

@JvmInline
public value class FoodActionId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Food action id must not be blank." }
    }
}

public enum class CommandRisk {
    LOW,
    REVIEW,
    CONFIRM,
    DESTRUCTIVE,
}

public data class ConfirmationRequirement(
    val required: Boolean,
    val risk: CommandRisk,
    val reason: String,
)

public data class SubjectSnapshot(
    val subject: EntityRef,
    val exists: Boolean,
    val status: String?,
    val summary: String?,
)

public data class FoodActionAudit(
    val id: FoodActionId,
    val idempotencyKey: IdempotencyKey,
    val actionType: String,
    val subject: EntityRef,
    val occurredAt: IsoTimestamp,
    val payload: String,
    val before: SubjectSnapshot?,
    val after: SubjectSnapshot?,
    val source: Source,
    val confidence: Confidence,
    val truthState: TruthState,
)

public sealed interface FoodCommand {
    public val idempotencyKey: IdempotencyKey
    public val requestedAt: IsoTimestamp
    public val source: Source
    public val confidence: Confidence
    public val subject: EntityRef
    public val summary: String

    public data class CreateFoodGraph(
        override val idempotencyKey: IdempotencyKey,
        override val requestedAt: IsoTimestamp,
        override val source: Source,
        override val confidence: Confidence,
        val page: Page,
        val food: Food,
        val aliases: List<FoodAlias>,
        val stockLots: List<StockLot>,
    ) : FoodCommand {
        override val subject: EntityRef = EntityRef(EntityType.FOOD, food.id.value)
        override val summary: String = "Create food ${food.name}"
    }

    public data class AddStockLot(
        override val idempotencyKey: IdempotencyKey,
        override val requestedAt: IsoTimestamp,
        override val source: Source,
        override val confidence: Confidence,
        val stockLot: StockLot,
    ) : FoodCommand {
        override val subject: EntityRef = EntityRef(EntityType.STOCK_LOT, stockLot.id.value)
        override val summary: String = "Add stock lot ${stockLot.id.value}"
    }

    public data class ArchiveFood(
        override val idempotencyKey: IdempotencyKey,
        override val requestedAt: IsoTimestamp,
        override val source: Source,
        override val confidence: Confidence,
        val foodId: FoodId,
        val reason: String,
    ) : FoodCommand {
        override val subject: EntityRef = EntityRef(EntityType.FOOD, foodId.value)
        override val summary: String = "Archive food ${foodId.value}"
    }

    public data class ArchiveStockLot(
        override val idempotencyKey: IdempotencyKey,
        override val requestedAt: IsoTimestamp,
        override val source: Source,
        override val confidence: Confidence,
        val stockLotId: StockLotId,
        val reason: String,
    ) : FoodCommand {
        override val subject: EntityRef = EntityRef(EntityType.STOCK_LOT, stockLotId.value)
        override val summary: String = "Archive stock lot ${stockLotId.value}"
    }

    public data class UndoFoodAction(
        override val idempotencyKey: IdempotencyKey,
        override val requestedAt: IsoTimestamp,
        override val source: Source,
        override val confidence: Confidence,
        val actionId: FoodActionId,
        val reason: String,
    ) : FoodCommand {
        override val subject: EntityRef = EntityRef(EntityType.FOOD_EVENT, actionId.value)
        override val summary: String = "Undo action ${actionId.value}"
    }
}

public fun FoodCommand.actionType(): String = when (this) {
    is FoodCommand.AddStockLot -> "AddStockLot"
    is FoodCommand.ArchiveFood -> "ArchiveFood"
    is FoodCommand.ArchiveStockLot -> "ArchiveStockLot"
    is FoodCommand.CreateFoodGraph -> "CreateFoodGraph"
    is FoodCommand.UndoFoodAction -> "UndoFoodAction"
}

public fun FoodCommand.confirmationRequirement(): ConfirmationRequirement = when (this) {
    is FoodCommand.AddStockLot -> ConfirmationRequirement(
        required = false,
        risk = CommandRisk.REVIEW,
        reason = "New inventory should remain reviewable and undoable.",
    )

    is FoodCommand.CreateFoodGraph -> ConfirmationRequirement(
        required = false,
        risk = CommandRisk.REVIEW,
        reason = "New food records should preserve source and confidence.",
    )

    is FoodCommand.ArchiveFood,
    is FoodCommand.ArchiveStockLot,
    -> ConfirmationRequirement(
        required = true,
        risk = CommandRisk.DESTRUCTIVE,
        reason = "Archive hides data from active views.",
    )

    is FoodCommand.UndoFoodAction -> ConfirmationRequirement(
        required = true,
        risk = CommandRisk.CONFIRM,
        reason = "Undo creates another audited mutation.",
    )
}

public fun FoodCommand.foodEvent(id: FoodEventId, type: FoodEventType, note: String?): FoodEvent =
    FoodEvent(
        id = id,
        subject = subject,
        type = type,
        occurredAt = requestedAt,
        quantity = when (this) {
            is FoodCommand.AddStockLot -> stockLot.quantity
            else -> null
        },
        note = note,
        source = source,
        confidence = confidence,
        truthState = TruthState.USER_CONFIRMED,
    )

public fun FoodCommand.validate(): List<String> {
    val errors = mutableListOf<String>()
    if (summary.isBlank()) errors += "Command summary must not be blank."
    when (this) {
        is FoodCommand.CreateFoodGraph -> {
            if (page.kind != PageKind.FOOD) errors += "CreateFoodGraph page must be a food page."
            if (page.entity?.type != EntityType.FOOD || page.entity?.id != food.id.value) {
                errors += "Food page entity must point at the created food."
            }
            if (food.pageId != page.id) errors += "Food must point at the created page."
            aliases.filterNot { it.foodId == food.id }.forEach {
                errors += "Alias ${it.id.value} points at a different food."
            }
            stockLots.filterNot { it.foodId == food.id }.forEach {
                errors += "Stock lot ${it.id.value} points at a different food."
            }
        }

        is FoodCommand.AddStockLot -> {
            if (stockLot.foodId.value.isBlank()) errors += "Stock lot must point at a food."
        }

        is FoodCommand.ArchiveFood -> {
            if (reason.isBlank()) errors += "Archiving food requires a reason."
        }

        is FoodCommand.ArchiveStockLot -> {
            if (reason.isBlank()) errors += "Archiving stock lots requires a reason."
        }

        is FoodCommand.UndoFoodAction -> {
            if (reason.isBlank()) errors += "Undo requires a reason."
        }
    }
    return errors
}
