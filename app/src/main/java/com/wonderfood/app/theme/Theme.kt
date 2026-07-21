package com.wonderfood.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF8BD7A8),
    onPrimary = Color(0xFF072A18),
    primaryContainer = Color(0xFF174F32),
    onPrimaryContainer = Color(0xFFE0F7E8),
    secondary = Color(0xFFE5DF95),
    onSecondary = Color(0xFF2F2D06),
    secondaryContainer = Color(0xFF474516),
    onSecondaryContainer = Color(0xFFF7F1B2),
    tertiary = Color(0xFFFFB59E),
    onTertiary = Color(0xFF5B1805),
    tertiaryContainer = Color(0xFF8D3218),
    onTertiaryContainer = Color(0xFFFFE3D8),
    background = Color(0xFF211E1A),
    onBackground = Color(0xFFF7EFE2),
    surface = Color(0xFF2A261F),
    onSurface = Color(0xFFF7EFE2),
    surfaceVariant = Color(0xFF383226),
    onSurfaceVariant = Color(0xFFE6DDCE),
    outline = Color(0xFFA69B8A),
    outlineVariant = Color(0xFF514A3D),
    inverseSurface = OatSurface,
    inverseOnSurface = Ink,
    inversePrimary = HerbGreen,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = HerbGreen,
    onPrimary = OatSurface,
    primaryContainer = HerbGreenContainer,
    onPrimaryContainer = OnHerbGreenContainer,
    secondary = Olive,
    onSecondary = OatSurface,
    secondaryContainer = OliveContainer,
    onSecondaryContainer = OnOliveContainer,
    tertiary = Tomato,
    onTertiary = OatSurface,
    tertiaryContainer = TomatoContainer,
    onTertiaryContainer = OnTomatoContainer,
    background = OatSurface,
    onBackground = Ink,
    surface = OatSurface,
    onSurface = Ink,
    surfaceVariant = OatSurfaceVariant,
    onSurfaceVariant = MutedInk,
    outline = OatOutline,
    outlineVariant = Color(0xFFC9D8C7),
    inverseSurface = Ink,
    inverseOnSurface = OatSurface,
    inversePrimary = HerbGreenContainer,
  )

enum class WonderFoodThemeMode {
  LIGHT,
  DARK,
  SYSTEM,
}

@Composable
fun WonderFoodTheme(
  darkTheme: Boolean? = null,
  themeMode: WonderFoodThemeMode = WonderFoodThemeMode.SYSTEM,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val useDarkTheme =
    darkTheme ?: when (themeMode) {
      WonderFoodThemeMode.LIGHT -> false
      WonderFoodThemeMode.DARK -> true
      WonderFoodThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      useDarkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
