package com.readqurantoday.quran

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.roundToInt

// Achievements: four pages a swipe apart. Goals first, each telling what it is, what is done and what is left
class StatsPane(private val host: Activity, strip: View, pager: ViewPager2) {

    /** [OPEN] plays the whole entrance; [UPDATE] moves rings and figures from where they last stood. */
    enum class Motion { OPEN, UPDATE }

    private val blow = host.layoutInflater
    private val res = host.resources

    // Goals, reading, khatma, listening: one scrolling column each
    private val pages = List(TABS) { blow.inflate(R.layout.part_scroll_column, pager, false).apply { keepToColumn() } }
    private val columns = pages.map { it.findViewById<LinearLayout>(R.id.column) }
    private val parent get() = columns[0]

    private val tabs = SwipeTabs(
        pager, strip,
        intArrayOf(R.string.sec_goal, R.string.sec_reading, R.string.sec_khatma, R.string.sec_listening),
        ViewPages(pages)
    ) { page -> if (built) columns[page].riseChildren() }

    private val picker = GoalPicker(host) { build() }

    // Where each ring and figure last stood, so an update moves on from there
    private val shares = HashMap<String, Float>()
    private val figuresShown = HashMap<String, Int>()

    private var motion = Motion.UPDATE
    private var moving = false
    private var built = false

    // Rebuilt whole after any change: the cards are few and every number may move together
    fun build(motion: Motion = Motion.UPDATE) {
        this.motion = motion
        moving = motionOn(host)
        columns.forEach { it.removeAllViews() }

        val today = Stats.day(host, Stats.today())
        val goal = Stats.goal(host)
        val fortnight = Stats.lastDays(host, WEEK * 2)
        val week = fortnight.takeLast(WEEK)
        val before = fortnight.take(WEEK)

        goalsPage(columns[GOALS], today, goal, week, before)
        readingPage(columns[READING], today, goal)
        khatmaPage(columns[KHATMA_PAGE])
        listeningPage(columns[LISTENING], today, week, before)

        if (motion == Motion.OPEN) {
            columns[tabs.current].riseChildren()
            celebrateOnce(today, goal)
        }
        built = true
    }

    // The first look at the day's reached goal is met with a celebration; later looks are quiet
    private fun celebrateOnce(today: Stats.Day, goal: Int) {
        if (goal == 0 || today.pages.size < goal || Stats.celebrated(host)) return
        Stats.setCelebrated(host)
        val over = host.findViewById<ViewGroup>(android.R.id.content)
        over.postDelayed({ over.celebrate(host.getString(R.string.goal_done)) }, CELEBRATE_AFTER_MS)
    }

    // --- goals: what it is, what is done, what is left ---

    private fun goalsPage(into: LinearLayout, today: Stats.Day, goal: Int, week: List<Stats.Day>, before: List<Stats.Day>) {
        blow.card(into, R.string.wird_title, wirdRows(today, goal, week, before))
        blow.card(into, R.string.hifz_title, hifzRows())
        blow.card(into, 0, listOf(quiet(host.getString(R.string.stats_private))))
    }

    private fun wirdRows(today: Stats.Day, goal: Int, week: List<Stats.Day>, before: List<Stats.Day>): List<View> {
        val plan = Stats.goalPlan(host)
        val read = today.pages.size
        val hero = hero(
            key = TODAY, count = read,
            say = { if (it == 0) none(res) else figures(it, res) },
            unit = if (read == 0) "" else res.getQuantityString(R.plurals.pages_unit, read),
            title = host.getString(R.string.stats_pages_today),
            line = when {
                read == 0 -> host.getString(R.string.stats_none_today)
                goal == 0 -> pagesSaid(read, res)
                read >= goal -> host.getString(R.string.stats_goal_reached)
                else -> host.getString(R.string.of_count, figures(read, res), pagesSaid(goal, res))
            },
            note = if (goal == 0) host.getString(R.string.goal_none_today) else ""
        ).also {
            fillRing(it, TODAY, read.toFloat(), goal.toFloat())
            showTrend(it.findViewById(R.id.hero_trend), week.sumOf { d -> d.pages.size }, before.sumOf { d -> d.pages.size })
        }
        return listOf(
            hero,
            changeRow(plan.said(host)) { picker.reading(plan) },
            row(host.getString(R.string.done_week), metOfWeek(plan, week)),
            row(host.getString(R.string.left_today), leftSaid(read, goal), khatmaNote(plan, goal))
        )
    }

