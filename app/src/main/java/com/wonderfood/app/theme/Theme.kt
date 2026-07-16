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
    primary = HerbGreenContainer,
    onPrimary = HerbGreenDark,
    primaryContainer = HerbGreenDark,
    onPrimaryContainer = HerbGreenContainer,
    secondary = OliveContainer,
    onSecondary = Olive,
    secondaryContainer = Olive,
    onSecondaryContainer = OliveContainer,
    tertiary = TomatoContainer,
    onTertiary = Tomato,
    tertiaryContainer = Tomato,
    onTertiaryContainer = TomatoContainer,
    background = FoodSurfaceDark,
    surface = FoodSurfaceDark,
    surfaceVariant = Color(0xFF2C2820),
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
