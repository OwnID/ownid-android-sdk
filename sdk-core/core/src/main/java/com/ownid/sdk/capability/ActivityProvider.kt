package com.ownid.sdk.capability

import android.app.Activity

/**
 * Provides access to the current foreground [Activity].
 *
 * Core UI-backed capabilities use this as a best-effort source for launching platform UI in the app's current task.
 * The default provider tracks started or resumed activities and clears the reference when that activity stops.
 */
public interface ActivityProvider : Capability {
    /**
     * Returns the current foreground [Activity], or `null` if none is alive and usable.
     *
     * The returned activity is not retained by this contract. Callers should use it immediately and tolerate `null`
     * when the app has no foreground activity or the last tracked activity is finishing or destroyed.
     */
    public fun getActivity(): Activity?
}
