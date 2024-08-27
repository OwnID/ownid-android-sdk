package com.ownid.demo.custom

import android.app.Application
import com.ownid.sdk.OwnId

class DemoApp : Application() {

    lateinit var identityPlatform: IdentityPlatform

    override fun onCreate() {
        super.onCreate()

        identityPlatform = IdentityPlatform("...")

        OwnId.createInstanceFromJson(
            context = applicationContext,
            configurationJson = """{"appId": "..."}""",
            productName = "DirectIntegration/3.4.0"
        )
    }
}