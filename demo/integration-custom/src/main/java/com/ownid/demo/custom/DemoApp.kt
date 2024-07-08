package com.ownid.demo.custom

import android.app.Application

import com.ownid.sdk.OwnId

class DemoApp : Application() {

    lateinit var identityPlatform: IdentityPlatform

    override fun onCreate() {
        super.onCreate()

        identityPlatform = IdentityPlatform("https://node-mongo.custom.demo.dev.ownid.com/api/auth/") //TODO

        OwnId.createInstanceFromFile(
            context = applicationContext,
            configurationAssetFileName = CustomIntegration.CONFIGURATION_FILE, //TODO
            productName = CustomIntegration.PRODUCT_NAME_VERSION,
            ownIdIntegration = { CustomIntegration(identityPlatform) }
        )
    }
}