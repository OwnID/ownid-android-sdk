package com.ownid.sdk.capability.impl

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.RunDiagnostic
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.Storage
import com.ownid.sdk.di.DIContainerResolver
import com.ownid.sdk.di.getOrNull
import com.ownid.sdk.di.getOrThrow
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.logger.ServerLogger
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.runCatchingExceptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean

/** Process-owned [RunDiagnostic] for Android 11 and newer in the default application process. */
@InternalOwnIdAPI
@RequiresApi(Build.VERSION_CODES.R)
internal class RunDiagnosticImpl internal constructor(
    context: Context,
    localInfo: LocalInfo,
    processName: String,
    private val storage: Storage,
    private val serialization: Serialization,
    private val scope: CoroutineScope,
    private val ownIdLoggerProvider: () -> OwnIdLogger?
) : RunDiagnostic {

    @Serializable
    private class RunRecord(
        val correlationId: String,
        val processId: Int,
        val processName: String,
        val initializedAt: Long
    )

    private val applicationContext = context.applicationContext
    private val activityManager = applicationContext.getSystemService(ActivityManager::class.java)
    private val currentRun = RunRecord(
        correlationId = localInfo.correlationId,
        processId = Process.myPid(),
        processName = processName,
        initializedAt = System.currentTimeMillis()
    )
    private val claimed = AtomicBoolean(false)

    override fun reportPreviousRun(serverLogger: ServerLogger?) {
        if (!claimed.compareAndSet(false, true)) return

        scope.launch {
            rotateAndReport(serverLogger)
        }
    }

    private suspend fun rotateAndReport(serverLogger: ServerLogger?) {
        val previousRun = readPreviousRun()

        try {
            val encodedCurrentRun = serialization.encodeToString(RunRecord.serializer(), currentRun)
            storage.putString(STORAGE_KEY, encodedCurrentRun)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logLocalWarning("Failed to persist the current run: ${error.message}", error)
            return
        }

        if (previousRun == null || serverLogger == null) return

        serverLogger.log(
            level = OwnIdLogger.Level.Warn,
            className = EVENT_CLASS_NAME,
            message = "Previous run observed",
            cause = null,
            previousRun = RunDiagnostic.PreviousRun(
                correlationId = previousRun.correlationId,
                processId = previousRun.processId,
                exit = findMatchingExit(previousRun)
            )
        )
    }

    private suspend fun readPreviousRun(): RunRecord? {
        val encoded = storage.getString(STORAGE_KEY) ?: return null
        return try {
            serialization.decodeFromString(RunRecord.serializer(), encoded).also { run ->
                require(run.correlationId.isNotBlank()) { "Missing correlation ID" }
                require(run.processId > 0) { "Invalid process ID" }
                require(run.processName.isNotBlank()) { "Missing process name" }
                require(run.initializedAt >= 0) { "Invalid initialization timestamp" }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logLocalWarning("Ignoring malformed stored run: ${error.message}", error)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun findMatchingExit(previousRun: RunRecord): RunDiagnostic.PreviousRun.Exit? = try {
        activityManager
            ?.getHistoricalProcessExitReasons(applicationContext.packageName, previousRun.processId, 0)
            ?.asSequence()
            ?.filter { exit ->
                exit.pid == previousRun.processId &&
                        exit.processName == previousRun.processName &&
                        exit.timestamp in previousRun.initializedAt..currentRun.initializedAt
            }
            ?.minByOrNull { it.timestamp }
            ?.let { exit ->
                RunDiagnostic.PreviousRun.Exit(
                    processName = exit.processName,
                    reasonCode = exit.reason,
                    timestamp = exit.timestamp,
                    statusCode = exit.status,
                    importanceCode = exit.importance
                )
            }
    } catch (error: Exception) {
        logLocalWarning("Failed to query historical process exits: ${error.message}", error)
        null
    }

    private fun logLocalWarning(message: String, cause: Throwable?) {
        ownIdLoggerProvider()?.log(OwnIdLogger.Level.Warn, EVENT_CLASS_NAME, message, cause)
    }

    internal companion object {
        private const val STORAGE_FILE_NAME: String = "run_record"
        private const val STORAGE_KEY: String = "RUN_RECORD"
        private const val EVENT_CLASS_NAME: String = "RunDiagnostic"

        fun create(resolver: DIContainerResolver): RunDiagnostic? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

            var processJob: Job? = null
            return resolver.runCatchingExceptions {
                val context = getOrThrow<Context>().applicationContext
                val processName = Application.getProcessName()
                if (processName != context.applicationInfo.processName) return@runCatchingExceptions null

                val job = SupervisorJob().also { processJob = it }
                val scope = CoroutineScope(Dispatchers.IO + job + CoroutineName("OwnId.RunDiagnostic"))
                val storage = StorageImpl(context = context, fileName = STORAGE_FILE_NAME, scope = scope, logger = null)
                RunDiagnosticImpl(
                    context = context,
                    localInfo = getOrThrow<LocalInfo>(),
                    processName = processName,
                    storage = storage,
                    serialization = getOrThrow<Serialization>(),
                    scope = scope,
                    ownIdLoggerProvider = { resolver.getOrNull<OwnIdLogger>() }
                )
            }.onFailure { error ->
                processJob?.cancel()
                resolver.getOrNull<OwnIdLogger>()?.log(
                    OwnIdLogger.Level.Warn,
                    EVENT_CLASS_NAME,
                    "Failed to create run diagnostics: ${error.message}",
                    error
                )
            }.getOrNull()
        }
    }
}
