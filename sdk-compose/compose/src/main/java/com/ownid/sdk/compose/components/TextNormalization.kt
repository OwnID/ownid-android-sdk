package com.ownid.sdk.compose.components

/**
 * Returns this character as an ASCII decimal digit when SDK verification fields should accept it.
 *
 * Unicode decimal digits with values `0` through `9` are normalized to ASCII `0`..`9`. Other characters return `null`
 * so callers can ignore separators, whitespace, letters, and unsupported numeric symbols.
 */
internal fun Char.asciiDigitOrNull(): Char? = when {
    this in '0'..'9' -> this
    Character.isDigit(this) -> {
        val v = Character.getNumericValue(this)
        if (v in 0..9) ('0' + v) else null
    }

    else -> null
}

/**
 * Returns normalized OTP text for SDK verification fields.
 *
 * The result contains only ASCII decimal digits and keeps at most [limit] accepted digits. Callers pass the positive
 * challenge OTP length as [limit]; submission remains owned by the operation UI that observes the normalized text.
 */
internal fun String.normalizeDigits(limit: Int): String = buildString(limit) {
    for (ch in this@normalizeDigits) {
        val d = ch.asciiDigitOrNull() ?: continue
        append(d)
        if (length == limit) break
    }
}
