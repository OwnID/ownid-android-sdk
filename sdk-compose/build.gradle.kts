import org.gradle.api.credentials.PasswordCredentials

plugins {
    alias(libs.plugins.nexusPublish)
    base
}

val sdkVersion = providers.fileContents(layout.projectDirectory.file("../sdk-version.txt")).asText.map(String::trim).get()

group = "com.ownid.android-sdk"
version = sdkVersion

subprojects {
    group = rootProject.group
    version = rootProject.version
}

nexusPublishing {
    repositories.sonatype {
        packageGroup.set("com.ownid.android-sdk")
        nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
        snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        stagingProfileId.set("com.ownid")
        username.set(providers.provider {
            (extensions.extraProperties.properties["ownIdPublishingCentralCredentials"] as? PasswordCredentials)
                ?.username.orEmpty()
        })
        password.set(providers.provider {
            (extensions.extraProperties.properties["ownIdPublishingCentralCredentials"] as? PasswordCredentials)
                ?.password.orEmpty()
        })
    }
}
