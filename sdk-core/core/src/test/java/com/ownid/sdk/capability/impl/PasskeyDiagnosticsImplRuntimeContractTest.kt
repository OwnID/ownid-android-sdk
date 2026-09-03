package com.ownid.sdk.capability.impl

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.InternalOwnIdAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: DIAG-RUNTIME-050
internal class PasskeyDiagnosticsImplRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun verifySkipsDuplicateNormalizedRpIdWithoutStartingSecondDiagnosticsRun() = runTest {
        val logs = mutableListOf<LogEntry>()
        val ownerJob = SupervisorJob()
        val ownerScope = CoroutineScope(ownerJob + StandardTestDispatcher(testScheduler))
        val diagnostics = PasskeyDiagnosticsImpl(
            context = app,
            localInfo = localInfo(),
            scope = ownerScope,
            logger = recordingRouter(logs)
        )

        try {
            diagnostics.verify("Example.COM")
            diagnostics.verify("example.com")

            val diagnosticsJob = ownerJob.children.single()
            assertThat(diagnosticsJob.isActive).isTrue()
            assertThat(logs).hasSize(1)
            assertThat(logs.single().message).contains("example.com")
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun invalidRpIdReportsLocallyAndStopsBeforeNetworkSteps() = runTest {
        val logs = mutableListOf<LogEntry>()
        val ownerJob = SupervisorJob()
        val ownerScope = CoroutineScope(ownerJob + StandardTestDispatcher(testScheduler))
        val diagnostics = PasskeyDiagnosticsImpl(
            context = app,
            localInfo = localInfo(),
            scope = ownerScope,
            logger = recordingRouter(logs)
        )

        try {
            diagnostics.verify("https://invalid.example/path")
            val diagnosticsJob = ownerJob.children.single()
            runCurrent()

            assertThat(diagnosticsJob.isCompleted).isTrue()
            assertThat(logs).hasSize(1)
            val report = logs.single().message
            assertThat(report).contains("https://invalid.example/path")
            assertThat(report).contains("FAIL - Verify rpId")
            assertThat(report).doesNotContain("Verify robots.txt")
            assertThat(report).doesNotContain("Fetch asset links")
            assertThat(report).doesNotContain("Verify DAL")
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun cancellationFromReportSinkRemainsCoroutineCancellation() = runTest {
        val cancellation = CancellationException("diagnostics canceled")
        val ownerJob = SupervisorJob()
        val ownerScope = CoroutineScope(ownerJob + StandardTestDispatcher(testScheduler))
        var sinkInvocations = 0
        val diagnostics = PasskeyDiagnosticsImpl(
            context = app,
            localInfo = localInfo(),
            scope = ownerScope,
            logger = logRouter {
                sinkInvocations += 1
                throw cancellation
            }
        )

        try {
            diagnostics.verify("https://invalid.example/path")
            val diagnosticsJob = ownerJob.children.single()
            var completionObserved = false
            var completionCause: Throwable? = null
            diagnosticsJob.invokeOnCompletion { cause ->
                completionObserved = true
                completionCause = cause
            }
            runCurrent()

            assertThat(sinkInvocations).isEqualTo(1)
            assertThat(completionObserved).isTrue()
            assertThat(completionCause).isSameInstanceAs(cancellation)
            assertThat(diagnosticsJob.isCancelled).isTrue()
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun regularReportSinkFailureIsContainedInsideDiagnosticsJob() = runTest {
        val ownerJob = SupervisorJob()
        val ownerScope = CoroutineScope(ownerJob + StandardTestDispatcher(testScheduler))
        var sinkInvocations = 0
        val diagnostics = PasskeyDiagnosticsImpl(
            context = app,
            localInfo = localInfo(),
            scope = ownerScope,
            logger = logRouter {
                sinkInvocations += 1
                throw IllegalStateException("sink failed")
            }
        )

        try {
            diagnostics.verify("https://invalid.example/path")
            val diagnosticsJob = ownerJob.children.single()
            var completionObserved = false
            var completionCause: Throwable? = IllegalStateException("not completed")
            diagnosticsJob.invokeOnCompletion { cause ->
                completionObserved = true
                completionCause = cause
            }
            runCurrent()

            assertThat(sinkInvocations).isEqualTo(1)
            assertThat(completionObserved).isTrue()
            assertThat(completionCause).isNull()
            assertThat(diagnosticsJob.isCompleted).isTrue()
            assertThat(diagnosticsJob.isCancelled).isFalse()
        } finally {
            ownerScope.cancel()
        }
    }

    private fun localInfo(): LocalInfo = object : LocalInfo {
        override val modules: List<Pair<String, String>> = listOf("Core" to "1.2.3")
        override val packageName: String = app.packageName
        override val appVersion: String = "42.7"
        override val userAgent: String = "OwnID-Test/1.0"
        override val correlationId: String = "correlation-1"
        override val isDebuggable: Boolean = false
        override val isSystemFidoCapable: Boolean = true
        override val isDeviceSecured: Boolean = true
        override val isFingerprintHardwarePresent: Boolean = false
        override val isFaceHardwarePresent: Boolean = true
        override val isIrisHardwarePresent: Boolean = false
        override val isStrongBiometricEnabled: Boolean = true
    }

    private fun recordingRouter(entries: MutableList<LogEntry>): OwnIdLogRouter {
        return logRouter { message -> entries += LogEntry(message = message) }
    }

    private fun logRouter(onLog: (String) -> Unit): OwnIdLogRouter {
        val logger = object : OwnIdLogger {
            override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
            override val tag: String = "OwnID-PasskeyDiagnostics-Test"

            override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
                onLog(message)
            }
        }
        return OwnIdLogRouter(ownIdLoggerProvider = { logger }, serverLoggerProvider = { null })
    }

    private data class LogEntry(
        val message: String
    )
}
