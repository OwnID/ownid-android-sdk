plugins {
    id("com.android.library").version("8.8.0")
    id("org.jetbrains.kotlin.android").version("1.9.24")
    id("org.jetbrains.kotlin.plugin.serialization").version("1.9.24")
}

android {
    namespace = "com.ownid.demo"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    buildToolsVersion = rootProject.extra["buildToolsVersion"] as String

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    api(platform("androidx.compose:compose-bom:2024.09.00"))

    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    api("androidx.navigation:navigation-compose:2.8.0")
    api("androidx.activity:activity-compose:1.9.2")
    api("androidx.appcompat:appcompat:1.7.0")

    api("androidx.compose.material3:material3")

    api("com.jakewharton:process-phoenix:3.0.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}