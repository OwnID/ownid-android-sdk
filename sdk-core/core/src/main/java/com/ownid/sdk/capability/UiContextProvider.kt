package com.ownid.sdk.capability

import android.app.Activity
import androidx.annotation.MainThread

/**
 * Resolves an [Activity] for SDK UI presentation.
 *
 * The default provider makes a best-effort choice among app Activities and excludes OwnID's own UI hosts.
 * If OwnID initializes after an Activity has started or resumed, the default may not discover it until a later
 * start or resume. Initialize OwnID before the Activity starts, or bind it explicitly with `uiContext(activity)`.
 * If no host can be found, resolution returns [Result.Failure].
 * The selected Activity is not guaranteed to be the one that initiated the operation, and success
 * does not guarantee presentation.
 *
 * Custom providers choose an Activity using the app's own routing and lifecycle policy. They should return
 * [Result.Failure] instead of a finishing, destroyed, or otherwise unavailable Activity.
 */
public fun interface UiContextProvider : Capability {

    /**
     * Resolves an [Activity] synchronously on the main thread, without waiting.
     *
     * Return [Result.Failure] when no host can be resolved. This method must not wait for a future Activity; custom
     * providers may supply their own [Throwable].
     */
    @MainThread
    public fun current(): Result

    /** The outcome of resolving a native UI host. */
    public sealed interface Result {
        /** An [Activity] selected for the current request. Success does not guarantee that UI can be presented. */
        public class Success(public val activity: Activity) : Result

        /** The error to surface when the provider cannot select an [Activity]. */
        public class Failure(public val error: Throwable) : Result
    }
}

/** Built-in UI-context resolution failure, with a machine-readable [reason] and diagnostic message. */
public class UiContextResolutionException(
    public val reason: Reason,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause) {
    /** Reasons reported by SDK-provided UI-context providers. */
    public enum class Reason {
        /** No suitable host is available for the current request. */
        Unavailable,

        /** The explicitly bound owner's lifetime has ended. */
        OwnerClosed
    }
}
