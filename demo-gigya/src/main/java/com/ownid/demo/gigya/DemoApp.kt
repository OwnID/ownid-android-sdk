package com.ownid.demo.gigya

import com.gigya.android.sdk.Gigya
import com.ownid.demo.ui.activity.BaseDemoApp
import com.ownid.sdk.OwnId
import com.ownid.sdk.createGigyaInstance

class DemoApp : BaseDemoApp() {

    override fun onCreate() {
        super.onCreate()

        Gigya.setApplication(this)
        val gigya = Gigya.getInstance(OwnIdGigyaAccount::class.java)

        OwnId.createGigyaInstance(this, gigya)
    }
}