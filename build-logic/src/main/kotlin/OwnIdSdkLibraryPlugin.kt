import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.signing.SigningExtension
import javax.inject.Inject

private val sdkVersionRegex = Regex("^(0|[1-9]\\d?)\\.(0|[1-9]\\d?)\\.(0|[1-9]\\d?)(-SNAPSHOT)?$")

private data class SdkArtifact(
    val label: String,
    val runtimeName: String,
    val versionAssetPath: String,
    val pomName: String,
    val pomDescription: String,
    val stableUploadRequiresStandaloneBuild: Boolean,
)

@CacheableTask
abstract class GenerateVersionAssetTask : DefaultTask() {
    @get:Input
    abstract val runtimeName: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val versionName: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val relativePath: org.gradle.api.provider.Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputFile = outputDirectory.file(relativePath.get()).get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText("name=${runtimeName.get()}\nversion=${versionName.get()}\n")
    }
}

@CacheableTask
abstract class ForbiddenApisTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @get:Input
    abstract val projectDisplayPath: org.gradle.api.provider.Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun checkSources() {
        val importRegexes = listOf(
            Regex("^\\s*import\\s+java\\.time\\."),
            Regex("^\\s*import\\s+java\\.util\\.stream\\."),
        )
        val projectDirectory = projectDirectory.get().asFile
        val offendingLines = sourceFiles.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (importRegexes.any { it.containsMatchIn(line) }) {
                    "${file.relativeTo(projectDirectory)}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }

        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        report.writeText(offendingLines.joinToString("\n"))

        if (offendingLines.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Forbidden Java APIs detected in ${projectDisplayPath.get()}:")
                    appendLine(" - java.time.* or java.util.stream.* found in src/main sources")
                    appendLine()
                    offendingLines.forEach { appendLine(it) }
                },
            )
        }
    }
}

