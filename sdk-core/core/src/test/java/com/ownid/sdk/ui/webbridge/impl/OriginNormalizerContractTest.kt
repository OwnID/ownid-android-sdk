package com.ownid.sdk.ui.webbridge.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.model.InternalOwnIdAPI
import org.junit.Test

@OptIn(InternalOwnIdAPI::class)
// Covers: WB-010, WB-020, WB-030
internal class OriginNormalizerContractTest {

    @Test
    fun allowedOriginRulesNormalizeAndDeduplicateInFirstSeenOrder() {
        val result = OriginNormalizer.normalizeAllowedOriginRules(
            linkedSetOf(
                " Example.COM ",
                "https://EXAMPLE.com",
                "http://Sub.Example.com:8080",
                "*.Example.com",
                "[2001:DB8::1]",
                "192.168.1.10"
            )
        )

        assertThat(result.valid).containsExactly(
            "https://example.com",
            "http://sub.example.com:8080",
            "https://*.example.com",
            "https://[2001:db8::1]",
            "https://192.168.1.10"
        ).inOrder()
        assertThat(result.skipped).isEmpty()
    }

    @Test
    fun allowedOriginRulesSkipUnsafeOrMalformedInputsWithoutDroppingValidRules() {
        val result = OriginNormalizer.normalizeAllowedOriginRules(
            linkedSetOf(
                "*",
                "https://user@example.com",
                "example.com/path",
                "https://example.com?",
                "*.192.168.0.1",
                "bad..host",
                "https://example.com:",
                "https://valid.example"
            )
        )

        assertThat(result.valid).containsExactly("*", "https://valid.example").inOrder()
        assertThat(result.skipped).containsExactly(
            "https://user@example.com",
            "example.com/path",
            "https://example.com?",
            "*.192.168.0.1",
            "bad..host",
            "https://example.com:"
        ).inOrder()
    }

    @Test
    fun pageUrlToOriginRequiresAbsoluteHttpOriginAndDropsPathQueryAndFragment() {
        assertThat(OriginNormalizer.pageUrlToOriginOrNull("https://Example.COM:443/path?q=1#fragment")).isEqualTo("https://example.com:443")
        assertThat(OriginNormalizer.pageUrlToOriginOrNull("HTTP://[2001:DB8::1]:8080/path")).isEqualTo("http://[2001:db8::1]:8080")
        assertThat(OriginNormalizer.pageUrlToOriginOrNull("example.com/path")).isNull()
        assertThat(OriginNormalizer.pageUrlToOriginOrNull("ftp://example.com/path")).isNull()
        assertThat(OriginNormalizer.pageUrlToOriginOrNull("https://user@example.com/path")).isNull()
        assertThat(OriginNormalizer.pageUrlToOriginOrNull("https://example.com:")).isNull()
    }
}
