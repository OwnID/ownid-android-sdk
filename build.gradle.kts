import java.util.Properties

val minSdkVersion by extra(23)
val targetSdkVersion by extra(35)
val compileSdkVersion by extra(targetSdkVersion)
val buildToolsVersion by extra("35.0.0")

version = "3.9.0"

val demoVersionCode by extra(30900)
val demoVersionName by extra(version)

val coreVersion by extra(version)
val composeVersion by extra(version)
val gigyaVersion by extra(version)

val localProperties = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
listOf("mavenCentralUsername", "mavenCentralPassword", "signingKeyId", "signingKey", "signingPassword").forEach { prop ->
    project.extra[prop] = localProperties.getProperty(prop, "")
}