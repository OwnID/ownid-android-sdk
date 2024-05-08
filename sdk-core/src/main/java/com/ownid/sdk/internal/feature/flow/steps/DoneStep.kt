package com.ownid.sdk.internal.feature.flow.steps

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.internal.feature.flow.OwnIdFlowData
import com.ownid.sdk.internal.feature.flow.OwnIdFlowError
import com.ownid.sdk.internal.component.locale.OwnIdLocaleKey
import com.ownid.sdk.internal.feature.flow.AbstractStep

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class DoneStep(
    ownIdFlowData: OwnIdFlowData,
    onNextStep: (AbstractStep) -> Unit,
    private val ownIdResponse: Result<OwnIdResponse>
) : AbstractStep(ownIdFlowData, onNextStep) {

    @MainThread
    internal fun getOwnIdResponse(context: Context): Result<OwnIdResponse> = runCatching {
        ownIdResponse.getOrElse { error ->
            throw if (error !is OwnIdFlowError) error
            else error.toOwnIdUserError(ownIdFlowData.ownIdCore.localeService.getString(context, OwnIdLocaleKey.UNSPECIFIED_ERROR))
        }
    }
}