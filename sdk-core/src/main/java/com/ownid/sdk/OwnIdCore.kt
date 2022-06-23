package com.ownid.sdk

import android.content.Context
import android.content.Intent
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdResponse
import com.ownid.sdk.internal.events.LogService
import com.ownid.sdk.internal.events.MetricService

/**
 * Creates OwnID Register/Login flow intents.
 *
 * App developers using this library must override the `ownIdRedirectScheme`
 * property in their `build.gradle` to specify the custom scheme that will be used for
 * the OwnID redirect. If you prefer to use https schema, then a custom intent filter should be
 * defined in your application manifest instead. See more details in the documentation.
 */
public interface OwnIdCore : OwnIdInstance {

    public companion object {
        public const val PRODUCT_NAME: String = "OwnIDCore"
    }

    /**
     * Configuration of OwnId instance. See [Configuration]
     */
    public val configuration: Configuration

    /**
     * Log service of OwnId instance. See [LogService]
     */
    @InternalOwnIdAPI
    public val logService: LogService

    /**
     * Metric service of OwnId instance. See [MetricService]
     */
    @InternalOwnIdAPI
    public val metricService: MetricService

    /**
     * Create [Intent] that will trigger OwnID Register flow.
     * This intent must be launched as [Activity.startActivityForResult](https://developer.android.com/reference/android/app/Activity#startActivityForResult(android.content.Intent,%20int))
     * or using [Activity Result APIs](https://developer.android.com/training/basics/intents/result)
     *
     * @param context       Android [Context]
     * @param languageTags  Language TAGs list for Web App (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language))
     * @param email         User email. Can be an empty string `""` if email is not available
     *
     * @throws OwnIdException if Intent creation failed.
     */
    @InternalOwnIdAPI
    @Throws(OwnIdException::class)
    public fun createRegisterIntent(context: Context, languageTags: String, email: String): Intent

    /**
     * Create [Intent] that will trigger OwnID Login flow.
     * This intent must be launched as [Activity.startActivityForResult](https://developer.android.com/reference/android/app/Activity#startActivityForResult(android.content.Intent,%20int))
     * or using [Activity Result APIs](https://developer.android.com/training/basics/intents/result)
     *
     * @param context       Android [Context]
     * @param languageTags  Language TAGs list for Web App (well-formed [IETF BCP 47 language tag](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language))
     * @param email         User email. Can be an empty string `""` if email is not available (email is required for LinkOnLogin)
     *
     * @throws OwnIdException if Intent creation failed.
     */
    @InternalOwnIdAPI
    @Throws(OwnIdException::class)
    public fun createLoginIntent(context: Context, languageTags: String, email: String): Intent

    /**
     * Complete OwnID Registration flow and register new user. User password will be generated automatically.
     *
     * @param email          User email.
     * @param params         [RegistrationParameters] Additional parameters for registration. Depend on integration.
     * @param ownIdResponse  [OwnIdResponse] from [OwnIdCore.createRegisterIntent] flow.
     * @param callback       [OwnIdCallback] with [Unit] value of Registration flow result or with [OwnIdException]
     * cause value if Registration flow failed.
     */
    public fun register(email: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>)

    /**
     * Complete OwnID Login flow.
     *
     * @param ownIdResponse  [OwnIdResponse] from [OwnIdCore.createLoginIntent] flow.
     * @param callback       [OwnIdCallback] with [Unit] value of Login flow result or with [OwnIdException] cause if Login failed.
     */
    public fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>)
}