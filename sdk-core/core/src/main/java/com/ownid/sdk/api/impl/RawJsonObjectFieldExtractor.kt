package com.ownid.sdk.api.impl

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Extracts raw top-level JSON fields that public domain models must preserve without generated-model normalization.
 *
 * Auth Server responses can contain provider-owned JSON blobs such as `sessionPayload`. Those values are SDK pass-through data for the host app, so the API
 * layer validates the envelope with generated models and then preserves the original field value without converting it to a generated DTO.
 */
internal object RawJsonObjectFieldExtractor {

    @Throws(SerializationException::class)
    internal fun extractRequiredTopLevelRawValue(json: String, fieldName: String): String =
        extractTopLevelRawValue(json, fieldName) ?: throw SerializationException("Missing required field \"$fieldName\".")

    private fun extractTopLevelRawValue(json: String, fieldName: String): String? {
        var index = skipWhitespace(json, 0)

        if (index >= json.length || json[index] != '{') {
            throw SerializationException("Expected root JSON object.")
        }
        index++

        index = skipWhitespace(json, index)
        if (index < json.length && json[index] == '}') {
            index++
            ensureOnlyTrailingWhitespace(json, index)
            return null
        }

        while (true) {
            index = skipWhitespace(json, index)
            val keyTokenStart = index
            val afterKey = skipJsonStringToken(json, keyTokenStart)
            index = skipWhitespace(json, afterKey)

            if (index >= json.length || json[index] != ':') {
                throw SerializationException("Expected ':' after key.")
            }
            index++
            index = skipWhitespace(json, index)

            val valueStart = index
            val valueEnd = skipJsonValue(json, valueStart)

            if (matchesUnescapedKeyToken(json, keyTokenStart = keyTokenStart, keyTokenEnd = afterKey, expected = fieldName)) {
                return normalizeExtractedValue(json, valueStart, valueEnd)
            }

            index = skipWhitespace(json, valueEnd)

            if (index >= json.length) {
                throw SerializationException("Unexpected end of JSON object.")
            }

            when (json[index]) {
                ',' -> index++
                '}' -> {
                    index++
                    ensureOnlyTrailingWhitespace(json, index)
                    return null
                }

                else -> throw SerializationException("Expected ',' or '}' after value.")
            }
        }
    }

    private fun normalizeExtractedValue(json: String, valueStart: Int, valueEnd: Int): String {
        if (json[valueStart] != '"') {
            return json.substring(valueStart, valueEnd)
        }

        return Json.decodeFromString(String.serializer(), json.substring(valueStart, valueEnd))
    }

    private fun skipJsonValue(json: String, start: Int): Int {
        if (start >= json.length) {
            throw SerializationException("Unexpected end of JSON while reading value.")
        }

        return when (json[start]) {
            '"' -> skipJsonStringToken(json, start)
            '{' -> skipNested(json, start, '{', '}')
            '[' -> skipNested(json, start, '[', ']')
            't' -> consumeLiteral(json, start, "true")
            'f' -> consumeLiteral(json, start, "false")
            'n' -> consumeLiteral(json, start, "null")
            '-', in '0'..'9' -> skipJsonNumber(json, start)
            else -> throw SerializationException("Unsupported JSON value at index $start.")
        }
    }

    private fun skipJsonStringToken(json: String, start: Int): Int {
        var index = start
        if (index >= json.length || json[index] != '"') {
            throw SerializationException("Expected JSON string token at index $start.")
        }
        index++
        while (index < json.length) {
            when (val current = json[index]) {
                '"' -> return index + 1
                '\\' -> {
                    index++
                    if (index >= json.length) {
                        throw SerializationException("Invalid escape sequence in JSON string token.")
                    }
                    if (json[index] == 'u') {
                        if (index + 4 >= json.length) {
                            throw SerializationException("Invalid unicode escape sequence in JSON string token.")
                        }
                        index += 4
                    }
                }

                else -> if (current.code < 0x20) {
                    throw SerializationException("Unescaped control character in JSON string token.")
                }
            }
            index++
        }
        throw SerializationException("Unterminated JSON string token.")
    }

    private fun matchesUnescapedKeyToken(json: String, keyTokenStart: Int, keyTokenEnd: Int, expected: String): Boolean {
        var source = keyTokenStart + 1
        var target = 0
        val sourceEnd = keyTokenEnd - 1

        while (source < sourceEnd) {
            val ch = json[source]
            if (ch == '\\') return false
            if (target >= expected.length || ch != expected[target]) return false
            source++
            target++
        }
        return target == expected.length
    }

    private fun skipNested(json: String, start: Int, open: Char, close: Char): Int {
        var index = start + 1
        var depth = 1
        var inString = false
        var escaped = false

        while (index < json.length) {
            val current = json[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (current == '\\') {
                    escaped = true
                } else if (current == '"') {
                    inString = false
                }
                index++
                continue
            }

            when (current) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) {
                        return index + 1
                    }
                }
            }
            index++
        }

        throw SerializationException("Unterminated nested JSON value.")
    }

    private fun consumeLiteral(json: String, start: Int, literal: String): Int {
        if (!json.regionMatches(start, literal, 0, literal.length)) {
            throw SerializationException("Invalid JSON literal at index $start.")
        }
        return start + literal.length
    }

    private fun skipJsonNumber(json: String, start: Int): Int {
        var index = start

        if (json[index] == '-') index++
        if (index >= json.length) throw SerializationException("Invalid JSON number at index $start.")

        if (json[index] == '0') {
            index++
        } else {
            if (json[index] !in '1'..'9') {
                throw SerializationException("Invalid JSON number at index $start.")
            }
            index++
            while (index < json.length && json[index] in '0'..'9') index++
        }

        if (index < json.length && json[index] == '.') {
            index++
            if (index >= json.length || json[index] !in '0'..'9') {
                throw SerializationException("Invalid JSON number at index $start.")
            }
            while (index < json.length && json[index] in '0'..'9') index++
        }

        if (index < json.length && (json[index] == 'e' || json[index] == 'E')) {
            index++
            if (index < json.length && (json[index] == '+' || json[index] == '-')) index++
            if (index >= json.length || json[index] !in '0'..'9') {
                throw SerializationException("Invalid JSON number at index $start.")
            }
            while (index < json.length && json[index] in '0'..'9') index++
        }

        return index
    }

    private fun ensureOnlyTrailingWhitespace(json: String, fromIndex: Int) {
        val tail = skipWhitespace(json, fromIndex)
        if (tail != json.length) {
            throw SerializationException("Unexpected trailing characters after root JSON object.")
        }
    }

    private fun skipWhitespace(json: String, start: Int): Int {
        var index = start
        while (index < json.length) {
            when (json[index]) {
                ' ', '\n', '\r', '\t' -> index++
                else -> return index
            }
        }
        return index
    }
}
