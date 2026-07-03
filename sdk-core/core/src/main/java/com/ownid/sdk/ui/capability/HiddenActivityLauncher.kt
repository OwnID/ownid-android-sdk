package com.ownid.sdk.ui.capability

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.annotation.RestrictTo
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.OwnIdHiddenActivity

/**
 * Launches [OwnIdHiddenActivity] to host operation UI or WebBridge WebView.
 *
 * - Always launches on the main thread.
 * - Uses a `ResultReceiver` to propagate close reasons back to the caller.
 * - Allows only one hidden activity launch/run at a time through [OwnIdHiddenActivity.LaunchLock].
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface HiddenActivityLauncher : Capability {

    /**
     * Creates and starts the hidden activity with the intent produced by [intentProvider].
     *
     * @param intentProvider Factory that builds the launch intent from a [Context].
     * @param onShowError Called if launch is already in progress, activity startup fails, or an error result is returned.
     */
    public fun launchActivityForResult(intentProvider: (Context) -> Intent, onShowError: (error: Throwable?, details: String?) -> Unit)

    /**
     * Strategy interface that [OwnIdHiddenActivity] delegates to for content setup and teardown.
     *
     * Delegates identify their own intents, render content for the supplied instance resolver, and release any
     * activity-owned resources from [onDestroy]. The resolver is the current instance container at activity creation
     * time and must not be retained after the activity lifecycle ends.
     */
    @InternalOwnIdAPI
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface Delegate {
        /** Returns `true` if this delegate handles the given [intent]. */
        public fun isThis(intent: Intent): Boolean

        /** Called from the activity's `onCreate`; sets up the UI content. */
        public fun onCreate(activity: ComponentActivity, instanceResolver: DIContainerResolver)

        /** Called from the activity's `onDestroy`; releases resources. */
        public fun onDestroy(activity: ComponentActivity)
    }
}
