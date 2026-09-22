# Migration From v3 To v4

Use this reference when migrating an existing Android app from OwnID SDK v3 to
OwnID SDK v4. Treat migration as an integration redesign at the SDK boundary,
not as a version-only update.

Public migration overview: `../../../../docs/upgrade/v3-to-v4.md`.

## Contents

- [Migration Principle](#migration-principle)
- [Read Before Editing](#read-before-editing)
- [Audit The v3 Integration](#audit-the-v3-integration)
- [Dependency Mapping](#dependency-mapping)
- [Initialization And Configuration](#initialization-and-configuration)
- [Map v3 Integration Responsibilities](#map-v3-integration-responsibilities)
- [Flow Surface Mapping](#flow-surface-mapping)
- [WebBridge Migration](#webbridge-migration)
- [Passkey Enrollment Migration](#passkey-enrollment-migration)
- [UI Customization Migration](#ui-customization-migration)
- [Migration Audit Checklist](#migration-audit-checklist)
- [Validation Guidance](#validation-guidance)
- [Decisions To Confirm](#decisions-to-confirm)

## Migration Principle

Version 4 keeps the product flows but changes ownership:

- OwnID SDK owns runtime initialization, flow APIs, passkey operations,
  Compose widgets, Passkey Enrollment, and WebBridge.
- The client app owns identity-platform integration, password authentication,
  app-session creation, social sign-in SDK setup, fallback auth, registration
  submission, and navigation.
- Replace packaged v3 integrations and Android View UI with the appropriate v4
  callbacks, handoffs, providers, flows, and Compose UI.

First identify the user journey the app needs, then wire that journey to the
smallest v4 surface.

## Read Before Editing

Load only the references that match the v3 inventory and target v4 surface:

- `install.md` for Maven artifacts and compatibility gates.
- `configuration.md` for `OwnId.initialize*` startup.
- `providers.md` for provider setup and source-only provider helpers.
- `boost-flow.md` for native login/registration forms with SDK widgets.
- `elite-flow.md` for OwnID-hosted WebView flows.
- `webbridge.md` for app-owned `WebView` pages that load and initialize the
  OwnID Web SDK.
- `passkey-enrollment.md` and `enable-passkeys.md` for post-login enrollment
  and Android passkey prerequisites.
- `ui-customization.md` for supported v4 Compose customization.
- `validation.md` for the smallest meaningful post-migration checks.

Use v4 docs for target product flows, production API/source for exact calls and
lifecycle behavior, and maintained DemoBase/DemoAdvanced implementations for
working host-app composition.

## Audit The v3 Integration

Before changing files, inspect the host app and make a local migration inventory.
A temporary v4 dependency upgrade plus compilation can help expose mechanical
v3 API breakages, but it does not prove the migrated integration is correct.

Search for these high-signal v3 surfaces first. Treat the list as migration
triage, not as an exhaustive v3 API catalog:

```text
com.ownid.android-sdk:core
com.ownid.android-sdk:compose
com.ownid.android-sdk:gigya
com.ownid.android-sdk:redirect
OwnId.createInstanceFromFile
OwnId.createInstanceFromJson
createGigyaInstanceFromFile
createGigyaInstanceFromJson
OwnId.providers
OwnId.start
OwnIdButton
OwnIdAuthButton
OwnIdImageButton
OwnIdLoginButton
OwnIdAuthLoginButton
OwnIdRegisterButton
OwnIdViewModel
OwnIdLoginViewModel
OwnIdRegisterViewModel
OwnIdEnrollmentViewModel
OwnIdGigya
GigyaRegistrationParameters
OwnIdGigyaWebBridge
createWebViewBridge
injectInto
enrollCredential
ownIdIntegrationSdkConfig.json
ownIdGigyaSdkConfig.json
ownIdSdkConfig.json
OwnIdConfig
ownIdData
```

Classify each hit by behavior:

- **Boost login/registration:** v3 buttons, Compose wrappers, login/register
  ViewModels, `OwnIdRegisterEvent.ReadyToRegister`, or `ownIdData` submitted
  with registration.
- **Packaged Gigya:** `com.ownid.android-sdk:gigya`, `OwnIdGigya*`,
  `GigyaRegistrationParameters`, `createGigyaInstance*`, Gigya Screen-Sets
  bridge helpers.
- **Direct/custom integration:** app code that handles OwnID response payloads
  directly or implements `OwnIdIntegration`.
- **Elite:** `OwnId.start { ... }`, page-action handlers, or OwnID-hosted web
  flow startup.
- **WebBridge:** `createWebViewBridge().injectInto(webView)`, Gigya
  Screen-Sets bridge, Capacitor/WebView bridge setup.
- **Enrollment:** `OwnIdEnrollmentViewModel.enrollCredential`,
  `OwnIdGigya.defaultLoginIdProvider`, or auth-token/login-ID provider pairs.
- **Redirect SDK:** `com.ownid.android-sdk:redirect` or redirect helper code.
- **UI customization:** XML attributes, `styleRes`, custom subclasses of
  `OwnIdButton`/`OwnIdAuthButton`, or old View wrapping.

If the inventory shows backend/session contract changes, get the product
decision before implementing that boundary.

## Dependency Mapping

Replace v3 dependencies with the smallest v4 artifact:

| v3 surface | v4 replacement |
| --- | --- |
| `com.ownid.android-sdk:core` used for runtime, flow, enrollment, or WebBridge | `com.ownid.android-sdk:core:<v4>` |
| `com.ownid.android-sdk:compose` old wrappers around View widgets | `com.ownid.android-sdk:compose:<v4>` for native Compose widgets, themes, colors, and reusable UI components |
| `com.ownid.android-sdk:gigya` | remove; copy/register the source-only Gigya provider helper only when the app already uses SAP CDC/Gigya |
| `com.ownid.android-sdk:redirect` | remove; v4 has no direct redirect SDK replacement |
| Android View widgets from Core | remove; use v4 Compose widgets |

Add one v4 artifact by default. `compose` depends on `core`; add `core`
directly only when the module imports Core APIs or project dependency rules
require explicit direct dependencies.

Respect the compatibility gates and host-project policies in `install.md`
before changing dependencies or platform settings.

## Initialization And Configuration

Replace v3 SDK creation/configuration with one v4 initialization path during app startup:

```kotlin
OwnId.logger {
    // Optional: configure according to the app's logging policy.
}

OwnId.initialize {
    applicationContext = appContext
    appId = "<OWNID_APP_ID>"
    env = OwnIdConfiguration.Env.Prod
    region = OwnIdConfiguration.Region.US
}
```

Supported v4 sources:

- `OwnId.initialize { ... }` for programmatic config.
- `OwnId.initializeFromJson { applicationContext = appContext; json = ... }`.
- `OwnId.initializeFromFile { applicationContext = appContext; fileName = ... }`.

Migration rules:

- Replace `createInstanceFromFile`, `createInstanceFromJson`, and
  `createGigyaInstanceFrom*` with `OwnId.initialize*`.
- Recreate config using v4 keys: `appId` or `appID`, `env`, `region`,
  `rootUrl` or `rootURL`, optional `languages`.
- Default v4 asset file name is `OwnIdConfig.json`; recreate configuration with
  v4 keys.
- Initialize once before providers, widgets, flows, enrollment, or WebBridge.
- Pass any Android `Context`; prefer the app/application context at startup.
  The SDK stores `Context.getApplicationContext`.
- Use `OwnId.setLanguage(...)` only when the app intentionally overrides
  process language tags.

## Map v3 Integration Responsibilities

Version 3 Boost integration boundaries and Elite providers do not have one
mechanical version 4 replacement. Use the public
[migration mapping](../../../../docs/upgrade/v3-to-v4.md#migrate-integration-responsibilities)
to classify each responsibility before choosing callbacks, handoffs, or
providers. Choose the version 4 journey first, keep app-owned responsibilities
at their existing boundaries, and register only capabilities that the selected
integration invokes.

For selected provider capabilities, open `providers.md` for exact parameter,
result, availability, and scope contracts.

Migration rules:

- Register providers after successful `OwnId.initialize*` and before starting
  SDK functionality that may call them.
- Preserve existing app-owned authentication, session types, storage, and
  lifecycle policies. Route provider inputs through those boundaries and
  extend them only when the selected journey requires a new app-owned contract.
- Use `withProviders { ... }` as a one-flow, screen, or WebBridge provider
  override when behavior must differ from top-level providers.
- Keep fallback password login and manual registration paths unless the product
  owner explicitly removes them.

For SAP Customer Data Cloud/Gigya apps:

- Remove `com.ownid.android-sdk:gigya`.
- Keep the app's SAP CDC SDK dependency only if the app already owns it.
- Copy the v4 source-only helper from
  `../../../../providers/OwnIdGigyaProviders.kt`.
- Register it after both OwnID and Gigya are initialized:

```kotlin
OwnId.setProviders {
    gigyaProviders(gigya)
}
```

Gigya providers are source-only helper wiring, not a Maven artifact.

## Flow Surface Mapping

### Boost Login And Registration

Replace v3 View widgets, old Compose wrappers, and login/register ViewModels
with v4 Compose widgets from `com.ownid.android-sdk:compose`:

- Login screen: `OwnIdLoginWidget(loginId = email, onLogin = ..., ...)`.
- Registration screen:
  `OwnIdCreatePasskeyWidget(loginId = email, onNewPasskey = ..., onLogin = ...)`.

Keep the app's existing form validation, submit buttons, password fallback,
registration endpoint, navigation, and session owner.

Do not mechanically carry over v3 widget ViewModels. Use the standard v4
widget overload that owns its ViewModel. Provide an external widget ViewModel
only when the host explicitly needs widget state across navigation,
conditional replacement, or custom composition. Keep app form and
registration-handoff state separately app-owned.

Critical rules:

- Pass the visible login ID field into the widget.
- In callbacks, treat `response.loginId.id` as authoritative and copy it back
  to the form because OwnID may normalize it.
- On login success, use `response.session` when present; otherwise route
  `response.accessToken` and `response.sessionPayload` to the app session
  boundary.
- On create-passkey success, submit `response.ownIdDataFor(currentLoginId = ...)`
  unchanged with the matching registration request when it returns a value. It
  is opaque and may be `null`.
- Clear pending create-passkey state when the user changes login ID, the widget
  calls `onReset`, or registration completes.
- Keep the existing successful registration policy unless the backend already
  supports generated-password or passwordless behavior.

Open `boost-flow.md` before editing Boost code.

### Elite Flow

Replace v3 `OwnId.start { ... }` and page-action APIs with v4 Elite Flow. Open
`elite-flow.md` before editing Elite code.

- Use `EliteFlowContext` for optional WebView display options and event
  handlers.
- Keep the returned controller while the flow is active; use `abort(reason)` for
  semantic cancellation and `close()` for lifecycle cleanup.
- Hand `accessToken` and `ownIdData` to existing app/backend owners. Create
  sessions in UI callbacks only when that is where the app already owns session
  creation.
- Replace old v3 page-action wrappers or webflow implementation details with
  their v4 flow/event surfaces.

### Direct Or Custom Integration

There is no v4 Integration Component equivalent. Choose the v4 surface that
matches the app-owned journey:

- Use Boost widgets when the app keeps native login/registration forms.
- Use providers plus Elite/WebBridge when authentication is web-hosted.

If neither supported surface matches the existing custom journey, stop after
classifying its responsibilities and report that the target integration is
outside this skill's supported surfaces.

### Redirect SDK

Remove v3 redirect SDK usage. v4 has no direct redirect artifact. If the app
still has browser/custom-tab return behavior, decide with the product owner
whether it should become Elite, WebBridge, or app-owned web auth.

## WebBridge Migration

Version 3 injected a bridge into an existing `WebView`. Version 4 creates a
fresh bridge from the current namespace and explicitly attaches it. Open
`webbridge.md` before editing WebBridge code.

- Replace `createWebViewBridge().injectInto(webView)` and `OwnIdGigyaWebBridge`
  with `OwnId.webBridge.create().attach(...)`.
- Attach before loading content, or reload after successful attach.
- Create a new `WebBridge` per `WebView` attachment.
- Pass every required trusted origin explicitly when the first attachment must
  succeed deterministically; attach does not wait for server configuration.
  Avoid global wildcard origins in production.
- Scope context before `create()` when the page needs a specific app session,
  login ID, or access token. Use provider overrides when this bridge instance
  must differ from top-level providers.
- Use bridge plugin customization through SDK plugin registry APIs.
- Detach according to lifecycle when the attached page is no longer used.

The app-owned web page must load and initialize OwnID Web SDK before it can use
native bridge capabilities. Native WebBridge does not create the WebView, load
the page, or own the web content.

## Passkey Enrollment Migration

Replace v3 Credential Enrollment APIs and `OwnIdEnrollmentViewModel` with v4
Passkey Enrollment. Open `passkey-enrollment.md` before editing enrollment code.

Migration rules:

- Enrollment is post-login for the currently signed-in user.
- v4 needs an OwnID access token for that user; replace v3 login-ID/auth-token
  provider pairs with the v4 access-token path.
- If the enrollment path has a proof token, pass it through the
  `PasskeyEnrollFlowContext` route.
- Check Android passkey prerequisites: AndroidX Credentials, Digital Asset
  Links, app ID, signing certificate variants, and tenant/domain alignment.
- Treat availability failures and user cancellation as fallback states, not as
  account failure.

## UI Customization Migration

Discard v3 Android View customization:

- XML attrs for `OwnIdButton`/`OwnIdAuthButton`;
- `styleRes` passed to old widgets;
- custom View subclasses around v3 buttons;
- old ViewModel-driven widget state.

Recreate the design through v4 Compose customization:

- widget parameters: `theme`, `widgetStrings`, `showSpinner`, `position`,
  `widgetId`;
- slots: `iconButton`, `checkmark`, `orText`;
- shared `OwnIdTheme` and `OwnIdColors`;
- app-hosted operation UI via `appHostedComponent` and `OwnIdOperationUi`;
- language configuration, `BoostWidgetStrings`, and operation UI strings.

Use v4 customization surfaces for appearance changes.

## Migration Audit Checklist

Before ending the migration, verify by inspection that:

- v3 OwnID artifacts are removed from the app target, with any retained copies
  kept strictly as old-source reference material.
- App code imports v4 widgets and APIs rather than removed v3 widgets,
  ViewModels, Gigya SDK packages, or redirect SDK.
- OwnID initializes once from v4 config before providers and flows.
- Version 3 integration responsibilities were classified with the public
  migration mapping before choosing version 4 callbacks, handoffs, or providers.
- Only providers used by the selected integration are registered, at the correct
  global or scoped boundary.
- Configured provider callbacks go through the existing app-owned integration
  boundary; unrelated app-owned flows remain separate.
- Any migrated Boost registration path submits only current matching
  `ownIdData`.
- Any migrated WebBridge attaches before page load/reload and uses trusted
  origins.
- Any migrated Passkey Enrollment path uses a current OwnID access token.
- Fallback password/manual registration flows remain available unless their
  removal was explicitly approved.
- UI customization uses v4 Compose APIs.

## Validation Guidance

When the task allows validation, run the smallest meaningful host-app check:

- dependency resolution or module build for dependency/config-only changes;
- affected Android app build after code migration;
- targeted manual or automated flow check for login, registration,
  WebBridge, or enrollment;
- inspect Digital Asset Links/passkey setup when passkey behavior changed.

Report unrelated validation failures separately with the command and failing
owner.

## Decisions To Confirm

Confirm these product or platform decisions before implementing the affected
migration boundary:

- removing password fallback or manual registration;
- changing backend registration/session contracts;
- changing identity provider, tenant, region, app ID, domains, or secrets;
- replacing redirect behavior with a different user journey;
- raising `minSdk`, Kotlin, Compose, AGP, or dependency baselines;
- changing SAP CDC/Gigya configuration or schema;
- using unpublished SDK versions or private repositories.
