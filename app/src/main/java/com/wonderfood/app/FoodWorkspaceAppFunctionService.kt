package com.wonderfood.app

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import androidx.appfunctions.service.AppFunction
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionFunctionNotFoundException
import androidx.core.content.edit
import com.wonderfood.app.data.FoodDraftCommandOrigin
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.LinkActionDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.HouseholdDraftCommandMapper
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import com.wonderfood.core.data.HouseholdRepositories
import com.wonderfood.core.data.room.WonderFoodDatabaseFactory
import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.engine.HouseholdCommandExecutionResult
import com.wonderfood.core.engine.HouseholdCommandExecutor
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.collections.LinkedHashMap

private const val APP_FUNCTIONS_PREFS_NAME = "wonderfood_app_functions"
private const val APP_FUNCTIONS_REQUEST_ID_PREFIX = "wf-app-function-request:"
private const val APP_FUNCTIONS_ACTION_IDEMPOTENCY_PREFIX = "wf-app-function-action:"
private const val APP_FUNCTION_MIN_SDK = 36

private const val APP_FUNCTION_STATUS_APPLIED = "APPLIED"
private const val APP_FUNCTION_STATUS_REJECTED = "REJECTED"
private const val APP_FUNCTION_STATUS_FAILED = "FAILED"
private const val APP_FUNCTION_STATUS_REPLAYED = "REPLAYED"
private const val APP_FUNCTION_STATUS_REVIEW_REQUIRED = "REVIEW_REQUIRED"

private const val EXECUTE_FOOD_WORKSPACE_ACTIONS_ID =
    "com.wonderfood.app.FoodWorkspaceAppFunctionService#executeFoodWorkspaceActions"
private const val PROPOSE_FOOD_WORKSPACE_ACTIONS_ID =
    "com.wonderfood.app.FoodWorkspaceAppFunctionService#proposeFoodWorkspaceActions"

@TargetApi(APP_FUNCTION_MIN_SDK)
@SuppressLint("NewApi")
class FoodWorkspaceAppFunctionService : AppFunctionService() {
    override suspend fun executeFunction(
        request: ExecuteAppFunctionRequest,
    ): ExecuteAppFunctionResponse {
        if (Build.VERSION.SDK_INT < APP_FUNCTION_MIN_SDK) {
            return ExecuteAppFunctionResponse.Error(
                AppFunctionAppUnknownException("Food workspace AppFunctions require Android 16+."),
            )
        }

        val functionId = request.functionIdentifier
        if (functionId != EXECUTE_FOOD_WORKSPACE_ACTIONS_ID &&
            functionId != PROPOSE_FOOD_WORKSPACE_ACTIONS_ID
        ) {
            return ExecuteAppFunctionResponse.Error(
                AppFunctionFunctionNotFoundException("Unable to find $functionId"),
            )
        }

        return try {
            val functionInput = request.functionParameters.deserialize(FoodWorkspaceActionRequest::class.java)
            val context = object : AppFunctionContext {
                override val context: Context = this@FoodWorkspaceAppFunctionService
            }
            val result = when (functionId) {
                EXECUTE_FOOD_WORKSPACE_ACTIONS_ID -> {
                    executeFoodWorkspaceActions(context, functionInput)
                }
                else -> {
                    proposeFoodWorkspaceActions(context, functionInput)
                }
            }
            ExecuteAppFunctionResponse.Success(
                AppFunctionData.serialize(
                    result,
                    FoodWorkspaceActionResponse::class.java,
                ),
            )
        } catch (error: Exception) {
            ExecuteAppFunctionResponse.Error(
                AppFunctionAppUnknownException(error.message ?: "Unknown error executing $functionId"),
            )
        }
    }

