package com.ownid.sdk.view.tooltip

import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.view.OwnIdImageButton

@InternalOwnIdAPI
public class Tooltip internal constructor(
    private val anchorView: OwnIdImageButton,
    private val position: TooltipPosition,
    textAppearance: Int,
    backgroundColor: Int,
    borderColor: Int,
) {

    private val tooltipView = TooltipView(anchorView, position, textAppearance, backgroundColor, borderColor)
    private val popupWindow = PopupWindow(tooltipView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        isOutsideTouchable = true
        //  isClippingEnabled = false // Can be outside screen
    }

    public fun getTextView(): TextView = tooltipView.textView

    public fun isShowing(): Boolean = popupWindow.isShowing

    public fun show() {
        if (popupWindow.isShowing) return
        anchorView.post { showAsDropDown() }
    }

    public fun dismiss() {
        if (popupWindow.isShowing) popupWindow.dismiss()
    }

    private fun showAsDropDown() {
        if (popupWindow.isShowing) return

        var xOff = 0
        var yOff = 0

        when (position) {
            TooltipPosition.TOP -> {
                xOff = if (anchorView.layoutDirection == LAYOUT_DIRECTION_RTL) {
                    -(anchorView.width + tooltipView.measuredWidth) / 2
                } else {
                    (anchorView.width - tooltipView.measuredWidth) / 2
                }

                yOff = -tooltipView.measuredHeight - anchorView.height
            }

            TooltipPosition.BOTTOM -> {
                xOff = if (anchorView.layoutDirection == LAYOUT_DIRECTION_RTL) {
                    -(anchorView.width + tooltipView.measuredWidth) / 2
                } else {
                    (anchorView.width - tooltipView.measuredWidth) / 2
                }

                yOff = 0
            }

            TooltipPosition.START -> {
                xOff = if (anchorView.layoutDirection == LAYOUT_DIRECTION_RTL) {
                    0
                } else {
                    -tooltipView.measuredWidth
                }

                yOff = -(anchorView.height + tooltipView.measuredHeight) / 2
            }

            TooltipPosition.END -> {
                xOff = if (anchorView.layoutDirection == LAYOUT_DIRECTION_RTL) {
                    -tooltipView.measuredWidth - anchorView.width
                } else {
                    anchorView.width
                }

                yOff = -(anchorView.height + tooltipView.measuredHeight) / 2
            }
        }

        popupWindow.showAsDropDown(anchorView, xOff, yOff)
    }
}