package com.ownid.sdk.ui.capability.impl

import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.ui.capability.LoginIdCollectStrings
import com.ownid.sdk.ui.capability.LoginIdCollectStringsParams
import com.ownid.sdk.ui.capability.LoginIdCollectStringsProvider
import com.ownid.sdk.ui.capability.StringsProvider
import com.ownid.sdk.ui.locale.ServerLocaleDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Maps login ID collection server keys into typed strings and fills missing keys from embedded defaults.
 *
 * Embedded defaults depend on the collectable login ID types and whether the system can use passkeys. If no local
 * device information is available, passkey-capable wording is used.
 */
internal class LoginIdCollectStringsEmbeddedRepositoryImpl(
    private val localInfo: LocalInfo?
) : LoginIdCollectStringsProvider.EmbeddedRepository {

    override fun fallbackToEmbedded(params: StringsProvider.Params, map: Map<String, String>): LoginIdCollectStrings {
        params as LoginIdCollectStringsParams

        val isSystemFidoCapable = localInfo?.isSystemFidoCapable ?: true
        val defaults = LoginIdCollectStrings.default(
            loginIdTypes = params.loginIdTypes,
            isSystemFidoCapable = isSystemFidoCapable,
        )

        return LoginIdCollectStrings(
            title = map["title"] ?: defaults.title,
            message = map["message"] ?: defaults.message,
            placeholder = map["placeholder"] ?: defaults.placeholder,
            cancel = map["cancel"] ?: defaults.cancel,
            cta = map["cta"] ?: defaults.cta,
            error = map["error"] ?: defaults.error,
        )
    }
}

/**
 * Reads login ID collection strings from the server locale data source.
 *
 * The server key path is selected from the collectable login ID types and passkey capability. Missing locale data emits
 * `null`; unsupported login ID type sets and missing individual keys emit an empty or partial map so the embedded
 * repository can fill the result. Unavailable or unreadable persisted locale payloads are visible here as missing server
 * data; suppressed refresh failures may continue serving previously available data when a payload exists.
 */
internal class LoginIdCollectStringsServerRepositoryImpl(
    private val localInfo: LocalInfo,
    private val serverLocaleProvider: ServerLocaleDataSource.Provider
) : LoginIdCollectStringsProvider.ServerRepository {

    private val prefix = arrayOf("steps", "login-id-collect")

    override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> {
        params as LoginIdCollectStringsParams
        val isSystemFidoCapable = localInfo.isSystemFidoCapable

        return serverLocaleProvider.getDataSource(languageTag).map { dataSource ->
            if (dataSource == null) return@map null

            val loginIdTypes = params.loginIdTypes
                .distinct()
                .filter { it == LoginId.Type.Email || it == LoginId.Type.PhoneNumber || it == LoginId.Type.UserName }
                .toSet()

            val loginIdKey = when (loginIdTypes) {
                setOf(LoginId.Type.Email) -> "email"
                setOf(LoginId.Type.PhoneNumber) -> "phoneNumber"
                setOf(LoginId.Type.UserName) -> "userName"
                setOf(LoginId.Type.Email, LoginId.Type.PhoneNumber) -> "emailOrPhoneNumber"
                setOf(LoginId.Type.Email, LoginId.Type.UserName) -> "emailOrUserName"
                setOf(LoginId.Type.PhoneNumber, LoginId.Type.UserName) -> "phoneNumberOrUserName"
                setOf(LoginId.Type.Email, LoginId.Type.PhoneNumber, LoginId.Type.UserName) -> "emailOrPhoneNumberOrUserName"
                else -> return@map emptyMap()
            }

            mapOf(
                "title" to when (isSystemFidoCapable) {
                    true -> dataSource.getString(*prefix, loginIdKey, "title")
                    else -> dataSource.getString(*prefix, loginIdKey, "no-biometrics", "title")
                },
                "message" to when (isSystemFidoCapable) {
                    true -> dataSource.getString(*prefix, loginIdKey, "message")
                    else -> dataSource.getString(*prefix, loginIdKey, "no-biometrics", "message")
                },
                "placeholder" to when (isSystemFidoCapable) {
                    true -> dataSource.getString(*prefix, loginIdKey, "placeholder")
                    else -> dataSource.getString(*prefix, loginIdKey, "no-biometrics", "placeholder")
                },
                "cancel" to dataSource.getString(*prefix, loginIdKey, "cancel"),
                "cta" to dataSource.getString(*prefix, loginIdKey, "cta"),
                "error" to dataSource.getString(*prefix, loginIdKey, "error"),
            ).filterValues { it != null }.mapValues { it.value!! }
        }
    }
}

/**
 * Server repository used when locale data source setup is unavailable.
 *
 * Emits an empty map so string resolution completes with embedded defaults.
 */
internal object LoginIdCollectStringsServerRepositoryEmptyFallback : LoginIdCollectStringsProvider.ServerRepository {
    override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> =
        flowOf(emptyMap())
}
