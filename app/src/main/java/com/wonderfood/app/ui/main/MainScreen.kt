package com.wonderfood.app.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wonderfood.app.ai.AiProvider
import com.wonderfood.app.ai.LiteLlmConfig
import com.wonderfood.app.WonderFoodVoiceCommand
import com.wonderfood.app.data.ChatMessage
import com.wonderfood.app.data.ChatSourceRef
import com.wonderfood.app.data.ChatAction
import com.wonderfood.app.data.ChatActionStatus
import com.wonderfood.app.data.ChatRole
import com.wonderfood.app.data.CanonicalCartPreviewItem
import com.wonderfood.app.data.CanonicalHouseholdSearchItem
import com.wonderfood.app.data.CanonicalHouseholdUiSummary
import com.wonderfood.app.data.CanonicalKitchenPreviewItem
import com.wonderfood.app.data.CanonicalRecentSpendingItem
import com.wonderfood.app.data.CanonicalRecipeMatchItem
import com.wonderfood.app.data.CanonicalSavedRecipeItem
import com.wonderfood.app.data.CanonicalWeekPlanItem
import com.wonderfood.app.data.CompositeDraft
import com.wonderfood.app.data.FoodCandidate
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodDraftCommandOrigin
import com.wonderfood.app.data.FoodEvent
import com.wonderfood.app.data.FoodEventType
import com.wonderfood.app.data.HouseholdUiMemory
import com.wonderfood.app.data.FoodOperation
import com.wonderfood.app.data.GroceryItem
import com.wonderfood.app.data.GroceryDraft
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.InventoryAction
import com.wonderfood.app.data.InventoryDraft
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.InventoryTransaction
import com.wonderfood.app.data.MealLog
import com.wonderfood.app.data.MealLogDraft
import com.wonderfood.app.data.MealPlan
import com.wonderfood.app.data.MealPlanDraft
import com.wonderfood.app.data.MealPlanEntry
import com.wonderfood.app.data.MealPlanEntryStatus
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.LinkActionDraft
import com.wonderfood.app.data.LifeOsDomain
import com.wonderfood.app.data.FoodPreferences
import com.wonderfood.app.data.Recipe
import com.wonderfood.app.data.RecipeDraft
import com.wonderfood.core.data.room.WonderFoodDatabase
import com.wonderfood.app.data.ReceiptCapture
import com.wonderfood.app.data.ReceiptDraft
import com.wonderfood.app.data.ReceiptItemDisposition
import com.wonderfood.app.data.ReceiptStatus
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.data.foodEmojiForName
import com.wonderfood.app.data.receiptMoney
import com.wonderfood.app.health.HealthDailySummary
import com.wonderfood.app.integration.capture.FoodCaptureContracts
import com.wonderfood.app.sync.GoogleDriveAccess
import com.wonderfood.app.sync.GoogleDriveAuthorization
import com.wonderfood.app.sync.GoogleSheetsAuthorization
import com.wonderfood.app.sync.GoogleSignInGateway
import com.wonderfood.app.sync.WonderFoodCsvGateway
import com.wonderfood.app.theme.WonderFoodTheme
import com.wonderfood.app.theme.WonderFoodThemeMode
import com.wonderfood.core.data.backend.BackendType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.launch
import java.util.Locale

private typealias InventoryUpdateHandler = (
    Long,
    String,
    String,
    StorageZone,
    String,
    String,
    Int?,
    Double?,
    Double?,
    Double?,
    String,
    String,
    String?,
    String,
    Long?,
) -> Unit
private typealias GroceryUpdateHandler = (
    Long,
    String,
    String,
    GroceryStatus,
    String,
    String,
    Int?,
    Double?,
    Double?,
    Double?,
    String,
    String,
    String?,
    String,
) -> Unit
private typealias RecipeUpdateHandler = (Long, String, String, String, Int?, Int?, String, String?, String) -> Unit
private typealias MealUpdateHandler = (Long, String, Int?, Double?, Double?, Double?, MealSlot, String, Long, String) -> Unit
private typealias MealPlanUpdateHandler = (Long, String, String, String, Long?) -> Unit
private typealias MealPlanEntryCreateHandler = (Long, MealSlot, String, Int?) -> Unit
private typealias MealPlanEntryUpdateHandler = (Long, Long, MealSlot, String, Int?, MealPlanEntryStatus) -> Unit
private typealias ReceiptUpdateHandler = (Long, String, ReceiptStatus) -> Unit

private enum class GoogleSyncAction {
    BACKUP,
    RESTORE,
}

private const val CREATE_GOOGLE_SHEET_REQUEST = "wonderfood:create-google-sheet"

private data class FoodPrimaryAction(
    val label: String,
    val contentDescription: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun MainScreen(
    themeMode: WonderFoodThemeMode,
    onThemeModeChange: (WonderFoodThemeMode) -> Unit,
    voiceCommand: WonderFoodVoiceCommand?,
    onVoiceCommandConsumed: (WonderFoodVoiceCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(context) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHealthStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val healthLauncher =
        rememberLauncherForActivityResult(viewModel.healthPermissionContract) {
            viewModel.refreshHealthStatus()
        }
    var pendingRecipeImageId by remember { mutableStateOf<Long?>(null) }
    var voiceNoteAutoAcceptForResult by rememberSaveable { mutableStateOf(false) }
    var pendingReceiptUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingReceiptNote by rememberSaveable { mutableStateOf("") }
    val receiptPhotoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            pendingReceiptUri = uri
            if (uri == null) pendingReceiptNote = ""
        }
    val recipePhotoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val recipeId = pendingRecipeImageId
            if (recipeId != null && uri != null) viewModel.updateRecipeImage(recipeId, uri.toString())
            pendingRecipeImageId = null
        }
    val voiceNoteLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val text = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()
                viewModel.sendVoiceNote(text, autoAccept = voiceNoteAutoAcceptForResult)
            }
            voiceNoteAutoAcceptForResult = false
        }
    val csvExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) viewModel.exportCsvTo(uri)
        }
    val csvImportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.importDataFrom(uri)
        }
    val googleScope = rememberCoroutineScope()
    val googleSignInGateway = remember(context) { GoogleSignInGateway(context) }
    var pendingGoogleAction by rememberSaveable { mutableStateOf<GoogleSyncAction?>(null) }
    var pendingGoogleEmail by rememberSaveable { mutableStateOf("") }
    var pendingGoogleSheetUrl by rememberSaveable { mutableStateOf("") }
    fun runGoogleAction(accessToken: String) {
        val access = GoogleDriveAccess(
            accessToken = accessToken,
            accountEmail = pendingGoogleEmail.ifBlank { state.googleAccountEmail },
        )
        when (pendingGoogleAction) {
            GoogleSyncAction.BACKUP -> viewModel.backupToGoogleDrive(access)
            GoogleSyncAction.RESTORE -> viewModel.restoreFromGoogleDrive(access)
            null -> viewModel.onGoogleSyncError("Google Drive action expired. Try again.")
        }
        pendingGoogleAction = null
    }
    val googleDriveAuthorizationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                pendingGoogleAction = null
                viewModel.onGoogleSyncError("Google Drive permission was cancelled.")
                return@rememberLauncherForActivityResult
            }
            runCatching {
                GoogleDriveAuthorization.accessTokenFromResultIntent(context, result.data)
            }.onSuccess(::runGoogleAction)
                .onFailure { error ->
                    pendingGoogleAction = null
                    viewModel.onGoogleSyncError("Google Drive permission failed: ${error.message ?: "unknown error"}")
                }
        }
    val googleSheetsAuthorizationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                pendingGoogleSheetUrl = ""
                viewModel.onGoogleSyncError("Google Sheets permission was cancelled.")
                return@rememberLauncherForActivityResult
            }
            runCatching {
                GoogleSheetsAuthorization.accessTokenFromResultIntent(context, result.data)
            }.onSuccess { accessToken ->
                if (pendingGoogleSheetUrl == CREATE_GOOGLE_SHEET_REQUEST) {
                    viewModel.createGoogleSheetsBackend(
                        accountEmail = pendingGoogleEmail.ifBlank { state.googleAccountEmail },
                        accessToken = accessToken,
                    )
                } else {
                    viewModel.connectGoogleSheetsBackend(
                        sheetInput = pendingGoogleSheetUrl,
                        accountEmail = pendingGoogleEmail.ifBlank { state.googleAccountEmail },
                        accessToken = accessToken,
                    )
                }
                pendingGoogleSheetUrl = ""
            }.onFailure { error ->
                pendingGoogleSheetUrl = ""
                viewModel.onGoogleSyncError("Google Sheets permission failed: ${error.message ?: "unknown error"}")
            }
        }
    fun requestGoogleDriveAccess(action: GoogleSyncAction) {
        val activity = context as? Activity ?: run {
            viewModel.onGoogleSyncError("Google Drive backup needs an active Android screen.")
            return
        }
        pendingGoogleAction = action
        val accountEmail = pendingGoogleEmail.ifBlank { state.googleAccountEmail }
        GoogleDriveAuthorization.requestAccess(
            activity = activity,
            accountEmail = accountEmail,
            onResolution = googleDriveAuthorizationLauncher::launch,
            onAccessToken = ::runGoogleAction,
            onFailure = { error ->
                pendingGoogleAction = null
                viewModel.onGoogleSyncError("Google Drive permission failed: ${error.message ?: "unknown error"}")
            },
        )
    }
    fun signInAndRequestGoogleDrive(action: GoogleSyncAction) {
        googleScope.launch {
            runCatching {
                googleSignInGateway.signIn(state.googleOAuthClientId)
            }.onSuccess { profile ->
                pendingGoogleEmail = profile.email
                viewModel.onGoogleSignIn(profile)
                requestGoogleDriveAccess(action)
            }.onFailure { error ->
                pendingGoogleAction = null
                viewModel.onGoogleSyncError("Google sign-in failed: ${error.message ?: "unknown error"}")
            }
        }
    }
    fun signInGoogleOnly() {
        googleScope.launch {
            runCatching {
                googleSignInGateway.signIn(state.googleOAuthClientId)
            }.onSuccess { profile ->
                pendingGoogleEmail = profile.email
                viewModel.onGoogleSignIn(profile)
            }.onFailure { error ->
                viewModel.onGoogleSyncError("Google sign-in failed: ${error.message ?: "unknown error"}")
            }
        }
    }
    fun requestGoogleSheetsAccess(sheetUrl: String, accountEmail: String) {
        val activity = context as? Activity ?: run {
            viewModel.onGoogleSyncError("Google Sheets needs an active Android screen.")
            return
        }
        pendingGoogleSheetUrl = sheetUrl
        GoogleSheetsAuthorization.requestAccess(
            activity = activity,
            accountEmail = accountEmail,
            onResolution = googleSheetsAuthorizationLauncher::launch,
            onAccessToken = { accessToken ->
                if (sheetUrl == CREATE_GOOGLE_SHEET_REQUEST) {
                    viewModel.createGoogleSheetsBackend(
                        accountEmail = accountEmail,
                        accessToken = accessToken,
                    )
                } else {
                    viewModel.connectGoogleSheetsBackend(
                        sheetInput = sheetUrl,
                        accountEmail = accountEmail,
                        accessToken = accessToken,
                    )
                }
                pendingGoogleSheetUrl = ""
            },
            onFailure = { error ->
                pendingGoogleSheetUrl = ""
                viewModel.onGoogleSyncError("Google Sheets permission failed: ${error.message ?: "unknown error"}")
            },
        )
    }
    fun signInAndConnectGoogleSheets(sheetUrl: String) {
        val clientId = state.googleOAuthClientId.trim()
        if (clientId.isBlank() || clientId.startsWith("TODO_")) {
            viewModel.onGoogleSyncError(
                "Paste the Google Web OAuth client ID in Settings > Backup & restore before using Google Sheets.",
            )
            return
        }
        googleScope.launch {
            runCatching {
                googleSignInGateway.signIn(clientId)
            }.onSuccess { profile ->
                pendingGoogleEmail = profile.email
                viewModel.onGoogleSignIn(profile)
                requestGoogleSheetsAccess(sheetUrl, profile.email)
            }.onFailure { error ->
                viewModel.onGoogleSyncError("Google sign-in failed: ${error.message ?: "unknown error"}")
            }
        }
    }
    fun signInAndCreateGoogleSheets() {
        val clientId = state.googleOAuthClientId.trim()
        if (clientId.isBlank() || clientId.startsWith("TODO_")) {
            viewModel.onGoogleSyncError(
                "Paste the Google Web OAuth client ID in Settings > Backup & restore before using Google Sheets.",
            )
            return
        }
        googleScope.launch {
            runCatching {
                googleSignInGateway.signIn(clientId)
            }.onSuccess { profile ->
                pendingGoogleEmail = profile.email
                viewModel.onGoogleSignIn(profile)
                requestGoogleSheetsAccess(CREATE_GOOGLE_SHEET_REQUEST, profile.email)
            }.onFailure { error ->
                viewModel.onGoogleSyncError("Google sign-in failed: ${error.message ?: "unknown error"}")
            }
        }
    }
    LaunchedEffect(voiceCommand?.id) {
        val command = voiceCommand ?: return@LaunchedEffect
        viewModel.handleVoiceCommand(command)
        onVoiceCommandConsumed(command)
    }
    LaunchedEffect(state.guidedVoiceNonce) {
        if (state.guidedVoiceNonce == 0L || state.guidedVoicePrompt.isBlank()) return@LaunchedEffect
        voiceNoteAutoAcceptForResult = state.guidedVoiceAutoAccept
        voiceNoteLauncher.launch(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, state.guidedVoicePrompt)
            },
        )
    }

    WonderFoodScreen(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onNewChat = viewModel::startNewChat,
        onSectionSelected = viewModel::selectSection,
        onAcceptDraft = viewModel::acceptDraft,
        onRejectDraft = viewModel::rejectDraft,
        onDraftChange = viewModel::updatePendingDraft,
        onClearChatHistory = viewModel::clearChatHistory,
        onDeleteInventory = viewModel::deleteInventory,
        onUpdateInventory = viewModel::updateInventory,
        onDeleteGrocery = viewModel::deleteGrocery,
        onUpdateGrocery = viewModel::updateGrocery,
        onMarkGroceryBought = viewModel::markGroceryBought,
        onMarkCanonicalCartLinePurchased = viewModel::markCanonicalCartLinePurchased,
        onArchiveCanonicalCartLine = viewModel::archiveCanonicalCartLine,
        onAddCanonicalKitchenItemToCart = viewModel::addCanonicalKitchenItemToCart,
        onArchiveCanonicalKitchenItem = viewModel::archiveCanonicalKitchenItem,
        onDeleteRecipe = viewModel::deleteRecipe,
        onCookRecipe = viewModel::cookRecipe,
        onAddRecipeMissingToList = viewModel::addMissingRecipeGroceries,
        onUpdateRecipe = viewModel::updateRecipe,
        onPickRecipeImage = { recipeId ->
            pendingRecipeImageId = recipeId
            recipePhotoLauncher.launch(
                FoodCaptureContracts.imageOnlyRequest(),
            )
        },
        onDeleteMeal = viewModel::deleteMealLog,
        onUpdateMeal = viewModel::updateMealLog,
        onUpdateMealPlan = viewModel::updateMealPlan,
        onAddMealPlanEntry = viewModel::addMealPlanEntry,
        onUpdateMealPlanEntry = viewModel::updateMealPlanEntry,
        onDeleteMealPlanEntry = viewModel::deleteMealPlanEntry,
        onDeleteMealPlanEntries = viewModel::deleteMealPlanEntries,
        onDeleteAllPlans = viewModel::deleteAllMealPlans,
        onUpdateReceipt = viewModel::updateReceipt,
        onExportMeal = viewModel::exportMeal,
        onOpenDetail = viewModel::openDetail,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onCloseDetail = viewModel::closeDetail,
        onPreferencesChange = viewModel::onPreferencesChange,
        onSavePreferences = viewModel::savePreferences,
        onAiConfigChange = viewModel::onAiConfigChange,
        onAiFallbackConfigChange = viewModel::onAiFallbackConfigChange,
        onSaveAiConfig = viewModel::saveAiConfig,
        onCreateEncryptedBackup = viewModel::createEncryptedBackup,
        onRestoreLatestEncryptedBackup = viewModel::restoreLatestEncryptedBackup,
        onGoogleOAuthClientIdChange = viewModel::onGoogleOAuthClientIdChange,
        onConnectGoogle = ::signInGoogleOnly,
        onBackupToGoogleDrive = { signInAndRequestGoogleDrive(GoogleSyncAction.BACKUP) },
        onRestoreFromGoogleDrive = { signInAndRequestGoogleDrive(GoogleSyncAction.RESTORE) },
        onDisconnectGoogle = viewModel::disconnectGoogleSync,
        onExportCsv = {
            csvExportLauncher.launch(WonderFoodCsvGateway.defaultExportName())
        },
        onImportCsv = {
            csvImportLauncher.launch(
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "text/*",
                    "application/json",
                    "application/vnd.wonderfood.proposal+json",
                    "application/vnd.ms-excel",
                ),
            )
        },
        onTestAiConnection = viewModel::testAiConnection,
        onDeleteAllAppData = viewModel::deleteAllAppData,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onPickReceiptPhoto = {
            receiptPhotoLauncher.launch(
                FoodCaptureContracts.imageOnlyRequest(),
            )
        },
        onRecordVoiceNote = {
            voiceNoteAutoAcceptForResult = false
            voiceNoteLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell WonderFood what changed")
                },
            )
        },
        onRequestHealthConnect = { healthLauncher.launch(viewModel.healthPermissions) },
        onCreateManual = viewModel::createManual,
        onLogWater = viewModel::logWater,
        onUndo = viewModel::undoLastAction,
        onDismissFeedback = viewModel::clearFeedback,
        onChooseLocalBackend = viewModel::chooseLocalBackend,
        onValidateGoogleSheetsBackend = ::signInAndConnectGoogleSheets,
        onCreateGoogleSheetsBackend = ::signInAndCreateGoogleSheets,
        onConnectNotionBackend = viewModel::connectNotionBackend,
        onConnectPostgresBackend = viewModel::connectPostgresBackend,
        onSelectPendingBackend = viewModel::selectPendingBackend,
        onSelectLifeOsDomain = viewModel::selectLifeOsDomain,
        onDismissBackendOnboarding = viewModel::dismissBackendOnboardingForNow,
        onClearWorkspaceConflictInbox = viewModel::clearWorkspaceConflictInbox,
        modifier = modifier,
    )
    state.csvImportPreview?.let { preview ->
        CsvImportPreviewDialog(
            preview = preview,
            onConfirm = viewModel::confirmCsvImportPreview,
            onDismiss = viewModel::cancelCsvImportPreview,
        )
    }
    state.googleRestorePreview?.let { preview ->
        GoogleRestorePreviewDialog(
            preview = preview,
            onConfirm = viewModel::confirmGoogleRestorePreview,
            onDismiss = viewModel::cancelGoogleRestorePreview,
        )
    }
    state.sheetsImportPreview?.let { preview ->
        SheetsImportPreviewDialog(
            preview = preview,
            onConfirm = viewModel::confirmSheetsImportPreview,
            onDismiss = viewModel::cancelSheetsImportPreview,
        )
    }
    pendingReceiptUri?.let { uri ->
        ReceiptNoteDialog(
            note = pendingReceiptNote,
            onNoteChange = { pendingReceiptNote = it },
            onAnalyze = {
                viewModel.attachReceiptPhoto(uri, pendingReceiptNote)
                pendingReceiptUri = null
                pendingReceiptNote = ""
            },
            onDismiss = {
                pendingReceiptUri = null
                pendingReceiptNote = ""
            },
        )
    }
}

@Composable
private fun ReceiptNoteDialog(
    note: String,
    onNoteChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add receipt context") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Optional: add the store, what you bought, corrections, or any hard-to-read lines before analysis.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { onNoteChange(it.take(2_000)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Receipt context note" },
                    label = { Text("Receipt note") },
                    placeholder = { Text("Bought for pantry; line 4 is oat milk…") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(18.dp),
                )
                Text(
                    "The photo and this note are staged for review. Nothing is added until you approve the proposal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onAnalyze, shape = RoundedCornerShape(18.dp)) { Text("Analyze receipt") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(18.dp)) { Text("Cancel") }
        },
    )
}

