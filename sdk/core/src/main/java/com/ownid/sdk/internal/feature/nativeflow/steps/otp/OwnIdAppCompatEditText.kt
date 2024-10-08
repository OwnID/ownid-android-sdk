package com.ownid.sdk.internal.feature.nativeflow.steps.otp

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.RestrictTo
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatEditText
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.component.events.OwnIdInternalEventsService
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowType

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdAppCompatEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    override fun onTextContextMenuItem(id: Int): Boolean {
        val consumed = super.onTextContextMenuItem(id)

        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText)
            flowType?.let { eventsService?.sendMetric(it, Metric.EventType.Track, action = "User Pasted Verification Code") }

        return consumed
    }

    internal var flowType: OwnIdNativeFlowType? = null
    internal var eventsService: OwnIdInternalEventsService? = null
}