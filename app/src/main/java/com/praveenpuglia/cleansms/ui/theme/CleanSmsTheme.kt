package com.praveenpuglia.cleansms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.praveenpuglia.cleansms.R
import com.praveenpuglia.cleansms.AppSettings

@Composable
fun CleanSmsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Material You is always available on minSdk 33.
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val fontFamily = when (AppSettings.getFontFamily(context)) {
        AppSettings.FontFamily.SANS_SERIF -> FontFamily(Font(R.font.google_sans_flex))
        AppSettings.FontFamily.MONOSPACE -> FontFamily(Font(R.font.google_sans_code))
        AppSettings.FontFamily.SYSTEM -> FontFamily.SansSerif
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography().withFontFamily(fontFamily),
        content = content,
    )
}

private fun Typography.withFontFamily(fontFamily: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily),
)
