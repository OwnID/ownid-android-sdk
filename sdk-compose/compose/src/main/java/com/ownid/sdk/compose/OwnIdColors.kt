@file:JvmName("OwnIdColorTokens")

package com.ownid.sdk.compose

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse

/**
 * OwnID-specific color tokens used by OwnID Compose surfaces.
 *
 * These tokens complement Material [ColorScheme] with OwnID component roles that do not have a direct Material token.
 * Provide a complete palette when OwnID UI should match your app theme, use [OwnIdColors.from] to derive a palette from Material
 * colors, or start from [OwnIdDefaultColorsLight] or [OwnIdDefaultColorsDark] and override selected tokens with [copy].
 *
 * OwnID uses the supplied values as-is. When overriding paired colors such as [primary]/[onPrimary] or
 * [surface]/[onSurface], provide foreground colors that remain readable on the matching background.
 *
 * Token names describe their component roles. Prefer overriding paired foreground/background values together so SDK
 * text, icons, and controls remain readable.
 */
@Immutable
public class OwnIdColors(
    public val primary: Color,
    public val onPrimary: Color,
    public val error: Color,
    public val surface: Color,
    public val onSurface: Color,
    public val onSurfaceVariant: Color,
    public val fieldBackground: Color,

    public val progress: Color,
    public val progressTrack: Color,

    public val iconButtonBackground: Color,
    public val iconButtonBackgroundDisabled: Color,
    public val iconButtonBorder: Color,

    public val checkmarkButton: Color,
    public val checkmarkButtonBackground: Color,
) {
    /**
     * Returns a copy of this palette with optional overrides.
     *
     * Parameters default to current token values. Passing [Color.Unspecified] for a parameter keeps the current
     * value for that token.
     */
    public fun copy(
        primary: Color = this.primary,
        onPrimary: Color = this.onPrimary,
        error: Color = this.error,
        surface: Color = this.surface,
        onSurface: Color = this.onSurface,
        onSurfaceVariant: Color = this.onSurfaceVariant,
        fieldBackground: Color = this.fieldBackground,
        progress: Color = this.progress,
        progressTrack: Color = this.progressTrack,
        iconButtonBackground: Color = this.iconButtonBackground,
        iconButtonBackgroundDisabled: Color = this.iconButtonBackgroundDisabled,
        iconButtonBorder: Color = this.iconButtonBorder,
        checkmarkButton: Color = this.checkmarkButton,
        checkmarkButtonBackground: Color = this.checkmarkButtonBackground,
    ): OwnIdColors = OwnIdColors(
        primary.takeOrElse { this.primary },
        onPrimary.takeOrElse { this.onPrimary },
        error.takeOrElse { this.error },
        surface.takeOrElse { this.surface },
        onSurface.takeOrElse { this.onSurface },
        onSurfaceVariant.takeOrElse { this.onSurfaceVariant },
        fieldBackground.takeOrElse { this.fieldBackground },
        progress.takeOrElse { this.progress },
        progressTrack.takeOrElse { this.progressTrack },
        iconButtonBackground.takeOrElse { this.iconButtonBackground },
        iconButtonBackgroundDisabled.takeOrElse { this.iconButtonBackgroundDisabled },
        iconButtonBorder.takeOrElse { this.iconButtonBorder },
        checkmarkButton.takeOrElse { this.checkmarkButton },
        checkmarkButtonBackground.takeOrElse { this.checkmarkButtonBackground },
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OwnIdColors) return false
        return primary == other.primary &&
                onPrimary == other.onPrimary &&
                error == other.error &&
                surface == other.surface &&
                onSurface == other.onSurface &&
                onSurfaceVariant == other.onSurfaceVariant &&
                fieldBackground == other.fieldBackground &&
                progress == other.progress &&
                progressTrack == other.progressTrack &&
                iconButtonBackground == other.iconButtonBackground &&
                iconButtonBackgroundDisabled == other.iconButtonBackgroundDisabled &&
                iconButtonBorder == other.iconButtonBorder &&
                checkmarkButton == other.checkmarkButton &&
                checkmarkButtonBackground == other.checkmarkButtonBackground
    }

    override fun hashCode(): Int {
        var result = primary.hashCode()
        result = 31 * result + onPrimary.hashCode()
        result = 31 * result + error.hashCode()
        result = 31 * result + surface.hashCode()
        result = 31 * result + onSurface.hashCode()
        result = 31 * result + onSurfaceVariant.hashCode()
        result = 31 * result + fieldBackground.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + progressTrack.hashCode()
        result = 31 * result + iconButtonBackground.hashCode()
        result = 31 * result + iconButtonBackgroundDisabled.hashCode()
        result = 31 * result + iconButtonBorder.hashCode()
        result = 31 * result + checkmarkButton.hashCode()
        result = 31 * result + checkmarkButtonBackground.hashCode()
        return result
    }

    public companion object {
        /**
         * Derives OwnID color tokens from a Material [ColorScheme].
         *
         * The supplied scheme maps to OwnID tokens as follows: primary, on-primary, error, surface, on-surface,
         * on-surface-variant, and outline keep their matching Material roles; field background is transparent;
         * progress uses on-surface-variant with a 30% track; icon buttons use surface with an outline border. Checkmark
         * button tokens stay on the built-in OwnID success palette selected by [darkTheme].
         *
         * When [darkTheme] is not supplied, OwnID infers it from [colorScheme] by comparing [ColorScheme.surface] and
         * [ColorScheme.onSurface]. Pass an explicit value when using a non-standard color scheme whose surface/content
         * relationship does not represent the intended light or dark appearance.
         */
        @Composable
        public fun from(
            colorScheme: ColorScheme = MaterialTheme.colorScheme,
            darkTheme: Boolean = colorScheme.prefersDarkOwnIdDefaults(),
        ): OwnIdColors {
            val defaultColors = if (darkTheme) OwnIdDefaultColorsDark else OwnIdDefaultColorsLight
            return OwnIdColors(
                primary = colorScheme.primary,
                onPrimary = colorScheme.onPrimary,
                error = colorScheme.error,
                surface = colorScheme.surface,
                onSurface = colorScheme.onSurface,
                onSurfaceVariant = colorScheme.onSurfaceVariant,
                fieldBackground = Color.Transparent,
                progress = colorScheme.onSurfaceVariant,
                progressTrack = colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                iconButtonBackground = colorScheme.surface,
                iconButtonBackgroundDisabled = colorScheme.surface,
                iconButtonBorder = colorScheme.outline,
                checkmarkButton = defaultColors.checkmarkButton,
                checkmarkButtonBackground = defaultColors.checkmarkButtonBackground,
            )
        }
    }
}

