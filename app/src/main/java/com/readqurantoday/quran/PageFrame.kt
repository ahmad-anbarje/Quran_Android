package com.readqurantoday.quran

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import kotlin.math.floor

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
     * A band across the page's head, [head] deep from [top], where its labels sit, and one across its foot, [foot] deep
     * up from [bottom], where its number sits, both from [left] to [right]; the sides are left open, so a letter that
     * reaches past the measure never meets a rule. Its rules are [edge] thick, tinted with [ink]; its almonds, [almond]
     * across, are filled with [paper].
     */
    fun frame(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, head: Float, foot: Float, almond: Float,
              edge: Float, ink: Int, paper: Int) {
        // Only the bands are tinted, so what is drawn between them stays as it is
        strip.reset()
        strip.addRect(left, top, right, top + head, Path.Direction.CW)
        strip.addRect(left, bottom - foot, right, bottom, Path.Direction.CW)
        fill.color = ColorUtils.setAlphaComponent(ink, TINT)
        canvas.drawPath(strip, fill)

        chain(canvas, left, top + head / 2f, right, top + head / 2f, almond, edge, ink, paper)
        chain(canvas, left, bottom - foot / 2f, right, bottom - foot / 2f, almond, edge, ink, paper)

        // Each band's rules: a bold one at the page's edge, a fine one at the text
        line.color = ink
        line.strokeWidth = edge
        canvas.drawLine(left, top + edge / 2f, right, top + edge / 2f, line)
        canvas.drawLine(left, bottom - edge / 2f, right, bottom - edge / 2f, line)
        line.strokeWidth = edge / 2f
        canvas.drawLine(left, top + head, right, top + head, line)
        canvas.drawLine(left, bottom - foot, right, bottom - foot, line)
    }

    // Almonds end to end from (x0, y0) to (x1, y1), along the middle of a band [band] wide, a dot between each two
    private fun chain(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, band: Float, edge: Float, ink: Int, paper: Int) {
        val length = maxOf(x1 - x0, y1 - y0)
        val step = band * STEP
        val count = floor(length / step).toInt()
        if (count < 1) return
        // Spread to fill the side exactly, so both ends meet the corners alike
        val each = length / count
        val along = x1 - x0 > y1 - y0
        val a = each * ALMOND_LONG / 2f
        val b = band * ALMOND_WIDE / 2f
        for (i in 0 until count) {
            val c = (i + 0.5f) * each
            val cx = if (along) x0 + c else x0
            val cy = if (along) y0 else y0 + c
            shape.reset()
            if (along) {
                shape.moveTo(cx - a, cy)
                shape.quadTo(cx, cy - b * 2f, cx + a, cy)
                shape.quadTo(cx, cy + b * 2f, cx - a, cy)
            } else {
                shape.moveTo(cx, cy - a)
                shape.quadTo(cx + b * 2f, cy, cx, cy + a)
                shape.quadTo(cx - b * 2f, cy, cx, cy - a)
            }
            shape.close()
            fill.color = paper
            canvas.drawPath(shape, fill)
            line.color = ink
            line.strokeWidth = edge / 2f
            canvas.drawPath(shape, line)
            // A dot where two almonds meet
            if (i > 0) {
                fill.color = ink
                val d = i * each
                canvas.drawCircle(if (along) x0 + d else x0, if (along) y0 else y0 + d, band * DOT, fill)
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

    private companion object {
        // The band's tint out of 255
        const val TINT = 70
        // Shares of the almonds' size: an almond's length (of its step), its width, the gap between almonds' centres,
        // and the dots between them
        const val ALMOND_LONG = 0.82f
        const val ALMOND_WIDE = 0.5f
        const val STEP = 1.9f
        const val DOT = 0.07f
    }
}
