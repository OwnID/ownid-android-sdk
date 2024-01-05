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
    public override fun toMap(): Map<String, Any?> = mapOf(
        "className" to javaClass.simpleName,
        "message" to message,
        "GigyaError.data" to gigyaError.data,
        "GigyaError.errorCode" to gigyaError.errorCode,
        "GigyaError.localizedMessage" to gigyaError.localizedMessage,
        "GigyaError.callId" to gigyaError.callId
    )
}