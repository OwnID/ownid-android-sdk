import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    id("com.android.library").version("8.1.1")
    id("org.jetbrains.kotlin.android").version("1.8.22")
    id("org.jetbrains.kotlinx.binary-compatibility-validator").version("0.17.0")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.ownid.sdk"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    buildToolsVersion = rootProject.extra["buildToolsVersion"] as String

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int

        gradle.projectsEvaluated { project.tasks.preBuild.dependsOn("setVersionProperties") }

        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs += "-Xexplicit-api=strict"
    }

    kotlin {
        explicitApi()
//        jvmToolchain(JavaVersion.VERSION_17.ordinal)
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
    api("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
    api("androidx.appcompat:appcompat:1.7.0")
    api("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    api("androidx.fragment:fragment:1.6.2")
    api("com.google.android.material:material:1.11.0")
    api("androidx.constraintlayout:constraintlayout:2.1.4")
    api("androidx.browser:browser:1.7.0")
    api("androidx.webkit:webkit:1.9.0")
    api("com.squareup.okio:okio:3.4.0")
    api("com.squareup.okhttp3:okhttp:4.11.0")
    api("androidx.datastore:datastore-preferences:1.0.0")

    api("androidx.credentials:credentials:1.3.0")
    api("androidx.credentials:credentials-play-services-auth:1.3.0")
    api("com.google.android.libraries.identity.googleid:googleid:1.1.1")

//    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:truth:1.6.0")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.awaitility:awaitility-kotlin:4.2.1")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")
}

private val propertiesFileName = "src/main/assets/com/ownid/sdk/core.properties"
tasks.clean { doFirst { File(projectDir, propertiesFileName).apply { if (exists() && isFile) delete() } } }
tasks.register<WriteProperties>("setVersionProperties") {
    destinationFile = File(propertiesFileName)
    property("name", "OwnIDCore")
    property("version", rootProject.extra["coreVersion"] as String)
}

publishing {
    publications {
        create<MavenPublication>("CoreRelease") {
            groupId = "com.ownid.android-sdk"
            artifactId = "core"
            version = rootProject.extra["coreVersion"] as String
            afterEvaluate { from(components["release"]) }

            pom {
                name = "OwnID Core Android SDK"
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
    sign(publishing.publications["CoreRelease"])
}