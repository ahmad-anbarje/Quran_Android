package com.readqurantoday.quran

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils

// A small label over one word of the page: how the lit word sounds, and under a rule, what it means
class WordTip(context: Context) {

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        // The system face: it carries the dotted and macroned Latin letters, and the scripts of the meanings
        typeface = Typeface.DEFAULT
        textSize = context.resources.getDimension(R.dimen.text_body)
    }
    private val mean = Paint(text).apply { textSize = context.resources.getDimension(R.dimen.text_meta) }
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    /** Over the word bounded by [left]..[right], [top]..[bottom]; below it when there is no room above. */
    fun draw(
        canvas: Canvas, said: String?, means: String?,
        left: Float, top: Float, right: Float, bottom: Float,
        minX: Float, maxX: Float, minY: Float,
        ink: Int, paper: Int
    ) {
        if (said == null && means == null) return
        val padX = text.textSize * 0.55f
        val padY = text.textSize * 0.32f
        val saidTall = if (said != null) text.textSize else 0f
        val meansTall = if (means != null) mean.textSize * LINE else 0f
        val between = if (said != null && means != null) padY * 2 else 0f
        val w = maxOf(said?.let { text.measureText(it) } ?: 0f, means?.let { mean.measureText(it) } ?: 0f) + 2 * padX
        val h = saidTall + between + meansTall + 2 * padY
        // The word's box stops short of its marks above and below, so the label clears them too
        val gap = text.textSize * 0.25f + (bottom - top) * MARK_CLEARANCE

        val cx = ((left + right) / 2f).coerceIn(minX + w / 2f, maxOf(minX + w / 2f, maxX - w / 2f))
        val above = top - gap - h >= minY
        val y0 = if (above) top - gap - h else bottom + gap
        box.set(cx - w / 2f, y0, cx + w / 2f, y0 + h)

        ground.color = ink or 0xFF000000.toInt()
        // Rounder as a pill on one line; a card's corners on two
        val round = if (between > 0f) text.textSize * 0.6f else h / 2f
        canvas.drawRoundRect(box, round, round, ground)
        var y = y0 + padY
        if (said != null) {
            text.color = paper
            canvas.drawText(said, cx, y + saidTall / 2f - (text.ascent() + text.descent()) / 2f, text)
            y += saidTall
        }
        if (between > 0f) {
            rule.color = ColorUtils.setAlphaComponent(paper, RULE_ALPHA)
            canvas.drawRect(box.left + padX, y + between / 2f - 0.5f, box.right - padX, y + between / 2f + 0.5f, rule)
            y += between
        }
        if (means != null) {
            mean.color = ColorUtils.setAlphaComponent(paper, MEANS_ALPHA)
            canvas.drawText(means, cx, y + meansTall / 2f - (mean.ascent() + mean.descent()) / 2f, mean)
        }
    }

    private companion object {
        const val MARK_CLEARANCE = 0.25f
        // The meaning's line height over its size, its ink's alpha against the label, and the rule's
        const val LINE = 1.25f
        const val MEANS_ALPHA = 0xE0
        const val RULE_ALPHA = 0x55
    }
}
