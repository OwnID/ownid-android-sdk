package com.ownid.sdk

import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.ownid.sdk.dsl.AuthResult
import com.ownid.sdk.dsl.OwnIdProvidersBuilder
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.toGigyaError
import com.ownid.sdk.internal.toGigyaSession
import com.ownid.sdk.internal.toMap
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Extension function to add Gigya-specific providers to the [OwnIdProvidersBuilder].
 *
 * Sets up the default implementation of session provider, account provider, and password authentication provider for Gigya authentication.
 */
@OptIn(InternalOwnIdAPI::class)
public fun <A : GigyaAccount> OwnIdProvidersBuilder.getGigyaProviders(
    gigya: Gigya<A>
): OwnIdProvidersBuilder = apply {
    session {
        create { loginId: String, rawSession: String, authToken: String, authMethod: AuthMethod? ->
            runCatching {
                val json = JSONObject(rawSession)

                json.toGigyaSession()?.let { session ->
                    gigya.setSession(session)
                    return@create AuthResult.LoggedIn()
                }

                json.toGigyaError()?.let { gigyaError ->
                    OwnIdInternalLogger.logW(this, "GigyaSessionAdapter", "[${gigyaError.errorCode}] ${gigyaError.localizedMessage}")
                    throw GigyaException(gigyaError, "GigyaSessionCreate: [${gigyaError.errorCode}] ${gigyaError.localizedMessage}")
                }

                OwnIdInternalLogger.logW(this, "GigyaSessionCreate", "Unexpected data in '$rawSession'")
                return@create AuthResult.Fail("GigyaSessionCreate: Unexpected data in '$rawSession'")
            }.getOrElse { error ->
                OwnIdInternalLogger.logW(this, "GigyaSessionCreate", error.message, error)
                return@create AuthResult.Fail("GigyaSessionCreate: Error: ${error.message}")
            }
        }
    }

    account {
        register { loginId: String, rawProfile: String, ownIdData: String?, authToken: String? ->
            val paramsWithOwnIdData = runCatching {
                val params = JSONObject(rawProfile).toMap().toMutableMap()
                OwnIdGigya.appendWithOwnIdData(params, ownIdData)
            }.getOrElse {
                OwnIdInternalLogger.logW(this, "GigyaAccountRegister", it.message, it)
                return@register AuthResult.Fail("GigyaAccountRegister: Error: ${it.message}")
            }

            suspendCoroutine { continuation ->
                val password = OwnId.gigya.ownIdCore.generatePassword(9)

                gigya.register(loginId, password, paramsWithOwnIdData, object : GigyaLoginCallback<A>() {
                    override fun onSuccess(account: A) {
                        continuation.resume(
                            when {
                                account.sessionInfo != null -> AuthResult.LoggedIn()
                                else -> AuthResult.Fail("GigyaAccountRegister: sessionInfo == null")
                            }
                        )
                    }

                    override fun onError(error: GigyaError) {
                        continuation.resume(AuthResult.Fail(error.localizedMessage))
                    }
                })
            }
        }
    }

    auth {
        password {
            authenticate { loginId, password ->
                suspendCoroutine { continuation ->
                    gigya.login(loginId, password, object : GigyaLoginCallback<A>() {
                        override fun onSuccess(account: A) {
                            continuation.resume(
                                when {
                                    account.sessionInfo != null -> AuthResult.LoggedIn()
                                    else -> AuthResult.Fail("GigyaPasswordAuthenticate: sessionInfo == null")
                                }
                            )
                        }

                        override fun onError(error: GigyaError) {
                            continuation.resume(AuthResult.Fail(error.localizedMessage))
                        }
                    })
                }
            }
        }
    }
}