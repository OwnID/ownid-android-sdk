package com.ownid.sdk.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R
import com.ownid.sdk.internal.LocaleService
import com.ownid.sdk.internal.events.MetricItem
import com.ownid.sdk.view.delegate.EmailDelegate
import com.ownid.sdk.view.delegate.EmailDelegateImpl
import com.ownid.sdk.view.delegate.EmailValidator.isValidEmail
import com.ownid.sdk.view.delegate.LanguageTagsDelegate
import com.ownid.sdk.view.delegate.LanguageTagsDelegateImpl
import com.ownid.sdk.view.tooltip.Tooltip
import com.ownid.sdk.view.tooltip.TooltipPosition
import com.ownid.sdk.viewmodel.OwnIdBaseViewModel
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

/**
 * OwnID Skip Password Button view.
 */
@androidx.annotation.OptIn(InternalOwnIdAPI::class)
public open class OwnIdButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = R.style.OwnIdButton_Default
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes),
    EmailDelegate by EmailDelegateImpl(),
    LanguageTagsDelegate by LanguageTagsDelegateImpl() {

    public enum class IconVariant(@DrawableRes private val drawableId: Int) {
        FINGERPRINT(R.drawable.com_ownid_sdk_button_fingerprint),
        FACE_ID(R.drawable.com_ownid_sdk_button_face_id);

        @InternalOwnIdAPI
        internal fun getDrawable(context: Context): Drawable = AppCompatResources.getDrawable(context, drawableId)!!
    }

    private var localeUpdateListener: LocaleService.LocaleUpdateListener? = null

    @InternalOwnIdAPI
    protected lateinit var ownIdViewModel: OwnIdBaseViewModel<*>

    @InternalOwnIdAPI
    protected lateinit var lifecycleOwner: LifecycleOwner

    @InternalOwnIdAPI
    protected var tooltipTextAppearance: Int = 0

    @InternalOwnIdAPI
    protected var tooltipBackgroundColor: Int = 0

    @InternalOwnIdAPI
    protected var tooltipBorderColor: Int = 0

    @InternalOwnIdAPI
    protected var tooltipPosition: TooltipPosition? = null

    @InternalOwnIdAPI
    protected var tooltip: Tooltip? = null

    private var oldVisible: Boolean? = null

    init {
        inflate(context, R.layout.com_ownid_sdk_button, this)
        gravity = Gravity.CENTER_VERTICAL
    }

    @InternalOwnIdAPI
    protected val tvOr: TextView = findViewById(R.id.com_ownid_sdk_tv_or)

    @InternalOwnIdAPI
    protected val bOwnId: OwnIdImageButton = findViewById(R.id.com_ownid_sdk_image_button)

    init {
        var backgroundColor: ColorStateList? = null
        var borderColor: ColorStateList? = null
        var iconColor: ColorStateList? = null

        context.theme.obtainStyledAttributes(attrs, R.styleable.OwnIdButton, defStyleAttr, defStyleRes).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveAttributeDataForStyleable(context, R.styleable.OwnIdButton, attrs, this, defStyleAttr, defStyleRes)
            }

            try {
                val iconVariant = when (getInt(R.styleable.OwnIdButton_variant, 0)) {
                    1 -> IconVariant.FACE_ID
                    else -> IconVariant.FINGERPRINT
                }
                bOwnId.setIconVariant(iconVariant)

                val showOr = getBoolean(R.styleable.OwnIdButton_showOr, true)
                if (showOr.not()) tvOr.visibility = View.GONE

                val orTextAppearance = getResourceId(R.styleable.OwnIdButton_orTextAppearance, R.style.OwnIdButton_OrTextAppearance_Default)
                tvOr.setTextAppearance(orTextAppearance)

                backgroundColor = getColorStateList(R.styleable.OwnIdButton_backgroundColor) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_color_button_background)
                }

                borderColor = getColorStateList(R.styleable.OwnIdButton_borderColor) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_color_button_border)
                }

                iconColor = getColorStateList(R.styleable.OwnIdButton_iconColor) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_color_button_icon)
                }

                setEmailViewId(getResourceId(R.styleable.OwnIdButton_emailEditText, View.NO_ID))

                tooltipTextAppearance =
                    getResourceId(R.styleable.OwnIdButton_tooltipTextAppearance, R.style.OwnIdButton_TooltipTextAppearance_Default)

                tooltipBackgroundColor = getColor(
                    R.styleable.OwnIdButton_tooltipBackgroundColor,
                    ContextCompat.getColor(context, R.color.com_ownid_sdk_color_tooltip_background)
                )

                tooltipBorderColor = getColor(
                    R.styleable.OwnIdButton_tooltipBorderColor,
                    ContextCompat.getColor(context, R.color.com_ownid_sdk_color_tooltip_border)
                )

                val tooltipPositionIndex = getInt(R.styleable.OwnIdButton_tooltipPosition, 0)
                tooltipPosition = when (tooltipPositionIndex) {
                    1 -> TooltipPosition.TOP
                    2 -> TooltipPosition.BOTTOM
                    3 -> TooltipPosition.START
                    4 -> TooltipPosition.END
                    else -> null
                }

            } finally {
                recycle()
            }
        }

        bOwnId.setColors(backgroundColor, borderColor, iconColor)
    }

    @InternalOwnIdAPI
    protected open fun createTooltip() {
        tooltip = tooltipPosition?.let { position ->
            Tooltip(bOwnId, position, tooltipTextAppearance, tooltipBackgroundColor, tooltipBorderColor)
        } ?: return

        if (ownIdViewModel is OwnIdRegisterViewModel) {
            getEmailView(this@OwnIdButton)?.setOnFocusChangeListener { _, hasFocus ->
                val hasOwnIdResponse = ownIdViewModel.ownIdResponseStatus.value?.hasResponse() ?: false
                if (hasFocus.not() && getEmail(this@OwnIdButton).isValidEmail() && hasOwnIdResponse.not()) showTooltip()
            }
        }

        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                super.onResume(owner)
                val hasOwnIdResponse = ownIdViewModel.ownIdResponseStatus.value?.hasResponse() ?: false
                if (hasOwnIdResponse) return
                when (ownIdViewModel) {
                    is OwnIdRegisterViewModel -> if (getEmail(this@OwnIdButton).isValidEmail()) showTooltip()
                    is OwnIdLoginViewModel -> showTooltip()
                }
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                hideTooltip()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                hideTooltip()
                tooltip = null
            }
        })
    }

    @InternalOwnIdAPI
    protected open fun showTooltip() {
        if (visibility == View.VISIBLE) tooltip?.show()
    }

    @InternalOwnIdAPI
    protected open fun hideTooltip() {
        tooltip?.dismiss()
    }

    /**
     * Set an OwnID ViewModel to this view.
     *
     * @param viewModel  an instance of [OwnIdLoginViewModel] or [OwnIdRegisterViewModel].
     * @param owner      this view [LifecycleOwner].
     */
    public fun setViewModel(viewModel: OwnIdBaseViewModel<*>, owner: LifecycleOwner) {
        ownIdViewModel = viewModel
        lifecycleOwner = owner
        createTooltip()
        viewModel.attachToViewInternal(bOwnId, owner, { getEmail(this) }, { getLanguageTags(this) }) { hasOwnIdResponse ->
            bOwnId.setHasOwnIdResponse(hasOwnIdResponse)
        }
        viewModel.sendMetric(MetricItem.EventType.Track, "OwnID Widget is Loaded")
    }

    @CallSuper
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        oldVisible != isVisible || return
        oldVisible = isVisible
        if (isVisible.not()) hideTooltip()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

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

    @CallSuper
    @InternalOwnIdAPI
    protected open fun setStrings() {
        if (isInEditMode.not()) {
            LocaleService.getInstance().apply {
                bOwnId.contentDescription = getString(context, LocaleService.Key.SKIP_PASSWORD)
                tvOr.text = getString(context, LocaleService.Key.OR)
            }
        } else {
            bOwnId.contentDescription = context.getString(R.string.com_ownid_sdk_skip_password)
            tvOr.text = context.getString(R.string.com_ownid_sdk_or)
        }
        if (isInLayout.not()) requestLayout()
    }
}