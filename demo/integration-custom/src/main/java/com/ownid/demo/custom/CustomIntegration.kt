package com.ownid.demo.custom

import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdIntegration
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.ProductName
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.event.LoginData
import org.json.JSONObject

class CustomIntegration(
    private val identityPlatform: IdentityPlatform
) : OwnIdIntegration {

    // Registration parameters in addition to user Login ID (optional)
    class IntegrationRegistrationParameters(val name: String) : RegistrationParameters

    override fun register(loginId: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<LoginData?>) {
        // Get registration parameters (optional)
        val name = (params as? IntegrationRegistrationParameters)?.name ?: ""

        // Generate random password
        val password = "QWEasd123!@#"

        // Register user with your identity platform and set OwnId Data to user profile
        identityPlatform.register(name, loginId, password, ownIdResponse.payload.data) {
            onFailure { callback(Result.failure(it)) }
            onSuccess { callback(Result.success(null)) }
        }
    }

    override fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<LoginData?>) {
        // Use data to login user
        val token = JSONObject(ownIdResponse.payload.data).getString("token")

        identityPlatform.getProfile(token) {
            onSuccess { callback(Result.success(null)) }
            onFailure { callback(Result.failure(it)) }
        }
    }

    companion object {
        const val CONFIGURATION_FILE: String = "ownIdIntegrationSdkConfig.json"

        const val PRODUCT_NAME_VERSION: ProductName = "CustomIntegration/3.4.0"
    }
}