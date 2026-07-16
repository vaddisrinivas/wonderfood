package com.wonderfood.app.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wonderfood.app.ai.FoodInterpreter
import com.wonderfood.app.ai.LiteLlmConfig
import com.wonderfood.app.ai.LiteLlmFoodInterpreter
import com.wonderfood.app.ai.LiteLlmSettings
import com.wonderfood.app.WonderFoodVoiceAction
import com.wonderfood.app.WonderFoodVoiceCommand
import com.wonderfood.app.data.ChatRole
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodChatStore
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodEventConfidence
import com.wonderfood.app.data.FoodEventType
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.FoodPreferences
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.ReceiptStatus
import com.wonderfood.app.data.categorizeFood
import com.wonderfood.app.data.classifyStorageZone
import com.wonderfood.app.health.HealthConnectGateway
import com.wonderfood.app.health.HealthExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainScreenViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val store = FoodChatStore(appContext)
    private val interpreter = FoodInterpreter()
    private val liteLlmInterpreter = LiteLlmFoodInterpreter()
    private val liteLlmSettings = LiteLlmSettings(appContext)
    private val health = HealthConnectGateway(appContext)

    private val _uiState = MutableStateFlow(WonderFoodUiState())
    val uiState: StateFlow<WonderFoodUiState> = _uiState.asStateFlow()

    val healthPermissionContract = health.permissionContract()
    val healthWritePermissions: Set<String> = health.nutritionWritePermissions

    init {
        viewModelScope.launch(Dispatchers.IO) {
            store.seedIfEmpty()
            refreshFromDisk()
            refreshAiStatus()
            refreshHealthStatus()
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun selectSection(section: FoodSection) {
        _uiState.update { it.copy(section = section, detailTarget = null) }
    }

    fun openDetail(target: FoodDetailTarget) {
        _uiState.update { it.copy(detailTarget = target) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(detailTarget = null) }
    }

    fun onPreferencesChange(preferences: FoodPreferences) {
        _uiState.update { it.copy(preferencesForm = preferences) }
    }

    fun onAiConfigChange(config: LiteLlmConfig) {
        _uiState.update { it.copy(aiConfigForm = config) }
    }

    fun savePreferences() {
        val preferences = _uiState.value.preferencesForm
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            store.savePreferences(preferences)
            store.insertMessage(ChatRole.ASSISTANT, "Saved your food preferences and AI instructions.")
            refreshFromDisk(isWorking = false)
        }
    }

    fun saveAiConfig() {
        val config = _uiState.value.aiConfigForm
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            liteLlmSettings.save(config)
            refreshAiStatus()
            store.insertMessage(ChatRole.ASSISTANT, "Saved AI provider settings.")
            refreshFromDisk(isWorking = false)
        }
    }

    fun handleVoiceCommand(command: WonderFoodVoiceCommand) {
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val message = when (command.action) {
                WonderFoodVoiceAction.OPEN_SECTION -> {
                    val section = command.section.toFoodSection()
                    _uiState.update { it.copy(section = section, detailTarget = null) }
                    "Opened ${section.label}."
                }
                WonderFoodVoiceAction.SHOW_NUMBERS -> {
                    _uiState.update { it.copy(section = FoodSection.MEALS, detailTarget = null) }
                    "Opened today's numbers."
                }
                WonderFoodVoiceAction.LOG_WATER -> store.logWater(command.amount?.toInt() ?: 250, source = "google_assistant")
                WonderFoodVoiceAction.START_SHOPPING -> {
                    store.logFoodEvent(FoodEventType.SHOP, source = "google_assistant", confidence = FoodEventConfidence.EXACT, note = "Started shopping")
                    _uiState.update { it.copy(section = FoodSection.BUY, detailTarget = null) }
                    "Started shopping."
                }
                WonderFoodVoiceAction.DONE_SHOPPING -> {
                    store.logFoodEvent(FoodEventType.SHOP, source = "google_assistant", confidence = FoodEventConfidence.EXACT, note = "Finished shopping")
                    _uiState.update { it.copy(section = FoodSection.BUY, detailTarget = null) }
                    "Finished shopping."
                }
                WonderFoodVoiceAction.START_COOKING -> {
                    val recipe = store.readMemory().recipes.findVoiceRecipe(command.recipeName)
                    store.logFoodEvent(
                        type = FoodEventType.COOK,
                        source = "google_assistant",
                        confidence = FoodEventConfidence.EXACT,
                        relatedRecipeId = recipe?.id,
                        note = "Started cooking ${recipe?.title ?: command.recipeName.ifBlank { "recipe" }}",
                    )
                    _uiState.update {
                        it.copy(
                            section = FoodSection.RECIPES,
                            detailTarget = recipe?.let { found -> FoodDetailTarget(FoodDetailKind.RECIPE, id = found.id) },
                        )
                    }
                    "Started cooking ${recipe?.title ?: command.recipeName.ifBlank { "recipe" }}."
                }
                WonderFoodVoiceAction.DONE_COOKING -> {
                    val recipe = store.readMemory().recipes.findVoiceRecipe(command.recipeName)
                    if (recipe == null) {
                        store.logFoodEvent(FoodEventType.COOK, source = "google_assistant", confidence = FoodEventConfidence.ESTIMATED, note = "Finished cooking ${command.recipeName}")
                        _uiState.update { it.copy(section = FoodSection.RECIPES, detailTarget = null) }
                        "Finished cooking."
                    } else {
                        store.cookRecipe(recipe.id)
                    }
                }
                WonderFoodVoiceAction.LOG_MEAL -> {
                    val title = command.text.ifBlank { "Meal from Google" }
                    store.applyDraft(
                        MealLogDraft(
                            titleText = title,
                            calories = command.amount?.toInt() ?: 420,
                            proteinGrams = 18.0,
                            carbsGrams = 42.0,
                            fatGrams = 16.0,
                            mealSlot = MealSlot.FLEX,
                            source = "google_assistant_estimate",
                        ),
                        sourceMessageId = null,
                    )
                    _uiState.update { it.copy(section = FoodSection.MEALS, detailTarget = null) }
                    "Logged meal: $title."
                }
                WonderFoodVoiceAction.ADD_GROCERY -> {
                    val item = command.itemName.ifBlank { "Grocery item" }
                    store.applyDraft(
                        GroceryDraft(
                            items = listOf(
                                FoodCandidate(
                                    name = item,
                                    quantity = command.quantity,
                                    zone = classifyStorageZone(item),
                                    category = categorizeFood(item),
                                ),
                            ),
                        ),
                        sourceMessageId = null,
                    )
                    _uiState.update { it.copy(section = FoodSection.BUY, detailTarget = null) }
                    "Added $item to shopping list."
                }
            }
            refreshFromDisk(isWorking = false)
            _uiState.update { it.copy(voiceStatus = message) }
        }
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return
        _uiState.update { it.copy(input = "", isWorking = true) }

        viewModelScope.launch(Dispatchers.IO) {
            submitToAi(text, "text")
        }
    }

    fun sendVoiceNote(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        _uiState.update { it.copy(input = "", isWorking = true, voiceStatus = "Voice note sent to AI.") }
        viewModelScope.launch(Dispatchers.IO) {
            submitToAi(clean, "voice")
        }
    }

    fun attachReceiptPhoto(uri: Uri?) {
        if (uri == null) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val receiptId = store.insertReceiptCapture(uri.toString(), status = ReceiptStatus.SAVED)
            val sourceMessageId = store.insertMessage(ChatRole.USER, "Attached receipt photo.")
            val memory = store.readMemory()
            val config = liteLlmSettings.read()
            val turn = liteLlmInterpreter.interpretReceiptPhoto(appContext, uri, memory, config)
            if (turn == null) {
                store.updateReceiptStatus(receiptId, rawText = "", status = ReceiptStatus.NEEDS_TEXT)
                store.insertMessage(
                    ChatRole.ASSISTANT,
                    "I saved the receipt photo. This LiteLLM model did not return receipt items, so paste the visible receipt lines and I will draft groceries.",
                )
                refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
            } else {
                store.updateReceiptStatus(
                    receiptId,
                    rawText = turn.reply,
                    status = if (turn.draft == null) ReceiptStatus.NEEDS_TEXT else ReceiptStatus.EXTRACTED,
                )
                store.insertMessage(ChatRole.ASSISTANT, turn.reply)
                refreshFromDisk(
                    pendingDraft = turn.draft,
                    pendingSourceMessageId = sourceMessageId,
                    isWorking = false,
                )
            }
        }
    }

    fun acceptDraft() {
        val draft = _uiState.value.pendingDraft ?: return
        val sourceMessageId = _uiState.value.pendingSourceMessageId
        _uiState.update { it.copy(isWorking = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val summary = store.applyDraft(draft, sourceMessageId)
            store.insertMessage(ChatRole.ASSISTANT, "$summary Local SQLite is now the source of truth.")
            refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
        }
    }

    fun rejectDraft() {
        val draft = _uiState.value.pendingDraft
        val sourceMessageId = _uiState.value.pendingSourceMessageId
        viewModelScope.launch(Dispatchers.IO) {
            if (draft != null) store.rejectDraft(draft, sourceMessageId)
            store.insertMessage(ChatRole.ASSISTANT, "Draft discarded. Keep chatting and I will revise.")
            refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null)
        }
    }

    fun deleteInventory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            store.deleteInventory(id)
            refreshFromDisk()
        }
    }

    fun deleteGrocery(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            store.deleteGrocery(id)
            refreshFromDisk()
        }
    }

    fun markGroceryBought(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = store.markGroceryBought(id)
            store.insertMessage(ChatRole.ASSISTANT, summary)
            refreshFromDisk()
        }
    }

    fun cookRecipe(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = store.cookRecipe(id)
            store.insertMessage(ChatRole.ASSISTANT, summary)
            refreshFromDisk()
        }
    }

    fun addMissingRecipeGroceries(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = store.addMissingRecipeGroceries(id)
            store.insertMessage(ChatRole.ASSISTANT, summary)
            refreshFromDisk()
        }
    }

    fun deleteRecipe(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            store.deleteRecipe(id)
            refreshFromDisk()
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
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            store.updateRecipe(id, title, ingredients, steps, servings, prepMinutes, tags, imageUri)
            refreshFromDisk()
            _uiState.update { it.copy(voiceStatus = "Recipe updated.") }
        }
    }

    fun updateRecipeImage(id: Long, imageUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            store.updateRecipeImage(id, imageUri)
            refreshFromDisk()
            _uiState.update { it.copy(voiceStatus = "Recipe image updated.") }
        }
    }

    fun deleteMealLog(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            store.deleteMealLog(id)
            refreshFromDisk()
        }
    }

    fun refreshHealthStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val label = health.statusLabel()
            _uiState.update { it.copy(healthStatus = label) }
        }
    }

    fun refreshAiStatus() {
        val config = liteLlmSettings.read()
        val label = if (liteLlmInterpreter.isConfigured(config)) {
            "AI: LiteLLM ${config.model}"
        } else {
            "AI: local fallback"
        }
        _uiState.update { it.copy(aiStatus = label, aiConfigForm = config) }
    }

    fun exportLatestMeal() {
        val meal = _uiState.value.memory.mealLogs.firstOrNull() ?: return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val message = when (health.exportMeal(meal)) {
                HealthExportResult.Exported -> "Exported `${meal.title}` to Health Connect nutrition."
                HealthExportResult.MissingPermission -> "Health Connect needs nutrition write permission first."
                HealthExportResult.Unavailable -> "Health Connect is unavailable on this device."
                HealthExportResult.Failed -> "Health Connect export failed. The local meal log is still saved."
            }
            store.insertMessage(ChatRole.ASSISTANT, message)
            refreshHealthStatus()
            refreshFromDisk(isWorking = false)
        }
    }

    private fun submitToAi(text: String, source: String) {
        val sourceMessageId = store.insertMessage(ChatRole.USER, if (source == "voice") "Voice note: $text" else text)
        val memory = store.readMemory()
        val config = liteLlmSettings.read()
        val turn = liteLlmInterpreter.interpret(text, memory, config)
            ?: interpreter.interpret(text, memory)
        store.insertMessage(ChatRole.ASSISTANT, turn.reply)
        refreshFromDisk(
            pendingDraft = turn.draft,
            pendingSourceMessageId = sourceMessageId,
            isWorking = false,
        )
    }

    private fun refreshFromDisk(
        pendingDraft: FoodDraft? = _uiState.value.pendingDraft,
        pendingSourceMessageId: Long? = _uiState.value.pendingSourceMessageId,
        isWorking: Boolean = _uiState.value.isWorking,
    ) {
        val memory = store.readMemory()
        _uiState.update {
            it.copy(
                memory = memory,
                preferencesForm = if (it.preferencesForm == FoodPreferences()) memory.preferences else it.preferencesForm,
                pendingDraft = pendingDraft,
                pendingSourceMessageId = pendingSourceMessageId,
                isWorking = isWorking,
            )
        }
    }
}

