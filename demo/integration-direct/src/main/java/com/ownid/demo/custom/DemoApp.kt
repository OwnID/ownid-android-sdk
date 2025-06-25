package com.ownid.demo.custom

import android.app.Application
import com.ownid.sdk.AuthMethod
import com.ownid.sdk.OwnId
import com.ownid.sdk.dsl.AuthResult
import com.ownid.sdk.dsl.providers
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DemoApp : Application() {

    lateinit var identityPlatform: IdentityPlatform

    override fun onCreate() {
        super.onCreate()

        identityPlatform = IdentityPlatform("...")

        OwnId.createInstanceFromJson(
            context = applicationContext,
            configurationJson = """{"appId": "..."}""",
            productName = "DirectIntegration/3.8.1"
        )

        OwnId.providers {
            session {
                create { loginId: String, session: String, authToken: String, authMethod: AuthMethod? ->
                    val token = JSONObject(session).getString("token")
                    suspendCoroutine { continuation ->
                        identityPlatform.getProfile(token, authToken) {
                            onFailure { error -> continuation.resume(AuthResult.Fail(error.message)) }
                            onSuccess { user -> continuation.resume(AuthResult.LoggedIn()) }
                        }
                    }
                }
            }

            account {
                register { loginId: String, profile: String, ownIdData: String?, authToken: String? ->
                    val name = JSONObject(profile).getString("firstName")
                    suspendCoroutine { continuation ->
                        identityPlatform.registerWithOwnId(name, loginId, ownIdData) {
                            onFailure { continuation.resume(AuthResult.Fail(it.message)) }
                            onSuccess { session ->
                                val token = JSONObject(session).getString("token")
                                identityPlatform.getProfile(token, null) {
                                    onFailure { continuation.resume(AuthResult.Fail(it.message)) }
                                    onSuccess { user -> continuation.resume(AuthResult.LoggedIn()) }
                                }
                            }
                        }
                    }
                }
            }

            auth {
                password {
                    authenticate { loginId: String, password: String ->
                        suspendCoroutine { continuation ->
                            identityPlatform.login(loginId, password) {
                                onFailure { error -> continuation.resume(AuthResult.Fail(error.message)) }
                                onSuccess { user -> continuation.resume(AuthResult.LoggedIn()) }
                            }
                        }
                    }
                }
            }
        }
    }
}