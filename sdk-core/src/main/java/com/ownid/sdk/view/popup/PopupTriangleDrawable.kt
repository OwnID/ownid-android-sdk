package com.ownid.sdk.view.popup

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PopupTriangleDrawable(
    private val isHorizontal: Boolean,
    private val shapeColor: Int,
    private val borderColor: Int,
    private val borderWidth: Float
) : Drawable() {

    private val shapePath: Path = Path()
    private val shapePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeJoin = Paint.Join.ROUND
        color = shapeColor
        style = Paint.Style.FILL
    }

    private val borderPath: Path = Path()
    private val borderPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeJoin = Paint.Join.ROUND
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
    }

    private var isDirty: Boolean = true

    private fun updatePath() {
        if (isDirty) isDirty = false
        shapePath.reset()
        borderPath.reset()

        if (isHorizontal) {
            shapePath.moveTo(0f, 0f)
            shapePath.lineTo((bounds.width()).toFloat(), (bounds.height() / 2).toFloat())
            shapePath.lineTo(0f, bounds.height().toFloat())
            shapePath.close()

            borderPath.moveTo(0f, 0f)
            borderPath.lineTo((bounds.width()).toFloat(), (bounds.height() / 2).toFloat())
            borderPath.lineTo(0f, bounds.height().toFloat())
        } else {
            shapePath.moveTo(0f, 0f)
            shapePath.lineTo((bounds.width() / 2).toFloat(), bounds.height().toFloat())
            shapePath.lineTo(bounds.width().toFloat(), 0f)
            shapePath.close()

            borderPath.moveTo(0f, 0f)
            borderPath.lineTo((bounds.width() / 2).toFloat(), bounds.height().toFloat())
            borderPath.lineTo(bounds.width().toFloat(), 0f)
        }
    }

    override fun draw(canvas: Canvas) {
        if (isDirty) updatePath()
        canvas.drawPath(shapePath, shapePaint)
        canvas.drawPath(borderPath, borderPaint)
    }

    override fun setAlpha(alpha: Int) {
        shapePaint.alpha = alpha
        borderPaint.alpha = alpha
        isDirty = true
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        shapePaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
        isDirty = true
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        if (shapePaint.colorFilter != null) return PixelFormat.TRANSLUCENT

        when (shapePaint.color ushr 24) {
            255 -> return PixelFormat.OPAQUE
            0 -> return PixelFormat.TRANSPARENT
        }

        return PixelFormat.TRANSLUCENT
    }

    override fun getOutline(outline: Outline) {
        outline.setEmpty()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (isDirty) updatePath()
            outline.setPath(shapePath)
            outline.alpha = 1.0F
        }
    }
}