package com.readqurantoday.quran

import android.os.Bundle
import android.view.View
import android.widget.TextView

/** Every page read today with the time it took, in mushaf order: a screen of its own, since a day may hold hundreds. */
class PageTimesActivity : CardsActivity(R.string.stats_page_times) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Surahs.load(this)
        val today = Stats.day(this, Stats.today())
        val pages = today.pages
        val total = pages.sumOf { today.pageSec.getValue(it) }
        layoutInflater.card(cards, 0, listOf(
            row(pagesSaid(pages.size, resources), spent(total, resources), "")
        ))
        layoutInflater.card(cards, 0, pages.map { page ->
            // Plain name in the quiet line; the calligraphy is for titles
            row(getString(R.string.head_page, figures(page, resources)),
                spentExact(today.pageSec.getValue(page), resources), Surahs.ofPage(page)?.name.orEmpty())
        })
        sayBars()
    }

    private fun row(label: String, value: String, note: String): View =
        layoutInflater.inflate(R.layout.row_progress, cards, false).apply {
            findViewById<TextView>(R.id.prog_label).text = label
            findViewById<TextView>(R.id.prog_value).text = value
            findViewById<TextView>(R.id.prog_note).apply { text = note; visibility = if (note.isEmpty()) View.GONE else View.VISIBLE }
        }
}
