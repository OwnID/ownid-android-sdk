plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
}

val sdkVersion = rootProject.version.toString()
val demoVersionName = sdkVersion.removeSuffix("-SNAPSHOT")
val demoVersionParts = demoVersionName.split('.').map { it.toInt() }
require(demoVersionParts.size == 3 && demoVersionParts.all { it in 0..99 } && demoVersionName == demoVersionParts.joinToString(".")) {
    "SDK version must have the form major.minor.patch with every part between 0 and 99 and no leading zeros"
}
val demoVersionCode = demoVersionParts.fold(0) { code, part -> code * 100 + part }

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.ownid.demo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ownid.demo"
        minSdk = 23
        targetSdk = 37
        versionCode = demoVersionCode
        versionName = demoVersionName
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

    implementation("com.ownid.android-sdk:core:$sdkVersion")
    implementation("com.ownid.android-sdk:compose:$sdkVersion")

    implementation("com.squareup.okhttp3:okhttp:5.3.2")
}
