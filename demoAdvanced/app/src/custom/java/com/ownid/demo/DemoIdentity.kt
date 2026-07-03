package com.ownid.demo

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ownid.sdk.model.AccessToken
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.provider.OwnIdProvidersRegistrar
import com.ownid.sdk.provider.SessionOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class DemoUserSession(val token: String, val accessToken: AccessToken? = null)

@Serializable
data class DemoUser(val email: String, val name: String? = null)

object DemoUserSessionStorage {
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

class CustomDemoAuthIntegration(context: Context) : DemoAuthIntegration {
    init {
        DemoUserSessionStorage.init(context)
    }

    override val isSignedIn: Flow<Boolean> = DemoUserSessionStorage.observe()
        .map { session -> session != null }
        .distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        DemoIdentityPlatform.login(email, password)
            .map { userSession -> DemoUserSessionStorage.save(userSession) }

    override suspend fun registerAndSignIn(name: String, email: String, password: String, ownIdData: String?): Result<Unit> =
        DemoIdentityPlatform.register(name, email, password, ownIdData)
            .map { userSession -> DemoUserSessionStorage.save(userSession) }

    override suspend fun createUser(name: String, email: String, password: String, ownIdData: String?): Result<Unit> =
        DemoIdentityPlatform.register(name, email, password, ownIdData)
            .map { }

    override suspend fun loadCurrentUser(): Result<DemoAuthIntegration.CurrentUser> =
        DemoUserSessionStorage.get()
            ?.let { session ->
                DemoIdentityPlatform.getProfileByToken(session)
                    .onFailure { DemoUserSessionStorage.clear() }
                    .map { user -> DemoAuthIntegration.CurrentUser(email = user.email, name = user.name, accessToken = session.accessToken) }
            }
            ?: Result.failure(IllegalStateException("User is not logged in"))

    override fun signOut() {
        DemoUserSessionStorage.clear()
    }
}

object DemoIdentityPlatform {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    suspend fun register(name: String, email: String, password: String, ownIdData: String?): Result<DemoUserSession> {
        val registerPayload = buildJsonObject {
            put("name", name)
            put("email", email)
            put("password", password)
            ownIdData?.let { put("ownIdData", it) }
        }.toString()

        val registerRequest = Request.Builder()
            .url("${BuildConfig.DEMO_IDENTITY_BASE_URL}register")
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
            .url("${BuildConfig.DEMO_IDENTITY_BASE_URL}login")
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

    suspend fun getProfileByToken(session: DemoUserSession): Result<DemoUser> {
        val profileRequest = Request.Builder()
            .url("${BuildConfig.DEMO_IDENTITY_BASE_URL}profile")
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
