package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class FlowInsights(
    /**
     * Duration in milliseconds
     */
    @SerialName("duration")
    val duration: Long? = null,

    /**
     * Percentage of errors encountered in the flow
     */
    @SerialName("errorRate")
    val errorRate: Double? = null,

    @SerialName("retries")
    val retries: Int? = null,

    @SerialName("clicksCount")
    val clicksCount: Int? = null,

    /**
     * Authentication method used in the flow
     */
    @SerialName("authMethod")
    val authMethod: AuthMethod? = null,

    /**
     * Indicates if the user was logged in
     */
    @SerialName("loggedIn")
    val loggedIn: Boolean? = null,

    /**
     * Indicates if the user was registered
     */
    @SerialName("registered")
    val registered: Boolean? = null
)
