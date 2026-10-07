package com.readqurantoday.quran

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/** A statistics row (row_progress) that opens or changes something: an arrow in the accent after its value. */
fun View.opens(act: () -> Unit) {
    findViewById<ImageView>(R.id.prog_go).apply {
        setImageResource(R.drawable.ic_chevron)
        imageTintList = ColorStateList.valueOf(context.getColor(R.color.accent))
        visibility = View.VISIBLE
    }
    isClickable = true
    setOnClickListener { act() }
}

/** A row with a real switch: slide it or tap anywhere on the row; [changed] hears where it ends up. */
fun LayoutInflater.switchRow(parent: ViewGroup, label: String, on: Boolean, changed: (Boolean) -> Unit): View =
    inflate(R.layout.row_switch, parent, false).apply {
        findViewById<TextView>(R.id.switch_label).text = label
        val switch = findViewById<SwitchCompat>(R.id.switch_on)
        switch.isChecked = on
        switch.contentDescription = label
        switch.setOnCheckedChangeListener { _, now -> changed(now) }
        setOnClickListener { switch.toggle() }
    }
