buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinCompose) apply false
    base
}

version = providers.fileContents(layout.projectDirectory.file("../sdk-version.txt")).asText.map(String::trim).get()
