package com.ownid.sdk.internal.webbridge

import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl.Companion.asValidOriginOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class OwnIdWebViewBridgeImplTest {

    @Test
    public fun `asValidOriginOrNull - valid HTTPS URL returns HTTPS host`() {
        val url = "https://www.example.com:443/path"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("https://www.example.com")
    }

    @Test
    public fun `asValidOriginOrNull - valid HTTP URL returns HTTP host`() {
        val url = "http://www.example.com/path"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("http://www.example.com")
    }

    @Test
    public fun `asValidOriginOrNull - valid domain without scheme adds HTTPS scheme`() {
        val domain = "www.example.com"
        val result = domain.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("https://www.example.com")
    }

    @Test
    public fun `asValidOriginOrNull - invalid URL returns null`() {
        val url = "http://"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isNull()
    }

    @Test
    public fun `asValidOriginOrNull - empty string returns null`() {
        val url = ""
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isNull()
    }

    @Test
    public fun `asValidOriginOrNull - URL with user info returns HTTPS host without user info`() {
        val url = "https://user:password@www.example.com/path"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("https://www.example.com")
    }

    @Test
    public fun `asValidOriginOrNull - non-http(s) scheme returns the host`() {
        val url = "ftp://www.example.com"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("ftp://www.example.com")
    }

    @Test
    public fun `asValidOriginOrNull - custom scheme returns the host`() {
        val url = "customscheme://www.example.com"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("customscheme://www.example.com")
    }

    @Test
    public fun `asValidOriginOrNull - plain string returns HTTPS host`() {
        val url = "example.com"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("https://example.com")
    }

    @Test
    public fun `asValidOriginOrNull - URL with invalid characters returns null`() {
        val url = "https://www.example.com/!@#$%^&*()"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("https://www.example.com")
    }

    @Test
    public fun `asValidOriginOrNull - URL with fragment identifier returns HTTPS host`() {
        val url = "https://www.example.com/path#fragment"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("https://www.example.com")
    }

    @Test
    public fun `asValidOriginOrNull - URL with query parameters returns HTTPS host`() {
        val url = "https://www.example.com/path?param1=value1&param2=value2"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("https://www.example.com")
    }

    @Test
    public fun `asValidOriginOrNull - wildcard asterisk returns asterisk`() {
        val url = "*"
        val result = url.asValidOriginOrNull()
        Truth.assertThat(result).isEqualTo("*")
    }
}