class OwnIdSdkLibraryPlugin @Inject constructor(private val buildFeatures: BuildFeatures) : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            val artifact = artifactFor(name)
            val sdkProjectPath = path
            require(sdkVersionRegex.matches(version.toString())) {
                "SDK version must have the form major.minor.patch or major.minor.patch-SNAPSHOT, with every part between 0 and 99 and no leading zeros."
            }
            val publishingSecrets = loadPublishingSecrets(buildFeatures)
            if (publishingSecrets != null) {
                rootProject.extensions.extraProperties["ownIdPublishingCentralCredentials"] =
                    RedactedPasswordCredentials(publishingSecrets.centralUsername, publishingSecrets.centralPassword)
            }
            pluginManager.apply("maven-publish")
            pluginManager.apply("signing")

            val forbiddenApisCheck = tasks.register<ForbiddenApisTask>("forbiddenApisCheck") {
                group = "verification"
                description = "Fails if src/main imports java.time or java.util.stream"
                sourceFiles.from(
                    fileTree("src/main/java") { include("**/*.java", "**/*.kt") },
                    fileTree("src/main/kotlin") { include("**/*.java", "**/*.kt") },
                )
                projectDisplayPath.set(sdkProjectPath)
                projectDirectory.set(layout.projectDirectory)
                reportFile.set(layout.buildDirectory.file("reports/forbiddenApis/forbidden-apis.txt"))
            }

            tasks.register("ownIdForbiddenApisCheck") {
                group = "OwnID"
                description = "Run forbidden APIs check"
                dependsOn(forbiddenApisCheck)
            }

            val generateVersionAsset = tasks.register<GenerateVersionAssetTask>("setVersionProperties") {
                runtimeName.set(artifact.runtimeName)
                versionName.set(version.toString())
                relativePath.set(artifact.versionAssetPath)
                outputDirectory.set(layout.buildDirectory.dir("generated/assets"))
                dependsOn(forbiddenApisCheck)
            }

            pluginManager.withPlugin("com.android.library") {
                extensions.getByType<LibraryAndroidComponentsExtension>().onVariants { variant ->
                    variant.sources.assets?.addGeneratedSourceDirectory(
                        generateVersionAsset,
                        GenerateVersionAssetTask::outputDirectory,
                    )
                }
                tasks.named("preBuild").configure { dependsOn(forbiddenApisCheck) }
                tasks.named("check").configure { dependsOn(forbiddenApisCheck) }
            }

            configurePublication(artifact)
            configureSigningAndReleaseTasks(artifact, publishingSecrets)
        }
    }

    private fun Project.configurePublication(artifact: SdkArtifact) {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("release") {
                    components.matching { it.name == "release" }.all { from(this) }
                    artifactId = project.name
                    pom {
                        name.set(artifact.pomName)
                        description.set(artifact.pomDescription)
                        url.set("https://github.com/OwnID/ownid-android-sdk")
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        developers {
                            developer {
                                id.set("ownid")
                                name.set("OwnID")
                                email.set("support@ownid.com")
                            }
                        }
                        scm {
                            url.set("https://github.com/OwnID/ownid-android-sdk")
                            connection.set("scm:git:https://github.com/OwnID/ownid-android-sdk.git")
                            developerConnection.set("scm:git:ssh://git@github.com/OwnID/ownid-android-sdk.git")
                        }
                    }
                }
            }
        }
    }

    private fun Project.configureSigningAndReleaseTasks(artifact: SdkArtifact, publishingSecrets: PublishingSecrets?) {
        if (publishingSecrets != null) {
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(publishingSecrets.signingKeyId, publishingSecrets.signingKey, publishingSecrets.signingKeyPassword)
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }

        val publicationVersion = version.toString()
        val standaloneBuild = gradle.parent == null
        val verifySecurePublication = tasks.register("ownIdVerifySecurePublication") {
            group = "verification"
            description = "Verify that publication was started by the private OwnID publishing application"
            doLast {
                if (publishingSecrets == null) {
                    throw GradleException("Run Maven Central publication from the private OwnID SDK release workflow.")
                }
            }
        }
        val verifySnapshot = tasks.register("ownIdVerifySnapshotPublicationMode") {
            group = "verification"
            description = "Verify that ${artifact.label} SDK uses a snapshot version before snapshot publication"
            doLast {
                check(publicationVersion.endsWith("-SNAPSHOT")) {
                    "${artifact.label} snapshot publication requires an SDK version ending with -SNAPSHOT in sdk-version.txt."
                }
            }
        }
        val verifyStable = tasks.register("ownIdVerifyStablePublicationMode") {
            group = "verification"
            description = "Verify that ${artifact.label} SDK uses a stable version before stable publication"
            doLast {
                check(!publicationVersion.endsWith("-SNAPSHOT")) {
                    "${artifact.label} stable upload requires an SDK version without -SNAPSHOT in sdk-version.txt."
                }
                check(!artifact.stableUploadRequiresStandaloneBuild || standaloneBuild) {
                    "Compose stable upload must be run from standalone sdk-compose after public Core is available."
                }
            }
        }

        tasks.register("ownIdPublishSnapshotToCentral") {
            group = "OwnID publishing"
            description = "Publish ${artifact.label} SDK snapshot artifact to Maven Central snapshots"
            dependsOn(verifySnapshot, "publishReleasePublicationToSonatypeRepository")
        }
        tasks.register("ownIdUploadStableToCentralPortal") {
            group = "OwnID publishing"
            description = "Upload ${artifact.label} SDK stable artifact to Maven Central Portal for manual release"
            dependsOn(
                verifyStable,
                "publishReleasePublicationToSonatypeRepository",
                rootProject.tasks.named("closeSonatypeStagingRepository"),
            )
        }

        val verifyPublicationMode = if (publicationVersion.endsWith("-SNAPSHOT")) verifySnapshot else verifyStable
        afterEvaluate {
            val publishToSonatype = tasks.named("publishReleasePublicationToSonatypeRepository")
            publishToSonatype.configure {
                dependsOn(verifyPublicationMode, verifySecurePublication)
            }
            rootProject.tasks.named("initializeSonatypeStagingRepository").configure {
                dependsOn(verifyPublicationMode, verifySecurePublication)
                mustRunAfter(verifySnapshot, verifyStable)
            }
            rootProject.tasks.named("closeSonatypeStagingRepository").configure {
                dependsOn(verifyStable, verifySecurePublication)
                mustRunAfter(publishToSonatype)
            }
        }

        val guardedNexusTasks = setOf(
            "retrieveSonatypeStagingProfile",
            "findSonatypeStagingRepository",
            "closeSonatypeStagingRepository",
            "closeStagingRepositories",
        )
        rootProject.tasks.matching { it.name in guardedNexusTasks }.configureEach {
            dependsOn(verifySecurePublication)
        }

        val manualPortalReleaseTasks = setOf(
            "releaseSonatypeStagingRepository",
            "closeAndReleaseSonatypeStagingRepository",
            "releaseStagingRepositories",
            "closeAndReleaseStagingRepositories",
        )
        rootProject.tasks.matching { it.name in manualPortalReleaseTasks }.configureEach {
            doFirst {
                throw GradleException(
                    "Maven Central release is manual. Complete it in the Central Portal web UI.",
                )
            }
        }

        gradle.projectsEvaluated {
            tasks.named("publish").configure {
                setDependsOn(emptyList<Any>())
                doFirst {
                    throw GradleException("Generic remote publication is disabled. Use ownIdPublishSnapshotToCentral or ownIdUploadStableToCentralPortal.")
                }
            }
            rootProject.tasks.matching {
                it.name == "publishToSonatype" || it.name == "publishAllPublicationsToSonatypeRepository"
            }.configureEach {
                setDependsOn(emptyList<Any>())
                doFirst {
                    throw GradleException("Run Maven Central publication from the private OwnID SDK release workflow.")
                }
            }
            rootProject.tasks.matching { it.name in manualPortalReleaseTasks }.configureEach {
                setDependsOn(emptyList<Any>())
            }
        }
    }

    private fun artifactFor(projectName: String) = when (projectName) {
        "core" -> SdkArtifact(
            label = "Core",
            runtimeName = "OwnIDCore",
            versionAssetPath = "com/ownid/sdk/core.properties",
            pomName = "OwnID Core Android SDK",
            pomDescription = "OwnID Core for Android helps mobile apps deliver modern, passkey-first authentication journeys. It provides the core runtime, APIs, and flow orchestration for login, verification, passkey registration and sign-in, enrollment, and web-based flows.",
            stableUploadRequiresStandaloneBuild = false,
        )

        "compose" -> SdkArtifact(
            label = "Compose",
            runtimeName = "OwnIDCompose",
            versionAssetPath = "com/ownid/sdk/compose.properties",
            pomName = "OwnID Compose Android SDK",
            pomDescription = "OwnID Compose UI for Android provides polished Compose components for integrating OwnID user journeys. It includes UI for login ID collection and verification, plus reusable login and create-passkey widgets that work with OwnID Core.",
            stableUploadRequiresStandaloneBuild = true,
        )

        else -> error("ownid.sdk-library supports only the core and compose modules, not '$projectName'.")
    }
}
