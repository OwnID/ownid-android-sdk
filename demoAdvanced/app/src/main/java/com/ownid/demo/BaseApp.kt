package com.ownid.demo

import android.app.Application
import android.os.Build
import android.os.StrictMode

open class BaseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
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
                    .detectLeakedSqlLiteObjects()
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectFileUriExposure()
                    .detectCleartextNetwork()
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detectContentUriWithoutPermission()
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detectUntaggedSockets()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) detectCredentialProtectedWhileLocked()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) detectIncorrectContextUse()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) detectUnsafeIntentLaunch()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) detectImplicitUriPermissionGrant()
                    }
                    .penaltyLog()
                    .build()
            )
        }
    }
}
