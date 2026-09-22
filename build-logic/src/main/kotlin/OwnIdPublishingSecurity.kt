import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.logging.LogLevel
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyStore
import kotlin.io.path.getOwner
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

private const val PUBLISHING_STORE_FILE = "publishing-secrets.p12"
private const val SECURE_TRANSPORT_PROPERTY = "ownIdSecurePublishingTransport"
private const val SECURE_TRANSPORT_VALUE = "stdin-v1"
private const val MIN_STORE_PASSWORD_LENGTH = 32
private const val MAX_STORE_PASSWORD_LENGTH = 256
private const val MAX_PUBLISHING_STORE_SIZE = 1024L * 1024L

private const val CENTRAL_USERNAME_ALIAS = "ownid.maven-central.username.v1"
private const val CENTRAL_PASSWORD_ALIAS = "ownid.maven-central.password.v1"
private const val SIGNING_KEY_ALIAS = "ownid.signing.key.v1"
private const val SIGNING_KEY_PASSWORD_ALIAS = "ownid.signing.key-password.v1"
private const val SIGNING_KEY_ID_ALIAS = "ownid.signing.key-id.v1"

private val publishingStoreAliases = setOf(
    CENTRAL_USERNAME_ALIAS,
    CENTRAL_PASSWORD_ALIAS,
    SIGNING_KEY_ALIAS,
    SIGNING_KEY_PASSWORD_ALIAS,
    SIGNING_KEY_ID_ALIAS,
)

private const val STORE_UNLOCK_FAILURE =
    "Could not unlock the OwnID publishing secrets store. Start again from the private OwnID SDK release workflow."
private val sdkVersionRegex = Regex("^(0|[1-9]\\d?)\\.(0|[1-9]\\d?)\\.(0|[1-9]\\d?)(-SNAPSHOT)?$")

private enum class PublicationMode(val taskName: String) {
    SNAPSHOT("ownIdPublishSnapshotToCentral"),
    STABLE("ownIdUploadStableToCentralPortal"),
}

internal class PublishingSecrets(
    val centralUsername: String,
    val centralPassword: String,
    val signingKey: String,
    val signingKeyPassword: String,
    val signingKeyId: String,
)

internal class RedactedPasswordCredentials(
    private var usernameValue: String?,
    private var passwordValue: String?,
) : PasswordCredentials {
    override fun getUsername(): String? = usernameValue

    override fun setUsername(username: String?) {
        usernameValue = username
    }

    override fun getPassword(): String? = passwordValue

    override fun setPassword(password: String?) {
        passwordValue = password
    }

    override fun toString(): String = "PasswordCredentials([redacted])"
}

internal fun Project.loadPublishingSecrets(buildFeatures: BuildFeatures): PublishingSecrets? {
    val repositoryRoot = findRepositoryRoot()
    securePublicationMode(buildFeatures, repositoryRoot) ?: return null
    return readPublishingSecrets(repositoryRoot)
}

private fun Project.findRepositoryRoot(): Path =
    generateSequence(projectDir.toPath()) { it.parent }
        .firstOrNull { it.resolve("sdk-version.txt").isRegularFile() }
        ?: throw GradleException("Could not find repository-root sdk-version.txt from $projectDir.")

private fun Project.securePublicationMode(
    buildFeatures: BuildFeatures,
    repositoryRoot: Path,
): PublicationMode? {
    val requestedTasks = gradle.startParameter.taskNames
    val requestedModes = requestedTasks.mapNotNull { requested ->
        PublicationMode.entries.singleOrNull { mode -> requested == "$path:${mode.taskName}" }
    }
    if (requestedModes.isEmpty()) return null
    if (requestedTasks.size != 1 || requestedModes.size != 1) {
        throw GradleException(
            "A secure publication invocation must request exactly one OwnID publication task. " +
                    "Start again from the private OwnID SDK release workflow.",
        )
    }

    val mode = requestedModes.single()
    if (gradle.startParameter.isDryRun) return null
    if (gradle.startParameter.excludedTaskNames.isNotEmpty()) {
        throw GradleException("Secure publication does not allow excluded tasks.")
    }
    if (gradle.startParameter.isContinueOnFailure) {
        throw GradleException("Secure publication does not allow continuing after a task failure.")
    }
    if (gradle.parent != null) {
        throw GradleException(
            "Maven Central publication must run as a standalone SDK build. " +
                    "Start again from the private OwnID SDK release workflow.",
        )
    }
    if (providers.gradleProperty(SECURE_TRANSPORT_PROPERTY).orNull != SECURE_TRANSPORT_VALUE) {
        throw GradleException("Run Maven Central publication from the private OwnID SDK release workflow.")
    }
    if (buildFeatures.configurationCache.requested.getOrElse(false)) {
        throw GradleException(
            "Secure publication requires the configuration cache to be disabled. " +
                    "Start again from the private OwnID SDK release workflow.",
        )
    }
    if (gradle.startParameter.isBuildCacheEnabled) {
        throw GradleException(
            "Secure publication requires the build cache to be disabled. " +
                    "Start again from the private OwnID SDK release workflow.",
        )
    }
    if (gradle.startParameter.isBuildScan || !gradle.startParameter.isNoBuildScan) {
        throw GradleException(
            "Secure publication requires build scans to be explicitly disabled. " +
                    "Start again from the private OwnID SDK release workflow.",
        )
    }
    if (gradle.startParameter.logLevel == LogLevel.DEBUG) {
        throw GradleException(
            "Secure publication does not allow Gradle DEBUG logging. " +
                    "Start again from the private OwnID SDK release workflow.",
        )
    }

    validatePublicationVersion(repositoryRoot, mode)
    return mode
}

