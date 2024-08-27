package com.ownid.sdk.internal.feature.enrollment

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdEnrollmentParams(
    @JvmField internal val ownIdCore: OwnIdCoreImpl,
    @JvmField internal val loginId: String,
    @JvmField internal val fidoOptions: String,
    @JvmField internal val token: String
)