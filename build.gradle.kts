import java.util.Properties

val minSdkVersion by extra(23)
val targetSdkVersion by extra(34)
val compileSdkVersion by extra(targetSdkVersion)
val buildToolsVersion by extra("35.0.0")

plugins {
    id("io.github.gradle-nexus.publish-plugin").version("2.0.0")
}

group = "com.ownid.android-sdk"
version = "3.8.0"

val demoVersionCode by extra(30800)
val demoVersionName by extra(version)

val coreVersion by extra(version)
val composeVersion by extra(version)
val gigyaVersion by extra(version)

private val localProps = Properties()
File(rootProject.rootDir, "local.properties").apply { if (exists() && isFile) inputStream().use { localProps.load(it) } }

val signingKeyId: String by extra(localProps.getProperty("signingKeyId", "\"\""))
val signingKey: String by extra(localProps.getProperty("signingKey", "\"\""))
val signingPassword: String by extra(localProps.getProperty("signingPassword", "\"\""))

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            stagingProfileId.set(localProps.getProperty("sonatypeStagingProfileId", "\"\""))
            username.set(localProps.getProperty("ossrhUsername", "\"\""))
            password.set(localProps.getProperty("ossrhPassword", "\"\""))
        }
    }
}