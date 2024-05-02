package com.ownid.sdk.internal.feature

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.enrollment.OwnIdEnrollmentFeature
import com.ownid.sdk.internal.feature.enrollment.OwnIdEnrollmentFeatureImpl
import com.ownid.sdk.internal.feature.flow.OwnIdFlowFeature
import com.ownid.sdk.internal.feature.flow.OwnIdFlowFeatureImpl

@InternalOwnIdAPI
internal interface OwnIdFeature<T> {

    @InternalOwnIdAPI
    public companion object {
        internal fun fromIntent(intent: Intent): OwnIdFeature<*>? = when {
            OwnIdFlowFeature.isThisFeature(intent) -> OwnIdFlowFeatureImpl()
            OwnIdEnrollmentFeature.isThisFeature(intent) -> OwnIdEnrollmentFeatureImpl()
            else -> null
        }
    }

    public fun onCreate(activity: AppCompatActivity, savedInstanceState: Bundle?)
    public fun onSaveInstanceState(outState: Bundle)
    public fun sendResult(activity: AppCompatActivity, result: Result<T>)
}