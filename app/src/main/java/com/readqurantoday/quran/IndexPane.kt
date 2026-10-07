package com.readqurantoday.quran

import android.app.Activity
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// The index tab: surahs, juz, hizb and pages, a swipe apart; every row opens or recites at its page
class IndexPane(
    private val host: Activity,
    private val surahs: SurahAdapter,
    private val open: (Int) -> Unit,
    private val pickReciter: (Surahs.Surah?) -> Unit,
    private val played: () -> Unit
) {

    private val res = host.resources
    private val juz = division(Surahs.juzStarts(), R.string.head_juz, ::surahAndPage, null, Surahs::juzOfPage)
    private val hizb = division(Surahs.hizbStarts(), R.string.head_hizb, ::surahAndPage, ::quarters, Surahs::hizbOfPage)
    private val pages = division(IntArray(Mushaf.PAGES) { it + 1 }, R.string.head_page, ::surahAndJuz, null) { it }

    private val lists: List<RecyclerView.Adapter<*>> = listOf(surahs, juz, hizb, pages)

    init {
        SwipeTabs(
            host.findViewById(R.id.lists), host.findViewById(R.id.segments),
            intArrayOf(R.string.list_surahs, R.string.tab_juz, R.string.list_hizb, R.string.list_pages), Lists()
        )
    }

    /** Rows show which part is being recited; called as the recitation moves or stops. */
    fun refresh() = lists.forEach { it.notifyDataSetChanged() }

    private fun division(starts: IntArray, title: Int, where: (Int) -> String, more: ((Int) -> String)?, numberOf: (Int) -> Int) =
        DivisionAdapter(
            starts = starts,
            title = { n -> host.getString(title, figures(n, res)) },
            where = where,
            more = more,
            onOpen = open,
            onPlay = { page -> play(page, numberOf) },
            onReciter = { page -> pickReciter(Surahs.ofPage(page)) },
            // Read from where the recitation actually is, so every list agrees wherever it was started
            playing = { numberOf(Recite.playingPage(host)) }
        )

    // Where a hizb's quarter, half and three-quarters begin, by page
    private fun quarters(hizb: Int): String {
        val at = Surahs.quartersOf(hizb).drop(1).map { figures(it, res) }
        if (at.size < 3) return ""
        return host.getString(R.string.hizb_quarters, at[0], at[1], at[2])
    }

    // The surah it begins in, then the page
    private fun surahAndPage(page: Int): String = meta(page, host.getString(R.string.head_page, figures(page, res)))

    // A page names its own number already, so its juz stands in for it
    private fun surahAndJuz(page: Int): String =
        meta(page, host.getString(R.string.head_juz, figures(Surahs.juzOfPage(page), res)))

    private fun meta(page: Int, after: String): String {
        val surah = Surahs.ofPage(page) ?: return after
        return host.getString(R.string.place_line, host.getString(R.string.surah_named, surah.name), after)
    }

    private fun play(page: Int, numberOf: (Int) -> Int) {
        val here = numberOf(page)
        // Already reciting this part: the button is a pause, as it is on a surah row
        if (here != 0 && here == numberOf(Recite.playingPage(host))) Recite.toggle()
        else Recite.startPage(host, page)
        played()
    }

    private inner class Lists : RecyclerView.Adapter<Lists.Page>() {

        inner class Page(val list: RecyclerView) : RecyclerView.ViewHolder(list)

        override fun getItemCount() = lists.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Page(
            (host.layoutInflater.inflate(R.layout.part_list, parent, false) as RecyclerView).apply {
                layoutManager = LinearLayoutManager(parent.context)
                keepToColumn()
                addHandle()
            }
        )

        override fun onBindViewHolder(holder: Page, position: Int) {
            holder.list.adapter = lists[position]
        }
    }
}
