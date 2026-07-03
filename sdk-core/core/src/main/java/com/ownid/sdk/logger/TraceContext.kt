package com.ownid.sdk.logger

import com.ownid.sdk.model.secureRandom

/**
 * Creates and advances `traceparent` values for SDK network requests.
 *
 * New contexts use version `00`, a non-zero 16-byte trace ID, a non-zero 8-byte parent ID, and trace flags `01`.
 * Existing values are accepted only when they contain four hexadecimal fields with lengths 2/32/16/2, a version other
 * than `ff`, and non-zero trace and parent IDs. Accepted values keep their version, trace ID, and trace flags while
 * rotating the parent ID for each outbound send attempt.
 */
internal object TraceContext {

    /** Parsed trace seed reused across retry attempts for one logical request. */
    internal data class Seed(
        val version: String,
        val traceId: String,
        val traceFlags: String
    )

    /** Generates a complete `traceparent` value for a new logical request. */
    fun generateTraceparent(): String {
        return nextTraceparent(generateSeed())
    }

    /** Returns a parsed seed from [traceparent], or a new seed when the value is missing or invalid. */
    fun resolveSeed(traceparent: String?): Seed = parseSeedOrNull(traceparent) ?: generateSeed()

    /** Builds the next `traceparent` value by preserving [seed] and generating a new parent ID. */
    fun nextTraceparent(seed: Seed): String {
        val parentId = nonZeroRandomBytes(8).toHex()
        return "${seed.version}-${seed.traceId}-$parentId-${seed.traceFlags}"
    }

    private fun generateSeed(): Seed = Seed(
        version = "00",
        traceId = nonZeroRandomBytes(16).toHex(),
        traceFlags = "01"
    )

    private fun parseSeedOrNull(traceparent: String?): Seed? {
        val raw = traceparent ?: return null
        val parts = raw.split('-')
        if (parts.size != 4) return null

        val version = parts[0].lowercase()
        val traceId = parts[1].lowercase()
        val parentId = parts[2].lowercase()
        val traceFlags = parts[3].lowercase()

        if (version.length != 2 || traceId.length != 32 || parentId.length != 16 || traceFlags.length != 2) return null
        if (!version.isLowerHex() || !traceId.isLowerHex() || !parentId.isLowerHex() || !traceFlags.isLowerHex()) return null
        if (version == "ff") return null
        if (traceId.isAllZeroHex() || parentId.isAllZeroHex()) return null

        return Seed(
            version = version,
            traceId = traceId,
            traceFlags = traceFlags
        )
    }

    private tailrec fun nonZeroRandomBytes(size: Int): ByteArray {
        val bytes = secureRandom(size)
        return if (bytes.any { it != 0.toByte() }) bytes else nonZeroRandomBytes(size)
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (b in this@toHex) {
            append(((b.toInt() ushr 4) and 0xF).toHexChar())
            append((b.toInt() and 0xF).toHexChar())
        }
    }

    private fun Int.toHexChar(): Char = "0123456789abcdef"[this]

    private fun String.isLowerHex(): Boolean = all { (it in '0'..'9') || (it in 'a'..'f') }

    private fun String.isAllZeroHex(): Boolean = all { it == '0' }
}