    // Days this week that met their goal, out of the days that had one
    private fun metOfWeek(plan: Goal, week: List<Stats.Day>): String {
        val cal = Calendar.getInstance()
        var had = 0
        var met = 0
        week.forEachIndexed { i, d ->
            cal.timeInMillis = Stats.noonOf(Stats.today() - (week.size - 1 - i))
            // A khatma's pages a day are only known for today, so any day of reading counts toward it
            val want = if (plan.kind == Goal.Kind.KHATMA) 1 else plan.pagesOn(cal.get(Calendar.DAY_OF_WEEK))
            if (want > 0) {
                had++
                if (d.pages.size >= want) met++
            }
        }
        if (had == 0) return none(res)
        return host.getString(R.string.of_count, figures(met, res), res.getQuantityString(R.plurals.days_count, had, figures(had, res)))
    }

    private fun leftSaid(done: Int, goal: Int): String = when {
        goal == 0 -> none(res)
        done >= goal -> host.getString(R.string.left_none)
        else -> pagesSaid(goal - done, res)
    }

    // A khatma goal says what its daily pages are for
    private fun khatmaNote(plan: Goal, goal: Int): String =
        if (plan.kind != Goal.Kind.KHATMA || goal == 0) ""
        else host.getString(R.string.khatma_daily, pagesSaid(goal, res), dateSaid(host, plan.amount.toLong()))

    private fun hifzRows(): List<View> {
        val plan = Stats.hifzPlan(host)
        val mine = mineRow()
        if (plan == null) return listOf(
            quiet(host.getString(R.string.hifz_none)),
            action(host.getString(R.string.hifz_add)) { picker.hifz(null) },
            mine
        )
        val goal = Stats.hifzGoal(host)
        val learned = Hifz.learnedOn(host, Stats.today())
        val hero = hero(
            key = HIFZ, count = learned,
            say = { if (it == 0) none(res) else figures(it, res) },
            unit = if (learned == 0) "" else res.getQuantityString(R.plurals.pages_unit, learned),
            title = host.getString(R.string.hifz_today),
            line = when {
                goal == 0 -> if (learned == 0) host.getString(R.string.goal_none_today) else pagesSaid(learned, res)
                learned >= goal -> host.getString(R.string.stats_goal_reached)
                else -> host.getString(R.string.of_count, figures(learned, res), pagesSaid(goal, res))
            },
            note = ""
        ).also { fillRing(it, HIFZ, learned.toFloat(), goal.toFloat()) }
        return listOf(
            hero,
            changeRow(plan.said(host)) { picker.hifz(plan) },
            row(host.getString(R.string.left_today), leftSaid(learned, goal)),
            mine
        )
    }

    // What is memorised in all, a tap away from the list of surahs and pages
    private fun mineRow(): View {
        val known = Hifz.pages(host).size
        return row(
            host.getString(R.string.hifz_mine),
            if (known == 0) none(res) else host.getString(R.string.percent, figures(known * 100 / Mushaf.PAGES, res)),
            if (known == 0) host.getString(R.string.hifz_mine_empty) else host.getString(R.string.hifz_mine_pages, pagesSaid(known, res))
        ).apply {
            isClickable = true
            setOnClickListener { host.startActivity(Intent(host, MemorizedActivity::class.java)) }
        }
    }

    // The goal as written, with the way to change it
    private fun changeRow(said: String, change: () -> Unit): View =
        row(host.getString(R.string.goal_title), host.getString(R.string.goal_change), said).apply {
            isClickable = true
            setOnClickListener { change() }
        }

    // --- reading ---