internal fun ColorScheme.prefersDarkOwnIdDefaults(): Boolean = surface.luminance() < onSurface.luminance()

/** Default OwnID color tokens used as the SDK light palette and as a base for app overrides. */
public val OwnIdDefaultColorsLight: OwnIdColors = OwnIdColors(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    error = Color(0xFFB3261E),
    surface = Color.White,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF757575),
    fieldBackground = Color.Transparent,
    progress = Color(0xFF858585),
    progressTrack = Color(0xFF858585).copy(alpha = 0.3f),
    iconButtonBackground = Color.White,
    iconButtonBackgroundDisabled = Color.White,
    iconButtonBorder = Color(0xFFD0D0D0),
    checkmarkButton = Color.White,
    checkmarkButtonBackground = Color(0xFF36A41D)
)

/** Default OwnID color tokens used as the SDK dark palette and as a base for app overrides. */
public val OwnIdDefaultColorsDark: OwnIdColors = OwnIdColors(
    primary = Color(0xFF82B1FF),
    onPrimary = Color.Black,
    error = Color(0xFFF2B8B5),
    surface = Color(0xFF2A2831),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCAC4D0),
    fieldBackground = Color.Transparent,
    progress = Color(0xFFADADAD),
    progressTrack = Color(0xFFDFDFDF),
    iconButtonBackground = Color(0xFF2A3743),
    iconButtonBackgroundDisabled = Color(0xFF2A3743),
    iconButtonBorder = Color(0xFF2A3743),
    checkmarkButton = Color.Black,
    checkmarkButtonBackground = Color(0xFF66BB6A)
)

/**
 * [OwnIdColors] provided to OwnID Compose components in the current composition.
 *
 * OwnID themes replace this value for their descendants. Without an OwnID theme, the composition uses
 * [OwnIdDefaultColorsLight].
 */
public val LocalOwnIdColors: ProvidableCompositionLocal<OwnIdColors> = compositionLocalOf { OwnIdDefaultColorsLight }
