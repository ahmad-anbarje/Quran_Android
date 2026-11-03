package com.readqurantoday.quran

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import androidx.core.graphics.withTranslation

/**
 * One paragraph of a closing page, wrapped to the page's width: its lines spread to the full width with the last
 * set from the right, or centred as a title. Its size is set by its page, so the page's text fits it whole.
 */
@SuppressLint("ViewConstructor")
class EndBlock(context: Context, private val text: CharSequence, private val centred: Boolean, paint: TextPaint) : View(context) {

    // Its own, as each layout keeps the paint it was made with
    private val paint = TextPaint(paint)

    /** The type size, set by the page so its paragraphs share one. */
    var size = 0f
        set(value) {
            if (field == value) return
            field = value
            layout = null
            requestLayout()
        }

    private var layout: StaticLayout? = null

    /** How tall the paragraph stands [wide] across at [size], its marks' room included. */
    fun tallAt(wide: Int, size: Float): Int = build(wide, size).height + (2 * size * PAD).toInt()

    private fun build(wide: Int, size: Float): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, wide.coerceAtLeast(1))
            .setAlignment(if (centred) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(size * SPACING, 1f)
            .setIncludePad(false)
            .apply {
                if (!centred && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
            }
            .build()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val wide = MeasureSpec.getSize(widthSpec)
        val laid = layout?.takeIf { it.width == wide } ?: build(wide, size).also { layout = it }
        setMeasuredDimension(wide, laid.height + (2 * size * PAD).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val laid = layout ?: return
        canvas.withTranslation(0f, size * PAD) { laid.draw(this) }
    }

    private companion object {
        // In ems of the paragraph's size: the room added between lines, not scaled, so a line a Quranic word makes tall
        // grows no further, and the room above and below for its marks
        const val SPACING = 0.45f
        const val PAD = 0.3f
    }
}
