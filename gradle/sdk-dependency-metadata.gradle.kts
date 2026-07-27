import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory

val dependencyMetadataBaselinePath = extra["ownIdDependencyMetadataBaselineFile"] as String
val dependencyMetadataBaselineFile = layout.projectDirectory.file(dependencyMetadataBaselinePath).asFile
val releasePublicationDir = layout.buildDirectory.dir("publications/release")
val expectedCoreVersion = rootProject.extra["coreVersion"].toString()

fun Element.childText(tagName: String): String? {
    val nodes = getElementsByTagName(tagName)
    return if (nodes.length == 0) null else nodes.item(0).textContent.trim()
}

fun normalizeDependencyVersion(group: String?, module: String?, version: Any?): Any? {
    val versionText = version?.toString()
    return if (group == "com.ownid.android-sdk" && module == "core" && versionText != null) {
        check(versionText == expectedCoreVersion) {
            "Expected com.ownid.android-sdk:core dependency version $expectedCoreVersion, but generated metadata contains $versionText."
        }
        "<coreVersion>"
    } else {
        version
    }
}

fun normalizeExclusions(rawExclusions: Any?): List<Map<String, String?>> {
    val exclusions = rawExclusions as? Iterable<*> ?: return emptyList()
    return exclusions
        .mapNotNull { it as? Map<*, *> }
        .map {
            sortedMapOf(
                "group" to it["group"]?.toString(),
                "module" to it["module"]?.toString()
            )
        }
        .sortedWith(compareBy({ it["group"].orEmpty() }, { it["module"].orEmpty() }))
}

fun normalizePomDependencies(pomFile: File): List<Map<String, Any?>> {
    val document = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(pomFile)
    val dependencies = document.getElementsByTagName("dependency")

    return (0 until dependencies.length)
        .map { dependencies.item(it) as Element }
        .map { dependency ->
            val group = dependency.childText("groupId")
            val module = dependency.childText("artifactId")
            val version = normalizeDependencyVersion(group, module, dependency.childText("version"))
            val exclusions = dependency.getElementsByTagName("exclusion")
            sortedMapOf<String, Any?>(
                "group" to group,
                "module" to module,
                "version" to version,
                "scope" to dependency.childText("scope"),
                "optional" to dependency.childText("optional"),
                "exclusions" to (0 until exclusions.length)
                    .map { exclusions.item(it) as Element }
                    .map {
                        sortedMapOf(
                            "group" to it.childText("groupId"),
                            "module" to it.childText("artifactId")
                        )
                    }
                    .sortedWith(compareBy({ it["group"].orEmpty() }, { it["module"].orEmpty() }))
            )
        }
        .sortedWith(compareBy({ it["scope"].toString() }, { it["group"].toString() }, { it["module"].toString() }))
}

fun normalizeModuleDependencies(moduleFile: File): Map<String, Any?> {
    val moduleJson = JsonSlurper().parse(moduleFile) as Map<*, *>
    val variants = moduleJson["variants"] as? Iterable<*> ?: emptyList<Any>()
    val libraryVariants = variants
        .mapNotNull { it as? Map<*, *> }
        .filter { variant ->
            val attributes = variant["attributes"] as? Map<*, *> ?: return@filter false
            attributes["org.gradle.category"] == "library" &&
                attributes["org.gradle.usage"] in listOf("java-api", "java-runtime")
        }

    return libraryVariants
        .associate { variant ->
            val attributes = variant["attributes"] as Map<*, *>
            val usage = attributes["org.gradle.usage"].toString()
            val dependencies = (variant["dependencies"] as? Iterable<*> ?: emptyList<Any>())
                .mapNotNull { it as? Map<*, *> }
                .map { dependency ->
                    val group = dependency["group"]?.toString()
                    val module = dependency["module"]?.toString()
                    val versionMap = dependency["version"] as? Map<*, *>
                    sortedMapOf<String, Any?>(
                        "group" to group,
                        "module" to module,
                        "version" to versionMap
                            ?.mapKeys { it.key.toString() }
                            ?.mapValues { normalizeDependencyVersion(group, module, it.value) }
                            ?.toSortedMap(),
                        "excludes" to normalizeExclusions(dependency["excludes"])
                    )
                }
                .sortedWith(compareBy({ it["group"].toString() }, { it["module"].toString() }))

            usage to sortedMapOf(
                "dependencies" to dependencies,
                "dependencyConstraints" to (variant["dependencyConstraints"] ?: emptyList<Any>()),
                "capabilities" to (variant["capabilities"] ?: emptyList<Any>()),
                "availableAt" to (variant["available-at"] ?: variant["availableAt"] ?: emptyList<Any>())
            )
        }
        .toSortedMap()
}

fun normalizeDependencyMetadata(): Map<String, Any?> {
    val publicationDir = releasePublicationDir.get().asFile
    return sortedMapOf(
        "pom" to normalizePomDependencies(publicationDir.resolve("pom-default.xml")),
        "gradleModuleMetadata" to normalizeModuleDependencies(publicationDir.resolve("module.json"))
    )
}

fun prettyJson(value: Any?): String {
    val writer = StringWriter()
    JsonOutput.prettyPrint(JsonOutput.toJson(value)).replace(Regex("\\[\\s*]"), "[]").lineSequence().forEach { line ->
        writer.append(line.trimEnd()).append('\n')
    }
    return writer.toString()
}

tasks.register("ownIdDependencyMetadataDump") {
    group = "OwnID"
    description = "Update the expected published dependency metadata baseline"
    dependsOn("generatePomFileForReleasePublication", "generateMetadataFileForReleasePublication")

    doLast {
        dependencyMetadataBaselineFile.parentFile.mkdirs()
        dependencyMetadataBaselineFile.writeText(prettyJson(normalizeDependencyMetadata()))
        logger.lifecycle("Updated dependency metadata baseline: ${dependencyMetadataBaselineFile.relativeTo(projectDir)}")
    }
}

tasks.register("ownIdDependencyMetadataCheck") {
    group = "OwnID"
    description = "Check generated published dependency metadata against the expected baseline"
    dependsOn("generatePomFileForReleasePublication", "generateMetadataFileForReleasePublication")

    doLast {
        check(dependencyMetadataBaselineFile.isFile) {
            "Missing dependency metadata baseline: ${dependencyMetadataBaselineFile.path}. Run ownIdDependencyMetadataDump to create it."
        }

        val expected = dependencyMetadataBaselineFile.readText()
        val actual = prettyJson(normalizeDependencyMetadata())
        check(expected == actual) {
            "Dependency metadata baseline is stale for ${project.path}. Run ownIdDependencyMetadataDump and review the dependency surface changes."
        }
    }
}
