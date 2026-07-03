import org.gradle.api.tasks.WriteProperties

plugins.withId("com.android.library") {
    val ownIdVersionName = requireNotNull(project.findProperty("ownIdVersionName") ?: extra["ownIdVersionName"]) as String
    val ownIdVersionPropertiesFile = requireNotNull(project.findProperty("ownIdVersionPropertiesFile") ?: extra["ownIdVersionPropertiesFile"]) as String

    val setVersionProperties = tasks.register<WriteProperties>("setVersionProperties") {
        destinationFile = layout.buildDirectory.file("generated/assets/$ownIdVersionPropertiesFile").get().asFile
        property("name", ownIdVersionName)
        property("version", project.version.toString())
    }

    tasks.named("preBuild").configure {
        dependsOn(setVersionProperties)
    }

    tasks.register("ownIdPublicApiDump") {
        group = "OwnID"
        description = "Dump public binary API"
        dependsOn(tasks.named("apiDump"))
    }

    tasks.register("ownIdPublicApiCheck") {
        group = "OwnID"
        description = "Run public binary API check"
        dependsOn(tasks.named("apiCheck"))
    }

    tasks.register("ownIdForbiddenApisCheck") {
        group = "OwnID"
        description = "Run forbidden APIs check"
        dependsOn(tasks.named("forbiddenApisCheck"))
    }
}
