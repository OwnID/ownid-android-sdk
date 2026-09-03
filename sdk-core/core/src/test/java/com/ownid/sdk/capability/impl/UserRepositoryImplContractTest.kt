package com.ownid.sdk.capability.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.Storage
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.User
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.IOException

// Covers: STORAGE-010, STORAGE-030
internal class UserRepositoryImplContractTest {

    private val storage = FakeStorage()
    private val serialization = SerializationImpl()
    private val repository = UserRepositoryImpl(storage, serialization)

    @Test
    fun storedUserSerializesLoginIdAndAuthMethod() = runTest {
        val user = User(LoginId("person@example.com", LoginId.Type.Email), AuthMethod.Passkey)

        repository.setLastUser(user)

        val storedJson = serialization.json.parseToJsonElement(storage.storedStrings.getValue("LAST_USER")).jsonObject
        assertThat(storedJson.getValue("loginId").jsonObject.getValue("id").jsonPrimitive.contentOrNull).isEqualTo("person@example.com")
        assertThat(storedJson.getValue("loginId").jsonObject.getValue("type").jsonPrimitive.contentOrNull).isEqualTo("Email")
        assertThat(storedJson.getValue("authMethod").jsonPrimitive.contentOrNull).isEqualTo("passkey")

        val restored = repository.getLastUser()
        assertThat(restored?.loginId).isEqualTo(user.loginId)
        assertThat(restored?.authMethod).isEqualTo(user.authMethod)
    }

    @Test
    fun missingAndClearedUserReturnNoLastUser() = runTest {
        assertThat(repository.getLastUser()).isNull()

        repository.setLastUser(User(LoginId("+15555550100", LoginId.Type.PhoneNumber), AuthMethod.Otp))
        repository.clearLastUser()

        assertThat(repository.getLastUser()).isNull()
        assertThat(storage.storedStrings).isEmpty()
        assertThat(storage.removedKeys).containsExactly("LAST_USER")
    }

    @Test
    fun storageWriteAndRemoveFailuresPropagate() = runTest {
        val writeFailure = IOException("write failed")
        val removeFailure = IOException("remove failed")
        val failingStorage = FakeStorage(putFailure = writeFailure, removeFailure = removeFailure)
        val failingRepository = UserRepositoryImpl(failingStorage, serialization)
        val user = User(LoginId("person@example.com", LoginId.Type.Email), AuthMethod.Passkey)

        assertThat(failureOf { failingRepository.setLastUser(user) }).isSameInstanceAs(writeFailure)
        assertThat(failureOf { failingRepository.clearLastUser() }).isSameInstanceAs(removeFailure)
    }

    @Test
    fun codecFailuresFromReadsAndWritesPropagate() = runTest {
        val decodeFailure = SerializationException("decode failed")
        val decodingRepository = UserRepositoryImpl(
            FakeStorage(initialStrings = mapOf("LAST_USER" to "stored-user")),
            object : Serialization by serialization {
                override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T =
                    throw decodeFailure
            }
        )
        val encodeFailure = SerializationException("encode failed")
        val encodingStorage = FakeStorage()
        val encodingRepository = UserRepositoryImpl(
            encodingStorage,
            object : Serialization by serialization {
                override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String =
                    throw encodeFailure
            }
        )
        val user = User(LoginId("person@example.com", LoginId.Type.Email), AuthMethod.Passkey)

        assertThat(failureOf { decodingRepository.getLastUser() }).isSameInstanceAs(decodeFailure)
        assertThat(failureOf { encodingRepository.setLastUser(user) }).isSameInstanceAs(encodeFailure)
        assertThat(encodingStorage.storedStrings).isEmpty()
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (error: Exception) {
        error
    }

    private class FakeStorage(
        initialStrings: Map<String, String> = emptyMap(),
        private val putFailure: IOException? = null,
        private val removeFailure: IOException? = null
    ) : Storage {
        private val strings = initialStrings.toMutableMap()
        val removedKeys = mutableListOf<String>()

        val storedStrings: Map<String, String>
            get() = strings.toMap()

        override suspend fun getString(key: String, default: String?): String? = strings[key] ?: default
        override suspend fun putString(key: String, value: String) {
            putFailure?.let { throw it }
            strings[key] = value
        }

        override suspend fun getBoolean(key: String, default: Boolean?): Boolean = error("Unexpected boolean read")
        override suspend fun putBoolean(key: String, value: Boolean): Unit = error("Unexpected boolean write")
        override suspend fun getNumber(key: String, default: Long?): Long = error("Unexpected number read")
        override suspend fun putNumber(key: String, value: Long): Unit = error("Unexpected number write")
        override suspend fun getDouble(key: String, default: Double?): Double = error("Unexpected double read")
        override suspend fun putDouble(key: String, value: Double): Unit = error("Unexpected double write")
        override suspend fun remove(key: String) {
            removeFailure?.let { throw it }
            removedKeys += key
            strings.remove(key)
        }
    }
}
