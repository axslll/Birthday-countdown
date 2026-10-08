package com.example.speedometer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Thin horizontal bar: a rounded track with a filled portion. */
class MeterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.hairline)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
    }
    private val rect = RectF()
    private var fraction = 0f

    fun setFraction(value: Float) {
        fraction = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, track)
        if (fraction > 0f) {
            rect.set(0f, 0f, maxOf(width * fraction, height.toFloat()), height.toFloat())
            canvas.drawRoundRect(rect, r, r, fill)
        }
    }
}
