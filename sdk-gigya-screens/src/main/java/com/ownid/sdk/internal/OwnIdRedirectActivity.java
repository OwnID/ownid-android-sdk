package com.ownid.sdk.internal;

import android.app.Activity;
import android.os.Bundle;

/**
 * Activity that receives the redirect Uri sent by the OwnID Web App and close itself.
 * <p>
 * App developers using this library must override the `ownIdRedirectScheme`
 * property in their `build.gradle` to specify the custom scheme that will be used for
 * the OwnID redirect. If you prefer to use https schema, then a custom intent filter should be
 * defined in your application manifest instead.
 * See [Verify Android App Links](https://developer.android.com/training/app-links/verify-site-associations)
 */
public class OwnIdRedirectActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        finish();
        overridePendingTransition(0, 0);
    }
}