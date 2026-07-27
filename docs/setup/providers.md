# Providers

Providers are optional extensions that add app-specific capabilities to the OwnID SDK experience. Register only the providers that match the product journeys and identity systems in your integration; you do not need to register every available provider.

Common provider-backed capabilities include:

- `sessionCreate` to create or restore the app session after OwnID authentication.
- `passwordAuthenticate` to support password login through the existing app authentication system.
- Social sign-in providers, such as Sign in with Google.

Some integrations can use OwnID-provided source-only helpers instead of writing provider implementations from scratch. These helpers are copied into the app target so the app owns the dependency, configuration, and runtime behavior.

## Contents

- [How Providers Work](#how-providers-work)
- [Session Create](#session-create)
- [Password Authenticate](#password-authenticate)
- [Sign in with Google](#sign-in-with-google)
- [SAP Customer Data Cloud (Gigya)](#sap-customer-data-cloud-gigya)

## How Providers Work

Register providers after SDK initialization and before starting functionality that needs them. Because providers are usually shared app capabilities, such as session creation, password authentication, and social sign-in, most apps should register them through top-level `OwnId.setProviders`, typically once during startup:

```kotlin
import com.ownid.sdk.OwnId

OwnId.setProviders {
    sessionCreate {
        create { params -> appAuth.createSession(params) }
    }
    passwordAuthenticate {
        authenticate { params -> appAuth.passwordLogin(params) }
    }
}
```

Use `OwnId.setProviders` to update top-level provider bindings so subsequent SDK calls can use the same app-owned capabilities. Provider types declared in the block replace existing providers of the same type; other provider bindings remain unchanged.

Concrete `flows`, `headless`, and `webBridge` namespace handles expose `withProviders`, not in-place `setProviders`. Use `withProviders` only when one specific SDK request or session needs provider overrides without changing top-level bindings; it is a special-case override tool, not the default setup pattern. See [Namespace Handles](namespace-handles.md) for handle behavior.

Provider bindings are materialized when the providers block returns; do not retain the registrar or provider builders outside that block.

`isAvailable` describes whether a registered provider can handle the current request. Providers are available by default. Add the callback when support depends on the request parameters, and return `true` for the cases supported by the app's authentication or session backend. For example, a provider backed by an email-only endpoint checks for an email login ID.

Provider failures are reported by the SDK feature that called the provider. Handle them in that result or event callback rather than treating provider errors as a separate global channel.

## Session Create

Use `sessionCreate` when the OwnID experience should create or restore the app session after authentication. The provider receives the authenticated login ID, [OwnID Access Token](access-token.md), authentication method, and session payload; use those values at your app's session boundary.

Implement `create`; it returns Kotlin `Result<SessionOutput>`, and registration throws if `create` is missing.

Treat `accessToken` and `sessionPayload` as sensitive session material. OwnID invokes `create` and `isAvailable` on the main thread. The callback must return after the app-owned session work finishes; if that work uses blocking APIs, dispatch it away from the main thread inside the callback. For exact parameters and return values, see [`SessionCreate`](../../sdk-core/core/src/main/java/com/ownid/sdk/provider/SessionCreate.kt).

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.provider.SessionOutput

OwnId.setProviders {
    sessionCreate {
        isAvailable { params ->
            params.loginId.type == LoginId.Type.Email
        }

        create { params ->
            // appAuth is your app-owned auth/session layer, not an OwnID SDK object.
            appAuth.createSession(
                loginId = params.loginId.id,
                ownIdAccessToken = params.accessToken.token,
                sessionPayload = params.sessionPayload
            ).map { appSession ->
                SessionOutput(session = appSession)
            }
        }
    }
}
```

## Password Authenticate

Use `passwordAuthenticate` when the OwnID experience should support password login through the existing app authentication system. Verify passwords through your app's authentication system or identity provider; do not send passwords to OwnID.

Implement `authenticate`; it returns Kotlin `Result<SessionOutput>`, and registration throws if `authenticate` is missing.

OwnID invokes `authenticate` and `isAvailable` on the main thread. The callback must return after the app-owned authentication work finishes; if that work uses blocking APIs, dispatch it away from the main thread inside the callback. For exact parameters and return values, see [`PasswordAuthenticate`](../../sdk-core/core/src/main/java/com/ownid/sdk/provider/PasswordAuthenticate.kt).

```kotlin
import com.ownid.sdk.OwnId
import com.ownid.sdk.provider.SessionOutput

OwnId.setProviders {
    passwordAuthenticate {
        authenticate { params ->
            // appAuth is your app-owned auth layer, not an OwnID SDK object.
            appAuth.passwordLogin(
                loginId = params.loginId.id,
                password = params.password
            ).map { appSession ->
                SessionOutput(session = appSession)
            }
        }
    }
}
```

## Sign in with Google

Sign in with Google setup has two parts: app-side provider wiring in your Android app and Google Sign-in configuration in the OwnID Console. OwnID's source-only Google provider helper connects OwnID Google sign-in to AndroidX Credential Manager; copy it into your app and compile it with the app target.

The Google provider implements `signIn`; it returns `SignInWithSocial.Result.Success`, `Canceled`, or `Fail`. For exact parameters and return values, see [`SignInWithSocial`](../../sdk-core/core/src/main/java/com/ownid/sdk/capability/SignInWithSocial.kt).

To use it:

1. Copy [`providers/OwnIdSignInWithGoogleProvider.kt`](../../providers/OwnIdSignInWithGoogleProvider.kt) into your app source set.

2. Add Credential Manager `1.6.0` or later and Google ID `1.2.0` or later to your app module dependencies. These are the minimum versions this helper is documented against; newer compatible versions can be used. See Google's [Credential Manager Sign in with Google guide](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation) for the platform setup details.

   ```kotlin
   dependencies {
       implementation("androidx.credentials:credentials:1.6.0")
       implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
       implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
   }
   ```

3. Register the copied helper with OwnID after SDK initialization and before using functionality that needs Google sign-in. Start Google sign-in from an active `Activity` context so Credential Manager can present UI in the current task.

   ```kotlin
   import com.ownid.sdk.OwnId
   import com.ownid.sdk.provider.signInWithGoogleProvider

   OwnId.setProviders {
       signInWithGoogleProvider()
   }
   ```

4. Follow the [OwnID Google Sign-In guide](https://docs.ownid.com/social-providers/google) for Google Cloud and OwnID Console configuration.

## SAP Customer Data Cloud (Gigya)

SAP Customer Data Cloud (Gigya) setup has two parts: app-side provider wiring in your Android app and SAP Customer Data Cloud configuration for OwnID.

The source-only helper:

- Connects OwnID `sessionCreate` and `passwordAuthenticate` providers to SAP Customer Data Cloud.
- Must be copied into your app source set and compiled with the app target.
- Depends on the SAP Customer Data Cloud Android SDK version owned by the app.

The helper expects the OwnID-provided SAP Customer Data Cloud session payload shape: `sessionInfo` for successful session creation or `errorJson` for SAP Customer Data Cloud errors. Password-login cancellation is best-effort because SAP Customer Data Cloud does not expose a cancellation handle.

To use it:

1. Copy [`providers/OwnIdGigyaProviders.kt`](../../providers/OwnIdGigyaProviders.kt) into your app source set.

2. Add SAP Customer Data Cloud Android SDK `7.2.0` or later to your app module dependencies. This is the minimum version this helper is documented against; newer compatible versions can be used. See the official [SAP/gigya-android-sdk](https://github.com/SAP/gigya-android-sdk) repository for SDK setup details.

   ```kotlin
   dependencies {
       implementation("com.sap.oss.gigya-android-sdk:sdk-core:7.2.0")
   }
   ```

3. Configure and initialize SAP Customer Data Cloud in your app before registering OwnID providers.

4. Register the copied helper with OwnID after SDK initialization and before using functionality that needs SAP Customer Data Cloud sessions or password login.

   ```kotlin
   import com.gigya.android.sdk.Gigya
   import com.gigya.android.sdk.account.models.GigyaAccount
   import com.ownid.sdk.OwnId
   import com.ownid.sdk.provider.gigyaProviders

   val gigya: Gigya<GigyaAccount> = /* app-owned Gigya instance */

   OwnId.setProviders {
       gigyaProviders(gigya)
   }
   ```

5. Follow the [OwnID SAP Customer Data Cloud guide](https://docs.ownid.com/integrations/sap-customer-data-cloud) for SAP Customer Data Cloud and OwnID Console configuration.
