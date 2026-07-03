import java.io.StringReader
import java.util.Properties

val versionsFile = rootProject.file("../versions.properties")
require(versionsFile.isFile) { "Missing versions.properties at ${versionsFile.path}" }

val versionsText = providers.fileContents(rootProject.layout.projectDirectory.file("../versions.properties")).asText.get()
val versionsProps = Properties().apply { load(StringReader(versionsText)) }

val useSnapshotVersions = versionsProps.getProperty("useSnapshotVersions")?.toBoolean() ?: false
val baseCoreVersion = requireNotNull(versionsProps.getProperty("coreVersion"))
val baseComposeVersion = requireNotNull(versionsProps.getProperty("composeVersion"))

val snapshotSuffix = if (useSnapshotVersions) "-SNAPSHOT" else ""
extra["coreVersion"] = "$baseCoreVersion$snapshotSuffix"
extra["composeVersion"] = "$baseComposeVersion$snapshotSuffix"
