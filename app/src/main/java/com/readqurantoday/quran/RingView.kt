package com.readqurantoday.quran

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** A progress ring: the full track, then the share done drawn over it from the top. */
class RingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var share = 0f

    private val stroke = resources.getDimension(R.dimen.ring_stroke)
    private val track = pen(R.color.line)
    private val done = pen(R.color.accent)
    private val box = RectF()

    private fun pen(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(colour)
    }

    /** [done] of [total]; nothing drawn over the track when [total] is 0. */
    fun show(done: Float, total: Float) {
        share = if (total > 0f) (done / total).coerceIn(0f, 1f) else 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val half = stroke / 2f
        box.set(paddingLeft + half, paddingTop + half, width - paddingRight - half, height - paddingBottom - half)
        canvas.drawOval(box, track)
        // Clockwise in left-to-right reading, the other way in Arabic, as a hand would trace it
        val sweep = 360f * share * if (layoutDirection == LAYOUT_DIRECTION_RTL) -1f else 1f
        if (share > 0f) canvas.drawArc(box, -90f, sweep, false, done)
    }
}
