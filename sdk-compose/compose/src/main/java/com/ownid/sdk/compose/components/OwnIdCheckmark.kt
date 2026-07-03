package com.ownid.sdk.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ownid.sdk.OwnId
import com.ownid.sdk.compose.LocalOwnIdColors
import com.ownid.sdk.compose.OwnIdColors
import com.ownid.sdk.compose.components.icon.Checkmark
import com.ownid.sdk.compose.components.icon.Icons

/**
 * Displays the circular OwnID success checkmark badge.
 *
 * The badge is decorative by default: the icon has no content description and should not be the only place where a
 * completed state is communicated. Add parent semantics or visible status text when completion needs an
 * accessibility announcement. Size is caller-controlled through [modifier]; the badge keeps a square aspect ratio.
 *
 * @param modifier Modifier for the badge container.
 * @param icon Badge icon asset. Defaults to [Checkmark].
 * @param colors Badge colors. Defaults to [OwnIdCheckmarkDefaults.colors] from [LocalOwnIdColors].
 */
@Composable
public fun OwnIdCheckmark(
    modifier: Modifier = Modifier,
    icon: ImageVector = OwnId.Icons.Checkmark,
    colors: OwnIdCheckmarkColors = OwnIdCheckmarkDefaults.colors(LocalOwnIdColors.current),
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.backgroundColor)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            tint = colors.color
        )
    }
}

/**
 * Color configuration for [OwnIdCheckmark].
 *
 * The default values come from [OwnIdColors] through [OwnIdCheckmarkDefaults.colors].
 */
@Immutable
public class OwnIdCheckmarkColors(
    public val color: Color,
    public val backgroundColor: Color
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is OwnIdCheckmarkColors && color == other.color && backgroundColor == other.backgroundColor)

    override fun hashCode(): Int = 31 * color.hashCode() + backgroundColor.hashCode()
}

/** Default color values for [OwnIdCheckmark]. */
public object OwnIdCheckmarkDefaults {
    @Composable
    @ReadOnlyComposable
    public fun colors(ownIdColors: OwnIdColors): OwnIdCheckmarkColors = ownIdCheckmarkColorsFrom(ownIdColors)
}

internal fun ownIdCheckmarkColorsFrom(ownIdColors: OwnIdColors): OwnIdCheckmarkColors =
    OwnIdCheckmarkColors(color = ownIdColors.checkmarkButton, backgroundColor = ownIdColors.checkmarkButtonBackground)
