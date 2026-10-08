package com.readqurantoday.quran

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat

/**
 * A week of reading as bars, today at the reading end. Each day stands against its own goal, since a day such as
 * Friday may ask for more: a soft track rises to the goal behind the bar, the pages read are written over it,
 * and the day's name sits at the foot with its goal beneath.
 */
class DayBars(context: Context) : View(context) {

    private var pages: List<Int> = emptyList()
    private var goals: List<Int> = emptyList()
    private var dayName: (Int) -> String = { "" }
    private var goalSaid: (Int) -> String = { "" }

    // 0 to 1 across the whole rise; each bar takes its own slice of it, oldest first
    private var grown = 1f

    private val d = resources.displayMetrics.density
    private val face = ResourcesCompat.getFont(context, R.font.cairo)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.accent) }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.accent_soft) }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.line) }
    private val count = text(R.color.text, bold = true)
    private val label = text(R.color.text_mute, bold = false)
    private val todayLabel = text(R.color.text, bold = true)
    private val gap = resources.getDimension(R.dimen.chart_gap)
    private val barWide = resources.getDimension(R.dimen.chart_bar)

    private fun text(colour: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(colour)
        textSize = resources.getDimension(R.dimen.chart_label)
        typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    /**
     * [pages] and [goals] oldest first, one a day; [dayName] names a day by how many days ago it was, and [goalSaid]
     * writes a day's goal. [grow] raises the bars from the baseline one after another; otherwise they simply stand.
     */
    fun show(pages: List<Int>, goals: List<Int>, dayName: (Int) -> String, goalSaid: (Int) -> String, grow: Boolean = false) {
        this.pages = pages
        this.goals = goals
        this.dayName = dayName
        this.goalSaid = goalSaid
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

    // Room for the pages over the tallest bar, and the day's name and goal beneath the baseline
    private fun line() = label.textSize + gap

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val tall = resources.getDimensionPixelSize(R.dimen.chart_height) + paddingTop + paddingBottom + (3 * line()).toInt()
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthSpec), resolveSize(tall, heightSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (pages.isEmpty()) return
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val top = maxOf(pages.max(), goals.maxOrNull() ?: 0, 1)
        val left = paddingLeft.toFloat()
        val right = width - paddingRight.toFloat()
        val roof = paddingTop + line()
        val base = height - paddingBottom - 2 * line()
        val room = base - roof

        val slot = (right - left) / pages.size
        // Slim bars with round ends, the same width however wide the screen
        val wide = minOf(barWide, slot * 0.6f)
        val round = wide / 2f
        pages.forEachIndexed { i, n ->
            // Oldest first, so the newest day sits where the line of reading ends
            val centre = if (rtl) right - (i + 0.5f) * slot else left + (i + 0.5f) * slot
            val l = centre - wide / 2f
            val r = centre + wide / 2f
            val goal = goals.getOrElse(i) { 0 }
            val goalTop = base - room * goal / top
            if (goal > 0) canvas.drawRoundRect(l, goalTop, r, base, round, round, track)
            // A day with nothing read keeps a dot of a bar, so the run of days still reads as a week
            val tall = maxOf(if (n > 0) room * n / top else 0f, wide) * rise(i)
            canvas.drawRoundRect(l, base - tall, r, base, round, round, if (n > 0) bar else empty)
            if (n > 0 && rise(i) == 1f) canvas.drawText(figures(n, resources), centre, minOf(base - tall, goalTop) - gap, count)
        }

        // Every day's name and goal fit under its own bar; on a narrow screen they shrink alike
        val last = pages.size - 1
        val names = pages.indices.map { dayName(last - it) }
        val said = goals.map { if (it > 0) goalSaid(it) else "" }
        val widest = (names + said).maxOf { todayLabel.measureText(it) }
        val full = label.textSize
        val fit = if (widest > slot - gap / 2f) full * (slot - gap / 2f) / widest else full
        label.textSize = fit
        todayLabel.textSize = fit
        val nameY = base + gap + fit * 0.9f
        pages.indices.forEach { i ->
            val centre = if (rtl) right - (i + 0.5f) * slot else left + (i + 0.5f) * slot
            canvas.drawText(names[i], centre, nameY, if (i == last) todayLabel else label)
            if (said[i].isNotEmpty()) canvas.drawText(said[i], centre, nameY + fit + gap / 2f, label)
        }
        label.textSize = full
        todayLabel.textSize = full
    }

    private companion object {
        const val GROW_MS = 900L
        const val WAVE = 3f
    }
}
