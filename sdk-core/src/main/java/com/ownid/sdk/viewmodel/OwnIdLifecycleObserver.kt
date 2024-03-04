package com.ownid.sdk.viewmodel

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.annotation.RestrictTo
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.event.OwnIdEvent

/**
 * Calls OwnID ViewModel to register [ActivityResultLauncher] during ON_CREATE event
 * for [Fragment] or [ComponentActivity] as [LifecycleOwner]. Requires Java 8+ bytecode.
 */
@PublishedApi
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdLifecycleObserver private constructor(
    private var ownIdBaseViewModelProducer: (() -> OwnIdBaseViewModel<out OwnIdEvent, out OwnIdEvent>)?,
    private var activityResultRegistryProducer: (() -> ActivityResultRegistry)?
) : DefaultLifecycleObserver {

    @PublishedApi
    internal companion object {

        @JvmSynthetic
        @PublishedApi
        internal fun observe(activity: ComponentActivity, ownIdBaseViewModelProducer: () -> OwnIdBaseViewModel<out OwnIdEvent, out OwnIdEvent>) {
            activity.lifecycle.addObserver(OwnIdLifecycleObserver(ownIdBaseViewModelProducer) {
                activity.activityResultRegistry
            })
        }

        @JvmSynthetic
        @PublishedApi
        internal fun observe(fragment: Fragment, ownIdBaseViewModelProducer: () -> OwnIdBaseViewModel<out OwnIdEvent, out OwnIdEvent>) {
            fragment.lifecycle.addObserver(OwnIdLifecycleObserver(ownIdBaseViewModelProducer) {
                (fragment.requireActivity() as ComponentActivity).activityResultRegistry
            })
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        val ownIdBaseViewModel = ownIdBaseViewModelProducer!!.invoke().apply {
            ownIdBaseViewModelProducer = null
        }

        val activityResultRegistry = activityResultRegistryProducer!!.invoke().apply {
            activityResultRegistryProducer = null
        }

        ownIdBaseViewModel.createResultLauncher(activityResultRegistry, owner)
    }
}