    private val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
    private val householdRepository by lazy { HouseholdRepositories.room(WonderFoodDatabaseFactory.create(applicationContext)) }
    private val householdCommandExecutor by lazy { HouseholdCommandExecutor(householdRepository) }
    private val householdDraftCommandMapper by lazy { HouseholdDraftCommandMapper(householdId) }
    private val actionPrefs by lazy {
        applicationContext.getSharedPreferences(APP_FUNCTIONS_PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Write-mode helper for trusted AppFunction callers.
     *
     * Non-destructive and non-sensitive actions are applied directly.
     * Destructive actions (delete-like operations) and preference/allergy edits are
     * intentionally review-only and are not applied directly.
     */
    @AppFunction(isEnabled = true)
    suspend fun executeFoodWorkspaceActions(
        context: AppFunctionContext,
        request: FoodWorkspaceActionRequest,
    ): FoodWorkspaceActionResponse = withContext(Dispatchers.IO) {
        executeRequest(request, executeChanges = true)
    }

    /**
     * Read-only preview mode for callers that want to validate requests first.
     * Actions are normalized and validated, but no writes are performed.
     */
    @AppFunction(isEnabled = true)
    suspend fun proposeFoodWorkspaceActions(
        context: AppFunctionContext,
        request: FoodWorkspaceActionRequest,
    ): FoodWorkspaceActionResponse = withContext(Dispatchers.IO) {
        executeRequest(request, executeChanges = false)
    }

    internal fun executeRequest(
        request: FoodWorkspaceActionRequest,
        executeChanges: Boolean,
    ): FoodWorkspaceActionResponse {
        val normalizedActions = request.normalizedActions()
        if (normalizedActions.isEmpty()) {
            throw AppFunctionInvalidArgumentException("No actions were provided.")
        }
        if (normalizedActions.size > WonderFoodCommandContract.MAX_BULK_ACTIONS) {
            throw AppFunctionInvalidArgumentException(
                "Too many actions in request. Maximum ${WonderFoodCommandContract.MAX_BULK_ACTIONS} actions are allowed.",
            )
        }

        var appliedCount = 0
        var rejectedCount = 0
        var failedCount = 0
        var replayedCount = 0
        var reviewRequiredCount = 0
        val attempts = buildList {
            normalizedActions.forEachIndexed { index, action ->
                val attempt = executeSingleAction(index, request, action, executeChanges)
                when (attempt.status) {
                    APP_FUNCTION_STATUS_APPLIED -> appliedCount++
                    APP_FUNCTION_STATUS_REJECTED -> rejectedCount++
                    APP_FUNCTION_STATUS_FAILED -> failedCount++
                    APP_FUNCTION_STATUS_REPLAYED -> replayedCount++
                    APP_FUNCTION_STATUS_REVIEW_REQUIRED -> reviewRequiredCount++
                }
                add(attempt)
            }
        }

        return FoodWorkspaceActionResponse(
            requestId = request.requestId,
            totalCount = normalizedActions.size,
            appliedCount = appliedCount,
            rejectedCount = rejectedCount,
            failedCount = failedCount,
            replayedCount = replayedCount,
            summary = responseSummary(
                applied = appliedCount,
                rejected = rejectedCount,
                failed = failedCount,
                replayed = replayedCount,
                reviewRequired = reviewRequiredCount,
                executeChanges = executeChanges,
            ),
            attempts = attempts,
        )
    }

    private fun executeSingleAction(
        index: Int,
        request: FoodWorkspaceActionRequest,
        action: FoodWorkspaceAction,
        executeChanges: Boolean,
    ): FoodWorkspaceActionAttempt {
        val actionType = WonderFoodCommandContract.normalizeActionType(action.type)
            ?: return FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_FAILED,
                actionType = action.type,
                targetKind = action.targetKind,
                message = "Action type '${action.type}' is not supported.",
                idempotentReplay = false,
            )
        val spec = WonderFoodCommandContract.actionSpec(actionType)
            ?: return FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_FAILED,
                actionType = actionType,
                targetKind = action.targetKind,
                message = "Action '$actionType' is not supported.",
                idempotentReplay = false,
            )

        val requestedTargetKind = action.targetKind.ifBlank { spec.targetKind }
        if (requestedTargetKind != spec.targetKind) {
            return FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_FAILED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = "Action '$actionType' does not target '$requestedTargetKind'.",
                idempotentReplay = false,
            )
        }

