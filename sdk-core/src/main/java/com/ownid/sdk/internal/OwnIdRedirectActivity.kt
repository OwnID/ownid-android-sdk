package com.ownid.sdk.internal

import android.app.Activity
import android.os.Bundle
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.logD

/**
 * Activity that receives the redirect Uri sent by the OwnID Web App. It forwards the data
 * received as part of this redirect to [OwnIdActivity], which
 * destroys the browser tab before returning the result.
 *
 * If redirect Uri contains parameter `redirect=false`, then [OwnIdActivity] will not be launched.
 *
 * App developers using this library must override the `ownIdRedirectScheme`
 * property in their `build.gradle` to specify the custom scheme that will be used for
 * the OwnID redirect. If you prefer to use https schema, then a custom intent filter should be
 * defined in your application manifest instead.
 * See [Verify Android App Links](https://developer.android.com/training/app-links/verify-site-associations)
 */
@InternalOwnIdAPI
public class OwnIdRedirectActivity : Activity() {
    private companion object {
        private const val KEY_REQUEST_REDIRECT = "redirect"
    }

    public override fun onCreate(savedInstanceBundle: Bundle?) {
        super.onCreate(savedInstanceBundle)

        val data = intent.data

        val instanceName = InstanceName(
            runCatching { data?.getQueryParameter(OwnIdRequest.KEY_REQUEST_INSTANCE_NAME) }.getOrNull() ?: ""
        )
        val ownIdInstance = OwnId.getInstanceOrNull(instanceName) as? OwnIdCore

        logD("onCreate.data: $data", ownIdInstance)

        if (data?.getQueryParameter(KEY_REQUEST_REDIRECT) != "false") {
            startActivity(OwnIdActivity.createRedirectIntent(this, data))
        }

        finish()
        overridePendingTransition(0, 0)
    }
}