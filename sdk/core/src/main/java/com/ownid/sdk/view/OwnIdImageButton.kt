package com.ownid.sdk.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.PointerIcon
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.RestrictTo
import androidx.appcompat.content.res.AppCompatResources
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.R

/**
 * OwnID Image Button view.
 */
@SuppressLint("AppCompatCustomView", "ClickableViewAccessibility")
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdImageButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : ImageView(context, attrs, defStyleAttr, defStyleRes) {

    private var hasOwnIdResponse: Boolean = false
    private lateinit var stateList: StateListDrawable

    private var backgroundColor: ColorStateList? = null
    private var borderColor: ColorStateList? = null
    private var iconColor: ColorStateList? = null

    init {
        isFocusable = true
        isClickable = true

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> elevation = 1.5f * resources.displayMetrics.density
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> elevation = 0f
            }
            false
        }

        setColors(backgroundColor, borderColor, iconColor)
    }

    @JvmSynthetic
    internal fun setHasOwnIdResponse(value: Boolean) {
        hasOwnIdResponse = value
        refreshDrawableState()
    }

    @InternalOwnIdAPI
    internal fun setColors(
        backgroundColor: ColorStateList? = this.backgroundColor,
        borderColor: ColorStateList? = this.borderColor,
        iconColor: ColorStateList? = this.iconColor
    ) {
        this.backgroundColor = backgroundColor
        this.borderColor = borderColor
        this.iconColor = iconColor

        val background = (AppCompatResources.getDrawable(context, R.drawable.com_ownid_sdk_button_background) as GradientDrawable).apply {
            if (backgroundColor != null || borderColor != null) {
                mutate()
                backgroundColor?.let { color = it }
                borderColor?.let { setStroke(1.toPx, it) }
            }
        }

        val icon = AppCompatResources.getDrawable(context, R.drawable.com_ownid_sdk_button_fingerprint)

        iconColor?.let {
            icon?.mutate()
            icon?.setTintList(it)
        }

        val normal = LayerDrawable(arrayOf(background, icon)).apply {
            setLayerInset(0, 0, 0, 0, 0)
            setLayerInset(1, 6.toPx, 6.toPx, 6.toPx, 6.toPx)
        }

        val checkmark = AppCompatResources.getDrawable(context, R.drawable.com_ownid_sdk_button_checkmark)

        val checked = LayerDrawable(arrayOf(background, icon, checkmark)).apply {
            setLayerInset(0, 0, 0, 0, 0)
            setLayerInset(1, 6.toPx, 6.toPx, 6.toPx, 6.toPx)
            setLayerInsetRelative(2, 0, 0, 0, 0)
        }

        val pressed = LayerDrawable(arrayOf(background, icon)).apply {
            setLayerInset(0, 1.toPx, 1.toPx, 1.toPx, 1.toPx)
            setLayerInset(1, 7.toPx, 7.toPx, 7.toPx, 7.toPx)
        }

        stateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_checked), checked)
            addState(intArrayOf(), normal)
        }

        this.background = stateList
    }

    @SuppressLint("CanvasSize")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        stateList.setBounds(0, 0, canvas.width, canvas.height)
        stateList.draw(canvas)
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray? {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (hasOwnIdResponse) mergeDrawableStates(drawableState, intArrayOf(android.R.attr.state_checked))
        return drawableState
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        stateList.state = drawableState
        invalidate()
    }

    override fun onSetAlpha(alpha: Int): Boolean = false

    override fun getAccessibilityClassName(): CharSequence? = ImageButton::class.java.name

    override fun onResolvePointerIcon(event: MotionEvent?, pointerIndex: Int): PointerIcon? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && pointerIcon == null && isClickable && isEnabled)
            PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HAND)
        else
            super.onResolvePointerIcon(event, pointerIndex)
    }

    private val Number.toPx get() = (this.toFloat() * this@OwnIdImageButton.resources.displayMetrics.density).toInt()
}