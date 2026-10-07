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

// Achievements: reading, memorising, khatma and listening a swipe apart; each opens on where it stands,
// what is wanted and what is done
class StatsPane(private val host: Activity, strip: View, pager: ViewPager2) {

    /** [OPEN] plays the whole entrance; [UPDATE] moves rings and figures from where they last stood. */
    enum class Motion { OPEN, UPDATE }

    private val blow = host.layoutInflater
    private val res = host.resources

    // One scrolling column a tab
    private val pages = List(TABS) { blow.inflate(R.layout.part_scroll_column, pager, false).apply { keepToColumn() } }
    private val columns = pages.map { it.findViewById<LinearLayout>(R.id.column) }
    private val parent get() = columns[0]

    private val tabs = SwipeTabs(
        pager, strip,
        intArrayOf(R.string.sec_reading, R.string.sec_hifz, R.string.sec_khatma, R.string.sec_listening),
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

        readingPage(columns[READING], today, goal, week, before)
        hifzPage(columns[HIFZ_PAGE])
        khatmaPage(columns[KHATMA_PAGE])
        listeningPage(columns[LISTENING], today, week, before)

        if (motion == Motion.OPEN) {
            columns[tabs.current].riseChildren()
            celebrateOnce(today, goal)
        }
        built = true
    }

    // Pages read today, or the whole goal once the reader says it was read from a printed mushaf
    private fun readToday(today: Stats.Day, goal: Int) =
        if (today.doneByHand) maxOf(today.pages.size, goal) else today.pages.size

    // The first look at the day's reached goal is met with a celebration; later looks are quiet
    private fun celebrateOnce(today: Stats.Day, goal: Int) {
        if (goal == 0 || readToday(today, goal) < goal || Stats.celebrated(host)) return
        Stats.setCelebrated(host)
        val over = host.findViewById<ViewGroup>(android.R.id.content)
        over.postDelayed({ over.celebrate(host.getString(R.string.goal_done)) }, CELEBRATE_AFTER_MS)
    }

    // --- reading: today against the goal first, then the month ---

    private fun readingPage(into: LinearLayout, today: Stats.Day, goal: Int, week: List<Stats.Day>, before: List<Stats.Day>) {
        blow.card(into, 0, wirdRows(today, goal, week, before))
        val hijri = Stats.hijri(host)
        val thisMonth = Stats.monthDays(host)
        into.addView(monthHead(hijri))
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
        blow.card(into, 0, listOf(quiet(host.getString(R.string.stats_private))))
    }

    private fun wirdRows(today: Stats.Day, goal: Int, week: List<Stats.Day>, before: List<Stats.Day>): List<View> {
        val plan = Stats.goalPlan(host)
        val read = readToday(today, goal)
        val hero = hero(
            key = TODAY, count = read,
            say = { if (it == 0) none(res) else figures(it, res) },
            unit = if (read == 0) "" else res.getQuantityString(R.plurals.pages_unit, read),
            title = host.getString(R.string.stats_pages_today),
            line = todayLine(read, goal, R.string.stats_none_today),
            note = todayNote(read, goal)
        ).also {
            fillRing(it, TODAY, read.toFloat(), goal.toFloat())
            showTrend(it.findViewById(R.id.hero_trend), week.sumOf { d -> d.pages.size }, before.sumOf { d -> d.pages.size })
        }
        val rows = mutableListOf(
            hero,
            changeRow(khatmaNote(plan, goal).ifEmpty { plan.said(host) }) { picker.reading(plan) },
            row(host.getString(R.string.done_week), metOfWeek(
                want = { weekday -> if (plan.kind == Goal.Kind.KHATMA) 1 else plan.pagesOn(weekday) },
                done = { ago -> week[WEEK - 1 - ago].let { if (it.doneByHand) Int.MAX_VALUE else it.pages.size } }
            ))
        )
        // Read away from the app, from a printed mushaf: the switch counts today's goal as done
        if (goal > 0 && (today.doneByHand || today.pages.size < goal)) rows += row(host.getString(R.string.paper_read), "").apply {
            toggles(today.doneByHand) {
                Stats.setDoneByHand(host, !today.doneByHand)
                build()
                if (!today.doneByHand) celebrateOnce(Stats.day(host, Stats.today()), goal)
            }
        }
        return rows
    }

