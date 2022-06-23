package com.ownid.demo.ui.activity

import android.app.Application
import android.os.StrictMode


open class BaseDemoApp : Application() {

    val logs = StringBuffer()

    override fun onCreate() {
        super.onCreate()

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .permitDiskReads()
                .permitDiskWrites()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )

        AppConfig.init(this)
    }
}