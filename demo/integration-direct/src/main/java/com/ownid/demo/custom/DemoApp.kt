package com.ownid.demo.custom

import android.app.Application
import com.ownid.sdk.OwnId

class DemoApp : Application() {

    lateinit var identityPlatform: IdentityPlatform

    override fun onCreate() {
        super.onCreate()

        identityPlatform = IdentityPlatform("https://node-mongo.custom.demo.dev.ownid.com/api/auth/") //TODO

        OwnId.createInstanceFromJson(
            context = applicationContext,
            configurationJson = """{"appId": "d1yk6gcngrc0og", "env": "dev", "enableLogging": true}""", //TODO
            productName = "DirectIntegration/3.4.0"
        )
    }
}