    // Done against the goal, counting on past it
    private fun todayLine(done: Int, goal: Int, nothingYet: Int): String = when {
        done == 0 -> host.getString(nothingYet)
        goal == 0 -> pagesSaid(done, res)
        done < goal -> host.getString(R.string.of_count, figures(done, res), pagesSaid(goal, res))
        done == goal -> host.getString(R.string.stats_goal_reached)
        else -> host.getString(R.string.goal_over, pagesSaid(done - goal, res))
    }

    private fun todayNote(done: Int, goal: Int): String = when {
        goal == 0 -> host.getString(R.string.goal_none_today)
        done < goal -> host.getString(R.string.goal_left, pagesSaid(goal - done, res))
        else -> ""
    }

    // Days of the last week that met their goal, out of the days that had one; a khatma's pages a day are
    // only known for today, so any reading counts toward it
    private fun metOfWeek(want: (weekday: Int) -> Int, done: (ago: Int) -> Int): String {
        val cal = Calendar.getInstance()
        var had = 0
        var met = 0
        for (ago in 0 until WEEK) {
            cal.timeInMillis = Stats.noonOf(Stats.today() - ago)
            val wanted = want(cal.get(Calendar.DAY_OF_WEEK))
            if (wanted > 0) {
                had++
                if (done(ago) >= wanted) met++
            }
        }
        if (had == 0) return none(res)
        if (met == 0) return host.getString(R.string.done_week_none)
        return host.getString(R.string.of_count, figures(met, res), res.getQuantityString(R.plurals.days_count, had, figures(had, res)))
    }

    // A khatma goal says what its daily pages are for
    private fun khatmaNote(plan: Goal, goal: Int): String =
        if (plan.kind != Goal.Kind.KHATMA || goal == 0) ""
        else host.getString(R.string.khatma_daily, pagesSaid(goal, res), dateSaid(host, plan.amount.toLong()))

    // --- memorising ---

    private fun hifzPage(into: LinearLayout) {
        val plan = Stats.hifzPlan(host)
        val kept = Hifz.pages(host)
        if (plan == null) blow.card(into, 0, listOf(action(host.getString(R.string.hifz_add)) { picker.hifz(null) }))
        else {
            val goal = Stats.hifzGoal(host)
            val learned = kept.count { it.value == Stats.today() }
            val hero = hero(
                key = HIFZ, count = learned,
                say = { if (it == 0) none(res) else figures(it, res) },
                unit = if (learned == 0) "" else res.getQuantityString(R.plurals.pages_unit, learned),
                title = host.getString(R.string.hifz_today),
                line = todayLine(learned, goal, R.string.hifz_none_today),
                note = todayNote(learned, goal)
            ).also { fillRing(it, HIFZ, learned.toFloat(), goal.toFloat()) }
            blow.card(into, 0, listOf(
                hero,
                changeRow(plan.said(host)) { picker.hifz(plan) },
                row(host.getString(R.string.done_week), metOfWeek(
                    want = { weekday -> plan.pagesOn(weekday) },
                    done = { ago -> kept.count { it.value == Stats.today() - ago } }
                ))
            ))
        }
        blow.card(into, 0, listOf(mineRow(kept.size)))
    }

    // What is memorised in all, a tap away from the list of surahs and pages
    private fun mineRow(known: Int): View =
        row(
            host.getString(R.string.hifz_mine),
            if (known == 0) host.getString(R.string.hifz_mine_add) else pagesSaid(known, res),
            if (known == 0) host.getString(R.string.hifz_mine_empty) else host.getString(R.string.hifz_mine_pages, shareSaid(known))
        ).apply { opens { host.startActivity(Intent(host, MemorizedActivity::class.java)) } }

