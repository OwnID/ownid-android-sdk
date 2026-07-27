# WebBridge

Use this reference when integrating OwnID SDK v4 WebBridge into an Android
app-owned `WebView`.

Full public docs: `../../../../docs/integration/webbridge.md`.

## Contents

- [Scope](#scope)
- [Before Editing](#before-editing)
- [Providers And Context](#providers-and-context)
- [Origins And Attachment](#origins-and-attachment)
- [Implementation Pattern](#implementation-pattern)
- [Plugins And Lifecycle](#plugins-and-lifecycle)
- [Validation](#validation)

## Scope

WebBridge is for an app-owned Android `WebView` that loads an app/tenant page
where the OwnID Web SDK is initialized. The app owns the `WebView`, lifecycle,
page URL, trusted origins, and web content. The native SDK supplies the bridge
so that page can call selected native SDK capabilities.

Choose WebBridge when the page needs native OwnID SDK capabilities such as
passkeys, stored user, scoped context, Google sign-in, `sessionCreate`, or
`passwordAuthenticate`.

Add `com.ownid.android-sdk:core` and complete Android passkey prerequisites.
WebBridge has no WebBridge-specific manifest entry, permission, activity,
service, or metadata setup.

## Before Editing

Collect:

- SDK initialization location;
- app-owned `WebView` instance and lifecycle owner source;
- page URL and trusted origins;
- whether server-provided `webView.allowedOrigins` is enough or explicit
  `allowedOriginRules` are needed;
- WebBridge capabilities used by the page;
- existing passkey setup and provider setup.

Resolve trusted origins, relying-party domains, provider setup, backend session
contracts, and wildcard-origin policy before attaching the bridge.

## Providers And Context

Register shared providers through top-level `OwnId.setProviders` for
provider-backed capabilities the page can call. Use scoped `withProviders` when
this `WebView` session needs behavior different from the top-level bindings.

Put session-local auth input in `withContext` before `OwnId.webBridge.create()`.
Changing context or providers after bridge creation does not update that
bridge.

Use the public WebBridge docs for the capability table and origin rules. Use
the providers, context, and namespace-handle references for their detailed
contracts.

## Origins And Attachment

Attach only to reviewed trusted origins. Pass explicit `allowedOriginRules`
when server config may not be available yet or when the session needs a
local/tenant-specific trusted origin. Treat `*` as a production security-review
decision.

Attach before `loadUrl(...)`; reload the page after a successful attach if it
was already loaded. `attach(...)` runs on the UI thread, requires a
`LifecycleOwner` resolved from the `WebView` tree or passed as `owner`, and
requires AndroidX WebKit support for `WEB_MESSAGE_LISTENER` and
`DOCUMENT_START_SCRIPT`.

Enable JavaScript on the trusted-page `WebView` before loading the OwnID-enabled
page; enable DOM storage only when that page requires it. Keep the host app's
normal WebView hardening in place, including its mixed-content, file/content
access, navigation, and certificate-error policies.

`attach(...)` returns `null` on success and a `Throwable` on failure. Handle
attachment failure before loading a page that requires native bridge
capabilities.

## Implementation Pattern

```kotlin
webView.settings.javaScriptEnabled = true

val bridge = OwnId.webBridge
    .withContext {
        authz = Authz.fromToken(accessToken)
        accountDisplayName = displayName
    }
    .create()

val error = bridge.attach(
    webView = webView,
    allowedOriginRules = setOf("https://login.example.com"),
    owner = viewLifecycleOwner
)

if (error == null) {
    webView.loadUrl("https://login.example.com")
} else {
    // Map the bridge attachment failure to app diagnostics or fallback UI.
}
```

Omit `withContext` when the page does not need scoped auth/context input.

## Plugins And Lifecycle

Most apps use the default plugin set. Add or remove per-bridge custom plugins
through `bridge.plugins` before `attach(...)`, and use fresh plugin instances
per bridge. Change `OwnId.webBridge.defaultPluginFactories` before `create()`
so newly created bridges receive those defaults.

Create one fresh bridge per `WebView` session. Keep a strong reference while
the page is active. Call `detach()` if the session ends before its lifecycle
owner is destroyed; owner destruction detaches automatically.

On API 26+, if `WebViewClient.onRenderProcessGone(...)` reports renderer
termination, call `bridge.detach(webViewRendererGone = true)` before removing
and destroying the affected `WebView` and clearing its references. Skip
ordinary detach for that unusable `WebView`. Return `true` after the app has
followed Android's WebView termination contract:
<https://developer.android.com/develop/ui/views/layout/webapps/handle-termination>.

Use WebBridge docs for the integration flow, production API/source for exact
calls and lifecycle, and a host-app WebView pattern for ownership and teardown.

## Validation

- OwnID initializes before bridge creation.
- The trusted page loads OwnID Web SDK.
- `attach(...)` succeeds for the intended origin before page load or after
  reload.
- Attachment failure uses an app fallback before loading a page that requires
  the bridge.
- Capabilities used by the page work with the active scope.
- Session teardown detaches the bridge and a new `WebView` session creates a
  new bridge.
- Renderer termination uses `detach(webViewRendererGone = true)` and disposes
  only the affected `WebView`.
