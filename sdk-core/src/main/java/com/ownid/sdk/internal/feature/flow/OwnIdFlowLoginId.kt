package com.ownid.sdk.internal.feature.flow

import androidx.annotation.RestrictTo
import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.config.OwnIdServerConfiguration

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal sealed class OwnIdFlowLoginId(
    @JvmField internal val value: String,
    @JvmField internal val regex: Regex,
    @JvmField internal val localeKey: String
) {

    internal companion object {
        @Throws(OwnIdException::class)
        internal fun fromString(value: String, configuration: Configuration): OwnIdFlowLoginId {
            configuration.isServerConfigurationSet || throw OwnIdException("OwnIdLoginId.fromString: Server configuration not set")
            return when (configuration.server.loginId.type) {
                OwnIdServerConfiguration.LoginId.Type.Email -> Email(value, configuration.server.loginId.regex)
                OwnIdServerConfiguration.LoginId.Type.PhoneNumber -> PhoneNumber(value, configuration.server.loginId.regex)
                OwnIdServerConfiguration.LoginId.Type.UserName -> UserName(value, configuration.server.loginId.regex)
            }
        }
    }

    internal class Email(value: String, regex: Regex) : OwnIdFlowLoginId(value, regex, "email")
    internal class PhoneNumber(value: String, regex: Regex) : OwnIdFlowLoginId(value, regex, "phoneNumber")
    internal class UserName(value: String, regex: Regex) : OwnIdFlowLoginId(value, regex, "userName")


    internal fun isEmpty(): Boolean = value.isBlank()
    internal fun isNotEmpty(): Boolean = value.isNotBlank()
    internal fun isValid(): Boolean = regex.matches(value)
}