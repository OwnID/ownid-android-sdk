package com.ownid.sdk.ui.webbridge

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.InternalOwnIdAPI
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
@OptIn(InternalOwnIdAPI::class)
@Suppress("DEPRECATION")
// Covers: WB-BRIDGE-160
internal class EliteWebViewClientRuntimeContractTest {

    @Test
    fun bothNavigationCallbacksBlockUnsafeSchemesWithoutExternalDispatch() = withWebView { activity, webView ->
        val errors = mutableListOf<String>()
        val client = client(webView, errors)

        assertThat(client.shouldOverrideUrlLoading(webView, "javascript:alert(1)")).isTrue()
        listOf("data", "file", "content", "intent").forEach { scheme ->
            assertThat(client.shouldOverrideUrlLoading(webView, request(Uri.parse("$scheme://blocked.example/path")))).isTrue()
        }

        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        assertThat(errors).isEmpty()
    }

    @Test
    fun ownIdJavaScriptCallbacksMapToTerminalErrors() = withWebView { _, webView ->
        val errors = mutableListOf<String>()
        val client = client(webView, errors)

        assertThat(client.shouldOverrideUrlLoading(webView, request(Uri.parse("ownid://on-js-load-error")))).isTrue()
        assertThat(client.shouldOverrideUrlLoading(webView, "ownid://on-js-exception?ex=boom")).isTrue()

        assertThat(errors).containsExactly(
            "Failed to load content: ownid://on-js-load-error",
            "JavaScript exception: boom"
        ).inOrder()
    }

    @Test
    fun genericLoadErrorIsLoggedWithoutTerminalCallback() = withWebView { _, webView ->
        val errors = mutableListOf<String>()
        val logs = mutableListOf<LogEntry>()
        val client = EliteWebViewClient(webView, recordingRouter(logs), errors::add, onRendererGone = {})

        client.onReceivedError(
            webView,
            request(Uri.parse("https://login.example.test/unavailable")),
            webResourceError(errorCode = -2, description = "host unavailable")
        )

        assertThat(errors).isEmpty()
        assertThat(logs.single().level).isEqualTo(OwnIdLogger.Level.Info)
        assertThat(logs.single().message).contains("host unavailable")
        assertThat(logs.single().message).contains("https://login.example.test/unavailable")
    }

    @Test
    fun externalHttpsUsesBrowsableViewIntentAndGmailPrefersPackageLaunch() = withWebView { activity, webView ->
        val errors = mutableListOf<String>()
        val gmailLaunchIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(GMAIL_PACKAGE)
        shadowOf(activity.packageManager).addResolveInfoForIntent(
            gmailLaunchIntent,
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = GMAIL_PACKAGE
                    name = "$GMAIL_PACKAGE.ConversationListActivity"
                }
            }
        )
        val client = client(webView, errors)

        assertThat(client.shouldOverrideUrlLoading(webView, request(Uri.parse("https://www.gmail.com/mail/u/0")))).isTrue()
        val gmailIntent = shadowOf(activity).nextStartedActivity
        assertThat(gmailIntent.`package`).isEqualTo(GMAIL_PACKAGE)
        assertThat(gmailIntent.component?.packageName).isEqualTo(GMAIL_PACKAGE)

        assertThat(client.shouldOverrideUrlLoading(webView, "https://accounts.example.test/continue")).isTrue()
        val externalIntent = shadowOf(activity).nextStartedActivity
        assertThat(externalIntent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(externalIntent.data).isEqualTo(Uri.parse("https://accounts.example.test/continue"))
        assertThat(externalIntent.categories).contains(Intent.CATEGORY_BROWSABLE)
        assertThat(errors).isEmpty()
    }

    @Test
    fun rendererLossCallbackRunsBeforeAffectedWebViewIsRemovedAndDestroyed() = withWebView { activity, webView ->
        activity.setContentView(webView)
        val callbackState = mutableListOf<Pair<Boolean, Boolean>>()
        val rendererDetails = mutableListOf<String>()
        val client = EliteWebViewClient(
            webView,
            logger = null,
            onError = {},
            onRendererGone = { details ->
                rendererDetails += details
                callbackState += (webView.parent != null) to shadowOf(webView).wasDestroyCalled()
            }
        )

        assertThat(client.onRenderProcessGone(webView, renderProcessGoneDetail())).isTrue()

        assertThat(rendererDetails).containsExactly("WebView render process gone. [didCrash: true]")
        assertThat(callbackState).containsExactly(true to false)
        assertThat(webView.parent).isNull()
        assertThat(shadowOf(webView).wasDestroyCalled()).isTrue()
    }

    private fun client(webView: WebView, errors: MutableList<String>): EliteWebViewClient =
        EliteWebViewClient(webView, logger = null, onError = errors::add, onRendererGone = {})

    private inline fun withWebView(block: (ComponentActivity, WebView) -> Unit) {
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = activityController.get()
            block(activity, WebView(activity))
        } finally {
            activityController.destroyIfNeeded()
        }
    }

    private fun request(uri: Uri): WebResourceRequest = object : WebResourceRequest {
        override fun getUrl(): Uri = uri
        override fun isForMainFrame(): Boolean = true
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }

    private fun renderProcessGoneDetail(): RenderProcessGoneDetail =
        object : RenderProcessGoneDetail() {
            override fun didCrash(): Boolean = true
            override fun rendererPriorityAtExit(): Int = 0
        }

    private fun webResourceError(errorCode: Int, description: String): WebResourceError =
        mockk<WebResourceError> {
            every { this@mockk.errorCode } returns errorCode
            every { this@mockk.description } returns description
        }

    private fun recordingRouter(entries: MutableList<LogEntry>): OwnIdLogRouter {
        val logger = object : OwnIdLogger {
            override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
            override val tag: String = "OwnID-WebBridge-Client-Test"

            override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
                entries += LogEntry(level, message)
            }
        }
        return OwnIdLogRouter(ownIdLoggerProvider = { logger }, serverLoggersProvider = { emptyList() })
    }

    private fun ActivityController<out android.app.Activity>.destroyIfNeeded() {
        if (!get().isDestroyed) destroy()
    }

    private data class LogEntry(val level: OwnIdLogger.Level, val message: String)

    private companion object {
        private const val GMAIL_PACKAGE = "com.google.android.gm"
    }
}
