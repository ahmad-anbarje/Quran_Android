package com.readqurantoday.quran

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.text.SpannableString
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
    for (rule in RULES) rows.addView(ruleRow(rows, rule))
    raise(dialog, view)
}

/**
 * A card of one word as the Tajweed edition prints it, large, with only the rules its colours mark.
 * While a recitation plays it is handed each word the reciter reaches; [closed] hears it go.
 */
class TajweedCard(
    private val host: Activity,
    private val playOrPause: (surah: Int, ayah: Int, word: Int) -> Unit,
    private val speedChanged: () -> Unit,
    private val closed: () -> Unit
) {

    private val dialog = Dialog(host, R.style.SheetDialog)
    private val view = host.layoutInflater.inflate(R.layout.part_sheet, null)
    private val rows = view.findViewById<LinearLayout>(R.id.sheet_rows)
    private var shown: IntArray? = null

    val open get() = dialog.isShowing

    init {
        // The word speaks for itself: no title above it
        view.findViewById<TextView>(R.id.sheet_title).visibility = View.GONE
        dialog.setOnDismissListener {
            if (WordVoice.saying) WordVoice.stop()
            closed()
        }
    }

    /** Shows word [word] of [surah]:[ayah] on [page], raising the card the first time; [again] redraws it as it is. */
    fun show(page: Int, surah: Int, ayah: Int, word: Int, again: Boolean = false) {
        val now = intArrayOf(page, surah, ayah, word)
        if (!again && now.contentEquals(shown)) return
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
        // Recite from the word, hear it alone, and while a recitation is under way, sounding or paused, its speed;
        // so pausing and playing never move the buttons
        rows.addView(LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            addView(recite(this, page, surah, ayah, word))
            addView(listen(surah, ayah, word))
            if (Recite.playing != 0) addView(speed(this))
        })
        if (rules.isEmpty()) rows.addView((host.layoutInflater.inflate(R.layout.row_setting_note, rows, false) as TextView).apply {
            setText(R.string.tajweed_word_plain)
        })
        for (rule in rules) rows.addView(host.ruleRow(rows, rule))
        if (!dialog.isShowing) host.raise(dialog, view)
    }

    fun close() = dialog.dismiss()

    // Recites on from this word, or pauses the recitation; then the card shows its buttons as they now stand
    private fun recite(row: LinearLayout, page: Int, surah: Int, ayah: Int, word: Int): View =
        host.layoutInflater.inflate(R.layout.part_word_listen, row, false).apply {
            val sounding = Recite.wantsToPlay()
            contentDescription = host.getString(if (sounding) R.string.stop else R.string.play)
            findViewById<ImageView>(R.id.word_listen_icon).apply {
                setImageResource(if (sounding) R.drawable.ic_pause else R.drawable.ic_play)
                imageTintList = ColorStateList.valueOf(host.getColor(R.color.accent))
            }
            (layoutParams as LinearLayout.LayoutParams).marginEnd = (BUTTON_GAP * resources.displayMetrics.density).toInt()
            setOnClickListener {
                playOrPause(surah, ayah, word)
                show(page, surah, ayah, word, again = true)
            }
        }

    // Steps the recitation's speed, as the player's speed button does
    private fun speed(row: LinearLayout): View = host.layoutInflater.inflate(R.layout.part_speed_chip, row, false).apply {
        findViewById<ImageView>(R.id.speed_chip_icon).imageTintList = ColorStateList.valueOf(host.getColor(R.color.accent))
        val label = findViewById<TextView>(R.id.speed_chip_label)
        label.setText(speedName(host))
        setOnClickListener {
            stepSpeed(host)
            label.setText(speedName(host))
            speedChanged()
        }
    }

    // Hears the word on its own; while a recitation plays it is paused, as the player's Word button does
    private fun listen(surah: Int, ayah: Int, word: Int): View = host.layoutInflater.inflate(R.layout.part_word_listen, rows, false).apply {
        val icon = findViewById<ImageView>(R.id.word_listen_icon)
        val accent = ColorStateList.valueOf(host.getColor(R.color.accent))
        icon.imageTintList = accent
        WordVoice.warm(host, surah, ayah, word)
        // Faded while the word is fetched
        fun say() {
            icon.imageAlpha = if (WordVoice.loading) LOADING_ALPHA else 0xFF
        }
        setOnClickListener {
            if (WordVoice.loading) return@setOnClickListener
            if (WordVoice.saying) { WordVoice.stop(); return@setOnClickListener }
            if (Recite.wantsToPlay()) Recite.toggle()
            host.warnIfMuted()
            WordVoice.say(host, surah, ayah, word, changed = ::say) { how ->
                say()
                when (how) {
                    WordVoice.End.MISSING -> host.notice(host.getString(R.string.word_missing))
                    WordVoice.End.UNREACHABLE -> host.notice(host.getString(R.string.word_unheard))
                    WordVoice.End.HEARD -> {}
                }
            }
        }
    }
}

private fun Activity.ruleRow(rows: LinearLayout, rule: Rule) = layoutInflater.inflate(R.layout.item_tajweed_rule, rows, false).apply {
    findViewById<ImageView>(R.id.rule_swatch).imageTintList = ColorStateList.valueOf(getColor(rule.colour))
    findViewById<TextView>(R.id.rule_name).setText(rule.name)
}

// The object character a word's span stands on; the word drawn three times the sheet's text, with room above and below
private const val OBJECT = "￼"
private const val WORD_SCALE = 3f
private const val WORD_PAD = 12f
private const val LOADING_ALPHA = 0x66
private const val BUTTON_GAP = 10f
