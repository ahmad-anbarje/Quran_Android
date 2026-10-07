package com.readqurantoday.quran

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources

/** What is memorised: every surah with a tick for all of it, and a tap for its pages one by one. */
class MemorizedActivity : CardsActivity(R.string.hifz_mine) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Surahs.load(this)
        build()
        sayBars()
    }

    // The tick on a surah's button once it is all known, in the button's own text colour
    private fun tick() = AppCompatResources.getDrawable(this, R.drawable.ic_check)?.mutate()?.apply {
        val size = resources.getDimensionPixelSize(R.dimen.mark_button_icon)
        setBounds(0, 0, size, size)
        setTint(getColor(R.color.on_dark))
    }

    private lateinit var summary: View
    private lateinit var rows: List<View>

    private fun build() {
        summary = layoutInflater.inflate(R.layout.row_progress, cards, false)
        layoutInflater.card(cards, 0, listOf(summary, quiet(getString(R.string.hifz_how))))
        rows = Surahs.list().map { surah ->
            layoutInflater.inflate(R.layout.row_place, cards, false).apply {
                (findViewById<View>(R.id.place_icon).parent as View).visibility = View.GONE
                fillSurahTitle(findViewById(R.id.place_title), surah.id, R.dimen.surah_title_row)
            }
        }
        layoutInflater.card(cards, 0, rows)
        refresh()
    }

    // Filled again in place after each change, so the list keeps where it was scrolled to
    private fun refresh() {
        val kept = Hifz.pages(this)
        val known = kept.size
        summary.findViewById<TextView>(R.id.prog_label).text = getString(R.string.hifz_total)
        summary.findViewById<TextView>(R.id.prog_value).text =
            if (known == 0) none(resources) else getString(R.string.percent, figures(known * 100 / Mushaf.PAGES, resources))
        summary.findViewById<TextView>(R.id.prog_note).text =
            if (known == 0) getString(R.string.hifz_mine_empty) else getString(R.string.hifz_mine_pages, pagesSaid(known, resources))
        Surahs.list().forEachIndexed { i, surah -> fillRow(rows[i], surah, kept) }
    }

    private fun fillRow(row: View, surah: Surahs.Surah, kept: Map<Int, Long>) {
        row.apply {
            val all = surah.to - surah.from + 1
            val done = Hifz.knownOf(this@MemorizedActivity, surah, kept)
            val whole = done == all
            findViewById<TextView>(R.id.place_detail).text =
                if (done == 0) pagesSaid(all, resources)
                else getString(R.string.of_count, figures(done, resources), pagesSaid(all, resources))
            findViewById<View>(R.id.place_track).apply {
                if (done > 0) fillTrack(done.toFloat(), all.toFloat()) else visibility = View.GONE
            }
            // The button is the whole surah, known from before; the row opens its pages
            findViewById<TextView>(R.id.place_mark).apply {
                visibility = View.VISIBLE
                setText(if (whole) R.string.hifz_surah_known else R.string.hifz_surah_mark)
                markChoice(this, whole)
                setCompoundDrawablesRelative(if (whole) tick() else null, null, null, null)
                contentDescription = getString(if (whole) R.string.hifz_surah_unmark_said else R.string.hifz_surah_mark_said, surah.name)
                setOnClickListener {
                    Hifz.setSurah(this@MemorizedActivity, surah, known = !whole)
                    refresh()
                }
            }
            setOnClickListener { pickPages(surah, kept) }
        }
    }

    // A page chosen here is learned today, and counts toward today's memorisation goal
    private fun pickPages(surah: Surahs.Surah, kept: Map<Int, Long>) {
        val pages = (surah.from..surah.to).toList()
        val was = pages.filter { it in kept }.toSet()
        pickPages(getString(R.string.surah_named, surah.name), getString(R.string.hifz_pages_ask), pages, was) { now ->
            Hifz.setPages(this, marked = now - was, unmarked = was - now)
            refresh()
        }
    }

    private fun quiet(text: String): View =
        (layoutInflater.inflate(R.layout.row_setting_note, cards, false) as TextView).also { it.text = text }
}
