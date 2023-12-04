package com.ownid.demo.gigya

import android.app.Application
import com.gigya.android.sdk.Gigya
import com.ownid.sdk.OwnId
import com.ownid.sdk.createGigyaInstanceFromJson

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Gigya.setApplication(this)
        Gigya.getInstance().init("3_O4QE0Kk7QstG4VGDPED5omrr8mgbTuf_Gim8V_Y19YDP75m_msuGtNGQz89X0KWP", "us1.gigya.com")

        OwnId.createGigyaInstanceFromJson(this, """{"app_id": "l16tzgmvvyf5qn"""")
    }
}