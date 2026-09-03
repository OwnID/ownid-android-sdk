package com.ownid.sdk.capability.impl

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Storage
import com.ownid.sdk.di.impl.DIContainerImpl
import com.ownid.sdk.di.register
import com.ownid.sdk.logger.ImmediateAppConfigProvider
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.logger.RecordingServerNetwork
import com.ownid.sdk.logger.ServerLogger
import com.ownid.sdk.logger.diagnosticsAppConfig
import com.ownid.sdk.logger.diagnosticsConfiguration
import com.ownid.sdk.logger.diagnosticsLocalInfo
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.InternalOwnIdAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okio.Buffer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowProcess
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: DIAG-RUNTIME-060, DIAG-RUNTIME-070
internal class RunDiagnosticImplRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUpProcessIdentity() {
        app.applicationInfo.processName = app.packageName
        ShadowApplication.setProcessName(app.packageName)
    }

    @Test
    fun validPreviousRunRotatesCurrentIdentityBeforeOneWarningAttempt(): Unit = runTest {
        val storage = RecordingStorage(initialValue = previousRunRecord())
        var persistedBeforeReport = false
        val network = RecordingServerNetwork {
            persistedBeforeReport = storage.persistedValues.size == 1
            Network.Response.Success(
                url = it.url,
                code = 204,
                headers = emptyMap(),
                body = ""
            )
        }
        val instanceJob = Job()
        val serverLogger = serverLogger(network, StandardTestDispatcher(testScheduler), instanceJob)
        val diagnostic = runDiagnostic(storage = storage, scope = this)

        try {
            runCurrent()
            diagnostic.reportPreviousRun(serverLogger)
            runCurrent()

            assertThat(persistedBeforeReport).isTrue()
            assertThat(storage.readCount).isEqualTo(1)
            assertThat(storage.persistedValues).hasSize(1)
            val currentRun = storage.persistedValues.single().asJson()
            assertThat(currentRun.getValue("correlationId").jsonPrimitive.content).isEqualTo("correlation-1")
            assertThat(currentRun.getValue("processId").jsonPrimitive.content).isEqualTo("9001")
            assertThat(currentRun.getValue("processName").jsonPrimitive.content).isEqualTo(app.applicationInfo.processName)
            assertThat(currentRun.getValue("initializedAt").jsonPrimitive.content.toLong()).isAtLeast(PREVIOUS_INITIALIZED_AT)

            assertThat(network.requests).hasSize(1)
            val previousRun = network.requests.single().jsonBody()
                .getValue("metadata").jsonObject
                .getValue("previousRun").jsonObject
            assertThat(previousRun.getValue("correlationId").jsonPrimitive.content).isEqualTo("previous-correlation")
            assertThat(previousRun.getValue("processId").jsonPrimitive.content).isEqualTo("4242")
            assertThat(previousRun).doesNotContainKey("exit")

            diagnostic.reportPreviousRun(serverLogger)
            runCurrent()

            assertThat(storage.readCount).isEqualTo(1)
            assertThat(storage.persistedValues).hasSize(1)
            assertThat(network.requests).hasSize(1)
        } finally {
            instanceJob.cancel()
            runCurrent()
        }
    }

    @Test
    fun nullFirstLoggerRotatesOnceAndDoesNotTransferWarningOwnership(): Unit = runTest {
        val storage = RecordingStorage(initialValue = previousRunRecord())
        val network = RecordingServerNetwork()
        val instanceJob = Job()
        val serverLogger = serverLogger(network, StandardTestDispatcher(testScheduler), instanceJob)
        val diagnostic = runDiagnostic(storage = storage, scope = this)

        try {
            runCurrent()
            diagnostic.reportPreviousRun(null)
            runCurrent()
            diagnostic.reportPreviousRun(serverLogger)
            runCurrent()

            assertThat(storage.readCount).isEqualTo(1)
            assertThat(storage.persistedValues).hasSize(1)
            assertThat(network.requests).isEmpty()
        } finally {
            instanceJob.cancel()
            runCurrent()
        }
    }

    @Test
    fun malformedStoredRecordRotatesOnceWithoutPartialOrTransferredEvent(): Unit = runTest {
        val storage = RecordingStorage(initialValue = "{not-json")
        val network = RecordingServerNetwork()
        val localLogs = mutableListOf<LocalLog>()
        val instanceJob = Job()
        val serverLogger = serverLogger(network, StandardTestDispatcher(testScheduler), instanceJob)
        val diagnostic = runDiagnostic(storage = storage, scope = this, localLogger = recordingLogger(localLogs))

        try {
            runCurrent()
            diagnostic.reportPreviousRun(serverLogger)
            runCurrent()
            diagnostic.reportPreviousRun(serverLogger)
            runCurrent()

            assertThat(storage.readCount).isEqualTo(1)
            assertThat(storage.persistedValues).hasSize(1)
            assertThat(network.requests).isEmpty()
            assertThat(localLogs.any { it.message.startsWith("Ignoring malformed stored run:") }).isTrue()
        } finally {
            instanceJob.cancel()
            runCurrent()
        }
    }

    @Test
    fun persistenceFailureProducesNoPartialOrTransferredEvent(): Unit = runTest {
        val persistenceFailure = IOException("run record write failed")
        val storage = RecordingStorage(initialValue = previousRunRecord(), putFailure = persistenceFailure)
        val network = RecordingServerNetwork()
        val localLogs = mutableListOf<LocalLog>()
        val instanceJob = Job()
        val serverLogger = serverLogger(network, StandardTestDispatcher(testScheduler), instanceJob)
        val diagnostic = runDiagnostic(storage = storage, scope = this, localLogger = recordingLogger(localLogs))

        try {
            runCurrent()
            diagnostic.reportPreviousRun(serverLogger)
            runCurrent()
            diagnostic.reportPreviousRun(serverLogger)
            runCurrent()

            assertThat(storage.readCount).isEqualTo(1)
            assertThat(storage.putAttempts).isEqualTo(1)
            assertThat(storage.persistedValues).isEmpty()
            assertThat(network.requests).isEmpty()
            assertThat(localLogs).containsExactly(
                LocalLog(
                    level = OwnIdLogger.Level.Warn,
                    className = "RunDiagnostic",
                    message = "Failed to persist the current run: run record write failed",
                    cause = persistenceFailure
                )
            )
        } finally {
            instanceJob.cancel()
            runCurrent()
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun factoryIsAbsentBelowApi30(): Unit {
        assertThat(RunDiagnosticImpl.create(DIContainerImpl(scopeName = "run-diagnostic-api-gate"))).isNull()
    }

    @Test
    fun factoryIsAbsentOutsideDefaultApplicationProcess(): Unit {
        val defaultProcessName = app.applicationInfo.processName
        val resolver = DIContainerImpl(scopeName = "run-diagnostic-process-gate").apply {
            register<Context>(app)
        }
        ShadowApplication.setProcessName("$defaultProcessName:secondary")

        try {
            assertThat(RunDiagnosticImpl.create(resolver)).isNull()
        } finally {
            ShadowApplication.setProcessName(defaultProcessName)
        }
    }

    @Test
    fun exitEnrichmentUsesExactIdentityAndTimeFiltersAndEarliestMatch(): Unit = runTest {
        val processName = app.applicationInfo.processName
        val activityManager = app.getSystemService(ActivityManager::class.java)
        val shadowActivityManager = shadowOf(activityManager)
        shadowActivityManager.addApplicationExitInfo(exitInfo(pid = 4242, processName = processName, timestamp = 900L))
        shadowActivityManager.addApplicationExitInfo(exitInfo(pid = 4242, processName = "$processName:other", timestamp = 1_050L))
        shadowActivityManager.addApplicationExitInfo(exitInfo(pid = 5252, processName = processName, timestamp = 1_060L))
        shadowActivityManager.addApplicationExitInfo(exitInfo(pid = 4242, processName = processName, timestamp = Long.MAX_VALUE))
        shadowActivityManager.addApplicationExitInfo(
            exitInfo(
                pid = 4242,
                processName = processName,
                timestamp = 1_300L,
                reason = ApplicationExitInfo.REASON_CRASH_NATIVE,
                status = 11,
                importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
            )
        )
        shadowActivityManager.addApplicationExitInfo(
            exitInfo(
                pid = 4242,
                processName = processName,
                timestamp = 1_100L,
                reason = ApplicationExitInfo.REASON_CRASH,
                status = 7,
                importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                description = "must not leave the allowlist"
            )
        )
        val storage = RecordingStorage(initialValue = previousRunRecord(processName = processName))
        val network = RecordingServerNetwork()
        val instanceJob = Job()
        val serverLogger = serverLogger(network, StandardTestDispatcher(testScheduler), instanceJob)
        val diagnostic = runDiagnostic(storage = storage, scope = this, processName = processName)

        try {
            runCurrent()
            diagnostic.reportPreviousRun(serverLogger)
            runCurrent()

            val exit = network.requests.single().jsonBody()
                .getValue("metadata").jsonObject
                .getValue("previousRun").jsonObject
                .getValue("exit").jsonObject
            assertThat(exit.keys).containsExactly("processName", "reasonCode", "timestamp", "statusCode", "importanceCode")
            assertThat(exit.getValue("processName").jsonPrimitive.content).isEqualTo(processName)
            assertThat(exit.getValue("reasonCode").jsonPrimitive.content).isEqualTo("${ApplicationExitInfo.REASON_CRASH}")
            assertThat(exit.getValue("timestamp").jsonPrimitive.content).isEqualTo("1100")
            assertThat(exit.getValue("statusCode").jsonPrimitive.content).isEqualTo("7")
            assertThat(exit.getValue("importanceCode").jsonPrimitive.content)
                .isEqualTo("${ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND}")
        } finally {
            instanceJob.cancel()
            runCurrent()
        }
    }

    private fun runDiagnostic(
        storage: Storage,
        scope: CoroutineScope,
        processName: String = app.applicationInfo.processName,
        localLogger: OwnIdLogger? = null
    ): RunDiagnosticImpl {
        ShadowProcess.setPid(9001)
        return RunDiagnosticImpl(
            context = app,
            localInfo = diagnosticsLocalInfo(),
            processName = processName,
            storage = storage,
            serialization = SerializationImpl(),
            scope = scope,
            ownIdLoggerProvider = { localLogger }
        )
    }

    private fun serverLogger(
        network: RecordingServerNetwork,
        dispatcher: CoroutineDispatcher,
        instanceJob: Job
    ): ServerLogger = ServerLogger(
        instanceName = InstanceName("run-diagnostic-test"),
        configuration = diagnosticsConfiguration(rootUrl = "https://diagnostics.example/diagnostics/"),
        localInfo = diagnosticsLocalInfo(),
        appConfigProvider = ImmediateAppConfigProvider(diagnosticsAppConfig(AppConfig.LogLevel.DEBUG)),
        network = network,
        serialization = SerializationImpl(),
        dispatcher = dispatcher,
        instanceJob = instanceJob,
        ownIdLogger = null
    )

    private fun previousRunRecord(
        processName: String = app.applicationInfo.processName,
        initializedAt: Long = PREVIOUS_INITIALIZED_AT
    ): String = """
        {
          "correlationId": "previous-correlation",
          "processId": 4242,
          "processName": "$processName",
          "initializedAt": $initializedAt
        }
    """.trimIndent()

    private fun exitInfo(
        pid: Int,
        processName: String,
        timestamp: Long,
        reason: Int = ApplicationExitInfo.REASON_OTHER,
        status: Int = 0,
        importance: Int = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
        description: String? = null
    ): ApplicationExitInfo = ApplicationExitInfoBuilder.newBuilder()
        .setPid(pid)
        .setProcessName(processName)
        .setTimestamp(timestamp)
        .setReason(reason)
        .setStatus(status)
        .setImportance(importance)
        .apply { if (description != null) setDescription(description) }
        .build()

    private fun recordingLogger(entries: MutableList<LocalLog>): OwnIdLogger = object : OwnIdLogger {
        override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
        override val tag: String = "OwnID-RunDiagnostic-Test"

        override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
            entries += LocalLog(level = level, className = className, message = message, cause = cause)
        }
    }

    private fun String.asJson(): JsonObject = SerializationImpl().json.parseToJsonElement(this).jsonObject

    private fun Request.jsonBody(): JsonObject {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8().asJson()
    }

    private data class LocalLog(
        val level: OwnIdLogger.Level,
        val className: String,
        val message: String,
        val cause: Throwable?
    )

    private class RecordingStorage(
        initialValue: String?,
        private val putFailure: IOException? = null
    ) : Storage {
        private var value: String? = initialValue
        var readCount: Int = 0
            private set
        var putAttempts: Int = 0
            private set
        val persistedValues = mutableListOf<String>()

        override suspend fun getString(key: String, default: String?): String? {
            readCount += 1
            return value ?: default
        }

        override suspend fun putString(key: String, value: String) {
            putAttempts += 1
            putFailure?.let { throw it }
            this.value = value
            persistedValues += value
        }

        override suspend fun getBoolean(key: String, default: Boolean?): Boolean? = error("Unexpected boolean read")
        override suspend fun putBoolean(key: String, value: Boolean): Unit = error("Unexpected boolean write")
        override suspend fun getNumber(key: String, default: Long?): Long? = error("Unexpected number read")
        override suspend fun putNumber(key: String, value: Long): Unit = error("Unexpected number write")
        override suspend fun getDouble(key: String, default: Double?): Double? = error("Unexpected double read")
        override suspend fun putDouble(key: String, value: Double): Unit = error("Unexpected double write")
        override suspend fun remove(key: String): Unit = error("Unexpected remove")
    }

    private companion object {
        private const val PREVIOUS_INITIALIZED_AT = 1_000L
    }
}
