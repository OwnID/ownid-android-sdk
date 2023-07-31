package com.ownid.demo.gigya

import android.app.Application
import com.gigya.android.sdk.Gigya
import com.ownid.sdk.OwnId
import com.ownid.sdk.createGigyaInstanceFromJson

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Gigya.setApplication(this)
        Gigya.getInstance().init("3_hOdIVleWrXNvjArcZRwHJLiGA4e6Jrcwq7RfH5nL7ZUHyI_77z43_IQrJYxLbiq_", "us1.gigya.com")

        OwnId.createGigyaInstanceFromJson(this, """{"app_id": "gephu5k2dnff2v", "env": "dev", "enable_logging": true}""")
    }
}