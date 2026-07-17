package com.wonderfood.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BaseTypography = Typography()

val Typography =
  Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = FontFamily.Default, letterSpacing = 0.sp),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = FontFamily.Default, letterSpacing = 0.sp),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = FontFamily.Default, letterSpacing = 0.sp),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = FontFamily.Default, letterSpacing = 0.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = FontFamily.Default, letterSpacing = 0.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = FontFamily.Default, letterSpacing = 0.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
  )
