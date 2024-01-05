package com.ownid.sdk.view.popup.tooltip

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.R
import com.ownid.sdk.internal.locale.OwnIdLocaleKey
import com.ownid.sdk.view.popup.Popup
import com.ownid.sdk.view.popup.PopupView
import com.ownid.sdk.viewmodel.OwnIdBaseViewModel

@InternalOwnIdAPI
@SuppressLint("ViewConstructor")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TooltipView(
    ownIdViewModel: OwnIdBaseViewModel<*>,
    anchorView: View,
    position: Popup.Position,
    properties: Tooltip.Properties
) : PopupView(ownIdViewModel, anchorView, position, properties) {

    private val textPaddingHor = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_text_padding_horizontal)
    private val textPaddingVer = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_text_padding_vertical)
    private val textAppearanceId = properties.textAppearance ?: R.style.OwnIdButton_TooltipTextAppearance_Default

    private val textView: TextView = TextView(context).apply {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        isSingleLine = true
        setPadding(textPaddingHor.toInt(), textPaddingVer.toInt(), textPaddingHor.toInt(), textPaddingVer.toInt())
        setTextAppearance(textAppearanceId)
        properties.textColor?.let { setTextColor(it) }
        text = (ownIdViewModel.ownIdInstance.ownIdCore as OwnIdCoreImpl).localeService.getString(context, TOOLTIP)
    }

    init {
        container.addView(textView)
    }

    override fun setStrings() {
        if (isInEditMode.not()) {
            textView.text = (ownIdViewModel.ownIdInstance.ownIdCore as OwnIdCoreImpl).localeService.getString(context, TOOLTIP)
        } else {
            textView.text = context.getString(R.string.com_ownid_sdk_widgets_sbsButton_tooltip)
        }
    }

    public fun setTextSize(sizeSp: Float) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private companion object LocaleKeys {
        private val TOOLTIP = OwnIdLocaleKey("widgets", "sbs-button", "tooltip")
            .withFallback(R.string.com_ownid_sdk_widgets_sbsButton_tooltip)
    }
}