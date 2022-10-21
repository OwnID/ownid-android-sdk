package com.ownid.sdk.internal.events

import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
internal class MetricItem(
    private val category: Category, // required
    private val type: EventType, // required
    private val action: String?,
    private val context: String = "", // required
    private val metadata: JSONObject? = null, // Any JSON object: {test:123}
    private val errorMessage: String? = null,
    private val userAgent: String,
    private val version: String,  // required
    private val component: String = "AndroidSdk", // required
    private val sourceTimestamp: String = "${System.currentTimeMillis()}"  // required
) {

    internal enum class Category(internal val value: String) {
        Registration("registration"),
        Login("login")
    }

    internal enum class EventType(internal val value: String) {
        Click("click"),
        Track("track"),
        PageView("pageView"),
        Error("error"),
    }

    private companion object {
        private const val KEY_CONTEXT = "context"
        private const val KEY_COMPONENT = "component"
        private const val KEY_CATEGORY = "category"
        private const val KEY_TYPE = "type"
        private const val KEY_ACTION = "action"
        private const val KEY_METADATA = "metadata"
        private const val KEY_ERROR_MESSAGE = "errorMessage"
        private const val KEY_USER_AGENT = "userAgent"
        private const val KEY_VERSION = "version"
        private const val KEY_SOURCE_TIMESTAMP = "sourceTimestamp"
    }

    @Throws(JSONException::class)
    internal fun toJsonString(): String {
        return JSONObject()
            .put(KEY_CONTEXT, context.ifBlank { "no_context" })
            .put(KEY_COMPONENT, component)
            .put(KEY_CATEGORY, category.value)
            .put(KEY_TYPE, type.value)
            .apply {
                if (action != null) put(KEY_ACTION, action)
                if (metadata != null) put(KEY_METADATA, metadata)
                if (errorMessage != null) put(KEY_ERROR_MESSAGE, errorMessage)
            }
            .put(KEY_USER_AGENT, userAgent)
            .put(KEY_VERSION, version)
            .put(KEY_SOURCE_TIMESTAMP, sourceTimestamp)
            .toString()
    }
}