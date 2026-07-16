package com.wonderfood.app.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Inventory2
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wonderfood.app.ai.LiteLlmConfig
import com.wonderfood.app.WonderFoodVoiceCommand
import com.wonderfood.app.data.ChatMessage
import com.wonderfood.app.data.ChatAction
import com.wonderfood.app.data.ChatActionStatus
import com.wonderfood.app.data.ChatRole
import com.wonderfood.app.data.FoodDraft
import com.wonderfood.app.data.FoodEvent
import com.wonderfood.app.data.FoodEventType
import com.wonderfood.app.data.FoodMemory
import com.wonderfood.app.data.GroceryItem
import com.wonderfood.app.data.GroceryStatus
import com.wonderfood.app.data.InventoryAction
import com.wonderfood.app.data.InventoryItem
import com.wonderfood.app.data.InventoryTransaction
import com.wonderfood.app.data.MealLog
import com.wonderfood.app.data.MealPlan
import com.wonderfood.app.data.MealPlanEntry
import com.wonderfood.app.data.MealSlot
import com.wonderfood.app.data.FoodPreferences
import com.wonderfood.app.data.Recipe
import com.wonderfood.app.data.ReceiptCapture
import com.wonderfood.app.data.StorageZone
import com.wonderfood.app.theme.WonderFoodTheme
import com.wonderfood.app.theme.WonderFoodThemeMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

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
    val healthLauncher =
        rememberLauncherForActivityResult(viewModel.healthPermissionContract) {
            viewModel.refreshHealthStatus()
        }
    var pendingRecipeImageId by remember { mutableStateOf<Long?>(null) }
    val receiptPhotoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            viewModel.attachReceiptPhoto(uri)
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
                viewModel.sendVoiceNote(text)
            }
        }
    LaunchedEffect(voiceCommand?.id) {
        val command = voiceCommand ?: return@LaunchedEffect
        viewModel.handleVoiceCommand(command)
        onVoiceCommandConsumed(command)
    }

    WonderFoodScreen(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onSectionSelected = viewModel::selectSection,
        onAcceptDraft = viewModel::acceptDraft,
        onRejectDraft = viewModel::rejectDraft,
        onDeleteInventory = viewModel::deleteInventory,
        onDeleteGrocery = viewModel::deleteGrocery,
        onMarkGroceryBought = viewModel::markGroceryBought,
        onDeleteRecipe = viewModel::deleteRecipe,
        onCookRecipe = viewModel::cookRecipe,
        onAddRecipeMissingToList = viewModel::addMissingRecipeGroceries,
        onUpdateRecipe = viewModel::updateRecipe,
        onPickRecipeImage = { recipeId ->
            pendingRecipeImageId = recipeId
            recipePhotoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onDeleteMeal = viewModel::deleteMealLog,
        onExportLatestMeal = viewModel::exportLatestMeal,
        onOpenDetail = viewModel::openDetail,
        onCloseDetail = viewModel::closeDetail,
        onPreferencesChange = viewModel::onPreferencesChange,
        onSavePreferences = viewModel::savePreferences,
        onAiConfigChange = viewModel::onAiConfigChange,
        onSaveAiConfig = viewModel::saveAiConfig,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onPickReceiptPhoto = {
            receiptPhotoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onRecordVoiceNote = {
            voiceNoteLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell WonderFood what changed")
                },
            )
        },
        onRequestHealthConnect = { healthLauncher.launch(viewModel.healthWritePermissions) },
        modifier = modifier,
    )
}

