package com.ownid.sdk.internal.feature.enrollment

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OwnIdEnrollmentSkipped : OwnIdException("Credential enrolment skipped")