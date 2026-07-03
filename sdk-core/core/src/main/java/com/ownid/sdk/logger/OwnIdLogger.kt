package com.ownid.sdk.logger

import android.util.Log
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdConfigurationDsl
import com.ownid.sdk.logger.OwnIdLogger.Companion.DEFAULT_TAG
import com.ownid.sdk.logger.OwnIdLogger.Companion.Default

/**
 * Local logging abstraction used by the OwnID SDK.
 *
 * Configure it with [OwnId.logger]. The most recent logger replaces the previous SDK-wide logger and is used for local
 * SDK log events. Instance-scoped server diagnostics are a separate sink and are filtered by remote AppConfig.
 *
 * If you never configure a logger, local SDK logging is silent. The SDK uses [Default] only as a temporary fallback for
 * early configuration failures before a configured SDK instance can provide local logging. The active [Level] threshold
 * determines which local messages are emitted.
 *
 * @see OwnId.logger
 */
public interface OwnIdLogger {

    /**
     * Severity level of a log message.
     *
     * @property priority The Android log priority associated with this level.
     */
    public enum class Level(public val priority: Int) {
        Verbose(2), Debug(3), Info(4), Warn(5), Error(6), Assert(7), Off(Int.MAX_VALUE);

        /** Returns `true` when messages at [other] should be emitted under this threshold. */
        public fun isEnabled(other: Level): Boolean = this != Off && other != Off && other.priority >= priority
    }

    /** Logging threshold; messages below this level are ignored. */
    public val level: Level

    /** Tag used for log messages. */
    public val tag: String

    /**
     * Logs a message.
     *
     * @param level Severity of the log message.
     * @param className Name of the class that produced the log.
     * @param message Message to log.
     * @param cause Optional error associated with the log event.
     */
    public fun log(level: Level, className: String, message: String, cause: Throwable? = null)

    /** Returns true when messages at [level] would be emitted. */
    public fun isEnabled(level: Level): Boolean = this.level.isEnabled(level)

    public companion object {

        /** Default log tag used by the OwnID SDK. */
        public const val DEFAULT_TAG: String = "OwnID-SDK"

        internal fun androidLogger(level: Level, tag: String): OwnIdLogger = object : OwnIdLogger {
            override val level: Level = level
            override val tag: String = tag

            override fun log(level: Level, className: String, message: String, cause: Throwable?) {
                if (!isEnabled(level)) return
                val logMessage = when (cause) {
                    null -> "$className |> $message"
                    else -> "$className |> $message\n${cause.stackTraceToString()}"
                }
                Log.println(level.priority, tag, logMessage)
            }
        }

        /**
         * Default fallback logger.
         *
         * Relays messages to Android Log with [Level.Warn] and [DEFAULT_TAG]. The SDK uses this logger only for early
         * configuration failures before a configured SDK instance can provide local logging. It is not installed
         * globally unless you register it yourself.
         */
        public val Default: OwnIdLogger = androidLogger(Level.Warn, DEFAULT_TAG)
    }
}

/**
 * Builder for configuring an SDK-wide [OwnIdLogger] via [OwnId.logger].
 *
 * Defaults: [level] is [OwnIdLogger.Level.Off] and [tag] is [DEFAULT_TAG], so the built logger emits no local messages
 * until you lower the threshold. Without a custom [log] sink, enabled messages are written to Android Log. With a
 * custom sink, enabled messages are forwarded to your handler; if the handler throws, the failure is reported to
 * Android Log with error priority and is not rethrown.
 *
 * Configure logging before SDK initialization when you need initialization logs or local HTTP logs. HTTP logs are local
 * only and are enabled when the logger accepts [OwnIdLogger.Level.Debug] messages.
 *
 * @see OwnId.logger
 */
@OwnIdConfigurationDsl
public class OwnIdLoggerBuilder internal constructor() {
    /** Logging threshold. Messages below this level are ignored. Defaults to [OwnIdLogger.Level.Off]. */
    public var level: OwnIdLogger.Level = OwnIdLogger.Level.Off

    /** Log tag. Defaults to [OwnIdLogger.DEFAULT_TAG]. */
    public var tag: String = DEFAULT_TAG

    private var customLogAction: ((OwnIdLogger.Level, String, String, Throwable?) -> Unit)? = null

    /**
     * Sets a custom sink for log events.
     *
     * The sink is invoked only for messages accepted by [level]. Exceptions thrown by the sink are caught and reported
     * to Android Log with error priority.
     *
     * @param block Callback invoked for each log event: (level, className, message, cause).
     */
    public fun log(block: (OwnIdLogger.Level, String, String, Throwable?) -> Unit) {
        customLogAction = block
    }

    /** Builds an [OwnIdLogger] using the configured settings. */
    internal fun build(): OwnIdLogger {
        val level = this.level
        val tag = this.tag

        val action = customLogAction ?: return OwnIdLogger.androidLogger(level, tag)

        return object : OwnIdLogger {
            override val level: OwnIdLogger.Level = level
            override val tag: String = tag

            override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
                if (!isEnabled(level)) return
                try {
                    action.invoke(level, className, message, cause)
                } catch (e: Exception) {
                    Log.println(Log.ERROR, tag, "Logging failed: ${e.stackTraceToString()}")
                }
            }
        }
    }
}
