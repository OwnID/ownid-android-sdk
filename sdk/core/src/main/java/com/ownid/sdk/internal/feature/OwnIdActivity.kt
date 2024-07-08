package com.ownid.sdk.internal.feature

import android.content.Intent
import android.os.Bundle
import androidx.annotation.RestrictTo
import androidx.appcompat.app.AppCompatActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException


@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdActivity : AppCompatActivity() {

    internal companion object {
        internal const val KEY_RESULT = "com.ownid.sdk.internal.intent.KEY_RESULT"
        internal const val KEY_RESULT_UI_ERROR = "com.ownid.sdk.internal.result.KEY_RESULT_UI_ERROR"
    }

    private lateinit var feature: OwnIdFeature<*>

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        overridePendingTransition(0, 0)

        feature = OwnIdFeature.fromIntent(intent) ?: run {
            setResult(RESULT_OK, Intent().putExtra(KEY_RESULT, Result.failure<Any>(OwnIdException("Unknown feature intent"))))
            finish()
            overridePendingTransition(0, 0)
            return
        }

        supportFragmentManager.setFragmentResultListener(KEY_RESULT_UI_ERROR, this) { _, bundle ->
            val cause = bundle.getSerializable(KEY_RESULT_UI_ERROR) as? Throwable
            val exception = OwnIdException("Error creating UI: ${cause?.message}", cause)
            feature.sendResult(this@OwnIdActivity, Result.failure(exception))
        }

        feature.onCreate(this@OwnIdActivity, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::feature.isInitialized) feature.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
}