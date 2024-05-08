package com.ownid.sdk.internal.component.locale

import androidx.annotation.RestrictTo
import androidx.annotation.StringRes
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdLocaleKey private constructor(@StringRes internal val fallbackId: Int, internal vararg val keys: String) {
    constructor(vararg keys: String) : this(0, *keys)

    internal fun withFallback(@StringRes fallbackId: Int) = OwnIdLocaleKey(fallbackId, *keys)

    override fun toString(): String = keys.joinToString()

    internal companion object {
        @JvmField
        internal val UNSPECIFIED_ERROR = OwnIdLocaleKey("steps", "error").withFallback(R.string.com_ownid_sdk_internal_ui_steps_error)
    }
}
