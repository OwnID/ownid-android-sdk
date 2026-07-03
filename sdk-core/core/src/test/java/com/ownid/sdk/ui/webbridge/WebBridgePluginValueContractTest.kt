package com.ownid.sdk.ui.webbridge

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.InternalOwnIdAPI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: WB-040, WB-100, WB-110, WB-120
internal class WebBridgePluginValueContractTest {

    @Test
    fun pluginKeysNormalizeToUppercaseAndCompareByCanonicalKey() {
        val lowerCaseKey = WebBridgePluginKey.from("fido")
        val upperCaseKey = WebBridgePluginKey.from("FIDO")

        assertThat(lowerCaseKey.id).isEqualTo("FIDO")
        assertThat(lowerCaseKey.key).isEqualTo("FIDO")
        assertThat(lowerCaseKey.toString()).isEqualTo("FIDO")
        assertThat(lowerCaseKey).isEqualTo(upperCaseKey)
        assertThat(lowerCaseKey.hashCode()).isEqualTo(upperCaseKey.hashCode())
        assertThat(lowerCaseKey).isNotEqualTo(WebBridgePluginKey.from("storage"))
    }

    @Test
    fun pluginKeyRejectsBlankIdsAfterNormalization() {
        assertThrows(IllegalArgumentException::class.java) {
            WebBridgePluginKey.from("  ")
        }
    }

    @Test
    fun messagePayloadRequiresRoutablePluginActionAndCallbackPath() {
        val payload = WebBridgePlugin.Message.Payload(
            pluginId = "FIDO",
            action = "get",
            callbackPath = "ownid.callback",
            params = """{"challenge":"abc"}"""
        )

        assertThat(payload.pluginId).isEqualTo("FIDO")
        assertThat(payload.action).isEqualTo("get")
        assertThat(payload.callbackPath).isEqualTo("ownid.callback")
        assertThat(payload.params).isEqualTo("""{"challenge":"abc"}""")
        assertThrows(IllegalArgumentException::class.java) {
            WebBridgePlugin.Message.Payload(pluginId = "", action = "get", callbackPath = "ownid.callback")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebBridgePlugin.Message.Payload(pluginId = "FIDO", action = " ", callbackPath = "ownid.callback")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebBridgePlugin.Message.Payload(pluginId = "FIDO", action = "get", callbackPath = "")
        }
    }

    @Test
    fun resultStringPrefersSuccessPayloadOverErrorPayload() {
        val result = WebBridgePlugin.Result(
            success = buildJsonObject { put("ok", true) },
            error = buildJsonObject { put("message", "ignored") }
        )

        assertThat(Json.parseToJsonElement(result.toResultString()).jsonObject["ok"]?.jsonPrimitive?.content).isEqualTo("true")
    }

    @Test
    fun resultStringNormalizesMissingErrorTypeToUnknown() {
        val result = WebBridgePlugin.Result(error = buildJsonObject { put("message", "bad request") })
        val error = Json.parseToJsonElement(result.toResultString()).jsonObject["error"]?.jsonObject

        assertThat(error?.get("type")?.jsonPrimitive?.content).isEqualTo("UNKNOWN")
        assertThat(error?.get("message")?.jsonPrimitive?.content).isEqualTo("bad request")
    }

    @Test
    fun resultStringKeepsExplicitErrorTypeAndDefaultsEmptyResultToUnknownError() {
        val explicit = WebBridgePlugin.Result(error = JsonObject(mapOf("type" to JsonPrimitive("ABORTED"))))
        val explicitError = Json.parseToJsonElement(explicit.toResultString()).jsonObject["error"]?.jsonObject
        val emptyError = Json.parseToJsonElement(WebBridgePlugin.Result().toResultString()).jsonObject["error"]?.jsonObject

        assertThat(explicitError?.get("type")?.jsonPrimitive?.content).isEqualTo("ABORTED")
        assertThat(emptyError?.get("type")?.jsonPrimitive?.content).isEqualTo("UNKNOWN")
    }

    @Test
    fun defaultInjectionDataUsesPluginKeyIdAndNonEmptyActionsOnly() {
        val pluginWithActions = TestPlugin(key = WebBridgePluginKey.from("metadata"), actions = listOf("get"))
        val pluginWithoutActions = TestPlugin(key = WebBridgePluginKey.from("silent"), actions = emptyList())

        assertThat(pluginWithActions.getInjectionData()).isEqualTo("METADATA" to listOf("get"))
        assertThat(pluginWithoutActions.getInjectionData()).isNull()
    }

    private class TestPlugin(
        override val key: WebBridgePluginKey,
        override val actions: List<String>
    ) : WebBridgePlugin {
        override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result = WebBridgePlugin.Result()
    }
}
