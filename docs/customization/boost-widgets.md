# Boost Widget Customization

Boost widgets are the Compose UI entry points for login and create-passkey flows. This page covers widget customization only. For integration flow wiring, see [Boost Flow](../flows/boost-flow.md). For custom operation screens, see [Operation UI](../integration/operation-ui.md).

Both widgets trim whitespace and newlines from `loginId` before starting their flow. Blank values are ignored and passed as no login ID.

| Widget | Purpose |
| --- | --- |
| [`OwnIdLoginWidget`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/widget/OwnIdLoginWidget.kt) | Adds OwnID login to an existing login screen. |
| [`OwnIdCreatePasskeyWidget`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/widget/OwnIdCreatePasskeyWidget.kt) | Adds create-passkey to registration or account screens. |

## Contents

- [Customization Surface](#customization-surface)
- [Examples](#examples)
- [Theme and Text](#theme-and-text)
- [Spinner](#spinner)
- [Position](#position)
- [Icon Button Slot](#icon-button-slot)
- [Checkmark Slot](#checkmark-slot)
- [Separator Text](#separator-text)
- [Externally Owned ViewModel](#externally-owned-viewmodel)
- [Multiple Widgets and State Ownership](#multiple-widgets-and-state-ownership)

## Customization Surface

| Need | API |
| --- | --- |
| Match app colors, typography, shapes | [`theme: OwnIdTheme`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/OwnIdTheme.kt); see [Themes and Colors](themes-and-colors.md) |
| Override widget text | [`widgetStrings: BoostWidgetStrings`](../../sdk-core/core/src/main/java/com/ownid/sdk/ui/capability/BoostWidgetStringsProvider.kt); see [Localization](localization.md) |
| Hide the busy spinner | `showSpinner: Boolean` |
| Show OwnID after the `or` separator | [`position = OwnIdBoostButtonPosition.End`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/components/OwnIdBoostButton.kt) |
| Replace the icon button | `iconButton` slot |
| Replace create-passkey success mark | `checkmark` slot |
| Hide or replace separator text | `orText` slot |
| Own widget state outside composition | [`OwnIdLoginWidgetViewModel`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/widget/OwnIdLoginWidget.kt) / [`OwnIdCreatePasskeyWidgetViewModel`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/widget/OwnIdCreatePasskeyWidget.kt) overloads |
| Keep repeated widget state separate | Stable `widgetId`, or a separate externally owned ViewModel per logical widget |

## Examples

Advanced examples show complete screen wiring:

- [Boost login example](../../demoAdvanced/app/src/main/java/com/ownid/demo/flow/boost/BoostLoginScreen.kt)
- [Boost create-passkey example](../../demoAdvanced/app/src/main/java/com/ownid/demo/flow/boost/BoostCreatePasskeyScreen.kt)

## Theme and Text

Pass an explicit `OwnIdTheme` when one widget should use stable theme values. For app-wide theme publishing, per-widget themes, and color-token guidance, see [Themes and Colors](themes-and-colors.md).

Use `widgetStrings` for the separator text and the icon-button accessibility label. For server-provided and app-provided localization, see [Localization](localization.md).

## Spinner

By default, widgets show a spinner while the Boost flow is running. Set `showSpinner = false` when your screen already shows progress elsewhere.

```kotlin
import com.ownid.sdk.compose.widget.OwnIdLoginWidget

OwnIdLoginWidget(
    loginId = email,
    onLogin = ::onOwnIdLogin,
    showSpinner = false
)
```

When replacing the icon-button slot but keeping the OwnID spinner, use [`OwnIdSpinner`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/components/OwnIdSpinner.kt) in the custom slot. Pass `color`, `trackColor`, `strokeWidth`, or `strokeCap` for per-slot styling, or use `OwnIdSpinnerDefaults` to derive values from an [`OwnIdColors`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/OwnIdColors.kt) palette.

## Position

By default, the OwnID action appears before the `or` separator. Keep this default when the widget appears before the app's password field.

Use `position = OwnIdBoostButtonPosition.End` when your screen places the widget after the password field. This keeps the separator between the password field and OwnID.

```kotlin
import com.ownid.sdk.compose.components.OwnIdBoostButtonPosition
import com.ownid.sdk.compose.widget.OwnIdLoginWidget

OwnIdLoginWidget(
    loginId = email,
    onLogin = ::onOwnIdLogin,
    position = OwnIdBoostButtonPosition.End
)
```

`Start` and `End` follow the screen's layout direction, so the same value works for left-to-right and right-to-left languages.

## Icon Button Slot

Use the `iconButton` slot when you need to render the icon button with your own component.

The widget passes a throttled `onClick`, localized `contentDescription`, spinner busy state, and enabled state. `isBusy` is `false` when `showSpinner = false`, even if a flow is running.

Your button should call `onClick` directly, apply the accessibility label to the interactive element, respect `enabled`, and keep a comparable control size and button semantics.

```kotlin
import com.ownid.sdk.compose.components.OwnIdSpinner
import com.ownid.sdk.compose.widget.OwnIdLoginWidget

OwnIdLoginWidget(
    loginId = email,
    onLogin = ::onOwnIdLogin,
    iconButton = { onClick, contentDescription, isBusy, enabled ->
        // Your custom icon button.
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.semantics {
                this.contentDescription = contentDescription
            }
        ) {
            if (isBusy) {
                OwnIdSpinner()
            } else {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null
                )
            }
        }
    }
)
```

## Checkmark Slot

`OwnIdCreatePasskeyWidget` can show a checkmark after passkey creation succeeds. Replace it with the `checkmark` slot.

```kotlin
import com.ownid.sdk.compose.widget.OwnIdCreatePasskeyWidget

OwnIdCreatePasskeyWidget(
    loginId = email,
    onLogin = ::onOwnIdLogin,
    onNewPasskey = ::onNewPasskey,
    onReset = ::onPasskeyReset,
    checkmark = {
        // Your custom success mark.
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
)
```

`OwnIdLoginWidget` does not render a checkmark because login does not keep completed create-passkey state.

The default checkmark is decorative. If completion must be announced, expose that state through surrounding screen UI or accessibility semantics.

## Separator Text

Pass `null` to hide the separator.

```kotlin
import com.ownid.sdk.compose.widget.OwnIdLoginWidget

OwnIdLoginWidget(
    loginId = email,
    onLogin = ::onOwnIdLogin,
    orText = null
)
```

Or replace it with app-owned content.

```kotlin
import com.ownid.sdk.compose.widget.OwnIdLoginWidget

OwnIdLoginWidget(
    loginId = email,
    onLogin = ::onOwnIdLogin,
    orText = { text ->
        // Your custom separator text.
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium
        )
    }
)
```

## Externally Owned ViewModel

By default, each widget owns its ViewModel. Use the ViewModel overload when the screen already owns the widget lifetime, when the widget appears inside navigation where you need a specific `ViewModelStoreOwner`, or when you build custom OwnID UI for the same Boost flow.

```kotlin
import com.ownid.sdk.compose.widget.OwnIdLoginWidget
import com.ownid.sdk.compose.widget.OwnIdLoginWidgetViewModel

val viewModel: OwnIdLoginWidgetViewModel = viewModel(
    factory = OwnIdLoginWidgetViewModel.factory()
)

OwnIdLoginWidget(
    loginId = email,
    onLogin = ::onOwnIdLogin,
    viewModel = viewModel
)
```

Create-passkey widgets have a matching ViewModel overload.

```kotlin
import com.ownid.sdk.compose.widget.OwnIdCreatePasskeyWidget
import com.ownid.sdk.compose.widget.OwnIdCreatePasskeyWidgetViewModel

val viewModel: OwnIdCreatePasskeyWidgetViewModel = viewModel(
    factory = OwnIdCreatePasskeyWidgetViewModel.factory()
)

OwnIdCreatePasskeyWidget(
    loginId = email,
    onLogin = ::onOwnIdLogin,
    onNewPasskey = ::onNewPasskey,
    onReset = ::onPasskeyReset,
    viewModel = viewModel
)
```

`OwnIdCreatePasskeyWidgetViewModel` keeps the latest create-passkey response only while the ViewModel is alive. If the effective `loginId` changes, the widget clears the visible checkmark and emits `onReset`; if the login ID later matches the remembered response again, it can emit `onNewPasskey` again without starting a new flow. Tapping the widget while it is already showing the checkmark clears that visible state and emits `onReset` without starting a new flow.

## Multiple Widgets and State Ownership

Provide a stable `widgetId` when the same screen renders multiple widgets of the same type from dynamic or repeated UI.

```kotlin
import com.ownid.sdk.compose.widget.OwnIdLoginWidget

OwnIdLoginWidget(
    widgetId = "shipping-email",
    loginId = shippingEmail,
    onLogin = ::onOwnIdLogin
)
```

When widget state must survive navigation, conditional composition, or custom widget composition, keep a separate ViewModel for each logical widget and pass it into the widget. Do not share one ViewModel between two widgets that are visible at the same time.
