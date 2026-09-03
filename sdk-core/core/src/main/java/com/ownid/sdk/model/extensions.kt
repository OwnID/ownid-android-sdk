package com.ownid.sdk.model

import kotlinx.coroutines.CancellationException
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal fun String?.shorten(): String? {
    if (this == null) return null
    if (length <= 16) return this
    return "${take(8)}..[${length - 16}]...${takeLast(8)}"
}

internal fun String.maskId(): String {
    val s = this.trim()
    if (s.isEmpty()) return ""
    if (s.length <= 4) return "•••"
    val prefixLen = 2
    val suffixLen = 2
    val maskLen = s.length - prefixLen - suffixLen
    return buildString(s.length) {
        append(s, 0, prefixLen)
        repeat(maskLen) { append('•') }
        append(s, s.length - suffixLen, s.length)
    }
}

internal fun String.maskEmail(): String {
    if (!contains("@")) return this
    val (localPart, domainPart) = split("@", limit = 2)
    if (localPart.length in 1..3) return "***@$domainPart"
    val keepFirst = localPart.take(1)
    val endChar = localPart.takeLast(1)
    val middleSection = localPart.drop(1).dropLast(1)
    val maskSection = middleSection.replace(Regex("."), "*")
    return "$keepFirst$maskSection$endChar@$domainPart"
}

internal fun String.maskPhoneNumber(): String {
    val e164Regex = Regex("^\\+?[1-9]\\d{1,14}$")
    if (!e164Regex.matches(this)) return this
    val digits = removePrefix("+")
    if (digits.length <= 6) return if (startsWith("+")) "+***" else "***"
    val masked = buildString(digits.length) {
        append(digits, 0, 2)
        repeat(digits.length - 6) { append('*') }
        append(digits, digits.length - 4, digits.length)
    }
    return if (startsWith("+")) "+$masked" else masked
}

internal fun secureRandom(size: Int = 16): ByteArray = ByteArray(size).apply { SecureRandom().nextBytes(this) }

@OptIn(ExperimentalEncodingApi::class)
internal fun ByteArray.encodeToBase64UrlSafe(noPadding: Boolean = true): String =
    Base64.UrlSafe.encode(this).let { encoded ->
        if (noPadding) encoded.trimEnd('=') else encoded
    }

private val base64UrlWithOptionalPaddingRegex = Regex("^[A-Za-z0-9_-]*={0,2}$")

@OptIn(ExperimentalEncodingApi::class)
internal fun String.decodeBase64UrlToByteArray(): ByteArray {
    require(base64UrlWithOptionalPaddingRegex.matches(this)) { "Invalid base64url value" }
    val unpadded = trimEnd('=')
    require(unpadded.length % 4 != 1) { "Invalid base64url length" }
    require(length == unpadded.length || length % 4 == 0) { "Invalid base64url padding" }

    val padded = when (unpadded.length % 4) {
        0 -> unpadded
        2 -> "$unpadded=="
        3 -> "$unpadded="
        else -> error("Unreachable base64url length")
    }
    return Base64.UrlSafe.decode(padded)
}

/** Captures regular [Exception] failures as [Result.failure] while preserving coroutine cancellation. */
internal inline fun <R> runCatchingExceptions(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

/** Captures regular [Exception] failures as [Result.failure] while preserving coroutine cancellation. */
internal inline fun <T, R> T.runCatchingExceptions(block: T.() -> R): Result<R> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
