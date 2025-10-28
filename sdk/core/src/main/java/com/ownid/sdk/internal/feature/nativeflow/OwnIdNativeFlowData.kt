package com.ownid.sdk.internal.feature.nativeflow

import androidx.annotation.RestrictTo
import androidx.core.os.CancellationSignal
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.internal.toBase64UrlSafeNoPadding
import okhttp3.HttpUrl
import kotlin.random.Random

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdNativeFlowData(
    @JvmField internal val ownIdCore: OwnIdCoreImpl,
    @JvmField internal val flowType: OwnIdNativeFlowType,
    @JvmField internal val loginType: OwnIdLoginType?,
    @JvmField internal var loginId: OwnIdNativeFlowLoginId
) {
    @JvmField internal var useLoginId: Boolean = true
    @JvmField internal val canceller = CancellationSignal()
    @JvmField internal val verifier: String = Random.nextBytes(32).toBase64UrlSafeNoPadding()
    @JvmField internal val qr: Boolean = false
    @JvmField internal val passkeyAutofill: Boolean = false

    @JvmField internal var expiration: Long = 1200000L
    internal var context: String = ""
//    internal lateinit var stopUrl: HttpUrl
    internal lateinit var statusFinalUrl: HttpUrl
}