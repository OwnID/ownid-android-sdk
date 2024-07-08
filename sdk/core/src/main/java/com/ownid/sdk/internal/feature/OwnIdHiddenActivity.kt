package com.ownid.sdk.internal.feature

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.RestrictTo
import androidx.appcompat.app.AppCompatActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.component.OwnIdInternalLogger

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdHiddenActivity : AppCompatActivity() {

    internal companion object {
        internal fun createIntent(context: Context): Intent =
            Intent(context, OwnIdHiddenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    private lateinit var feature: OwnIdFeature<*>

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        feature = OwnIdFeature.fromIntent(intent) ?: run {
            OwnIdInternalLogger.logE(this, "onCreate", "Unknown feature intent")
            finish()
            overridePendingTransition(0, 0)
            return
        }

        feature.onCreate(this@OwnIdHiddenActivity, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::feature.isInitialized) feature.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::feature.isInitialized) feature.onDestroy(this)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        OwnIdInternalLogger.logD(this, "onConfigurationChanged", "Invoked")
        super.onConfigurationChanged(newConfig)
    }
}