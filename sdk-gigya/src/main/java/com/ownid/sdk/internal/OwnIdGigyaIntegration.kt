package com.ownid.sdk.internal

import androidx.annotation.RestrictTo
import androidx.core.os.LocaleListCompat
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.api.GigyaApiResponse
import com.gigya.android.sdk.network.GigyaError
import com.gigya.android.sdk.session.SessionInfo
import com.ownid.sdk.GigyaRegistrationParameters
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.event.LoginData
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.exception.OwnIdException
import org.json.JSONObject
import java.util.Locale

/**
 * OwnID SDK component that contains integration functionality: Registration and Login using Gigya identity platform.
 *
 * Used internally by OwnID SDK and not expected to be used outside OwnID SDK.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdGigyaIntegration<A : GigyaAccount>(
    private val ownIdCore: OwnIdCore,
    private val gigya: Gigya<A>,
) : OwnIdIntegration {

    /**
     * Performs OwnID Registration process and register new user in Gigya. User password will be generated automatically.
     *
     * @param loginId        User email for Gigya account.
     * @param params         [GigyaRegistrationParameters] (optional) Additional parameters for registration.
     * @param ownIdResponse  [OwnIdResponse] from OwnID Register flow.
     * @param callback       [OwnIdCallback] with `null` value of Registration process result or with [OwnIdException] cause value if Registration process failed.
     */
    override fun register(loginId: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<LoginData?>) {
        OwnIdInternalLogger.logD(this, "register", "Invoked")

        val paramsWithOwnIdData = runCatching {
            val gigyaParams = (params as? GigyaRegistrationParameters)?.params ?: emptyMap()

            val dataJson = if (gigyaParams.containsKey("data").not()) JSONObject()
            else JSONObject(java.lang.String.valueOf(gigyaParams["data"]))

            val ownIdDataFieldName = JSONObject(ownIdResponse.payload.metadata).getString("dataField")
            dataJson.put(ownIdDataFieldName, JSONObject(ownIdResponse.payload.data))

            val paramsWithOwnIdData = gigyaParams.toMutableMap().apply { put("data", dataJson.toString()) }

            val profileJson = if (gigyaParams.containsKey("profile").not()) JSONObject()
            else JSONObject(java.lang.String.valueOf(gigyaParams["profile"]))
            if (profileJson.has("locale")) return@runCatching paramsWithOwnIdData

            val localeList = LocaleListCompat.forLanguageTags(ownIdResponse.languageTag)
            if (localeList.isEmpty) return@runCatching paramsWithOwnIdData

            val language = Locale(localeList.get(0)?.language ?: Locale.ENGLISH.language).toLanguageTag()
            if (language.isBlank() || language == "und") return@runCatching paramsWithOwnIdData

            profileJson.put("locale", language)

            paramsWithOwnIdData.apply { put("profile", profileJson.toString()) }
        }.getOrElse {
            OwnIdInternalLogger.logE(this, "register", "Error creating gigya registration params", it)
            callback(Result.failure(OwnIdException("Error creating gigya registration params", it)))
            return
        }

        val password = ownIdCore.generatePassword(20)

        gigya.register(loginId, password, paramsWithOwnIdData, object : GigyaLoginCallback<A>() {
            override fun onSuccess(account: A?) {
                callback(Result.success(null))
            }

            override fun onError(error: GigyaError) {
                callback(Result.failure(GigyaException(error, "[${error.errorCode}] ${error.localizedMessage}")))
            }
        })
    }

    /**
     * Performs OwnID Login process and Login user in Gigya.
     *
     * @param ownIdResponse  [OwnIdResponse] from OwnID Login flow.
     * @param callback       [OwnIdCallback] with `null` value of Login process result or with [OwnIdException] cause value if Login process failed.
     */
    override fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<LoginData?>) {
        OwnIdInternalLogger.logD(this, "login", "Invoked")

        runCatching {
            val dataJson = JSONObject(ownIdResponse.payload.data)

            when {
                dataJson.has("sessionInfo") -> {
                    val payloadJson = dataJson.getJSONObject("sessionInfo")

                    val sessionSecret = payloadJson.getString("sessionSecret")
                    val sessionToken = payloadJson.getString("sessionToken")

                    val expiresInValue = payloadJson.optLong("expires_in")
                    val expirationTimeValue = payloadJson.optLong("expirationTime")
                    val expirationTime = when {
                        expiresInValue > 0L -> expiresInValue
                        expirationTimeValue > 0L -> expirationTimeValue
                        else -> 0L
                    }
                    gigya.setSession(SessionInfo(sessionSecret, sessionToken, expirationTime))
                }

                dataJson.has("errorJson") -> {
                    val errorJsonObject = JSONObject(dataJson.getString("errorJson"))
                    val gigyaError = GigyaError.fromResponse(GigyaApiResponse(errorJsonObject.toString()))
                    throw GigyaException(gigyaError, "[${gigyaError.errorCode}] ${gigyaError.localizedMessage}")
                }

                else -> {
                    OwnIdInternalLogger.logE(this, "login", "Unexpected data", OwnIdException("Unexpected data: ${ownIdResponse.payload.data}"))
                    throw OwnIdException("Unexpected data")
                }
            }
        }
            .onSuccess { callback(Result.success(null)) }
            .onFailure {
                if (it is OwnIdException) callback(Result.failure(it))
                else {
                    OwnIdInternalLogger.logE(this, "login", "Error in JSON", it)
                    callback(Result.failure(OwnIdException("Error in JSON", it)))
                }
            }
    }
}