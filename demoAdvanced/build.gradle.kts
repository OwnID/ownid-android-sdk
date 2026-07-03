plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinCompose) apply false
    base
}

apply(from = rootProject.file("../gradle/sdk-versions.gradle.kts"))
