# Elite Flow

Elite Flow renders an OwnID-hosted page inside an SDK-managed WebView. The OwnID Web SDK runs the hosted authentication experience, and the native SDK forwards hosted-page events into Elite Flow callbacks through the [OwnID WebBridge](../integration/webbridge.md).

Use Elite when OwnID should own the primary authentication UI and your app only needs to handle final native handoff points.

## Minimal Integration

Start Elite Flow from the app entry point, keep the returned controller while the WebView flow is active, and handle final hosted events from [`EliteFlowContext.events`](../../sdk-core/core/src/main/java/com/ownid/sdk/flow/elite/EliteFlowContext.kt).

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.flow.elite.EliteFlowContext
import com.ownid.sdk.flow.onCanceled
import com.ownid.sdk.flow.onError
import com.ownid.sdk.flow.onSuccess

class EliteFlowViewModel : ViewModel() {
    data class PendingRegistration(
        val email: String,
        val ownIdData: String?
    )

    var pendingRegistration: PendingRegistration? = null
        private set

    fun startEliteFlow() {
        viewModelScope.launch {
            val context = EliteFlowContext {
                // Hosted page events forwarded from the OwnID Web SDK through WebBridge.
                events {
                    onNativeAction { loginId, ownIdData, accessToken ->
                        pendingRegistration = PendingRegistration(loginId, ownIdData)
                        // accessToken is optional. Use it only if your app handoff needs it.
                    }

                    onFinish { loginId, authMethod, accessToken ->
                        // Create or restore your app session.
                    }

                    onError { error ->
                        // Show an app-owned error or fallback state.
                    }

                    onClose {
                        // Return the app UI to an idle state.
                    }
                }
            }

            val controller = OwnId.flows.elite.start(context)
                .also { addCloseable("ownid_elite_controller", it) }

            // Native controller settlement for the SDK-managed WebView run.
            controller.whenSettled()
                .onSuccess {
                    // A hosted terminal event handler completed.
                    // Do not create an app session here; handle auth handoff in onFinish or onNativeAction when applicable.
                }
                .onCanceled { reason ->
                    // The app or SDK canceled before a hosted terminal event completed.
                }
                .onError { error ->
                    // SDK/WebBridge failure before a hosted terminal event completed.
                }
        }
    }
}
```

## Examples

- [Base Elite Flow ViewModel example](../../demoBase/app/src/main/java/com/ownid/demo/elite/EliteFlowViewModel.kt)
- [Base Elite Flow screen example](../../demoBase/app/src/main/java/com/ownid/demo/elite/EliteFlowScreen.kt)

## Prerequisites

- Add the Core SDK as described in [Install](../../README.md#install), initialize OwnID in [Configuration](../setup/configuration.md), and complete platform passkey setup in [Passkey Setup](../setup/passkeys.md).
- Register only the optional providers that match the hosted experience in your integration, such as [`sessionCreate`](../setup/providers.md#session-create), [`passwordAuthenticate`](../setup/providers.md#password-authenticate), and social sign-in providers like [Sign in with Google](../setup/providers.md#sign-in-with-google).
- Keep the app's existing authentication, registration, and session paths available for native handoff, hosted errors, and user close.

## Flow Shape

```mermaid
flowchart LR
    A["App opens<br/>Elite entry point"] --> B["Native SDK starts<br/>SDK-managed WebView"]
    B --> C{"WebView and<br/>WebBridge ready?"}
    C -->|"Yes"| D["OwnID Web SDK<br/>runs hosted flow"]
    C -->|"No"| E["Controller returns<br/>failure or cancellation"]
    D --> F["OwnID WebBridge<br/>forwards hosted event"]
    F --> G{"Hosted event"}
    G -->|"onNativeAction"| H["App completes registration<br/>or native handoff"]
    G -->|"onFinish"| I["App handles final<br/>auth handoff"]
    G -->|"onError"| J["App shows error or<br/>fallback state"]
    G -->|"onClose"| K["App returns to<br/>idle state"]
    E --> L["Controller settles"]
    H --> L
    I --> L
    J --> L
    K --> L
