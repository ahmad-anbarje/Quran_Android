package com.readqurantoday.quran

import android.app.Activity
import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.roundToInt

// Statistics tab: today told large, tiles beside it, the goal, the khatma, recent days, and what was heard
class StatsPane(private val host: Activity, private val into: LinearLayout) {

    /** [OPEN] plays the whole entrance; [UPDATE] moves rings and figures from where they last stood. */
    enum class Motion { OPEN, UPDATE }

    private val blow = host.layoutInflater
    private val res = host.resources

    // Where each ring and figure last stood, so an update moves on from there
    private val shares = HashMap<String, Float>()
    private val figuresShown = HashMap<String, Int>()

    private var motion = Motion.UPDATE
    private var moving = false

    // Rebuilt whole after any change: the cards are few and every number may move together
    fun build(motion: Motion = Motion.UPDATE) {
        this.motion = motion
        moving = motionOn(host)
        into.removeAllViews()
        val today = Stats.day(host, Stats.today())
        val goal = Stats.goal(host)
        val fortnight = Stats.lastDays(host, WEEK * 2)
        val week = fortnight.takeLast(WEEK)
        val before = fortnight.take(WEEK)

        blow.card(into, R.string.stats_today, listOf(todayHero(today, goal, week, before)))
        into.addView(pair(
            Tile(R.drawable.ic_clock, spent(today.readSec, res), host.getString(R.string.stats_read_time)),
            Tile(R.drawable.ic_headphones, spent(today.listenSec, res), host.getString(R.string.stats_listen_time))
        ))
        into.addView(pair(
            Tile(R.drawable.ic_calendar,
                host.getString(R.string.of_count, figures(week.count { it.pages.isNotEmpty() }, res), figures(WEEK, res)),
                host.getString(R.string.stats_days_read, figures(WEEK, res))),
            Tile(R.drawable.ic_stats_outline,
                pagesSaid(Stats.average(host, Stats.CHART_DAYS).roundToInt(), res),
                host.getString(R.string.stats_avg))
        ))

        blow.card(into, 0, listOf(goalRow(goal)))
        if (today.pages.isNotEmpty()) blow.card(into, R.string.stats_what, readRows(today.pages))
        blow.card(into, R.string.stats_khatma, khatmaRows())

        val month = Stats.lastDays(host, Stats.CHART_DAYS)
        blow.card(into, host.getString(R.string.stats_days, figures(Stats.CHART_DAYS, res)), monthRows(month, goal))
        blow.card(into, R.string.stats_listening, listeningRows(week, before))
        blow.card(into, 0, listOf(quiet(host.getString(R.string.stats_private))))

        // Card after card, a beat apart, top first
        if (moving && motion == Motion.OPEN) {
            for (i in 0 until into.childCount) into.getChildAt(i).riseIn(i * STAGGER_MS)
        }
    }

    // --- today ---

    private fun todayHero(today: Stats.Day, goal: Int, week: List<Stats.Day>, before: List<Stats.Day>): View {
        val read = today.pages.size
        return hero(
            key = TODAY,
            count = read,
            say = { figures(it, res) },
            unit = res.getQuantityString(R.plurals.pages_unit, read),
            title = host.getString(R.string.stats_pages_today),
            line = when {
                goal == 0 && read == 0 -> host.getString(R.string.stats_none_today)
                goal == 0 -> pagesSaid(read, res)
                read >= goal -> host.getString(R.string.stats_goal_reached)
                else -> host.getString(R.string.of_count, figures(read, res), pagesSaid(goal, res))
            },
            note = if (goal == 0) host.getString(R.string.stats_goal_hint) else ""
        ).also {
            fillRing(it, TODAY, read.toFloat(), goal.toFloat())
            showTrend(it.findViewById(R.id.hero_trend), week.sumOf { d -> d.pages.size }, before.sumOf { d -> d.pages.size })
        }
    }

    /* Pages read in unbroken runs, so 22, 23, 24 reads as one stretch. */
    private fun readRows(pages: List<Int>): List<View> {
        val runs = mutableListOf<Pair<Int, Int>>()
        for (p in pages) {
            val last = runs.lastOrNull()
            if (last != null && last.second == p - 1) runs[runs.size - 1] = last.first to p else runs += p to p
        }
        return runs.map { (from, to) ->
            val label = if (from == to) host.getString(R.string.head_page, figures(from, res))
                else host.getString(R.string.stats_pages_range, figures(from, res), figures(to, res))
            row(label, pagesSaid(to - from + 1, res), surahsOn(from, to))
        }
    }

