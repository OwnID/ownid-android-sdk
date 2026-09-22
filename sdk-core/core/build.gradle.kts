import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.dokkaJavadoc)
    id("ownid.sdk-library")
}

kotlin {
    explicitApi()
    jvmToolchain(17)
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_1_8
        languageVersion = KotlinVersion.KOTLIN_1_8
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "com.ownid.sdk"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 23

        aarMetadata {
            minCompileSdk = 35
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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

val legacyJavadocOutput = layout.buildDirectory.dir("intermediates/java_doc_dir/release/javaDocReleaseGeneration")

tasks.matching { it.name == "javaDocReleaseGeneration" }.configureEach {
    enabled = false
}

tasks.withType<Jar>().matching { it.name == "javaDocReleaseJar" }.configureEach {
    exclude { it.file.toPath().startsWith(legacyJavadocOutput.get().asFile.toPath()) }
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
}

dependencies {
    api(libs.kotlin.stdlibJdk8)
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
