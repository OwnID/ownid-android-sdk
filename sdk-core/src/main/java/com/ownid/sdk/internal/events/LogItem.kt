package com.ownid.sdk.internal.events

import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONException
import org.json.JSONObject

/**
 * Represent single log record for OwnID Server
 */
@InternalOwnIdAPI
internal class LogItem(
    private val context: String = "", // required
    private val component: String = "AndroidSdk", // required
    private val requestPath: String = "", // real server address
    private val level: Level, // required
    private val className: String,
    private val message: String,
    private val exception: String? = null,
    private val metadata: JSONObject? = null, // Any JSON object: {test:123}
    private val userAgent: String,
    private val version: String,  // required
    private val sourceTimestamp: String = "${System.currentTimeMillis()}"  // required
) {
    @InternalOwnIdAPI
    internal enum class Level(internal val value: Int) {

        /**
         * Logs that contain the most detailed messages. These messages may contain sensitive application data.
         * These messages have no long-term value and should never be enabled in a production environment.
         */
        VERBOSE(0),

        /**
         * Logs that are used for interactive investigation during development.
         * These logs should primarily contain information useful for debugging and have no long-term value.
         */
        DEBUG(1),

        /**
         * Logs that track the general flow of the application. These logs should have long-term value.
         */
        INFO(2),

        /**
         * Logs that highlight an abnormal or unexpected event in the application flow,
         * but do not otherwise cause the application execution to stop.
         */
        WARN(3),

        /**
         * Logs that highlight when the current flow of execution is stopped due to a failure.
         * These should indicate a failure in the current activity, not an application-wide failure.
         */
        ERROR(4),

        /**
         * Logs that describe an unrecoverable application or system crash,
         * or a catastrophic failure that requires immediate attention.
         */
        CRITICAL(5)
    }

    private companion object {
        private const val KEY_CONTEXT = "context"
        private const val KEY_COMPONENT = "component"
        private const val KEY_REQUEST_PATH = "requestPath"
        private const val KEY_LEVEL = "level"
        private const val KEY_CODE_INITIATOR = "codeInitiator" // className
        private const val KEY_MESSAGE = "message"
        private const val KEY_EXCEPTION = "exception"
        private const val KEY_METADATA = "metadata"
        private const val KEY_USER_AGENT = "userAgent"
        private const val KEY_VERSION = "version"
        private const val KEY_SOURCE_TIMESTAMP = "sourceTimestamp"
    }

    @Throws(JSONException::class)
    internal fun toJsonString(): String {
        return JSONObject()
            .put(KEY_CONTEXT, context.ifBlank { "no_context" })
            .put(KEY_COMPONENT, component)
            .apply { if (requestPath.isBlank()) put(KEY_REQUEST_PATH, requestPath) }
            .put(KEY_LEVEL, level.value)
            .put(KEY_CODE_INITIATOR, className)
            .put(KEY_MESSAGE, message)
            .apply { if (exception.isNullOrBlank().not()) put(KEY_EXCEPTION, exception) }
            .apply { if (metadata != null) put(KEY_METADATA, metadata) }
            .put(KEY_USER_AGENT, userAgent)
            .put(KEY_VERSION, version)
            .put(KEY_SOURCE_TIMESTAMP, sourceTimestamp)
            .toString()
    }

    internal fun thisLevelOrAbove(level: Level): Boolean = this.level.value >= level.value
}