package com.readqurantoday.quran

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * A heading pinned over a long list, naming the part the top row belongs to; the next part's heading pushes it
 * up as it arrives. [part] gives a row's part, and [said] how the heading writes it. The list makes room for it
 * at the top, so the first row is never under it.
 */
fun RecyclerView.addPinnedHead(part: (Int) -> Int, said: (Int) -> String) {
    val head = PinnedHead(this, part, said)
    setPadding(paddingLeft, paddingTop + head.tall.toInt(), paddingRight, paddingBottom)
    addItemDecoration(head)
}

private class PinnedHead(
    private val list: RecyclerView,
    private val part: (Int) -> Int,
    private val said: (Int) -> String
) : RecyclerView.ItemDecoration() {

    private val res = list.resources
    val tall = res.getDimension(R.dimen.pinned_head_height)
    private val margin = res.getDimension(R.dimen.screen_margin)

    private val ground = Paint().apply { color = list.context.getColor(R.color.surface) }
    private val seam = Paint().apply { color = list.context.getColor(R.color.line) }
    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = list.context.getColor(R.color.text_mute)
        textSize = res.getDimension(R.dimen.row_meta)
        typeface = Typeface.create(ResourcesCompat.getFont(list.context, R.font.cairo), Typeface.BOLD)
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        // The heading stands over the rows; the row just under its foot is the one it names
        val foot = list.paddingTop
        var now = -1
        var push = 0f
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            val at = list.getChildAdapterPosition(child)
            if (at == RecyclerView.NO_POSITION) continue
            if (now < 0) {
                if (child.bottom > foot) now = part(at)
                continue
            }
            // The next part's first row, coming up under the heading, pushes it out ahead of it
            if (part(at) != now) {
                push = minOf(0f, child.top - foot - tall)
                break
            }
        }
        if (now < 0) return

        val head = foot - tall + push
        c.drawRect(0f, head, list.width.toFloat(), head + tall, ground)
        c.drawRect(0f, head + tall - SEAM_PX, list.width.toFloat(), head + tall, seam)
        val rtl = list.layoutDirection == View.LAYOUT_DIRECTION_RTL
        words.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        val x = if (rtl) list.width - margin else margin
        c.drawText(said(now), x, head + tall / 2f - (words.descent() + words.ascent()) / 2f, words)
    }

    private companion object {
        const val SEAM_PX = 1f
    }
}
