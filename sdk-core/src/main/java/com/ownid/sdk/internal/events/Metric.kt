package com.ownid.sdk.internal.events

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Metric(
    private val applicationOrigin: String,
    private val category: Category, // required
    private val type: EventType, // required
    private val action: String?,
    private val context: String?,
    private val metadata: Metadata, // Any JSON object
    private val loginId: String? = null, // as Base64-URLSafe-NoPadding(SHA256(<LoginID String>))
    private val source: String? = null,
    private val errorMessage: String? = null,
    private val errorCode: String? = null,
    private val userAgent: String,
    private val version: String, // required
    private val component: String = "AndroidSdk", // required
    private val sourceTimestamp: String = "${System.currentTimeMillis()}" // required
) {

    @InternalOwnIdAPI
    public enum class Category(internal val value: String) {
        Registration("registration"),
        Login("login")
    }

    @InternalOwnIdAPI
    public enum class EventType(internal val value: String) {
        Click("click"),
        Track("track"),
        PageView("pageView"),
        Error("error"),
    }

    @Throws(OwnIdException::class)
    internal fun toJsonString(): String = runCatching {
        JSONObject()
            .put("applicationOrigin", applicationOrigin)
            .put("category", category.value)
            .apply { if (context != null) put("context", context) }
            .put("type", type.value)
            .apply { if (action != null) put("action", action) }
            .put("metadata", metadata.toJSONObject())
            .apply { if (loginId != null) put("loginId", loginId) }
            .apply { if (source != null) put("source", source) }
            .apply { if (errorMessage != null) put("errorMessage", errorMessage) }
            .apply { if (errorCode != null) put("errorCode", errorCode) }
            .put("userAgent", userAgent)
            .put("version", version)
            .put("component", component)
            .put("sourceTimestamp", sourceTimestamp)
            .toString()
    }.getOrElse {
        throw OwnIdException("Metric.toJsonString", it)
    }
}