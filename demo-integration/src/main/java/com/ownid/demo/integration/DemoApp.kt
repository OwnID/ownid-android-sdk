package com.ownid.demo.integration

import android.app.Application
import com.ownid.sdk.OwnId

class DemoApp : Application() {

    lateinit var identityPlatform: IdentityPlatform

    override fun onCreate() {
        super.onCreate()

        identityPlatform = IdentityPlatform("...")

        OwnId.createInstanceFromFile(
            applicationContext, OwnIdIntegration.CONFIGURATION_FILE, OwnIdIntegration.PRODUCT_NAME_VERSION, OwnIdIntegration.INSTANCE_NAME
        ) { ownIdCore ->
            OwnIdIntegration(ownIdCore, identityPlatform)
        }
    }
}