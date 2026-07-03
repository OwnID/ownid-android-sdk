package com.ownid.sdk.capability

import android.content.Context
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.Reason

/**
 * FIDO2/WebAuthn passkey operations for creating and retrieving credentials.
 *
 * This capability is only registered on API 28+; it is unavailable on lower API levels.
 *
 * Overlapping requests on the same capability instance are not supported. If a request is already running,
 * a new one fails immediately with [Result.Fail].
 *
 * Calls switch to the Android main thread before invoking AndroidX Credential Manager. Passkey prompts are owned by
 * the platform provider; the SDK cannot force presentation when the provided [Context] is not suitable for UI.
 *
 * Results are returned as [Result.Success], [Result.Canceled], or [Result.Fail] for expected outcomes.
 */
public interface Passkey : Capability {

    /**
     * Outcome of a passkey operation.
     *
     * [Success] carries credential data, [Fail] carries an error (see [NoCredentialError] for the no-credential
     * case), and [Canceled] indicates a user or system cancellation.
     */
    public sealed interface Result<out R> {
        /** Successful result carrying the credential [data]. */
        public class Success<R>(public val data: R) : Result<R> {
            override fun equals(other: Any?): Boolean = this === other || (other is Success<*> && data == other.data)
            override fun hashCode(): Int = data?.hashCode() ?: 0
        }

        /** Failed result. See [NoCredentialError] for the no-credential case. */
        public class Fail(public val error: Throwable) : Result<Nothing> {
            override fun equals(other: Any?): Boolean = this === other || (other is Fail && error == other.error)
            override fun hashCode(): Int = error.hashCode()
        }

        /** Canceled by the user or system, with a [reason]. */
        public class Canceled(public val reason: Reason) : Result<Nothing> {
            override fun equals(other: Any?): Boolean = this === other || (other is Canceled && reason == other.reason)
            override fun hashCode(): Int = reason.hashCode()
        }
    }

    /** Error indicating no passkey credential is available on the device. */
    public class NoCredentialError : Exception("No credentials available")

    /**
     * Retrieves an existing passkey credential (assertion).
     *
     * OwnID passes [context] directly to AndroidX Credential Manager's `getCredential(...)` call.
     * Use an active `Activity` context when invoking this method. AndroidX recommends that context type for
     * UI-launching credential requests so the system selector stays in the same task stack.
     *
     * If another passkey request is already running on the same Passkey instance, this call fails immediately.
     *
     * If you pass a non-`Activity` context, OwnID still forwards it, but any AndroidX or provider failure is surfaced as [Result.Fail].
     *
     * @param context Android [Context] forwarded to AndroidX Credential Manager.
     * @param assertionOptions Server-provided assertion options.
     *
     * @return [Result] with the assertion data, a cancellation, or a failure.
     */
    public suspend fun getCredential(context: Context, assertionOptions: AssertionOptions): Result<AssertionResult>

    /**
     * Creates a new passkey credential (attestation).
     *
     * OwnID passes [context] directly to AndroidX Credential Manager's `createCredential(...)` call.
     * Use an active `Activity` context when invoking this method. AndroidX recommends that context type for
     * UI-launching credential requests so the system selector stays in the same task stack.
     *
     * If another passkey request is already running on the same Passkey instance, this call fails immediately.
     *
     * If you pass a non-`Activity` context, OwnID still forwards it, but any AndroidX or provider failure is surfaced as [Result.Fail].
     *
     * @param context Android [Context] forwarded to AndroidX Credential Manager.
     * @param attestationOptions Server-provided attestation options.
     * @param isAutoSelectAllowed Auto-select the credential when it is the only one available. Defaults to true.
     *
     * @return [Result] with the attestation data, a cancellation, or a failure.
     */
    public suspend fun createCredential(
        context: Context,
        attestationOptions: AttestationOptions,
        isAutoSelectAllowed: Boolean = true
    ): Result<AttestationResult>
}
