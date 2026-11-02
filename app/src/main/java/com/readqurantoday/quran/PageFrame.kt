package com.readqurantoday.quran

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import kotlin.math.floor

/**
 * The page's frame, after the printed mushaf's: a tinted band between two rules running round the page,
 * a chain of almonds along it and a rosette at each corner, and cartouches for its labels.
 */
class PageFrame {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val box = RectF()
    private val strip = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val shape = Path()

    /**
     * The band round [left], [top], [right], [bottom], [band] wide inward from those edges and [foot] at the bottom,
     * where the page's number sits; its rules [edge] thick, tinted with [ink], its almonds filled with [paper].
     */
    fun frame(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, band: Float, foot: Float, edge: Float, ink: Int, paper: Int) {
        // Only the band is tinted, so what is drawn inside stays as it is
        strip.reset()
        strip.addRect(left, top, right, bottom, Path.Direction.CW)
        strip.addRect(left + band, top + band, right - band, bottom - foot, Path.Direction.CW)
        fill.color = ColorUtils.setAlphaComponent(ink, TINT)
        canvas.drawPath(strip, fill)

        // The almonds along each side, end to end between the corners, and a rosette in each corner
        val half = band / 2f
        chain(canvas, left + band, top + half, right - band, top + half, band, edge, ink, paper)
        chain(canvas, left + band, bottom - foot / 2f, right - band, bottom - foot / 2f, band, edge, ink, paper)
        chain(canvas, left + half, top + band, left + half, bottom - foot, band, edge, ink, paper)
        chain(canvas, right - half, top + band, right - half, bottom - foot, band, edge, ink, paper)
        for (x in floatArrayOf(left + half, right - half)) for (y in floatArrayOf(top + half, bottom - foot / 2f)) {
            rosette(canvas, x, y, half * ROSETTE, edge, ink, paper)
        }

        // Its rules: a bold one outside, a fine one inside
        line.color = ink
        line.strokeWidth = edge
        box.set(left + edge / 2f, top + edge / 2f, right - edge / 2f, bottom - edge / 2f)
        canvas.drawRect(box, line)
        line.strokeWidth = edge / 2f
        box.set(left + band, top + band, right - band, bottom - foot)
        canvas.drawRect(box, line)
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

    // Two squares, one turned an eighth, about ([x], [y]): the corner's star
    private fun rosette(canvas: Canvas, x: Float, y: Float, r: Float, edge: Float, ink: Int, paper: Int) {
        val d = r * 1.4142f
        shape.reset()
        shape.addRect(x - r, y - r, x + r, y + r, Path.Direction.CW)
        shape.moveTo(x, y - d)
        shape.lineTo(x + d, y)
        shape.lineTo(x, y + d)
        shape.lineTo(x - d, y)
        shape.close()
        fill.color = paper
        canvas.drawPath(shape, fill)
        line.color = ink
        line.strokeWidth = edge / 2f
        canvas.drawPath(shape, line)
        fill.color = ink
        canvas.drawCircle(x, y, r * 0.35f, fill)
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
        // Shares of the band's width: an almond's length (of its step), its width, the gap between almonds' centres,
        // the dots between them, and the corner rosette's half-size (of half the band)
        const val ALMOND_LONG = 0.82f
        const val ALMOND_WIDE = 0.5f
        const val STEP = 1.9f
        const val DOT = 0.07f
        const val ROSETTE = 0.62f
    }
}
