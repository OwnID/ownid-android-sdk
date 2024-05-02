@file:JvmName("OwnIdGigyaEnrollmentExt")

package com.ownid.sdk

import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.api.GigyaApiResponse
import com.gigya.android.sdk.network.GigyaError
import com.ownid.sdk.exception.GigyaException

/**
 * Provides a default login ID provider function for credential enrollment with OwnID and Gigya integration.
 *
 * @param gigya  (optional) An instance of [Gigya]. Defaults to the Gigya instance returned by `Gigya.getInstance()`.
 * @return       A function that takes an OwnIdCallback as a parameter and provides the user's login ID.
 */
public fun OwnIdGigya.Companion.defaultLoginIdProvider(
    gigya: Gigya<out GigyaAccount> = Gigya.getInstance()
): (OwnIdCallback<String>) -> Unit = { callback ->
    gigyaApiWrapper(gigya, "accounts.getAccountInfo", callback) { response ->
        response.getField("profile.email", String::class.java)!!
    }
}

/**
 * Provides a default authentication token provider function for credential enrollment with OwnID and Gigya integration.
 *
 * @param gigya  (optional) An instance of [Gigya]. Defaults to the Gigya instance returned by `Gigya.getInstance()`.
 * @return       A function that takes an OwnIdCallback as a parameter and provides the user's authentication token.
 */
public fun OwnIdGigya.Companion.defaultAuthTokenProvider(
    gigya: Gigya<out GigyaAccount> = Gigya.getInstance()
): (OwnIdCallback<String>) -> Unit = { callback ->
    gigyaApiWrapper(gigya, "accounts.getJWT", callback) { response ->
        response.getField("id_token", String::class.java)!!
    }
}

@OptIn(InternalOwnIdAPI::class)
private fun gigyaApiWrapper(
    gigya: Gigya<out GigyaAccount>,
    api: String,
    callback: OwnIdCallback<String>,
    onResponse: (GigyaApiResponse) -> String
) {
    gigya.send(api, null, object : GigyaCallback<GigyaApiResponse>() {
        override fun onSuccess(response: GigyaApiResponse) {
            if (response.errorCode == 0) {
                callback.invoke(runCatching { onResponse.invoke(response) })
            } else {
                onError(GigyaError.fromResponse(response))
            }
        }

        override fun onError(gigyaError: GigyaError) {
            callback.invoke(Result.failure(GigyaException(gigyaError, gigyaError.localizedMessage)))
        }
    })
}