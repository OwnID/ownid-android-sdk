package com.ownid.sdk.capability.impl

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.PasskeyDiagnostics
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.runCatchingExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Best-effort runtime diagnostics for Android passkey relying-party configuration.
 *
 * Verification runs at most once per normalized RP ID in the provided instance scope. It reports signing-certificate,
 * Digital Asset Links, robots.txt, and Google DAL API findings through the SDK logger only; diagnostics do not gate or
 * authorize passkey operations.
 */
@InternalOwnIdAPI
internal class PasskeyDiagnosticsImpl(
    private val context: Context,
    private val localInfo: LocalInfo,
    private val scope: CoroutineScope,
    private val logger: OwnIdLogRouter?,
) : PasskeyDiagnostics {

    private companion object {
        private val FINGERPRINT_REGEX = Regex("^([A-Fa-f0-9]{2}:){31}[A-Fa-f0-9]{2}$")
        private val IPV4_REGEX = Regex("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$")
        private val PORT_REGEX = Regex(":\\d+$")
        private val LABEL_REGEX = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")

        private const val MAX_INCLUDE_DEPTH = 10
        private const val REL_GET_LOGIN_CREDS = "delegate_permission/common.get_login_creds"
        private const val REL_HANDLE_ALL_URLS = "delegate_permission/common.handle_all_urls"
    }

    private enum class Status { PASS, FAIL, WARN, SKIP }

    private data class StepResult(
        val name: String,
        val status: Status,
        val reason: String? = null,
        val details: Map<String, String> = emptyMap()
    )

    private data class AssetLinksBundle(val primary: StepResult, val includes: List<StepResult>, val allStatements: List<Statement>)

    @Serializable
    private data class Statement(val relation: List<String> = emptyList(), val target: Target? = null, val include: String? = null)

    @Serializable
    private data class Target(
        val namespace: String? = null,
        @SerialName("package_name") val packageName: String? = null,
        @SerialName("sha256_cert_fingerprints") val sha256CertFingerprints: List<String>? = null,
        val site: String? = null,
    )

    @Serializable
    private data class DalCheck(val linked: Boolean, val debugString: String? = null)

    @OptIn(ExperimentalSerializationApi::class)
    private val diagnosticsJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
        prettyPrint = false
    }

    private val verifiedRpIdsLock = Any()
    private val verifiedRpIds = mutableSetOf<String>()

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun Map<String, List<String>>.firstHeader(name: String): List<String> =
        entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()

    private fun Network.Response.Success.contentTypeHeaderIsJson(): Boolean =
        headers.firstHeader(Network.Request.Header.CONTENT_TYPE).joinToString().lowercase().startsWith("application/json")

    override fun verify(rpId: String) {
        val key = rpId.lowercase()
        val shouldVerify = synchronized(verifiedRpIdsLock) { verifiedRpIds.add(key) }
        if (!shouldVerify) {
            logger?.logV(this, "verify", "Skipping duplicate passkey diagnostics for rpId=$key")
            return
        }

        scope.launch {
            val results = mutableListOf<StepResult>()

            val fingerprints = getShaFingerprints()
            results += if (fingerprints.isEmpty()) {
                StepResult("Runtime signing certs", Status.FAIL, "No signing certificates discovered at runtime")
            } else {
                StepResult("Runtime signing certs", Status.PASS, details = mapOf("fingerprints" to fingerprints.joinToString()))
            }

            results += verifyRpId(rpId)

            results += verifyRobotsTxt(robotsUrl = "https://$rpId/robots.txt")

            val (assetFetchStep, body) = fetchAssetLinks("https://$rpId/.well-known/assetlinks.json", "Fetch asset links")
            results += assetFetchStep

            if (assetFetchStep.status == Status.PASS && body != null) {
                val assetBundle = parseAndVerifyAssetLinks(body)

                results += assetBundle.primary
                results += assetBundle.includes

                results += verifyAndroidLinkage(assetBundle.allStatements, fingerprints, REL_GET_LOGIN_CREDS, "Android linkage (passkeys)")
                results += verifyAndroidLinkage(assetBundle.allStatements, fingerprints, REL_HANDLE_ALL_URLS, "Android linkage (AppLinks)")
            }

            results += verifyDalLinkage(rpId, fingerprints, REL_GET_LOGIN_CREDS, "Passkeys")
            results += verifyDalLinkage(rpId, fingerprints, REL_HANDLE_ALL_URLS, "AppLinks")

            val report = buildReport(results)

            logger?.logW(this, "verify", report)
        }
    }

    @Suppress("DEPRECATION")
    private fun getShaFingerprints(): Set<String> = runCatchingExceptions {
        val signatures: List<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            pkgInfo.signingInfo?.apkContentsSigners?.toList().orEmpty() + pkgInfo.signingInfo?.signingCertificateHistory?.toList().orEmpty()
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.toList().orEmpty()
        }

        val certificateFactory = CertificateFactory.getInstance("X.509")
        val messageDigest = MessageDigest.getInstance("SHA-256")

        signatures.asSequence().mapNotNull { sig ->
            runCatchingExceptions {
                val cert = certificateFactory.generateCertificate(ByteArrayInputStream(sig.toByteArray())) as X509Certificate
                messageDigest.digest(cert.encoded).joinToString(":") { "%02X".format(it) }
            }.getOrNull()
        }.toSet()
    }.getOrDefault(emptySet())

    private fun verifyRpId(rpId: String): StepResult {
        fun fail(cause: String): StepResult =
            StepResult("Verify rpId", Status.FAIL, "Invalid rpId", mapOf("cause" to cause, "rpId" to rpId))

        if (rpId.isEmpty()) return fail("Empty rpId")
        if (rpId.any(Char::isWhitespace)) return fail("Contains whitespace")

        val preflights = listOf("://" to "Contains scheme", "@" to "Contains userinfo", "/" to "Contains path")
        for ((token, cause) in preflights) if (rpId.contains(token)) return fail(cause)

        if (!rpId.startsWith("[") && PORT_REGEX.containsMatchIn(rpId)) return fail("Contains port")

        val url = ("https://$rpId/").toHttpUrlOrNull() ?: return fail("Malformed host")
        val host = url.host

        if (IPV4_REGEX.matches(host) || host.contains(':')) return fail("Host is IP address")

        val fqdn = host.trimEnd('.')
        if (fqdn.length > 253) return fail("Host too long")

        val labels = fqdn.split('.')
        if (labels.any { it.isEmpty() }) return fail("Host has empty label")
        for (label in labels) {
            if (!LABEL_REGEX.matches(label)) return fail("Host has invalid label")
        }

        return StepResult("Verify rpId", Status.PASS, details = mapOf("rpId" to rpId))
    }

    private suspend fun verifyRobotsTxt(robotsUrl: String): StepResult {
        val request = Request.Builder()
            .url(robotsUrl)
            .get()
            .header("User-Agent", localInfo.userAgent)
            .header("Accept", "application/json, text/plain")
            .header("Accept-Language", "en")
            .build()

        return when (val response = client.newCall(request).executeAsync()) {
            is Network.Response.Success -> {
                val allowed = robotsAllowsAssetLinks(response.body)
                StepResult(
                    "Verify robots.txt",
                    if (allowed) Status.PASS else Status.FAIL,
                    if (allowed) null else "Robots denies assetlinks.json",
                    if (allowed) emptyMap() else mapOf(
                        "code" to "${response.code}", "message" to "Disallow for /.well-known/assetlinks.json", "url" to robotsUrl
                    )
                )
            }

            is Network.Response.Fail.HttpError -> if (response.statusCode == 404)
                StepResult("Verify robots.txt", Status.PASS, details = mapOf("code" to "${response.statusCode}", "url" to robotsUrl))
            else
                StepResult("Verify robots.txt", Status.WARN, "Network error", mapOf("code" to "${response.statusCode}", "url" to robotsUrl))

            is Network.Response.Fail.NetworkError ->
                StepResult("Verify robots.txt", Status.WARN, "Network error", mapOf("error" to "$response", "url" to robotsUrl))

            is Network.Response.Fail.ResponseError ->
                StepResult("Verify robots.txt", Status.WARN, "Network error", mapOf("error" to "$response", "url" to robotsUrl))
        }
    }

    private fun robotsAllowsAssetLinks(body: String): Boolean {
        val target = "/.well-known/assetlinks.json"
        var inGroup = false
        var inStarGroup = false
        var bestType: String? = null
        var bestLen = -1

        for (raw in body.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val colon = line.indexOf(':')
            if (colon <= 0) continue

            val key = line.take(colon).trim().lowercase()
            var value = line.substring(colon + 1).trim()
            val hash = value.indexOf('#')
            if (hash >= 0) value = value.take(hash).trim()
            if (value.isEmpty()) continue

            when (key) {
                "user-agent" -> {
                    val ua = value.lowercase()
                    if (!inGroup) {
                        inGroup = true
                        inStarGroup = (ua == "*")
                    } else {
                        if (inStarGroup) break
                        inStarGroup = (ua == "*")
                    }
                }

                "allow", "disallow" -> {
                    if (!inGroup || !inStarGroup) continue
                    val exact = value.endsWith("$")
                    val pattern = if (exact) value.dropLast(1) else value

                    if (exact) {
                        if (target == pattern) return key != "disallow"
                    } else if (target.startsWith(pattern)) {
                        val len = pattern.length
                        if (len > bestLen || (len == bestLen && key == "allow")) {
                            bestLen = len
                            bestType = key
                        }
                    }
                }
            }
        }
        return bestType != "disallow"
    }

    private suspend fun fetchAssetLinks(url: String, stepName: String): Pair<StepResult, String?> {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", localInfo.userAgent)
            .header("Accept", "application/json, text/plain")
            .header("Accept-Language", "en")
            .build()

        return when (val response = client.newCall(request).executeAsync()) {
            is Network.Response.Success -> when {
                response.code != 200 ->
                    StepResult(stepName, Status.FAIL, "Non-200 response code", mapOf("url" to url, "code" to "${response.code}")) to null

                !response.contentTypeHeaderIsJson() ->
                    StepResult(
                        stepName,
                        Status.FAIL,
                        "Wrong Content-Type",
                        mapOf(
                            "url" to url,
                            "contentType" to response.headers.firstHeader(Network.Request.Header.CONTENT_TYPE).joinToString()
                        )
                    ) to null

                else ->
                    StepResult(stepName, Status.PASS, details = mapOf("url" to url, "code" to "${response.code}")) to response.body
            }

            is Network.Response.Fail.HttpError ->
                StepResult(stepName, Status.FAIL, "Network error", mapOf("url" to url, "code" to "${response.statusCode}")) to null

            is Network.Response.Fail.NetworkError ->
                StepResult(stepName, Status.FAIL, "Network error", mapOf("url" to url, "error" to "$response")) to null

            is Network.Response.Fail.ResponseError ->
                StepResult(stepName, Status.FAIL, "Network error", mapOf("url" to url, "error" to "$response")) to null
        }
    }

    private suspend fun parseAndVerifyAssetLinks(body: String): AssetLinksBundle {
        val includeResults = mutableListOf<StepResult>()
        return runCatchingExceptions {
            val top: List<Statement> = diagnosticsJson.decodeFromString<List<Statement>>(body)
            val allStatements = mutableListOf<Statement>()
            val visited = mutableSetOf<String>()
            var includeCount = 0

            allStatements += top

            val queue: ArrayDeque<Pair<String, Int>> = ArrayDeque()
            for (statement in top) {
                val includedStatement = statement.include ?: continue
                queue.add(includedStatement to 0)
            }

            while (queue.isNotEmpty()) {
                val (includeUrl, depth) = queue.removeFirst()
                if (includeCount >= MAX_INCLUDE_DEPTH) {
                    includeResults += StepResult("Parse & verify assetlinks.json: include limit", Status.WARN, "Include limit reached")
                    break
                }
                includeCount++

                val includeStepName = "Parse & verify assetlinks.json: include#${includeResults.size + 1}"
                if (!includeUrl.startsWith("https://", ignoreCase = true)) {
                    includeResults += StepResult(includeStepName, Status.WARN, "Non-HTTPS include ignored", mapOf("url" to includeUrl))
                    continue
                }

                if (!visited.add(includeUrl)) continue

                val (fetchStep, includeBody) = fetchAssetLinks(includeUrl, includeStepName)
                if (fetchStep.status != Status.PASS || includeBody == null) {
                    includeResults += fetchStep
                    continue
                }

                runCatchingExceptions { diagnosticsJson.decodeFromString<List<Statement>>(includeBody) }.onSuccess { includedStatements ->
                    allStatements += includedStatements
                    includeResults += StepResult(
                        includeStepName,
                        Status.PASS,
                        details = mapOf("url" to includeUrl, "statements" to includedStatements.size.toString()),
                    )
                    if (depth + 1 < MAX_INCLUDE_DEPTH) {
                        for (includedStatement in includedStatements) {
                            val url = includedStatement.include
                            if (url != null) queue.add(url to (depth + 1))
                        }
                    }
                }.onFailure {
                    includeResults += StepResult(
                        includeStepName,
                        Status.FAIL,
                        "JSON parse error",
                        mapOf("url" to includeUrl, "error" to it.message.orEmpty())
                    )
                }
            }

            var hasWhitespaceInFp = false
            var hasDuplicateFp = false

            val isFingerprintInvalid = allStatements.any { statement ->
                if (statement.target?.namespace != "android_app" || statement.target.packageName != context.packageName) return@any false
                val fingerprints = statement.target.sha256CertFingerprints ?: return@any true
                if (fingerprints.isEmpty()) return@any true

                val normalized = mutableSetOf<String>()
                var localDup = false
                var localWhitespace = false
                for (fingerprint in fingerprints) {
                    if (fingerprint != fingerprint.trim()) localWhitespace = true
                    if (!normalized.add(fingerprint.trim().uppercase())) localDup = true
                    if (!FINGERPRINT_REGEX.matches(fingerprint.trim())) return@any true
                }
                if (localDup) hasDuplicateFp = true
                if (localWhitespace) hasWhitespaceInFp = true
                false
            }

            val stepResult = if (isFingerprintInvalid) {
                StepResult(
                    "Parse & verify assetlinks.json",
                    Status.FAIL,
                    "Invalid or missing fingerprints",
                    mapOf(
                        "PackageName" to context.packageName,
                        "fingerprints" to allStatements.joinToString { it.target?.sha256CertFingerprints?.joinToString() ?: "" }
                    )
                )
            } else {
                StepResult(
                    "Parse & verify assetlinks.json",
                    if (hasWhitespaceInFp || hasDuplicateFp) Status.WARN else Status.PASS,
                    if (hasWhitespaceInFp || hasDuplicateFp) buildString {
                        if (hasWhitespaceInFp) append("Whitespace around fingerprints; ")
                        if (hasDuplicateFp) append("Duplicate fingerprints detected")
                    }.trim().trimEnd(';') else null
                )
            }

            AssetLinksBundle(stepResult, includeResults, allStatements.toList())
        }.getOrElse {
            val stepResult = StepResult(
                "Parse & verify assetlinks.json",
                Status.FAIL,
                "JSON parse error",
                mapOf("error" to it.message.orEmpty())
            )
            AssetLinksBundle(stepResult, includeResults, emptyList())
        }
    }

    private fun verifyAndroidLinkage(
        statements: List<Statement>,
        fingerprints: Set<String>,
        relation: String,
        stepName: String
    ): StepResult {
        val relationStatements = statements.filter { it.relation.any { r -> r == relation } }
        if (relationStatements.isEmpty()) {
            return StepResult(stepName, Status.FAIL, "No relation", mapOf("relation" to relation))
        }

        val androidStatements = relationStatements.filter { statement -> statement.target?.namespace == "android_app" }
        val packageMatchingTargets = androidStatements.filter { statement -> statement.target?.packageName == context.packageName }
        if (packageMatchingTargets.isEmpty()) {
            return StepResult(
                stepName,
                Status.FAIL,
                "Package mismatch",
                mapOf(
                    "current" to context.packageName,
                    "available" to androidStatements.mapNotNull { it.target?.packageName }.distinct().joinToString()
                )
            )
        }

        val fingerprintMatchingTargets = packageMatchingTargets.filter { statement ->
            val targetFingerprints = statement.target?.sha256CertFingerprints?.map { it.trim().uppercase() } ?: emptyList()
            targetFingerprints.any { it in fingerprints }
        }

        return if (fingerprintMatchingTargets.isNotEmpty()) {
            StepResult(
                stepName,
                Status.PASS,
                details = mapOf(
                    "package" to context.packageName,
                    "matchedFingerprint" to fingerprintMatchingTargets
                        .flatMap { it.target?.sha256CertFingerprints ?: emptyList() }
                        .firstOrNull { it.trim().uppercase() in fingerprints }.orEmpty()
                )
            )
        } else {
            StepResult(
                stepName,
                Status.FAIL,
                "Certificate mismatch",
                mapOf(
                    "current" to fingerprints.joinToString(),
                    "available" to packageMatchingTargets.flatMap { it.target?.sha256CertFingerprints ?: emptyList() }.joinToString()
                )
            )
        }
    }

    private suspend fun verifyDalLinkage(rpId: String, fingerprints: Set<String>, relation: String, stepLabel: String): List<StepResult> {
        val okHttpClient = client.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .build()

        return fingerprints.mapIndexed { idx, fingerprint ->
            var passCount = 0
            var failCount = 0

            val url = HttpUrl.Builder()
                .scheme("https")
                .host("digitalassetlinks.googleapis.com")
                .addPathSegments("v1/assetlinks:check")
                .addQueryParameter("source.web.site", "https://$rpId")
                .addQueryParameter("relation", relation)
                .addQueryParameter("target.android_app.package_name", context.packageName)
                .addQueryParameter("target.android_app.certificate.sha256_fingerprint", fingerprint)
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", localInfo.userAgent)
                .header("Accept", "application/json, text/plain")
                .header("Accept-Language", "en")
                .header("Connection", "close")
                .build()

            flow {
                repeat(5) {
                    val response = okHttpClient.newCall(request).executeAsync()
                    if (response is Network.Response.Success && response.code == 200) {
                        emit(runCatchingExceptions { diagnosticsJson.decodeFromString<DalCheck>(response.body) }.getOrNull())
                    } else {
                        emit(null)
                    }
                    delay(150.milliseconds)
                }
            }.collect { dalCheck ->
                if (dalCheck?.linked == true) passCount++ else failCount++
            }

            val status = when {
                passCount == 0 -> Status.FAIL
                failCount == 0 -> Status.PASS
                else -> Status.WARN
            }

            StepResult(
                "Verify DAL ($stepLabel) #${idx + 1}",
                status,
                when (status) {
                    Status.WARN -> "Inconsistent across servers"
                    Status.FAIL -> "Not linked"
                    else -> null
                },
                mapOf("fingerprint" to fingerprint, "pass" to "$passCount", "fail" to "$failCount")
            )
        }
    }

    private fun buildReport(results: List<StepResult>): String = buildString {
        append('\n')
        append("PasskeyDiagnostics: ")
        append(
            when {
                results.any { it.status == Status.FAIL } -> "FAIL"
                results.any { it.status == Status.WARN } -> "WARN"
                else -> "PASS"
            })
        append('\n')
        append("Env: Application packageName=").append(localInfo.packageName)
        append(", version=").append(localInfo.appVersion)
        append(", debuggable=").append(localInfo.isDebuggable)
        append('\n')
        append("Env: CorrelationId=").append(localInfo.correlationId)
        append('\n')
        append("Env: isSystemFidoCapable=").append(localInfo.isSystemFidoCapable)
        append(", isDeviceSecured=").append(localInfo.isDeviceSecured.toString())
        append(", isStrongBiometricEnabled=").append(localInfo.isStrongBiometricEnabled.toString())
        append(", isFingerprintHardwarePresent=").append(localInfo.isFingerprintHardwarePresent.toString())
        append(", isFaceHardwarePresent=").append(localInfo.isFaceHardwarePresent.toString())
        append(", isIrisHardwarePresent=").append(localInfo.isIrisHardwarePresent.toString())
        append('\n')

        results.forEach { result ->
            append('\n')
            append(result.status.name)
            append(" - ")
            append(result.name)
            result.reason?.let { append(" - ").append(it) }
            if (result.details.isNotEmpty()) {
                val kv = result.details.entries.joinToString(", ") { it.key + "=" + it.value }
                append(" [").append(kv).append(']')
            }
        }
    }
}
