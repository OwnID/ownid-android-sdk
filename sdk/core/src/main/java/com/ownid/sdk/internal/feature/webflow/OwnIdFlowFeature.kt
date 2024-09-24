package com.ownid.sdk.internal.feature.webflow

import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.OwnIdFeature

@InternalOwnIdAPI
internal interface OwnIdFlowFeature : OwnIdFeature<Nothing> {

    @InternalOwnIdAPI
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        internal fun createIntent(context: Context): Intent =
            OwnIdFlowFeatureWebView.createIntent(context)

        internal fun isThisFeature(intent: Intent): Boolean =
            OwnIdFlowFeatureWebView.isThisFeature(intent)
    }
}