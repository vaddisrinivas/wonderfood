package com.wonderfood.app.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.wonderfood.app.ai.AiProvider
import com.wonderfood.app.ai.CommandEnvelopeDraftMapper
import com.wonderfood.app.ai.DeterministicReceiptParser
import com.wonderfood.app.ai.FoodInterpreter
import com.wonderfood.app.ai.LiteLlmConfig
import com.wonderfood.app.ai.LiteLlmFoodInterpreter
import com.wonderfood.app.ai.LiteLlmInterpretation
import com.wonderfood.app.ai.LiteLlmSettings
import com.wonderfood.app.WonderFoodVoiceAction
import com.wonderfood.app.WonderFoodVoiceCommand
import com.wonderfood.app.data.AiTurn
import com.wonderfood.app.data.ChatRole
import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodChatStore
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodDraftCommand
import com.wonderfood.app.data.FoodDraftCommandExecutor
import com.wonderfood.app.data.FoodDraftCommandOrigin
import com.wonderfood.app.data.FoodDraftExecutionResult
import com.wonderfood.app.data.FoodDraftNormalizer
import com.wonderfood.app.data.FoodEventConfidence
import com.wonderfood.app.data.FoodEventType
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.FoodMutationCommand
import com.wonderfood.app.data.FoodMutationCommandExecutor
import com.wonderfood.app.data.FoodMutationCommandType
import com.wonderfood.app.data.FoodMutationExecutionResult
import com.wonderfood.app.data.FoodPreferences
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.LinkActionDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntryStatus
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.ReceiptStatus
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import com.wonderfood.app.integration.capture.ProductionReceiptCaptureProvider
import com.wonderfood.app.integration.capture.ReceiptCaptureProvider
import com.wonderfood.app.health.HealthConnectGateway
import com.wonderfood.app.health.HealthDailySummary
import com.wonderfood.app.health.HealthExportResult
import com.wonderfood.app.integration.capture.FoodCaptureGateway
import com.wonderfood.app.integration.capture.FoodCaptureStatus
import com.wonderfood.app.sync.AndroidKeystoreCredentialVault
import com.wonderfood.app.sync.GoogleAccountProfile
import com.wonderfood.app.sync.GoogleDriveAccess
import com.wonderfood.app.sync.GoogleDriveAppDataGateway
import com.wonderfood.app.sync.GoogleDriveBackupDownload
import com.wonderfood.app.sync.GoogleSheetsGateway
import com.wonderfood.app.sync.GoogleSheetsSnapshotSyncCoordinator
import com.wonderfood.app.sync.GoogleSheetsSnapshotSyncResult
import com.wonderfood.app.sync.LegacyFoodMemorySnapshotExporter
import com.wonderfood.app.sync.LegacySnapshotDraftImporter
import com.wonderfood.app.sync.NotionGateway
import com.wonderfood.app.sync.PostgresGateway
import com.wonderfood.app.sync.WonderFoodBackupGateway
import com.wonderfood.app.sync.WonderFoodCsvGateway
import com.wonderfood.app.sync.WonderFoodCsvImport
import com.wonderfood.app.sync.WorkspaceMergeResult
import com.wonderfood.core.data.backend.BackendSecret
import com.wonderfood.core.data.backend.BackendType
import com.wonderfood.core.data.backend.CredentialRef
import com.wonderfood.core.data.backend.GoogleSheetsConfig
import com.wonderfood.core.data.backend.GoogleSheetsUrlParser
import com.wonderfood.core.data.backend.LocalSqliteConfig
import com.wonderfood.core.data.backend.NotionConfig
import com.wonderfood.core.data.backend.NotionUrlParser
import com.wonderfood.core.data.backend.PostgresConfig
import com.wonderfood.core.data.backend.PostgresConnectionMode
import com.wonderfood.core.data.backend.PostgresConnectionParser
import com.wonderfood.core.data.backend.SharedPreferencesBackendConfigurationStore
import com.wonderfood.core.model.WonderFoodSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch

class MainScreenViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private var store = FoodChatStore(appContext)
    private val draftCommandExecutor = FoodDraftCommandExecutor { store }
    private val mutationCommandExecutor = FoodMutationCommandExecutor { store }
    private val interpreter = FoodInterpreter()
    private val liteLlmInterpreter = LiteLlmFoodInterpreter.withBundledSkill(appContext)
    private val liteLlmSettings = LiteLlmSettings(appContext)
    private val health = HealthConnectGateway(appContext)
    private val captureGateway = FoodCaptureGateway(appContext)
    private val receiptCaptureProvider: ReceiptCaptureProvider = ProductionReceiptCaptureProvider(liteLlmInterpreter)
    private val backupGateway = WonderFoodBackupGateway(appContext)
    private val googleDriveGateway = GoogleDriveAppDataGateway()
    private val googleSheetsGateway = GoogleSheetsGateway()
    private val notionGateway = NotionGateway()
    private val postgresGateway = PostgresGateway()
    private val backendConfigurationStore = SharedPreferencesBackendConfigurationStore(appContext)
    private val credentialVault = AndroidKeystoreCredentialVault(appContext)
    private val shellPrefs = appContext.getSharedPreferences(SHELL_PREFS_NAME, Context.MODE_PRIVATE)
    private val directActionPrefs = appContext.getSharedPreferences(DIRECT_ACTION_PREFS_NAME, Context.MODE_PRIVATE)
    private val googleSyncPrefs = appContext.getSharedPreferences(GOOGLE_SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    private var pendingUndo: PendingUndo? = null
    private var pendingDraftOrigin: FoodDraftCommandOrigin = FoodDraftCommandOrigin.AI_REVIEW
    private var preferenceAutoSaveJob: Job? = null
    private var backendSnapshotSyncJob: Job? = null
    private var pendingGoogleRestoreDownload: GoogleDriveBackupDownload? = null
    private var pendingSheetsImportSnapshot: WonderFoodSnapshot? = null
    private var pendingSheetsImportLabel: String = "Sheet data"

    private val _uiState = MutableStateFlow(
        WonderFoodUiState(
            section = readSelectedSection(),
            workspaceConflictInbox = readWorkspaceConflictInbox(),
        ),
    )
    val uiState: StateFlow<WonderFoodUiState> = _uiState.asStateFlow()

    val healthPermissionContract = health.permissionContract()
    val healthPermissions: Set<String> = health.healthPermissions
    val healthWritePermissions: Set<String> = health.nutritionWritePermissions

    init {
        viewModelScope.launch(Dispatchers.IO) {
            store.seedIfEmpty()
            refreshFromDisk()
            refreshAiStatus()
            refreshHealthStatus()
            refreshSyncStatus()
            refreshBackendHome()
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun selectSection(section: FoodSection) {
        setSection(section)
    }

    fun openDetail(target: FoodDetailTarget) {
        _uiState.update { it.copy(detailTarget = target) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(detailTarget = null) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = "", undoMessage = "") }
        pendingUndo = null
    }

    fun undoLastAction() {
        val action = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch(Dispatchers.IO) {
            val fallback = "Restored ${action.label}."
            val result = executeMutationCommand(
                type = FoodMutationCommandType.UNDO_ACTION,
                label = "Undo ${action.kind.name.lowercase()}",
                payload = mapOf(
                    "kind" to action.kind.name,
                    "id" to action.id.toString(),
                    "ids" to action.ids.joinToString(","),
                    "label" to action.label,
                ),
            ) {
                when (action.kind) {
                    UndoKind.EVENT -> store.deleteFoodEvent(action.id)
                    UndoKind.INVENTORY -> store.restoreInventory(action.id)
                    UndoKind.GROCERY -> store.restoreGrocery(action.id)
                    UndoKind.GROCERY_BOUGHT -> store.undoGroceryBought(action.id)
                    UndoKind.RECIPE -> store.restoreRecipe(action.id)
                    UndoKind.MEAL -> store.restoreMealLog(action.id)
                    UndoKind.PLAN_ENTRY -> store.restoreMealPlanEntry(action.id)
                    UndoKind.PLAN_ENTRIES -> store.restoreMealPlanEntries(action.ids)
                }
                fallback
            }
            refreshFromDisk()
            showFeedback(result.summaryOrFallback(fallback))
        }
    }

    fun onPreferencesChange(preferences: FoodPreferences) {
        _uiState.update { it.copy(preferencesForm = preferences, settingsSaveStatus = "Saving…") }
        schedulePreferenceAutoSave(preferences)
    }

    fun onAiConfigChange(config: LiteLlmConfig) {
        _uiState.update { it.copy(aiConfigForm = config) }
    }

    fun onAiFallbackConfigChange(config: LiteLlmConfig) {
        _uiState.update { it.copy(aiFallbackConfigForm = config) }
    }

    fun savePreferences() {
        preferenceAutoSaveJob?.cancel()
        val preferences = _uiState.value.preferencesForm
        savePreferencesNow(preferences, announce = true)
    }

    private fun schedulePreferenceAutoSave(preferences: FoodPreferences) {
        preferenceAutoSaveJob?.cancel()
        preferenceAutoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(PREFERENCE_AUTO_SAVE_DELAY_MILLIS)
            savePreferencesNow(preferences, announce = false)
        }
    }

    private fun savePreferencesNow(preferences: FoodPreferences, announce: Boolean) {
        if (announce) {
            _uiState.update { it.copy(isWorking = true) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            store.savePreferences(preferences)
            if (announce) {
                store.insertMessage(ChatRole.ASSISTANT, "Saved your food preferences and AI instructions.")
            }
            val memory = store.readMemory()
            _uiState.update {
                it.copy(
                    memory = memory,
                    preferencesForm = preferences,
                    isWorking = false,
                    settingsSaveStatus = "Saved",
                )
            }
            if (announce) showFeedback("Settings saved.")
        }
    }

    fun saveAiConfig() {
        val primary = _uiState.value.aiConfigForm
        val fallback = _uiState.value.aiFallbackConfigForm
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            liteLlmSettings.saveAll(listOf(primary, fallback))
            refreshAiStatus()
            store.insertMessage(ChatRole.ASSISTANT, "Saved primary and fallback AI provider settings.")
            refreshFromDisk(isWorking = false)
            showFeedback("Primary and fallback AI providers saved.")
        }
    }

    fun handleVoiceCommand(command: WonderFoodVoiceCommand) {
        if (hasHandledDirectAction(command)) {
            _uiState.update {
                it.copy(
                    isWorking = false,
                    voiceStatus = "Already handled this assistant action.",
                )
            }
            return
        }
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            if (command.action == WonderFoodVoiceAction.AI_REVIEW) {
                submitToAi(command.text.ifBlank { "Food note from assistant" }, "voice")
                markDirectActionHandled(command)
                return@launch
            }
            if (command.action == WonderFoodVoiceAction.LINK_ACTION) {
                val draft = command.toLinkActionDraft()
                if (draft == null) {
                    markDirectActionHandled(command)
                    refreshFromDisk(isWorking = false)
                    _uiState.update { it.copy(voiceStatus = "Linked action was empty or unsupported.") }
                    return@launch
                }
                markDirectActionHandled(command)
                stageVoiceDraftForReview(
                    command = command,
                    draft = draft,
                    section = command.linkActionSection(),
                    status = "Linked action proposal ready for review: ${draft.summary}.",
                    origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
                    sourceLabel = "Deep link",
                )
                return@launch
            }
            val message = when (command.action) {
                WonderFoodVoiceAction.OPEN_SECTION -> {
                    val section = command.section.toFoodSection()
                    setSection(section)
                    "Opened ${section.label}."
                }
                WonderFoodVoiceAction.SHOW_NUMBERS -> {
                    setSection(FoodSection.TODAY)
                    "Opened today's numbers."
                }
                WonderFoodVoiceAction.LOG_WATER -> {
                    val amount = command.amount?.toInt() ?: 250
                    var eventId = -1L
                    val status = executeMutationCommand(
                        type = FoodMutationCommandType.LOG_EVENT,
                        label = "Google Assistant water log",
                        origin = FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
                        payload = mapOf("amount_ml" to amount.toString(), "source" to "google_assistant"),
                    ) {
                        val (createdEventId, createdStatus) = store.logWaterEvent(amount, source = "google_assistant")
                        eventId = createdEventId
                        createdStatus
                    }.summaryOrFallback("Logged $amount ml water.")
                    if (eventId != -1L) registerUndo(UndoKind.EVENT, eventId, "water log", status)
                    status
                }
                WonderFoodVoiceAction.START_SHOPPING -> {
                    var eventId = -1L
                    setSection(FoodSection.SHOP)
                    val status = executeMutationCommand(
                        type = FoodMutationCommandType.LOG_EVENT,
                        label = "Google Assistant start shopping",
                        origin = FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
                        payload = mapOf("event_type" to FoodEventType.SHOP.name, "note" to "Started shopping"),
                    ) {
                        eventId = store.logFoodEvent(FoodEventType.SHOP, source = "google_assistant", confidence = FoodEventConfidence.EXACT, note = "Started shopping")
                        "Started shopping."
                    }.summaryOrFallback("Started shopping.")
                    if (eventId != -1L) registerUndo(UndoKind.EVENT, eventId, "shopping start", status)
                    status
                }
                WonderFoodVoiceAction.DONE_SHOPPING -> {
                    var eventId = -1L
                    setSection(FoodSection.SHOP)
                    val status = executeMutationCommand(
                        type = FoodMutationCommandType.LOG_EVENT,
                        label = "Google Assistant finish shopping",
                        origin = FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
                        payload = mapOf("event_type" to FoodEventType.SHOP.name, "note" to "Finished shopping"),
                    ) {
                        eventId = store.logFoodEvent(FoodEventType.SHOP, source = "google_assistant", confidence = FoodEventConfidence.EXACT, note = "Finished shopping")
                        "Finished shopping."
                    }.summaryOrFallback("Finished shopping.")
                    if (eventId != -1L) registerUndo(UndoKind.EVENT, eventId, "shopping finish", status)
                    status
                }
                WonderFoodVoiceAction.START_COOKING -> {
                    val recipe = store.readMemory().recipes.findVoiceRecipe(command.recipeName)
                    val recipeLabel = recipe?.title ?: command.recipeName.ifBlank { "recipe" }
                    var eventId = -1L
                    _uiState.update {
                        it.copy(
                            section = FoodSection.RECIPES,
                            detailTarget = recipe?.let { found -> FoodDetailTarget(FoodDetailKind.RECIPE, id = found.id) },
                        )
                    }
                    saveSelectedSection(FoodSection.RECIPES)
                    val status = executeMutationCommand(
                        type = FoodMutationCommandType.LOG_EVENT,
                        label = "Google Assistant start cooking",
                        origin = FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
                        payload = mapOf(
                            "event_type" to FoodEventType.COOK.name,
                            "recipe_id" to recipe?.id?.toString(),
                            "note" to "Started cooking $recipeLabel",
                        ),
                    ) {
                        eventId = store.logFoodEvent(
                            type = FoodEventType.COOK,
                            source = "google_assistant",
                            confidence = FoodEventConfidence.EXACT,
                            relatedRecipeId = recipe?.id,
                            note = "Started cooking $recipeLabel",
                        )
                        "Started cooking $recipeLabel."
                    }.summaryOrFallback("Started cooking $recipeLabel.")
                    if (eventId != -1L) registerUndo(UndoKind.EVENT, eventId, "cooking start", status)
                    status
                }
                WonderFoodVoiceAction.DONE_COOKING -> {
                    val recipe = store.readMemory().recipes.findVoiceRecipe(command.recipeName)
                    if (recipe == null) {
                        var eventId = -1L
                        setSection(FoodSection.RECIPES)
                        val status = executeMutationCommand(
                            type = FoodMutationCommandType.LOG_EVENT,
                            label = "Google Assistant finish cooking",
                            origin = FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
                            payload = mapOf(
                                "event_type" to FoodEventType.COOK.name,
                                "note" to "Finished cooking ${command.recipeName}",
                            ),
                        ) {
                            eventId = store.logFoodEvent(FoodEventType.COOK, source = "google_assistant", confidence = FoodEventConfidence.ESTIMATED, note = "Finished cooking ${command.recipeName}")
                            "Finished cooking."
                        }.summaryOrFallback("Finished cooking.")
                        if (eventId != -1L) registerUndo(UndoKind.EVENT, eventId, "cooking finish", status)
                        status
                    } else {
                        executeMutationCommand(
                            type = FoodMutationCommandType.LOG_EVENT,
                            label = "Google Assistant cook recipe",
                            origin = FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
                            payload = mapOf("recipe_id" to recipe.id.toString(), "recipe_title" to recipe.title),
                        ) {
                            store.cookRecipe(recipe.id)
                        }.summaryOrFallback("Logged ${recipe.title}.")
                    }
                }
                WonderFoodVoiceAction.LOG_MEAL -> {
                    if (command.text.isBlank()) {
                        requestGuidedVoiceCapture(
                            prompt = "What did you eat?",
                            section = FoodSection.TODAY,
                            status = "Tell WonderFood what you ate.",
                            instruction = "The user is logging a meal. Estimate nutrition from the spoken food and portion. Return a meal_log draft if enough detail is present; otherwise ask one short follow-up.",
                        )
                        return@launch
                    }
                    val title = command.text
                    val draft = MealLogDraft(
                        titleText = title,
                        calories = command.amount?.toInt(),
                        proteinGrams = null,
                        carbsGrams = null,
                        fatGrams = null,
                        mealSlot = MealSlot.FLEX,
                        source = if (command.amount != null) "google_assistant_explicit" else "google_assistant_unverified",
                    )
                    markDirectActionHandled(command)
                    stageVoiceDraftForReview(
                        command = command,
                        draft = draft,
                        section = FoodSection.TODAY,
                        status = "Meal log ready for review: $title.",
                    )
                    return@launch
                }
                WonderFoodVoiceAction.ADD_GROCERY -> {
                    command.localGroceryDraft()?.let { draft ->
                        markDirectActionHandled(command)
                        stageVoiceDraftForReview(
                            command = command,
                            draft = draft,
                            section = FoodSection.SHOP,
                            status = "Grocery proposal ready for review: ${draft.items.size} item${draft.items.size.pluralWord}.",
                        )
                        return@launch
                    }
                    if (command.itemName.isBlank()) {
                        requestGuidedVoiceCapture(
                            prompt = "What groceries should I add?",
                            section = FoodSection.SHOP,
                            status = "Tell WonderFood the groceries to add.",
                            instruction = "The user is adding groceries or a shopping list. Return a grocery draft with individual items, quantities, category, emoji/image metadata, and nutrition estimates where practical.",
                        )
                        return@launch
                    }
                    val item = command.itemName
                    val draft = GroceryDraft(
                        items = listOf(
                            FoodCandidate(
                                name = item,
                                quantity = command.quantity,
                                zone = command.zone.toStorageZone(item),
                                category = command.category.ifBlank { categorizeFood(item) },
                            ),
                        ),
                    )
                    markDirectActionHandled(command)
                    stageVoiceDraftForReview(
                        command = command,
                        draft = draft,
                        section = FoodSection.SHOP,
                        status = "Grocery proposal ready for review: $item.",
                    )
                    return@launch
                }
                WonderFoodVoiceAction.ADD_INVENTORY -> {
                    command.localInventoryDraft()?.let { draft ->
                        markDirectActionHandled(command)
                        stageVoiceDraftForReview(
                            command = command,
                            draft = draft,
                            section = FoodSection.KITCHEN,
                            status = "Kitchen proposal ready for review: ${draft.items.size} item${draft.items.size.pluralWord}.",
                        )
                        return@launch
                    }
                    if (command.itemName.isBlank()) {
                        requestGuidedVoiceCapture(
                            prompt = "What should I add to the kitchen?",
                            section = FoodSection.KITCHEN,
                            status = "Tell WonderFood what belongs in the fridge, freezer, or pantry.",
                            instruction = "The user is adding fridge, freezer, or pantry inventory. Return an inventory draft with item names, quantities, storage zone, category, emoji/image metadata, and nutrition estimates where practical.",
                        )
                        return@launch
                    }
                    val item = command.itemName
                    val zone = command.zone.toStorageZone(item)
                    val draft = InventoryDraft(
                        items = listOf(
                            FoodCandidate(
                                name = item,
                                quantity = command.quantity,
                                zone = zone,
                                category = command.category.ifBlank { categorizeFood(item) },
                            ),
                        ),
                    )
                    markDirectActionHandled(command)
                    stageVoiceDraftForReview(
                        command = command,
                        draft = draft,
                        section = FoodSection.KITCHEN,
                        status = "Kitchen proposal ready for review: $item to ${zone.label}.",
                    )
                    return@launch
                }
                WonderFoodVoiceAction.PLAN_MEALS -> {
                    requestGuidedVoiceCapture(
                        prompt = "What should I plan?",
                        section = FoodSection.PLAN,
                        status = "Tell WonderFood the meals, dates, constraints, or goals.",
                        instruction = "The user is planning meals. Use pantry/history/preferences if available and return a meal_plan draft with dates or slots when implied. If accepted, it should become the plan.",
                    )
                    return@launch
                }
                WonderFoodVoiceAction.AI_REVIEW -> error("AI review commands return before direct-action handling.")
                WonderFoodVoiceAction.LINK_ACTION -> error("Link action commands return before direct-action handling.")
            }
            markDirectActionHandled(command)
            refreshFromDisk(isWorking = false)
            _uiState.update { it.copy(voiceStatus = message) }
        }
    }

    private fun requestGuidedVoiceCapture(prompt: String, section: FoodSection, status: String, instruction: String) {
        _uiState.update {
            it.copy(
                section = section,
                isWorking = false,
                voiceStatus = status,
                guidedVoicePrompt = prompt,
                guidedVoiceNonce = System.nanoTime(),
                guidedVoiceAutoAccept = true,
                guidedVoiceInstruction = instruction,
            )
        }
        saveSelectedSection(section)
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return
        val target = _uiState.value.detailTarget
        val memory = _uiState.value.memory
        val section = _uiState.value.section
        _uiState.update { it.copy(input = "", isWorking = true, voiceStatus = "Text sent to AI.") }

        viewModelScope.launch(Dispatchers.IO) {
            val pageEdit = applyContextualPageEdit(text, target, memory)
            if (pageEdit != null) {
                store.insertMessage(ChatRole.USER, text)
                store.insertMessage(ChatRole.ASSISTANT, pageEdit)
                refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
                _uiState.update { it.copy(voiceStatus = pageEdit) }
            } else {
                submitToAi(text, "text", target.toAiPromptContext(memory, section))
            }
        }
    }

    fun sendVoiceNote(text: String, autoAccept: Boolean = false) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val target = _uiState.value.detailTarget
        val memory = _uiState.value.memory
        val section = _uiState.value.section
        val guidedInstruction = _uiState.value.guidedVoiceInstruction.takeIf { autoAccept && it.isNotBlank() }
        val aiText = guidedInstruction?.let { "$it\n\nSpoken answer: $clean" } ?: clean
        _uiState.update { it.copy(input = "", isWorking = true, voiceStatus = "Voice note sent to AI.") }
        viewModelScope.launch(Dispatchers.IO) {
            val pageEdit = applyContextualPageEdit(clean, target, memory)
            if (pageEdit != null) {
                store.insertMessage(ChatRole.USER, "Voice note: $clean")
                store.insertMessage(ChatRole.ASSISTANT, pageEdit)
                refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
                _uiState.update { it.copy(voiceStatus = pageEdit) }
            } else {
                submitToAi(aiText, "voice", target.toAiPromptContext(memory, section), autoAccept = autoAccept)
            }
        }
    }

    fun attachReceiptPhoto(uri: Uri?, note: String = "") {
        if (uri == null) return
        val cleanNote = note.trim().take(2_000)
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val capture = captureGateway.stageReceiptPhoto(uri)
            val privateUri = capture.privateUri ?: uri
            val receiptEvidence = listOfNotNull(
                capture.evidenceText,
                cleanNote.takeIf { it.isNotBlank() }?.let { "User note:\n$it" },
            ).joinToString("\n\n")
            val receiptId = store.insertReceiptCapture(
                imageUri = privateUri.toString(),
                rawText = receiptEvidence,
                status = if (capture.status == FoodCaptureStatus.STAGED) ReceiptStatus.SAVED else ReceiptStatus.NEEDS_TEXT,
            )
            val sourceMessageId = store.insertMessage(
                ChatRole.USER,
                buildString {
                    append("Attached receipt photo.")
                    if (cleanNote.isNotBlank()) append("\nReceipt note: $cleanNote")
                },
            )
            val memory = store.readMemory()
            val configs = liteLlmSettings.readAll()
            val turn = if (capture.status == FoodCaptureStatus.STAGED) {
                interpretReceiptPhotoWithVisibleRetries(privateUri, memory, configs, cleanNote)
            } else {
                null
            }
            if (turn == null) {
                executeMutationCommand(
                    type = FoodMutationCommandType.UPDATE_RECEIPT,
                    label = "Mark receipt needs text",
                    origin = FoodDraftCommandOrigin.RECEIPT,
                    sourceMessageId = sourceMessageId,
                    payload = mapOf("id" to receiptId.toString(), "status" to ReceiptStatus.NEEDS_TEXT.name),
                ) {
                    store.updateReceiptStatus(receiptId, rawText = receiptEvidence, status = ReceiptStatus.NEEDS_TEXT)
                    "Receipt needs text."
                }
                store.insertMessage(
                    ChatRole.ASSISTANT,
                    "I saved a private copy of the receipt. OCR or AI did not finish, so paste the visible lines or retry the capture when the provider is available.",
                )
                refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
            } else {
                val linkedDraft = turn.draft?.linkReceipt(receiptId)
                val receiptStatus = if (linkedDraft == null) ReceiptStatus.NEEDS_TEXT else ReceiptStatus.EXTRACTED
                executeMutationCommand(
                    type = FoodMutationCommandType.UPDATE_RECEIPT,
                    label = "Update receipt extraction",
                    origin = FoodDraftCommandOrigin.RECEIPT,
                    sourceMessageId = sourceMessageId,
                    payload = mapOf("id" to receiptId.toString(), "status" to receiptStatus.name),
                ) {
                    store.updateReceiptStatus(
                        receiptId,
                        rawText = listOfNotNull(
                            cleanNote.takeIf { it.isNotBlank() }?.let { "User note:\n$it" },
                            "Extraction:\n${turn.reply}",
                            linkedDraft?.receiptAuditText(),
                        ).joinToString("\n\n"),
                        status = receiptStatus,
                    )
                    "Receipt page updated."
                }
                store.insertMessage(ChatRole.ASSISTANT, turn.reply)
                pendingDraftOrigin = if (linkedDraft == null) FoodDraftCommandOrigin.AI_REVIEW else FoodDraftCommandOrigin.RECEIPT
                refreshFromDisk(
                    pendingDraft = linkedDraft,
                    pendingSourceMessageId = sourceMessageId,
                    isWorking = false,
                )
            }
        }
    }

    fun acceptDraft() {
        val state = _uiState.value
        val draft = state.pendingDraft?.withPreferenceRiskWarnings(state.memory.preferences) ?: return
        val sourceMessageId = _uiState.value.pendingSourceMessageId
        _uiState.update { it.copy(isWorking = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val result = executeDraftCommand(draft, sourceMessageId, pendingDraftOrigin)
            when (result) {
                is FoodDraftExecutionResult.Applied -> {
                    store.insertMessage(ChatRole.ASSISTANT, result.summary)
                    pendingDraftOrigin = FoodDraftCommandOrigin.AI_REVIEW
                    refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
                    _uiState.update { it.copy(aiAttemptStatus = "", voiceStatus = "") }
                }
                is FoodDraftExecutionResult.Rejected -> {
                    val summary = "Could not save this proposal: ${result.errors.joinToString("; ")}"
                    store.insertMessage(ChatRole.ASSISTANT, summary)
                    refreshFromDisk(isWorking = false)
                    _uiState.update { it.copy(aiAttemptStatus = "", voiceStatus = summary) }
                }
            }
        }
    }

    fun updatePendingDraft(draft: FoodDraft) {
        _uiState.update { state ->
            if (state.pendingDraft == null) state else state.copy(
                pendingDraft = draft.withPreferenceRiskWarnings(state.memory.preferences),
            )
        }
    }

    fun rejectDraft() {
        val draft = _uiState.value.pendingDraft
        val sourceMessageId = _uiState.value.pendingSourceMessageId
        viewModelScope.launch(Dispatchers.IO) {
            if (draft != null) {
                draftCommandExecutor.reject(FoodDraftCommand(draft, sourceMessageId, pendingDraftOrigin))
                pendingDraftOrigin = FoodDraftCommandOrigin.AI_REVIEW
            }
            store.insertMessage(ChatRole.ASSISTANT, "Draft discarded. Keep chatting and I will revise.")
            refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null)
            _uiState.update { it.copy(aiAttemptStatus = "", voiceStatus = "") }
        }
    }

    fun startNewChat() {
        _uiState.update { it.copy(input = "", isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            store.startNewChat()
            refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
            _uiState.update { it.copy(voiceStatus = "New chat started.") }
        }
    }

    fun clearChatHistory() {
        _uiState.update { it.copy(input = "", isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            store.clearChatHistory()
            refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
            _uiState.update { it.copy(voiceStatus = "Chat memory reset.") }
        }
    }

    fun updateChatMessage(id: Long, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (store.updateMessage(id, body)) {
                refreshFromDisk(isWorking = false)
                showFeedback("Chat message updated.")
            }
        }
    }

    fun deleteInventory(id: Long) {
        val label = _uiState.value.memory.inventory.firstOrNull { it.id == id }?.name ?: "kitchen item"
        viewModelScope.launch(Dispatchers.IO) {
            executeMutationCommand(
                type = FoodMutationCommandType.DELETE_INVENTORY,
                label = "Archive pantry item",
                payload = mapOf("id" to id.toString(), "name" to label),
            ) {
                store.deleteInventory(id)
                "Archived $label."
            }
            refreshFromDisk(isWorking = false)
            registerUndo(UndoKind.INVENTORY, id, label, "Archived $label.")
        }
    }

    fun deleteGrocery(id: Long) {
        val label = _uiState.value.memory.groceries.firstOrNull { it.id == id }?.name ?: "grocery item"
        viewModelScope.launch(Dispatchers.IO) {
            executeMutationCommand(
                type = FoodMutationCommandType.DELETE_GROCERY,
                label = "Archive grocery item",
                payload = mapOf("id" to id.toString(), "name" to label),
            ) {
                store.deleteGrocery(id)
                "Archived $label."
            }
            refreshFromDisk()
            registerUndo(UndoKind.GROCERY, id, label, "Archived $label.")
        }
    }

    fun markGroceryBought(id: Long) {
        val label = _uiState.value.memory.groceries.firstOrNull { it.id == id }?.name ?: "grocery item"
        viewModelScope.launch(Dispatchers.IO) {
            val result = executeMutationCommand(
                type = FoodMutationCommandType.MARK_GROCERY_BOUGHT,
                label = "Move grocery to inventory",
                payload = mapOf("id" to id.toString(), "name" to label),
            ) {
                store.markGroceryBought(id)
            }
            val summary = result.summaryOrFallback("Moved $label into inventory and recorded shopping.")
            store.insertMessage(ChatRole.ASSISTANT, summary)
            refreshFromDisk()
            if (summary.startsWith("Moved ")) {
                registerUndo(UndoKind.GROCERY_BOUGHT, id, label, summary)
            } else {
                showFeedback(summary)
            }
        }
    }

    fun updateInventory(
        id: Long,
        name: String,
        quantity: String,
        zone: StorageZone,
        category: String,
        servingText: String,
        calories: Int?,
        proteinGrams: Double?,
        carbsGrams: Double?,
        fatGrams: Double?,
        nutritionSource: String,
        notes: String,
        imageUri: String?,
        imageUrl: String,
        expiresAtMillis: Long?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeName = name.ifBlank { "Untitled food" }
            val safeCategory = category.ifBlank { categorizeFood(safeName) }
            executeMutationCommand(
                type = FoodMutationCommandType.UPDATE_INVENTORY,
                label = "Update pantry item",
                payload = mapOf(
                    "id" to id.toString(),
                    "name" to safeName,
                    "quantity" to quantity,
                    "zone" to zone.name,
                    "category" to safeCategory,
                    "nutrition_source" to nutritionSource.ifBlank { "manual_edit" },
                ),
            ) {
                store.updateInventory(
                    id = id,
                    name = safeName,
                    quantity = quantity,
                    zone = zone,
                    category = safeCategory,
                    servingText = servingText,
                    calories = calories,
                    proteinGrams = proteinGrams,
                    carbsGrams = carbsGrams,
                    fatGrams = fatGrams,
                    nutritionSource = nutritionSource.ifBlank { "manual_edit" },
                    notes = notes,
                    imageUri = imageUri,
                    imageUrl = imageUrl,
                    expiresAtMillis = expiresAtMillis,
                )
                "Kitchen page updated."
            }
            refreshFromDisk()
            showFeedback("Kitchen page updated.")
        }
    }

    fun updateGrocery(
        id: Long,
        name: String,
        quantity: String,
        status: GroceryStatus,
        category: String,
        servingText: String,
        calories: Int?,
        proteinGrams: Double?,
        carbsGrams: Double?,
        fatGrams: Double?,
        nutritionSource: String,
        source: String,
        imageUri: String?,
        imageUrl: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeName = name.ifBlank { "Untitled grocery" }
            val safeCategory = category.ifBlank { categorizeFood(safeName) }
            executeMutationCommand(
                type = FoodMutationCommandType.UPDATE_GROCERY,
                label = "Update grocery item",
                payload = mapOf(
                    "id" to id.toString(),
                    "name" to safeName,
                    "quantity" to quantity,
                    "status" to status.name,
                    "category" to safeCategory,
                    "source" to source.ifBlank { "manual_edit" },
                ),
            ) {
                store.updateGrocery(
                    id = id,
                    name = safeName,
                    quantity = quantity,
                    status = status,
                    category = safeCategory,
                    servingText = servingText,
                    calories = calories,
                    proteinGrams = proteinGrams,
                    carbsGrams = carbsGrams,
                    fatGrams = fatGrams,
                    nutritionSource = nutritionSource.ifBlank { "manual_edit" },
                    source = source.ifBlank { "manual_edit" },
                    imageUri = imageUri,
                    imageUrl = imageUrl,
                )
                "Grocery page updated."
            }
            refreshFromDisk()
            showFeedback("Grocery page updated.")
        }
    }

    fun cookRecipe(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = executeMutationCommand(
                type = FoodMutationCommandType.LOG_EVENT,
                label = "Cook recipe",
                payload = mapOf("recipe_id" to id.toString()),
            ) {
                store.cookRecipe(id)
            }.summaryOrFallback("Logged recipe.")
            store.insertMessage(ChatRole.ASSISTANT, summary)
            refreshFromDisk()
            showFeedback(summary)
        }
    }

    fun addMissingRecipeGroceries(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = executeMutationCommand(
                type = FoodMutationCommandType.ADD_MISSING_RECIPE_GROCERIES,
                label = "Add missing recipe groceries",
                payload = mapOf("recipe_id" to id.toString()),
            ) {
                store.addMissingRecipeGroceries(id)
            }.summaryOrFallback("Added missing groceries.")
            store.insertMessage(ChatRole.ASSISTANT, summary)
            refreshFromDisk()
            showFeedback(summary)
        }
    }

    fun deleteRecipe(id: Long) {
        val label = _uiState.value.memory.recipes.firstOrNull { it.id == id }?.title ?: "recipe"
        viewModelScope.launch(Dispatchers.IO) {
            executeMutationCommand(
                type = FoodMutationCommandType.DELETE_RECIPE,
                label = "Archive recipe",
                payload = mapOf("id" to id.toString(), "title" to label),
            ) {
                store.deleteRecipe(id)
                "Archived $label."
            }
            refreshFromDisk()
            registerUndo(UndoKind.RECIPE, id, label, "Archived $label.")
        }
    }

    fun updateRecipe(
        id: Long,
        title: String,
        ingredients: String,
        steps: String,
        servings: Int?,
        prepMinutes: Int?,
        tags: String,
        imageUri: String?,
        imageUrl: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeTitle = title.ifBlank { "Untitled recipe" }
            executeMutationCommand(
                type = FoodMutationCommandType.UPDATE_RECIPE,
                label = "Update recipe",
                payload = mapOf(
                    "id" to id.toString(),
                    "title" to safeTitle,
                    "servings" to servings?.toString(),
                    "prep_minutes" to prepMinutes?.toString(),
                    "tags" to tags,
                ),
            ) {
                store.updateRecipe(id, safeTitle, ingredients, steps, servings, prepMinutes, tags, imageUri, imageUrl)
                "Recipe updated."
            }
            refreshFromDisk()
            showFeedback("Recipe updated.")
        }
    }

    fun updateRecipeImage(id: Long, imageUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            executeMutationCommand(
                type = FoodMutationCommandType.UPDATE_RECIPE_IMAGE,
                label = "Update recipe image",
                payload = mapOf("id" to id.toString(), "image_uri" to imageUri),
            ) {
                store.updateRecipeImage(id, imageUri)
                "Recipe image updated."
            }
            refreshFromDisk()
            showFeedback("Recipe image updated.")
        }
    }

    fun updateMealLog(
        id: Long,
        title: String,
        calories: Int?,
        proteinGrams: Double?,
        carbsGrams: Double?,
        fatGrams: Double?,
        mealSlot: MealSlot,
        usedItemsText: String,
        loggedDateEpochDay: Long,
        source: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeTitle = title.ifBlank { "Untitled meal" }
            executeMutationCommand(
                type = FoodMutationCommandType.UPDATE_MEAL_LOG,
                label = "Update meal log",
                payload = mapOf(
                    "id" to id.toString(),
                    "title" to safeTitle,
                    "meal_slot" to mealSlot.name,
                    "logged_date_epoch_day" to loggedDateEpochDay.toString(),
                    "calories" to calories?.toString(),
                    "source" to source.ifBlank { "manual_edit" },
                ),
            ) {
                store.updateMealLog(
                    id = id,
                    title = safeTitle,
                    calories = calories,
                    proteinGrams = proteinGrams,
                    carbsGrams = carbsGrams,
                    fatGrams = fatGrams,
                    mealSlot = mealSlot,
                    usedItemsText = usedItemsText,
                    loggedDateEpochDay = loggedDateEpochDay,
                    source = source.ifBlank { "manual_edit" },
                )
                "Meal page updated."
            }
            refreshFromDisk()
            showFeedback("Meal page updated.")
        }
    }

    fun updateMealPlan(
        id: Long,
        title: String,
        daysText: String,
        groceryHint: String,
        startDateEpochDay: Long?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeTitle = title.ifBlank { "Meal plan" }
            executeMutationCommand(
                type = FoodMutationCommandType.UPDATE_MEAL_PLAN,
                label = "Update meal plan",
                payload = mapOf(
                    "id" to id.toString(),
                    "title" to safeTitle,
                    "start_date_epoch_day" to startDateEpochDay?.toString(),
                ),
            ) {
                store.updateMealPlan(
                    id = id,
                    title = safeTitle,
                    daysText = daysText,
                    groceryHint = groceryHint,
                    startDateEpochDay = startDateEpochDay,
                )
                "Meal plan updated."
            }
            refreshFromDisk()
            showFeedback("Meal plan updated.")
        }
    }

    fun addMealPlanEntry(
        dateEpochDay: Long,
        slot: MealSlot,
        title: String,
        calorieTarget: Int?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeTitle = title.ifBlank { "Planned meal" }
            executeMutationCommand(
                type = FoodMutationCommandType.ADD_MEAL_PLAN_ENTRY,
                label = "Add planned meal",
                payload = mapOf(
                    "date_epoch_day" to dateEpochDay.toString(),
                    "slot" to slot.name,
                    "title" to safeTitle,
                    "calorie_target" to calorieTarget?.toString(),
                ),
            ) {
                store.addMealPlanEntry(
                    dateEpochDay = dateEpochDay,
                    slot = slot,
                    title = safeTitle,
                    calorieTarget = calorieTarget,
                )
                "Planned meal added."
            }
            refreshFromDisk()
            showFeedback("Planned meal added.")
        }
    }

    fun updateMealPlanEntry(
        id: Long,
        dateEpochDay: Long,
        slot: MealSlot,
        title: String,
        calorieTarget: Int?,
        status: MealPlanEntryStatus,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val safeTitle = title.ifBlank { "Planned meal" }
            executeMutationCommand(
                type = FoodMutationCommandType.UPDATE_MEAL_PLAN_ENTRY,
                label = "Update planned meal",
                payload = mapOf(
                    "id" to id.toString(),
                    "date_epoch_day" to dateEpochDay.toString(),
                    "slot" to slot.name,
                    "title" to safeTitle,
                    "calorie_target" to calorieTarget?.toString(),
                    "status" to status.name,
                ),
            ) {
                store.updateMealPlanEntry(
                    id = id,
                    dateEpochDay = dateEpochDay,
                    slot = slot,
                    title = safeTitle,
                    calorieTarget = calorieTarget,
                    status = status,
                )
                if (status == MealPlanEntryStatus.EATEN) "Planned meal marked eaten and added to meal log." else "Planned meal updated."
            }
            refreshFromDisk()
            showFeedback(if (status == MealPlanEntryStatus.EATEN) "Planned meal marked eaten and added to meal log." else "Planned meal updated.")
        }
    }

    fun deleteMealPlanEntry(id: Long) {
        val label = _uiState.value.memory.mealPlanEntries.firstOrNull { it.id == id }?.title ?: "planned meal"
        viewModelScope.launch(Dispatchers.IO) {
            executeMutationCommand(
                type = FoodMutationCommandType.DELETE_MEAL_PLAN_ENTRY,
                label = "Archive planned meal",
                payload = mapOf("id" to id.toString(), "title" to label),
            ) {
                store.deleteMealPlanEntry(id)
                "Archived planned meal: $label."
            }
            refreshFromDisk()
            registerUndo(UndoKind.PLAN_ENTRY, id, label, "Archived planned meal: $label.")
        }
    }

    fun deleteMealPlanEntries(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val selected = _uiState.value.memory.mealPlanEntries.filter { it.id in ids }
        if (selected.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            executeMutationCommand(
                type = FoodMutationCommandType.DELETE_MEAL_PLAN_ENTRIES,
                label = "Archive planned meals",
                payload = mapOf(
                    "ids" to ids.joinToString(","),
                    "count" to selected.size.toString(),
                ),
            ) {
                store.deleteMealPlanEntries(ids)
                "Archived ${selected.size} planned meal${selected.size.pluralWord}."
            }
            refreshFromDisk()
            registerUndo(
                kind = UndoKind.PLAN_ENTRIES,
                id = selected.first().id,
                label = "${selected.size} planned meal${selected.size.pluralWord}",
                message = "Archived ${selected.size} planned meal${selected.size.pluralWord}.",
                ids = selected.map { it.id }.toSet(),
            )
        }
    }

    fun deleteAllMealPlans() {
        viewModelScope.launch(Dispatchers.IO) {
            var planCount = 0
            var entryCount = 0
            val result = executeMutationCommand(
                type = FoodMutationCommandType.DELETE_ALL_MEAL_PLANS,
                label = "Delete all meal plans",
            ) {
                val deleted = store.deleteAllMealPlans()
                planCount = deleted.first
                entryCount = deleted.second
                "Deleted $planCount meal plan${planCount.pluralWord} and $entryCount planned entr${if (entryCount == 1) "y" else "ies"}."
            }
            refreshFromDisk()
            showFeedback(result.summaryOrFallback("Deleted meal plans."))
        }
    }

    fun updateReceipt(
        id: Long,
        rawText: String,
        status: ReceiptStatus,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            executeMutationCommand(
                type = FoodMutationCommandType.UPDATE_RECEIPT,
                label = "Update receipt",
                payload = mapOf("id" to id.toString(), "status" to status.name),
            ) {
                store.updateReceiptStatus(id, rawText, status)
                "Receipt page updated."
            }
            refreshFromDisk()
            showFeedback("Receipt page updated.")
        }
    }

    fun deleteMealLog(id: Long) {
        val label = _uiState.value.memory.mealLogs.firstOrNull { it.id == id }?.title ?: "meal log"
        viewModelScope.launch(Dispatchers.IO) {
            executeMutationCommand(
                type = FoodMutationCommandType.DELETE_MEAL_LOG,
                label = "Archive meal log",
                payload = mapOf("id" to id.toString(), "title" to label),
            ) {
                store.deleteMealLog(id)
                "Archived meal log: $label."
            }
            refreshFromDisk()
            registerUndo(UndoKind.MEAL, id, label, "Archived meal log: $label.")
        }
    }

    fun createManual(request: ManualCreateRequest) {
        val title = request.title.trim()
        if (title.isBlank()) {
            showFeedback("Name is required.")
            return
        }
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val draft = when (request.kind) {
                ManualCreateKind.INVENTORY -> InventoryDraft(
                    items = listOf(FoodCandidate(name = title, quantity = request.detail.trim(), zone = request.zone)),
                )
                ManualCreateKind.GROCERY -> GroceryDraft(
                    items = listOf(FoodCandidate(name = title, quantity = request.detail.trim(), zone = classifyStorageZone(title))),
                )
                ManualCreateKind.RECIPE -> RecipeDraft(
                    titleText = title,
                    ingredientsText = request.detail.trim(),
                    stepsText = request.secondaryDetail.trim(),
                    tags = "manual",
                )
                ManualCreateKind.MEAL -> MealLogDraft(
                    titleText = title,
                    mealSlot = request.slot,
                    loggedDateEpochDay = request.dateEpochDay ?: java.time.LocalDate.now().toEpochDay(),
                    calories = request.calories,
                    source = FoodDraftCommandOrigin.MANUAL_SAVE.writeSource,
                )
            }
            val result = executeDraftCommand(draft, sourceMessageId = null, origin = FoodDraftCommandOrigin.MANUAL_SAVE)
            refreshFromDisk(isWorking = false)
            showFeedback(
                when (result) {
                    is FoodDraftExecutionResult.Applied -> result.summary
                    is FoodDraftExecutionResult.Rejected -> "Manual save failed: ${result.errors.joinToString("; ")}"
                },
            )
        }
    }

    fun logWater(ml: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            var eventId = -1L
            val message = executeMutationCommand(
                type = FoodMutationCommandType.LOG_EVENT,
                label = "Log water",
                payload = mapOf("amount_ml" to ml.toString(), "source" to "manual"),
            ) {
                val (createdEventId, status) = store.logWaterEvent(ml, source = "manual")
                eventId = createdEventId
                status
            }.summaryOrFallback("Logged $ml ml water.")
            refreshFromDisk()
            if (eventId != -1L) registerUndo(UndoKind.EVENT, eventId, "water log", message)
        }
    }

    fun refreshHealthStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = health.dailySummary()
            _uiState.update {
                it.copy(
                    healthStatus = summary.label,
                    healthSummary = summary,
                )
            }
        }
    }

    fun refreshSyncStatus() {
        _uiState.update {
            it.copy(
                syncStatus = backupGateway.latestBackupLabel(),
                googleAccountEmail = readGoogleAccountEmail(),
                googleOAuthClientId = readGoogleOAuthClientId(),
                googleSyncStatus = googleSyncStatusLabel(),
            )
        }
    }

    fun refreshBackendHome() {
        viewModelScope.launch(Dispatchers.IO) {
            val config = backendConfigurationStore.activeConfiguration()
            val dismissed = backendConfigurationStore.onboardingDismissed()
            val syncStatus = shellPrefs.getString(KEY_BACKEND_SYNC_STATUS, "").orEmpty()
            val safetyStatus = backupGateway.latestBackendSwitchSafetyLabel()
            _uiState.update {
                it.copy(
                    backendHome = BackendHomeUiState.fromConfig(
                        config = config,
                        onboardingDismissed = dismissed,
                    ).let { backendHome ->
                        backendHome.copy(
                            message = syncStatus.ifBlank { backendHome.message },
                            safetyMessage = safetyStatus,
                        )
                    },
                )
            }
        }
    }

    fun chooseLocalBackend() {
        viewModelScope.launch(Dispatchers.IO) {
            val previous = _uiState.value.backendHome.label
            val safety = createBackendSwitchSafety("On this phone")
            backendConfigurationStore.saveActiveConfiguration(LocalSqliteConfig())
            backendConfigurationStore.setOnboardingDismissed(true)
            _uiState.update {
                it.copy(
                    backendHome = BackendHomeUiState(
                        activeType = BackendType.LOCAL_SQLITE,
                        label = "On this phone",
                        detail = "Private local storage is active.",
                        requiresOnboarding = false,
                        safetyMessage = safety,
                    ),
                )
            }
            showFeedback("WonderFood will keep data on this phone. Rollback snapshot saved from $previous.")
        }
    }

    fun validateGoogleSheetsBackend(sheetInput: String) {
        connectGoogleSheetsBackend(sheetInput, readGoogleAccountEmail(), accessToken = "pending")
    }

    fun connectGoogleSheetsBackend(sheetInput: String, accountEmail: String, accessToken: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (accessToken.isBlank()) {
                _uiState.update {
                    it.copy(
                        backendHome = it.backendHome.copy(
                            message = "Google Sheets permission did not return access. Try again.",
                        ),
                    )
                }
                return@launch
            }
            val reference = runCatching { GoogleSheetsUrlParser.parse(sheetInput) }
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            backendHome = it.backendHome.copy(
                                message = error.safeMessage(),
                            ),
                        )
                    }
                    return@launch
                }
            val bootstrap = runCatching {
                googleSheetsGateway.ensureWonderFoodSchema(accessToken, reference.spreadsheetId)
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        backendHome = it.backendHome.copy(
                            message = "Google Sheets check failed: ${error.safeMessage()}",
                        ),
                    )
                }
                showFeedback("Google Sheets check failed: ${error.safeMessage()}")
                return@launch
            }
            val snapshot = LegacyFoodMemorySnapshotExporter.toSnapshot(store.readMemory())
            val coordinator = GoogleSheetsSnapshotSyncCoordinator(
                sheetsGateway = googleSheetsGateway,
                clock = { java.time.Instant.now().toString() },
            )
            val workspaceMerge = runCatching {
                coordinator.readRemoteWorkspaceMerge(accessToken, reference.spreadsheetId, snapshot)
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        backendHome = it.backendHome.copy(
                            message = "Google Sheets workspace merge check failed: ${error.safeMessage()}",
                        ),
                    )
                }
                showFeedback("Google Sheets workspace merge check failed: ${error.safeMessage()}")
                return@launch
            }
            val remoteSnapshot = runCatching {
                coordinator.readRemoteSnapshot(accessToken, reference.spreadsheetId)
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        backendHome = it.backendHome.copy(
                            message = "Google Sheets import check failed: ${error.safeMessage()}",
                        ),
                    )
                }
                showFeedback("Google Sheets import check failed: ${error.safeMessage()}")
                return@launch
            }
            val workspaceImportPreview = (workspaceMerge as? GoogleSheetsSnapshotSyncResult.RemoteWorkspaceMerge)
                ?.merge
                ?.toSheetsImportPreview(reference.canonicalUrl, workspaceMerge.rowCount)
            val shouldPreserveRemote = workspaceImportPreview != null || remoteSnapshot is GoogleSheetsSnapshotSyncResult.RemoteSnapshot &&
                remoteSnapshot.snapshot.hasUserData()
            val rawSnapshotImportPreview = (remoteSnapshot as? GoogleSheetsSnapshotSyncResult.RemoteSnapshot)
                ?.snapshot
                ?.takeIf { workspaceImportPreview == null && shouldPreserveRemote }
                ?.toSheetsImportPreview(reference.canonicalUrl)
            val sheetsImportPreview = workspaceImportPreview ?: rawSnapshotImportPreview
            pendingSheetsImportSnapshot = sheetsImportPreview?.let {
                workspaceImportPreview?.let { workspaceMerge.merge.snapshot }
                    ?: (remoteSnapshot as GoogleSheetsSnapshotSyncResult.RemoteSnapshot).snapshot
            }
            pendingSheetsImportLabel = if (workspaceImportPreview != null) "Sheet workspace merge" else "Sheet data"
            val export = if (shouldPreserveRemote) {
                null
            } else {
                runCatching {
                    coordinator.exportSnapshot(accessToken, reference.spreadsheetId, snapshot)
                }.getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            backendHome = it.backendHome.copy(
                                message = "Google Sheets export failed: ${error.safeMessage()}",
                            ),
                        )
                    }
                    showFeedback("Google Sheets export failed: ${error.safeMessage()}")
                    return@launch
                }
            }
            val safety = createBackendSwitchSafety("Google Sheets")
            val credentialRef = CredentialRef(BackendType.GOOGLE_SHEETS, "google-sheets-primary")
            credentialVault.put(
                credentialRef,
                BackendSecret.OAuthAccess(
                    accessToken = accessToken,
                    refreshToken = null,
                    expiresAtEpochMillis = null,
                ),
            )
            backendConfigurationStore.saveActiveConfiguration(
                GoogleSheetsConfig(
                    spreadsheetUrl = reference.canonicalUrl,
                    spreadsheetId = reference.spreadsheetId,
                    accountEmail = accountEmail.ifBlank { null },
                    credentialRef = credentialRef,
                ),
            )
            backendConfigurationStore.setOnboardingDismissed(true)
            _uiState.update {
                it.copy(
                    backendHome = BackendHomeUiState(
                        activeType = BackendType.GOOGLE_SHEETS,
                        label = "Google Sheets",
                        detail = if (shouldPreserveRemote) {
                            "Connected to ${bootstrap.title.ifBlank { "Google Sheet" }}. Existing WonderFood data was preserved."
                        } else {
                            "Connected to ${bootstrap.title.ifBlank { "Google Sheet" }}. Snapshot sync is active."
                        },
                        requiresOnboarding = false,
                        sheetUrl = reference.canonicalUrl,
                        safetyMessage = safety,
                    ),
                    sheetsImportPreview = sheetsImportPreview,
                )
            }
            val feedback = if (shouldPreserveRemote) {
                "Google Sheets ready: existing WonderFood data found and preserved for import."
            } else {
                "Google Sheets ready: ${bootstrap.createdCount} tabs created, ${bootstrap.initializedCount} headers checked, ${export?.rowCount ?: 0} sync rows exported plus Home, Kitchen, Recipes, Meals, Plans, Shopping, Purchases, Goals, and managed data tabs."
            }
            shellPrefs.edit { putString(KEY_BACKEND_SYNC_STATUS, feedback) }
            showFeedback(feedback)
        }
    }

    fun cancelSheetsImportPreview() {
        val preview = _uiState.value.sheetsImportPreview
        val inbox = preview.toConflictInbox(
            decision = "Preserved remote workspace; no local changes applied.",
        )
        inbox?.let(::rememberWorkspaceConflictInbox)
        pendingSheetsImportSnapshot = null
        pendingSheetsImportLabel = "Sheet data"
        _uiState.update {
            it.copy(
                sheetsImportPreview = null,
                workspaceConflictInbox = inbox ?: it.workspaceConflictInbox,
                backendHome = it.backendHome.copy(
                    message = "Existing ${preview?.providerLabel ?: "Sheet"} data preserved. Import/merge review is still needed.",
                ),
            )
        }
    }

    fun confirmSheetsImportPreview() {
        val snapshot = pendingSheetsImportSnapshot
        val preview = _uiState.value.sheetsImportPreview
        if (snapshot == null) {
            _uiState.update {
                it.copy(
                    sheetsImportPreview = null,
                    backendHome = it.backendHome.copy(message = "Sheet import preview expired. Reconnect to review it again."),
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val draft = LegacySnapshotDraftImporter.toDraft(snapshot)
            if (draft == null) {
                pendingSheetsImportSnapshot = null
                _uiState.update {
                    it.copy(
                        sheetsImportPreview = null,
                        backendHome = it.backendHome.copy(message = "Sheet import found no daily food records to apply."),
                    )
                }
                return@launch
            }
            val result = executeDraftCommand(
                draft = draft,
                sourceMessageId = null,
                origin = FoodDraftCommandOrigin.CSV_IMPORT,
            )
            pendingSheetsImportSnapshot = null
            val label = pendingSheetsImportLabel
            pendingSheetsImportLabel = "Sheet data"
            val message = when (result) {
                is FoodDraftExecutionResult.Applied -> "Imported $label. ${result.summary}"
                is FoodDraftExecutionResult.Rejected -> "$label rejected: ${result.errors.joinToString("; ")}"
            }
            val inbox = preview.toConflictInbox(decision = message)
            inbox?.let(::rememberWorkspaceConflictInbox)
            _uiState.update {
                it.copy(
                    memory = store.readMemory(),
                    sheetsImportPreview = null,
                    workspaceConflictInbox = inbox ?: it.workspaceConflictInbox,
                    backendHome = it.backendHome.copy(message = message),
                )
            }
            shellPrefs.edit { putString(KEY_BACKEND_SYNC_STATUS, message) }
            showFeedback(message)
        }
    }

    fun clearWorkspaceConflictInbox() {
        shellPrefs.edit { remove(KEY_WORKSPACE_CONFLICT_INBOX) }
        _uiState.update { it.copy(workspaceConflictInbox = null) }
    }

    fun connectNotionBackend(pageInput: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) {
                showBackendMessage("Notion token is required.")
                return@launch
            }
            val reference = runCatching { NotionUrlParser.parse(pageInput) }
                .getOrElse { error ->
                    showBackendMessage(error.safeMessage())
                    return@launch
                }
            val access = runCatching { notionGateway.retrievePage(cleanToken, reference.pageId) }
                .getOrElse { error ->
                    showBackendMessage("Notion check failed: ${error.safeMessage()}")
                    return@launch
                }
            val remoteSnapshot = runCatching { notionGateway.readRemoteSnapshot(cleanToken, reference.pageId) }
                .getOrElse { error ->
                    showBackendMessage("Notion import check failed: ${error.safeMessage()}")
                    return@launch
                }
            val snapshot = LegacyFoodMemorySnapshotExporter.toSnapshot(store.readMemory())
            val updatedAt = java.time.Instant.now().toString()
            val workspaceMerge = runCatching {
                notionGateway.readRemoteWorkspaceMerge(
                    token = cleanToken,
                    pageId = reference.pageId,
                    baseSnapshot = snapshot,
                    updatedAt = updatedAt,
                )
            }.getOrElse { error ->
                showBackendMessage("Notion workspace merge check failed: ${error.safeMessage()}")
                return@launch
            }
            val notionImportPreview = workspaceMerge.merge?.toSheetsImportPreview(
                spreadsheetUrl = reference.canonicalUrl,
                workspaceRowCount = workspaceMerge.rowCount,
                providerLabel = "Notion",
            )
            val rawSnapshotImportPreview = remoteSnapshot.snapshot
                ?.takeIf { notionImportPreview == null && it.hasUserData() }
                ?.toSheetsImportPreview(reference.canonicalUrl, providerLabel = "Notion")
            val importPreview = notionImportPreview ?: rawSnapshotImportPreview
            val foundRemoteData = importPreview != null
            pendingSheetsImportSnapshot = importPreview?.let {
                notionImportPreview?.let { workspaceMerge.merge.snapshot } ?: requireNotNull(remoteSnapshot.snapshot)
            }
            pendingSheetsImportLabel = if (notionImportPreview != null) "Notion workspace merge" else "Notion data"
            val workspaceProvision = runCatching {
                if (foundRemoteData) {
                    notionGateway.ensureWorkspaceDatabases(cleanToken, reference.pageId)
                } else {
                    null
                }
            }.getOrElse { error ->
                showBackendMessage("Notion workspace check failed: ${error.safeMessage()}")
                return@launch
            }
            val workspaceExport = if (foundRemoteData) {
                null
            } else {
                runCatching {
                    notionGateway.exportWorkspace(
                        token = cleanToken,
                        pageId = reference.pageId,
                        snapshot = snapshot,
                        updatedAt = updatedAt,
                    )
                }.getOrElse { error ->
                    showBackendMessage("Notion workspace export failed: ${error.safeMessage()}")
                    return@launch
                }
            }
            val export = if (foundRemoteData) {
                null
            } else {
                runCatching {
                    notionGateway.exportSnapshot(
                        token = cleanToken,
                        pageId = reference.pageId,
                        snapshot = snapshot,
                        updatedAt = updatedAt,
                    )
                }.getOrElse { error ->
                    showBackendMessage("Notion export failed: ${error.safeMessage()}")
                    return@launch
                }
            }
            val safety = createBackendSwitchSafety("Notion")
            val credentialRef = CredentialRef(BackendType.NOTION, "notion-primary")
            credentialVault.put(credentialRef, BackendSecret.BearerToken(cleanToken))
            backendConfigurationStore.saveActiveConfiguration(
                NotionConfig(
                    pageUrl = reference.canonicalUrl,
                    rootPageId = reference.pageId,
                    workspaceName = null,
                    credentialRef = credentialRef,
                ),
            )
            backendConfigurationStore.setOnboardingDismissed(true)
            _uiState.update {
                it.copy(
                    backendHome = BackendHomeUiState(
                        activeType = BackendType.NOTION,
                        label = "Notion",
                        detail = if (foundRemoteData) {
                            "Notion page ${access.pageId} has existing WonderFood data. Workspace databases and snapshot sync are active; import review is next."
                        } else {
                            "Notion page ${access.pageId} is reachable. Workspace databases and snapshot sync are active."
                        },
                        requiresOnboarding = false,
                        message = if (foundRemoteData) {
                            val createdCount = workspaceProvision?.createdDatabases?.size ?: 0
                            "Notion preserved existing remote data, created $createdCount database${createdCount.pluralWord}, and prepared an import review instead of overwriting your workspace."
                        } else {
                            val createdCount = workspaceExport?.createdDatabases?.size ?: 0
                            val upsertedRows = workspaceExport?.upsertedRows ?: 0
                            val chunkCount = export?.chunkCount ?: 0
                            "Notion created $createdCount database${createdCount.pluralWord}, upserted $upsertedRows workspace row${upsertedRows.pluralWord}, and exported $chunkCount snapshot block${chunkCount.pluralWord}."
                        },
                        safetyMessage = safety,
                    ),
                    sheetsImportPreview = importPreview,
                )
            }
            val feedback = if (foundRemoteData) {
                "Notion connected: existing remote data preserved; ${workspaceProvision?.createdDatabases?.size ?: 0} database${(workspaceProvision?.createdDatabases?.size ?: 0).pluralWord} created and import review is ready."
            } else {
                "Notion connected: ${workspaceExport?.createdDatabases?.size ?: 0} database${(workspaceExport?.createdDatabases?.size ?: 0).pluralWord} created and ${workspaceExport?.upsertedRows ?: 0} workspace row${(workspaceExport?.upsertedRows ?: 0).pluralWord} synced."
            }
            shellPrefs.edit { putString(KEY_BACKEND_SYNC_STATUS, feedback) }
            showFeedback(feedback)
        }
    }

    fun connectPostgresBackend(endpoint: String, householdId: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanToken = token.trim()
            val reference = runCatching {
                PostgresConnectionParser.parse(endpoint = endpoint, householdId = householdId)
            }.getOrElse { error ->
                showBackendMessage(error.safeMessage())
                return@launch
            }
            if (reference.mode != PostgresConnectionMode.DIRECT_DSN && cleanToken.isBlank()) {
                showBackendMessage("Postgres API token is required.")
                return@launch
            }
            val hostedAccess = if (reference.mode == PostgresConnectionMode.DIRECT_DSN) {
                null
            } else {
                runCatching {
                    postgresGateway.validateHostedApi(reference.mode, reference.endpoint, cleanToken)
                }.getOrElse { error ->
                    showBackendMessage("${reference.mode.label} check failed: ${error.safeMessage()}")
                    return@launch
                }
            }
            val export = if (reference.mode == PostgresConnectionMode.DIRECT_DSN) {
                null
            } else {
                val remoteSnapshot = runCatching {
                    postgresGateway.readRemoteSnapshot(
                        mode = reference.mode,
                        endpoint = reference.endpoint,
                        token = cleanToken,
                        householdId = reference.householdId,
                    )
                }.getOrElse { error ->
                    showBackendMessage("${reference.mode.label} import check failed: ${error.safeMessage()}")
                    return@launch
                }
                val foundRemoteData = remoteSnapshot.snapshot?.hasUserData() == true
                runCatching {
                    postgresGateway.exportSnapshot(
                        mode = reference.mode,
                        endpoint = reference.endpoint,
                        token = cleanToken,
                        householdId = reference.householdId,
                        snapshot = LegacyFoodMemorySnapshotExporter.toSnapshot(store.readMemory()),
                        updatedAt = java.time.Instant.now().toString(),
                    )
                }.getOrElse { error ->
                    showBackendMessage("${reference.mode.label} export failed: ${error.safeMessage()}")
                    return@launch
                } to foundRemoteData
            }
            val safety = createBackendSwitchSafety(reference.mode.label)
            val credentialRef = CredentialRef(BackendType.POSTGRES, "postgres-primary")
            val secret = if (reference.mode == PostgresConnectionMode.DIRECT_DSN) {
                BackendSecret.ConnectionString(requireNotNull(reference.credentialSecret))
            } else {
                BackendSecret.ApiToken(cleanToken)
            }
            credentialVault.put(credentialRef, secret)
            backendConfigurationStore.saveActiveConfiguration(
                PostgresConfig(
                    connectionMode = reference.mode,
                    endpoint = reference.endpoint,
                    householdId = reference.householdId,
                    credentialRef = credentialRef,
                ),
            )
            backendConfigurationStore.setOnboardingDismissed(true)
            _uiState.update {
                it.copy(
                    backendHome = BackendHomeUiState(
                        activeType = BackendType.POSTGRES,
                        label = reference.mode.label,
                        detail = if (hostedAccess == null) {
                            "Direct PostgreSQL connection string saved for ${reference.householdId}."
                        } else if (export?.second == true) {
                            "${hostedAccess.mode.label} has existing WonderFood data for ${reference.householdId}. Snapshot export is active; import review is next."
                        } else {
                            "${hostedAccess.mode.label} snapshot export is active for ${reference.householdId}."
                        },
                        requiresOnboarding = false,
                        message = export?.let {
                            if (it.second) {
                                "${reference.mode.label} preserved existing remote data and exported ${it.first.byteCount} bytes."
                            } else {
                                "${reference.mode.label} exported ${it.first.byteCount} bytes."
                            }
                        }
                            ?: "${reference.mode.label} connected.",
                        safetyMessage = safety,
                    ),
                )
            }
            val feedback = export?.let {
                if (it.second) {
                    "${reference.mode.label} connected: existing remote data detected; exported ${it.first.byteCount} bytes."
                } else {
                    "${reference.mode.label} connected: exported ${it.first.byteCount} bytes."
                }
            }
                ?: "${reference.mode.label} connected."
            shellPrefs.edit { putString(KEY_BACKEND_SYNC_STATUS, feedback) }
            showFeedback(feedback)
        }
    }

    fun selectPendingBackend(type: BackendType) {
        val message = when (type) {
            BackendType.NOTION -> "Notion setup is next: page link plus integration token."
            BackendType.POSTGRES -> "Postgres setup is next: server URL, token, and household."
            BackendType.GOOGLE_SHEETS -> "Paste a Google Sheet link first."
            BackendType.LOCAL_SQLITE -> "Use this phone to continue without setup."
        }
        _uiState.update { it.copy(backendHome = it.backendHome.copy(message = message)) }
    }

    private val PostgresConnectionMode.label: String
        get() = when (this) {
            PostgresConnectionMode.SUPABASE -> "Supabase"
            PostgresConnectionMode.POSTGREST -> "PostgREST"
            PostgresConnectionMode.WONDERFOOD_SERVER -> "WonderFood server"
            PostgresConnectionMode.DIRECT_DSN -> "Direct PostgreSQL"
        }

    private fun createBackendSwitchSafety(toLabel: String): String {
        val fromLabel = _uiState.value.backendHome.label
        val snapshot = backupGateway.createBackendSwitchSafetyBackup(
            memory = store.readMemory(),
            fromLabel = fromLabel,
            toLabel = toLabel,
        )
        return "Rollback snapshot before $fromLabel -> $toLabel: ${snapshot.sizeBytes / 1024} KB"
    }

    private fun showBackendMessage(message: String) {
        _uiState.update {
            it.copy(
                backendHome = it.backendHome.copy(message = message),
            )
        }
        showFeedback(message)
    }

    fun dismissBackendOnboardingForNow() {
        backendConfigurationStore.setOnboardingDismissed(true)
        _uiState.update {
            it.copy(
                backendHome = it.backendHome.copy(
                    requiresOnboarding = false,
                    message = "You can choose a data home from Settings later.",
                ),
            )
        }
    }

    fun onGoogleOAuthClientIdChange(value: String) {
        val cleaned = value.trim()
        googleSyncPrefs.edit { putString(KEY_GOOGLE_WEB_CLIENT_ID, cleaned) }
        _uiState.update {
            it.copy(
                googleOAuthClientId = cleaned,
                googleSyncStatus = googleSyncStatusLabel(),
            )
        }
    }

    fun onGoogleSignIn(profile: GoogleAccountProfile) {
        googleSyncPrefs.edit {
            putString(KEY_GOOGLE_EMAIL, profile.email)
            putString(KEY_GOOGLE_NAME, profile.displayName)
        }
        _uiState.update {
            it.copy(
                googleAccountEmail = profile.email,
                googleSyncStatus = "Connected as ${profile.email}. Ready to back up or restore.",
            )
        }
        showFeedback("Google connected: ${profile.email}.")
    }

    fun onGoogleSyncError(message: String) {
        _uiState.update { it.copy(isWorking = false, googleSyncStatus = message) }
        showFeedback(message)
    }

    fun backupToGoogleDrive(access: GoogleDriveAccess) {
        if (access.accessToken.isBlank()) {
            onGoogleSyncError("Google Drive access expired. Connect Google again.")
            return
        }
        rememberGoogleEmail(access.accountEmail)
        _uiState.update {
            it.copy(
                isWorking = true,
                googleSyncStatus = "Creating Google Drive backup…",
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                store.checkpointForBackup()
                val memory = store.readMemory()
                val payload = backupGateway.createGoogleDriveBackup(memory)
                val remote = googleDriveGateway.uploadBackup(access.accessToken, payload)
                store.insertMessage(
                    ChatRole.ASSISTANT,
                    "Backed up WonderFood to Google Drive app data: ${payload.snapshot.itemCount} food objects.",
                )
                remote to payload
            }.onSuccess { (_, payload) ->
                refreshFromDisk(isWorking = false)
                _uiState.update {
                    it.copy(
                        syncStatus = backupGateway.latestBackupLabel(),
                        googleAccountEmail = readGoogleAccountEmail(),
                        googleSyncStatus = "Google backup complete: ${payload.snapshot.itemCount} food objects, ${payload.sizeBytes / 1024} KB.",
                    )
                }
                showFeedback("Backed up to Google Drive.")
            }.onFailure { error ->
                refreshFromDisk(isWorking = false)
                _uiState.update {
                    it.copy(googleSyncStatus = "Google backup failed: ${error.safeMessage()}")
                }
                showFeedback("Google backup failed: ${error.safeMessage()}")
            }
        }
    }

    fun restoreFromGoogleDrive(access: GoogleDriveAccess) {
        if (access.accessToken.isBlank()) {
            onGoogleSyncError("Google Drive access expired. Connect Google again.")
            return
        }
        rememberGoogleEmail(access.accountEmail)
        _uiState.update {
            it.copy(
                isWorking = true,
                googleRestorePreview = null,
                googleSyncStatus = "Downloading latest Google Drive backup…",
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val download = googleDriveGateway.downloadLatestBackup(access.accessToken)
                val preview = backupGateway.previewGoogleDriveBackup(
                    bytes = download.bytes,
                    sourceName = download.remoteFile.name,
                    remoteModifiedTime = download.remoteFile.modifiedTime,
                    remoteSizeBytes = download.remoteFile.sizeBytes,
                )
                pendingGoogleRestoreDownload = download
                preview.toUiPreview()
            }.onSuccess { preview ->
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        googleAccountEmail = readGoogleAccountEmail(),
                        googleRestorePreview = preview,
                        googleSyncStatus = "Restore preview ready: ${preview.itemCount} food objects.",
                    )
                }
            }.onFailure { error ->
                refreshFromDisk(isWorking = false)
                _uiState.update {
                    it.copy(googleSyncStatus = "Google restore preview failed: ${error.safeMessage()}")
                }
                showFeedback("Google restore preview failed: ${error.safeMessage()}")
            }
        }
    }

    fun cancelGoogleRestorePreview() {
        pendingGoogleRestoreDownload = null
        _uiState.update { it.copy(googleRestorePreview = null, googleSyncStatus = googleSyncStatusLabel()) }
    }

    fun confirmGoogleRestorePreview() {
        val download = pendingGoogleRestoreDownload ?: run {
            showFeedback("Download the latest Google backup first.")
            return
        }
        _uiState.update {
            it.copy(
                isWorking = true,
                googleRestorePreview = null,
                googleSyncStatus = "Creating safety backup before restore…",
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                store.checkpointForBackup()
                val safety = backupGateway.createRestoreSafetyBackup(store.readMemory())
                store.close()
                val snapshot = backupGateway.restoreGoogleDriveBackup(download.bytes, download.remoteFile.name)
                store = FoodChatStore(appContext)
                store.insertMessage(
                    ChatRole.ASSISTANT,
                    "Restored WonderFood from Google Drive backup `${download.remoteFile.name}`. Safety backup created: ${safety.fileName}.",
                )
                safety to snapshot
            }.onSuccess { (safety, snapshot) ->
                pendingGoogleRestoreDownload = null
                refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
                _uiState.update {
                    it.copy(
                        syncStatus = backupGateway.latestBackupLabel(),
                        googleAccountEmail = readGoogleAccountEmail(),
                        googleSyncStatus = "Restored ${snapshot.itemCount} food objects from Google Drive. Safety backup: ${safety.fileName}.",
                    )
                }
                showFeedback("Restored WonderFood from Google Drive.")
            }.onFailure { error ->
                store = FoodChatStore(appContext)
                refreshFromDisk(isWorking = false)
                _uiState.update {
                    it.copy(googleSyncStatus = "Google restore failed: ${error.safeMessage()}")
                }
                showFeedback("Google restore failed: ${error.safeMessage()}")
            }
        }
    }

    fun disconnectGoogleSync() {
        googleSyncPrefs.edit {
            remove(KEY_GOOGLE_EMAIL)
            remove(KEY_GOOGLE_NAME)
        }
        _uiState.update {
            it.copy(
                googleAccountEmail = "",
                googleSyncStatus = googleSyncStatusLabel(),
            )
        }
        showFeedback("Google backup disconnected on this phone.")
    }

    fun refreshAiStatus() {
        val primary = liteLlmSettings.read()
        val fallback = liteLlmSettings.readFallback() ?: LiteLlmConfig("", "", LiteLlmSettings.DEFAULT_MODEL)
        val label = if (primary.isUsable) {
            "AI: primary ${primary.statusLabel}" + if (fallback.isUsable) " → fallback ${fallback.statusLabel}" else ""
        } else if (fallback.isUsable) {
            "AI: fallback ${fallback.statusLabel} (primary not configured)"
        } else {
            "AI: local fallback"
        }
        _uiState.update {
            it.copy(
                aiStatus = label,
                aiConfigForm = primary,
                savedAiConfig = primary,
                aiFallbackConfigForm = fallback,
                savedAiFallbackConfig = fallback,
            )
        }
    }

    fun testAiConnection(config: LiteLlmConfig = _uiState.value.aiConfigForm) {
        _uiState.update { it.copy(isWorking = true, aiStatus = "Testing ${config.statusLabel}…") }
        viewModelScope.launch(Dispatchers.IO) {
            liteLlmInterpreter.testConnection(config)
                .onSuccess { message ->
                    _uiState.update { it.copy(isWorking = false, aiStatus = message) }
                    showFeedback(message)
                }
                .onFailure { error ->
                    val failure = error.safeMessage()
                    val message = "AI connection failed: $failure${config.connectionFailureHint(failure)}"
                    _uiState.update { it.copy(isWorking = false, aiStatus = message) }
                    showFeedback(message)
                }
        }
    }

    fun deleteAllAppData() {
        _uiState.update { it.copy(isWorking = true, googleRestorePreview = null, csvImportPreview = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                pendingUndo = null
                pendingGoogleRestoreDownload = null
                preferenceAutoSaveJob?.cancel()
                store.close()
                appContext.deleteDatabase("wonderfood.db")
                backupGateway.deleteLocalBackups()
                liteLlmSettings.clear()
                googleSyncPrefs.edit { clear() }
                directActionPrefs.edit { clear() }
                shellPrefs.edit { clear() }
                store = FoodChatStore(appContext)
                store.seedIfEmpty()
            }.onSuccess {
                _uiState.update { WonderFoodUiState(section = FoodSection.TODAY, memory = store.readMemory(), isWorking = false) }
                refreshAiStatus()
                refreshHealthStatus()
                refreshSyncStatus()
                showFeedback("Deleted local WonderFood data on this device.")
            }.onFailure { error ->
                store = FoodChatStore(appContext)
                refreshFromDisk(isWorking = false)
                showFeedback("Delete all app data failed: ${error.safeMessage()}")
            }
        }
    }

    fun exportMeal(id: Long) {
        val meal = _uiState.value.memory.mealLogs.firstOrNull { it.id == id } ?: run {
            showFeedback("Meal log not found.")
            return
        }
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val message = when (health.exportMeal(meal)) {
                HealthExportResult.Exported -> "Exported `${meal.title}` to Health Connect nutrition."
                HealthExportResult.MissingNutrition -> "Add calories and macros before syncing this meal."
                HealthExportResult.MissingPermission -> "Health Connect needs nutrition write permission first."
                HealthExportResult.Unavailable -> "Health Connect is unavailable on this device."
                HealthExportResult.Failed -> "Health Connect export failed. The local meal log is still saved."
            }
            store.insertMessage(ChatRole.ASSISTANT, message)
            refreshHealthStatus()
            refreshFromDisk(isWorking = false)
            showFeedback(message)
        }
    }

    fun createEncryptedBackup(passphrase: String) {
        if (passphrase.length < 8) {
            showFeedback("Use at least 8 characters for the backup passphrase.")
            return
        }
        _uiState.update { it.copy(isWorking = true, syncStatus = "Creating encrypted backup…") }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                store.checkpointForBackup()
                backupGateway.createEncryptedBackup(passphrase, store.readMemory())
            }.onSuccess { snapshot ->
                refreshFromDisk(isWorking = false)
                _uiState.update {
                    it.copy(syncStatus = "Encrypted local backup ready: ${snapshot.sizeBytes / 1024} KB.")
                }
                showFeedback("Encrypted backup created.")
            }.onFailure { error ->
                refreshFromDisk(isWorking = false)
                _uiState.update { it.copy(syncStatus = "Backup failed: ${error.safeMessage()}") }
                showFeedback("Backup failed: ${error.safeMessage()}")
            }
        }
    }

    fun restoreLatestEncryptedBackup(passphrase: String) {
        if (passphrase.length < 8) {
            showFeedback("Enter the backup passphrase used for this backup.")
            return
        }
        _uiState.update { it.copy(isWorking = true, syncStatus = "Restoring encrypted backup…") }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                store.close()
                backupGateway.restoreLatestEncryptedBackup(passphrase)
            }.onSuccess { snapshot ->
                store = FoodChatStore(appContext)
                refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
                _uiState.update {
                    it.copy(syncStatus = "Restored ${snapshot.itemCount} food objects from latest backup.")
                }
                showFeedback("Restored latest WonderFood backup.")
            }.onFailure { error ->
                store = FoodChatStore(appContext)
                refreshFromDisk(isWorking = false)
                _uiState.update { it.copy(syncStatus = "Restore failed: ${error.safeMessage()}") }
                showFeedback("Restore failed: ${error.safeMessage()}")
            }
        }
    }

    fun exportCsvTo(uri: Uri) {
        _uiState.update { it.copy(isWorking = true, syncStatus = "Exporting CSV…") }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val memory = store.readMemory()
                val csv = WonderFoodCsvGateway.export(memory)
                val resolver = appContext.contentResolver
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(csv)
                } ?: error("Could not open selected CSV destination.")
                memory
            }.onSuccess { memory ->
                refreshFromDisk(isWorking = false)
                _uiState.update {
                    it.copy(syncStatus = "CSV exported: ${memory.inventory.size} kitchen, ${memory.groceries.size} shopping, ${memory.recipes.size} recipes.")
                }
                showFeedback("WonderFood CSV exported.")
            }.onFailure { error ->
                refreshFromDisk(isWorking = false)
                _uiState.update { it.copy(syncStatus = "CSV export failed: ${error.safeMessage()}") }
                showFeedback("CSV export failed: ${error.safeMessage()}")
            }
        }
    }

    fun importDataFrom(uri: Uri) {
        _uiState.update { it.copy(isWorking = true, syncStatus = "Reading WonderFood import…", csvImportPreview = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val raw = readImportText(uri)
                val proposal = CommandEnvelopeDraftMapper.tryMap(raw)
                if (proposal != null) {
                    val sourceMessageId = store.insertMessage(ChatRole.USER, "Imported external WonderFood proposal file.")
                    store.insertMessage(ChatRole.ASSISTANT, proposal.reply)
                    return@runCatching ImportReadResult.Proposal(
                        turn = proposal,
                        sourceMessageId = sourceMessageId,
                    )
                }
                val imported = WonderFoodCsvGateway.parse(raw)
                require(imported.importedCount > 0) { "No WonderFood rows found in CSV." }
                ImportReadResult.Csv(imported.toPreview(uri))
            }.onSuccess { result ->
                when (result) {
                    is ImportReadResult.Proposal -> {
                        pendingDraftOrigin = if (result.turn.draft == null) {
                            FoodDraftCommandOrigin.AI_REVIEW
                        } else {
                            FoodDraftCommandOrigin.EXTERNAL_PROPOSAL
                        }
                        refreshFromDisk(
                            pendingDraft = result.turn.draft,
                            pendingSourceMessageId = result.sourceMessageId,
                            isWorking = false,
                        )
                        _uiState.update {
                            it.copy(
                                csvImportPreview = null,
                                syncStatus = if (result.turn.draft != null) {
                                    "External proposal ready for review."
                                } else {
                                    "External proposal was read, but no draft can be applied."
                                },
                                voiceStatus = if (result.turn.draft != null) "Proposal ready. Review before saving." else result.turn.reply,
                            )
                        }
                    }
                    is ImportReadResult.Csv -> {
                        _uiState.update {
                            it.copy(
                                isWorking = false,
                                csvImportPreview = result.preview,
                                syncStatus = "CSV import preview ready: ${result.preview.summary}.",
                            )
                        }
                    }
                }
            }.onFailure { error ->
                refreshFromDisk(isWorking = false)
                _uiState.update { it.copy(syncStatus = "Import failed: ${error.safeMessage()}") }
                showFeedback("Import failed: ${error.safeMessage()}")
            }
        }
    }

    fun importCsvFrom(uri: Uri) {
        importDataFrom(uri)
    }

    fun cancelCsvImportPreview() {
        _uiState.update { it.copy(csvImportPreview = null, syncStatus = "CSV import cancelled.") }
    }

    fun confirmCsvImportPreview() {
        val preview = _uiState.value.csvImportPreview ?: run {
            showFeedback("Choose a CSV file first.")
            return
        }
        _uiState.update { it.copy(isWorking = true, syncStatus = "Importing CSV…", csvImportPreview = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val imported = readCsvImport(preview.uri)
                require(imported.importedCount > 0) { "No WonderFood rows found in CSV." }
                applyCsvImport(imported)
                imported
            }.onSuccess { imported ->
                refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
                _uiState.update { it.copy(syncStatus = "CSV imported: ${imported.summary()}.") }
                showFeedback("CSV imported: ${imported.summary()}.")
            }.onFailure { error ->
                refreshFromDisk(isWorking = false)
                _uiState.update { it.copy(syncStatus = "CSV import failed: ${error.safeMessage()}") }
                showFeedback("CSV import failed: ${error.safeMessage()}")
            }
        }
    }

    private fun readCsvImport(uri: Uri): WonderFoodCsvImport {
        val csv = readImportText(uri)
        return WonderFoodCsvGateway.parse(csv)
    }

    private fun readImportText(uri: Uri): String =
        appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        } ?: error("Could not open selected import file.")

    private fun applyCsvImport(imported: WonderFoodCsvImport) {
        val drafts = buildList {
            if (imported.inventory.isNotEmpty()) add(InventoryDraft(imported.inventory))
            if (imported.groceries.isNotEmpty()) add(GroceryDraft(imported.groceries))
            addAll(imported.recipes)
            addAll(imported.mealLogs)
            addAll(imported.mealPlans)
        }
        if (drafts.isNotEmpty()) {
            val draft = if (drafts.size == 1) drafts.single() else CompositeDraft(drafts)
            when (val result = executeDraftCommand(draft, sourceMessageId = null, origin = FoodDraftCommandOrigin.CSV_IMPORT)) {
                is FoodDraftExecutionResult.Applied -> store.insertMessage(ChatRole.ASSISTANT, result.summary)
                is FoodDraftExecutionResult.Rejected -> store.insertMessage(
                    ChatRole.ASSISTANT,
                    "CSV draft rejected before saving: ${result.errors.joinToString("; ")}",
                )
            }
        }
        imported.preferences?.let(store::savePreferences)
        store.insertMessage(ChatRole.ASSISTANT, "Imported CSV: ${imported.summary()}.")
    }

    private data class AiInterpretation(
        val turn: AiTurn,
        val origin: FoodDraftCommandOrigin,
        val autoAcceptAllowed: Boolean,
        val status: String,
    )

    private fun submitToAi(text: String, source: String, promptContext: String? = null, autoAccept: Boolean = false) {
        val sourceMessageId = store.insertMessage(ChatRole.USER, if (source == "voice") "Voice note: $text" else text)
        val memory = store.readMemory()
        val configs = liteLlmSettings.readAll()
        val promptText = promptContext
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it\n\nUser request:\n$text" }
            ?: text
        val interpretation = interpretWithVisibleRetries(promptText, text, memory, promptContext, configs)
        val turn = interpretation.turn
        store.insertMessage(ChatRole.ASSISTANT, turn.reply)
        if (autoAccept && turn.draft != null && interpretation.autoAcceptAllowed) {
            val result = executeDraftCommand(turn.draft, sourceMessageId, FoodDraftCommandOrigin.VOICE_AUTO_ACCEPT)
            val summary = when (result) {
                is FoodDraftExecutionResult.Applied -> result.summary
                is FoodDraftExecutionResult.Rejected -> "Draft rejected before saving: ${result.errors.joinToString("; ")}"
            }
            refreshFromDisk(
                pendingDraft = null,
                pendingSourceMessageId = null,
                isWorking = false,
            )
            _uiState.update {
                it.copy(
                    voiceStatus = "Okay, added. $summary",
                    aiAttemptStatus = "",
                    guidedVoiceAutoAccept = false,
                    guidedVoiceInstruction = "",
                )
            }
            return
        }
        pendingDraftOrigin = interpretation.origin
        refreshFromDisk(
            pendingDraft = turn.draft,
            pendingSourceMessageId = sourceMessageId,
            isWorking = false,
        )
        _uiState.update {
            it.copy(
                voiceStatus = if (turn.draft != null) {
                    if (autoAccept && !interpretation.autoAcceptAllowed) {
                        "Provider proposal ready. Auto-accept is disabled for LLM drafts; review before saving."
                    } else {
                        "Proposal ready. Review before saving."
                    }
                } else {
                    turn.reply
                },
                aiAttemptStatus = interpretation.status,
                guidedVoiceAutoAccept = false,
                guidedVoiceInstruction = "",
            )
        }
    }

    private fun interpretWithVisibleRetries(
        promptText: String,
        originalText: String,
        memory: FoodMemory,
        promptContext: String?,
        configs: List<LiteLlmConfig>,
    ): AiInterpretation {
        CommandEnvelopeDraftMapper.tryMap(originalText)?.let { turn ->
            _uiState.update {
                it.copy(
                    aiAttemptStatus = "Imported structured proposal locally.",
                    voiceStatus = "Imported structured proposal locally.",
                )
            }
            return AiInterpretation(
                turn = turn,
                origin = FoodDraftCommandOrigin.EXTERNAL_PROPOSAL,
                autoAcceptAllowed = false,
                status = "Imported structured proposal locally.",
            )
        }
        val localReceipt = DeterministicReceiptParser.tryParse(originalText, promptContext)
        val localTurn = interpreter.interpret(originalText, memory, promptContext)
        var lastProviderDiagnostic = ""
        configs.forEachIndexed { index, config ->
            val routeLabel = if (index == 0) "primary" else "fallback"
            _uiState.update {
                it.copy(
                    aiAttemptStatus = "Trying $routeLabel: ${config.statusLabel}.",
                    voiceStatus = "Trying $routeLabel AI provider.",
                )
            }
            when (val result = liteLlmInterpreter.interpretWithDiagnostics(promptText, memory, config)) {
                is LiteLlmInterpretation.Success -> {
                    _uiState.update {
                        it.copy(
                            aiAttemptStatus = "Answered by ${config.statusLabel}.",
                            voiceStatus = "Answered by ${config.statusLabel}.",
                        )
                    }
                    return AiInterpretation(
                        turn = result.turn,
                        origin = if (result.turn.draft.containsReceiptDraft()) FoodDraftCommandOrigin.RECEIPT else FoodDraftCommandOrigin.AI_REVIEW,
                        autoAcceptAllowed = false,
                        status = result.diagnostic,
                    )
                }
                is LiteLlmInterpretation.Failure -> {
                    lastProviderDiagnostic = result.diagnostic
                    _uiState.update {
                        it.copy(
                            aiAttemptStatus = result.diagnostic,
                            voiceStatus = if (index == 0 && configs.size > 1) {
                                "Primary failed; checking fallback."
                            } else {
                                "AI provider failed."
                            },
                        )
                    }
                }
            }
        }
        val localFallbackStatus = if (configs.size > 1) {
            "Primary and fallback failed. Using local fallback."
        } else {
            "Primary failed. Using local fallback."
        }
        _uiState.update {
            it.copy(
                aiAttemptStatus = localFallbackStatus,
                voiceStatus = localFallbackStatus,
            )
        }
        if (localReceipt != null) {
            return AiInterpretation(
                turn = AiTurn(
                    reply = "The configured AI route was unavailable. I recovered ${localReceipt.draft.items.size} receipt line${localReceipt.draft.items.size.pluralWord} locally. Review every field before saving.",
                    draft = FoodDraftNormalizer.normalize(localReceipt.draft),
                ),
                origin = FoodDraftCommandOrigin.RECEIPT,
                autoAcceptAllowed = false,
                status = lastProviderDiagnostic.ifBlank { "AI unavailable; receipt parsed locally." },
            )
        }
        return AiInterpretation(
            turn = localTurn,
            origin = FoodDraftCommandOrigin.LOCAL_FALLBACK,
            autoAcceptAllowed = localTurn.draft != null,
            status = lastProviderDiagnostic.ifBlank { "Provider routes unavailable. Using local fallback." },
        )
    }

    private fun interpretReceiptPhotoWithVisibleRetries(
        uri: Uri,
        memory: FoodMemory,
        configs: List<LiteLlmConfig>,
        userNote: String,
    ): com.wonderfood.app.data.AiTurn? {
        configs.forEachIndexed { index, config ->
            val routeLabel = if (index == 0) "primary" else "fallback"
            _uiState.update {
                it.copy(
                    aiAttemptStatus = "Reading receipt with $routeLabel: ${config.statusLabel}.",
                    voiceStatus = "Reading receipt with $routeLabel AI provider.",
                )
            }
            receiptCaptureProvider.interpretReceiptPhoto(appContext, uri, memory, config, userNote)?.let { turn ->
                _uiState.update {
                    it.copy(
                        aiAttemptStatus = "Receipt read by ${config.statusLabel} via ${receiptCaptureProvider.providerName}.",
                        voiceStatus = "Receipt read by ${config.statusLabel} via ${receiptCaptureProvider.providerName}.",
                    )
                }
                return turn
            }
        }
        val status = if (configs.size > 1) {
            "Receipt primary and fallback failed."
        } else {
            "Receipt primary failed."
        }
        _uiState.update {
            it.copy(
                aiAttemptStatus = status,
                voiceStatus = status,
            )
        }
        return null
    }

    private fun refreshFromDisk(
        pendingDraft: FoodDraft? = _uiState.value.pendingDraft,
        pendingSourceMessageId: Long? = _uiState.value.pendingSourceMessageId,
        isWorking: Boolean = _uiState.value.isWorking,
        pendingDraftOrigin: FoodDraftCommandOrigin = this.pendingDraftOrigin,
    ) {
        val memory = store.readMemory()
        _uiState.update {
            it.copy(
                memory = memory,
                preferencesForm = if (it.preferencesForm == FoodPreferences()) memory.preferences else it.preferencesForm,
                pendingDraft = pendingDraft?.withPreferenceRiskWarnings(memory.preferences),
                pendingSourceMessageId = pendingSourceMessageId,
                pendingDraftOrigin = pendingDraftOrigin,
                isWorking = isWorking,
            )
        }
    }

    private fun setSection(section: FoodSection) {
        saveSelectedSection(section)
        _uiState.update { it.copy(section = section, detailTarget = null) }
    }

    private fun saveSelectedSection(section: FoodSection) {
        shellPrefs.edit { putString(KEY_SELECTED_SECTION, section.name) }
    }

    private fun readSelectedSection(): FoodSection =
        runCatching {
            FoodSection.valueOf(
                shellPrefs.getString(KEY_SELECTED_SECTION, FoodSection.TODAY.name).orEmpty(),
            )
        }.getOrDefault(FoodSection.TODAY)

    private fun rememberWorkspaceConflictInbox(inbox: WorkspaceConflictInbox) {
        shellPrefs.edit { putString(KEY_WORKSPACE_CONFLICT_INBOX, inbox.toJson().toString()) }
    }

    private fun readWorkspaceConflictInbox(): WorkspaceConflictInbox? {
        val raw = shellPrefs.getString(KEY_WORKSPACE_CONFLICT_INBOX, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            WorkspaceConflictInbox(
                providerLabel = json.optString("providerLabel"),
                sourceLabel = json.optString("sourceLabel"),
                conflictCount = json.optInt("conflictCount"),
                changeCount = json.optInt("changeCount"),
                mergeClock = json.optString("mergeClock"),
                decision = json.optString("decision"),
                conflictSummary = json.optJSONArray("conflictSummary")
                    .orEmptyStrings(),
            )
        }.getOrNull()
    }

    private fun rememberGoogleEmail(email: String) {
        if (email.isBlank()) return
        googleSyncPrefs.edit { putString(KEY_GOOGLE_EMAIL, email) }
    }

    private fun readGoogleAccountEmail(): String =
        googleSyncPrefs.getString(KEY_GOOGLE_EMAIL, "").orEmpty()

    private fun readGoogleOAuthClientId(): String =
        googleSyncPrefs.getString(KEY_GOOGLE_WEB_CLIENT_ID, "").orEmpty()

    private fun googleSyncStatusLabel(): String {
        val email = readGoogleAccountEmail()
        val cached = backupGateway.latestCloudBackupLabel()
        val hasClientId = readGoogleOAuthClientId().isNotBlank()
        return if (!hasClientId) {
            "Paste the Google Web OAuth client ID on this phone, then sign in."
        } else if (email.isBlank()) {
            "OAuth client ID saved. Sign in with Google to back up and restore from Drive."
        } else {
            "Connected as $email. $cached"
        }
    }

    private fun hasHandledDirectAction(command: WonderFoodVoiceCommand): Boolean =
        command.idempotencyKey.isNotBlank() &&
            directActionPrefs.getBoolean(command.directActionPreferenceKey(), false)

    private fun markDirectActionHandled(command: WonderFoodVoiceCommand) {
        if (command.idempotencyKey.isBlank()) return
        directActionPrefs.edit { putBoolean(command.directActionPreferenceKey(), true) }
    }

    private fun WonderFoodVoiceCommand.directActionPreferenceKey(): String =
        "handled:$idempotencyKey"

    private fun executeDraftCommand(
        draft: FoodDraft,
        sourceMessageId: Long?,
        origin: FoodDraftCommandOrigin,
    ): FoodDraftExecutionResult {
        val result = draftCommandExecutor.execute(FoodDraftCommand(draft, sourceMessageId, origin))
        if (result is FoodDraftExecutionResult.Applied) {
            queueBackendSnapshotSync("draft")
        }
        return result
    }

    private fun executeMutationCommand(
        type: FoodMutationCommandType,
        label: String,
        origin: FoodDraftCommandOrigin = FoodDraftCommandOrigin.MANUAL_SAVE,
        sourceMessageId: Long? = null,
        payload: Map<String, String?> = emptyMap(),
        write: () -> String,
    ): FoodMutationExecutionResult {
        val result = mutationCommandExecutor.execute(
            FoodMutationCommand(
                type = type,
                label = label,
                origin = origin,
                sourceMessageId = sourceMessageId,
                payload = payload,
            ),
            write = write,
        )
        if (result is FoodMutationExecutionResult.Applied) {
            queueBackendSnapshotSync(type.name.lowercase())
        }
        return result
    }

    private fun queueBackendSnapshotSync(reason: String) {
        backendSnapshotSyncJob?.cancel()
        backendSnapshotSyncJob = viewModelScope.launch(Dispatchers.IO) {
            delay(BACKEND_SNAPSHOT_SYNC_DEBOUNCE_MILLIS)
            val config = backendConfigurationStore.activeConfiguration() ?: return@launch
            val export = runCatching { exportSnapshotToActiveBackend(config) }
            _uiState.update {
                val message = export.fold(
                    onSuccess = { result -> "$result after $reason." },
                    onFailure = { error -> "Backend sync failed after $reason: ${error.safeMessage()}" },
                )
                shellPrefs.edit { putString(KEY_BACKEND_SYNC_STATUS, message) }
                it.copy(backendHome = it.backendHome.copy(message = message))
            }
        }
    }

    private suspend fun exportSnapshotToActiveBackend(config: com.wonderfood.core.data.backend.BackendConfig): String {
        val snapshot = LegacyFoodMemorySnapshotExporter.toSnapshot(store.readMemory())
        return when (config) {
            is GoogleSheetsConfig -> {
                val secret = credentialVault.get(config.credentialRef) as? BackendSecret.OAuthAccess ?: return "Google Sheets sync skipped: missing OAuth token"
                val result = GoogleSheetsSnapshotSyncCoordinator(
                    sheetsGateway = googleSheetsGateway,
                    clock = { java.time.Instant.now().toString() },
                ).exportSnapshot(
                    accessToken = secret.accessToken,
                    spreadsheetId = config.spreadsheetId,
                    snapshot = snapshot,
                )
                "Google Sheets synced ${result.rowCount} rows"
            }
            is NotionConfig -> {
                val secret = credentialVault.get(config.credentialRef) as? BackendSecret.BearerToken ?: return "Notion sync skipped: missing token"
                val updatedAt = java.time.Instant.now().toString()
                val workspaceResult = notionGateway.exportWorkspace(
                    token = secret.token,
                    pageId = config.rootPageId,
                    snapshot = snapshot,
                    updatedAt = updatedAt,
                )
                val result = notionGateway.exportSnapshot(
                    token = secret.token,
                    pageId = config.rootPageId,
                    snapshot = snapshot,
                    updatedAt = updatedAt,
                )
                "Notion synced ${workspaceResult.upsertedRows} workspace row${workspaceResult.upsertedRows.pluralWord} and ${result.chunkCount} snapshot block${result.chunkCount.pluralWord}"
            }
            is PostgresConfig -> {
                if (config.connectionMode == PostgresConnectionMode.DIRECT_DSN) return "Direct PostgreSQL sync skipped: server-side adapter required"
                val secret = credentialVault.get(config.credentialRef) as? BackendSecret.ApiToken ?: return "${config.connectionMode.label} sync skipped: missing API token"
                val result = postgresGateway.exportSnapshot(
                    mode = config.connectionMode,
                    endpoint = config.endpoint,
                    token = secret.token,
                    householdId = config.householdId,
                    snapshot = snapshot,
                    updatedAt = java.time.Instant.now().toString(),
                )
                "${config.connectionMode.label} synced ${result.byteCount} bytes"
            }
            else -> "Backend sync skipped: ${config.type.label} export is not implemented yet"
        }
    }

    private fun FoodMutationExecutionResult.summaryOrFallback(successFallback: String): String =
        when (this) {
            is FoodMutationExecutionResult.Applied -> summary.ifBlank { successFallback }
            is FoodMutationExecutionResult.Rejected -> "Command rejected: ${errors.joinToString("; ")}"
            is FoodMutationExecutionResult.Failed -> "Command failed: $summary"
        }

    private fun stageVoiceDraftForReview(
        command: WonderFoodVoiceCommand,
        draft: FoodDraft,
        section: FoodSection,
        status: String,
        origin: FoodDraftCommandOrigin = FoodDraftCommandOrigin.GOOGLE_ASSISTANT,
        sourceLabel: String = "Google Assistant",
    ) {
        val normalizedDraft = FoodDraftNormalizer.normalize(draft)
        pendingDraftOrigin = origin
        val sourceText = command.voiceSourceText()
        val sourceMessageId = store.insertMessage(
            ChatRole.USER,
            if (sourceText.isBlank()) "$sourceLabel request." else "$sourceLabel: $sourceText",
        )
        store.insertMessage(ChatRole.ASSISTANT, "$status Nothing is saved until you accept.")
        setSection(section)
        refreshFromDisk(
            pendingDraft = normalizedDraft,
            pendingSourceMessageId = sourceMessageId,
            isWorking = false,
        )
        _uiState.update { it.copy(voiceStatus = "$status Review before saving.") }
    }

    private fun WonderFoodVoiceCommand.voiceSourceText(): String =
        text.ifBlank { itemName }
            .ifBlank { recipeName }
            .ifBlank { quantity }

    private fun WonderFoodVoiceCommand.toLinkActionDraft(): FoodDraft? {
        val drafts = linkActions.map { action ->
            LinkActionDraft(
                actionType = action.type,
                targetKind = action.targetKind,
                targetRef = action.targetRef,
                displayName = action.displayName,
                fields = action.fields,
                destructive = action.destructive,
                sensitive = action.sensitive,
            )
        }
        return when (drafts.size) {
            0 -> null
            1 -> drafts.single()
            else -> CompositeDraft(drafts)
        }
    }

    private fun WonderFoodVoiceCommand.linkActionSection(): FoodSection =
        when (linkActions.firstOrNull()?.targetKind) {
            "grocery" -> FoodSection.SHOP
            "inventory" -> FoodSection.KITCHEN
            "meal_log", "event" -> FoodSection.TODAY
            "meal_plan", "plan_entry" -> FoodSection.PLAN
            "recipe" -> FoodSection.RECIPES
            "preferences" -> FoodSection.TODAY
            else -> FoodSection.TODAY
        }

    private fun WonderFoodVoiceCommand.localGroceryDraft(): GroceryDraft? {
        val packedText = text.takeIf { it.contains(",") || it.contains(" and ", ignoreCase = true) } ?: return null
        val turn = interpreter.interpret(
            text = "Need $packedText",
            memory = store.readMemory(),
            promptContext = "Current WonderFood section: Shop. Infer the smallest food-memory operation.",
        )
        return turn.draft as? GroceryDraft
    }

    private fun WonderFoodVoiceCommand.localInventoryDraft(): InventoryDraft? {
        val packedText = text.takeIf { it.contains(",") || it.contains(" and ", ignoreCase = true) } ?: return null
        val turn = interpreter.interpret(
            text = "I have $packedText",
            memory = store.readMemory(),
            promptContext = "Current WonderFood section: Kitchen. Infer the smallest food-memory operation.",
        )
        return turn.draft as? InventoryDraft
    }

    private fun applyContextualPageEdit(
        text: String,
        target: FoodDetailTarget?,
        memory: FoodMemory,
    ): String? =
        when (target?.kind) {
            FoodDetailKind.RECIPE -> memory.recipes.firstOrNull { it.id == target.id }?.let { recipe ->
                applyRecipePageEdit(text, recipe)
            }
            FoodDetailKind.MEAL -> memory.mealLogs.firstOrNull { it.id == target.id }?.let { meal ->
                applyMealPageEdit(text, meal)
            }
            else -> null
        }

    private fun applyRecipePageEdit(
        text: String,
        recipe: com.wonderfood.app.data.Recipe,
    ): String? {
        val lower = text.lowercase()
        var title = recipe.title
        var ingredients = recipe.ingredients
        var steps = recipe.steps
        var servings = recipe.servings
        var prepMinutes = recipe.prepMinutes
        var tags = recipe.tags
        var changed = false

        text.extractTitleEdit()?.let {
            title = it
            changed = true
        }
        if ("serving" in lower) {
            text.firstNumberOrNull()?.let {
                servings = it
                changed = true
            }
        }
        if ("minute" in lower || "prep" in lower || "time" in lower) {
            text.firstNumberOrNull()?.let {
                prepMinutes = it
                changed = true
            }
        }
        text.extractFieldEdit("ingredient", "ingredients")?.let { edit ->
            ingredients = if (edit.append) ingredients.appendCsvish(edit.value) else edit.value
            changed = true
        }
        text.extractFieldEdit("step", "steps", "instruction", "instructions")?.let { edit ->
            steps = if (edit.append) steps.appendLineish(edit.value) else edit.value
            changed = true
        }
        text.extractFieldEdit("tag", "tags")?.let { edit ->
            tags = if (edit.append) tags.appendCsvish(edit.value) else edit.value
            changed = true
        }

        if (!changed) return null
        return executeMutationCommand(
            type = FoodMutationCommandType.UPDATE_RECIPE,
            label = "AI page edit recipe",
            origin = FoodDraftCommandOrigin.AI_REVIEW,
            payload = mapOf(
                "id" to recipe.id.toString(),
                "title" to title,
                "servings" to servings?.toString(),
                "prep_minutes" to prepMinutes?.toString(),
                "tags" to tags,
            ),
        ) {
            store.updateRecipe(recipe.id, title, ingredients, steps, servings, prepMinutes, tags, recipe.imageUri, recipe.imageUrl)
            "Updated recipe page: $title."
        }.summaryOrFallback("Updated recipe page: $title.")
    }

    private fun applyMealPageEdit(
        text: String,
        meal: com.wonderfood.app.data.MealLog,
    ): String? {
        val lower = text.lowercase()
        var title = meal.title
        var calories = meal.calories
        var proteinGrams = meal.proteinGrams
        var carbsGrams = meal.carbsGrams
        var fatGrams = meal.fatGrams
        var mealSlot = meal.mealSlot
        var usedItemsText = meal.usedItemsText
        var changed = false

        text.extractTitleEdit()?.let {
            title = it
            changed = true
        }
        text.numberNear("calorie", "calories", "kcal")?.let {
            calories = it
            changed = true
        }
        text.numberNear("protein")?.let {
            proteinGrams = it.toDouble()
            changed = true
        }
        text.numberNear("carb", "carbs", "carbohydrate", "carbohydrates")?.let {
            carbsGrams = it.toDouble()
            changed = true
        }
        text.numberNear("fat", "fats")?.let {
            fatGrams = it.toDouble()
            changed = true
        }
        lower.detectExplicitMealSlot()?.let {
            mealSlot = it
            changed = true
        }
        text.extractFieldEdit("used", "made with", "from kitchen", "ingredient", "ingredients")?.let { edit ->
            usedItemsText = if (edit.append) usedItemsText.appendCsvish(edit.value) else edit.value
            changed = true
        }

        if (!changed) return null
        return executeMutationCommand(
            type = FoodMutationCommandType.UPDATE_MEAL_LOG,
            label = "AI page edit meal",
            origin = FoodDraftCommandOrigin.AI_REVIEW,
            payload = mapOf(
                "id" to meal.id.toString(),
                "title" to title,
                "meal_slot" to mealSlot.name,
                "calories" to calories?.toString(),
                "source" to meal.source.ifBlank { "ai_page_edit" },
            ),
        ) {
            store.updateMealLog(
                id = meal.id,
                title = title,
                calories = calories,
                proteinGrams = proteinGrams,
                carbsGrams = carbsGrams,
                fatGrams = fatGrams,
                mealSlot = mealSlot,
                usedItemsText = usedItemsText,
                loggedDateEpochDay = meal.loggedDateEpochDay,
                source = meal.source.ifBlank { "ai_page_edit" },
            )
            "Updated meal page: $title."
        }.summaryOrFallback("Updated meal page: $title.")
    }

    private fun showFeedback(message: String) {
        _uiState.update { it.copy(feedbackMessage = message, undoMessage = "") }
    }

    private fun registerUndo(
        kind: UndoKind,
        id: Long,
        label: String,
        message: String,
        ids: Set<Long> = setOf(id),
    ) {
        pendingUndo = PendingUndo(kind, id, label, ids)
        _uiState.update {
            it.copy(
                detailTarget = null,
                feedbackMessage = "",
                undoMessage = message,
                isWorking = false,
            )
        }
    }

    private data class PendingUndo(
        val kind: UndoKind,
        val id: Long,
        val label: String,
        val ids: Set<Long>,
    )

    private enum class UndoKind {
        EVENT,
        INVENTORY,
        GROCERY,
        GROCERY_BOUGHT,
        RECIPE,
        MEAL,
        PLAN_ENTRY,
        PLAN_ENTRIES,
    }

    private companion object {
        const val SHELL_PREFS_NAME = "wonderfood_shell"
        const val DIRECT_ACTION_PREFS_NAME = "wonderfood_direct_actions"
        const val GOOGLE_SYNC_PREFS_NAME = "wonderfood_google_sync"
        const val KEY_SELECTED_SECTION = "selected_section"
        const val KEY_GOOGLE_EMAIL = "google_email"
        const val KEY_GOOGLE_NAME = "google_name"
        const val KEY_GOOGLE_WEB_CLIENT_ID = "google_web_client_id"
        const val KEY_BACKEND_SYNC_STATUS = "backend_sync_status"
        const val KEY_WORKSPACE_CONFLICT_INBOX = "workspace_conflict_inbox"
        const val PREFERENCE_AUTO_SAVE_DELAY_MILLIS = 450L
        const val BACKEND_SNAPSHOT_SYNC_DEBOUNCE_MILLIS = 900L
    }
}

