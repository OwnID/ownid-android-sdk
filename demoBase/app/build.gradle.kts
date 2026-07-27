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
        minSdk = 23
        targetSdk = 36
        versionCode = 40100
        versionName = "4.1.0"
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

    flavorDimensions += "environment"
    productFlavors {
        create("uat") {
            dimension = "environment"
            applicationIdSuffix = ".uat"
            buildConfigField("String", "OWNID_CONFIG_JSON", """"{\"appId\":\"4usvqc0dt3k7ol\",\"env\":\"uat\"}"""")
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
    implementation(libs.kotlinx.serialization.json)

    implementation("com.ownid.android-sdk:core:$coreVersion")
    implementation("com.ownid.android-sdk:compose:$composeVersion")

    implementation("com.squareup.okhttp3:okhttp:5.3.2")
}
