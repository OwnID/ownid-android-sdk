package com.ownid.sdk.logger

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.Network
import com.ownid.sdk.model.InternalOwnIdAPI
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: LOG-010, LOG-020, LOG-030
internal class LoggerBoundaryContractTest {

    @Test
    fun customLoggerSinkReplacesDefaultLocalSinkAndAppliesConfiguredThreshold() {
        val entries = mutableListOf<LogEntry>()
        val cause = IllegalStateException("boom")

        val logger = OwnIdLoggerBuilder().apply {
            level = OwnIdLogger.Level.Info
            tag = "OwnID-Test"
            log { level, className, message, throwable ->
                entries += LogEntry(level, className, message, throwable)
            }
        }.build()

        logger.log(OwnIdLogger.Level.Debug, "Source", "below threshold")
        logger.log(OwnIdLogger.Level.Info, "Source", "visible", cause)

        assertThat(logger.tag).isEqualTo("OwnID-Test")
        assertThat(logger.isEnabled(OwnIdLogger.Level.Debug)).isFalse()
        assertThat(logger.isEnabled(OwnIdLogger.Level.Info)).isTrue()
        assertThat(entries).containsExactly(LogEntry(OwnIdLogger.Level.Info, "Source", "visible", cause))
    }

    @Test
    fun logRouterFansOutToCurrentLocalLoggerAndNormalizesMissingMessage() {
        val entries = mutableListOf<LogEntry>()
        val logger = object : OwnIdLogger {
            override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
            override val tag: String = "OwnID-Test"

            override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
                entries += LogEntry(level, className, message, cause)
            }
        }
        val router = OwnIdLogRouter(ownIdLoggerProvider = { logger }, serverLoggerProvider = { null })

        router.logW(this, "prefix", null)

        assertThat(entries).hasSize(1)
        assertThat(entries.single().level).isEqualTo(OwnIdLogger.Level.Warn)
        assertThat(entries.single().className).contains("LoggerBoundaryContractTest#")
        assertThat(entries.single().className).contains(":prefix")
        assertThat(entries.single().message).isEmpty()
    }

    @Test
    fun customLoggerSinkFailureIsReportedToAndroidLogAndDoesNotEscape() {
        mockkStatic(Log::class)
        try {
            every { Log.println(any(), any(), any()) } returns 0
            val logger = OwnIdLoggerBuilder().apply {
                level = OwnIdLogger.Level.Error
                tag = "OwnID-Test"
                log { _, _, _, _ -> throw IllegalStateException("sink failed") }
            }.build()

            logger.log(OwnIdLogger.Level.Error, "Source", "message")

            verify(exactly = 1) {
                Log.println(
                    Log.ERROR,
                    "OwnID-Test",
                    match { it.contains("Logging failed") && it.contains("sink failed") }
                )
            }
        } finally {
            unmockkStatic(Log::class)
        }
    }

    @Test
    fun networkRequestSuppressHttpLogMarksOnlySuppressedRequests() {
        val url = "https://example.com/events".toHttpUrl()

        val normal = Network.Request.Builder(url).build()
        val suppressed = Network.Request.Builder(url).suppressHttpLog().build()

        assertThat(normal.tag(Network.Request.NoHttpLogTag::class.java)).isNull()
        assertThat(suppressed.tag(Network.Request.NoHttpLogTag::class.java)).isSameInstanceAs(Network.Request.NoHttpLogTag)
    }

    private data class LogEntry(
        val level: OwnIdLogger.Level,
        val className: String,
        val message: String,
        val cause: Throwable?
    )
}
