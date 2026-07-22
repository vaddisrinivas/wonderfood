package com.wonderfood.app.ui.main

import android.content.Context
import android.content.SharedPreferences
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
import com.wonderfood.app.data.CanonicalCartPreviewItem
import com.wonderfood.app.data.CanonicalCartMutationCommandFactory
import com.wonderfood.app.data.CanonicalHouseholdSearchItem
import com.wonderfood.app.data.CanonicalHouseholdUiSummary
import com.wonderfood.app.data.CanonicalKitchenMutationCommandFactory
import com.wonderfood.app.data.CanonicalKitchenPreviewItem
import com.wonderfood.app.data.CanonicalRecentSpendingItem
import com.wonderfood.app.data.CanonicalRecipeMatchItem
import com.wonderfood.app.data.CanonicalSavedRecipeItem
import com.wonderfood.app.data.CanonicalWeekPlanItem
import com.wonderfood.app.data.ChatRole
import com.wonderfood.app.data.ChatMessage
import com.wonderfood.app.data.ChatSourceRef
import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodDraftCommand
import com.wonderfood.app.data.FoodDraftCommandOrigin
import com.wonderfood.app.data.FoodDraftCommandPolicy
import com.wonderfood.app.data.FoodDraftExecutionResult
import com.wonderfood.app.data.FoodDraftNormalizer
import com.wonderfood.app.data.FoodDraftValidator
import com.wonderfood.app.data.FoodEventType
import com.wonderfood.app.data.HouseholdUiMemory
import com.wonderfood.app.data.LifeOsDomain
import com.wonderfood.app.data.LifeOsDomainCatalog
import com.wonderfood.app.data.FoodPreferences
import com.wonderfood.app.data.GroceryItem
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.HouseholdDraftCommandMapper
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.InventoryAction
import com.wonderfood.app.data.InventoryTransaction
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.LinkActionDraft
import com.wonderfood.app.data.MealLog
import com.wonderfood.app.data.MealPlan
import com.wonderfood.app.data.MealPlanEntry
import com.wonderfood.app.data.MealPlanStatus
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntryStatus
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.Recipe
import com.wonderfood.app.data.ReceiptStatus
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.ReceiptCapture
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
import com.wonderfood.app.sync.CanonicalHouseholdSnapshotExporter
import com.wonderfood.app.sync.GoogleAccountProfile
import com.wonderfood.app.sync.GoogleDriveAccess
import com.wonderfood.app.sync.GoogleDriveAppDataGateway
import com.wonderfood.app.sync.GoogleDriveBackupDownload
import com.wonderfood.app.sync.GoogleSheetsGateway
import com.wonderfood.app.sync.GoogleSheetsV4InboundWorkspaceImporter
import com.wonderfood.app.sync.V4InboundNeedsReviewDiagnostic
import com.wonderfood.app.sync.V4InboundWorkspaceImportResult
import com.wonderfood.app.sync.NotionGateway
import com.wonderfood.app.sync.PostgresGateway
import com.wonderfood.app.sync.WonderFoodBackupGateway
import com.wonderfood.app.sync.WonderFoodCsvGateway
import com.wonderfood.app.sync.WonderFoodCsvImport
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
import com.wonderfood.core.data.HouseholdRepositories
import com.wonderfood.core.data.room.WonderFoodDatabaseFactory
import com.wonderfood.core.engine.HouseholdCommandExecutor
import com.wonderfood.core.engine.HouseholdCommandExecutionResult
import com.wonderfood.core.model.WonderFoodSnapshot
import com.wonderfood.core.engine.HouseholdCommand
import com.wonderfood.core.model.household.Attachment
import com.wonderfood.core.model.household.AttachmentKind
import com.wonderfood.core.model.household.CalendarDate
import com.wonderfood.core.model.household.ChangeProposal
import com.wonderfood.core.model.household.CommandId
import com.wonderfood.core.model.household.CommandIntent
import com.wonderfood.core.model.household.CommandRecord
import com.wonderfood.core.model.household.DataHomeKind
import com.wonderfood.core.model.household.DecimalAmount
import com.wonderfood.core.model.household.EntityMetadata
import com.wonderfood.core.model.household.Household
import com.wonderfood.core.model.household.HouseholdWorkspaceContract
import com.wonderfood.core.model.household.EntityId
import com.wonderfood.core.model.household.LatestSafetySnapshot
import com.wonderfood.core.model.household.PayloadHash
import com.wonderfood.core.model.household.Purchase
import com.wonderfood.core.model.household.PurchaseStatus
import com.wonderfood.core.model.household.Quantity
import com.wonderfood.core.model.household.QuantityUnit
import com.wonderfood.core.model.household.ReviewState
import com.wonderfood.core.model.household.SourceKind
import com.wonderfood.core.model.household.SourceRef
import com.wonderfood.core.model.household.UtcTimestamp
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch

class MainScreenViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private var householdDatabase = WonderFoodDatabaseFactory.create(appContext)
    private var householdRepository = HouseholdRepositories.room(householdDatabase)
    private var householdCommandExecutor = HouseholdCommandExecutor(householdRepository)
    private val localChatLock = Any()
    private val localChatMessages = mutableListOf<ChatMessage>()
    private var localChatCounter = 1L
    private var localMessageCounter = 0L
    private val householdDraftCommandMapper = HouseholdDraftCommandMapper(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
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
    private val backendPrefs = appContext.getSharedPreferences(BACKEND_PREFS_NAME, Context.MODE_PRIVATE)
    private val directActionPrefs = appContext.getSharedPreferences(DIRECT_ACTION_PREFS_NAME, Context.MODE_PRIVATE)
    private val googleSyncPrefs = appContext.getSharedPreferences(GOOGLE_SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    private val backendPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshBackendHome()
    }
    private val shellPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_WORKSPACE_CONFLICT_INBOX -> {
                _uiState.update { it.copy(workspaceConflictInbox = readWorkspaceConflictInbox()) }
            }
            KEY_TEMPLATE_NOTION_URL,
            KEY_TEMPLATE_SHEETS_URL,
            -> refreshBackendHome()
        }
    }
    private var pendingUndo: PendingUndo? = null
    private var pendingDraftOrigin: FoodDraftCommandOrigin = FoodDraftCommandOrigin.AI_REVIEW
    private var preferenceAutoSaveJob: Job? = null
    private var backendSnapshotSyncJob: Job? = null
    private var canonicalSearchJob: Job? = null
    private val backendRefreshGeneration = AtomicInteger(0)
    private var pendingGoogleRestoreDownload: GoogleDriveBackupDownload? = null
    private var pendingProviderImportCommands: List<HouseholdCommand> = emptyList()
    private var pendingProviderImportDiagnostics: List<V4InboundNeedsReviewDiagnostic> = emptyList()
    private var pendingProviderImportLabel: String = "Provider data"

    private val _uiState = MutableStateFlow(
        WonderFoodUiState(
            section = readSelectedSection(),
            workspaceConflictInbox = readWorkspaceConflictInbox(),
            lifeOsDomains = LifeOsDomainCatalog.bundled(appContext).domains,
            selectedLifeOsDomainId = readSelectedLifeOsDomainId(),
        ),
    )
    val uiState: StateFlow<WonderFoodUiState> = _uiState.asStateFlow()

    val healthPermissionContract = health.permissionContract()
    val healthPermissions: Set<String> = health.healthPermissions
    val healthWritePermissions: Set<String> = health.nutritionWritePermissions
    private val localChatSeedMessage = "Ask naturally. I can answer with sources from your phone, LifeOS Notion, Sheets, MCP schema, and Health Connect when connected; changes stay review-only until you accept."

    private fun seedLocalChatIfNeeded() {
        synchronized(localChatLock) {
            if (localChatMessages.isNotEmpty()) return@synchronized
            val now = System.currentTimeMillis()
            localMessageCounter -= 1
            localChatMessages.add(
                ChatMessage(
                    id = localMessageCounter,
                    role = ChatRole.ASSISTANT,
                    body = localChatSeedMessage,
                    createdAtMillis = now,
                    chatId = localChatCounter,
                ),
            )
            saveLocalChatMessagesLocked()
        }
    }

    private fun insertLocalChatMessage(
        role: ChatRole,
        body: String,
        chatId: Long = localChatCounter,
        sources: List<ChatSourceRef> = emptyList(),
    ): Long {
        val clean = body.trim()
        if (clean.isBlank()) return -1L
        val now = System.currentTimeMillis()
        return synchronized(localChatLock) {
            localMessageCounter -= 1
            val message = ChatMessage(
                id = localMessageCounter,
                role = role,
                body = clean,
                createdAtMillis = now,
                chatId = chatId,
                sources = sources,
            )
            localChatMessages.add(message)
            saveLocalChatMessagesLocked()
            message.id
        }
    }

    private fun startLocalNewChat() {
        synchronized(localChatLock) {
            localChatCounter += 1
            insertLocalChatMessage(
                role = ChatRole.ASSISTANT,
                body = "New chat started. Your kitchen, recipes, groceries, plans, and settings are still saved.",
                chatId = localChatCounter,
            )
        }
    }

    private fun clearLocalChatHistory() {
        synchronized(localChatLock) {
            localChatCounter = 1L
            localMessageCounter = 0L
            localChatMessages.clear()
            insertLocalChatMessage(
                role = ChatRole.ASSISTANT,
                body = "Chat memory reset. Your kitchen, recipes, groceries, plans, and settings are still saved.",
                chatId = localChatCounter,
            )
        }
    }

    private fun loadLocalChatMessages() {
        synchronized(localChatLock) {
            val raw = shellPrefs.getString(KEY_LOCAL_CHAT_HISTORY, null).orEmpty()
            if (raw.isBlank()) return@synchronized
            val loaded = runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val role = runCatching { ChatRole.valueOf(item.optString("role")) }.getOrNull() ?: continue
                        val body = item.optString("body").trim()
                        if (body.isBlank()) continue
                        add(
                            ChatMessage(
                                id = item.optLong("id"),
                                role = role,
                                body = body,
                                createdAtMillis = item.optLong("createdAtMillis", System.currentTimeMillis()),
                                chatId = item.optLong("chatId", 1L).coerceAtLeast(1L),
                                sources = item.optJSONArray("sources").toChatSourceRefs(),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
            if (loaded.isEmpty()) return@synchronized
            localChatMessages.clear()
            localChatMessages.addAll(loaded.sortedWith(compareBy<ChatMessage> { it.chatId }.thenBy { it.createdAtMillis }.thenBy { it.id }))
            localChatCounter = localChatMessages.maxOfOrNull { it.chatId } ?: 1L
            localMessageCounter = localChatMessages.minOfOrNull { it.id }?.coerceAtMost(0L) ?: 0L
        }
    }

    private fun saveLocalChatMessagesLocked() {
        val array = JSONArray()
        localChatMessages
            .sortedWith(compareBy<ChatMessage> { it.chatId }.thenBy { it.createdAtMillis }.thenBy { it.id })
            .takeLast(240)
            .forEach { message ->
                array.put(
                    JSONObject()
                        .put("id", message.id)
                        .put("role", message.role.name)
                        .put("body", message.body)
                        .put("createdAtMillis", message.createdAtMillis)
                        .put("chatId", message.chatId)
                        .put("sources", message.sources.toJsonArray()),
                )
            }
        shellPrefs.edit { putString(KEY_LOCAL_CHAT_HISTORY, array.toString()) }
    }

    private fun JSONArray?.toChatSourceRefs(): List<ChatSourceRef> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                val detail = item.optString("detail").trim()
                if (title.isBlank() || detail.isBlank()) continue
                add(
                    ChatSourceRef(
                        title = title.take(80),
                        detail = detail.take(160),
                        quote = item.optString("quote").trim().take(240),
                        uri = item.optString("uri").trim().take(512),
                    ),
                )
            }
        }
    }

    private fun List<ChatSourceRef>.toJsonArray(): JSONArray {
        val array = JSONArray()
        take(8).forEach { source ->
            array.put(
                JSONObject()
                    .put("title", source.title)
                    .put("detail", source.detail)
                    .put("quote", source.quote)
                    .put("uri", source.uri),
            )
        }
        return array
    }

    private fun chatSourcesForTurn(
        memory: HouseholdUiMemory,
        backendHome: BackendHomeUiState,
        activeDomain: LifeOsDomain?,
        status: String,
        promptContext: String?,
        draft: FoodDraft?,
        providerSources: List<ChatSourceRef> = emptyList(),
    ): List<ChatSourceRef> = buildList {
        providerSources.take(4).forEach(::add)
        lifeOsSourcePackSources(memory, backendHome, activeDomain, status).forEach(::add)
        add(
            ChatSourceRef(
                title = "Active data home",
                detail = backendHome.label,
                quote = backendHome.detail,
                uri = backendHome.dataPlaneUrl.ifBlank { backendHome.sheetUrl },
            ),
        )
        if (backendHome.templateNotionUrl.isNotBlank()) {
            add(
                ChatSourceRef(
                    title = "LifeOS Notion",
                    detail = "Human dashboard",
                    quote = "Relations, rollups, dashboards, quests, journals, and template QA.",
                    uri = backendHome.templateNotionUrl,
                ),
            )
        }
        if (backendHome.templateSheetsUrl.isNotBlank()) {
            add(
                ChatSourceRef(
                    title = "LifeOS Sheets",
                    detail = "Workbook mirror",
                    quote = "Auditable rows, formulas, imports, exports, conflicts, and schema parity.",
                    uri = backendHome.templateSheetsUrl,
                ),
            )
        }
        add(
            ChatSourceRef(
                title = "LifeOS package runtime",
                detail = "Bundled domain catalog",
                quote = "Food is live. Health is companion. Plants is template-ready. New domains start as config.",
                uri = "asset://lifeos/domain-catalog.v1.json",
            ),
        )
        add(
            ChatSourceRef(
                title = "Template health",
                detail = "Template QA",
                quote = "Guard @now, sample/empty parity, relations, rollups, and source visibility.",
                uri = "notion://template-health",
            ),
        )
        add(
            ChatSourceRef(
                title = "AI route",
                detail = status.ifBlank { "Local deterministic parser" },
                quote = if (draft == null) "No draft was staged." else "${draft.title}: ${draft.rows.take(3).joinToString("; ")}",
            ),
        )
        promptContext
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.let { line ->
                add(ChatSourceRef("Current app page", line.take(120), promptContext.lineSequence().take(4).joinToString(" ").take(220)))
            }
        if (memory.inventory.isNotEmpty()) {
            add(
                ChatSourceRef(
                    title = "Kitchen",
                    detail = "${memory.inventory.size} food item${memory.inventory.size.pluralWord}",
                    quote = memory.inventory.take(6).joinToString("; ") { "${it.name} (${it.zone.label}${if (it.quantity.isBlank()) "" else ", ${it.quantity}"})" },
                ),
            )
        }
        if (memory.groceries.isNotEmpty()) {
            add(
                ChatSourceRef(
                    title = "Shopping",
                    detail = "${memory.groceries.count { it.status == GroceryStatus.NEEDED }} to buy, ${memory.groceries.count { it.status == GroceryStatus.BOUGHT }} bought",
                    quote = memory.groceries.take(6).joinToString("; ") { "${it.name} (${it.status.name.lowercase()})" },
                ),
            )
        }
        if (memory.recipes.isNotEmpty()) {
            add(
                ChatSourceRef(
                    title = "Recipes",
                    detail = "${memory.recipes.size} saved recipe${memory.recipes.size.pluralWord}",
                    quote = memory.recipes.take(5).joinToString("; ") { it.title },
                ),
            )
        }
        if (memory.mealPlanEntries.isNotEmpty() || memory.mealLogs.isNotEmpty()) {
            add(
                ChatSourceRef(
                    title = "Meals and plans",
                    detail = "${memory.mealLogs.size} logs, ${memory.mealPlanEntries.size} planned entries",
                    quote = (memory.mealPlanEntries.take(4).map { "${it.slot.label}: ${it.title}" } + memory.mealLogs.take(3).map { "${it.mealSlot.label}: ${it.title}" })
                        .take(6)
                        .joinToString("; "),
                ),
            )
        }
        if (memory.receipts.isNotEmpty()) {
            add(
                ChatSourceRef(
                    title = "Receipts",
                    detail = "${memory.receipts.size} receipt capture${memory.receipts.size.pluralWord}",
                    quote = memory.receipts.take(3).joinToString("; ") { it.rawText.lineSequence().firstOrNull().orEmpty().ifBlank { it.status.name.lowercase() } },
                ),
            )
        }
        if (!memory.preferences.isEmpty) {
            add(
                ChatSourceRef(
                    title = "Preferences",
                    detail = "Diet, allergy, store, nutrition, and assistant instruction fields",
                    quote = listOf(
                        memory.preferences.dietStyle.takeIf { it.isNotBlank() }?.let { "Diet: $it" },
                        memory.preferences.allergies.takeIf { it.isNotBlank() }?.let { "Allergies: $it" },
                        memory.preferences.dislikes.takeIf { it.isNotBlank() }?.let { "Dislikes: $it" },
                        memory.preferences.preferredStaples.takeIf { it.isNotBlank() }?.let { "Staples: $it" },
                    ).filterNotNull().joinToString("; "),
                ),
            )
        }
    }.distinctBy { it.title }.take(8)

    private fun lifeOsSourcePackSources(
        memory: HouseholdUiMemory,
        backendHome: BackendHomeUiState,
        activeDomain: LifeOsDomain?,
        status: String,
    ): List<ChatSourceRef> {
        val domainLabel = activeDomain?.label.orEmpty().ifBlank { "Food" }
        val handles = buildList {
            add("Android local snapshot")
            backendHome.templateNotionUrl.takeIf { it.isNotBlank() }?.let { add("LifeOS Notion") }
            backendHome.templateSheetsUrl.takeIf { it.isNotBlank() }?.let { add("LifeOS Sheets") }
            add("MCP domain catalog")
            add("Template health")
        }.joinToString(" → ")
        val localCounts = listOf(
            "kitchen ${memory.inventory.size}",
            "shopping ${memory.groceries.size}",
            "recipes ${memory.recipes.size}",
            "meal logs ${memory.mealLogs.size}",
            "plans ${memory.mealPlanEntries.size}",
            "receipts ${memory.receipts.size}",
        ).joinToString(", ")
        val health = activeDomain?.templateHealth.orEmpty().take(3).joinToString("; ").ifBlank {
            "schema parity, source links, and visible relation/rollup checks"
        }
        val sync = activeDomain?.syncLoop.orEmpty().take(4).joinToString(" → ").ifBlank {
            "Notion → Sheets → Android → MCP/GPT"
        }
        return listOf(
            ChatSourceRef(
                title = "LifeOS source pack",
                detail = "$domainLabel across app, Notion, Sheets, MCP",
                quote = "Handles: $handles. Local snapshot: $localCounts.",
                uri = backendHome.templateNotionUrl.ifBlank {
                    backendHome.templateSheetsUrl.ifBlank { "asset://lifeos/domain-catalog.v1.json" }
                },
            ),
            ChatSourceRef(
                title = "Sync health",
                detail = status.ifBlank { "Schema/source parity check" },
                quote = "Loop: $sync. Health: $health.",
                uri = "wonderfood://lifeos/domain-catalog-v1",
            ),
        )
    }

    private fun lifeOsSourcePackPromptContext(
        memory: HouseholdUiMemory,
        backendHome: BackendHomeUiState,
        activeDomain: LifeOsDomain?,
    ): String {
        val domainLabel = activeDomain?.label.orEmpty().ifBlank { "Food" }
        val counts = listOf(
            "inventory=${memory.inventory.size}",
            "groceries=${memory.groceries.size}",
            "recipes=${memory.recipes.size}",
            "meal_logs=${memory.mealLogs.size}",
            "meal_plans=${memory.mealPlans.size}",
            "plan_entries=${memory.mealPlanEntries.size}",
            "receipts=${memory.receipts.size}",
        ).joinToString(", ")
        val handles = buildList {
            add("[App snapshot]: on-device canonical state; $counts")
            backendHome.templateNotionUrl.takeIf { it.isNotBlank() }?.let { url ->
                add("[LifeOS Notion]: $url")
            }
            backendHome.templateSheetsUrl.takeIf { it.isNotBlank() }?.let { url ->
                add("[LifeOS Sheets]: $url")
            }
            add("[MCP schema]: wonderfood://lifeos/domain-catalog-v1")
            add("[Template health]: ${activeDomain?.templateHealth.orEmpty().take(4).joinToString("; ").ifBlank { "@now guard, sample/empty parity, relation/rollup checks, visible source cards" }}")
        }
        val syncLoop = activeDomain?.syncLoop.orEmpty().take(4).joinToString(" -> ").ifBlank {
            "Notion -> Sheets -> Android -> MCP/GPT"
        }
        val operatingLoops = activeDomain?.operatingLoops.orEmpty().take(6).joinToString("; ").ifBlank {
            "Food quests, good habits, bad-habit reviews, boss fights, daily journal, P.A.R.A."
        }
        return buildString {
            appendLine("Active LifeOS package: $domainLabel.")
            appendLine("Source handles:")
            handles.forEach { appendLine("- $it") }
            appendLine("Sync loop: $syncLoop.")
            appendLine("Borrowed template loops: $operatingLoops.")
            append("Instruction: when a user asks about LifeOS, sources, Notion, Sheets, MCP, schema, or sync, answer from these handles explicitly.")
        }
    }

    private fun sourcePackAnswer(sourceContext: String): String =
        buildString {
            val sync = sourceContext.lineSequence()
                .firstOrNull { it.startsWith("Sync loop:") }
                ?.removePrefix("Sync loop:")
                ?.trim()
                ?.trimEnd('.')
                ?: "Notion -> Sheets -> Android -> MCP/GPT"
            appendLine("Here is the current LifeOS source pack:")
            appendLine()
            appendLine("| Handle | What I use it for |")
            appendLine("|---|---|")
            appendLine("| [App snapshot] | Phone state: kitchen, shopping, recipes, meals, receipts, preferences, Health Connect. |")
            appendLine("| [LifeOS Notion] | Human dashboard: relations, rollups, quests, habits, journal, template QA. |")
            appendLine("| [LifeOS Sheets] | Spreadsheet mirror: schema rows, import/export checks, formulas, conflicts. |")
            appendLine("| [MCP schema] | Headless/GPT bridge: domain catalog, validators, review-only proposals. |")
            appendLine("| [Template health] | @now guard, sample/empty parity, relation and rollup checks. |")
            appendLine()
            appendLine("Sync path:")
            sync.split(" -> ")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(5)
                .forEachIndexed { index, hop ->
                    appendLine("${index + 1}. $hop")
                }
            append("Any save still becomes a reviewable draft first.")
        }

    private fun localChatMessagesForMemory(): List<ChatMessage> =
        synchronized(localChatLock) {
            localChatMessages
                .sortedWith(compareBy<ChatMessage> { it.createdAtMillis }.thenBy { it.id })
                .map {
                    it.copy()
                }
        }

    private fun canonicalSafeSourceMessageId(sourceMessageId: Long?): Long? =
        sourceMessageId.takeIf { it != null && it > 0L }

    init {
        backendPrefs.registerOnSharedPreferenceChangeListener(backendPrefsListener)
        shellPrefs.registerOnSharedPreferenceChangeListener(shellPrefsListener)
        viewModelScope.launch(Dispatchers.IO) {
            loadLocalChatMessages()
            seedLocalChatIfNeeded()
            ensureCanonicalHousehold()
            refreshFromDisk()
            refreshCanonicalSummary()
            refreshAiStatus()
            refreshHealthStatus()
            refreshSyncStatus()
            refreshBackendHome()
        }
    }

    override fun onCleared() {
        backendPrefs.unregisterOnSharedPreferenceChangeListener(backendPrefsListener)
        shellPrefs.unregisterOnSharedPreferenceChangeListener(shellPrefsListener)
        super.onCleared()
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun selectSection(section: FoodSection) {
        setSection(section)
    }

    fun selectLifeOsDomain(domainId: String) {
        val clean = domainId.trim().lowercase()
        val domain = _uiState.value.lifeOsDomains.firstOrNull { it.id == clean } ?: return
        shellPrefs.edit { putString(KEY_SELECTED_LIFEOS_DOMAIN, domain.id) }
        _uiState.update { it.copy(selectedLifeOsDomainId = domain.id) }
        showFeedback("${domain.label} package selected. Food remains the active native workspace until a package maps its tabs to app screens.")
    }

    fun openDetail(target: FoodDetailTarget) {
        _uiState.update { it.copy(detailTarget = target) }
    }

    fun updateSearchQuery(query: String) {
        val clean = query.trim()
        canonicalSearchJob?.cancel()
        if (clean.isBlank()) {
            _uiState.update {
                it.copy(
                    canonicalSearchQuery = "",
                    canonicalSearchItems = emptyList(),
                )
            }
            return
        }
        _uiState.update { it.copy(canonicalSearchQuery = clean) }
        canonicalSearchJob = viewModelScope.launch(Dispatchers.IO) {
            ensureCanonicalHousehold()
            val results = householdRepository
                .searchItems(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID, clean)
                .map(CanonicalHouseholdSearchItem::from)
            _uiState.update { current ->
                if (current.canonicalSearchQuery == clean) {
                    current.copy(canonicalSearchItems = results)
                } else {
                    current
                }
            }
        }
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
            action.canonicalCommands?.let { commands ->
                val failures = executeCanonicalHouseholdCommands(commands)
                if (failures.isEmpty()) {
                    refreshCanonicalSummary()
                    queueBackendSnapshotSync("canonical_undo")
                    showFeedback("Undid ${action.label}.")
                } else {
                    showFeedback("Undo failed: ${failures.joinToString("; ")}")
                }
                return@launch
            }
            refreshFromDisk()
            showFeedback("Undo for ${action.label} needs canonical recovery history.")
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
            if (announce) {
                insertLocalChatMessage(ChatRole.ASSISTANT, "Saved your food preferences and AI instructions.")
            }
            val memory = canonicalRuntimeMemory(includeLegacyChat = true)
            _uiState.update {
                it.copy(
                    memory = memory.copy(preferences = preferences),
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
            insertLocalChatMessage(ChatRole.ASSISTANT, "Saved primary and fallback AI provider settings.")
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
                WonderFoodVoiceAction.PROOF_PACK -> {
                    val notionUrl = command.templateNotionUrl.takeIf(::isTrustedNotionUrl).orEmpty()
                    val sheetsUrl = command.templateSheetsUrl.takeIf(::isTrustedSheetsUrl).orEmpty()
                    if (notionUrl.isBlank() && sheetsUrl.isBlank()) {
                        "Template link was ignored: expected HTTPS Notion and/or Google Sheets URLs."
                    } else {
                        shellPrefs.edit {
                            if (notionUrl.isNotBlank()) putString(KEY_TEMPLATE_NOTION_URL, notionUrl)
                            if (sheetsUrl.isNotBlank()) putString(KEY_TEMPLATE_SHEETS_URL, sheetsUrl)
                            putString(KEY_BACKEND_SYNC_STATUS, "LifeOS Notion and Sheets template links saved on this device.")
                        }
                        setSection(FoodSection.TODAY)
                        refreshBackendHome()
                        "LifeOS template links saved. Open Settings → Data home to see Notion and Sheets links."
                    }
                }
                WonderFoodVoiceAction.LOG_WATER -> {
                    val amount = command.amount?.toInt() ?: 250
                    val (eventId, eventSummary) = recordCanonicalActionEvent(
                        eventType = "WATER",
                        label = "Google Assistant water log",
                        note = "Logged $amount ml water.",
                        sourceLabel = "google_assistant",
                        payload = mapOf("amount_ml" to amount.toString()),
                    )
                    val status = recordCanonicalMutationApplied("log_event", eventSummary)
                    registerUndo(UndoKind.EVENT, eventId, "water log", status)
                    status
                }
                WonderFoodVoiceAction.START_SHOPPING -> {
                    setSection(FoodSection.SHOP)
                    val (eventId, eventSummary) = recordCanonicalActionEvent(
                        eventType = "SHOP",
                        label = "Google Assistant start shopping",
                        note = "Started shopping.",
                        sourceLabel = "google_assistant",
                        payload = mapOf("event_type" to FoodEventType.SHOP.name, "note" to "Started shopping"),
                    )
                    val status = recordCanonicalMutationApplied("log_event", eventSummary)
                    registerUndo(UndoKind.EVENT, eventId, "shopping start", status)
                    status
                }
                WonderFoodVoiceAction.DONE_SHOPPING -> {
                    setSection(FoodSection.SHOP)
                    val (eventId, eventSummary) = recordCanonicalActionEvent(
                        eventType = "SHOP",
                        label = "Google Assistant finish shopping",
                        note = "Finished shopping.",
                        sourceLabel = "google_assistant",
                        payload = mapOf("event_type" to FoodEventType.SHOP.name, "note" to "Finished shopping"),
                    )
                    val status = recordCanonicalMutationApplied("log_event", eventSummary)
                    registerUndo(UndoKind.EVENT, eventId, "shopping finish", status)
                    status
                }
                WonderFoodVoiceAction.START_COOKING -> {
                    val recipe = canonicalRuntimeMemory(includeLegacyChat = false).recipes.findVoiceRecipe(command.recipeName)
                    val recipeLabel = recipe?.title ?: command.recipeName.ifBlank { "recipe" }
                    _uiState.update {
                        it.copy(
                            section = FoodSection.RECIPES,
                            detailTarget = recipe?.let { found -> FoodDetailTarget(FoodDetailKind.RECIPE, id = found.id) },
                        )
                    }
                    saveSelectedSection(FoodSection.RECIPES)
                    val (eventId, eventSummary) = recordCanonicalActionEvent(
                        eventType = "COOK",
                        label = "Google Assistant start cooking",
                        note = "Started cooking $recipeLabel.",
                        sourceLabel = "google_assistant",
                        payload = mapOf(
                            "event_type" to FoodEventType.COOK.name,
                            "recipe_id" to recipe?.id?.toString(),
                            "note" to "Started cooking $recipeLabel",
                        ),
                    )
                    val status = recordCanonicalMutationApplied("log_event", eventSummary)
                    registerUndo(UndoKind.EVENT, eventId, "cooking start", status)
                    status
                }
                WonderFoodVoiceAction.DONE_COOKING -> {
                    val recipe = canonicalRuntimeMemory(includeLegacyChat = false).recipes.findVoiceRecipe(command.recipeName)
                    if (recipe == null) {
                        setSection(FoodSection.RECIPES)
                        val (eventId, eventSummary) = recordCanonicalActionEvent(
                            eventType = "COOK",
                            label = "Google Assistant finish cooking",
                            note = "Finished cooking.",
                            sourceLabel = "google_assistant",
                            payload = mapOf(
                                "event_type" to FoodEventType.COOK.name,
                                "note" to "Finished cooking ${command.recipeName}",
                            ),
                        )
                        val status = recordCanonicalMutationApplied("log_event", eventSummary)
                        registerUndo(UndoKind.EVENT, eventId, "cooking finish", status)
                        status
                    } else {
                        val recipeId = canonicalRecipeIdForLegacyRecipeId(recipe.id)
                        if (recipeId == null) {
                            "Could not log ${recipe.title}: canonical recipe not found."
                        } else {
                            cookCanonicalRecipe(recipeId)
                        }
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
                insertLocalChatMessage(ChatRole.USER, text)
                insertLocalChatMessage(ChatRole.ASSISTANT, pageEdit)
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
                insertLocalChatMessage(ChatRole.USER, "Voice note: $clean")
                insertLocalChatMessage(ChatRole.ASSISTANT, pageEdit)
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
            val receiptId = stableLegacyId("receipt:${privateUri}:${receiptEvidence.take(256)}")
            val initialStatus = if (capture.status == FoodCaptureStatus.STAGED) ReceiptStatus.SAVED else ReceiptStatus.NEEDS_TEXT
            val captureSummary = upsertCanonicalReceiptCapture(
                receiptId = receiptId,
                imageUri = privateUri.toString(),
                rawText = receiptEvidence,
                status = initialStatus,
            )
            val sourceMessageId = insertLocalChatMessage(
                ChatRole.USER,
                buildString {
                    append("Attached receipt photo.")
                    if (cleanNote.isNotBlank()) append("\nReceipt note: $cleanNote")
                },
            )
            val memory = canonicalRuntimeMemory(includeLegacyChat = true)
            val configs = liteLlmSettings.readAll()
            val turn = if (capture.status == FoodCaptureStatus.STAGED) {
                interpretReceiptPhotoWithVisibleRetries(privateUri, memory, configs, cleanNote)
            } else {
                null
            }
            if (turn == null) {
                val updateSummary = upsertCanonicalReceiptCapture(
                    receiptId = receiptId,
                    imageUri = privateUri.toString(),
                    rawText = receiptEvidence,
                    status = ReceiptStatus.NEEDS_TEXT,
                )
                recordCanonicalMutationApplied("update_receipt", updateSummary)
                insertLocalChatMessage(
                    ChatRole.ASSISTANT,
                    "$captureSummary OCR or AI did not finish, so paste the visible lines or retry the capture when the provider is available.",
                )
                refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
            } else {
                val linkedDraft = turn.draft?.linkReceipt(receiptId)
                val receiptStatus = if (linkedDraft == null) ReceiptStatus.NEEDS_TEXT else ReceiptStatus.EXTRACTED
                val updateSummary = upsertCanonicalReceiptCapture(
                    receiptId = receiptId,
                    imageUri = privateUri.toString(),
                    rawText = listOfNotNull(
                        cleanNote.takeIf { it.isNotBlank() }?.let { "User note:\n$it" },
                        "Extraction:\n${turn.reply}",
                        linkedDraft?.receiptAuditText(),
                    ).joinToString("\n\n"),
                    status = receiptStatus,
                )
                recordCanonicalMutationApplied("update_receipt", updateSummary)
                insertLocalChatMessage(ChatRole.ASSISTANT, turn.reply)
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
        val origin = pendingDraftOrigin
        _uiState.update { it.copy(isWorking = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val result = if (draft.canApplyDirectlyToCanonicalHousehold(origin)) {
                executeCanonicalDraftCommand(draft, sourceMessageId, origin)
            } else {
                executeDraftCommand(draft, sourceMessageId, origin)
            }
            when (result) {
                is FoodDraftExecutionResult.Applied -> {
                    insertLocalChatMessage(ChatRole.ASSISTANT, result.summary)
                    pendingDraftOrigin = FoodDraftCommandOrigin.AI_REVIEW
                    refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
                    _uiState.update { it.copy(aiAttemptStatus = "", voiceStatus = "") }
                }
                is FoodDraftExecutionResult.Rejected -> {
                    val summary = "Could not save this proposal: ${result.errors.joinToString("; ")}"
                    insertLocalChatMessage(ChatRole.ASSISTANT, summary)
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
        viewModelScope.launch(Dispatchers.IO) {
            if (draft != null) {
                pendingDraftOrigin = FoodDraftCommandOrigin.AI_REVIEW
            }
            insertLocalChatMessage(ChatRole.ASSISTANT, "Draft discarded. Keep chatting and I will revise.")
            refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null)
            _uiState.update { it.copy(aiAttemptStatus = "", voiceStatus = "") }
        }
    }

    fun startNewChat() {
        _uiState.update { it.copy(input = "", isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            startLocalNewChat()
            refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
            _uiState.update { it.copy(voiceStatus = "New chat started.") }
        }
    }

    fun clearChatHistory() {
        _uiState.update { it.copy(input = "", isWorking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            clearLocalChatHistory()
            refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
            _uiState.update { it.copy(voiceStatus = "Chat memory reset.") }
        }
    }

    fun deleteInventory(id: Long) {
        val label = _uiState.value.memory.inventory.firstOrNull { it.id == id }?.name ?: "kitchen item"
        viewModelScope.launch(Dispatchers.IO) {
            val itemId = canonicalItemIdForLegacyInventoryId(id)
            if (itemId == null) {
                showFeedback("Could not archive $label: canonical kitchen row not found.")
                return@launch
            }
            val result = applyCanonicalKitchenItemMutation(itemId.value, "Archived") { snapshot, canonicalId, now ->
                CanonicalKitchenMutationCommandFactory.archiveItem(snapshot, canonicalId, now)
            }
            refreshFromDisk(isWorking = false)
            showFeedback(result)
        }
    }

    fun deleteGrocery(id: Long) {
        val label = _uiState.value.memory.groceries.firstOrNull { it.id == id }?.name ?: "grocery item"
        viewModelScope.launch(Dispatchers.IO) {
            val lineId = canonicalShoppingLineIdForLegacyGroceryId(id)
            if (lineId == null) {
                showFeedback("Could not archive $label: canonical cart row not found.")
                return@launch
            }
            val result = applyCanonicalCartLineMutation(lineId.value, "Archived") { line, now ->
                CanonicalCartMutationCommandFactory.archive(line, now)
            }
            refreshFromDisk()
            showFeedback(result)
        }
    }

    fun markGroceryBought(id: Long) {
        val label = _uiState.value.memory.groceries.firstOrNull { it.id == id }?.name ?: "grocery item"
        viewModelScope.launch(Dispatchers.IO) {
            val lineId = canonicalShoppingLineIdForLegacyGroceryId(id)
            if (lineId == null) {
                showFeedback("Could not buy $label: canonical cart row not found.")
                return@launch
            }
            val summary = applyCanonicalCartLineMutation(lineId.value, "Bought") { line, now ->
                CanonicalCartMutationCommandFactory.markPurchased(line, now)
            }
            insertLocalChatMessage(ChatRole.ASSISTANT, summary)
            refreshFromDisk()
            showFeedback(summary)
        }
    }

    fun markCanonicalCartLinePurchased(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = applyCanonicalCartLineMutation(id, "Bought") { line, now ->
                CanonicalCartMutationCommandFactory.markPurchased(line, now)
            }
            showFeedback(result)
        }
    }

    fun archiveCanonicalCartLine(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = applyCanonicalCartLineMutation(id, "Archived") { line, now ->
                CanonicalCartMutationCommandFactory.archive(line, now)
            }
            showFeedback(result)
        }
    }

    fun addCanonicalKitchenItemToCart(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ensureCanonicalHousehold()
            val itemId = runCatching { EntityId(id) }.getOrNull()
            if (itemId == null) {
                showFeedback("Could not update kitchen row: invalid canonical id.")
                return@launch
            }
            val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            val item = snapshot?.items?.firstOrNull { it.metadata.id == itemId }
            if (snapshot == null || item == null) {
                showFeedback("Could not add kitchen item to cart: canonical kitchen row not found.")
                return@launch
            }
            val now = UtcTimestamp(System.currentTimeMillis())
            val command = CanonicalKitchenMutationCommandFactory.addToCart(snapshot, itemId, now)
            if (command == null) {
                showFeedback("Could not add ${item.name} to cart.")
                return@launch
            }
            val failures = executeCanonicalHouseholdCommands(listOf(command))
            if (failures.isNotEmpty()) {
                showFeedback("Could not add ${item.name} to cart.")
                return@launch
            }
            refreshCanonicalSummary()
            queueBackendSnapshotSync("canonical_kitchen")
            registerCanonicalUndo(
                label = "add ${item.name} to cart",
                message = "Added ${item.name} to cart.",
                commands = listOf(CanonicalCartMutationCommandFactory.archive(command.line, UtcTimestamp(System.currentTimeMillis()))),
            )
        }
    }

    fun archiveCanonicalKitchenItem(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ensureCanonicalHousehold()
            val itemId = runCatching { EntityId(id) }.getOrNull()
            if (itemId == null) {
                showFeedback("Could not update kitchen row: invalid canonical id.")
                return@launch
            }
            val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            val item = snapshot?.items?.firstOrNull { it.metadata.id == itemId }
            if (snapshot == null || item == null) {
                showFeedback("Could not archive kitchen item: canonical kitchen row not found.")
                return@launch
            }
            val activeLots = snapshot.inventoryLots.filter { lot ->
                lot.itemId == itemId && lot.metadata.archivedAt == null
            }
            val now = UtcTimestamp(System.currentTimeMillis())
            val commands = CanonicalKitchenMutationCommandFactory.archiveItem(snapshot, itemId, now)
            if (commands.isEmpty()) {
                showFeedback("Could not archive ${item.name}.")
                return@launch
            }
            val failures = executeCanonicalHouseholdCommands(commands)
            if (failures.isNotEmpty()) {
                showFeedback("Could not archive ${item.name}.")
                return@launch
            }
            val undoAt = UtcTimestamp(System.currentTimeMillis())
            val undoCommands = buildList {
                add(
                    HouseholdCommand.UpsertItem(
                        record = canonicalCommandRecord(item.metadata.id, item.metadata.householdId, "UndoArchiveKitchenItem", undoAt),
                        item = item.copy(
                            metadata = item.metadata.copy(
                                updatedAt = undoAt,
                                revision = item.metadata.revision + 1,
                                archivedAt = null,
                            ),
                        ),
                    ),
                )
                activeLots.forEach { lot ->
                    add(
                        HouseholdCommand.UpsertInventoryLot(
                            record = canonicalCommandRecord(lot.metadata.id, lot.metadata.householdId, "UndoArchiveInventoryLot", undoAt),
                            lot = lot.copy(
                                metadata = lot.metadata.copy(
                                    updatedAt = undoAt,
                                    revision = lot.metadata.revision + 1,
                                    archivedAt = null,
                                ),
                            ),
                        ),
                    )
                }
            }
            refreshCanonicalSummary()
            queueBackendSnapshotSync("canonical_kitchen")
            registerCanonicalUndo(
                label = "archive ${item.name}",
                message = "Archived ${item.name}.",
                commands = undoCommands,
            )
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
            val itemId = canonicalItemIdForLegacyInventoryId(id)
            if (itemId == null) {
                showFeedback("Could not update $safeName: canonical kitchen row not found.")
                return@launch
            }
            val result = applyCanonicalKitchenItemMutation(itemId.value, "Updated") { snapshot, canonicalId, now ->
                CanonicalKitchenMutationCommandFactory.updateItem(
                    snapshot = snapshot,
                    itemId = canonicalId,
                    name = safeName,
                    quantityText = quantity,
                    category = safeCategory,
                    notes = notes,
                    expiresAtMillis = expiresAtMillis,
                    now = now,
                )
            }
            refreshFromDisk()
            showFeedback(result)
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
            val lineId = canonicalShoppingLineIdForLegacyGroceryId(id)
            if (lineId == null) {
                showFeedback("Could not update $safeName: canonical cart row not found.")
                return@launch
            }
            val result = applyCanonicalCartLineMutation(lineId.value, "Updated") { line, now ->
                CanonicalCartMutationCommandFactory.update(
                    line = line,
                    name = safeName,
                    quantityText = quantity,
                    status = status.toCanonicalShoppingLineStatus(),
                    category = safeCategory,
                    preferredStore = source.ifBlank { "manual_edit" },
                    now = now,
                )
            }
            refreshFromDisk()
            showFeedback(result)
        }
    }

    fun cookRecipe(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val recipeId = canonicalRecipeIdForLegacyRecipeId(id)
            if (recipeId == null) {
                showFeedback("Could not log recipe: canonical recipe not found.")
                return@launch
            }
            val summary = cookCanonicalRecipe(recipeId)
            insertLocalChatMessage(ChatRole.ASSISTANT, summary)
            refreshFromDisk()
            showFeedback(summary)
        }
    }

    fun addMissingRecipeGroceries(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val recipeId = canonicalRecipeIdForLegacyRecipeId(id)
            if (recipeId == null) {
                showFeedback("Could not add groceries: canonical recipe not found.")
                return@launch
            }
            val summary = addMissingCanonicalRecipeGroceries(recipeId)
            insertLocalChatMessage(ChatRole.ASSISTANT, summary)
            refreshFromDisk()
            showFeedback(summary)
        }
    }

    fun deleteRecipe(id: Long) {
        val label = _uiState.value.memory.recipes.firstOrNull { it.id == id }?.title ?: "recipe"
        viewModelScope.launch(Dispatchers.IO) {
            val recipeId = canonicalRecipeIdForLegacyRecipeId(id)
            if (recipeId == null) {
                showFeedback("Could not archive $label: canonical recipe not found.")
                return@launch
            }
            val summary = archiveCanonicalRecipe(recipeId)
            refreshFromDisk()
            showFeedback(summary)
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
            val recipeId = canonicalRecipeIdForLegacyRecipeId(id)
            if (recipeId == null) {
                showFeedback("Could not update $safeTitle: canonical recipe not found.")
                return@launch
            }
            val result = updateCanonicalRecipe(
                recipeId = recipeId,
                title = safeTitle,
                ingredients = ingredients,
                steps = steps,
                servings = servings,
                prepMinutes = prepMinutes,
                tags = tags,
            )
            refreshFromDisk()
            showFeedback(result)
        }
    }

    fun updateRecipeImage(id: Long, imageUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = updateCanonicalRecipeImage(id, imageUri)
            recordCanonicalMutationApplied("update_recipe_image", summary)
            refreshFromDisk()
            showFeedback(summary)
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
            val mealEntryId = canonicalMealEntryIdForLegacyMealLogId(id)
            if (mealEntryId == null) {
                showFeedback("Could not update $safeTitle: canonical meal not found.")
                return@launch
            }
            val result = updateCanonicalMealEntry(
                mealEntryId = mealEntryId,
                title = safeTitle,
                mealSlot = mealSlot,
                loggedDateEpochDay = loggedDateEpochDay,
                usedItemsText = usedItemsText,
            )
            refreshFromDisk()
            showFeedback(result)
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
            val mealPlanId = canonicalMealPlanIdForLegacyMealPlanId(id)
            if (mealPlanId == null) {
                showFeedback("Could not update $safeTitle: canonical meal plan not found.")
                return@launch
            }
            val result = updateCanonicalMealPlan(mealPlanId, safeTitle, startDateEpochDay)
            refreshFromDisk()
            showFeedback(result)
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
            val result = addCanonicalMealEntry(
                title = safeTitle,
                mealSlot = slot,
                dateEpochDay = dateEpochDay,
                status = com.wonderfood.core.model.household.MealEntryStatus.PLANNED,
            )
            refreshFromDisk()
            showFeedback(result)
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
            val mealEntryId = canonicalMealEntryIdForLegacyMealLogId(id)
            if (mealEntryId == null) {
                showFeedback("Could not update $safeTitle: canonical planned meal not found.")
                return@launch
            }
            val result = updateCanonicalMealEntry(
                mealEntryId = mealEntryId,
                title = safeTitle,
                mealSlot = slot,
                loggedDateEpochDay = dateEpochDay,
                usedItemsText = "",
                status = status.toCanonicalMealEntryStatus(),
            )
            refreshFromDisk()
            showFeedback(result)
        }
    }

    fun deleteMealPlanEntry(id: Long) {
        val label = _uiState.value.memory.mealPlanEntries.firstOrNull { it.id == id }?.title ?: "planned meal"
        viewModelScope.launch(Dispatchers.IO) {
            val mealEntryId = canonicalMealEntryIdForLegacyMealLogId(id)
            if (mealEntryId == null) {
                showFeedback("Could not archive $label: canonical planned meal not found.")
                return@launch
            }
            val summary = archiveCanonicalMealEntry(mealEntryId)
            refreshFromDisk()
            showFeedback(summary)
        }
    }

    fun deleteMealPlanEntries(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val selected = _uiState.value.memory.mealPlanEntries.filter { it.id in ids }
        if (selected.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val canonicalIds = selected.mapNotNull { canonicalMealEntryIdForLegacyMealLogId(it.id) }
            if (canonicalIds.isEmpty()) {
                showFeedback("Could not archive planned meals: canonical planned meals not found.")
                return@launch
            }
            canonicalIds.forEach { archiveCanonicalMealEntry(it) }
            refreshFromDisk()
            showFeedback("Archived ${canonicalIds.size} planned meal${canonicalIds.size.pluralWord}.")
        }
    }

    fun deleteAllMealPlans() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = archiveAllCanonicalMealPlans()
            refreshFromDisk()
            showFeedback(result)
            insertLocalChatMessage(ChatRole.ASSISTANT, result)
        }
    }

    fun updateReceipt(
        id: Long,
        rawText: String,
        status: ReceiptStatus,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = updateCanonicalReceiptStatus(id, rawText, status)
            recordCanonicalMutationApplied("update_receipt", summary)
            refreshFromDisk()
            showFeedback(summary)
        }
    }

    fun deleteMealLog(id: Long) {
        val label = _uiState.value.memory.mealLogs.firstOrNull { it.id == id }?.title ?: "meal log"
        viewModelScope.launch(Dispatchers.IO) {
            val mealEntryId = canonicalMealEntryIdForLegacyMealLogId(id)
            if (mealEntryId == null) {
                showFeedback("Could not archive $label: canonical meal not found.")
                return@launch
            }
            val summary = archiveCanonicalMealEntry(mealEntryId)
            refreshFromDisk()
            showFeedback(summary)
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
            val result = if (draft.canApplyDirectlyToCanonicalHousehold(FoodDraftCommandOrigin.MANUAL_SAVE)) {
                executeCanonicalDraftCommand(draft, sourceMessageId = null, origin = FoodDraftCommandOrigin.MANUAL_SAVE)
            } else {
                executeDraftCommand(draft, sourceMessageId = null, origin = FoodDraftCommandOrigin.MANUAL_SAVE)
            }
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
            val (eventId, eventSummary) = recordCanonicalActionEvent(
                eventType = "WATER",
                label = "Log water",
                note = "Logged $ml ml water.",
                sourceLabel = "manual",
                payload = mapOf("amount_ml" to ml.toString()),
            )
            val message = recordCanonicalMutationApplied("log_event", eventSummary)
            refreshFromDisk()
            registerUndo(UndoKind.EVENT, eventId, "water log", message)
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
        val generation = backendRefreshGeneration.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            val config = backendConfigurationStore.activeConfiguration()
            val dismissed = backendConfigurationStore.onboardingDismissed()
            val syncStatus = shellPrefs.getString(KEY_BACKEND_SYNC_STATUS, "").orEmpty()
            val safetyStatus = backupGateway.latestBackendSwitchSafetyLabel()
            if (generation != backendRefreshGeneration.get()) return@launch
            _uiState.update {
                it.copy(
                    backendHome = BackendHomeUiState.fromConfig(
                        config = config,
                        onboardingDismissed = dismissed,
                    ).let { backendHome ->
                        backendHome.copy(
                            message = syncStatus.ifBlank { backendHome.message },
                            safetyMessage = safetyStatus,
                        ).withTemplateProofLinks()
                    },
                )
            }
        }
    }

    private fun BackendHomeUiState.withTemplateProofLinks(): BackendHomeUiState =
        copy(
            templateNotionUrl = shellPrefs.getString(KEY_TEMPLATE_NOTION_URL, "").orEmpty(),
            templateSheetsUrl = shellPrefs.getString(KEY_TEMPLATE_SHEETS_URL, "").orEmpty(),
        )

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
                    ).withTemplateProofLinks(),
                )
            }
            showFeedback("WonderFood will keep data on this phone. Rollback snapshot saved from $previous.")
        }
    }

    fun validateGoogleSheetsBackend(sheetInput: String) {
        connectGoogleSheetsBackend(sheetInput, readGoogleAccountEmail(), accessToken = "pending")
    }

    fun createGoogleSheetsBackend(accountEmail: String, accessToken: String) {
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
            createBackendSwitchSafety("Google Sheets")
            val created = runCatching {
                googleSheetsGateway.createWonderFoodSpreadsheet(accessToken)
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        backendHome = it.backendHome.copy(
                            message = "Google Sheet creation failed: ${error.safeMessage()}",
                        ),
                    )
                }
                showFeedback("Google Sheet creation failed: ${error.safeMessage()}")
                return@launch
            }
            _uiState.update {
                it.copy(
                    backendHome = it.backendHome.copy(
                        message = "Created ${created.title}. Setting up WonderFood tabs...",
                    ),
                )
            }
            connectGoogleSheetsBackend(
                sheetInput = created.spreadsheetUrl,
                accountEmail = accountEmail,
                accessToken = accessToken,
            )
        }
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
            val safety = createBackendSwitchSafety("Google Sheets")
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
            val householdSnapshot = canonicalHouseholdSnapshotForBackup()
            val updatedAt = java.time.Instant.now().toString()
            val workspaceRows = runCatching {
                googleSheetsGateway.readWorkspaceRows(accessToken, reference.spreadsheetId)
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        backendHome = it.backendHome.copy(
                            message = "Google Sheets workspace import check failed: ${error.safeMessage()}",
                        ),
                    )
                }
                showFeedback("Google Sheets workspace import check failed: ${error.safeMessage()}")
                return@launch
            }
            val inbound = GoogleSheetsV4InboundWorkspaceImporter.importRows(
                rows = workspaceRows,
                householdId = householdSnapshot.household.id,
                now = UtcTimestamp(java.time.Instant.parse(updatedAt).toEpochMilli()),
                defaultCurrency = householdSnapshot.household.defaultCurrency,
                providerKey = "google_sheets",
                baseSnapshot = householdSnapshot,
            )
            val shouldPreserveRemote = workspaceRows.isNotEmpty()
            val sheetsImportPreview = if (shouldPreserveRemote) {
                inbound.toSheetsImportPreview(
                    workspaceUrl = reference.canonicalUrl,
                    workspaceRowCount = workspaceRows.size,
                    providerLabel = "Google Sheets",
                    mergeClock = updatedAt,
                )
            } else {
                null
            }
            pendingProviderImportCommands = if (shouldPreserveRemote) inbound.commands else emptyList()
            pendingProviderImportDiagnostics = if (shouldPreserveRemote) inbound.diagnostics else emptyList()
            pendingProviderImportLabel = if (shouldPreserveRemote) "Google Sheets V4 workspace" else "Provider data"
            val export = if (shouldPreserveRemote) {
                null
            } else {
                runCatching {
                    googleSheetsGateway.exportGraph(accessToken, reference.spreadsheetId, householdSnapshot)
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
                        dataPlaneUrl = reference.canonicalUrl,
                        externalId = reference.spreadsheetId,
                        accountLabel = accountEmail,
                        proofLabel = "Google Sheets workbook",
                        safetyMessage = safety,
                    ),
                    sheetsImportPreview = sheetsImportPreview,
                )
            }
            val feedback = if (shouldPreserveRemote) {
                "Google Sheets ready: existing WonderFood data found and preserved for import."
            } else {
                "Google Sheets ready: ${bootstrap.createdCount} tabs created, ${bootstrap.initializedCount} headers checked, ${export?.rowCount ?: 0} sync rows exported plus Home, Kitchen, Shopping, Meals, Recipes, Spending, Lists & Help, and managed sync tabs."
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
        pendingProviderImportCommands = emptyList()
        pendingProviderImportDiagnostics = emptyList()
        pendingProviderImportLabel = "Provider data"
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
        val commands = pendingProviderImportCommands
        val diagnostics = pendingProviderImportDiagnostics
        val label = pendingProviderImportLabel
        val preview = _uiState.value.sheetsImportPreview
        if (preview == null) {
            _uiState.update {
                it.copy(
                    sheetsImportPreview = null,
                    backendHome = it.backendHome.copy(message = "Provider import preview expired. Reconnect to review it again."),
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (commands.isEmpty()) {
                pendingProviderImportCommands = emptyList()
                pendingProviderImportDiagnostics = emptyList()
                pendingProviderImportLabel = "Provider data"
                val message = if (diagnostics.isNotEmpty()) {
                    "$label has ${diagnostics.size} issue${diagnostics.size.pluralWord} to review. No safe commands were applied."
                } else {
                    "$label had no safe V4 changes to apply."
                }
                val inbox = preview.toConflictInbox(decision = message)
                inbox?.let(::rememberWorkspaceConflictInbox)
                _uiState.update {
                    it.copy(
                        sheetsImportPreview = null,
                        workspaceConflictInbox = inbox ?: it.workspaceConflictInbox,
                        backendHome = it.backendHome.copy(message = message),
                    )
                }
                shellPrefs.edit { putString(KEY_BACKEND_SYNC_STATUS, message) }
                showFeedback(message)
                return@launch
            }
            ensureCanonicalHousehold()
            val failures = executeCanonicalHouseholdCommands(commands)
            pendingProviderImportCommands = emptyList()
            pendingProviderImportDiagnostics = emptyList()
            pendingProviderImportLabel = "Provider data"
            val message = if (failures.isEmpty()) {
                refreshCanonicalSummary()
                queueBackendSnapshotSync("provider_v4_import")
                "Imported $label: ${commands.size} canonical command${commands.size.pluralWord} applied."
            } else {
                "$label rejected: ${failures.joinToString("; ")}"
            }
            val inbox = preview.toConflictInbox(decision = message)
            inbox?.let(::rememberWorkspaceConflictInbox)
            _uiState.update {
                it.copy(
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
            val safety = createBackendSwitchSafety("Notion")
            val access = runCatching { notionGateway.retrievePage(cleanToken, reference.pageId) }
                .getOrElse { error ->
                    showBackendMessage("Notion check failed: ${error.safeMessage()}")
                    return@launch
                }
            val householdSnapshot = canonicalHouseholdSnapshotForBackup()
            val updatedAt = java.time.Instant.now().toString()
            val workspaceRows = runCatching {
                notionGateway.readWorkspaceRows(cleanToken, reference.pageId)
            }.getOrElse { error ->
                showBackendMessage("Notion workspace import check failed: ${error.safeMessage()}")
                return@launch
            }
            val inbound = GoogleSheetsV4InboundWorkspaceImporter.importRows(
                rows = workspaceRows,
                householdId = householdSnapshot.household.id,
                now = UtcTimestamp(java.time.Instant.parse(updatedAt).toEpochMilli()),
                defaultCurrency = householdSnapshot.household.defaultCurrency,
                providerKey = "notion",
                baseSnapshot = householdSnapshot,
            )
            val foundRemoteData = workspaceRows.isNotEmpty()
            val importPreview = if (foundRemoteData) {
                inbound.toSheetsImportPreview(
                    workspaceUrl = reference.canonicalUrl,
                    workspaceRowCount = workspaceRows.size,
                    providerLabel = "Notion",
                    mergeClock = updatedAt,
                )
            } else {
                null
            }
            pendingProviderImportCommands = if (foundRemoteData) inbound.commands else emptyList()
            pendingProviderImportDiagnostics = if (foundRemoteData) inbound.diagnostics else emptyList()
            pendingProviderImportLabel = if (foundRemoteData) "Notion V4 workspace" else "Provider data"
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
                        snapshot = householdSnapshot,
                        updatedAt = updatedAt,
                    )
                }.getOrElse { error ->
                    showBackendMessage("Notion workspace export failed: ${error.safeMessage()}")
                    return@launch
                }
            }
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
                            "Notion created $createdCount database${createdCount.pluralWord} and upserted $upsertedRows linked workspace row${upsertedRows.pluralWord}."
                        },
                        dataPlaneUrl = reference.canonicalUrl,
                        externalId = reference.pageId,
                        proofLabel = "Notion root page",
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
            if (cleanToken.isBlank()) {
                showBackendMessage("Postgres API token is required.")
                return@launch
            }
            val safety = createBackendSwitchSafety(reference.mode.label)
            val hostedAccess = runCatching {
                postgresGateway.validateHostedApi(reference.mode, reference.endpoint, cleanToken)
            }.getOrElse { error ->
                showBackendMessage("${reference.mode.label} check failed: ${error.safeMessage()}")
                return@launch
            }
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
            val export = runCatching {
                postgresGateway.exportSnapshot(
                    mode = reference.mode,
                    endpoint = reference.endpoint,
                    token = cleanToken,
                    householdId = reference.householdId,
                    snapshot = canonicalSnapshotForPostgresApi(),
                    updatedAt = java.time.Instant.now().toString(),
                )
            }.getOrElse { error ->
                showBackendMessage("${reference.mode.label} export failed: ${error.safeMessage()}")
                return@launch
            } to foundRemoteData
            val credentialRef = CredentialRef(BackendType.POSTGRES, "postgres-primary")
            val secret = BackendSecret.ApiToken(cleanToken)
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
                        detail = if (export.second) {
                            "${hostedAccess.mode.label} has existing WonderFood data for ${reference.householdId}. Snapshot export is active; import review is next."
                        } else {
                            "${hostedAccess.mode.label} snapshot export is active for ${reference.householdId}."
                        },
                        requiresOnboarding = false,
                        message = if (export.second) {
                            "${reference.mode.label} preserved existing remote data and exported ${export.first.byteCount} bytes."
                        } else {
                            "${reference.mode.label} exported ${export.first.byteCount} bytes."
                        },
                        dataPlaneUrl = reference.endpoint,
                        externalId = reference.householdId,
                        proofLabel = "${reference.mode.label} endpoint",
                        safetyMessage = safety,
                    ),
                )
            }
            val feedback = if (export.second) {
                "${reference.mode.label} connected: existing remote data detected; exported ${export.first.byteCount} bytes."
            } else {
                "${reference.mode.label} connected: exported ${export.first.byteCount} bytes."
            }
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
            PostgresConnectionMode.POSTGREST -> "PostgREST"
            PostgresConnectionMode.WONDERFOOD_SERVER -> "WonderFood server"
        }

    private suspend fun createBackendSwitchSafety(toLabel: String): String {
        val fromLabel = _uiState.value.backendHome.label
        val householdSnapshot = canonicalHouseholdSnapshotForBackup()
        val backup = backupGateway.createBackendSwitchSafetyBackup(
            snapshot = householdSnapshot,
            fromLabel = fromLabel,
            toLabel = toLabel,
        )
        val now = UtcTimestamp(java.time.Instant.now().toEpochMilli())
        val safetyId = EntityId(stableUuid("${householdSnapshot.household.id.value}:latest-safety:${backup.fileName}"))
        val record = canonicalCommandRecord(
            affectedId = safetyId,
            householdId = householdSnapshot.household.id,
            type = "StoreLatestSafetySnapshot",
            now = now,
        )
        val safety = LatestSafetySnapshot(
            id = safetyId,
            householdId = householdSnapshot.household.id,
            reason = "latest-safety before switch from $fromLabel to $toLabel",
            createdAt = now,
            localReplicaHash = PayloadHash("backup:${backup.fileName}:${backup.sizeBytes}"),
            activeDataHome = householdSnapshot.household.activeDataHome,
            commandId = record.commandId,
        )
        householdCommandExecutor.execute(
            HouseholdCommand.StoreLatestSafetySnapshot(
                record = record,
                safetySnapshot = safety,
            ),
        )
        return "Rollback snapshot before $fromLabel -> $toLabel: ${backup.sizeBytes / 1024} KB"
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
                val snapshot = canonicalHouseholdSnapshotForBackup()
                val payload = backupGateway.createGoogleDriveBackup(snapshot)
                val remote = googleDriveGateway.uploadBackup(access.accessToken, payload)
                insertLocalChatMessage(
                    ChatRole.ASSISTANT,
                    "Backed up WonderFood to Google Drive app data: ${payload.snapshot.itemCount} household objects.",
                )
                remote to payload
            }.onSuccess { (_, payload) ->
                refreshFromDisk(isWorking = false)
                _uiState.update {
                    it.copy(
                        syncStatus = backupGateway.latestBackupLabel(),
                        googleAccountEmail = readGoogleAccountEmail(),
                        googleSyncStatus = "Google backup complete: ${payload.snapshot.itemCount} household objects, ${payload.sizeBytes / 1024} KB.",
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
                        googleSyncStatus = "Restore preview ready: ${preview.itemCount} household objects.",
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
                val safety = backupGateway.createRestoreSafetyBackup(canonicalHouseholdSnapshotForBackup())
                closeCanonicalHouseholdDatabase()
                val snapshot = backupGateway.restoreGoogleDriveBackup(download.bytes, download.remoteFile.name)
                reopenCanonicalHouseholdDatabase()
                insertLocalChatMessage(
                    ChatRole.ASSISTANT,
                    "Restored WonderFood from Google Drive backup `${download.remoteFile.name}`. Safety backup created: ${safety.fileName}.",
                )
                safety to snapshot
            }.onSuccess { (safety, snapshot) ->
                pendingGoogleRestoreDownload = null
                refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
                refreshCanonicalSummary()
                _uiState.update {
                    it.copy(
                        syncStatus = backupGateway.latestBackupLabel(),
                        googleAccountEmail = readGoogleAccountEmail(),
                        googleSyncStatus = "Restored ${snapshot.itemCount} household objects from Google Drive. Safety backup: ${safety.fileName}.",
                    )
                }
                showFeedback("Restored WonderFood from Google Drive.")
            }.onFailure { error ->
                reopenCanonicalHouseholdDatabase()
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
                closeCanonicalHouseholdDatabase()
                appContext.deleteDatabase("wonderfood.db")
                backupGateway.deleteLocalBackups()
                liteLlmSettings.clear()
                googleSyncPrefs.edit { clear() }
                directActionPrefs.edit { clear() }
                shellPrefs.edit { clear() }
                reopenCanonicalHouseholdDatabase()
                seedLocalChatIfNeeded()
            }.onSuccess {
                _uiState.update { WonderFoodUiState(section = FoodSection.TODAY, memory = canonicalRuntimeMemory(includeLegacyChat = true), isWorking = false) }
                refreshAiStatus()
                refreshHealthStatus()
                refreshSyncStatus()
                showFeedback("Deleted local WonderFood data on this device.")
            }.onFailure { error ->
                reopenCanonicalHouseholdDatabase()
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
            insertLocalChatMessage(ChatRole.ASSISTANT, message)
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
                backupGateway.createEncryptedBackup(passphrase, canonicalHouseholdSnapshotForBackup())
            }.onSuccess { snapshot ->
                refreshFromDisk(isWorking = false)
                _uiState.update {
                    it.copy(syncStatus = "Encrypted canonical backup ready: ${snapshot.itemCount} household objects, ${snapshot.sizeBytes / 1024} KB.")
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
                closeCanonicalHouseholdDatabase()
                backupGateway.restoreLatestEncryptedBackup(passphrase)
            }.onSuccess { snapshot ->
                reopenCanonicalHouseholdDatabase()
                refreshFromDisk(pendingDraft = null, pendingSourceMessageId = null, isWorking = false)
                refreshCanonicalSummary()
                _uiState.update {
                    it.copy(syncStatus = "Restored ${snapshot.itemCount} household objects from latest backup.")
                }
                showFeedback("Restored latest WonderFood backup.")
            }.onFailure { error ->
                reopenCanonicalHouseholdDatabase()
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
                val snapshot = canonicalHouseholdSnapshotForBackup()
                val csv = WonderFoodCsvGateway.export(snapshot)
                val resolver = appContext.contentResolver
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(csv)
                } ?: error("Could not open selected CSV destination.")
                snapshot
            }.onSuccess { snapshot ->
                refreshFromDisk(isWorking = false)
                _uiState.update {
                    it.copy(syncStatus = "CSV exported: ${snapshot.items.size} items, ${snapshot.inventoryLots.size} lots, ${snapshot.shoppingLines.size} shopping lines.")
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
                    val sourceMessageId = insertLocalChatMessage(ChatRole.USER, "Imported external WonderFood proposal file.")
                    insertLocalChatMessage(ChatRole.ASSISTANT, proposal.reply)
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

    private suspend fun applyCsvImport(imported: WonderFoodCsvImport) {
        if (imported.canImportDirectlyToCanonicalHousehold()) {
            applyCanonicalCsvImport(imported)
            insertLocalChatMessage(ChatRole.ASSISTANT, "Imported CSV to canonical household store: ${imported.summary()}.")
            return
        }
        val drafts = buildList {
            if (imported.inventory.isNotEmpty()) add(InventoryDraft(imported.inventory))
            if (imported.groceries.isNotEmpty()) add(GroceryDraft(imported.groceries))
            addAll(imported.recipes)
            addAll(imported.receipts)
            addAll(imported.mealLogs)
            addAll(imported.mealPlans)
        }
        if (drafts.isNotEmpty()) {
            val draft = if (drafts.size == 1) drafts.single() else CompositeDraft(drafts)
            when (val result = executeDraftCommand(draft, sourceMessageId = null, origin = FoodDraftCommandOrigin.CSV_IMPORT)) {
                is FoodDraftExecutionResult.Applied -> insertLocalChatMessage(ChatRole.ASSISTANT, result.summary)
                is FoodDraftExecutionResult.Rejected -> insertLocalChatMessage(
                    ChatRole.ASSISTANT,
                    "CSV draft rejected before saving: ${result.errors.joinToString("; ")}",
                )
            }
        }
        imported.preferences?.let { preferences ->
            _uiState.update { state ->
                state.copy(
                    memory = state.memory.copy(preferences = preferences),
                    preferencesForm = preferences,
                )
            }
        }
        insertLocalChatMessage(ChatRole.ASSISTANT, "Imported CSV: ${imported.summary()}.")
    }

    private suspend fun applyCanonicalCsvImport(imported: WonderFoodCsvImport) {
        val drafts = buildList {
            if (imported.inventory.isNotEmpty()) add(InventoryDraft(imported.inventory))
            if (imported.groceries.isNotEmpty()) add(GroceryDraft(imported.groceries))
            addAll(imported.recipes)
            addAll(imported.receipts)
            addAll(imported.mealLogs)
            addAll(imported.mealPlans)
        }
        val draft = if (drafts.size == 1) drafts.single() else CompositeDraft(drafts)
        applyCanonicalDraftImport(draft, FoodDraftCommandOrigin.CSV_IMPORT)
    }

    private suspend fun applyCanonicalDraftImport(draft: FoodDraft, origin: FoodDraftCommandOrigin) {
        val errors = FoodDraftValidator.validate(draft)
        require(errors.isEmpty()) { "Import draft rejected before saving: ${errors.joinToString("; ")}" }
        val commands = householdDraftCommandMapper.toCommands(draft, origin)
        require(commands.isNotEmpty()) { "Import did not contain canonical household rows." }
        ensureCanonicalHousehold()
        val failures = executeCanonicalHouseholdCommands(commands)
        require(failures.isEmpty()) { "Import canonical commands rejected: ${failures.joinToString("; ")}" }
        refreshCanonicalSummary()
        queueBackendSnapshotSync(origin.writeSource)
    }

    private suspend fun executeCanonicalDraftCommand(
        draft: FoodDraft,
        sourceMessageId: Long?,
        origin: FoodDraftCommandOrigin,
    ): FoodDraftExecutionResult {
        val normalized = FoodDraftNormalizer.normalize(draft)
        val command = FoodDraftCommand(
            draft = normalized,
            sourceMessageId = canonicalSafeSourceMessageId(sourceMessageId),
            origin = origin,
        )
        val errors = FoodDraftValidator.validate(normalized) + FoodDraftCommandPolicy.validate(command)
        if (errors.isNotEmpty()) return FoodDraftExecutionResult.Rejected(errors)
        val commands = householdDraftCommandMapper.toCommands(normalized, origin)
        if (commands.isEmpty()) {
            return FoodDraftExecutionResult.Rejected(listOf("Draft did not contain canonical household rows."))
        }
        return runCatching {
            ensureCanonicalHousehold()
            val failures = executeCanonicalHouseholdCommands(commands)
            check(failures.isEmpty()) { "Canonical commands rejected: ${failures.joinToString("; ")}" }
            refreshCanonicalSummary()
            queueBackendSnapshotSync(origin.writeSource)
            "Saved ${normalized.title.lowercase()} to household."
        }.fold(
            onSuccess = FoodDraftExecutionResult::Applied,
            onFailure = { error ->
                FoodDraftExecutionResult.Rejected(
                    listOf(error.message?.takeIf { it.isNotBlank() } ?: "Draft could not be saved. No changes were applied."),
                )
            },
        )
    }

    private suspend fun applyCanonicalCartLineMutation(
        id: String,
        verb: String,
        commandFor: (com.wonderfood.core.model.household.ShoppingLine, UtcTimestamp) -> HouseholdCommand,
    ): String {
        ensureCanonicalHousehold()
        val lineId = runCatching { EntityId(id) }.getOrNull()
            ?: return "Could not update cart row: invalid canonical id."
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not update cart row: household is not ready."
        val line = snapshot.shoppingLines.firstOrNull { it.metadata.id == lineId }
            ?: return "Could not update cart row: it is no longer in the household cart."
        val command = commandFor(line, UtcTimestamp(System.currentTimeMillis()))
        return when (householdCommandExecutor.execute(command)) {
            is com.wonderfood.core.engine.HouseholdCommandExecutionResult.Applied,
            is com.wonderfood.core.engine.HouseholdCommandExecutionResult.Duplicate,
            -> {
                refreshCanonicalSummary()
                queueBackendSnapshotSync("canonical_cart")
                "$verb ${line.displayName}."
            }
            is com.wonderfood.core.engine.HouseholdCommandExecutionResult.Rejected ->
                "Could not update ${line.displayName}."
        }
    }

    private suspend fun applyCanonicalKitchenItemMutation(
        id: String,
        verb: String,
        commandsFor: (
            com.wonderfood.core.model.household.HouseholdSnapshot,
            EntityId,
            UtcTimestamp,
        ) -> List<HouseholdCommand>,
    ): String {
        ensureCanonicalHousehold()
        val itemId = runCatching { EntityId(id) }.getOrNull()
            ?: return "Could not update kitchen row: invalid canonical id."
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not update kitchen row: household is not ready."
        val item = snapshot.items.firstOrNull { it.metadata.id == itemId }
            ?: return "Could not update kitchen row: it is no longer in the household."
        val commands = commandsFor(snapshot, itemId, UtcTimestamp(System.currentTimeMillis()))
        if (commands.isEmpty()) return "Could not update ${item.name}."
        val rejected = commands.map { householdCommandExecutor.execute(it) }
            .filterIsInstance<com.wonderfood.core.engine.HouseholdCommandExecutionResult.Rejected>()
        return if (rejected.isEmpty()) {
            refreshCanonicalSummary()
            queueBackendSnapshotSync("canonical_kitchen")
            "$verb ${item.name}."
        } else {
            "Could not update ${item.name}."
        }
    }

    private suspend fun canonicalItemIdForLegacyInventoryId(id: Long): EntityId? {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID) ?: return null
        return snapshot.items.firstOrNull { stableLegacyId(it.metadata.id.value) == id }?.metadata?.id
    }

    private suspend fun canonicalShoppingLineIdForLegacyGroceryId(id: Long): EntityId? {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID) ?: return null
        return snapshot.shoppingLines.firstOrNull { stableLegacyId(it.metadata.id.value) == id }?.metadata?.id
    }

    private suspend fun canonicalRecipeIdForLegacyRecipeId(id: Long): EntityId? {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID) ?: return null
        return snapshot.recipes.firstOrNull { stableLegacyId(it.metadata.id.value) == id }?.metadata?.id
    }

    private suspend fun canonicalMealEntryIdForLegacyMealLogId(id: Long): EntityId? {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID) ?: return null
        return snapshot.mealEntries.firstOrNull { stableLegacyId(it.metadata.id.value) == id }?.metadata?.id
    }

    private suspend fun canonicalMealPlanIdForLegacyMealPlanId(id: Long): EntityId? {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID) ?: return null
        return snapshot.mealPlans.firstOrNull { stableLegacyId(it.metadata.id.value) == id }?.metadata?.id
    }

    private suspend fun canonicalPurchaseIdForLegacyReceiptId(id: Long): EntityId? {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID) ?: return null
        return snapshot.purchases.firstOrNull { stableLegacyId(it.metadata.id.value) == id }?.metadata?.id
    }

    private fun canonicalReceiptPurchaseId(receiptId: Long): EntityId {
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        val scope = "receipt:receipt_id:$receiptId"
        return EntityId(stableUuid("${householdId.value}:$scope:purchase:purchase"))
    }

    private fun canonicalReceiptAttachmentId(receiptId: Long): EntityId {
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        return EntityId(stableUuid("${householdId.value}:receipt_attachment:$receiptId:attachment"))
    }

    private suspend fun upsertCanonicalReceiptCapture(
        receiptId: Long,
        imageUri: String,
        rawText: String,
        status: ReceiptStatus,
    ): String {
        ensureCanonicalHousehold()
        val now = UtcTimestamp(System.currentTimeMillis())
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        val attachmentId = canonicalReceiptAttachmentId(receiptId)
        val purchaseId = canonicalPurchaseIdForLegacyReceiptId(receiptId) ?: canonicalReceiptPurchaseId(receiptId)
        val existing = householdRepository.snapshot(householdId)?.purchases?.firstOrNull { it.metadata.id == purchaseId }
        val attachment = Attachment(
            metadata = EntityMetadata(
                id = attachmentId,
                householdId = householdId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                source = SourceRef(SourceKind.RECEIPT, "receipt_capture"),
            ),
            kind = AttachmentKind.RECEIPT,
            localUri = imageUri,
            label = rawText.take(160).ifBlank { "Receipt photo" },
            capturedAt = now,
        )
        val purchase = (existing ?: Purchase(
            metadata = EntityMetadata(
                id = purchaseId,
                householdId = householdId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                source = SourceRef(SourceKind.RECEIPT, "receipt_capture"),
            ),
            occurredAt = now,
            status = status.toCanonicalPurchaseStatus(),
            paymentNote = rawText.take(2_000).ifBlank { null },
        )).copy(
            metadata = (existing?.metadata ?: EntityMetadata(
                id = purchaseId,
                householdId = householdId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                source = SourceRef(SourceKind.RECEIPT, "receipt_capture"),
            )).copy(
                updatedAt = now,
                revision = (existing?.metadata?.revision ?: 0) + 1,
                source = SourceRef(SourceKind.RECEIPT, "receipt_capture"),
            ),
            receiptAttachmentIds = (existing?.receiptAttachmentIds.orEmpty() + attachmentId).distinct(),
            paymentNote = rawText.take(2_000).ifBlank { existing?.paymentNote },
            status = status.toCanonicalPurchaseStatus(),
        )
        val failures = executeCanonicalHouseholdCommands(
            listOf(
                HouseholdCommand.UpsertAttachment(
                    record = canonicalReceiptCommandRecord(attachmentId, "UpsertReceiptAttachment", now),
                    attachment = attachment,
                ),
                HouseholdCommand.UpsertPurchase(
                    record = canonicalReceiptCommandRecord(purchaseId, "UpsertReceiptPurchase", now),
                    purchase = purchase,
                ),
            ),
        )
        if (failures.isNotEmpty()) return "Receipt could not be saved: ${failures.joinToString("; ")}"
        refreshCanonicalSummary()
        queueBackendSnapshotSync("canonical_receipt")
        return if (status == ReceiptStatus.NEEDS_TEXT) "Receipt needs text." else "Receipt page updated."
    }

    private suspend fun updateCanonicalReceiptStatus(id: Long, rawText: String, status: ReceiptStatus): String {
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not update receipt: household is not ready."
        val purchase = snapshot.purchases.firstOrNull { stableLegacyId(it.metadata.id.value) == id }
            ?: return "Could not update receipt: canonical receipt not found."
        val now = UtcTimestamp(System.currentTimeMillis())
        val updated = purchase.copy(
            metadata = purchase.metadata.copy(
                updatedAt = now,
                revision = purchase.metadata.revision + 1,
                source = SourceRef(SourceKind.RECEIPT, "receipt_update"),
            ),
            paymentNote = rawText.take(2_000).ifBlank { purchase.paymentNote },
            status = status.toCanonicalPurchaseStatus(),
        )
        val result = householdCommandExecutor.execute(
            HouseholdCommand.UpsertPurchase(
                record = canonicalReceiptCommandRecord(purchase.metadata.id, "UpdateReceiptPurchase", now),
                purchase = updated,
            ),
        )
        return when (result) {
            is HouseholdCommandExecutionResult.Applied,
            is HouseholdCommandExecutionResult.Duplicate,
            -> {
                refreshCanonicalSummary()
                queueBackendSnapshotSync("canonical_receipt")
                "Receipt page updated."
            }
            is HouseholdCommandExecutionResult.Rejected ->
                "Receipt could not be updated: ${result.errors.joinToString("; ")}"
        }
    }

    private suspend fun recordCanonicalActionEvent(
        eventType: String,
        label: String,
        note: String,
        sourceLabel: String,
        payload: Map<String, String?>,
    ): Pair<Long, String> {
        ensureCanonicalHousehold()
        val now = UtcTimestamp(System.currentTimeMillis())
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        val normalizedPayload = payload
            .filterValues { !it.isNullOrBlank() }
            .toSortedMap()
            .entries
            .joinToString("|") { "${it.key}=${it.value}" }
        val proposalId = EntityId(stableUuid("${householdId.value}:action-event:$eventType:$sourceLabel:$normalizedPayload:${now.epochMillis}"))
        val payloadHash = stableUuid("action-event:$eventType:$sourceLabel:$normalizedPayload")
        val proposal = ChangeProposal(
            metadata = EntityMetadata(
                id = proposalId,
                householdId = householdId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                source = SourceRef(SourceKind.MANUAL, sourceLabel),
            ),
            sourcePayloadReference = "local-action:$eventType:${now.epochMillis}",
            requestedCommands = listOf(CommandIntent("LogActionEvent:$eventType", payloadHash)),
            warnings = listOf("Action event is recorded as canonical audit history; undo requires recovery history."),
            status = ReviewState.ACCEPTED,
            reviewedAt = now,
            reviewer = "app",
        )
        val result = householdCommandExecutor.execute(
            HouseholdCommand.StoreProposal(
                record = CommandRecord(
                    commandId = CommandId.new(),
                    householdId = householdId,
                    type = "LogActionEvent",
                    source = SourceRef(SourceKind.MANUAL, sourceLabel),
                    requestedAt = now,
                    appliedAt = now,
                    affectedEntityIds = listOf(proposalId),
                ),
                proposal = proposal,
            ),
        )
        val summary = when (result) {
            is HouseholdCommandExecutionResult.Applied,
            is HouseholdCommandExecutionResult.Duplicate,
            -> note
            is HouseholdCommandExecutionResult.Rejected ->
                "Could not log ${label.lowercase()}: ${result.errors.joinToString("; ")}"
        }
        refreshCanonicalSummary()
        queueBackendSnapshotSync("canonical_action_event")
        return stableLegacyId(proposalId.value) to summary
    }

    private suspend fun updateCanonicalRecipeImage(id: Long, imageUri: String?): String {
        val recipeId = canonicalRecipeIdForLegacyRecipeId(id)
            ?: return "Could not update recipe image: canonical recipe not found."
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not update recipe image: household is not ready."
        val recipe = snapshot.recipes.firstOrNull { it.metadata.id == recipeId }
            ?: return "Could not update recipe image: canonical recipe not found."
        val now = UtcTimestamp(System.currentTimeMillis())
        val attachmentId = EntityId(stableUuid("${recipeId.value}:recipe_image"))
        val attachment = imageUri?.takeIf { it.isNotBlank() }?.let { uri ->
            Attachment(
                metadata = EntityMetadata(
                    id = attachmentId,
                    householdId = recipe.metadata.householdId,
                    createdAt = now,
                    updatedAt = now,
                    revision = 1,
                    source = SourceRef(SourceKind.MANUAL, "recipe_image"),
                ),
                kind = AttachmentKind.IMAGE,
                localUri = uri,
                label = "${recipe.name} image",
                capturedAt = now,
            )
        }
        val updatedRecipe = recipe.copy(
            metadata = recipe.metadata.copy(
                updatedAt = now,
                revision = recipe.metadata.revision + 1,
                source = SourceRef(SourceKind.MANUAL, "recipe_image"),
            ),
            attachmentIds = attachment?.let { (recipe.attachmentIds + attachmentId).distinct() } ?: recipe.attachmentIds,
        )
        val commands = listOfNotNull(
            attachment?.let {
                HouseholdCommand.UpsertAttachment(
                    record = canonicalCommandRecord(attachmentId, recipe.metadata.householdId, "UpsertRecipeImageAttachment", now),
                    attachment = it,
                )
            },
            HouseholdCommand.UpsertRecipe(
                record = canonicalCommandRecord(recipeId, recipe.metadata.householdId, "UpdateRecipeImage", now),
                recipe = updatedRecipe,
            ),
        )
        val failures = executeCanonicalHouseholdCommands(commands)
        if (failures.isNotEmpty()) return "Recipe image could not be updated: ${failures.joinToString("; ")}"
        refreshCanonicalSummary()
        queueBackendSnapshotSync("canonical_recipe")
        return "Recipe image updated."
    }

    private fun canonicalReceiptCommandRecord(
        affectedId: EntityId,
        type: String,
        now: UtcTimestamp,
    ): CommandRecord =
        CommandRecord(
            commandId = CommandId.new(),
            householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID,
            type = type,
            source = SourceRef(SourceKind.RECEIPT, "canonical_receipt"),
            requestedAt = now,
            appliedAt = now,
            affectedEntityIds = listOf(affectedId),
        )

    private fun ReceiptStatus.toCanonicalPurchaseStatus(): PurchaseStatus =
        when (this) {
            ReceiptStatus.SAVED -> PurchaseStatus.DRAFT
            ReceiptStatus.NEEDS_TEXT -> PurchaseStatus.ARCHIVED
            ReceiptStatus.EXTRACTED -> PurchaseStatus.REVIEWED
        }

    private suspend fun archiveCanonicalRecipe(recipeId: EntityId): String {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not archive recipe: household is not ready."
        val recipe = snapshot.recipes.firstOrNull { it.metadata.id == recipeId }
            ?: return "Could not archive recipe: it is no longer in the household."
        val now = UtcTimestamp(System.currentTimeMillis())
        val archived = recipe.copy(
            metadata = recipe.metadata.copy(
                updatedAt = now,
                archivedAt = now,
                revision = recipe.metadata.revision + 1,
                source = SourceRef(SourceKind.MANUAL, "canonical_recipe"),
            ),
            status = com.wonderfood.core.model.household.RecipeStatus.ARCHIVED,
        )
        val result = householdCommandExecutor.execute(
            HouseholdCommand.UpsertRecipe(
                record = CommandRecord(
                    commandId = CommandId.new(),
                    householdId = recipe.metadata.householdId,
                    type = "ArchiveRecipe",
                    source = SourceRef(SourceKind.MANUAL, "canonical_recipe"),
                    requestedAt = now,
                    appliedAt = now,
                    affectedEntityIds = listOf(recipe.metadata.id),
                ),
                recipe = archived,
            ),
        )
        return when (result) {
            is HouseholdCommandExecutionResult.Applied,
            is HouseholdCommandExecutionResult.Duplicate,
            -> {
                refreshCanonicalSummary()
                queueBackendSnapshotSync("canonical_recipe")
                "Archived ${recipe.name}."
            }
            is HouseholdCommandExecutionResult.Rejected -> "Could not archive ${recipe.name}."
        }
    }

    private suspend fun updateCanonicalRecipe(
        recipeId: EntityId,
        title: String,
        ingredients: String,
        steps: String,
        servings: Int?,
        prepMinutes: Int?,
        tags: String,
    ): String {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not update recipe: household is not ready."
        val recipe = snapshot.recipes.firstOrNull { it.metadata.id == recipeId }
            ?: return "Could not update recipe: it is no longer in the household."
        val now = UtcTimestamp(System.currentTimeMillis())
        val ingredientLines = ingredients.lines().map { it.trim() }.filter { it.isNotBlank() }
        val stepLines = steps.lines().map { it.trim() }.filter { it.isNotBlank() }
        val ingredientIds = ingredientLines.mapIndexed { index, line ->
            EntityId(stableUuid("${recipeId.value}:ingredient:$index:${line.lowercase()}"))
        }
        val stepIds = stepLines.mapIndexed { index, line ->
            EntityId(stableUuid("${recipeId.value}:step:$index:${line.lowercase()}"))
        }
        val updatedRecipe = recipe.copy(
            metadata = recipe.metadata.copy(
                updatedAt = now,
                revision = recipe.metadata.revision + 1,
                source = SourceRef(SourceKind.MANUAL, "canonical_recipe"),
            ),
            name = title.trim().ifBlank { recipe.name },
            yield = servings?.takeIf { it > 0 }?.let {
                Quantity(DecimalAmount.of(it.toString()), QuantityUnit.SERVING)
            } ?: recipe.yield,
            prepMinutes = prepMinutes?.takeIf { it >= 0 },
            tags = tags.split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet(),
            status = com.wonderfood.core.model.household.RecipeStatus.ACTIVE,
            ingredientIds = ingredientIds,
            stepIds = stepIds,
        )
        val archiveOldIngredients = snapshot.recipeIngredients
            .filter { it.recipeId == recipeId && it.metadata.id !in ingredientIds && it.metadata.archivedAt == null }
            .map { ingredient ->
                HouseholdCommand.UpsertRecipeIngredient(
                    record = canonicalCommandRecord(ingredient.metadata.id, ingredient.metadata.householdId, "ArchiveRecipeIngredient", now),
                    ingredient = ingredient.copy(
                        metadata = ingredient.metadata.copy(
                            updatedAt = now,
                            archivedAt = now,
                            revision = ingredient.metadata.revision + 1,
                            source = SourceRef(SourceKind.MANUAL, "canonical_recipe"),
                        ),
                    ),
                )
            }
        val archiveOldSteps = snapshot.recipeSteps
            .filter { it.recipeId == recipeId && it.metadata.id !in stepIds && it.metadata.archivedAt == null }
            .map { step ->
                HouseholdCommand.UpsertRecipeStep(
                    record = canonicalCommandRecord(step.metadata.id, step.metadata.householdId, "ArchiveRecipeStep", now),
                    step = step.copy(
                        metadata = step.metadata.copy(
                            updatedAt = now,
                            archivedAt = now,
                            revision = step.metadata.revision + 1,
                            source = SourceRef(SourceKind.MANUAL, "canonical_recipe"),
                        ),
                    ),
                )
            }
        val newIngredients = ingredientLines.mapIndexed { index, line ->
            val id = ingredientIds[index]
            HouseholdCommand.UpsertRecipeIngredient(
                record = canonicalCommandRecord(id, recipe.metadata.householdId, "UpsertRecipeIngredient", now),
                ingredient = com.wonderfood.core.model.household.RecipeIngredient(
                    metadata = EntityMetadata(
                        id = id,
                        householdId = recipe.metadata.householdId,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1,
                        source = SourceRef(SourceKind.MANUAL, "canonical_recipe"),
                    ),
                    recipeId = recipeId,
                    originalText = line,
                    quantity = Quantity.unknown(),
                    order = index,
                ),
            )
        }
        val newSteps = stepLines.mapIndexed { index, line ->
            val id = stepIds[index]
            HouseholdCommand.UpsertRecipeStep(
                record = canonicalCommandRecord(id, recipe.metadata.householdId, "UpsertRecipeStep", now),
                step = com.wonderfood.core.model.household.RecipeStep(
                    metadata = EntityMetadata(
                        id = id,
                        householdId = recipe.metadata.householdId,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1,
                        source = SourceRef(SourceKind.MANUAL, "canonical_recipe"),
                    ),
                    recipeId = recipeId,
                    order = index,
                    instruction = line,
                ),
            )
        }
        val commands = listOf(
            HouseholdCommand.UpsertRecipe(
                record = canonicalCommandRecord(recipe.metadata.id, recipe.metadata.householdId, "UpdateRecipe", now),
                recipe = updatedRecipe,
            ),
        ) + archiveOldIngredients + archiveOldSteps + newIngredients + newSteps
        val rejected = commands.map { householdCommandExecutor.execute(it) }
            .filterIsInstance<HouseholdCommandExecutionResult.Rejected>()
        return if (rejected.isEmpty()) {
            refreshCanonicalSummary()
            queueBackendSnapshotSync("canonical_recipe")
            "Updated recipe page: ${updatedRecipe.name}."
        } else {
            "Could not update recipe page: ${rejected.flatMap { it.errors }.joinToString("; ").take(140)}"
        }
    }

    private suspend fun addMissingCanonicalRecipeGroceries(recipeId: EntityId): String {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not add recipe groceries: household is not ready."
        val recipe = snapshot.recipes.firstOrNull { it.metadata.id == recipeId }
            ?: return "Could not add recipe groceries: recipe is no longer in the household."
        val existingNames = snapshot.shoppingLines
            .filter { it.metadata.archivedAt == null }
            .map { it.displayName.lowercase() }
            .toSet()
        val ingredients = snapshot.recipeIngredients
            .filter { it.recipeId == recipeId && it.metadata.archivedAt == null }
            .filter { it.originalText.isNotBlank() }
            .filterNot { it.originalText.lowercase() in existingNames }
        if (ingredients.isEmpty()) return "No missing groceries found for ${recipe.name}."
        val now = UtcTimestamp(System.currentTimeMillis())
        val commands = ingredients.mapIndexed { index, ingredient ->
            val displayName = ingredient.originalText.trim()
            val lineId = EntityId(stableUuid("${recipeId.value}:recipe-grocery:$index:${displayName.lowercase()}"))
            HouseholdCommand.UpsertShoppingLine(
                record = canonicalCommandRecord(lineId, recipe.metadata.householdId, "AddRecipeGrocery", now),
                line = com.wonderfood.core.model.household.ShoppingLine(
                    metadata = EntityMetadata(
                        id = lineId,
                        householdId = recipe.metadata.householdId,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1,
                        source = SourceRef(SourceKind.RECIPE, "canonical_recipe"),
                    ),
                    shoppingListId = HouseholdDraftCommandMapper.DEFAULT_SHOPPING_LIST_ID,
                    itemId = ingredient.itemId,
                    displayName = displayName,
                    quantity = ingredient.quantity,
                    category = recipe.category,
                    status = com.wonderfood.core.model.household.ShoppingLineStatus.NEEDED,
                    reason = com.wonderfood.core.model.household.ShoppingReason.RECIPE_GAP,
                    sourceEntityIds = listOf(recipeId, ingredient.metadata.id),
                ),
            )
        }
        val rejected = commands.map { householdCommandExecutor.execute(it) }
            .filterIsInstance<HouseholdCommandExecutionResult.Rejected>()
        return if (rejected.isEmpty()) {
            refreshCanonicalSummary()
            queueBackendSnapshotSync("canonical_recipe")
            "Added ${commands.size} missing grocer${if (commands.size == 1) "y" else "ies"} for ${recipe.name}."
        } else {
            "Could not add missing groceries for ${recipe.name}."
        }
    }

    private suspend fun cookCanonicalRecipe(recipeId: EntityId): String {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not log recipe: household is not ready."
        val recipe = snapshot.recipes.firstOrNull { it.metadata.id == recipeId }
            ?: return "Could not log recipe: it is no longer in the household."
        val result = addCanonicalMealEntry(
            title = recipe.name,
            mealSlot = MealSlot.DINNER,
            dateEpochDay = java.time.LocalDate.now().toEpochDay(),
            status = com.wonderfood.core.model.household.MealEntryStatus.COOKED,
            recipeId = recipeId,
        )
        return if (result == "Planned meal added.") "Logged ${recipe.name}." else result
    }

    private suspend fun updateCanonicalMealEntry(
        mealEntryId: EntityId,
        title: String,
        mealSlot: MealSlot,
        loggedDateEpochDay: Long,
        usedItemsText: String,
        status: com.wonderfood.core.model.household.MealEntryStatus = com.wonderfood.core.model.household.MealEntryStatus.EATEN,
    ): String {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not update meal: household is not ready."
        val meal = snapshot.mealEntries.firstOrNull { it.metadata.id == mealEntryId }
            ?: return "Could not update meal: it is no longer in the household."
        val now = UtcTimestamp(System.currentTimeMillis())
        val scheduledAt = java.time.LocalDate.ofEpochDay(loggedDateEpochDay)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
            .let(::UtcTimestamp)
        val updated = meal.copy(
            metadata = meal.metadata.copy(
                updatedAt = now,
                revision = meal.metadata.revision + 1,
                source = SourceRef(SourceKind.MANUAL, "canonical_meal"),
            ),
            scheduledAt = scheduledAt,
            slot = mealSlot.name.lowercase(),
            title = title.trim().ifBlank { meal.title },
            status = status,
            notes = usedItemsText.ifBlank { meal.notes },
        )
        return when (
            householdCommandExecutor.execute(
                HouseholdCommand.UpsertMealEntry(
                    record = canonicalCommandRecord(meal.metadata.id, meal.metadata.householdId, "UpdateMealEntry", now),
                    entry = updated,
                ),
            )
        ) {
            is HouseholdCommandExecutionResult.Applied,
            is HouseholdCommandExecutionResult.Duplicate,
            -> {
                refreshCanonicalSummary()
                queueBackendSnapshotSync("canonical_meal")
                "Updated meal page: ${updated.title}."
            }
            is HouseholdCommandExecutionResult.Rejected -> "Could not update meal page: ${meal.title}."
        }
    }

    private suspend fun addCanonicalMealEntry(
        title: String,
        mealSlot: MealSlot,
        dateEpochDay: Long,
        status: com.wonderfood.core.model.household.MealEntryStatus,
        recipeId: EntityId? = null,
    ): String {
        ensureCanonicalHousehold()
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        val now = UtcTimestamp(System.currentTimeMillis())
        val entryId = EntityId(stableUuid("${householdId.value}:meal:${dateEpochDay}:${mealSlot.name}:${title.lowercase()}"))
        val scheduledAt = java.time.LocalDate.ofEpochDay(dateEpochDay)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
            .let(::UtcTimestamp)
        val entry = com.wonderfood.core.model.household.MealEntry(
            metadata = EntityMetadata(
                id = entryId,
                householdId = householdId,
                createdAt = now,
                updatedAt = now,
                revision = 1,
                source = SourceRef(SourceKind.MANUAL, "canonical_meal"),
            ),
            scheduledAt = scheduledAt,
            slot = mealSlot.name.lowercase(),
            title = title,
            recipeId = recipeId,
            status = status,
        )
        return when (
            householdCommandExecutor.execute(
                HouseholdCommand.UpsertMealEntry(
                    record = canonicalCommandRecord(entryId, householdId, "AddMealEntry", now),
                    entry = entry,
                ),
            )
        ) {
            is HouseholdCommandExecutionResult.Applied,
            is HouseholdCommandExecutionResult.Duplicate,
            -> {
                refreshCanonicalSummary()
                queueBackendSnapshotSync("canonical_meal")
                "Planned meal added."
            }
            is HouseholdCommandExecutionResult.Rejected -> "Could not add planned meal."
        }
    }

    private suspend fun updateCanonicalMealPlan(
        mealPlanId: EntityId,
        title: String,
        startDateEpochDay: Long?,
    ): String {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not update meal plan: household is not ready."
        val plan = snapshot.mealPlans.firstOrNull { it.metadata.id == mealPlanId }
            ?: return "Could not update meal plan: it is no longer in the household."
        val now = UtcTimestamp(System.currentTimeMillis())
        val startsOn = startDateEpochDay?.toCalendarDate() ?: plan.startsOn
        val updated = plan.copy(
            metadata = plan.metadata.copy(
                updatedAt = now,
                revision = plan.metadata.revision + 1,
                source = SourceRef(SourceKind.MANUAL, "canonical_meal_plan"),
            ),
            name = title.trim().ifBlank { plan.name },
            startsOn = startsOn,
            endsOn = runCatching {
                java.time.LocalDate.parse(startsOn.value).plusDays(6).toString()
            }.map(::CalendarDate).getOrDefault(plan.endsOn),
            status = com.wonderfood.core.model.household.MealPlanStatus.ACTIVE,
        )
        return when (
            householdCommandExecutor.execute(
                HouseholdCommand.UpsertMealPlan(
                    record = canonicalCommandRecord(plan.metadata.id, plan.metadata.householdId, "UpdateMealPlan", now),
                    plan = updated,
                ),
            )
        ) {
            is HouseholdCommandExecutionResult.Applied,
            is HouseholdCommandExecutionResult.Duplicate,
            -> {
                refreshCanonicalSummary()
                queueBackendSnapshotSync("canonical_meal_plan")
                "Meal plan updated: ${updated.name}."
            }
            is HouseholdCommandExecutionResult.Rejected -> "Could not update meal plan: ${plan.name}."
        }
    }

    private suspend fun archiveCanonicalMealEntry(mealEntryId: EntityId): String {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not archive meal: household is not ready."
        val meal = snapshot.mealEntries.firstOrNull { it.metadata.id == mealEntryId }
            ?: return "Could not archive meal: it is no longer in the household."
        val now = UtcTimestamp(System.currentTimeMillis())
        val archived = meal.copy(
            metadata = meal.metadata.copy(
                updatedAt = now,
                archivedAt = now,
                revision = meal.metadata.revision + 1,
                source = SourceRef(SourceKind.MANUAL, "canonical_meal"),
            ),
            status = com.wonderfood.core.model.household.MealEntryStatus.ARCHIVED,
        )
        return when (
            householdCommandExecutor.execute(
                HouseholdCommand.UpsertMealEntry(
                    record = canonicalCommandRecord(meal.metadata.id, meal.metadata.householdId, "ArchiveMealEntry", now),
                    entry = archived,
                ),
            )
        ) {
            is HouseholdCommandExecutionResult.Applied,
            is HouseholdCommandExecutionResult.Duplicate,
            -> {
                refreshCanonicalSummary()
                queueBackendSnapshotSync("canonical_meal")
                "Archived meal log: ${meal.title}."
            }
            is HouseholdCommandExecutionResult.Rejected -> "Could not archive meal log: ${meal.title}."
        }
    }

    private suspend fun archiveAllCanonicalMealPlans(): String {
        ensureCanonicalHousehold()
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
            ?: return "Could not archive meal plans: household is not ready."
        val activePlans = snapshot.mealPlans.filter { it.metadata.archivedAt == null }
        val activeEntries = snapshot.mealEntries.filter { it.metadata.archivedAt == null }
        if (activePlans.isEmpty() && activeEntries.isEmpty()) return "No meal plans to archive."
        val now = UtcTimestamp(System.currentTimeMillis())
        val planCommands = activePlans.map { plan ->
            HouseholdCommand.UpsertMealPlan(
                record = canonicalCommandRecord(plan.metadata.id, plan.metadata.householdId, "ArchiveMealPlan", now),
                plan = plan.copy(
                    metadata = plan.metadata.copy(
                        updatedAt = now,
                        archivedAt = now,
                        revision = plan.metadata.revision + 1,
                        source = SourceRef(SourceKind.MANUAL, "canonical_meal_plan"),
                    ),
                    status = com.wonderfood.core.model.household.MealPlanStatus.ARCHIVED,
                ),
            )
        }
        val entryCommands = activeEntries.map { entry ->
            HouseholdCommand.UpsertMealEntry(
                record = canonicalCommandRecord(entry.metadata.id, entry.metadata.householdId, "ArchiveMealEntry", now),
                entry = entry.copy(
                    metadata = entry.metadata.copy(
                        updatedAt = now,
                        archivedAt = now,
                        revision = entry.metadata.revision + 1,
                        source = SourceRef(SourceKind.MANUAL, "canonical_meal"),
                    ),
                    status = com.wonderfood.core.model.household.MealEntryStatus.ARCHIVED,
                ),
            )
        }
        val rejected = (planCommands + entryCommands)
            .map { householdCommandExecutor.execute(it) }
            .filterIsInstance<HouseholdCommandExecutionResult.Rejected>()
        return if (rejected.isEmpty()) {
            refreshCanonicalSummary()
            queueBackendSnapshotSync("canonical_meal_plan")
            "Archived ${activePlans.size} meal plan${activePlans.size.pluralWord} and ${activeEntries.size} planned entr${if (activeEntries.size == 1) "y" else "ies"}."
        } else {
            "Could not archive all meal plans."
        }
    }

    private fun canonicalCommandRecord(
        affectedId: EntityId,
        householdId: com.wonderfood.core.model.household.HouseholdId,
        type: String,
        now: UtcTimestamp,
    ): CommandRecord =
        CommandRecord(
            commandId = CommandId.new(),
            householdId = householdId,
            type = type,
            source = SourceRef(SourceKind.MANUAL, "canonical_runtime"),
            requestedAt = now,
            appliedAt = now,
            affectedEntityIds = listOf(affectedId),
        )

    private fun stableUuid(input: String): String =
        UUID.nameUUIDFromBytes(input.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun Long.toCalendarDate(): CalendarDate =
        CalendarDate(java.time.LocalDate.ofEpochDay(this).toString())

    private fun FoodDraft.canApplyDirectlyToCanonicalHousehold(origin: FoodDraftCommandOrigin): Boolean =
        when (this) {
            is InventoryDraft,
            is GroceryDraft,
            is ReceiptDraft,
            is RecipeDraft,
            is MealLogDraft,
            is MealPlanDraft,
            -> householdDraftCommandMapper.toCommands(this, origin).isNotEmpty()
            is CompositeDraft -> drafts.isNotEmpty() && drafts.all { it.canApplyDirectlyToCanonicalHousehold(origin) }
            is LinkActionDraft,
            -> false
        }

    private data class AiInterpretation(
        val turn: AiTurn,
        val origin: FoodDraftCommandOrigin,
        val autoAcceptAllowed: Boolean,
        val status: String,
        val sources: List<ChatSourceRef> = emptyList(),
    )

    private suspend fun submitToAi(text: String, source: String, promptContext: String? = null, autoAccept: Boolean = false) {
        val sourceMessageId = insertLocalChatMessage(ChatRole.USER, if (source == "voice") "Voice note: $text" else text)
        val memory = canonicalRuntimeMemory(includeLegacyChat = true)
        val configs = liteLlmSettings.readAll()
        val currentState = _uiState.value
        val sourcePackContext = lifeOsSourcePackPromptContext(
            memory = memory,
            backendHome = currentState.backendHome,
            activeDomain = currentState.selectedLifeOsDomain,
        )
        val promptText = promptContext
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it\n\nUser request:\n$text" }
            ?: text
        val interpretation = interpretWithVisibleRetries(promptText, text, memory, promptContext, configs, sourcePackContext)
        val turn = interpretation.turn
        val sources = chatSourcesForTurn(
            memory = memory,
            backendHome = currentState.backendHome,
            activeDomain = currentState.selectedLifeOsDomain,
            status = interpretation.status,
            promptContext = promptContext,
            draft = turn.draft,
            providerSources = interpretation.sources,
        )
        insertLocalChatMessage(ChatRole.ASSISTANT, turn.reply, sources = sources)
        if (autoAccept && turn.draft != null && interpretation.autoAcceptAllowed) {
            val origin = FoodDraftCommandOrigin.VOICE_AUTO_ACCEPT
            val result = if (turn.draft.canApplyDirectlyToCanonicalHousehold(origin)) {
                executeCanonicalDraftCommand(turn.draft, sourceMessageId, origin)
            } else {
                executeDraftCommand(turn.draft, sourceMessageId, origin)
            }
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
        memory: HouseholdUiMemory,
        promptContext: String?,
        configs: List<LiteLlmConfig>,
        sourcePackContext: String,
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
        if (originalText.looksLikeSourcePackRequest()) {
            return AiInterpretation(
                turn = AiTurn(reply = sourcePackAnswer(sourcePackContext), draft = null),
                origin = FoodDraftCommandOrigin.AI_REVIEW,
                autoAcceptAllowed = false,
                status = "Answered from LifeOS source pack context.",
            )
        }
        var lastProviderDiagnostic = ""
        configs.forEachIndexed { index, config ->
            val routeLabel = if (index == 0) "primary" else "fallback"
            _uiState.update {
                it.copy(
                    aiAttemptStatus = "Trying $routeLabel: ${config.statusLabel}.",
                    voiceStatus = "Trying $routeLabel AI provider.",
                )
            }
            when (val result = liteLlmInterpreter.interpretWithDiagnostics(promptText, memory, config, sourcePackContext)) {
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
                        sources = result.sources,
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
        memory: HouseholdUiMemory,
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
        val memory = canonicalRuntimeMemory(includeLegacyChat = true)
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

    private fun canonicalRuntimeMemory(includeLegacyChat: Boolean = false): HouseholdUiMemory {
        val canonicalSnapshot = runBlocking {
            householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
        }
        val canonicalMemory = canonicalSnapshot?.toCanonicalHouseholdUiMemory() ?: HouseholdUiMemory()
        return if (includeLegacyChat) {
            canonicalMemory.copy(messages = localChatMessagesForMemory())
        } else {
            canonicalMemory
        }
    }

    private fun com.wonderfood.core.model.household.HouseholdSnapshot.toCanonicalHouseholdUiMemory(): HouseholdUiMemory {
        val locationById = storageLocations.associateBy { it.metadata.id }
        val lotsByItem = inventoryLots.filter { it.metadata.archivedAt == null }
            .groupBy { it.itemId }
        val recipeIngredientsByRecipe = recipeIngredients
            .filter { it.metadata.archivedAt == null }
            .groupBy { it.recipeId }
        val recipeStepsByRecipe = recipeSteps
            .filter { it.metadata.archivedAt == null }
            .groupBy { it.recipeId }
        val visibleMealEntries = mealEntries.filter { it.metadata.archivedAt == null }
        val mealEntriesByPlan = visibleMealEntries.groupBy { it.mealPlanId }

        val inventory = items
            .asSequence()
            .filter { it.kind == com.wonderfood.core.model.household.ItemKind.FOOD }
            .mapNotNull { item ->
                val lot = lotsByItem[item.metadata.id]?.maxByOrNull { it.metadata.updatedAt.epochMillis }
                if (item.metadata.archivedAt != null) return@mapNotNull null
                val lotLocation = lot?.locationId?.let { locationById[it] }
                InventoryItem(
                    id = stableLegacyId(item.metadata.id.value),
                    name = item.name,
                    quantity = lot?.quantity?.toLegacyQuantityText() ?: "",
                    zone = lotLocation?.type?.toStorageZone() ?: classifyStorageZone(item.name),
                    category = item.category.orEmpty(),
                    servingText = item.foodDetailsId?.let { id ->
                        foodDetails.firstOrNull { it.itemId == id }?.defaultServing?.toLegacyQuantityText().orEmpty()
                    } ?: "",
                    calories = null,
                    proteinGrams = null,
                    carbsGrams = null,
                    fatGrams = null,
                    nutritionSource = "canonical",
                    notes = item.notes.orEmpty(),
                    imageUri = null,
                    expiresAtMillis = lot?.expiresOn?.let { java.time.LocalDate.parse(it.value).toEpochDay() * 86_400_000L },
                    source = item.preferredStore.orEmpty().ifBlank { "canonical" },
                    createdAtMillis = item.metadata.createdAt.epochMillis,
                    updatedAtMillis = item.metadata.updatedAt.epochMillis,
                    imageUrl = "",
                    purchaseDateEpochDay = lot?.purchasedAt?.epochMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay() },
                    purchasePriceCents = lot?.unitCost?.minorUnits,
                    currencyCode = lot?.unitCost?.currencyCode ?: "USD",
                    storeName = item.preferredStore.orEmpty(),
                )
            }.toList()

        val groceries = shoppingLines
            .filter { it.metadata.archivedAt == null }
            .map { line ->
                GroceryItem(
                    id = stableLegacyId(line.metadata.id.value),
                    name = line.displayName,
                    quantity = line.quantity.toLegacyQuantityText(),
                    status = when (line.status) {
                        com.wonderfood.core.model.household.ShoppingLineStatus.PURCHASED -> GroceryStatus.BOUGHT
                        else -> GroceryStatus.NEEDED
                    },
                    category = line.category ?: "",
                    servingText = "",
                    calories = null,
                    proteinGrams = null,
                    carbsGrams = null,
                    fatGrams = null,
                    nutritionSource = "canonical",
                    source = line.reason.name,
                    imageUri = null,
                    createdAtMillis = line.metadata.createdAt.epochMillis,
                    updatedAtMillis = line.metadata.updatedAt.epochMillis,
                    imageUrl = "",
                )
            }

        val canonicalRecipes = recipes
            .asSequence()
            .filter { it.metadata.archivedAt == null }
            .mapNotNull { recipe ->
                val ingredients = recipeIngredientsByRecipe[recipe.metadata.id]
                    ?.sortedBy { it.order }
                    ?.joinToString("\n") { ingredient ->
                        val text = ingredient.originalText.ifBlank { "ingredient" }
                        buildString {
                            append(text)
                            if (ingredient.quantity.isKnown) {
                                append(" (")
                                append(ingredient.quantity.toLegacyQuantityText())
                                append(")")
                            }
                        }
                    }.orEmpty()
                val steps = recipeStepsByRecipe[recipe.metadata.id]
                    ?.sortedBy { it.order }
                    ?.joinToString("\n") { it.instruction }
                    .orEmpty()
                val servings = recipe.yield.amount
                    ?.toBigDecimal()
                    ?.toDouble()
                    ?.toInt()
                if (recipe.name.isBlank()) return@mapNotNull null
                Recipe(
                    id = stableLegacyId(recipe.metadata.id.value),
                    title = recipe.name,
                    ingredients = ingredients,
                    steps = steps,
                    servings = servings,
                    prepMinutes = recipe.prepMinutes,
                    tags = recipe.tags.toList().sorted().joinToString(", "),
                    rating = null,
                    imageUri = null,
                    createdAtMillis = recipe.metadata.createdAt.epochMillis,
                    updatedAtMillis = recipe.metadata.updatedAt.epochMillis,
                    imageUrl = "",
                )
            }
            .toList()

        val mealLogs = visibleMealEntries
            .asSequence()
            .filter { it.status == com.wonderfood.core.model.household.MealEntryStatus.EATEN || it.status == com.wonderfood.core.model.household.MealEntryStatus.COOKED }
            .map { entry ->
                MealLog(
                    id = stableLegacyId(entry.metadata.id.value),
                    title = entry.title,
                    calories = null,
                    proteinGrams = null,
                    carbsGrams = null,
                    fatGrams = null,
                    mealSlot = canonicalMealSlot(entry.slot),
                    usedItemsText = "",
                    loggedDateEpochDay = java.time.Instant.ofEpochMilli(entry.scheduledAt.epochMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay(),
                    source = "canonical",
                    createdAtMillis = entry.metadata.createdAt.epochMillis,
                    updatedAtMillis = entry.metadata.updatedAt.epochMillis,
                )
            }.toList()

        val mealPlans = mealPlans
            .asSequence()
            .filter { it.metadata.archivedAt == null }
            .map { plan ->
                val entries = mealEntriesByPlan[plan.metadata.id].orEmpty()
                val daysText = buildString {
                    append("${entries.size} planned entries")
                    if (entries.isNotEmpty()) {
                        append(", from ")
                        append(java.time.Instant.ofEpochMilli(entries.minByOrNull { it.scheduledAt.epochMillis }!!.scheduledAt.epochMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay())
                        append(" to ")
                        append(java.time.Instant.ofEpochMilli(entries.maxByOrNull { it.scheduledAt.epochMillis }!!.scheduledAt.epochMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay())
                    }
                }
                MealPlan(
                    id = stableLegacyId(plan.metadata.id.value),
                    title = plan.name,
                    daysText = daysText,
                    groceryHint = "",
                    status = if (plan.status == com.wonderfood.core.model.household.MealPlanStatus.ACTIVE) MealPlanStatus.ACCEPTED else MealPlanStatus.DRAFT,
                    startDateEpochDay = null,
                    createdAtMillis = plan.metadata.createdAt.epochMillis,
                    updatedAtMillis = plan.metadata.updatedAt.epochMillis,
                )
            }.toList()

        val mealPlanEntries = visibleMealEntries
            .map { entry ->
                val planId = entry.mealPlanId?.let { stableLegacyId(it.value) }
                MealPlanEntry(
                    id = stableLegacyId(entry.metadata.id.value),
                    planId = planId ?: -1,
                    dateEpochDay = java.time.Instant.ofEpochMilli(entry.scheduledAt.epochMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay(),
                    slot = canonicalMealSlot(entry.slot),
                    title = entry.title,
                    calorieTarget = null,
                    status = when (entry.status) {
                        com.wonderfood.core.model.household.MealEntryStatus.EATEN -> MealPlanEntryStatus.EATEN
                        com.wonderfood.core.model.household.MealEntryStatus.SKIPPED -> MealPlanEntryStatus.SKIPPED
                        com.wonderfood.core.model.household.MealEntryStatus.COOKED -> MealPlanEntryStatus.PLANNED
                        else -> MealPlanEntryStatus.DRAFT
                    },
                    source = "canonical",
                    imageUri = null,
                    imageUrl = "",
                    recipeId = entry.recipeId?.let { stableLegacyId(it.value) },
                    createdAtMillis = entry.metadata.createdAt.epochMillis,
                    updatedAtMillis = entry.metadata.updatedAt.epochMillis,
                )
            }

        val receipts = purchases
            .asSequence()
            .filter { it.metadata.archivedAt == null }
            .map { purchase ->
                val lines = purchaseLines.filter { it.purchaseId == purchase.metadata.id }
                ReceiptCapture(
                    id = stableLegacyId(purchase.metadata.id.value),
                    imageUri = attachments
                        .firstOrNull { attachment ->
                            purchase.receiptAttachmentIds.contains(attachment.metadata.id)
                        }
                        ?.localUri
                        ?: "canonical:${purchase.metadata.id.value}",
                    rawText = lines.joinToString("\n") { it.displayName },
                    status = when (purchase.status) {
                        com.wonderfood.core.model.household.PurchaseStatus.DRAFT -> ReceiptStatus.SAVED
                        com.wonderfood.core.model.household.PurchaseStatus.ARCHIVED -> ReceiptStatus.NEEDS_TEXT
                        else -> ReceiptStatus.EXTRACTED
                    },
                    createdAtMillis = purchase.metadata.createdAt.epochMillis,
                )
            }

        val inventoryTransactions = inventoryEvents
            .map { event ->
                InventoryTransaction(
                    id = stableLegacyId(event.metadata.id.value),
                    inventoryItemId = event.itemId.toString().let(::stableLegacyId),
                    itemName = lotItemName(event.itemId, items),
                    quantityText = event.quantityDelta?.toLegacyQuantityText() ?: "",
                    zone = StorageZone.PANTRY,
                    action = when (event.type) {
                        com.wonderfood.core.model.household.InventoryEventType.ADD -> InventoryAction.ADDED
                        com.wonderfood.core.model.household.InventoryEventType.CONSUME -> InventoryAction.USED
                        com.wonderfood.core.model.household.InventoryEventType.DISCARD -> InventoryAction.REMOVED
                        else -> InventoryAction.UPDATED
                    },
                    reason = event.reason.orEmpty(),
                    relatedRecipeId = event.relatedEntityId?.let { stableLegacyId(it.value) },
                    relatedMealLogId = null,
                    occurredDateEpochDay = java.time.Instant.ofEpochMilli(event.metadata.updatedAt.epochMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay(),
                    source = event.type.name.lowercase(),
                    createdAtMillis = event.metadata.createdAt.epochMillis,
                )
            }

        return HouseholdUiMemory(
            messages = emptyList(),
            actions = emptyList(),
            events = emptyList(),
            inventory = inventory,
            inventoryTransactions = inventoryTransactions,
            groceries = groceries,
            recipes = canonicalRecipes,
            mealLogs = mealLogs,
            mealPlans = mealPlans,
            mealPlanEntries = mealPlanEntries,
            receipts = receipts.toList(),
            preferences = toCanonicalPreferences(),
        )
    }

    private fun com.wonderfood.core.model.household.HouseholdSnapshot.toCanonicalPreferences(): FoodPreferences {
        val profile = profiles.firstOrNull()
        val allergies = profile?.allergies.orEmpty().joinToString(", ")
        val dislikes = profile?.dislikes.orEmpty().joinToString(", ")
        val staples = profile?.dietaryTags.orEmpty().joinToString(", ")
        val caloriesGoal = profile?.nutritionGoals?.values
            ?.firstOrNull { it.value.isNotBlank() }
            ?.toString()
            ?: ""
        return FoodPreferences(
            allergies = allergies,
            dislikes = dislikes,
            preferredStaples = staples,
            calorieGoal = caloriesGoal,
        )
    }

    private fun stableLegacyId(value: String): Long =
        value.fold(1469598103934665603L) { acc, char -> (acc xor char.code.toLong()) * 1099511628211L }.let {
            if (it == Long.MIN_VALUE) 0L else kotlin.math.abs(it)
        }

    private fun com.wonderfood.core.model.household.Quantity.toLegacyQuantityText(): String {
        val amount = this.amount?.value?.trim().orEmpty()
        return if (amount.isBlank()) "" else if (unit == com.wonderfood.core.model.household.QuantityUnit.UNKNOWN) amount else "$amount ${unit.code}"
    }

    private fun canonicalMealSlot(slot: String): MealSlot =
        when (slot.lowercase()) {
            "breakfast" -> MealSlot.BREAKFAST
            "lunch" -> MealSlot.LUNCH
            "dinner" -> MealSlot.DINNER
            "snack" -> MealSlot.SNACK
            else -> MealSlot.FLEX
        }

    private fun lotItemName(itemId: com.wonderfood.core.model.household.EntityId, items: List<com.wonderfood.core.model.household.Item>): String =
        items.firstOrNull { it.metadata.id == itemId }?.name.orEmpty()

    private fun com.wonderfood.core.model.household.StorageLocationType.toStorageZone(): StorageZone =
        when (this) {
            com.wonderfood.core.model.household.StorageLocationType.FRIDGE -> StorageZone.FRIDGE
            com.wonderfood.core.model.household.StorageLocationType.FREEZER -> StorageZone.FREEZER
            else -> StorageZone.PANTRY
        }

    private fun com.wonderfood.core.model.household.CalendarDate.toEpochDay(): Long =
        java.time.LocalDate.parse(value).toEpochDay()


    private suspend fun ensureCanonicalHousehold() {
        val householdId = HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID
        if (householdRepository.snapshot(householdId) != null) return
        val now = UtcTimestamp(System.currentTimeMillis())
        householdCommandExecutor.execute(
            HouseholdCommand.UpsertHousehold(
                record = CommandRecord(
                    commandId = CommandId("00000000-0000-0000-0000-000000000105"),
                    householdId = householdId,
                    type = "UpsertHousehold",
                    source = SourceRef(SourceKind.SYSTEM, "app_start"),
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

    private suspend fun refreshCanonicalSummary() {
        val snapshot = householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
        val summary = CanonicalHouseholdUiSummary.fromSnapshot(snapshot)
        val cartPreview = CanonicalCartPreviewItem.fromSnapshot(snapshot)
        val kitchenPreview = CanonicalKitchenPreviewItem.fromSnapshot(snapshot)
        val spendingPreview = CanonicalRecentSpendingItem.fromSnapshot(snapshot)
        val recipeMatches = CanonicalRecipeMatchItem.fromSnapshot(snapshot)
        val recipePreview = CanonicalSavedRecipeItem.fromSnapshot(snapshot)
        val weekPreview = CanonicalWeekPlanItem.fromSnapshot(snapshot)
        _uiState.update {
            it.copy(
                canonicalSummary = summary,
                canonicalCartPreview = cartPreview,
                canonicalKitchenPreview = kitchenPreview,
                canonicalSpendingPreview = spendingPreview,
                canonicalRecipeMatches = recipeMatches,
                canonicalRecipePreview = recipePreview,
                canonicalWeekPreview = weekPreview,
            )
        }
    }

    private suspend fun canonicalHouseholdSnapshotForBackup() =
        run {
            ensureCanonicalHousehold()
            checkpointCanonicalHouseholdDatabase()
            householdRepository.snapshot(HouseholdDraftCommandMapper.DEFAULT_HOUSEHOLD_ID)
                ?: error("Canonical household was not initialized.")
        }

    private suspend fun canonicalSnapshotForPostgresApi(): WonderFoodSnapshot =
        CanonicalHouseholdSnapshotExporter.toSnapshot(canonicalHouseholdSnapshotForBackup())

    private fun checkpointCanonicalHouseholdDatabase() {
        householdDatabase.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
    }

    private fun closeCanonicalHouseholdDatabase() {
        runCatching { householdDatabase.close() }
    }

    private fun reopenCanonicalHouseholdDatabase() {
        if (householdDatabase.isOpen) return
        householdDatabase = WonderFoodDatabaseFactory.create(appContext)
        householdRepository = HouseholdRepositories.room(householdDatabase)
        householdCommandExecutor = HouseholdCommandExecutor(householdRepository)
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

    private fun readSelectedLifeOsDomainId(): String =
        shellPrefs.getString(KEY_SELECTED_LIFEOS_DOMAIN, "food").orEmpty().ifBlank { "food" }

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

    private fun isTrustedNotionUrl(url: String): Boolean =
        runCatching {
            val uri = Uri.parse(url.trim())
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.orEmpty().lowercase().let { host ->
                    host == "notion.so" ||
                        host.endsWith(".notion.so") ||
                        host == "notion.com" ||
                        host.endsWith(".notion.com")
                }
        }.getOrDefault(false)

    private fun isTrustedSheetsUrl(url: String): Boolean =
        runCatching {
            val uri = Uri.parse(url.trim())
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.orEmpty().lowercase() == "docs.google.com" &&
                uri.path.orEmpty().startsWith("/spreadsheets/")
        }.getOrDefault(false)

    private suspend fun executeDraftCommand(
        draft: FoodDraft,
        sourceMessageId: Long?,
        origin: FoodDraftCommandOrigin,
    ): FoodDraftExecutionResult {
        return executeCanonicalDraftCommand(draft, sourceMessageId, origin)
    }

    private fun mirrorDraftToHouseholdRepository(draft: FoodDraft, origin: FoodDraftCommandOrigin) {
        val commands = householdDraftCommandMapper.toCommands(draft, origin)
        if (commands.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ensureCanonicalHousehold()
                val failures = executeCanonicalHouseholdCommands(commands)
                check(failures.isEmpty()) { "Canonical commands rejected: ${failures.joinToString("; ")}" }
                refreshCanonicalSummary()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(syncStatus = "Canonical repository update failed: ${error.safeMessage()}")
                }
            }
        }
    }

    private suspend fun executeCanonicalHouseholdCommands(commands: List<HouseholdCommand>): List<String> =
        commands.mapNotNull { command ->
            when (val result = householdCommandExecutor.execute(command)) {
                is HouseholdCommandExecutionResult.Applied,
                is HouseholdCommandExecutionResult.Duplicate,
                -> null
                is HouseholdCommandExecutionResult.Rejected ->
                    "${command.record.type}: ${result.errors.joinToString("; ")}"
            }
        }

    private fun recordCanonicalMutationApplied(reason: String, summary: String): String {
        queueBackendSnapshotSync(reason)
        return summary
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
        val householdSnapshot = canonicalHouseholdSnapshotForBackup()
        return when (config) {
            is GoogleSheetsConfig -> {
                val secret = credentialVault.get(config.credentialRef) as? BackendSecret.OAuthAccess ?: return "Google Sheets sync skipped: missing OAuth token"
                val result = googleSheetsGateway.exportGraph(
                    accessToken = secret.accessToken,
                    spreadsheetId = config.spreadsheetId,
                    snapshot = householdSnapshot,
                )
                "Google Sheets synced ${result.rowCount} rows"
            }
            is NotionConfig -> {
                val secret = credentialVault.get(config.credentialRef) as? BackendSecret.BearerToken ?: return "Notion sync skipped: missing token"
                val updatedAt = java.time.Instant.now().toString()
                val workspaceResult = notionGateway.exportWorkspace(
                    token = secret.token,
                    pageId = config.rootPageId,
                    snapshot = householdSnapshot,
                    updatedAt = updatedAt,
                )
                "Notion synced ${workspaceResult.upsertedRows} linked workspace row${workspaceResult.upsertedRows.pluralWord}"
            }
            is PostgresConfig -> {
                val secret = credentialVault.get(config.credentialRef) as? BackendSecret.ApiToken ?: return "${config.connectionMode.label} sync skipped: missing API token"
                val result = postgresGateway.exportSnapshot(
                    mode = config.connectionMode,
                    endpoint = config.endpoint,
                    token = secret.token,
                    householdId = config.householdId,
                    snapshot = CanonicalHouseholdSnapshotExporter.toSnapshot(householdSnapshot),
                    updatedAt = java.time.Instant.now().toString(),
                )
                "${config.connectionMode.label} synced ${result.byteCount} bytes"
            }
            else -> "Backend sync skipped: ${config.type.label} export is not implemented yet"
        }
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
        val sourceMessageId = insertLocalChatMessage(
            ChatRole.USER,
            if (sourceText.isBlank()) "$sourceLabel request." else "$sourceLabel: $sourceText",
        )
        insertLocalChatMessage(ChatRole.ASSISTANT, "$status Nothing is saved until you accept.")
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
            memory = canonicalRuntimeMemory(includeLegacyChat = false),
            promptContext = "Current WonderFood section: Shop. Infer the smallest food-memory operation.",
        )
        return turn.draft as? GroceryDraft
    }

    private fun WonderFoodVoiceCommand.localInventoryDraft(): InventoryDraft? {
        val packedText = text.takeIf { it.contains(",") || it.contains(" and ", ignoreCase = true) } ?: return null
        val turn = interpreter.interpret(
            text = "I have $packedText",
            memory = canonicalRuntimeMemory(includeLegacyChat = false),
            promptContext = "Current WonderFood section: Kitchen. Infer the smallest food-memory operation.",
        )
        return turn.draft as? InventoryDraft
    }

    private fun applyContextualPageEdit(
        text: String,
        target: FoodDetailTarget?,
        memory: HouseholdUiMemory,
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
        val recipeId = runBlocking { canonicalRecipeIdForLegacyRecipeId(recipe.id) } ?: return "Could not update recipe page: canonical recipe not found."
        return runBlocking {
            updateCanonicalRecipe(
                recipeId = recipeId,
                title = title,
                ingredients = ingredients,
                steps = steps,
                servings = servings,
                prepMinutes = prepMinutes,
                tags = tags,
            )
        }
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
        val mealEntryId = runBlocking { canonicalMealEntryIdForLegacyMealLogId(meal.id) }
            ?: return "Could not update meal page: canonical meal not found."
        return runBlocking {
            updateCanonicalMealEntry(
                mealEntryId = mealEntryId,
                title = title,
                mealSlot = mealSlot,
                loggedDateEpochDay = meal.loggedDateEpochDay,
                usedItemsText = usedItemsText,
            )
        }
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

    private fun registerCanonicalUndo(label: String, message: String, commands: List<HouseholdCommand>) {
        pendingUndo = PendingUndo(UndoKind.CANONICAL, 0L, label, emptySet(), commands)
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
        val canonicalCommands: List<HouseholdCommand>? = null,
    )

    private enum class UndoKind {
        CANONICAL,
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
        const val BACKEND_PREFS_NAME = "wonderfood_backend_configuration"
        const val DIRECT_ACTION_PREFS_NAME = "wonderfood_direct_actions"
        const val GOOGLE_SYNC_PREFS_NAME = "wonderfood_google_sync"
        const val KEY_SELECTED_SECTION = "selected_section"
        const val KEY_SELECTED_LIFEOS_DOMAIN = "selected_lifeos_domain"
        const val KEY_GOOGLE_EMAIL = "google_email"
        const val KEY_GOOGLE_NAME = "google_name"
        const val KEY_GOOGLE_WEB_CLIENT_ID = "google_web_client_id"
        const val KEY_BACKEND_SYNC_STATUS = "backend_sync_status"
        const val KEY_WORKSPACE_CONFLICT_INBOX = "workspace_conflict_inbox"
        const val KEY_TEMPLATE_NOTION_URL = "template_notion_url"
        const val KEY_TEMPLATE_SHEETS_URL = "template_sheets_url"
        const val KEY_LOCAL_CHAT_HISTORY = "local_chat_history_v1"
        const val PREFERENCE_AUTO_SAVE_DELAY_MILLIS = 450L
        const val BACKEND_SNAPSHOT_SYNC_DEBOUNCE_MILLIS = 900L
    }
}

private val Int.pluralWord: String
    get() = if (this == 1) "" else "s"

private fun Throwable.safeMessage(): String =
    message?.take(140)?.ifBlank { null } ?: "unknown error"

private fun String.looksLikeSourcePackRequest(): Boolean {
    val lower = lowercase()
        .replace("%20", " ")
        .replace("+", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val asksAboutSourcePack = "source pack" in lower ||
        "source-pack" in lower ||
        ("sources" in lower && ("lifeos" in lower || "notion" in lower || "sheets" in lower || "mcp" in lower))
    val asksAboutSync = ("sync loop" in lower || "data plane" in lower) &&
        ("lifeos" in lower || "notion" in lower || "sheets" in lower || "mcp" in lower)
    val asksAboutArchitecture = ("what can you cite" in lower || "what are your sources" in lower) &&
        ("app" in lower || "notion" in lower || "sheets" in lower)
    return asksAboutSourcePack || asksAboutSync || asksAboutArchitecture
}

private fun GroceryStatus.toCanonicalShoppingLineStatus(): com.wonderfood.core.model.household.ShoppingLineStatus =
    when (this) {
        GroceryStatus.NEEDED -> com.wonderfood.core.model.household.ShoppingLineStatus.NEEDED
        GroceryStatus.BOUGHT -> com.wonderfood.core.model.household.ShoppingLineStatus.PURCHASED
    }

private fun MealPlanEntryStatus.toCanonicalMealEntryStatus(): com.wonderfood.core.model.household.MealEntryStatus =
    when (this) {
        MealPlanEntryStatus.DRAFT -> com.wonderfood.core.model.household.MealEntryStatus.PROPOSED
        MealPlanEntryStatus.PLANNED -> com.wonderfood.core.model.household.MealEntryStatus.PLANNED
        MealPlanEntryStatus.EATEN -> com.wonderfood.core.model.household.MealEntryStatus.EATEN
        MealPlanEntryStatus.SKIPPED -> com.wonderfood.core.model.household.MealEntryStatus.SKIPPED
    }

private fun WonderFoodSnapshot.hasUserData(): Boolean =
    foods.isNotEmpty() ||
        stockLots.isNotEmpty() ||
        recipes.isNotEmpty() ||
        mealPlans.isNotEmpty() ||
        mealLogs.isNotEmpty() ||
        shoppingItems.isNotEmpty() ||
        receipts.isNotEmpty() ||
        foodEvents.isNotEmpty()

private fun V4InboundWorkspaceImportResult.toSheetsImportPreview(
    workspaceUrl: String,
    workspaceRowCount: Int,
    providerLabel: String = "Google Sheets",
    mergeClock: String,
): SheetsImportPreview =
    SheetsImportPreview(
        spreadsheetUrl = workspaceUrl,
        providerLabel = providerLabel,
        sourceLabel = "V4 linked workspace",
        schemaVersion = 4,
        foodCount = commands.filterIsInstance<HouseholdCommand.UpsertItem>().size,
        stockLotCount = commands.filterIsInstance<HouseholdCommand.UpsertInventoryLot>().size,
        shoppingItemCount = commands.filterIsInstance<HouseholdCommand.UpsertShoppingLine>().size,
        recipeCount = commands.filterIsInstance<HouseholdCommand.UpsertRecipe>().size,
        mealPlanCount = 0,
        mealLogCount = commands.filterIsInstance<HouseholdCommand.UpsertMealEntry>().size,
        eventCount = 0,
        workspaceRowCount = workspaceRowCount,
        changeCount = commands.size,
        conflictCount = diagnostics.size,
        fieldClockCount = 0,
        mergeClock = mergeClock,
        conflictSummary = diagnostics.take(4).map { "${it.surface.label}: ${it.field} - ${it.message}" },
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
    val memory: HouseholdUiMemory = HouseholdUiMemory(),
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
    val canonicalSummary: CanonicalHouseholdUiSummary = CanonicalHouseholdUiSummary(),
    val canonicalCartPreview: List<CanonicalCartPreviewItem> = emptyList(),
    val canonicalKitchenPreview: List<CanonicalKitchenPreviewItem> = emptyList(),
    val canonicalSpendingPreview: List<CanonicalRecentSpendingItem> = emptyList(),
    val canonicalRecipeMatches: List<CanonicalRecipeMatchItem> = emptyList(),
    val canonicalRecipePreview: List<CanonicalSavedRecipeItem> = emptyList(),
    val canonicalWeekPreview: List<CanonicalWeekPlanItem> = emptyList(),
    val canonicalSearchQuery: String = "",
    val canonicalSearchItems: List<CanonicalHouseholdSearchItem> = emptyList(),
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
    val lifeOsDomains: List<LifeOsDomain> = emptyList(),
    val selectedLifeOsDomainId: String = "food",
)

val WonderFoodUiState.selectedLifeOsDomain: LifeOsDomain?
    get() = lifeOsDomains.firstOrNull { it.id == selectedLifeOsDomainId } ?: lifeOsDomains.firstOrNull()

data class BackendHomeUiState(
    val activeType: BackendType? = null,
    val label: String = "Choose data home",
    val detail: String = "Pick where WonderFood keeps kitchen, plan, recipe and shopping data.",
    val requiresOnboarding: Boolean = true,
    val sheetUrl: String = "",
    val dataPlaneUrl: String = "",
    val externalId: String = "",
    val accountLabel: String = "",
    val proofLabel: String = "",
    val message: String = "",
    val safetyMessage: String = "",
    val templateNotionUrl: String = "",
    val templateSheetsUrl: String = "",
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
                    proofLabel = "Local Room/SQLite store",
                )
                is GoogleSheetsConfig -> BackendHomeUiState(
                    activeType = BackendType.GOOGLE_SHEETS,
                    label = "Google Sheets",
                    detail = "Sheet connected. Schema check and sync are next.",
                    requiresOnboarding = false,
                    sheetUrl = config.spreadsheetUrl,
                    dataPlaneUrl = config.spreadsheetUrl,
                    externalId = config.spreadsheetId,
                    accountLabel = config.accountEmail.orEmpty(),
                    proofLabel = "Google Sheets workbook",
                )
                is NotionConfig -> BackendHomeUiState(
                    activeType = BackendType.NOTION,
                    label = "Notion",
                    detail = "Notion page connected. Workspace databases and sync contracts are saved.",
                    requiresOnboarding = false,
                    dataPlaneUrl = config.pageUrl,
                    externalId = config.rootPageId,
                    accountLabel = config.workspaceName.orEmpty(),
                    proofLabel = "Notion root page",
                )
                is PostgresConfig -> BackendHomeUiState(
                    activeType = BackendType.POSTGRES,
                    label = when (config.connectionMode) {
                        PostgresConnectionMode.POSTGREST -> "PostgREST"
                        PostgresConnectionMode.WONDERFOOD_SERVER -> "WonderFood server"
                    },
                    detail = "Hosted snapshot endpoint connected for household ${config.householdId}.",
                    requiresOnboarding = false,
                    dataPlaneUrl = config.endpoint,
                    externalId = config.householdId,
                    proofLabel = "Hosted endpoint",
                )
                null -> BackendHomeUiState(requiresOnboarding = !onboardingDismissed)
            }
    }
}

val BackendType.label: String
    get() = when (this) {
        BackendType.LOCAL_SQLITE -> "On this phone"
        BackendType.GOOGLE_SHEETS -> "Google Sheets"
        BackendType.NOTION -> "Notion"
        BackendType.POSTGRES -> "Postgres"
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

private fun FoodDetailTarget?.toAiPromptContext(memory: HouseholdUiMemory, section: FoodSection): String? =
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
