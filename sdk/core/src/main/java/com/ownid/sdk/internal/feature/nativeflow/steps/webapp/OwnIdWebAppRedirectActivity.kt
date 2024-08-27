package com.ownid.sdk.internal.feature.nativeflow.steps.webapp

import android.app.Activity
import android.os.Bundle
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.component.OwnIdInternalLogger

/**
 * Activity that receives the redirect Uri sent by the OwnID Web Application. It forwards the data
 * received as part of this redirect to [OwnIdWebAppActivity], which destroys the browser tab before returning the result.
 *
 * If redirect Uri contains parameter `redirect=false`, then [OwnIdWebAppActivity] will not be launched.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdWebAppRedirectActivity : Activity() {

    @Suppress("DEPRECATION")
    public override fun onCreate(savedInstanceBundle: Bundle?) {
        super.onCreate(savedInstanceBundle)
        overridePendingTransition(0, 0)

        val data = intent.data
        OwnIdInternalLogger.logD(this, "onCreate", "data: $data")

        if (data?.getQueryParameter("redirect") != "false") {
            startActivity(OwnIdWebAppActivity.createRedirectIntent(this, data))
        }

        finish()
        overridePendingTransition(0, 0)
    }
}