package com.ownid.demo

import com.ownid.sdk.OwnId
import com.ownid.sdk.logger.OwnIdLogger

class DemoAdvancedApp : BaseApp() {

    override fun onCreate() {
        super.onCreate()

        DemoAuthIntegrationProvider.integration = CustomDemoAuthIntegration(applicationContext)

        OwnId.logger {
            level = OwnIdLogger.Level.Verbose
        }

        OwnId.initializeFromJson {
            applicationContext = this@DemoAdvancedApp.applicationContext
            json = BuildConfig.OWNID_CONFIG_JSON
        }

        OwnId.setProviders {
            demoIdentityProviders()
            signInWithGoogleProvider()
        }
    }
}
