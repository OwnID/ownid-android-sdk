package com.ownid.sdk.internal

import android.util.Log
import androidx.annotation.RestrictTo
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdLogger
import com.ownid.sdk.internal.events.LogItem
import com.ownid.sdk.internal.events.Metadata
import com.ownid.sdk.internal.events.OwnIdInternalEventsService
import java.util.LinkedList

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object OwnIdInternalLogger {

    @JvmSynthetic
    public fun logD(clazz: Any, prefix: String, message: String?, cause: Throwable? = null, errorMessage: String? = null): Unit =
        privateLogger.log(Log.DEBUG, clazz, prefix, message, cause, errorMessage)

    @JvmSynthetic
    public fun logI(clazz: Any, prefix: String, message: String?, cause: Throwable? = null, errorMessage: String? = null): Unit =
        privateLogger.log(Log.INFO, clazz, prefix, message, cause, errorMessage)

    @JvmSynthetic
    public fun logW(clazz: Any, prefix: String, message: String?, cause: Throwable? = null, errorMessage: String? = null): Unit =
        privateLogger.log(Log.WARN, clazz, prefix, message, cause, errorMessage)

    @JvmSynthetic
    public fun logE(clazz: Any, prefix: String, message: String?, cause: Throwable? = null, errorMessage: String? = null): Unit =
        privateLogger.log(Log.ERROR, clazz, prefix, message, cause, errorMessage)

    @JvmSynthetic
    internal fun init(instanceName: InstanceName, eventsService: OwnIdInternalEventsService) {
        privateLogger.init(instanceName, eventsService)
    }

    @JvmSynthetic
    internal fun setLogLevel(logLevel: LogItem.Level) {
        logI(this, "setLogLevel", "Log level set to $logLevel")
        privateLogger.setLogLevel(logLevel)
    }

    @JvmSynthetic
    internal fun setFlowContext(context: String?) {
        privateLogger.setContext(context)
    }

    private val privateLogger = PrivateLogger()

    @InternalOwnIdAPI
    private class PrivateLogger {

        @InternalOwnIdAPI
        private class PostponedLog(
            private val level: LogItem.Level,
            private val className: String,
            private val message: String,
            private val context: String? = null,
            private val metadata: Metadata? = null,
            private val errorMessage: String? = null
        ) {
            internal fun send(eventsService: OwnIdInternalEventsService, logLevel: LogItem.Level) {
                if (logLevel.value <= level.value) eventsService.sendLog(level, className, message, context, metadata, errorMessage)
            }
        }

        private val logsQueue = LinkedList<PostponedLog>()
        private lateinit var instanceName: InstanceName
        private lateinit var eventsService: OwnIdInternalEventsService
        private var logLevel: LogItem.Level? = null
        private var context: String? = null

        @Synchronized
        internal fun init(instanceName: InstanceName, eventsService: OwnIdInternalEventsService) {
            this.instanceName = instanceName
            this.eventsService = eventsService
        }

        @Synchronized
        internal fun setLogLevel(logLevel: LogItem.Level) {
            this.logLevel = logLevel
            sendLogs()
        }

        @Synchronized
        internal fun setContext(context: String?) {
            this.context = context
        }

        @Synchronized
        fun log(priority: Int, clazz: Any, prefix: String, message: String?, cause: Throwable? = null, errorMessage: String? = null) {
            val instance = if (::instanceName.isInitialized) "$instanceName:" else ""
            val classTag = "$instance${clazz.javaClass.simpleName}#${clazz.hashCode()}@${Thread.currentThread().name}"

            OwnIdLogger.log(priority, "$classTag:$prefix", message ?: "", cause)

            val level: LogItem.Level = when {
                priority <= Log.DEBUG -> LogItem.Level.DEBUG
                priority == Log.INFO -> LogItem.Level.INFORMATION
                priority == Log.WARN -> LogItem.Level.WARNING
                priority == Log.ERROR -> LogItem.Level.ERROR
                else -> LogItem.Level.DEBUG
            }

            val metadata = Metadata(stackTrace = cause?.stackTraceToString())
            logsQueue.add(
                PostponedLog(
                    level, classTag, "$instance${clazz.javaClass.simpleName}.$prefix => $message", context, metadata, errorMessage
                )
            )

            sendLogs()
        }

        @Synchronized
        private fun sendLogs() {
            logLevel?.let {
                while (logsQueue.isNotEmpty()) {
                    logsQueue.pollFirst()?.send(eventsService, it)
                }
            }
        }
    }
}