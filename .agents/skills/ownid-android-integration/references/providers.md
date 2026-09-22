# Providers

Use this reference when an OwnID Android SDK v4 integration needs app-owned
provider callbacks. Provider parameters, return types, helper setup, and full
snippets live in the SDK docs, source, and provider examples.

Source docs:

- `../../../../docs/setup/providers.md`
- `../../../../docs/setup/access-token.md`
- `../../../../docs/setup/namespace-handles.md`

SDK contracts: `OwnId.setProviders`, `withProviders`,
`OwnIdProvidersRegistrar`, `SessionCreate`, `PasswordAuthenticate`,
`SignInWithSocial`, and `UiContextProvider`.

## Contents

- [Boundary](#boundary)
- [Integration Rules](#integration-rules)
- [Scope Semantics](#scope-semantics)
- [Provider Contracts](#provider-contracts)
- [Source-Only Helpers](#source-only-helpers)
- [Review Checks](#review-checks)

## Boundary

Providers connect OwnID SDK surfaces to app-owned capabilities. The app still
owns session state, password verification, token storage, logout, navigation,
analytics, backend identity model, social SDK setup, and error handling.

Provider categories:

- `sessionCreate`: turn an OwnID-authenticated result into an app session.
- `passwordAuthenticate`: let a supported OwnID surface ask the app/backend to
  verify a password. Keep passwords inside the app/identity-provider
  authentication boundary.
- Social providers such as Google: let supported OwnID social steps call the
  app's provider SDK and receive a provider ID token.
- `uiContext`: select the Android Activity used for SDK native UI. See
  [UI Context](../../../../docs/setup/providers.md#ui-context).
- Source-only identity helpers can wire provider callbacks for a specific
  identity platform.

## Integration Rules

- Read the selected surface reference first. Register only providers that the
  selected surface or hosted page can actually call.
- Register providers after successful SDK initialization and before starting
  the SDK surface that needs them.
- Register providers as high as practical, normally once with
  `OwnId.setProviders` during startup.
- Use `withProviders` only when one concrete SDK call, flow run, or bridge
  instance intentionally needs different provider behavior from the app-wide
  bindings.
- For a direct UI-backed call with a known host, bind `uiContext(activity)` in
  scoped `withProviders` on the main thread. Use a top-level UI-context binding
  only when subsequent top-level calls should follow that same logical
  Activity. Default widgets and WebBridge bind their own hosts.
- Keep the registrar and provider builder objects inside the provider block.
- Use `isAvailable` when a provider can handle only some requests.
- Complete each provider registration with its required handler: `create` for
  `sessionCreate`, `authenticate` for `passwordAuthenticate`, and `signIn` for
  `signInWithGoogle`.

## Scope Semantics

- `OwnId.setProviders` updates top-level provider bindings in place. Provider
  types declared in the block replace existing bindings of the same type;
  supported provider types omitted from the block remain unchanged.
- `withProviders` returns a derived handle when the block registers at
  least one provider. The child inherits other bindings and overrides only the
  provider types registered in the block.
- Concrete `flows` and `webBridge` namespace handles expose
  `withProviders`, not in-place `setProviders`.
- An empty `withProviders` or `OwnId.setProviders` block is a no-op.
- Keep using the returned derived handle. Calling back into top-level `OwnId`
  entry points uses the top-level bindings again.

## Provider Contracts

Open `../../../../docs/setup/providers.md` before writing concrete provider
code. Verify exact parameters and return values against production API/source
and maintained provider/demo examples for the selected SDK release.

Critical contracts:

- `sessionCreate.create` receives authenticated login/session data and returns
  Kotlin `Result<SessionOutput>`.
- `passwordAuthenticate.authenticate` verifies user-entered passwords through
  the app backend or identity provider and returns Kotlin
  `Result<SessionOutput>`.
- Google/social `signIn(activity, params)` runs on the main thread and returns
  `SignInWithSocial.Result`.
- A custom `uiContext` provider is authoritative; its failure does not fall
  back to an inherited provider.
- `sessionCreate` and `passwordAuthenticate` handlers are invoked on the main
  thread. Social `isAvailable`, `cancel`, and `signOut` handlers have no
  main-thread guarantee.
- Provider callbacks must return after app-owned work finishes. If that work
  uses blocking APIs, dispatch it away from the main thread inside the callback
  and await completion.
- Provider failure handling is surface-specific. In Elite Flow and WebBridge,
  `sessionCreate` and `passwordAuthenticate` failures are returned to the hosted
  page as a failed provider result, not as `FlowResult.Failure`; SDK/WebView
  infrastructure failures are reported separately. Direct API calls report a
  missing or failed provider through that call's typed failure result.

## Source-Only Helpers

Copy OwnID-published provider helper source files unchanged into the app source
set. Keep app-owned dependencies, configuration, and provider registration
outside the helper.

- Google helper: `../../../../providers/OwnIdSignInWithGoogleProvider.kt`.
- For SAP Customer Data Cloud/Gigya, use the public providers docs and the
  source-only helper `../../../../providers/OwnIdGigyaProviders.kt`.

## Review Checks

- Shared providers are registered through top-level `OwnId.setProviders`;
  scoped overrides stay with the derived surface handle that uses them.
- UI host selection is explicit where the caller knows the Activity; default
  widget and WebBridge bindings are left to those surfaces.
- The integration registers only provider types the selected surface can call.
- Passwords remain inside the app backend or identity-provider boundary.
- Provider callbacks return the exact result types required by the selected SDK
  release.
- External console, OAuth, cloud, signing, backend, and production tenant setup
  is tracked as explicit external work.
