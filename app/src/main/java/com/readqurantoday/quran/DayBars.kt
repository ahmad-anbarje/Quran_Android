package com.readqurantoday.quran

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import kotlin.math.ceil

/**
 * Pages read on each of the last days as bars, today at the reading end, with a scale of pages up the side,
 * days named along the foot, and the goal, if set, as a dashed line. A week names every day and writes its pages.
 */
class DayBars(context: Context) : View(context) {

    private var pages: List<Int> = emptyList()
    private var goal = 0
    private var goalSaid = ""
    private var dayName: (Int) -> String = { "" }

    // 0 to 1 across the whole rise; each bar takes its own slice of it, oldest first
    private var grown = 1f

    private val d = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.accent) }
    private val none = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.line) }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.line)
        strokeWidth = d
    }
    private val goalPen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_mute)
        strokeWidth = d
        pathEffect = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_mute)
        textSize = resources.getDimension(R.dimen.chart_label)
        typeface = ResourcesCompat.getFont(context, R.font.cairo)
    }
    private val count = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text)
        textSize = resources.getDimension(R.dimen.chart_label)
        typeface = ResourcesCompat.getFont(context, R.font.cairo)
        textAlign = Paint.Align.CENTER
    }
    private val gap = resources.getDimension(R.dimen.chart_gap)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.surface) }
    private val chip = android.graphics.RectF()

    /**
     * [pages] oldest first; [dayName] names a day by how many days ago it was; [goalSaid] labels the goal line.
     * [grow] raises the bars from the baseline one after another; otherwise they simply stand.
     */
    fun show(pages: List<Int>, goal: Int, goalSaid: String, dayName: (Int) -> String, grow: Boolean = false) {
        this.pages = pages
        this.goal = goal
        this.goalSaid = goalSaid
        this.dayName = dayName
        grown = if (grow) 0f else 1f
        invalidate()
        if (!grow) return
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GROW_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                grown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // A bar's own progress: it starts when the wave reaches it and eases in over a few bars' time
    private fun rise(i: Int): Float {
        val local = ((grown * (pages.size + WAVE) - i) / WAVE).coerceIn(0f, 1f)
        return 1f - (1f - local) * (1f - local)
    }

    // A scale that ends on a round number with a whole number halfway, so its marks read as counts
    private fun scaleTop(): Int {
        val top = maxOf(pages.maxOrNull() ?: 0, goal, 1)
        return when {
            top <= 4 -> top
            top <= 10 -> (top + 1) / 2 * 2
            else -> (ceil(top / 10.0) * 10).toInt()
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val foot = label.textSize + gap
        val tall = resources.getDimensionPixelSize(R.dimen.chart_height) + paddingTop + paddingBottom + foot.toInt()
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthSpec), resolveSize(tall, heightSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (pages.isEmpty()) return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        // A week is few enough bars to name every day and write its pages over it; that says what a scale would
        val everyDay = pages.size <= EVERY_DAY
        val top = scaleTop()
        val marks = listOf(0, top / 2, top).distinct()
        val said = marks.associateWith { figures(it, resources) }

        // The scale sits at the start edge; the bars take the rest
        val gutter = if (everyDay) 0f else said.values.maxOf { label.measureText(it) } + gap
        val left = paddingLeft + if (rtl) 0f else gutter
        val right = width - paddingRight - if (rtl) gutter else 0f
        val roof = paddingTop + label.textSize / 2f + if (everyDay) label.textSize + gap else 0f
        val base = height - paddingBottom - label.textSize - gap
        val room = base - roof

        // Scale: a faint line at each mark, its number beside it
        label.textAlign = if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT
        val numberX = if (rtl) right + gap else left - gap
        for (m in if (everyDay) listOf(0) else marks) {
            val y = base - room * m / top
            canvas.drawLine(left, y, right, y, grid)
            // The baseline speaks for itself, and an Arabic zero is only a dot
            if (m > 0) canvas.drawText(said.getValue(m), numberX, y + label.textSize / 3f, label)
        }

        val slot = (right - left) / pages.size
        val inset = slot * 0.2f
        pages.forEachIndexed { i, n ->
            // Oldest first, so the newest day sits where the line of reading ends
            val x = if (rtl) right - (i + 1) * slot else left + i * slot
            // A day with nothing read keeps a stub, so the run of days still reads as a calendar
            val tall = (if (n > 0) room * n / top else 2 * d) * rise(i)
            canvas.drawRoundRect(x + inset, base - tall, x + slot - inset, base, d, d, if (n > 0) bar else none)
            if (everyDay && n > 0 && rise(i) == 1f) canvas.drawText(figures(n, resources), x + slot / 2f, base - tall - gap, count)
        }

        // Days along the foot: today under its bar, then every so many days back
        label.textAlign = Paint.Align.CENTER
        val footY = base + gap + label.textSize * 0.8f
        val last = pages.size - 1
        val named = if (everyDay) (0..last).toList() else listOf(0, last / 3, 2 * last / 3, last).distinct()
        val full = label.textSize
        // Every day's name fits under its own bar; on a narrow screen they all shrink alike
        if (everyDay) {
            val widest = named.maxOf { label.measureText(dayName(it)) }
            if (widest > slot - gap) label.textSize = full * (slot - gap) / widest
        }
        for (ago in named) {
            val i = last - ago
            val centre = if (rtl) right - (i + 0.5f) * slot else left + (i + 0.5f) * slot
            val text = dayName(ago)
            // Kept inside the chart, so an end label never runs off the card
            val half = label.measureText(text) / 2f
            canvas.drawText(text, centre.coerceIn(left + half, right - half), footY, label)
        }
        label.textSize = full

        if (goal > 0) {
            val y = base - room * goal / top
            canvas.drawLine(left, y, right, y, goalPen)
            // On a chip of the card's own colour, so tall bars behind it never hide the word
            val wide = label.measureText(goalSaid)
            val x = if (rtl) left else right - wide
            chip.set(x - gap / 2f, y - label.textSize - gap / 2f, x + wide + gap / 2f, y - gap / 4f)
            canvas.drawRoundRect(chip, gap, gap, chipPaint)
            label.textAlign = Paint.Align.LEFT
            canvas.drawText(goalSaid, x, y - gap / 2f, label)
        }
    }

    private companion object {
        const val GROW_MS = 900L
        const val WAVE = 6f
        const val EVERY_DAY = 7
    }
}
