package com.wonderfood.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
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
    persistProofPack(intent)
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
    persistProofPack(intent)
    voiceCommand = WonderFoodDeepLink.from(intent)
  }

  private fun persistProofPack(intent: Intent?) {
    val uri = intent?.data ?: return
    val isProofPack = intent.action == Intent.ACTION_VIEW &&
      uri.scheme.equals("wonderfood", ignoreCase = true) &&
      uri.host.equals("proof-pack", ignoreCase = true)
    val isHttpsProofPack = intent.action == Intent.ACTION_VIEW &&
      uri.scheme.equals("https", ignoreCase = true) &&
      uri.host.orEmpty().lowercase() in setOf("wonderfood.app", "www.wonderfood.app") &&
      uri.path.orEmpty().startsWith("/proof-pack")
    if (!isProofPack && !isHttpsProofPack) return

    val notionUrl = uri.getQueryParameter("notion")
      ?: uri.getQueryParameter("notion_url")
      ?: ""
    val sheetsUrl = uri.getQueryParameter("sheets")
      ?: uri.getQueryParameter("sheets_url")
      ?: uri.getQueryParameter("sheet")
      ?: ""
    if (!isTrustedNotionUrl(notionUrl) && !isTrustedSheetsUrl(sheetsUrl)) return

    getSharedPreferences(SHELL_PREFS_NAME, MODE_PRIVATE).edit {
      if (isTrustedNotionUrl(notionUrl)) putString(KEY_TEMPLATE_NOTION_URL, notionUrl)
      if (isTrustedSheetsUrl(sheetsUrl)) putString(KEY_TEMPLATE_SHEETS_URL, sheetsUrl)
      putString(KEY_BACKEND_SYNC_STATUS, "LifeOS Notion and Sheets template links saved on this device.")
    }
  }

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

  private fun readThemeMode(): WonderFoodThemeMode =
    runCatching {
      WonderFoodThemeMode.valueOf(
        getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
          .getString(KEY_THEME_MODE, WonderFoodThemeMode.SYSTEM.name)
          .orEmpty(),
      )
    }.getOrDefault(WonderFoodThemeMode.SYSTEM)

  private fun saveThemeMode(mode: WonderFoodThemeMode) {
    getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE).edit {
      putString(KEY_THEME_MODE, mode.name)
    }
  }

  companion object {
    private const val THEME_PREFS_NAME = "wonderfood_theme"
    private const val SHELL_PREFS_NAME = "wonderfood_shell"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_BACKEND_SYNC_STATUS = "backend_sync_status"
    private const val KEY_TEMPLATE_NOTION_URL = "template_notion_url"
    private const val KEY_TEMPLATE_SHEETS_URL = "template_sheets_url"
  }
}
