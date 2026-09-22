package com.ownid.sdk.ui.capability

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.annotation.RestrictTo
import com.ownid.sdk.capability.Capability
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.OwnIdHiddenActivity

/**
 * Launches SDK-owned operation UI from an Activity selected for the request.
 *
 * Launch requests run on the main thread. Only one SDK-owned host can be pending or active at a time.
 * Host-resolution, startup, and host-reported errors are returned through the launch callback. The selected Activity
 * must still have an attached, visible window when launch starts; window focus is not required.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface HiddenActivityLauncher : Capability {

    /**
     * Creates one intent from the initially selected Activity and starts the target SDK host.
     *
     * Supply an explicit intent whose target participates in the SDK host result and launch-lock lifecycle.
     * When the intent requests IME handoff, launch may wait for the source keyboard to hide and then start the same
     * intent from a different, freshly resolved Activity.
     * It checks that the final Activity has a visible window immediately before starting the SDK host.
     *
     * @param intentProvider Factory that builds the intent from the initially selected [Activity].
     * @param onShowError Called if launch is already in progress, [UiContextProvider] resolution fails, startup fails,
     * or the host returns an error. A resolution failure supplies the provider's original error.
     */
    public fun launchActivityForResult(intentProvider: (Activity) -> Intent, onShowError: (error: Throwable?, details: String?) -> Unit)

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
