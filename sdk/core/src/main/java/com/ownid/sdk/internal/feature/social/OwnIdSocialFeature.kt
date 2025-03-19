package com.ownid.sdk.internal.feature.social

import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.OwnIdFeature

@InternalOwnIdAPI
internal interface OwnIdSocialFeature : OwnIdFeature<Pair<String, String>> {

    public enum class Provider { GOOGLE }
    public enum class OauthResponseType { CODE, ID_TOKEN }

    public class Challenge(
        public val challengeId: String,
        public val clientId: String,
        public val challengeUrl: String? = null
    )

    @InternalOwnIdAPI
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        internal fun createIntent(context: Context, challengeId: String, clientId: String): Intent =
            OwnIdSocialFeatureImpl.createIntent(context, challengeId, clientId)

        internal fun isThisFeature(intent: Intent): Boolean =
            OwnIdSocialFeatureImpl.isThisFeature(intent)
    }
}