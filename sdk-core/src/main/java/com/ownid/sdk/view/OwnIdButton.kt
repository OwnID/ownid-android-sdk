package com.ownid.sdk.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R
import com.ownid.sdk.internal.events.Metadata
import com.ownid.sdk.internal.locale.OwnIdLocaleKey
import com.ownid.sdk.view.popup.Popup
import com.ownid.sdk.view.popup.tooltip.Tooltip
import com.ownid.sdk.view.popup.tooltip.TooltipView
import com.ownid.sdk.viewmodel.OwnIdBaseViewModel
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel
import kotlinx.coroutines.job

/**
 * OwnID Button view. Extends ConstraintLayout and holds all OwnID view elements.
 */
@OptIn(InternalOwnIdAPI::class)
public open class OwnIdButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = R.attr.ownIdButtonStyle,
    @StyleRes defStyleRes: Int = R.style.OwnIdButton_Default
) : AbstractOwnIdWidget(context, attrs, defStyleAttr, defStyleRes) {

    /** OwnID button position.
     * [Position.START] - Before password input field.
     * [Position.END] - After password input field.
     */
    public enum class Position { START, END; }

    protected var position: Position = Position.START
        set(value) {
            field = value
            val orMargin = resources.getDimensionPixelSize(R.dimen.com_ownid_sdk_button_or_margin)
            when (value) {
                Position.START -> ConstraintSet().apply {
                    clone(this@OwnIdButton)
                    clear(R.id.com_ownid_sdk_image_button, ConstraintSet.START)
                    connect(R.id.com_ownid_sdk_image_button, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    clear(R.id.com_ownid_sdk_tv_or, ConstraintSet.START)
                    connect(R.id.com_ownid_sdk_tv_or, ConstraintSet.START, R.id.com_ownid_sdk_image_button, ConstraintSet.END, orMargin)
                    applyTo(this@OwnIdButton)
                }

                Position.END -> ConstraintSet().apply {
                    clone(this@OwnIdButton)
                    clear(R.id.com_ownid_sdk_image_button, ConstraintSet.START)
                    connect(R.id.com_ownid_sdk_image_button, ConstraintSet.START, R.id.com_ownid_sdk_tv_or, ConstraintSet.END, orMargin)
                    clear(R.id.com_ownid_sdk_tv_or, ConstraintSet.START)
                    connect(R.id.com_ownid_sdk_tv_or, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    applyTo(this@OwnIdButton)
                }
            }
        }

    protected var showOr: Boolean = true
        set(value) {
            field = value
            tvOr.visibility = if (value) View.VISIBLE else View.GONE
        }

    protected var showSpinner: Boolean = true
        set(value) {
            field = value
            if (value.not()) progress.visibility = View.GONE
        }

    protected var tooltipProperties: Tooltip.Properties = Tooltip.Properties()
    protected var tooltip: Tooltip? = null
        private set
    private var oldVisible: Boolean? = null

    protected val tvOr: TextView
    protected val bOwnId: OwnIdImageButton
    protected val progress: CircularProgressIndicator

    private var textColor: ColorStateList? = null
    private var backgroundColor: ColorStateList? = null
    private var borderColor: ColorStateList? = null
    private var iconColor: ColorStateList? = null
    @ColorInt private var spinnerIndicatorColor: Int? = null
    @ColorInt private var spinnerTrackColor: Int? = null

    init {
        inflate(context, R.layout.com_ownid_sdk_button, this)

        tvOr = findViewById(R.id.com_ownid_sdk_tv_or)
        bOwnId = findViewById(R.id.com_ownid_sdk_image_button)
        progress = findViewById(R.id.com_ownid_sdk_progress)

        var backgroundColor: ColorStateList? = null
        var borderColor: ColorStateList? = null
        var iconColor: ColorStateList? = null
        var spinnerIndicatorColor: Int? = null
        var spinnerTrackColor: Int? = null

        context.theme.obtainStyledAttributes(attrs, R.styleable.OwnIdButton, defStyleAttr, defStyleRes).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveAttributeDataForStyleable(context, R.styleable.OwnIdButton, attrs, this, defStyleAttr, defStyleRes)
            }

            try {
                position = when (getInt(R.styleable.OwnIdButton_widgetPosition, 0)) {
                    1 -> Position.END
                    else -> Position.START
                }

                showOr = getBoolean(R.styleable.OwnIdButton_showOr, true)

                val orTextAppearance = getResourceId(R.styleable.OwnIdButton_orTextAppearance, R.style.OwnIdButton_OrTextAppearance_Default)
                tvOr.setTextAppearance(orTextAppearance)

                backgroundColor = getColorStateList(R.styleable.OwnIdButton_backgroundColor) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_widgets_button_color_background)
                }

                borderColor = getColorStateList(R.styleable.OwnIdButton_borderColor) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_widgets_button_color_border)
                }

                iconColor = getColorStateList(R.styleable.OwnIdButton_iconColor) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_widgets_button_color_icon)
                }

                showSpinner = getBoolean(R.styleable.OwnIdButton_showSpinner, true)

                spinnerIndicatorColor = getColor(
                    R.styleable.OwnIdButton_spinnerIndicatorColor,
                    ContextCompat.getColor(context, R.color.com_ownid_sdk_widgets_button_color_spinner_indicator)
                )

                spinnerTrackColor = getColor(
                    R.styleable.OwnIdButton_spinnerTrackColor,
                    ContextCompat.getColor(context, R.color.com_ownid_sdk_widgets_button_color_spinner_track)
                )

                setLoginIdViewId(getResourceId(R.styleable.OwnIdButton_loginIdEditText, View.NO_ID))

                val tooltipTextAppearance =
                    getResourceId(R.styleable.OwnIdButton_tooltipTextAppearance, R.style.OwnIdButton_TooltipTextAppearance_Default)

                val tooltipBackgroundColor = getColor(
                    R.styleable.OwnIdButton_tooltipBackgroundColor, context.getColor(R.color.com_ownid_sdk_widgets_button_color_tooltip_background)
                )

                val tooltipBorderColor = getColor(
                    R.styleable.OwnIdButton_tooltipBorderColor, context.getColor(R.color.com_ownid_sdk_widgets_button_color_tooltip_border)
                )

                val tooltipPosition = when (getInt(R.styleable.OwnIdButton_tooltipPosition, 0)) {
                    1 -> Popup.Position.TOP
                    2 -> Popup.Position.BOTTOM
                    3 -> Popup.Position.START
                    4 -> Popup.Position.END
                    else -> null
                }

                tooltipProperties =
                    Tooltip.Properties(tooltipTextAppearance, null, tooltipBackgroundColor, tooltipBorderColor, tooltipPosition)
            } finally {
                recycle()
            }
        }

        setColors(null, backgroundColor, borderColor, iconColor, spinnerIndicatorColor, spinnerTrackColor)
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    public fun setColors(
        textColor: ColorStateList? = this.textColor,
        backgroundColor: ColorStateList? = this.backgroundColor,
        borderColor: ColorStateList? = this.borderColor,
        iconColor: ColorStateList? = this.iconColor,
        @ColorInt spinnerIndicatorColor: Int? = this.spinnerIndicatorColor,
        @ColorInt spinnerTrackColor: Int? = this.spinnerTrackColor
    ) {
        this.textColor = textColor
        this.backgroundColor = backgroundColor
        this.borderColor = borderColor
        this.iconColor = iconColor
        this.spinnerIndicatorColor = spinnerIndicatorColor
        this.spinnerTrackColor = spinnerTrackColor

        textColor?.let { tvOr.setTextColor(it) }

        bOwnId.setColors(backgroundColor, borderColor, iconColor)

        spinnerIndicatorColor?.let { progress.setIndicatorColor(it) }
        spinnerTrackColor?.let { progress.trackColor = it }
        progress.background =
            (AppCompatResources.getDrawable(context, R.drawable.com_ownid_sdk_button_background) as GradientDrawable).apply {
                if (backgroundColor != null || borderColor != null) {
                    mutate()
                    backgroundColor?.let { color = it }
                    borderColor?.let { setStroke(1.toPx, it) }
                }
            }
    }

    @InternalOwnIdAPI
    override fun setOnClickListener(l: OnClickListener?) {
        bOwnId.setOnClickListener(l)
    }

    protected open fun createTooltip() {
        tooltip = tooltipProperties.position?.let { position ->
            val tooltipType = when (ownIdViewModel) {
                is OwnIdRegisterViewModel -> Tooltip.Type.REGISTER
                else -> Tooltip.Type.LOGIN
            }
            val tooltipView = TooltipView(tooltipType, getLocaleService()!!, bOwnId, position, tooltipProperties)
            Tooltip(bOwnId, tooltipView, position)
        } ?: return

        val viewModel = requireNotNull(ownIdViewModel)

        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                when (viewModel) {
                    is OwnIdRegisterViewModel -> {
                        if (viewModel.isReadyToRegister.not() && isLoginIdValid(getLoginId())) showTooltip()
                        setLoginIdChangeListener {
                            if (viewModel.isReadyToRegister.not() && isLoginIdValid(getLoginId())) showTooltip()
                        }
                    }

                    is OwnIdLoginViewModel -> if (viewModel.isReadyToRegister.not()) showTooltip()
                }
            }

            override fun onPause(owner: LifecycleOwner) {
                if (viewModel is OwnIdRegisterViewModel) setLoginIdChangeListener(null)
                hideTooltip()
            }
        }

        val scope = requireNotNull(viewModel.viewLifecycleCoroutineScope)

        scope.lifecycle.addObserver(observer)
        scope.coroutineContext.job.invokeOnCompletion {
            scope.lifecycle.removeObserver(observer)
            hideTooltip()
            tooltip = null
        }
    }

    protected open fun showTooltip() {
        if (visibility == View.VISIBLE) tooltip?.show()
    }

    protected open fun hideTooltip() {
        tooltip?.dismiss()
    }

    @CallSuper
    @JvmSynthetic
    @InternalOwnIdAPI
    internal override fun setViewModel(viewModel: OwnIdBaseViewModel) {
        super.setViewModel(viewModel)
        createTooltip()
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    internal override fun getMetadata(): Metadata = Metadata(
        widgetPosition = position,
        widgetType = Metadata.WidgetType.FINGERPRINT
    )

    @JvmSynthetic
    @InternalOwnIdAPI
    public override fun onBusy(isBusy: Boolean) {
        showSpinner || return
        if (isBusy) progress.show() else progress.hide()
    }

    @CallSuper
    protected override fun setHasOwnIdResponse(value: Boolean) {
        super.setHasOwnIdResponse(value)
        bOwnId.setHasOwnIdResponse(value)
    }

    @CallSuper
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        oldVisible != isVisible || return
        oldVisible = isVisible
        if (isVisible.not()) hideTooltip()
    }

    @CallSuper
    protected override fun setStrings() {
        if (isInEditMode.not()) {
            bOwnId.contentDescription = getLocaleService()?.getString(context, SKIP_PASSWORD)
                ?: context.getString(R.string.com_ownid_sdk_widgets_sbsButton_skipPassword)
            tvOr.text = getLocaleService()?.getString(context, OR)
                ?: context.getString(R.string.com_ownid_sdk_widgets_sbsButton_or)
        } else {
            bOwnId.contentDescription = context.getString(R.string.com_ownid_sdk_widgets_sbsButton_skipPassword)
            tvOr.text = context.getString(R.string.com_ownid_sdk_widgets_sbsButton_or)
        }
        if (isInLayout.not()) requestLayout()
    }

    private fun isLoginIdValid(loginId: String): Boolean {
        val configuration = ownIdViewModel?.ownIdCore?.configuration ?: return false
        configuration.isServerConfigurationSet || return false
        return configuration.server.loginId.regex.matches(loginId)
    }

    private companion object LocaleKeys {
        private val SKIP_PASSWORD = OwnIdLocaleKey("widgets", "sbs-button", "skipPassword")
            .withFallback(R.string.com_ownid_sdk_widgets_sbsButton_skipPassword)

        private val OR = OwnIdLocaleKey("widgets", "sbs-button", "or")
            .withFallback(R.string.com_ownid_sdk_widgets_sbsButton_or)
    }
}