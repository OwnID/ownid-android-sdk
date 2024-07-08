package com.ownid.sdk.internal.webflow

import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.feature.OwnIdHiddenActivity
import com.ownid.sdk.internal.feature.webflow.FlowWebViewClient
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class FlowWebViewClientTest {
    private lateinit var webView: WebView
    private lateinit var error: OwnIdException
    private val onError = object : (OwnIdException) -> Unit {
        override fun invoke(e: OwnIdException) {
            error = e
        }
    }
    private lateinit var webViewClient: FlowWebViewClient

    private val hiddenActivity = Robolectric.buildActivity(OwnIdHiddenActivity::class.java).create().get()
    private val shadowActivity = Shadows.shadowOf(hiddenActivity)

    @Before
    public fun setUp() {
        webView = WebView(RuntimeEnvironment.getApplication())
        webViewClient = FlowWebViewClient(webView, onError)
    }

    @Test
    public fun `shouldOverrideUrlLoading - handles JSLoadError`() {
        val request = mockk<WebResourceRequest> {
            every { url } returns Uri.parse("ownid://on-js-load-error")
        }

        val result = webViewClient.shouldOverrideUrlLoading(webView, request)

        Truth.assertThat(result).isTrue()
        Truth.assertThat(error).isInstanceOf(OwnIdException::class.java)
        Truth.assertThat(error.message).isEqualTo("Filed to load content")
    }

    @Test
    public fun `shouldOverrideUrlLoading - handles JSException`() {
        val request = mockk<WebResourceRequest> {
            every { url } returns Uri.parse("ownid://on-js-exception?ex=Some%20error%20message")
        }

        val result = webViewClient.shouldOverrideUrlLoading(webView, request)

        Truth.assertThat(result).isTrue()
        Truth.assertThat(error).isInstanceOf(OwnIdException::class.java)
        Truth.assertThat(error.message).isEqualTo("Some error message")
    }

    @Test
    public fun `shouldOverrideUrlLoading - handles JSException without ex parameter`() {
        val request = mockk<WebResourceRequest> {
            every { url } returns Uri.parse("ownid://on-js-exception")
        }

        val result = webViewClient.shouldOverrideUrlLoading(webView, request)

        Truth.assertThat(result).isTrue()
        Truth.assertThat(error).isInstanceOf(OwnIdException::class.java)
        Truth.assertThat(error.message).isEqualTo("ownid://on-js-exception")
    }

    @Test
    public fun `shouldOverrideUrlLoading - opens external URLs`() {
        val webView = mockk<WebView>(relaxed = true)
        every { webView.context } returns hiddenActivity
        val request = mockk<WebResourceRequest> {
            every { url } returns Uri.parse("https://www.example.com")
        }

        val result = webViewClient.shouldOverrideUrlLoading(webView, request)
        val startedIntent = shadowActivity.nextStartedActivity

        Truth.assertThat(result).isTrue()
        Truth.assertThat(startedIntent).isNotNull()
        Truth.assertThat(startedIntent.data.toString()).isEqualTo("https://www.example.com")
    }

    @Test
    public fun `onRenderProcessGone - handles render process crash`() {
        val detail = mockk<RenderProcessGoneDetail> {
            every { didCrash() } returns true
        }

        val result = webViewClient.onRenderProcessGone(webView, detail)

        Truth.assertThat(result).isTrue()
        Truth.assertThat(error).isInstanceOf(OwnIdException::class.java)
        Truth.assertThat(error.message).isEqualTo("WebView's render process has exited")
    }

    @Test
    public fun `onRenderProcessGone - delegates to super for other WebViews`() {
        val otherWebView = mockk<WebView>()
        val detail = mockk<RenderProcessGoneDetail>()

        val result = webViewClient.onRenderProcessGone(otherWebView, detail)

        Truth.assertThat(result).isFalse()
    }
}