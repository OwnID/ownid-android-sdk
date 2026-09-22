# Boost Flow

Use this reference when adding OwnID SDK v4 Boost widgets to an existing native
Android login or registration form. Boost keeps the form app-owned and returns
results through widget callbacks.

Full public docs: `../../../../docs/flows/boost-flow.md`.

## Contents

- [Mental Model](#mental-model)
- [Prerequisites](#prerequisites)
- [Widget Placement And Login ID](#widget-placement-and-login-id)
- [Login Widget Contract](#login-widget-contract)
- [Create-Passkey Widget Contract](#create-passkey-widget-contract)
- [State And Customization](#state-and-customization)
- [Integration Checklist](#integration-checklist)

## Mental Model

Boost enhances an existing native form. The app keeps its login and registration
screens, validation, submit buttons, password/manual paths, navigation,
registration endpoint, and session model.

OwnID supplies two widgets:

- `OwnIdLoginWidget` starts Boost login and calls `onLogin` after successful
  authentication.
- `OwnIdCreatePasskeyWidget` starts Boost create-passkey from a registration
  form. It can return `onNewPasskey` for registration submit or `onLogin` when
  the user is recognized and authenticated.

## Prerequisites

- Add `com.ownid.android-sdk:compose` for Boost widgets.
- Initialize OwnID before rendering widgets.
- Complete Android passkey prerequisites.
- Default widgets bind each new flow to their host Activity automatically.
- Keep password login available next to Boost. Keep manual registration as the
  fallback when no matching `ownIdData` is available.
- Configure `sessionCreate` when Boost login should return an app-defined
  session value as `response.session`.
- Keep the form's native password submit path app-owned. Register
  `passwordAuthenticate` when another selected SDK surface invokes
  provider-driven password authentication; Boost does not use it for the
  native password fallback.

## Widget Placement And Login ID

Place the widget in the same native form the user already edits:

- Login screen: `OwnIdLoginWidget(loginId = loginId, ...)`.
- Registration/create-account screen:
  `OwnIdCreatePasskeyWidget(loginId = loginId, ...)`.

By default, present the standard compact widget directly beside the password
input or password action in the same visual row. Preserve the intended control
sizing and usability of both elements. Use any other placement when the
developer explicitly requests it, and implement the chosen arrangement with
platform-native layout. Keep the app's submit buttons available.

The built-in widgets trim whitespace and newlines from `loginId`. Blank values
are passed as no login ID. For forms with a visible login ID field, pass that
field directly to the widget.

After success, treat `response.loginId.id` as authoritative and copy it back
into the form because the SDK may resolve or normalize the value.

Boost is token-first. If the current SDK context contains an OwnID access token,
the SDK derives the login ID from that token and starts the token login path for
that run. Widget login ID hints do not override that token path.

Without an access token, Boost uses login ID hints in this order:

1. Login ID supplied to the widget after trimming.
2. Login ID or raw login ID from the current SDK context.
3. Stored last user.
4. SDK login-ID collection UI, when the flow still needs an identifier.

Use login-ID context on shared login/register screens by default; apply
signed-in access-token context there when token-first behavior is intentional.

## Login Widget Contract

Use `OwnIdLoginWidget` on the existing sign-in form:

```kotlin
OwnIdLoginWidget(
    loginId = email,
    onLogin = { response ->
        email = response.loginId.id
        finishLogin(response)
    },
    onError = { failure ->
        showRetryableOwnIdError(failure)
    },
    onCancel = {
        // Stay on the login screen.
    }
)
```

`onLogin` receives `BoostFlow.Response.Login`:

- `loginId`: authenticated login identifier. Update the visible form value.
- `authMethod`: completed method, such as passkey, OTP, or FaceKey.
- `accessToken`: OwnID access token for the authenticated user.
- `sessionPayload`: server-provided payload for app session integration.
  Structured values remain JSON text; plain strings remain strings.
- `session`: app-defined value returned by `sessionCreate`; `null` when the
  provider is not configured or unavailable for that login.

Session handoff:

- Boost always includes `accessToken` and `sessionPayload` in successful login
  responses.
- When `sessionCreate` is configured and available, the SDK passes `loginId`,
  `authMethod`, `accessToken`, and `sessionPayload` to the provider before
  `onLogin`; the provider result becomes `response.session`.
- Without an available `sessionCreate`, `onLogin` receives `accessToken` and
  `sessionPayload` directly and `response.session` is `null`.

`onCancel` means the user or flow canceled before success. Keep the current
screen state and leave password login enabled.

`onError` covers flow start failures and terminal failures. Show retryable app
copy, log diagnostics according to the app's policy, and keep password login
available. Provider failures such as `sessionCreate` failures surface as
integration failures to the flow.

Before `onLogin`, the SDK may satisfy server requirements with passkey,
verification, or another supported Boost operation.

## Create-Passkey Widget Contract

Use `OwnIdCreatePasskeyWidget` on registration or create-account screens. It can
finish in two ways:

- `onNewPasskey`: a create-passkey result is ready for a pending registration.
- `onLogin`: the user was recognized and authenticated instead of creating a new
  account; handle it exactly like Boost login.

Minimal state pattern:

```kotlin
var createPasskeyResponse by remember { mutableStateOf<BoostFlow.Response.CreatePasskey?>(null) }

val ownIdDataForSubmit = createPasskeyResponse?.ownIdDataFor(currentLoginId = email)

OwnIdCreatePasskeyWidget(
    loginId = email,
    onLogin = { response ->
        email = response.loginId.id
        createPasskeyResponse = null
        finishLogin(response)
    },
    onNewPasskey = { response ->
        email = response.loginId.id
        createPasskeyResponse = response
    },
    onReset = {
        createPasskeyResponse = null
    },
    onError = { failure ->
        showRetryableOwnIdError(failure)
    },
    onCancel = {
        // Stay on the registration screen.
    }
)
```

Submit `ownIdDataForSubmit` unchanged with the app's registration request,
according to the backend contract. Import it from
`com.ownid.sdk.flow.boost.ownIdDataFor`.

`BoostFlow.Response.CreatePasskey` contains:

- `loginId`: registered user's login identifier. Update the form with this
  value.
- `proofToken`: proof of completed registration operations when returned; can
  be `null`.
- `ownIdData`: opaque value for the app registration backend; can be `null`.

When `ownIdDataFor(currentLoginId = ...)` returns non-null `ownIdData`, keep the
existing password field in the form to preserve a stable layout, but disable
it. Do not validate or require a user-entered password. Submit `ownIdData`
unchanged with the registration request. How the account is created without a
user-entered password—passwordless registration, a generated internal
password, or another supported mechanism—is determined by the app's identity
system and backend business logic.

If no matching `ownIdData` is returned, continue with the app's normal manual
registration flow, including password collection when required.

Bind `ownIdData` to the form login ID by reading it with
`ownIdDataFor(currentLoginId = ...)` immediately before submit. A response for
a different login ID must not enter the registration request.

Edits made while the flow is running do not retarget that flow and do not cause
an intermediate `onReset`. Treat `response.loginId.id` as that flow's completed
login ID and update the form in `onNewPasskey`.

## State And Customization

`OwnIdCreatePasskeyWidget` can show a completion checkmark after
`onNewPasskey`. `onReset` means the widget cleared that completed state,
including after the form login ID stops matching the saved response; clear the
app's pending response too.

The widget ViewModel keeps only the latest create-passkey result in
memory while that ViewModel is alive. If the user returns to the matching login
ID, the ViewModel can re-emit `onNewPasskey` and show the checkmark again.
Before registration submit, use `ownIdDataFor(currentLoginId = ...)` to read
`ownIdData` for the current form login ID.

After a successful registration submit, clear the pending create-passkey
response. If registration fails, follow the app's retry policy and revalidate
the response against the form login ID before another submit.

When rendering multiple or dynamic widgets of the same type, provide a stable
`widgetId` or externally own one widget ViewModel per logical widget.

Start with the built-in widgets and use their customization parameters:

- `theme: OwnIdTheme` for per-widget theme snapshots.
- `widgetStrings: BoostWidgetStrings` for widget copy.
- `showSpinner = false` when the screen already shows progress.
- `iconButton`, `checkmark`, and `orText` slots for component-level changes.
- `OwnIdLoginWidgetViewModel` or `OwnIdCreatePasskeyWidgetViewModel` overloads
  when the screen must own widget state across navigation/custom UI.
- `OwnIdBoostButton` when the app fully owns flow start and state mapping but
  wants the shared Boost row layout.

## Integration Checklist

- `com.ownid.android-sdk:compose` is present for Boost widgets.
- OwnID initializes before widget rendering.
- Android passkey setup is complete.
- Login widget receives the visible form login ID, updates the form from
  `response.loginId.id`, and keeps password login available on cancel/error.
- Default placement presents the compact widget and password input or action as
  neighboring controls in the same visual row, with appropriate native sizing;
  any other placement reflects an explicit developer request.
- Login success uses `response.session` when present, otherwise uses
  `response.accessToken` and `response.sessionPayload` through the app's session
  handoff.
- Create-passkey widget stores the latest response, submits
  `ownIdDataFor(currentLoginId = ...)` unchanged, and clears it on `onReset` and
  successful registration.
- Registration keeps the existing password field in place but disabled, and
  does not validate or require it when matching `ownIdData` is available.
- `onLogin` from the create-passkey widget routes through the login path.
- Multiple/dynamic widgets have stable `widgetId` values or externally owned
  ViewModels.
- Success, cancel, and error callbacks have been exercised with the host app's
  session, password login, and registration paths.
