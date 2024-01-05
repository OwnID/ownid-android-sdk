package com.ownid.demo.gigya.screens

import android.app.Application
import com.gigya.android.sdk.Gigya
import com.ownid.sdk.OwnId
import com.ownid.sdk.configureGigyaWebBridge
import com.ownid.sdk.createGigyaInstanceFromFile

class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        OwnId.configureGigyaWebBridge()

        Gigya.setApplication(this)

        OwnId.createGigyaInstanceFromFile(this)
    }
}