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

Create a bridge for the app-owned `WebView`, attach it to trusted origins before loading the page, then load the OwnID-enabled web page.

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

## Prerequisites

- Add the Core SDK as described in [Install](../../README.md#install) and initialize OwnID in [Configuration](../setup/configuration.md).
- Use an app-owned `WebView` that loads a trusted app/tenant page with the OwnID Web SDK.
- The `WebView` must be associated with a live `Activity`.
- Pass required origins explicitly when the first attachment cannot depend on server configuration. See [Allowed Origins](#allowed-origins).

## Configuration Checklist

| Area | Required when | Details |
| --- | --- | --- |
| WebView attachment | Always. | Use an app-owned `WebView`, create a new bridge for each attachment, and attach before loading the trusted page. See [Attach and Lifecycle](#attach-and-lifecycle). |
| Allowed origins | Always. | Pass explicit origins for deterministic first attachment; origins already observed from server configuration are also merged. See [Allowed Origins](#allowed-origins). |
| Passkeys | The hosted page uses passkey web actions. | Complete [platform passkey setup](../setup/passkeys.md). |
| Stored user | The hosted page uses stored-user web actions. | Built-in SDK capability; no app setup required. |
| Context | The hosted page requests native context. | Set context before bridge creation. See [Context](../setup/context.md) and [Namespace Handles](../setup/namespace-handles.md). |
| Google sign-in | The hosted page uses Google sign-in. | Register [`signInWithGoogle`](../setup/providers.md#sign-in-with-google). |
| Session creation | The hosted page requests app session creation. | Register [`sessionCreate`](../setup/providers.md#session-create). |
| Password authentication | The hosted page uses password authentication. | Register [`passwordAuthenticate`](../setup/providers.md#password-authenticate). |

Provider-backed web actions use app-owned provider capabilities from the SDK scope used to create the bridge. Normally register shared providers through top-level `OwnId.setProviders` during startup, before creating the bridge. Use `withProviders` only for a bridge instance that needs different provider bindings. See [Providers](../setup/providers.md).

WebBridge uses the `Activity` associated with the attached `WebView`. An outer `uiContext` registration does not affect it.

Set bridge-specific auth input close to the bridge with `withContext`:

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.model.Authz

val bridge = OwnId.webBridge
    .withContext { authz = Authz.fromToken(accessToken) }
    .create()
```

## Attach and Lifecycle

Create a fresh bridge for each attachment to an app-owned `WebView`. Configure providers, context, and default plugin factories before `create()`, and configure `bridge.plugins` before `attach(...)`; later changes do not affect that bridge or attachment.

Attach the bridge before loading the trusted page. `attach()` returns `null` on success and the attachment failure as a `Throwable` otherwise. A bridge instance can attach successfully only once.

A failed attempt before the first successful attachment can be retried after correcting the cause.

`attach()` must run on the UI thread and requires the device WebView provider to support the AndroidX WebKit `WEB_MESSAGE_LISTENER` and `DOCUMENT_START_SCRIPT` feature checks. It also requires a `LifecycleOwner`, resolved from the `WebView` tree or passed as `owner`, and a `WebView` context that unwraps to a live `Activity`.

The bridge uses the `Activity` associated with the `WebView` at attachment. After `Activity` replacement, create a new bridge for a `WebView` associated with the replacement. If the bound `Activity` closes, later native UI requests are returned to the hosted page as bridge failures.

The SDK detaches automatically when the resolved or supplied lifecycle is destroyed. Call `detach()` only when the attached page is no longer used before the lifecycle owner is destroyed. `detach()` is a UI-thread no-op when the bridge is not attached.

## Allowed Origins

Allowed origins define which hosted pages can use the native bridge. At `attach(...)`, the bridge merges explicit `allowedOriginRules` with the server-provided `webView.allowedOrigins` already observed by that bridge, then normalizes the effective set. `attach(...)` does not fetch or wait for server configuration. Server updates observed after a successful attachment do not change that attachment.

> [!WARNING]
> Attach WebBridge only to trusted origins that you control. Avoid `*` in production unless your security review explicitly approves it.

Pass explicit `allowedOriginRules` when the first attachment must succeed deterministically. If no valid explicit or already observed server origin is available, attachment fails. Before the first successful attachment, the same bridge may be retried after correcting the cause.

Origin rule handling:

- Explicit rules and server-provided rules already observed by this bridge are merged before normalization.
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

Use [`bridge.plugins`](../../sdk-core/core/src/main/java/com/ownid/sdk/ui/webbridge/WebBridgePluginRegistry.kt) when only one bridge instance needs a custom plugin. Add, replace, or remove plugins before `attach(...)`; the bridge snapshots this registry during attachment.

Register a fresh plugin instance for each bridge. A plugin with the same key replaces the previous plugin for that key.

### Default Plugins

Use [`OwnId.webBridge.defaultPluginFactories`](../../sdk-core/core/src/main/java/com/ownid/sdk/ui/webbridge/WebBridgePluginFactoryStore.kt) only when future bridges from that namespace should receive the same plugin change. Update default factories before `create()`; an already created bridge is changed through `bridge.plugins`.

Each factory must return a fresh plugin instance for every bridge.

## Runtime Errors

After a successful attach, WebBridge sends native capability results back to the hosted page. Expected runtime outcomes are reported through the hosted-page WebBridge contract when the incoming request can be handled safely. Invalid, untrusted, or incomplete requests are rejected by the bridge and should be treated as integration diagnostics.

Use an app-owned recovery path when the bridge cannot attach, when a request is rejected because its origin, frame, or payload is not trusted or valid, or when the app intentionally exits the `WebView` flow before loading the bridged page. Treat cancellation after `detach()` as a normal session-ending outcome and make page-side requests tolerant of a missing callback.
