package com.ownid.sdk.ui.webbridge.impl

import java.net.URI

/**
 * Normalizes WebBridge origin rules into the string format accepted by AndroidX WebView origin allowlists.
 *
 * Rules may be full HTTP(S) origins, schemeless host rules that default to `https`, host rules with explicit ports,
 * DNS-only wildcard rules such as `*.example.com`, bracketed IPv6 literals, or the global `*` wildcard. Paths, query
 * strings, fragments, userinfo, empty or malformed ports, trailing-dot hosts, zone-scoped IPv6 literals, and wildcard
 * rules for IP hosts are skipped before any rule reaches WebView.
 *
 * Page URLs are stricter about intent: they must already be absolute HTTP(S) URLs and are reduced to their origin by
 * discarding path, query, and fragment while preserving a valid explicit port.
 */
internal object OriginNormalizer {

    /**
     * Result of origin rule normalization.
     *
     * [valid] preserves first-seen order after normalization and de-duplicates equivalent rules. [skipped] preserves
     * the original rejected rule text so callers can log actionable diagnostics without exposing normalized guesses.
     */
    data class NormalizationResult(
        val valid: Set<String>,
        val skipped: List<String>
    )

    private enum class HostType { DNS, IPV4, IPV6 }

    private val ipv4PartPattern = Regex("^\\d{1,3}$")
    private val digitsAndDotsPattern = Regex("^[0-9.]+$")
    private val dnsLabelPattern = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    /**
     * Normalizes configured allowlist rules for AndroidX WebView injection and message delivery.
     *
     * Invalid entries do not fail the whole set; callers decide whether an empty [NormalizationResult.valid] set is a
     * fatal attachment error.
     */
    fun normalizeAllowedOriginRules(rules: Set<String>): NormalizationResult {
        val valid = linkedSetOf<String>()
        val skipped = mutableListOf<String>()

        for (rawRule in rules) {
            val normalized = normalizeAllowedOriginRuleOrNull(rawRule)
            if (normalized == null) skipped += rawRule else valid += normalized
        }

        return NormalizationResult(valid = valid, skipped = skipped)
    }

    /**
     * Extracts an HTTP(S) origin from an absolute page URL.
     *
     * This is used when an operation receives a concrete page URL and needs an equivalent origin rule. Relative URLs,
     * unsupported schemes, userinfo, malformed ports, and hosts rejected by [OriginNormalizer.normalizeHostOrNull] return `null`.
     */
    fun pageUrlToOriginOrNull(pageUrl: String): String? {
        val raw = pageUrl.trim()
        if (raw.isEmpty()) return null

        val parsed = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!parsed.isAbsolute) return null
        if (parsed.rawAuthority.isNullOrBlank()) return null
        if (parsed.rawAuthority.endsWith(":")) return null
        if (parsed.rawUserInfo != null) return null

        val scheme = parsed.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
        val host = parsed.host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizedHost = normalizeHostOrNull(host, wildcard = false) ?: return null
        val portPart = parsed.port.toNormalizedPortPart() ?: return null

        return "$scheme://$normalizedHost$portPart"
    }

    private fun normalizeAllowedOriginRuleOrNull(rawRule: String): String? {
        val raw = rawRule.trim()
        if (raw.isEmpty()) return null
        if (raw == "*") return "*"

        val withScheme = if ("://" in raw) raw else "https://$raw"
        val schemeDelimiterIndex = withScheme.indexOf("://")
        if (schemeDelimiterIndex <= 0) return null

        val scheme = withScheme.substring(0, schemeDelimiterIndex).lowercase()
            .takeIf { it == "http" || it == "https" } ?: return null

        val authorityRaw = withScheme.substring(schemeDelimiterIndex + 3)
        val wildcard = authorityRaw.startsWith("*.")
        val authority = if (wildcard) authorityRaw.removePrefix("*.") else authorityRaw
        if (authority.isEmpty()) return null
        if ('*' in authority) return null

        val parsed = runCatching { URI("$scheme://$authority") }.getOrNull() ?: return null
        if (parsed.rawAuthority.isNullOrBlank()) return null
        if (parsed.rawAuthority.endsWith(":")) return null
        if (parsed.rawUserInfo != null) return null
        if (!parsed.rawPath.isNullOrEmpty()) return null
        if (parsed.rawQuery != null || parsed.rawFragment != null) return null

        val host = parsed.host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizedHost = normalizeHostOrNull(host, wildcard = wildcard) ?: return null
        val portPart = parsed.port.toNormalizedPortPart() ?: return null
        val wildcardPrefix = if (wildcard) "*." else ""

        return "$scheme://$wildcardPrefix$normalizedHost$portPart"
    }

    private fun normalizeHostOrNull(host: String, wildcard: Boolean): String? {
        val hostType = host.classifyHost() ?: return null
        if (wildcard && hostType != HostType.DNS) return null

        return when (hostType) {
            HostType.DNS, HostType.IPV4 -> host.lowercase()
            HostType.IPV6 -> "[${host.removeSurrounding("[", "]").lowercase()}]"
        }
    }

    private fun Int.toNormalizedPortPart(): String? = when (this) {
        -1 -> ""
        in 1..65535 -> ":$this"
        else -> null
    }

    private fun String.classifyHost(): HostType? {
        if (startsWith("[") && endsWith("]")) {
            val value = removeSurrounding("[", "]")
            if (value.isEmpty() || '%' in value) return null
            val parsedHost = runCatching { URI("https://[$value]") }.getOrNull()?.host ?: return null
            return if (parsedHost.equals("[$value]", ignoreCase = true)) HostType.IPV6 else null
        }

        if (contains(':') || endsWith(".")) return null
        if (digitsAndDotsPattern.matches(this)) return if (isValidIpv4()) HostType.IPV4 else null
        if (isValidIpv4()) return HostType.IPV4
        return if (isValidDnsHostname()) HostType.DNS else null
    }

    private fun String.isValidIpv4(): Boolean {
        val parts = split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            if (!ipv4PartPattern.matches(part)) return@all false
            if (part.length > 1 && part.startsWith("0")) return@all false
            part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun String.isValidDnsHostname(): Boolean {
        val normalized = lowercase()
        if (normalized.isEmpty() || normalized.length > 253) return false
        val labels = normalized.split('.')
        if (labels.any { it.isEmpty() }) return false
        return labels.all { dnsLabelPattern.matches(it) }
    }
}
