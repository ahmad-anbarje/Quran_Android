package com.readqurantoday.quran

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.text.SpannableString
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// One colour of the Tajweed edition: the font's palette slots it is painted from, its swatch, and the rule it marks
private data class Rule(val slots: Set<Int>, val colour: Int, val name: Int)

// Slots read off the font's own words; names as Dar Al-Maarifa's Tajweed mushaf, the font's source, gives them
private val RULES = listOf(
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
    rows.addView((layoutInflater.inflate(R.layout.row_setting_note, rows, false) as TextView).apply {
        setText(R.string.tajweed_legend_intro)
    })
    for (rule in RULES) rows.addView(ruleRow(rows, rule))
    raise(dialog, view)
}

/**
 * A card of one word as the Tajweed edition prints it, large, with only the rules its colours mark.
 * While a recitation plays it is handed each word the reciter reaches; [closed] hears it go.
 */
class TajweedCard(private val host: Activity, private val closed: () -> Unit) {

    private val dialog = Dialog(host, R.style.SheetDialog)
    private val view = host.layoutInflater.inflate(R.layout.part_sheet, null)
    private val rows = view.findViewById<LinearLayout>(R.id.sheet_rows)
    private var shown: IntArray? = null

    val open get() = dialog.isShowing

    init {
        view.findViewById<TextView>(R.id.sheet_title).setText(R.string.tajweed_word)
        dialog.setOnDismissListener { closed() }
    }

    /** Shows word [word] of [surah]:[ayah] on [page], raising the card the first time. */
    fun show(page: Int, surah: Int, ayah: Int, word: Int) {
        val now = intArrayOf(page, surah, ayah, word)
        if (now.contentEquals(shown)) return
        val glyphs = Mushaf.tajweed.glyphsOf(page, surah, ayah, word) ?: return
        val table = Mushaf.tajweed.glyphs(host, page) ?: return
        shown = now
        val ids = glyphs.codePoints().toArray().map { table.id(it) }.filter { it >= 0 }
        val used = TajweedFonts.slotsOf(host, page, ids)
        val rules = RULES.filter { rule -> rule.slots.any { it in used } }

        rows.removeAllViews()
        rows.addView(TextView(host).apply {
            gravity = Gravity.CENTER
            setTextColor(Settings.inkColor(host))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_heading))
            val pad = (WORD_PAD * resources.displayMetrics.density).toInt()
            setPadding(0, pad, 0, pad)
            text = SpannableString(OBJECT).apply {
                setSpan(QuranWordSpan(host, EndPages.Word(page, glyphs), WORD_SCALE, Mushaf.tajweed), 0, 1, 0)
            }
        })
        if (rules.isEmpty()) rows.addView((host.layoutInflater.inflate(R.layout.row_setting_note, rows, false) as TextView).apply {
            setText(R.string.tajweed_word_plain)
        })
        for (rule in rules) rows.addView(host.ruleRow(rows, rule))
        if (!dialog.isShowing) host.raise(dialog, view)
    }

    fun close() = dialog.dismiss()
}

private fun Activity.ruleRow(rows: LinearLayout, rule: Rule) = layoutInflater.inflate(R.layout.item_tajweed_rule, rows, false).apply {
    findViewById<ImageView>(R.id.rule_swatch).imageTintList = ColorStateList.valueOf(getColor(rule.colour))
    findViewById<TextView>(R.id.rule_name).setText(rule.name)
}

// The object character a word's span stands on; the word drawn three times the sheet's text, with room above and below
private const val OBJECT = "￼"
private const val WORD_SCALE = 3f
private const val WORD_PAD = 12f