    private fun readingPage(into: LinearLayout, today: Stats.Day, goal: Int) {
        val hijri = Stats.hijri(host)
        val thisMonth = Stats.monthDays(host)
        blow.card(into, 0, listOf(calendarRow(hijri)))
        into.addView(pair(
            Tile(R.drawable.ic_clock, spent(today.readSec, res), host.getString(R.string.stats_read_time)),
            Tile(R.drawable.ic_clock, spent(thisMonth.sumOf { it.readSec }, res), host.getString(R.string.stats_read_month))
        ))
        into.addView(pair(
            Tile(R.drawable.ic_calendar,
                thisMonth.count { it.pages.isNotEmpty() }.let { n ->
                    if (n == 0) none(res) else host.getString(R.string.of_count, figures(n, res), figures(thisMonth.size, res))
                },
                host.getString(R.string.stats_days_read)),
            Tile(R.drawable.ic_stats_outline,
                pagesSaid((thisMonth.sumOf { it.pages.size }.toFloat() / thisMonth.size).roundToInt(), res),
                host.getString(R.string.stats_avg))
        ))
        blow.card(into, R.string.stats_speed, speedRows(today, thisMonth))
        val month = Stats.lastDays(host, Stats.CHART_DAYS)
        blow.card(into, host.getString(R.string.stats_days, figures(Stats.CHART_DAYS, res)), monthRows(month, goal))
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
                if (today.pages.isEmpty()) none(res) else perPage(onPages(today) / today.pages.size)),
            row(host.getString(R.string.stats_page_avg_month),
                if (monthPages == 0) none(res) else perPage(monthSec / monthPages)),
            perHourRow(today, monthPages, monthSec)
        )
        if (today.pages.isNotEmpty()) rows += action(
            host.getString(R.string.stats_page_times_open, figures(today.pages.size, res))
        ) { host.startActivity(Intent(host, PageTimesActivity::class.java)) }
        return rows
    }

    // A rate, not a promise: today's pace over an hour, or the month's before anything is read today
    private fun perHourRow(today: Stats.Day, monthPages: Int, monthSec: Int): View {
        val todaySec = onPages(today)
        val (pages, sec, by) = if (todaySec > 0) Triple(today.pages.size, todaySec, R.string.stats_per_hour_today)
            else Triple(monthPages, monthSec, R.string.stats_per_hour_juz)
        if (sec == 0) return row(host.getString(R.string.stats_per_hour), none(res), host.getString(R.string.stats_per_hour_note))
        val perHour = pages * 3600f / sec
        return row(host.getString(R.string.stats_per_hour), pagesSaid(perHour.roundToInt(), res), host.getString(by, juzSaid(perHour, res)))
    }

    private fun perPage(sec: Int) = host.getString(R.string.per_page, spentExact(sec, res))

    // Time on the pages that were read, leaving out pages only passed over
    private fun onPages(day: Stats.Day): Int = day.pages.sumOf { day.pageSec.getValue(it) }

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
        return listOf(chart, row(host.getString(R.string.stats_total, figures(month.size, res)), pagesSaid(counts.sum(), res)))
    }

    private fun dayName(ago: Int): String =
        if (ago == 0) host.getString(R.string.chart_today) else res.getQuantityString(R.plurals.ago_days, ago, figures(ago, res))

    // --- khatma ---

    private fun khatmaPage(into: LinearLayout) {
        val k = Stats.khatma(host)
        val all = Mushaf.PAGES
        val pace = Stats.pace(host)
        val finish = if (pace > 0f) {
            host.getString(R.string.stats_finish_on, dateSaid(host, Stats.today() + ceil((all - k.read) / pace).toLong()))
        } else host.getString(R.string.stats_finish_unknown)

        val rows = mutableListOf<View>()
        rows += hero(
            key = KHATMA, count = k.read * 100 / all,
            say = { if (it == 0) none(res) else host.getString(R.string.percent, figures(it, res)) },
            unit = "", title = "",
            line = if (k.read == 0) host.getString(R.string.stats_khatma_none)
                else host.getString(R.string.stats_khatma_read, pagesSaid(k.read, res), figures(all, res)),
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
        blow.card(into, 0, rows)
    }

    // --- listening ---

    private fun listeningPage(into: LinearLayout, today: Stats.Day, week: List<Stats.Day>, before: List<Stats.Day>) {
        val weekSec = week.sumOf { it.listenSec }
        into.addView(pair(
            Tile(R.drawable.ic_headphones, spent(today.listenSec, res), host.getString(R.string.stats_listen_time)),
            Tile(R.drawable.ic_headphones, spent(weekSec, res), host.getString(R.string.stats_week, figures(WEEK, res))) {
                // Minutes, not seconds, so a few seconds either way is not called a change
                showTrend(it, (weekSec + 30) / 60, (before.sumOf { d -> d.listenSec } + 30) / 60)
            }
        ))
        mostHeard(week)?.let { blow.card(into, host.getString(R.string.stats_most_heard, figures(WEEK, res)), it) }
    }

    private fun mostHeard(week: List<Stats.Day>): List<View>? {
        val heard = HashMap<Int, Int>()
        week.forEach { d -> d.surahSec.forEach { (s, sec) -> heard[s] = (heard[s] ?: 0) + sec } }
        val top = heard.entries.sortedByDescending { it.value }.take(TOP_HEARD)
        return top.map { (surah, sec) -> heardRow(surah, sec) }.ifEmpty { null }
    }

    // A surah heading its own row is written as the mushaf writes it
    private fun heardRow(surah: Int, sec: Int): View =
        blow.inflate(R.layout.row_place, parent, false).apply {
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
        // The arrow already says up or down, so the chip says only by how much; a screen reader hears it whole
        val pct = if (then == 0) "" else host.getString(R.string.percent, figures(kotlin.math.abs(now - then) * 100 / then, res))
        chip.findViewById<TextView>(R.id.trend_text).text = when {
            now == then -> host.getString(R.string.trend_same)
            then == 0 -> host.getString(R.string.trend_new)
            else -> host.getString(R.string.trend_by, pct)
        }
        chip.contentDescription = when {
            now == then -> host.getString(R.string.trend_same)
            then == 0 -> host.getString(R.string.trend_new)
            else -> host.getString(if (up) R.string.trend_up else R.string.trend_down, pct)
        }
    }

    private fun hero(key: String, count: Int, say: (Int) -> String, unit: String, title: String, line: String, note: String): View =
        blow.inflate(R.layout.part_stat_hero, parent, false).apply {
            countFigure(findViewById(R.id.hero_value), key, count, say)
            findViewById<TextView>(R.id.hero_unit).apply { text = unit; visibility = if (unit.isEmpty()) View.GONE else View.VISIBLE }
            findViewById<TextView>(R.id.hero_title).apply { text = title; visibility = if (title.isEmpty()) View.GONE else View.VISIBLE }
            findViewById<TextView>(R.id.hero_line).text = line
            findViewById<TextView>(R.id.hero_note).apply { text = note; visibility = if (note.isEmpty()) View.GONE else View.VISIBLE }
        }

    // From empty when the tab opens, from where it stood on an update, at once when motion is off
    private fun fillRing(hero: View, key: String, done: Float, total: Float) {
        val share = if (total > 0f) (done / total).coerceIn(0f, 1f) else 0f
        // A first build, with nothing shown before it, simply shows the value
        val from = if (!moving) null else if (motion == Motion.OPEN) 0f else shares[key]
        hero.findViewById<RingView>(R.id.hero_ring).show(done, total, from)
        shares[key] = share
    }

    private fun countFigure(view: TextView, key: String, value: Int, say: (Int) -> String) {
        val from = if (motion == Motion.OPEN) 0 else figuresShown[key] ?: value
        if (moving && from != value) view.countTo(from, value, say) else view.text = say(value)
        figuresShown[key] = value
    }

    private class Tile(val icon: Int, val value: String, val label: String, val trend: ((View) -> Unit)? = null)

    private fun pair(a: Tile, b: Tile): View =
        blow.inflate(R.layout.part_stat_pair, parent, false).apply {
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

    private fun row(label: String, value: String, note: String = ""): View =
        blow.inflate(R.layout.row_progress, parent, false).apply {
            findViewById<TextView>(R.id.prog_label).text = label
            findViewById<TextView>(R.id.prog_value).text = value
            findViewById<TextView>(R.id.prog_note).apply { text = note; visibility = if (note.isEmpty()) View.GONE else View.VISIBLE }
        }

    private fun quiet(text: String): View =
        (blow.inflate(R.layout.row_setting_note, parent, false) as TextView).also { it.text = text }

    private fun action(label: String, act: () -> Unit): View =
        (blow.inflate(R.layout.row_setting_action, parent, false) as TextView).also {
            it.text = label
            it.setOnClickListener { act() }
        }

    private companion object {
        const val TABS = 4
        const val GOALS = 0
        const val READING = 1
        const val KHATMA_PAGE = 2
        const val LISTENING = 3

        const val WEEK = 7
        const val TOP_HEARD = 3
        // Long enough for the cards to have come in first
        const val CELEBRATE_AFTER_MS = 450L
        const val TODAY = "today"
        const val HIFZ = "hifz"
        const val KHATMA = "khatma"
    }
}
