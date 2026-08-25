import java.util.Properties

val minSdkVersion by extra(23)
val targetSdkVersion by extra(35)
val compileSdkVersion by extra(targetSdkVersion)
val buildToolsVersion by extra("35.0.0")

version = "3.11.0"

val demoVersionCode by extra(31100)
val demoVersionName by extra(version)

val coreVersion by extra(version)
val composeVersion by extra("3.8.3")
val gigyaVersion by extra("3.9.0")

val localProperties = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
listOf("mavenCentralUsername", "mavenCentralPassword", "signingKeyId", "signingKey", "signingPassword").forEach { prop ->
    project.extra[prop] = localProperties.getProperty(prop, "")
}