    // Plain names in the meta line; the calligraphy is for titles
    private fun surahsOn(from: Int, to: Int): String {
        val first = Surahs.ofPage(from) ?: return ""
        val last = Surahs.ofPage(to) ?: first
        val name = { s: Surahs.Surah -> if (arabic()) s.name else s.english }
        return if (first.id == last.id) name(first) else "${name(first)} – ${name(last)}"
    }

    // --- daily goal ---

    private val goals by lazy {
        listOf(
            0 to Choice(host.getString(R.string.stats_goal_none)),
            5 to Choice(pagesSaid(5, res)),
            10 to Choice(pagesSaid(10, res)),
            20 to Choice(host.getString(R.string.stats_goal_juz), host.getString(R.string.stats_goal_month)),
            40 to Choice(host.getString(R.string.stats_goal_juz2), host.getString(R.string.stats_goal_half_month))
        )
    }

    private fun goalRow(goal: Int): View {
        val said = goals.firstOrNull { it.first == goal }?.second?.label ?: pagesSaid(goal, res)
        return row(host.getString(R.string.stats_goal), said).apply {
            isClickable = true
            setOnClickListener {
                host.sheet(host.getString(R.string.stats_goal), goals.map { (pages, c) -> c.copy(on = pages == goal) }) { i ->
                    Stats.setGoal(host, goals[i].first)
                    build()
                }
            }
        }
    }

    // --- khatma ---

    private fun khatmaRows(): List<View> {
        val k = Stats.khatma(host)
        val all = Mushaf.PAGES
        val pace = Stats.pace(host)
        val finish = if (pace > 0f) {
            host.getString(R.string.stats_finish_on, date(Stats.today() + ceil((all - k.read) / pace).toLong()))
        } else host.getString(R.string.stats_finish_unknown)

        val rows = mutableListOf<View>()
        rows += hero(
            key = KHATMA,
            count = k.read * 100 / all,
            say = { host.getString(R.string.percent, figures(it, res)) },
            unit = "",
            title = host.getString(R.string.stats_khatma),
            line = host.getString(R.string.stats_khatma_read, figures(k.read, res), figures(all, res)),
            note = finish
        ).also { fillRing(it, KHATMA, k.read.toFloat(), all.toFloat()) }
        rows += row(host.getString(R.string.stats_left), pagesSaid(all - k.read, res))
        if (k.done > 0) rows += row(host.getString(R.string.stats_done), figures(k.done, res))
        rows += action(host.getString(R.string.stats_new_khatma)) {
            host.sheet(host.getString(R.string.stats_new_khatma_ask), listOf(Choice(host.getString(R.string.stats_new_khatma_do)))) {
                Stats.newKhatma(host)
                build()
            }
        }
        return rows
    }

    private fun date(day: Long): String {
        val far = day - Stats.today() > 300
        val flags = DateUtils.FORMAT_SHOW_DATE or if (far) DateUtils.FORMAT_SHOW_YEAR else DateUtils.FORMAT_NO_YEAR
        return localDigits(DateUtils.formatDateTime(host, Stats.noonOf(day), flags), res)
    }

    // --- recent days ---

    private fun monthRows(month: List<Stats.Day>, goal: Int): List<View> {
        val counts = month.map { it.pages.size }
        val pad = res.getDimensionPixelSize(R.dimen.tile_pad)
        val chart = DayBars(host).apply {
            setPadding(pad, pad, pad, pad)
            show(counts, goal, grow = moving && motion == Motion.OPEN)
            contentDescription = host.getString(
                R.string.stats_chart_desc, figures(month.size, res), pagesSaid(counts.max(), res)
            )
        }
        return listOf(chart, row(host.getString(R.string.stats_total), pagesSaid(counts.sum(), res)))
    }

    // --- listening ---

    private fun listeningRows(week: List<Stats.Day>, before: List<Stats.Day>): List<View> {
        val heard = HashMap<Int, Int>()
        week.forEach { d -> d.surahSec.forEach { (s, sec) -> heard[s] = (heard[s] ?: 0) + sec } }
        val weekSec = week.sumOf { it.listenSec }

        val rows = mutableListOf<View>()
        rows += row(host.getString(R.string.stats_week, figures(WEEK, res)), spent(weekSec, res)).also {
            // Minutes, not seconds, so a few seconds either way is not called a change
            showTrend(it.findViewById(R.id.prog_trend), (weekSec + 30) / 60, (before.sumOf { d -> d.listenSec } + 30) / 60)
        }
        val top = heard.entries.sortedByDescending { it.value }.take(TOP_HEARD)
        if (top.isNotEmpty()) rows += quiet(host.getString(R.string.stats_most_heard, figures(WEEK, res)))
        top.forEach { (surah, sec) -> rows += heardRow(surah, sec) }
        return rows
    }

