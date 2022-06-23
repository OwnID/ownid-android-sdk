package com.ownid.sdk.internal.events

import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter

@InternalOwnIdAPI
public class LogService(
    private val configuration: Configuration,
    private val correlationId: String,
    private val networkService: EventsNetworkService
) {
    private companion object {
        private const val KEY_SCOPE_CORRELATION_ID = "correlationId"
        private const val KEY_SCOPE_STACK_TRACE = "stackTrace"
    }

    internal fun v(className: String, message: String, context: String = "") {
        networkService.submitLogRunnable(
            LogItem(
                context = context,
                level = LogItem.Level.VERBOSE,
                className = className,
                message = message,
                metadata = JSONObject().put(KEY_SCOPE_CORRELATION_ID, correlationId),
                userAgent = configuration.userAgent,
                version = configuration.version
            )
        )
    }

    internal fun d(className: String, message: String, context: String = "") {
        networkService.submitLogRunnable(
            LogItem(
                context = context,
                level = LogItem.Level.DEBUG,
                className = className,
                message = message,
                metadata = JSONObject().put(KEY_SCOPE_CORRELATION_ID, correlationId),
                userAgent = configuration.userAgent,
                version = configuration.version
            )
        )
    }

    internal fun i(className: String, message: String, context: String = "") {
        networkService.submitLogRunnable(
            LogItem(
                context = context,
                level = LogItem.Level.INFO,
                className = className,
                message = message,
                metadata = JSONObject().put(KEY_SCOPE_CORRELATION_ID, correlationId),
                userAgent = configuration.userAgent,
                version = configuration.version
            )
        )
    }

    internal fun w(className: String, message: String, context: String = "") {
        networkService.submitLogRunnable(
            LogItem(
                context = context,
                level = LogItem.Level.WARN,
                className = className,
                message = message,
                metadata = JSONObject().put(KEY_SCOPE_CORRELATION_ID, correlationId),
                userAgent = configuration.userAgent,
                version = configuration.version
            )
        )
    }

    internal fun e(className: String, message: String, cause: Throwable, context: String = "") {
        val stackTrace = StringWriter().also { cause.printStackTrace(PrintWriter(it)) }.toString()
        networkService.submitLogRunnable(
            LogItem(
                context = context,
                level = LogItem.Level.ERROR,
                className = className,
                message = message,
                exception = cause.toString(),
                metadata = JSONObject()
                    .put(KEY_SCOPE_CORRELATION_ID, correlationId)
                    .put(KEY_SCOPE_STACK_TRACE, stackTrace),
                userAgent = configuration.userAgent,
                version = configuration.version
            )
        )
    }
}