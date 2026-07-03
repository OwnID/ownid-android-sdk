# Themes and Colors

The Compose UI SDK (`com.ownid.android-sdk:compose`) contains OwnID UI surfaces: Boost widgets, SDK-hosted operation UI, app-hosted operation UI, and reusable UI components. These UI surfaces are themed through [`OwnIdTheme`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/OwnIdTheme.kt).

SDK-hosted UI needs a published theme to reuse your app theme. App-hosted UI captures the local theme by default and needs an explicit `theme` only for custom theme values or selected overrides.

## Contents

- [Theme Paths](#theme-paths)
- [Examples](#examples)
- [SDK-Hosted UI](#sdk-hosted-ui)
- [App-Hosted UI](#app-hosted-ui)
- [Build a Theme](#build-a-theme)
- [Color Tokens](#color-tokens)

## Theme Paths

| Path | Default | What to do |
| --- | --- | --- |
| SDK-hosted operation UI | Uses SDK light or dark defaults unless a theme is published. | Add [`OwnIdThemeBridge()`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/OwnIdTheme.kt) near your themed app root. Pass `theme` only for custom theme values or selected overrides. |
| App-hosted operation UI | Captures the local `MaterialTheme` at the `OwnIdOperationUi` call site. | No extra setup. Pass `theme` only for custom theme values or selected overrides. |
| Custom operation content | Receives resolved `OwnIdColors`. | Use the provided `colors` instead of hard-coded colors. |
| Boost widgets | Capture the local `MaterialTheme` at the widget call site. | No extra setup. Pass `theme` only for custom theme values or selected overrides. |
| Widget subcomponents | Use built-in themed components. | Use slots for structural changes; see [Boost Widget Customization](boost-widgets.md). |

## Examples

- [Advanced app root example](../../demoAdvanced/app/src/main/java/com/ownid/demo/MainActivity.kt): publishes the app theme for SDK-hosted OwnID UI with `OwnIdThemeBridge()`.
- [Advanced Boost login screen](../../demoAdvanced/app/src/main/java/com/ownid/demo/flow/boost/BoostLoginScreen.kt): renders a Boost widget inside the app Compose hierarchy.
- [Advanced Boost create-passkey screen](../../demoAdvanced/app/src/main/java/com/ownid/demo/flow/boost/BoostCreatePasskeyScreen.kt): renders a create-passkey widget inside the app Compose hierarchy.

## SDK-Hosted UI

Default SDK-presented OwnID operation UI is started from your app code but rendered by the SDK. Because that UI is not a child of the caller's composable, it cannot read the caller's local composition directly.

The bridge captures the current `MaterialTheme` and publishes it for the current OwnID runtime. The last published theme remains active for future SDK-hosted UI until another bridge publishes a replacement theme or the SDK is reinitialized.

Place `OwnIdThemeBridge()` inside your app theme near the root of the screens that can start OwnID UI, even when you do not need custom colors.

```kotlin
AppTheme {
    OwnIdThemeBridge()
    AppContent()
}
```

Publish a custom theme only when SDK-hosted UI should use selected values or overrides.

```kotlin
OwnIdThemeBridge(theme = ownIdTheme)
```

## App-Hosted UI

Boost widgets and `OwnIdOperationUi` are rendered directly inside your app's Compose hierarchy, so they can read the `MaterialTheme` at their call site. `OwnIdThemeBridge()` is not involved in this path.

### Boost Widgets

`OwnIdLoginWidget` and `OwnIdCreatePasskeyWidget` use the `theme` parameter when that widget should use custom theme values or selected token overrides instead of whatever `MaterialTheme` is active around that composable.

```kotlin
OwnIdLoginWidget(
    loginId = email,
    onLogin = ::handleLogin,
    theme = ownIdTheme
)
```

### Operation UI

App-hosted operation UI captures the surrounding `MaterialTheme` unless you pass an explicit `OwnIdTheme`.

```kotlin
OwnIdOperationUi(
    operationUiController = controller,
    theme = ownIdTheme
)
```

Custom operation content receives the resolved `OwnIdColors` for the operation host. Treat those colors as the operation's semantic palette and use them instead of hard-coded colors.

```kotlin
OwnIdOperationUi(
    operationUiController = controller,
    loginIdCollectContent = { uiState, strings, modifier, colors, errorTextProvider, isReadyForInitialFocus ->
        MyLoginIdCollectContent(
            uiState = uiState,
            strings = strings,
            colors = colors,
            modifier = modifier
        )
    }
)
```

## Build a Theme

`OwnIdTheme` contains the full theme snapshot used by OwnID Compose content:

| Part | Source by default | Used for |
| --- | --- | --- |
| `colorScheme: ColorScheme` | `MaterialTheme.colorScheme` | Material colors inside OwnID Compose content. |
| `shapes: Shapes` | `MaterialTheme.shapes` | Material component shapes inside OwnID Compose content. |
| `typography: Typography` | `MaterialTheme.typography` | Material text styles inside OwnID Compose content. |
| `colors: OwnIdColors` | `OwnIdColors.from(colorScheme, darkTheme)` | OwnID-specific semantic colors for widgets, operation UI, fields, indicators, and state marks. |

OwnID renders its Compose content by applying the captured Material theme and providing `OwnIdColors` through `LocalOwnIdColors`.

```kotlin
val ownIdTheme = OwnIdTheme.capture()
```

`OwnIdTheme.capture()` infers `darkTheme` from the captured `ColorScheme` and uses it for OwnID defaults that do not map directly to Material roles, such as checkmark tokens. Pass `darkTheme` explicitly when a custom `ColorScheme` should use the opposite OwnID default palette.

Use `copy` when only a few tokens should change.

```kotlin
val ownIdTheme = OwnIdTheme.capture().copy(
    colors = OwnIdColors.from().copy(
        primary = MaterialTheme.colorScheme.primary,
        onPrimary = MaterialTheme.colorScheme.onPrimary,
        iconButtonBorder = MaterialTheme.colorScheme.outline,
        checkmarkButtonBackground = MaterialTheme.colorScheme.tertiary
    )
)
```

## Color Tokens

[`OwnIdColors.from(colorScheme, darkTheme)`](../../sdk-compose/compose/src/main/java/com/ownid/sdk/compose/OwnIdColors.kt) derives OwnID tokens from Material colors. Tokens without direct Material roles, such as `checkmarkButton` and `checkmarkButtonBackground`, use the SDK light or dark defaults selected by `darkTheme`. When omitted, `darkTheme` is inferred from the supplied `ColorScheme`. Override only the tokens your app needs.

OwnID uses color values as provided. When overriding paired colors such as `primary` / `onPrimary` or `surface` / `onSurface`, keep the foreground color readable on the matching background.

| Token | Used for |
| --- | --- |
| `primary` / `onPrimary` | Primary actions, filled buttons, focused states, and primary icon content. |
| `error` | Validation and error states. |
| `surface` | OwnID containers and sheets. |
| `onSurface` | Primary text and content on `surface`. |
| `onSurfaceVariant` | Secondary text, helper text, outlines, and progress defaults. |
| `fieldBackground` | Text field and OTP field containers. |
| `progress` / `progressTrack` | Loading indicators. |
| `iconButtonBackground` / `iconButtonBackgroundDisabled` | Boost icon button containers. |
| `iconButtonBorder` | Boost icon button border. |
| `checkmarkButton` / `checkmarkButtonBackground` | Create-passkey success checkmark. |

`OwnIdDefaultColorsLight` and `OwnIdDefaultColorsDark` are available when you want to start from the SDK palettes instead of the current Material theme.

`OwnIdSpinnerDefaults.color(colors)` and `OwnIdSpinnerDefaults.trackColor(colors)` map `progress` and `progressTrack` to `OwnIdSpinner`. Use those defaults when custom spinner content should keep the same semantic theme mapping as built-in OwnID loading indicators.
