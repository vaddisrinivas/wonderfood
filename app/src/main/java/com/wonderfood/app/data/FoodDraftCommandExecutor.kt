package com.wonderfood.app.data

import com.wonderfood.app.LinkActionOperation
import com.wonderfood.app.WonderFoodCommandContract

class FoodDraftCommandExecutor(
    private val sinkProvider: () -> FoodDraftCommandSink,
) {
    fun execute(command: FoodDraftCommand): FoodDraftExecutionResult {
        val draft = FoodDraftNormalizer.normalize(command.draft)
        val normalizedCommand = command.copy(draft = draft)
        val errors = FoodDraftValidator.validate(draft) + FoodDraftCommandPolicy.validate(normalizedCommand)
        if (errors.isNotEmpty()) return FoodDraftExecutionResult.Rejected(errors)
        return runCatching {
            sinkProvider().applyDraft(
                draft = draft,
                sourceMessageId = command.sourceMessageId,
                writeSource = command.origin.writeSource,
            )
        }.fold(
            onSuccess = FoodDraftExecutionResult::Applied,
            onFailure = { error ->
                FoodDraftExecutionResult.Rejected(
                    listOf(
                        (error as? FoodDraftApplyException)?.message
                            ?.takeIf(String::isNotBlank)
                            ?: "Draft could not be saved. No changes were applied.",
                    ),
                )
            },
        )
    }

    fun reject(command: FoodDraftCommand): String =
        sinkProvider().rejectDraft(
            draft = FoodDraftNormalizer.normalize(command.draft),
            sourceMessageId = command.sourceMessageId,
            writeSource = command.origin.writeSource,
        )
}

object FoodDraftCommandPolicy {
    fun validate(command: FoodDraftCommand): List<String> =
        buildList {
            val leafCount = command.draft.leafDraftCount()
            if (command.origin.limitedExternalBulk && leafCount > EXTERNAL_REVIEW_DRAFT_LIMIT) {
                add("External review drafts are limited to $EXTERNAL_REVIEW_DRAFT_LIMIT actions.")
            }
            if (command.origin == FoodDraftCommandOrigin.VOICE_AUTO_ACCEPT && command.draft.hasDestructiveOperation()) {
                add("Voice auto-accept cannot apply destructive drafts.")
            }
        }

    private fun FoodDraft.leafDraftCount(): Int =
        when (this) {
            is CompositeDraft -> drafts.sumOf { it.leafDraftCount() }
            else -> 1
        }

    private fun FoodDraft.hasDestructiveOperation(): Boolean =
        when (this) {
            is CompositeDraft -> drafts.any { it.hasDestructiveOperation() }
            else -> operation == FoodOperation.DELETE
        }

    private val FoodDraftCommandOrigin.limitedExternalBulk: Boolean
        get() =
            when (this) {
                FoodDraftCommandOrigin.AI_REVIEW,
                FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
                FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
                FoodDraftCommandOrigin.LOCAL_FALLBACK,
                FoodDraftCommandOrigin.RECEIPT,
                FoodDraftCommandOrigin.VOICE_AUTO_ACCEPT,
                -> true
                FoodDraftCommandOrigin.CSV_IMPORT,
                FoodDraftCommandOrigin.MANUAL_SAVE,
                -> false
            }

    private const val EXTERNAL_REVIEW_DRAFT_LIMIT = 12
}

interface FoodDraftCommandSink {
    fun applyDraft(draft: FoodDraft, sourceMessageId: Long?, writeSource: String): String
    fun rejectDraft(draft: FoodDraft, sourceMessageId: Long?, writeSource: String): String
}

class FoodDraftApplyException(message: String) : IllegalStateException(message)

