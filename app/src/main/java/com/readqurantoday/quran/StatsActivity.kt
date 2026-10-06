package com.readqurantoday.quran

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.ceil

/** Reading and listening at a glance: today, the daily goal, the khatma, recent days, and what was heard. */
class StatsActivity : CardsActivity(R.string.stats_title) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Surahs.load(this)
        build()
        sayBars()
    }

    // Rebuilt whole after any change: the cards are few and every number may move together
    private fun build() {
        cards.removeAllViews()
        val today = Stats.day(this, Stats.today())
        val goal = Stats.goal(this)
        val blow = layoutInflater

        blow.card(cards, R.string.stats_today, todayRows(today, goal))
        blow.card(cards, 0, listOf(goalRow(goal)))
        blow.card(cards, R.string.stats_khatma, khatmaRows())

        val month = Stats.lastDays(this, Stats.CHART_DAYS)
        blow.card(cards, getString(R.string.stats_days, figures(Stats.CHART_DAYS, resources)), monthRows(month, goal))

        blow.card(cards, R.string.stats_listening, listeningRows(today, month))
        blow.card(cards, 0, listOf(quiet(getString(R.string.stats_private))))
    }

    // --- today ---

    private fun todayRows(today: Stats.Day, goal: Int): List<View> {
        val read = today.pages.size
        val rows = mutableListOf<View>()
        rows += row(getString(R.string.stats_pages_read), pagesSaid(read, resources)).also {
            if (goal > 0) {
                it.track(read, goal)
                it.note(getString(
                    if (read >= goal) R.string.stats_goal_reached else R.string.stats_goal_of,
                    figures(read, resources), pagesSaid(goal, resources)
                ))
            }
        }
        rows += row(getString(R.string.stats_read_time), spent(today.readSec, resources))
        rows += row(getString(R.string.stats_listen_time), spent(today.listenSec, resources))
        if (read == 0) return rows + quiet(getString(R.string.stats_none_today))
        rows += quiet(getString(R.string.stats_what))
        rows += runs(today.pages).map { (from, to) -> row(range(from, to), "").also { it.note(surahsOn(from, to)) } }
        return rows
    }

    /* Pages read in unbroken runs, so 22, 23, 24 reads as one stretch. */
    private fun runs(pages: List<Int>): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        for (p in pages) {
            val last = out.lastOrNull()
            if (last != null && last.second == p - 1) out[out.size - 1] = last.first to p else out += p to p
        }
        return out
    }

    private fun range(from: Int, to: Int) =
        if (from == to) getString(R.string.head_page, figures(from, resources))
        else getString(R.string.stats_pages_range, figures(from, resources), figures(to, resources))

    // Plain names in the meta line; the calligraphy is for titles
    private fun surahsOn(from: Int, to: Int): String {
        val first = Surahs.ofPage(from) ?: return ""
        val last = Surahs.ofPage(to) ?: first
        val name = { s: Surahs.Surah -> if (isArabic()) s.name else s.english }
        return if (first.id == last.id) name(first) else "${name(first)} – ${name(last)}"
    }

    // --- daily goal ---

    private val goals by lazy {
        listOf(
            0 to Choice(getString(R.string.stats_goal_none)),
            5 to Choice(pagesSaid(5, resources)),
            10 to Choice(pagesSaid(10, resources)),
            20 to Choice(getString(R.string.stats_goal_juz), getString(R.string.stats_goal_month)),
            40 to Choice(getString(R.string.stats_goal_juz2), getString(R.string.stats_goal_half_month))
        )
    }

    private fun goalRow(goal: Int): View {
        val said = goals.firstOrNull { it.first == goal }?.second?.label ?: pagesSaid(goal, resources)
        return row(getString(R.string.stats_goal), said).apply {
            isClickable = true
            setOnClickListener {
                sheet(getString(R.string.stats_goal), goals.map { (pages, c) -> c.copy(on = pages == goal) }) { i ->
                    Stats.setGoal(this@StatsActivity, goals[i].first)
                    build()
                }
            }
        }
    }

    // --- khatma ---

    private fun khatmaRows(): List<View> {
        val k = Stats.khatma(this)
        val all = Mushaf.PAGES
        val left = all - k.read
        val rows = mutableListOf<View>()
        rows += row(
            getString(R.string.stats_khatma_read, figures(k.read, resources), figures(all, resources)),
            getString(R.string.percent, figures(k.read * 100 / all, resources))
        ).also { it.track(k.read, all) }
        rows += row(getString(R.string.stats_left), pagesSaid(left, resources))

        val pace = Stats.pace(this)
        rows += if (pace > 0f) {
            val day = Stats.today() + ceil(left / pace).toLong()
            row(getString(R.string.stats_finish), date(day))
        } else quiet(getString(R.string.stats_finish_unknown))

        if (k.done > 0) rows += row(getString(R.string.stats_done), figures(k.done, resources))
        rows += action(getString(R.string.stats_new_khatma)) {
            sheet(getString(R.string.stats_new_khatma_ask), listOf(Choice(getString(R.string.stats_new_khatma_do)))) {
                Stats.newKhatma(this)
                build()
            }
        }
        return rows
    }

    private fun date(day: Long): String {
        val far = day - Stats.today() > 300
        val flags = DateUtils.FORMAT_SHOW_DATE or if (far) DateUtils.FORMAT_SHOW_YEAR else DateUtils.FORMAT_NO_YEAR
        return localDigits(DateUtils.formatDateTime(this, Stats.noonOf(day), flags), resources)
    }

    // --- recent days ---

    private fun monthRows(month: List<Stats.Day>, goal: Int): List<View> {
        val counts = month.map { it.pages.size }
        val pad = resources.getDimensionPixelSize(R.dimen.screen_margin)
        val chart = DayBars(this).apply {
            setPadding(pad, pad, pad, pad)
            show(counts, goal)
            contentDescription = getString(
                R.string.stats_chart_desc, figures(month.size, resources), pagesSaid(counts.max(), resources)
            )
        }
        val average = Stats.average(this, Stats.CHART_DAYS)
        return listOf(chart, row(getString(R.string.stats_avg), pagesSaid(Math.round(average), resources)))
    }

    // --- listening ---

    private fun listeningRows(today: Stats.Day, month: List<Stats.Day>): List<View> {
        val week = month.takeLast(WEEK)
        val heard = HashMap<Int, Int>()
        week.forEach { d -> d.surahSec.forEach { (s, sec) -> heard[s] = (heard[s] ?: 0) + sec } }

        val rows = mutableListOf<View>()
        rows += row(getString(R.string.stats_today), spent(today.listenSec, resources))
        rows += row(getString(R.string.stats_week, figures(WEEK, resources)), spent(week.sumOf { it.listenSec }, resources))
        val top = heard.entries.sortedByDescending { it.value }.take(TOP_HEARD)
        if (top.isNotEmpty()) {
            rows += quiet(getString(R.string.stats_most_heard, figures(WEEK, resources)))
            rows += top.map { (surah, sec) -> heardRow(surah, sec) }
        }
        return rows
    }

    // A surah heading its own row is written as the mushaf writes it
    private fun heardRow(surah: Int, sec: Int): View =
        layoutInflater.inflate(R.layout.row_place, cards, false).apply {
            isClickable = false
            findViewById<ImageView>(R.id.place_icon).apply {
                setImageResource(R.drawable.ic_play)
                imageTintList = ColorStateList.valueOf(getColor(R.color.accent))
            }
            fillSurahTitle(findViewById(R.id.place_title), surah, R.dimen.surah_title_row)
            findViewById<TextView>(R.id.place_detail).text = spent(sec, resources)
        }

    // --- rows ---

    private fun row(label: String, value: String): View =
        layoutInflater.inflate(R.layout.row_progress, cards, false).apply {
            findViewById<TextView>(R.id.prog_label).text = label
            findViewById<TextView>(R.id.prog_value).text = value
        }

    private fun View.note(text: String) {
        findViewById<TextView>(R.id.prog_note).apply {
            this.text = text
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun View.track(done: Int, total: Int) =
        findViewById<View>(R.id.prog_track).fillTrack(done.toFloat(), total.toFloat())

    private fun quiet(text: String): View =
        (layoutInflater.inflate(R.layout.row_setting_note, cards, false) as TextView).also { it.text = text }

    private fun action(label: String, act: () -> Unit): View =
        (layoutInflater.inflate(R.layout.row_setting_action, cards, false) as TextView).also {
            it.text = label
            it.setOnClickListener { act() }
        }

    private fun isArabic() = resources.configuration.locales[0].language == "ar"

    private companion object {
        const val WEEK = 7
        const val TOP_HEARD = 3
    }
}
