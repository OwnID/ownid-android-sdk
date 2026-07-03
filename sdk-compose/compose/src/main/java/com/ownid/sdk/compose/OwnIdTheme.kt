package com.ownid.sdk.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ownid.sdk.OwnId
import com.ownid.sdk.compose.OwnIdTheme.Companion.capture
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Theme value applied to OwnID UI.
 *
 * An OwnID theme carries the Material [ColorScheme], [Shapes], and [Typography] used by SDK content together with
 * [OwnIdColors], the OwnID-specific semantic color tokens. OwnID applies these values to descendants through
 * Material [MaterialTheme] and [LocalOwnIdColors].
 *
 * Pass a theme to an OwnID widget or operation view when that call site should use a stable theme value instead of the
 * surrounding composition. Use [OwnIdThemeBridge] when SDK-presented OwnID UI should reuse a theme captured from your
 * app's Compose hierarchy.
 *
 * @property colorScheme Material color scheme used inside OwnID Compose content.
 * @property shapes Material shapes used inside OwnID Compose content.
 * @property typography Material typography used inside OwnID Compose content.
 * @property colors OwnID-specific color tokens used by SDK components.
 */
@Immutable
public class OwnIdTheme(
    public val colorScheme: ColorScheme,
    public val shapes: Shapes,
    public val typography: Typography,
    public val colors: OwnIdColors,
) {
    /**
     * Returns a copy of this theme with optional replacements.
     *
     * Use this to adjust selected theme parts before passing the theme to an OwnID composable or SDK-presented UI.
     */
    public fun copy(
        colorScheme: ColorScheme = this.colorScheme,
        shapes: Shapes = this.shapes,
        typography: Typography = this.typography,
        colors: OwnIdColors = this.colors,
    ): OwnIdTheme = OwnIdTheme(colorScheme, shapes, typography, colors)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is OwnIdTheme && colorScheme == other.colorScheme && shapes == other.shapes && typography == other.typography && colors == other.colors
    }

    override fun hashCode(): Int = (((colorScheme.hashCode() * 31) + shapes.hashCode()) * 31 + typography.hashCode()) * 31 + colors.hashCode()

    override fun toString(): String = "OwnIdTheme(colorScheme=$colorScheme, shapes=$shapes, typography=$typography, colors=$colors)"

    public companion object {
        /**
         * Captures a theme from the current Compose appearance.
         *
         * By default, this reads [MaterialTheme.colorScheme], [MaterialTheme.shapes], and [MaterialTheme.typography] from
         * the current composition and derives [colors] with [OwnIdColors.from]. [darkTheme] controls light or dark OwnID
         * defaults for tokens that do not map directly to Material roles. Pass explicit values when OwnID UI should use a
         * different Material theme or OwnID palette than the surrounding screen.
         */
        @Composable
        public fun capture(
            colorScheme: ColorScheme = MaterialTheme.colorScheme,
            shapes: Shapes = MaterialTheme.shapes,
            typography: Typography = MaterialTheme.typography,
            darkTheme: Boolean = colorScheme.prefersDarkOwnIdDefaults(),
            colors: OwnIdColors = OwnIdColors.from(colorScheme, darkTheme),
        ): OwnIdTheme = OwnIdTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = typography,
            colors = colors,
        )
    }
}

/**
 * Captures and publishes an app theme value for SDK-presented OwnID UI.
 *
 * Add this bridge inside your app's themed Compose hierarchy when OwnID operation UI presented by the SDK should reuse
 * the same styling as the surrounding screen. The default [theme] is captured from the current Material theme and
 * updated again when Compose recomposes with different theme values.
 *
 * Embedded OwnID composables keep following their local composition theme unless a `theme` override is passed
 * explicitly to that composable.
 *
 * The last theme published for an [instanceName] stays active for future SDK-presented UI for that instance until
 * another bridge publishes a replacement theme or the instance is recreated.
 *
 * @param instanceName OwnID instance to update. Defaults to [InstanceName.Default].
 * @param theme Theme value to publish for SDK-presented OwnID UI. Defaults to [OwnIdTheme.capture].
 */
@Composable
@OptIn(InternalOwnIdAPI::class)
public fun OwnIdThemeBridge(
    instanceName: InstanceName = InstanceName.Default,
    theme: OwnIdTheme = capture(),
) {
    val containerFlow = remember(instanceName) { OwnId.getInstanceContainerStateFlow(instanceName) }
    val container by containerFlow.collectAsStateWithLifecycle()
    val themeStore = remember(container) { container?.getOrNull<OwnIdThemeStore>() }

    LaunchedEffect(themeStore, theme) {
        themeStore?.set(theme)
    }
}


internal class OwnIdThemeStore {
    val theme: MutableStateFlow<OwnIdTheme?> = MutableStateFlow(null)

    fun set(theme: OwnIdTheme) {
        this.theme.value = theme
    }
}

@Composable
internal fun ProvideOwnIdTheme(
    theme: OwnIdTheme,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = theme.colorScheme,
        shapes = theme.shapes,
        typography = theme.typography
    ) {
        CompositionLocalProvider(LocalOwnIdColors provides theme.colors) {
            content()
        }
    }
}

@Composable
internal fun defaultOwnIdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    shapes: Shapes = MaterialTheme.shapes,
    typography: Typography = MaterialTheme.typography,
): OwnIdTheme {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    return OwnIdTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = typography,
        colors = if (darkTheme) OwnIdDefaultColorsDark else OwnIdDefaultColorsLight
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = OwnIdDefaultColorsDark.primary,
    onPrimary = OwnIdDefaultColorsDark.onPrimary,
    surface = OwnIdDefaultColorsDark.surface,
    onSurface = OwnIdDefaultColorsDark.onSurface,
    onSurfaceVariant = OwnIdDefaultColorsDark.onSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = OwnIdDefaultColorsLight.primary,
    onPrimary = OwnIdDefaultColorsLight.onPrimary,
    surface = OwnIdDefaultColorsLight.surface,
    onSurface = OwnIdDefaultColorsLight.onSurface,
    onSurfaceVariant = OwnIdDefaultColorsLight.onSurfaceVariant
)
