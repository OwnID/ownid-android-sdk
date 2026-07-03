package com.ownid.sdk.capability.impl

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.InternalOwnIdAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: STORAGE-RUNTIME-010, STORAGE-RUNTIME-020
internal class StorageImplRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun stringValuesPersistAcrossRecreatedStoreAndStayIsolatedByFileName(): Unit = runTest {
        val fileName = uniqueFileName()
        val isolatedFileName = "$fileName-isolated"

        val firstOwner = newOwnerScope()
        try {
            val first = storage(fileName, firstOwner)
            first.putString("LAST_USER", "serialized-user")
        } finally {
            firstOwner.cancel()
        }
        advanceUntilIdle()

        val recreatedOwner = newOwnerScope()
        val isolatedOwner = newOwnerScope()
        try {
            val recreated = storage(fileName, recreatedOwner)
            val isolated = storage(isolatedFileName, isolatedOwner)

            assertThat(recreated.getString("LAST_USER")).isEqualTo("serialized-user")
            assertThat(isolated.getString("LAST_USER")).isNull()
        } finally {
            recreatedOwner.cancel()
            isolatedOwner.cancel()
        }
    }

    @Test
    fun corruptedPreferencesAreLoggedReplacedWithEmptyPreferencesAndAcceptLaterWrites(): Unit = runTest {
        val fileName = uniqueFileName()
        val firstOwner = newOwnerScope()
        val file = try {
            val first = storage(fileName, firstOwner)
            first.putString("LAST_USER", "serialized-user")
            storageFile(fileName).also { assertThat(it.exists()).isTrue() }
        } finally {
            firstOwner.cancel()
        }
        advanceUntilIdle()
        file.writeBytes(byteArrayOf(0x00, 0x7f, 0x55, 0x2a))

        val logEntries = mutableListOf<LogEntry>()
        val ownerAfterCorruption = newOwnerScope()
        try {
            val afterCorruption = storage(fileName, ownerAfterCorruption, logger = recordingRouter(logEntries))

            assertThat(afterCorruption.getString("LAST_USER", default = "missing")).isEqualTo("missing")
            assertThat(logEntries.any { it.level == OwnIdLogger.Level.Warn && it.message.contains("DataStore corrupted") }).isTrue()

            afterCorruption.putString("LAST_USER", "rewritten-user")
            assertThat(afterCorruption.getString("LAST_USER")).isEqualTo("rewritten-user")
        } finally {
            ownerAfterCorruption.cancel()
        }
    }

    private fun TestScope.newOwnerScope(): CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private fun storage(fileName: String, ownerScope: CoroutineScope, logger: OwnIdLogRouter? = null): StorageImpl =
        StorageImpl(context = app, fileName = fileName, scope = ownerScope, logger = logger)

    private fun storageFile(fileName: String): File = File(app.noBackupFilesDir, "ownid/$fileName.preferences_pb")

    private fun uniqueFileName(): String = "storage-runtime-${UUID.randomUUID()}"

    private fun recordingRouter(entries: MutableList<LogEntry>): OwnIdLogRouter {
        val logger = object : OwnIdLogger {
            override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
            override val tag: String = "OwnID-Storage-Test"

            override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
                entries += LogEntry(level = level, message = message)
            }
        }
        return OwnIdLogRouter(ownIdLoggerProvider = { logger }, serverLoggersProvider = { emptyList() })
    }

    private data class LogEntry(
        val level: OwnIdLogger.Level,
        val message: String
    )
}