@Composable
private fun WonderFoodScreen(
    state: WonderFoodUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSectionSelected: (FoodSection) -> Unit,
    onAcceptDraft: () -> Unit,
    onRejectDraft: () -> Unit,
    onDeleteInventory: (Long) -> Unit,
    onDeleteGrocery: (Long) -> Unit,
    onMarkGroceryBought: (Long) -> Unit,
    onDeleteRecipe: (Long) -> Unit,
    onCookRecipe: (Long) -> Unit,
    onAddRecipeMissingToList: (Long) -> Unit,
    onUpdateRecipe: (Long, String, String, String, Int?, Int?, String, String?) -> Unit,
    onPickRecipeImage: (Long) -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onExportLatestMeal: () -> Unit,
    onOpenDetail: (FoodDetailTarget) -> Unit,
    onCloseDetail: () -> Unit,
    onPreferencesChange: (FoodPreferences) -> Unit,
    onSavePreferences: () -> Unit,
    onAiConfigChange: (LiteLlmConfig) -> Unit,
    onSaveAiConfig: () -> Unit,
    themeMode: WonderFoodThemeMode,
    onThemeModeChange: (WonderFoodThemeMode) -> Unit,
    onPickReceiptPhoto: () -> Unit,
    onRecordVoiceNote: () -> Unit,
    onRequestHealthConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val showNavigationRail = maxWidth >= 720.dp
        val showCalendarPane = maxWidth >= 980.dp && state.section == FoodSection.PLAN && state.detailTarget == null
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
                    onSectionSelected = onSectionSelected,
                    onAcceptDraft = onAcceptDraft,
                    onRejectDraft = onRejectDraft,
                    onDeleteInventory = onDeleteInventory,
                    onDeleteGrocery = onDeleteGrocery,
                    onMarkGroceryBought = onMarkGroceryBought,
                    onDeleteRecipe = onDeleteRecipe,
                    onCookRecipe = onCookRecipe,
                    onAddRecipeMissingToList = onAddRecipeMissingToList,
                    onUpdateRecipe = onUpdateRecipe,
                    onPickRecipeImage = onPickRecipeImage,
                    onDeleteMeal = onDeleteMeal,
                    onExportLatestMeal = onExportLatestMeal,
                    onOpenDetail = onOpenDetail,
                    onCloseDetail = onCloseDetail,
                    onPreferencesChange = onPreferencesChange,
                    onSavePreferences = onSavePreferences,
                    onAiConfigChange = onAiConfigChange,
                    onSaveAiConfig = onSaveAiConfig,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onPickReceiptPhoto = onPickReceiptPhoto,
                    onRecordVoiceNote = onRecordVoiceNote,
                    onRequestHealthConnect = onRequestHealthConnect,
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
                onSectionSelected = onSectionSelected,
                onAcceptDraft = onAcceptDraft,
                onRejectDraft = onRejectDraft,
                onDeleteInventory = onDeleteInventory,
                onDeleteGrocery = onDeleteGrocery,
                onMarkGroceryBought = onMarkGroceryBought,
                onDeleteRecipe = onDeleteRecipe,
                onCookRecipe = onCookRecipe,
                onAddRecipeMissingToList = onAddRecipeMissingToList,
                onUpdateRecipe = onUpdateRecipe,
                onPickRecipeImage = onPickRecipeImage,
                onDeleteMeal = onDeleteMeal,
                onExportLatestMeal = onExportLatestMeal,
                onOpenDetail = onOpenDetail,
                onCloseDetail = onCloseDetail,
                onPreferencesChange = onPreferencesChange,
                onSavePreferences = onSavePreferences,
                onAiConfigChange = onAiConfigChange,
                onSaveAiConfig = onSaveAiConfig,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onPickReceiptPhoto = onPickReceiptPhoto,
                onRecordVoiceNote = onRecordVoiceNote,
                onRequestHealthConnect = onRequestHealthConnect,
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
                selected = selected == section,
                onClick = { onSectionSelected(section) },
                icon = { Icon(section.icon, contentDescription = section.label) },
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
    onSectionSelected: (FoodSection) -> Unit,
    onAcceptDraft: () -> Unit,
    onRejectDraft: () -> Unit,
    onDeleteInventory: (Long) -> Unit,
    onDeleteGrocery: (Long) -> Unit,
    onMarkGroceryBought: (Long) -> Unit,
    onDeleteRecipe: (Long) -> Unit,
    onCookRecipe: (Long) -> Unit,
    onAddRecipeMissingToList: (Long) -> Unit,
    onUpdateRecipe: (Long, String, String, String, Int?, Int?, String, String?) -> Unit,
    onPickRecipeImage: (Long) -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onExportLatestMeal: () -> Unit,
    onOpenDetail: (FoodDetailTarget) -> Unit,
    onCloseDetail: () -> Unit,
    onPreferencesChange: (FoodPreferences) -> Unit,
    onSavePreferences: () -> Unit,
    onAiConfigChange: (LiteLlmConfig) -> Unit,
    onSaveAiConfig: () -> Unit,
    themeMode: WonderFoodThemeMode,
    onThemeModeChange: (WonderFoodThemeMode) -> Unit,
    onPickReceiptPhoto: () -> Unit,
    onRecordVoiceNote: () -> Unit,
    onRequestHealthConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var secondaryPane by rememberSaveable { mutableStateOf<SecondaryPane?>(null) }
    var showAiCapture by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showAiCapture || secondaryPane != null || state.detailTarget != null) {
        when {
            showAiCapture -> showAiCapture = false
            secondaryPane != null -> secondaryPane = null
            else -> onCloseDetail()
        }
    }
    Box(
        modifier = modifier
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopBar(
                state = state,
                onSearchClick = { secondaryPane = SecondaryPane.SEARCH },
                onSettingsClick = { secondaryPane = SecondaryPane.SETTINGS },
                onAiClick = { showAiCapture = true },
            )
            if (state.section == FoodSection.KITCHEN || state.section == FoodSection.SHOP) {
                KitchenSnapshot(memory = state.memory)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    secondaryPane == SecondaryPane.SEARCH -> SearchContent(
                        memory = state.memory,
                        onBack = { secondaryPane = null },
                        onOpenDetail = { target ->
                            secondaryPane = null
                            onOpenDetail(target)
                        },
                    )
                    secondaryPane == SecondaryPane.SETTINGS -> PreferencesContent(
                        memory = state.memory,
                        preferences = state.preferencesForm,
                        aiConfig = state.aiConfigForm,
                        aiStatus = state.aiStatus,
                        healthStatus = state.healthStatus,
                        themeMode = themeMode,
                        onChange = onPreferencesChange,
                        onSave = onSavePreferences,
                        onAiConfigChange = onAiConfigChange,
                        onSaveAiConfig = onSaveAiConfig,
                        onThemeModeChange = onThemeModeChange,
                        onRequestHealthConnect = onRequestHealthConnect,
                        onBack = { secondaryPane = null },
                    )
                    state.detailTarget != null -> {
                    DetailPage(
                        target = state.detailTarget,
                        memory = state.memory,
                        onBack = onCloseDetail,
                        onDeleteInventory = onDeleteInventory,
                        onDeleteGrocery = onDeleteGrocery,
                        onMarkGroceryBought = onMarkGroceryBought,
                        onDeleteRecipe = onDeleteRecipe,
                        onCookRecipe = onCookRecipe,
                        onAddRecipeMissingToList = onAddRecipeMissingToList,
                        onUpdateRecipe = onUpdateRecipe,
                        onPickRecipeImage = onPickRecipeImage,
                        onDeleteMeal = onDeleteMeal,
                        onExportLatestMeal = onExportLatestMeal,
                    )
                    }
                    else -> when (state.section) {
                    FoodSection.KITCHEN -> PantryContent(
                        items = state.memory.inventory,
                        onOpen = { item ->
                            onOpenDetail(FoodDetailTarget(FoodDetailKind.INVENTORY, id = item.id))
                        },
                        onDelete = onDeleteInventory,
                    )
                    FoodSection.SHOP -> GroceryContent(
                        items = state.memory.groceries,
                        onOpen = { item ->
                            onOpenDetail(FoodDetailTarget(FoodDetailKind.GROCERY, id = item.id))
                        },
                        onBought = onMarkGroceryBought,
                        onDelete = onDeleteGrocery,
                    )
                    FoodSection.TODAY -> TodayContent(
                        memory = state.memory,
                        onOpenDetail = onOpenDetail,
                        onDeleteMeal = onDeleteMeal,
                    )
                    FoodSection.PLAN -> PlanContent(
                        memory = state.memory,
                        showCalendar = showInlineCalendar,
                        onOpenDetail = onOpenDetail,
                    )
                    FoodSection.RECIPES -> RecipesContent(
                        memory = state.memory,
                        onOpen = { recipe ->
                            onOpenDetail(FoodDetailTarget(FoodDetailKind.RECIPE, id = recipe.id))
                        },
                        onDelete = onDeleteRecipe,
                        onCook = onCookRecipe,
                    )
                    }
                }
            }
            if (state.pendingDraft != null) {
                DraftCard(
                    draft = state.pendingDraft,
                    onAccept = onAcceptDraft,
                    onReject = onRejectDraft,
                )
            }
            if (showBottomNavigation) {
                FoodBottomNavigation(selected = state.section, onSelected = onSectionSelected)
            }
        }
        if (showAiCapture) {
            AiCaptureSheet(
                input = state.input,
                isWorking = state.isWorking,
                status = when {
                    state.isWorking -> "AI is reviewing this."
                    state.pendingDraft != null -> "Proposal ready. Close this tray to review."
                    state.voiceStatus.isNotBlank() -> state.voiceStatus
                    else -> "AI reviews before saving."
                },
                onInputChange = onInputChange,
                onSend = onSend,
                onPickReceiptPhoto = onPickReceiptPhoto,
                onRecordVoiceNote = onRecordVoiceNote,
                onDismiss = { showAiCapture = false },
            )
        }
    }
}

