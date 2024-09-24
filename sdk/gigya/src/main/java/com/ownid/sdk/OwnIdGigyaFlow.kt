package com.ownid.sdk

import com.gigya.android.sdk.session.SessionInfo

/**
 * A [TypeAdapter] implementation for transforming Gigya session data into a [SessionInfo] object.
 */
//@OptIn(InternalOwnIdAPI::class)
//public object DefaultGigyaSessionAdapter : TypeAdapter<SessionInfo> {
//    override fun deserialize(rawData: String): SessionInfo =
//        runCatching {
//            JSONObject(rawData).apply {
//                toGigyaSession()?.let { session -> return@runCatching session }
//                toGigyaError()?.let { gigyaError ->
//                    throw GigyaException(gigyaError, "[${gigyaError.errorCode}] ${gigyaError.localizedMessage}")
//                }
//            }
//            throw OwnIdException("Unexpected data in 'session'")
//        }.recoverCatching {
//            val error = OwnIdException.map("Gigya type adapter error: ${it.message}", it)
//            OwnIdInternalLogger.logW(this, "GigyaSessionAdapter", error.message, error)
//            throw error
//        }.getOrThrow()
//}