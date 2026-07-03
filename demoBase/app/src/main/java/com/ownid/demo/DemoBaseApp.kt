package com.ownid.demo

import android.app.Application
import com.ownid.sdk.OwnId

class DemoBaseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        DemoIdentityPlatform.init(applicationContext)

        OwnId.initializeFromJson {
            applicationContext = this@DemoBaseApp.applicationContext
            json = BuildConfig.OWNID_CONFIG_JSON
        }

        OwnId.setProviders {
            demoIdentityProviders()
            signInWithGoogleProvider()
        }
    }
}
