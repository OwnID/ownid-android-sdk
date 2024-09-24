package com.ownid.demo.custom

import android.os.Handler
import android.os.Looper
import com.ownid.sdk.exception.OwnIdIntegrationError
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection


data class User(val name: String, val email: String, val token: String, val authToken: String? = null)

class IdentityPlatform(val baseApiUrl: String) {

    var currentUser: User? = null

    fun register(name: String, email: String, password: String, callback: Result<String>.() -> Unit) {
        val postJsonData = JSONObject()
            .put("name", name)
            .put("email", email)
            .put("password", password)
            .toString()

        doPost(baseApiUrl + "register", postJsonData, null, callback)
    }

    fun registerWithOwnId(name: String, email: String, ownIdData: String?, callback: Result<String>.() -> Unit) {
        val postJsonData = JSONObject()
            .put("name", name)
            .put("email", email)
            .put("password", "password")
            .apply { if (ownIdData != null) put("ownIdData", ownIdData) }
            .toString()

        doPost(baseApiUrl + "register", postJsonData, null, callback)
    }

    fun login(email: String, password: String, callback: Result<User>.() -> Unit) {
        val postJsonData = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()

        doPost(baseApiUrl + "login", postJsonData) {
            mapCatching { response -> JSONObject(response).getString("token") }
                .onSuccess { token -> getProfile(token, null, callback) }
                .onFailure { callback(Result.failure(it)) }
        }
    }

    fun logout() {
        currentUser = null
    }

    fun getProfile(token: String, authToken: String? = null, callback: Result<User>.() -> Unit) {
        doGet(baseApiUrl + "profile", token) {
            mapCatching { response ->
                val responseJson = JSONObject(response)
                User(responseJson.optString("name").ifBlank { "-" }, responseJson.optString("email").ifBlank { "-" }, token, authToken)
            }
                .onSuccess { user ->
                    currentUser = user
                    callback(Result.success(user))
                }
                .onFailure { callback(Result.failure(it)) }
        }
    }

    class NetworkError(override val message: String, override val cause: Throwable? = null) : Exception()

    private fun doGet(url: String, token: String, callback: Result<String>.() -> Unit) {
        val request: Request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .cacheControl(DEFAULT_CACHE_CONTROL)
            .get()
            .build()

        runRequest(request, callback)
    }

    private fun doPost(url: String, postJsonData: String, token: String? = null, callback: Result<String>.() -> Unit) {
        val request: Request = Request.Builder()
            .url(url)
            .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
            .cacheControl(DEFAULT_CACHE_CONTROL)
            .post(postJsonData.toRequestBody(DEFAULT_MEDIA_TYPE))
            .build()

        runRequest(request, callback)
    }

    private fun runRequest(request: Request, callback: Result<String>.() -> Unit) {
        CLIENT.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                MAIN_HANDLER.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NO_CONTENT)) {
                    runCatching<String> { response.body?.bytes()?.decodeToString() ?: "" }
                        .onSuccess { responseString -> MAIN_HANDLER.post { callback(Result.failure(OwnIdIntegrationError(responseString))) } }
                        .onFailure { MAIN_HANDLER.post { callback(Result.failure(NetworkError("Request fail", it))) } }
                } else {
                    runCatching<String?> { response.body?.bytes()?.decodeToString() }
                        .onSuccess { responseString -> MAIN_HANDLER.post { callback(Result.success(responseString ?: "")) } }
                        .onFailure { MAIN_HANDLER.post { callback(Result.failure(NetworkError("Request fail", it))) } }
                }
            }
        })
    }

    private companion object {
        @JvmStatic
        private val DEFAULT_MEDIA_TYPE: MediaType = "application/json".toMediaType()

        @JvmStatic
        private val DEFAULT_CACHE_CONTROL: CacheControl = CacheControl.Builder().noCache().noStore().build()

        @JvmStatic
        private val CLIENT = OkHttpClient.Builder().build()

        @JvmStatic
        private val MAIN_HANDLER: Handler = Handler(Looper.getMainLooper())
    }
}