package com.readqurantoday.quran

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// One colour of the Tajweed edition: the font's palette slots it is painted from, its swatch, and the rule it marks
internal data class Rule(val slots: Set<Int>, val colour: Int, val name: Int)

// Slots read off the font's own words; names as Dar Al-Maarifa's Tajweed mushaf, the font's source, gives them
internal val RULES = listOf(
    Rule(setOf(3), R.color.tajweed_necessary, R.string.tajweed_necessary),
    Rule(setOf(9), R.color.tajweed_obligatory, R.string.tajweed_obligatory),
    Rule(setOf(4), R.color.tajweed_permissible, R.string.tajweed_permissible),
    Rule(setOf(5), R.color.tajweed_natural, R.string.tajweed_natural),
    Rule(setOf(6), R.color.tajweed_ghunnah, R.string.tajweed_ghunnah),
    Rule(setOf(8), R.color.tajweed_qalqalah, R.string.tajweed_qalqalah),
    Rule(setOf(7), R.color.tajweed_heavy, R.string.tajweed_heavy),
    Rule(setOf(1, 2, 15), R.color.tajweed_silent, R.string.tajweed_silent),
)

/** What the Tajweed edition's colours mean, on a sheet; letters left in the page's ink are read as ever. */
fun Activity.showTajweedLegend() {
    val dialog = Dialog(this, R.style.SheetDialog)
    val view = layoutInflater.inflate(R.layout.part_sheet, null)
    view.findViewById<TextView>(R.id.sheet_title).setText(R.string.tajweed_legend)
    val rows = view.findViewById<LinearLayout>(R.id.sheet_rows)
    for (rule in RULES) rows.addView(ruleRow(rows, rule))
    raise(dialog, view)
}

internal fun Activity.ruleRow(rows: LinearLayout, rule: Rule) = layoutInflater.inflate(R.layout.item_tajweed_rule, rows, false).apply {
    findViewById<ImageView>(R.id.rule_swatch).imageTintList = ColorStateList.valueOf(getColor(rule.colour))
    findViewById<TextView>(R.id.rule_name).setText(rule.name)
}
