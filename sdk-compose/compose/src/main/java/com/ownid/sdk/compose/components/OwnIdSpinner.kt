package com.ownid.sdk.compose.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ownid.sdk.compose.LocalOwnIdColors
import com.ownid.sdk.compose.OwnIdColors

/**
 * Displays an OwnID activity indicator.
 *
 * The spinner uses [LocalOwnIdColors] by default and is usually placed inside a button or loading container that
 * already owns the accessibility label. When used as standalone status UI, provide the surrounding content or
 * [modifier] semantics that should be announced. Size is entirely caller-controlled through [modifier].
 *
 * @param modifier Modifier for the progress indicator.
 * @param colors Source OwnID palette used by default color providers.
 * @param color Indicator color. Defaults to [OwnIdSpinnerDefaults.color] from [colors].
 * @param trackColor Track color. Defaults to [OwnIdSpinnerDefaults.trackColor] from [colors].
 * @param strokeWidth Defaults to [OwnIdSpinnerDefaults.strokeWidth].
 * @param strokeCap Defaults to [OwnIdSpinnerDefaults.strokeCap].
 */
@Composable
public fun OwnIdSpinner(
    modifier: Modifier = Modifier,
    colors: OwnIdColors = LocalOwnIdColors.current,
    color: Color = OwnIdSpinnerDefaults.color(colors),
    trackColor: Color = OwnIdSpinnerDefaults.trackColor(colors),
    strokeWidth: Dp = OwnIdSpinnerDefaults.strokeWidth,
    strokeCap: StrokeCap = OwnIdSpinnerDefaults.strokeCap
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth,
        trackColor = trackColor,
        strokeCap = strokeCap
    )
}

/** Default color and stroke values for [OwnIdSpinner]. */
public object OwnIdSpinnerDefaults {
    public val strokeWidth: Dp = 3.0.dp

    public val strokeCap: StrokeCap = StrokeCap.Round

    @Composable
    @ReadOnlyComposable
    public fun color(colors: OwnIdColors): Color = colors.progress

    @Composable
    @ReadOnlyComposable
    public fun trackColor(colors: OwnIdColors): Color = colors.progressTrack
}
