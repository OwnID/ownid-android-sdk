package com.ownid.sdk.internal.feature.webflow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import androidx.annotation.RestrictTo
import com.ownid.sdk.FlowResult
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.SessionAdapter
import com.ownid.sdk.internal.feature.OwnIdFeature

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal interface OwnIdFlowFeature : OwnIdFeature<FlowResult<*>> {

    @InternalOwnIdAPI
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        internal fun createIntent(context: Context, instanceName: InstanceName, resultReceiver: ResultReceiver): Intent =
            OwnIdFlowFeatureImpl.createIntent(context, instanceName, resultReceiver)

        internal fun isThisFeature(intent: Intent): Boolean =
            OwnIdFlowFeatureImpl.isThisFeature(intent)

        internal fun sendCloseRequest(context: Context) =
            OwnIdFlowFeatureImpl.sendCloseRequest(context)

        internal fun <T> decodeResult(resultCode: Int, resultData: Bundle, adapter: SessionAdapter<T>): FlowResult<T> =
            OwnIdFlowFeatureImpl.decodeResult(resultCode, resultData, adapter)
    }

    @InternalOwnIdAPI
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public class Result(public val type: Type, public val value: String? = null) {
        public enum class Type { ON_ACCOUNT_NOT_FOUND, ON_LOGIN, ON_CLOSE, ON_ERROR }
    }
}