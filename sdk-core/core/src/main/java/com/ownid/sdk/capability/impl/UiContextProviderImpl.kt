package com.ownid.sdk.capability.impl

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.annotation.MainThread
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.UiContextResolutionException
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.OwnIdHiddenActivity
import com.ownid.sdk.ui.OwnIdWebViewActivity
import java.lang.ref.WeakReference

/** Process-root, best-effort UI host selection from application lifecycle observations. */
@OptIn(InternalOwnIdAPI::class)
internal class UiContextProviderImpl(
    application: Application
) : UiContextProvider, Application.ActivityLifecycleCallbacks {

    internal interface ActivityBindingObserver {
        fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?)
        fun onActivitySaveInstanceState(activity: Activity, outState: Bundle)
        fun onActivityDestroyed(activity: Activity)
    }

    internal fun interface ActivityBindingSubscription {
        @MainThread
        fun unsubscribe()
    }

    private class Candidate(
        val activity: WeakReference<Activity>,
        var resumed: Boolean = false,
        var lastResumeSequence: Long = 0,
        var lastStartSequence: Long = 0
    )

    private class LifecycleObserverEntry(observer: ActivityBindingObserver) {
        val observerRef = WeakReference(observer)
        var active: Boolean = true
    }

    private val candidates = mutableListOf<Candidate>()
    private val lifecycleObservers = mutableListOf<LifecycleObserverEntry>()
    private var sequence: Long = 0

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    @MainThread
    internal fun subscribeActivityBinding(observer: ActivityBindingObserver): ActivityBindingSubscription {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Activity UI-context lifecycle observers must be registered on the main thread" }
        pruneLifecycleObservers()

        val entry = LifecycleObserverEntry(observer)
        lifecycleObservers += entry

        return ActivityBindingSubscription {
            check(Looper.myLooper() == Looper.getMainLooper()) { "Activity UI-context lifecycle observers must be unregistered on the main thread" }
            if (entry.active) {
                entry.active = false
                lifecycleObservers.remove(entry)
            }
        }
    }

    @MainThread
    override fun current(): UiContextProvider.Result {
        check(Looper.myLooper() == Looper.getMainLooper()) { "UiContextProvider.current must run on the main thread" }
        pruneCandidates()

        var selected: Activity? = null
        var selectedTier = -1
        var selectedResumeSequence = 0L
        var selectedStartSequence = 0L

        candidates.forEach { candidate ->
            val activity = candidate.activity.get() ?: return@forEach
            val focused = activity.window?.peekDecorView()?.hasWindowFocus() == true
            val tier = if (focused) 2 else if (candidate.resumed) 1 else 0
            if (tier > selectedTier ||
                (tier == selectedTier && candidate.lastResumeSequence > selectedResumeSequence) ||
                (tier == selectedTier && candidate.lastResumeSequence == selectedResumeSequence && candidate.lastStartSequence > selectedStartSequence)
            ) {
                selected = activity
                selectedTier = tier
                selectedResumeSequence = candidate.lastResumeSequence
                selectedStartSequence = candidate.lastStartSequence
            }
        }

        return selected?.let { UiContextProvider.Result.Success(it) }
            ?: UiContextProvider.Result.Failure(
                UiContextResolutionException(
                    reason = UiContextResolutionException.Reason.Unavailable,
                    message = "No eligible app Activity is currently tracked for UI presentation"
                )
            )
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        dispatchLifecycleObservers { it.onActivityCreated(activity, savedInstanceState) }
    }

    override fun onActivityStarted(activity: Activity) {
        pruneLifecycleObservers()
        if (activity.isSdkHost()) return
        val candidate = getOrAddCandidate(activity)
        candidate.resumed = false
        candidate.lastStartSequence = ++sequence
    }

    override fun onActivityResumed(activity: Activity) {
        pruneLifecycleObservers()
        if (activity.isSdkHost()) return
        val candidate = getOrAddCandidate(activity)
        candidate.resumed = true
        candidate.lastResumeSequence = ++sequence
    }

    override fun onActivityPaused(activity: Activity) {
        pruneLifecycleObservers()
        candidates.firstOrNull { it.activity.get() === activity }?.resumed = false
    }

    override fun onActivityStopped(activity: Activity) {
        pruneLifecycleObservers()
        removeCandidate(activity)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        dispatchLifecycleObservers { it.onActivitySaveInstanceState(activity, outState) }
    }

    override fun onActivityDestroyed(activity: Activity) {
        removeCandidate(activity)
        dispatchLifecycleObservers { it.onActivityDestroyed(activity) }
    }

    private fun Activity.isSdkHost(): Boolean = this is OwnIdHiddenActivity || this is OwnIdWebViewActivity

    private fun getOrAddCandidate(activity: Activity): Candidate {
        pruneCandidates()
        return candidates.firstOrNull { it.activity.get() === activity }
            ?: Candidate(WeakReference(activity)).also(candidates::add)
    }

    private fun pruneCandidates() {
        candidates.removeAll { candidate ->
            val activity = candidate.activity.get()
            activity == null || activity.isFinishing || activity.isDestroyed
        }
    }

    private fun removeCandidate(activity: Activity) {
        candidates.removeAll { it.activity.get() == null || it.activity.get() === activity }
    }

    private inline fun dispatchLifecycleObservers(block: (ActivityBindingObserver) -> Unit) {
        pruneLifecycleObservers()
        val snapshot = lifecycleObservers.toList()
        try {
            snapshot.forEach { entry ->
                if (entry.active) entry.observerRef.get()?.let(block)
            }
        } finally {
            pruneLifecycleObservers()
        }
    }

    private fun pruneLifecycleObservers() {
        lifecycleObservers.removeAll { entry -> !entry.active || entry.observerRef.get() == null }
    }
}