private val Int.pluralWord: String
    get() = if (this == 1) "" else "s"

private fun Throwable.safeMessage(): String =
    message?.take(140)?.ifBlank { null } ?: "unknown error"

private fun WonderFoodSnapshot.hasUserData(): Boolean =
    foods.isNotEmpty() ||
        stockLots.isNotEmpty() ||
        recipes.isNotEmpty() ||
        mealPlans.isNotEmpty() ||
        mealLogs.isNotEmpty() ||
        shoppingItems.isNotEmpty() ||
        receipts.isNotEmpty() ||
        foodEvents.isNotEmpty()

private fun WonderFoodSnapshot.toSheetsImportPreview(
    spreadsheetUrl: String,
    providerLabel: String = "Google Sheets",
): SheetsImportPreview =
    SheetsImportPreview(
        spreadsheetUrl = spreadsheetUrl,
        providerLabel = providerLabel,
        sourceLabel = "Raw WonderFood snapshot",
        schemaVersion = schemaVersion,
        foodCount = foods.size,
        stockLotCount = stockLots.size,
        shoppingItemCount = shoppingItems.size,
        recipeCount = recipes.size,
        mealPlanCount = mealPlans.size,
        mealLogCount = mealLogs.size,
        eventCount = foodEvents.size,
    )

