package com.readqurantoday.quran

import android.app.Activity
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

// The index tab: surahs, juz, hizb and pages, a swipe apart; every row opens or recites at its page
class IndexPane(
    private val host: Activity,
    private val surahs: SurahAdapter,
    private val open: (Int) -> Unit,
    private val pickReciter: (Surahs.Surah?) -> Unit,
    private val played: () -> Unit
) {

    private val res = host.resources
    private val pager = host.findViewById<ViewPager2>(R.id.lists)
    private val segs = intArrayOf(R.id.seg_surahs, R.id.seg_juz, R.id.seg_hizb, R.id.seg_pages)
        .map { host.findViewById<TextView>(it) }
    private val names = intArrayOf(R.string.list_surahs, R.string.tab_juz, R.string.list_hizb, R.string.list_pages)

    /* The switch's own face, read before the first bolding, so the app font survives it. */
    private val segFace = segs[0].typeface

    private val juz = division(Surahs.juzStarts(), R.string.head_juz, ::surahAndPage, null, Surahs::juzOfPage)
    private val hizb = division(Surahs.hizbStarts(), R.string.head_hizb, ::surahAndPage, ::quarters, Surahs::hizbOfPage)
    private val pages = division(IntArray(Mushaf.PAGES) { it + 1 }, R.string.head_page, ::surahAndJuz, null) { it }

    private val lists: List<RecyclerView.Adapter<*>> = listOf(surahs, juz, hizb, pages)

    init {
        segs.forEachIndexed { i, seg ->
            seg.setText(names[i])
            seg.setOnClickListener { pager.currentItem = i }
        }
        pager.adapter = Lists()
        // All four kept laid out, so a swipe back finds a list where it was left
        pager.offscreenPageLimit = lists.size - 1
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = mark(position)
        })
        mark(pager.currentItem)
    }

    /** Rows show which part is being recited; called as the recitation moves or stops. */
    fun refresh() = lists.forEach { it.notifyDataSetChanged() }

    private fun mark(on: Int) = segs.forEachIndexed { i, seg ->
        val isOn = i == on
        seg.setBackgroundResource(if (isOn) R.drawable.seg_on else R.drawable.row_flat)
        seg.setTextColor(host.getColor(if (isOn) R.color.accent else R.color.text_mute))
        // Built from the theme's own face: defaultFromStyle would put the system font here
        seg.typeface = Typeface.create(segFace, if (isOn) Typeface.BOLD else Typeface.NORMAL)
    }

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
