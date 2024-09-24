import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    id("com.android.library").version("8.5.2")
    id("org.jetbrains.kotlin.android").version("1.8.22")
    id("org.jetbrains.kotlinx.binary-compatibility-validator").version("0.16.3")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.ownid.sdk.compose"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    buildToolsVersion = rootProject.extra["buildToolsVersion"] as String

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int

        gradle.projectsEvaluated { project.tasks.preBuild.dependsOn("setVersionProperties") }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs += "-Xexplicit-api=strict"
    }

    lint {
        enable += "Interoperability"
    }

    apiValidation {
        nonPublicMarkers.addAll(listOf("kotlin.PublishedApi", "com.ownid.sdk.InternalOwnIdAPI"))
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

//noinspection GradleDependency
dependencies {
    api(project(":sdk:core"))

    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    api("androidx.activity:activity-compose:1.8.0")
    api("androidx.compose.ui:ui:1.5.4")
}

private val propertiesFileName = "src/main/assets/com/ownid/sdk/compose.properties"
tasks.clean { doFirst { File(projectDir, propertiesFileName).apply { if (exists() && isFile) delete() } } }
tasks.register<WriteProperties>("setVersionProperties") {
    destinationFile = File(propertiesFileName)
    property("name", "OwnIDCompose")
    property("version", rootProject.extra["composeVersion"] as String)
}

publishing {
    publications {
        create<MavenPublication>("ComposeRelease") {
            groupId = "com.ownid.android-sdk"
            artifactId = "compose"
            version = rootProject.extra["composeVersion"] as String
            afterEvaluate { from(components["release"]) }

            pom {
                name = "OwnID Compose Android SDK"
                description = "Secure and passwordless login alternative"
                url = "https://www.ownid.com"

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                developers {
                    developer {
                        id = "dkrivoruchko"
                        name = "Dmitriy Krivoruchko"
                        email = "dmitriy@ownid.com"
                    }
                }

                scm {
                    url = "https://github.com/OwnID/ownid-android-sdk"
                }
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(
        rootProject.extra["signingKeyId"] as String,
        rootProject.extra["signingKey"] as String,
        rootProject.extra["signingPassword"] as String
    )
    sign(publishing.publications["ComposeRelease"])
}