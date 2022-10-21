package com.ownid.sdk.view.tooltip

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R
import com.ownid.sdk.internal.LocaleService
import com.ownid.sdk.view.OwnIdImageButton
import com.ownid.sdk.view.delegate.LanguageTagsDelegate
import com.ownid.sdk.view.delegate.LanguageTagsDelegateImpl

@InternalOwnIdAPI
@SuppressLint("ViewConstructor")
public class TooltipView(
    private val anchorView: OwnIdImageButton,
    private val position: TooltipPosition,
    private val textAppearance: Int,
    private val tooltipBackgroundColor: Int,
    private val tooltipBorderColor: Int
) : LinearLayout(anchorView.context),
    LanguageTagsDelegate by LanguageTagsDelegateImpl() {

    private val rootMargin = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_margin)
    private val arrowMargin = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_arrow_margin)
    private val arrowHeight = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_arrow_height)
    private val arrowWidth = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_arrow_width)
    private val textPaddingHor = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_text_padding_horizontal)
    private val textPaddingVer = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_text_padding_vertical)

    private val backgroundCornerRadius = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_corner_radius)
    private val backgroundBorderWidth = resources.getDimension(R.dimen.com_ownid_sdk_tooltip_border)

    private val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    internal val textView: AppCompatTextView
    private lateinit var triangle: AppCompatImageView

    private val onPreDrawListener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            if (anchorView.isAttachedToWindow.not()) return true
            if (anchorView.isShown.not()) return true
            if (position.isHorizontal()) return true

            val anchorViewScreenLocation = IntArray(2).also { anchorView.getLocationOnScreen(it) }
            val anchorViewCenterX = anchorViewScreenLocation[0] + anchorView.width / 2

            val tooltipViewLocation = IntArray(2).also { this@TooltipView.getLocationOnScreen(it) }
            val marginStart = anchorViewCenterX - tooltipViewLocation[0]

            val margin = marginStart - (arrowWidth - backgroundBorderWidth) / 2 - rootMargin
            if (margin > 0) {
                triangle.layoutParams = (triangle.layoutParams as LayoutParams).apply {
                    if (anchorView.layoutDirection == LAYOUT_DIRECTION_RTL) {
                        gravity = Gravity.END
                        setMarginEnd(margin.toInt())
                    } else {
                        gravity = Gravity.START
                        setMarginStart(margin.toInt())
                    }
                }
            } else {
                triangle.layoutParams = (triangle.layoutParams as LayoutParams).apply {
                    gravity = Gravity.CENTER
                    setMarginStart(0)
                    setMarginEnd(0)
                }
            }
            return false
        }
    }

    private fun updateLayout() {
        layoutDirection = anchorView.layoutDirection
        textView.layoutDirection = anchorView.layoutDirection
        triangle.layoutDirection = anchorView.layoutDirection

        if (anchorView.layoutDirection == LAYOUT_DIRECTION_RTL) {
            if (position == TooltipPosition.START) {
                setPadding(arrowMargin.toInt(), rootMargin.toInt(), rootMargin.toInt(), rootMargin.toInt())
                triangle.rotation = 180F
            }
            if (position == TooltipPosition.END) {
                setPadding(rootMargin.toInt(), rootMargin.toInt(), arrowMargin.toInt(), rootMargin.toInt())
                triangle.rotation = 0F
            }
        }
    }

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        orientation = if (position.isHorizontal()) HORIZONTAL else VERTICAL
        clipToPadding = false

        when (position) {
            TooltipPosition.TOP -> setPadding(rootMargin.toInt(), rootMargin.toInt(), rootMargin.toInt(), arrowMargin.toInt())
            TooltipPosition.BOTTOM -> setPadding(rootMargin.toInt(), arrowMargin.toInt(), rootMargin.toInt(), rootMargin.toInt())
            TooltipPosition.START -> setPadding(rootMargin.toInt(), rootMargin.toInt(), arrowMargin.toInt(), rootMargin.toInt())
            TooltipPosition.END -> setPadding(arrowMargin.toInt(), rootMargin.toInt(), rootMargin.toInt(), rootMargin.toInt())
        }

        textView = AppCompatTextView(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isSingleLine = true
            setPadding(textPaddingHor.toInt(), textPaddingVer.toInt(), textPaddingHor.toInt(), textPaddingVer.toInt())
            setTextAppearance(textAppearance)
            text = LocaleService.getInstance().getString(context, LocaleService.Key.TOOLTIP)

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                backgroundCornerRadius.let { cornerRadii = floatArrayOf(it, it, it, it, it, it, it, it) }
                setColor(tooltipBackgroundColor)
                setStroke(backgroundBorderWidth.toInt(), tooltipBorderColor)
            }

            when (currentNightMode) {
                Configuration.UI_MODE_NIGHT_NO -> elevation = 8F
                Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_UNDEFINED -> elevation = 0F
            }
        }

        triangle = AppCompatImageView(context).apply {
            var width = arrowWidth.toInt()
            var height = arrowHeight.toInt()
            if (position.isHorizontal()) {
                width = arrowHeight.toInt()
                height = arrowWidth.toInt()
            }

            layoutParams = LayoutParams(width, height).apply {
                backgroundBorderWidth.toInt().let { setMargins(-it, -it, -it, -it) }
            }

            rotation = when (position) {
                TooltipPosition.TOP, TooltipPosition.START -> 0F
                TooltipPosition.BOTTOM, TooltipPosition.END -> 180F
            }

            gravity = Gravity.CENTER
            background =
                TriangleDrawable(position.isHorizontal(), tooltipBackgroundColor, tooltipBorderColor, backgroundBorderWidth).apply {
                    setBounds(0, 0, width, height)
                }

            when (currentNightMode) {
                Configuration.UI_MODE_NIGHT_NO -> elevation = 8.01F
                Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_UNDEFINED -> elevation = 0F
            }
        }

        when (position) {
            TooltipPosition.TOP, TooltipPosition.START -> {
                addView(textView)
                addView(triangle)
            }
            TooltipPosition.BOTTOM, TooltipPosition.END -> {
                addView(triangle)
                addView(textView)
            }
        }

        // Measure now to be able calculate tooltip position
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED).let { measure(it, it) }
    }

    private var localeUpdateListener: LocaleService.LocaleUpdateListener? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLayout()

        if (isInEditMode.not()) {
            LocaleService.getInstance().registerLocaleUpdateListener(
                object : LocaleService.LocaleUpdateListener { override fun onLocaleUpdated() = setStrings() }
                    .also { localeUpdateListener = it }
            )
        }

        setStrings()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        localeUpdateListener?.let { LocaleService.getInstance().unregisterLocaleUpdateListener(it) }
        localeUpdateListener = null
    }

    private fun setStrings() {
        if (isInEditMode.not()) {
            textView.text = LocaleService.getInstance().getString(context, LocaleService.Key.TOOLTIP)
        } else {
            textView.text = context.getString(R.string.com_ownid_sdk_tooltip)
        }

        if (isInLayout.not()) requestLayout()
        invalidate()
        viewTreeObserver.addOnPreDrawListener(onPreDrawListener)
    }
}