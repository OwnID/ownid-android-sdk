package com.ownid.sdk.compose.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.compose.OwnIdColors
import org.junit.Test

// Covers: UI-050
internal class ComponentColorValueContractTest {

    @Test
    fun iconButtonColorsCopyKeepsUnspecifiedValuesAndComparesByTokens() {
        val colors = OwnIdIconButtonColors(
            containerColor = Color(0xFF111111),
            borderColor = Color(0xFF222222),
            contentColor = Color(0xFF333333),
            disabledContainerColor = Color(0xFF444444),
            disabledContentColor = Color(0xFF555555)
        )

        val copied = colors.copy(
            containerColor = Color.Unspecified,
            borderColor = Color(0xFFABCDEF),
            disabledContentColor = Color.Unspecified
        )

        assertThat(copied.containerColor).isEqualTo(colors.containerColor)
        assertThat(copied.borderColor).isEqualTo(Color(0xFFABCDEF))
        assertThat(copied.disabledContentColor).isEqualTo(colors.disabledContentColor)
        assertThat(colors.copy()).isEqualTo(colors)
        assertThat(colors.copy().hashCode()).isEqualTo(colors.hashCode())
        assertThat(colors.copy(contentColor = Color.Magenta)).isNotEqualTo(colors)
    }

    @Test
    fun iconButtonDisabledBorderKeepsConfiguredColorWithDisabledAlpha() {
        val colors = OwnIdIconButtonColors(
            containerColor = Color.White,
            borderColor = Color(0xFF2468AC),
            contentColor = Color.Black,
            disabledContainerColor = Color.Gray,
            disabledContentColor = Color.DarkGray
        )

        assertThat(colors.borderColor(enabled = true)).isEqualTo(Color(0xFF2468AC))
        assertThat(colors.borderColor(enabled = false)).isEqualTo(Color(0xFF2468AC).copy(alpha = 0.12f))
    }

    @Test
    fun checkmarkColorsMapFromOwnIdPaletteAndCompareByTokens() {
        val ownIdColors = ownIdColors(
            checkmarkButton = Color(0xFF112233),
            checkmarkButtonBackground = Color(0xFF445566)
        )

        val colors = ownIdCheckmarkColorsFrom(ownIdColors)

        assertThat(colors.color).isEqualTo(Color(0xFF112233))
        assertThat(colors.backgroundColor).isEqualTo(Color(0xFF445566))
        assertThat(OwnIdCheckmarkColors(Color(0xFF112233), Color(0xFF445566))).isEqualTo(colors)
        assertThat(OwnIdCheckmarkColors(Color(0xFF112233), Color(0xFF445566)).hashCode()).isEqualTo(colors.hashCode())
        assertThat(OwnIdCheckmarkColors(Color.Magenta, Color(0xFF445566))).isNotEqualTo(colors)
    }

    @Test
    fun spinnerDefaultsExposePureStrokeValues() {
        assertThat(OwnIdSpinnerDefaults.strokeWidth).isEqualTo(3.0.dp)
        assertThat(OwnIdSpinnerDefaults.strokeCap).isEqualTo(StrokeCap.Round)
    }

    @Test
    fun userActionThrottlerRunsFirstActionAndDropsActionsInsideDelayWindow() {
        var now = 1_000L
        val actions = mutableListOf<Long>()
        val throttler = UserActionThrottler(throttleDelayMs = 300L, uptimeMillis = { now })

        throttler.processAction { actions += now }
        throttler.processAction { actions += now }

        now = 1_299L
        throttler.processAction { actions += now }

        now = 1_300L
        throttler.processAction { actions += now }

        assertThat(actions).containsExactly(1_000L, 1_300L).inOrder()
    }

    private fun ownIdColors(
        checkmarkButton: Color = Color.White,
        checkmarkButtonBackground: Color = Color.Black
    ): OwnIdColors =
        OwnIdColors(
            primary = Color.Red,
            onPrimary = Color.White,
            error = Color.Yellow,
            surface = Color.Black,
            onSurface = Color.White,
            onSurfaceVariant = Color.Gray,
            fieldBackground = Color.Transparent,
            progress = Color.Blue,
            progressTrack = Color.Cyan,
            iconButtonBackground = Color.LightGray,
            iconButtonBackgroundDisabled = Color.DarkGray,
            iconButtonBorder = Color.Green,
            checkmarkButton = checkmarkButton,
            checkmarkButtonBackground = checkmarkButtonBackground
        )
}
