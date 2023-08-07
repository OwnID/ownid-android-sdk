package com.ownid.sdk.internal

import androidx.core.os.LocaleListCompat
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.api.GigyaApiResponse
import com.gigya.android.sdk.network.GigyaError
import com.gigya.android.sdk.session.SessionInfo
import com.ownid.sdk.Configuration
import com.ownid.sdk.GigyaRegistrationParameters
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.OwnIdGigya
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.logV
import org.json.JSONObject
import java.util.*

/**
 * Class extends [OwnIdCore], holds [Gigya] instance and implements Register/Login flows with Gigya.
 *
 * Recommended to be a single instance per-application per-configuration.
 */
@InternalOwnIdAPI
internal class OwnIdGigyaImpl<A : GigyaAccount>(
    instanceName: InstanceName,
    configuration: Configuration,
    private val gigya: Gigya<A>,
) : OwnIdCoreImpl(instanceName, configuration), OwnIdGigya {

    /**
     * Performs OwnID Registration flow and register new user in Gigya.
     * User password will be generated automatically.
     *
     * @param email          User email for Gigya account.
     * @param params         [GigyaRegistrationParameters] (optional) Additional parameters for registration.
     * @param ownIdResponse  [OwnIdResponse] from [OwnIdCore.createRegisterIntent] flow.
     * @param callback       [OwnIdCallback] with [Unit] value of Registration flow result or with [OwnIdException]
     * cause value if Registration flow failed.
     */
    override fun register(
        email: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>
    ) {
        logV("register", ownIdResponse)

        val paramsWithOwnIdData = runCatching {
            val gigyaParams = (params as? GigyaRegistrationParameters)?.params ?: emptyMap()

            val dataJson = if (gigyaParams.containsKey("data").not()) JSONObject()
            else JSONObject(java.lang.String.valueOf(gigyaParams["data"]))

            val ownIdDataFieldName = JSONObject(ownIdResponse.payload.metadata).getString("dataField")
            dataJson.put(ownIdDataFieldName, JSONObject(ownIdResponse.payload.ownIdData))

            val paramsWithOwnIdData = gigyaParams.toMutableMap().apply { put("data", dataJson.toString()) }

            val profileJson = if (gigyaParams.containsKey("profile").not()) JSONObject()
            else JSONObject(java.lang.String.valueOf(gigyaParams["profile"]))
            if (profileJson.has("locale")) return@runCatching paramsWithOwnIdData

            val localeList = LocaleListCompat.forLanguageTags(ownIdResponse.languageTags)
            if (localeList.isEmpty) return@runCatching paramsWithOwnIdData

            val language = Locale(localeList.get(0).language).toLanguageTag()
            if (language.isBlank() || language == "und") return@runCatching paramsWithOwnIdData

            profileJson.put("locale", language)

            paramsWithOwnIdData.apply { put("profile", profileJson.toString()) }
        }.getOrElse {
            callback(Result.failure(OwnIdException("Register: Error creating gigya params", it)))
            return
        }

        val password = generatePassword(16)

        gigya.register(email, password, paramsWithOwnIdData, object : GigyaLoginCallback<A>() {
            override fun onSuccess(account: A?) {
                callback(Result.success(Unit))
            }

            override fun onError(error: GigyaError?) {
                val cause = error?.let { GigyaException(it, "Register: ${error.localizedMessage}") }
                    ?: OwnIdException("Register.onError: null")

                callback(Result.failure(cause))
            }
        })
    }

    override fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>) {
        logV("login", ownIdResponse)

        runCatching {
            val dataJson = JSONObject(ownIdResponse.payload.ownIdData)

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
                    throw GigyaException(gigyaError, "Login: [${gigyaError.errorCode}] ${gigyaError.localizedMessage}")
                }

                else -> throw OwnIdException("Login: Unexpected data: ${ownIdResponse.payload.ownIdData}")
            }
        }
            .onSuccess { callback(Result.success(Unit)) }
            .onFailure {
                if (it is OwnIdException) callback(Result.failure(it))
                else callback(Result.failure(OwnIdException("Login: Error in JSON", it)))
            }
    }
}