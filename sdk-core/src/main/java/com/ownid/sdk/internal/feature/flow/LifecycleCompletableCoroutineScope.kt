package com.ownid.sdk.internal.feature.flow

import androidx.annotation.RestrictTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.ownid.sdk.InternalOwnIdAPI
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LifecycleCompletableCoroutineScope(
    internal val lifecycle: Lifecycle,
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main.immediate,
    handler: CompletionHandler
) : CoroutineScope, LifecycleEventObserver {

    init {
        coroutineContext.job.invokeOnCompletion(handler)

        if (lifecycle.currentState >= Lifecycle.State.INITIALIZED) {
            lifecycle.addObserver(this@LifecycleCompletableCoroutineScope)
        } else {
            coroutineContext.cancel()
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (lifecycle.currentState <= Lifecycle.State.DESTROYED) {
            lifecycle.removeObserver(this)
            coroutineContext.cancel()
        }
    }
}