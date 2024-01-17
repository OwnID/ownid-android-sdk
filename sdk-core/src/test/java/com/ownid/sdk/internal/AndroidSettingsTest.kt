package com.ownid.sdk.internal

import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.config.OwnIdServerConfiguration
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class AndroidSettingsTest {
    @Test
    public fun `fromResponse should parse JSON correctly SHA256`() {
        // Given
        val response = JSONObject(
            """
            {
              "androidSettings": {
                "packageName": "com.example.app",
                "certificateHashes": ["96:CB:FB:4A:0C:49:6E:DA:DA:28:0A:F3:3C:16:9C:54:8C:3B:3A:3F:47:DC:D8:7E:10:51:1B:C9:5D:FA:FC:E2",
                 "E2:1A:81:75:BF:CB:9D:47:9F:84:8D:09:10:C1:F5:39:CA:2D:90:81:44:28:9F:7A:F2:4A:ED:43:AE:5F:99:43"],
                "redirectUrlOverride": "https://example.com/redirect"
              }
            }
        """.trimIndent()
        )

        // When
        val androidSettings = OwnIdServerConfiguration.AndroidSettings.fromResponse(response)

        // Then
        Truth.assertThat("com.example.app").isEqualTo(androidSettings.packageName)
        Truth.assertThat(
            setOf(
                "96:CB:FB:4A:0C:49:6E:DA:DA:28:0A:F3:3C:16:9C:54:8C:3B:3A:3F:47:DC:D8:7E:10:51:1B:C9:5D:FA:FC:E2",
                "E2:1A:81:75:BF:CB:9D:47:9F:84:8D:09:10:C1:F5:39:CA:2D:90:81:44:28:9F:7A:F2:4A:ED:43:AE:5F:99:43"
            )
        ).isEqualTo(androidSettings.certificateHashes)
        Truth.assertThat("https://example.com/redirect").isEqualTo(androidSettings.redirectUrlOverride)
    }

    @Test
    public fun `fromResponse should parse JSON correctly SHA1`() {
        // Given
        val response = JSONObject(
            """
            {
              "androidSettings": {
                "packageName": "com.example.app",
                "certificateHashes": ["78:6F:3B:F9:49:23:20:23:CB:84:3E:BC:C7:CE:F3:EF:12:F1:2B:68",
                 "E2:1A:81:75:BF:CB:9D:47:9F:84:8D:09:10:C1:F5:39:CA:2D:90:81:44:28:9F:7A:F2:4A:ED:43:AE:5F:99:43"],
                "redirectUrlOverride": "https://example.com/redirect"
              }
            }
        """.trimIndent()
        )

        // When
        val androidSettings = OwnIdServerConfiguration.AndroidSettings.fromResponse(response)

        // Then
        Truth.assertThat("com.example.app").isEqualTo(androidSettings.packageName)
        Truth.assertThat(
            setOf(
                "78:6F:3B:F9:49:23:20:23:CB:84:3E:BC:C7:CE:F3:EF:12:F1:2B:68",
                "E2:1A:81:75:BF:CB:9D:47:9F:84:8D:09:10:C1:F5:39:CA:2D:90:81:44:28:9F:7A:F2:4A:ED:43:AE:5F:99:43"
            )
        ).isEqualTo(androidSettings.certificateHashes)
        Truth.assertThat("https://example.com/redirect").isEqualTo(androidSettings.redirectUrlOverride)
    }

    @Test
    public fun `fromResponse should parse JSON correctly bad redirect`() {
        // Given
        val response = JSONObject(
            """
            {
              "androidSettings": {
                "packageName": "com.example.app",
                "certificateHashes": ["E2:1A:81:75:BF:CB:9D:47:9F:84:8D:09:10:C1:F5:39:CA:2D:90:81:44:28:9F:7A:F2:4A:ED:43:AE:5F:99:43"],
                "redirectUrlOverride": "//example.com/redirect"
              }
            }
        """.trimIndent()
        )

        // When
        val androidSettings = OwnIdServerConfiguration.AndroidSettings.fromResponse(response)

        // Then
        Truth.assertThat("com.example.app").isEqualTo(androidSettings.packageName)
        Truth.assertThat(setOf("E2:1A:81:75:BF:CB:9D:47:9F:84:8D:09:10:C1:F5:39:CA:2D:90:81:44:28:9F:7A:F2:4A:ED:43:AE:5F:99:43"))
            .isEqualTo(androidSettings.certificateHashes)
        Truth.assertThat(androidSettings.redirectUrlOverride).isNull()
    }

    @Test
    public fun `fromResponse should handle missing androidSettings field`() {
        // Given
        val response = JSONObject("{}")

        // When
        val androidSettings = OwnIdServerConfiguration.AndroidSettings.fromResponse(response)

        // Then
        Truth.assertThat("").isEqualTo(androidSettings.packageName)
        Truth.assertThat(emptySet<String>()).isEqualTo(androidSettings.certificateHashes)
        Truth.assertThat(androidSettings.redirectUrlOverride).isNull()
    }

    @Test
    public fun `fromResponse bad certificate hash`() {
        // Given
        val response = JSONObject(
            """
            {
              "androidSettings": {
                "packageName": "com.example.app",
                "certificateHashes": ["A5:4B:30:4A:2E:8E", "96:CB:FB:4A:0C:49:6E:DA:DA:28:0A:F3:3C:16:9C:54:8C:3B:3A:3F:47:DC:D8:7E:10:51:1B:C9:5D:FA:FC:E2"],
                "redirectUrlOverride": "https://example.com/redirect"
              }
            }
        """.trimIndent()
        )

        // When
        val androidSettings = OwnIdServerConfiguration.AndroidSettings.fromResponse(response)

        // Then
        Truth.assertThat("com.example.app").isEqualTo(androidSettings.packageName)
        Truth.assertThat(
            setOf(
                "96:CB:FB:4A:0C:49:6E:DA:DA:28:0A:F3:3C:16:9C:54:8C:3B:3A:3F:47:DC:D8:7E:10:51:1B:C9:5D:FA:FC:E2"
            )
        )
            .isEqualTo(androidSettings.certificateHashes)
        Truth.assertThat("https://example.com/redirect").isEqualTo(androidSettings.redirectUrlOverride)
    }
}