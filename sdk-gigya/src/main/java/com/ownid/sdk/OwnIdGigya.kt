package com.ownid.sdk

/**
 * Perform OwnID Gigya Register/Login flows.
 */
public interface OwnIdGigya : OwnIdInstance {

    public companion object {
        @get:JvmName("getProductName")
        public val PRODUCT_NAME: ProductName = "OwnIDGigya"

        @JvmStatic
        @get:JvmName("getDefaultInstanceName")
        public val DEFAULT_INSTANCE_NAME: InstanceName = InstanceName(PRODUCT_NAME)

        @JvmStatic
        @get:JvmName("getDefaultFileName")
        public val DEFAULT_CONFIGURATION_FILE_NAME: String = "ownIdGigyaSdkConfig.json"
    }
}