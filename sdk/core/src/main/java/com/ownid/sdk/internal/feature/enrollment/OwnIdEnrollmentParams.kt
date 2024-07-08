package com.ownid.sdk.internal.feature.enrollment

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.internal.OwnIdLoginId

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdEnrollmentParams(
    @JvmField internal val ownIdCore: OwnIdCoreImpl,
    @JvmField internal val loginId: OwnIdLoginId,
    @JvmField internal val displayName: String,
    @JvmField internal val token: String
)