class FoodMutationCommandExecutor(
    private val sinkProvider: () -> FoodMutationCommandSink,
) {
    fun execute(command: FoodMutationCommand, write: () -> String): FoodMutationExecutionResult {
        val normalized = command.normalized()
        val errors = FoodMutationValidator.validate(normalized)
        if (errors.isNotEmpty()) {
            val summary = "Command rejected: ${errors.joinToString("; ")}"
            sinkProvider().recordMutationCommand(normalized, status = "REJECTED", summary = summary)
            return FoodMutationExecutionResult.Rejected(errors)
        }
        return runCatching {
            val summary = write()
            sinkProvider().recordMutationCommand(normalized, status = "APPLIED", summary = summary)
            FoodMutationExecutionResult.Applied(summary)
        }.getOrElse { error ->
            val summary = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
            sinkProvider().recordMutationCommand(normalized, status = "FAILED", summary = summary)
            FoodMutationExecutionResult.Failed(summary)
        }
    }
}

interface FoodMutationCommandSink {
    fun recordMutationCommand(command: FoodMutationCommand, status: String, summary: String)
}

data class FoodDraftCommand(
    val draft: FoodDraft,
    val sourceMessageId: Long?,
    val origin: FoodDraftCommandOrigin,
)

enum class FoodDraftCommandOrigin(val writeSource: String) {
    AI_REVIEW("ai_review"),
    CSV_IMPORT("csv_import"),
    EXTERNAL_PROPOSAL("external_proposal"),
    GOOGLE_ASSISTANT("google_assistant"),
    LOCAL_FALLBACK("local_fallback"),
    MANUAL_SAVE("manual"),
    RECEIPT("receipt"),
    VOICE_AUTO_ACCEPT("voice_auto_accept"),
}

data class FoodMutationCommand(
    val type: FoodMutationCommandType,
    val label: String,
    val origin: FoodDraftCommandOrigin,
    val sourceMessageId: Long? = null,
    val payload: Map<String, String?> = emptyMap(),
) {
    fun normalized(): FoodMutationCommand =
        copy(
            label = label.trim(),
            payload = payload
                .filterKeys { it.isNotBlank() }
                .mapKeys { it.key.trim() }
                .mapValues { it.value?.trim() },
        )
}

enum class FoodMutationCommandType(val commandType: String, val destructive: Boolean = false) {
    ADD_MISSING_RECIPE_GROCERIES("ADD_MISSING_RECIPE_GROCERIES"),
    ADD_MEAL_PLAN_ENTRY("ADD_MEAL_PLAN_ENTRY"),
    DELETE_ALL_MEAL_PLANS("DELETE_ALL_MEAL_PLANS", destructive = true),
    DELETE_GROCERY("DELETE_GROCERY", destructive = true),
    DELETE_INVENTORY("DELETE_INVENTORY", destructive = true),
    DELETE_MEAL_LOG("DELETE_MEAL_LOG", destructive = true),
    DELETE_MEAL_PLAN_ENTRIES("DELETE_MEAL_PLAN_ENTRIES", destructive = true),
    DELETE_MEAL_PLAN_ENTRY("DELETE_MEAL_PLAN_ENTRY", destructive = true),
    DELETE_RECIPE("DELETE_RECIPE", destructive = true),
    LOG_EVENT("LOG_EVENT"),
    MARK_GROCERY_BOUGHT("MARK_GROCERY_BOUGHT"),
    UPDATE_GROCERY("UPDATE_GROCERY"),
    UPDATE_INVENTORY("UPDATE_INVENTORY"),
    UPDATE_MEAL_LOG("UPDATE_MEAL_LOG"),
    UPDATE_MEAL_PLAN("UPDATE_MEAL_PLAN"),
    UPDATE_MEAL_PLAN_ENTRY("UPDATE_MEAL_PLAN_ENTRY"),
    UPDATE_RECEIPT("UPDATE_RECEIPT"),
    UPDATE_RECIPE("UPDATE_RECIPE"),
    UPDATE_RECIPE_IMAGE("UPDATE_RECIPE_IMAGE"),
    UNDO_ACTION("UNDO_ACTION"),
}

