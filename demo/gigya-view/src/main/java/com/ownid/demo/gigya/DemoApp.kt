package com.ownid.demo.gigya

import android.app.Application
import com.gigya.android.sdk.Gigya
import com.ownid.sdk.OwnId
import com.ownid.sdk.createGigyaInstanceFromFile

class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Gigya.setApplication(this)

        OwnId.createGigyaInstanceFromFile(this)

        // If you use custom account class
        // OwnId.createGigyaInstanceFromFile(this, gigya = Gigya.getInstance(MyAccount::class.java))
    }
}