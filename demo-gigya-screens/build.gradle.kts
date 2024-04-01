plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    apply(from = "../signing.gradle")

    namespace = "com.ownid.demo.gigya"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    buildToolsVersion = rootProject.extra["buildToolsVersion"] as String

    defaultConfig {
        applicationId = "com.ownid.demo.gigya"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = rootProject.extra["demoVersionCode"] as Int
        versionName = rootProject.extra["demoVersionName"] as String
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("demosign")
        }
        release {
            signingConfig = signingConfigs.getByName("demosign")
            isMinifyEnabled = false // Gigya SDK does not support it https://github.com/SAP/gigya-android-sdk/issues/3
            isShrinkResources = false // Gigya SDK does not support it https://github.com/SAP/gigya-android-sdk/issues/3
        }
    }
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

dependencies {
    implementation(project(":demo-common"))

    implementation("com.sap.oss.gigya-android-sdk:sdk-core:7.0.9")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.ownid.android-sdk:gigya:3.2.0")
}