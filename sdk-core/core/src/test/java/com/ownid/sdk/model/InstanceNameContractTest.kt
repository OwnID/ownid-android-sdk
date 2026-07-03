package com.ownid.sdk.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Covers: MODEL-170
internal class InstanceNameContractTest {

    @Test
    fun defaultInstanceNameUsesPublicDefaultValue() {
        assertThat(InstanceName.Default.value).isEqualTo("DEFAULT")
        assertThat(InstanceName.Default.toString()).isEqualTo("DEFAULT")
    }

    @Test
    fun instanceNameEqualityUsesRawValue() {
        assertThat(InstanceName("tenant-a")).isEqualTo(InstanceName("tenant-a"))
        assertThat(InstanceName("tenant-a").hashCode()).isEqualTo(InstanceName("tenant-a").hashCode())
        assertThat(InstanceName("tenant-a")).isNotEqualTo(InstanceName("TENANT-A"))
    }
}