private fun WorkspaceMergeResult.toSheetsImportPreview(
    spreadsheetUrl: String,
    workspaceRowCount: Int,
    providerLabel: String = "Google Sheets",
): SheetsImportPreview =
    SheetsImportPreview(
        spreadsheetUrl = spreadsheetUrl,
        providerLabel = providerLabel,
        sourceLabel = "Editable workspace tabs",
        schemaVersion = snapshot.schemaVersion,
        foodCount = snapshot.foods.size,
        stockLotCount = snapshot.stockLots.size,
        shoppingItemCount = snapshot.shoppingItems.size,
        recipeCount = snapshot.recipes.size,
        mealPlanCount = snapshot.mealPlans.size,
        mealLogCount = snapshot.mealLogs.size,
        eventCount = snapshot.foodEvents.size,
        workspaceRowCount = workspaceRowCount,
        changeCount = changes.size,
        conflictCount = conflicts.size,
        fieldClockCount = fieldClocks.size,
        mergeClock = mergeClock,
        conflictSummary = conflicts.take(4).map { "${it.table}: ${it.field} - ${it.reason}" },
    )

private fun LiteLlmConfig.connectionFailureHint(message: String): String {
    if (!message.contains("HTTP 403", ignoreCase = true)) return ""
    return when (provider) {
        AiProvider.OPENAI_COMPATIBLE ->
            " Hint: 403 usually means the key/project can reach the provider but is not allowed to use this model or route."
        AiProvider.AZURE_OPENAI ->
            " Hint: 403 usually means the key, resource, deployment, or network policy rejected this request."
        AiProvider.ANTHROPIC ->
            " Hint: 403 usually means the key or workspace is not allowed to use this model."
    }
}

