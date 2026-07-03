package com.ownid.sdk.capability.impl

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.InternalOwnIdAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun verifySkipsDuplicateNormalizedRpIdWithoutStartingSecondDiagnosticsRun() {
        val logs = mutableListOf<LogEntry>()
        val ownerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher())
        val diagnostics = PasskeyDiagnosticsImpl(
            context = app,
            localInfo = localInfo(),
            scope = ownerScope,
            logger = recordingRouter(logs)
        )

        try {
            diagnostics.verify("Example.COM")
            diagnostics.verify("example.com")

            assertThat(logs).containsExactly(
                LogEntry(
                    message = "Skipping duplicate passkey diagnostics for rpId=example.com"
                )
            )
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
        val logger = object : OwnIdLogger {
            override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
            override val tag: String = "OwnID-PasskeyDiagnostics-Test"

            override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
                entries += LogEntry(message = message)
            }
        }
        return OwnIdLogRouter(ownIdLoggerProvider = { logger }, serverLoggersProvider = { emptyList() })
    }

    private data class LogEntry(
        val message: String
    )
}