@Composable
private fun TopBar(
    state: WonderFoodUiState,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAiClick: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
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
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search WonderFood")
                }
                IconButton(onClick = onAiClick) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "Open AI capture")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Open settings")
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
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search WonderFood")
                    }
                    IconButton(onClick = onAiClick) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "Open AI capture")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Open settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceStatusCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
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
private fun KitchenSnapshot(memory: FoodMemory) {
    val snapshot = listOf(
        Triple("Fridge", memory.inventory.count { it.zone == StorageZone.FRIDGE }, MaterialTheme.colorScheme.primaryContainer),
        Triple("Freezer", memory.inventory.count { it.zone == StorageZone.FREEZER }, MaterialTheme.colorScheme.surfaceVariant),
        Triple("Pantry", memory.inventory.count { it.zone == StorageZone.PANTRY }, MaterialTheme.colorScheme.secondaryContainer),
        Triple("To buy", memory.groceries.count { it.status == GroceryStatus.NEEDED }, MaterialTheme.colorScheme.tertiaryContainer),
    )
    BoxWithConstraints {
        if (maxWidth < 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                snapshot.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, value, color) ->
                            SnapshotCard(label, value, color, Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(snapshot) { (label, value, color) ->
                    SnapshotCard(label, value, color)
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(min = 132.dp),
        shape = RoundedCornerShape(8.dp),
        color = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FoodBottomNavigation(selected: FoodSection, onSelected: (FoodSection) -> Unit) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        FoodSection.entries.forEach { section ->
            NavigationBarItem(
                selected = selected == section,
                onClick = { onSelected(section) },
                icon = { Icon(section.icon, contentDescription = section.label) },
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
        shape = RoundedCornerShape(8.dp),
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
        shape = RoundedCornerShape(8.dp),
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
            OutlinedButton(onClick = { onPrompt("Plan meals this week") }, shape = RoundedCornerShape(8.dp)) {
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
            shape = RoundedCornerShape(8.dp),
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
private fun DraftCard(draft: FoodDraft, onAccept: () -> Unit, onReject: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(draft.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(draft.summary, style = MaterialTheme.typography.bodyMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                draft.rows.forEach { row ->
                    Text("- $row", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Accept")
                }
                OutlinedButton(onClick = onReject, shape = RoundedCornerShape(8.dp)) { Text("Reject") }
            }
        }
    }
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
        EmptyState("No pantry memory yet.", "Tell chat what came home.")
        return
    }
    var query by remember { mutableStateOf("") }
    var zoneFilter by remember { mutableStateOf<StorageZone?>(null) }
    var categoryFilter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf("Recent") }
    var viewMode by rememberSaveable { mutableStateOf(DatabaseViewMode.GALLERY) }
    val categories = remember(items) { listOf("All") + items.map { it.category.ifBlank { "other" } }.distinct().sorted() }
    val visible = items
        .asSequence()
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true) }
        .filter { zoneFilter == null || it.zone == zoneFilter }
        .filter { categoryFilter == "All" || it.category.equals(categoryFilter, ignoreCase = true) }
        .let { sequence ->
            when (sort) {
                "Name" -> sequence.sortedBy { it.name.lowercase() }
                "Zone" -> sequence.sortedWith(compareBy<InventoryItem> { it.zone.ordinal }.thenBy { it.name.lowercase() })
                "Category" -> sequence.sortedWith(compareBy<InventoryItem> { it.category }.thenBy { it.name.lowercase() })
                else -> sequence.sortedByDescending { it.updatedAtMillis }
            }
        }
        .toList()
    if (viewMode == DatabaseViewMode.LIST) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            item {
                KitchenControlPanel(
                    query = query,
                    onQuery = { query = it },
                    zoneFilter = zoneFilter,
                    onZone = { zoneFilter = it },
                    categories = categories,
                    category = categoryFilter,
                    onCategory = { categoryFilter = it },
                    sort = sort,
                    onSort = { sort = it },
                    viewMode = viewMode,
                    onViewMode = { viewMode = it },
                    resultCount = visible.size,
                )
            }
            items(visible, key = { it.id }) { item ->
                InventoryCard(item = item, onOpen = { onOpen(item) })
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
                KitchenControlPanel(
                    query = query,
                    onQuery = { query = it },
                    zoneFilter = zoneFilter,
                    onZone = { zoneFilter = it },
                    categories = categories,
                    category = categoryFilter,
                    onCategory = { categoryFilter = it },
                    sort = sort,
                    onSort = { sort = it },
                    viewMode = viewMode,
                    onViewMode = { viewMode = it },
                    resultCount = visible.size,
                )
            }
            gridItems(visible, key = { it.id }) { item ->
                InventoryTile(item = item, onOpen = { onOpen(item) })
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
    sort: String,
    onSort: (String) -> Unit,
    viewMode: DatabaseViewMode,
    onViewMode: (DatabaseViewMode) -> Unit,
    resultCount: Int,
) {
    val chips = buildList {
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
    DatabaseToolbar(
        icon = "🥬",
        title = "Kitchen files",
        subtitle = "$resultCount visible",
        query = query,
        placeholder = "Search food, category, notes",
        onQuery = onQuery,
        viewMode = viewMode,
        onViewModeChange = onViewMode,
        chips = chips,
    )
}

@Composable
private fun GroceryContent(
    items: List<GroceryItem>,
    onOpen: (GroceryItem) -> Unit,
    onBought: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState("Nothing to buy.", "Ask chat for groceries or plan meals.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { item ->
            GroceryCard(
                item = item,
                onOpen = { onOpen(item) },
                onBought = { onBought(item.id) },
                onDelete = { onDelete(item.id) },
            )
        }
    }
}

@Composable
private fun TodayContent(
    memory: FoodMemory,
    onOpenDetail: (FoodDetailTarget) -> Unit,
    onDeleteMeal: (Long) -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    val todayMeals = memory.mealLogs.filter { it.loggedDateEpochDay == today }
    val todayPlanEntries = memory.mealPlanEntries.filter { it.dateEpochDay == today }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TodayDashboard(memory = memory, onOpenDetail = onOpenDetail)
        }
        if (todayPlanEntries.isEmpty() && todayMeals.isEmpty()) {
            item { EmptyState("Today is open.", "Log what happened, cook from the kitchen, or ask for a plan.") }
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
private fun PlanContent(
    memory: FoodMemory,
    showCalendar: Boolean,
    onOpenDetail: (FoodDetailTarget) -> Unit,
) {
    val plans = memory.mealPlans
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            PlanSummaryCard(memory = memory)
        }
        if (showCalendar) {
            item {
                CalendarPane(
                    memory = memory,
                    compact = true,
                    onOpenDay = { day ->
                        onOpenDetail(FoodDetailTarget(FoodDetailKind.DAY, epochDay = day.date.toEpochDay()))
                    },
                )
            }
        }
        if (plans.isEmpty()) {
            item { EmptyState("No meal plan yet.", "Create a draft plan from recipes and kitchen items.") }
        } else {
            item { SectionLabel("Plans") }
        }
        items(plans, key = { it.id }) { plan ->
            MealPlanCard(
                plan = plan,
                onOpen = { onOpenDetail(FoodDetailTarget(FoodDetailKind.PLAN, id = plan.id)) },
            )
        }
        item { SectionLabel("Plan versus actual") }
        items(calendarSlots(memory), key = { it.date.toEpochDay() }) { day ->
            CalendarDayCard(day = day, onOpen = { onOpenDetail(FoodDetailTarget(FoodDetailKind.DAY, epochDay = day.date.toEpochDay())) })
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
private fun PlanSummaryCard(memory: FoodMemory) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ObjectImage(image = "🗓️", color = MaterialTheme.colorScheme.surface, size = 56.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Meal plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${memory.mealPlans.size} plan${memory.mealPlans.size.pluralWord}, ${memory.mealPlanEntries.size} scheduled meal${memory.mealPlanEntries.size.pluralWord}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            MetricPill("🛒", "${memory.groceries.count { it.status == GroceryStatus.NEEDED }} to buy")
        }
    }
}

@Composable
private fun SearchContent(
    memory: FoodMemory,
    onBack: () -> Unit,
    onOpenDetail: (FoodDetailTarget) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val allResults = remember(memory) { memory.globalSearchResults() }
    val visible = remember(allResults, query) {
        val clean = query.trim()
        if (clean.isBlank()) {
            allResults.take(12)
        } else {
            allResults.filter { result ->
                result.title.contains(clean, ignoreCase = true) ||
                    result.subtitle.contains(clean, ignoreCase = true)
            }
        }
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
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search WonderFood") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
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
            SearchResultCard(result = result, onOpen = { onOpenDetail(result.target) })
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResult, onOpen: () -> Unit) {
    MemoryCard(
        title = result.title,
        subtitle = result.subtitle,
        accent = MaterialTheme.colorScheme.surfaceVariant,
        image = result.image,
        onOpen = onOpen,
    )
}

@Composable
private fun TodayDashboard(memory: FoodMemory, onOpenDetail: (FoodDetailTarget) -> Unit) {
    val today = LocalDate.now().toEpochDay()
    val todayMeals = memory.mealLogs.filter { it.loggedDateEpochDay == today }
    val todayPlanEntries = memory.mealPlanEntries.filter { it.dateEpochDay == today }
    val calories = todayMeals.sumOf { it.calories }
    val protein = todayMeals.sumOf { it.proteinGrams }.toInt()
    val calorieGoal = memory.preferences.calorieGoal.firstNumberOrNull()
    val proteinGoal = memory.preferences.proteinGoal.firstNumberOrNull()
    val todayEvents = memory.events.filter { it.startedAtMillis.toLocalDate().toEpochDay() == today }
    val waterMl = todayEvents
        .filter { it.type == FoodEventType.WATER && it.unit == "ml" }
        .sumOf { it.amount ?: 0.0 }
        .toInt()
    val shopCount = todayEvents.count { it.type == FoodEventType.SHOP || it.type == FoodEventType.GROCERY_PURCHASE }
    val cookCount = todayEvents.count { it.type == FoodEventType.COOK }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ObjectImage(image = "🍽️", color = MaterialTheme.colorScheme.surface, size = 56.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (todayPlanEntries.isEmpty()) "Ask AI for a plan or log what happened."
                        else "${todayPlanEntries.size} planned meal${todayPlanEntries.size.pluralWord}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                MetricPill("🔥", if (calorieGoal == null) "$calories kcal" else "$calories/$calorieGoal")
                MetricPill("💪", if (proteinGoal == null) "${protein}g" else "$protein/${proteinGoal}g")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricPill("💧", "${waterMl / 1000.0} L")
                MetricPill("🛒", "$shopCount shop")
                MetricPill("🍳", "$cookCount cook")
                MetricPill("🧾", "${memory.inventoryTransactions.count { it.occurredDateEpochDay == today }} ledger")
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
                            }
                        },
                    )
                }
            }
            if (memory.actions.isNotEmpty() || memory.inventoryTransactions.isNotEmpty()) {
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
                shape = RoundedCornerShape(8.dp),
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
                shape = RoundedCornerShape(8.dp),
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
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
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
    val hasData = planned != null || eaten != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasData) Modifier.clickable(onClick = onOpen) else Modifier),
        shape = RoundedCornerShape(8.dp),
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
    memory: FoodMemory,
    onOpen: (Recipe) -> Unit,
    onDelete: (Long) -> Unit,
    onCook: (Long) -> Unit,
) {
    val recipes = memory.recipes
    if (recipes.isEmpty()) {
        EmptyState("No personal recipes yet.", "Say `save recipe for ...` in chat.")
        return
    }
    var query by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf("Recent") }
    var viewMode by rememberSaveable { mutableStateOf(DatabaseViewMode.GALLERY) }
    val tags = remember(recipes) {
        listOf("All") + recipes.flatMap { it.tags.split(",") }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val visible = recipes
        .asSequence()
        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.ingredients.contains(query, ignoreCase = true) || it.tags.contains(query, ignoreCase = true) }
        .filter { tagFilter == "All" || it.tags.contains(tagFilter, ignoreCase = true) }
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
                    onOpen = { onOpen(recipe) },
                    onCook = { onCook(recipe.id) },
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
                    lastHad = memory.lastHad(recipe),
                    onOpen = { onOpen(recipe) },
                    onCook = { onCook(recipe.id) },
                )
            }
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
    memory: FoodMemory,
    preferences: FoodPreferences,
    aiConfig: LiteLlmConfig,
    aiStatus: String,
    healthStatus: String,
    themeMode: WonderFoodThemeMode,
    onChange: (FoodPreferences) -> Unit,
    onSave: () -> Unit,
    onAiConfigChange: (LiteLlmConfig) -> Unit,
    onSaveAiConfig: () -> Unit,
    onThemeModeChange: (WonderFoodThemeMode) -> Unit,
    onRequestHealthConnect: () -> Unit,
    onBack: () -> Unit,
) {
    var panel by remember { mutableStateOf(MorePanel.TASTE) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item {
            DetailShell(
                image = "⚙️",
                title = "Settings",
                subtitle = "Taste, AI, health, data, theme",
                onBack = onBack,
            ) {}
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Food OS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Taste, AI behavior, health, local data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoreStat("🥬", "${memory.inventory.size}", "items")
                        MoreStat("📖", "${memory.recipes.size}", "recipes")
                        MoreStat("🍽️", "${memory.mealLogs.size}", "logs")
                        MoreStat("✅", "${memory.actions.count { it.status == ChatActionStatus.ACCEPTED }}", "actions")
                    }
                }
            }
        }
        item {
            MorePanelPicker(selected = panel, onSelected = { panel = it })
        }
        when (panel) {
            MorePanel.TASTE -> {
                item {
                    SettingsGroupCard(
                        icon = Icons.Rounded.Restaurant,
                        title = "Taste profile",
                        subtitle = "What AI should prefer before it plans or logs",
                        action = {
                            Button(onClick = onSave, shape = RoundedCornerShape(8.dp)) {
                                Text("Save")
                            }
                        },
                    ) {
                        PreferenceChipEditor(
                            label = "Diet style",
                            value = preferences.dietStyle,
                            suggestions = listOf("high protein", "vegetarian-ish", "meal prep", "low carb", "South Indian"),
                            placeholder = "Add diet style",
                            onValue = { onChange(preferences.copy(dietStyle = it)) },
                        )
                        PreferenceChipEditor(
                            label = "Preferred staples",
                            value = preferences.preferredStaples,
                            suggestions = listOf("ragi", "rice", "Greek yogurt", "rajma", "chickpeas", "paneer", "tortillas", "oats"),
                            placeholder = "Add staple",
                            onValue = { onChange(preferences.copy(preferredStaples = it)) },
                        )
                        PreferenceChipEditor(
                            label = "Preferred cuisines",
                            value = preferences.preferredCuisines,
                            suggestions = listOf("South Indian", "Indian", "Mediterranean", "Mexican", "high-protein bowls"),
                            placeholder = "Add cuisine",
                            onValue = { onChange(preferences.copy(preferredCuisines = it)) },
                        )
                    }
                }
                item {
                    SettingsGroupCard(
                        icon = Icons.Rounded.HealthAndSafety,
                        title = "Constraints",
                        subtitle = "Hard stops and softer avoids",
                    ) {
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
                    }
                }
            }
            MorePanel.AI -> {
                item {
                    SettingsGroupCard(
                        icon = Icons.AutoMirrored.Rounded.Chat,
                        title = "AI behavior",
                        subtitle = aiStatus,
                        action = {
                            OutlinedButton(onClick = onSaveAiConfig, shape = RoundedCornerShape(8.dp)) {
                                Text("Save AI")
                            }
                        },
                    ) {
                        PreferenceTextField(
                            label = "AI prompt rules",
                            value = preferences.customAiInstructions,
                            placeholder = "Use my kitchen first. Show missing groceries. Ask before reducing quantities...",
                            minLines = 4,
                            onValue = { onChange(preferences.copy(customAiInstructions = it)) },
                        )
                        PreferenceTextField(
                            label = "LiteLLM URL",
                            value = aiConfig.baseUrl,
                            placeholder = "https://...",
                            onValue = { onAiConfigChange(aiConfig.copy(baseUrl = it)) },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreferenceTextField(
                                label = "Model",
                                value = aiConfig.model,
                                placeholder = "gpt-5.4-mini",
                                modifier = Modifier.weight(1f),
                                onValue = { onAiConfigChange(aiConfig.copy(model = it)) },
                            )
                        }
                        OutlinedTextField(
                            value = aiConfig.apiKey,
                            onValueChange = { onAiConfigChange(aiConfig.copy(apiKey = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API key") },
                            placeholder = { Text("Stored only on this device") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            }
            MorePanel.VOICE -> {
                item {
                    SettingsGroupCard(
                        icon = Icons.Rounded.Mic,
                        title = "Voice and Google",
                        subtitle = "Direct actions first, AI voice notes second",
                    ) {
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
                            "Assistant deep links now route to direct app actions. Low-risk actions like water and shopping events log immediately; cooking opens or records the matching recipe flow.",
                        )
                        DetailSection(
                            "In-app mic",
                            "Use the mic beside the composer when you want a voice note sent to AI for extraction instead of typing.",
                        )
                    }
                }
            }
            MorePanel.HEALTH -> {
                item {
                    SettingsGroupCard(
                        icon = Icons.Rounded.HealthAndSafety,
                        title = "Goals and Health Connect",
                        subtitle = healthStatus,
                        action = {
                            OutlinedButton(onClick = onRequestHealthConnect, shape = RoundedCornerShape(8.dp)) {
                                Text("Connect")
                            }
                        },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreferenceTextField(
                                label = "Calories",
                                value = preferences.calorieGoal,
                                placeholder = "2200/day",
                                modifier = Modifier.weight(1f),
                                onValue = { onChange(preferences.copy(calorieGoal = it)) },
                            )
                            PreferenceTextField(
                                label = "Protein",
                                value = preferences.proteinGoal,
                                placeholder = "150g/day",
                                modifier = Modifier.weight(1f),
                                onValue = { onChange(preferences.copy(proteinGoal = it)) },
                            )
                        }
                        PreferenceTextField(
                            label = "Health notes",
                            value = preferences.healthNotes,
                            placeholder = "training days, digestion, sodium, caffeine...",
                            minLines = 3,
                            onValue = { onChange(preferences.copy(healthNotes = it)) },
                        )
                    }
                }
            }
            MorePanel.DATA -> {
                item {
                    SettingsGroupCard(
                        icon = Icons.Rounded.Inventory2,
                        title = "Local food memory",
                        subtitle = "SQLite on this phone. No account required.",
                    ) {
                        VisualFactGrid(
                            facts = listOf(
                                "🥬" to "${memory.inventory.size} pantry/fridge/freezer",
                                "🛒" to "${memory.groceries.count { it.status == GroceryStatus.NEEDED }} to buy",
                                "📖" to "${memory.recipes.size} recipes",
                                "🍽️" to "${memory.mealLogs.size} meal logs",
                                "🧾" to "${memory.inventoryTransactions.size} ledger rows",
                                "✅" to "${memory.actions.size} AI actions",
                            ),
                        )
                        DetailSection(
                            "What is tracked",
                            "Accepted AI actions, rejected drafts, receipt captures, shopping, meals, recipe matches, and kitchen transactions.",
                        )
                        DetailSection(
                            "Next data upgrade",
                            "Encrypted export/import and Android backup should come before this becomes daily-driver data.",
                        )
                        DetailSection(
                            "Pantry deductions",
                            "Cooking records ingredient usage in the ledger, but does not blindly reduce quantities yet. Silent wrong deductions would make the kitchen memory feel untrustworthy.",
                        )
                    }
                }
            }
            MorePanel.APPEARANCE -> {
                item {
                    SettingsGroupCard(
                        icon = Icons.Rounded.Settings,
                        title = "Appearance",
                        subtitle = "Use system by default, override when needed",
                    ) {
                        ThemeModeSelector(selected = themeMode, onSelected = onThemeModeChange)
                    }
                }
            }
            MorePanel.STORES -> {
                item {
                    SettingsGroupCard(
                        icon = Icons.Rounded.Storefront,
                        title = "Stores and brands",
                        subtitle = "Guide grocery suggestions",
                        action = {
                            Button(onClick = onSave, shape = RoundedCornerShape(8.dp)) {
                                Text("Save")
                            }
                        },
                    ) {
                        PreferenceChipEditor(
                            label = "Preferred stores / brands",
                            value = preferences.preferredStores,
                            suggestions = listOf("Costco", "Trader Joe's", "Indian grocery store", "Whole Foods", "Walmart"),
                            placeholder = "Add store",
                            onValue = { onChange(preferences.copy(preferredStores = it)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreStat(icon: String, value: String, label: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MorePanelPicker(selected: MorePanel, onSelected: (MorePanel) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MorePanel.entries.forEach { panel ->
            Surface(
                modifier = Modifier.widthIn(min = 148.dp).height(72.dp).clickable { onSelected(panel) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected == panel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(panel.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(panel.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(panel.caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

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
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
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

private enum class MorePanel(val label: String, val caption: String, val icon: ImageVector) {
    TASTE("Taste", "preferences", Icons.Rounded.Restaurant),
    VOICE("Voice", "Google + mic", Icons.Rounded.Mic),
    HEALTH("Health", "goals + sync", Icons.Rounded.HealthAndSafety),
    AI("AI", "provider + prompts", Icons.AutoMirrored.Rounded.Chat),
    DATA("Data", "local memory", Icons.Rounded.Inventory2),
    STORES("Stores", "shopping prefs", Icons.Rounded.Storefront),
    APPEARANCE("Theme", "light/dark", Icons.Rounded.Settings),
}

@Composable
private fun ThemeModeSelector(
    selected: WonderFoodThemeMode,
    onSelected: (WonderFoodThemeMode) -> Unit,
) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
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
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
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
                    shape = RoundedCornerShape(8.dp),
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
        maxLines = if (minLines > 1) 6 else 2,
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun CalendarPane(
    memory: FoodMemory,
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
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
                shape = RoundedCornerShape(8.dp),
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
                    shape = RoundedCornerShape(8.dp),
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
    memory: FoodMemory,
    onBack: () -> Unit,
    onDeleteInventory: (Long) -> Unit,
    onDeleteGrocery: (Long) -> Unit,
    onMarkGroceryBought: (Long) -> Unit,
    onDeleteRecipe: (Long) -> Unit,
    onCookRecipe: (Long) -> Unit,
    onAddRecipeMissingToList: (Long) -> Unit,
    onUpdateRecipe: (Long, String, String, String, Int?, Int?, String, String?) -> Unit,
    onPickRecipeImage: (Long) -> Unit,
    onDeleteMeal: (Long) -> Unit,
    onExportLatestMeal: () -> Unit,
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
                            onDelete = onDeleteInventory,
                        )
                    }
                }
                FoodDetailKind.GROCERY -> {
                    val item = memory.groceries.firstOrNull { it.id == target.id }
                    if (item == null) MissingDetail(onBack) else GroceryDetail(item, onBack, onMarkGroceryBought, onDeleteGrocery)
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
                            onExport = onExportLatestMeal,
                            onDelete = onDeleteMeal,
                        )
                    }
                }
                FoodDetailKind.PLAN -> {
                    val plan = memory.mealPlans.firstOrNull { it.id == target.id }
                    if (plan == null) {
                        MissingDetail(onBack)
                    } else {
                        PlanDetail(plan, memory.mealPlanEntries.filter { it.planId == plan.id }, onBack)
                    }
                }
                FoodDetailKind.DAY -> {
                    if (slot == null) MissingDetail(onBack) else DayDetail(slot, onBack)
                }
                FoodDetailKind.RECEIPT -> {
                    val receipt = memory.receipts.firstOrNull { it.id == target.id }
                    if (receipt == null) MissingDetail(onBack) else ReceiptDetail(receipt, onBack)
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
    onDelete: (Long) -> Unit,
) {
    DetailShell(
        image = foodEmoji(item.name),
        title = item.name,
        subtitle = "Stored in ${item.zone.label}",
        onBack = onBack,
    ) {
        VisualFactGrid(
            facts = listOf(
                "📍" to item.zone.label,
                "🏷️" to item.category,
                "⚖️" to item.quantity.ifBlank { "unknown" },
                "🔥" to nutritionSummary(item.calories, item.proteinGrams),
                "🧠" to item.source,
                "📅" to item.createdAtMillis.shortDate(),
                "⏳" to (item.expiresAtMillis?.shortDate() ?: "not set"),
            ),
        )
        if (item.notes.isNotBlank()) {
            DetailSection("📝 Notes", item.notes)
        }
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
    onBought: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    DetailShell(
        image = foodEmoji(item.name),
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.status == GroceryStatus.NEEDED) {
                Button(onClick = { onBought(item.id) }, shape = RoundedCornerShape(8.dp)) {
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
    memory: FoodMemory,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit,
    onCook: (Long) -> Unit,
    onAddMissing: (Long) -> Unit,
    onUpdate: (Long, String, String, String, Int?, Int?, String, String?) -> Unit,
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
            OutlinedButton(onClick = { editing = !editing }, shape = RoundedCornerShape(8.dp)) {
                Text(if (editing) "Done editing" else "Edit recipe")
            }
            OutlinedButton(onClick = { onPickImage(recipe.id) }, shape = RoundedCornerShape(8.dp)) {
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
                            )
                            editing = false
                        },
                        shape = RoundedCornerShape(8.dp),
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
            Button(onClick = { onCook(recipe.id) }, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Rounded.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cook + log")
            }
            if (match.need.isNotEmpty()) {
                OutlinedButton(onClick = { onAddMissing(recipe.id) }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Rounded.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add missing")
                }
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
    onDelete: (Long) -> Unit,
) {
    DetailShell(image = foodEmoji(meal.title), title = meal.title, subtitle = "Meal log", onBack = onBack) {
        VisualFactGrid(
            facts = listOf(
                "🍽️" to meal.mealSlot.label,
                "🔥" to "${meal.calories} kcal",
                "💪" to "${meal.proteinGrams.toInt()}g protein",
                "🍚" to "${meal.carbsGrams.toInt()}g carbs",
                "🫒" to "${meal.fatGrams.toInt()}g fat",
                "🧠" to meal.source,
            ),
        )
        DetailSection("🥬 Used from kitchen", meal.usedItemsText.ifBlank { "No pantry/freezer/fridge usage linked yet." })
        if (transactions.isNotEmpty()) {
            DetailSection(
                "🧾 Ledger",
                transactions.joinToString("\n") { "${it.action.label}: ${foodEmoji(it.itemName)} ${it.itemName} (${it.zone.label})" },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Rounded.HealthAndSafety, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sync")
            }
            ConfirmIconTextButton(Icons.Rounded.Delete, "Remove", "Remove ${meal.title}?", { onDelete(meal.id) })
        }
    }
}

@Composable
private fun PlanDetail(plan: MealPlan, entries: List<MealPlanEntry>, onBack: () -> Unit) {
    DetailShell(image = "🗓️", title = plan.title, subtitle = "Accepted meal plan", onBack = onBack) {
        val entryText = entries.joinToString("\n") { entry ->
            "${entry.dateEpochDay.epochDayShortDate()} ${entry.slot.label}: ${entry.title}" +
                (entry.calorieTarget?.let { "  ${it} kcal" } ?: "")
        }
        DetailSection("🍽 Meals", entryText.ifBlank { plan.daysText })
        DetailSection("🛒 Suggested groceries", plan.groceryHint)
    }
}

@Composable
private fun DayDetail(day: CalendarSlot, onBack: () -> Unit) {
    val bought = day.inventoryChanges.filter { it.action == InventoryAction.BOUGHT }
    val used = day.inventoryChanges.filter { it.action == InventoryAction.USED }
    val waterMl = day.events.filter { it.type == FoodEventType.WATER && it.unit == "ml" }.sumOf { it.amount ?: 0.0 }.toInt()
    val cookEvents = day.events.filter { it.type == FoodEventType.COOK }
    val shopEvents = day.events.filter { it.type == FoodEventType.SHOP || it.type == FoodEventType.GROCERY_PURCHASE }
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
        DetailSection(
            "🗓 Planned",
            day.planEntries.joinToString("\n") { "${it.slot.label}: ${foodEmoji(it.title)} ${it.title}  ${it.calorieTarget ?: 0} kcal target" }
                .ifBlank { day.plannedMeal ?: "No meal plan for this day." },
        )
        DetailSection("🍽 Ate", day.meals.joinToString("\n") { "${foodEmoji(it.title)} ${it.mealSlot.label}: ${it.title}  ${it.calories} kcal" }.ifBlank { "No meal logged." })
        DetailSection(
            "🛒 Shopping",
            bought.joinToString("\n") { "${foodEmoji(it.itemName)} ${it.itemName}" }
                .ifBlank { day.shopping.joinToString("\n") { "${foodEmoji(it.name)} ${it.name}" }.ifBlank { "No shopping captured." } },
        )
        DetailSection("📖 Recipes added", day.recipes.joinToString("\n") { "${foodEmoji(it.title)} ${it.title}" }.ifBlank { "No recipes added." })
        DetailSection(
            "🔢 Numbers",
            listOf(
                "Water: ${waterMl / 1000.0} L",
                "Cook events: ${cookEvents.size}",
                "Shopping events: ${shopEvents.size}",
                "Calories logged: ${day.meals.sumOf { it.calories }} kcal",
                "Protein logged: ${day.meals.sumOf { it.proteinGrams }.toInt()}g",
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
private fun ReceiptDetail(receipt: ReceiptCapture, onBack: () -> Unit) {
    DetailShell(image = "🧾", title = "Receipt photo", subtitle = receipt.status.name.lowercase(), onBack = onBack) {
        VisualFactGrid(
            facts = listOf(
                "📅" to receipt.createdAtMillis.shortDate(),
                "🧠" to receipt.status.name.lowercase(),
                "🖼️" to receipt.imageUri.takeLast(16),
            ),
        )
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
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
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
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
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
        if (image.startsWith("content://") || image.startsWith("file://")) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(image))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        } else {
            null
        }
    }
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(8.dp),
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
                Text(image.ifBlank { "🍽️" }, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun InventoryCard(item: InventoryItem, onOpen: () -> Unit) {
    MemoryCard(
        title = item.name,
        subtitle = listOf(
            item.quantity,
            item.zone.label,
            nutritionSummary(item.calories, item.proteinGrams).takeIf { item.calories != null },
        ).filterNotNull().filter { it.isNotBlank() }.joinToString("  "),
        accent = zoneColor(item.zone),
        image = foodEmoji(item.name),
        onOpen = onOpen,
        trailing = { SourceBadge(item.source) },
    )
}

@Composable
private fun InventoryTile(item: InventoryItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(172.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FoodImage(image = item.imageUri ?: foodEmoji(item.name), fallback = foodEmoji(item.name), color = zoneColor(item.zone), size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(item.category.ifBlank { "other" }, style = MaterialTheme.typography.labelMedium)
                ConfidenceBadge(if (item.calories == null) BadgeConfidence.UNKNOWN else BadgeConfidence.ESTIMATED)
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
        image = foodEmoji(item.name),
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
        subtitle = "🔥 ${meal.calories}  💪 ${meal.proteinGrams.toInt()}g  🍚 ${meal.carbsGrams.toInt()}g  🫒 ${meal.fatGrams.toInt()}g",
        accent = MaterialTheme.colorScheme.tertiaryContainer,
        image = foodEmoji(meal.title),
        onOpen = onOpen,
        trailing = { ConfirmIconTextButton(Icons.Rounded.Delete, "Remove", "Remove ${meal.title}?", onDelete) },
    )
}

@Composable
private fun RecipeCard(recipe: Recipe, onOpen: () -> Unit, onCook: () -> Unit) {
    MemoryCard(
        title = recipe.title,
        subtitle = recipe.ingredients.take(90),
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
    lastHad: Long?,
    onOpen: () -> Unit,
    onCook: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(210.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
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
                Text(recipe.tags.substringBefore(",").ifBlank { "recipe" }, style = MaterialTheme.typography.labelMedium)
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
    onOpen: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
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
private fun ConfirmIconTextButton(icon: ImageVector, label: String, title: String, onConfirm: () -> Unit) {
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
            text = { Text("This changes your local food memory. Keep it deliberate.") },
            confirmButton = {
                Button(
                    onClick = {
                        open = false
                        onConfirm()
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(label)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { open = false }, shape = RoundedCornerShape(8.dp)) {
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
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AiCaptureSheet(
    input: String,
    isWorking: Boolean,
    status: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickReceiptPhoto: () -> Unit,
    onRecordVoiceNote: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(onClick = onDismiss)
            .padding(12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ask WonderFood", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Food change, receipt, or voice note.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close AI capture")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onPickReceiptPhoto,
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Receipt")
                    }
                    OutlinedButton(
                        onClick = onRecordVoiceNote,
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Voice")
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "AI capture text" },
                    placeholder = { Text("Tell AI what changed...") },
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    shape = RoundedCornerShape(12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onSend,
                        enabled = input.isNotBlank() && !isWorking,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isWorking) "..." else "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun zoneColor(zone: StorageZone): Color =
    when (zone) {
        StorageZone.FRIDGE -> MaterialTheme.colorScheme.primaryContainer
        StorageZone.FREEZER -> MaterialTheme.colorScheme.surfaceVariant
        StorageZone.PANTRY -> MaterialTheme.colorScheme.secondaryContainer
    }

private fun FoodMemory.globalSearchResults(): List<SearchResult> =
    buildList {
        inventory.sortedByDescending { it.updatedAtMillis }.forEach { item ->
            add(
                SearchResult(
                    kind = "kitchen",
                    id = item.id,
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
                    id = item.id,
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
                    id = recipe.id,
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
                    id = meal.id,
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
                    id = plan.id,
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
                    id = receipt.id,
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
            FoodSection.TODAY -> "Plan, log, sync"
            FoodSection.KITCHEN -> "Fridge, freezer, pantry"
            FoodSection.PLAN -> "Future meals"
            FoodSection.RECIPES -> "Personal recipes"
            FoodSection.SHOP -> "Cart into kitchen"
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

private fun FoodMemory.lastHad(recipe: Recipe): Long? =
    mealLogs
        .filter { it.title.recipeMatches(recipe.title) || it.usedItemsText.recipeMatches(recipe.title) }
        .maxOfOrNull { it.loggedDateEpochDay }

private fun String.recipeMatches(other: String): Boolean {
    val left = lowercase()
    val right = other.lowercase()
    return left.contains(right) || right.contains(left)
}

private fun Recipe.kitchenMatch(memory: FoodMemory): RecipeKitchenMatch {
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
    val id: Long,
    val title: String,
    val subtitle: String,
    val image: String,
    val target: FoodDetailTarget,
)

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

private fun calendarSlots(memory: FoodMemory): List<CalendarSlot> {
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

private fun Long.shortDate(): String =
    toLocalDate().format(DateTimeFormatter.ofPattern("MMM d"))

private fun Long.epochDayShortDate(): String =
    LocalDate.ofEpochDay(this).format(DateTimeFormatter.ofPattern("MMM d"))

private fun foodEmoji(name: String): String {
    val text = name.lowercase()
    return when {
        "egg" in text -> "🥚"
        "yogurt" in text -> "🥣"
        "spinach" in text || "lettuce" in text || "greens" in text -> "🥬"
        "berry" in text || "berries" in text -> "🫐"
        "banana" in text -> "🍌"
        "apple" in text -> "🍎"
        "oat" in text -> "🌾"
        "rice" in text -> "🍚"
        "pasta" in text -> "🍝"
        "pizza" in text -> "🍕"
        "chicken" in text -> "🍗"
        "fish" in text || "salmon" in text -> "🐟"
        "beef" in text || "steak" in text -> "🥩"
        "bean" in text -> "🫘"
        "milk" in text -> "🥛"
        "cheese" in text -> "🧀"
        "bread" in text || "toast" in text -> "🍞"
        "sandwich" in text -> "🥪"
        "salad" in text -> "🥗"
        "soup" in text -> "🍲"
        "curry" in text -> "🍛"
        "bowl" in text -> "🥙"
        "coffee" in text -> "☕"
        "tea" in text -> "🍵"
        "smoothie" in text -> "🥤"
        "freezer" in text || "frozen" in text -> "❄️"
        else -> "🍽️"
    }
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
                memory = FoodMemory(
                    messages = listOf(
                        ChatMessage(1, ChatRole.ASSISTANT, "Tell me what you bought or ate.", 0),
                        ChatMessage(2, ChatRole.USER, "I bought eggs and spinach", 0),
                    ),
                ),
            ),
            onInputChange = {},
            onSend = {},
            onSectionSelected = {},
            onAcceptDraft = {},
            onRejectDraft = {},
            onDeleteInventory = {},
            onDeleteGrocery = {},
            onMarkGroceryBought = {},
            onDeleteRecipe = {},
            onCookRecipe = {},
            onAddRecipeMissingToList = {},
            onUpdateRecipe = { _, _, _, _, _, _, _, _ -> },
            onPickRecipeImage = {},
            onDeleteMeal = {},
            onExportLatestMeal = {},
            onOpenDetail = {},
            onCloseDetail = {},
            onPreferencesChange = {},
            onSavePreferences = {},
            onAiConfigChange = {},
            onSaveAiConfig = {},
            themeMode = WonderFoodThemeMode.SYSTEM,
            onThemeModeChange = {},
            onPickReceiptPhoto = {},
            onRecordVoiceNote = {},
            onRequestHealthConnect = {},
        )
    }
}
