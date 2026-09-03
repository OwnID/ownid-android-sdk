package com.ownid.sdk.logger

import androidx.annotation.RestrictTo
import com.ownid.sdk.model.InternalOwnIdAPI

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
/**
 * Internal routing point for local SDK logs and instance-scoped server diagnostics.
 *
 * Local logging and server diagnostics are independent sinks. Each log call is offered to the currently configured
 * [OwnIdLogger] and to this router's [ServerLogger], when present. The local logger applies the app-configured threshold;
 * the server logger applies the instance diagnostics threshold from remote AppConfig before sending.
 *
 * A missing message is normalized to an empty string. Callers may pass an optional [Throwable] when the diagnostic is
 * tied to a failure. Logging must remain a best-effort side effect: callers do not observe delivery success, server
 * acceptance, or local sink failures through this API.
 */
public class OwnIdLogRouter internal constructor(
    private val ownIdLoggerProvider: () -> OwnIdLogger?,
    private val serverLoggerProvider: () -> ServerLogger?,
) {
    /** Logs a verbose message to local and server loggers. */
    public fun logV(clazz: Any, prefix: String, message: String?, cause: Throwable? = null) {
        log(OwnIdLogger.Level.Verbose, clazz, prefix, message, cause)
    }

    /** Logs a debug message to local and server loggers. */
    public fun logD(clazz: Any, prefix: String, message: String?, cause: Throwable? = null) {
        log(OwnIdLogger.Level.Debug, clazz, prefix, message, cause)
    }

    /** Logs an info message to local and server loggers. */
    public fun logI(clazz: Any, prefix: String, message: String?, cause: Throwable? = null) {
        log(OwnIdLogger.Level.Info, clazz, prefix, message, cause)
    }

    /** Logs a warning message to local and server loggers. */
    public fun logW(clazz: Any, prefix: String, message: String?, cause: Throwable? = null) {
        log(OwnIdLogger.Level.Warn, clazz, prefix, message, cause)
    }

    /** Logs an error message to local and server loggers. */
    public fun logE(clazz: Any, prefix: String, message: String?, cause: Throwable? = null) {
        log(OwnIdLogger.Level.Error, clazz, prefix, message, cause)
    }

    private fun log(level: OwnIdLogger.Level, clazz: Any, prefix: String, message: String?, cause: Throwable?) {
        val classNameLocal = "${clazz::class.java.simpleName}#${clazz.hashCode()}@${Thread.currentThread().name}:$prefix"
        ownIdLoggerProvider.invoke()?.log(level, classNameLocal, message.orEmpty(), cause)
        val classNameServer = "${clazz::class.java.simpleName}:$prefix"
        serverLoggerProvider.invoke()?.log(level, classNameServer, message.orEmpty(), cause)
    }
}