sealed interface FoodDraftExecutionResult {
    data class Applied(val summary: String) : FoodDraftExecutionResult
    data class Rejected(val errors: List<String>) : FoodDraftExecutionResult
}

sealed interface FoodMutationExecutionResult {
    data class Applied(val summary: String) : FoodMutationExecutionResult
    data class Rejected(val errors: List<String>) : FoodMutationExecutionResult
    data class Failed(val summary: String) : FoodMutationExecutionResult
}

object FoodDraftValidator {
    fun validate(draft: FoodDraft): List<String> =
        when (draft) {
            is CompositeDraft -> draft.validateComposite()
            is GroceryDraft -> draft.items.validateCandidates("grocery")
            is InventoryDraft -> draft.items.validateCandidates("inventory")
            is ReceiptDraft -> draft.validateReceipt()
            is LinkActionDraft -> draft.validateLinkAction()
            is MealLogDraft -> draft.validateMealLog()
            is MealPlanDraft -> draft.validateMealPlan()
            is RecipeDraft -> draft.validateRecipe()
        }

    private fun CompositeDraft.validateComposite(): List<String> =
        buildList {
            if (drafts.isEmpty()) add("Composite draft must include at least one child draft.")
            drafts.forEachIndexed { index, child ->
                validate(child).forEach { error -> add("Draft ${index + 1}: $error") }
            }
        }

    private fun ReceiptDraft.validateReceipt(): List<String> =
        buildList {
            if (items.isEmpty()) add("Receipt draft must include at least one reviewed line.")
            items.map { it.food }.validateCandidates("receipt").forEach(::add)
            items.forEachIndexed { index, item ->
                if (item.food.confidence !in 0.0..1.0) add("Receipt item ${index + 1} confidence must be between 0 and 1.")
            }
        }

    private fun LinkActionDraft.validateLinkAction(): List<String> =
        buildList {
            val spec = WonderFoodCommandContract.actionSpec(actionType)
            if (spec == null) add("Linked action '$actionType' is not supported.")
            if (spec != null && targetKind != spec.targetKind) {
                add("Linked action target '$targetKind' does not match '$actionType'.")
            }
            if (targetRef.isNotBlank() && targetRef.toLongOrNull() == null) {
                add("Linked action target id must be a local numeric id.")
            }
            val operation = spec?.operation
            val isCreate = operation == LinkActionOperation.CREATE || operation == LinkActionOperation.LOG
            val isSpecialNoFieldAction = operation in setOf(
                LinkActionOperation.MARK_BOUGHT,
                LinkActionOperation.MARK_EATEN,
                LinkActionOperation.MARK_SKIPPED,
            )
            if (!isCreate && targetRef.isBlank() && displayName.isBlank() && targetKind != "preferences") {
                add("Linked action needs a target id or exact name.")
            }
            if (operation == LinkActionOperation.DELETE && !destructive) {
                add("Linked destructive action is missing its destructive marker.")
            }
            if (operation != LinkActionOperation.DELETE && destructive) {
                add("Linked non-destructive action cannot carry a destructive marker.")
            }
            if (targetKind == "preferences" && !sensitive) {
                add("Linked preference action is missing its sensitive marker.")
            }
            if (operation != LinkActionOperation.DELETE && !isCreate && !isSpecialNoFieldAction && fields.isEmpty()) {
                add("Linked update needs at least one field.")
            }
            if (fields.size > 24) add("Linked action has too many fields.")
            val targetFields = WonderFoodCommandContract.allowedFields(targetKind)
            fields.forEach { (key, value) ->
                if (key !in WonderFoodCommandContract.LINK_ACTION_FIELD_KEYS) add("Linked action field '$key' is not supported.")
                if (key !in targetFields) add("Linked action field '$key' is not supported for $targetKind.")
                if (value.length > WonderFoodCommandContract.MAX_LINK_FIELD_LENGTH) add("Linked action field '$key' is too long.")
            }
            validateCreateFields(operation).forEach(::add)
            validateTypedFields().forEach(::add)
        }

