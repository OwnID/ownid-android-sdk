package com.ownid.sdk.ui.webbridge.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.ui.webbridge.WebBridgePluginKey
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: WB-080
internal class WebBridgePluginRegistryImplContractTest {

    @Test
    fun replacesByKeyWithoutChangingInjectionOrder() {
        val firstKey = WebBridgePluginKey.from("first")
        val secondKey = WebBridgePluginKey.from("second")
        val first = TestWebBridgePlugin(firstKey, listOf("old"))
        val second = TestWebBridgePlugin(secondKey, listOf("second"))
        val replacement = TestWebBridgePlugin(firstKey, listOf("new"))
        val registry = WebBridgePluginRegistryImpl(initialPlugins = listOf(first, second))

        registry.add(replacement)
        val captured = registry.snapshot()
        registry.remove(firstKey)

        assertThat(registry.get(firstKey)).isNull()
        assertThat(registry.get(secondKey)).isSameInstanceAs(second)
        assertThat(captured).containsExactly(replacement, second).inOrder()
    }
}
