# Headless

## Overview

Headless integration lets your app own the user interface and authentication state machine while OwnID runs discovery, passkeys, verification, login, and passkey enrollment steps.

Use Headless when the app needs custom screens, navigation, analytics, loading states, fallback ordering, or registration/session handoff that is different from the SDK-managed flows.

## Contents

- [Examples](#examples)
- [Prerequisites](#prerequisites)
- [Flow Shape](#flow-shape)
- [Headless Entry Points](#headless-entry-points)
- [Login Flow Integration](#login-flow-integration)
- [Additional Authentication Requirements](#additional-authentication-requirements)
- [Supported Headless Steps](#supported-headless-steps)
- [Post-Login Passkey Enrollment](#post-login-passkey-enrollment)
- [API Failure Handling](#api-failure-handling)
- [Operation Failure Handling](#operation-failure-handling)
- [Security and Data Handling](#security-and-data-handling)
- [Related Documentation](#related-documentation)

## Examples

- [Base Headless example](../../demoBase/app/src/main/java/com/ownid/demo/headless/HeadlessViewModel.kt)
- [Advanced Headless example](../../demoAdvanced/app/src/main/java/com/ownid/demo/headless/HeadlessViewModel.kt)

## Prerequisites

- Add the Core SDK as described in [Install](../../README.md#install), initialize OwnID in [Configuration](../setup/configuration.md), and complete platform passkey setup in [Enable Passkeys](../../README.md#enable-passkeys).
- Decide which headless steps the app will support and build the matching app-owned UI: login ID collection, loading and error states, passkey authentication, email or phone verification, fallback, and optional passkey enrollment.
- Keep the app's existing authentication and session paths available: scope each attempt with [Context](../setup/context.md) and [Namespace Handles](../setup/namespace-handles.md), complete app login from [`LoginResponse.Success`](../../sdk-core/core/src/main/java/com/ownid/sdk/model/LoginId.kt), and route unsupported, canceled, or failed steps to fallback.

## Flow Shape

```mermaid
flowchart TD
    A["App collects<br/>login ID"] --> B["Scope Headless<br/>with login ID"]
    B --> C["auth.discover"]
    C --> D{"LoginResponse"}
    D -->|Success| E["Create app session"]
    E --> F{"Offer passkey<br/>enrollment?"}
    F -->|Yes| G["passkeys.enroll"]
    G --> H["Signed-in app state"]
    F -->|No| H
    D -->|AuthRequired| I["Read authRequirements"]
    I --> J{"Can app complete<br/>a supported step?"}
    J -->|Yes| K{"Choose<br/>supported step"}
    K -->|Passkey auth| L["passkeys.auth"]
    K -->|Email or phone verification| M["Start verification<br/>challenge"]
    M --> N["completeWithCode /<br/>resend / cancel"]
    L -->|Access Token| O["Scope Headless<br/>with Access Token"]
    N -->|Access Token| O
    O --> P["auth.login"]
    P --> Q["Handle returned<br/>LoginResponse"]
    Q -.-> D
    J -->|No| R["Exit Headless path<br/>or use app auth"]
    N -->|Challenge failed or abandoned| R
    D -->|AccountNotFound| S["Account creation<br/>or fallback"]
    D -->|AccountBlocked| T["Blocked-account handling"]
```

## Headless Entry Points

Use these `OwnId.headless` entries to compose the app-owned login flow:

- Start with `auth.discover.start()` from a login ID scope.
- If OwnID returns `LoginResponse.AuthRequired`, choose a supported operation from `authRequirements`.
- Continue with `auth.login.start()` after passkey authentication or verification returns an Access Token.
- Use `passkeys.enroll` only after successful authentication, when the app wants to offer passkey enrollment.
- Operation and flow entries return app-owned controllers; call `whenSettled()` for terminal [`OperationResult`](../../sdk-core/core/src/main/java/com/ownid/sdk/ops/OperationCapability.kt) or [`FlowResult`](../../sdk-core/core/src/main/java/com/ownid/sdk/flow/Flow.kt).
- Availability can report missing SDK setup before start; starting an entry can still fail if required SDK dependencies are unavailable.

| Entry | Result | Use |
| --- | --- | --- |
| `auth.discover.start()` | [`ApiResult<LoginResponse, DiscoverApiFailure>`](../../sdk-core/core/src/main/java/com/ownid/sdk/api/ApiCapability.kt) | Start from a login ID and receive the next login outcome. |
| `auth.login.start()` | `ApiResult<LoginResponse, LoginApiFailure>` | Continue after OwnID returns an `AccessToken`. |
| `passkeys.auth` | [`OperationController<AccessToken, PasskeyAssertionOperationFailure>`](../../sdk-core/core/src/main/java/com/ownid/sdk/ops/OperationCapability.kt) | Authenticate with an existing passkey. |
| `verifications.email` | `ApiResult<EmailVerificationApi.Controller, EmailVerificationStartApiFailure>` | Start an email verification challenge. |
| `verifications.phone` | `ApiResult<PhoneVerificationApi.Controller, PhoneVerificationStartApiFailure>` | Start a phone verification challenge. |
| `passkeys.enroll` | [`PasskeyEnrollFlow.Controller<PasskeyEnrollFlow.Response, PasskeyEnrollFlowFailure>`](../../sdk-core/core/src/main/java/com/ownid/sdk/flow/passkey/PasskeyEnrollFlow.kt) | Add a passkey after successful login from the current user's Access Token; the flow runs local passkey creation and server enrollment. See [Passkey Enrollment](passkey-enrollment.md). |

## Login Flow Integration

Scope the headless namespace with the login ID the user entered, discover the next login outcome, then handle each `LoginResponse` case. When additional authentication is required, run supported next steps until `auth.login.start()` returns `LoginResponse.Success` or the app routes to fallback.

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.api.fold
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.LoginResponse
import com.ownid.sdk.namespace.start

suspend fun startHeadlessLogin(email: String) {
    val headless = OwnId.headless.withContext {
        authz = Authz.start(email, LoginId.Type.Email)
    }

    val response = headless.auth.discover.start().fold(
        onSuccess = { it },
        onError = { null }
    ) ?: return

    when (response) {
        is LoginResponse.Success -> completeAppLogin(response.accessToken, response.sessionPayload)
        is LoginResponse.AuthRequired -> continueAuth(response.authRequirements, headless)
        is LoginResponse.AccountNotFound -> showAccountNotFound(response.reason)
        is LoginResponse.AccountBlocked -> showAccountBlocked(response.reason)
    }
}
```

Handle every `LoginResponse` case. `auth.login.start()` returns the same `LoginResponse` shape and should be handled with the same completeness.

Use `withContext` for per-attempt login IDs and Access Tokens:

```kotlin
val loginScoped = OwnId.headless.withContext {
    authz = Authz.start(email, LoginId.Type.Email)
}

val tokenScoped = OwnId.headless.withContext {
    authz = Authz.fromToken(accessToken)
}
```

## Additional Authentication Requirements

`LoginResponse.AuthRequired.authRequirements` contains:

- `targetScore`: the minimum cumulative score that must be reached;
- `operations`: server-provided recommended [`OperationRequirement`](../../sdk-core/core/src/main/java/com/ownid/sdk/model/Operation.kt) items. Use each item's `type`, `score`, and optional `channels` to choose supported next steps.
- optional `channels` on operation requirements, such as available email or phone delivery choices.

Choose a next step from `operations` that this app can actually complete. If passkey authentication or verification returns an Access Token, call `auth.login`. If no supported Headless step can continue the attempt, leave the Headless path and use the app's existing authentication flow.

```kotlin
import com.ownid.sdk.model.AuthRequirements
import com.ownid.sdk.model.OperationType
import com.ownid.sdk.namespace.OwnIdHeadless

suspend fun continueAuth(requirements: AuthRequirements, headless: OwnIdHeadless) {
    val types = requirements.operations.map { it.type }

    when {
        OperationType.PASSKEY_AUTH in types -> {
            runPasskeyAuth(headless)?.let { accessToken ->
                finishLogin(accessToken)
            } ?: showFallback()
        }
        OperationType.EMAIL_VERIFICATION in types -> runEmailVerification(headless)
        OperationType.PHONE_NUMBER_VERIFICATION in types -> runPhoneVerification(headless)
        else -> showFallback()
    }
}
```

## Supported Headless Steps

### Passkey Authentication

Run `passkeys.auth` only when `PASSKEY_AUTH` is present in the current requirements.

OwnID passkey operations delegate to Android Credential Manager. Availability and runtime results depend on device support, credential providers, relying-party/domain setup, tenant configuration, and available credentials.

Check availability, start the operation, retain the controller until settlement, and finish login with the returned Access Token. A local controller is enough only when the calling coroutine owns the whole step; otherwise keep it in owner state and call `abort(reason)` or `close()` when the owner cancels or is torn down.

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.onUnavailable
import com.ownid.sdk.namespace.OwnIdHeadless
import com.ownid.sdk.namespace.availability
import com.ownid.sdk.namespace.start
import com.ownid.sdk.ops.getOrNull

suspend fun runPasskeyAuth(headless: OwnIdHeadless): AccessToken? {
    val passkeyAuth = headless.passkeys.auth

    passkeyAuth.availability().onUnavailable {
        return null
    }

    val controller = passkeyAuth.start()
    // Keep in owner state if the step can outlive this coroutine.
    // Abort or close it on teardown.
    return controller.whenSettled()
        .getOrNull()
}

suspend fun finishLogin(accessToken: AccessToken) {
    val result = OwnId.headless
        .withContext { authz = Authz.fromToken(accessToken) }
        .auth.login
        .start()

    // Route the LoginResponse result through the app auth/session boundary.
}
```

### Email and Phone Verification

Email and phone verification use the same controller pattern: start a challenge, render `controller.challenge`, then call `completeWithCode`, `resend`, or `cancel`. The challenge carries the delivery destination in `controller.challenge.channel.channel`, OTP configuration in `controller.challenge.methods`, and resend rules in `controller.challenge.resendPolicy`.

- Use `verifications.email` for `EMAIL_VERIFICATION` and `verifications.phone` for `PHONE_NUMBER_VERIFICATION`.
- For an email or phone login ID, start the matching verification from the login ID scoped to the current Headless attempt.
- For a username login ID, choose an email or phone channel from the matching `OperationRequirement.channels`, start the matching verification with explicit params, and pass the selected channel `id` as `loginIdHintId`.
- Verification completion returns [`AccessOrProofToken`](../../sdk-core/core/src/main/java/com/ownid/sdk/model/Tokens.kt). In the login `authRequirements` path, continue only with an `AccessToken`: call `auth.login` with `Authz.fromToken(accessToken)`. Treat a `ProofToken` result as unsupported for this login attempt.
- Complete, resend, and cancel failures belong to the current verification challenge. Keep the controller only while retry or resend is still valid for that challenge; otherwise cancel or restart from a fresh challenge. Releasing or clearing the controller reference does not cancel the server challenge; call `controller.cancel(reason)` when the user abandons it.

```kotlin
import com.ownid.sdk.api.EmailVerificationApi
import com.ownid.sdk.api.EmailVerificationCompleteApiFailure
import com.ownid.sdk.api.onError
import com.ownid.sdk.api.onSuccess
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.ProofToken
import com.ownid.sdk.model.Reason
import com.ownid.sdk.namespace.OwnIdHeadless
import com.ownid.sdk.namespace.start

class EmailVerificationStateHolder {
    private var emailVerificationController: EmailVerificationApi.Controller? = null

    suspend fun startEmailVerification(headless: OwnIdHeadless) {
        headless.verifications.email.start()
            .onSuccess { controller ->
                emailVerificationController = controller
                // Render controller.challenge.
            }
    }

    suspend fun completeEmailCode(code: String): AccessToken? {
        val controller = emailVerificationController ?: return null
        var accessToken: AccessToken? = null

        controller.completeWithCode(code)
            .onSuccess { result ->
                emailVerificationController = null
                when (result) {
                    is AccessToken -> accessToken = result
                    is ProofToken -> showFallback()
                }
            }
            .onError { error ->
                if (error is EmailVerificationCompleteApiFailure.BadRequest.WrongCode) {
                    // Keep this challenge active and show an OTP error.
                } else {
                    // Invalid, expired, completed, or max-attempt challenges need a fresh start.
                    emailVerificationController = null
                }
            }

        return accessToken
    }

    suspend fun resendEmailCode() {
        emailVerificationController?.resend()
    }

    suspend fun cancelEmailVerification() {
        emailVerificationController?.cancel(Reason.UserClose())
        emailVerificationController = null
    }
}
```

## Post-Login Passkey Enrollment

Use Passkey Enrollment after successful authentication, when the app has an Access Token for the signed-in user and a reason to offer a new passkey. Good signals from the current Headless attempt's `LoginResponse.AuthRequired.authRequirements` are:

- No passkey authentication step was offered.
- Passkey authentication was offered, but no applicable credential was available and the user completed login through another supported step.

Start `passkeys.enroll` from an Access Token scope for the current user. The enrollment flow handles local passkey creation and server enrollment internally. Check availability before enabling the add-passkey action and follow [Passkey Enrollment](passkey-enrollment.md).

## API Failure Handling

Direct API calls report handled endpoint failures through typed failure values. Branch on the endpoint-specific failure type first; use `errorCode` only as a localization key when showing OwnID-related copy.

The table below covers failures returned by the Headless direct API entries and verification controllers. Account state and additional-auth requirements are returned as `LoginResponse`, not as API failures.

| API step | Common failure groups | App handling |
| --- | --- | --- |
| `auth.discover.start()` and `auth.login.start()` | Invalid or unsupported login ID or token input, forbidden access, failed or missing server-side providers, unexpected failures. | Fix invalid input before retrying. For failed or missing server-side providers, inspect the provider/capability setup and backend integration before retrying. Handle `AuthRequired`, `AccountNotFound`, and `AccountBlocked` from `LoginResponse`, not as API failures. |
| `verifications.email.start()` and `verifications.phone.start()` | Invalid login ID, unsupported login ID type, missing channel, forbidden access, user not found, failed or missing server-side providers, maximum active challenges, unexpected failures. | Keep the selected auth step explicit. Let the user correct invalid identifiers, choose another available channel or method when a channel is missing, route user-not-found to account creation, and avoid starting duplicate challenges when the server reports challenge limits. |
| `completeWithCode` on a verification controller | Wrong code, invalid challenge, maximum attempts, forbidden access, unexpected failures. | Wrong codes stay within the current challenge while attempts remain. Invalid, expired, completed, or max-attempt challenges require clearing local challenge state and starting a fresh challenge. |
| `resend` on a verification controller | Invalid challenge, maximum resend limit or debounce, failed or missing server-side providers, unexpected failures. | Disable or delay resend according to the current challenge policy. If the challenge is invalid, expired, or completed, clear local state and start a new challenge instead of retrying the same controller. |
| `cancel` on a verification controller | Invalid challenge, maximum attempts, unexpected failures. | Clear local challenge state when the user abandons the step. If cancel fails because the challenge is already invalid, completed, or exhausted, do not keep using that controller. |

## Operation Failure Handling

In this Headless flow, `passkeys.auth` is the operation entry that settles through `OperationResult<AccessToken, PasskeyAssertionOperationFailure>`. `OperationResult.Canceled` means the user dismissed passkey UI, the challenge timed out, or the system ended the passkey-auth attempt before it completed; clear the active controller and offer another supported step or exit Headless to the app fallback.

For `OperationResult.Failure`, branch on `PasskeyAssertionOperationFailure` first. These categories summarize passkey-auth failures:

| Failure category | Meaning | App handling |
| --- | --- | --- |
| `Input` | A resolvable login ID or Access Token was missing, the login ID was invalid or unsupported, or the request shape was invalid. | Fix the scoped context or explicit params before retrying. If the login ID cannot be supported, use another auth path. |
| `Challenge` | The passkey assertion challenge could not be started or verified, or its limits were reached. | Do not keep retrying the same challenge. Start a new passkey-auth operation only when the user retries; otherwise offer another supported step or exit Headless to the app fallback. |
| `Access` | The user/account/token state does not allow passkey authentication, such as forbidden or user-not-found responses. | Route through app account-state handling: re-authentication, account creation, support, or another app-owned path. |
| `Credential` | Android Credential Manager reported that no matching passkey credential is available for this assertion request. | Let the user retry platform passkey UI, choose another supported Headless step, or exit Headless to the app fallback. |
| `Integration` | Required SDK setup, login ID validation, platform passkey provider, or server-side provider/capability failed or is missing. | Keep fallback auth available and inspect SDK setup, passkey/domain configuration, server-side provider configuration, and platform diagnostics before retrying. |
| `Unexpected` | The SDK, runtime, transport, or response mapping failed outside expected passkey-auth paths. | Show a generic retry/fallback state and log diagnostics. Start a new operation only after the user retries. |

## Security and Data Handling

- Treat Access Token, Proof Token, `sessionPayload`, verification codes, challenge data, and platform credential payloads as sensitive authentication handoff values.
- Do not log passwords, verification codes, tokens, full API or provider responses, raw credential payloads, or full controller/flow results.
- Keep verification, operation, and flow controllers scoped to the current Headless attempt. Cancel or abort active work when the user abandons a step, and clear stale controllers after settlement.
- Headless does not replace the app session boundary. Create or refresh the app session only from `LoginResponse.Success`, and keep fallback authentication explicit when a Headless step is unavailable, canceled, or fails.

## Related Documentation

- [Configuration](../setup/configuration.md)
- [Context](../setup/context.md)
- [Namespace Handles](../setup/namespace-handles.md)
- [Providers](../setup/providers.md)
- [Passkey Enrollment](passkey-enrollment.md)