data class WonderFoodUiState(
    val memory: FoodMemory = FoodMemory(),
    val input: String = "",
    val section: FoodSection = FoodSection.TODAY,
    val pendingDraft: FoodDraft? = null,
    val pendingSourceMessageId: Long? = null,
    val pendingDraftOrigin: FoodDraftCommandOrigin = FoodDraftCommandOrigin.AI_REVIEW,
    val aiStatus: String = "AI: checking",
    val aiAttemptStatus: String = "",
    val healthStatus: String = "Checking Health Connect",
    val healthSummary: HealthDailySummary = HealthDailySummary(),
    val syncStatus: String = "Local encrypted backup ready.",
    val googleAccountEmail: String = "",
    val googleOAuthClientId: String = "",
    val googleSyncStatus: String = "Paste the Google Web OAuth client ID on this phone, then sign in.",
    val googleRestorePreview: GoogleRestorePreview? = null,
    val csvImportPreview: CsvImportPreview? = null,
    val sheetsImportPreview: SheetsImportPreview? = null,
    val workspaceConflictInbox: WorkspaceConflictInbox? = null,
    val settingsSaveStatus: String = "",
    val preferencesForm: FoodPreferences = FoodPreferences(),
    val aiConfigForm: LiteLlmConfig = LiteLlmConfig("", "", LiteLlmSettings.DEFAULT_MODEL),
    val savedAiConfig: LiteLlmConfig = LiteLlmConfig("", "", LiteLlmSettings.DEFAULT_MODEL),
    val aiFallbackConfigForm: LiteLlmConfig = LiteLlmConfig("", "", LiteLlmSettings.DEFAULT_MODEL),
    val savedAiFallbackConfig: LiteLlmConfig = LiteLlmConfig("", "", LiteLlmSettings.DEFAULT_MODEL),
    val detailTarget: FoodDetailTarget? = null,
    val voiceStatus: String = "",
    val guidedVoicePrompt: String = "",
    val guidedVoiceNonce: Long = 0L,
    val guidedVoiceAutoAccept: Boolean = false,
    val guidedVoiceInstruction: String = "",
    val feedbackMessage: String = "",
    val undoMessage: String = "",
    val isWorking: Boolean = false,
    val backendHome: BackendHomeUiState = BackendHomeUiState(),
)

