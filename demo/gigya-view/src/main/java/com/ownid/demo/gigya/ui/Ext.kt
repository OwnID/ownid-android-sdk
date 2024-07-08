package com.ownid.demo.gigya.ui

import com.gigya.android.sdk.network.GigyaError
import org.json.JSONObject

fun GigyaError.toUserMessage(): String =
    (JSONObject(data).optJSONArray("validationErrors")?.getJSONObject(0)?.optString("message") ?: "")
        .ifBlank { localizedMessage }
