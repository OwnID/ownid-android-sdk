package com.ownid.sdk

import org.json.JSONObject

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

        /**
         * Appends OwnIdData to the provided Gigya parameters.
         *
         * @param params The mutable map of Gigya parameters.
         * @param ownIdData The OwnIdData to append.
         *
         * @return The updated mutable map of Gigya parameters with the appended OwnIdData.
         */
        public fun appendWithOwnIdData(params: MutableMap<String, Any>, ownIdData: String?): MutableMap<String, Any> {
            if (ownIdData == null) return params

            val dataJson = if (params.containsKey("data").not()) JSONObject()
            else JSONObject(java.lang.String.valueOf(params["data"]))

            dataJson.put("ownId", JSONObject(ownIdData))

            return params.apply { put("data", dataJson.toString()) }
        }
    }
}