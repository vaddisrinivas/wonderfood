package com.wonderfood.app

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
import com.wonderfood.app.data.FoodDraftCommand
import com.wonderfood.app.data.FoodDraftCommandExecutor
import com.wonderfood.app.data.FoodDraftCommandOrigin
import com.wonderfood.app.data.FoodDraftExecutionResult
import com.wonderfood.app.data.LinkActionDraft
import kotlinx.coroutines.Dispatchers
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

    private val draftExecutor by lazy { FoodDraftCommandExecutor { FoodChatStore(applicationContext) } }
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

        val result = runCatching {
            draftExecutor.execute(
                FoodDraftCommand(
                    draft = draft,
                    sourceMessageId = null,
                    origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
                ),
            )
        }.getOrElse { error ->
            return FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_FAILED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = error.message?.takeIf(String::isNotBlank)
                    ?: "Action '$actionType' failed unexpectedly.",
                idempotentReplay = false,
            )
        }

        val attempt = when (result) {
            is FoodDraftExecutionResult.Applied -> FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_APPLIED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = result.summary,
                idempotentReplay = false,
            )
            is FoodDraftExecutionResult.Rejected -> FoodWorkspaceActionAttempt(
                status = APP_FUNCTION_STATUS_REJECTED,
                actionType = actionType,
                targetKind = requestedTargetKind,
                message = result.errors.joinToString(separator = "; "),
                idempotentReplay = false,
            )
        }

        if (idempotencyKey.isNotBlank()) markHandled(idempotencyKey)
        return attempt
    }

    private fun FoodWorkspaceAction.toDraft(actionType: String, targetKind: String): LinkActionDraft {
        return LinkActionDraft(
            actionType = actionType,
            targetKind = targetKind,
            targetRef = sanitizeText(this.targetRef, 96),
            displayName = sanitizeText(this.displayName, 240),
            fields = normalizedFields(),
        )
    }

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
    val requestId: String = "",
    /**
     * Backward-compatible single-action input; use `actions` for multi-action payloads.
     * Example: `{"type":"inventory.add", ...}`.
     */
    val action: FoodWorkspaceAction? = null,
    /**
     * Ordered list of actions to execute. Maximum of 12 actions in a request.
     * Example: `[ {"type":"inventory.add"}, {"type":"grocery.add"} ]`.
     */
    val actions: List<FoodWorkspaceAction> = emptyList(),
)

@AppFunctionSerializable
data class FoodWorkspaceAction(
    /** Action verb, e.g. `inventory.add`, `meal_log.log`, `grocery.mark_bought`. */
    val type: String,
    /**
     * Target model for the action, e.g. `inventory`, `grocery`, `meal_log`, `meal_plan`, `recipe`.
     */
    val targetKind: String = "",
    /**
     * Existing target object id string for mutate/delete actions; blank for create-like actions.
     */
    val targetRef: String = "",
    /**
     * Human-readable target label for create-like actions and for assistant-facing summaries.
     */
    val displayName: String = "",
    /**
     * Structured payload fields for the requested mutation.
     * Example: `[ {"key":"quantity","value":"2"}, {"key":"zone","value":"fridge"} ]`.
     */
    val fields: List<FoodWorkspaceActionField> = emptyList(),
    /** Optional caller-controlled dedupe key (`idempotencyKey`). */
    val idempotencyKey: String = "",
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
