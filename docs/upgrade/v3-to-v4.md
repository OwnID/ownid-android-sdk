# Migration from Version 3 to Version 4

This page is a migration overview for moving an Android app from OwnID SDK version 3 to version 4. It is not a one-to-one API replacement table; use it to choose the version 4 integration path, then follow the linked version 4 documentation for detailed setup and examples.

## Contents

- [Key Differences Between Version 3 and Version 4](#key-differences-between-version-3-and-version-4)
- [Update SDK Dependencies](#update-sdk-dependencies)
- [Initialize and Configure OwnID](#initialize-and-configure-ownid)
- [Migrate Integration Responsibilities](#migrate-integration-responsibilities)
- [Migrate User Journeys](#migrate-user-journeys)
- [Update UI Customization](#update-ui-customization)

## Key Differences Between Version 3 and Version 4

Version 4 keeps the main product flows, but changes the SDK boundary. The SDK now focuses on OwnID runtime, flows, widgets, WebBridge, and provider contracts. Identity-platform work remains app-owned and is wired through flow callbacks or optional providers.

| Area | Version 3 | Version 4 |
| --- | --- | --- |
| Core SDK | Android API 23+; Kotlin 1.8.22; runtime plus Android View widgets | Android API 23+; Kotlin 1.8.22; runtime, providers, flows, Passkey Enrollment, WebBridge |
| Android View widgets | `OwnIdButton`, `OwnIdAuthButton`, XML attributes, ViewModels | Use Compose widgets |
| Compose SDK | Android API 23+; Kotlin 1.9.24; Compose 1.6.8; wraps View widgets | Android API 23+; Kotlin 1.9.25; Compose 1.7.8+; native Compose UI |
| Boost Flow | View/Compose buttons and ViewModels | Boost Flow widgets and callbacks; see [Migrate Boost Flow](#migrate-boost-flow) |
| Elite Flow | Start/page-action APIs | Elite Flow context, callbacks, and app-owned controller; see [Migrate Elite Flow](#migrate-elite-flow) |
| Passkey Enrollment | Credential Enrollment APIs or ViewModels | Passkey Enrollment; see [Migrate Passkey Enrollment](#migrate-passkey-enrollment) |
| WebBridge | Bridge injection APIs | Explicit create/attach lifecycle, trusted origins, and provider/context setup |
| Gigya SDK integration | `com.ownid.android-sdk:gigya`; native Gigya integration; Gigya Screen-Sets setup | Use providers and the source-only helper; see [Providers](../setup/providers.md#sap-customer-data-cloud-gigya) |
| Direct/custom integration | Direct response handling or Integration Component | Choose the matching v4 flow or feature: Boost Flow, Elite Flow, WebBridge, or Headless; wire providers where that flow or feature needs app-owned capabilities |
| Redirect SDK | `com.ownid.android-sdk:redirect` | No longer needed |

## Update SDK Dependencies

Use the smallest version 4 SDK artifact that covers the flow your app needs:

- Add `com.ownid.android-sdk:core` for SDK configuration, providers, Elite Flow, Passkey Enrollment, and WebBridge.
- Add `com.ownid.android-sdk:compose` for Boost Flow widgets, themes, colors, and reusable UI components. It depends on Core.
- Remove version 3 integration artifacts such as `com.ownid.android-sdk:gigya` and `com.ownid.android-sdk:redirect`.

For dependency snippets, start with [Install](../../README.md#install).

## Initialize and Configure OwnID

Version 3 apps commonly initialize the SDK with integration-specific file names and factory methods such as `createInstanceFromFile(...)` or `createGigyaInstanceFromFile(...)`.

In version 4, initialize OwnID once during app startup with one of the standard configuration sources:

- configure from code;
- configure from a JSON string;
- configure from an app asset file.

The default configuration file is now `OwnIdConfig.json`. Configuration still includes the OwnID application ID, environment, region, and language options, but it is no longer tied to a direct/custom/Gigya integration setup.

Do not reuse a version 3 configuration file as-is; recreate it using the version 4 configuration keys. Remove version 3 redirect and logging keys such as `redirectUrl`, `redirectUrlAndroid`, and `enableLogging`; configure logging with `OwnId.logger` when needed.

See [SDK Configuration](../setup/configuration.md) for the supported initialization APIs and configuration keys.

## Migrate Integration Responsibilities

Version 3 exposed two separate app-integration boundaries: Boost could delegate login and registration to `OwnIdIntegration`, while the Elite provider DSL exposed app-owned capabilities to the hosted flow. Version 4 does not merge these boundaries into one universal provider replacement. Choose the version 4 flow first, then migrate only the responsibilities that flow uses.

| Version 3 boundary | Version 4 migration |
| --- | --- |
| Boost `OwnIdIntegration.login(...)` | Handle the authenticated result in the Boost `onLogin` callback. Register [`sessionCreate`](../setup/providers.md#session-create) only when OwnID should turn that result into an app-defined session; otherwise create or restore the session from the callback response. |
| Boost `OwnIdIntegration.register(...)` | Use the Boost `onNewPasskey` registration handoff and keep account creation in the app and its backend. The same widget can call `onLogin` for an existing account. There is no version 4 account provider. |
| Elite `session { ... }` / `OwnIdProvider.SessionProvider` | Migrate the responsibility to [`sessionCreate`](../setup/providers.md#session-create). This is a semantic replacement, not a callback-signature replacement: version 3 returned `AuthResult`, while version 4 returns `Result<SessionOutput>`. |
| Elite `auth { password { ... } }` / `OwnIdProvider.AuthProvider.Password` | Register [`passwordAuthenticate`](../setup/providers.md#password-authenticate) only when an OwnID-hosted surface delegates password authentication to the app. The app's ordinary native password submission remains app-owned. |
| Elite `account { ... }` / `OwnIdProvider.AccountProvider` | There is no direct version 4 provider. Keep registration app-owned through the callback or handoff of the selected version 4 flow. |

Do not derive the version 4 provider set from every authentication function in the app. Register a provider only when the selected OwnID flow or feature calls that capability, after OwnID initialization and before that functionality starts. For exact provider parameters and result contracts, see [Providers](../setup/providers.md); for the native login and registration handoffs, see [Boost Flow](../flows/boost-flow.md).

If the version 3 integration also owned custom UI or state-machine behavior, choose the version 4 flow or feature that matches that journey: [Boost Flow](../flows/boost-flow.md), [Elite Flow](../flows/elite-flow.md), [WebBridge](../integration/webbridge.md), or [Headless](../flows/headless.md).

## Migrate User Journeys

### Migrate Boost Flow

Boost Flow still adds OwnID to existing native login and account-creation experiences. The migration changes the UI entry points and callback model.

Version 3 apps use Android View buttons, optional Compose wrappers, and registration/login view models. Version 4 removes the Android View widgets and exposes Boost Flow through Compose login and create-passkey widgets.

The app still keeps its existing login and registration forms. The login widget reports authenticated login results, and the create-passkey widget reports either a create-passkey result for the registration path or an existing-account login result.

See [Boost Flow](../flows/boost-flow.md) for the version 4 flow model and examples. See [Boost Widget Customization](../customization/boost-widgets.md) for widget appearance and state ownership.

### Migrate Elite Flow

Elite Flow still runs an OwnID-hosted authentication experience in an SDK-managed WebView. The migration changes how the app starts the flow and receives hosted-page events.

Version 3 Elite uses start/page-action APIs. Version 4 starts Elite Flow with an explicit `EliteFlowContext`. Hosted-page outcomes are delivered through event callbacks, and the running flow is represented by an app-owned controller.

See [Elite Flow](../flows/elite-flow.md) for the version 4 start API, callback semantics, and controller ownership.

### Migrate Passkey Enrollment

Version 4 keeps post-login credential enrollment as Passkey Enrollment.

Version 3 starts enrollment through Credential Enrollment APIs or view models with login ID and auth-token providers. Version 4 enrollment requires an OwnID Access Token for the signed-in user, and the SDK handles passkey creation and enrollment for that account.

See [Passkey Enrollment](../flows/passkey-enrollment.md) for the version 4 enrollment setup and examples.

### Migrate WebBridge

WebBridge still connects OwnID Web SDK pages inside an app-owned `WebView` to native OwnID capabilities. Version 4 changes setup from injection-style APIs to explicit bridge creation and attachment.

Version 3 apps inject a bridge into the `WebView` from the SDK. Version 4 apps create a fresh `WebBridge` from the current OwnID runtime, attach it to a trusted `WebView`, then load or reload the page after attachment succeeds.

Version 4 also makes WebBridge setup more explicit: configure trusted origins, providers, plugins, and context before using the bridge, and create a new `WebBridge` for each `WebView` session.

See [WebBridge](../integration/webbridge.md) for allowed origins, setup, lifecycle, and provider/context setup.

## Update UI Customization

Version 4 UI customization is tied to the version 4 UI features:

- use [Themes and Colors](../customization/themes-and-colors.md) for shared theme and color-token setup;
- use [Localization](../customization/localization.md) for OwnID UI text.

Do not carry over version 3 XML style attributes, Android View customization, or view-model customization directly. Recreate only the customization that the version 4 Compose UI supports.