data class BackendHomeUiState(
    val activeType: BackendType? = null,
    val label: String = "Choose data home",
    val detail: String = "Pick where WonderFood keeps kitchen, plan, recipe and shopping data.",
    val requiresOnboarding: Boolean = true,
    val sheetUrl: String = "",
    val message: String = "",
    val safetyMessage: String = "",
) {
    companion object {
        fun fromConfig(
            config: com.wonderfood.core.data.backend.BackendConfig?,
            onboardingDismissed: Boolean,
        ): BackendHomeUiState =
            when (config) {
                is LocalSqliteConfig -> BackendHomeUiState(
                    activeType = BackendType.LOCAL_SQLITE,
                    label = "On this phone",
                    detail = "Private local storage is active.",
                    requiresOnboarding = false,
                )
                is GoogleSheetsConfig -> BackendHomeUiState(
                    activeType = BackendType.GOOGLE_SHEETS,
                    label = "Google Sheets",
                    detail = "Sheet connected. Schema check and sync are next.",
                    requiresOnboarding = false,
                    sheetUrl = config.spreadsheetUrl,
                )
                null -> BackendHomeUiState(requiresOnboarding = !onboardingDismissed)
                else -> BackendHomeUiState(
                    activeType = config.type,
                    label = config.type.label,
                    detail = "Connection details saved.",
                    requiresOnboarding = false,
                )
            }
    }
}

