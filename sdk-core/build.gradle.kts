plugins {
    alias(libs.plugins.nexusPublish)
    base
}

apply(from = rootProject.file("../gradle/sdk-publishing.gradle.kts"))

group = "com.ownid.android-sdk"
version = rootProject.extra["coreVersion"] as String
val publishingProps = rootProject.extra["publishingProps"] as java.util.Properties?

if (publishingProps != null) {
    nexusPublishing {
        repositories {
            sonatype {
                packageGroup.set("com.ownid.android-sdk")
                stagingProfileId.set("com.ownid")
                nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
                snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
                username.set(publishingProps.getProperty("mavenCentralUsername"))
                password.set(publishingProps.getProperty("mavenCentralPassword"))
            }
        }
    }
}

subprojects {
    apply(from = rootProject.file("../gradle/sdk-api-guard.gradle.kts"))
}
