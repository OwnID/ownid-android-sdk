package com.ownid.sdk.internal;

import android.app.Activity;
import android.os.Bundle;

/**
 * Activity that receives the redirect Uri sent by the OwnID Web App and close itself.
 */
public class OwnIdRedirectActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        finish();
        overridePendingTransition(0, 0);
    }
}