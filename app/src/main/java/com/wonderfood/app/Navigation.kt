package com.wonderfood.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wonderfood.app.theme.WonderFoodThemeMode
import com.wonderfood.app.ui.main.MainScreen

@Composable
fun MainNavigation(
  themeMode: WonderFoodThemeMode,
  onThemeModeChange: (WonderFoodThemeMode) -> Unit,
  voiceCommand: WonderFoodVoiceCommand?,
  onVoiceCommandConsumed: (WonderFoodVoiceCommand) -> Unit,
) {
  MainScreen(
    themeMode = themeMode,
    onThemeModeChange = onThemeModeChange,
    voiceCommand = voiceCommand,
    onVoiceCommandConsumed = onVoiceCommandConsumed,
    modifier = Modifier,
  )
}
