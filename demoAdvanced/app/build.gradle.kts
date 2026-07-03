plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
}

val coreVersion = rootProject.extra["coreVersion"] as String
val composeVersion = rootProject.extra["composeVersion"] as String

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.ownid.demo"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.ownid.demo"
        targetSdk = 36
        versionCode = 40000
        versionName = "4.0.0"
    }

    signingConfigs {
        register("demosign") {
            storeFile = file("../OwnID-Demo-Key.jks")
            storePassword = "OwnID-Demo-Key"
            keyAlias = "ownid-demo-key-alias"
            keyPassword = "OwnID-Demo-Key"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    //SHA1: 00:84:B8:EF:74:62:8C:D5:6D:D8:4E:B6:0C:FD:79:F7:32:2B:F0:FA
    //SHA256: 4C:C2:AC:17:6E:0B:FA:AF:35:CD:8D:71:CD:80:F8:87:E9:9C:FC:8E:38:7A:22:91:37:CA:AB:59:E5:0F:4D:8E

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("demosign")
        }
        release {
            signingConfig = signingConfigs.getByName("demosign")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions += "integration"
    flavorDimensions += "environment"
    productFlavors {
        create("custom") {
            dimension = "integration"
            minSdk = 23
        }
        create("uat") {
            dimension = "environment"
            applicationIdSuffix = ".uat"
            buildConfigField("String", "OWNID_CONFIG_JSON", "\"{\\\"appId\\\":\\\"4usvqc0dt3k7ol\\\",\\\"env\\\":\\\"uat\\\"}\"")
            buildConfigField("String", "DEMO_IDENTITY_BASE_URL", "\"https://node-mongo.custom.demo.staging.ownid.com/api/auth/\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycleViewmodelCompose)
    implementation(libs.androidx.lifecycleViewmodelKtx)
    implementation(libs.androidx.activityCompose)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentialsplayServices)
    implementation(libs.googleid)

    implementation(platform(libs.androidx.composeBom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation3Runtime)
    implementation(libs.androidx.navigation3Ui)
    implementation(libs.androidx.lifecycleViewmodelNavigation3)
    implementation(libs.androidx.material3AdaptiveNavigationSuite)
    implementation(libs.kotlinx.serialization.json)

    implementation("com.ownid.android-sdk:core:$coreVersion")
    implementation("com.ownid.android-sdk:compose:$composeVersion")

    "customImplementation"("com.squareup.okhttp3:okhttp:5.3.2")
    "customImplementation"("com.squareup.okhttp3:logging-interceptor:5.3.2")
}
