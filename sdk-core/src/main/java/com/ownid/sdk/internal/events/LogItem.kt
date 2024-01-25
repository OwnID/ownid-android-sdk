package com.ownid.sdk.internal.events

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class LogItem(
    private val level: Level, // required
    private val context: String?, // required
    private val className: String,
    private val message: String,
    private val userAgent: String,
    private val version: String,  // required
    private val metadata: Metadata,
    private val errorMessage: String? = null,
    private val component: String = "AndroidSdk", // required
    private val sourceTimestamp: String = "${System.currentTimeMillis()}", // required
    private val requestPath: String = "", // real server address
    private val exception: String? = null,
) {
    @InternalOwnIdAPI
    internal enum class Level(internal val value: Int) {
        DEBUG(1), INFORMATION(2), WARNING(3), ERROR(4);

        internal companion object {
            internal fun fromString(value: String): Level =
                Level.values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WARNING
        }
    }

    @Throws(OwnIdException::class)
    internal fun toJsonString(): String = runCatching {
        JSONObject()
            .apply { if (context != null) put("context", context) }
            .put("component", component)
            .apply { if (requestPath.isNotBlank()) put("requestPath", requestPath) }
            .put("level", level.value)
            .put("codeInitiator", className)
            .put("message", message)
            .apply { if (exception.isNullOrBlank().not()) put("exception", exception) }
            .apply { if (errorMessage != null) put("errorMessage", errorMessage) }
            .put("metadata", metadata.toJSONObject())
            .put("userAgent", userAgent)
            .put("version", version)
            .put("sourceTimestamp", sourceTimestamp)
            .toString()
    }.getOrElse {
        throw OwnIdException("LogItem.toJsonString", it)
    }
}