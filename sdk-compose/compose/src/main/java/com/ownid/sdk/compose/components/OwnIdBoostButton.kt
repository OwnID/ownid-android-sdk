package com.ownid.sdk.compose.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessStarted
import com.ownid.sdk.OwnId
import com.ownid.sdk.compose.OwnIdTheme
import com.ownid.sdk.compose.ProvideOwnIdTheme
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.capability.BoostWidgetStrings
import com.ownid.sdk.ui.capability.BoostWidgetStringsParams
import com.ownid.sdk.ui.capability.BoostWidgetStringsProvider
import kotlinx.coroutines.flow.filterNotNull

/**
 * Controls where the OwnID Boost button appears relative to the "or" separator.
 */
public enum class OwnIdBoostButtonPosition {
    /** Shows the OwnID Boost button before the "or" separator. */
    Start,

    /** Shows the OwnID Boost button after the "or" separator. */
    End
}

/**
 * Displays the OwnID Boost button UI.
 *
 * The component arranges an icon button, optional "or" separator, and optional completion checkmark. Use it when the
 * app owns the surrounding action but wants the standard OwnID button row, localized separator text, theme propagation,
 * and accessibility label handoff.
 *
 * The caller owns [onClick] and any work it starts. The default icon button invokes [onClick] only while enabled,
 * drops clicks when the lifecycle is not started, and ignores rapid repeat taps. By default the button is disabled
 * while [isBusy] is true. When [showSpinner] is true, [isBusy] is forwarded to the icon-button slot so the default
 * button replaces the icon with [OwnIdSpinner].
 *
 * [widgetStrings] supplies the visible separator text and the accessibility label passed to [iconButton]. Custom
 * icon-button slots must apply the received content description to the interactive element. The default checkmark
 * is decorative; expose completion through surrounding UI state when the app needs an accessibility announcement.
 *
 * The row sizes itself from its slots. The default [OwnIdIconButton] supplies a minimum interactive touch target;
 * custom slots should keep a comparable button size and semantics.
 *
 * @param onClick Callback invoked when the icon button is activated.
 * @param isBusy Whether the owning action is currently running.
 * @param modifier Modifier for the row container.
 * @param position Whether the OwnID Boost button appears before or after the "or" separator. Defaults to
 *   [OwnIdBoostButtonPosition.Start].
 * @param instanceName OwnID instance used to resolve default strings.
 * @param enabled Whether the icon button accepts input. Defaults to `!isBusy`.
 * @param finished Whether to show the completion checkmark overlay.
 * @param showSpinner Whether the icon-button slot receives a busy state when [isBusy] is true. Defaults to `true`.
 * @param theme OwnID theme for this button. Defaults to the current OwnID theme.
 * @param widgetStrings Explicit strings for this button row. Defaults to localized strings for [instanceName].
 * @param iconButton Interactive button slot. Defaults to [OwnIdIconButton].
 * @param checkmark Completion badge slot shown when [finished] is true. Defaults to [OwnIdCheckmark].
 * @param orText Optional "or" separator content. Pass `null` to hide it.
 */
@Composable
public fun OwnIdBoostButton(
    onClick: () -> Unit,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
    position: OwnIdBoostButtonPosition = OwnIdBoostButtonPosition.Start,
    instanceName: InstanceName = InstanceName.Default,
    enabled: Boolean = isBusy.not(),
    finished: Boolean = false,
    showSpinner: Boolean = true,
    theme: OwnIdTheme = OwnIdTheme.capture(),
    widgetStrings: BoostWidgetStrings = widgetStrings(instanceName),
    iconButton: @Composable (onClick: () -> Unit, contentDescription: String, isBusy: Boolean, enabled: Boolean) -> Unit =
        { iconButtonOnClick, contentDescription, iconButtonIsBusy, iconButtonEnabled ->
            OwnIdIconButton(iconButtonOnClick, contentDescription, isBusy = iconButtonIsBusy, enabled = iconButtonEnabled)
        },
    checkmark: @Composable AnimatedVisibilityScope.() -> Unit = {
        OwnIdCheckmark()
    },
    orText: @Composable (RowScope.(text: String) -> Unit)? = { text ->
        Text(text = text, maxLines = 1)
    }
) {
    val actionThrottler = remember { UserActionThrottler() }
    val throttledOnClick = dropUnlessStarted { actionThrottler.processAction { onClick() } }

    ProvideOwnIdTheme(theme) {
        Row(
            modifier = modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val boostButton: @Composable RowScope.() -> Unit = {
                val iconButtonBusy = showSpinner && isBusy

                Box {
                    iconButton(throttledOnClick, widgetStrings.skipPassword, iconButtonBusy, enabled)

                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.5f)
                            .aspectRatio(1f)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = finished,
                            label = "OwnIdBoostWidget.Checkmark",
                            modifier = Modifier
                                .fillMaxSize(0.75F)
                                .aspectRatio(1f),
                            content = checkmark
                        )
                    }
                }
            }

            val separatorText: @Composable RowScope.() -> Unit = {
                if (orText != null) {
                    orText(widgetStrings.or)
                }
            }

            when (position) {
                OwnIdBoostButtonPosition.Start -> {
                    boostButton()
                    separatorText()
                }

                OwnIdBoostButtonPosition.End -> {
                    separatorText()
                    boostButton()
                }
            }
        }
    }
}

/**
 * Returns current Boost widget strings for [instanceName].
 *
 * Returns [BoostWidgetStrings.DEFAULT] until the instance provides a non-null value. Later `null` updates leave the
 * latest non-null value unchanged. Reinitializing or destroying the instance clears the retained value, so the
 * function returns the default again until the reinitialized instance provides strings.
 */
@Composable
@OptIn(InternalOwnIdAPI::class)
public fun widgetStrings(
    instanceName: InstanceName = InstanceName.Default
): BoostWidgetStrings = key(instanceName) {
    val instanceResolverFlow = remember(instanceName) { OwnId.getInstanceContainerStateFlow(instanceName) }
    val instanceResolver by instanceResolverFlow.collectAsStateWithLifecycle()
    val stringsProvider = remember(instanceResolver) { instanceResolver?.getOrNull<BoostWidgetStringsProvider>() }

    LaunchedEffect(instanceResolver, stringsProvider) {
        val resolvedInstanceResolver = instanceResolver
        if (resolvedInstanceResolver != null && stringsProvider == null) {
            val ownIdLogRouter = resolvedInstanceResolver.getOrNull<OwnIdLogRouter>()
            ownIdLogRouter?.logW(resolvedInstanceResolver, "widgetStrings", "No BoostWidgetStringsProvider found for $instanceName")
        }
    }

    val stringsFlow = remember(stringsProvider) { stringsProvider?.getStrings(BoostWidgetStringsParams()) }
    if (stringsFlow == null) {
        BoostWidgetStrings.DEFAULT
    } else {
        key(instanceResolver) {
            val data by remember(stringsFlow) { stringsFlow.filterNotNull() }
                .collectAsStateWithLifecycle(initialValue = BoostWidgetStrings.DEFAULT)

            data
        }
    }
}
