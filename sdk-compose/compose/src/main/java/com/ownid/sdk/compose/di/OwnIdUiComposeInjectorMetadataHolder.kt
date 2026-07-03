package com.ownid.sdk.compose.di

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.ownid.sdk.model.InternalOwnIdAPI

/**
 * Internal service carrying optional UI module metadata.
 *
 * The service declaration carries the SDK module metadata that points Core discovery to [OwnIdUiComposeInjector].
 * Host apps do not bind to this service, start it, or use it as an integration point.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class OwnIdUiComposeInjectorMetadataHolder : Service() {
    private val binder = LocalBinder()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal inner class LocalBinder : Binder() {
        fun getService(): OwnIdUiComposeInjectorMetadataHolder = this@OwnIdUiComposeInjectorMetadataHolder
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
