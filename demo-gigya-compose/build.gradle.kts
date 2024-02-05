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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        compose = true
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
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.runtime:runtime-livedata:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    implementation("com.ownid.android-sdk:compose:3.0.2")
    implementation("com.ownid.android-sdk:gigya:3.0.2")
}