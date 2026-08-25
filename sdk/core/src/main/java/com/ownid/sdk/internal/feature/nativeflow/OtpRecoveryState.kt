package com.ownid.sdk.internal.feature.nativeflow

import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.nativeflow.steps.otp.OtpAuthStep
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OtpRecoveryState private constructor(
    internal val loginId: OwnIdNativeFlowLoginId,
    internal val verifier: String,
    internal val context: String,
    internal val statusFinalUrl: HttpUrl,
    internal val expiresAtElapsedRealtime: Long,
    internal val enableRegistrationFromLogin: Boolean,
    internal val redirectUri: String,
    internal val languageTag: String,
    internal val otpData: OtpAuthStep.Data,
) {
    internal fun toBundle(): Bundle = Bundle(15).apply {
        putString(KEY_LOGIN_ID_TYPE, loginId.localeKey)
        putString(KEY_LOGIN_ID_VALUE, loginId.value)
        // The verifier deliberately crosses into short-lived system saved-state memory to resume the same transaction.
        putString(KEY_VERIFIER, verifier)
        putString(KEY_CONTEXT, context)
        putString(KEY_FINAL_STATUS_URL, statusFinalUrl.toString())
        putLong(KEY_EXPIRES_AT, expiresAtElapsedRealtime)
        putBoolean(KEY_ENABLE_REGISTRATION_FROM_LOGIN, enableRegistrationFromLogin)
        putString(KEY_REDIRECT_URI, redirectUri)
        putString(KEY_LANGUAGE_TAG, languageTag)
        putString(KEY_OTP_URL, otpData.url.toString())
        putString(KEY_OTP_RESTART_URL, otpData.restartUrl.toString())
        putString(KEY_OTP_RESEND_URL, otpData.resendUrl.toString())
        putInt(KEY_OTP_LENGTH, otpData.otpLength)
        putString(KEY_OTP_VERIFICATION_TYPE, otpData.verificationType.name)
        putString(KEY_OTP_OPERATION_TYPE, otpData.operationType.name)
    }

    internal companion object {
        internal const val BUNDLE_KEY = "com.ownid.sdk.internal.intent.KEY_OTP_SNAPSHOT"

        internal fun from(step: OtpAuthStep): OtpRecoveryState {
            val flowData = step.ownIdNativeFlowData
            return OtpRecoveryState(
                flowData.loginId,
                flowData.verifier,
                flowData.context,
                flowData.statusFinalUrl,
                flowData.expiresAtElapsedRealtime,
                flowData.enableRegistrationFromLogin,
                flowData.redirectUri,
                flowData.languageTag,
                step.data,
            )
        }

        internal fun fromBundle(bundle: Bundle): OtpRecoveryState {
            require(bundle.containsKey(KEY_ENABLE_REGISTRATION_FROM_LOGIN)) { "Missing primitive OTP state" }

            val expiresAt = bundle.getLong(KEY_EXPIRES_AT)
            require(SystemClock.elapsedRealtime() < expiresAt) { "OTP snapshot expired" }

            val otpLength = bundle.getInt(KEY_OTP_LENGTH)
            require(otpLength > 0) { "Invalid OTP length" }

            return OtpRecoveryState(
                OwnIdNativeFlowLoginId.restore(
                    bundle.requireString(KEY_LOGIN_ID_TYPE),
                    bundle.requireString(KEY_LOGIN_ID_VALUE),
                ),
                bundle.requireString(KEY_VERIFIER),
                bundle.requireString(KEY_CONTEXT),
                bundle.requireString(KEY_FINAL_STATUS_URL).toHttpUrl(),
                expiresAt,
                bundle.getBoolean(KEY_ENABLE_REGISTRATION_FROM_LOGIN),
                bundle.requireString(KEY_REDIRECT_URI),
                bundle.requireString(KEY_LANGUAGE_TAG),
                OtpAuthStep.Data(
                    bundle.requireString(KEY_OTP_URL).toHttpUrl(),
                    bundle.requireString(KEY_OTP_RESTART_URL).toHttpUrl(),
                    bundle.requireString(KEY_OTP_RESEND_URL).toHttpUrl(),
                    otpLength,
                    OtpAuthStep.VerificationType.valueOf(bundle.requireString(KEY_OTP_VERIFICATION_TYPE)),
                    OtpAuthStep.OperationType.valueOf(bundle.requireString(KEY_OTP_OPERATION_TYPE)),
                ),
            )
        }

        private fun Bundle.requireString(key: String): String =
            requireNotNull(getString(key)) { "Missing OTP state: $key" }

        private const val KEY_LOGIN_ID_TYPE = "loginIdType"
        private const val KEY_LOGIN_ID_VALUE = "loginIdValue"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_CONTEXT = "context"
        private const val KEY_FINAL_STATUS_URL = "finalStatusUrl"
        private const val KEY_EXPIRES_AT = "expiresAt"
        private const val KEY_ENABLE_REGISTRATION_FROM_LOGIN = "enableRegistrationFromLogin"
        private const val KEY_REDIRECT_URI = "redirectUri"
        private const val KEY_LANGUAGE_TAG = "languageTag"
        private const val KEY_OTP_URL = "otpUrl"
        private const val KEY_OTP_RESTART_URL = "otpRestartUrl"
        private const val KEY_OTP_RESEND_URL = "otpResendUrl"
        private const val KEY_OTP_LENGTH = "otpLength"
        private const val KEY_OTP_VERIFICATION_TYPE = "otpVerificationType"
        private const val KEY_OTP_OPERATION_TYPE = "otpOperationType"
    }
}
