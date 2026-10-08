package com.example.speedometer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Circular gauge with a 270° sweep, glowing progress arc, tick labels and a digital readout. */
class SpeedometerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val startAngle = 135f
    private val sweep = 270f

    private var maxValue = 240f
    private var step = 20
    private var unitLabel = "km/h"
    private var shown = 0f
    private var target = 0f
    private val animator = ValueAnimator()

    private val density = resources.displayMetrics.density
    private val oval = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#1B2338")
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5C6684"); strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AA4BF"); textAlign = Paint.Align.CENTER
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3D71"); strokeCap = Paint.Cap.ROUND
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141B2D") }
    private val hubRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#FF3D71")
    }
    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); textAlign = Paint.Align.CENTER
    }

    /** Switch the dial scale, e.g. 240 km/h or 160 mph. */
    fun setScale(max: Int, tickStep: Int, unit: String) {
        maxValue = max.toFloat(); step = tickStep; unitLabel = unit
        target = min(target, maxValue); shown = min(shown, maxValue)
        invalidate()
    }

    fun setSpeed(value: Float) {
        target = value.coerceIn(0f, maxValue)
        animator.cancel()
        animator.setFloatValues(shown, target)
        animator.duration = 450
        animator.interpolator = DecelerateInterpolator()
        animator.removeAllUpdateListeners()
        animator.addUpdateListener { shown = it.animatedValue as Float; invalidate() }
        animator.start()
    }

    private fun angleFor(v: Float) = startAngle + sweep * (v / maxValue)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - 24 * density
        val stroke = 16 * density

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        trackPaint.strokeWidth = stroke
        canvas.drawArc(oval, startAngle, sweep, false, trackPaint)

        // progress arc: cyan -> amber -> red
        arcPaint.strokeWidth = stroke
        arcPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.parseColor("#00E5FF"), Color.parseColor("#7CFF6B"),
                Color.parseColor("#FFC107"), Color.parseColor("#FF3D71"),
                Color.parseColor("#00E5FF")
            ),
            floatArrayOf(0f, 0.3f, 0.55f, 0.75f, 1f)
        ).also {
            val m = android.graphics.Matrix()
            m.setRotate(startAngle, cx, cy)
            it.setLocalMatrix(m)
        }
        val progressSweep = sweep * (shown / maxValue)
        if (progressSweep > 0.5f) canvas.drawArc(oval, startAngle, progressSweep, false, arcPaint)

        // ticks + labels
        val outer = radius - stroke / 2f - 8 * density
        labelPaint.textSize = 12 * density
        var v = 0
        while (v <= maxValue) {
            val a = Math.toRadians(angleFor(v.toFloat()).toDouble())
            tickPaint.strokeWidth = 2.5f * density
            val inner = outer - 10 * density
            canvas.drawLine(
                cx + (cos(a) * outer).toFloat(), cy + (sin(a) * outer).toFloat(),
                cx + (cos(a) * inner).toFloat(), cy + (sin(a) * inner).toFloat(), tickPaint
            )
            val lr = inner - 14 * density
            canvas.drawText(
                v.toString(),
                cx + (cos(a) * lr).toFloat(),
                cy + (sin(a) * lr).toFloat() + labelPaint.textSize / 3f,
                labelPaint
            )
            v += step
        }

        // needle
        val na = Math.toRadians(angleFor(shown).toDouble())
        needlePaint.strokeWidth = 4 * density
        canvas.drawLine(
            cx, cy,
            cx + (cos(na) * (outer - 4 * density)).toFloat(),
            cy + (sin(na) * (outer - 4 * density)).toFloat(),
            needlePaint
        )
        hubRing.strokeWidth = 3 * density
        canvas.drawCircle(cx, cy, 14 * density, hubPaint)
        canvas.drawCircle(cx, cy, 14 * density, hubRing)

        // digital readout
        speedPaint.textSize = 54 * density
        canvas.drawText(shown.roundToInt().toString(), cx, cy + radius * 0.62f, speedPaint)
        unitPaint.textSize = 16 * density
        canvas.drawText(unitLabel, cx, cy + radius * 0.62f + 22 * density, unitPaint)
    }
}
