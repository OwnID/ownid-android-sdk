package com.ownid.sdk.capability

import android.app.Activity
import androidx.annotation.MainThread
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
 * Supply an Activity suitable for presenting the request. The platform provider controls presentation;
 * supplying an Activity does not guarantee that UI will be shown.
 * OwnID calls [getCredential] and [createCredential] on the Android main thread. Implementations that perform
 * blocking work must move it off the main thread without moving UI presentation there.
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
     * If another passkey request is already running on the same Passkey instance, this call fails immediately.
     * OwnID invokes this method on the Android main thread.
     *
     * @param activity Activity selected for this request. Do not retain it after the request completes.
     * @param assertionOptions Server-provided assertion options.
     *
     * @return [Result] with the assertion data, a cancellation, or a failure.
     */
    @MainThread
    public suspend fun getCredential(activity: Activity, assertionOptions: AssertionOptions): Result<AssertionResult>

    /**
     * Creates a new passkey credential (attestation).
     *
     * If another passkey request is already running on the same Passkey instance, this call fails immediately.
     * OwnID invokes this method on the Android main thread.
     *
     * @param activity Activity selected for this request. Do not retain it after the request completes.
     * @param attestationOptions Server-provided attestation options.
     * @param isAutoSelectAllowed Allows automatic selection when supported by the credential provider. Defaults to true.
     *
     * @return [Result] with the attestation data, a cancellation, or a failure.
     */
    @MainThread
    public suspend fun createCredential(
        activity: Activity,
        attestationOptions: AttestationOptions,
        isAutoSelectAllowed: Boolean = true
    ): Result<AttestationResult>
}