    private fun LinkActionDraft.validateCreateFields(operation: LinkActionOperation?): List<String> =
        buildList {
            if (operation != LinkActionOperation.CREATE && operation != LinkActionOperation.LOG) return@buildList
            fun hasAny(vararg keys: String): Boolean =
                displayName.isNotBlank() || keys.any { fields[it].orEmpty().isNotBlank() }
            when (targetKind) {
                "inventory", "grocery", "meal_log", "plan_entry" ->
                    if (!hasAny("name", "title", "text")) add("Linked $targetKind create action needs a name or title.")
                "recipe" -> {
                    if (!hasAny("name", "title")) add("Linked recipe create action needs a title.")
                    if (fields["ingredients"].isNullOrBlank() && fields["steps"].isNullOrBlank()) {
                        add("Linked recipe create action needs ingredients or steps.")
                    }
                }
                "meal_plan" -> {
                    if (!hasAny("name", "title")) add("Linked meal plan create action needs a title.")
                    if (fields["days"].isNullOrBlank() && fields["days_text"].isNullOrBlank() && fields["text"].isNullOrBlank()) {
                        add("Linked meal plan create action needs planned days or entries.")
                    }
                }
                "event" -> if (fields["event_type"].isNullOrBlank()) add("Linked event log needs an event type.")
            }
        }

    private fun LinkActionDraft.validateTypedFields(): List<String> =
        buildList {
            val integerFields = setOf("calorie_target", "calories", "duration_minutes", "prep_minutes", "servings")
            val longFields = setOf(
                "date_epoch_day",
                "ended_at_millis",
                "expires_at_millis",
                "inventory_item_id",
                "logged_date_epoch_day",
                "meal_log_id",
                "recipe_id",
                "shopping_trip_id",
                "started_at_millis",
            )
            val decimalFields = setOf("amount", "carbs_g", "fat_g", "protein_g")
            fields.forEach { (key, value) ->
                when {
                    key in integerFields && (value.toIntOrNull() == null || value.toInt() < 0) ->
                        add("Linked action field '$key' must be a non-negative whole number.")
                    key in longFields && value.toLongOrNull() == null ->
                        add("Linked action field '$key' must be a whole number.")
                    key in decimalFields && (value.toDoubleOrNull() == null || value.toDouble() < 0.0) ->
                        add("Linked action field '$key' must be a non-negative number.")
                }
            }
            fields["zone"]?.lowercase()?.takeUnless { it in VALID_ZONES }?.let {
                add("Linked action zone '$it' is not supported.")
            }
            fields["meal_slot"]?.lowercase()?.takeUnless { it in VALID_MEAL_SLOTS }?.let {
                add("Linked action meal slot '$it' is not supported.")
            }
            fields["slot"]?.lowercase()?.takeUnless { it in VALID_MEAL_SLOTS }?.let {
                add("Linked action slot '$it' is not supported.")
            }
            fields["event_type"]?.lowercase()?.takeUnless { it in VALID_EVENT_TYPES }?.let {
                add("Linked action event type '$it' is not supported.")
            }
            fields["confidence"]?.lowercase()?.takeUnless { it in VALID_CONFIDENCE_VALUES }?.let {
                add("Linked action confidence '$it' is not supported.")
            }
            fields["status"]?.lowercase()?.let { status ->
                val allowed = when (targetKind) {
                    "grocery" -> VALID_GROCERY_STATUSES
                    "plan_entry" -> VALID_PLAN_ENTRY_STATUSES
                    else -> emptySet()
                }
                if (status !in allowed) add("Linked action status '$status' is not supported for $targetKind.")
            }
        }

