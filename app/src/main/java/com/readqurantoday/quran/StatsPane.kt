package com.readqurantoday.quran

import android.app.Activity
import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
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

        val month = Stats.lastDays(host, Stats.CHART_DAYS)

        section(R.string.sec_goal)
        blow.card(into, 0, listOf(todayHero(today, goal, week, before), goalRow(goal)))

        section(R.string.sec_reading)
        val hijri = Stats.hijri(host)
        val thisMonth = Stats.monthDays(host)
        blow.card(into, 0, listOf(calendarRow(hijri)))
        into.addView(pair(
            Tile(R.drawable.ic_clock, spent(today.readSec, res), host.getString(R.string.stats_read_time)),
            Tile(R.drawable.ic_clock, spent(thisMonth.sumOf { it.readSec }, res), host.getString(R.string.stats_read_month))
        ))
        into.addView(pair(
            Tile(R.drawable.ic_calendar,
                host.getString(R.string.of_count, figures(thisMonth.count { it.pages.isNotEmpty() }, res), figures(thisMonth.size, res)),
                host.getString(R.string.stats_days_read)),
            Tile(R.drawable.ic_stats_outline,
                pagesSaid((thisMonth.sumOf { it.pages.size }.toFloat() / thisMonth.size).roundToInt(), res),
                host.getString(R.string.stats_avg))
        ))
        blow.card(into, R.string.stats_speed, speedRows(today, thisMonth))
        blow.card(into, host.getString(R.string.stats_days, figures(Stats.CHART_DAYS, res)), monthRows(month, goal))

        section(R.string.sec_khatma)
        blow.card(into, 0, khatmaRows())

        section(R.string.sec_listening)
        val weekSec = week.sumOf { it.listenSec }
        into.addView(pair(
            Tile(R.drawable.ic_headphones, spent(today.listenSec, res), host.getString(R.string.stats_listen_time)),
            Tile(R.drawable.ic_headphones, spent(weekSec, res), host.getString(R.string.stats_week, figures(WEEK, res))) {
                // Minutes, not seconds, so a few seconds either way is not called a change
                showTrend(it, (weekSec + 30) / 60, (before.sumOf { d -> d.listenSec } + 30) / 60)
            }
        ))
        mostHeard(week)?.let { blow.card(into, host.getString(R.string.stats_most_heard, figures(WEEK, res)), it) }

        blow.card(into, 0, listOf(quiet(host.getString(R.string.stats_private))))

        if (motion == Motion.OPEN) {
            into.riseChildren()
            celebrateOnce(today, goal)
        }
    }

    // The first look at the day's reached goal is met with a celebration; later looks are quiet
    private fun celebrateOnce(today: Stats.Day, goal: Int) {
        if (goal == 0 || today.pages.size < goal || Stats.celebrated(host)) return
        Stats.setCelebrated(host)
        val over = host.findViewById<ViewGroup>(android.R.id.content)
        over.postDelayed({ over.celebrate(host.getString(R.string.goal_done)) }, CELEBRATE_AFTER_MS)
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

    // Which month the numbers below belong to; a tap moves between the Hijri and Gregorian calendars
    private fun calendarRow(hijri: Boolean): View =
        row(host.getString(R.string.stats_month), monthName(Stats.today(), hijri, res),
            host.getString(if (hijri) R.string.cal_hijri else R.string.cal_greg)).apply {
            isClickable = true
            setOnClickListener {
                host.sheet(host.getString(R.string.stats_calendar), listOf(
                    Choice(host.getString(R.string.cal_hijri), on = hijri),
                    Choice(host.getString(R.string.cal_greg), on = !hijri)
                )) { i ->
                    Stats.setHijri(host, i == 0)
                    build()
                }
            }
        }

    // How long a page takes, today and this month; every page's own time is a screen of its own, for days of many pages
    private fun speedRows(today: Stats.Day, thisMonth: List<Stats.Day>): List<View> {
        val monthPages = thisMonth.sumOf { it.pages.size }
        val monthSec = thisMonth.sumOf { onPages(it) }
        val rows = mutableListOf(
            row(host.getString(R.string.stats_page_avg),
                if (today.pages.isEmpty()) "–" else spentExact(onPages(today) / today.pages.size, res)),
            row(host.getString(R.string.stats_page_avg_month),
                if (monthPages == 0) "–" else spentExact(monthSec / monthPages, res)),
            row(host.getString(R.string.stats_per_hour),
                if (monthSec == 0) "–" else pagesSaid((monthPages * 3600f / monthSec).roundToInt(), res))
        )
        if (today.pages.isNotEmpty()) rows += action(
            host.getString(R.string.stats_page_times_open, figures(today.pages.size, res))
        ) { host.startActivity(android.content.Intent(host, PageTimesActivity::class.java)) }
        return rows
    }

    // Time on the pages that were read, leaving out pages only passed over
    private fun onPages(day: Stats.Day): Int = day.pages.sumOf { day.pageSec.getValue(it) }

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
            // The card is already headed «khatma»
            title = "",
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
            show(counts, goal, host.getString(R.string.chart_goal), ::dayName, grow = moving && motion == Motion.OPEN)
            contentDescription = host.getString(
                R.string.stats_chart_desc, figures(month.size, res), pagesSaid(counts.max(), res)
            )
        }
        return listOf(chart, row(host.getString(R.string.stats_total), pagesSaid(counts.sum(), res)))
    }

    private fun dayName(ago: Int): String =
        if (ago == 0) host.getString(R.string.chart_today) else res.getQuantityString(R.plurals.ago_days, ago, figures(ago, res))

    // --- listening ---

    private fun mostHeard(week: List<Stats.Day>): List<View>? {
        val heard = HashMap<Int, Int>()
        week.forEach { d -> d.surahSec.forEach { (s, sec) -> heard[s] = (heard[s] ?: 0) + sec } }
        val top = heard.entries.sortedByDescending { it.value }.take(TOP_HEARD)
        return top.map { (surah, sec) -> heardRow(surah, sec) }.ifEmpty { null }
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
            findViewById<TextView>(R.id.hero_title).apply { text = title; visibility = if (title.isEmpty()) View.GONE else View.VISIBLE }
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

    private class Tile(val icon: Int, val value: String, val label: String, val trend: ((View) -> Unit)? = null)

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
        t.trend?.invoke(tile.findViewById(R.id.tile_trend))
    }

    private fun section(title: Int) {
        (blow.inflate(R.layout.part_section_head, into, false) as TextView).also {
            it.setText(title)
            into.addView(it)
        }
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


    private companion object {
        const val WEEK = 7
        const val TOP_HEARD = 3
        // Long enough for the cards to have come in first
        const val CELEBRATE_AFTER_MS = 450L
        const val TODAY = "today"
        const val KHATMA = "khatma"
    }
}