        val draft = action.toDraft(actionType, requestedTargetKind)

        if (draft.destructive || draft.sensitive) {
            return FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_REVIEW_REQUIRED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = if (executeChanges) {
                    "Action '$actionType' is destructive or preference-sensitive and requires app review."
                } else {
                    "Action '$actionType' is review-only. No direct write was performed."
                },
                idempotentReplay = false,
            )
        }
        if (!draft.canExecuteDirectlyInCanonicalRepository()) {
            return FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_REVIEW_REQUIRED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = if (executeChanges) {
                    "Action '$actionType' requires app review before applying."
                } else {
                    "Action '$actionType' was prepared for app review."
                },
                idempotentReplay = false,
            )
        }

        if (!executeChanges) {
            return FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_REVIEW_REQUIRED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = "Action '$actionType' was prepared for review.",
                idempotentReplay = false,
            )
        }

        val idempotencyKey = action.resolveIdempotencyKey(request.requestId, index)
        if (idempotencyKey.isNotBlank() && alreadyHandled(idempotencyKey)) {
            return FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_REPLAYED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = "Action already processed for key '$idempotencyKey'.",
                idempotentReplay = true,
            )
        }

        val commandResult = try {
            val canonicalDraft = draft.toCanonicalDraft()
            if (canonicalDraft == null) {
                throw IllegalStateException("Action '$actionType' cannot be converted to canonical draft.")
            }
            runBlocking {
                ensureCanonicalHousehold()
                executeCanonicalCommands(
                    householdDraftCommandMapper.toCommands(
                        canonicalDraft,
                        FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
                    ),
                )
            }
        } catch (error: Exception) {
            return FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_FAILED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = error.message?.takeIf(String::isNotBlank)
                    ?: "Action '$actionType' failed unexpectedly.",
                idempotentReplay = false,
            )
        }

        val attempt = when (commandResult) {
            is AppFunctionCommandResult.Applied -> FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_APPLIED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = commandResult.summary,
                idempotentReplay = false,
            )
            is AppFunctionCommandResult.Rejected -> FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_REJECTED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = commandResult.errors.joinToString(separator = "; "),
                idempotentReplay = false,
            )
        }

        if (idempotencyKey.isNotBlank()) markHandled(idempotencyKey)
        return attempt
    }

    private fun executeCanonicalCommands(
        commands: List<HouseholdCommand>,
    ): AppFunctionCommandResult {
        if (commands.isEmpty()) {
            return AppFunctionCommandResult.Rejected(listOf("No canonical command was produced."))
        }
        val results = commands.map { command ->
            runBlocking { householdCommandExecutor.execute(command) }
        }
        val rejectedErrors = results.filterIsInstance<HouseholdCommandExecutionResult.Rejected>()
            .flatMap { it.errors }
        if (rejectedErrors.isNotEmpty()) {
            return AppFunctionCommandResult.Rejected(rejectedErrors.distinct())
        }
        val appliedCount = results.count { it is HouseholdCommandExecutionResult.Applied }
        return if (appliedCount > 0) {
            AppFunctionCommandResult.Applied("Applied ${results.size} canonical command(s).")
        } else {
            AppFunctionCommandResult.Applied("No new canonical rows were written.")
        }
    }

    private fun ensureCanonicalHousehold() {
        if (householdRepositorySnapshotExists()) return
        val now = UtcTimestamp(System.currentTimeMillis())
        runBlocking {
            householdCommandExecutor.execute(
                HouseholdCommand.UpsertHousehold(
                    record = CommandRecord(
                        commandId = CommandId("00000000-0000-0000-0000-000000000105"),
                        householdId = householdId,
                        type = "UpsertHousehold",
                        source = SourceRef(SourceKind.SYSTEM, "app_functions"),
                        requestedAt = now,
                        appliedAt = now,
                        affectedEntityIds = emptyList(),
                    ),
                    household = Household(
                        id = householdId,
                        name = "My household",
                        defaultCurrency = "USD",
                        timezone = "America/New_York",
                        locale = "en-US",
                        activeDataHome = DataHomeKind.LOCAL,
                        schemaVersion = HouseholdWorkspaceContract.SCHEMA_VERSION,
                        createdAt = now,
                        updatedAt = now,
                        revision = 0,
                    ),
                ),
            )
        }
    }

    private fun householdRepositorySnapshotExists(): Boolean =
        runBlocking { householdRepository.snapshot(householdId) != null }

    private fun FoodWorkspaceAction.toDraft(actionType: String, targetKind: String): LinkActionDraft {
        return LinkActionDraft(
            actionType = actionType,
            targetKind = targetKind,
            targetRef = sanitizeText(this.targetRef, 96),
            displayName = sanitizeText(this.displayName, 240),
            fields = normalizedFields(),
        )
    }

    private fun LinkActionDraft.canExecuteDirectlyInCanonicalRepository(): Boolean {
        val spec = WonderFoodCommandContract.actionSpec(actionType) ?: return false
        val operation = spec.operation
        return when (spec.targetKind) {
            "inventory" -> operation == LinkActionOperation.CREATE || operation == LinkActionOperation.LOG
            "grocery" -> operation == LinkActionOperation.CREATE
            "recipe" -> operation == LinkActionOperation.CREATE
            "meal_log" -> operation == LinkActionOperation.LOG
            "meal_plan" -> operation == LinkActionOperation.CREATE
            else -> false
        }
    }

    private fun LinkActionDraft.toCanonicalDraft(): FoodDraft? {
        return when (actionTypeSpec().targetKind) {
            "inventory" -> if (isCreateLike() || isLogLike()) InventoryDraft(listOf(toFoodCandidate())) else null
            "grocery" -> if (isCreateLike()) GroceryDraft(listOf(toFoodCandidate())) else null
            "recipe" -> if (isCreateLike()) {
                RecipeDraft(
                    titleText = toTextValue("title", "name", "item", fallback = "Untitled recipe"),
                    ingredientsText = firstField("ingredients", "ingredient"),
                    stepsText = firstField("steps", "step"),
                    servings = firstInt("servings"),
                    prepMinutes = firstInt("prep_minutes", "prep"),
                    tags = firstField("tags"),
                    imageUri = firstField("image_uri").ifBlank { null },
                    imageUrl = firstField("image_url"),
                )
            } else null
            "meal_log" -> if (isLogLike()) {
                MealLogDraft(
                    titleText = toTextValue("title", "name", "item", fallback = "Logged meal"),
                    calories = firstInt("calories"),
                    proteinGrams = firstDouble("protein_g", "protein"),
                    carbsGrams = firstDouble("carbs_g", "carbs"),
                    fatGrams = firstDouble("fat_g", "fat"),
                    mealSlot = mealSlot(),
                    usedItemsText = firstField("used_items_text", "ingredients", "text"),
                    loggedDateEpochDay = firstLong("logged_date_epoch_day", "date_epoch_day"),
                )
            } else null
            "meal_plan" -> if (isCreateLike()) {
                MealPlanDraft(
                    titleText = toTextValue("title", "name", "item", fallback = "Meal plan"),
                    daysText = firstField("days_text", "days").ifBlank { "1" },
                    groceryHint = firstField("grocery_hint"),
                    entries = emptyList(),
                    startDateEpochDay = firstLong("date_epoch_day"),
                )
            } else null
            else -> null
        }
    }

    private fun FoodWorkspaceAction.toCanonicalDraft(): FoodDraft? {
        val actionType = WonderFoodCommandContract.normalizeActionType(type) ?: return null
        val targetKind = targetKind.ifBlank { WonderFoodCommandContract.actionSpec(actionType)?.targetKind.orEmpty() }
        return toDraft(actionType, targetKind).let { draft -> draft.toCanonicalDraft() }
    }

    private fun LinkActionDraft.actionTypeSpec() =
        WonderFoodCommandContract.actionSpec(actionType)
            ?: throw IllegalArgumentException("Unknown action type '$actionType'.")

    private fun LinkActionDraft.toFoodCandidate(): FoodCandidate {
        val name = firstField("name", "item", "title").ifBlank { displayName.ifBlank { "item" } }
        return FoodCandidate(
            name = name,
            quantity = firstField("quantity", "amount"),
            zone = classifyStorageZone(firstField("zone").ifBlank { name }),
            category = firstField("category").ifBlank { categorizeFood(name) },
            servingText = firstField("serving_text"),
            calories = firstInt("calories"),
            proteinGrams = firstDouble("protein_g", "protein"),
            carbsGrams = firstDouble("carbs_g", "carbs"),
            fatGrams = firstDouble("fat_g", "fat"),
            nutritionSource = firstField("nutrition_source"),
            notes = firstField("notes"),
            imageUri = firstField("image_uri").ifBlank { null },
            imageUrl = firstField("image_url"),
            expiresAtMillis = firstLong("expires_at_millis"),
        )
    }

    private fun LinkActionDraft.toTextValue(vararg keys: String, fallback: String): String {
        return firstField(*keys).ifBlank { fallback }
    }

    private fun LinkActionDraft.isCreateLike(): Boolean =
        actionTypeSpec().operation == LinkActionOperation.CREATE

    private fun LinkActionDraft.isLogLike(): Boolean =
        actionTypeSpec().operation == LinkActionOperation.LOG

    private fun LinkActionDraft.mealSlot(): MealSlot {
        return when (firstField("meal_slot", "slot").lowercase()) {
            "breakfast" -> MealSlot.BREAKFAST
            "lunch" -> MealSlot.LUNCH
            "dinner" -> MealSlot.DINNER
            "snack" -> MealSlot.SNACK
            else -> MealSlot.FLEX
        }
    }

    private fun LinkActionDraft.firstField(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> fields[key]?.trim()?.takeIf { it.isNotBlank() } }
            .orEmpty()

    private fun LinkActionDraft.firstInt(vararg keys: String): Int? =
        firstField(*keys).toIntOrNull()

    private fun LinkActionDraft.firstLong(vararg keys: String): Long? =
        firstField(*keys).toLongOrNull()

    private fun LinkActionDraft.firstDouble(vararg keys: String): Double? =
        firstField(*keys).toDoubleOrNull()

    private fun FoodWorkspaceAction.normalizedFields(): LinkedHashMap<String, String> {
        val normalized = LinkedHashMap<String, String>(fields.size)
        fields.forEach { field ->
            val key = sanitizeText(field.key, 80).lowercase().replace('-', '_')
            val value = sanitizeText(field.value, WonderFoodCommandContract.MAX_LINK_FIELD_LENGTH)
            if (key.isNotBlank() && value.isNotBlank()) normalized[key] = value
        }
        return normalized
    }

    private fun FoodWorkspaceAction.resolveIdempotencyKey(
        requestId: String,
        index: Int,
    ): String {
        val explicit = sanitizeText(idempotencyKey, 240)
        if (explicit.isNotBlank()) return explicit
        if (requestId.isNotBlank()) return sanitizeText("${APP_FUNCTIONS_REQUEST_ID_PREFIX}$requestId#$index", 240)
        return ""
    }

    private fun alreadyHandled(idempotencyKey: String): Boolean =
        actionPrefs.getBoolean(APP_FUNCTIONS_ACTION_IDEMPOTENCY_PREFIX + idempotencyKey, false)

    private fun markHandled(idempotencyKey: String) {
        actionPrefs.edit { putBoolean(APP_FUNCTIONS_ACTION_IDEMPOTENCY_PREFIX + idempotencyKey, true) }
    }

    private fun sanitizeText(value: String, maxLength: Int): String =
        value.filterNot(Char::isISOControl).trim().take(maxLength)

    private fun responseSummary(
        applied: Int,
        rejected: Int,
        failed: Int,
        replayed: Int,
        reviewRequired: Int,
        executeChanges: Boolean,
    ): String =
        when {
            failed > 0 -> "Action execution failed for $failed action(s)."
            reviewRequired > 0 && executeChanges ->
                "Applied $applied action(s), ${if (replayed > 0) "$replayed replayed, " else ""}" +
                    "$reviewRequired review-only action(s)."
            reviewRequired > 0 && applied == 0 && !executeChanges ->
                "$reviewRequired action(s) prepared for app review."
            applied > 0 && replayed > 0 ->
                "Applied $applied action(s), replayed $replayed action(s)."
            applied > 0 -> "Applied $applied action(s)."
            rejected > 0 -> "Action request rejected for $rejected action(s)."
            replayed > 0 -> "Action request completed with $replayed replayed action(s)."
            else -> "No action was executed."
        }
}