    private fun List<FoodCandidate>.validateCandidates(label: String): List<String> =
        buildList {
            if (this@validateCandidates.isEmpty()) add("$label draft must include at least one item.")
            this@validateCandidates.forEachIndexed { index, item ->
                if (item.name.isBlank()) add("$label item ${index + 1} needs a name.")
                if (item.calories != null && item.calories < 0) add("${item.name.ifBlank { label }} calories cannot be negative.")
                if (item.proteinGrams != null && item.proteinGrams < 0.0) add("${item.name.ifBlank { label }} protein cannot be negative.")
                if (item.carbsGrams != null && item.carbsGrams < 0.0) add("${item.name.ifBlank { label }} carbs cannot be negative.")
                if (item.fatGrams != null && item.fatGrams < 0.0) add("${item.name.ifBlank { label }} fat cannot be negative.")
            }
        }

    private fun RecipeDraft.validateRecipe(): List<String> =
        buildList {
            if (titleText.isBlank()) add("Recipe title is required.")
            if (ingredientsText.isBlank() && stepsText.isBlank()) add("Recipe needs ingredients or steps.")
            if (servings != null && servings <= 0) add("Recipe servings must be positive.")
            if (prepMinutes != null && prepMinutes < 0) add("Recipe prep minutes cannot be negative.")
        }

    private fun MealLogDraft.validateMealLog(): List<String> =
        buildList {
            if (titleText.isBlank()) add("Meal title is required.")
            if (calories != null && calories < 0) add("Meal calories cannot be negative.")
            if (proteinGrams != null && proteinGrams < 0.0) add("Meal protein cannot be negative.")
            if (carbsGrams != null && carbsGrams < 0.0) add("Meal carbs cannot be negative.")
            if (fatGrams != null && fatGrams < 0.0) add("Meal fat cannot be negative.")
        }

    private fun MealPlanDraft.validateMealPlan(): List<String> =
        buildList {
            if (titleText.isBlank()) add("Meal plan title is required.")
            if (daysText.isBlank() && entries.isEmpty()) add("Meal plan needs days text or entries.")
            entries.forEachIndexed { index, entry ->
                if (entry.title.isBlank()) add("Meal plan entry ${index + 1} needs a title.")
                if (entry.calorieTarget != null && entry.calorieTarget < 0) add("Meal plan entry ${index + 1} calories cannot be negative.")
            }
        }
}

private val VALID_ZONES = setOf("fridge", "refrigerator", "freezer", "frozen", "pantry", "shelf", "cupboard")
private val VALID_MEAL_SLOTS = setOf("breakfast", "lunch", "dinner", "snack", "flex", "flexible")
private val VALID_EVENT_TYPES = setOf("water", "hydration", "meal", "cook", "cooking", "shop", "shopping", "prep", "grocery_purchase", "purchase", "pantry_use", "inventory_use", "outside_food", "restaurant")
private val VALID_CONFIDENCE_VALUES = setOf("exact", "estimated", "estimate", "ai_estimated", "ai estimated")
private val VALID_GROCERY_STATUSES = setOf("bought", "done", "purchased", "needed", "need", "todo")
private val VALID_PLAN_ENTRY_STATUSES = setOf("eaten", "skipped", "draft", "planned")

object FoodMutationValidator {
    fun validate(command: FoodMutationCommand): List<String> =
        buildList {
            if (command.label.isBlank()) add("Mutation command needs a label.")
            if (command.type.destructive && command.origin.blocksDirectDestructiveMutation) {
                add("${command.origin.writeSource} cannot execute destructive mutations directly.")
            }
        }

    private val FoodDraftCommandOrigin.blocksDirectDestructiveMutation: Boolean
        get() =
            when (this) {
                FoodDraftCommandOrigin.AI_REVIEW,
                FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
                FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
                FoodDraftCommandOrigin.LOCAL_FALLBACK,
                FoodDraftCommandOrigin.RECEIPT,
                FoodDraftCommandOrigin.VOICE_AUTO_ACCEPT,
                -> true
                FoodDraftCommandOrigin.CSV_IMPORT,
                FoodDraftCommandOrigin.MANUAL_SAVE,
                -> false
            }
}
