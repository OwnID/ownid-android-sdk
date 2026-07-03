package com.ownid.sdk.compose.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Covers: UI-140
internal class TextNormalizationContractTest {

    @Test
    fun asciiDigitOrNullKeepsAsciiDigitsAndRejectsNonDigits() {
        assertThatObject('0'.asciiDigitOrNull()).isEqualTo('0')
        assertThatObject('9'.asciiDigitOrNull()).isEqualTo('9')
        assertThatObject(' '.asciiDigitOrNull()).isNull()
        assertThatObject('-'.asciiDigitOrNull()).isNull()
        assertThatObject('A'.asciiDigitOrNull()).isNull()
    }

    @Test
    fun asciiDigitOrNullNormalizesUnicodeDecimalDigits() {
        assertThatObject('１'.asciiDigitOrNull()).isEqualTo('1')
        assertThatObject('٢'.asciiDigitOrNull()).isEqualTo('2')
        assertThatObject('۳'.asciiDigitOrNull()).isEqualTo('3')
        assertThatObject('४'.asciiDigitOrNull()).isEqualTo('4')
    }

    @Test
    fun asciiDigitOrNullRejectsNumericNonDecimalCharacters() {
        assertThatObject('²'.asciiDigitOrNull()).isNull()
        assertThatObject('Ⅷ'.asciiDigitOrNull()).isNull()
    }

    @Test
    fun normalizeDigitsDropsSeparatorsAndStopsAtPositiveLimit() {
        val rawText = "A１ ٢-۳\n४5۶٧۸90"

        assertThat(rawText.normalizeDigits(limit = 6)).isEqualTo("123456")
        assertThat(rawText.normalizeDigits(limit = 10)).isEqualTo("1234567890")
        assertThat("abc- ".normalizeDigits(limit = 4)).isEmpty()
    }

    private fun assertThatObject(actual: Any?) = assertThat(actual)
}
