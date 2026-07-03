package com.ownid.sdk.api

import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.capability.Capability
import okhttp3.HttpUrl

/**
 * Provides the resolved base URL for OwnID API requests.
 *
 * The returned URL is the API root for the current [OwnIdConfiguration]. A custom [OwnIdConfiguration.rootUrl]
 * changes the host/root used by this instance; otherwise the SDK uses the configured app ID, environment, and region.
 */
public interface ApiBaseUrl : Capability {
    /**
     * Returns the base URL used for all API calls in this instance.
     *
     * @throws Exception when the implementation cannot provide a usable base URL.
     */
    @Throws(Exception::class)
    public fun getBaseUrlOrThrow(): HttpUrl
}
