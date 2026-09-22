package com.ownid.sdk.capability.impl

import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.Signature
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
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

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

    @Test
    @Config(sdk = [27])
    fun networkFailureAtAnyFetchSuppressesTheWholeReport() = runTest {
        for (failedPath in listOf("/robots.txt", "/.well-known/assetlinks.json", "/include.json", "/nested.json", "/v1/assetlinks:check")) {
            var dalSamples = 0
            var failureReached = false
            val report = runFixture { request ->
                if (request.url.encodedPath == failedPath) {
                    if (failedPath != "/v1/assetlinks:check" || dalSamples++ > 0) {
                        failureReached = true
                        throw IOException("offline")
                    }
                }
                fixtureResponse(request)
            }
            assertThat(failureReached).isTrue()
            assertThat(report).isEmpty()
        }
    }

    @Test
    @Config(sdk = [27])
    fun bodyReadIoFailureSuppressesReportWhileNonIoResponseFailureRemainsReportable() = runTest {
        for (failure in listOf(IOException("body interrupted"), IllegalStateException("body processing failed"))) {
            var failureReached = false
            val reports = runFixture { request ->
                if (request.url.encodedPath == "/.well-known/assetlinks.json") {
                    val source = object : ForwardingSource(Buffer()) {
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            failureReached = true
                            throw failure
                        }
                    }.buffer()
                    response(request, 200, "").newBuilder().body(object : ResponseBody() {
                        override fun contentType() = null
                        override fun contentLength() = -1L
                        override fun source() = source
                    }).build()
                } else fixtureResponse(request)
            }
            assertThat(failureReached).isTrue()
            if (failure is IOException) assertThat(reports).isEmpty()
            else assertThat(reports.single().message).contains("body processing failed")
        }
    }

    @Test
    @Config(sdk = [27])
    fun completedAssetLinkHttpAndContentFailuresRemainReportable() = runTest {
        for ((code, type, body) in listOf(Triple(404, "application/json", ""), Triple(200, "text/plain", "[]"), Triple(200, "application/json", "{"))) {
            val report = runFixture { request ->
                if (request.url.encodedPath == "/.well-known/assetlinks.json") response(request, code, body, type)
                else fixtureResponse(request)
            }.single().message
            assertThat(report).contains("PasskeyDiagnostics: FAIL")
            assertThat(report).contains(if (code == 404) "code=404" else if (type == "text/plain") "Wrong Content-Type" else "JSON parse error")
        }
    }

    @Test
    @Config(sdk = [27])
    fun completedDalFailuresAreNotReportedAsConflictingLinks() = runTest {
        for ((code, body) in listOf(503 to "", 200 to "{")) {
            var samples = 0
            val report = runFixture { request ->
                if (request.url.host == "digitalassetlinks.googleapis.com" && samples++ > 0) response(request, code, body)
                else fixtureResponse(request)
            }.single().message
            assertThat(report).contains("PasskeyDiagnostics: FAIL")
            assertThat(report).contains(if (code == 503) "HTTP error" else "JSON parse error")
            assertThat(report).doesNotContain("Conflicting DAL results")
            assertThat(samples).isEqualTo(3)
        }
    }

    @Test
    @Config(sdk = [27])
    fun appLinksOnlyFailuresDoNotFailPasskeySummaryAndSuccessIsLimitedToObservedLinks() = runTest {
        val report = runFixture { request ->
            if (request.url.queryParameter("relation")?.endsWith("handle_all_urls") == true) response(request, 200, "{\"linked\":false}")
            else fixtureResponse(request)
        }.single().message
        assertThat(report).contains("PasskeyDiagnostics: PASS")
        assertThat(report).contains("FAIL - Android linkage (AppLinks)")
        assertThat(report).contains("FAIL - Verify DAL (AppLinks)")
        assertThat(report).contains("Matching entry found")
        assertThat(report).contains("Link found")
        assertThat(report).contains("do not establish installed association or overall passkey readiness")
    }

    @Test
    @Config(sdk = [27])
    fun mixedCompletedDalBooleansReportConflictingResults() = runTest {
        var samples = 0
        val report = runFixture { request ->
            if (request.url.host == "digitalassetlinks.googleapis.com") response(request, 200, "{\"linked\":${samples++ % 2 == 0}}")
            else fixtureResponse(request)
        }.single().message
        assertThat(report).contains("PasskeyDiagnostics: WARN")
        assertThat(report).contains("Conflicting DAL results")
        assertThat(report).doesNotContain("Inconsistent across servers")
        assertThat(samples).isEqualTo(10)
    }

    @Suppress("DEPRECATION")
    private suspend fun kotlinx.coroutines.test.TestScope.runFixture(respond: (Request) -> Response): List<LogEntry> {
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES)
        packageInfo.signatures = arrayOf(Signature(certificate))
        shadowOf(app.packageManager).installPackage(packageInfo)
        val logs = mutableListOf<LogEntry>()
        val diagnostics = PasskeyDiagnosticsImpl(
            context = app,
            localInfo = localInfo(),
            scope = this,
            logger = recordingRouter(logs),
            client = OkHttpClient.Builder().addInterceptor { respond(it.request()) }.build()
        )
        diagnostics.verify("example.com")
        coroutineContext[kotlinx.coroutines.Job]!!.children.single().join()
        return logs
    }

    private fun fixtureResponse(request: Request): Response = when (request.url.encodedPath) {
        "/robots.txt" -> response(request, 404, "")
        "/.well-known/assetlinks.json" -> response(request, 200, "[{\"include\":\"https://example.com/include.json\"}]")
        "/include.json" -> response(request, 200, "[{\"include\":\"https://example.com/nested.json\"}]")
        "/nested.json" -> response(
            request, 200, """
            [{"relation":["delegate_permission/common.get_login_creds"],"target":{
            "namespace":"android_app","package_name":"${app.packageName}","sha256_cert_fingerprints":["$fingerprint"]}}]
        """.trimIndent()
        )

        "/v1/assetlinks:check" -> response(request, 200, "{\"linked\":true}")
        else -> error("Unexpected diagnostics request: ${request.url}")
    }

    private fun response(request: Request, code: Int, body: String, contentType: String = "application/json"): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
        .header("Content-Type", contentType).body(body.toResponseBody()).build()

    // This fixture is parsed and fingerprinted only; diagnostics do not validate certificate expiry.
    private val certificate: ByteArray
        get() = Base64.getDecoder().decode(
            "MIICEjCCAXugAwIBAgIUVRiX9twa7m8//qpIDy5OhoK0HCUwDQYJKoZIhvcNAQELBQAwGzEZMBcGA1UEAwwQZGlhZ25vc3RpY3MtdGVzdDAeFw0yNjA5MDkw" +
                    "OTQwMzNaFw0yNjA5MTAwOTQwMzNaMBsxGTAXBgNVBAMMEGRpYWdub3N0aWNzLXRlc3QwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMoONUBHeHfsY5s2" +
                    "2uKPKvwGYkWP7QN8PPYi71gfojYMuaNkF4ul4C4aVVJWaX0u0uB46Wq6mbVrh3G19v6muMv63g/HIKyOIBQBbtAezaWP84j8OVXt2LrSBeC4hdeYbnX/moAn" +
                    "rPgKAc/fEEw+CFb/pLOxJNmTK2VoAFPzvxKNAgMBAAGjUzBRMB0GA1UdDgQWBBTK0HthheUEoaaMMU/AjzeE5zoEBjAfBgNVHSMEGDAWgBTK0HthheUEoaaM" +
                    "MU/AjzeE5zoEBjAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4GBAIb8dj/4l1oZ6JUuDECmsFWXg2JZd8/6lasmSq07+VVDH3dwaHVu2CC0Fq0S" +
                    "aWpo5DEiZk5vHS0wtcY6gE2VSf4avQSPXJmDuSha168AM27zeBSHqpd6BI8t+4VkJezrz86/TYS48cPbfkMNbZJh7n74uS0euQudLKbVE1ofQ6ug"
        )

    private val fingerprint: String
        get() = MessageDigest.getInstance("SHA-256").digest(certificate).joinToString(":") { "%02X".format(it) }

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
