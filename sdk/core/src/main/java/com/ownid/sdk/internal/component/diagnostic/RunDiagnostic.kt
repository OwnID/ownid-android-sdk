package com.ownid.sdk.internal.component.diagnostic

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Rotates and reports the previous SDK-initialized run once per application process. */
@OptIn(InternalOwnIdAPI::class)
internal object RunDiagnostic {

    internal class PreviousRun(
        private val correlationId: String,
        private val processId: Int,
        private val exit: Exit?
    ) {
        internal class Exit(
            private val processName: String,
            private val reasonCode: Int,
            private val timestamp: Long,
            private val statusCode: Int,
            private val importanceCode: Int
        ) {
            internal fun toJSONObject(): JSONObject = JSONObject()
                .put("processName", processName)
                .put("reasonCode", reasonCode)
                .put("timestamp", timestamp)
                .put("statusCode", statusCode)
                .put("importanceCode", importanceCode)
        }

        internal fun toJSONObject(): JSONObject = JSONObject()
            .put("correlationId", correlationId)
            .put("processId", processId)
            .apply { if (exit != null) put("exit", exit.toJSONObject()) }
    }

    private class RunRecord(
        val correlationId: String,
        val processId: Int,
        val processName: String,
        val initializedAt: Long
    ) {
        fun toJsonString(): String = JSONObject()
            .put("correlationId", correlationId)
            .put("processId", processId)
            .put("processName", processName)
            .put("initializedAt", initializedAt)
            .toString()
    }

    private const val STORAGE_KEY_NAME: String = "com.ownid.sdk.storage.RUN_RECORD"
    private const val STORAGE_RELATIVE_PATH: String = "ownid/run_record.preferences_pb"
    private const val LOG_TAG: String = "RunDiagnostic"

    private val storageKey: Preferences.Key<String> = stringPreferencesKey(STORAGE_KEY_NAME)
    private val claimed = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("OwnId.RunDiagnostic"))
    private val dataStoreLock = Any()

    @Volatile
    private var dataStore: DataStore<Preferences>? = null

    internal fun reportPreviousRun(context: Context, ownIdCore: OwnIdCoreImpl) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        reportPreviousRunApi30(context, ownIdCore)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reportPreviousRunApi30(context: Context, ownIdCore: OwnIdCoreImpl) {
        try {
            val applicationContext = context.applicationContext
            val processName = Application.getProcessName()
            if (processName != applicationContext.applicationInfo.processName) return
            if (!claimed.compareAndSet(false, true)) return

            val currentRun = RunRecord(
                correlationId = ownIdCore.correlationId,
                processId = Process.myPid(),
                processName = processName,
                initializedAt = System.currentTimeMillis()
            )
            val processDataStore = getOrCreateDataStore(applicationContext)

            scope.launch {
                rotateAndReport(applicationContext, processDataStore, currentRun, ownIdCore)
            }
        } catch (error: Exception) {
            logLocalWarning("Failed to start run diagnostics: ${error.message}", error)
        }
    }

    private fun getOrCreateDataStore(context: Context): DataStore<Preferences> =
        dataStore ?: synchronized(dataStoreLock) {
            dataStore ?: PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { error ->
                    logLocalWarning("DataStore corrupted: ${error.message}", error)
                    emptyPreferences()
                },
                scope = scope,
                produceFile = { File(context.noBackupFilesDir, STORAGE_RELATIVE_PATH) }
            ).also { dataStore = it }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun rotateAndReport(
        context: Context,
        dataStore: DataStore<Preferences>,
        currentRun: RunRecord,
        ownIdCore: OwnIdCoreImpl
    ) {
        val previousRun = readPreviousRun(dataStore)

        try {
            withContext(NonCancellable) {
                dataStore.edit { preferences -> preferences[storageKey] = currentRun.toJsonString() }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logLocalWarning("Failed to persist the current run: ${error.message}", error)
            return
        }

        if (previousRun == null) return

        ownIdCore.eventsService.sendPreviousRunMetric(
            PreviousRun(
                correlationId = previousRun.correlationId,
                processId = previousRun.processId,
                exit = findMatchingExit(context, previousRun, currentRun)
            )
        )
    }

    private suspend fun readPreviousRun(dataStore: DataStore<Preferences>): RunRecord? {
        val encoded = try {
            dataStore.data.first()[storageKey]
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logLocalWarning("Failed to read the previous run: ${error.message}", error)
            null
        } ?: return null

        return try {
            val json = JSONObject(encoded)
            RunRecord(
                correlationId = json.requireString("correlationId"),
                processId = json.requireInt("processId"),
                processName = json.requireString("processName"),
                initializedAt = json.requireLong("initializedAt")
            ).also { run ->
                require(run.correlationId.isNotBlank()) { "Missing correlation ID" }
                require(run.processId > 0) { "Invalid process ID" }
                require(run.processName.isNotBlank()) { "Missing process name" }
                require(run.initializedAt >= 0) { "Invalid initialization timestamp" }
            }
        } catch (error: Exception) {
            logLocalWarning("Ignoring malformed stored run: ${error.message}", error)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun findMatchingExit(
        context: Context,
        previousRun: RunRecord,
        currentRun: RunRecord
    ): PreviousRun.Exit? = try {
        context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(context.packageName, previousRun.processId, 0)
            ?.asSequence()
            ?.filter { exit ->
                exit.pid == previousRun.processId &&
                        exit.processName == previousRun.processName &&
                        exit.timestamp in previousRun.initializedAt..currentRun.initializedAt
            }
            ?.minByOrNull { exit -> exit.timestamp }
            ?.let { exit ->
                PreviousRun.Exit(
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

    private fun JSONObject.requireString(name: String): String =
        (opt(name) as? String) ?: throw IllegalArgumentException("Missing or invalid $name")

    private fun JSONObject.requireInt(name: String): Int {
        val number = opt(name) as? Number ?: throw IllegalArgumentException("Missing or invalid $name")
        val long = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == long.toDouble()) { "Invalid $name" }
        require(long in Int.MIN_VALUE..Int.MAX_VALUE) { "Invalid $name" }
        return long.toInt()
    }

    private fun JSONObject.requireLong(name: String): Long {
        val number = opt(name) as? Number ?: throw IllegalArgumentException("Missing or invalid $name")
        val long = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == long.toDouble()) { "Invalid $name" }
        return long
    }

    private fun logLocalWarning(message: String, cause: Throwable?) {
        OwnIdLogger.log(Log.WARN, LOG_TAG, message, cause)
    }
}
