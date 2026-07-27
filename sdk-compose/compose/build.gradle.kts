plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.binaryCompatibilityValidator)
    alias(libs.plugins.kover)
    id("signing")
    id("maven-publish")
}

val coreVersion = rootProject.extra["coreVersion"] as String

group = "com.ownid.android-sdk"
version = rootProject.extra["composeVersion"] as String
val publishingProps = rootProject.extra["publishingProps"] as java.util.Properties?

extra["ownIdVersionName"] = "OwnIDCompose"
extra["ownIdVersionPropertiesFile"] = "com/ownid/sdk/compose.properties"
apply(from = rootProject.file("../gradle/sdk-library-conventions.gradle.kts"))
extra["ownIdDependencyMetadataBaselineFile"] = "api/compose.dependencies.json"

val composeStabilityConfigurationPath: String = layout.projectDirectory.file("compose_compiler_stability_config.conf").asFile.absolutePath

kotlin {
    explicitApi()
    jvmToolchain(17)
}

android {
    namespace = "com.ownid.sdk.compose"

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
        apiVersion = "1.9"
        languageVersion = "1.9"
        jvmTarget = JavaVersion.VERSION_11.toString()
        freeCompilerArgs += listOf(
            "-Xexplicit-api=strict",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=$composeStabilityConfigurationPath"
        )
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
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
    api("com.ownid.android-sdk:core:$coreVersion")

    api(libs.kotlinx.coroutinesCore)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.foundationLayout)
    api(libs.androidx.compose.animation)
    api(libs.androidx.material3)
    api(libs.androidx.lifecycleViewmodel)

    implementation(libs.kotlinx.coroutinesAndroid)
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.lifecycleViewmodelCompose)
    implementation(libs.androidx.lifecycleRuntimeCompose)

    debugImplementation(libs.androidx.compose.uiTooling)
    debugImplementation(libs.androidx.compose.uiToolingPreview)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}

apiValidation {
    nonPublicMarkers.addAll(listOf("com.ownid.sdk.model.InternalOwnIdAPI"))
}

publishing {
    publications {
        create<MavenPublication>("release") {
            project.components.matching { it.name == "release" }.all { from(this) }
            artifactId = "compose"
            pom {
                name.set("OwnID Compose Android SDK")
                description.set("OwnID Compose UI for Android provides polished Compose components for integrating OwnID user journeys. It includes UI for login ID collection and verification, plus reusable login and create-passkey widgets that work with OwnID Core.")
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
                "Compose snapshot publication requires useSnapshotVersions=true in versions.properties."
            }
        }
    }
    val verifyStablePublicationMode = tasks.register("ownIdVerifyStablePublicationMode") {
        doLast {
            check(!project.version.toString().endsWith("-SNAPSHOT")) {
                "Compose stable upload requires useSnapshotVersions=false in versions.properties."
            }
            check(gradle.parent == null) {
                "Compose stable upload must be run from standalone sdk-compose after public Core is available."
            }
        }
    }

    tasks.register("ownIdPublishSnapshotToCentral") {
        group = "OwnID"
        description = "Publish Compose SDK snapshot artifact to Maven Central snapshots"
        dependsOn(verifySnapshotPublicationMode)
        dependsOn(publishReleasePublicationToSonatypeRepository)
    }

    tasks.register("ownIdUploadStableToCentralPortal") {
        group = "OwnID"
        description = "Upload Compose SDK stable artifact to Maven Central Portal for manual release"
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
