package com.ownid.demo.gigya

import android.app.Application
import com.gigya.android.sdk.Gigya
import com.ownid.sdk.OwnId
import com.ownid.sdk.createGigyaInstance

class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Gigya.setApplication(this)

        OwnId.createGigyaInstance(this)

        // If you use custom account class
        // OwnId.createGigyaInstance(this, gigya = Gigya.getInstance(MyAccount::class.java))
    }
}