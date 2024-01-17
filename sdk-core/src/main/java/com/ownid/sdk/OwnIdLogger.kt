package com.ownid.sdk

import android.util.Log

/**
 * OwnIdLogger used for logging in OwnID SDK.
 * By default:
 * - logging is disabled
 * - tag set to `OwnID-SDK`
 * - logger set to [OwnIdLogger.DefaultLogger]
 *
 * Use [OwnIdLogger.set] method to set custom tag and logger.
 * Use [OwnIdLogger.enabled] property to enable/disable logging
 */
public object OwnIdLogger {
    /**
     * OwnID Logger interface. If you need custom logging logic, implement it and set to [OwnIdLogger]
     */
    public interface Logger {
        /**
         * Log [message] with [priority] value from [android.util.Log] that happened in Class [className] and optional [cause] error.
         */
        public fun log(priority: Int, className: String, message: String, cause: Throwable?)
    }

    /**
     * Default OwnID Logger implementation. Just relays logs to [android.util.Log]
     */
    public object DefaultLogger : Logger {
        override fun log(priority: Int, className: String, message: String, cause: Throwable?) {
            val error = cause?.let { "\n" + it.stackTraceToString() } ?: ""
            Log.println(priority, tag, "$className |> $message$error")
        }
    }

    /**
     * Allows enable/disable logging in SDK
     */
    @Volatile
    @JvmField
    public var enabled: Boolean = true

    @Volatile
    private var tag: String = "OwnID-SDK"

    @Volatile
    private var logger: Logger = DefaultLogger

    /**
     * Set OwnIdLogger tag and customLogger (optional)
     */
    @JvmStatic
    @JvmOverloads
    public fun set(customTag: String, customLogger: Logger = DefaultLogger) {
        tag = customTag
        logger = customLogger
    }

    @JvmStatic
    @JvmOverloads
    @InternalOwnIdAPI
    public fun log(priority: Int, className: String, message: String, cause: Throwable? = null) {
        if (enabled) logger.log(priority, className, message, cause)
    }
}