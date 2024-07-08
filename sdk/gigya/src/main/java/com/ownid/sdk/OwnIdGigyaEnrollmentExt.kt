@file:JvmName("OwnIdGigyaEnrollmentExt")

package com.ownid.sdk

import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.account.models.GigyaAccount

/**
 * Provides a default login ID provider function for credential enrollment with OwnID and Gigya integration.
 *
 * @param gigya  (optional) An instance of [Gigya]. Defaults to the Gigya instance returned by `Gigya.getInstance()`.
 * @return       A function that takes an OwnIdCallback as a parameter and provides the user's login ID.
 */
public fun OwnIdGigya.Companion.defaultLoginIdProvider(
    gigya: Gigya<out GigyaAccount> = Gigya.getInstance()
): (OwnIdCallback<String>) -> Unit = OwnIdGigyaEnrollment.defaultLoginIdProvider(gigya)

/**
 * Provides a default authentication token provider function for credential enrollment with OwnID and Gigya integration.
 *
 * @param gigya  (optional) An instance of [Gigya]. Defaults to the Gigya instance returned by `Gigya.getInstance()`.
 * @return       A function that takes an OwnIdCallback as a parameter and provides the user's authentication token.
 */
public fun OwnIdGigya.Companion.defaultAuthTokenProvider(
    gigya: Gigya<out GigyaAccount> = Gigya.getInstance()
): (OwnIdCallback<String>) -> Unit = OwnIdGigyaEnrollment.defaultAuthTokenProvider(gigya)