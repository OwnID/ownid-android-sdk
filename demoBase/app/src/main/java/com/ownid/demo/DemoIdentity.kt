package com.ownid.demo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.provider.OwnIdProvidersRegistrar
import com.ownid.sdk.provider.SessionOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private val json = Json { ignoreUnknownKeys = true }

@Serializable
@Immutable
data class DemoUserSession(val token: String, val accessToken: AccessToken? = null)

@Serializable
@Immutable
data class DemoUser(val email: String, val name: String? = null)

private object DemoUserSessionStorage {
    private const val PREFS_USER_KEY = "saved_session_json"
    private val currentUserFlow = MutableStateFlow<DemoUserSession?>(null)
    private lateinit var preferences: SharedPreferences

    fun observe(): StateFlow<DemoUserSession?> = currentUserFlow.asStateFlow()

    fun get(): DemoUserSession? = currentUserFlow.value

    fun save(userSession: DemoUserSession) {
        preferences.edit { putString(PREFS_USER_KEY, json.encodeToString(userSession)) }
        currentUserFlow.value = userSession
    }

    fun clear() {
        preferences.edit { remove(PREFS_USER_KEY) }
        currentUserFlow.value = null
    }

    fun init(context: Context) {
        if (::preferences.isInitialized) return
        preferences = context.applicationContext.getSharedPreferences("demo_prefs", Context.MODE_PRIVATE)
        currentUserFlow.value = preferences.getString(PREFS_USER_KEY, null)
            ?.let { runCatching { json.decodeFromString<DemoUserSession>(it) }.getOrNull() }
    }
}

object DemoIdentityPlatform {
    val BASE_API_URL: String = BuildConfig.DEMO_IDENTITY_BASE_URL

    private val okHttpClient = OkHttpClient.Builder().build()

    fun init(context: Context) {
        DemoUserSessionStorage.init(context)
    }

    fun currentSession(): DemoUserSession? = DemoUserSessionStorage.get()

    fun observeSession(): StateFlow<DemoUserSession?> = DemoUserSessionStorage.observe()

    fun logout() {
        DemoUserSessionStorage.clear()
    }

    suspend fun passwordLogin(email: String, password: String): Result<Unit> =
        login(email, password)
            .map { userSession -> DemoUserSessionStorage.save(userSession) }

    suspend fun registerAndSaveSession(name: String, email: String, password: String, ownIdData: String?): Result<Unit> =
        register(name, email, password, ownIdData)
            .map { userSession -> DemoUserSessionStorage.save(userSession) }

    suspend fun loadCurrentUser(session: DemoUserSession): Result<DemoUser> =
        getProfileByToken(session)
            .onFailure { DemoUserSessionStorage.clear() }

    suspend fun register(name: String, email: String, password: String, ownIdData: String?): Result<DemoUserSession> {
        val registerPayload = buildJsonObject {
            put("name", name)
            put("email", email)
            put("password", password)
            ownIdData?.let { put("ownIdData", it) }
        }.toString()

        val registerRequest = Request.Builder()
            .url("${BASE_API_URL}register")
            .post(registerPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            runCatching {
                okHttpClient.newCall(registerRequest).execute().use { registerResponse ->
                    val body = registerResponse.body.string()
                    if (!registerResponse.isSuccessful) throw IOException("Register request fail: ${registerResponse.code} $body")
                    json.decodeFromString<DemoUserSession>(body)
                }
            }
        }
    }

    suspend fun login(email: String, password: String): Result<DemoUserSession> {
        val loginPayload = buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()

        val loginRequest = Request.Builder()
            .url("${BASE_API_URL}login")
            .post(loginPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            runCatching {
                okHttpClient.newCall(loginRequest).execute().use { loginResponse ->
                    val body = loginResponse.body.string()
                    if (!loginResponse.isSuccessful) throw IOException("Login request fail: ${loginResponse.code} $body")
                    json.decodeFromString<DemoUserSession>(body)
                }
            }
        }
    }

    private suspend fun getProfileByToken(session: DemoUserSession): Result<DemoUser> {
        val profileRequest = Request.Builder()
            .url("${BASE_API_URL}profile")
            .addHeader("Authorization", "Bearer ${session.token}")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            runCatching {
                okHttpClient.newCall(profileRequest).execute().use { profileResponse ->
                    val body = profileResponse.body.string()
                    if (!profileResponse.isSuccessful) throw IOException("Profile request fail: ${profileResponse.code} $body")
                    json.decodeFromString<DemoUser>(body)
                }
            }
        }
    }
}

fun OwnIdProvidersRegistrar.demoIdentityProviders(): OwnIdProvidersRegistrar = apply {

    sessionCreate {
        isAvailable { params -> params.loginId.type == LoginId.Type.Email }
        create { params ->
            runCatching { json.decodeFromString<DemoUserSession>(params.sessionPayload) }.map { it.copy(accessToken = params.accessToken) }
                .onSuccess { DemoUserSessionStorage.save(it) }
                .map { SessionOutput(session = it) }
        }
    }

    passwordAuthenticate {
        isAvailable { params -> params.loginId.type == LoginId.Type.Email }
        authenticate { params ->
            DemoIdentityPlatform.login(params.loginId.id, params.password)
                .onSuccess { DemoUserSessionStorage.save(it) }
                .map { SessionOutput(session = it) }
        }
    }
}
