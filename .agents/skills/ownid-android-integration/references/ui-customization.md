# UI Customization

Use this when integrating OwnID Android SDK v4 UI in a client app. Combine
Compose SDK APIs and lifecycle contracts with the documented flows and
maintained DemoAdvanced widget and operation-screen patterns.

Primary source docs:

- `../../../../docs/customization/themes-and-colors.md`
- `../../../../docs/customization/boost-widgets.md`
- `../../../../docs/integration/operation-ui.md`
- `../../../../docs/customization/localization.md`

Working examples:

- `../../../../demoAdvanced/app/src/main/java/com/ownid/demo/flow/boost/`
- `../../../../demoAdvanced/app/src/main/java/com/ownid/demo/ops/`

## Contents

- [Choose The Customization Layer](#choose-the-customization-layer)
- [Themes And Colors](#themes-and-colors)
- [Language, Strings, And Error Text](#language-strings-and-error-text)
- [Boost Widgets](#boost-widgets)
- [App-Hosted Operation UI](#app-hosted-operation-ui)
- [Validation And Accessibility Guardrails](#validation-and-accessibility-guardrails)

## Choose The Customization Layer

Pick the narrowest SDK integration layer that matches the app requirement.

| Requirement | SDK layer | Notes |
| --- | --- | --- |
| Make SDK Compose UI match app Material colors, typography, and shapes | `OwnIdTheme`, `OwnIdColors`, `OwnIdThemeBridge`, `theme` parameters | Use semantic color tokens; app-owned replacement content can also apply its design system. |
| Change Boost widget text for one screen | `BoostWidgetStrings` on the widget | Prefer SDK localization for global language behavior. |
| Force SDK UI language | `languages` during initialization or `OwnId.setLanguage(...)` | Language codes with optional regions. This switches from automatic app/device language tracking to explicit values. |
| Replace widget icon/checkmark/spinner/trailing text | Boost widget slots | The widget still owns throttling, flow state, callbacks, and accessibility label source. |
| Put supported operation UI inside app layout/dialog/sheet | `appHostedComponent` plus `OwnIdOperationUi` | Supported for login ID collection, email verification, and phone verification. |

## Themes And Colors

OwnID Compose UI uses `OwnIdTheme`. It snapshots:

- Material `ColorScheme`, `Shapes`, and `Typography`;
- OwnID semantic `OwnIdColors` tokens for fields, buttons, progress,
  checkmark state, surfaces, and error states.

Use `OwnIdTheme.capture()` and `copy(...)` when you need selected overrides.
Prefer changing `OwnIdColors` tokens such as `primary`, `onPrimary`, `surface`,
`onSurface`, `onSurfaceVariant`, `fieldBackground`, `progress`,
`iconButtonBorder`, or `checkmarkButtonBackground` over unsupported styling.
`OwnIdTheme.capture(...)` and `OwnIdColors.from(...)` infer light or dark OwnID
defaults from the supplied Material `ColorScheme`; pass `darkTheme` explicitly
when a non-standard scheme should use the opposite OwnID default palette.

There are two rendering paths:

- SDK-hosted UI is presented by the SDK outside the caller's current Compose
  tree. Publish a theme snapshot with `OwnIdThemeBridge()` near the app theme
  root for screens that can start SDK-presented UI. A later bridge publication
  replaces the active snapshot for the current OwnID runtime.
- App-hosted UI is rendered at the current call site. `OwnIdLoginWidget`,
  `OwnIdCreatePasskeyWidget`, `OwnIdBoostButton`, and `OwnIdOperationUi`
  capture the current Compose theme by default. Pass `theme = ownIdTheme` only
  when that UI entry needs a stable snapshot or selected overrides.

Custom operation content receives resolved `OwnIdColors`. Use those tokens in
app-owned replacement content so light/dark mode, brand colors, and SDK state
colors stay coherent.

## Language, Strings, And Error Text

Default behavior is SDK localization. Configure global language only when the
app must force OwnID text:

- initialization `languages = listOf("en-US")`;
- later `OwnId.setLanguage(listOf("en-US", "fr-FR"))`.
- restore automatic tracking with `OwnId.setLanguage(emptyList())`.

When a non-empty explicit language list is set, Android app/device language
tracking stops until another `setLanguage` call or process restart. An empty
list keeps or restores automatic tracking. Resolution falls back from full tag
to language-only tag, then English, then embedded SDK fallback strings.

Use SDK text overrides at these points:

- Boost widgets: pass `BoostWidgetStrings(skipPassword = ..., or = ...)`.
  `skipPassword` labels the button and is also the accessibility description
  passed to custom icon-button content. Keep it short and action-oriented.
- App-hosted operation UI: custom content receives operation-specific localized
  strings (`LoginIdCollectStrings`, `EmailVerificationStrings`,
  `PhoneVerificationStrings`). Use those values for titles, messages,
  placeholders, resend/cancel/not-you actions, and CTA text.
- Operation error copy: pass `errorTextProvider: (ErrorCode) -> String` to
  `OwnIdOperationUi` when app-specific copy is needed. Otherwise built-in
  content displays the SDK `UiError.localizedMessage`.

## Boost Widgets

Boost UI entry points are:

- `OwnIdLoginWidget`;
- `OwnIdCreatePasskeyWidget`;
- `OwnIdBoostButton` for the shared row when the app owns flow state.

Both built-in widgets trim whitespace/newlines from `loginId`; blank values are
passed as no login ID. Keep the password/manual login or registration path
available next to the widget.

Supported customization:

- `theme: OwnIdTheme`;
- `widgetStrings: BoostWidgetStrings`;
- `showSpinner: Boolean`;
- `position: OwnIdBoostButtonPosition`;
- `iconButton` slot;
- `checkmark` slot on `OwnIdCreatePasskeyWidget`;
- `orText` slot, with `null` to hide it;
- externally owned `OwnIdLoginWidgetViewModel` or
  `OwnIdCreatePasskeyWidgetViewModel`;
- stable `widgetId` when repeated or dynamic UI renders multiple widgets of the
  same type.

Slot guardrails:

- The `iconButton` slot receives throttled `onClick`, localized
  `contentDescription`, busy state, and enabled state. Call `onClick` directly,
  respect `enabled`, and preserve an accessible label.
- Use `OwnIdSpinner(...)` when custom content should keep the SDK spinner. It
  derives indicator and track colors from `OwnIdColors` through
  `OwnIdSpinnerDefaults`; override them locally only when needed.
- If `showSpinner = false`, provide another progress indication if the screen
  can otherwise look idle during a running flow.
- The create-passkey checkmark reflects the widget ViewModel's in-memory
  completion state. Store the create-passkey response, read `ownIdData` with
  `ownIdDataFor(currentLoginId = ...)` before submit, and clear app state on
  `onReset`.
- For repeated widgets, use stable `widgetId`. For longer-lived state across
  navigation or custom composition, own a separate widget ViewModel for each
  logical widget.

## App-Hosted Operation UI

Use app-hosted operation UI when the app owns placement or presentation chrome
but wants the SDK to own operation state, validation, resend, cancellation, and
settlement.

Supported operations:

- `operations.loginId.collect`;
- `operations.verifications.email`;
- `operations.verifications.phone`.

Pattern:

1. Scope context as needed for the operation.
2. Select the concrete operation and apply `.appHostedComponent`.
3. Check availability when the operation exposes it.
4. Start the operation and retain the returned `OwnIdOperationUiController` in
   UI state. For embedded UI, use a configuration-surviving screen owner such
   as a `ViewModel`.
5. Render `OwnIdOperationUi(operationUiController = controller)` while active.
6. Await `controller.whenSettled()` and clear the retained controller when it
   is still the current one.

Presentation modes:

- Embedded: render `OwnIdOperationUi` directly in the screen. After
  configuration-driven Activity recreation, render the same retained controller
  from the replacement composition; recreation does not settle or abort it.
  Deliberately removing the UI from a stable screen or navigation destination
  before settlement aborts with user-close semantics. If the app decides not to
  remount a retained controller after recreation, call `controller.abort(reason)`
  or `controller.close()` explicitly.
- Dialog/sheet/overlay: create a fresh `OwnIdUiContainerController` for each
  presentation cycle. Call `markOpened()` when visible and ready for input, call
  `markClosed()` after the container is fully dismissed, and route user
  dismissal through `containerController.close()`.

Custom content overrides are available on `OwnIdOperationUi`:

- `loginIdCollectContent`;
- `emailVerificationContent`;
- `phoneVerificationContent`.

Each override receives UI state, localized strings, modifier, `OwnIdColors`,
optional `errorTextProvider`, and `isReadyForInitialFocus`. It owns rendering
and user-event wiring only. Complete, resend, and cancel through callbacks from
the supplied UI state.

Preserve these behavior contracts when replacing built-in content:

- Login ID collection: update through `uiState.onLoginIdChange`, continue
  through `uiState.onContinue`, preserve keyboard/autofill hints for the
  collectable login ID types, show login ID validation errors from
  `strings.error`, and show other UI errors from `errorTextProvider` or the
  current SDK UI error.
- Email/phone verification: accept/normalize OTP digits, submit through
  `uiState.onCodeEntered` only when required length is reached and the operation
  is not busy, reset challenge-scoped input when `uiState.challenge.challengeId`
  changes, clear input on visible errors, and invoke resend, cancel, and "not
  you" only from the matching user action.
- Initial focus: request it only when `isReadyForInitialFocus` is true,
  especially in dialogs/sheets.

`OwnIdOperationUi` covers app-hosted operation content for supported operations.
Native theme tokens affect OwnID Compose widgets and app-hosted operation UI.

## Validation And Accessibility Guardrails

For an integration answer, recommend the smallest meaningful app validation for
the changed surface:

- light and dark theme screenshots or manual inspection for changed
  `OwnIdColors`;
- TalkBack/accessibility scan for custom icon buttons, custom operation content,
  focus order, labels, and touch targets;
- font scale and narrow-width checks for widget trailing text and operation
  content;
- login/create-passkey callbacks after widget customization, including
  `onCancel`, `onError`, and `onReset` for create-passkey;
- operation dialog/sheet open, close, retry, resend, and settle paths when using
  app-hosted operation UI.

If a required visual change exceeds these surfaces, use app-owned custom
content where supported or report the remaining product capability gap.
