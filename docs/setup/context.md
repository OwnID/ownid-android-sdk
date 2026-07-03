# Context

OwnID context keeps user or session input available to related SDK calls. Use it when those calls should share the same login ID or Access Token without storing that value globally in the app.

Context is optional. Many widgets, flow contexts, operations, and APIs can also receive the same values directly through their own parameters, but context is often the clearer way to keep one OwnID sequence scoped to the right user or session.

For how context attaches to namespace handles, see [Namespace Handles](namespace-handles.md).

## Context Values

Context can carry:

- `authz = Authz.start(...)`: login-ID auth input.
- `authz = Authz.fromToken(...)`: Access Token auth input.
- `accountDisplayName`: optional display/context value.

A [`Context`](../../sdk-core/core/src/main/java/com/ownid/sdk/model/Context.kt) has one `authz` slot, so choose either login ID or Access Token for the next SDK call. Raw login IDs are stored as provided and may be resolved or validated by the consuming SDK feature. Typed [`LoginId`](../../sdk-core/core/src/main/java/com/ownid/sdk/model/LoginId.kt) values are preserved as provided and may still be validated by that feature.

Only a few SDK surfaces read `accountDisplayName`. The direct passkey attestation API uses it as the fallback for `PasskeyAttestationApi.Params.accountDisplayName`; WebBridge exposes it in the context payload for hosted pages that request context.

## Scoped Context

Use `withContext` when one flow, operation, or API run needs its own auth input. It returns a derived namespace handle and does not mutate the current handle.

```kotlin
val headless = OwnId.headless.withContext {
    authz = Authz.start(email, LoginId.Type.Email)
}

val response = headless.auth.discover.start()
```

The returned handle stays bound to that context. Keep using it for the related sequence instead of switching back to the original handle midway.

The child context is built only from the `withContext` block. Unset context fields do not inherit values from a previously set parent context. If a child needs both `authz` and `accountDisplayName`, set both in the same block.

## Current Context

Use `setContext` only when future calls from the current handle should reuse the same context.

```kotlin
OwnId.setContext {
    authz = Authz.fromToken(accessToken)
}
```

`setContext` updates the current handle in place:

- Fields assigned in the block replace existing values.
- Fields not assigned keep their current values.
- Assigning `null` clears that field.

Use `clearContext` when the user signs out, switches account, switches tenant, or the app no longer wants calls from that handle to inherit the previous context.

```kotlin
OwnId.clearContext()
```

For login screens, one-off actions, and WebBridge instances, prefer `withContext` unless shared current context is intentional.
