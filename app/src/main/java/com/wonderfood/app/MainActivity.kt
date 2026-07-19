package com.wonderfood.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wonderfood.app.theme.WonderFoodTheme
import com.wonderfood.app.theme.WonderFoodThemeMode

class MainActivity : ComponentActivity() {
  private var voiceCommand by mutableStateOf<WonderFoodVoiceCommand?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    voiceCommand = WonderFoodDeepLink.from(intent)

    enableEdgeToEdge()
    setContent {
      var themeMode by remember { mutableStateOf(readThemeMode()) }
      WonderFoodTheme(themeMode = themeMode, dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
            themeMode = themeMode,
            onThemeModeChange = { mode ->
              saveThemeMode(mode)
              themeMode = mode
            },
            voiceCommand = voiceCommand,
            onVoiceCommandConsumed = { consumed ->
              if (voiceCommand?.id == consumed.id) voiceCommand = null
            },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    voiceCommand = WonderFoodDeepLink.from(intent)
  }

  private fun readThemeMode(): WonderFoodThemeMode =
    runCatching {
      WonderFoodThemeMode.valueOf(
        getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
          .getString(KEY_THEME_MODE, WonderFoodThemeMode.LIGHT.name)
          .orEmpty(),
      )
    }.getOrDefault(WonderFoodThemeMode.LIGHT)

  private fun saveThemeMode(mode: WonderFoodThemeMode) {
    getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE).edit {
      putString(KEY_THEME_MODE, mode.name)
    }
  }

  companion object {
    private const val THEME_PREFS_NAME = "wonderfood_theme"
    private const val KEY_THEME_MODE = "theme_mode"
  }
}
