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
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2F6F6A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8E2DA),
    surfaceContainer = Color(0xFFEAF2F0),
    surfaceContainerLow = Color(0xFFEAF2F0),
    background = Color(0xFFF3F0E6),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF8E9AAF),
    outline = Color(0xFFDCE2EB)
)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9BAFCE),
    onPrimary = Color(0xFF12203C),
    secondary = Color(0xFF9BAFCE),
    onSecondary = Color(0xFF12203C),
    secondaryContainer = Color(0xFF26344F),
    surfaceContainer = Color(0xFF141922),
    surfaceContainerLow = Color(0xFF141922),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF10141B),
    onSurface = Color(0xFFE7EBF2),
    onSurfaceVariant = Color(0xFFB8C7DD),
    outline = Color(0xFF4B5568)
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