    // A share of the whole mushaf; a few pages are less than one in a hundred, never nought
    private fun shareSaid(pages: Int): String {
        val pct = pages * 100 / Mushaf.PAGES
        return if (pct == 0) host.getString(R.string.percent_under_one) else host.getString(R.string.percent, figures(pct, res))
    }

    // The goal as written, with the way to change it
    private fun changeRow(said: String, change: () -> Unit): View =
        row(host.getString(R.string.goal_title), host.getString(R.string.goal_change), said).apply { opens(change) }

    // Which month the numbers below count, and a switch between the Hijri and Gregorian calendars
    private fun monthHead(hijri: Boolean): View =
        blow.inflate(R.layout.part_month_head, parent, false).apply {
            findViewById<TextView>(R.id.month_name).text = monthName(Stats.today(), hijri, res)
            for ((id, isHijri) in listOf(R.id.month_hijri to true, R.id.month_greg to false)) {
                val on = isHijri == hijri
                findViewById<TextView>(id).apply {
                    setBackgroundResource(if (on) R.drawable.seg_on else R.drawable.row_flat)
                    setTextColor(host.getColor(if (on) R.color.accent else R.color.text_mute))
                    isSelected = on
                    setOnClickListener {
                        if (!on) {
                            Stats.setHijri(host, isHijri)
                            build()
                        }
                    }
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
        if (today.pages.isNotEmpty()) rows += row(host.getString(R.string.stats_page_times_open), pagesSaid(today.pages.size, res)).apply {
            opens { host.startActivity(Intent(host, PageTimesActivity::class.java)) }
        }
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
        val plan = Stats.goalPlan(host)
        val rows = mutableListOf<View>()
        rows += hero(
            key = KHATMA, count = k.read * 100 / all,
            say = { if (it == 0) none(res) else host.getString(R.string.percent, figures(it, res)) },
            unit = "", title = "",
            line = if (k.read == 0) host.getString(R.string.stats_khatma_none)
                else host.getString(R.string.stats_khatma_read, pagesSaid(k.read, res), figures(all, res)),
            note = if (k.done > 0) res.getQuantityString(R.plurals.khatmas_before, k.done, figures(k.done, res)) else ""
        ).also { fillRing(it, KHATMA, k.read.toFloat(), all.toFloat()) }
        rows += finishRow(plan, all - k.read)
        rows += row(host.getString(R.string.stats_left), pagesSaid(all - k.read, res))
        rows += action(host.getString(R.string.stats_new_khatma)) {
            host.sheet(host.getString(R.string.stats_new_khatma_ask), listOf(Choice(host.getString(R.string.stats_new_khatma_do)))) {
                Stats.newKhatma(host)
                build()
            }
        }
        blow.card(into, 0, rows)
    }

    // When the khatma ends: the day set as a goal, or else the day the recent pace reaches; a tap sets the day
    private fun finishRow(plan: Goal, left: Int): View {
        val set = plan.kind == Goal.Kind.KHATMA
        val pace = Stats.pace(host)
        val (value, note) = when {
            set -> dateSaid(host, plan.amount.toLong()) to host.getString(R.string.khatma_per_day, pagesSaid(Stats.goal(host), res))
            pace > 0f -> dateSaid(host, Stats.today() + ceil(left / pace).toLong()) to host.getString(R.string.khatma_by_pace)
            else -> none(res) to host.getString(R.string.stats_finish_unknown)
        }
        return row(host.getString(if (set) R.string.khatma_goal else R.string.khatma_expected), value, note).apply {
            opens { picker.khatma(plan) }
        }
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
        const val READING = 0
        const val HIFZ_PAGE = 1
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
