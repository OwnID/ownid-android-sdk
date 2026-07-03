package com.ownid.sdk.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
// Covers: UI-040, UI-060, UI-PRESENT-200
internal class OwnIdThemeColorValueContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultOwnIdColorPalettesExposeSourceOwnedTokenValues() {
        assertThat(OwnIdDefaultColorsLight.primary).isEqualTo(Color(0xFF1A73E8))
        assertThat(OwnIdDefaultColorsLight.onPrimary).isEqualTo(Color.White)
        assertThat(OwnIdDefaultColorsLight.error).isEqualTo(Color(0xFFB3261E))
        assertThat(OwnIdDefaultColorsLight.surface).isEqualTo(Color.White)
        assertThat(OwnIdDefaultColorsLight.onSurface).isEqualTo(Color.Black)
        assertThat(OwnIdDefaultColorsLight.onSurfaceVariant).isEqualTo(Color(0xFF757575))
        assertThat(OwnIdDefaultColorsLight.fieldBackground).isEqualTo(Color.Transparent)
        assertThat(OwnIdDefaultColorsLight.progress).isEqualTo(Color(0xFF858585))
        assertThat(OwnIdDefaultColorsLight.progressTrack).isEqualTo(Color(0xFF858585).copy(alpha = 0.3f))
        assertThat(OwnIdDefaultColorsLight.iconButtonBackground).isEqualTo(Color.White)
        assertThat(OwnIdDefaultColorsLight.iconButtonBackgroundDisabled).isEqualTo(Color.White)
        assertThat(OwnIdDefaultColorsLight.iconButtonBorder).isEqualTo(Color(0xFFD0D0D0))
        assertThat(OwnIdDefaultColorsLight.checkmarkButton).isEqualTo(Color.White)
        assertThat(OwnIdDefaultColorsLight.checkmarkButtonBackground).isEqualTo(Color(0xFF36A41D))

