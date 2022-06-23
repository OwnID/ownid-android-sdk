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
        public const val PRODUCT_NAME: String = "OwnIDGigya"

        @JvmStatic
        public val DEFAULT_INSTANCE_NAME: InstanceName = InstanceName("OwnIdGigya")

        public const val DEFAULT_CONFIGURATION_FILE: String = "ownIdGigyaSdkConfig.json"
    }
}