package com.readqurantoday.quran

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView

// --- statistics rows (row_progress) that do something when tapped ---

/** A row that opens or changes something: its value in the accent, an arrow after it. */
fun View.opens(act: () -> Unit) {
    mark(R.drawable.ic_chevron, R.dimen.row_go, R.dimen.row_go)
    findViewById<TextView>(R.id.prog_value).setTextColor(context.getColor(R.color.accent))
    isClickable = true
    setOnClickListener { act() }
}

/** A row that is on or off: a switch at its end, filled when on; the whole row flips it. */
fun View.toggles(on: Boolean, flip: () -> Unit) {
    mark(if (on) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off, R.dimen.row_toggle_w, R.dimen.row_toggle_h)
    findViewById<ImageView>(R.id.prog_go).imageTintList =
        ColorStateList.valueOf(context.getColor(if (on) R.color.accent else R.color.text_mute))
    isClickable = true
    isSelected = on
    setOnClickListener { flip() }
}

private fun View.mark(icon: Int, width: Int, height: Int) {
    findViewById<ImageView>(R.id.prog_go).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(context.getColor(R.color.accent))
        layoutParams = layoutParams.apply {
            this.width = resources.getDimensionPixelSize(width)
            this.height = resources.getDimensionPixelSize(height)
        }
        visibility = View.VISIBLE
    }
}
