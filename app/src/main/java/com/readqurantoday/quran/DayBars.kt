package com.readqurantoday.quran

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View

/** Pages read on each of the last days as bars, today at the reading end; a goal, if set, as a dashed line. */
class DayBars(context: Context) : View(context) {

    private var pages: List<Int> = emptyList()
    private var goal = 0

    private val d = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.accent) }
    private val none = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.line) }
    private val goalPen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_mute)
        strokeWidth = d
        pathEffect = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f)
    }

    fun show(pages: List<Int>, goal: Int) {
        this.pages = pages
        this.goal = goal
        invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val tall = resources.getDimensionPixelSize(R.dimen.chart_height) + paddingTop + paddingBottom
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthSpec), resolveSize(tall, heightSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (pages.isEmpty()) return
        val top = maxOf(pages.max(), goal, 1)
        val base = (height - paddingBottom).toFloat()
        val room = base - paddingTop
        val slot = (width - paddingLeft - paddingRight).toFloat() / pages.size
        val inset = slot * 0.2f
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL

        pages.forEachIndexed { i, n ->
            // Oldest first, so the newest day sits where the line of reading ends
            val left = if (rtl) width - paddingRight - (i + 1) * slot else paddingLeft + i * slot
            // A day with nothing read keeps a stub, so the run of days still reads as a calendar
            val rise = if (n > 0) room * n / top else 2 * d
            canvas.drawRoundRect(left + inset, base - rise, left + slot - inset, base, d, d, if (n > 0) bar else none)
        }

        if (goal > 0) {
            val y = base - room * goal / top
            canvas.drawLine(paddingLeft.toFloat(), y, (width - paddingRight).toFloat(), y, goalPen)
        }
    }
}
