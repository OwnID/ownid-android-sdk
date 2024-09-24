package com.ownid.demo.gigya

import android.app.Application
import com.gigya.android.sdk.Gigya
import com.ownid.sdk.OwnId
import com.ownid.sdk.createGigyaInstanceFromFile
import com.ownid.sdk.dsl.providers
import com.ownid.sdk.getGigyaProviders

class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Gigya.setApplication(this)
        OwnId.createGigyaInstanceFromFile(this)

        OwnId.providers {
            getGigyaProviders(Gigya.getInstance())
        }
    }
}