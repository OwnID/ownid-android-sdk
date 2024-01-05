package com.ownid.sdk.view.popup

import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.view.OwnIdImageButton

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class Popup internal constructor(
    private val anchorView: OwnIdImageButton,
    private val popupView: View,
    private val position: Position
) {

    @InternalOwnIdAPI
    public enum class Position {
        TOP, BOTTOM, START, END;

        public fun isHorizontal(): Boolean = this == START || this == END
        public fun isVertical(): Boolean = this == TOP || this == BOTTOM
    }

    @InternalOwnIdAPI
    public open class Properties(
        internal val backgroundColor: Int? = null,
        internal val borderColor: Int? = null,
        internal val position: Position? = null
    )

    private val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        isOutsideTouchable = true
        //  isClippingEnabled = false // Can be outside screen
    }

    public fun getPopupView(): View = popupView

    public fun isShowing(): Boolean = popupWindow.isShowing

    public fun show() {
        if (popupWindow.isShowing) return
        anchorView.post { showPopup() }
    }

    public fun dismiss() {
        if (popupWindow.isShowing) popupWindow.dismiss()
    }

    private fun showPopup() {
        if (popupWindow.isShowing) return

        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED).let { popupView.measure(it, it) }

        var xOff = 0
        var yOff = 0

        when (position) {
            Position.TOP -> {
                xOff = if (anchorView.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    -(anchorView.width + popupView.measuredWidth) / 2
                } else {
                    (anchorView.width - popupView.measuredWidth) / 2
                }

                yOff = -popupView.measuredHeight - anchorView.height
            }

            Position.BOTTOM -> {
                xOff = if (anchorView.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    -(anchorView.width + popupView.measuredWidth) / 2
                } else {
                    (anchorView.width - popupView.measuredWidth) / 2
                }

                yOff = 0
            }

            Position.START -> {
                xOff = if (anchorView.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    0
                } else {
                    -popupView.measuredWidth
                }

                yOff = -(anchorView.height + popupView.measuredHeight) / 2
            }

            Position.END -> {
                xOff = if (anchorView.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    -popupView.measuredWidth - anchorView.width
                } else {
                    anchorView.width
                }

                yOff = -(anchorView.height + popupView.measuredHeight) / 2
            }
        }

        popupWindow.showAsDropDown(anchorView, xOff, yOff)
    }
}