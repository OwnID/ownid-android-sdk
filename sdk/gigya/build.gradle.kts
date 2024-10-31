import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    id("com.android.library").version("8.1.1")
    id("org.jetbrains.kotlin.android").version("1.8.22")
    id("org.jetbrains.kotlinx.binary-compatibility-validator").version("0.16.3")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.ownid.sdk.gigya"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    buildToolsVersion = rootProject.extra["buildToolsVersion"] as String

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int

        gradle.projectsEvaluated { project.tasks.preBuild.dependsOn("setVersionProperties") }
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

    testOptions.unitTests.isIncludeAndroidResources = true
}

//noinspection GradleDependency
dependencies {
    api(project(":sdk:core"))

    compileOnly("com.sap.oss.gigya-android-sdk:sdk-core:7.0.11")

    testImplementation("com.sap.oss.gigya-android-sdk:sdk-core:7.0.11")
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:truth:1.5.0")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("org.awaitility:awaitility-kotlin:4.2.1")
    testImplementation("io.mockk:mockk:1.13.10")
}

private val propertiesFileName = "src/main/assets/com/ownid/sdk/gigya.properties"
tasks.clean { doFirst { File(projectDir, propertiesFileName).apply { if (exists() && isFile) delete() } } }
tasks.register<WriteProperties>("setVersionProperties") {
    destinationFile = File(propertiesFileName)
    property("name", "OwnIDGigya")
    property("version", rootProject.extra["gigyaVersion"] as String)
}

publishing {
    publications {
        create<MavenPublication>("GigyaRelease") {
            groupId = "com.ownid.android-sdk"
            artifactId = "gigya"
            version = rootProject.extra["gigyaVersion"] as String
            afterEvaluate { from(components["release"]) }

            pom {
                name = "OwnID Gigya Android SDK"
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
    sign(publishing.publications["GigyaRelease"])
}