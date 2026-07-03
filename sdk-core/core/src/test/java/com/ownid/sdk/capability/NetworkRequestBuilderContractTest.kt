package com.ownid.sdk.capability

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.AccessToken
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.Test

// Covers: CAP-020
internal class NetworkRequestBuilderContractTest {

    private val url = "https://api.example.test/path".toHttpUrl()

    @Test
    fun buildDefaultsToPostEmptyJsonBodyAndSdkDefaultHeaders() {
        val request = Network.Request.Builder(url).build()

        assertThat(request.method).isEqualTo("POST")
        assertThat(request.header(Network.Request.Header.ACCEPT)).isEqualTo("application/json")
        assertThat(request.header(Network.Request.Header.CACHE_CONTROL)).isEqualTo("no-store")
        assertThat(request.bodyString()).isEqualTo("{}")
        assertThat(request.body?.contentType().toString()).isEqualTo("application/json; charset=utf-8")
    }

    @Test
    fun queuedSingletonHeadersReplaceSdkDefaultsAndPreserveAuthorizationAndTraceParent() {
        val request = Network.Request.Builder(url)
            .queueHeader(Network.Request.Header.ACCEPT to "application/vnd.ownid.test+json")
            .queueHeader(Network.Request.Header.CACHE_CONTROL to "no-cache")
            .queueAccessToken(AccessToken("token-1"))
            .queueTraceParent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00")
            .build()

        assertThat(request.headers(Network.Request.Header.ACCEPT)).containsExactly("application/vnd.ownid.test+json")
        assertThat(request.headers(Network.Request.Header.CACHE_CONTROL)).containsExactly("no-cache")
        assertThat(request.headers(Network.Request.Header.AUTHORIZATION)).containsExactly("Bearer token-1")
        assertThat(request.headers(Network.Request.Header.TRACE_PARENT)).containsExactly("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00")
    }

    @Test
    fun nonSingletonHeadersKeepAllQueuedValuesInOrder() {
        val request = Network.Request.Builder(url)
            .queueHeader("X-Test" to "first")
            .queueHeader("X-Test" to "second")
            .build()

        assertThat(request.headers("X-Test")).containsExactly("first", "second").inOrder()
    }

    @Test
    fun getRequestsHaveNoBodyButKeepSdkDefaultHeaders() {
        val request = Network.Request.Builder(url).apply {
            method = Network.Request.Method.GET
        }.build()

        assertThat(request.method).isEqualTo("GET")
        assertThat(request.body).isNull()
        assertThat(request.header(Network.Request.Header.ACCEPT)).isEqualTo("application/json")
        assertThat(request.header(Network.Request.Header.CACHE_CONTROL)).isEqualTo("no-store")
    }

    @Test
    fun suppressHttpLogTagsOnlySuppressedRequests() {
        val request = Network.Request.Builder(url).suppressHttpLog().build()

        assertThat(request.tag(Network.Request.NoHttpLogTag::class.java)).isSameInstanceAs(Network.Request.NoHttpLogTag)
    }

    private fun okhttp3.Request.bodyString(): String {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8()
    }
}
