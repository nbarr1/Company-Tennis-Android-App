package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = FrostedPrimaryDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = FrostedPrimaryContainerDark,
    onPrimaryContainer = FrostedOnPrimaryContainerDark,
    secondary = FrostedSecondary,
    onSecondary = Color(0xFF332D41),
    tertiary = FrostedTertiary,
    background = FrostedBackgroundDark,
    surface = FrostedSurfaceDark,
    surfaceVariant = FrostedSurfaceVariantDark,
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = FrostedOutline,
    outlineVariant = FrostedOutlineVariant,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = FrostedPrimary,
    onPrimary = Color.White,
    primaryContainer = FrostedPrimaryContainer,
    onPrimaryContainer = FrostedOnPrimaryContainer,
    secondary = FrostedSecondary,
    onSecondary = Color(0xFF1D1B20),
    tertiary = FrostedTertiary,
    background = FrostedBackgroundLight,
    surface = FrostedSurfaceLight,
    surfaceVariant = FrostedSurfaceVariantLight,
    onBackground = FrostedTextPrimary,
    onSurface = FrostedTextPrimary,
    onSurfaceVariant = FrostedTextSecondary,
    outline = FrostedOutline,
    outlineVariant = FrostedOutlineVariant,
    error = ErrorRed
)

@Composable
fun TennisLeagueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our handcrafted tennis brand theme by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
