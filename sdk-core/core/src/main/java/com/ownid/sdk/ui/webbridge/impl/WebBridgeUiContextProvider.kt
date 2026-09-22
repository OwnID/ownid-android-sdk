package com.ownid.sdk.ui.webbridge.impl

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.annotation.MainThread
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.capability.UiContextResolutionException
import java.lang.ref.WeakReference

/**
 * Bridge-local UI host with a retryable unbound state and a terminal closed state.
 *
 * [bind] unwraps the WebView context once and weakly retains that exact [Activity]; later [ContextWrapper] changes do
 * not replace it. [current] reports unavailable before binding or after the weak reference is lost.
 * [resetAfterFailedAttach] restores the retryable state after a failed attachment. [close], or [current] observing the
 * bound Activity closing or destroyed, makes the provider terminal. An initial bind whose Activity is already closing
 * or destroyed fails without closing the provider, so the attachment can be retried. All operations run on the main
 * thread.
 */
internal class WebBridgeUiContextProvider : UiContextProvider {
    private var activityRef: WeakReference<Activity>? = null
    private var closed = false

    @MainThread
    fun bind(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebBridgeUiContextProvider must run on the main thread" }
        check(!closed && activityRef == null) { "WebBridge UI context is already bound or closed" }

        val activity = context.findActivityOrNull()
            ?: throw UiContextResolutionException(
                reason = UiContextResolutionException.Reason.Unavailable,
                message = "WebView context does not contain an Activity for UI presentation"
            )
        if (activity.isFinishing || activity.isDestroyed) {
            throw UiContextResolutionException(
                reason = UiContextResolutionException.Reason.OwnerClosed,
                message = "The WebView Activity owner has closed"
            )
        }

        activityRef = WeakReference(activity)
    }

    @MainThread
    fun resetAfterFailedAttach() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebBridgeUiContextProvider must run on the main thread" }
        if (!closed) activityRef = null
    }

    @MainThread
    fun close() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebBridgeUiContextProvider must run on the main thread" }
        closed = true
        activityRef = null
    }

    @MainThread
    override fun current(): UiContextProvider.Result {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebBridgeUiContextProvider must run on the main thread" }

        if (closed) {
            return UiContextProvider.Result.Failure(
                UiContextResolutionException(
                    reason = UiContextResolutionException.Reason.OwnerClosed,
                    message = "The WebBridge Activity owner has closed"
                )
            )
        }

        val activity = activityRef?.get() ?: return UiContextProvider.Result.Failure(
            UiContextResolutionException(
                reason = UiContextResolutionException.Reason.Unavailable,
                message = "WebBridge is not attached to an Activity"
            )
        )

        if (activity.isFinishing || activity.isDestroyed) {
            closed = true
            activityRef = null
            return UiContextProvider.Result.Failure(
                UiContextResolutionException(
                    reason = UiContextResolutionException.Reason.OwnerClosed,
                    message = "The WebBridge Activity owner has closed"
                )
            )
        }

        return UiContextProvider.Result.Success(activity)
    }

    private fun Context.findActivityOrNull(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            val baseContext = context.baseContext
            if (baseContext === context) return null
            context = baseContext
        }
        return null
    }
}
