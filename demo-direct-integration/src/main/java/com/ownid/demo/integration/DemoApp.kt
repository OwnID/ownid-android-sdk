package com.ownid.demo.integration

import android.app.Application
import com.ownid.sdk.OwnId

class DemoApp : Application() {

    lateinit var identityPlatform: IdentityPlatform

    override fun onCreate() {
        super.onCreate()

        identityPlatform = IdentityPlatform("...")

        OwnId.createInstanceFromFile(
            context = applicationContext,
            configurationAssetFileName = "ownIdIntegrationSdkConfig.json",
            productName = "DirectIntegration/3.3.0"
        )
    }
}