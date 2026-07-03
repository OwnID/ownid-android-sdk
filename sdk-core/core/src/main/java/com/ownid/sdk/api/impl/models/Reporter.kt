package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Reporter metadata attached to analytics event uploads. */
internal class Reporter(
    /**
     * Reporter service identifier.
     */
    @SerialName("service")
    val service: ReporterService,

    /**
     * Reporter version string.
     */
    @SerialName("version")
    val version: String? = null,

    /**
     * Origin URL of the reporting application.
     */
    @SerialName("origin")
    val origin: String,

    /**
     * Referer URL of the reporting application.
     */
    @SerialName("referer")
    val referer: String
)