    // A surah heading its own row is written as the mushaf writes it
    private fun heardRow(surah: Int, sec: Int): View =
        blow.inflate(R.layout.row_place, into, false).apply {
            isClickable = false
            findViewById<ImageView>(R.id.place_icon).apply {
                setImageResource(R.drawable.ic_headphones)
                imageTintList = ColorStateList.valueOf(host.getColor(R.color.accent))
            }
            fillSurahTitle(findViewById(R.id.place_title), surah, R.dimen.surah_title_row)
            findViewById<TextView>(R.id.place_detail).text = spent(sec, res)
        }

    // --- parts ---

    // Up or down against the week before; nothing until there is a week before to compare with
    private fun showTrend(chip: View, now: Int, then: Int) {
        if (Stats.daysKept(host) <= WEEK || (now == 0 && then == 0)) return
        val up = now >= then
        chip.visibility = View.VISIBLE
        chip.findViewById<ImageView>(R.id.trend_icon).apply {
            setImageResource(if (up) R.drawable.ic_trend_up else R.drawable.ic_trend_down)
            imageTintList = ColorStateList.valueOf(host.getColor(R.color.accent))
        }
        chip.findViewById<TextView>(R.id.trend_text).text = when {
            now == then -> host.getString(R.string.trend_same)
            then == 0 -> host.getString(R.string.trend_new)
            else -> {
                val pct = host.getString(R.string.percent, figures(kotlin.math.abs(now - then) * 100 / then, res))
                host.getString(if (up) R.string.trend_up else R.string.trend_down, pct)
            }
        }
    }

    private fun hero(key: String, count: Int, say: (Int) -> String, unit: String, title: String, line: String, note: String): View =
        blow.inflate(R.layout.part_stat_hero, into, false).apply {
            countFigure(findViewById(R.id.hero_value), key, count, say)
            findViewById<TextView>(R.id.hero_unit).apply { text = unit; visibility = if (unit.isEmpty()) View.GONE else View.VISIBLE }
            findViewById<TextView>(R.id.hero_title).text = title
            findViewById<TextView>(R.id.hero_line).text = line
            findViewById<TextView>(R.id.hero_note).apply { text = note; visibility = if (note.isEmpty()) View.GONE else View.VISIBLE }
        }

    // From empty when the tab opens, from where it stood on an update, at once when motion is off
    private fun fillRing(hero: View, key: String, done: Float, total: Float) {
        val share = if (total > 0f) (done / total).coerceIn(0f, 1f) else 0f
        val from = if (!moving) null else if (motion == Motion.OPEN) 0f else shares[key] ?: 0f
        hero.findViewById<RingView>(R.id.hero_ring).show(done, total, from)
        shares[key] = share
    }

    private fun countFigure(view: TextView, key: String, value: Int, say: (Int) -> String) {
        val from = if (motion == Motion.OPEN) 0 else figuresShown[key] ?: 0
        if (moving && from != value) view.countTo(from, value, say) else view.text = say(value)
        figuresShown[key] = value
    }

    private data class Tile(val icon: Int, val value: String, val label: String)

    private fun pair(a: Tile, b: Tile): View =
        blow.inflate(R.layout.part_stat_pair, into, false).apply {
            fill(findViewById(R.id.tile_a), a)
            fill(findViewById(R.id.tile_b), b)
        }

    private fun fill(tile: View, t: Tile) {
        tile.findViewById<ImageView>(R.id.tile_icon).apply {
            setImageResource(t.icon)
            imageTintList = ColorStateList.valueOf(host.getColor(R.color.accent))
        }
        tile.findViewById<TextView>(R.id.tile_value).text = t.value
        tile.findViewById<TextView>(R.id.tile_label).text = t.label
    }

    private fun row(label: String, value: String, note: String = ""): View =
        blow.inflate(R.layout.row_progress, into, false).apply {
            findViewById<TextView>(R.id.prog_label).text = label
            findViewById<TextView>(R.id.prog_value).text = value
            findViewById<TextView>(R.id.prog_note).apply { text = note; visibility = if (note.isEmpty()) View.GONE else View.VISIBLE }
        }

    private fun quiet(text: String): View =
        (blow.inflate(R.layout.row_setting_note, into, false) as TextView).also { it.text = text }

    private fun action(label: String, act: () -> Unit): View =
        (blow.inflate(R.layout.row_setting_action, into, false) as TextView).also {
            it.text = label
            it.setOnClickListener { act() }
        }

    private fun arabic() = res.configuration.locales[0].language == "ar"

    private companion object {
        const val WEEK = 7
        const val TOP_HEARD = 3
        const val STAGGER_MS = 60L
        const val TODAY = "today"
        const val KHATMA = "khatma"
    }
}
