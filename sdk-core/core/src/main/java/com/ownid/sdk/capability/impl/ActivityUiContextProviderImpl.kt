package com.ownid.sdk.capability.impl

import android.app.Activity
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModelStore
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.UiContextResolutionException
import java.lang.ref.WeakReference
import java.util.UUID

/** UI-context provider bound to one logical app Activity. */
internal class ActivityUiContextProviderImpl(
    activity: Activity,
    rootUiContextProvider: UiContextProviderImpl
) : UiContextProvider, UiContextProviderImpl.ActivityBindingObserver {

    private enum class State { Live, AwaitingConfigurationReplacement, Unavailable, Closed }

    private sealed interface Identity {
        class Component(val storeRef: WeakReference<ViewModelStore>) : Identity
        class Plain(val savedStateKey: String) : Identity
        object Exact : Identity
    }

    private var activityRef: WeakReference<Activity> = WeakReference(activity)
    private var state: State
    private val identity: Identity
    private var plainStateSaved: Boolean = false
    private var lifecycleSubscription: UiContextProviderImpl.ActivityBindingSubscription? = null

    init {
        check(Looper.myLooper() == Looper.getMainLooper()) { "ActivityUiContextProviderImpl must be created on the main thread" }

        state = when {
            activity.isFinishing -> State.Closed
            activity.isDestroyed && activity.isChangingConfigurations -> State.Unavailable
            activity.isDestroyed -> State.Closed
            else -> State.Live
        }

        if (state != State.Live) {
            identity = Identity.Exact
        } else {
            checkNotNull(activity.application) {
                "uiContext(activity) requires an Activity attached to an Application"
            }
            identity = if (activity is ComponentActivity) {
                val componentStore = checkNotNull(activity.viewModelStoreOrNull()) {
                    "uiContext(activity) requires the ComponentActivity ViewModelStore to be available"
                }
                Identity.Component(WeakReference(componentStore))
            } else {
                Identity.Plain(SAVED_STATE_KEY_PREFIX + UUID.randomUUID())
            }
            lifecycleSubscription = rootUiContextProvider.subscribeActivityBinding(this)
        }
    }

    @MainThread
    override fun current(): UiContextProvider.Result {
        check(Looper.myLooper() == Looper.getMainLooper()) { "UiContextProvider.current must run on the main thread" }

        return when (state) {
            State.Closed -> ownerClosed("The bound Activity owner has closed")
            State.AwaitingConfigurationReplacement -> unavailable("The bound Activity is being replaced after a configuration change")
            State.Unavailable -> unavailable("The bound Activity is unavailable and cannot be recovered")
            State.Live -> {
                val currentActivity = activityRef.get() ?: run {
                    becomeUnavailable()
                    return unavailable("The bound Activity reference is unavailable")
                }

                when {
                    currentActivity.isFinishing -> {
                        closeOwner()
                        ownerClosed("The bound Activity is finishing")
                    }

                    currentActivity.isDestroyed && currentActivity.isChangingConfigurations -> {
                        handleConfigurationDestruction()
                        unavailable("The bound Activity was destroyed for a configuration change")
                    }

                    currentActivity.isDestroyed -> {
                        closeOwner()
                        ownerClosed("The bound Activity was destroyed")
                    }

                    else -> UiContextProvider.Result.Success(currentActivity)
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (state != State.AwaitingConfigurationReplacement) return

        val matches = when (val ownerIdentity = identity) {
            is Identity.Component -> {
                val expectedStore = ownerIdentity.storeRef.get()
                if (expectedStore == null) {
                    becomeUnavailable()
                    false
                } else {
                    activity is ComponentActivity && activity.viewModelStoreOrNull() === expectedStore
                }
            }

            is Identity.Plain -> savedInstanceState?.getBoolean(ownerIdentity.savedStateKey) == true
            Identity.Exact -> false
        }

        if (matches) {
            activityRef = WeakReference(activity)
            plainStateSaved = false
            state = State.Live
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        if (state != State.Live || activityRef.get() !== activity) return

        val plainIdentity = identity as? Identity.Plain ?: return
        outState.putBoolean(plainIdentity.savedStateKey, true)
        plainStateSaved = true
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (state != State.Live || activityRef.get() !== activity) return

        if (activity.isChangingConfigurations && !activity.isFinishing) {
            handleConfigurationDestruction()
        } else {
            closeOwner()
        }
    }

    private fun handleConfigurationDestruction() {
        val canRecover = when (val ownerIdentity = identity) {
            is Identity.Component -> ownerIdentity.storeRef.get() != null
            is Identity.Plain -> plainStateSaved
            Identity.Exact -> false
        }

        if (canRecover) {
            activityRef.clear()
            state = State.AwaitingConfigurationReplacement
        } else {
            becomeUnavailable()
        }
    }

    private fun closeOwner() {
        activityRef.clear()
        state = State.Closed
        lifecycleSubscription?.unsubscribe()
        lifecycleSubscription = null
    }

    private fun becomeUnavailable() {
        activityRef.clear()
        state = State.Unavailable
        lifecycleSubscription?.unsubscribe()
        lifecycleSubscription = null
    }

    private fun ComponentActivity.viewModelStoreOrNull(): ViewModelStore? = try {
        viewModelStore
    } catch (_: IllegalStateException) {
        null
    }

    private fun unavailable(message: String): UiContextProvider.Result.Failure =
        UiContextProvider.Result.Failure(UiContextResolutionException(UiContextResolutionException.Reason.Unavailable, message))

    private fun ownerClosed(message: String): UiContextProvider.Result.Failure =
        UiContextProvider.Result.Failure(UiContextResolutionException(UiContextResolutionException.Reason.OwnerClosed, message))

    private companion object {
        private const val SAVED_STATE_KEY_PREFIX = "com.ownid.sdk.uiContext."
    }
}
