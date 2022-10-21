@file:JvmName("LoggerExt")
@file:InternalOwnIdAPI

package com.ownid.sdk

import com.ownid.sdk.internal.OwnIdRequest
import com.ownid.sdk.internal.OwnIdResponse

@JvmSynthetic
@InternalOwnIdAPI
public fun Any.logV(message: String, ownIdCore: OwnIdCore? = null) {
    val ownId = ownIdCore ?: if (this is OwnIdCore) this else null
    val className = className(ownId)
    OwnIdLogger.v(className, message)
    ownId?.logService?.v(className, message)
}

@JvmSynthetic
@InternalOwnIdAPI
internal fun Any.logV(message: String, ownIdRequest: OwnIdRequest) {
    val ownId = if (this is OwnIdCore) this else ownIdRequest.ownIdCore
    val className = className(ownId)
    OwnIdLogger.v(className, message)
    ownId.logService.v(className, message, ownIdRequest.context)
}

@JvmSynthetic
@InternalOwnIdAPI
public fun Any.logV(message: String, ownIdResponse: OwnIdResponse) {
    val ownId = if (this is OwnIdCore) this else null
    val className = className(ownId)
    OwnIdLogger.v(className, message)
    ownId?.logService?.v(className, message, ownIdResponse.context)
}

@JvmSynthetic
@InternalOwnIdAPI
public fun Any.logD(message: String, ownIdCore: OwnIdCore? = null) {
    val ownId = ownIdCore ?: if (this is OwnIdCore) this else null
    val className = className(ownId)
    OwnIdLogger.d(className, message)
    ownId?.logService?.d(className, message)
}

@JvmSynthetic
@InternalOwnIdAPI
internal fun Any.logD(message: String, ownIdRequest: OwnIdRequest) {
    val ownId = if (this is OwnIdCore) this else ownIdRequest.ownIdCore
    val className = className(ownId)
    OwnIdLogger.d(className, message)
    ownId.logService.d(className, message, ownIdRequest.context)
}

@JvmSynthetic
@InternalOwnIdAPI
internal fun Any.logD(message: String, ownIdResponse: OwnIdResponse) {
    val ownId = if (this is OwnIdCore) this else null
    val className = className(ownId)
    OwnIdLogger.d(className, message)
    ownId?.logService?.d(className, message, ownIdResponse.context)
}

@JvmSynthetic
@InternalOwnIdAPI
internal fun Any.logI(message: String, ownIdCore: OwnIdCore? = null) {
    val ownId = ownIdCore ?: if (this is OwnIdCore) this else null
    val className = className(ownId)
    OwnIdLogger.i(className, message)
    ownId?.logService?.i(className, message)
}

@JvmSynthetic
@InternalOwnIdAPI
internal fun Any.logW(message: String, ownIdCore: OwnIdCore? = null) {
    val ownId = ownIdCore ?: if (this is OwnIdCore) this else null
    val className = className(ownId)
    OwnIdLogger.w(className, message)
    ownId?.logService?.w(className, message)
}

@JvmSynthetic
@InternalOwnIdAPI
public fun Any.logW(message: String, ownIdResponse: OwnIdResponse) {
    val ownId = if (this is OwnIdCore) this else null
    val className = className(ownId)
    OwnIdLogger.w(className, message)
    ownId?.logService?.w(className, message, ownIdResponse.context)
}

@JvmSynthetic
@InternalOwnIdAPI
public fun Any.logE(message: String, cause: Throwable, ownIdCore: OwnIdCore? = null) {
    val ownId = ownIdCore ?: if (this is OwnIdCore) this else null
    val className = className(ownId)
    OwnIdLogger.e(className, message, cause)
    ownId?.logService?.e(className, message, cause)
}

private fun Any.className(ownIdCore: OwnIdCore? = null): String =
    "${this.javaClass.simpleName}#${this.hashCode()}@${Thread.currentThread().name}" +
            (ownIdCore?.let { ":${it.instanceName.value}" } ?: "")