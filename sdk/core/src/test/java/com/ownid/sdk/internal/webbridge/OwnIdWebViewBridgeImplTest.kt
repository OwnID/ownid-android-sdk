package com.ownid.sdk.internal.webbridge

import android.os.Looper
import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl.Companion.asHttpsHostOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
public class OwnIdWebViewBridgeImplTest {
    private val testDispatcher = StandardTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    public fun setup() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Dispatchers.setMain(testDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun `asHttpsHostOrNull - valid HTTPS URL returns HTTPS host`() {
        val url = "https://www.example.com:443/path"
        val result = url.asHttpsHostOrNull()
        Truth.assertThat(result).isEqualTo("https://www.example.com")
    }

    @Test
    public fun `asHttpsHostOrNull - valid HTTP URL returns null`() {
        val url = "http://www.example.com/path"
        val result = url.asHttpsHostOrNull()
        Truth.assertThat(result).isNull()
    }

    @Test
    public fun `asHttpsHostOrNull - valid domain with HTTP scheme returns null`() {
        val domain = "http://www.example.com"
        val result = domain.asHttpsHostOrNull()
        Truth.assertThat(result).isNull()
    }

    @Test
    public fun `asHttpsHostOrNull - invalid URL returns null`() {
        val url = "http://"
        val result = url.asHttpsHostOrNull()
        Truth.assertThat(result).isNull()
    }

    @Test
    public fun `asHttpsHostOrNull - empty string returns null`() {
        val url = ""
        val result = url.asHttpsHostOrNull()
        Truth.assertThat(result).isNull()
    }

    @Test
    public fun `asHttpsHostOrNull - URL with no scheme returns null`() {
        val url = "www.example.com/path"
        val result = url.asHttpsHostOrNull()
        Truth.assertThat(result).isNull()
    }

    @Test
    public fun `asHttpsHostOrNull - URL with user info returns HTTPS host without user info`() {
        val url = "https://user:password@www.example.com/path"
        val result = url.asHttpsHostOrNull()
        Truth.assertThat(result).isEqualTo("https://www.example.com")
    }
}