val BackendType.label: String
    get() = when (this) {
        BackendType.LOCAL_SQLITE -> "On this phone"
        BackendType.GOOGLE_SHEETS -> "Google Sheets"
        BackendType.NOTION -> "Notion"
        BackendType.POSTGRES -> "Postgres / Supabase"
    }

data class GoogleRestorePreview(
    val fileName: String,
    val modifiedTime: String,
    val sizeBytes: Long,
    val format: String,
    val schemaVersion: Int,
    val device: String,
    val createdAtMillis: Long,
    val inventoryCount: Int,
    val groceryCount: Int,
    val recipeCount: Int,
    val mealCount: Int,
    val mealPlanCount: Int,
    val planEntryCount: Int,
    val messageCount: Int,
    val itemCount: Int,
)

data class CsvImportPreview(
    val uri: Uri,
    val inventoryCount: Int,
    val groceryCount: Int,
    val recipeCount: Int,
    val mealCount: Int,
    val planCount: Int,
    val importsPreferences: Boolean,
    val summary: String,
)

data class SheetsImportPreview(
    val spreadsheetUrl: String,
    val providerLabel: String,
    val sourceLabel: String,
    val schemaVersion: Int,
    val foodCount: Int,
    val stockLotCount: Int,
    val shoppingItemCount: Int,
    val recipeCount: Int,
    val mealPlanCount: Int,
    val mealLogCount: Int,
    val eventCount: Int,
    val workspaceRowCount: Int = 0,
    val changeCount: Int = 0,
    val conflictCount: Int = 0,
    val fieldClockCount: Int = 0,
    val mergeClock: String = "",
    val conflictSummary: List<String> = emptyList(),
)

