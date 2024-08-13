package com.ownid.sdk.view

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.locale.OwnIdLocaleKey


/**
 * OwnID Auth Button view. Extends ConstraintLayout and holds all OwnID view elements.
 */
@OptIn(InternalOwnIdAPI::class)
public open class OwnIdAuthButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = R.attr.ownIdAuthButtonStyle,
    @StyleRes defStyleRes: Int = R.style.OwnIdAuthButton_Default
) : AbstractOwnIdWidget(context, attrs, defStyleAttr, defStyleRes) {

    protected val button: MaterialButton
    protected val progress: CircularProgressIndicator

    private var isBusy: Boolean = false
        @MainThread
        set(value) {
            OwnIdInternalLogger.logD(this, "setBusy", "$value")
            field = value
            if (value) {
                button.text = ""
                progress.show()
            } else {
                progress.hide()
                setStrings()
            }
        }

    private var textColor: ColorStateList? = null
    private var buttonTintList: ColorStateList? = null
    @ColorInt private var spinnerIndicatorColor: Int? = null
    @ColorInt private var spinnerTrackColor: Int? = null

    init {
        inflate(context, R.layout.com_ownid_sdk_button_auth, this)
        setPadding(0.toPx, 0.toPx, 0.toPx, 0.toPx)

        filterTouchesWhenObscured = true

        button = findViewById(R.id.com_ownid_sdk_button_auth_button)
        progress = findViewById(R.id.com_ownid_sdk_button_auth_progress)

        var textColor: ColorStateList? = null
        var buttonTintList: ColorStateList? = null
        var spinnerIndicatorColor: Int? = null
        var spinnerTrackColor: Int? = null

        context.theme.obtainStyledAttributes(attrs, R.styleable.OwnIdAuthButton, defStyleAttr, defStyleRes).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveAttributeDataForStyleable(context, R.styleable.OwnIdAuthButton, attrs, this, defStyleAttr, defStyleRes)
            }

            try {
                textColor = getColorStateList(R.styleable.OwnIdAuthButton_textColor) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_widgets_button_auth_color_text)
                }

                button.setTextAppearance(
                    getResourceId(R.styleable.OwnIdAuthButton_textAppearance, R.style.OwnIdAuthButton_TextAppearance_Default)
                )

                buttonTintList = getColorStateList(R.styleable.OwnIdAuthButton_backgroundTint) ?: run {
                    ContextCompat.getColorStateList(context, R.color.com_ownid_sdk_widgets_button_auth_color_background)
                }

                spinnerIndicatorColor = getColor(
                    R.styleable.OwnIdAuthButton_spinnerIndicatorColor,
                    ContextCompat.getColor(context, R.color.com_ownid_sdk_widgets_button_auth_color_spinner_indicator)
                )

                spinnerTrackColor = getColor(
                    R.styleable.OwnIdAuthButton_spinnerTrackColor,
                    ContextCompat.getColor(context, R.color.com_ownid_sdk_widgets_button_auth_color_spinner_track)
                )

                setLoginIdViewId(getResourceId(R.styleable.OwnIdAuthButton_loginIdEditText, View.NO_ID))
            } finally {
                recycle()
            }
        }

        setColors(textColor, buttonTintList, spinnerIndicatorColor, spinnerTrackColor)
    }

    public fun setColors(
        textColor: ColorStateList? = this.textColor,
        backgroundTintList: ColorStateList? = this.buttonTintList,
        @ColorInt spinnerIndicatorColor: Int? = this.spinnerIndicatorColor,
        @ColorInt spinnerTrackColor: Int? = this.spinnerTrackColor
    ) {
        this.textColor = textColor
        this.buttonTintList = backgroundTintList
        this.spinnerIndicatorColor = spinnerIndicatorColor
        this.spinnerTrackColor = spinnerTrackColor

        textColor?.let { button.setTextColor(it) }
        backgroundTintList?.let { button.backgroundTintList = it }
        spinnerIndicatorColor?.let { progress.setIndicatorColor(it) }
        spinnerTrackColor?.let { progress.trackColor = it }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        button.setOnClickListener(l)
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    internal override fun getMetadata(): Metadata = Metadata(widgetType = Metadata.WidgetType.AUTH_BUTTON)

    @JvmSynthetic
    @InternalOwnIdAPI
    public override fun onBusy(isBusy: Boolean) {
        this.isBusy = isBusy
    }

    @CallSuper
    protected override fun setStrings() {
        button.text = if (isInEditMode.not())
            if (isBusy) ""
            else getLocaleService()?.getString(context, CONTINUE) ?: context.getString(R.string.com_ownid_sdk_widgets_button_auth_message)
        else
            context.getString(R.string.com_ownid_sdk_widgets_button_auth_message)

        button.minEms = button.text.length

        if (isInLayout.not()) requestLayout()
    }

    private companion object LocaleKeys {
        private val CONTINUE = OwnIdLocaleKey("widgets", "auth-button", "message")
            .withFallback(R.string.com_ownid_sdk_widgets_button_auth_message)
    }
}