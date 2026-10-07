package com.readqurantoday.quran

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/** What is memorised: every surah with a tick for all of it, and a tap for its pages one by one. */
class MemorizedActivity : CardsActivity(R.string.hifz_mine) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Surahs.load(this)
        build()
        sayBars()
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
        summary.findViewById<TextView>(R.id.prog_label).text = getString(R.string.hifz_mine)
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
            // The tick is the whole surah, known from before; the row opens its pages
            findViewById<ImageView>(R.id.place_remove).apply {
                visibility = View.VISIBLE
                setImageResource(if (whole) R.drawable.ic_check else R.drawable.ic_circle)
                imageTintList = ColorStateList.valueOf(getColor(if (whole) R.color.accent else R.color.text_mute))
                contentDescription = getString(if (whole) R.string.hifz_surah_unmark else R.string.hifz_surah_mark)
                setOnClickListener {
                    Hifz.setSurah(this@MemorizedActivity, surah, known = !whole)
                    refresh()
                }
            }
            setOnClickListener { pickPages(surah, kept) }
        }
    }

    // A page ticked here is learned today, and counts toward today's memorisation goal
    private fun pickPages(surah: Surahs.Surah, kept: Map<Int, Long>) {
        val pages = (surah.from..surah.to).toList()
        val was = BooleanArray(pages.size) { pages[it] in kept }
        val labels = pages.map { getString(R.string.head_page, figures(it, resources)) }
        pickMany(surah.name, labels, was.copyOf(), getString(R.string.hifz_keep)) { now ->
            Hifz.setPages(this,
                marked = pages.filterIndexed { i, _ -> now[i] && !was[i] },
                unmarked = pages.filterIndexed { i, _ -> !now[i] && was[i] })
            refresh()
        }
    }

    private fun quiet(text: String): View =
        (layoutInflater.inflate(R.layout.row_setting_note, cards, false) as TextView).also { it.text = text }
}
