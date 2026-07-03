package com.ownid.sdk.capability.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.Storage
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.User
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

// Covers: STORAGE-010
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

    private class FakeStorage : Storage {
        private val strings = mutableMapOf<String, String>()
        val removedKeys = mutableListOf<String>()

        val storedStrings: Map<String, String>
            get() = strings.toMap()

        override suspend fun getString(key: String, default: String?): String? = strings[key] ?: default
        override suspend fun putString(key: String, value: String) {
            strings[key] = value
        }

        override suspend fun getBoolean(key: String, default: Boolean?): Boolean = error("Unexpected boolean read")
        override suspend fun putBoolean(key: String, value: Boolean): Unit = error("Unexpected boolean write")
        override suspend fun getNumber(key: String, default: Long?): Long = error("Unexpected number read")
        override suspend fun putNumber(key: String, value: Long): Unit = error("Unexpected number write")
        override suspend fun getDouble(key: String, default: Double?): Double = error("Unexpected double read")
        override suspend fun putDouble(key: String, value: Double): Unit = error("Unexpected double write")
        override suspend fun remove(key: String) {
            removedKeys += key
            strings.remove(key)
        }
    }
}
