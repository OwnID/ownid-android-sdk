import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokkaJavadoc)
    id("ownid.sdk-library")
}

kotlin {
    explicitApi()
    jvmToolchain(17)
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_1_9
        languageVersion = KotlinVersion.KOTLIN_1_9
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "com.ownid.sdk.compose"

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

    buildFeatures {
        compose = true
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

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_compiler_stability_config.conf"))
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
    api(libs.kotlin.stdlib)
    api("com.ownid.android-sdk:core:${project.version}")

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
}
