package com.ownid.sdk.capability.impl

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.ownid.sdk.capability.ActivityProvider
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@OptIn(InternalOwnIdAPI::class)
internal class ActivityProviderImpl(
    private val context: Context,
    private val logger: OwnIdLogRouter?
) : ActivityProvider, AutoCloseable {

    private val currentRef = AtomicReference<WeakReference<Activity>?>(null)
    private val registered = AtomicBoolean(false)

    override fun getActivity(): Activity? = currentRef.get()?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    override fun close() {
        logger?.logD(this@ActivityProviderImpl, "close", "unregisterActivityLifecycleCallbacks")
        (context as Application).unregisterActivityLifecycleCallbacks(callback)
        currentRef.set(null)
        registered.set(false)
    }

    private val callback = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) {
            currentRef.set(WeakReference(activity))
            val prefix = "onActivityStarted:${activity::class.java.simpleName}#${activity.hashCode()}"
            logger?.logD(this@ActivityProviderImpl, prefix, "Current: ${currentRef.get()?.get()?.hashCode()}")
        }

        override fun onActivityResumed(activity: Activity) {
            currentRef.set(WeakReference(activity))
            val prefix = "onActivityResumed:${activity::class.java.simpleName}#${activity.hashCode()}"
            logger?.logD(this@ActivityProviderImpl, prefix, "Current: ${currentRef.get()?.get()?.hashCode()}")
        }

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) {
            if (currentRef.get()?.get() == activity) currentRef.set(null)
            val prefix = "onActivityStopped:${activity::class.java.simpleName}#${activity.hashCode()}"
            logger?.logD(this@ActivityProviderImpl, prefix, "Current: ${currentRef.get()?.get()?.hashCode()}")
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    init {
        logger?.logD(this@ActivityProviderImpl, "init", "registerActivityLifecycleCallbacks")
        (context as Application).registerActivityLifecycleCallbacks(callback)
        registered.set(true)
    }
}
