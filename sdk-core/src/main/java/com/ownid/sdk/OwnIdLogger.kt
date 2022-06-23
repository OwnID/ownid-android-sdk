package com.ownid.sdk

import android.util.Log

/**
 * OwnIdLogger used for logging in OwnID SDK.
 * By default:
 * - logging is disabled
 * - tag set to `OwnID-SDK`
 * - logger set to [OwnIdLogger.DefaultLogger]
 *
 * Use [OwnIdLogger.set] to set custom tag and logger.
 * Use [OwnIdLogger.enabled] to enable/disable logging
 */
public object OwnIdLogger {
    /**
     * OwnID Logger interface. If you need custom logging logic, implement it and set to [OwnIdLogger]
     */
    public interface Logger {
        /**
         * Logs that contain the most detailed messages. These messages may contain sensitive application data.
         * These messages have no long-term value and should never be enabled in a production environment.
         */
        public fun v(className: String, message: String)

        /**
         * Logs that are used for interactive investigation during development.
         * These logs should primarily contain information useful for debugging and have no long-term value.
         */
        public fun d(className: String, message: String)

        /**
         * Logs that track the general flow of the application. These logs should have long-term value.
         */
        public fun i(className: String, message: String)

        /**
         * Logs that highlight an abnormal or unexpected event in the application flow,
         * but do not otherwise cause the application execution to stop.
         */
        public fun w(className: String, message: String)

        /**
         * Logs that highlight when the current flow of execution is stopped due to a failure.
         * These should indicate a failure in the current activity or an application-wide failure.
         */
        public fun e(className: String, message: String)

        /**
         * Logs that highlight when the current flow of execution is stopped due to a failure.
         * These should indicate a failure in the current activity or an application-wide failure.
         */
        public fun e(className: String, message: String, cause: Throwable?)
    }

    /**
     * Default OwnID Logger implementation. Just relays logs to [android.util.Log]
     */
    public object DefaultLogger : Logger {
        override fun v(className: String, message: String) {
            Log.v(tag, "$className |>$message")
        }

        override fun d(className: String, message: String) {
            Log.d(tag, "$className |>$message")
        }

        override fun i(className: String, message: String) {
            Log.i(tag, "$className |>$message")
        }

        override fun w(className: String, message: String) {
            Log.w(tag, "$className |>$message")
        }

        override fun e(className: String, message: String) {
            Log.e(tag, "$className |>$message")
        }

        override fun e(className: String, message: String, cause: Throwable?) {
            Log.e(tag, "$className |>$message", cause)
        }
    }

    /**
     * Allows enable/disable logging in SDK
     */
    @Volatile
    @JvmField
    public var enabled: Boolean = false

    @Volatile
    private var tag: String = "OwnID-SDK"

    @Volatile
    private var logger: Logger = DefaultLogger


    /**
     * Set OwnIdLogger tag and customLogger (optional)
     */
    @JvmStatic
    @JvmOverloads
    public fun set(tag: String, customLogger: Logger = DefaultLogger) {
        this.tag = tag
        logger = customLogger
    }

    @JvmStatic
    public fun v(className: String, message: String) {
        if (enabled) logger.v(className, message)
    }

    @JvmStatic
    public fun d(className: String, message: String) {
        if (enabled) logger.d(className, message)
    }

    @JvmStatic
    public fun i(className: String, message: String) {
        if (enabled) logger.i(className, message)
    }

    @JvmStatic
    public fun w(className: String, message: String) {
        if (enabled) logger.w(className, message)
    }

    @JvmStatic
    public fun e(className: String, message: String) {
        if (enabled) logger.e(className, message)
    }

    @JvmStatic
    public fun e(className: String, message: String, cause: Throwable?) {
        if (enabled) logger.e(className, message, cause)
    }
}