package com.ownid.sdk.exception

import com.gigya.android.sdk.network.GigyaError
import com.ownid.sdk.InternalOwnIdAPI

/**
 * Class wraps [GigyaError] to [GigyaException].
 *
 * @property gigyaError Holds [GigyaError] if available.
 * @property message    Text message describing reason for exception.
 */
public class GigyaException @InternalOwnIdAPI constructor(public val gigyaError: GigyaError, message: String) :
    OwnIdIntegrationError(message) {

    @InternalOwnIdAPI
    public override fun toMap(): Map<String, Any?> = super.toMap().plus(
        "gigyaError" to mapOf(
            "data" to gigyaError.data,
            "errorCode" to gigyaError.errorCode,
            "localizedMessage" to gigyaError.localizedMessage,
            "callId" to gigyaError.callId
        )
    )
}