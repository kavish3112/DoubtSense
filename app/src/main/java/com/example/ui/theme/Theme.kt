package com.example.ui.theme

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

import com.example.util.ThemeMode

private val DoubtSenseDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Slate950,
    primaryContainer = DeepViolet,
    onPrimaryContainer = SoftCyan,
    secondary = NeonViolet,
    onSecondary = Color.White,
    secondaryContainer = Slate850,
    onSecondaryContainer = Slate100,
    tertiary = EmeraldSuccess,
    onTertiary = Slate950,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate850,
    onSurfaceVariant = Slate300,
    outline = Slate700,
    error = RoseError,
    onError = Color.White
)

private val DoubtSenseLightColorScheme = lightColorScheme(
    primary = DeepViolet,
    onPrimary = Color.White,
    primaryContainer = SoftCyan,
    onPrimaryContainer = Slate950,
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Slate900,
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Slate700,
    outline = Color(0xFF94A3B8),
    error = RoseError,
    onError = Color.White
)

private val DoubtSenseHighContrastColorScheme = darkColorScheme(
    primary = HighContrastYellow,
    onPrimary = HighContrastBlack,
    primaryContainer = HighContrastCard,
    onPrimaryContainer = HighContrastWhite,
    secondary = HighContrastCyan,
    onSecondary = HighContrastBlack,
    secondaryContainer = HighContrastSurface,
    onSecondaryContainer = HighContrastWhite,
    tertiary = HighContrastCyan,
    onTertiary = HighContrastBlack,
    background = HighContrastBlack,
    onBackground = HighContrastWhite,
    surface = HighContrastSurface,
    onSurface = HighContrastWhite,
    surfaceVariant = HighContrastCard,
    onSurfaceVariant = HighContrastWhite,
    outline = HighContrastWhite,
    error = Color(0xFFFF6B6B),
    onError = HighContrastBlack
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    darkTheme: Boolean = themeMode != ThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        themeMode == ThemeMode.HIGH_CONTRAST -> DoubtSenseHighContrastColorScheme
        themeMode == ThemeMode.LIGHT -> DoubtSenseLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DoubtSenseDarkColorScheme
        else -> DoubtSenseLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
