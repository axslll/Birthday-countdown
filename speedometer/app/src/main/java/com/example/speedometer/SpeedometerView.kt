package com.example.speedometer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 270° gauge: thin progress arc, tick scale, large numeric readout.
 * A small tick on the arc marks the top speed of the current trip.
 */
class SpeedometerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val startAngle = 135f
    private val sweep = 270f
    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    private var max = 240f
    private var major = 40
    private var minor = 10
    private var unit = "km/h"
    private var shown = 0f
    private var marker = 0f
    private val animator = ValueAnimator().apply {
        interpolator = DecelerateInterpolator()
        duration = 400
        addUpdateListener { shown = it.animatedValue as Float; invalidate() }
    }

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = 8 * dp; color = color(R.color.hairline)
    }
    private val progress = Paint(track).apply { color = color(R.color.accent) }
    private val tip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(R.color.text_primary) }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeWidth = 2 * dp; color = color(R.color.text_primary)
    }
    private val tickMinor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1 * dp; color = color(R.color.hairline); strokeCap = Paint.Cap.ROUND
    }
    private val tickMajor = Paint(tickMinor).apply {
        strokeWidth = 1.5f * dp; color = color(R.color.text_secondary)
    }
    private val scaleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.text_secondary); textAlign = Paint.Align.CENTER
        textSize = 11 * sp; fontFeatureSettings = "tnum"
    }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.text_primary); textAlign = Paint.Align.CENTER
        textSize = 92 * sp; fontFeatureSettings = "tnum"
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val unitText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(R.color.text_secondary); textAlign = Paint.Align.CENTER
        textSize = 14 * sp; letterSpacing = 0.12f
    }
    private val oval = RectF()

    fun setScale(maxValue: Int, majorStep: Int, minorStep: Int, unitLabel: String) {
        max = maxValue.toFloat(); major = majorStep; minor = minorStep; unit = unitLabel
        shown = min(shown, max); marker = min(marker, max)
        invalidate()
    }

    fun setSpeed(value: Float) {
        val to = value.coerceIn(0f, max)
        animator.cancel()
        animator.setFloatValues(shown, to)
        animator.start()
    }

    fun setMarker(value: Float) {
        marker = value.coerceIn(0f, max)
        invalidate()
    }

    private fun angle(v: Float) = Math.toRadians((startAngle + sweep * v / max).toDouble())

    private fun point(cx: Float, cy: Float, r: Float, a: Double) =
        (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - 16 * dp
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        canvas.drawArc(oval, startAngle, sweep, false, track)
        if (shown > 0.2f) canvas.drawArc(oval, startAngle, sweep * shown / max, false, progress)

        // scale, drawn inside the arc
        val outer = radius - 20 * dp
        var v = 0
        while (v <= max) {
            val isMajor = v % major == 0
            val len = if (isMajor) 10 * dp else 5 * dp
            val a = angle(v.toFloat())
            val (x1, y1) = point(cx, cy, outer, a)
            val (x2, y2) = point(cx, cy, outer - len, a)
            canvas.drawLine(x1, y1, x2, y2, if (isMajor) tickMajor else tickMinor)
            if (isMajor) {
                val (tx, ty) = point(cx, cy, outer - len - 16 * dp, a)
                canvas.drawText(v.toString(), tx, ty + scaleText.textSize * 0.35f, scaleText)
            }
            v += minor
        }

        // top-speed marker across the arc
        if (marker > 0f) {
            val a = angle(marker)
            val (x1, y1) = point(cx, cy, radius - 9 * dp, a)
            val (x2, y2) = point(cx, cy, radius + 9 * dp, a)
            canvas.drawLine(x1, y1, x2, y2, markerPaint)
        }

        // leading dot
        val (dx, dy) = point(cx, cy, radius, angle(shown))
        canvas.drawCircle(dx, dy, 6 * dp, tip)

        // readout
        val baseline = cy + number.textSize * 0.30f
        canvas.drawText(shown.roundToInt().toString(), cx, baseline, number)
        canvas.drawText(unit.uppercase(), cx, baseline + 30 * dp, unitText)
    }
}
