package com.readqurantoday.quran

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils

/**
 * The page's frame, after the printed mushaf's: a tinted band between two rules across its head and its foot,
 * a chain of almonds along each, and cartouches for its labels.
 */
class PageFrame {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val box = RectF()
    private val strip = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val shape = Path()

    /**
     * The band round [left], [top], [right], [bottom]: [head] deep at the top where the page's labels sit, [foot] at the
     * bottom where its number sits, and [side] at the sides, its tint running [bleed] further up, off the page's top
     * as the screen's edge. One rule, [edge] thick, edges the text; all in [ink], the head's and foot's almonds,
     * [almond] across, filled with [paper]. Each chain of almonds starts either side of its middle box, [headGap] or
     * [footGap] from the centre, so both sides mirror; the head's runs along [headMid] no further than [headFrom] and
     * [headTo], under its corner labels.
     */
    fun frame(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, bleed: Float, side: Float, head: Float,
              foot: Float, almond: Float, edge: Float, ink: Int, paper: Int, headGap: Float = 0f, footGap: Float = 0f,
              headFrom: Float = left + side, headTo: Float = right - side, headMid: Float = top + head / 2f) {
        // Only the band is tinted, so what is drawn inside stays as it is
        strip.reset()
        strip.addRect(left, top - bleed, right, bottom, Path.Direction.CW)
        strip.addRect(left + side, top + head, right - side, bottom - foot, Path.Direction.CW)
        fill.color = ColorUtils.setAlphaComponent(ink, TINT)
        canvas.drawPath(strip, fill)

        val centre = (left + right) / 2f
        chain(canvas, headMid, centre, headGap, headFrom, headTo, almond, edge, ink, paper)
        chain(canvas, bottom - foot / 2f, centre, footGap, left + side, right - side, almond, edge, ink, paper)

        // The screen's edge is the band's outer edge, so the one rule is at the text
        line.color = ink
        line.strokeWidth = edge * RULE
        box.set(left + side, top + head, right - side, bottom - foot)
        canvas.drawRect(box, line)
    }

    // Almonds along [y], [gap] either side of [centre] outward, whole ones only within [from] and [to], a dot between each two
    private fun chain(canvas: Canvas, y: Float, centre: Float, gap: Float, from: Float, to: Float, band: Float, edge: Float,
                      ink: Int, paper: Int) {
        val step = band * STEP
        val a = step * ALMOND_LONG / 2f
        val b = band * ALMOND_WIDE / 2f
        for (way in intArrayOf(1, -1)) {
            var i = 0
            while (true) {
                val start = centre + way * (gap + i * step)
                val c = start + way * step / 2f
                val end = c + way * a
                if (end > to || end < from) break
                shape.reset()
                shape.moveTo(c - a, y)
                shape.quadTo(c, y - b * 2f, c + a, y)
                shape.quadTo(c, y + b * 2f, c - a, y)
                shape.close()
                fill.color = paper
                canvas.drawPath(shape, fill)
                line.color = ink
                line.strokeWidth = edge / 2f
                canvas.drawPath(shape, line)
                // A dot where two almonds meet
                if (i > 0) {
                    fill.color = ink
                    canvas.drawCircle(start, y, band * DOT, fill)
                }
                i++
            }
        }
    }

    /** A cartouche for a label [half] either side of [centre], [tall] high about [mid], filled with [paper] and edged in [ink]. */
    fun label(canvas: Canvas, centre: Float, mid: Float, half: Float, tall: Float, edge: Float, ink: Int, paper: Int) {
        shape.cartouche(centre, mid, half, tall, box)
        fill.color = paper
        canvas.drawPath(shape, fill)
        line.color = ink
        line.strokeWidth = edge
        canvas.drawPath(shape, line)
    }

    companion object {
        /** The side band's width in dp: thin, so a letter reaching past the measure stays clear of it. */
        const val SIDE = 4f

        // The band's tint out of 255, and its rules' weight as a share of the cartouches' edge
        private const val TINT = 70
        private const val RULE = 0.75f
        // Shares of the almonds' size: an almond's length (of its step), its width, the gap between almonds' centres,
        // and the dots between them
        private const val ALMOND_LONG = 0.82f
        private const val ALMOND_WIDE = 0.5f
        private const val STEP = 1.9f
        private const val DOT = 0.07f
    }
}
