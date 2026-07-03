package com.ownid.demo.ui

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

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2F6F6A),
    onPrimary = Color.White,
    secondary = Color(0xFF2F6F6A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E2DA),

    surfaceContainer = Color(0xFFEAF2F0),
    surfaceContainerLow = Color(0xFFEAF2F0),

    background = Color(0xFFF3F0E6),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF8E9AAF),
    outline = Color(0xFFDCE2EB),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF75BAB3),
    onPrimary = Color.Black,

    secondary = Color(0xFF75BAB3),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF12141B),
    onSecondaryContainer = Color(0xFF75BAB3),

    surfaceContainer = Color(0xFF12141B),
    surfaceContainerLow = Color(0xFF12141B),

    background = Color(0xFF0C0C12),
    onBackground = Color(0xFFE7EBF2),
    surface = Color(0xFF16171F),
    onSurface = Color(0xFFE7EBF2),
    onSurfaceVariant = Color(0xB3E7EBF2),
    outline = Color(0xFF2B2D39),
    outlineVariant = Color(0xFF2B2D39),
)

@Composable
fun DemoAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
        content = content
    )
}