data class WorkspaceConflictInbox(
    val providerLabel: String,
    val sourceLabel: String,
    val conflictCount: Int,
    val changeCount: Int,
    val mergeClock: String,
    val decision: String,
    val conflictSummary: List<String>,
)

private fun WorkspaceConflictInbox.toJson(): JSONObject =
    JSONObject()
        .put("providerLabel", providerLabel)
        .put("sourceLabel", sourceLabel)
        .put("conflictCount", conflictCount)
        .put("changeCount", changeCount)
        .put("mergeClock", mergeClock)
        .put("decision", decision)
        .put("conflictSummary", JSONArray().apply { conflictSummary.forEach(::put) })

private fun JSONArray?.orEmptyStrings(): List<String> =
    if (this == null) {
        emptyList()
    } else {
        List(length()) { index -> optString(index) }
    }

private fun SheetsImportPreview?.toConflictInbox(decision: String): WorkspaceConflictInbox? {
    val preview = this ?: return null
    if (preview.conflictCount <= 0 && preview.conflictSummary.isEmpty()) return null
    return WorkspaceConflictInbox(
        providerLabel = preview.providerLabel,
        sourceLabel = preview.sourceLabel,
        conflictCount = preview.conflictCount,
        changeCount = preview.changeCount,
        mergeClock = preview.mergeClock,
        decision = decision,
        conflictSummary = preview.conflictSummary,
    )
}

private sealed interface ImportReadResult {
    data class Proposal(
        val turn: AiTurn,
        val sourceMessageId: Long,
    ) : ImportReadResult

    data class Csv(
        val preview: CsvImportPreview,
    ) : ImportReadResult
}

private fun WonderFoodCsvImport.toPreview(uri: Uri): CsvImportPreview =
    CsvImportPreview(
        uri = uri,
        inventoryCount = inventory.size,
        groceryCount = groceries.size,
        recipeCount = recipes.size,
        mealCount = mealLogs.size,
        planCount = mealPlans.size,
        importsPreferences = preferences != null,
        summary = summary(),
    )

private fun com.wonderfood.app.sync.BackupManifestPreview.toUiPreview(): GoogleRestorePreview =
    GoogleRestorePreview(
        fileName = fileName,
        modifiedTime = modifiedTime,
        sizeBytes = sizeBytes,
        format = format,
        schemaVersion = schemaVersion,
        device = device,
        createdAtMillis = createdAtMillis,
        inventoryCount = inventoryCount,
        groceryCount = groceryCount,
        recipeCount = recipeCount,
        mealCount = mealCount,
        mealPlanCount = mealPlanCount,
        planEntryCount = planEntryCount,
        messageCount = messageCount,
        itemCount = itemCount,
    )

private fun FoodDraft.linkReceipt(receiptId: Long): FoodDraft =
    when (this) {
        is ReceiptDraft -> copy(receiptId = receiptId)
        is CompositeDraft -> copy(drafts = drafts.map { it.linkReceipt(receiptId) })
        else -> this
    }

private fun FoodDraft?.containsReceiptDraft(): Boolean =
    when (this) {
        is ReceiptDraft -> true
        is CompositeDraft -> drafts.any { it.containsReceiptDraft() }
        else -> false
    }

private fun FoodDraft.withPreferenceRiskWarnings(preferences: FoodPreferences): FoodDraft {
    val allergyTerms = preferences.allergies.riskTerms()
    val dislikeTerms = preferences.dislikes.riskTerms()
    if (allergyTerms.isEmpty() && dislikeTerms.isEmpty()) return this

    fun FoodCandidate.review(): FoodCandidate {
        val searchable = "$name $category $notes".lowercase()
        val allergyHits = allergyTerms.filter { it in searchable }
        val dislikeHits = dislikeTerms.filter { it in searchable }
        val added = buildList {
            if (allergyHits.isNotEmpty()) add("Matches saved allergy: ${allergyHits.joinToString()}. Do not accept unless this is intentional and safe.")
            if (dislikeHits.isNotEmpty()) add("Matches saved dislike: ${dislikeHits.joinToString()}.")
        }
        return copy(warnings = (warnings + added).distinct())
    }

    return when (this) {
        is CompositeDraft -> copy(drafts = drafts.map { it.withPreferenceRiskWarnings(preferences) })
        is InventoryDraft -> copy(items = items.map { it.review() })
        is GroceryDraft -> copy(items = items.map { it.review() })
        is ReceiptDraft -> copy(items = items.map { item -> item.copy(food = item.food.review()) })
        else -> this
    }
}

private fun String.riskTerms(): List<String> =
    split(',', ';', '\n', '|')
        .map { it.trim().lowercase() }
        .filter { it.length >= 3 }
        .filterNot { it in setOf("none", "unknown", "no known allergies", "n/a") }
        .distinct()

private fun FoodDraft.receiptAuditText(): String =
    when (this) {
        is ReceiptDraft -> buildString {
            append("Receipt proposal:\n")
            if (merchant.isNotBlank()) append("Merchant: ").append(merchant).append('\n')
            if (storeLocation.isNotBlank()) append("Store location: ").append(storeLocation).append('\n')
            purchasedAtMillis?.let { append("Purchased at: ").append(it).append('\n') }
            subtotalCents?.let { append("Subtotal: ").append(it).append(' ').append(currencyCode).append('\n') }
            taxCents?.let { append("Tax: ").append(it).append(' ').append(currencyCode).append('\n') }
            totalCents?.let { append("Total: ").append(it).append(' ').append(currencyCode).append('\n') }
            items.forEachIndexed { index, item ->
                append(index + 1).append(". ").append(item.food.name)
                    .append(" | ").append(item.disposition.name)
                    .append(" | ").append(item.food.zone.name)
                    .append(" | confidence=").append(item.food.confidence)
                if (item.receiptLine.isNotBlank()) append(" | evidence=").append(item.receiptLine.take(180))
                item.linePriceCents?.let { append(" | line_price_cents=").append(it).append(' ').append(currencyCode) }
                append('\n')
                item.food.warnings.forEach { warning -> append("   Review: ").append(warning).append('\n') }
            }
            if (rawText.isNotBlank()) append("\nVisible text:\n").append(rawText.take(4_000))
        }.trim()
        is CompositeDraft -> drafts.joinToString("\n\n") { it.receiptAuditText() }
        else -> "Proposal:\n${rows.joinToString("\n")}" 
    }

data class ManualCreateRequest(
    val kind: ManualCreateKind,
    val title: String,
    val detail: String = "",
    val secondaryDetail: String = "",
    val zone: StorageZone = StorageZone.PANTRY,
    val slot: MealSlot = MealSlot.FLEX,
    val dateEpochDay: Long? = null,
    val calories: Int? = null,
)

enum class ManualCreateKind {
    INVENTORY,
    GROCERY,
    RECIPE,
    MEAL,
}

data class FoodDetailTarget(
    val kind: FoodDetailKind,
    val id: Long? = null,
    val epochDay: Long? = null,
)

enum class FoodDetailKind {
    INVENTORY,
    GROCERY,
    RECIPE,
    MEAL,
    PLAN,
    DAY,
    RECEIPT,
}

enum class FoodSection(val label: String) {
    TODAY("Now"),
    KITCHEN("Food"),
    PLAN("Week"),
    RECIPES("Saved"),
    SHOP("Cart"),
}

private fun String.toFoodSection(): FoodSection {
    val text = lowercase()
    return when {
        text in listOf("today", "meals", "meal", "numbers", "health") -> FoodSection.TODAY
        text in listOf("kitchen", "pantry", "fridge", "freezer", "inventory") -> FoodSection.KITCHEN
        text in listOf("plan", "plans", "calendar", "week", "month") -> FoodSection.PLAN
        text in listOf("buy", "list", "shopping", "shop", "groceries", "grocery") -> FoodSection.SHOP
        text in listOf("recipe", "recipes", "cook", "cooking") -> FoodSection.RECIPES
        text in listOf("chat", "ask", "more", "settings", "ai", "data", "taste", "preferences") -> FoodSection.TODAY
        else -> FoodSection.TODAY
    }
}

private fun String.toStorageZone(itemName: String): StorageZone =
    when (lowercase().trim()) {
        "fridge", "refrigerator", "refrigirator" -> StorageZone.FRIDGE
        "freezer", "frozen" -> StorageZone.FREEZER
        "pantry", "shelf", "cupboard" -> StorageZone.PANTRY
        else -> classifyStorageZone(itemName)
    }

private fun FoodDetailTarget?.toAiPromptContext(memory: FoodMemory, section: FoodSection): String? =
    when (this?.kind) {
        FoodDetailKind.RECIPE -> memory.recipes.firstOrNull { it.id == id }?.let { recipe ->
            """
            Current WonderFood page: recipe ${recipe.id}, "${recipe.title}".
            Existing properties:
            ingredients: ${recipe.ingredients}
            steps: ${recipe.steps}
            servings: ${recipe.servings ?: "unset"}
            prep_minutes: ${recipe.prepMinutes ?: "unset"}
            tags: ${recipe.tags.ifBlank { "unset" }}
            If the user asks to edit this page, propose the smallest safe recipe update.
            """.trimIndent()
        }
        FoodDetailKind.MEAL -> memory.mealLogs.firstOrNull { it.id == id }?.let { meal ->
            """
            Current WonderFood page: meal log ${meal.id}, "${meal.title}".
            Existing properties:
            slot: ${meal.mealSlot.name}
            calories: ${meal.calories}
            protein_g: ${meal.proteinGrams}
            carbs_g: ${meal.carbsGrams}
            fat_g: ${meal.fatGrams}
            used_items: ${meal.usedItemsText.ifBlank { "none" }}
            If the user asks to edit this page, propose the smallest safe meal log update.
            """.trimIndent()
        }
        FoodDetailKind.INVENTORY -> memory.inventory.firstOrNull { it.id == id }?.let { item ->
            "Current WonderFood page: kitchen item ${item.id}, \"${item.name}\" in ${item.zone.name}, quantity ${item.quantity.ifBlank { "unset" }}."
        }
        FoodDetailKind.GROCERY -> memory.groceries.firstOrNull { it.id == id }?.let { item ->
            "Current WonderFood page: grocery item ${item.id}, \"${item.name}\", status ${item.status.name}, quantity ${item.quantity.ifBlank { "unset" }}."
        }
        FoodDetailKind.DAY -> epochDay?.let { day ->
            "Current WonderFood page: calendar day $day. Prefer meal logs, meal plans, water, and shopping notes for this day."
        }
        FoodDetailKind.PLAN -> memory.mealPlans.firstOrNull { it.id == id }?.let { plan ->
            "Current WonderFood page: meal plan ${plan.id}, \"${plan.title}\". Existing days: ${plan.daysText}"
        }
        FoodDetailKind.RECEIPT -> "Current WonderFood page: receipt. Prefer grocery or inventory extraction."
        null -> "Current WonderFood section: ${section.label}. Infer the smallest food-memory operation."
    }

private data class PageFieldEdit(
    val value: String,
    val append: Boolean,
)

private fun String.extractTitleEdit(): String? {
    val patterns = listOf(
        """(?i)\brename(?:\s+(?:this|recipe|meal|page))*\s+(?:to\s+)?(.+)""",
        """(?i)\bcall\s+(?:it|this|this page)\s+(.+)""",
        """(?i)\b(?:set|change|update)\s+(?:the\s+)?(?:title|name)\s+(?:to\s+)?(.+)""",
        """(?i)\b(?:title|name)\s*[:=]\s*(.+)""",
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        Regex(pattern).find(this)?.groupValues?.getOrNull(1)?.cleanPageEditValue()
    }
}

private fun String.extractFieldEdit(vararg fieldNames: String): PageFieldEdit? {
    val fields = fieldNames.joinToString("|") { Regex.escape(it) }
    val appendPattern = Regex("""(?i)\badd\s+(.+?)\s+(?:to|in|into)\s+(?:the\s+)?(?:$fields)\b""")
    val appendBeforePattern = Regex("""(?i)\b(?:$fields)\s+(?:add|include)\s+(.+)""")
    val setPattern = Regex("""(?i)\b(?:set|change|update|replace)\s+(?:the\s+)?(?:$fields)\s+(?:to|with|as)?\s+(.+)""")
    val colonPattern = Regex("""(?i)\b(?:$fields)\s*[:=]\s*(.+)""")
    return appendPattern.find(this)?.groupValues?.getOrNull(1)?.cleanPageEditValue()?.let {
        PageFieldEdit(it, append = true)
    } ?: appendBeforePattern.find(this)?.groupValues?.getOrNull(1)?.cleanPageEditValue()?.let {
        PageFieldEdit(it, append = true)
    } ?: setPattern.find(this)?.groupValues?.getOrNull(1)?.cleanPageEditValue()?.let {
        PageFieldEdit(it, append = false)
    } ?: colonPattern.find(this)?.groupValues?.getOrNull(1)?.cleanPageEditValue()?.let {
        PageFieldEdit(it, append = false)
    }
}

private fun String.numberNear(vararg labels: String): Int? {
    labels.forEach { label ->
        Regex("""(?i)(\d{1,4})\s*(?:g|grams?|kcal|calories?)?\s*(?:of\s+)?${Regex.escape(label)}""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
        Regex("""(?i)${Regex.escape(label)}\s*(?:to|is|:|=)?\s*(\d{1,4})""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
    }
    return null
}

private fun String.firstNumberOrNull(): Int? =
    Regex("""\d{1,4}""").find(this)?.value?.toIntOrNull()

private fun String.detectExplicitMealSlot(): MealSlot? =
    when {
        "breakfast" in this -> MealSlot.BREAKFAST
        "lunch" in this -> MealSlot.LUNCH
        "dinner" in this -> MealSlot.DINNER
        "snack" in this -> MealSlot.SNACK
        "flex" in this -> MealSlot.FLEX
        else -> null
    }

private fun String.cleanPageEditValue(): String =
    trim()
        .replace(Regex("""^[\s:=-]+"""), "")
        .replace(Regex("""[.!?]+$"""), "")
        .trim()

private fun String.appendCsvish(value: String): String =
    listOf(this.trim(), value.trim())
        .filter { it.isNotBlank() }
        .joinToString(", ")

private fun String.appendLineish(value: String): String =
    listOf(this.trim(), value.trim())
        .filter { it.isNotBlank() }
        .joinToString("\n")

private fun List<com.wonderfood.app.data.Recipe>.findVoiceRecipe(name: String): com.wonderfood.app.data.Recipe? {
    val text = name.trim()
    if (text.isBlank()) return firstOrNull()
    return firstOrNull { it.title.equals(text, ignoreCase = true) }
        ?: firstOrNull { it.title.contains(text, ignoreCase = true) || text.contains(it.title, ignoreCase = true) }
}
