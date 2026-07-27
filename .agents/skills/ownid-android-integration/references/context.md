# Context

Use this reference when an OwnID Android SDK v4 integration needs OwnID runtime
context. Namespace-handle mechanics live in `namespace-handles.md`;
surface-specific references decide whether context is needed.

Source docs:

- `../../../../docs/setup/context.md`
- `../../../../docs/setup/access-token.md`
- `../../../../docs/setup/namespace-handles.md`

SDK contracts: `Context`, `Authz`, `LoginId`, namespace `withContext`,
`OwnId.setContext`, and `OwnId.clearContext`.

## Mental Model

Context is caller-supplied auth/display input attached to an SDK namespace handle.
Use it when the selected SDK surface reads runtime context instead of receiving
the same value through an explicit parameter.

Context can carry:

- `authz = Authz.start(...)`: login-ID auth input.
- `authz = Authz.fromToken(...)`: OwnID access-token auth input.
- `accountDisplayName`: optional display/context value.

A context has one `authz` slot. Choose either login ID or access token for the
next SDK call.

## Choosing Auth Input

- Use `Authz.start(...)` when the selected surface starts from a visible login
  ID, such as email, phone, or another supported login identifier.
- Use `Authz.fromToken(...)` when the selected surface continues work for an
  already identified or authenticated OwnID user using an OwnID access token.
- Use typed `LoginId` when the app already knows the login ID type. Raw strings
  are preserved as provided; validation and type resolution belong to the
  consuming SDK surface. Typed values may still be validated by that surface.
- Set `accountDisplayName` when the selected surface can use a display value.

## Scoped vs Top-Level Context

Prefer `withContext` for screen-like or request-like work:

```kotlin
val scopedFlows = OwnId.flows.withContext {
    authz = Authz.start(email, LoginId.Type.Email)
}
```

Keep using the returned handle for the sequence. The child context is built
only from the block; unset fields do not inherit parent context values. If a
child needs both `authz` and `accountDisplayName`, set both in the same block.

Use `OwnId.setContext` when subsequent calls through top-level `OwnId` entry
points should inherit shared context:

```kotlin
OwnId.setContext {
    authz = Authz.fromToken(accessToken)
}
```

`OwnId.setContext` merges with top-level context: assigned fields replace
existing values, unassigned fields stay unchanged, and assigning `null` clears
that field.

Concrete `flows` and `webBridge` namespace handles expose
`withContext`, not in-place `setContext` or `clearContext`. Call
`OwnId.clearContext()` on sign-out, account switch, tenant switch, or any
boundary where later top-level calls must stop inheriting previous context.

## Review Checks

- The selected surface reference explicitly supports runtime context.
- The integration chose one auth input: `Authz.start(...)`,
  `Authz.fromToken(...)`, or no `authz`.
- Context is attached near the concrete SDK call unless shared top-level
  context is intentional.
- Shared top-level context is cleared or replaced on
  sign-out/account/tenant switch.
