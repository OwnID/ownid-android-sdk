package com.ownid.demo.gigya

import com.gigya.android.sdk.account.models.GigyaAccount
import com.google.gson.JsonObject

/**
 * Gigya user account class used in this demo.
 *
 * @param data  contains OwnID data
 */
class OwnIdGigyaAccount(var data: JsonObject? = null) : GigyaAccount()