private fun validatePublicationVersion(repositoryRoot: Path, mode: PublicationMode) {
    val expectedVersion = repositoryRoot.resolve("sdk-version.txt").readText().trim()
    if (!sdkVersionRegex.matches(expectedVersion)) {
        throw GradleException("sdk-version.txt must contain x.y.z or x.y.z-SNAPSHOT with every component between 0 and 99.")
    }
    when (mode) {
        PublicationMode.SNAPSHOT -> if (!expectedVersion.endsWith("-SNAPSHOT")) {
            throw GradleException("Snapshot publication requires sdk-version.txt to end with -SNAPSHOT.")
        }

        PublicationMode.STABLE -> if (expectedVersion.endsWith("-SNAPSHOT")) {
            throw GradleException("Stable upload requires sdk-version.txt without -SNAPSHOT.")
        }
    }
}

private fun readPublishingSecrets(repositoryRoot: Path): PublishingSecrets {
    val storePassword = readStorePassword()
    try {
        return readPublishingSecrets(repositoryRoot, storePassword)
    } finally {
        storePassword.fill('\u0000')
    }
}

private fun readStorePassword(): CharArray {
    val buffer = CharArray(MAX_STORE_PASSWORD_LENGTH)
    var length = 0
    try {
        while (true) {
            val value = System.`in`.read()
            when {
                value == -1 -> throw GradleException(STORE_UNLOCK_FAILURE)
                value == '\n'.code -> break
                value !in '!'.code..'~'.code -> throw GradleException(STORE_UNLOCK_FAILURE)
                length == MAX_STORE_PASSWORD_LENGTH -> throw GradleException(STORE_UNLOCK_FAILURE)
                else -> buffer[length++] = value.toChar()
            }
        }
        if (length < MIN_STORE_PASSWORD_LENGTH || System.`in`.read() != -1) {
            throw GradleException(STORE_UNLOCK_FAILURE)
        }
        return buffer.copyOf(length)
    } finally {
        buffer.fill('\u0000')
    }
}

private fun readPublishingSecrets(
    repositoryRoot: Path,
    storePassword: CharArray,
): PublishingSecrets {
    try {
        val keyStore = KeyStore.getInstance("PKCS12")
        val storePath = repositoryRoot.resolve(PUBLISHING_STORE_FILE)
        if (!storePath.isRegularFile(LinkOption.NOFOLLOW_LINKS)) {
            throw IllegalArgumentException("Invalid publishing store")
        }
        verifyOwnerOnlyPermissions(storePath)
        Files.newByteChannel(storePath, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            if (channel.size() !in 1..MAX_PUBLISHING_STORE_SIZE) {
                throw IllegalArgumentException("Invalid publishing store")
            }
            Channels.newInputStream(channel).use { input -> keyStore.load(input, storePassword) }
        }
        if (keyStore.aliases().toList().toSet() != publishingStoreAliases) {
            throw IllegalArgumentException("Invalid publishing store")
        }

        val protection = KeyStore.PasswordProtection(storePassword)
        try {
            return PublishingSecrets(
                centralUsername = keyStore.readUtf8Secret(CENTRAL_USERNAME_ALIAS, protection),
                centralPassword = keyStore.readUtf8Secret(CENTRAL_PASSWORD_ALIAS, protection),
                signingKey = keyStore.readUtf8Secret(SIGNING_KEY_ALIAS, protection),
                signingKeyPassword = keyStore.readUtf8Secret(SIGNING_KEY_PASSWORD_ALIAS, protection),
                signingKeyId = keyStore.readUtf8Secret(SIGNING_KEY_ID_ALIAS, protection),
            )
        } finally {
            protection.destroy()
        }
    } catch (_: Exception) {
        throw GradleException(STORE_UNLOCK_FAILURE)
    }
}

private fun verifyOwnerOnlyPermissions(path: Path) {
    val posixView = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
    if (posixView != null) {
        require(posixView.readAttributes().permissions() == PosixFilePermissions.fromString("rw-------"))
        return
    }

    val aclView = Files.getFileAttributeView(path, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
        ?: throw IllegalArgumentException("Owner-only permissions are unavailable")
    val owner = path.getOwner(LinkOption.NOFOLLOW_LINKS)
    val allowedEntries = aclView.acl.filter { it.type() == AclEntryType.ALLOW }
    require(allowedEntries.isNotEmpty() && allowedEntries.all { it.principal() == owner })
}

private fun KeyStore.readUtf8Secret(
    alias: String,
    protection: KeyStore.PasswordProtection,
): String {
    val entry = getEntry(alias, protection) as? KeyStore.SecretKeyEntry
        ?: throw IllegalArgumentException("Invalid publishing store")
    val secretKey = entry.secretKey
    if (!secretKey.algorithm.equals("HmacSHA256", ignoreCase = true) || !secretKey.format.equals("RAW", ignoreCase = true)) {
        throw IllegalArgumentException("Invalid publishing store")
    }
    val encoded = secretKey.encoded ?: throw IllegalArgumentException("Invalid publishing store")
    try {
        val value = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(encoded))
            .toString()
        if (value.isBlank()) throw IllegalArgumentException("Invalid publishing store")
        return value
    } finally {
        encoded.fill(0)
    }
}