@AppFunctionSerializable
data class FoodWorkspaceActionRequest(
    /**
     * Request-scoped id for idempotency and dedupe.
     * Example: `wf-af-001`.
     */
    val requestId: String,
    /**
     * Backward-compatible single-action input; use `actions` for multi-action payloads.
     * Example: `{"type":"inventory.add", ...}`.
     */
    val action: FoodWorkspaceAction?,
    /**
     * Ordered list of actions to execute. Maximum of 12 actions in a request.
     * Example: `[ {"type":"inventory.add"}, {"type":"grocery.add"} ]`.
     */
    val actions: List<FoodWorkspaceAction>,
)

@AppFunctionSerializable
data class FoodWorkspaceAction(
    /** Action verb, e.g. `inventory.add`, `meal_log.log`, `grocery.mark_bought`. */
    val type: String,
    /**
     * Target model for the action, e.g. `inventory`, `grocery`, `meal_log`, `meal_plan`, `recipe`.
     */
    val targetKind: String,
    /**
     * Existing target object id string for mutate/delete actions; blank for create-like actions.
     */
    val targetRef: String,
    /**
     * Human-readable target label for create-like actions and for assistant-facing summaries.
     */
    val displayName: String,
    /**
     * Structured payload fields for the requested mutation.
     * Example: `[ {"key":"quantity","value":"2"}, {"key":"zone","value":"fridge"} ]`.
     */
    val fields: List<FoodWorkspaceActionField>,
    /** Optional caller-controlled dedupe key (`idempotencyKey`). */
    val idempotencyKey: String,
)

@AppFunctionSerializable
data class FoodWorkspaceActionField(
    val key: String,
    val value: String,
)

@AppFunctionSerializable
data class FoodWorkspaceActionResponse(
    val requestId: String,
    val totalCount: Int,
    val appliedCount: Int,
    val rejectedCount: Int,
    val failedCount: Int,
    val replayedCount: Int,
    val summary: String,
    val attempts: List<FoodWorkspaceActionAttempt>,
)

@AppFunctionSerializable
data class FoodWorkspaceActionAttempt(
    val status: String,
    val actionType: String,
    val targetKind: String,
    val message: String,
    val idempotentReplay: Boolean,
)

private fun FoodWorkspaceActionRequest.normalizedActions(): List<FoodWorkspaceAction> {
    val list = mutableListOf<FoodWorkspaceAction>()
    action?.let(list::add)
    list.addAll(actions)
    return list.filter { it.type.isNotBlank() }
}

private sealed interface AppFunctionCommandResult {
    data class Applied(val summary: String) : AppFunctionCommandResult
    data class Rejected(val errors: List<String>) : AppFunctionCommandResult
}
