package com.ownid.sdk.internal.feature.enrollment

import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.OwnIdLoginId
import com.ownid.sdk.internal.feature.OwnIdFeature

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal interface OwnIdEnrollmentFeature : OwnIdFeature<String> {

    @InternalOwnIdAPI
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        internal fun createIntent(
            context: Context, instanceName: InstanceName, ownIdLoginId: OwnIdLoginId, displayName: String, token: String
        ): Intent =
            OwnIdEnrollmentFeatureImpl.createIntent(context, instanceName, ownIdLoginId, displayName, token)

        internal fun isThisFeature(intent: Intent): Boolean =
            OwnIdEnrollmentFeatureImpl.isThisFeature(intent)
    }
}