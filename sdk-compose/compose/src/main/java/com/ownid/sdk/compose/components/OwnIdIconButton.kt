package com.ownid.sdk.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessStarted
import com.ownid.sdk.OwnId
import com.ownid.sdk.compose.LocalOwnIdColors
import com.ownid.sdk.compose.OwnIdColors
import com.ownid.sdk.compose.components.icon.Fingerprint
import com.ownid.sdk.compose.components.icon.Icons

/**
 * Displays the square OwnID icon button.
 *
 * The button shows [icon] when [isBusy] is `false` and [OwnIdSpinner] when [isBusy] is `true`. Busy state is visual
 * only; [enabled] controls whether the button accepts input. Enabled clicks are lifecycle-aware and ignore rapid
 * repeat taps.
 *
 * [contentDescription] is applied to the button semantics. The icon and spinner are decorative within that button,
 * so they do not expose separate content descriptions. The button also supplies button role semantics and a minimum
 * interactive touch target. Its visual content keeps a square aspect ratio within the height provided by the parent
 * or [modifier].
 *
 * @param onClick Callback invoked when the enabled button is activated.
 * @param contentDescription Accessibility label for the button action.
 * @param modifier Modifier for the button surface.
 * @param icon Defaults to [Fingerprint].
 * @param isBusy Whether to show progress instead of [icon]. Defaults to `false`.
 * @param enabled Whether the button accepts input. Defaults to `true`.
 * @param shape Container shape. Defaults to [OwnIdIconButtonDefaults.outlinedShape].
 * @param colors Color set for enabled and disabled states. Defaults to [OwnIdIconButtonDefaults.buttonColors] from
 *   [LocalOwnIdColors].
 * @param border Optional border. Defaults to [OwnIdIconButtonDefaults.buttonBorder] using [enabled] or [isBusy] so a
 *   disabled busy button keeps the enabled border while showing progress.
 * @param interactionSource Interaction source to hoist state. Defaults to an internal remembered source.
 */
@Composable
public fun OwnIdIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = OwnId.Icons.Fingerprint,
    isBusy: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = OwnIdIconButtonDefaults.outlinedShape,
    colors: OwnIdIconButtonColors = OwnIdIconButtonDefaults.buttonColors(LocalOwnIdColors.current),
    border: BorderStroke? = OwnIdIconButtonDefaults.buttonBorder(enabled || isBusy, colors),
    interactionSource: MutableInteractionSource? = null,
) {
    val throttleOnClick = remember { UserActionThrottler() }

    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

    Surface(
        onClick = dropUnlessStarted { throttleOnClick.processAction { onClick() } },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        enabled = enabled,
        shape = shape,
        color = if (enabled) colors.containerColor else colors.disabledContainerColor,
        contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
        border = border,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isBusy,
                contentAlignment = Alignment.Center,
                label = "OwnIdIconButton.Spinner"
            ) {
                if (it) {
                    OwnIdSpinner(
                        modifier = Modifier
                            .fillMaxHeight(0.65f)
                            .aspectRatio(1f)
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Color configuration for [OwnIdIconButton].
 *
 * The default values come from [OwnIdColors] through [OwnIdIconButtonDefaults.buttonColors].
 */
@Immutable
public class OwnIdIconButtonColors(
    public val containerColor: Color,
    public val borderColor: Color,
    public val contentColor: Color,
    public val disabledContainerColor: Color,
    public val disabledContentColor: Color
) {

    @Stable
    internal fun borderColor(enabled: Boolean): Color = if (enabled) borderColor else borderColor.copy(alpha = 0.12f)

    /**
     * Returns a copy with optional overrides.
     *
     * Parameters default to current values. Passing [Color.Unspecified] for a parameter keeps the current value
     * for that token.
     */
    public fun copy(
        containerColor: Color = this.containerColor,
        borderColor: Color = this.borderColor,
        contentColor: Color = this.contentColor,
        disabledContainerColor: Color = this.disabledContainerColor,
        disabledContentColor: Color = this.disabledContentColor,
    ): OwnIdIconButtonColors =
        OwnIdIconButtonColors(
            containerColor.takeOrElse { this.containerColor },
            borderColor.takeOrElse { this.borderColor },
            contentColor.takeOrElse { this.contentColor },
            disabledContainerColor.takeOrElse { this.disabledContainerColor },
            disabledContentColor.takeOrElse { this.disabledContentColor },
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OwnIdIconButtonColors) return false

        if (containerColor != other.containerColor) return false
        if (borderColor != other.borderColor) return false
        if (contentColor != other.contentColor) return false
        if (disabledContainerColor != other.disabledContainerColor) return false
        if (disabledContentColor != other.disabledContentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + borderColor.hashCode()
        result = 31 * result + contentColor.hashCode()
        result = 31 * result + disabledContainerColor.hashCode()
        result = 31 * result + disabledContentColor.hashCode()
        return result
    }
}

/** Default shape, colors, and border values for [OwnIdIconButton]. */
public object OwnIdIconButtonDefaults {

    public val outlinedShape: Shape
        @Composable
        @ReadOnlyComposable
        get() = RoundedCornerShape(6.dp)

    @Composable
    @ReadOnlyComposable
    public fun buttonColors(ownIdColors: OwnIdColors): OwnIdIconButtonColors = OwnIdIconButtonColors(
        containerColor = ownIdColors.iconButtonBackground,
        borderColor = ownIdColors.iconButtonBorder,
        contentColor = ownIdColors.primary,
        disabledContainerColor = ownIdColors.iconButtonBackgroundDisabled,
        disabledContentColor = ownIdColors.primary.copy(alpha = 0.38f)
    )

    /** Returns a border that reduces its alpha for disabled state. */
    @Composable
    public fun buttonBorder(enabled: Boolean, colors: OwnIdIconButtonColors): BorderStroke {
        return remember(enabled, colors) { BorderStroke(1.0.dp, colors.borderColor(enabled)) }
    }
}
