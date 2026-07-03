@file:JvmName("OwnIdComposeIcons")
@file:JvmMultifileClass

package com.ownid.sdk.compose.components.icon

import com.ownid.sdk.OwnId

/**
 * Returns the namespace for OwnID-provided Compose icons.
 *
 * These icons are visual assets only. Components that expose them in interactive or informative UI remain
 * responsible for content descriptions and semantics.
 */
public val OwnId.Icons: OwnIdIcons
    get() = OwnIdIcons

/** Namespace for OwnID-provided Compose icon assets. */
public object OwnIdIcons
