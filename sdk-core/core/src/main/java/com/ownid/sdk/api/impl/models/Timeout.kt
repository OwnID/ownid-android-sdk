package com.ownid.sdk.api.impl.models

import kotlinx.serialization.Serializable

/**
 * A numerical hint, in milliseconds, which indicates the time the calling web app is willing to wait
 * for the creation operation to complete. This hint may be overridden by the browser.
 */
@JvmInline
@Serializable
internal value class Timeout(val value: Long)
