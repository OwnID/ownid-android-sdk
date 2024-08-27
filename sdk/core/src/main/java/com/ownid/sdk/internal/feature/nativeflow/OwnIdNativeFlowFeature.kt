package com.ownid.sdk.internal.feature.nativeflow

import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.internal.feature.OwnIdActivity
import com.ownid.sdk.internal.feature.OwnIdFeature

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal interface OwnIdNativeFlowFeature : OwnIdFeature<OwnIdResponse> {

    @InternalOwnIdAPI
    public companion object {
        internal const val KEY_CURRENT_STEP = "com.ownid.sdk.internal.intent.KEY_CURRENT_STEP"

        private const val KEY_FLOW_INTENT = "com.ownid.sdk.internal.intent.KEY_FLOW_INTENT"
        private const val KEY_INSTANCE_NAME = "com.ownid.sdk.internal.intent.KEY_INSTANCE_NAME"
        private const val KEY_FLOW_TYPE = "com.ownid.sdk.internal.intent.KEY_FLOW_TYPE"
        private const val KEY_LOGIN_TYPE = "com.ownid.sdk.internal.intent.KEY_LOGIN_TYPE"
        private const val KEY_LOGIN_ID = "com.ownid.sdk.internal.intent.KEY_LOGIN_ID"

        internal fun createIntent(
            context: Context, instanceName: InstanceName, flowType: OwnIdNativeFlowType, loginType: OwnIdLoginType?, loginId: String
        ): Intent =
            Intent(context, OwnIdActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(KEY_FLOW_INTENT, true)
                .putExtra(KEY_INSTANCE_NAME, instanceName.toString())
                .putExtra(KEY_FLOW_TYPE, flowType)
                .putExtra(KEY_LOGIN_TYPE, loginType)
                .putExtra(KEY_LOGIN_ID, loginId)

        internal fun isThisFeature(intent: Intent): Boolean = intent.getBooleanExtra(KEY_FLOW_INTENT, false)

        @Throws
        @Suppress("DEPRECATION")
        internal fun Intent.toOwnIdFlowData(): OwnIdNativeFlowData {
            val instanceName = InstanceName(getStringExtra(KEY_INSTANCE_NAME)!!)
            val ownIdCore = OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl
            val flowType = getSerializableExtra(KEY_FLOW_TYPE) as OwnIdNativeFlowType
            val loginType = getSerializableExtra(KEY_LOGIN_TYPE) as? OwnIdLoginType
            val loginId = getStringExtra(KEY_LOGIN_ID)!!

            val ownIdNativeFlowLoginId = OwnIdNativeFlowLoginId.fromString(loginId, ownIdCore.configuration)

            return OwnIdNativeFlowData(ownIdCore, flowType, loginType, ownIdNativeFlowLoginId)
        }
    }
}

