package com.ownid.sdk.view

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.MainThread
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R
import com.ownid.sdk.internal.LocaleService
import com.ownid.sdk.internal.events.MetricItem
import com.ownid.sdk.view.delegate.EmailDelegate
import com.ownid.sdk.view.delegate.EmailDelegateImpl
import com.ownid.sdk.view.delegate.LanguageTagsDelegate
import com.ownid.sdk.view.delegate.LanguageTagsDelegateImpl
import com.ownid.sdk.viewmodel.OwnIdBaseViewModel
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

/**
 * OwnID Skip Password Button view.
 */
@OptIn(InternalOwnIdAPI::class)
public class OwnIdButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = R.style.OwnIdButton_Default
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes),
    EmailDelegate by EmailDelegateImpl(),
    LanguageTagsDelegate by LanguageTagsDelegateImpl() {

    @InternalOwnIdAPI
    public interface StringsSetListener {
        @MainThread
        public fun onStringsSet()
    }

    private val stringsSetListenerSet = mutableSetOf<StringsSetListener>()

    @MainThread
    @InternalOwnIdAPI
    public fun registerStringsSetListener(listener: StringsSetListener) {
        stringsSetListenerSet.add(listener)
    }

    @MainThread
    @InternalOwnIdAPI
    public fun unregisterStringsSetListener(listener: StringsSetListener) {
        stringsSetListenerSet.remove(listener)
    }

    private var hasOwnIdResponse: Boolean = false
    private var localeUpdateListener: LocaleService.LocaleUpdateListener? = null

    init {
        inflate(context, R.layout.com_ownid_sdk_button, this)
        gravity = Gravity.CENTER_VERTICAL
    }

    private val tvOr: TextView = findViewById(R.id.com_ownid_sdk_tv_or)
    private val bOwnId: OwnIdImageButton = findViewById(R.id.com_ownid_sdk_image_button)

    init {
        var backgroundColor: ColorStateList? = null
        var borderColor: ColorStateList? = null
        var biometryIconColor: ColorStateList? = null

        context.theme.obtainStyledAttributes(attrs, R.styleable.OwnIdButton, defStyleAttr, defStyleRes).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveAttributeDataForStyleable(context, R.styleable.OwnIdButton, attrs, this, defStyleAttr, defStyleRes)
            }

            try {
                val showOr = getBoolean(R.styleable.OwnIdButton_showOr, true)
                if (showOr.not()) tvOr.visibility = View.GONE

                val orTextAppearance = getResourceId(
                    R.styleable.OwnIdButton_orTextAppearance, R.style.OwnIdButton_OrTextAppearance_Default
                )
                tvOr.setTextAppearance(orTextAppearance)

                backgroundColor = getColorStateList(R.styleable.OwnIdButton_backgroundColor) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_color_button_background)
                }

                borderColor = getColorStateList(R.styleable.OwnIdButton_borderColor) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_color_button_border)
                }

                biometryIconColor = getColorStateList(R.styleable.OwnIdButton_biometryIconColor) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_color_button_biometry_icon)
                }

                setEmailViewId(getResourceId(R.styleable.OwnIdButton_emailEditText, View.NO_ID))
            } finally {
                recycle()
            }
        }

        setColors(backgroundColor, borderColor, biometryIconColor)
    }

    @InternalOwnIdAPI
    public fun setColors(
        backgroundColor: ColorStateList? = null,
        borderColor: ColorStateList? = null,
        biometryIconColor: ColorStateList? = null
    ) {
        bOwnId.setColors(backgroundColor, borderColor, biometryIconColor)
    }

    @InternalOwnIdAPI
    public fun setShowOr(showOr: Boolean) {
        if (showOr.not()) tvOr.visibility = View.GONE
    }

    /**
     * Set an OwnID ViewModel to this view.
     *
     * @param viewModel  an instance of [OwnIdLoginViewModel] or [OwnIdRegisterViewModel].
     * @param owner      this view [LifecycleOwner].
     *
     * @throws IllegalArgumentException if [viewModel] in not an instance of [OwnIdLoginViewModel] or [OwnIdRegisterViewModel]
     */
    public fun setViewModel(viewModel: ViewModel, owner: LifecycleOwner) {
        val ownIdViewModel = (viewModel as? OwnIdBaseViewModel<*>)
            ?: throw IllegalArgumentException("Unexpected ViewModel class: ${viewModel::class.java}")

        val viewType = when (ownIdViewModel) {
            is OwnIdRegisterViewModel -> MetricItem.Category.Registration
            is OwnIdLoginViewModel -> MetricItem.Category.Login
            else -> throw IllegalArgumentException("Unexpected ViewModel class: ${ownIdViewModel::class.java}")
        }

        bOwnId.setOnClickListener {
            ownIdViewModel.sendMetric(viewType, MetricItem.EventType.Click, "Clicked Skip Password", "")
            ownIdViewModel.launch(context, getLanguageTags(this), getEmail(this))
        }

        ownIdViewModel.ownIdResponse.observe(owner) { ownIdResponse ->
            val hasResponse = ownIdResponse != null
            if (hasResponse == hasOwnIdResponse) return@observe
            hasOwnIdResponse = hasResponse
            bOwnId.setHasOwnIdResponse(hasOwnIdResponse)

            if (hasOwnIdResponse) {
                bOwnId.setOnClickListener {
                    ownIdViewModel.sendMetric(viewType, MetricItem.EventType.Click, "Clicked Skip Password Undo", ownIdResponse.context)
                    ownIdViewModel.undo()
                }
            } else {
                bOwnId.setOnClickListener {
                    ownIdViewModel.sendMetric(viewType, MetricItem.EventType.Click, "Clicked Skip Password", "")
                    ownIdViewModel.launch(context, getLanguageTags(this), getEmail(this))
                }
            }
        }

        ownIdViewModel.sendTrackMetric(viewType, "OwnID Widget is Loaded", "")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode.not()) {
            localeUpdateListener = object : LocaleService.LocaleUpdateListener {
                override fun onLocaleUpdated() {
                    setStrings()
                }
            }
            LocaleService.getInstance().registerLocaleUpdateListener(localeUpdateListener!!)
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
            LocaleService.getInstance().apply {
                bOwnId.contentDescription = getString(context, LocaleService.Key.SKIP_PASSWORD)
                tvOr.text = getString(context, LocaleService.Key.OR)
            }
        } else {
            bOwnId.contentDescription = context.getString(R.string.com_ownid_sdk_skip_password)
            tvOr.text = context.getString(R.string.com_ownid_sdk_or)
            if (isInLayout.not()) requestLayout()
        }
        stringsSetListenerSet.forEach { listener -> listener.onStringsSet() }
    }
}