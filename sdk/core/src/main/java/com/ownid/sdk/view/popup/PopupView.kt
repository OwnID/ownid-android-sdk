package com.ownid.sdk.view.popup

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RestrictTo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R
import com.ownid.sdk.internal.component.locale.OwnIdLocaleService

@InternalOwnIdAPI
@SuppressLint("ViewConstructor")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class PopupView(
    protected val localeService: OwnIdLocaleService,
    private val anchorView: View,
    private val position: Popup.Position,
    private val properties: Popup.Properties
) : ConstraintLayout(anchorView.context), OwnIdLocaleService.LocaleUpdateListener {

    private val rootMargin = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_margin)
    private val rootArrowMargin = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_arrow_margin)
    private val arrowHeight = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_arrow_height)
    private val arrowWidth = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_arrow_width)

    private val backgroundCornerRadius = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_corner_radius)
    private val backgroundBorderWidth = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_border)
    private val drawableBackgroundColor = properties.backgroundColor ?: context.getColor(R.color.com_ownid_sdk_widgets_button_color_tooltip_background)
    private val drawableBorderColor = properties.borderColor ?: context.getColor(R.color.com_ownid_sdk_widgets_button_color_tooltip_border)

    private val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    protected val container: FrameLayout = FrameLayout(context).apply {
        id = View.generateViewId()

        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            val arrowMargin = arrowHeight.toInt() - backgroundBorderWidth.toInt()
            when (position) {
                Popup.Position.TOP -> setMargins(0, 0, 0, arrowMargin)
                Popup.Position.BOTTOM -> setMargins(0, arrowMargin, 0, 0)
                Popup.Position.START -> setMargins(0, 0, arrowMargin, 0)
                Popup.Position.END -> setMargins(arrowMargin, 0, 0, 0)
            }
        }

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            backgroundCornerRadius.let { cornerRadii = floatArrayOf(it, it, it, it, it, it, it, it) }
            setColor(drawableBackgroundColor)
            setStroke(backgroundBorderWidth.toInt(), drawableBorderColor)
        }

        when (currentNightMode) {
            Configuration.UI_MODE_NIGHT_NO -> elevation = 8F
            Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_UNDEFINED -> elevation = 0F
        }
    }

    protected val arrow: ImageView = ImageView(context).apply {
        id = View.generateViewId()

        var width = arrowWidth.toInt()
        var height = arrowHeight.toInt()
        if (position.isHorizontal()) {
            width = arrowHeight.toInt()
            height = arrowWidth.toInt()
        }

        layoutParams = LayoutParams(width, height)

        rotation = when (position) {
            Popup.Position.TOP, Popup.Position.START -> 0F
            Popup.Position.BOTTOM, Popup.Position.END -> 180F
        }

        background = PopupTriangleDrawable(position.isHorizontal(), drawableBackgroundColor, drawableBorderColor, backgroundBorderWidth)
            .apply { setBounds(0, 0, width, height) }

        when (currentNightMode) {
            Configuration.UI_MODE_NIGHT_NO -> elevation = 8F
            Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_UNDEFINED -> elevation = 0F
        }
    }

    init {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

        when (position) {
            Popup.Position.TOP -> setPadding(rootMargin.toInt(), rootMargin.toInt(), rootMargin.toInt(), rootArrowMargin.toInt())
            Popup.Position.BOTTOM -> setPadding(rootMargin.toInt(), rootArrowMargin.toInt(), rootMargin.toInt(), rootMargin.toInt())
            Popup.Position.START -> setPadding(rootMargin.toInt(), rootMargin.toInt(), rootArrowMargin.toInt(), rootMargin.toInt())
            Popup.Position.END -> setPadding(rootArrowMargin.toInt(), rootMargin.toInt(), rootMargin.toInt(), rootMargin.toInt())
        }
        clipToPadding = false

        addView(container)
        addView(arrow)

        ConstraintSet().apply {
            clone(this@PopupView)

            connect(container.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(container.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(container.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(container.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

            if (position.isVertical()) {
                connect(arrow.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                connect(arrow.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            }

            if (position.isHorizontal()) {
                connect(arrow.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                connect(arrow.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            }

            when (position) {
                Popup.Position.TOP -> connect(arrow.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                Popup.Position.BOTTOM -> connect(arrow.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                Popup.Position.START -> connect(arrow.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                Popup.Position.END -> connect(arrow.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            }

            applyTo(this@PopupView)
        }
    }

    protected abstract fun setStrings()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        layoutDirection = anchorView.layoutDirection
        container.layoutDirection = anchorView.layoutDirection
        arrow.layoutDirection = anchorView.layoutDirection

        if (anchorView.layoutDirection == LAYOUT_DIRECTION_RTL) {
            if (position == Popup.Position.START) {
                setPadding(rootArrowMargin.toInt(), rootMargin.toInt(), rootMargin.toInt(), rootMargin.toInt())
                arrow.rotation = 180F
            }
            if (position == Popup.Position.END) {
                setPadding(rootMargin.toInt(), rootMargin.toInt(), rootArrowMargin.toInt(), rootMargin.toInt())
                arrow.rotation = 0F
            }
        }

        if (isInEditMode.not()) localeService.registerLocaleUpdateListener(this)
        onLocaleUpdated()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isInEditMode.not()) localeService.unregisterLocaleUpdateListener(this)
    }

    override fun onLocaleUpdated() {
        setStrings()
        reLayout()
    }

    private fun reLayout() {
        if (isInLayout.not()) requestLayout()
        invalidate()
        viewTreeObserver.addOnPreDrawListener(onPreDrawListener)
    }

    private val onPreDrawListener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            if (anchorView.isAttachedToWindow.not()) return true
            if (anchorView.isShown.not()) return true
            if (position.isHorizontal()) return true

            val anchorViewScreenLocation = IntArray(2).also { anchorView.getLocationOnScreen(it) }
            val anchorViewCenterX = anchorViewScreenLocation[0] + anchorView.width / 2

            val tooltipViewLocation = IntArray(2).also { this@PopupView.getLocationOnScreen(it) }
            val marginStart = anchorViewCenterX - tooltipViewLocation[0]

            val margin = (marginStart - (arrowWidth - backgroundBorderWidth) / 2 - rootMargin).toInt()
            if (margin > 0) {
                ConstraintSet().apply {
                    clone(this@PopupView)
                    clear(arrow.id, ConstraintSet.START)
                    clear(arrow.id, ConstraintSet.END)

                    if (anchorView.layoutDirection == LAYOUT_DIRECTION_RTL)
                        connect(arrow.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    else
                        connect(arrow.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)

                    applyTo(this@PopupView)
                }
                arrow.layoutParams = (arrow.layoutParams as LayoutParams).apply {
                    if (anchorView.layoutDirection == LAYOUT_DIRECTION_RTL) setMarginEnd(margin) else setMarginStart(margin)
                }
            } else {
                ConstraintSet().apply {
                    clone(this@PopupView)
                    clear(arrow.id, ConstraintSet.START)
                    clear(arrow.id, ConstraintSet.END)
                    connect(arrow.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(arrow.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    applyTo(this@PopupView)
                }

                arrow.layoutParams = (arrow.layoutParams as LayoutParams).apply {
                    setMarginStart(0)
                    setMarginEnd(0)
                }
            }
            return false
        }
    }
}