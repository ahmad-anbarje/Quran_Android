package com.readqurantoday.quran

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan

/** One printed word of the mushaf inside running text, drawn from its page's own font in [book], [scale] times the text's size. */
class QuranWordSpan(
    private val context: Context,
    private val word: EndPages.Word,
    private val scale: Float,
    private val book: Book = Mushaf.plain
) : ReplacementSpan() {

    private val ids = IntArray(word.glyphs.length)
    private val spots = FloatArray(word.glyphs.length * 2)

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val size = paint.textSize * scale
        // Room above and below for the mushaf's marks, which reach well past the text's own
        fm?.apply {
            ascent = -(size * RISE).toInt()
            top = ascent
            descent = (size * FALL).toInt()
            bottom = descent
        }
        return wide(size).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val glyphs = book.glyphs(context, word.page) ?: return
        val size = paint.textSize * scale
        val step = size / glyphs.upem
        if (medal(canvas, glyphs, size, x, y, paint)) return
        // Right to left from the span's far edge, each glyph placed as the page places it
        var pen = x + wide(size)
        var n = 0
        val codes = word.glyphs.codePoints().toArray()
        for (cp in codes) {
            if (glyphs.id(cp) < 0) {
                pen -= INNER * size
                continue
            }
            val advance = glyphs.advance(cp) * step
            ids[n] = glyphs.id(cp)
            spots[n * 2] = pen - advance
            spots[n * 2 + 1] = y.toFloat()
            n++
            pen -= advance
        }
        val face = book.font(context, word.page)
        val saved = paint.textSize
        paint.textSize = size
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && face != null) {
            canvas.drawGlyphs(ids, 0, spots, 0, n, face, paint)
        } else {
            val typeface = paint.typeface
            paint.typeface = book.face(context, word.page)
            var i = 0
            for (cp in codes) if (glyphs.id(cp) >= 0) {
                canvas.drawText(String(Character.toChars(cp)), spots[i * 2], y.toFloat(), paint)
                i++
            }
            paint.typeface = typeface
        }
        paint.textSize = saved
    }

    // An ayah's number as its page draws it, a medallion centred in the number's room; false where it stays plain
    private fun medal(canvas: Canvas, glyphs: Book.Glyphs, size: Float, x: Float, y: Int, paint: Paint): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || book !== Mushaf.plain || word.glyphs.length != 1) return false
        val cp = word.glyphs.codePointAt(0)
        val face = Mushaf.medalFont(context, word.page) ?: return false
        val medal = Mushaf.medal(context, word.page, cp) ?: return false
        ids[0] = medal.id
        spots[0] = x + (glyphs.advance(cp) * size / glyphs.upem - medal.advance * size / medal.upem) / 2f
        spots[1] = y.toFloat()
        val saved = paint.textSize
        paint.textSize = size
        canvas.drawGlyphs(ids, 0, spots, 0, 1, face, paint)
        paint.textSize = saved
        return true
    }

    private fun wide(size: Float): Float {
        val glyphs = book.glyphs(context, word.page) ?: return 0f
        var sum = 0f
        for (cp in word.glyphs.codePoints()) {
            sum += if (glyphs.id(cp) < 0) INNER * size else glyphs.advance(cp) * size / glyphs.upem
        }
        return sum
    }

    private companion object {
        // The mushaf's reach above and below the line, and the gap inside a word printed in two parts, in ems
        const val RISE = 1.25f
        const val FALL = 0.75f
        const val INNER = 0.04f
    }
}

/** Appends [word] as the mushaf prints it, after a space, or a no-break one before an ayah's number so it never wraps from its last word. */
fun SpannableStringBuilder.appendQuranWord(context: Context, word: EndPages.Word, scale: Float, book: Book = Mushaf.plain) {
    if (isNotEmpty() && last() != '(') append(if (word.glyphs in book.marksOn(word.page)) NO_BREAK else " ")
    val at = length
    append(OBJECT)
    // The screen's own context, which knows the reader's day or night, so a Tajweed word takes that palette
    setSpan(QuranWordSpan(context, word, scale, book), at, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

// The object character a word's span stands on, and the space that keeps an ayah's number on its last word's line
private const val OBJECT = "￼"
private const val NO_BREAK = " "