@Composable
private fun CsvImportPreviewDialog(
    preview: CsvImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import preview") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Import mode: Merge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                CsvPreviewLine("Kitchen items", preview.inventoryCount.toString())
                CsvPreviewLine("Shopping items", preview.groceryCount.toString())
                CsvPreviewLine("Recipes", preview.recipeCount.toString())
                CsvPreviewLine("Meals", preview.mealCount.toString())
                CsvPreviewLine("Plans", preview.planCount.toString())
                CsvPreviewLine("Preferences", if (preview.importsPreferences) "Yes" else "No")
                HorizontalDivider()
                Text(
                    "Rows are added to local SQLite. Existing rows are not deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(18.dp)) {
                Text("Import")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(18.dp)) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CsvPreviewLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SheetsImportPreviewDialog(
    preview: SheetsImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${preview.providerLabel} already has WonderFood data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Import mode: Review first", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                CsvPreviewLine("Source", preview.sourceLabel)
                if (preview.workspaceRowCount > 0) {
                    CsvPreviewLine("Workspace rows", preview.workspaceRowCount.toString())
                    CsvPreviewLine("Merge changes", preview.changeCount.toString())
                    CsvPreviewLine("Conflicts", preview.conflictCount.toString())
                    CsvPreviewLine("Field clocks", preview.fieldClockCount.toString())
                }
                CsvPreviewLine("Kitchen foods", preview.foodCount.toString())
                CsvPreviewLine("Stock lots", preview.stockLotCount.toString())
                CsvPreviewLine("Shopping items", preview.shoppingItemCount.toString())
                CsvPreviewLine("Recipes", preview.recipeCount.toString())
                CsvPreviewLine("Meal plans", preview.mealPlanCount.toString())
                CsvPreviewLine("Meal logs", preview.mealLogCount.toString())
                CsvPreviewLine("Events", preview.eventCount.toString())
                if (preview.mergeClock.isNotBlank()) {
                    CsvPreviewLine("Merge clock", preview.mergeClock)
                }
                if (preview.conflictSummary.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Needs attention", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    preview.conflictSummary.forEach { conflict ->
                        Text(conflict, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
                Text(
                    if (preview.conflictCount > 0) {
                        "WonderFood can preserve ${preview.providerLabel} and apply valid changes, but conflicts should be reviewed so household data is not duplicated or silently overwritten."
                    } else {
                        "WonderFood preserved this ${preview.providerLabel} workspace instead of overwriting it. Import/merge needs an explicit review step so household data is not duplicated or deleted accidentally."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(18.dp)) {
                Text("Use ${preview.providerLabel} data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(18.dp)) {
                Text("Preserve only")
            }
        },
    )
}

@Composable
private fun GoogleRestorePreviewDialog(
    preview: GoogleRestorePreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore preview") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Restore mode: Replace local database", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                GoogleRestoreLine("Backup", preview.fileName)
                GoogleRestoreLine("Created", preview.createdAtMillis.restoreDateLabel())
                GoogleRestoreLine("Device", preview.device)
                GoogleRestoreLine("Schema", if (preview.schemaVersion > 0) "v${preview.schemaVersion}" else "Unknown")
                GoogleRestoreLine("Size", "${preview.sizeBytes / 1024} KB")
                HorizontalDivider()
                GoogleRestoreLine("Kitchen", preview.inventoryCount.toString())
                GoogleRestoreLine("Shopping", preview.groceryCount.toString())
                GoogleRestoreLine("Recipes", preview.recipeCount.toString())
                GoogleRestoreLine("Meals", preview.mealCount.toString())
                GoogleRestoreLine("Plans", preview.mealPlanCount.toString())
                GoogleRestoreLine("Plan entries", preview.planEntryCount.toString())
                GoogleRestoreLine("Chat messages", preview.messageCount.toString())
                Text(
                    "A safety backup of current local data will be created before restore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(18.dp)) {
                Text("Restore")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(18.dp)) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun GoogleRestoreLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.widthIn(min = 96.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BackendOnboardingDialog(
    backendHome: BackendHomeUiState,
    conflictInbox: WorkspaceConflictInbox?,
    onUseLocal: () -> Unit,
    onConnectSheets: (String) -> Unit,
    onCreateSheets: () -> Unit,
    onConnectNotion: (String, String) -> Unit,
    onConnectPostgres: (String, String, String) -> Unit,
    onSelectPending: (BackendType) -> Unit,
    onClearConflictInbox: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedBackend by rememberSaveable(backendHome.activeType) {
        mutableStateOf(backendHome.activeType ?: BackendType.LOCAL_SQLITE)
    }
    var sheetUrl by rememberSaveable(backendHome.sheetUrl) { mutableStateOf(backendHome.sheetUrl) }
    var notionPageUrl by rememberSaveable { mutableStateOf("") }
    var notionToken by rememberSaveable { mutableStateOf("") }
    var postgresEndpoint by rememberSaveable { mutableStateOf("") }
    var postgresHousehold by rememberSaveable { mutableStateOf("home") }
    var postgresToken by rememberSaveable { mutableStateOf("") }
    var switchAcknowledged by rememberSaveable { mutableStateOf(false) }
    val activeType = backendHome.activeType
    val isBackendSwitch = activeType != null && selectedBackend != activeType
    val switchReady = !isBackendSwitch || switchAcknowledged
    val scrollState = rememberScrollState()
    LaunchedEffect(selectedBackend, isBackendSwitch) {
        if (selectedBackend == BackendType.LOCAL_SQLITE) {
            scrollState.animateScrollTo(0)
        } else {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose your food home")
                Text(
                    "One place stays in charge. WonderFood keeps a rollback snapshot before switching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                conflictInbox?.let { inbox ->
                    WorkspaceConflictInboxCard(
                        inbox = inbox,
                        onClear = onClearConflictInbox,
                    )
                }
                BackendDataPlaneProofCard(backendHome)
                BackendChoiceRow(
                    icon = Icons.Rounded.Kitchen,
                    title = "This phone",
                    subtitle = "Fastest setup. Private and offline.",
                    helper = "Best if you just want to start now.",
                    selected = selectedBackend == BackendType.LOCAL_SQLITE,
                    onClick = {
                        selectedBackend = BackendType.LOCAL_SQLITE
                        switchAcknowledged = false
                        onSelectPending(BackendType.LOCAL_SQLITE)
                    },
                )
                BackendChoiceRow(
                    icon = Icons.Rounded.Description,
                    title = "Google Sheets",
                    subtitle = "Shared spreadsheet. Easy family editing.",
                    helper = "Paste a Sheet link and approve Google access.",
                    selected = selectedBackend == BackendType.GOOGLE_SHEETS,
                    onClick = {
                        selectedBackend = BackendType.GOOGLE_SHEETS
                        switchAcknowledged = false
                        onSelectPending(BackendType.GOOGLE_SHEETS)
                    },
                )
                BackendChoiceRow(
                    icon = Icons.Rounded.Storefront,
                    title = "Notion",
                    subtitle = "A Notion page becomes the food workspace.",
                    helper = "Share the page with your integration first.",
                    selected = selectedBackend == BackendType.NOTION,
                    onClick = {
                        selectedBackend = BackendType.NOTION
                        switchAcknowledged = false
                        onSelectPending(BackendType.NOTION)
                    },
                )
                BackendChoiceRow(
                    icon = Icons.Rounded.ShoppingCart,
                    title = "Postgres",
                    subtitle = "For a Postgres-backed HTTPS API or server.",
                    helper = "Advanced. Needs API URL, household, and token.",
                    selected = selectedBackend == BackendType.POSTGRES,
                    onClick = {
                        selectedBackend = BackendType.POSTGRES
                        switchAcknowledged = false
                        onSelectPending(BackendType.POSTGRES)
                    },
                )
                BackendSafetyBanner()
                if (isBackendSwitch) {
                    BackendSwitchCheckpoint(
                        fromLabel = activeType.label,
                        toLabel = selectedBackend.label,
                        acknowledged = switchAcknowledged,
                        onAcknowledgedChange = { switchAcknowledged = it },
                    )
                }
                HorizontalDivider()
                when (selectedBackend) {
                    BackendType.LOCAL_SQLITE -> Unit
                    BackendType.GOOGLE_SHEETS -> GoogleSheetsBackendSetup(
                        sheetUrl = sheetUrl,
                        onSheetUrlChange = { sheetUrl = it.take(600) },
                        enabled = switchReady,
                        onConnect = { onConnectSheets(sheetUrl) },
                        onCreateNew = onCreateSheets,
                    )
                    BackendType.NOTION -> NotionBackendSetup(
                        pageUrl = notionPageUrl,
                        token = notionToken,
                        onPageUrlChange = { notionPageUrl = it.take(600) },
                        onTokenChange = { notionToken = it.take(300) },
                        enabled = switchReady,
                        onConnect = { onConnectNotion(notionPageUrl, notionToken) },
                    )
                    BackendType.POSTGRES -> PostgresBackendSetup(
                        endpoint = postgresEndpoint,
                        householdId = postgresHousehold,
                        token = postgresToken,
                        onEndpointChange = { postgresEndpoint = it.take(600) },
                        onHouseholdChange = { postgresHousehold = it.take(120) },
                        onTokenChange = { postgresToken = it.take(500) },
                        enabled = switchReady,
                        onConnect = { onConnectPostgres(postgresEndpoint, postgresHousehold, postgresToken) },
                    )
                }
                if (backendHome.message.isNotBlank()) {
                    BackendStatusMessage(backendHome.message)
                }
                Spacer(Modifier.height(8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("I’ll do this later") }
        },
        dismissButton = {
            TextButton(onClick = onUseLocal, enabled = selectedBackend == BackendType.LOCAL_SQLITE && switchReady) {
                Text("Start local now")
            }
        },
    )
}

@Composable
private fun BackendDataPlaneProofCard(backendHome: BackendHomeUiState) {
    val context = LocalContext.current
    val hasLiveHome = backendHome.activeType != null && !backendHome.requiresOnboarding
    val hasUrl = backendHome.dataPlaneUrl.isNotBlank()
    val hasTemplateProofPack = backendHome.templateNotionUrl.isNotBlank() || backendHome.templateSheetsUrl.isNotBlank()
    fun openProofUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                ObjectImage(
                    image = if (hasLiveHome) "🗂️" else "🟡",
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    size = 44.dp,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Data-plane links", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (hasLiveHome) {
                            "${backendHome.label} is the selected source of truth."
                        } else {
                            "No live Notion, Sheets, or Postgres data home is connected yet."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiCapabilityPill(if (hasLiveHome) "🟢" else "🟡", if (hasLiveHome) "Data home selected" else "Setup needed")
                AiCapabilityPill("🧬", if (hasLiveHome) "Schema linked" else "Schema waiting")
                AiCapabilityPill("↩️", "Rollback protected")
                AiCapabilityPill("⚖️", "Import review")
            }
            BackendProofLine(
                label = "Source target",
                value = backendHome.dataPlaneProofDetail(),
            )
            BackendProofLine(
                label = "Provider id",
                value = backendHome.externalId.ifBlank { "Not saved yet" },
            )
            BackendProofLine(
                label = "Account",
                value = backendHome.accountLabel.ifBlank { "Not saved yet" },
            )
            BackendProofLine(
                label = "Schema contract",
                value = when (backendHome.activeType) {
                    BackendType.GOOGLE_SHEETS -> "Google Sheets V4 graph tabs + hidden sync rows"
                    BackendType.NOTION -> "Notion workspace databases + linked rows"
                    BackendType.POSTGRES -> "Hosted snapshot schema + schema endpoint"
                    BackendType.LOCAL_SQLITE -> "Room/SQLite canonical household store"
                    null -> "Waiting for provider selection"
                },
            )
            if (hasTemplateProofPack) {
                BackendProofLine(
                    label = "Template model",
                    value = buildList {
                        if (backendHome.templateNotionUrl.isNotBlank()) add("Notion data plane")
                        if (backendHome.templateSheetsUrl.isNotBlank()) add("Google Sheets workbook")
                    }.joinToString(" + "),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (backendHome.templateNotionUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = { openProofUrl(backendHome.templateNotionUrl) },
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("Open Notion template")
                        }
                    }
                    if (backendHome.templateSheetsUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = { openProofUrl(backendHome.templateSheetsUrl) },
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("Open Sheets workbook")
                        }
                    }
                }
            }
            backendHome.message.takeIf { it.isNotBlank() }?.let {
                BackendProofLine("Last result", it)
            }
            backendHome.safetyMessage.takeIf { it.isNotBlank() }?.let {
                BackendProofLine("Safety", it)
            }
            if (hasUrl) {
                OutlinedButton(
                    onClick = { openProofUrl(backendHome.dataPlaneUrl) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open ${backendHome.proofLabel.ifBlank { backendHome.label }}")
                }
            } else if (!hasTemplateProofPack) {
                Text(
                    "Connect or create a provider below. Then this card will expose the live Notion page, Sheet workbook, or hosted endpoint directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BackendProofLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { "Not available" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WorkspaceConflictInboxCard(
    inbox: WorkspaceConflictInbox,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${inbox.providerLabel.ifBlank { "Workspace" }} conflict inbox"
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${inbox.providerLabel} conflict inbox",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        "${inbox.conflictCount} conflict${inbox.conflictCount.pluralWord} · ${inbox.changeCount} change${inbox.changeCount.pluralWord} · ${inbox.sourceLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            if (inbox.mergeClock.isNotBlank()) {
                Text(
                    "Merge clock: ${inbox.mergeClock}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                inbox.decision,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            inbox.conflictSummary.forEach { conflict ->
                Text(
                    conflict,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(
                onClick = onClear,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.semantics { contentDescription = "Clear workspace conflict inbox" },
            ) {
                Text("Dismiss reviewed conflicts")
            }
        }
    }
}

@Composable
private fun BackendSwitchCheckpoint(
    fromLabel: String,
    toLabel: String,
    acknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Switch checkpoint", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                "You are switching from $fromLabel to $toLabel. WonderFood will create a rollback snapshot first, verify the new data home, export a snapshot, then save the switch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = acknowledged, onCheckedChange = onAcknowledgedChange)
                Text(
                    "I understand the current data home stays recoverable from the rollback snapshot.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BackendChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    helper: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("Backend option $title")
                    if (selected) append(" selected")
                    append(". ")
                    append(helper)
                }
                this.selected = selected
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BackendSafetyBanner() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Simple rule", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                "Choose one data home. WonderFood checks access, exports a snapshot, then saves the choice. Failed setup never replaces your current data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocalBackendSetup(enabled: Boolean = true, onUseLocal: () -> Unit) {
    BackendSetupPanel(
        title = "Ready immediately",
        body = "No account, token, network, or permission. Your food stays on this phone until you switch.",
    ) {
        Button(
            onClick = onUseLocal,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            enabled = enabled,
        ) {
            Text("Use this phone")
        }
    }
}

@Composable
private fun GoogleSheetsBackendSetup(
    sheetUrl: String,
    onSheetUrlChange: (String) -> Unit,
    enabled: Boolean = true,
    onConnect: () -> Unit,
    onCreateNew: () -> Unit,
) {
    BackendSetupPanel(
        title = "What you need",
        body = "Create a new WonderFood Sheet or select an existing one by URL. WonderFood creates tabs, preserves existing WonderFood data, and asks Google for Sheets access.",
    ) {
        Button(
            onClick = onCreateNew,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            enabled = enabled,
        ) {
            Text("Create new Sheet")
        }
        OutlinedTextField(
            value = sheetUrl,
            onValueChange = onSheetUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Google Sheet URL") },
            placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        OutlinedButton(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            enabled = enabled && sheetUrl.isNotBlank(),
        ) {
            Text("Select existing by URL")
        }
    }
}

@Composable
private fun NotionBackendSetup(
    pageUrl: String,
    token: String,
    onPageUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    enabled: Boolean = true,
    onConnect: () -> Unit,
) {
    BackendSetupPanel(
        title = "What you need",
        body = "A Notion page shared with your integration. We never ask for your Notion username or password.",
    ) {
        OutlinedTextField(
            value = pageUrl,
            onValueChange = onPageUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notion page URL") },
            placeholder = { Text("https://www.notion.so/...") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Integration token") },
            placeholder = { Text("secret_...") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            enabled = enabled && pageUrl.isNotBlank() && token.isNotBlank(),
        ) {
            Text("Use Notion")
        }
    }
}

@Composable
private fun PostgresBackendSetup(
    endpoint: String,
    householdId: String,
    token: String,
    onEndpointChange: (String) -> Unit,
    onHouseholdChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    enabled: Boolean = true,
    onConnect: () -> Unit,
) {
    BackendSetupPanel(
        title = "Advanced setup",
        body = "Use a Postgres-backed HTTPS API or user-owned service endpoint. Android must not ship a raw database password or direct PostgreSQL socket.",
    ) {
        OutlinedTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Postgres API URL") },
            placeholder = { Text("https://food.example.com") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        OutlinedTextField(
            value = householdId,
            onValueChange = onHouseholdChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Household ID") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API token") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            enabled = enabled &&
                endpoint.isNotBlank() &&
                householdId.isNotBlank() &&
                token.isNotBlank(),
        ) {
            Text("Use Postgres")
        }
    }
}

@Composable
private fun BackendSetupPanel(
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun BackendStatusMessage(message: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun WonderFoodScreen(
    state: WonderFoodUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit,
    onSectionSelected: (FoodSection) -> Unit,
    onAcceptDraft: () -> Unit,
    onRejectDraft: () -> Unit,
    onDraftChange: (FoodDraft) -> Unit,
    onClearChatHistory: () -> Unit,
    onDeleteInventory: (Long) -> Unit,
    onUpdateInventory: InventoryUpdateHandler,
    onDeleteGrocery: (Long) -> Unit,
    onUpdateGrocery: GroceryUpdateHandler,
    onMarkGroceryBought: (Long) -> Unit,
    onMarkCanonicalCartLinePurchased: (String) -> Unit,
    onArchiveCanonicalCartLine: (String) -> Unit,
    onAddCanonicalKitchenItemToCart: (String) -> Unit,
    onArchiveCanonicalKitchenItem: (String) -> Unit,
    onDeleteRecipe: (Long) -> Unit,
    onCookRecipe: (Long) -> Unit,
    onAddRecipeMissingToList: (Long) -> Unit,
    onUpdateRecipe: RecipeUpdateHandler,
    onPickRecipeImage: (Long) -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onUpdateMeal: MealUpdateHandler,
    onUpdateMealPlan: MealPlanUpdateHandler,
    onAddMealPlanEntry: MealPlanEntryCreateHandler,
    onUpdateMealPlanEntry: MealPlanEntryUpdateHandler,
    onDeleteMealPlanEntry: (Long) -> Unit,
    onDeleteMealPlanEntries: (Set<Long>) -> Unit,
    onDeleteAllPlans: () -> Unit,
    onUpdateReceipt: ReceiptUpdateHandler,
    onExportMeal: (Long) -> Unit,
    onOpenDetail: (FoodDetailTarget) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onPreferencesChange: (FoodPreferences) -> Unit,
    onSavePreferences: () -> Unit,
    onAiConfigChange: (LiteLlmConfig) -> Unit,
    onAiFallbackConfigChange: (LiteLlmConfig) -> Unit,
    onSaveAiConfig: () -> Unit,
    onCreateEncryptedBackup: (String) -> Unit,
    onRestoreLatestEncryptedBackup: (String) -> Unit,
    onGoogleOAuthClientIdChange: (String) -> Unit,
    onConnectGoogle: () -> Unit,
    onBackupToGoogleDrive: () -> Unit,
    onRestoreFromGoogleDrive: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onTestAiConnection: (LiteLlmConfig) -> Unit,
    onDeleteAllAppData: () -> Unit,
    themeMode: WonderFoodThemeMode,
    onThemeModeChange: (WonderFoodThemeMode) -> Unit,
    onPickReceiptPhoto: () -> Unit,
    onRecordVoiceNote: () -> Unit,
    onRequestHealthConnect: () -> Unit,
    onCreateManual: (ManualCreateRequest) -> Unit,
    onLogWater: (Int) -> Unit,
    onUndo: () -> Unit,
    onDismissFeedback: () -> Unit,
    onChooseLocalBackend: () -> Unit,
    onValidateGoogleSheetsBackend: (String) -> Unit,
    onCreateGoogleSheetsBackend: () -> Unit,
    onConnectNotionBackend: (String, String) -> Unit,
    onConnectPostgresBackend: (String, String, String) -> Unit,
    onSelectPendingBackend: (BackendType) -> Unit,
    onSelectLifeOsDomain: (String) -> Unit,
    onDismissBackendOnboarding: () -> Unit,
    onClearWorkspaceConflictInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val showNavigationRail = maxWidth >= 720.dp
        val showCalendarPane = false
        val compactWide = maxWidth < 1040.dp
        val calendarWidth = if (compactWide) 248.dp else 336.dp

        if (showNavigationRail) {
            Row(Modifier.fillMaxSize()) {
                FoodNavigationRail(
                    selected = state.section,
                    onSectionSelected = onSectionSelected,
                )
                MainWorkspace(
                    state = state,
                    showBottomNavigation = false,
                    showInlineCalendar = !showCalendarPane,
                    onInputChange = onInputChange,
                    onSend = onSend,
                    onNewChat = onNewChat,
                    onSectionSelected = onSectionSelected,
                    onAcceptDraft = onAcceptDraft,
                    onRejectDraft = onRejectDraft,
                    onDraftChange = onDraftChange,
                    onClearChatHistory = onClearChatHistory,
                    onDeleteInventory = onDeleteInventory,
                    onUpdateInventory = onUpdateInventory,
                    onDeleteGrocery = onDeleteGrocery,
                    onUpdateGrocery = onUpdateGrocery,
                    onMarkGroceryBought = onMarkGroceryBought,
                    onMarkCanonicalCartLinePurchased = onMarkCanonicalCartLinePurchased,
                    onArchiveCanonicalCartLine = onArchiveCanonicalCartLine,
                    onAddCanonicalKitchenItemToCart = onAddCanonicalKitchenItemToCart,
                    onArchiveCanonicalKitchenItem = onArchiveCanonicalKitchenItem,
                    onDeleteRecipe = onDeleteRecipe,
                    onCookRecipe = onCookRecipe,
                    onAddRecipeMissingToList = onAddRecipeMissingToList,
                    onUpdateRecipe = onUpdateRecipe,
                    onPickRecipeImage = onPickRecipeImage,
                    onDeleteMeal = onDeleteMeal,
                    onUpdateMeal = onUpdateMeal,
                    onUpdateMealPlan = onUpdateMealPlan,
                    onAddMealPlanEntry = onAddMealPlanEntry,
                    onUpdateMealPlanEntry = onUpdateMealPlanEntry,
                    onDeleteMealPlanEntry = onDeleteMealPlanEntry,
                    onDeleteMealPlanEntries = onDeleteMealPlanEntries,
                    onDeleteAllPlans = onDeleteAllPlans,
                    onUpdateReceipt = onUpdateReceipt,
                    onExportMeal = onExportMeal,
                    onOpenDetail = onOpenDetail,
                    onSearchQueryChange = onSearchQueryChange,
                    onCloseDetail = onCloseDetail,
                    onPreferencesChange = onPreferencesChange,
                    onSavePreferences = onSavePreferences,
                    onAiConfigChange = onAiConfigChange,
                    onAiFallbackConfigChange = onAiFallbackConfigChange,
                    onSaveAiConfig = onSaveAiConfig,
                    onCreateEncryptedBackup = onCreateEncryptedBackup,
                    onRestoreLatestEncryptedBackup = onRestoreLatestEncryptedBackup,
                    onGoogleOAuthClientIdChange = onGoogleOAuthClientIdChange,
                    onConnectGoogle = onConnectGoogle,
                    onBackupToGoogleDrive = onBackupToGoogleDrive,
                    onRestoreFromGoogleDrive = onRestoreFromGoogleDrive,
                    onDisconnectGoogle = onDisconnectGoogle,
                    onExportCsv = onExportCsv,
                    onImportCsv = onImportCsv,
                    onTestAiConnection = onTestAiConnection,
                    onDeleteAllAppData = onDeleteAllAppData,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onPickReceiptPhoto = onPickReceiptPhoto,
                    onRecordVoiceNote = onRecordVoiceNote,
                    onRequestHealthConnect = onRequestHealthConnect,
                    onCreateManual = onCreateManual,
                    onLogWater = onLogWater,
                    onUndo = onUndo,
                    onDismissFeedback = onDismissFeedback,
                    onChooseLocalBackend = onChooseLocalBackend,
                    onValidateGoogleSheetsBackend = onValidateGoogleSheetsBackend,
                    onCreateGoogleSheetsBackend = onCreateGoogleSheetsBackend,
                    onConnectNotionBackend = onConnectNotionBackend,
                    onConnectPostgresBackend = onConnectPostgresBackend,
                    onSelectPendingBackend = onSelectPendingBackend,
                    onSelectLifeOsDomain = onSelectLifeOsDomain,
                    onDismissBackendOnboarding = onDismissBackendOnboarding,
                    onClearWorkspaceConflictInbox = onClearWorkspaceConflictInbox,
                    modifier = Modifier.weight(1f),
                )
                if (showCalendarPane) {
                    CalendarPane(
                        memory = state.memory,
                        onOpenDay = { day ->
                            onOpenDetail(FoodDetailTarget(FoodDetailKind.DAY, epochDay = day.date.toEpochDay()))
                        },
                        modifier = Modifier.width(calendarWidth).fillMaxHeight(),
                    )
                }
            }
        } else {
            MainWorkspace(
                state = state,
                showBottomNavigation = true,
                showInlineCalendar = true,
                onInputChange = onInputChange,
                onSend = onSend,
                onNewChat = onNewChat,
                onSectionSelected = onSectionSelected,
                onAcceptDraft = onAcceptDraft,
                onRejectDraft = onRejectDraft,
                onDraftChange = onDraftChange,
                onClearChatHistory = onClearChatHistory,
                onDeleteInventory = onDeleteInventory,
                onUpdateInventory = onUpdateInventory,
                onDeleteGrocery = onDeleteGrocery,
                onUpdateGrocery = onUpdateGrocery,
                onMarkGroceryBought = onMarkGroceryBought,
                onMarkCanonicalCartLinePurchased = onMarkCanonicalCartLinePurchased,
                onArchiveCanonicalCartLine = onArchiveCanonicalCartLine,
                onAddCanonicalKitchenItemToCart = onAddCanonicalKitchenItemToCart,
                onArchiveCanonicalKitchenItem = onArchiveCanonicalKitchenItem,
                onDeleteRecipe = onDeleteRecipe,
                onCookRecipe = onCookRecipe,
                onAddRecipeMissingToList = onAddRecipeMissingToList,
                onUpdateRecipe = onUpdateRecipe,
                onPickRecipeImage = onPickRecipeImage,
                onDeleteMeal = onDeleteMeal,
                onUpdateMeal = onUpdateMeal,
                onUpdateMealPlan = onUpdateMealPlan,
                onAddMealPlanEntry = onAddMealPlanEntry,
                onUpdateMealPlanEntry = onUpdateMealPlanEntry,
                onDeleteMealPlanEntry = onDeleteMealPlanEntry,
                onDeleteMealPlanEntries = onDeleteMealPlanEntries,
                onDeleteAllPlans = onDeleteAllPlans,
                onUpdateReceipt = onUpdateReceipt,
                onExportMeal = onExportMeal,
                onOpenDetail = onOpenDetail,
                onSearchQueryChange = onSearchQueryChange,
                onCloseDetail = onCloseDetail,
                onPreferencesChange = onPreferencesChange,
                onSavePreferences = onSavePreferences,
                onAiConfigChange = onAiConfigChange,
                onAiFallbackConfigChange = onAiFallbackConfigChange,
                onSaveAiConfig = onSaveAiConfig,
                onCreateEncryptedBackup = onCreateEncryptedBackup,
                onRestoreLatestEncryptedBackup = onRestoreLatestEncryptedBackup,
                onGoogleOAuthClientIdChange = onGoogleOAuthClientIdChange,
                onConnectGoogle = onConnectGoogle,
                onBackupToGoogleDrive = onBackupToGoogleDrive,
                onRestoreFromGoogleDrive = onRestoreFromGoogleDrive,
                onDisconnectGoogle = onDisconnectGoogle,
                onExportCsv = onExportCsv,
                onImportCsv = onImportCsv,
                onTestAiConnection = onTestAiConnection,
                onDeleteAllAppData = onDeleteAllAppData,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onPickReceiptPhoto = onPickReceiptPhoto,
                onRecordVoiceNote = onRecordVoiceNote,
                onRequestHealthConnect = onRequestHealthConnect,
                onCreateManual = onCreateManual,
                onLogWater = onLogWater,
                onUndo = onUndo,
                onDismissFeedback = onDismissFeedback,
                onChooseLocalBackend = onChooseLocalBackend,
                onValidateGoogleSheetsBackend = onValidateGoogleSheetsBackend,
                onCreateGoogleSheetsBackend = onCreateGoogleSheetsBackend,
                onConnectNotionBackend = onConnectNotionBackend,
                onConnectPostgresBackend = onConnectPostgresBackend,
                onSelectPendingBackend = onSelectPendingBackend,
                onSelectLifeOsDomain = onSelectLifeOsDomain,
                onDismissBackendOnboarding = onDismissBackendOnboarding,
                onClearWorkspaceConflictInbox = onClearWorkspaceConflictInbox,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FoodNavigationRail(
    selected: FoodSection,
    onSectionSelected: (FoodSection) -> Unit,
) {
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .safeDrawingPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        FoodSection.entries.forEach { section ->
            NavigationRailItem(
                modifier = Modifier.semantics { contentDescription = "Navigate to ${section.label}" },
                selected = selected == section,
                onClick = { onSectionSelected(section) },
                icon = { Icon(section.icon, contentDescription = null) },
                label = { Text(section.label) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun MainWorkspace(
    state: WonderFoodUiState,
    showBottomNavigation: Boolean,
    showInlineCalendar: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit,
    onSectionSelected: (FoodSection) -> Unit,
    onAcceptDraft: () -> Unit,
    onRejectDraft: () -> Unit,
    onDraftChange: (FoodDraft) -> Unit,
    onClearChatHistory: () -> Unit,
    onDeleteInventory: (Long) -> Unit,
    onUpdateInventory: InventoryUpdateHandler,
    onDeleteGrocery: (Long) -> Unit,
    onUpdateGrocery: GroceryUpdateHandler,
    onMarkGroceryBought: (Long) -> Unit,
    onMarkCanonicalCartLinePurchased: (String) -> Unit,
    onArchiveCanonicalCartLine: (String) -> Unit,
    onAddCanonicalKitchenItemToCart: (String) -> Unit,
    onArchiveCanonicalKitchenItem: (String) -> Unit,
    onDeleteRecipe: (Long) -> Unit,
    onCookRecipe: (Long) -> Unit,
    onAddRecipeMissingToList: (Long) -> Unit,
    onUpdateRecipe: RecipeUpdateHandler,
    onPickRecipeImage: (Long) -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onUpdateMeal: MealUpdateHandler,
    onUpdateMealPlan: MealPlanUpdateHandler,
    onAddMealPlanEntry: MealPlanEntryCreateHandler,
    onUpdateMealPlanEntry: MealPlanEntryUpdateHandler,
    onDeleteMealPlanEntry: (Long) -> Unit,
    onDeleteMealPlanEntries: (Set<Long>) -> Unit,
    onDeleteAllPlans: () -> Unit,
    onUpdateReceipt: ReceiptUpdateHandler,
    onExportMeal: (Long) -> Unit,
    onOpenDetail: (FoodDetailTarget) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onPreferencesChange: (FoodPreferences) -> Unit,
    onSavePreferences: () -> Unit,
    onAiConfigChange: (LiteLlmConfig) -> Unit,
    onAiFallbackConfigChange: (LiteLlmConfig) -> Unit,
    onSaveAiConfig: () -> Unit,
    onCreateEncryptedBackup: (String) -> Unit,
    onRestoreLatestEncryptedBackup: (String) -> Unit,
    onGoogleOAuthClientIdChange: (String) -> Unit,
    onConnectGoogle: () -> Unit,
    onBackupToGoogleDrive: () -> Unit,
    onRestoreFromGoogleDrive: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onTestAiConnection: (LiteLlmConfig) -> Unit,
    onDeleteAllAppData: () -> Unit,
    themeMode: WonderFoodThemeMode,
    onThemeModeChange: (WonderFoodThemeMode) -> Unit,
    onPickReceiptPhoto: () -> Unit,
    onRecordVoiceNote: () -> Unit,
    onRequestHealthConnect: () -> Unit,
    onCreateManual: (ManualCreateRequest) -> Unit,
    onLogWater: (Int) -> Unit,
    onUndo: () -> Unit,
    onDismissFeedback: () -> Unit,
    onChooseLocalBackend: () -> Unit,
    onValidateGoogleSheetsBackend: (String) -> Unit,
    onCreateGoogleSheetsBackend: () -> Unit,
    onConnectNotionBackend: (String, String) -> Unit,
    onConnectPostgresBackend: (String, String, String) -> Unit,
    onSelectPendingBackend: (BackendType) -> Unit,
    onSelectLifeOsDomain: (String) -> Unit,
    onDismissBackendOnboarding: () -> Unit,
    onClearWorkspaceConflictInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var secondaryPane by rememberSaveable { mutableStateOf<SecondaryPane?>(null) }
    var settingsInitialDestination by rememberSaveable { mutableStateOf(SettingsDestination.HOME) }
    var showAiCapture by rememberSaveable { mutableStateOf(false) }
    var shopMode by rememberSaveable { mutableStateOf(ShopMode.TO_BUY) }
    var manualCreateKind by rememberSaveable { mutableStateOf<ManualCreateKind?>(null) }
    var manualCreateDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var manualCreateSlot by rememberSaveable { mutableStateOf<MealSlot?>(null) }
    var showDiscardSettings by rememberSaveable { mutableStateOf(false) }
    val sectionStateHolder = rememberSaveableStateHolder()
    val aiContext = state.aiPageContext()
    val settingsDirty = state.preferencesForm != state.memory.preferences ||
        state.aiConfigForm != state.savedAiConfig ||
        state.aiFallbackConfigForm != state.savedAiFallbackConfig
    val hasFeedback = state.undoMessage.isNotBlank() || state.feedbackMessage.isNotBlank()
    val closeSecondaryPane = {
        if (secondaryPane == SecondaryPane.SETTINGS && settingsDirty) showDiscardSettings = true else secondaryPane = null
    }
    fun openAi(prompt: String? = null) {
        prompt?.let(onInputChange)
        showAiCapture = true
    }
    val logMealToday = {
        manualCreateDay = LocalDate.now().toEpochDay()
        manualCreateSlot = null
        manualCreateKind = ManualCreateKind.MEAL
    }
    val addKitchenFood = { manualCreateKind = ManualCreateKind.INVENTORY }
    val addShoppingItem = { manualCreateKind = ManualCreateKind.GROCERY }
    val addRecipe = { manualCreateKind = ManualCreateKind.RECIPE }
    val openTodayPlan = {
        onOpenDetail(FoodDetailTarget(FoodDetailKind.DAY, epochDay = LocalDate.now().toEpochDay()))
    }
    val putAwayTarget = state.memory.groceries.firstOrNull { it.status == GroceryStatus.BOUGHT }
        ?.let { FoodDetailTarget(FoodDetailKind.GROCERY, id = it.id) }
        ?: state.memory.receipts
            .sortedByDescending { it.createdAtMillis }
            .firstOrNull { it.status == ReceiptStatus.EXTRACTED }
            ?.let { FoodDetailTarget(FoodDetailKind.RECEIPT, id = it.id) }
    val canShowPrimaryAction = !showAiCapture &&
        !hasFeedback &&
        secondaryPane == null &&
        state.detailTarget == null &&
        state.pendingDraft == null &&
        manualCreateKind == null &&
        !showDiscardSettings
    val primaryAction = if (canShowPrimaryAction) {
        when (state.section) {
            FoodSection.TODAY -> FoodPrimaryAction(
                label = "Log meal",
                contentDescription = "Log meal",
                icon = Icons.Rounded.Restaurant,
                onClick = logMealToday,
            )
            FoodSection.KITCHEN -> FoodPrimaryAction(
                label = "Add food",
                contentDescription = "Add kitchen food",
                icon = Icons.Rounded.Add,
                onClick = addKitchenFood,
            )
            FoodSection.PLAN -> FoodPrimaryAction(
                label = "Plan today",
                contentDescription = "Plan today",
                icon = Icons.Rounded.CalendarMonth,
                onClick = openTodayPlan,
            )
            FoodSection.RECIPES -> FoodPrimaryAction(
                label = "New recipe",
                contentDescription = "Create recipe",
                icon = Icons.Rounded.Add,
                onClick = addRecipe,
            )
            FoodSection.SHOP -> {
                val addItem = FoodPrimaryAction(
                    label = "Add item",
                    contentDescription = "Add cart line",
                    icon = Icons.Rounded.Add,
                    onClick = addShoppingItem,
                )
                val scanReceipt = FoodPrimaryAction(
                    label = "Scan receipt",
                    contentDescription = "Scan receipt",
                    icon = Icons.Rounded.AddAPhoto,
                    onClick = onPickReceiptPhoto,
                )
                when (shopMode) {
                    ShopMode.TO_BUY -> addItem
                    ShopMode.RECEIPTS -> scanReceipt
                    ShopMode.PUT_AWAY -> putAwayTarget?.let { target ->
                        FoodPrimaryAction(
                            label = "Review item",
                            contentDescription = "Review put-away item",
                            icon = Icons.Rounded.Inventory2,
                            onClick = { onOpenDetail(target) },
                        )
                    } ?: scanReceipt
                }
            }
        }
    } else {
        null
    }
    val primaryActionContentPadding = if (primaryAction != null) 84.dp else 0.dp
    BackHandler(enabled = showAiCapture || secondaryPane != null || state.detailTarget != null) {
        when {
            showAiCapture -> showAiCapture = false
            secondaryPane != null -> closeSecondaryPane()
            else -> onCloseDetail()
        }
    }
    Box(
        modifier = modifier
            .safeDrawingPadding(),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                if (secondaryPane == null) {
                    TopBar(
                        state = state,
                        isAiWorking = state.isWorking,
                        onSearchClick = {
                            onDismissFeedback()
                            showAiCapture = false
                            secondaryPane = SecondaryPane.SEARCH
                        },
                        onAiClick = { openAi() },
                        onSettingsClick = {
                            onDismissFeedback()
                            settingsInitialDestination = SettingsDestination.HOME
                            showAiCapture = false
                            secondaryPane = SecondaryPane.SETTINGS
                        },
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                    )
                }
            },
            bottomBar = {
                if (showBottomNavigation) {
                    FoodBottomNavigation(
                        selected = state.section,
                        onSelected = { section ->
                            if (secondaryPane == SecondaryPane.SETTINGS && settingsDirty) {
                                showDiscardSettings = true
                            } else {
                                onDismissFeedback()
                                showAiCapture = false
                                secondaryPane = null
                                onSectionSelected(section)
                            }
                        },
                    )
                }
            },
            floatingActionButton = {
                primaryAction?.let { action ->
                    FoodPrimaryActionButton(action)
                }
            },
            floatingActionButtonPosition = FabPosition.End,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(
                        start = 16.dp,
                        top = if (secondaryPane == null) 8.dp else 16.dp,
                        end = 16.dp,
                        bottom = 16.dp + primaryActionContentPadding,
                    ),
            ) {
                when {
                    state.pendingDraft != null && !showAiCapture -> DraftCard(
                        draft = state.pendingDraft,
                        origin = state.pendingDraftOrigin,
                        onAccept = onAcceptDraft,
                        onReject = onRejectDraft,
                        onChange = onDraftChange,
                        fillAvailableHeight = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    secondaryPane == SecondaryPane.SEARCH -> SearchContent(
                        memory = state.memory,
                        canonicalItems = state.canonicalSearchItems,
                        onSearchQueryChange = onSearchQueryChange,
                        onBack = closeSecondaryPane,
                        onOpenDetail = { target ->
                            onDismissFeedback()
                            secondaryPane = null
                            onOpenDetail(target)
                        },
                    )
                    secondaryPane == SecondaryPane.SETTINGS -> PreferencesContent(
                        memory = state.memory,
                        preferences = state.preferencesForm,
                        aiConfig = state.aiConfigForm,
                        aiFallbackConfig = state.aiFallbackConfigForm,
                        aiStatus = state.aiStatus,
                        healthStatus = state.healthStatus,
                        healthSummary = state.healthSummary,
                        syncStatus = state.syncStatus,
                        canonicalSummary = state.canonicalSummary,
                        backendHome = state.backendHome,
                        lifeOsDomains = state.lifeOsDomains,
                        selectedLifeOsDomainId = state.selectedLifeOsDomainId,
                        workspaceConflictInbox = state.workspaceConflictInbox,
                        googleAccountEmail = state.googleAccountEmail,
                        googleOAuthClientId = state.googleOAuthClientId,
                        googleSyncStatus = state.googleSyncStatus,
                        settingsSaveStatus = state.settingsSaveStatus,
                        themeMode = themeMode,
                        onChange = onPreferencesChange,
                        onSave = onSavePreferences,
                        onAiConfigChange = onAiConfigChange,
                        onAiFallbackConfigChange = onAiFallbackConfigChange,
                        onSaveAiConfig = onSaveAiConfig,
                        onCreateEncryptedBackup = onCreateEncryptedBackup,
                        onRestoreLatestEncryptedBackup = onRestoreLatestEncryptedBackup,
                        onGoogleOAuthClientIdChange = onGoogleOAuthClientIdChange,
                        onConnectGoogle = onConnectGoogle,
                        onBackupToGoogleDrive = onBackupToGoogleDrive,
                        onRestoreFromGoogleDrive = onRestoreFromGoogleDrive,
                        onDisconnectGoogle = onDisconnectGoogle,
                        onExportCsv = onExportCsv,
                        onImportCsv = onImportCsv,
                        onTestAiConnection = onTestAiConnection,
                        onDeleteAllAppData = onDeleteAllAppData,
                        onChooseLocalBackend = onChooseLocalBackend,
                        onValidateGoogleSheetsBackend = onValidateGoogleSheetsBackend,
                        onCreateGoogleSheetsBackend = onCreateGoogleSheetsBackend,
                        onConnectNotionBackend = onConnectNotionBackend,
                        onConnectPostgresBackend = onConnectPostgresBackend,
                        onSelectPendingBackend = onSelectPendingBackend,
                        onSelectLifeOsDomain = onSelectLifeOsDomain,
                        onDeleteAllPlans = onDeleteAllPlans,
                        onClearChatHistory = onClearChatHistory,
                        onThemeModeChange = onThemeModeChange,
                        onRequestHealthConnect = onRequestHealthConnect,
                        onOpenChat = {
                            secondaryPane = null
                            showAiCapture = true
                        },
                        onClearWorkspaceConflictInbox = onClearWorkspaceConflictInbox,
                        initialDestination = settingsInitialDestination,
                        onBack = { secondaryPane = null },
                    )
                    state.detailTarget != null -> {
                    DetailPage(
                        target = state.detailTarget,
                        memory = state.memory,
                        onBack = onCloseDetail,
                        onDeleteInventory = onDeleteInventory,
                        onUpdateInventory = onUpdateInventory,
                        onDeleteGrocery = onDeleteGrocery,
                        onUpdateGrocery = onUpdateGrocery,
                        onMarkGroceryBought = onMarkGroceryBought,
                        onDeleteRecipe = onDeleteRecipe,
                        onCookRecipe = onCookRecipe,
                        onAddRecipeMissingToList = onAddRecipeMissingToList,
                        onUpdateRecipe = onUpdateRecipe,
                        onPickRecipeImage = onPickRecipeImage,
                        onDeleteMeal = onDeleteMeal,
                        onUpdateMeal = onUpdateMeal,
                        onUpdateMealPlan = onUpdateMealPlan,
                        onAddMealPlanEntry = onAddMealPlanEntry,
                        onUpdateMealPlanEntry = onUpdateMealPlanEntry,
                        onDeleteMealPlanEntry = onDeleteMealPlanEntry,
                        onUpdateReceipt = onUpdateReceipt,
                        onExportMeal = onExportMeal,
                        onOpenDetail = onOpenDetail,
                        onLogMeal = { day ->
                            manualCreateDay = day
                            manualCreateKind = ManualCreateKind.MEAL
                        },
                        onLogWater = onLogWater,
                    )
                    }
                    else -> sectionStateHolder.SaveableStateProvider(state.section.name) { when (state.section) {
                    FoodSection.KITCHEN -> FoodHubContent(
                        memory = state.memory,
                        canonicalKitchenPreview = state.canonicalKitchenPreview,
                        canonicalRecipeMatches = state.canonicalRecipeMatches,
                        onCanonicalAddToCart = onAddCanonicalKitchenItemToCart,
                        onCanonicalArchive = onArchiveCanonicalKitchenItem,
                        onOpenInventory = { item ->
                            onOpenDetail(FoodDetailTarget(FoodDetailKind.INVENTORY, id = item.id))
                        },
                        onDeleteInventory = onDeleteInventory,
                        onOpenRecipe = { recipe ->
                            onOpenDetail(FoodDetailTarget(FoodDetailKind.RECIPE, id = recipe.id))
                        },
                    )
                    FoodSection.SHOP -> GroceryContent(
                        memory = state.memory,
                        canonicalCartPreview = state.canonicalCartPreview,
                        mode = shopMode,
                        onModeChange = { shopMode = it },
                        onOpen = { item ->
                            onOpenDetail(FoodDetailTarget(FoodDetailKind.GROCERY, id = item.id))
                        },
                        onBought = onMarkGroceryBought,
                        onDelete = onDeleteGrocery,
                        onCanonicalBought = onMarkCanonicalCartLinePurchased,
                        onCanonicalArchive = onArchiveCanonicalCartLine,
                        onOpenReceipt = { receipt ->
                            onOpenDetail(FoodDetailTarget(FoodDetailKind.RECEIPT, id = receipt.id))
                        },
                    )
                    FoodSection.TODAY -> TodayContent(
                        memory = state.memory,
                        canonicalSummary = state.canonicalSummary,
                        canonicalSpendingPreview = state.canonicalSpendingPreview,
                        healthSummary = state.healthSummary,
                        onOpenDetail = onOpenDetail,
                        onDeleteMeal = onDeleteMeal,
                        onLogMeal = { slot ->
                            manualCreateDay = LocalDate.now().toEpochDay()
                            manualCreateSlot = slot
                            manualCreateKind = ManualCreateKind.MEAL
                        },
                    )
                    FoodSection.PLAN -> PlanContent(
                        memory = state.memory,
                        canonicalWeekPreview = state.canonicalWeekPreview,
                        showCalendar = showInlineCalendar,
                        onOpenDetail = onOpenDetail,
                        onDeleteMealPlanEntries = onDeleteMealPlanEntries,
                        onDeleteAllPlans = onDeleteAllPlans,
                    )
                    FoodSection.RECIPES -> RecipesContent(
                        memory = state.memory,
                        canonicalRecipePreview = state.canonicalRecipePreview,
                        onOpen = { recipe ->
                            onOpenDetail(FoodDetailTarget(FoodDetailKind.RECIPE, id = recipe.id))
                        },
                    )
                    } }
                }
            }
        }
        if (state.undoMessage.isNotBlank()) {
            FeedbackBar(
                message = state.undoMessage,
                actionLabel = "Undo",
                onAction = onUndo,
                onDismiss = onDismissFeedback,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (showBottomNavigation) 92.dp else 20.dp,
                    ),
            )
        } else if (state.feedbackMessage.isNotBlank()) {
            FeedbackBar(
                message = state.feedbackMessage,
                onDismiss = onDismissFeedback,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (showBottomNavigation) 92.dp else 20.dp,
                    ),
            )
        }
        if (showAiCapture) {
            AiCaptureSheet(
                input = state.input,
                isWorking = state.isWorking,
                draft = state.pendingDraft,
                draftOrigin = state.pendingDraftOrigin,
                messages = state.memory.messages,
                pageContext = aiContext,
                providerStatus = state.aiStatus,
                backendHome = state.backendHome,
                healthStatus = state.healthStatus,
                contextSummary = state.memory.aiVisibleContextSummary(),
                status = when {
                    state.isWorking && state.aiAttemptStatus.isNotBlank() -> state.aiAttemptStatus
                    state.isWorking -> "AI is reviewing this."
                    state.aiAttemptStatus.isNotBlank() -> state.aiAttemptStatus
                    state.pendingDraft != null -> "Proposal ready. Review before saving."
                    state.voiceStatus.isNotBlank() -> state.voiceStatus
                    else -> "AI reviews before saving."
                },
                onInputChange = onInputChange,
                onSend = onSend,
                onNewChat = onNewChat,
                onPickReceiptPhoto = onPickReceiptPhoto,
                onRecordVoiceNote = onRecordVoiceNote,
                onAcceptDraft = onAcceptDraft,
                onRejectDraft = onRejectDraft,
                onDraftChange = onDraftChange,
                onOpenDataHome = {
                    onDismissFeedback()
                    settingsInitialDestination = SettingsDestination.DATA_HOME
                    showAiCapture = false
                    secondaryPane = SecondaryPane.SETTINGS
                },
                onOpenAiControl = {
                    onDismissFeedback()
                    settingsInitialDestination = SettingsDestination.AI_ASSISTANT
                    showAiCapture = false
                    secondaryPane = SecondaryPane.SETTINGS
                },
                onDismiss = { showAiCapture = false },
            )
        }
        manualCreateKind?.let { kind ->
            ManualCreateDialog(
                kind = kind,
                defaultEpochDay = manualCreateDay,
                defaultSlot = manualCreateSlot,
                onDismiss = {
                    manualCreateKind = null
                    manualCreateDay = null
                    manualCreateSlot = null
                },
                onCreate = { request ->
                    onCreateManual(request)
                    manualCreateKind = null
                    manualCreateDay = null
                    manualCreateSlot = null
                },
            )
        }
        if (showDiscardSettings) {
            AlertDialog(
                onDismissRequest = { showDiscardSettings = false },
                title = { Text("Discard unsaved settings?") },
                text = { Text("Save changes before leaving Settings, or discard them.") },
                confirmButton = {
                    Button(onClick = {
                        onPreferencesChange(state.memory.preferences)
                        onAiConfigChange(state.savedAiConfig)
                        onAiFallbackConfigChange(state.savedAiFallbackConfig)
                        showDiscardSettings = false
                        secondaryPane = null
                    }) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardSettings = false }) { Text("Keep editing") }
                },
            )
        }
        if (state.backendHome.requiresOnboarding) {
            BackendOnboardingDialog(
                backendHome = state.backendHome,
                conflictInbox = state.workspaceConflictInbox,
                onUseLocal = onChooseLocalBackend,
                onConnectSheets = onValidateGoogleSheetsBackend,
                onCreateSheets = onCreateGoogleSheetsBackend,
                onConnectNotion = onConnectNotionBackend,
                onConnectPostgres = onConnectPostgresBackend,
                onSelectPending = onSelectPendingBackend,
                onClearConflictInbox = onClearWorkspaceConflictInbox,
                onDismiss = onDismissBackendOnboarding,
            )
        }
    }
}

@Composable
private fun TopBar(
    state: WonderFoodUiState,
    isAiWorking: Boolean,
    onSearchClick: () -> Unit,
    onAiClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < 560.dp
        val title = if (state.section == FoodSection.TODAY) todayTitle() else state.section.label
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.section.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.semantics {
                        contentDescription = "Search WonderFood"
                        role = Role.Button
                    },
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
                AiTopBarButton(isWorking = isAiWorking, onClick = onAiClick)
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.semantics {
                        contentDescription = "Open settings"
                        role = Role.Button
                    },
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            state.section.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.semantics {
                            contentDescription = "Search WonderFood"
                            role = Role.Button
                        },
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                    AiTopBarButton(isWorking = isAiWorking, onClick = onAiClick)
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.semantics {
                            contentDescription = "Open settings"
                            role = Role.Button
                        },
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiTopBarButton(isWorking: Boolean, onClick: () -> Unit) {
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .semantics {
                    contentDescription = "Open AI capture"
                    role = Role.Button
                },
        ) {
            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null)
        }
        ActionBadge(show = isWorking, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
    }
}

@Composable
private fun FoodPrimaryActionButton(action: FoodPrimaryAction) {
    ExtendedFloatingActionButton(
        onClick = action.onClick,
        modifier = Modifier.semantics { contentDescription = action.contentDescription },
        expanded = true,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = {
            Icon(
                action.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        text = { Text(action.label) },
    )
}

@Composable
private fun ActionBadge(show: Boolean, modifier: Modifier = Modifier) {
    if (!show) return
    Surface(
        modifier = modifier
            .size(14.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondary,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
        content = {},
    )
}

@Composable
private fun VoiceStatusCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FoodBottomNavigation(selected: FoodSection, onSelected: (FoodSection) -> Unit) {
    val visibleSections = remember {
        listOf(
            FoodSection.TODAY,
            FoodSection.KITCHEN,
            FoodSection.PLAN,
            FoodSection.RECIPES,
            FoodSection.SHOP,
        )
    }
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        visibleSections.forEach { section ->
            NavigationBarItem(
                modifier = Modifier.semantics { contentDescription = "Navigate to ${section.label}" },
                selected = selected == section,
                onClick = { onSelected(section) },
                icon = { Icon(section.icon, contentDescription = null) },
                label = {
                    Text(
                        section.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun ChatContent(
    messages: List<ChatMessage>,
    actions: List<ChatAction>,
    receipts: List<ReceiptCapture>,
    pendingDraft: FoodDraft?,
    onAcceptDraft: () -> Unit,
    onRejectDraft: () -> Unit,
    onPrompt: (String) -> Unit,
    onPickReceiptPhoto: () -> Unit,
    onOpenReceipt: (ReceiptCapture) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, pendingDraft) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item {
            TodayPlateCard(onPrompt = onPrompt)
        }
        if (receipts.isNotEmpty()) {
            item { SectionLabel("Receipt photos") }
            items(receipts.take(3), key = { it.id }) { receipt ->
                ReceiptCard(receipt = receipt, onOpen = { onOpenReceipt(receipt) })
            }
        }
        items(messages, key = { it.id }) { message ->
            MessageBubble(message)
        }
        if (pendingDraft != null) {
            item {
                DraftCard(
                    draft = pendingDraft,
                    onAccept = onAcceptDraft,
                    onReject = onRejectDraft,
                )
            }
        }
        if (actions.isNotEmpty()) {
            item { SectionLabel("AI actions") }
            items(actions.take(5), key = { it.id }) { action ->
                ChatActionCard(action = action)
            }
        }
        item {
            PromptChips(onPrompt = onPrompt, onPickReceiptPhoto = onPickReceiptPhoto)
        }
    }
}

@Composable
private fun ChatActionCard(action: ChatAction) {
    val accepted = action.status == ChatActionStatus.ACCEPTED
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (accepted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ObjectImage(
                    image = if (accepted) "✅" else "↩",
                    color = MaterialTheme.colorScheme.surface,
                    size = 40.dp,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${action.status.name.lowercase()} • ${action.operation.name.lowercase()} • ${action.createdAtMillis.shortDate()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(action.summary, style = MaterialTheme.typography.bodyMedium)
            if (action.rowsText.isNotBlank()) {
                Text(
                    action.rowsText.lineSequence().take(3).joinToString("\n") { "• $it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TodayPlateCard(onPrompt: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today's plate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Plan from what is fresh, log what happened.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            OutlinedButton(onClick = { onPrompt("Plan meals this week") }, shape = RoundedCornerShape(18.dp)) {
                Text("Plan")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = message.body,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DraftCard(
    draft: FoodDraft,
    origin: FoodDraftCommandOrigin = FoodDraftCommandOrigin.AI_REVIEW,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onChange: ((FoodDraft) -> Unit)? = null,
    fillAvailableHeight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val review = draft.reviewCopy(origin)
    var editing by remember { mutableStateOf(false) }
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "Draft review. ${review.sourceLabel}. ${review.actionLabel}. Not saved yet."
        },
    ) {
        Column(
            modifier = if (fillAvailableHeight) {
                Modifier.fillMaxSize().padding(14.dp)
            } else {
                Modifier.fillMaxWidth().padding(14.dp)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = if (fillAvailableHeight) {
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(draft.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(draft.summary, style = MaterialTheme.typography.bodyMedium)
                DraftReviewMeta(draft = draft, origin = origin)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    draft.rows.forEach { row ->
                        Text("- $row", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (editing && onChange != null) {
                    DraftReviewEditor(draft = draft, onChange = onChange)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(draft.acceptButtonLabel())
                }
                if (onChange != null) {
                    OutlinedButton(onClick = { editing = !editing }, shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (editing) "Done editing" else "Edit proposal")
                    }
                }
                OutlinedButton(onClick = onReject, shape = RoundedCornerShape(18.dp)) { Text("Reject") }
            }
        }
    }
}

@Composable
private fun DraftReviewEditor(
    draft: FoodDraft,
    showTitle: Boolean = true,
    onChange: (FoodDraft) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showTitle) {
            Text("Edit before saving", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        when (draft) {
            is CompositeDraft -> draft.drafts.forEachIndexed { index, child ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Action ${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (draft.drafts.size > 1) {
                        TextButton(onClick = { onChange(draft.copy(drafts = draft.drafts.filterIndexed { childIndex, _ -> childIndex != index })) }) {
                            Text("Remove action")
                        }
                    }
                }
                DraftReviewEditor(child) { updated ->
                    onChange(draft.copy(drafts = draft.drafts.toMutableList().also { it[index] = updated }))
                }
                if (index < draft.drafts.lastIndex) HorizontalDivider()
            }
            is InventoryDraft -> CandidateDraftEditor(
                items = draft.items,
                showZone = true,
                onChange = { onChange(draft.copy(items = it)) },
            )
            is GroceryDraft -> CandidateDraftEditor(
                items = draft.items,
                showZone = false,
                onChange = { onChange(draft.copy(items = it)) },
            )
            is ReceiptDraft -> ReceiptDraftEditor(draft = draft, onChange = onChange)
            is LinkActionDraft -> {
                DraftEditorField("Target id", draft.targetRef, "Local numeric id", KeyboardType.Number) {
                    onChange(draft.copy(targetRef = it))
                }
                DraftEditorField("Target or name", draft.displayName, "Exact visible name") {
                    onChange(draft.copy(displayName = it))
                }
                (draft.fields.keys + draft.defaultEditableFields()).sorted().forEach { key ->
                    val value = draft.fields[key].orEmpty()
                    DraftEditorField(
                        label = key.replace('_', ' ').replaceFirstChar(Char::uppercase),
                        value = value,
                        placeholder = key,
                        minLines = if (key in LONG_LINK_FIELDS) 2 else 1,
                    ) { updated ->
                        onChange(draft.copy(fields = draft.fields + (key to updated)))
                    }
                }
            }
            is RecipeDraft -> {
                DraftEditorField("Recipe title", draft.titleText, "Required") { onChange(draft.copy(titleText = it)) }
                DraftEditorField("Ingredients", draft.ingredientsText, "One ingredient per line", minLines = 3) {
                    onChange(draft.copy(ingredientsText = it))
                }
                DraftEditorField("Steps", draft.stepsText, "One step per line", minLines = 3) {
                    onChange(draft.copy(stepsText = it))
                }
                DraftEditorField("Tags", draft.tags, "Optional") { onChange(draft.copy(tags = it)) }
                DraftEditorField("Servings", draft.servings?.toString().orEmpty(), "Unknown is okay", KeyboardType.Number) { value ->
                    if (value.isBlank() || value.toIntOrNull() != null) onChange(draft.copy(servings = value.toIntOrNull()))
                }
                DraftEditorField("Prep minutes", draft.prepMinutes?.toString().orEmpty(), "Unknown is okay", KeyboardType.Number) { value ->
                    if (value.isBlank() || value.toIntOrNull() != null) onChange(draft.copy(prepMinutes = value.toIntOrNull()))
                }
            }
            is MealLogDraft -> {
                DraftEditorField("Meal title", draft.titleText, "Required") { onChange(draft.copy(titleText = it)) }
                DraftEditorField("Calories", draft.calories?.toString().orEmpty(), "Unknown is okay", KeyboardType.Number) { value ->
                    if (value.isBlank() || value.toIntOrNull() != null) onChange(draft.copy(calories = value.toIntOrNull()))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealSlot.entries.forEach { slot ->
                        FilterChip(
                            selected = draft.mealSlot == slot,
                            onClick = { onChange(draft.copy(mealSlot = slot)) },
                            label = { Text(slot.label) },
                        )
                    }
                }
                DraftEditorField("Used pantry items", draft.usedItemsText, "Optional", minLines = 2) {
                    onChange(draft.copy(usedItemsText = it))
                }
                DraftEditorField("Protein (g)", draft.proteinGrams?.toString().orEmpty(), "Unknown is okay", KeyboardType.Decimal) { value ->
                    if (value.isBlank() || value.toDoubleOrNull() != null) onChange(draft.copy(proteinGrams = value.toDoubleOrNull()))
                }
                DraftEditorField("Carbs (g)", draft.carbsGrams?.toString().orEmpty(), "Unknown is okay", KeyboardType.Decimal) { value ->
                    if (value.isBlank() || value.toDoubleOrNull() != null) onChange(draft.copy(carbsGrams = value.toDoubleOrNull()))
                }
                DraftEditorField("Fat (g)", draft.fatGrams?.toString().orEmpty(), "Unknown is okay", KeyboardType.Decimal) { value ->
                    if (value.isBlank() || value.toDoubleOrNull() != null) onChange(draft.copy(fatGrams = value.toDoubleOrNull()))
                }
            }
            is MealPlanDraft -> {
                DraftEditorField("Plan title", draft.titleText, "Required") { onChange(draft.copy(titleText = it)) }
                DraftEditorField("Planned days", draft.daysText, "Dates, slots, and meals", minLines = 4) {
                    onChange(draft.copy(daysText = it))
                }
                DraftEditorField("Shopping note", draft.groceryHint, "Optional", minLines = 2) {
                    onChange(draft.copy(groceryHint = it))
                }
                draft.entries.forEachIndexed { index, entry ->
                    Text("Planned meal ${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    DraftEditorField("Planned meal ${index + 1}", entry.title, "Required") { title ->
                        onChange(draft.copy(entries = draft.entries.toMutableList().also { it[index] = entry.copy(title = title) }))
                    }
                    DraftEditorField("Day offset", entry.dayOffset.toString(), "0 is the first day", KeyboardType.Number) { value ->
                        value.toIntOrNull()?.let { day ->
                            onChange(draft.copy(entries = draft.entries.toMutableList().also { it[index] = entry.copy(dayOffset = day) }))
                        }
                    }
                    DraftEditorField("Calorie target", entry.calorieTarget?.toString().orEmpty(), "Unknown is okay", KeyboardType.Number) { value ->
                        if (value.isBlank() || value.toIntOrNull() != null) {
                            onChange(draft.copy(entries = draft.entries.toMutableList().also { it[index] = entry.copy(calorieTarget = value.toIntOrNull()) }))
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MealSlot.entries.forEach { slot ->
                            FilterChip(
                                selected = entry.slot == slot,
                                onClick = { onChange(draft.copy(entries = draft.entries.toMutableList().also { it[index] = entry.copy(slot = slot) })) },
                                label = { Text(slot.label) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptDraftEditor(
    draft: ReceiptDraft,
    onChange: (FoodDraft) -> Unit,
) {
    DraftEditorField("Merchant", draft.merchant, "Optional store name") { onChange(draft.copy(merchant = it)) }
    DraftEditorField("Store location", draft.storeLocation, "Address, branch, or city when visible") { onChange(draft.copy(storeLocation = it)) }
    DraftEditorField(
        "Purchase date",
        draft.purchasedAtMillis?.toLocalDate()?.toString().orEmpty(),
        "YYYY-MM-DD",
    ) { value ->
        if (value.isBlank() || runCatching { LocalDate.parse(value) }.isSuccess) {
            onChange(
                draft.copy(
                    purchasedAtMillis = value.takeIf(String::isNotBlank)?.let { date ->
                        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    },
                ),
            )
        }
    }
    DraftEditorField("Currency", draft.currencyCode, "USD", KeyboardType.Text) {
        onChange(draft.copy(currencyCode = it.uppercase().take(3)))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReceiptMoneyField("Subtotal", draft.subtotalCents) { onChange(draft.copy(subtotalCents = it)) }
        ReceiptMoneyField("Tax", draft.taxCents) { onChange(draft.copy(taxCents = it)) }
        ReceiptMoneyField("Total", draft.totalCents) { onChange(draft.copy(totalCents = it)) }
    }
    draft.items.forEachIndexed { index, item ->
        val food = item.food
        fun updateFood(transform: (FoodCandidate) -> FoodCandidate) {
            onChange(
                draft.copy(
                    items = draft.items.toMutableList().also { rows ->
                        rows[index] = item.copy(food = transform(food))
                    },
                ),
            )
        }
        Text("Receipt item ${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (item.receiptLine.isNotBlank()) {
            Text(
                "Evidence: ${item.receiptLine}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Confidence ${(food.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ReceiptItemDisposition.entries.forEach { disposition ->
                FilterChip(
                    selected = item.disposition == disposition,
                    onClick = {
                        onChange(
                            draft.copy(
                                items = draft.items.toMutableList().also { rows ->
                                    rows[index] = item.copy(disposition = disposition)
                                },
                            ),
                        )
                    },
                    label = { Text(disposition.label) },
                )
            }
        }
        if (item.disposition == ReceiptItemDisposition.INVENTORY) {
            DraftEditorField("Name", food.name, "Required") { value -> updateFood { it.copy(name = value) } }
            DraftEditorField("Quantity", food.quantity, "Package/count if visible") { value -> updateFood { it.copy(quantity = value) } }
            DraftEditorField(
                "Line cost (${draft.currencyCode.ifBlank { "USD" }})",
                item.linePriceCents.moneyInput(),
                "0.00",
                KeyboardType.Decimal,
            ) { value ->
                if (value.isBlank() || value.moneyCentsOrNull() != null) {
                    onChange(
                        draft.copy(
                            items = draft.items.toMutableList().also { rows ->
                                rows[index] = item.copy(linePriceCents = value.moneyCentsOrNull())
                            },
                        ),
                    )
                }
            }
            DraftEditorField("Category", food.category, "Canonical food category") { value -> updateFood { it.copy(category = value) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StorageZone.entries.forEach { zone ->
                    FilterChip(
                        selected = food.zone == zone,
                        onClick = { updateFood { it.copy(zone = zone, zoneSource = "user_review") } },
                        label = { Text(zone.label) },
                    )
                }
            }
            DraftEditorField(
                "Best before",
                food.expiresAtMillis?.toLocalDate()?.toString().orEmpty(),
                "YYYY-MM-DD; estimated dates need review",
            ) { value ->
                if (value.isBlank() || runCatching { LocalDate.parse(value) }.isSuccess) {
                    updateFood {
                        it.copy(
                            expiresAtMillis = value.takeIf(String::isNotBlank)?.let { date ->
                                LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            },
                            expirySource = if (value.isBlank()) "" else "user_review",
                        )
                    }
                }
            }
            DraftEditorField("Serving", food.servingText, "Unknown is okay") { value -> updateFood { it.copy(servingText = value) } }
            DraftEditorField("Calories", food.calories?.toString().orEmpty(), "Unknown is okay", KeyboardType.Number) { value ->
                if (value.isBlank() || value.toIntOrNull() != null) updateFood { it.copy(calories = value.toIntOrNull()) }
            }
            DraftEditorField("Protein (g)", food.proteinGrams?.toString().orEmpty(), "Unknown is okay", KeyboardType.Decimal) { value ->
                if (value.isBlank() || value.toDoubleOrNull() != null) updateFood { it.copy(proteinGrams = value.toDoubleOrNull()) }
            }
            DraftEditorField("Nutrition source", food.nutritionSource, "label, barcode provider, AI estimate, or blank") { value ->
                updateFood { it.copy(nutritionSource = value) }
            }
            DraftEditorField("Notes", food.notes, "Optional", minLines = 2) { value -> updateFood { it.copy(notes = value) } }
        }
        food.warnings.forEach { warning ->
            Text("Review: $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = { onChange(draft.copy(items = draft.items.filterIndexed { rowIndex, _ -> rowIndex != index })) }) {
            Text("Remove receipt item ${index + 1}")
        }
        if (index < draft.items.lastIndex) HorizontalDivider()
    }
}

@Composable
private fun ReceiptMoneyField(label: String, cents: Long?, onChange: (Long?) -> Unit) {
    DraftEditorField(label, cents.moneyInput(), "0.00", KeyboardType.Decimal) { value ->
        if (value.isBlank() || value.moneyCentsOrNull() != null) onChange(value.moneyCentsOrNull())
    }
}

@Composable
private fun CandidateDraftEditor(
    items: List<FoodCandidate>,
    showZone: Boolean,
    onChange: (List<FoodCandidate>) -> Unit,
) {
    var showNutrition by rememberSaveable { mutableStateOf(false) }
    items.forEachIndexed { index, item ->
        if (items.size > 1) {
            Text("Item ${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        fun update(transform: (FoodCandidate) -> FoodCandidate) {
            onChange(items.toMutableList().also { it[index] = transform(item) })
        }
        DraftEditorField("Name", item.name, "Required") { update { candidate -> candidate.copy(name = it) } }
        DraftEditorField("Quantity", item.quantity, "Optional") { update { candidate -> candidate.copy(quantity = it) } }
        DraftEditorField("Category", item.category, "Optional") { update { candidate -> candidate.copy(category = it) } }
        DraftEditorField("Notes", item.notes, "Optional", minLines = 2) { update { candidate -> candidate.copy(notes = it) } }
        if (showNutrition) {
            DraftEditorField("Serving", item.servingText, "Optional") { update { candidate -> candidate.copy(servingText = it) } }
            DraftEditorField("Calories", item.calories?.toString().orEmpty(), "Unknown is okay", KeyboardType.Number) { value ->
                if (value.isBlank() || value.toIntOrNull() != null) update { candidate -> candidate.copy(calories = value.toIntOrNull()) }
            }
            DraftEditorField("Protein (g)", item.proteinGrams?.toString().orEmpty(), "Unknown is okay", KeyboardType.Decimal) { value ->
                if (value.isBlank() || value.toDoubleOrNull() != null) update { candidate -> candidate.copy(proteinGrams = value.toDoubleOrNull()) }
            }
            DraftEditorField("Carbs (g)", item.carbsGrams?.toString().orEmpty(), "Unknown is okay", KeyboardType.Decimal) { value ->
                if (value.isBlank() || value.toDoubleOrNull() != null) update { candidate -> candidate.copy(carbsGrams = value.toDoubleOrNull()) }
            }
            DraftEditorField("Fat (g)", item.fatGrams?.toString().orEmpty(), "Unknown is okay", KeyboardType.Decimal) { value ->
                if (value.isBlank() || value.toDoubleOrNull() != null) update { candidate -> candidate.copy(fatGrams = value.toDoubleOrNull()) }
            }
        }
        if (showZone) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StorageZone.entries.forEach { zone ->
                    FilterChip(
                        selected = item.zone == zone,
                        onClick = { update { candidate -> candidate.copy(zone = zone) } },
                        label = { Text(zone.label) },
                    )
                }
            }
        }
        if (item.evidence.isNotBlank()) {
            Text("Evidence: ${item.evidence}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.warnings.forEach { warning ->
            Text("Review: $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (items.size > 1) {
            TextButton(onClick = { onChange(items.filterIndexed { itemIndex, _ -> itemIndex != index }) }) {
                Text("Remove item ${index + 1}")
            }
        }
    }
    TextButton(onClick = { showNutrition = !showNutrition }) {
        Text(if (showNutrition) "Hide nutrition fields" else "Edit nutrition fields")
    }
    OutlinedButton(
        onClick = { onChange(items + FoodCandidate(name = "", zone = StorageZone.PANTRY)) },
        shape = RoundedCornerShape(18.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add another item")
    }
}

@Composable
private fun DraftEditorField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        minLines = minLines,
        maxLines = if (minLines > 1) 6 else 1,
        shape = RoundedCornerShape(18.dp),
    )
}

private val LONG_LINK_FIELDS = setOf(
    "allergies",
    "custom_ai_instructions",
    "days",
    "days_text",
    "dislikes",
    "health_notes",
    "ingredients",
    "notes",
    "steps",
    "text",
    "used_items_text",
)

private fun LinkActionDraft.defaultEditableFields(): Set<String> =
    when (targetKind) {
        "inventory" -> setOf("name", "quantity", "zone", "category", "notes")
        "grocery" -> setOf("name", "quantity", "category", "notes", "status")
        "recipe" -> setOf("title", "ingredients", "steps", "servings", "prep_minutes", "tags")
        "meal_log" -> setOf("title", "calories", "protein_g", "carbs_g", "fat_g", "meal_slot", "used_items_text")
        "meal_plan" -> setOf("title", "days_text", "grocery_hint", "date_epoch_day")
        "plan_entry" -> setOf("title", "date_epoch_day", "slot", "calorie_target", "status")
        "preferences" -> setOf(
            "diet_style",
            "allergies",
            "dislikes",
            "preferred_staples",
            "preferred_cuisines",
            "preferred_stores",
            "calorie_goal",
            "protein_goal",
            "health_notes",
            "custom_ai_instructions",
        )
        "event" -> setOf("event_type", "amount", "unit", "notes")
        else -> emptySet()
    }

@Composable
private fun PromptChips(onPrompt: (String) -> Unit, onPickReceiptPhoto: () -> Unit) {
    val prompts = listOf(
        "I bought eggs, Greek yogurt, spinach and frozen berries",
        "Need oats, bananas and chicken thighs",
        "Log chicken rice bowl for lunch",
        "Plan meals this week",
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        prompts.forEach { prompt ->
            SuggestionChip(onClick = { onPrompt(prompt) }, label = { Text(prompt) })
        }
        SuggestionChip(
            onClick = onPickReceiptPhoto,
            label = { Text("Attach receipt") },
            icon = { Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun PantryContent(
    items: List<InventoryItem>,
    onOpen: (InventoryItem) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (items.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyState("No kitchen items yet.", "Add food directly or scan a receipt when you're ready.")
        }
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var zoneFilter by rememberSaveable { mutableStateOf<StorageZone?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf("All") }
    var focusFilter by rememberSaveable { mutableStateOf(KitchenFocusFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf("Recent") }
    var viewMode by rememberSaveable { mutableStateOf(DatabaseViewMode.GALLERY) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val categories = remember(items) { listOf("All") + items.map { it.category.ifBlank { "other" } }.distinct().sorted() }
    val visible = items
        .asSequence()
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true) }
        .filter { zoneFilter == null || it.zone == zoneFilter }
        .filter { categoryFilter == "All" || it.category.equals(categoryFilter, ignoreCase = true) }
        .filter { item ->
            when (focusFilter) {
                KitchenFocusFilter.ALL -> true
                KitchenFocusFilter.USE_SOON -> item.kitchenPriorityRank() <= 1
                KitchenFocusFilter.NEEDS_DETAILS -> item.quantity.isBlank() || item.category.isBlank() ||
                    (item.calories == null && item.nutritionSource.isBlank())
            }
        }
        .let { sequence ->
            when (sort) {
                "Name" -> sequence.sortedBy { it.name.lowercase() }
                "Zone" -> sequence.sortedWith(compareBy<InventoryItem> { it.zone.ordinal }.thenBy { it.name.lowercase() })
                "Category" -> sequence.sortedWith(compareBy<InventoryItem> { it.category }.thenBy { it.name.lowercase() })
                else -> sequence.sortedByDescending { it.updatedAtMillis }
            }
        }
        .toList()
    val selectedVisibleCount = visible.count { it.id in selectedIds }
    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    LaunchedEffect(selectionMode, visible.map { it.id }) {
        if (!selectionMode) selectedIds = emptySet()
        selectedIds = selectedIds.intersect(visible.map { it.id }.toSet())
    }
    if (viewMode == DatabaseViewMode.LIST) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            item {
                KitchenUseFirstRail(items = visible, onOpen = onOpen)
            }
            item {
                KitchenControlPanel(
                    query = query,
                    onQuery = { query = it },
                    zoneFilter = zoneFilter,
                    onZone = { zoneFilter = it },
                    categories = categories,
                    category = categoryFilter,
                    onCategory = { categoryFilter = it },
                    focusFilter = focusFilter,
                    onFocusFilter = { focusFilter = it },
                    sort = sort,
                    onSort = { sort = it },
                    viewMode = viewMode,
                    onViewMode = { viewMode = it },
                    resultCount = visible.size,
                    selectionMode = selectionMode,
                    onSelectionMode = { selectionMode = it },
                )
            }
            if (selectionMode) {
                item {
                    KitchenSelectionBar(
                        selectedCount = selectedVisibleCount,
                        onClear = { selectedIds = emptySet() },
                        onArchive = {
                            val ids = selectedIds
                            selectionMode = false
                            ids.forEach(onDelete)
                        },
                    )
                }
            }
            items(visible, key = { it.id }) { item ->
                InventoryCard(
                    item = item,
                    selected = item.id in selectedIds,
                    selectionMode = selectionMode,
                    onToggleSelected = { toggleSelection(item.id) },
                    onOpen = { if (selectionMode) toggleSelection(item.id) else onOpen(item) },
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                KitchenUseFirstRail(items = visible, onOpen = onOpen)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                KitchenControlPanel(
                    query = query,
                    onQuery = { query = it },
                    zoneFilter = zoneFilter,
                    onZone = { zoneFilter = it },
                    categories = categories,
                    category = categoryFilter,
                    onCategory = { categoryFilter = it },
                    focusFilter = focusFilter,
                    onFocusFilter = { focusFilter = it },
                    sort = sort,
                    onSort = { sort = it },
                    viewMode = viewMode,
                    onViewMode = { viewMode = it },
                    resultCount = visible.size,
                    selectionMode = selectionMode,
                    onSelectionMode = { selectionMode = it },
                )
            }
            if (selectionMode) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    KitchenSelectionBar(
                        selectedCount = selectedVisibleCount,
                        onClear = { selectedIds = emptySet() },
                        onArchive = {
                            val ids = selectedIds
                            selectionMode = false
                            ids.forEach(onDelete)
                        },
                    )
                }
            }
            gridItems(visible, key = { it.id }) { item ->
                InventoryTile(
                    item = item,
                    selected = item.id in selectedIds,
                    selectionMode = selectionMode,
                    onToggleSelected = { toggleSelection(item.id) },
                    onOpen = { if (selectionMode) toggleSelection(item.id) else onOpen(item) },
                )
            }
        }
    }
}

private enum class FoodHubMode(val label: String) {
    CAN_MAKE("Can make"),
    IN_KITCHEN("In kitchen"),
    SAVED("Saved"),
}

@Composable
private fun FoodHubContent(
    memory: HouseholdUiMemory,
    canonicalKitchenPreview: List<CanonicalKitchenPreviewItem>,
    canonicalRecipeMatches: List<CanonicalRecipeMatchItem>,
    onCanonicalAddToCart: (String) -> Unit,
    onCanonicalArchive: (String) -> Unit,
    onOpenInventory: (InventoryItem) -> Unit,
    onDeleteInventory: (Long) -> Unit,
    onOpenRecipe: (Recipe) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(FoodHubMode.CAN_MAKE) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Food", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Cook from what you have, manage the kitchen, or open saved recipes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FoodHubMode.entries.forEach { option ->
                        SuggestionChip(
                            onClick = { mode = option },
                            label = { Text(option.label) },
                            icon = if (mode == option) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        when (mode) {
            FoodHubMode.CAN_MAKE -> CanMakeRecipesContent(
                memory = memory,
                canonicalRecipeMatches = canonicalRecipeMatches,
                onOpen = onOpenRecipe,
            )
            FoodHubMode.IN_KITCHEN -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canonicalKitchenPreview.isNotEmpty()) {
                    CanonicalKitchenPreviewSection(
                        items = canonicalKitchenPreview,
                        onAddToCart = onCanonicalAddToCart,
                        onArchive = onCanonicalArchive,
                    )
                }
                PantryContent(items = memory.inventory, onOpen = onOpenInventory, onDelete = onDeleteInventory)
            }
            FoodHubMode.SAVED -> RecipesContent(
                memory = memory,
                canonicalRecipePreview = emptyList(),
                onOpen = onOpenRecipe,
            )
        }
    }
}

@Composable
private fun CanonicalKitchenPreviewSection(
    items: List<CanonicalKitchenPreviewItem>,
    onAddToCart: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Household kitchen")
        items.take(5).forEach { item ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    when (value) {
                        SwipeToDismissBoxValue.StartToEnd -> onAddToCart(item.id)
                        SwipeToDismissBoxValue.EndToStart -> onArchive(item.id)
                        SwipeToDismissBoxValue.Settled -> Unit
                    }
                    false
                },
            )
            SwipeToDismissBox(
                modifier = Modifier.semantics {
                    contentDescription = "Kitchen row ${item.title}"
                },
                state = dismissState,
                backgroundContent = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Add to cart", style = MaterialTheme.typography.labelMedium)
                        Text("Archive", style = MaterialTheme.typography.labelMedium)
                    }
                },
            ) {
                MemoryCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    accent = MaterialTheme.colorScheme.tertiaryContainer,
                    image = "K",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onAddToCart(item.id) }) {
                                Icon(Icons.Rounded.ShoppingCart, contentDescription = "Add ${item.title} to cart")
                            }
                            IconButton(onClick = { onArchive(item.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Archive ${item.title}")
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CanMakeRecipesContent(
    memory: HouseholdUiMemory,
    canonicalRecipeMatches: List<CanonicalRecipeMatchItem>,
    onOpen: (Recipe) -> Unit,
) {
    if (canonicalRecipeMatches.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item { SectionLabel("Best from your kitchen") }
            items(canonicalRecipeMatches.take(8), key = { it.id }) { item ->
                MemoryCard(
                    title = item.title,
                    subtitle = "${item.status}  ${item.subtitle}",
                    accent = when (item.status) {
                        "Can make" -> MaterialTheme.colorScheme.primaryContainer
                        "Almost" -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    image = "R",
                    onOpen = {
                        memory.recipes.firstOrNull { recipe -> recipe.title.equals(item.title, ignoreCase = true) }
                            ?.let(onOpen)
                    },
                )
            }
        }
        return
    }
    val ranked = remember(memory.recipes, memory.inventory) {
        memory.recipes
            .map { recipe -> recipe to recipe.kitchenMatch(memory) }
            .filter { (_, match) -> match.have.isNotEmpty() || match.need.isNotEmpty() }
            .sortedWith(
                compareByDescending<Pair<Recipe, RecipeKitchenMatch>> { (_, match) ->
                    val total = match.have.size + match.need.size
                    if (total == 0) 0.0 else match.have.size.toDouble() / total
                }.thenBy { (_, match) -> match.need.size },
            )
    }
    if (ranked.isEmpty()) {
        EmptyState("No matched recipes yet.", "Save recipes with ingredients, then WonderFood will rank them from your Kitchen.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item {
            SectionLabel("Best from your kitchen")
        }
        items(ranked, key = { it.first.id }) { (recipe, match) ->
            RecipeCard(
                recipe = recipe,
                match = match,
                onOpen = { onOpen(recipe) },
                onCook = { onOpen(recipe) },
            )
        }
    }
}

private enum class KitchenFocusFilter(val label: String) {
    ALL("All food"),
    USE_SOON("Use soon"),
    NEEDS_DETAILS("Needs details"),
}

@Composable
private fun KitchenUseFirstRail(items: List<InventoryItem>, onOpen: (InventoryItem) -> Unit) {
    val focusItems = remember(items) {
        items.sortedWith(
            compareBy<InventoryItem> { it.kitchenPriorityRank() }
                .thenBy { it.expiresAtMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.updatedAtMillis },
        ).take(8)
    }
    if (focusItems.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Use first", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${items.size} Kitchen item${items.size.pluralWord}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(focusItems, key = { it.id }) { item ->
                KitchenFocusChip(item = item, onOpen = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun KitchenFocusChip(item: InventoryItem, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.width(204.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = kitchenFocusColor(item),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoodImage(image = item.imageUri ?: foodEmoji(item.name), fallback = foodEmoji(item.name), color = MaterialTheme.colorScheme.surface, size = 52.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.kitchenStateLabel(), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun KitchenControlPanel(
    query: String,
    onQuery: (String) -> Unit,
    zoneFilter: StorageZone?,
    onZone: (StorageZone?) -> Unit,
    categories: List<String>,
    category: String,
    onCategory: (String) -> Unit,
    focusFilter: KitchenFocusFilter,
    onFocusFilter: (KitchenFocusFilter) -> Unit,
    sort: String,
    onSort: (String) -> Unit,
    viewMode: DatabaseViewMode,
    onViewMode: (DatabaseViewMode) -> Unit,
    resultCount: Int,
    selectionMode: Boolean,
    onSelectionMode: (Boolean) -> Unit,
) {
    val chips = buildList {
        add(DatabaseChip("$resultCount visible", false) {})
        KitchenFocusFilter.entries.forEach { option ->
            add(DatabaseChip(option.label, focusFilter == option) { onFocusFilter(option) })
        }
        add(DatabaseChip("All zones", zoneFilter == null) { onZone(null) })
        StorageZone.entries.forEach { zone ->
            add(DatabaseChip(zone.label, zoneFilter == zone) { onZone(zone) })
        }
        categories.forEach { option ->
            add(DatabaseChip(option, category == option) { onCategory(option) })
        }
        listOf("Recent", "Name", "Zone", "Category").forEach { option ->
            add(DatabaseChip("Sort: $option", sort == option) { onSort(option) })
        }
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search food, category, notes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(18.dp),
                )
                GalleryListToggle(selected = viewMode, onSelected = onViewMode)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectionMode,
                        onClick = { onSelectionMode(!selectionMode) },
                        label = { Text(if (selectionMode) "Selecting" else "Select") },
                    )
                }
                items(chips.size) { index ->
                    val chip = chips[index]
                    FilterChip(selected = chip.selected, onClick = chip.onClick, label = { Text(chip.label, maxLines = 1) })
                }
            }
        }
    }
}

@Composable
private fun KitchenSelectionBar(selectedCount: Int, onClear: () -> Unit, onArchive: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✓", style = MaterialTheme.typography.titleMedium)
            Text(
                "$selectedCount selected",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            ConfirmActionButton(
                label = "Archive",
                title = "Archive $selectedCount kitchen item${selectedCount.pluralWord}?",
                body = "The selected items leave the active kitchen list. You can undo the latest archive from the confirmation bar.",
                onConfirm = onArchive,
            )
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

@Composable
private fun GroceryContent(
    memory: HouseholdUiMemory,
    canonicalCartPreview: List<CanonicalCartPreviewItem>,
    mode: ShopMode,
    onModeChange: (ShopMode) -> Unit,
    onOpen: (GroceryItem) -> Unit,
    onBought: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onCanonicalBought: (String) -> Unit,
    onCanonicalArchive: (String) -> Unit,
    onOpenReceipt: (ReceiptCapture) -> Unit = {},
) {
    val items = memory.groceries
    val receipts = memory.receipts.sortedByDescending { it.createdAtMillis }
    val needed = items.filter { it.status == GroceryStatus.NEEDED }
    val bought = items.filter { it.status == GroceryStatus.BOUGHT }
    val extractedReceipts = receipts.filter { it.status == ReceiptStatus.EXTRACTED }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        if (mode == ShopMode.TO_BUY && canonicalCartPreview.isNotEmpty()) {
            item {
                CanonicalCartPreviewSection(
                    items = canonicalCartPreview,
                    onBought = onCanonicalBought,
                    onArchive = onCanonicalArchive,
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ShopMode.entries) { option ->
                    val count = when (option) {
                        ShopMode.TO_BUY -> needed.size
                        ShopMode.RECEIPTS -> receipts.size
                        ShopMode.PUT_AWAY -> bought.size + extractedReceipts.size
                    }
                    FilterChip(
                        selected = mode == option,
                        onClick = { onModeChange(option) },
                        modifier = Modifier.semantics { contentDescription = "Shop mode ${option.label}" },
                        label = { Text("${option.label} ($count)") },
                    )
                }
            }
        }
        when (mode) {
            ShopMode.TO_BUY -> {
                if (needed.isEmpty()) {
                    item { EmptyState("Nothing to buy.", "Add an item or use a plan/recipe to draft missing groceries.") }
                } else {
                    item { SectionLabel("To buy") }
                    items(needed, key = { "needed-${it.id}" }) { item ->
                        GroceryCard(
                            item = item,
                            onOpen = { onOpen(item) },
                            onBought = { onBought(item.id) },
                            onDelete = { onDelete(item.id) },
                        )
                    }
                }
            }
            ShopMode.RECEIPTS -> {
                if (receipts.isEmpty()) {
                    item { EmptyState("No receipts yet.", "Capture a receipt and add a note before review.") }
                } else {
                    item { SectionLabel("Receipt evidence") }
                    items(receipts, key = { "receipt-${it.id}" }) { receipt ->
                        ReceiptCard(receipt = receipt, onOpen = { onOpenReceipt(receipt) })
                    }
                }
            }
            ShopMode.PUT_AWAY -> {
                if (bought.isEmpty() && extractedReceipts.isEmpty()) {
                    item { EmptyState("Put-away queue is clear.", "Bought and extracted food waits here for Kitchen confirmation.") }
                } else {
                    item { SectionLabel("Ready for Kitchen") }
                    items(bought, key = { "bought-${it.id}" }) { item ->
                        GroceryCard(
                            item = item,
                            onOpen = { onOpen(item) },
                            onBought = {},
                            onDelete = { onDelete(item.id) },
                        )
                    }
                    items(extractedReceipts, key = { "putaway-receipt-${it.id}" }) { receipt ->
                        ReceiptCard(receipt = receipt, onOpen = { onOpenReceipt(receipt) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CanonicalCartPreviewSection(
    items: List<CanonicalCartPreviewItem>,
    onBought: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Canonical cart")
        items.take(5).forEach { item ->
            MemoryCard(
                title = item.title,
                subtitle = item.subtitle,
                accent = MaterialTheme.colorScheme.primaryContainer,
                image = "⌂",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onBought(item.id) }) {
                            Icon(Icons.Rounded.Check, contentDescription = "Bought ${item.title}")
                        }
                        IconButton(onClick = { onArchive(item.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove ${item.title}")
                        }
                    }
                },
            )
        }
    }
}

private enum class ShopMode(val label: String) {
    TO_BUY("To buy"),
    RECEIPTS("Receipts"),
    PUT_AWAY("Put away"),
}

@Composable
private fun TodayContent(
    memory: HouseholdUiMemory,
    canonicalSummary: CanonicalHouseholdUiSummary,
    canonicalSpendingPreview: List<CanonicalRecentSpendingItem>,
    healthSummary: HealthDailySummary,
    onOpenDetail: (FoodDetailTarget) -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onLogMeal: (MealSlot?) -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    val todayMeals = memory.mealLogs.filter { it.loggedDateEpochDay == today }
    val todayPlanEntries = memory.mealPlanEntries.filter { it.dateEpochDay == today }
    val receiptNeedingAttention = memory.receipts
        .sortedByDescending { it.createdAtMillis }
        .firstOrNull { it.status == ReceiptStatus.NEEDS_TEXT || it.status == ReceiptStatus.SAVED }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        receiptNeedingAttention?.let { receipt ->
            item {
                TodayAttentionCard(
                    receipt = receipt,
                    onOpen = { onOpenDetail(FoodDetailTarget(FoodDetailKind.RECEIPT, id = receipt.id)) },
                )
            }
        }
        item {
            TodayDashboard(
                memory = memory,
                canonicalSummary = canonicalSummary,
                healthSummary = healthSummary,
                onOpenDetail = onOpenDetail,
                onLogMeal = onLogMeal,
            )
        }
        if (canonicalSpendingPreview.isNotEmpty()) {
            item { CanonicalSpendingPreviewSection(items = canonicalSpendingPreview) }
        }
        if (memory.inventory.isNotEmpty()) {
            item { KitchenUseFirstRail(items = memory.inventory, onOpen = { item ->
                onOpenDetail(FoodDetailTarget(FoodDetailKind.INVENTORY, id = item.id))
            }) }
        }
        if (todayPlanEntries.isNotEmpty()) {
            item { SectionLabel("Planned today") }
            items(todayPlanEntries, key = { it.id }) { entry ->
                TodayPlanEntryCard(
                    entry = entry,
                    onOpen = { onOpenDetail(FoodDetailTarget(FoodDetailKind.DAY, epochDay = today)) },
                )
            }
        }
        if (todayMeals.isNotEmpty()) item { SectionLabel("Logged today") }
        items(todayMeals, key = { it.id }) { meal ->
            MealCard(
                meal = meal,
                onOpen = { onOpenDetail(FoodDetailTarget(FoodDetailKind.MEAL, id = meal.id)) },
                onDelete = { onDeleteMeal(meal.id) },
            )
        }
    }
}

@Composable
private fun CanonicalSpendingPreviewSection(items: List<CanonicalRecentSpendingItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Recent spending")
        items.take(5).forEach { item ->
            MemoryCard(
                title = item.title,
                subtitle = item.subtitle,
                accent = MaterialTheme.colorScheme.secondaryContainer,
                image = "$",
            )
        }
    }
}

@Composable
private fun TodayAttentionCard(receipt: ReceiptCapture, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ObjectImage(image = "🧾", color = MaterialTheme.colorScheme.surface, size = 44.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Receipt needs review", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (receipt.status == ReceiptStatus.NEEDS_TEXT) "Add clearer text or context before extraction."
                    else "Review the capture before items enter Kitchen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text("Review", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PlanContent(
    memory: HouseholdUiMemory,
    canonicalWeekPreview: List<CanonicalWeekPlanItem>,
    showCalendar: Boolean,
    onOpenDetail: (FoodDetailTarget) -> Unit,
    onDeleteMealPlanEntries: (Set<Long>) -> Unit,
    onDeleteAllPlans: () -> Unit,
) {
    val plans = memory.mealPlans
    val plannedEntries = memory.mealPlanEntries
    val actualDays = remember(memory) {
        calendarSlots(memory).filter { day ->
            day.meals.isNotEmpty() ||
                day.shopping.isNotEmpty() ||
                day.recipes.isNotEmpty() ||
                day.inventoryChanges.isNotEmpty() ||
                day.events.isNotEmpty()
        }
    }
    var filter by rememberSaveable { mutableStateOf(PlanFilter.PLANNED) }
    var selectedEntryIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val selectedSet = selectedEntryIds.toSet()
    fun toggleSelection(id: Long) {
        selectedEntryIds = if (id in selectedSet) {
            selectedEntryIds.filterNot { it == id }
        } else {
            selectedEntryIds + id
        }
    }
    LaunchedEffect(plannedEntries) {
        val validIds = plannedEntries.map { it.id }.toSet()
        selectedEntryIds = selectedEntryIds.filter { it in validIds }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            PlanWeekStrip(memory = memory, onOpenDay = { day ->
                onOpenDetail(FoodDetailTarget(FoodDetailKind.DAY, epochDay = day))
            })
        }
        if (canonicalWeekPreview.isNotEmpty()) {
            item { CanonicalWeekPreviewSection(items = canonicalWeekPreview) }
        }
        if (plans.isNotEmpty() || plannedEntries.isNotEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ConfirmIconTextButton(
                        Icons.Rounded.Delete,
                        "Delete all plans",
                        "Delete all meal plans and planned entries?",
                        {
                            selectedEntryIds = emptyList()
                            onDeleteAllPlans()
                        },
                    )
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PlanFilter.entries) { option ->
                    val count = when (option) {
                        PlanFilter.PLANS -> plans.size
                        PlanFilter.PLANNED -> plannedEntries.size
                        PlanFilter.ACTUAL -> actualDays.size
                    }
                    FilterChip(
                        selected = filter == option,
                        onClick = {
                            filter = option
                            if (option != PlanFilter.PLANNED) selectedEntryIds = emptyList()
                        },
                        label = { Text("${option.label} ($count)") },
                    )
                }
            }
        }
        if (filter == PlanFilter.PLANNED && selectedEntryIds.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${selectedEntryIds.size} selected",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        ConfirmIconTextButton(
                            Icons.Rounded.Delete,
                            "Delete",
                            "Delete ${selectedEntryIds.size} planned meal${selectedEntryIds.size.pluralWord}?",
                            {
                                onDeleteMealPlanEntries(selectedSet)
                                selectedEntryIds = emptyList()
                            },
                        )
                        TextButton(onClick = { selectedEntryIds = emptyList() }) { Text("Clear") }
                    }
                }
            }
        }
        when (filter) {
            PlanFilter.PLANS -> {
                if (plans.isEmpty()) {
                    item {
                        EmptyState(
                            "No meal plan yet.",
                            "Open today when you're ready to add the first planned meal.",
                        )
                    }
                } else {
                    item { SectionLabel("Plans") }
                }
                items(plans, key = { it.id }) { plan ->
                    MealPlanCard(
                        plan = plan,
                        onOpen = { onOpenDetail(FoodDetailTarget(FoodDetailKind.PLAN, id = plan.id)) },
                    )
                }
            }
            PlanFilter.PLANNED -> {
                if (plannedEntries.isEmpty()) {
                    item {
                        EmptyState(
                            "No planned meals.",
                            "Open today when you're ready to add the first planned meal.",
                        )
                    }
                } else {
                    item { SectionLabel("Planned entries") }
                }
                items(plannedEntries, key = { it.id }) { entry ->
                    PlannedEntrySelectableCard(
                        entry = entry,
                        selected = entry.id in selectedSet,
                        selectionMode = selectedEntryIds.isNotEmpty(),
                        onOpen = {
                            if (selectedEntryIds.isNotEmpty()) {
                                toggleSelection(entry.id)
                            } else {
                                onOpenDetail(FoodDetailTarget(FoodDetailKind.DAY, epochDay = entry.dateEpochDay))
                            }
                        },
                        onSelect = { toggleSelection(entry.id) },
                    )
                }
            }
            PlanFilter.ACTUAL -> {
                if (actualDays.isEmpty()) {
                    item {
                        EmptyState(
                            "No actual activity yet.",
                            "Logged meals, shopping, cooking, and water will show here.",
                        )
                    }
                } else {
                    item { SectionLabel("Actual activity") }
                }
                items(actualDays, key = { it.date.toEpochDay() }) { day ->
                    CalendarDayCard(
                        day = day,
                        onOpen = { onOpenDetail(FoodDetailTarget(FoodDetailKind.DAY, epochDay = day.date.toEpochDay())) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CanonicalWeekPreviewSection(items: List<CanonicalWeekPlanItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Household week")
        items.take(7).forEach { item ->
            MemoryCard(
                title = item.title,
                subtitle = item.subtitle,
                accent = MaterialTheme.colorScheme.tertiaryContainer,
                image = "W",
            )
        }
    }
}

@Composable
private fun PlanWeekStrip(memory: HouseholdUiMemory, onOpenDay: (Long) -> Unit) {
    val today = LocalDate.now()
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val days = remember(today) { (0L..6L).map(weekStart::plusDays) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("This week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${memory.mealPlanEntries.count { it.dateEpochDay in days.first().toEpochDay()..days.last().toEpochDay() }} planned",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days, key = { it.toEpochDay() }) { day ->
                val planned = memory.mealPlanEntries.count { it.dateEpochDay == day.toEpochDay() }
                val eaten = memory.mealLogs.count { it.loggedDateEpochDay == day.toEpochDay() }
                val isToday = day == today
                Surface(
                    modifier = Modifier
                        .width(72.dp)
                        .semantics { role = Role.Button }
                        .clickable { onOpenDay(day.toEpochDay()) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelMedium)
                        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                eaten > 0 -> "$eaten eaten"
                                planned > 0 -> "$planned plan"
                                else -> "Open"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private enum class PlanFilter(val label: String) {
    PLANS("Plans"),
    PLANNED("Planned"),
    ACTUAL("Actual"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlannedEntrySelectableCard(
    entry: MealPlanEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = "Planned meal entry ${entry.title}"
            }
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onSelect,
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (selected) "✓" else foodEmoji(entry.title), style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${entry.dateEpochDay.epochDayShortDate()} • ${entry.slot.label}" +
                        (entry.calorieTarget?.let { " • ${it} kcal" }.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onSelect) {
                Text(if (selected) "Selected" else if (selectionMode) "Select" else "Select")
            }
        }
    }
}

@Composable
private fun TodayPlanEntryCard(entry: MealPlanEntry, onOpen: () -> Unit) {
    MemoryCard(
        title = "${entry.slot.label}: ${entry.title}",
        subtitle = entry.calorieTarget?.let { "$it kcal target" } ?: "Planned meal",
        accent = MaterialTheme.colorScheme.secondaryContainer,
        image = foodEmoji(entry.title),
        onOpen = onOpen,
    )
}

@Composable
private fun SearchContent(
    memory: HouseholdUiMemory,
    canonicalItems: List<CanonicalHouseholdSearchItem>,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (FoodDetailTarget) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val allResults = remember(memory) { memory.globalSearchResults() }
    val canonicalResults = remember(canonicalItems) {
        canonicalItems.map { item ->
            SearchResult(
                kind = "canonical",
                id = item.id,
                title = item.name,
                subtitle = item.subtitle,
                image = foodEmoji(item.name),
                target = null,
            )
        }
    }
    val visible = remember(allResults, canonicalResults, query) {
        val clean = query.trim()
        if (clean.isBlank()) {
            allResults.take(12)
        } else {
            canonicalResults + allResults.filter { result ->
                result.kind != "kitchen" && (
                    result.title.contains(clean, ignoreCase = true) ||
                        result.subtitle.contains(clean, ignoreCase = true)
                    )
            }
        }
    }
    LaunchedEffect(Unit) {
        onSearchQueryChange("")
        searchFocusRequester.requestFocus()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item {
            DetailShell(
                image = "🔎",
                title = "Search",
                subtitle = "Kitchen, plans, recipes, shopping, receipts",
                onBack = onBack,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearchQueryChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .semantics { contentDescription = "WonderFood search text" },
                    placeholder = { Text("Search meals, recipes, kitchen, shopping") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                )
            }
        }
        item {
            SectionLabel(if (query.isBlank()) "Recent food objects" else "${visible.size} result${visible.size.pluralWord}")
        }
        if (visible.isEmpty()) {
            item { EmptyState("No matches.", "Try a food, recipe, meal, receipt, or shopping item.") }
        }
        items(visible, key = { "${it.kind}-${it.id}-${it.title}" }) { result ->
            SearchResultCard(result = result, onOpen = result.target?.let { target -> { onOpenDetail(target) } })
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResult, onOpen: (() -> Unit)?) {
    MemoryCard(
        title = result.title,
        subtitle = result.subtitle,
        accent = MaterialTheme.colorScheme.surfaceVariant,
        image = result.image,
        onOpen = onOpen,
    )
}

@Composable
private fun TodayDashboard(
    memory: HouseholdUiMemory,
    canonicalSummary: CanonicalHouseholdUiSummary,
    healthSummary: HealthDailySummary,
    onOpenDetail: (FoodDetailTarget) -> Unit,
    onLogMeal: (MealSlot) -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    val todayMeals = memory.mealLogs.filter { it.loggedDateEpochDay == today }
    val todayPlanEntries = memory.mealPlanEntries.filter { it.dateEpochDay == today }
    val calories = todayMeals.mapNotNull { it.calories }.sum()
    val protein = todayMeals.mapNotNull { it.proteinGrams }.sum().toInt()
    val hasCalories = todayMeals.any { it.calories != null }
    val hasProtein = todayMeals.any { it.proteinGrams != null }
    val calorieGoal = memory.preferences.calorieGoal.firstNumberOrNull()
    val proteinGoal = memory.preferences.proteinGoal.firstNumberOrNull()
    val todayEvents = memory.events.filter { it.startedAtMillis.toLocalDate().toEpochDay() == today }
    val waterMl = todayEvents
        .filter { it.type == FoodEventType.WATER && it.unit == "ml" }
        .sumOf { it.amount ?: 0.0 }
        .toInt()
    val shopCount = todayEvents.count { it.type == FoodEventType.SHOP || it.type == FoodEventType.GROCERY_PURCHASE }
    val cookCount = todayEvents.count { it.type == FoodEventType.COOK }
    val canonicalDashboardLabel = canonicalSummary.dashboardLabel()
    val canonicalSpendingLabel = canonicalSummary.spendingDashboardLabel()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ObjectImage(image = "🍽️", color = MaterialTheme.colorScheme.primaryContainer, size = 44.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Meal timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            todayMeals.isNotEmpty() -> "${todayMeals.size} logged • ${todayPlanEntries.size} planned"
                            todayPlanEntries.isNotEmpty() -> "${todayPlanEntries.size} planned • nothing logged yet"
                            else -> "No plan required—log only what is useful."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER, MealSlot.SNACK).forEach { slot ->
                val planned = todayPlanEntries.firstOrNull { it.slot == slot }
                val eaten = todayMeals.firstOrNull { it.mealSlot == slot }
                MealSlotRow(
                    slot = slot,
                    planned = planned,
                    eaten = eaten,
                    onOpen = {
                        when {
                            eaten != null -> onOpenDetail(FoodDetailTarget(FoodDetailKind.MEAL, id = eaten.id))
                            planned != null -> onOpenDetail(FoodDetailTarget(FoodDetailKind.DAY, epochDay = today))
                            else -> onLogMeal(slot)
                        }
                    },
                )
            }
        }
        if (hasCalories || hasProtein || waterMl > 0 || shopCount > 0 || cookCount > 0 ||
            healthSummary.steps != null || healthSummary.activeCaloriesKcal != null ||
            canonicalDashboardLabel != null || canonicalSpendingLabel != null
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                canonicalDashboardLabel?.let { item { MetricPill("⌂", it) } }
                canonicalSpendingLabel?.let { item { MetricPill("💵", it) } }
                if (hasCalories) item {
                    MetricPill("🔥", calorieGoal?.let { "$calories/$it kcal" } ?: "$calories kcal")
                }
                if (hasProtein) item {
                    MetricPill("💪", proteinGoal?.let { "$protein/${it}g" } ?: "${protein}g")
                }
                if (waterMl > 0) item { MetricPill("💧", "${waterMl / 1000.0} L") }
                if (shopCount > 0) item { MetricPill("🛒", "$shopCount shop") }
                if (cookCount > 0) item { MetricPill("🍳", "$cookCount cook") }
                healthSummary.steps?.let { item { MetricPill("👟", "${it.compactCount()} steps") } }
                healthSummary.activeCaloriesKcal?.let { item { MetricPill("⚡", "$it active") } }
            }
        }
        if (memory.actions.isNotEmpty() || memory.inventoryTransactions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Latest change")
                LatestDecisionStrip(
                    action = memory.actions.firstOrNull(),
                    transaction = memory.inventoryTransactions.firstOrNull(),
                )
            }
        }
    }
}

@Composable
private fun LatestDecisionStrip(action: ChatAction?, transaction: InventoryTransaction?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        action?.let {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (it.status == ChatActionStatus.ACCEPTED) "✅" else "↩", style = MaterialTheme.typography.titleMedium)
                    Text(
                        it.summary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        transaction?.let {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(inventoryActionIcon(it.action), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${it.action.label}: ${it.itemName}",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricPill(icon: String, value: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MealSlotRow(
    slot: MealSlot,
    planned: MealPlanEntry?,
    eaten: MealLog?,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(slot.iconText, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(slot.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    eaten?.let { "Ate ${foodEmoji(it.title)} ${it.title}" }
                        ?: planned?.let { "Planned ${foodEmoji(it.title)} ${it.title}" }
                        ?: "Open",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                when {
                    eaten != null -> "✅"
                    planned != null -> "🗓️"
                    else -> "＋"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun RecipesContent(
    memory: HouseholdUiMemory,
    canonicalRecipePreview: List<CanonicalSavedRecipeItem>,
    onOpen: (Recipe) -> Unit,
) {
    val recipes = memory.recipes
    if (recipes.isEmpty() && canonicalRecipePreview.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyState("No personal recipes yet.", "Create one directly or ask AI to draft it from your Kitchen.")
        }
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var tagFilter by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf("Recent") }
    var viewMode by rememberSaveable { mutableStateOf(DatabaseViewMode.GALLERY) }
    var availability by rememberSaveable { mutableStateOf(RecipeAvailability.ALL) }
    val kitchenMatches = remember(recipes, memory.inventory) { recipes.associateWith { it.kitchenMatch(memory) } }
    val tags = remember(recipes) {
        listOf("All") + recipes.flatMap { it.tags.split(",") }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val visible = recipes
        .asSequence()
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.ingredients.contains(query, ignoreCase = true) || it.tags.contains(query, ignoreCase = true) }
        .filter { tagFilter == "All" || it.tags.contains(tagFilter, ignoreCase = true) }
        .filter { recipe ->
            val match = kitchenMatches.getValue(recipe)
            when (availability) {
                RecipeAvailability.ALL -> true
                RecipeAvailability.MAKE_NOW -> match.have.isNotEmpty() && match.need.isEmpty()
                RecipeAvailability.ALMOST -> match.need.size in 1..2
            }
        }
        .let { sequence ->
            when (sort) {
                "Name" -> sequence.sortedBy { it.title.lowercase() }
                "Time" -> sequence.sortedBy { it.prepMinutes ?: Int.MAX_VALUE }
                "Cooked" -> sequence.sortedByDescending { recipe -> memory.mealLogs.count { it.title.recipeMatches(recipe.title) } }
                else -> sequence.sortedByDescending { it.updatedAtMillis }
            }
        }
        .toList()
    if (viewMode == DatabaseViewMode.LIST) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (canonicalRecipePreview.isNotEmpty()) {
                item { CanonicalRecipePreviewSection(items = canonicalRecipePreview) }
            }
            item {
                RecipeAvailabilityFilters(
                    selected = availability,
                    matches = kitchenMatches.values,
                    onSelected = { availability = it },
                )
            }
            item {
                RecipeControlPanel(
                    query = query,
                    onQuery = { query = it },
                    tags = tags,
                    tag = tagFilter,
                    onTag = { tagFilter = it },
                    sort = sort,
                    onSort = { sort = it },
                    viewMode = viewMode,
                    onViewMode = { viewMode = it },
                    resultCount = visible.size,
                )
            }
            items(visible, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    match = kitchenMatches.getValue(recipe),
                    onOpen = { onOpen(recipe) },
                    onCook = { onOpen(recipe) },
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(190.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            if (canonicalRecipePreview.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CanonicalRecipePreviewSection(items = canonicalRecipePreview)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                RecipeAvailabilityFilters(
                    selected = availability,
                    matches = kitchenMatches.values,
                    onSelected = { availability = it },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                RecipeControlPanel(
                    query = query,
                    onQuery = { query = it },
                    tags = tags,
                    tag = tagFilter,
                    onTag = { tagFilter = it },
                    sort = sort,
                    onSort = { sort = it },
                    viewMode = viewMode,
                    onViewMode = { viewMode = it },
                    resultCount = visible.size,
                )
            }
            gridItems(visible, key = { it.id }) { recipe ->
                RecipeTile(
                    recipe = recipe,
                    match = kitchenMatches.getValue(recipe),
                    lastHad = memory.lastHad(recipe),
                    onOpen = { onOpen(recipe) },
                    onCook = { onOpen(recipe) },
                )
            }
        }
    }
}

@Composable
private fun CanonicalRecipePreviewSection(items: List<CanonicalSavedRecipeItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Household recipes")
        items.take(6).forEach { item ->
            MemoryCard(
                title = item.title,
                subtitle = item.subtitle,
                accent = MaterialTheme.colorScheme.primaryContainer,
                image = "R",
            )
        }
    }
}

private enum class RecipeAvailability(val label: String) {
    MAKE_NOW("Make now"),
    ALMOST("Almost"),
    ALL("All recipes"),
}

@Composable
private fun RecipeAvailabilityFilters(
    selected: RecipeAvailability,
    matches: Collection<RecipeKitchenMatch>,
    onSelected: (RecipeAvailability) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(RecipeAvailability.entries) { option ->
            val count = when (option) {
                RecipeAvailability.MAKE_NOW -> matches.count { it.have.isNotEmpty() && it.need.isEmpty() }
                RecipeAvailability.ALMOST -> matches.count { it.need.size in 1..2 }
                RecipeAvailability.ALL -> matches.size
            }
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text("${option.label} ($count)") },
            )
        }
    }
}

@Composable
private fun RecipeControlPanel(
    query: String,
    onQuery: (String) -> Unit,
    tags: List<String>,
    tag: String,
    onTag: (String) -> Unit,
    sort: String,
    onSort: (String) -> Unit,
    viewMode: DatabaseViewMode,
    onViewMode: (DatabaseViewMode) -> Unit,
    resultCount: Int,
) {
    val chips = buildList {
        tags.forEach { option ->
            add(DatabaseChip(option, tag == option) { onTag(option) })
        }
        listOf("Recent", "Name", "Time", "Cooked").forEach { option ->
            add(DatabaseChip("Sort: $option", sort == option) { onSort(option) })
        }
    }
    DatabaseToolbar(
        icon = "📖",
        title = "Recipe files",
        subtitle = "$resultCount visible",
        query = query,
        placeholder = "Search recipes or ingredients",
        onQuery = onQuery,
        viewMode = viewMode,
        onViewModeChange = onViewMode,
        chips = chips,
    )
}

@Composable
private fun PreferencesContent(
    memory: HouseholdUiMemory,
    preferences: FoodPreferences,
    aiConfig: LiteLlmConfig,
    aiFallbackConfig: LiteLlmConfig,
    aiStatus: String,
    healthStatus: String,
    healthSummary: HealthDailySummary,
    syncStatus: String,
    canonicalSummary: CanonicalHouseholdUiSummary,
    backendHome: BackendHomeUiState,
    lifeOsDomains: List<LifeOsDomain>,
    selectedLifeOsDomainId: String,
    workspaceConflictInbox: WorkspaceConflictInbox?,
    googleAccountEmail: String,
    googleOAuthClientId: String,
    googleSyncStatus: String,
    settingsSaveStatus: String,
    themeMode: WonderFoodThemeMode,
    onChange: (FoodPreferences) -> Unit,
    onSave: () -> Unit,
    onAiConfigChange: (LiteLlmConfig) -> Unit,
    onAiFallbackConfigChange: (LiteLlmConfig) -> Unit,
    onSaveAiConfig: () -> Unit,
    onCreateEncryptedBackup: (String) -> Unit,
    onRestoreLatestEncryptedBackup: (String) -> Unit,
    onGoogleOAuthClientIdChange: (String) -> Unit,
    onConnectGoogle: () -> Unit,
    onBackupToGoogleDrive: () -> Unit,
    onRestoreFromGoogleDrive: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onTestAiConnection: (LiteLlmConfig) -> Unit,
    onDeleteAllAppData: () -> Unit,
    onChooseLocalBackend: () -> Unit,
    onValidateGoogleSheetsBackend: (String) -> Unit,
    onCreateGoogleSheetsBackend: () -> Unit,
    onConnectNotionBackend: (String, String) -> Unit,
    onConnectPostgresBackend: (String, String, String) -> Unit,
    onSelectPendingBackend: (BackendType) -> Unit,
    onSelectLifeOsDomain: (String) -> Unit,
    onClearWorkspaceConflictInbox: () -> Unit,
    onDeleteAllPlans: () -> Unit,
    onClearChatHistory: () -> Unit,
    onThemeModeChange: (WonderFoodThemeMode) -> Unit,
    onRequestHealthConnect: () -> Unit,
    onOpenChat: () -> Unit,
    initialDestination: SettingsDestination = SettingsDestination.HOME,
    onBack: () -> Unit,
) {
    var destination by rememberSaveable(initialDestination) { mutableStateOf(initialDestination) }
    var backupPassphrase by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = destination != SettingsDestination.HOME) {
        destination = SettingsDestination.HOME
    }
    when (destination) {
        SettingsDestination.HOME -> SettingsHomeContent(
            memory = memory,
            preferences = preferences,
            aiStatus = aiStatus,
            healthStatus = healthStatus,
            healthSummary = healthSummary,
            backendHome = backendHome,
            canonicalSummary = canonicalSummary,
            lifeOsDomains = lifeOsDomains,
            selectedLifeOsDomainId = selectedLifeOsDomainId,
            googleAccountEmail = googleAccountEmail,
            googleSyncStatus = googleSyncStatus,
            settingsSaveStatus = settingsSaveStatus,
            themeMode = themeMode,
            onOpen = { destination = it },
            onBack = onBack,
        )
        SettingsDestination.LIFEOS_CONTROL -> SettingsDetailPage(
            image = "🧬",
            title = "LifeOS",
            subtitle = lifeOsDomains.firstOrNull { it.id == selectedLifeOsDomainId }?.let { "${it.label} live · Notion, Sheets, app sources" }
                ?: "Domains, skills, sources, and data homes",
            onBack = { destination = SettingsDestination.HOME },
        ) {
            LifeOsControlCenter(
                domains = lifeOsDomains,
                selectedDomainId = selectedLifeOsDomainId,
                backendHome = backendHome,
                aiStatus = aiStatus,
                healthStatus = healthStatus,
                onSelectDomain = onSelectLifeOsDomain,
                onOpenDataHome = { destination = SettingsDestination.DATA_HOME },
                onOpenAi = { destination = SettingsDestination.AI_ASSISTANT },
                onOpenChat = onOpenChat,
            )
        }
        SettingsDestination.FOOD_PROFILE -> SettingsDetailPage(
            image = "🍽️",
            title = "Food profile",
            subtitle = "Taste, safety, stores, and planning defaults",
            onBack = { destination = SettingsDestination.HOME },
        ) {
            FoodProfileSettings(preferences = preferences, settingsSaveStatus = settingsSaveStatus, onChange = onChange)
        }
        SettingsDestination.GOALS_HEALTH -> SettingsDetailPage(
            image = "❤️",
            title = "Goals & health",
            subtitle = healthStatus,
            onBack = { destination = SettingsDestination.HOME },
        ) {
            GoalsHealthSettings(
                preferences = preferences,
                healthStatus = healthStatus,
                healthSummary = healthSummary,
                settingsSaveStatus = settingsSaveStatus,
                onChange = onChange,
                onRequestHealthConnect = onRequestHealthConnect,
            )
        }
        SettingsDestination.AI_ASSISTANT -> SettingsDetailPage(
            image = "✨",
            title = "Model + skills",
            subtitle = aiStatus,
            onBack = { destination = SettingsDestination.HOME },
        ) {
            AiAssistantSettings(
                preferences = preferences,
                aiConfig = aiConfig,
                aiFallbackConfig = aiFallbackConfig,
                aiStatus = aiStatus,
                settingsSaveStatus = settingsSaveStatus,
                onChange = onChange,
                onAiConfigChange = onAiConfigChange,
                onAiFallbackConfigChange = onAiFallbackConfigChange,
                onSaveAiConfig = onSaveAiConfig,
                onTestAiConnection = onTestAiConnection,
                onClearChatHistory = onClearChatHistory,
            )
        }
        SettingsDestination.APPEARANCE -> SettingsDetailPage(
            image = "◐",
            title = "Appearance",
            subtitle = "${themeMode.displayLabel} theme",
            onBack = { destination = SettingsDestination.HOME },
        ) {
            AppearanceSettings(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
        }
        SettingsDestination.BACKUP_RESTORE -> SettingsDetailPage(
            image = "☁️",
            title = "Backup & restore",
            subtitle = if (googleAccountEmail.isBlank()) "Local only" else "Google Drive app backup",
            onBack = { destination = SettingsDestination.HOME },
        ) {
            BackupRestoreSettings(
                googleAccountEmail = googleAccountEmail,
                googleOAuthClientId = googleOAuthClientId,
                googleSyncStatus = googleSyncStatus,
                onGoogleOAuthClientIdChange = onGoogleOAuthClientIdChange,
                onConnectGoogle = onConnectGoogle,
                onBackupToGoogleDrive = onBackupToGoogleDrive,
                onRestoreFromGoogleDrive = onRestoreFromGoogleDrive,
                onDisconnectGoogle = onDisconnectGoogle,
            )
        }
        SettingsDestination.DATA_HOME -> BackendOnboardingDialog(
            backendHome = backendHome,
            onUseLocal = onChooseLocalBackend,
            onConnectSheets = onValidateGoogleSheetsBackend,
            onCreateSheets = onCreateGoogleSheetsBackend,
            onConnectNotion = onConnectNotionBackend,
            onConnectPostgres = onConnectPostgresBackend,
            onSelectPending = onSelectPendingBackend,
            conflictInbox = workspaceConflictInbox,
            onClearConflictInbox = onClearWorkspaceConflictInbox,
            onDismiss = { destination = SettingsDestination.HOME },
        )
        SettingsDestination.IMPORT_EXPORT_PRIVACY -> SettingsDetailPage(
            image = "⇄",
            title = "Import, export & privacy",
            subtitle = "CSV, encrypted backup, deletion",
            onBack = { destination = SettingsDestination.HOME },
        ) {
            DataPrivacySettings(
                memory = memory,
                syncStatus = syncStatus,
                backupPassphrase = backupPassphrase,
                onBackupPassphraseChange = { backupPassphrase = it },
                onCreateEncryptedBackup = onCreateEncryptedBackup,
                onRestoreLatestEncryptedBackup = onRestoreLatestEncryptedBackup,
                onExportCsv = onExportCsv,
                onImportCsv = onImportCsv,
                onDeleteAllPlans = onDeleteAllPlans,
                onClearChatHistory = onClearChatHistory,
                onDeleteAllAppData = onDeleteAllAppData,
            )
        }
        SettingsDestination.HELP_ABOUT -> SettingsDetailPage(
            image = "?",
            title = "Help & about",
            subtitle = "Voice shortcuts, privacy, diagnostics",
            onBack = { destination = SettingsDestination.HOME },
        ) {
            HelpAboutSettings()
        }
    }
}

@Composable
private fun SettingsHomeContent(
    memory: HouseholdUiMemory,
    preferences: FoodPreferences,
    aiStatus: String,
    healthStatus: String,
    healthSummary: HealthDailySummary,
    backendHome: BackendHomeUiState,
    canonicalSummary: CanonicalHouseholdUiSummary,
    lifeOsDomains: List<LifeOsDomain>,
    selectedLifeOsDomainId: String,
    googleAccountEmail: String,
    googleSyncStatus: String,
    settingsSaveStatus: String,
    themeMode: WonderFoodThemeMode,
    onOpen: (SettingsDestination) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        item {
            DetailShell(
                image = "⚙️",
                title = "Settings",
                subtitle = "Profile, assistant, backup, and app controls",
                onBack = onBack,
            ) {}
        }
        item {
            SettingsAccountSummary(
                googleAccountEmail = googleAccountEmail,
                googleSyncStatus = googleSyncStatus,
                onOpenBackup = { onOpen(SettingsDestination.BACKUP_RESTORE) },
            )
        }
        if (settingsSaveStatus.isNotBlank()) {
            item { SettingsSaveStatus(status = settingsSaveStatus) }
        }
        item { SettingsSectionHeader("YOUR EXPERIENCE") }
        item {
            val selectedDomain = lifeOsDomains.firstOrNull { it.id == selectedLifeOsDomainId }
            SettingsHomeRow(
                icon = Icons.Rounded.Description,
                title = "LifeOS",
                subtitle = selectedDomain?.let { "${it.emoji} ${it.label} · ${it.statusLabel} · ${it.schemaSurfaces.size} surfaces" }
                    ?: "Domains, skills, sources, MCP, and data homes",
                onClick = { onOpen(SettingsDestination.LIFEOS_CONTROL) },
            )
        }
        item {
            SettingsHomeRow(
                icon = Icons.Rounded.Restaurant,
                title = "Food profile",
                subtitle = foodProfileSummary(preferences),
                onClick = { onOpen(SettingsDestination.FOOD_PROFILE) },
            )
        }
        item {
            SettingsHomeRow(
                icon = Icons.Rounded.HealthAndSafety,
                title = "Goals & health",
                subtitle = goalsSummary(preferences, healthStatus, healthSummary),
                onClick = { onOpen(SettingsDestination.GOALS_HEALTH) },
            )
        }
        item { SettingsSectionHeader("ASSISTANT") }
        item {
            SettingsHomeRow(
                icon = Icons.AutoMirrored.Rounded.Chat,
                title = "Model + skills",
                subtitle = aiStatus.ifBlank { "Local fallback" },
                onClick = { onOpen(SettingsDestination.AI_ASSISTANT) },
            )
        }
        item { SettingsSectionHeader("APP") }
        item {
            SettingsHomeRow(
                icon = Icons.Rounded.Settings,
                title = "Appearance",
                subtitle = "${themeMode.displayLabel} theme",
                onClick = { onOpen(SettingsDestination.APPEARANCE) },
            )
        }
        item { SettingsSectionHeader("YOUR DATA") }
        item {
            SettingsHomeRow(
                icon = Icons.Rounded.Inventory2,
                title = "Canonical household store",
                subtitle = canonicalSummary.label(),
                onClick = { onOpen(SettingsDestination.IMPORT_EXPORT_PRIVACY) },
            )
        }
        item {
            SettingsHomeRow(
                icon = Icons.Rounded.Inventory2,
                title = "Data home",
                subtitle = backendHome.settingsSummary(),
                onClick = { onOpen(SettingsDestination.DATA_HOME) },
            )
        }
        item {
            SettingsHomeRow(
                icon = Icons.Rounded.Inventory2,
                title = "Backup & restore",
                subtitle = backupSummary(googleAccountEmail, googleSyncStatus),
                onClick = { onOpen(SettingsDestination.BACKUP_RESTORE) },
            )
        }
        item {
            SettingsHomeRow(
                icon = Icons.Rounded.Inventory2,
                title = "Import, export & privacy",
                subtitle = "${memory.inventory.size} kitchen · ${memory.recipes.size} recipes · ${memory.mealLogs.size} meals",
                onClick = { onOpen(SettingsDestination.IMPORT_EXPORT_PRIVACY) },
            )
        }
        item { SettingsSectionHeader("SUPPORT") }
        item {
            SettingsHomeRow(
                icon = Icons.Rounded.Mic,
                title = "Help & about",
                subtitle = "Voice shortcuts, diagnostics, privacy notes",
                onClick = { onOpen(SettingsDestination.HELP_ABOUT) },
            )
        }
    }
}

@Composable
private fun SettingsDetailPage(
    image: String,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        item {
            SettingsSubpageHeader(image = image, title = title, subtitle = subtitle, onBack = onBack)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun SettingsSubpageHeader(
    image: String,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to settings")
        }
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(image, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsAccountSummary(
    googleAccountEmail: String,
    googleSyncStatus: String,
    onOpenBackup: () -> Unit,
) {
    val connected = googleAccountEmail.isNotBlank()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(onClick = onOpenBackup),
        shape = RoundedCornerShape(18.dp),
        color = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (connected) googleAccountEmail else "Local only", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (connected) googleSyncStatus.oneLine() else "Google Drive backup not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(">", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun SettingsSaveStatus(status: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SettingsHomeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(">", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsStatusBlock(icon: ImageVector, title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsControlGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun LifeOsControlCenter(
    domains: List<LifeOsDomain>,
    selectedDomainId: String,
    backendHome: BackendHomeUiState,
    aiStatus: String,
    healthStatus: String,
    onSelectDomain: (String) -> Unit,
    onOpenDataHome: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val active = domains.firstOrNull { it.id == selectedDomainId } ?: domains.firstOrNull()
    var showArchitecture by rememberSaveable(active?.id ?: "lifeos") { mutableStateOf(false) }
    active?.let { domain ->
        LifeOsHeroCard(
            domain = domain,
            backendHome = backendHome,
            healthStatus = healthStatus,
            onOpenDataHome = onOpenDataHome,
            onOpenChat = onOpenChat,
        )
        LifeOsMetricGrid(domain = domain, backendHome = backendHome, aiStatus = aiStatus, healthStatus = healthStatus)
        Text("Package deck", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
            domains.forEach { option ->
                item(key = option.id) {
                    LifeOsDomainCard(
                        domain = option,
                        selected = option.id == selectedDomainId,
                        onSelect = { onSelectDomain(option.id) },
                    )
                }
            }
        }
        SettingsControlGroup {
            Text("Food workspace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "What is native today, and what the data model can already describe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                domain.bottomTabs.forEach { tab -> DraftReviewPill(tab) }
                domain.schemaSurfaces.take(6).forEach { surface -> DraftReviewPill(surface) }
                val hidden = (domain.schemaSurfaces.size - 6).coerceAtLeast(0)
                if (hidden > 0) DraftReviewPill("+$hidden more")
            }
        }
        SettingsControlGroup {
            Text("Source pack", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LifeOsSourceCard("📱", "App", "Local Food snapshot", Modifier.weight(1f))
                LifeOsSourceCard("📝", "Notion", "Dashboard + rollups", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LifeOsSourceCard("📊", "Sheets", "Auditable mirror", Modifier.weight(1f))
                LifeOsSourceCard("🧬", "MCP", "Schemas + validators", Modifier.weight(1f))
            }
        }
        SettingsControlGroup {
            Text("Skills", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LifeOsSourceCard("🍽️", "Domain", "Food brain", Modifier.weight(1f))
                LifeOsSourceCard("🔁", "Workflow", "Playbooks", Modifier.weight(1f))
                LifeOsSourceCard("📐", "Schema", "Contracts", Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                domain.skills.take(5).forEach { skill -> DraftReviewPill(skill.replace('_', ' ')) }
                if (domain.skills.size > 5) DraftReviewPill("+${domain.skills.size - 5} more")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAi, shape = RoundedCornerShape(18.dp)) {
                    Text("Model + skills")
                }
                OutlinedButton(onClick = onOpenDataHome, shape = RoundedCornerShape(18.dp)) {
                    Text("Data home")
                }
            }
        }
        TextButton(onClick = { showArchitecture = !showArchitecture }) {
            Text(if (showArchitecture) "Hide architecture details" else "Show architecture details")
        }
        if (showArchitecture) {
            LifeOsArchitectureDetails(domain = domain, aiStatus = aiStatus, backendHome = backendHome)
        }
    }
}

@Composable
private fun LifeOsHeroCard(
    domain: LifeOsDomain,
    backendHome: BackendHomeUiState,
    healthStatus: String,
    onOpenDataHome: () -> Unit,
    onOpenChat: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ),
                )
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    ObjectImage(domain.emoji, MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), 58.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${domain.label} LifeOS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            domain.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DraftReviewPill("${domain.schemaSurfaces.size} surfaces")
                    DraftReviewPill("${domain.skills.size} skills")
                    DraftReviewPill(backendHome.chatSourceLabel())
                    DraftReviewPill(healthStatus.ifBlank { "Health ready" }.take(36))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onOpenChat, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                        Text("Open chat")
                    }
                    OutlinedButton(onClick = onOpenDataHome, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                        Text("Data plane")
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeOsMetricGrid(domain: LifeOsDomain, backendHome: BackendHomeUiState, aiStatus: String, healthStatus: String) {
    val notionState = if (backendHome.templateNotionUrl.isNotBlank()) "Linked" else "Ready"
    val sheetsState = if (backendHome.templateSheetsUrl.isNotBlank()) "Linked" else "Ready"
    val healthState = if (healthStatus.isBlank()) "Ready" else "Live"
    val aiDetail = aiStatus.ifBlank { "Local fallback" }.take(28)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LifeOsMetricCard(
                icon = "🍽️",
                label = "Food brain",
                value = "Live",
                detail = "${domain.schemaSurfaces.size} surfaces · ${domain.skills.size} skills",
                modifier = Modifier.weight(1f),
            )
            LifeOsMetricCard(
                icon = "📝",
                label = "Notion",
                value = notionState,
                detail = "Dashboard + rollups",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LifeOsMetricCard(
                icon = "📊",
                label = "Sheets",
                value = sheetsState,
                detail = backendHome.chatSourceLabel(),
                modifier = Modifier.weight(1f),
            )
            LifeOsMetricCard(
                icon = "🛡️",
                label = "Review gate",
                value = "On",
                detail = "$healthState · $aiDetail",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LifeOsMetricCard(icon: String, label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LifeOsDomainCard(domain: LifeOsDomain, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(176.dp)
            .heightIn(min = 132.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onSelect)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f) else MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (selected) 3.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(domain.emoji, style = MaterialTheme.typography.titleLarge)
                Text(domain.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(domain.statusLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(domain.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LifeOsSourceCard(icon: String, title: String, detail: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LifeOsArchitectureDetails(domain: LifeOsDomain, aiStatus: String, backendHome: BackendHomeUiState) {
    SettingsControlGroup {
        Text("Operating playbooks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        domain.operatingLoops.forEach { loop -> LifeOsStatusLine("Loop", loop) }
    }
    SettingsControlGroup {
        Text("Source loop", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        domain.syncLoop.forEach { hop -> LifeOsStatusLine("Hop", hop) }
    }
    SettingsControlGroup {
        Text("Template QA", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        domain.templateHealth.forEach { check -> LifeOsStatusLine("Check", check) }
    }
    SettingsControlGroup {
        Text("Live status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        LifeOsStatusLine("AI/model", aiStatus)
        LifeOsStatusLine("Data home", backendHome.settingsSummary())
        LifeOsStatusLine("Config", "assets/lifeos/domain-catalog.v1.json")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            domain.benchmarks.forEach { benchmark -> DraftReviewPill(benchmark) }
        }
    }
}

@Composable
private fun LifeOsCompactDomainRow(
    domain: LifeOsDomain,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onSelect)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(domain.emoji, style = MaterialTheme.typography.titleMedium)
            Text(domain.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(domain.statusLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (selected) Text("✓", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LifeOsDomainRow(
    domain: LifeOsDomain,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onSelect)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(domain.emoji, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(domain.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("• ${domain.statusLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    domain.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LifeOsStatusLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.widthIn(min = 88.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "Not configured" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FoodProfileSettings(
    preferences: FoodPreferences,
    settingsSaveStatus: String,
    onChange: (FoodPreferences) -> Unit,
) {
    if (settingsSaveStatus.isNotBlank()) SettingsSaveStatus(status = settingsSaveStatus)
    SettingsStatusBlock(
        icon = Icons.Rounded.Restaurant,
        title = "Autosaved profile",
        body = "AI uses this for planning, logging, shopping, and substitutions.",
    )
    PreferenceChipEditor(
        label = "Diet style",
        value = preferences.dietStyle,
        suggestions = listOf("high protein", "vegetarian-ish", "meal prep", "low carb", "South Indian"),
        placeholder = "Add diet style",
        onValue = { onChange(preferences.copy(dietStyle = it)) },
    )
    PreferenceChipEditor(
        label = "Preferred cuisines",
        value = preferences.preferredCuisines,
        suggestions = listOf("South Indian", "Indian", "Mediterranean", "Mexican", "high-protein bowls"),
        placeholder = "Add cuisine",
        onValue = { onChange(preferences.copy(preferredCuisines = it)) },
    )
    PreferenceChipEditor(
        label = "Preferred staples",
        value = preferences.preferredStaples,
        suggestions = listOf("ragi", "rice", "Greek yogurt", "rajma", "chickpeas", "paneer", "tortillas", "oats"),
        placeholder = "Add staple",
        onValue = { onChange(preferences.copy(preferredStaples = it)) },
    )
    SettingsStatusBlock(
        icon = Icons.Rounded.HealthAndSafety,
        title = "Safety constraints",
        body = "Allergies are treated as hard stops. Dislikes are soft preferences the assistant can negotiate.",
    )
    PreferenceChipEditor(
        label = "Allergies",
        value = preferences.allergies,
        suggestions = listOf("peanuts", "tree nuts", "shellfish", "eggs", "lactose", "gluten"),
        placeholder = "Add allergy",
        onValue = { onChange(preferences.copy(allergies = it)) },
    )
    PreferenceChipEditor(
        label = "Dislikes / avoid",
        value = preferences.dislikes,
        suggestions = listOf("mushrooms", "very spicy", "sweet breakfasts", "fried food", "too much cheese"),
        placeholder = "Add avoid",
        onValue = { onChange(preferences.copy(dislikes = it)) },
    )
    PreferenceChipEditor(
        label = "Preferred stores / brands",
        value = preferences.preferredStores,
        suggestions = listOf("Costco", "Trader Joe's", "Indian grocery store", "Whole Foods", "Walmart"),
        placeholder = "Add store",
        onValue = { onChange(preferences.copy(preferredStores = it)) },
    )
}

@Composable
private fun GoalsHealthSettings(
    preferences: FoodPreferences,
    healthStatus: String,
    healthSummary: HealthDailySummary,
    settingsSaveStatus: String,
    onChange: (FoodPreferences) -> Unit,
    onRequestHealthConnect: () -> Unit,
) {
    if (settingsSaveStatus.isNotBlank()) SettingsSaveStatus(status = settingsSaveStatus)
    SettingsControlGroup {
        SettingsUnitTextField(
            label = "Daily calorie target",
            value = preferences.calorieGoal,
            placeholder = "2200",
            unit = "kcal",
            onValue = { onChange(preferences.copy(calorieGoal = it)) },
        )
        SettingsUnitTextField(
            label = "Daily protein target",
            value = preferences.proteinGoal,
            placeholder = "150",
            unit = "g",
            onValue = { onChange(preferences.copy(proteinGoal = it)) },
        )
        PreferenceTextField(
            label = "Health notes",
            value = preferences.healthNotes,
            placeholder = "training days, digestion, sodium, caffeine...",
            minLines = 3,
            onValue = { onChange(preferences.copy(healthNotes = it)) },
        )
    }
    SettingsStatusBlock(
        icon = Icons.Rounded.HealthAndSafety,
        title = "Health Connect",
        body = healthStatus,
    )
    HealthConnectSnapshot(summary = healthSummary)
    if (healthSummary.isAvailable && !healthSummary.isConnected) {
        Button(onClick = onRequestHealthConnect, shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Rounded.HealthAndSafety, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Grant Health Connect once")
        }
    }
}

@Composable
private fun HealthConnectSnapshot(summary: HealthDailySummary) {
    SettingsControlGroup {
        Text("Today from Health Connect", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill("✓", "${summary.grantedPermissions}/${summary.requestedPermissions} permissions")
            summary.steps?.let { MetricPill("👟", "${it.compactCount()} steps") }
            summary.activeCaloriesKcal?.let { MetricPill("⚡", "$it active kcal") }
            summary.totalCaloriesBurnedKcal?.let { MetricPill("🔥", "$it total kcal") }
            summary.nutritionCaloriesKcal?.let { MetricPill("🍽️", "$it eaten kcal") }
            summary.nutritionProteinGrams?.let { MetricPill("💪", "${it}g protein") }
        }
        Text(
            healthConnectDetail(summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiAssistantSettings(
    preferences: FoodPreferences,
    aiConfig: LiteLlmConfig,
    aiFallbackConfig: LiteLlmConfig,
    aiStatus: String,
    settingsSaveStatus: String,
    onChange: (FoodPreferences) -> Unit,
    onAiConfigChange: (LiteLlmConfig) -> Unit,
    onAiFallbackConfigChange: (LiteLlmConfig) -> Unit,
    onSaveAiConfig: () -> Unit,
    onTestAiConnection: (LiteLlmConfig) -> Unit,
    onClearChatHistory: () -> Unit,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showSkillEditor by rememberSaveable { mutableStateOf(false) }
    var revealKey by rememberSaveable { mutableStateOf(false) }
    var editingFallback by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val bundledSkill = remember(context) {
        runCatching {
            context.assets.open("ai/wonderfood_food_skill.md").bufferedReader().use { it.readText() }
        }.getOrDefault("Bundled WonderFood skill could not be read.")
    }
    val selectedConfig = if (editingFallback) aiFallbackConfig else aiConfig
    val onSelectedConfigChange = if (editingFallback) onAiFallbackConfigChange else onAiConfigChange
    val selectedEndpointPath = selectedConfig.baseUrl.substringBefore('?').trimEnd('/').lowercase()
    val azureEndpointSummary = when {
        selectedConfig.provider != AiProvider.AZURE_OPENAI -> ""
        selectedEndpointPath.endsWith("/responses") -> "Azure Responses API · full URL used exactly"
        selectedEndpointPath.endsWith("/chat/completions") -> "Azure Chat Completions · full URL used exactly"
        selectedEndpointPath.endsWith("/openai/v1") -> "Azure v1 Chat Completions · /chat/completions is appended"
        else -> "Legacy Azure deployment · deployment path and API version are appended"
    }
    if (settingsSaveStatus.isNotBlank()) SettingsSaveStatus(status = settingsSaveStatus)
    SettingsStatusBlock(
        icon = Icons.AutoMirrored.Rounded.Chat,
        title = "Connection",
        body = aiStatus,
    )
    SettingsControlGroup {
        Text("Provider routes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        AiProviderRouteLine("1  Primary", aiConfig)
        AiProviderRouteLine("2  Fallback", aiFallbackConfig)
        Text(
            "If a route fails, Chat falls back safely and keeps the answer review-only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SettingsControlGroup {
        Text("Chat runtime", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "These are the exact pieces Chat uses: model route, your instructions, Food skill, source pack, schemas, and MCP handoff.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiCapabilityPill("🤖", if (selectedConfig.isUsable) "Model configured" else "Model missing")
            AiCapabilityPill("🧠", if (preferences.aiSkillOverride.isBlank()) "Bundled skill" else "Custom skill")
            AiCapabilityPill("🧬", "Schemas packaged")
            AiCapabilityPill("🕸️", "MCP bridge-ready")
        }
        Text(
            "Phone Chat and GPT/plugin clients should behave the same: cite sources, draft changes, and wait for review before saving.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AiStackMap(
            modelConfigured = selectedConfig.isUsable,
            customSkill = preferences.aiSkillOverride.isNotBlank(),
        )
    }
    PreferenceTextField(
        label = "Assistant instructions",
        value = preferences.customAiInstructions,
        placeholder = "Use my kitchen first. Show missing groceries. Ask before reducing quantities...",
        minLines = 5,
        onValue = { onChange(preferences.copy(customAiInstructions = it)) },
    )
    SettingsControlGroup {
        Text("Core skill", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            if (preferences.aiSkillOverride.isBlank()) {
                "Bundled Food skill is active. Your personal instructions stay separate."
            } else {
                "Custom core skill is active."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { showSkillEditor = !showSkillEditor }) {
            Text(if (showSkillEditor) "Hide skill text" else "View skill text")
        }
        if (showSkillEditor) {
            PreferenceTextField(
                label = "Core AI skill",
                value = preferences.aiSkillOverride.ifBlank { bundledSkill },
                placeholder = "WonderFood system behavior",
                minLines = 14,
                onValue = { onChange(preferences.copy(aiSkillOverride = it)) },
            )
            Text(
                "Editing creates a local override. Runtime JSON, review, and safety contracts remain appended by the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { onChange(preferences.copy(aiSkillOverride = "")) },
                enabled = preferences.aiSkillOverride.isNotBlank(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Reset to bundled skill")
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSaveAiConfig, shape = RoundedCornerShape(18.dp)) {
            Text("Save providers")
        }
        OutlinedButton(
            onClick = { onTestAiConnection(selectedConfig) },
            enabled = selectedConfig.isUsable,
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Test ${if (editingFallback) "fallback" else "primary"}")
        }
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide advanced" else "Advanced")
        }
    }
    ConfirmIconTextButton(
        Icons.Rounded.Delete,
        "Reset chat memory",
        "Clear AI chat history?",
        onClearChatHistory,
    )
    if (showAdvanced) {
        SettingsControlGroup {
            Text("Request order", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Every request tries Primary once. Fallback runs only when Primary fails. There is no rotation or load balancing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !editingFallback,
                    onClick = { editingFallback = false; revealKey = false },
                    label = { Text("1  Primary") },
                )
                FilterChip(
                    selected = editingFallback,
                    onClick = { editingFallback = true; revealKey = false },
                    label = { Text("2  Fallback") },
                )
            }
            Text(
                if (editingFallback) "Fallback provider (optional)" else "Primary provider",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = selectedConfig.provider == provider,
                        onClick = { onSelectedConfigChange(selectedConfig.copy(provider = provider)) },
                        label = { Text(provider.label) },
                    )
                }
            }
            PreferenceTextField(
                label = "Provider URL",
                value = selectedConfig.baseUrl,
                placeholder = if (selectedConfig.provider == AiProvider.AZURE_OPENAI) {
                    "https://your-resource.services.ai.azure.com/openai/v1/responses"
                } else {
                    "https://..."
                },
                onValue = { onSelectedConfigChange(selectedConfig.copy(baseUrl = it)) },
            )
            PreferenceTextField(
                label = if (selectedConfig.provider == AiProvider.AZURE_OPENAI) "Azure model / deployment" else "Model",
                value = selectedConfig.model,
                placeholder = if (selectedConfig.provider == AiProvider.AZURE_OPENAI) "gpt-chat-latest" else "gpt-5.4-mini",
                onValue = { onSelectedConfigChange(selectedConfig.copy(model = it)) },
            )
            if (selectedConfig.provider == AiProvider.AZURE_OPENAI) {
                Text(
                    azureEndpointSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                PreferenceTextField(
                    label = "Azure API version",
                    value = selectedConfig.apiVersion,
                    placeholder = "Leave blank for v1; legacy example: 2024-10-21",
                    onValue = { onSelectedConfigChange(selectedConfig.copy(apiVersion = it)) },
                )
                Text(
                    "Supported: full /openai/v1/responses, full /openai/v1/chat/completions, an /openai/v1 base, or a legacy resource base URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AiProviderEndpointPreview(selectedConfig)
            OutlinedTextField(
                value = selectedConfig.apiKey,
                onValueChange = { onSelectedConfigChange(selectedConfig.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (selectedConfig.provider == AiProvider.AZURE_OPENAI) "Azure API key" else "API key") },
                placeholder = { Text("Encrypted on this device") },
                visualTransformation = if (revealKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(18.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { revealKey = !revealKey }) {
                    Text(if (revealKey) "Hide key" else "Reveal key")
                }
                TextButton(onClick = { onSelectedConfigChange(selectedConfig.copy(apiKey = "")) }) {
                    Text("Remove key")
                }
            }
            if (editingFallback) {
                TextButton(
                    onClick = {
                        onAiFallbackConfigChange(LiteLlmConfig("", "", com.wonderfood.app.ai.LiteLlmSettings.DEFAULT_MODEL))
                        revealKey = false
                    },
                ) {
                    Text("Remove fallback")
                }
            }
        }
    }
}

@Composable
private fun AiProviderRouteLine(label: String, config: LiteLlmConfig) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (config.isUsable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(label.take(1), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "$label: ${if (config.isUsable) config.statusLabel else "Not configured"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (config.isUsable) config.settingsEndpointPreview() else "Set provider URL, model, and key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (config.isUsable) {
                Text(
                    config.settingsAuthLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AiStackMap(modelConfigured: Boolean, customSkill: Boolean) {
    val rows = listOf(
        AiStackMapRow(
            icon = "🤖",
            title = "Model",
            state = if (modelConfigured) "configured" else "missing key/route",
            detail = "Primary + optional fallback. Chat uses the same route order.",
        ),
        AiStackMapRow(
            icon = "🧠",
            title = "Domain skill",
            state = if (customSkill) "custom override" else "bundled Food skill",
            detail = "Food owns vocabulary, defaults, safety, and user-facing behavior.",
        ),
        AiStackMapRow(
            icon = "🧬",
            title = "Schema contracts",
            state = "packaged",
            detail = "Command envelope, proposal package, Room, Notion/Sheets graph, App Functions contracts.",
        ),
        AiStackMapRow(
            icon = "🕸️",
            title = "MCP + agents",
            state = "local bridge ready",
            detail = "Local bridge exposes catalog, validators, workflows, and review-only app links.",
        ),
        AiStackMapRow(
            icon = "🗂️",
            title = "Data bindings",
            state = "selectable",
            detail = "SQLite, Notion, Sheets, and Postgres share IDs and safe import review.",
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row -> AiStackMapCard(row) }
    }
}

private data class AiStackMapRow(
    val icon: String,
    val title: String,
    val state: String,
    val detail: String,
)

@Composable
private fun AiStackMapCard(row: AiStackMapRow) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(row.icon, style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(row.state, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    row.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AiProviderEndpointPreview(config: LiteLlmConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Request preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Endpoint: ${config.settingsEndpointPreview()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Auth: ${config.settingsAuthLabel()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            config.settings403Hint(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun LiteLlmConfig.settingsEndpointPreview(): String {
    val trimmed = baseUrl.trim()
    if (trimmed.isBlank()) return "Set provider URL"
    return when (provider) {
        AiProvider.OPENAI_COMPATIBLE -> if (usesExplicitOpenAiEndpointForSettings()) {
            trimmed
        } else {
            trimmed.appendSettingsUrlPath("chat/completions")
        }
        AiProvider.AZURE_OPENAI -> {
            val endpoint = when {
                usesExplicitOpenAiEndpointForSettings() -> trimmed
                usesAzureV1ForSettings() -> trimmed.appendSettingsUrlPath("chat/completions")
                else -> {
                    val deployment = URLEncoder.encode(model.trim(), StandardCharsets.UTF_8.name())
                    trimmed.appendSettingsUrlPath("openai/deployments/$deployment/chat/completions")
                }
            }
            endpoint.withAzureSettingsApiVersion(apiVersion)
        }
        AiProvider.ANTHROPIC -> trimmed.trimEnd('/') + "/v1/messages"
    }
}

private fun LiteLlmConfig.settingsAuthLabel(): String =
    when (provider) {
        AiProvider.OPENAI_COMPATIBLE -> "Authorization: Bearer <key>"
        AiProvider.AZURE_OPENAI -> "api-key header"
        AiProvider.ANTHROPIC -> "x-api-key header"
    }

private fun LiteLlmConfig.settings403Hint(): String =
    when (provider) {
        AiProvider.OPENAI_COMPATIBLE ->
            "OpenAI-compatible 403: check key project, model access, provider URL, and whether this route supports chat/completions."
        AiProvider.AZURE_OPENAI ->
            "Azure 403: check resource key, deployment/model name, endpoint type, and Azure network/access policy."
        AiProvider.ANTHROPIC ->
            "Anthropic 403: check workspace/key access and model permission."
    }

private fun LiteLlmConfig.usesExplicitOpenAiEndpointForSettings(): Boolean {
    val path = baseUrl.substringBefore('?').trimEnd('/').lowercase()
    return path.endsWith("/chat/completions") || path.endsWith("/responses")
}

private fun LiteLlmConfig.usesAzureV1ForSettings(): Boolean =
    baseUrl.substringBefore('?').trimEnd('/').lowercase().let { path ->
        path.endsWith("/openai/v1") || "/openai/v1/" in path
    }

private fun String.appendSettingsUrlPath(path: String): String {
    val base = substringBefore('?').trimEnd('/')
    val query = substringAfter('?', "")
    return "$base/$path" + query.takeIf(String::isNotBlank)?.let { "?$it" }.orEmpty()
}

private fun String.withAzureSettingsApiVersion(apiVersion: String): String {
    if (apiVersion.isBlank() || contains("api-version=", ignoreCase = true)) return this
    val separator = if (contains("?")) "&" else "?"
    val encodedVersion = URLEncoder.encode(apiVersion.trim(), StandardCharsets.UTF_8.name())
    return "$this${separator}api-version=$encodedVersion"
}

@Composable
private fun AppearanceSettings(
    themeMode: WonderFoodThemeMode,
    onThemeModeChange: (WonderFoodThemeMode) -> Unit,
) {
    SettingsControlGroup {
        Text("Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        ThemeModeSelector(selected = themeMode, onSelected = onThemeModeChange)
    }
}

@Composable
private fun BackupRestoreSettings(
    googleAccountEmail: String,
    googleOAuthClientId: String,
    googleSyncStatus: String,
    onGoogleOAuthClientIdChange: (String) -> Unit,
    onConnectGoogle: () -> Unit,
    onBackupToGoogleDrive: () -> Unit,
    onRestoreFromGoogleDrive: () -> Unit,
    onDisconnectGoogle: () -> Unit,
) {
    var showDeveloperSetup by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    SettingsStatusBlock(
        icon = Icons.Rounded.Inventory2,
        title = if (googleAccountEmail.isBlank()) "Local only" else googleAccountEmail,
        body = if (googleAccountEmail.isBlank()) "Sign in to create an app-private Google Drive backup." else googleSyncStatus,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onConnectGoogle, shape = RoundedCornerShape(18.dp)) {
            Text(if (googleAccountEmail.isBlank()) "Continue with Google" else "Reconnect Google")
        }
        OutlinedButton(onClick = onBackupToGoogleDrive, shape = RoundedCornerShape(18.dp)) {
            Text("Back up now")
        }
        OutlinedButton(onClick = onRestoreFromGoogleDrive, shape = RoundedCornerShape(18.dp)) {
            Text("Restore")
        }
        if (googleAccountEmail.isNotBlank()) {
            TextButton(onClick = onDisconnectGoogle) {
                Text("Disconnect")
            }
        }
    }
    DetailSection(
        "What backs up",
        "WonderFood stores one app-private SQLite snapshot in Google Drive. Provider API keys stay on this phone.",
    )
    if (isDebuggable) {
        TextButton(onClick = { showDeveloperSetup = !showDeveloperSetup }) {
            Text(if (showDeveloperSetup) "Hide developer setup" else "Developer setup")
        }
    }
    if (isDebuggable && showDeveloperSetup) {
        SettingsControlGroup {
            PreferenceTextField(
                label = "Web OAuth client ID",
                value = googleOAuthClientId,
                placeholder = "1234567890-abc.apps.googleusercontent.com",
                onValue = onGoogleOAuthClientIdChange,
            )
            Text(
                "Temporary phone setup until the OAuth client is embedded in the app configuration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataPrivacySettings(
    memory: HouseholdUiMemory,
    syncStatus: String,
    backupPassphrase: String,
    onBackupPassphraseChange: (String) -> Unit,
    onCreateEncryptedBackup: (String) -> Unit,
    onRestoreLatestEncryptedBackup: (String) -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onDeleteAllPlans: () -> Unit,
    onClearChatHistory: () -> Unit,
    onDeleteAllAppData: () -> Unit,
) {
    VisualFactGrid(
        facts = listOf(
            "🥬" to "${memory.inventory.size} kitchen",
            "🛒" to "${memory.groceries.count { it.status == GroceryStatus.NEEDED }} to buy",
            "📖" to "${memory.recipes.size} recipes",
            "🍽️" to "${memory.mealLogs.size} meals",
            "🧾" to "${memory.inventoryTransactions.size} ledger rows",
            "✅" to "${memory.actions.size} AI actions",
        ),
    )
    SettingsStatusBlock(icon = Icons.Rounded.Inventory2, title = "Local backup status", body = syncStatus)
    SettingsControlGroup {
        OutlinedTextField(
            value = backupPassphrase,
            onValueChange = onBackupPassphraseChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Backup passphrase") },
            placeholder = { Text("8+ chars; needed on your other phone") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(18.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onCreateEncryptedBackup(backupPassphrase) },
                enabled = backupPassphrase.length >= 8,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Encrypted backup")
            }
            OutlinedButton(
                onClick = { onRestoreLatestEncryptedBackup(backupPassphrase) },
                enabled = backupPassphrase.length >= 8,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Restore latest")
            }
        }
    }
    SettingsControlGroup {
        Text("Spreadsheet tools", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportCsv, shape = RoundedCornerShape(18.dp)) {
                Text("Export CSV")
            }
            OutlinedButton(onClick = onImportCsv, shape = RoundedCornerShape(18.dp)) {
                Text("Import CSV/JSON")
            }
        }
    }
    SettingsControlGroup {
        Text("Delete data", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        ConfirmIconTextButton(
            Icons.Rounded.Delete,
            "Clear AI chat history",
            "Clear AI chat history?",
            onClearChatHistory,
        )
        ConfirmIconTextButton(
            Icons.Rounded.Delete,
            "Delete all plans",
            "Delete all meal plans and planned entries?",
            onDeleteAllPlans,
        )
        ConfirmIconTextButton(
            Icons.Rounded.Delete,
            "Delete all app data",
            "Delete all WonderFood data on this device?",
            onDeleteAllAppData,
            body = "This removes the local database, chat history, preferences, backups, Google setup, and AI provider settings on this phone.",
        )
    }
}

@Composable
private fun HelpAboutSettings() {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName ?: "dev"} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("dev")
    }
    VisualFactGrid(
        facts = listOf(
            "💧" to "Log water",
            "🛒" to "Start shopping",
            "🍳" to "Start cooking",
            "🍽️" to "Log meal",
            "🔢" to "Open numbers",
            "🎙️" to "Voice note to AI",
        ),
    )
    DetailSection(
        "Okay Google",
        "Assistant deep links route to direct app actions. Low-risk actions like water and shopping events log immediately.",
    )
    DetailSection(
        "In-app mic",
        "Use the mic beside the composer when a voice note should go through AI extraction.",
    )
    DetailSection(
        "Credential safety",
        "Provider keys are encrypted with Android Keystore and excluded from cloud backup and device transfer.",
    )
    DetailSection(
        "Diagnostics",
        "App version: $version\nDatabase schema: v${WonderFoodDatabase.SCHEMA_VERSION}\nPackage: ${context.packageName}",
    )
    DetailSection(
        "Send feedback",
        "Capture a screenshot and share what felt confusing. Diagnostics above help match the exact build.",
    )
}

@Composable
private fun SettingsUnitTextField(
    label: String,
    value: String,
    placeholder: String,
    unit: String,
    onValue: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.weight(1f),
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(18.dp),
        )
        Text(unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class SettingsDestination {
    HOME,
    LIFEOS_CONTROL,
    FOOD_PROFILE,
    GOALS_HEALTH,
    AI_ASSISTANT,
    APPEARANCE,
    BACKUP_RESTORE,
    DATA_HOME,
    IMPORT_EXPORT_PRIVACY,
    HELP_ABOUT,
}

private fun BackendHomeUiState.settingsSummary(): String =
    listOf(
        label,
        detail.takeIf { it.isNotBlank() && it != label },
        message.takeIf { it.isNotBlank() },
        safetyMessage.takeIf { it.isNotBlank() },
    )
        .filterNotNull()
        .joinToString(" · ")

private fun foodProfileSummary(preferences: FoodPreferences): String =
    listOf(
        preferences.dietStyle.takeIf { it.isNotBlank() },
        preferences.preferredCuisines.takeIf { it.isNotBlank() },
        preferences.preferredStaples.takeIf { it.isNotBlank() },
        preferences.allergies.takeIf { it.isNotBlank() }?.let { "allergies set" },
    ).filterNotNull().joinToString(" · ").ifBlank { "Diet, cuisines, staples, allergies" }

private fun goalsSummary(preferences: FoodPreferences, healthStatus: String, healthSummary: HealthDailySummary): String =
    listOf(
        preferences.calorieGoal.takeIf { it.isNotBlank() }?.let { "$it kcal" },
        preferences.proteinGoal.takeIf { it.isNotBlank() }?.let { "$it protein" },
        healthSummary.steps?.let { "${it.compactCount()} steps" },
        healthSummary.activeCaloriesKcal?.let { "$it active kcal" },
        healthStatus.takeIf { it.isNotBlank() },
    ).filterNotNull().joinToString(" · ").ifBlank { "Calories, protein, Health Connect" }

private fun healthConnectDetail(summary: HealthDailySummary): String =
    when {
        !summary.isAvailable -> summary.label
        !summary.isConnected -> "Grant once. Android remembers this unless you revoke permissions, reinstall, or disable unused app permissions."
        summary.steps == null && summary.activeCaloriesKcal == null && summary.nutritionCaloriesKcal == null ->
            "Automatic sync is on. No Health Connect activity or nutrition data found for today yet."
        else -> "Automatic sync is on. WonderFood uses activity and nutrition totals while planning."
    }

private fun Long.compactCount(): String =
    when {
        this >= 10000 -> "${this / 1000}k"
        this >= 1000 -> String.format(Locale.US, "%.1fk", this / 1000.0)
        else -> toString()
    }

private fun backupSummary(googleAccountEmail: String, googleSyncStatus: String): String =
    if (googleAccountEmail.isBlank()) {
        "Google Drive backup not connected"
    } else {
        googleSyncStatus.oneLine()
    }

private fun String.oneLine(): String =
    lineSequence().joinToString(" ") { it.trim() }.replace(Regex("\\s+"), " ").trim()

private fun Long.restoreDateLabel(): String =
    if (this <= 0L) "Unknown" else "${shortDate()} ${shortTime()}"

@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selected: WonderFoodThemeMode,
    onSelected: (WonderFoodThemeMode) -> Unit,
) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WonderFoodThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onSelected(mode) },
                        label = { Text(mode.displayLabel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceChipEditor(
    label: String,
    value: String,
    suggestions: List<String>,
    placeholder: String,
    onValue: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf("") }
    val selected = value.preferenceTokens()
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { option ->
                    val isSelected = selected.any { it.equals(option, ignoreCase = true) }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onValue(
                                if (isSelected) value.withoutPreferenceToken(option)
                                else value.withPreferenceToken(option),
                            )
                        },
                        label = { Text(option) },
                    )
                }
                selected.filterNot { token -> suggestions.any { it.equals(token, ignoreCase = true) } }.forEach { custom ->
                    AssistChip(
                        onClick = { onValue(value.withoutPreferenceToken(custom)) },
                        label = { Text("$custom ×") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (draft.isNotBlank()) {
                                onValue(value.withPreferenceToken(draft))
                                draft = ""
                            }
                        },
                    ),
                    shape = RoundedCornerShape(18.dp),
                )
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onValue(value.withPreferenceToken(draft))
                            draft = ""
                        }
                    },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add $label")
                }
            }
        }
    }
}

@Composable
private fun PreferenceTextField(
    label: String,
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        minLines = minLines,
        maxLines = if (minLines > 1) maxOf(6, minLines) else 2,
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun PreferenceInfoField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CalendarPane(
    memory: HouseholdUiMemory,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onOpenDay: (CalendarSlot) -> Unit = {},
) {
    val days = calendarSlots(memory)
    Surface(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.safeDrawingPadding()),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (compact) 0.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 0.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Meal calendar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.forEach { day ->
                        CalendarDayCard(day = day, onOpen = { onOpenDay(day) })
                    }
                    if (memory.mealLogs.isNotEmpty()) {
                        SectionLabel("Recently logged")
                        memory.mealLogs.take(3).forEach { meal ->
                            MealCard(meal = meal, onOpen = {}, onDelete = {})
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(days) { day ->
                        CalendarDayCard(day = day, onOpen = { onOpenDay(day) })
                    }
                    if (memory.mealLogs.isNotEmpty()) {
                        item { SectionLabel("Recently logged") }
                        items(memory.mealLogs.take(3), key = { it.id }) { meal ->
                            MealCard(meal = meal, onOpen = {}, onDelete = {})
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCard(day: CalendarSlot, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "Open calendar day ${day.title} ${day.weekday} ${day.dayNumber}"
            }
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (day.isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(day.weekday, style = MaterialTheme.typography.labelSmall)
                    Text(day.dayNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(day.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                CalendarSignals(day)
            }
        }
    }
}

@Composable
private fun CalendarSignals(day: CalendarSlot) {
    val signals = buildList {
        val shoppingCount = day.shopping.size + day.inventoryChanges.count { it.action == InventoryAction.BOUGHT }
        if (day.plannedMeal != null) add("🗓 plan")
        if (day.meals.isNotEmpty()) add("🍽 ${day.meals.size}")
        if (shoppingCount > 0) add("🛒 $shoppingCount")
        if (day.recipes.isNotEmpty()) add("📖 ${day.recipes.size}")
        if (day.inventoryChanges.isNotEmpty()) add("🥬 ${day.inventoryChanges.size}")
        if (day.meals.isNotEmpty()) add("❤️ sync")
        if (day.didEatPlanned) add("✅ matched")
    }
    if (signals.isEmpty()) {
        Text("✨ ask AI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            signals.take(5).forEach { signal ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        signal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailPage(
    target: FoodDetailTarget,
    memory: HouseholdUiMemory,
    onBack: () -> Unit,
    onDeleteInventory: (Long) -> Unit,
    onUpdateInventory: InventoryUpdateHandler,
    onDeleteGrocery: (Long) -> Unit,
    onUpdateGrocery: GroceryUpdateHandler,
    onMarkGroceryBought: (Long) -> Unit,
    onDeleteRecipe: (Long) -> Unit,
    onCookRecipe: (Long) -> Unit,
    onAddRecipeMissingToList: (Long) -> Unit,
    onUpdateRecipe: RecipeUpdateHandler,
    onPickRecipeImage: (Long) -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onUpdateMeal: MealUpdateHandler,
    onUpdateMealPlan: MealPlanUpdateHandler,
    onAddMealPlanEntry: MealPlanEntryCreateHandler,
    onUpdateMealPlanEntry: MealPlanEntryUpdateHandler,
    onDeleteMealPlanEntry: (Long) -> Unit,
    onUpdateReceipt: ReceiptUpdateHandler,
    onExportMeal: (Long) -> Unit,
    onOpenDetail: (FoodDetailTarget) -> Unit,
    onLogMeal: (Long) -> Unit,
    onLogWater: (Int) -> Unit,
) {
    val slot = target.epochDay?.let { day ->
        calendarSlots(memory).firstOrNull { it.date.toEpochDay() == day }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item {
            when (target.kind) {
                FoodDetailKind.INVENTORY -> {
                    val item = memory.inventory.firstOrNull { it.id == target.id }
                    if (item == null) {
                        MissingDetail(onBack)
                    } else {
                        InventoryDetail(
                            item = item,
                            transactions = memory.inventoryTransactions.filter { it.inventoryItemId == item.id || it.itemName.equals(item.name, ignoreCase = true) },
                            onBack = onBack,
                            onUpdate = onUpdateInventory,
                            onDelete = onDeleteInventory,
                        )
                    }
                }
                FoodDetailKind.GROCERY -> {
                    val item = memory.groceries.firstOrNull { it.id == target.id }
                    if (item == null) MissingDetail(onBack) else GroceryDetail(item, onBack, onUpdateGrocery, onMarkGroceryBought, onDeleteGrocery)
                }
                FoodDetailKind.RECIPE -> {
                    val recipe = memory.recipes.firstOrNull { it.id == target.id }
                    if (recipe == null) {
                        MissingDetail(onBack)
                    } else {
                        RecipeDetail(
                            recipe = recipe,
                            memory = memory,
                            onBack = onBack,
                            onDelete = onDeleteRecipe,
                            onCook = onCookRecipe,
                            onAddMissing = onAddRecipeMissingToList,
                            onUpdate = onUpdateRecipe,
                            onPickImage = onPickRecipeImage,
                        )
                    }
                }
                FoodDetailKind.MEAL -> {
                    val meal = memory.mealLogs.firstOrNull { it.id == target.id }
                    if (meal == null) {
                        MissingDetail(onBack)
                    } else {
                        MealDetail(
                            meal = meal,
                            transactions = memory.inventoryTransactions.filter { it.relatedMealLogId == meal.id },
                            onBack = onBack,
                            onExport = { onExportMeal(meal.id) },
                            onUpdate = onUpdateMeal,
                            onDelete = onDeleteMeal,
                        )
                    }
                }
                FoodDetailKind.PLAN -> {
                    val plan = memory.mealPlans.firstOrNull { it.id == target.id }
                    if (plan == null) {
                        MissingDetail(onBack)
                    } else {
                        PlanDetail(plan, memory.mealPlanEntries.filter { it.planId == plan.id }, onBack, onUpdateMealPlan)
                    }
                }
                FoodDetailKind.DAY -> {
                    if (slot == null) {
                        MissingDetail(onBack)
                    } else {
                        DayDetail(
                            day = slot,
                            onBack = onBack,
                            onAddPlanEntry = onAddMealPlanEntry,
                            onUpdatePlanEntry = onUpdateMealPlanEntry,
                            onDeletePlanEntry = onDeleteMealPlanEntry,
                            onOpenDetail = onOpenDetail,
                            onLogMeal = onLogMeal,
                            onLogWater = onLogWater,
                        )
                    }
                }
                FoodDetailKind.RECEIPT -> {
                    val receipt = memory.receipts.firstOrNull { it.id == target.id }
                    if (receipt == null) MissingDetail(onBack) else ReceiptDetail(receipt, onBack, onUpdateReceipt)
                }
            }
        }
    }
}

@Composable
private fun MissingDetail(onBack: () -> Unit) {
    DetailShell(image = "∅", title = "Not found", subtitle = "This item is no longer in local memory.", onBack = onBack) {
        EmptyState("Nothing here.", "The local row may have been removed.")
    }
}

@Composable
private fun InventoryDetail(
    item: InventoryItem,
    transactions: List<InventoryTransaction>,
    onBack: () -> Unit,
    onUpdate: InventoryUpdateHandler,
    onDelete: (Long) -> Unit,
) {
    var editing by remember(item.id) { mutableStateOf(false) }
    var name by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.name) }
    var quantity by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.quantity) }
    var zone by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.zone) }
    var category by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.category) }
    var servingText by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.servingText) }
    var calories by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.calories?.toString().orEmpty()) }
    var protein by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.proteinGrams?.cleanNumber().orEmpty()) }
    var carbs by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.carbsGrams?.cleanNumber().orEmpty()) }
    var fat by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.fatGrams?.cleanNumber().orEmpty()) }
    var nutritionSource by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.nutritionSource) }
    var notes by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.notes) }
    var imageUrl by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.imageUrl) }
    DetailShell(
        image = item.imageUri ?: foodEmoji(item.name),
        title = item.name,
        subtitle = "Stored in ${item.zone.label}",
        onBack = onBack,
    ) {
        VisualFactGrid(
            facts = buildList {
                add("📍" to item.zone.label)
                add("🏷️" to item.category)
                add("⚖️" to item.quantity.ifBlank { "unknown" })
                add("🔥" to nutritionSummary(item.calories, item.proteinGrams))
                add("🧠" to item.source)
                add("📅" to item.createdAtMillis.shortDate())
                add("⏳" to (item.expiresAtMillis?.shortDate() ?: "not set"))
                if (item.storeName.isNotBlank()) add("🏪" to item.storeName)
                item.purchasePriceCents?.let { add("💵" to it.receiptMoney(item.currencyCode)) }
                item.purchaseDateEpochDay?.let { add("🧾" to it.epochDayShortDate()) }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { editing = !editing }, shape = RoundedCornerShape(18.dp)) {
                Text(if (editing) "Done editing" else "Edit page")
            }
            AssistChip(onClick = {}, enabled = false, label = { Text("updated ${item.updatedAtMillis.shortDate()}") })
        }
        if (editing) {
            SettingsGroupCard(
                icon = Icons.Rounded.Kitchen,
                title = "Edit kitchen page",
                subtitle = "Name, quantity, zone, nutrition, notes",
                action = {
                    Button(
                        onClick = {
                            onUpdate(
                                item.id,
                                name,
                                quantity,
                                zone,
                                category,
                                servingText,
                                calories.toIntOrNull(),
                                protein.toDoubleOrNull(),
                                carbs.toDoubleOrNull(),
                                fat.toDoubleOrNull(),
                                nutritionSource,
                                notes,
                                item.imageUri,
                                imageUrl,
                                item.expiresAtMillis,
                            )
                            editing = false
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Save") }
                },
            ) {
                PreferenceTextField("Name", name, "Food name", onValue = { name = it })
                PreferenceTextField("Quantity", quantity, "2 cups, low, 1 pack", onValue = { quantity = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageZone.entries.forEach { option ->
                        FilterChip(selected = zone == option, onClick = { zone = option }, label = { Text(option.label) })
                    }
                }
                PreferenceTextField("Category", category, "protein, grain, vegetable", onValue = { category = it })
                PreferenceTextField("Serving", servingText, "100 g, 1 cup, 1 item", onValue = { servingText = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceTextField("Calories", calories, "120", modifier = Modifier.weight(1f), onValue = { calories = it })
                    PreferenceTextField("Protein", protein, "15", modifier = Modifier.weight(1f), onValue = { protein = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceTextField("Carbs", carbs, "20", modifier = Modifier.weight(1f), onValue = { carbs = it })
                    PreferenceTextField("Fat", fat, "5", modifier = Modifier.weight(1f), onValue = { fat = it })
                }
                PreferenceInfoField("Nutrition source", nutritionSourceLabel(nutritionSource))
                PreferenceTextField("Image URL", imageUrl, "https://...", onValue = { imageUrl = it })
                PreferenceTextField("Notes", notes, "Anything useful", minLines = 3, onValue = { notes = it })
                Button(
                    onClick = {
                        onUpdate(
                            item.id,
                            name,
                            quantity,
                            zone,
                            category,
                            servingText,
                            calories.toIntOrNull(),
                            protein.toDoubleOrNull(),
                            carbs.toDoubleOrNull(),
                            fat.toDoubleOrNull(),
                            nutritionSource,
                            notes,
                            item.imageUri,
                            imageUrl,
                            item.expiresAtMillis,
                        )
                        editing = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Save changes") }
            }
        }
        DetailSection("📝 Notes", item.notes.ifBlank { "No notes yet." })
        if (transactions.isNotEmpty()) {
            DetailSection(
                "🧾 Ledger",
                transactions.take(8).joinToString("\n") { tx ->
                    "${tx.action.label}: ${tx.quantityText.ifBlank { item.quantity }} • ${tx.reason.ifBlank { tx.source }} • ${tx.createdAtMillis.shortDate()}"
                },
            )
        }
        ConfirmIconTextButton(Icons.Rounded.Delete, "Remove", "Remove ${item.name}?", { onDelete(item.id) })
    }
}

@Composable
private fun GroceryDetail(
    item: GroceryItem,
    onBack: () -> Unit,
    onUpdate: GroceryUpdateHandler,
    onBought: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editing by remember(item.id) { mutableStateOf(false) }
    var name by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.name) }
    var quantity by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.quantity) }
    var status by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.status) }
    var category by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.category) }
    var servingText by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.servingText) }
    var calories by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.calories?.toString().orEmpty()) }
    var protein by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.proteinGrams?.cleanNumber().orEmpty()) }
    var carbs by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.carbsGrams?.cleanNumber().orEmpty()) }
    var fat by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.fatGrams?.cleanNumber().orEmpty()) }
    var nutritionSource by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.nutritionSource) }
    var source by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.source) }
    var imageUrl by remember(item.id, item.updatedAtMillis) { mutableStateOf(item.imageUrl) }
    DetailShell(
        image = item.imageUri ?: foodEmoji(item.name),
        title = item.name,
        subtitle = if (item.status == GroceryStatus.NEEDED) "On your shopping list" else "Bought",
        onBack = onBack,
    ) {
        VisualFactGrid(
            facts = listOf(
                "🛒" to item.status.name.lowercase(),
                "🏷️" to item.category,
                "⚖️" to item.quantity.ifBlank { "as needed" },
                "🔥" to nutritionSummary(item.calories, item.proteinGrams),
                "🧠" to item.source,
                "📅" to item.createdAtMillis.shortDate(),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { editing = !editing }, shape = RoundedCornerShape(18.dp)) {
                Text(if (editing) "Done editing" else "Edit page")
            }
            AssistChip(onClick = {}, enabled = false, label = { Text("updated ${item.updatedAtMillis.shortDate()}") })
        }
        if (editing) {
            SettingsGroupCard(
                icon = Icons.Rounded.ShoppingCart,
                title = "Edit grocery page",
                subtitle = "Name, quantity, status, nutrition",
                action = {
                    Button(
                        onClick = {
                            onUpdate(
                                item.id,
                                name,
                                quantity,
                                status,
                                category,
                                servingText,
                                calories.toIntOrNull(),
                                protein.toDoubleOrNull(),
                                carbs.toDoubleOrNull(),
                                fat.toDoubleOrNull(),
                                nutritionSource,
                                source,
                                item.imageUri,
                                imageUrl,
                            )
                            editing = false
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Save") }
                },
            ) {
                PreferenceTextField("Name", name, "Food name", onValue = { name = it })
                PreferenceTextField("Quantity", quantity, "2 packs, 1 lb, as needed", onValue = { quantity = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroceryStatus.entries.forEach { option ->
                        FilterChip(selected = status == option, onClick = { status = option }, label = { Text(option.name.lowercase()) })
                    }
                }
                PreferenceTextField("Category", category, "protein, grain, vegetable", onValue = { category = it })
                PreferenceTextField("Serving", servingText, "100 g, 1 cup, 1 item", onValue = { servingText = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceTextField("Calories", calories, "120", modifier = Modifier.weight(1f), onValue = { calories = it })
                    PreferenceTextField("Protein", protein, "15", modifier = Modifier.weight(1f), onValue = { protein = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceTextField("Carbs", carbs, "20", modifier = Modifier.weight(1f), onValue = { carbs = it })
                    PreferenceTextField("Fat", fat, "5", modifier = Modifier.weight(1f), onValue = { fat = it })
                }
                PreferenceInfoField("Nutrition source", nutritionSourceLabel(nutritionSource))
                PreferenceTextField("Source", source, "chat, receipt, manual", onValue = { source = it })
                PreferenceTextField("Image URL", imageUrl, "https://...", onValue = { imageUrl = it })
                Button(
                    onClick = {
                        onUpdate(
                            item.id,
                            name,
                            quantity,
                            status,
                            category,
                            servingText,
                            calories.toIntOrNull(),
                            protein.toDoubleOrNull(),
                            carbs.toDoubleOrNull(),
                            fat.toDoubleOrNull(),
                            nutritionSource,
                            source,
                            item.imageUri,
                            imageUrl,
                        )
                        editing = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Save changes") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.status == GroceryStatus.NEEDED) {
                Button(onClick = { onBought(item.id) }, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bought")
                }
            }
            ConfirmIconTextButton(Icons.Rounded.Delete, "Remove", "Remove ${item.name}?", { onDelete(item.id) })
        }
    }
}

@Composable
private fun RecipeDetail(
    recipe: Recipe,
    memory: HouseholdUiMemory,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit,
    onCook: (Long) -> Unit,
    onAddMissing: (Long) -> Unit,
    onUpdate: RecipeUpdateHandler,
    onPickImage: (Long) -> Unit,
) {
    val match = recipe.kitchenMatch(memory)
    var editing by remember(recipe.id) { mutableStateOf(false) }
    var title by remember(recipe.id, recipe.updatedAtMillis) { mutableStateOf(recipe.title) }
    var ingredients by remember(recipe.id, recipe.updatedAtMillis) { mutableStateOf(recipe.ingredients) }
    var steps by remember(recipe.id, recipe.updatedAtMillis) { mutableStateOf(recipe.steps) }
    var servings by remember(recipe.id, recipe.updatedAtMillis) { mutableStateOf(recipe.servings?.toString().orEmpty()) }
    var prep by remember(recipe.id, recipe.updatedAtMillis) { mutableStateOf(recipe.prepMinutes?.toString().orEmpty()) }
    var tags by remember(recipe.id, recipe.updatedAtMillis) { mutableStateOf(recipe.tags) }
    var imageUrl by remember(recipe.id, recipe.updatedAtMillis) { mutableStateOf(recipe.imageUrl) }
    DetailShell(image = recipe.imageUri ?: foodEmoji(recipe.title), title = recipe.title, subtitle = "Personal recipe", onBack = onBack) {
        VisualFactGrid(
            facts = listOf(
                "🍽️" to (recipe.servings?.toString() ?: "servings?"),
                "⏱️" to (recipe.prepMinutes?.let { "${it}m" } ?: "time?"),
                "🏷️" to recipe.tags.ifBlank { "untagged" },
                "⭐" to (recipe.rating?.toString() ?: "not rated"),
                "📅" to (memory.lastHad(recipe)?.epochDayShortDate() ?: "not cooked"),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { editing = !editing }, shape = RoundedCornerShape(18.dp)) {
                Text(if (editing) "Done editing" else "Edit recipe")
            }
            OutlinedButton(onClick = { onPickImage(recipe.id) }, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Image")
            }
        }
        if (editing) {
            SettingsGroupCard(
                icon = Icons.Rounded.Restaurant,
                title = "Edit recipe",
                subtitle = "Personal, local, fully changeable",
                action = {
                    Button(
                        onClick = {
                            onUpdate(
                                recipe.id,
                                title.ifBlank { recipe.title },
                                ingredients,
                                steps,
                                servings.toIntOrNull(),
                                prep.toIntOrNull(),
                                tags,
                                recipe.imageUri,
                                imageUrl,
                            )
                            editing = false
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Save") }
                },
            ) {
                PreferenceTextField("Title", title, "Recipe name", onValue = { title = it })
                PreferenceTextField("Ingredients", ingredients, "Ingredients", minLines = 4, onValue = { ingredients = it })
                PreferenceTextField("Steps", steps, "Steps", minLines = 5, onValue = { steps = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceTextField("Servings", servings, "2", modifier = Modifier.weight(1f), onValue = { servings = it })
                    PreferenceTextField("Minutes", prep, "25", modifier = Modifier.weight(1f), onValue = { prep = it })
                }
                PreferenceTextField("Tags", tags, "quick, high protein", onValue = { tags = it })
                PreferenceTextField("Image URL", imageUrl, "https://...", onValue = { imageUrl = it })
                Button(
                    onClick = {
                        onUpdate(
                            recipe.id,
                            title.ifBlank { recipe.title },
                            ingredients,
                            steps,
                            servings.toIntOrNull(),
                            prep.toIntOrNull(),
                            tags,
                            recipe.imageUri,
                            imageUrl,
                        )
                        editing = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Save changes") }
            }
        }
        DetailSection(
            "🧺 Kitchen match",
            buildString {
                appendLine("Have: ${match.have.joinToString(", ").ifBlank { "nothing matched yet" }}")
                append("Need: ${match.need.joinToString(", ").ifBlank { "looks covered" }}")
            },
        )
        DetailSection("🥬 Ingredients", recipe.ingredients.ifBlank { "No ingredients saved yet." })
        DetailSection("🍳 Steps", recipe.steps.ifBlank { "No steps saved yet." })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ConfirmIconTextButton(
                Icons.Rounded.Restaurant,
                "Finish cooking",
                "Finish ${recipe.title} and create a meal log?",
                { onCook(recipe.id) },
            )
            if (match.need.isNotEmpty()) {
                ConfirmIconTextButton(
                    Icons.Rounded.ShoppingCart,
                    "Add missing",
                    "Add ${match.need.size} missing ingredient${match.need.size.pluralWord} to shopping?",
                    { onAddMissing(recipe.id) },
                )
            }
            ConfirmIconTextButton(Icons.Rounded.Delete, "Remove", "Remove ${recipe.title}?", { onDelete(recipe.id) })
        }
    }
}

@Composable
private fun MealDetail(
    meal: MealLog,
    transactions: List<InventoryTransaction>,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onUpdate: MealUpdateHandler,
    onDelete: (Long) -> Unit,
) {
    var editing by remember(meal.id) { mutableStateOf(false) }
    var title by remember(meal.id, meal.updatedAtMillis) { mutableStateOf(meal.title) }
    var calories by remember(meal.id, meal.updatedAtMillis) { mutableStateOf(meal.calories?.toString().orEmpty()) }
    var protein by remember(meal.id, meal.updatedAtMillis) { mutableStateOf(meal.proteinGrams?.cleanNumber().orEmpty()) }
    var carbs by remember(meal.id, meal.updatedAtMillis) { mutableStateOf(meal.carbsGrams?.cleanNumber().orEmpty()) }
    var fat by remember(meal.id, meal.updatedAtMillis) { mutableStateOf(meal.fatGrams?.cleanNumber().orEmpty()) }
    var slot by remember(meal.id, meal.updatedAtMillis) { mutableStateOf(meal.mealSlot) }
    var usedItems by remember(meal.id, meal.updatedAtMillis) { mutableStateOf(meal.usedItemsText) }
    var source by remember(meal.id, meal.updatedAtMillis) { mutableStateOf(meal.source) }
    DetailShell(image = foodEmoji(meal.title), title = meal.title, subtitle = "Meal log", onBack = onBack) {
        VisualFactGrid(
            facts = listOf(
                "🍽️" to meal.mealSlot.label,
                "🔥" to (meal.calories?.let { "$it kcal" } ?: "Calories unknown"),
                "💪" to (meal.proteinGrams?.let { "${it.toInt()}g protein" } ?: "Protein unknown"),
                "🍚" to (meal.carbsGrams?.let { "${it.toInt()}g carbs" } ?: "Carbs unknown"),
                "🫒" to (meal.fatGrams?.let { "${it.toInt()}g fat" } ?: "Fat unknown"),
                "🧠" to meal.source,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { editing = !editing }, shape = RoundedCornerShape(18.dp)) {
                Text(if (editing) "Done editing" else "Edit page")
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("last edited ${meal.updatedAtMillis.shortDate()}") },
            )
        }
        if (editing) {
            SettingsGroupCard(
                icon = Icons.Rounded.Restaurant,
                title = "Edit meal page",
                subtitle = "Title, slot, nutrition, kitchen usage",
                action = {
                    Button(
                        onClick = {
                            onUpdate(
                                meal.id,
                                title.ifBlank { meal.title },
                                calories.toIntOrNull(),
                                protein.toDoubleOrNull(),
                                carbs.toDoubleOrNull(),
                                fat.toDoubleOrNull(),
                                slot,
                                usedItems,
                                meal.loggedDateEpochDay,
                                source,
                            )
                            editing = false
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Save") }
                },
            ) {
                PreferenceTextField("Title", title, "Lunch, dinner, snack", onValue = { title = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealSlot.entries.forEach { option ->
                        FilterChip(
                            selected = slot == option,
                            onClick = { slot = option },
                            label = { Text("${option.iconText} ${option.label}") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceTextField("Calories", calories, "520", modifier = Modifier.weight(1f), onValue = { calories = it })
                    PreferenceTextField("Protein", protein, "28", modifier = Modifier.weight(1f), onValue = { protein = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceTextField("Carbs", carbs, "55", modifier = Modifier.weight(1f), onValue = { carbs = it })
                    PreferenceTextField("Fat", fat, "16", modifier = Modifier.weight(1f), onValue = { fat = it })
                }
                PreferenceTextField("Used from kitchen", usedItems, "Rice, spinach, eggs", minLines = 3, onValue = { usedItems = it })
                PreferenceTextField("Source", source, "ai estimate, manual, restaurant", onValue = { source = it })
                Button(
                    onClick = {
                        onUpdate(
                            meal.id,
                            title.ifBlank { meal.title },
                            calories.toIntOrNull(),
                            protein.toDoubleOrNull(),
                            carbs.toDoubleOrNull(),
                            fat.toDoubleOrNull(),
                            slot,
                            usedItems,
                            meal.loggedDateEpochDay,
                            source,
                        )
                        editing = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Save changes") }
            }
        }
        DetailSection("🥬 Used from kitchen", meal.usedItemsText.ifBlank { "No pantry/freezer/fridge usage linked yet." })
        if (transactions.isNotEmpty()) {
            DetailSection(
                "🧾 Ledger",
                transactions.joinToString("\n") { "${it.action.label}: ${foodEmoji(it.itemName)} ${it.itemName} (${it.zone.label})" },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.HealthAndSafety, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sync")
            }
            ConfirmIconTextButton(Icons.Rounded.Delete, "Remove", "Remove ${meal.title}?", { onDelete(meal.id) })
        }
    }
}

@Composable
private fun PlanDetail(
    plan: MealPlan,
    entries: List<MealPlanEntry>,
    onBack: () -> Unit,
    onUpdate: MealPlanUpdateHandler,
) {
    var editing by remember(plan.id) { mutableStateOf(false) }
    var title by remember(plan.id, plan.updatedAtMillis) { mutableStateOf(plan.title) }
    var daysText by remember(plan.id, plan.updatedAtMillis) {
        mutableStateOf(
            entries.joinToString("\n") { entry ->
                "${entry.slot.label}: ${entry.title}${entry.calorieTarget?.let { " ${it} kcal" }.orEmpty()}"
            }.ifBlank { plan.daysText },
        )
    }
    var groceryHint by remember(plan.id, plan.updatedAtMillis) { mutableStateOf(plan.groceryHint) }
    var startDay by remember(plan.id, plan.updatedAtMillis) {
        mutableStateOf(plan.startDateEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty())
    }
    DetailShell(image = "🗓️", title = plan.title, subtitle = "Accepted meal plan", onBack = onBack) {
        val entryText = entries.joinToString("\n") { entry ->
            "${entry.dateEpochDay.epochDayShortDate()} ${entry.slot.label}: ${entry.title}" +
                (entry.calorieTarget?.let { "  ${it} kcal" } ?: "")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { editing = !editing }, shape = RoundedCornerShape(18.dp)) {
                Text(if (editing) "Done editing" else "Edit page")
            }
            AssistChip(onClick = {}, enabled = false, label = { Text("updated ${plan.updatedAtMillis.shortDate()}") })
        }
        if (editing) {
            SettingsGroupCard(
                icon = Icons.Rounded.CalendarMonth,
                title = "Edit meal plan",
                subtitle = "Title, meals, groceries, start day",
                action = {
                    Button(
                        onClick = {
                            onUpdate(
                                plan.id,
                                title,
                                daysText,
                                groceryHint,
                                startDay.toEpochDayOrNull() ?: plan.startDateEpochDay,
                            )
                            editing = false
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Save") }
                },
            ) {
                PreferenceTextField("Title", title, "Meal plan", onValue = { title = it })
                PreferenceTextField("Meals", daysText, "Lunch: tofu bowl 550 kcal", minLines = 5, onValue = { daysText = it })
                PreferenceTextField("Suggested groceries", groceryHint, "oats, spinach, yogurt", minLines = 3, onValue = { groceryHint = it })
                PreferenceTextField("Start date", startDay, "YYYY-MM-DD", onValue = { startDay = it })
                Button(
                    onClick = {
                        onUpdate(
                            plan.id,
                            title,
                            daysText,
                            groceryHint,
                            startDay.toEpochDayOrNull() ?: plan.startDateEpochDay,
                        )
                        editing = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Save changes") }
            }
        }
        DetailSection("🍽 Meals", entryText.ifBlank { plan.daysText })
        DetailSection("🛒 Suggested groceries", plan.groceryHint)
    }
}

@Composable
private fun DayDetail(
    day: CalendarSlot,
    onBack: () -> Unit,
    onAddPlanEntry: MealPlanEntryCreateHandler,
    onUpdatePlanEntry: MealPlanEntryUpdateHandler,
    onDeletePlanEntry: (Long) -> Unit,
    onOpenDetail: (FoodDetailTarget) -> Unit,
    onLogMeal: (Long) -> Unit,
    onLogWater: (Int) -> Unit,
) {
    val bought = day.inventoryChanges.filter { it.action == InventoryAction.BOUGHT }
    val used = day.inventoryChanges.filter { it.action == InventoryAction.USED }
    val waterMl = day.events.filter { it.type == FoodEventType.WATER && it.unit == "ml" }.sumOf { it.amount ?: 0.0 }.toInt()
    val cookEvents = day.events.filter { it.type == FoodEventType.COOK }
    val shopEvents = day.events.filter { it.type == FoodEventType.SHOP || it.type == FoodEventType.GROCERY_PURCHASE }
    val epochDay = day.date.toEpochDay()
    DetailShell(
        image = if (day.didEatPlanned) "✅" else "🗓️",
        title = "${day.weekday} ${day.dayNumber}",
        subtitle = day.date.format(DateTimeFormatter.ofPattern("MMMM d")),
        onBack = onBack,
    ) {
        VisualFactGrid(
            facts = listOf(
                "🗓️" to (day.plannedMeal ?: "open"),
                "🍽️" to "${day.meals.size}",
                "🛒" to "${day.shopping.size}",
                "📖" to "${day.recipes.size}",
                "🥬" to "${day.inventoryChanges.size}",
                "💧" to "${waterMl / 1000.0} L",
                "🍳" to "${cookEvents.size}",
                "❤️" to if (day.meals.isEmpty()) "no sync" else "syncable",
            ),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onLogMeal(epochDay) }, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Log meal")
            }
            OutlinedButton(onClick = { onLogWater(250) }, shape = RoundedCornerShape(18.dp)) { Text("+250 ml water") }
            OutlinedButton(onClick = { onLogWater(500) }, shape = RoundedCornerShape(18.dp)) { Text("+500 ml water") }
        }
        SectionLabel("Planned meals")
        if (day.planEntries.isEmpty()) {
            DetailSection("🗓 Planned", day.plannedMeal ?: "No meal plan for this day.")
        } else {
            day.planEntries.forEach { entry ->
                PlannedMealEntryCard(
                    entry = entry,
                    fallbackDateEpochDay = epochDay,
                    onUpdate = onUpdatePlanEntry,
                    onDelete = onDeletePlanEntry,
                )
            }
        }
        AddPlannedMealCard(
            dateEpochDay = epochDay,
            onAdd = onAddPlanEntry,
        )
        SectionLabel("Ate")
        if (day.meals.isEmpty()) {
            DetailSection("🍽 Meals", "No meal logged.")
        } else {
            day.meals.forEach { meal ->
                MemoryCard(
                    title = "${meal.mealSlot.label}: ${meal.title}",
                    subtitle = mealNutritionSummary(meal),
                    accent = MaterialTheme.colorScheme.tertiaryContainer,
                    image = foodEmoji(meal.title),
                    onOpen = { onOpenDetail(FoodDetailTarget(FoodDetailKind.MEAL, id = meal.id)) },
                )
            }
        }
        DetailSection(
            "🛒 Shopping",
            bought.joinToString("\n") { "${foodEmoji(it.itemName)} ${it.itemName}" }
                .ifBlank { day.shopping.joinToString("\n") { "${foodEmoji(it.name)} ${it.name}" }.ifBlank { "No shopping captured." } },
        )
        day.shopping.forEach { grocery ->
            MemoryCard(
                title = grocery.name,
                subtitle = grocery.status.name.lowercase(),
                accent = MaterialTheme.colorScheme.tertiaryContainer,
                image = foodEmoji(grocery.name),
                onOpen = { onOpenDetail(FoodDetailTarget(FoodDetailKind.GROCERY, id = grocery.id)) },
            )
        }
        DetailSection("📖 Recipes added", day.recipes.joinToString("\n") { "${foodEmoji(it.title)} ${it.title}" }.ifBlank { "No recipes added." })
        day.recipes.forEach { recipe ->
            TextButton(onClick = { onOpenDetail(FoodDetailTarget(FoodDetailKind.RECIPE, id = recipe.id)) }) {
                Text("Open ${recipe.title}")
            }
        }
        DetailSection(
            "🔢 Numbers",
            listOf(
                "Water: ${waterMl / 1000.0} L",
                "Cook events: ${cookEvents.size}",
                "Shopping events: ${shopEvents.size}",
                "Calories logged: ${day.meals.takeIf { meals -> meals.any { it.calories != null } }?.mapNotNull { it.calories }?.sum()?.let { "$it kcal" } ?: "unknown"}",
                "Protein logged: ${day.meals.takeIf { meals -> meals.any { it.proteinGrams != null } }?.mapNotNull { it.proteinGrams }?.sum()?.toInt()?.let { "${it}g" } ?: "unknown"}",
            ).joinToString("\n"),
        )
        DetailSection(
            "🥬 Kitchen changes",
            day.inventoryChanges.joinToString("\n") { "${it.action.label}: ${foodEmoji(it.itemName)} ${it.itemName}  ${it.zone.label}" }
                .ifBlank { "No pantry/fridge changes." },
        )
        DetailSection(
            "⚖️ Pantry usage",
            used.joinToString("\n") { "${foodEmoji(it.itemName)} ${it.itemName} for ${it.reason}" }
                .ifBlank { day.meals.joinToString("\n") { it.usedItemsText }.ifBlank { "No ingredient usage accepted yet." } },
        )
        DetailSection("❤️ Health Connect", if (day.meals.isEmpty()) "No nutrition export for this day yet." else "Meal nutrition can sync. Activity read-back comes next.")
    }
}

@Composable
private fun PlannedMealEntryCard(
    entry: MealPlanEntry,
    fallbackDateEpochDay: Long,
    onUpdate: MealPlanEntryUpdateHandler,
    onDelete: (Long) -> Unit,
) {
    var title by remember(entry.id, entry.updatedAtMillis) { mutableStateOf(entry.title) }
    var slot by remember(entry.id, entry.updatedAtMillis) { mutableStateOf(entry.slot) }
    var calories by remember(entry.id, entry.updatedAtMillis) { mutableStateOf(entry.calorieTarget?.toString().orEmpty()) }
    var status by remember(entry.id, entry.updatedAtMillis) { mutableStateOf(entry.status) }
    var dateText by remember(entry.id, entry.updatedAtMillis) {
        mutableStateOf(LocalDate.ofEpochDay(entry.dateEpochDay).toString())
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FoodImage(
                    image = entry.imageUri ?: foodEmoji(entry.title),
                    fallback = foodEmoji(entry.title),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    size = 44.dp,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${entry.slot.label} • ${entry.status.name.lowercase()} • ${entry.calorieTarget ?: 0} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ConfirmIconTextButton(
                    icon = Icons.Rounded.Delete,
                    label = "Remove",
                    title = "Remove ${entry.title} from this day?",
                    onConfirm = { onDelete(entry.id) },
                )
            }
            PreferenceTextField("Meal", title, "Rajma bowl", onValue = { title = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreferenceTextField("Calories", calories, "620", modifier = Modifier.weight(1f), onValue = { calories = it })
                PreferenceTextField("Date", dateText, LocalDate.ofEpochDay(fallbackDateEpochDay).toString(), modifier = Modifier.weight(1f), onValue = { dateText = it })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MealSlot.entries.forEach { option ->
                    FilterChip(
                        selected = slot == option,
                        onClick = { slot = option },
                        label = { Text("${option.iconText} ${option.label}") },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MealPlanEntryStatus.entries.forEach { option ->
                    FilterChip(
                        selected = status == option,
                        onClick = { status = option },
                        label = { Text(option.name.lowercase()) },
                    )
                }
            }
            Button(
                onClick = {
                    onUpdate(
                        entry.id,
                        dateText.toEpochDayOrNull() ?: fallbackDateEpochDay,
                        slot,
                        title,
                        calories.toIntOrNull(),
                        status,
                    )
                },
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save planned meal")
            }
        }
    }
}

@Composable
private fun AddPlannedMealCard(
    dateEpochDay: Long,
    onAdd: MealPlanEntryCreateHandler,
) {
    var title by remember(dateEpochDay) { mutableStateOf("") }
    var slot by remember(dateEpochDay) { mutableStateOf(MealSlot.LUNCH) }
    var calories by remember(dateEpochDay) { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add planned meal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            PreferenceTextField("Meal", title, "Lunch from pantry", onValue = { title = it })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MealSlot.entries.forEach { option ->
                    FilterChip(
                        selected = slot == option,
                        onClick = { slot = option },
                        label = { Text("${option.iconText} ${option.label}") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PreferenceTextField("Calories", calories, "550", modifier = Modifier.weight(1f), onValue = { calories = it })
                Button(
                    enabled = title.isNotBlank(),
                    onClick = {
                        onAdd(dateEpochDay, slot, title, calories.toIntOrNull())
                        title = ""
                        calories = ""
                    },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun ReceiptDetail(
    receipt: ReceiptCapture,
    onBack: () -> Unit,
    onUpdate: ReceiptUpdateHandler,
) {
    var editing by remember(receipt.id) { mutableStateOf(false) }
    var rawText by remember(receipt.id, receipt.rawText) { mutableStateOf(receipt.rawText) }
    var status by remember(receipt.id, receipt.status) { mutableStateOf(receipt.status) }
    DetailShell(image = "🧾", title = "Receipt photo", subtitle = receipt.status.name.lowercase(), onBack = onBack) {
        VisualFactGrid(
            facts = listOf(
                "📅" to receipt.createdAtMillis.shortDate(),
                "🧠" to receipt.status.name.lowercase(),
                "🖼️" to receipt.imageUri.takeLast(16),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { editing = !editing }, shape = RoundedCornerShape(18.dp)) {
                Text(if (editing) "Done editing" else "Edit page")
            }
        }
        if (editing) {
            SettingsGroupCard(
                icon = Icons.Rounded.Description,
                title = "Edit receipt extraction",
                subtitle = "Correct OCR or pasted receipt text",
                action = {
                    Button(
                        onClick = {
                            onUpdate(receipt.id, rawText, status)
                            editing = false
                        },
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Save") }
                },
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReceiptStatus.entries.forEach { option ->
                        FilterChip(selected = status == option, onClick = { status = option }, label = { Text(option.name.lowercase()) })
                    }
                }
                PreferenceTextField("Receipt text", rawText, "Paste or fix receipt lines", minLines = 6, onValue = { rawText = it })
            }
        }
        DetailSection(
            "🧾 Extraction",
            receipt.rawText.ifBlank { "No extracted text saved yet. If vision fails, paste the receipt lines into chat." },
        )
    }
}

@Composable
private fun DetailShell(
    image: String,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader(
            image = image,
            fallback = image,
            title = title,
            subtitle = subtitle,
            onClose = onBack,
        )
        content()
    }
}

@Composable
private fun VisualFactGrid(facts: List<Pair<String, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.forEach { (icon, value) ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(icon, style = MaterialTheme.typography.titleMedium)
                    Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, body: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ObjectImage(image: String, color: Color, size: androidx.compose.ui.unit.Dp = 52.dp) {
    val context = LocalContext.current
    val bitmap = remember(image) {
        if (image.isLocalImageUri()) {
            runCatching {
                context.contentResolver.openInputStream(image.toUri())?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        } else {
            null
        }
    }
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(18.dp),
        color = color,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(image.displayImageText("🍽️"), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

private fun String.isLocalImageUri(): Boolean =
    startsWith("content://") || startsWith("file://")

private fun String.displayImageText(fallback: String): String =
    takeUnless { it.isLocalImageUri() || it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        .orEmpty()
        .ifBlank { fallback }

@Composable
private fun InventoryCard(
    item: InventoryItem,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "Open inventory item ${item.name}"
            }
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoodImage(image = item.imageUri ?: foodEmoji(item.name), fallback = foodEmoji(item.name), color = zoneColor(item.zone), size = 52.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(item.quantity, item.zone.label, item.kitchenStateLabel()).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SourceBadge(item.source)
                    ConfidenceBadge(if (item.calories == null) BadgeConfidence.UNKNOWN else BadgeConfidence.ESTIMATED)
                }
            }
            if (selectionMode) {
                FilterChip(selected = selected, onClick = onToggleSelected, label = { Text(if (selected) "✓" else "+") })
            }
        }
    }
}

@Composable
private fun InventoryTile(
    item: InventoryItem,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 172.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Open inventory tile ${item.name}"
            }
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FoodImage(image = item.imageUri ?: foodEmoji(item.name), fallback = foodEmoji(item.name), color = zoneColor(item.zone), size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.zone.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        SourceBadge(item.source)
                    }
                }
            }
            Text(
                item.quantity.ifBlank { "quantity unknown" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                nutritionSummary(item.calories, item.proteinGrams),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(item.category.ifBlank { "other" }, style = MaterialTheme.typography.labelMedium)
                if (selectionMode) {
                    FilterChip(selected = selected, onClick = onToggleSelected, label = { Text(if (selected) "✓" else "+") })
                } else {
                    ConfidenceBadge(if (item.calories == null) BadgeConfidence.UNKNOWN else BadgeConfidence.ESTIMATED)
                }
            }
        }
    }
}

@Composable
private fun GroceryCard(item: GroceryItem, onOpen: () -> Unit, onBought: () -> Unit, onDelete: () -> Unit) {
    MemoryCard(
        title = item.name,
        subtitle = listOf(
            item.quantity,
            item.status.name.lowercase(),
            nutritionSummary(item.calories, item.proteinGrams).takeIf { item.calories != null },
        ).filterNotNull().filter { it.isNotBlank() }.joinToString("  "),
        accent = MaterialTheme.colorScheme.secondaryContainer,
        image = item.imageUri ?: foodEmoji(item.name),
        onOpen = onOpen,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.status == GroceryStatus.NEEDED) {
                    IconTextButton(Icons.Rounded.Check, "Bought", onBought)
                }
                ConfirmIconTextButton(Icons.Rounded.Delete, "Remove", "Remove ${item.name}?", onDelete)
            }
        },
    )
}

@Composable
private fun MealPlanCard(plan: MealPlan, onOpen: () -> Unit = {}) {
    MemoryCard(
        title = plan.title,
        subtitle = "🗓 ${plan.daysText.lineSequence().take(2).joinToString("  ")}",
        accent = MaterialTheme.colorScheme.primaryContainer,
        image = "🗓️",
        onOpen = onOpen,
    )
}

@Composable
private fun MealCard(meal: MealLog, onOpen: () -> Unit, onDelete: () -> Unit) {
    MemoryCard(
        title = meal.title,
        subtitle = mealNutritionSummary(meal),
        accent = MaterialTheme.colorScheme.tertiaryContainer,
        image = foodEmoji(meal.title),
        onOpen = onOpen,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTextButton(Icons.Rounded.Edit, "Edit", onOpen)
                ConfirmIconTextButton(Icons.Rounded.Delete, "Remove", "Remove ${meal.title}?", onDelete)
            }
        },
    )
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    match: RecipeKitchenMatch,
    onOpen: () -> Unit,
    onCook: () -> Unit,
) {
    val ingredientCount = match.have.size + match.need.size
    MemoryCard(
        title = recipe.title,
        subtitle = listOf(
            if (ingredientCount > 0) "${match.have.size}/$ingredientCount in Kitchen" else "Ingredients need review",
            recipe.ingredients.take(90),
        ).joinToString(" • "),
        accent = MaterialTheme.colorScheme.secondaryContainer,
        image = foodEmoji(recipe.title).ifBlank { "📖" },
        onOpen = onOpen,
        trailing = {
            IconTextButton(Icons.Rounded.Restaurant, "Cook", onCook)
        },
    )
}

@Composable
private fun RecipeTile(
    recipe: Recipe,
    match: RecipeKitchenMatch,
    lastHad: Long?,
    onOpen: () -> Unit,
    onCook: () -> Unit,
) {
    val ingredientCount = match.have.size + match.need.size
    Card(
        modifier = Modifier.fillMaxWidth().height(210.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FoodImage(recipe.imageUri ?: foodEmoji(recipe.title), fallback = foodEmoji(recipe.title), color = MaterialTheme.colorScheme.secondaryContainer, size = 54.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(recipe.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            recipe.prepMinutes?.let { "${it}m" },
                            recipe.servings?.let { "$it servings" },
                            lastHad?.let { "last ${it.epochDayShortDate()}" },
                        ).joinToString(" • ").ifBlank { "not cooked yet" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                recipe.ingredients,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        ingredientCount == 0 -> "Check ingredients"
                        match.need.isEmpty() -> "Make now • $ingredientCount/$ingredientCount"
                        else -> "${match.have.size}/$ingredientCount • ${match.need.size} missing"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (match.need.isEmpty() && ingredientCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconTextButton(Icons.Rounded.Restaurant, "Cook", onCook)
            }
        }
    }
}

@Composable
private fun ReceiptCard(receipt: ReceiptCapture, onOpen: () -> Unit) {
    MemoryCard(
        title = "Receipt photo",
        subtitle = "${receipt.status.name.lowercase()}  ${receipt.createdAtMillis.shortDate()}",
        accent = MaterialTheme.colorScheme.tertiaryContainer,
        image = "🧾",
        onOpen = onOpen,
    )
}

@Composable
private fun MemoryCard(
    title: String,
    subtitle: String,
    accent: Color,
    image: String,
    onOpen: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val cardModifier = if (onOpen == null) {
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Open $title"
            }
    } else {
        Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "Open ${title}"
            }
            .clickable(onClick = onOpen)
    }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ObjectImage(image = image, color = accent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun IconTextButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

@Composable
private fun ConfirmIconTextButton(
    icon: ImageVector,
    label: String,
    title: String,
    onConfirm: () -> Unit,
    body: String = "This changes your local food memory. Keep it deliberate.",
) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                Button(
                    onClick = {
                        open = false
                        onConfirm()
                    },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(label)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { open = false }, shape = RoundedCornerShape(18.dp)) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Empty state: $title" },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actionLabel?.let {
                Button(onClick = onAction, shape = RoundedCornerShape(18.dp)) { Text(it) }
            }
        }
    }
}

@Composable
private fun FeedbackBar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            actionLabel?.let { TextButton(onClick = onAction) { Text(it) } }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss message") }
        }
    }
}

@Composable
private fun ManualCreateDialog(
    kind: ManualCreateKind,
    defaultEpochDay: Long?,
    defaultSlot: MealSlot?,
    onDismiss: () -> Unit,
    onCreate: (ManualCreateRequest) -> Unit,
) {
    var title by rememberSaveable(kind) { mutableStateOf("") }
    var detail by rememberSaveable(kind) { mutableStateOf("") }
    var secondaryDetail by rememberSaveable(kind) { mutableStateOf("") }
    var zone by rememberSaveable(kind) { mutableStateOf(StorageZone.PANTRY) }
    var slot by rememberSaveable(kind, defaultSlot) { mutableStateOf(defaultSlot ?: MealSlot.FLEX) }
    var calories by rememberSaveable(kind) { mutableStateOf("") }
    var dateText by rememberSaveable(kind, defaultEpochDay) {
        mutableStateOf(LocalDate.ofEpochDay(defaultEpochDay ?: LocalDate.now().toEpochDay()).toString())
    }
    val titleText = when (kind) {
        ManualCreateKind.INVENTORY -> "Add kitchen food"
        ManualCreateKind.GROCERY -> "Add shopping item"
        ManualCreateKind.RECIPE -> "Create recipe"
        ManualCreateKind.MEAL -> "Log meal"
    }
    val parsedDate = dateText.toEpochDayOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PreferenceTextField(
                    label = when (kind) {
                        ManualCreateKind.RECIPE -> "Recipe title"
                        ManualCreateKind.MEAL -> "Meal title"
                        else -> "Name"
                    },
                    value = title,
                    placeholder = "Required",
                    onValue = { title = it },
                )
                when (kind) {
                    ManualCreateKind.INVENTORY -> {
                        PreferenceTextField("Quantity", detail, "2 bags, 500 g, 6", onValue = { detail = it })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StorageZone.entries.forEach { option ->
                                FilterChip(selected = zone == option, onClick = { zone = option }, label = { Text(option.label) })
                            }
                        }
                    }
                    ManualCreateKind.GROCERY ->
                        PreferenceTextField("Quantity", detail, "2, one bag, 500 g", onValue = { detail = it })
                    ManualCreateKind.RECIPE -> {
                        PreferenceTextField("Ingredients", detail, "One ingredient per line", minLines = 3, onValue = { detail = it })
                        PreferenceTextField("Steps", secondaryDetail, "One step per line", minLines = 3, onValue = { secondaryDetail = it })
                    }
                    ManualCreateKind.MEAL -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MealSlot.entries.forEach { option ->
                                FilterChip(selected = slot == option, onClick = { slot = option }, label = { Text(option.label) })
                            }
                        }
                        PreferenceTextField("Date", dateText, "YYYY-MM-DD", onValue = { dateText = it })
                        PreferenceTextField("Calories (optional)", calories, "Leave blank if unknown", onValue = { calories = it })
                    }
                    }
                }
            }
        }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && (kind != ManualCreateKind.MEAL || parsedDate != null),
                onClick = {
                    onCreate(
                        ManualCreateRequest(
                            kind = kind,
                            title = title,
                            detail = detail,
                            secondaryDetail = secondaryDetail,
                            zone = zone,
                            slot = slot,
                            dateEpochDay = parsedDate ?: defaultEpochDay,
                            calories = calories.toIntOrNull(),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AiCaptureSheet(
    input: String,
    isWorking: Boolean,
    draft: FoodDraft?,
    draftOrigin: FoodDraftCommandOrigin,
    messages: List<ChatMessage>,
    pageContext: AiPageContext,
    providerStatus: String,
    backendHome: BackendHomeUiState,
    healthStatus: String,
    contextSummary: String,
    status: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit,
    onPickReceiptPhoto: () -> Unit,
    onRecordVoiceNote: () -> Unit,
    onAcceptDraft: () -> Unit,
    onRejectDraft: () -> Unit,
    onDraftChange: (FoodDraft) -> Unit,
    onOpenDataHome: () -> Unit,
    onOpenAiControl: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val currentChatId = messages.maxOfOrNull(ChatMessage::chatId) ?: 1L
    var selectedChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val displayedChatId = selectedChatId ?: currentChatId
    val displayedMessages = messages.filter { it.chatId == displayedChatId }
    val viewingPreviousChat = displayedChatId != currentChatId
    LaunchedEffect(currentChatId) {
        selectedChatId = null
    }
    val sendAndClearFocus = {
        focusManager.clearFocus(force = true)
        onSend()
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "AI full page" },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AiChatTopCard(
                pageTitle = pageContext.title,
                messageCount = messages.size,
                isWorking = isWorking,
                onHistory = { showHistory = true },
                onNewChat = onNewChat,
                onDismiss = onDismiss,
            )

            AiConversationTimeline(
                messages = displayedMessages,
                draft = draft.takeUnless { viewingPreviousChat },
                draftOrigin = draftOrigin,
                isWorking = isWorking,
                pageContext = pageContext,
                providerStatus = providerStatus,
                backendHome = backendHome,
                healthStatus = healthStatus,
                contextSummary = contextSummary,
                onSuggestion = onInputChange,
                onAcceptDraft = onAcceptDraft,
                onRejectDraft = onRejectDraft,
                onDraftChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            if (viewingPreviousChat) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Reading a previous chat", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Button(onClick = { selectedChatId = null }, shape = RoundedCornerShape(18.dp)) {
                            Text("Current chat")
                        }
                    }
                }
            } else {
                AiChatComposer(
                    input = input,
                    isWorking = isWorking,
                    status = status,
                    placeholder = pageContext.placeholder,
                    onInputChange = onInputChange,
                    onSend = sendAndClearFocus,
                    onPickReceiptPhoto = onPickReceiptPhoto,
                    onRecordVoiceNote = onRecordVoiceNote,
                )
            }
        }
    }
    if (showHistory) {
        AiChatHistoryDialog(
            messages = messages,
            currentChatId = currentChatId,
            selectedChatId = displayedChatId,
            onSelect = { chatId ->
                selectedChatId = chatId.takeUnless { it == currentChatId }
                showHistory = false
            },
            onDismiss = { showHistory = false },
        )
    }
}

@Composable
private fun AiChatTopCard(
    pageTitle: String,
    messageCount: Int,
    isWorking: Boolean,
    onHistory: () -> Unit,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ObjectImage("💬", MaterialTheme.colorScheme.primaryContainer, 42.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "WonderFood Chat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pageTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close AI capture")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onHistory,
                    enabled = messageCount > 0,
                    label = { Text("History") },
                    leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = onNewChat,
                    enabled = !isWorking,
                    label = { Text("New") },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                DraftReviewPill("Sources on")
            }
        }
    }
}

@Composable
private fun AiChatHistoryDialog(
    messages: List<ChatMessage>,
    currentChatId: Long,
    selectedChatId: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val chats = messages.groupBy(ChatMessage::chatId).toList().sortedByDescending { it.first }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat history") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .semantics { contentDescription = "Chat history list" },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chats, key = { it.first }) { (chatId, chatMessages) ->
                    val first = chatMessages.first()
                    val preview = chatMessages.lastOrNull { it.role == ChatRole.USER }?.body
                        ?: chatMessages.lastOrNull()?.body.orEmpty()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(chatId) }
                            .semantics { selected = chatId == selectedChatId },
                        shape = RoundedCornerShape(18.dp),
                        color = if (chatId == selectedChatId) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (chatId == currentChatId) "Current chat" else "Chat from ${first.createdAtMillis.shortDate()}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                preview.replace('\n', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${chatMessages.size} messages • ${first.createdAtMillis.shortTime()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun AiConversationTimeline(
    messages: List<ChatMessage>,
    draft: FoodDraft?,
    draftOrigin: FoodDraftCommandOrigin,
    isWorking: Boolean,
    pageContext: AiPageContext,
    providerStatus: String,
    backendHome: BackendHomeUiState,
    healthStatus: String,
    contextSummary: String,
    onSuggestion: (String) -> Unit,
    onAcceptDraft: () -> Unit,
    onRejectDraft: () -> Unit,
    onDraftChange: (FoodDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(messages.size, draft, isWorking) {
        listState.animateScrollToItem(0)
    }
    val showJumpToLatest by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 96
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (draft != null) {
                item(key = "draft") {
                    AiDraftReview(
                        draft = draft,
                        origin = draftOrigin,
                        onAccept = onAcceptDraft,
                        onReject = onRejectDraft,
                        onChange = onDraftChange,
                    )
                }
            }
            if (isWorking) {
                item(key = "typing") {
                    AiTypingBubble()
                }
            }
            val recentMessages = messages.asReversed()
            if (recentMessages.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Start a real food chat.",
                        subtitle = "Ask a question, paste a receipt, plan meals, or ask what sources I can cite. I will draft changes separately for review.",
                    )
                }
            } else {
                items(recentMessages, key = { "message-${it.id}" }) { message ->
                    AiThreadMessageBubble(message)
                }
            }
            item(key = "suggestions") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pageContext.suggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { onSuggestion(suggestion) },
                            label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            item(key = "context") {
                AiContextCard(
                    pageContext = pageContext,
                    providerStatus = providerStatus,
                    backendHome = backendHome,
                    contextSummary = contextSummary,
                )
            }
            item(key = "day") {
                AiThreadDivider("Today")
            }
        }

        if (showJumpToLatest) {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                icon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                text = { Text("Latest") },
                expanded = false,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AiThreadDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AiThreadMessageBubble(
    message: ChatMessage,
) {
    val isUser = message.role == ChatRole.USER
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            ObjectImage(image = "✦", color = MaterialTheme.colorScheme.primaryContainer, size = 30.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isUser) "You" else "WonderFood",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    message.createdAtMillis.shortTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 20.dp else 6.dp,
                    topEnd = if (isUser) 6.dp else 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp,
                ),
                color = bubbleColor,
                tonalElevation = if (isUser) 0.dp else 1.dp,
            ) {
                AiRichMessageBody(
                    body = message.body,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                    color = textColor,
                )
            }
            if (!isUser && message.sources.isNotEmpty()) {
                AiMessageSourceStrip(sources = message.sources)
            }
        }
    }
}

private sealed interface AiRichBlock {
    data class Paragraph(val text: String) : AiRichBlock
    data class ListBlock(val items: List<String>, val ordered: Boolean) : AiRichBlock
    data class TableBlock(val header: List<String>, val rows: List<List<String>>) : AiRichBlock
}

@Composable
private fun AiRichMessageBody(body: String, modifier: Modifier = Modifier, color: Color) {
    val blocks = remember(body) { body.parseAiRichBlocks() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is AiRichBlock.Paragraph -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
                is AiRichBlock.ListBlock -> AiRichList(block = block, color = color)
                is AiRichBlock.TableBlock -> AiRichTable(block = block, color = color)
            }
        }
    }
}

@Composable
private fun AiRichList(block: AiRichBlock.ListBlock, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(
                    text = if (block.ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun AiRichTable(block: AiRichBlock.TableBlock, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AiRichTableRow(cells = block.header, color = color, header = true)
            block.rows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                AiRichTableRow(
                    cells = block.header.indices.map { index -> row.getOrNull(index).orEmpty() },
                    color = color,
                    header = false,
                )
            }
        }
    }
}

@Composable
private fun AiRichTableRow(cells: List<String>, color: Color, header: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Text(
                text = cell,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

private fun String.parseAiRichBlocks(): List<AiRichBlock> {
    val lines = lines()
    val blocks = mutableListOf<AiRichBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> index += 1
            index + 1 < lines.size && trimmed.looksLikePipeRow() && lines[index + 1].trim().isMarkdownTableSeparator() -> {
                val header = trimmed.splitPipeCells()
                val rows = mutableListOf<List<String>>()
                index += 2
                while (index < lines.size && lines[index].trim().looksLikePipeRow()) {
                    rows += lines[index].trim().splitPipeCells()
                    index += 1
                }
                if (header.isNotEmpty() && rows.isNotEmpty()) {
                    blocks += AiRichBlock.TableBlock(header = header, rows = rows)
                }
            }
            trimmed.isBulletLine() -> {
                val items = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().isBulletLine()) {
                    items += lines[index].trim().removeBulletPrefix()
                    index += 1
                }
                blocks += AiRichBlock.ListBlock(items = items.filter { it.isNotBlank() }, ordered = false)
            }
            trimmed.isNumberedLine() -> {
                val items = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().isNumberedLine()) {
                    items += lines[index].trim().replace(Regex("^\\d+[.)]\\s+"), "")
                    index += 1
                }
                blocks += AiRichBlock.ListBlock(items = items.filter { it.isNotBlank() }, ordered = true)
            }
            else -> {
                val paragraph = mutableListOf<String>()
                while (
                    index < lines.size &&
                    lines[index].trim().isNotBlank() &&
                    !lines[index].trim().isBulletLine() &&
                    !lines[index].trim().isNumberedLine() &&
                    !(index + 1 < lines.size && lines[index].trim().looksLikePipeRow() && lines[index + 1].trim().isMarkdownTableSeparator())
                ) {
                    paragraph += lines[index].trim().trimMarkdownHeading()
                    index += 1
                }
                blocks += AiRichBlock.Paragraph(paragraph.joinToString(" "))
            }
        }
    }
    return blocks.filterNot {
        it is AiRichBlock.Paragraph && it.text.isBlank() ||
            it is AiRichBlock.ListBlock && it.items.isEmpty()
    }.ifEmpty { listOf(AiRichBlock.Paragraph(this)) }
}

private fun String.looksLikePipeRow(): Boolean = count { it == '|' } >= 2

private fun String.isMarkdownTableSeparator(): Boolean =
    looksLikePipeRow() && splitPipeCells().isNotEmpty() && splitPipeCells().all { cell ->
        cell.isNotBlank() && cell.all { it == '-' || it == ':' || it.isWhitespace() }
    }

private fun String.splitPipeCells(): List<String> =
    trim()
        .trim('|')
        .split('|')
        .map { it.trim().trimInlineMarkdown() }
        .filter { it.isNotBlank() }

private fun String.isBulletLine(): Boolean =
    startsWith("- ") || startsWith("* ") || startsWith("• ")

private fun String.removeBulletPrefix(): String =
    removePrefix("- ").removePrefix("* ").removePrefix("• ").trim().trimInlineMarkdown()

private fun String.isNumberedLine(): Boolean = Regex("^\\d+[.)]\\s+.+").matches(this)

private fun String.trimMarkdownHeading(): String = replace(Regex("^#{1,6}\\s+"), "").trimInlineMarkdown()

private fun String.trimInlineMarkdown(): String =
    trim()
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("`(.+?)`"), "$1")

@Composable
private fun AiMessageSourceStrip(sources: List<ChatSourceRef>) {
    Column(
        modifier = Modifier.widthIn(max = 620.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sources",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${sources.size.coerceAtMost(6)} handles · swipe",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(sources.take(6).withIndex().toList()) { indexed ->
                AiMessageSourceCard(index = indexed.index + 1, source = indexed.value)
            }
        }
    }
}

@Composable
private fun AiMessageSourceCard(index: Int, source: ChatSourceRef) {
    val important = index == 1 || source.title.contains("source pack", ignoreCase = true)
    Surface(
        modifier = Modifier.width(264.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (important) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (important) MaterialTheme.colorScheme.primary.copy(alpha = 0.32f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        tonalElevation = if (important) 2.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "[$index]",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    source.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                source.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (source.quote.isNotBlank()) {
                Text(
                    source.quote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (important) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (source.uri.isNotBlank()) {
                Text(
                    source.uri.sourceUriLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AiTypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ObjectImage(image = "🧠", color = MaterialTheme.colorScheme.primaryContainer, size = 38.dp)
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                "WonderFood is thinking...",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiChatComposer(
    input: String,
    isWorking: Boolean,
    status: String,
    placeholder: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickReceiptPhoto: () -> Unit,
    onRecordVoiceNote: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = "AI capture text" },
                placeholder = { Text(placeholder) },
                minLines = 1,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(18.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPickReceiptPhoto, enabled = !isWorking) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = "Attach receipt photo")
                }
                IconButton(onClick = onRecordVoiceNote, enabled = !isWorking) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Record voice note")
                }
                Text(
                    text = status,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !isWorking,
                    modifier = Modifier
                        .height(56.dp)
                        .semantics { contentDescription = "Send AI capture" },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isWorking) "..." else "Send")
                }
            }
        }
    }
}

@Composable
private fun AiContextCard(
    pageContext: AiPageContext,
    providerStatus: String,
    backendHome: BackendHomeUiState,
    contextSummary: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pageContext.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(providerStatus, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DraftReviewPill("Data: ${backendHome.label}")
                        DraftReviewPill("Skills: food")
                        DraftReviewPill("Tools: receipt, voice, Health")
                        DraftReviewPill("Review before save")
                    }
                }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide AI context" else "What AI can see")
            }
            if (expanded) {
                Text(
                    contextSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AiCapabilityCenter(
    providerStatus: String,
    backendHome: BackendHomeUiState,
    healthStatus: String,
    contextSummary: String,
    messageCount: Int,
    onOpenDataHome: () -> Unit,
    onOpenAiControl: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val providerLive = "local fallback" !in providerStatus.lowercase()
    val dataHomeLive = backendHome.activeType != null && !backendHome.requiresOnboarding
    val dataLabel = backendHome.chatSourceLabel()
    val healthLive = listOf("granted", "steps", "calorie", "protein", "ready", "connected")
        .any { it in healthStatus.lowercase() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ObjectImage("📚", MaterialTheme.colorScheme.secondaryContainer, 38.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Sources ready", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${if (providerLive) "Model live" else "Local fallback"} · $dataLabel · citations visible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AssistChip(
                    onClick = { expanded = !expanded },
                    label = { Text(if (expanded) "Less" else "Details") },
                )
            }
            if (expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiCapabilityPill(
                        icon = if (providerLive) "🟢" else "🟡",
                        label = if (providerLive) "Model live" else "Local AI fallback",
                        onClick = onOpenAiControl,
                    )
                    AiCapabilityPill("💬", "$messageCount chat msgs")
                    AiCapabilityPill(
                        icon = if (dataHomeLive) "🟢" else "🟡",
                        label = "Data: $dataLabel",
                        onClick = onOpenDataHome,
                    )
                    AiCapabilityPill("📚", "Citations on")
                    AiCapabilityPill("🧠", "Food skill")
                    AiCapabilityPill("🧾", "Camera + voice")
                    AiCapabilityPill(if (healthLive) "🟢" else "⚪", "Health Connect")
                }
                AiCapabilityRows(
                    providerStatus = providerStatus,
                    backendHome = backendHome,
                    contextSummary = contextSummary,
                    healthStatus = healthStatus,
                    providerLive = providerLive,
                    dataHomeLive = dataHomeLive,
                    healthLive = healthLive,
                    onOpenDataHome = onOpenDataHome,
                    onOpenAiControl = onOpenAiControl,
                )
            }
        }
    }
}

@Composable
private fun AiCapabilityPill(icon: String, label: String, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = if (onClick == null) {
            Modifier
        } else {
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(role = Role.Button, onClick = onClick)
        },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, style = MaterialTheme.typography.labelMedium)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AiCapabilityRows(
    providerStatus: String,
    backendHome: BackendHomeUiState,
    contextSummary: String,
    healthStatus: String,
    providerLive: Boolean,
    dataHomeLive: Boolean,
    healthLive: Boolean,
    onOpenDataHome: () -> Unit,
    onOpenAiControl: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AiCapabilityRow(
            icon = "🤖",
            title = "Model route",
            state = if (providerLive) "Live provider" else "Local fallback",
            detail = providerStatus,
        )
        OutlinedButton(onClick = onOpenAiControl) {
            Text("Configure model + skills")
        }
        AiCapabilityRow(
            icon = "🗂️",
            title = "Data plane",
            state = if (dataHomeLive) "Connected" else "Needs setup",
            detail = backendHome.dataPlaneDetail(),
        )
        AiCapabilityRow(
            icon = "🔗",
            title = "Data-plane links",
            state = backendHome.dataPlaneProofState(dataHomeLive),
            detail = backendHome.dataPlaneProofDetail(),
        )
        if (!dataHomeLive) {
            OutlinedButton(onClick = onOpenDataHome) {
                Text("Set up data home")
            }
        }
        AiCapabilityRow(
            icon = "📚",
            title = "Citations",
            state = "Visible",
            detail = "Answers can cite app memory, LifeOS Notion, LifeOS Sheets, MCP schema, template health, and provider web/file sources.",
        )
        AiCapabilityRow(
            icon = "🧠",
            title = "Skills",
            state = "Food active",
            detail = "Domain skill is Food. Workflow skills are playbooks. Schemas are contracts, not separate top-level skills unless they have behavior.",
        )
        AiCapabilityRow(
            icon = "🧬",
            title = "Schemas",
            state = "Bridge-ready",
            detail = "Command envelope, Room schema, Notion/Sheets graph, MCP resources, and sync contracts ship as versioned resources.",
        )
        AiCapabilityRow(
            icon = "🕸️",
            title = "MCP / agents",
            state = "Local bridge ready",
            detail = "GPT/plugin clients get the same catalog, validators, proposal packages, and review-only app links.",
        )
        AiCapabilityRow(
            icon = "❤️",
            title = "Health Connect",
            state = if (healthLive) "Live/ready" else "Available when granted",
            detail = healthStatus.ifBlank { "Health status and nutrition export stay review-gated." },
        )
        Text(
            contextSummary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiCapabilityRow(icon: String, title: String, state: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("• $state", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                detail.ifBlank { "Not configured." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiDraftReview(
    draft: FoodDraft,
    origin: FoodDraftCommandOrigin,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onChange: (FoodDraft) -> Unit,
) {
    val review = draft.reviewCopy(origin)
    var editing by remember { mutableStateOf(false) }
    val editorRequester = remember { BringIntoViewRequester() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Draft review. ${review.sourceLabel}. ${review.actionLabel}. Not saved yet."
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(draft.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(draft.summary, style = MaterialTheme.typography.bodySmall)
            DraftReviewMeta(draft = draft, origin = origin, compact = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(draft.acceptButtonLabel())
                }
                OutlinedButton(onClick = { editing = !editing }, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (editing) "Done editing" else "Edit proposal")
                }
                OutlinedButton(onClick = onReject, shape = RoundedCornerShape(18.dp)) {
                    Text("Reject")
                }
            }
            draft.rows.take(4).forEach { row ->
                Text("- $row", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val hiddenRows = draft.rows.size - 4
            if (hiddenRows > 0) {
                Text("+ $hiddenRows more", style = MaterialTheme.typography.labelMedium)
            }
            if (editing) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(50)
                    editorRequester.bringIntoView()
                }
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Edit before saving",
                        modifier = Modifier.bringIntoViewRequester(editorRequester),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    DraftReviewEditor(draft = draft, onChange = onChange, showTitle = false)
                }
            }
        }
    }
}

private data class DraftReviewCopy(
    val sourceLabel: String,
    val actionLabel: String,
    val safetyText: String,
)

@Composable
private fun DraftReviewMeta(
    draft: FoodDraft,
    origin: FoodDraftCommandOrigin,
    compact: Boolean = false,
) {
    val review = draft.reviewCopy(origin)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DraftReviewPill("Source: ${review.sourceLabel}")
            DraftReviewPill("Action: ${review.actionLabel}")
            DraftReviewPill("Not saved yet")
        }
        val destructive = draft.operation == FoodOperation.DELETE
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Text(
                text = review.safetyText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DraftReviewPill(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun FoodDraft.reviewCopy(origin: FoodDraftCommandOrigin): DraftReviewCopy =
    DraftReviewCopy(
        sourceLabel = origin.reviewSourceLabel,
        actionLabel = operation.reviewActionLabel,
        safetyText = if (operation == FoodOperation.DELETE) {
            "Destructive change. Nothing is deleted until you confirm this exact draft."
        } else {
            when (origin) {
                FoodDraftCommandOrigin.AI_REVIEW ->
                    "LLM proposal only. Check names, quantities, allergens, dates, and duplicates before saving."
                FoodDraftCommandOrigin.LOCAL_FALLBACK ->
                    "Parsed locally on this phone. No provider was needed; review before saving."
                FoodDraftCommandOrigin.EXTERNAL_PROPOSAL ->
                    "External proposal filled this form only. The app has not saved or changed anything yet."
                FoodDraftCommandOrigin.GOOGLE_ASSISTANT ->
                    "Google Assistant filled this form. Review it here; reject and retry if anything is off."
                FoodDraftCommandOrigin.RECEIPT ->
                    "Receipt result only puts away rows marked Put away. Check names, quantities, storage, nutrition sources, estimated dates, duplicates, and non-food exclusions."
                FoodDraftCommandOrigin.CSV_IMPORT ->
                    "CSV import preview. Only validated rows can be saved."
                FoodDraftCommandOrigin.VOICE_AUTO_ACCEPT ->
                    "Voice path reached review instead of auto-save. Confirm only if every row is right."
                FoodDraftCommandOrigin.MANUAL_SAVE ->
                    "Manual draft. Review before saving."
            }
        },
    )

private val FoodDraftCommandOrigin.reviewSourceLabel: String
    get() =
        when (this) {
            FoodDraftCommandOrigin.AI_REVIEW -> "LLM proposal"
            FoodDraftCommandOrigin.CSV_IMPORT -> "CSV import"
            FoodDraftCommandOrigin.EXTERNAL_PROPOSAL -> "External app/link"
            FoodDraftCommandOrigin.GOOGLE_ASSISTANT -> "Google Assistant"
            FoodDraftCommandOrigin.LOCAL_FALLBACK -> "Local parser"
            FoodDraftCommandOrigin.MANUAL_SAVE -> "Manual"
            FoodDraftCommandOrigin.RECEIPT -> "Receipt/OCR"
            FoodDraftCommandOrigin.VOICE_AUTO_ACCEPT -> "Voice"
        }

private val FoodOperation.reviewActionLabel: String
    get() =
        when (this) {
            FoodOperation.CREATE -> "Create"
            FoodOperation.UPDATE -> "Update"
            FoodOperation.DELETE -> "Delete"
            FoodOperation.LOG -> "Log"
            FoodOperation.PLAN -> "Plan"
            FoodOperation.IMPORT -> "Import"
        }

private fun FoodDraft.acceptButtonLabel(): String =
    if (this is ReceiptDraft) {
        "Put away items"
    } else when (operation) {
        FoodOperation.DELETE -> "Confirm"
        FoodOperation.UPDATE -> "Update"
        FoodOperation.LOG -> "Log"
        FoodOperation.PLAN -> "Save plan"
        FoodOperation.IMPORT -> "Import"
        FoodOperation.CREATE -> "Save"
    }

@Composable
private fun kitchenFocusColor(item: InventoryItem): Color =
    when {
        item.expiresAtMillis != null && item.expiresAtMillis <= System.currentTimeMillis() -> MaterialTheme.colorScheme.tertiaryContainer
        item.quantity.trim() == "0" -> MaterialTheme.colorScheme.errorContainer
        item.quantity.isKitchenLowSignal() -> MaterialTheme.colorScheme.secondaryContainer
        else -> zoneColor(item.zone)
    }

@Composable
private fun zoneColor(zone: StorageZone): Color =
    when (zone) {
        StorageZone.FRIDGE -> MaterialTheme.colorScheme.primaryContainer
        StorageZone.FREEZER -> MaterialTheme.colorScheme.surfaceVariant
        StorageZone.PANTRY -> MaterialTheme.colorScheme.secondaryContainer
    }

private fun HouseholdUiMemory.globalSearchResults(): List<SearchResult> =
    buildList {
        inventory.sortedByDescending { it.updatedAtMillis }.forEach { item ->
            add(
                SearchResult(
                    kind = "kitchen",
                    id = item.id.toString(),
                    title = item.name,
                    subtitle = "Kitchen • ${item.zone.label} • ${item.quantity.ifBlank { "quantity unknown" }}",
                    image = foodEmoji(item.name),
                    target = FoodDetailTarget(FoodDetailKind.INVENTORY, id = item.id),
                ),
            )
        }
        groceries.sortedByDescending { it.updatedAtMillis }.forEach { item ->
            add(
                SearchResult(
                    kind = "shop",
                    id = item.id.toString(),
                    title = item.name,
                    subtitle = "Shop • ${item.status.name.lowercase()} • ${item.quantity.ifBlank { "as needed" }}",
                    image = foodEmoji(item.name),
                    target = FoodDetailTarget(FoodDetailKind.GROCERY, id = item.id),
                ),
            )
        }
        recipes.sortedByDescending { it.updatedAtMillis }.forEach { recipe ->
            add(
                SearchResult(
                    kind = "recipe",
                    id = recipe.id.toString(),
                    title = recipe.title,
                    subtitle = "Recipe • ${recipe.tags.ifBlank { "untagged" }}",
                    image = recipe.imageUri ?: foodEmoji(recipe.title),
                    target = FoodDetailTarget(FoodDetailKind.RECIPE, id = recipe.id),
                ),
            )
        }
        mealLogs.sortedByDescending { it.updatedAtMillis }.forEach { meal ->
            add(
                SearchResult(
                    kind = "meal",
                    id = meal.id.toString(),
                    title = meal.title,
                    subtitle = "Today • ${meal.mealSlot.label} • ${meal.loggedDateEpochDay.epochDayShortDate()}",
                    image = foodEmoji(meal.title),
                    target = FoodDetailTarget(FoodDetailKind.MEAL, id = meal.id),
                ),
            )
        }
        mealPlans.sortedByDescending { it.updatedAtMillis }.forEach { plan ->
            add(
                SearchResult(
                    kind = "plan",
                    id = plan.id.toString(),
                    title = plan.title,
                    subtitle = "Plan • ${plan.status.name.lowercase()}",
                    image = "🗓️",
                    target = FoodDetailTarget(FoodDetailKind.PLAN, id = plan.id),
                ),
            )
        }
        receipts.sortedByDescending { it.createdAtMillis }.forEach { receipt ->
            add(
                SearchResult(
                    kind = "receipt",
                    id = receipt.id.toString(),
                    title = "Receipt ${receipt.id}",
                    subtitle = "Shop • ${receipt.status.name.lowercase()} • ${receipt.createdAtMillis.shortDate()}",
                    image = "🧾",
                    target = FoodDetailTarget(FoodDetailKind.RECEIPT, id = receipt.id),
                ),
            )
        }
    }

private val FoodSection.icon: ImageVector
    get() =
        when (this) {
            FoodSection.TODAY -> Icons.Rounded.Restaurant
            FoodSection.KITCHEN -> Icons.Rounded.Kitchen
            FoodSection.PLAN -> Icons.Rounded.CalendarMonth
            FoodSection.RECIPES -> Icons.Rounded.Restaurant
            FoodSection.SHOP -> Icons.Rounded.ShoppingCart
        }

private val FoodSection.subtitle: String
    get() =
        when (this) {
            FoodSection.TODAY -> "Next meal, capture, reviews"
            FoodSection.KITCHEN -> "Can make, kitchen, saved"
            FoodSection.PLAN -> "This week and beyond"
            FoodSection.RECIPES -> "Saved recipes"
            FoodSection.SHOP -> "Cart, receipts, put away"
        }

private val WonderFoodThemeMode.displayLabel: String
    get() =
        when (this) {
            WonderFoodThemeMode.LIGHT -> "Light"
            WonderFoodThemeMode.DARK -> "Dark"
            WonderFoodThemeMode.SYSTEM -> "System"
        }

private val MealSlot.iconText: String
    get() =
        when (this) {
            MealSlot.BREAKFAST -> "☕"
            MealSlot.LUNCH -> "🥗"
            MealSlot.DINNER -> "🍛"
            MealSlot.SNACK -> "🍌"
            MealSlot.FLEX -> "🍽️"
        }

private val Int.pluralWord: String
    get() = if (this == 1) "" else "s"

private fun String.firstNumberOrNull(): Int? =
    Regex("""\d+""").find(this)?.value?.toIntOrNull()

private fun nutritionSummary(calories: Int?, protein: Double?): String =
    when {
        calories != null && protein != null -> "$calories kcal, ${protein.toInt()}g protein"
        calories != null -> "$calories kcal"
        protein != null -> "${protein.toInt()}g protein"
        else -> "nutrition pending"
    }

private fun nutritionSourceLabel(source: String): String =
    when (source.trim().lowercase()) {
        "manual" -> "Manual entry"
        "label", "nutrition_label" -> "Nutrition label"
        "ai_estimate", "ai_estimate_local", "ai" -> "AI estimate"
        "receipt" -> "Receipt estimate"
        "" -> "Not set"
        else -> source.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

private fun Double.cleanNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun InventoryItem.kitchenPriorityRank(): Int =
    when {
        expiresAtMillis != null && expiresAtMillis <= System.currentTimeMillis() + THREE_DAYS_MILLIS -> 0
        quantity.isKitchenLowSignal() -> 1
        calories == null && nutritionSource.isBlank() -> 2
        else -> 3
    }

private fun InventoryItem.kitchenStateLabel(): String =
    when {
        expiresAtMillis != null && expiresAtMillis <= System.currentTimeMillis() -> "Expired or due now"
        expiresAtMillis != null && expiresAtMillis <= System.currentTimeMillis() + THREE_DAYS_MILLIS -> "Use by ${expiresAtMillis.shortDate()}"
        quantity.trim() == "0" -> "Out of stock"
        quantity.isKitchenLowSignal() -> "Low stock"
        calories == null && nutritionSource.isBlank() -> "Needs nutrition"
        category.isNotBlank() -> category
        else -> zone.label
    }

private fun String.isKitchenLowSignal(): Boolean {
    val text = lowercase()
    return text == "0" ||
        "low" in text ||
        "empty" in text ||
        "out" in text ||
        "finish" in text ||
        "last" in text
}

private const val THREE_DAYS_MILLIS: Long = 3L * 24L * 60L * 60L * 1000L

private fun inventoryActionIcon(action: InventoryAction): String =
    when (action) {
        InventoryAction.ADDED -> "＋"
        InventoryAction.UPDATED -> "✎"
        InventoryAction.BOUGHT -> "🛒"
        InventoryAction.USED -> "🍳"
        InventoryAction.REMOVED -> "−"
    }

private fun String.preferenceTokens(): List<String> =
    split(",", "\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

private fun String.withPreferenceToken(token: String): String =
    (preferenceTokens() + token.trim())
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString(", ")

private fun String.withoutPreferenceToken(token: String): String =
    preferenceTokens()
        .filterNot { it.equals(token, ignoreCase = true) }
        .joinToString(", ")

private data class RecipeKitchenMatch(
    val have: List<String>,
    val need: List<String>,
)

private fun HouseholdUiMemory.lastHad(recipe: Recipe): Long? =
    mealLogs
        .filter { it.title.recipeMatches(recipe.title) || it.usedItemsText.recipeMatches(recipe.title) }
        .maxOfOrNull { it.loggedDateEpochDay }

private fun String.recipeMatches(other: String): Boolean {
    val left = lowercase()
    val right = other.lowercase()
    return left.contains(right) || right.contains(left)
}

private fun Recipe.kitchenMatch(memory: HouseholdUiMemory): RecipeKitchenMatch {
    val ingredients = ingredients
        .split(",", "\n", ";")
        .map { it.cleanIngredientName() }
        .filter { it.length >= 3 }
        .distinctBy { it.lowercase() }
    val inventoryNames = memory.inventory.map { it.name.lowercase() }
    val have = ingredients.filter { ingredient ->
        val normalized = ingredient.lowercase()
        inventoryNames.any { item -> item.contains(normalized) || normalized.contains(item) }
    }
    return RecipeKitchenMatch(
        have = have,
        need = ingredients.filterNot { candidate -> have.any { it.equals(candidate, ignoreCase = true) } },
    )
}

private fun String.cleanIngredientName(): String =
    replace(Regex("""^[\s\-•*\d./]+"""), "")
        .replace(Regex("""\b(cups?|tbsp|tsp|teaspoons?|tablespoons?|grams?|g|kg|ml|liters?|large|medium|small|cooked|dry|fresh)\b""", RegexOption.IGNORE_CASE), "")
        .trim()

private enum class SecondaryPane {
    SEARCH,
    SETTINGS,
}

private data class SearchResult(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val image: String,
    val target: FoodDetailTarget?,
)

private data class AiPageContext(
    val title: String,
    val subtitle: String,
    val placeholder: String,
    val suggestions: List<String>,
)

private fun WonderFoodUiState.aiPageContext(): AiPageContext {
    val target = detailTarget
    return when (target?.kind) {
        FoodDetailKind.RECIPE -> {
            val recipe = memory.recipes.firstOrNull { it.id == target.id }
            AiPageContext(
                title = recipe?.let { "Editing recipe: ${it.title}" } ?: "Editing recipe page",
                subtitle = "AI note applies to this recipe page.",
                placeholder = "Change ingredients, steps, servings, tags...",
                suggestions = listOf(
                    "Add spinach to ingredients",
                    "Set servings to 4",
                    "Make steps shorter",
                ),
            )
        }
        FoodDetailKind.MEAL -> {
            val meal = memory.mealLogs.firstOrNull { it.id == target.id }
            AiPageContext(
                title = meal?.let { "Editing meal: ${it.title}" } ?: "Editing meal page",
                subtitle = "AI note applies to this lunch/dinner log.",
                placeholder = "Change calories, protein, slot, used items...",
                suggestions = listOf(
                    "Set calories to 650",
                    "Change to lunch",
                    "Add rice and spinach to used items",
                ),
            )
        }
        FoodDetailKind.INVENTORY -> {
            val item = memory.inventory.firstOrNull { it.id == target.id }
            AiPageContext(
                title = item?.let { "Kitchen item: ${it.name}" } ?: "Kitchen item",
                subtitle = "Ask for grocery, cooking, or nutrition actions around this item.",
                placeholder = "Use this item, add to grocery list, update notes...",
                suggestions = listOf(
                    "What can I cook with this?",
                    "Add this to shopping list",
                    "Estimate nutrition",
                ),
            )
        }
        FoodDetailKind.GROCERY -> {
            val item = memory.groceries.firstOrNull { it.id == target.id }
            AiPageContext(
                title = item?.let { "Shopping item: ${it.name}" } ?: "Shopping item",
                subtitle = "Ask AI to reason about this to-buy item.",
                placeholder = "Change quantity, mark bought, suggest substitute...",
                suggestions = listOf(
                    "Move bought item into kitchen",
                    "Suggest a substitute",
                    "Estimate nutrition",
                ),
            )
        }
        FoodDetailKind.DAY -> AiPageContext(
            title = "Logging this calendar day",
            subtitle = "AI note becomes a meal, plan, water, or shopping update.",
            placeholder = "Log lunch, plan dinner, record shopping...",
            suggestions = listOf(
                "Log lunch: chicken rice bowl",
                "Plan dinner from what is fresh",
                "I drank 500 ml water",
            ),
        )
        FoodDetailKind.PLAN -> AiPageContext(
            title = "Editing meal plan",
            subtitle = "Ask AI to revise planned meals or grocery hints.",
            placeholder = "Move dinner, change calories, add groceries...",
            suggestions = listOf(
                "Make dinners higher protein",
                "Add groceries for this plan",
                "Move leftovers to tomorrow",
            ),
        )
        FoodDetailKind.RECEIPT -> AiPageContext(
            title = "Receipt extraction",
            subtitle = "AI can turn receipt details into groceries or kitchen inventory.",
            placeholder = "Paste receipt lines or describe what was bought...",
            suggestions = listOf(
                "Extract groceries from this receipt",
                "Move bought items into kitchen",
                "Estimate nutrition for these items",
            ),
        )
        null -> when (section) {
            FoodSection.TODAY -> AiPageContext(
                title = "Today: log food without typing much",
                subtitle = "Text or voice can create meal logs, plans, water, and shopping notes.",
                placeholder = "Log lunch, plan dinner, note water...",
                suggestions = listOf(
                    "Log lunch: chicken rice bowl",
                    "Plan meals this week",
                    "Need oats and berries",
                    "Need preferred staples",
                ),
            )
            FoodSection.KITCHEN -> AiPageContext(
                title = "Kitchen: pantry, fridge, freezer",
                subtitle = "Add, classify, or reason about what is on hand.",
                placeholder = "I bought eggs, yogurt, spinach...",
                suggestions = listOf(
                    "I bought eggs, yogurt, spinach",
                    "Stock weekly Costco",
                    "What should I use first?",
                    "Add preferred staples to pantry",
                ),
            )
            FoodSection.RECIPES -> AiPageContext(
                title = "Recipes: personal food pages",
                subtitle = "Create or tweak recipes from chat and kitchen memory.",
                placeholder = "Save recipe for...",
                suggestions = listOf(
                    "Save recipe for rajma curry",
                    "What can I cook today?",
                    "Add missing groceries for a recipe",
                ),
            )
            FoodSection.PLAN -> AiPageContext(
                title = "Plan: meals and calendar",
                subtitle = "Draft meal plans from history, preferences, and groceries.",
                placeholder = "Plan dinners for this week...",
                suggestions = listOf(
                    "Plan dinners this week",
                    "Suggest next meal",
                    "Build grocery list for the plan",
                ),
            )
            FoodSection.SHOP -> AiPageContext(
                title = "Shopping: to-buy list",
                subtitle = "Add groceries or turn bought items into kitchen inventory.",
                placeholder = "Need oats, bananas, milk...",
                suggestions = listOf(
                    "Need oats and berries",
                    "Need Indian groceries",
                    "Need preferred staples",
                    "I bought everything on the list",
                ),
            )
        }
    }
}

private data class CalendarSlot(
    val date: LocalDate,
    val weekday: String,
    val dayNumber: String,
    val title: String,
    val plannedMeal: String?,
    val planEntries: List<MealPlanEntry>,
    val meals: List<MealLog>,
    val shopping: List<GroceryItem>,
    val recipes: List<Recipe>,
    val events: List<FoodEvent>,
    val inventoryChanges: List<InventoryTransaction>,
    val isToday: Boolean,
) {
    val didEatPlanned: Boolean =
        planEntries.any { plan ->
            meals.any { meal ->
                meal.title.contains(plan.title, ignoreCase = true) ||
                    plan.title.contains(meal.title, ignoreCase = true)
            }
        } || plannedMeal != null && meals.any { meal ->
            meal.title.contains(plannedMeal, ignoreCase = true) || plannedMeal.contains(meal.title, ignoreCase = true)
        }
}

private fun calendarSlots(memory: HouseholdUiMemory): List<CalendarSlot> {
    val planLines = memory.mealPlans.firstOrNull()?.daysText
        ?.lines()
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
    val formatter = DateTimeFormatter.ofPattern("d")
    val today = LocalDate.now()
    return (0..6).map { index ->
        val date = today.plusDays(index.toLong())
        val epochDay = date.toEpochDay()
        val line = planLines.getOrNull(index)
        val planEntries = memory.mealPlanEntries.filter { it.dateEpochDay == epochDay }
        val plannedMeal = planEntries.firstOrNull()?.title ?: line?.substringAfter(": ", line)?.ifBlank { null }
        val meals = memory.mealLogs.filter { it.loggedDateEpochDay == epochDay }
        val shopping = memory.groceries.filter { it.createdAtMillis.toLocalDate() == date }
        val recipes = memory.recipes.filter { it.createdAtMillis.toLocalDate() == date }
        val events = memory.events.filter { it.startedAtMillis.toLocalDate() == date }
        val inventoryChanges = memory.inventoryTransactions.filter { it.occurredDateEpochDay == epochDay }
        val title = plannedMeal
            ?: meals.firstOrNull()?.title
            ?: recipes.firstOrNull()?.title
            ?: if (index == 0) "Open slot" else "Draft meal"
        CalendarSlot(
            date = date,
            weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            dayNumber = date.format(formatter),
            title = title,
            plannedMeal = plannedMeal,
            planEntries = planEntries,
            meals = meals,
            shopping = shopping,
            recipes = recipes,
            events = events,
            inventoryChanges = inventoryChanges,
            isToday = index == 0,
        )
    }
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun Long?.moneyInput(): String =
    this?.let { cents -> "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}" }.orEmpty()

private fun String.moneyCentsOrNull(): Long? {
    val amount = trim().replace(Regex("[^0-9.\\-]"), "").toBigDecimalOrNull() ?: return null
    return runCatching { amount.movePointRight(2).longValueExact() }.getOrNull()?.takeIf { it >= 0 }
}

private fun Long.shortDate(): String =
    toLocalDate().format(DateTimeFormatter.ofPattern("MMM d"))

private fun Long.shortTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))

private fun Long.epochDayShortDate(): String =
    LocalDate.ofEpochDay(this).format(DateTimeFormatter.ofPattern("MMM d"))

private fun String.toEpochDayOrNull(): Long? =
    runCatching { LocalDate.parse(trim()).toEpochDay() }.getOrNull()

private fun mealNutritionSummary(meal: MealLog): String {
    val parts = buildList {
        meal.calories?.let { add("🔥 $it kcal") }
        meal.proteinGrams?.let { add("💪 ${it.toInt()}g") }
        meal.carbsGrams?.let { add("🍚 ${it.toInt()}g") }
        meal.fatGrams?.let { add("🫒 ${it.toInt()}g") }
    }
    return parts.joinToString("  ").ifBlank { "Nutrition not added" }
}

private fun foodEmoji(name: String): String = foodEmojiForName(name)

private fun String.sourceUriLabel(): String =
    trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('?')
        .trimEnd('/')
        .take(64)

private fun BackendHomeUiState.dataPlaneDetail(): String =
    listOf(
        detail,
        message.takeIf { it.isNotBlank() },
        safetyMessage.takeIf { it.isNotBlank() },
    )
        .filterNotNull()
        .joinToString(" • ")
        .ifBlank { "Choose Notion, Google Sheets, local SQLite, or Postgres as the data home." }

private fun BackendHomeUiState.chatSourceLabel(): String =
    when {
        label.equals("Choose data home", ignoreCase = true) &&
            (templateNotionUrl.isNotBlank() || templateSheetsUrl.isNotBlank()) -> "LifeOS links ready"
        label.equals("On this phone", ignoreCase = true) -> "Phone store"
        label.isNotBlank() -> label
        else -> "Phone store"
    }

private fun BackendHomeUiState.lifeOsDataHomeDetail(): String =
    when {
        templateNotionUrl.isNotBlank() || templateSheetsUrl.isNotBlank() -> {
            val surfaces = buildList {
                if (templateNotionUrl.isNotBlank()) add("Notion")
                if (templateSheetsUrl.isNotBlank()) add("Sheets")
            }.joinToString(" + ")
            val homeLabel = label
                .takeUnless { it.equals("Choose data home", ignoreCase = true) }
                ?.takeIf { it.isNotBlank() }
                ?.let { if (it.equals("On this phone", ignoreCase = true)) "Phone store" else it }
                ?: "Local phone store"
            "$homeLabel is active. $surfaces are linked as LifeOS source surfaces."
        }
        activeType != null && !requiresOnboarding -> "${label.ifBlank { "Data home" }} is active for this phone."
        else -> "Choose Notion, Google Sheets, local SQLite, or Postgres when you want a primary LifeOS data home."
    }

private fun BackendHomeUiState.dataPlaneProofState(dataHomeLive: Boolean): String =
    when {
        !dataHomeLive -> "Missing"
        dataPlaneUrl.isNotBlank() -> "Link visible"
        activeType == BackendType.LOCAL_SQLITE -> "On-device"
        else -> "Saved"
    }

private fun BackendHomeUiState.dataPlaneProofDetail(): String =
    if (activeType == null || requiresOnboarding) {
        if (templateNotionUrl.isNotBlank() || templateSheetsUrl.isNotBlank()) {
            "LifeOS Notion and Sheets template links are visible below. Pick one active data home when ready."
        } else {
            "No live data-plane link saved yet. Configure Notion or Sheets in Settings → Data home, then Chat will cite it here."
        }
    } else {
        buildList {
            add(proofLabel.ifBlank { label })
            dataPlaneUrl.sourceUriLabel().takeIf { it.isNotBlank() }?.let { add(it) }
            externalId.takeIf { it.isNotBlank() }?.let { add("id ${it.take(18)}") }
            accountLabel.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" • ")
    }

private fun HouseholdUiMemory.aiVisibleContextSummary(): String = buildString {
    appendLine("Sent when relevant:")
    appendLine(
        "• Current message, page context, ${if (preferences.aiSkillOverride.isBlank()) "bundled" else "custom override"} WonderFood skill, and your Assistant instructions",
    )
    appendLine("• Current chat: up to 40 recent messages")
    appendLine("• Kitchen: ${inventory.size} items; ${inventory.take(8).joinToString { "${it.name} (${it.zone.label})" }.ifBlank { "empty" }}")
    appendLine("• Shopping: ${groceries.size} items; ${groceries.take(8).joinToString { it.name }.ifBlank { "empty" }}")
    appendLine("• Recipes: ${recipes.size}; ${recipes.take(6).joinToString { it.title }.ifBlank { "empty" }}")
    appendLine("• Meal logs/plans: ${mealLogs.size}/${mealPlanEntries.size}, plus recent food events and inventory ledger")
    append("• Saved diet, allergy, dislike, staple, cuisine, store, calorie, protein, and health-note fields")
}

private fun todayTitle(): String {
    val today = LocalDate.now()
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val monthDay = today.format(DateTimeFormatter.ofPattern("MMM d"))
    return "$weekday, $monthDay"
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun WonderFoodPreview() {
    WonderFoodTheme(dynamicColor = false, darkTheme = false) {
        WonderFoodScreen(
            state = WonderFoodUiState(
                memory = HouseholdUiMemory(
                    messages = listOf(
                        ChatMessage(1, ChatRole.ASSISTANT, "Tell me what you bought or ate.", 0),
                        ChatMessage(2, ChatRole.USER, "I bought eggs and spinach", 0),
                    ),
                ),
            ),
            onInputChange = {},
            onSend = {},
            onNewChat = {},
            onSectionSelected = {},
            onAcceptDraft = {},
            onRejectDraft = {},
            onDraftChange = {},
            onClearChatHistory = {},
            onDeleteInventory = {},
            onUpdateInventory = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
            onDeleteGrocery = {},
            onUpdateGrocery = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
            onMarkGroceryBought = {},
            onMarkCanonicalCartLinePurchased = {},
            onArchiveCanonicalCartLine = {},
            onAddCanonicalKitchenItemToCart = {},
            onArchiveCanonicalKitchenItem = {},
            onDeleteRecipe = {},
            onCookRecipe = {},
            onAddRecipeMissingToList = {},
            onUpdateRecipe = { _, _, _, _, _, _, _, _, _ -> },
            onPickRecipeImage = {},
            onDeleteMeal = {},
            onUpdateMeal = { _, _, _, _, _, _, _, _, _, _ -> },
            onUpdateMealPlan = { _, _, _, _, _ -> },
            onAddMealPlanEntry = { _, _, _, _ -> },
            onUpdateMealPlanEntry = { _, _, _, _, _, _ -> },
            onDeleteMealPlanEntry = {},
            onDeleteMealPlanEntries = {},
            onDeleteAllPlans = {},
            onUpdateReceipt = { _, _, _ -> },
            onExportMeal = {},
            onOpenDetail = {},
            onSearchQueryChange = {},
            onCloseDetail = {},
            onPreferencesChange = {},
            onSavePreferences = {},
            onAiConfigChange = {},
            onAiFallbackConfigChange = {},
            onSaveAiConfig = {},
            onCreateEncryptedBackup = {},
            onRestoreLatestEncryptedBackup = {},
            onGoogleOAuthClientIdChange = {},
            onConnectGoogle = {},
            onBackupToGoogleDrive = {},
            onRestoreFromGoogleDrive = {},
            onDisconnectGoogle = {},
            onExportCsv = {},
            onImportCsv = {},
            onTestAiConnection = { _ -> },
            onDeleteAllAppData = {},
            themeMode = WonderFoodThemeMode.SYSTEM,
            onThemeModeChange = {},
            onPickReceiptPhoto = {},
            onRecordVoiceNote = {},
            onRequestHealthConnect = {},
            onCreateManual = {},
            onLogWater = {},
            onUndo = {},
            onDismissFeedback = {},
            onChooseLocalBackend = {},
            onValidateGoogleSheetsBackend = {},
            onCreateGoogleSheetsBackend = {},
            onConnectNotionBackend = { _, _ -> },
            onConnectPostgresBackend = { _, _, _ -> },
            onSelectPendingBackend = {},
            onSelectLifeOsDomain = {},
            onDismissBackendOnboarding = {},
            onClearWorkspaceConflictInbox = {},
        )
    }
}
