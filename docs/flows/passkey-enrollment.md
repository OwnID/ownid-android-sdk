# Passkey Enrollment

Passkey Enrollment adds a passkey for a user who is already signed in to your app. In this guide, a signed-in user means your app has a valid OwnID Access Token for the current user.

Use this flow from account security, a post-login prompt, or a registration completion screen.

## Minimal Integration

Use the current user's Access Token, check availability before enabling enrollment, then keep the returned controller until the flow settles.

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.flow.onCanceled
import com.ownid.sdk.flow.onError
import com.ownid.sdk.flow.onSuccess
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.onUnavailable
import com.ownid.sdk.namespace.availability
import com.ownid.sdk.namespace.start

class AccountViewModel : ViewModel() {
    private var passkeyEnrollJob: Job? = null

    fun enrollPasskey(accessToken: AccessToken) {
        if (passkeyEnrollJob != null) return

        passkeyEnrollJob = viewModelScope.launch {
            try {
                val passkeyEnroll = OwnId.headless
                    .withContext { authz = Authz.fromToken(accessToken) }
                    .passkeys.enroll

                passkeyEnroll.availability().onUnavailable { message ->
                    // Hide or disable the action.
                    // Use message for integration diagnostics; do not show it as end-user copy.
                    return@launch
                }

                val controller = passkeyEnroll.start()
                addCloseable("ownid-passkey-enroll", controller)

                controller.whenSettled()
                    .onSuccess { response ->
                        // response.loginId identifies the user whose passkey was enrolled.
                    }
                    .onCanceled { reason ->
                        // User closed platform UI or the flow was canceled.
                    }
                    .onError { error ->
                        // Show a retryable error or log diagnostics.
                    }
            } finally {
                passkeyEnrollJob = null
            }
        }
    }
}
```

## Examples

- [Current-user enrollment ViewModel](../../demoBase/app/src/main/java/com/ownid/demo/user/CurrentUserViewModel.kt)
- [Current-user enrollment screen](../../demoBase/app/src/main/java/com/ownid/demo/user/CurrentUserScreen.kt)
- [Headless enrollment after login](../../demoBase/app/src/main/java/com/ownid/demo/headless/HeadlessViewModel.kt)
- [Advanced passkey examples](../../demoAdvanced/app/src/main/java/com/ownid/demo/headless/HeadlessViewModel.kt)

## Prerequisites

- Add the Core SDK as described in [Install](../../README.md#install), initialize OwnID in [Configuration](../setup/configuration.md), and complete platform passkey setup in [Passkey Setup](../setup/passkeys.md).
- Start enrollment only after your app has a valid [OwnID Access Token](../setup/access-token.md) for the current user. Pass it in `PasskeyEnrollFlowContext`, or provide it through the current OwnID context with `withContext` or `setContext`; see [Context](../setup/context.md).
- Keep the current app session and account/security UI available for unavailable, canceled, or failed enrollment attempts.

## Flow Shape

```mermaid
flowchart LR
    A["App has valid<br/>Access Token"] --> B["Build enrollment context<br/>(Access Token +<br/>optional Proof Token)"]
    B --> C["Check availability<br/>for that context"]
    C --> D{"Available?"}
    D -->|"No"| E["Hide or<br/>disable action"]
    D -->|"Yes"| F["Start Passkey<br/>Enrollment"]
    F --> G{"Proof Token<br/>present?"}
    G -->|"No"| H["SDK creates<br/>passkey locally"]
    H --> I["SDK enrolls<br/>created passkey<br/>with Access Token"]
    G -->|"Yes"| J["SDK enrolls<br/>Proof Token<br/>with Access Token"]
    I --> K["App handles result"]
    J --> K
