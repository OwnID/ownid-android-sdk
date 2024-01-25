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
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdGigya
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.event.LoginData
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.events.Metadata
import com.ownid.sdk.internal.events.Metric
import com.ownid.sdk.internal.flow.OwnIdFlowType
import org.json.JSONObject
import java.util.Locale

/**
 * Class extends [OwnIdCore], holds [Gigya] instance and implements Register/Login flows with Gigya.
 *
 * Recommended to be a single instance per-application per-configuration.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdGigyaImpl<A : GigyaAccount>(
    override val ownIdCore: OwnIdCore,
    private val gigya: Gigya<A>,
) : OwnIdGigya {

    /**
     * Performs OwnID Registration flow and register new user in Gigya. User password will be generated automatically.
     *
     * @param loginId        User email for Gigya account.
     * @param params         [GigyaRegistrationParameters] (optional) Additional parameters for registration.
     * @param ownIdResponse  [OwnIdResponse] from OwnID Register flow.
     * @param callback       [OwnIdCallback] with `null` value of Registration flow result or with [OwnIdException] cause value if Registration flow failed.
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
            callback(Result.failure(OwnIdException("Register: Error creating gigya params", it)))
            return
        }

        val password = ownIdCore.generatePassword(20)

        gigya.register(loginId, password, paramsWithOwnIdData, object : GigyaLoginCallback<A>() {
            override fun onSuccess(account: A?) {
                callback(Result.success(null))
            }

            override fun onError(error: GigyaError?) {
                if (error == null) {
                    callback(Result.failure(OwnIdException("Register.onError: null")))
                } else {
                    if (error.errorCode in listOf(206001, 206002, 206006, 403102, 403101)) {
                        (ownIdCore as OwnIdCoreImpl).eventsService.sendMetric(
                            OwnIdFlowType.REGISTER, Metric.EventType.Track, "User is Registered",
                            Metadata(authType = ownIdResponse.flowInfo.authType),
                            errorMessage = error.localizedMessage, errorCode = error.errorCode.toString()
                        )
                    }
                    callback(Result.failure(GigyaException(error, "Register: [${error.errorCode}] ${error.data}")))
                }
            }
        })
    }

    /**
     * Performs OwnID Login flow and Login new user in Gigya.
     *
     * @param ownIdResponse  [OwnIdResponse] from OwnID Login flow.
     * @param callback       [OwnIdCallback] with `null` value of Login flow result or with [OwnIdException] cause value if Login flow failed.
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
                    throw GigyaException(gigyaError, "Login: [${gigyaError.errorCode}] ${gigyaError.localizedMessage}")
                }

                else -> throw OwnIdException("Unexpected payload data")
            }
        }
            .onSuccess { callback(Result.success(null)) }
            .onFailure {
                OwnIdInternalLogger.logD(this, "login", "Payload data: ${ownIdResponse.payload.data}")

                if (it is GigyaException && it.gigyaError.errorCode in listOf(206001, 206002, 206006, 403102, 403101)) {
                    (ownIdCore as OwnIdCoreImpl).eventsService.sendMetric(
                        OwnIdFlowType.LOGIN, Metric.EventType.Track, "User is Logged in",
                        Metadata(authType = ownIdResponse.flowInfo.authType),
                        errorMessage = it.gigyaError.localizedMessage, errorCode = it.gigyaError.errorCode.toString()
                    )
                }

                if (it is OwnIdException) callback(Result.failure(it))
                else callback(Result.failure(OwnIdException("Login: Error in JSON", it)))
            }
    }
}