        assertThat(OwnIdDefaultColorsDark.primary).isEqualTo(Color(0xFF82B1FF))
        assertThat(OwnIdDefaultColorsDark.onPrimary).isEqualTo(Color.Black)
        assertThat(OwnIdDefaultColorsDark.error).isEqualTo(Color(0xFFF2B8B5))
        assertThat(OwnIdDefaultColorsDark.surface).isEqualTo(Color(0xFF2A2831))
        assertThat(OwnIdDefaultColorsDark.onSurface).isEqualTo(Color.White)
        assertThat(OwnIdDefaultColorsDark.onSurfaceVariant).isEqualTo(Color(0xFFCAC4D0))
        assertThat(OwnIdDefaultColorsDark.fieldBackground).isEqualTo(Color.Transparent)
        assertThat(OwnIdDefaultColorsDark.progress).isEqualTo(Color(0xFFADADAD))
        assertThat(OwnIdDefaultColorsDark.progressTrack).isEqualTo(Color(0xFFDFDFDF))
        assertThat(OwnIdDefaultColorsDark.iconButtonBackground).isEqualTo(Color(0xFF2A3743))
        assertThat(OwnIdDefaultColorsDark.iconButtonBackgroundDisabled).isEqualTo(Color(0xFF2A3743))
        assertThat(OwnIdDefaultColorsDark.iconButtonBorder).isEqualTo(Color(0xFF2A3743))
        assertThat(OwnIdDefaultColorsDark.checkmarkButton).isEqualTo(Color.Black)
        assertThat(OwnIdDefaultColorsDark.checkmarkButtonBackground).isEqualTo(Color(0xFF66BB6A))
    }

    @Test
    fun ownIdColorsFromUsesExplicitDarkThemeForDefaultOwnIdTokens() {
        var explicitLight: OwnIdColors? = null
        var explicitDark: OwnIdColors? = null
        val appScheme = lightColorScheme(
            primary = Color(0xFF123456),
            onPrimary = Color(0xFFEFEFEF),
            surface = Color.White,
            onSurface = Color.Black
        )

        composeRule.setContent {
            val light = OwnIdColors.from(colorScheme = appScheme, darkTheme = false)
            val dark = OwnIdColors.from(colorScheme = appScheme, darkTheme = true)
            SideEffect {
                explicitLight = light
                explicitDark = dark
            }
        }

        composeRule.waitForIdle()
        val capturedExplicitLight = requireNotNull(explicitLight)
        val capturedExplicitDark = requireNotNull(explicitDark)

        assertThat(capturedExplicitLight.primary).isEqualTo(Color(0xFF123456))
        assertThat(capturedExplicitLight.checkmarkButton).isEqualTo(OwnIdDefaultColorsLight.checkmarkButton)
        assertThat(capturedExplicitLight.checkmarkButtonBackground).isEqualTo(OwnIdDefaultColorsLight.checkmarkButtonBackground)
        assertThat(capturedExplicitDark.primary).isEqualTo(Color(0xFF123456))
        assertThat(capturedExplicitDark.checkmarkButton).isEqualTo(OwnIdDefaultColorsDark.checkmarkButton)
        assertThat(capturedExplicitDark.checkmarkButtonBackground).isEqualTo(OwnIdDefaultColorsDark.checkmarkButtonBackground)
    }

    @Test
    fun ownIdColorsFromInfersDefaultOwnIdTokensFromSurfaceAndOnSurfaceLuminance() {
        var inferredLight: OwnIdColors? = null
        var inferredDark: OwnIdColors? = null
        val lightSignalingScheme = lightColorScheme(
            primary = Color(0xFF123456),
            surface = Color.White,
            onSurface = Color.Black
        )
        val darkSignalingScheme = lightColorScheme(
            primary = Color(0xFF654321),
            surface = Color.Black,
            onSurface = Color.White
        )

        composeRule.setContent {
            val light = OwnIdColors.from(colorScheme = lightSignalingScheme)
            val dark = OwnIdColors.from(colorScheme = darkSignalingScheme)
            SideEffect {
                inferredLight = light
                inferredDark = dark
            }
        }

        composeRule.waitForIdle()
        val capturedInferredLight = requireNotNull(inferredLight)
        val capturedInferredDark = requireNotNull(inferredDark)

        assertThat(capturedInferredLight.primary).isEqualTo(Color(0xFF123456))
        assertThat(capturedInferredLight.checkmarkButtonBackground).isEqualTo(OwnIdDefaultColorsLight.checkmarkButtonBackground)
        assertThat(capturedInferredDark.primary).isEqualTo(Color(0xFF654321))
        assertThat(capturedInferredDark.checkmarkButtonBackground).isEqualTo(OwnIdDefaultColorsDark.checkmarkButtonBackground)
    }

    @Test
    fun ownIdThemeCaptureUsesExplicitDarkThemeForDefaultOwnIdTokens() {
        var explicitLight: OwnIdTheme? = null
        var explicitDark: OwnIdTheme? = null
        val appScheme = lightColorScheme(
            primary = Color(0xFF345678),
            surface = Color.White,
            onSurface = Color.Black
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = appScheme) {
                val light = OwnIdTheme.capture(darkTheme = false)
                val dark = OwnIdTheme.capture(darkTheme = true)
                SideEffect {
                    explicitLight = light
                    explicitDark = dark
                }
            }
        }

        composeRule.waitForIdle()
        val capturedExplicitLight = requireNotNull(explicitLight)
        val capturedExplicitDark = requireNotNull(explicitDark)

        assertThat(capturedExplicitLight.colorScheme).isEqualTo(appScheme)
        assertThat(capturedExplicitLight.colors.checkmarkButtonBackground).isEqualTo(OwnIdDefaultColorsLight.checkmarkButtonBackground)
        assertThat(capturedExplicitDark.colorScheme).isEqualTo(appScheme)
        assertThat(capturedExplicitDark.colors.checkmarkButtonBackground).isEqualTo(OwnIdDefaultColorsDark.checkmarkButtonBackground)
    }

    @Test
    fun ownIdThemeCaptureInfersDefaultOwnIdTokensFromSurfaceAndOnSurfaceLuminance() {
        var inferredLight: OwnIdTheme? = null
        var inferredDark: OwnIdTheme? = null
        val lightSignalingScheme = lightColorScheme(
            primary = Color(0xFF456789),
            surface = Color.White,
            onSurface = Color.Black
        )
        val darkSignalingScheme = lightColorScheme(
            primary = Color(0xFF987654),
            surface = Color.Black,
            onSurface = Color.White
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = lightSignalingScheme) {
                val theme = OwnIdTheme.capture()
                SideEffect { inferredLight = theme }
            }
            MaterialTheme(colorScheme = darkSignalingScheme) {
                val theme = OwnIdTheme.capture()
                SideEffect { inferredDark = theme }
            }
        }

        composeRule.waitForIdle()
        val capturedInferredLight = requireNotNull(inferredLight)
        val capturedInferredDark = requireNotNull(inferredDark)

        assertThat(capturedInferredLight.colorScheme).isEqualTo(lightSignalingScheme)
        assertThat(capturedInferredLight.colors.checkmarkButtonBackground).isEqualTo(OwnIdDefaultColorsLight.checkmarkButtonBackground)
        assertThat(capturedInferredDark.colorScheme).isEqualTo(darkSignalingScheme)
        assertThat(capturedInferredDark.colors.checkmarkButtonBackground).isEqualTo(OwnIdDefaultColorsDark.checkmarkButtonBackground)
    }

    @Test
    fun ownIdColorsCopyKeepsUnspecifiedTokensAndOverridesExplicitTokens() {
        val original = OwnIdColors(
            primary = Color(0xFF101010),
            onPrimary = Color(0xFF202020),
            error = Color(0xFF303030),
            surface = Color(0xFF404040),
            onSurface = Color(0xFF505050),
            onSurfaceVariant = Color(0xFF606060),
            fieldBackground = Color(0xFF707070),
            progress = Color(0xFF808080),
            progressTrack = Color(0xFF909090),
            iconButtonBackground = Color(0xFFA0A0A0),
            iconButtonBackgroundDisabled = Color(0xFFB0B0B0),
            iconButtonBorder = Color(0xFFC0C0C0),
            checkmarkButton = Color(0xFFD0D0D0),
            checkmarkButtonBackground = Color(0xFFE0E0E0)
        )

        val copied = original.copy(
            primary = Color.Unspecified,
            onSurface = Color(0xFFABCDEF),
            iconButtonBorder = Color(0xFF123456),
            checkmarkButtonBackground = Color.Unspecified
        )

        assertThat(copied.primary).isEqualTo(original.primary)
        assertThat(copied.onSurface).isEqualTo(Color(0xFFABCDEF))
        assertThat(copied.iconButtonBorder).isEqualTo(Color(0xFF123456))
        assertThat(copied.checkmarkButtonBackground).isEqualTo(original.checkmarkButtonBackground)
        assertThat(copied.error).isEqualTo(original.error)
    }

    @Test
    fun ownIdColorsCompareByEveryToken() {
        val colors = OwnIdDefaultColorsLight

        assertThat(colors.copy()).isEqualTo(colors)
        assertThat(colors.copy().hashCode()).isEqualTo(colors.hashCode())
        assertThat(colors.copy(progress = Color.Magenta)).isNotEqualTo(colors)
    }

    @Test
    fun ownIdThemeCopyAndEqualityUsePureValueParts() {
        val lightScheme = lightColorScheme(primary = Color(0xFF13579B), onPrimary = Color.White)
        val darkScheme = darkColorScheme(primary = Color(0xFF2468AC), onPrimary = Color.Black)
        val shapes = Shapes()
        val typography = Typography()
        val colors = OwnIdDefaultColorsLight
        val theme = OwnIdTheme(lightScheme, shapes, typography, colors)

        val sameTheme = theme.copy()
        val changedTheme = theme.copy(colorScheme = darkScheme, colors = OwnIdDefaultColorsDark)

        assertThat(sameTheme).isEqualTo(theme)
        assertThat(sameTheme.hashCode()).isEqualTo(theme.hashCode())
        assertThat(changedTheme).isNotEqualTo(theme)
        assertThat(changedTheme.colorScheme).isEqualTo(darkScheme)
        assertThat(changedTheme.colors).isEqualTo(OwnIdDefaultColorsDark)
    }

    @Test
    fun themeStoreStartsEmptyAndPublishesLatestThemeValue() {
        val store = OwnIdThemeStore()
        val firstTheme = OwnIdTheme(lightColorScheme(primary = Color(0xFF123456)), Shapes(), Typography(), OwnIdDefaultColorsLight)
        val secondTheme = firstTheme.copy(colorScheme = darkColorScheme(primary = Color(0xFF654321)), colors = OwnIdDefaultColorsDark)

        assertThat(store.theme.value).isNull()

        store.set(firstTheme)
        assertThat(store.theme.value).isEqualTo(firstTheme)

        store.set(secondTheme)
        assertThat(store.theme.value).isEqualTo(secondTheme)
    }
}
