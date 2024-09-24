package com.ownid.sdk.internal.feature

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.enrollment.OwnIdEnrollmentFeature
import com.ownid.sdk.internal.feature.enrollment.OwnIdEnrollmentFeatureImpl
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowFeature
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowFeatureImpl
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowFeature
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowFeatureWebView

@InternalOwnIdAPI
internal interface OwnIdFeature<T> {

    @InternalOwnIdAPI
    public companion object {
        internal fun fromIntent(intent: Intent): OwnIdFeature<*>? = when {
            OwnIdNativeFlowFeature.isThisFeature(intent) -> OwnIdNativeFlowFeatureImpl()
            OwnIdEnrollmentFeature.isThisFeature(intent) -> OwnIdEnrollmentFeatureImpl()
            OwnIdFlowFeature.isThisFeature(intent) -> OwnIdFlowFeatureWebView()
            else -> null
        }
    }

    public fun onCreate(activity: AppCompatActivity, savedInstanceState: Bundle?)
    public fun onSaveInstanceState(outState: Bundle) = Unit
    public fun sendResult(activity: AppCompatActivity, result: Result<T>) = Unit
    public fun onDestroy(activity: AppCompatActivity) = Unit
}