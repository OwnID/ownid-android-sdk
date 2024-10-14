package com.ownid.sdk.internal.webbridge

import android.app.Application
import android.net.Uri
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeContext
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdWebViewBridgeContextTest {
    private val testDispatcher = StandardTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    public fun setup() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>().mainLooper).idle()
        Dispatchers.setMain(testDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test(expected = IllegalStateException::class)
    public fun `ensureMainFrame - throws exception if not main frame`() {
        val context = OwnIdWebViewBridgeContext(
            mockk(), mockk(), Job(), emptyList(), mockk(), false, "callback"
        )
        context.ensureMainFrame()
    }

    @Test
    public fun `ensureMainFrame - does not throw exception if main frame`() {
        val context = OwnIdWebViewBridgeContext(
            mockk(), mockk(), Job(), emptyList(), mockk(), true, "callback"
        )
        context.ensureMainFrame()
    }

    @Test(expected = IllegalStateException::class)
    public fun `ensureAllowedOrigin - throws exception if origin not allowed`() {
        val context = OwnIdWebViewBridgeContext(
            mockk(), mockk(), Job(), listOf("https://allowed.com"), Uri.parse("https://example.com"), true, "callback"
        )
        context.ensureAllowedOrigin()
    }

    @Test
    public fun `ensureAllowedOrigin - does not throw exception if origin allowed`() {
        val context = OwnIdWebViewBridgeContext(
            mockk(), mockk(), Job(), listOf("https://example.com"), Uri.parse("https://example.com"), true, "callback"
        )
        context.ensureAllowedOrigin()
    }

    @Test
    public fun `ensureAllowedOrigin - does not throw exception if wildcard origin allowed`() {
        val context = OwnIdWebViewBridgeContext(
            mockk(), mockk(), Job(), listOf("https://*.example.com"), Uri.parse("https://www.example.com"), true, "callback"
        )
        context.ensureAllowedOrigin()
    }

    @Test
    public fun `finishWithSuccess - evaluates JavaScript`() {
        val slotScript = slot<String>()

        val webView = mockk<WebView> {
            every { context } returns ApplicationProvider.getApplicationContext()
            every { evaluateJavascript(capture(slotScript), any()) } returns Unit
        }
        val context = OwnIdWebViewBridgeContext(
            mockk(), webView, Job(), emptyList(), mockk(), true, "callback"
        )
        context.finishWithSuccess("""{"result": "success"}""")

        verify(exactly = 1) {
            webView.evaluateJavascript(capture(slotScript), any())
        }

        Truth.assertThat(slotScript.captured).contains("javascript:callback({\"result\": \"success\"})")
    }

    @Test
    public fun `finishWithError - evaluates JavaScript with error`() {
        val slotScript = slot<String>()

        val webView = mockk<WebView> {
            every { context } returns ApplicationProvider.getApplicationContext()
            every { evaluateJavascript(capture(slotScript), any()) } returns Unit
        }
        val context = OwnIdWebViewBridgeContext(
            mockk(), webView, Job(), emptyList(), mockk(), true, "callback"
        )
        val handler = mockk<OwnIdWebViewBridgeImpl.NamespaceHandler>()
        val exception = Exception("Test exception")
        context.finishWithError(handler, exception)

        verify(exactly = 1) {
            webView.evaluateJavascript(capture(slotScript), any())
        }

        Truth.assertThat(slotScript.captured)
            .matches("javascript:callback\\(\\{\"error\":\\{\"name\":\"[a-zA-Z0-9\\$\\.]+\",\"type\":\"Exception\",\"message\":\"Test exception\"\\}\\}\\)")
    }

    @Test
    public fun `finishWithSuccess - cancels context`() {
        val webView = mockk<WebView> {
            every { context } returns ApplicationProvider.getApplicationContext()
            every { evaluateJavascript(any(), any()) } returns Unit
        }
        val context = OwnIdWebViewBridgeContext(
            mockk(), webView, Job(), emptyList(), mockk(), true, "callback"
        )
        context.finishWithSuccess("""{"result": "success"}""")
        Truth.assertThat(context.isActive).isFalse()
    }

    @Test
    public fun `finishWithError - cancels context`() {
        val webView = mockk<WebView> {
            every { context } returns ApplicationProvider.getApplicationContext()
            every { evaluateJavascript(any(), any()) } returns Unit
        }
        val context = OwnIdWebViewBridgeContext(
            mockk(), webView, Job(), emptyList(), mockk(), true, "callback"
        )
        val handler = mockk<OwnIdWebViewBridgeImpl.NamespaceHandler>()
        val exception = Exception("Test exception")
        context.finishWithError(handler, exception)
        Truth.assertThat(context.isActive).isFalse()
    }
}