```

## Handling Elite Flow Completion

Elite Flow has two signal layers. Hosted events come from the OwnID Web SDK and describe what happened in the hosted page. Controller results come from the native SDK and describe how the native WebView run settled.

### Hosted Events

Configure Elite Flow callbacks with `EliteFlowContext.events`.

| Callback | When it runs | App handling |
| --- | --- | --- |
| `onNativeAction` | The hosted page asks the native app to complete an app-owned native step, often registration. | Complete the app-owned native step. For registration, store the pending `loginId` and opaque `ownIdData`; submit and save `ownIdData` exactly as returned after registration succeeds. |
| `onFinish` | Hosted authentication completed successfully. | Complete the app authentication handoff at the app boundary. Use the optional [OwnID Access Token](../setup/access-token.md) only if your integration needs it. |
| `onError` | The hosted page reported an error. | Show an app-owned error or fallback state. Treat the optional error string as hosted-page context, not a native SDK failure. |
| `onClose` | The hosted page reported a close event. | Clear transient state and return to idle UI. Native user close or controller cancellation is reported through the controller result instead. |

All hosted event callbacks run on the main thread and are terminal after they return successfully. Then the SDK closes the WebView and the native controller settles successfully. If the SDK cannot process the hosted event, the hosted page receives a bridge error and the native run does not settle successfully from that event.

### Controller Result

The controller is a high-level handle for the running WebView flow. Keep it until settlement when you need to await the result, use `abort` or `close` for app-driven cancellation or lifecycle cleanup, and await `whenSettled()` for cleanup and for native cancellation or infrastructure failures.

The same flow instance is single-run: repeated `start(...)` calls return the same controller and do not start another WebView run.

`whenSettled()` returns [`FlowResult<Unit, EliteFlowFailure>`](../../sdk-core/core/src/main/java/com/ownid/sdk/flow/Flow.kt). The failure type is declared in [`EliteFlowFailure`](../../sdk-core/core/src/main/java/com/ownid/sdk/flow/elite/EliteFlow.kt).

| Result | Meaning | App handling |
| --- | --- | --- |
| `FlowResult.Success` | A hosted terminal event handler returned successfully. This can follow `onFinish`, `onNativeAction`, `onError`, or `onClose`; it does not mean hosted authentication succeeded. | Use this for cleanup. Continue authentication from `onFinish` or `onNativeAction` when applicable. |
| `FlowResult.Canceled` | The app aborted or closed the controller, the SDK shut down, or the WebView was canceled or detached before a hosted terminal event completed. | Clear the running controller and return to an idle or retry state. |
| `FlowResult.Failure` | The native SDK or WebBridge could not start or run, or the WebView failed before a hosted terminal event completed. | Show an error or fallback state and keep fallback authentication available. |

Use `FlowResult.Failure` only for native SDK/WebBridge infrastructure failures:

| Failure | What it usually means | Recommended handling |
| --- | --- | --- |
| `OperationFailed` | WebBridge was unavailable, could not start, or settled with a native failure. | End the current WebView attempt, keep another authentication path available, and inspect WebBridge configuration, allowed origin, native container/UI setup, and diagnostics. |
| `Unexpected` | The native Elite flow stopped because of an unexpected SDK/runtime failure or internal invariant violation. | Show a generic fallback state, log diagnostics, and start a new attempt only after the app returns to a clean state. |

Use the failure's `errorCode` as a localization key when showing OwnID-related copy. Treat `message` and nested errors as diagnostics, not hosted-page business state.

## Customization

Use `EliteFlowContext.options` to configure SDK-managed WebView options for an Elite Flow session. Most integrations can leave these values unset and use server configuration plus SDK defaults. Set options only when the app needs to override hosted content inputs, WebView diagnostics, or Android system bar appearance for this specific flow.

```kotlin
val context = EliteFlowContext {
    options {
        baseUrl = "https://webview.ownid.com"
        statusBarColor = Color.WHITE
        navigationBarColor = Color.WHITE
    }
}
```

Common options:

| Option | Use |
| --- | --- |
| `baseUrl` | Base URL used for the HTML page. Leave it unset to use server configuration, then the SDK fallback `https://webview.ownid.com`. |
| `userAgent` | Custom WebView User-Agent string. Leave it unset to use the SDK's default local User-Agent value. |
| `statusBarColor` | Android status bar color for the SDK-managed WebView container. Leave it unset to keep the container default, currently white. |
| `navigationBarColor` | Android navigation bar color for the SDK-managed WebView container. Leave it unset to keep the container default, currently white. |

When the default WebBridge integration is used, `baseUrl` is also added to the bridge allowed-origin set. Use an origin-shaped URL such as `https://example.com` without a path, query, or fragment unless you intentionally need page URL origin derivation.

## Security and Data Handling

- Treat `accessToken` and `ownIdData` as sensitive handoff values.
- Do not log provider tokens, OwnID Access Tokens, session payloads, `ownIdData`, or full hosted-event payloads.
- Keep fallback authentication available when the hosted page or native bridge cannot complete.
