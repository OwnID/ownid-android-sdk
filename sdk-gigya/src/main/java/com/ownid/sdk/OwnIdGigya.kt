package com.ownid.sdk

/**
 * Perform OwnID Gigya Register/Login flows.
 *
 * App developers using this library must override the `ownIdRedirectScheme`
 * property in their `build.gradle` to specify the custom scheme that will be used for
 * the OwnID redirect. If you prefer to use https schema then a custom intent filter should be
 * defined in your application manifest instead. See more details in the documentation.
 */
public interface OwnIdGigya : OwnIdCore {

    public companion object {
        @InternalOwnIdAPI
        @get:JvmName("getProductName")
        public val PRODUCT_NAME: String = "OwnIDGigya"

        @JvmStatic
        @get:JvmName("getDefaultInstanceName")
        public val DEFAULT_INSTANCE_NAME: InstanceName = InstanceName("OwnIdGigya")

        @JvmStatic
        @get:JvmName("getDefaultFileName")
        public val DEFAULT_CONFIGURATION_FILE_NAME: String = "ownIdGigyaSdkConfig.json"
    }
}