package com.ownid.sdk.capability

/**
 * Process-wide local application and device information exposed through OwnID SDK instances.
 *
 * - Values are derived from the host application and device at root SDK initialization time.
 * - Shared by all SDK instances within the current process.
 * - Values can be included in SDK request headers and diagnostic or analytics metadata.
 * - The correlation ID is a process/root SDK lifetime value for request correlation, not a persisted user or device ID.
 * - Biometric/device flags are for analytics and risk signals only, not access control.
 *   The SDK does not pre-block passkey operations based on these flags.
 */
public interface LocalInfo : Capability {
    /** Name/version pairs for OwnID modules bundled in the app (e.g. core, compose). */
    public val modules: List<Pair<String, String>>

    /** The application package name. */
    public val packageName: String

    /** The application version string; empty if unavailable. */
    public val appVersion: String

    /** User-Agent header sent on SDK HTTP requests. */
    public val userAgent: String

    /** Random Base64URL string generated once per process/root SDK lifetime and propagated via the baggage header. */
    public val correlationId: String

    /** Whether the app is built as debuggable. */
    public val isDebuggable: Boolean

    /** Coarse OS-level indicator for FIDO/passkey support (true on API 28+). */
    public val isSystemFidoCapable: Boolean

    /** Whether a device credential (PIN/pattern/password) is configured; null if detection fails. */
    public val isDeviceSecured: Boolean?

    /** Whether fingerprint hardware is present; null if detection fails. */
    public val isFingerprintHardwarePresent: Boolean?

    /** Whether face authentication hardware is present (API 29+); null if detection fails. */
    public val isFaceHardwarePresent: Boolean?

    /** Whether iris authentication hardware is present (API 29+); null if detection fails. */
    public val isIrisHardwarePresent: Boolean?

    /** Whether strong biometrics can authenticate (API 30+); null if detection fails. */
    public val isStrongBiometricEnabled: Boolean?
}
