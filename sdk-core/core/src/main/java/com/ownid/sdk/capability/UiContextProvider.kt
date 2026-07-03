package com.ownid.sdk.capability

import android.app.Activity
import android.content.Context

/**
 * Provides the best available Android [Context] for SDK UI-backed work.
 *
 * The default provider returns the current foreground [Activity] from [ActivityProvider] when one is
 * available, then falls back to the instance [Context]. UI launchers still need to handle presentation failure because
 * the returned context may be an application context instead of an activity host.
 */
public fun interface UiContextProvider : Capability {

    /**
     * Returns the best available Android [Context] for UI operations.
     *
     * The returned value is typically the current foreground `Activity`.
     *
     * When no such `Activity` is available, this method may return a non-UI application [Context] instead. A returned
     * context is not a guarantee that platform UI can be presented.
     *
     * @return The best available context for UI-backed work.
     *
     * @throws IllegalStateException if no context of any kind is available.
     */
    @Throws(IllegalStateException::class)
    public fun current(): Context
}
