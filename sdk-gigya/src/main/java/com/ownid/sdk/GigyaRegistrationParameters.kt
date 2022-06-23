package com.ownid.sdk


/**
 * Gigya registration parameters.
 *
 * @param params    Custom parameters for [Gigya register request](https://github.com/SAP/gigya-android-sdk/tree/main/sdk-core#register-via-email--password).
 */
public class GigyaRegistrationParameters(public val params: Map<String, Any>) : RegistrationParameters