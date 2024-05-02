package com.ownid.sdk.internal.feature.flow

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class OwnIdFlowType { REGISTER, LOGIN }