data class WonderFoodUiState(
    val memory: FoodMemory = FoodMemory(),
    val input: String = "",
    val section: FoodSection = FoodSection.MEALS,
    val pendingDraft: FoodDraft? = null,
    val pendingSourceMessageId: Long? = null,
    val aiStatus: String = "AI: checking",
    val healthStatus: String = "Checking Health Connect",
    val preferencesForm: FoodPreferences = FoodPreferences(),
    val aiConfigForm: LiteLlmConfig = LiteLlmConfig("", "", LiteLlmSettings.DEFAULT_MODEL),
    val detailTarget: FoodDetailTarget? = null,
    val voiceStatus: String = "",
    val isWorking: Boolean = false,
)

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
    PANTRY("Kitchen"),
    BUY("List"),
    MEALS("Today"),
    RECIPES("Recipes"),
    PREFS("More"),
}

private fun String.toFoodSection(): FoodSection {
    val text = lowercase()
    return when {
        text in listOf("today", "meals", "meal", "calendar", "numbers", "health") -> FoodSection.MEALS
        text in listOf("kitchen", "pantry", "fridge", "freezer", "inventory") -> FoodSection.PANTRY
        text in listOf("buy", "list", "shopping", "groceries", "grocery") -> FoodSection.BUY
        text in listOf("recipe", "recipes", "cook", "cooking") -> FoodSection.RECIPES
        text in listOf("more", "settings", "ai", "data", "taste", "preferences") -> FoodSection.PREFS
        text in listOf("chat", "ask") -> FoodSection.MEALS
        else -> FoodSection.MEALS
    }
}

private fun List<com.wonderfood.app.data.Recipe>.findVoiceRecipe(name: String): com.wonderfood.app.data.Recipe? {
    val text = name.trim()
    if (text.isBlank()) return firstOrNull()
    return firstOrNull { it.title.equals(text, ignoreCase = true) }
        ?: firstOrNull { it.title.contains(text, ignoreCase = true) || text.contains(it.title, ignoreCase = true) }
}
