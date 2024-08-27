package com.ownid.sdk.internal.feature.nativeflow.steps

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowData
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowError
import com.ownid.sdk.internal.component.locale.OwnIdLocaleKey
import com.ownid.sdk.internal.feature.nativeflow.AbstractStep

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class DoneStep(
    ownIdNativeFlowData: OwnIdNativeFlowData,
    onNextStep: (AbstractStep) -> Unit,
    private val ownIdResponse: Result<OwnIdResponse>
) : AbstractStep(ownIdNativeFlowData, onNextStep) {

    @MainThread
    internal fun getOwnIdResponse(context: Context): Result<OwnIdResponse> = runCatching {
        ownIdResponse.getOrElse { error ->
            throw if (error !is OwnIdNativeFlowError) error
            else error.toOwnIdUserError(ownIdNativeFlowData.ownIdCore.localeService.getString(context, OwnIdLocaleKey.UNSPECIFIED_ERROR))
        }
    }
}