```

## Integration Details

Use Passkey Enrollment from a signed-in account UI. The flow requires an Access Token for the current user and can optionally take a Proof Token when a previous OwnID step already produced proof for enrollment.

### Context and Availability

Availability is branch-specific. Build the [`PasskeyEnrollFlowContext`](../../sdk-core/core/src/main/java/com/ownid/sdk/flow/passkey/PasskeyEnrollFlowContext.kt) for the attempt first, then use that same context for both `availability(...)` and `start(...)`.

| Context | Availability checks | `start(...)` behavior |
| --- | --- | --- |
| `accessToken` | Preflight for SDK dependencies and required input on the local passkey-creation path. | Creates a passkey locally, then enrolls it with the Access Token. |
| `accessToken` + `proofToken` | Preflight for enrollment dependencies and required tokens. | Skips local passkey creation and enrolls the Proof Token. |
| No Access Token | Unavailable before enrollment can start. | Do not start enrollment from signed-out state. |

Availability is a preflight check, not a completion guarantee. Platform passkey UI, device or provider state, and backend token validation can still fail or cancel after `start(...)`.

Use `isAvailable(context)` when you only need a Boolean. Use `availability(context).onUnavailable { message -> ... }` when you want the SDK diagnostic reason for integration logging; do not show that message as end-user copy.

### Enrollment Paths

- **Create and enroll passkey:** Use `accessToken` when the current user should create a new passkey on the device. The SDK runs local passkey creation, so platform passkey UI can appear, then consumes the local proof internally and enrolls the created passkey with the Access Token. Success returns `FlowResult.Success` with `PasskeyEnrollFlow.Response.loginId`; `ownIdData` is not returned.

- **Enroll existing proof:** Use `accessToken` plus `proofToken` when a previous OwnID step already returned the Proof Token needed to enroll the passkey. The SDK skips local passkey creation and enrolls the Proof Token with the Access Token. Success returns `FlowResult.Success` with `PasskeyEnrollFlow.Response.loginId`.

```kotlin
val context = PasskeyEnrollFlowContext {
    this.accessToken = accessToken

    // Optional. Set only when a previous OwnID step already returned a Proof Token.
    // When set, availability and start use the server-enrollment path.
    this.proofToken = proofToken
}

val passkeyEnroll = OwnId.headless.passkeys.enroll

val availability = passkeyEnroll.availability(context)
// If available, start with the same context:
// val controller = passkeyEnroll.start(context)
```

## Controller Ownership

A Passkey Enrollment controller represents one enrollment attempt for the current signed-in user. Keep the returned controller until `whenSettled()` returns.

If the owning `ViewModel` or lifecycle is torn down before settlement, call `close()` or `abort(reason)`. After settlement, clear the stored controller and update the account/security UI from the result.

Start a new enrollment attempt only after the previous controller has settled.

## Results and Error Handling

`whenSettled()` returns [`FlowResult<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure>`](../../sdk-core/core/src/main/java/com/ownid/sdk/flow/Flow.kt). The response and failure types are declared in [`PasskeyEnrollFlow`](../../sdk-core/core/src/main/java/com/ownid/sdk/flow/passkey/PasskeyEnrollFlow.kt).

| Result | Meaning | App handling |
| --- | --- | --- |
| `FlowResult.Success` | Server enrollment completed for the returned `loginId`. | Refresh the account/security UI for that user and mark passkey enrollment complete. |
| `FlowResult.Canceled` | The user, app, SDK scope, or platform passkey UI ended the attempt before server enrollment completed. | Clear the running controller, keep the current app session intact, and let the user retry when appropriate. |
| `FlowResult.Failure` | The flow could not resolve required input, start or complete a required passkey step, or hit an unexpected SDK/runtime state. | Keep the current app session intact, branch on `PasskeyEnrollFlowFailure`, and show app-owned retry or fallback UI. |

For `FlowResult.Failure`, branch on `PasskeyEnrollFlowFailure`:

| Failure | What it usually means | Recommended handling |
| --- | --- | --- |
| `Input.MissingAccessToken` | Enrollment was started without an OwnID Access Token for the current signed-in user. | Do not continue this attempt. Require a valid signed-in user context before showing or starting passkey enrollment. |
| `Input.UnresolvedLoginId` | The Access Token was present, but the SDK could not resolve a usable `loginId` from it. | Stop this attempt. Check token contents, token forwarding, and login ID validation setup; ask the user to re-authenticate only when the app cannot recover the current session cleanly. |
| `OperationFailed` | A required passkey creation or enrollment step could not start, was unavailable, or failed. | Keep the account/security UI usable. Offer retry or another security method, and inspect `operationType`, `operationId`, `operationFailure`, `error`, and `message` when routing or troubleshooting needs more detail. |
| `Unexpected` | The flow hit an SDK/runtime error or internal invariant outside expected input, cancellation, or passkey operation failure paths. | End the attempt, show a generic retry/fallback state, and log diagnostics. Start a new attempt only from a clean UI state. |

Use the failure's `errorCode` as a localization key when showing OwnID-related copy. Treat `message` and nested errors as diagnostics, not end-user text.

## Security and Data Handling

- Treat Access Token and Proof Token values as sensitive authentication handoff values.
- Do not log tokens, platform credential payloads, provider payloads, or full flow results.
- Passkey Enrollment does not create, refresh, or replace the app session. Keep the current app session explicit and intact when enrollment succeeds, is canceled, or fails.
- The flow returns the enrolled `loginId` only. It consumes local attestation proof internally and does not return `ownIdData`.
