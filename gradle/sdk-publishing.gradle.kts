import java.util.Properties

apply(from = rootProject.file("../gradle/sdk-versions.gradle.kts"))

val publishingProps = rootProject.file("../publishing.properties")
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

extra["publishingProps"] = publishingProps

subprojects {
    if (publishingProps != null) {
        plugins.withId("signing") {
            extensions.configure(SigningExtension::class.java) {
                useInMemoryPgpKeys(
                    publishingProps.getProperty("signingInMemoryKeyId"),
                    publishingProps.getProperty("signingInMemoryKey"),
                    publishingProps.getProperty("signingInMemoryKeyPassword")
                )
            }

            plugins.withId("maven-publish") {
                extensions.configure(SigningExtension::class.java) {
                    sign(extensions.getByType(PublishingExtension::class.java).publications)
                }
            }
        }
    }
}
