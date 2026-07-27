# Elite Flow

Use this reference when integrating OwnID SDK v4 Elite Flow into an Android app.

Full public docs: `../../../../docs/flows/elite-flow.md`.

Read first:

- `install.md` and `configuration.md` for `core` and SDK initialization.
- `providers.md` for `sessionCreate`, `passwordAuthenticate`, and Google.
- `context.md` and `namespace-handles.md` for context/provider placement.

## Contents

- [Scope](#scope)
- [Before Editing](#before-editing)
- [Providers And Context](#providers-and-context)
- [Event Rules](#event-rules)
- [Controller And Result Rules](#controller-and-result-rules)
- [Options](#options)
- [Implementation Pattern](#implementation-pattern)
- [Validation](#validation)

## Scope

Elite Flow opens a temporary SDK-managed `WebView` with an OwnID-hosted page.
Inside that page, the OwnID Web SDK runs the hosted authentication experience;
the native Android SDK uses OwnID WebBridge plumbing to deliver hosted-page
events to `EliteFlowContext.events`.

Use Elite when OwnID owns the primary hosted auth UI and the app owns the entry
point, provider setup, native registration/session handoff, lifecycle, and
fallback UI.

## Before Editing

- `com.ownid.android-sdk:core` is installed and OwnID is initialized before
  `start`.
- Android passkey setup from the public README is complete.
- The tenant/hosted page requirements are known: native registration handoff,
  session creation, password auth, Google sign-in, passkeys, and runtime context.
- App behavior is decided for `onNativeAction`, `onFinish`, `onError`, and
  `onClose`.

## Providers And Context

Register app-wide providers through top-level `OwnId.setProviders`. Use
`OwnId.flows.withProviders` when one Elite run needs provider behavior different
from the app-wide bindings.

If the hosted page needs a login ID, OwnID access token, or account display
name, apply `withContext` on `OwnId.flows` before `elite.start(...)`, then call
`start` on the returned derived handle. Prefer one-run context for public login
or registration entry points.

`EliteFlowContext` is separate from SDK runtime context. It configures the one
Elite WebView run through `events` and optional `options`.

## Event Rules

Configure hosted-page callbacks with `EliteFlowContext.events`. Handlers run on
the main thread. After a terminal handler returns, the SDK closes the WebView and
settles the controller.

| Event | App responsibility |
| --- | --- |
| `onNativeAction(loginId, ownIdData, accessToken)` | Continue native handoff, commonly registration. Preserve `ownIdData` unchanged and treat `accessToken` as optional app/backend handoff data. |
| `onFinish(loginId, authMethod, accessToken)` | Route hosted authentication completion through the app auth/session boundary. |
| `onError(error)` | Handle a hosted functional error with retry/fallback UI. |
| `onClose()` | Clear transient state after a normal hosted close. |

If `onFinish`, `onError`, or `onClose` are omitted, the SDK installs default
hosted-page handlers. Add handlers when the app must update auth state,
navigation, analytics, or user-visible fallback UI. `onNativeAction` has no
default app behavior.

## Controller And Result Rules

Starting Elite returns an app-owned controller. Keep it strongly referenced in
lifecycle-aware state that survives rotation and window resizing until
`whenSettled()` completes, and disable duplicate starts while active. The SDK
preserves the active hosted page and JavaScript session across the configuration
changes it handles, so retain the same controller and run. Call `abort(...)` or
`close()` when the app-owned flow lifetime ends.

Interpret `whenSettled()` as infrastructure settlement:

- `FlowResult.Success`: a terminal hosted-page handler completed. This can
  follow `onFinish`, `onNativeAction`, `onError`, or `onClose`; it does not mean
  hosted authentication succeeded. Create an app session from `onFinish` or
  the applicable `onNativeAction` handoff rather than controller settlement.
- `FlowResult.Canceled`: the app or SDK canceled before a terminal hosted event.
- `FlowResult.Failure`: SDK or WebBridge infrastructure failed before a terminal
  hosted event.

## Options

Hosted page UX and business behavior come from the OwnID-hosted Web SDK page and
tenant/server configuration. `EliteFlowContext.options` only configures the
SDK-managed WebView operation.

For normal customization, use:

- `statusBarColor`
- `navigationBarColor`

The SDK applies each color to its corresponding Android system-bar region where
that bar appearance is supported; API 23-25 retain the platform navigation-bar
fallback. Leave options unset when the default white SDK-host background and
system-bar regions fit the app. Check production API comments before using
advanced WebView content options.

## Implementation Pattern

```kotlin
val flowContext = EliteFlowContext {
    events {
        onNativeAction { loginId, ownIdData, accessToken ->
            pendingRegistration = PendingRegistration(loginId, ownIdData)
        }

        onFinish { loginId, authMethod, accessToken ->
            // Route through the app auth/session boundary.
        }

        onError { error -> showEliteFallback(error) }
        onClose { clearEliteRunningState() }
    }
}

runningEliteController = OwnId.flows.elite.start(flowContext)
```

If the run needs scoped context or an intentional provider override, start from
the scoped namespace returned by `withContext` or `withProviders`. Clear the
stored controller after `whenSettled()` returns.

## Validation

When validation is in scope, exercise the Elite entry point and confirm startup
prerequisites, required providers/context, native `ownIdData` handoff, session
handoff, hosted error/close handling, controller retention, and cancellation or
failure fallback. Rotate or resize while the hosted page has transient state and
confirm that the same run and page state remain active. Also confirm that system
bar colors and the white exposed native surface remain correct around cutouts
and the visible keyboard.
