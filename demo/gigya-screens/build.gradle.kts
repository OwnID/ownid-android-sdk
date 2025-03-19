plugins {
    id("com.android.application").version("8.1.1")
    id("org.jetbrains.kotlin.android").version("1.9.24")
    id("org.jetbrains.kotlin.plugin.serialization").version("1.9.24")
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation(project(":demo:common"))

    // IdentityPlatform
    implementation("com.sap.oss.gigya-android-sdk:sdk-core:7.0.11")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.ownid.android-sdk:gigya:3.8.0")
}