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
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

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

        val localeService = (ownIdViewModel.ownIdInstance.ownIdCore as OwnIdCoreImpl).localeService
        when (ownIdViewModel) {
            is OwnIdLoginViewModel -> text = localeService.getString(context, TOOLTIP_LOGIN)
            is OwnIdRegisterViewModel -> text = localeService.getString(context, TOOLTIP_REGISTER)
        }
    }

    init {
        container.addView(textView)
    }

    override fun setStrings() {
        if (isInEditMode.not()) {
            val localeService = (ownIdViewModel.ownIdInstance.ownIdCore as OwnIdCoreImpl).localeService
            when (ownIdViewModel) {
                is OwnIdLoginViewModel -> textView.text = localeService.getString(context, TOOLTIP_LOGIN)
                is OwnIdRegisterViewModel -> textView.text = localeService.getString(context, TOOLTIP_REGISTER)
            }
        } else {
            textView.text = context.getString(R.string.com_ownid_sdk_widgets_sbsButton_tooltip_login)
        }
    }

    public fun setTextSize(sizeSp: Float) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private companion object LocaleKeys {
        private val TOOLTIP_LOGIN = OwnIdLocaleKey("widgets", "sbs-button", "tooltip", "login", "title")
            .withFallback(R.string.com_ownid_sdk_widgets_sbsButton_tooltip_login)
        private val TOOLTIP_REGISTER = OwnIdLocaleKey("widgets", "sbs-button", "tooltip", "register", "title")
            .withFallback(R.string.com_ownid_sdk_widgets_sbsButton_tooltip_register)
    }
}