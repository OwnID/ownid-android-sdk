package com.ownid.sdk.capability

import com.ownid.sdk.model.Reason

/**
 * Base interface for SDK capability boundaries.
 *
 * Capabilities are available by default. Implementations that depend on runtime state, platform support,
 * configuration, or caller-supplied parameters override [isAvailable]. Availability is owned by the capability and is
 * evaluated at the point where an SDK module asks to use it.
 */
public interface Capability {

    /**
     * Returns `true` when this capability is ready to be used with the given [params].
     *
     * Availability is a preflight signal only. A later operation, flow, provider call, or UI presentation can still
     * fail or be canceled if runtime state changes after this check. The default implementation returns `true`.
     *
     * @param params Optional capability-specific parameters. Pass `null` to check availability without explicit parameters.
     */
    public suspend fun isAvailable(params: Params?): Boolean = true

    /**
     * Marker interface for parameter objects used by capability availability checks.
     *
     * Capability-specific parameter types should implement this interface when they need to participate in [isAvailable] checks.
     */
    public interface Params

    /**
     * Controller for an active capability session (operation, flow, etc.).
     *
     * Use [abort] for semantic cancellation when the caller has an explicit reason that should be propagated to the
     * terminal result.
     *
     * Use [close] for owner-driven lifecycle cleanup via [AutoCloseable], for example when the owner that started the
     * capability is being torn down without a domain-specific reason.
     *
     * Cancellation is best-effort. If the capability is already in a terminal state, additional cancellation requests may have no effect.
     */
    public interface Controller : AutoCloseable {
        /**
         * Requests capability cancellation with an explicit [reason].
         *
         * Use this API when cancellation semantics are meaningful to the caller and should be reflected in the terminal result.
         *
         * @param reason Reason propagated to the result.
         */
        public fun abort(reason: Reason)

        /**
         * Requests cancellation through the [AutoCloseable] cleanup path.
         *
         * The default implementation aborts with an unknown close reason. Override or call [abort] directly when the
         * caller can provide a more specific cancellation reason.
         */
        override fun close(): Unit = abort(Reason.Unknown("Closed as AutoCloseable"))
    }
}
