package com.ownid.sdk.compose.widget

import android.app.Activity
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.ownid.sdk.capability.UiContextResolutionException
import java.lang.ref.WeakReference

/** A widget's current host, kept weakly across its ViewModel's asynchronous flow start. */
internal class WidgetUiActivitySource(activity: Activity?, view: View) {
    private val activityRef = activity?.let(::WeakReference)
    private val viewRef = WeakReference(view)
    private var disposed = false

    fun current(): Activity {
        if (disposed) unavailable("The widget is no longer composed")
        if (viewRef.get()?.isAttachedToWindow != true) unavailable("The widget view is not attached to a window")
        return activityRef?.get() ?: unavailable("The widget has no Activity")
    }

    fun invalidate() {
        disposed = true
        activityRef?.clear()
        viewRef.clear()
    }

    private fun unavailable(message: String): Nothing =
        throw UiContextResolutionException(UiContextResolutionException.Reason.Unavailable, message)
}

@Composable
internal fun rememberWidgetUiActivitySource(): WidgetUiActivitySource {
    val activity = LocalActivity.current
    val view = LocalView.current
    val source = remember(activity, view) { WidgetUiActivitySource(activity, view) }

    DisposableEffect(source) {
        onDispose { source.invalidate() }
    }

    return source
}
