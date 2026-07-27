plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.binaryCompatibilityValidator)
    alias(libs.plugins.kover)
    id("signing")
    id("maven-publish")
}

group = "com.ownid.android-sdk"
version = rootProject.extra["coreVersion"] as String
val publishingProps = rootProject.extra["publishingProps"] as java.util.Properties?

extra["ownIdVersionName"] = "OwnIDCore"
extra["ownIdVersionPropertiesFile"] = "com/ownid/sdk/core.properties"
apply(from = rootProject.file("../gradle/sdk-library-conventions.gradle.kts"))
extra["ownIdDependencyMetadataBaselineFile"] = "api/core.dependencies.json"

kotlin {
    explicitApi()
    jvmToolchain(17)
}

android {
    namespace = "com.ownid.sdk"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 23

        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        apiVersion = "1.8"
        languageVersion = "1.8"
        jvmTarget = JavaVersion.VERSION_11.toString()
        freeCompilerArgs += "-Xexplicit-api=strict"
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/assets"))
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(libs.kotlinx.coroutinesCore)
    api(libs.kotlinx.serialization)
    api(libs.okhttp)
    api(libs.androidx.lifecycleCommon)

    implementation(libs.kotlinx.coroutinesAndroid)
    implementation(libs.okio)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycleRuntime)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.datastorePreferences) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-parcelize-runtime")
    }

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentialsplayServices)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
}

apiValidation {
    nonPublicMarkers.addAll(listOf("com.ownid.sdk.model.InternalOwnIdAPI"))
}

publishing {
    publications {
        create<MavenPublication>("release") {
            project.components.matching { it.name == "release" }.all { from(this) }
            artifactId = "core"
            pom {
                name.set("OwnID Core Android SDK")
                description.set("OwnID Core for Android helps mobile apps deliver modern, passkey-first authentication journeys. It provides the core runtime, APIs, and flow orchestration for login, verification, passkey registration and sign-in, enrollment, and web-based flows.")
                url.set("https://github.com/OwnID/ownid-android-sdk")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("ownid")
                        name.set("OwnID")
                        email.set("support@ownid.com")
                    }
                }
                scm {
                    url.set("https://github.com/OwnID/ownid-android-sdk")
                    connection.set("scm:git:https://github.com/OwnID/ownid-android-sdk.git")
                    developerConnection.set("scm:git:ssh://git@github.com/OwnID/ownid-android-sdk.git")
                }
            }
        }
    }
}

apply(from = rootProject.file("../gradle/sdk-dependency-metadata.gradle.kts"))

if (publishingProps != null) {
    val publishReleasePublicationToSonatypeRepository = tasks.named("publishReleasePublicationToSonatypeRepository")
    val verifySnapshotPublicationMode = tasks.register("ownIdVerifySnapshotPublicationMode") {
        doLast {
            check(project.version.toString().endsWith("-SNAPSHOT")) {
                "Core snapshot publication requires useSnapshotVersions=true in versions.properties."
            }
        }
    }
    val verifyStablePublicationMode = tasks.register("ownIdVerifyStablePublicationMode") {
        doLast {
            check(!project.version.toString().endsWith("-SNAPSHOT")) {
                "Core stable upload requires useSnapshotVersions=false in versions.properties."
            }
        }
    }

    tasks.register("ownIdPublishSnapshotToCentral") {
        group = "OwnID"
        description = "Publish Core SDK snapshot artifact to Maven Central snapshots"
        dependsOn(verifySnapshotPublicationMode)
        dependsOn(publishReleasePublicationToSonatypeRepository)
    }

    tasks.register("ownIdUploadStableToCentralPortal") {
        group = "OwnID"
        description = "Upload Core SDK stable artifact to Maven Central Portal for manual release"
        dependsOn(verifyStablePublicationMode)
        dependsOn(publishReleasePublicationToSonatypeRepository)
        dependsOn(rootProject.tasks.named("closeSonatypeStagingRepository"))
    }

    publishReleasePublicationToSonatypeRepository.configure {
        mustRunAfter(verifySnapshotPublicationMode, verifyStablePublicationMode)
    }
    rootProject.tasks.named("initializeSonatypeStagingRepository").configure {
        mustRunAfter(verifySnapshotPublicationMode, verifyStablePublicationMode)
    }
    rootProject.tasks.named("closeSonatypeStagingRepository").configure {
        mustRunAfter(publishReleasePublicationToSonatypeRepository)
    }
}
