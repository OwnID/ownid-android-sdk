# WebBridge

[`WebBridge`](../../sdk-core/core/src/main/java/com/ownid/sdk/ui/webbridge/WebBridge.kt) connects the OwnID Web SDK running in an app-owned `WebView` to native OwnID Android SDK capabilities. Use it when your app hosts OwnID-enabled web authentication pages and the page needs native passkeys, stored user data, social sign-in, scoped SDK context, or app-owned [authentication providers](../setup/providers.md).

## Contents

- [Minimal Integration](#minimal-integration)
- [Prerequisites](#prerequisites)
- [Configuration Checklist](#configuration-checklist)
- [Attach and Lifecycle](#attach-and-lifecycle)
- [Allowed Origins](#allowed-origins)
- [Plugins](#plugins)
- [Runtime Errors](#runtime-errors)

## Minimal Integration

Create a fresh bridge for each `WebView` session, attach it to trusted origins before loading the page, then load the OwnID-enabled web page.

```kotlin
import com.ownid.sdk.OwnId

webView.settings.javaScriptEnabled = true

val bridge = OwnId.webBridge.create()

val error = bridge.attach(
    webView = webView,
    allowedOriginRules = setOf("https://login.example.com"),
    owner = viewLifecycleOwner // Optional when the WebView can resolve a lifecycle owner from the view tree.
)

if (error == null) {
    webView.loadUrl("https://login.example.com")
} else {
    // Map the bridge attachment failure to app diagnostics or an app-owned recovery path.
}
```

JavaScript must be enabled before loading the OwnID-enabled page. Enable DOM storage only when that page requires it, and keep the app's other `WebView` security settings in place.

`attach()` returns `null` on success and a `Throwable` on failure. A bridge instance can attach successfully only once; create a new `WebBridge` for each new `WebView` session.

## Prerequisites

- Add the Core SDK as described in [Install](../../README.md#install), initialize OwnID in [Configuration](../setup/configuration.md), and complete platform passkey setup in [Passkey Setup](../setup/passkeys.md).
- Use an app-owned `WebView` that loads a trusted app/tenant page with the OwnID Web SDK.
- Provide at least one valid trusted origin through server configuration, explicit `allowedOriginRules`, or both. If the first bridged page must work before server configuration is available, pass the required origins explicitly.

## Configuration Checklist

WebBridge attaches native SDK capabilities to an app-owned web session. The bridge captures available plugin instances and injection metadata when it is created and attached; configure only the areas that the hosted page can use.

| Area | Required when | Details |
| --- | --- | --- |
| Web view session | Always. | Use an app-owned `WebView`, create one bridge per session, and attach before loading the trusted page. See [Attach and Lifecycle](#attach-and-lifecycle). |
| Allowed origins | Always. | Provide trusted origins through server configuration, explicit `allowedOriginRules`, or both. See [Allowed Origins](#allowed-origins). |
| Passkeys | The hosted page uses passkey web actions. | Complete [platform passkey setup](../setup/passkeys.md). |
| Stored user | The hosted page uses stored-user web actions. | Built-in SDK capability; no app setup required. |
| Context | The hosted page requests native context. | Set context before bridge creation. See [Context](../setup/context.md) and [Namespace Handles](../setup/namespace-handles.md). |
| Google sign-in | The hosted page uses Google sign-in. | Register [`signInWithGoogle`](../setup/providers.md#sign-in-with-google). |
| Session creation | The hosted page requests app session creation. | Register [`sessionCreate`](../setup/providers.md#session-create). |
| Password authentication | The hosted page uses password authentication. | Register [`passwordAuthenticate`](../setup/providers.md#password-authenticate). |

Provider-backed web actions use app-owned provider capabilities from the SDK scope used to create the bridge. Normally register shared providers through top-level `OwnId.setProviders` during startup, before creating the bridge. Use `withProviders` only for a WebBridge session that needs different provider bindings. See [Providers](../setup/providers.md).

Set session-specific auth input close to the bridge with `withContext`:

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.model.Authz

val bridge = OwnId.webBridge
    .withContext { authz = Authz.fromToken(accessToken) }
    .create()
```

## Attach and Lifecycle

Create a fresh bridge for each app-owned `WebView` session. Configure providers, context, and default plugin factories before `create()` because the bridge receives those plugin instances at creation time. Configure bridge-specific plugins through `bridge.plugins` before `attach(...)`; the bridge captures that plugin registry and the effective allowed origins during attachment.

Attach the bridge before loading the trusted page. `attach()` returns `null` on success and the attachment failure as a `Throwable` otherwise. A bridge instance can attach successfully only once.

`attach()` must run on the UI thread and requires the device WebView provider to support the AndroidX WebKit `WEB_MESSAGE_LISTENER` and `DOCUMENT_START_SCRIPT` feature checks. It also requires a `LifecycleOwner`, resolved from the `WebView` tree or passed as `owner`.

The SDK detaches automatically when that lifecycle is destroyed. Call `detach()` only when the `WebView` session ends earlier than the lifecycle owner. `detach()` is a UI-thread no-op when the bridge is not attached.

## Allowed Origins

Allowed origins define which hosted pages can use the native bridge. During `attach(...)`, the SDK builds the effective allowlist from explicit `allowedOriginRules` and the latest available server configuration `webView.allowedOrigins`, normalizes the combined set, and applies it to bridge injection and message handling.

> [!WARNING]
> Attach WebBridge only to trusted origins that you control. Avoid `*` in production unless your security review explicitly approves it.

When server configuration already contains every trusted origin for the `WebView`, call `attach(webView = webView, owner = ...)` without explicit `allowedOriginRules`. Pass explicit `allowedOriginRules` when the first bridged page must work before server configuration is available, or when the app needs trusted local, staging, or environment-specific origins for that session.

Origin rule handling:

- Explicit rules and server-provided rules are merged before normalization.
- Invalid rules are skipped and logged.
- Attachment fails when no valid rule remains after normalization.
- Only `http` and `https` origins are accepted; custom schemes are rejected.
- Rules must describe origins, not full page URLs. Do not include paths, query strings, fragments, or user info.
- Missing scheme defaults to `https`.
- DNS wildcard rules are supported only as a leftmost subdomain wildcard, for example `https://*.example.com`; they do not match the bare `example.com` host.
- IPv4 literals and bracketed IPv6 literals are supported. Wildcards for IP hosts are rejected.
- `*` allows any origin and should be avoided outside explicitly reviewed cases.

| Rule | Normalized as | Notes |
| --- | --- | --- |
| `https://login.example.com` | `https://login.example.com` | Recommended production form. |
| `http://localhost:3000` | `http://localhost:3000` | Use only for local or trusted non-production environments. |
| `login.example.com` | `https://login.example.com` | Missing scheme defaults to `https`. |
| `https://login.example.com:8443` | `https://login.example.com:8443` | Explicit ports must be in `1...65535`. |
| `https://*.example.com` | `https://*.example.com` | Matches DNS subdomains only; does not match `example.com`. |
| `127.0.0.1` | `https://127.0.0.1` | IPv4 literals are supported. |
| `[2001:db8::1]` | `https://[2001:db8::1]` | IPv6 literals must be bracketed. |
| `*` | `*` | Allows any origin. Avoid in production. |

## Plugins

Most apps should use the default plugin set. Built-in plugins back the WebBridge capabilities listed in [Configuration Checklist](#configuration-checklist).

Customize plugins only when the hosted page and app define an additional [`WebBridgePlugin`](../../sdk-core/core/src/main/java/com/ownid/sdk/ui/webbridge/WebBridgePlugin.kt) namespace/action contract.

### Per-Bridge Plugins

Use [`bridge.plugins`](../../sdk-core/core/src/main/java/com/ownid/sdk/ui/webbridge/WebBridgePluginRegistry.kt) when only one `WebView` session needs a custom plugin. Add, replace, or remove plugins before `attach(...)`; the bridge snapshots this registry during attachment.

Register a fresh plugin instance for each bridge. A plugin with the same key replaces the previous plugin for that key.

### Default Plugins

Use [`OwnId.webBridge.defaultPluginFactories`](../../sdk-core/core/src/main/java/com/ownid/sdk/ui/webbridge/WebBridgePluginFactoryStore.kt) only when future bridges from that namespace should receive the same plugin change. Update default factories before `create()`; an already created bridge is changed through `bridge.plugins`.

Each factory must create a fresh plugin instance for each bridge.

## Runtime Errors

After a successful attach, WebBridge sends native capability results back to the hosted page. Expected runtime outcomes are reported through the hosted-page WebBridge contract when the incoming request can be handled safely. Invalid, untrusted, or incomplete requests are rejected by the bridge and should be treated as integration diagnostics.

Use an app-owned recovery path only when the bridge cannot attach or the app intentionally exits the `WebView` flow before loading the bridged page.
