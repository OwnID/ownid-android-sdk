package com.ownid.sdk.ui.webbridge.impl.plugins

import android.net.Uri
import android.webkit.WebView
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LoginIdValidator
import com.ownid.sdk.capability.UserRepository
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AuthMethod
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.model.User
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
// Covers: WB-BRIDGE-220
internal class WebBridgeUserRepositoryPluginHandleContractTest {

    private val serialization = SerializationImpl()

    @Test
    fun getLastUserReturnsRepositoryUserPayloadOrJsonNull(): Unit = runTest {
        val repository = RecordingUserRepository(
            lastUser = User(LoginId("person@example.test", LoginId.Type.Email), AuthMethod.Passkey)
        )
        val plugin = plugin(repository)

        val userResult = plugin.handle(message(action = "getLastUser"))
        repository.lastUser = null
        val missingResult = plugin.handle(message(action = "GETLASTUSER"))

        val userPayload = userResult.success!!.jsonObject
        assertThat(userPayload["loginId"]!!.jsonPrimitive.content).isEqualTo("person@example.test")
        assertThat(userPayload["authMethod"]!!.jsonPrimitive.content).isEqualTo("passkey")
        assertThat(missingResult.success).isSameInstanceAs(JsonNull)
        assertThat(repository.events).containsExactly("get", "get").inOrder()
    }

    @Test
    fun setLastUserValidatesLoginIdStoresUserAndReturnsEmptyObject(): Unit = runTest {
        val repository = RecordingUserRepository()
        val validator = RecordingLoginIdValidator(type = LoginId.Type.UserName)
        val plugin = plugin(repository = repository, loginIdValidator = validator)

        val result = plugin.handle(message(action = "setLastUser", params = """{"loginId":"person","authMethod":"otp"}"""))

        assertThat(result.success).isEqualTo(JsonObject(emptyMap()))
        assertThat(result.error).isNull()
        assertThat(validator.loginIds).containsExactly("person")
        assertThat(repository.storedUsers.single().loginId).isEqualTo(LoginId("person", LoginId.Type.UserName))
        assertThat(repository.storedUsers.single().authMethod).isSameInstanceAs(AuthMethod.Otp)
        assertThat(repository.events).containsExactly("set").inOrder()
    }

    @Test
    fun setLastUserInvalidParamsReturnBridgeErrorBeforeRepositoryWrite(): Unit = runTest {
        val repository = RecordingUserRepository()
        val plugin = plugin(repository)

        val missingParams = plugin.handle(message(action = "setLastUser"))
        val missingLoginId = plugin.handle(message(action = "setLastUser", params = """{"authMethod":"passkey"}"""))

        assertThat(missingParams.error!!["message"]!!.jsonPrimitive.contentOrNull)
            .isEqualTo("WebBridgeUserRepositoryPlugin: No params set for 'setLastUser'")
        assertThat(missingLoginId.error!!["message"]!!.jsonPrimitive.contentOrNull)
            .isEqualTo("WebBridgeUserRepositoryPlugin: Missing or invalid 'loginId' parameter")
        assertThat(repository.events).isEmpty()
    }

    @Test
    fun repositoryFailureMapsToBridgeErrorPayload(): Unit = runTest {
        val readFailure = plugin(RecordingUserRepository(getFailure = IllegalStateException("read failed")))
        val writeFailure = plugin(RecordingUserRepository(setFailure = IllegalStateException("write failed")))

        val readResult = readFailure.handle(message(action = "getLastUser"))
        val writeResult = writeFailure.handle(message(action = "setLastUser", params = """{"loginId":"person@example.test"}"""))

        assertThat(readResult.success).isNull()
        assertThat(readResult.error!!["message"]!!.jsonPrimitive.contentOrNull).isEqualTo("read failed")
        assertThat(writeResult.success).isNull()
        assertThat(writeResult.error!!["message"]!!.jsonPrimitive.contentOrNull).isEqualTo("write failed")
    }

    @Test
    fun repositoryCancellationIsPropagatedToBridgeLifecycle(): Unit = runTest {
        val readCancellation = CancellationException("cancel get")
        val readPlugin = plugin(RecordingUserRepository(getFailure = readCancellation))
        val writeCancellation = CancellationException("cancel set")
        val writePlugin = plugin(RecordingUserRepository(setFailure = writeCancellation))

        val thrownRead = captureCancellation { readPlugin.handle(message(action = "getLastUser")) }
        val thrownWrite = captureCancellation {
            writePlugin.handle(message(action = "setLastUser", params = """{"loginId":"person@example.test"}"""))
        }

        assertThat(thrownRead).isSameInstanceAs(readCancellation)
        assertThat(thrownWrite).isSameInstanceAs(writeCancellation)
    }

    private suspend fun captureCancellation(block: suspend () -> Unit): CancellationException? =
        try {
            block()
            null
        } catch (error: CancellationException) {
            error
        }

    private fun plugin(
        repository: RecordingUserRepository,
        loginIdValidator: LoginIdValidator = RecordingLoginIdValidator()
    ): WebBridgeUserRepositoryPluginImpl =
        WebBridgeUserRepositoryPluginImpl(
            userRepository = repository,
            loginIdValidator = loginIdValidator,
            serialization = serialization
        )

    private fun message(action: String, params: String? = null): WebBridgePlugin.Message =
        WebBridgePlugin.Message(
            webView = WebView(RuntimeEnvironment.getApplication()),
            sourceOrigin = Uri.parse("https://example.test"),
            isMainFrame = true,
            allowedOriginRules = setOf("https://example.test"),
            payload = WebBridgePlugin.Message.Payload(
                pluginId = "STORAGE",
                action = action,
                callbackPath = "ownid.callback",
                params = params
            )
        )

    private class RecordingUserRepository(
        var lastUser: User? = null,
        private val getFailure: Throwable? = null,
        private val setFailure: Throwable? = null
    ) : UserRepository {
        val events = mutableListOf<String>()
        val storedUsers = mutableListOf<User>()

        override suspend fun getLastUser(): User? {
            events += "get"
            getFailure?.let { throw it }
            return lastUser
        }

        override suspend fun setLastUser(user: User) {
            events += "set"
            setFailure?.let { throw it }
            storedUsers += user
            lastUser = user
        }

        override suspend fun clearLastUser() {
            events += "clear"
            lastUser = null
        }
    }

    private class RecordingLoginIdValidator(
        private val type: LoginId.Type = LoginId.Type.Email
    ) : LoginIdValidator {
        val loginIds = mutableListOf<String>()

        override fun determineLoginIdType(loginId: String): LoginId.Type {
            loginIds += loginId
            return type
        }

        override fun validate(loginId: LoginId): LoginId = loginId
    }
}
