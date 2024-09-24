package com.ownid.sdk.internal.feature.webflow

import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdProviders
import com.ownid.sdk.dsl.start
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.webbridge.handler.OwnIdWebViewBridgeFlow

/**
 * Starts the OwnID Elite.
 *
 * This function initiates the OwnID Elite, which involves launching an activity containing a WebView to interact with the OwnID.
 * You can provide event wrappers to respond to different stages of the flow.
 *
 * **Note:** This is a low-level API. It's recommended to use the DSL builder [OwnId.start].
 *
 * **Note:** This function must be called from the main thread.
 *
 * @param providers   Optional, if present will override Global providers set in [OwnId.providers]
 * @param eventWrappers Wrappers for different events of the OwnID flow.
 *
 * @return An [AutoCloseable] object that can be used to cancel the OwnID flow.
 *
 * @throws IllegalArgumentException if called from a thread other than the main thread.
 * @throws IllegalStateException if no OwnID instances available.
 */
@MainThread
@InternalOwnIdAPI
@Throws(IllegalArgumentException::class, IllegalStateException::class)
internal fun OwnId.start(
    providers: OwnIdProviders?,
    eventWrappers: List<OwnIdFlowWrapper<*>>
): AutoCloseable {
    check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

    val allWrappers = combineWrappers(providers, eventWrappers)

    val ownIdCore = instance.ownIdCore as OwnIdCoreImpl
    val closeable = OwnIdWebViewBridgeFlow.setWrappers(ownIdCore, allWrappers)

    ownIdCore.applicationContext.run { startActivity(OwnIdFlowFeature.createIntent(this)) }
    ownIdCore.eventsService.sendMetric(Metric.Category.General, Metric.EventType.Track, "Flow Triggered From Mobile SDK")

    return closeable
}

@InternalOwnIdAPI
@VisibleForTesting
internal fun combineWrappers(providers: OwnIdProviders?, eventWrappers: List<OwnIdFlowWrapper<*>>) = buildList {
    val globalProvidersWrappers = OwnId.providers.toWrappers()
    if (providers != null) {
        val providersWrappers = providers.toWrappers()
        addAll(providersWrappers)
        globalProvidersWrappers.forEach { globalWrapper ->
            if (providersWrappers.none { it::class == globalWrapper::class }) add(globalWrapper)
        }
    } else {
        addAll(globalProvidersWrappers)
    }

    addAll(eventWrappers)
    if (eventWrappers.none { it is OnFinishWrapper }) add(OnFinishWrapper.DEFAULT)
    if (eventWrappers.none { it is OnErrorWrapper }) add(OnErrorWrapper.DEFAULT)
    if (eventWrappers.none { it is OnCloseWrapper }) add(OnCloseWrapper.DEFAULT)
}