package com.ownid.demo.gigya.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0070F2),
    onPrimary = Color.White,
    background = Color(0xFF18191B),
    surface = Color(0xFF222325),
    onSurface = Color(0xFFCED1CC),
    onSurfaceVariant = Color(0xFFCED1CC),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0070F2),
    onPrimary = Color.White,
    background = Color(0xFFF5F6F7),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF5B738B),
    onSurfaceVariant = Color(0xFF354A5F),
)

val ColorScheme.headerColor: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFF2A3743) else Color(0xFF354A5F)

val ColorScheme.textBackgroundColor: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFF222325) else Color(0xFFEAECEE)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val headerColor = MaterialTheme.colorScheme.headerColor
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = headerColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}