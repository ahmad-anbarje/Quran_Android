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

        readingPage(columns[READING], today, goal, week)
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

    private fun readingPage(into: LinearLayout, today: Stats.Day, goal: Int, week: List<Stats.Day>) {
        blow.card(into, 0, wirdRows(today, goal))
        val thisMonth = Stats.monthDays(host)
        val monthPages = thisMonth.sumOf { it.pages.size }
        blow.card(into, R.string.stats_today, todayRows(today, thisMonth))
        into.addView(pair(
            Tile(R.drawable.ic_calendar, if (monthPages == 0) none(res) else pagesSaid(monthPages, res), host.getString(R.string.stats_pages_month)),
            Tile(R.drawable.ic_clock, spent(thisMonth.sumOf { it.readSec }, res), host.getString(R.string.stats_read_month))
        ))
        blow.card(into, host.getString(R.string.stats_last_days, figures(WEEK, res)), weekRows(week, goal))
    }

    private fun wirdRows(today: Stats.Day, goal: Int): List<View> {
        val plan = Stats.goalPlan(host)
        val read = readToday(today, goal)
        // A khatma's pages a day are only known for today, so any reading counts toward it on other days
        val rings = goalRings(TODAY, read, goal, R.string.stats_none_today,
            want = { _, weekday -> if (plan.kind == Goal.Kind.KHATMA) 1 else plan.pagesOn(weekday) },
            done = { day -> Stats.day(host, day).let { if (it.doneByHand) Int.MAX_VALUE else it.pages.size } })
        val rows = mutableListOf(
            rings,
            changeRow(khatmaNote(plan, goal).ifEmpty { plan.said(host) }) { picker.reading(plan) }
        )
        // The reader says today's goal is done, wherever it was read
        if (goal > 0 && today.pages.size < goal) rows += blow.switchRow(parent, host.getString(R.string.paper_read), today.doneByHand) { on ->
            Stats.setDoneByHand(host, on)
            // The knob finishes its slide before the numbers move
            parent.postDelayed({
                build()
                if (on) celebrateOnce(Stats.day(host, Stats.today()), goal)
            }, SWITCH_SETTLE_MS)
        }
        return rows
    }

    // Today, this week and this month side by side: today in pages, the others in days the goal was met,
    // out of the period's days that have one
    private fun goalRings(key: String, todayDone: Int, goal: Int, nothingYet: Int,
                          want: (day: Long, weekday: Int) -> Int, done: (day: Long) -> Int): View =
        blow.inflate(R.layout.part_rings, parent, false).apply {
            fillGoalRing(findViewById(R.id.ring_today), "$key.day", todayDone, goal, R.string.ring_today,
                unit = if (goal > 0) host.getString(R.string.ring_of, figures(goal, res))
                    else res.getQuantityString(R.plurals.pages_unit, todayDone))
            for ((id, span, label) in listOf(
                Triple(R.id.ring_week, weekSpan(), R.string.ring_week),
                Triple(R.id.ring_month, monthSpan(), R.string.ring_month)
            )) {
                val (met, had) = metDays(span, want, done)
                fillGoalRing(findViewById(id), "$key.$label", met, had, label,
                    unit = if (had > 0) host.getString(R.string.ring_of, figures(had, res)) else "")
            }
            findViewById<TextView>(R.id.rings_line).text = todayLine(todayDone, goal, nothingYet)
        }

    private fun fillGoalRing(ring: View, key: String, done: Int, total: Int, label: Int, unit: String,
                             say: (Int) -> String = { if (it == 0) none(res) else figures(it, res) }) {
        countFigure(ring.findViewById(R.id.ring_value), key, done, say)
        ring.findViewById<TextView>(R.id.ring_unit).apply { text = unit; visibility = if (unit.isEmpty()) View.GONE else View.VISIBLE }
        ring.findViewById<TextView>(R.id.ring_label).setText(label)
        fillRing(ring.findViewById(R.id.ring), key, done.toFloat(), total.toFloat())
    }

    // This week, Saturday to Friday as the app's week runs
    private fun weekSpan(): LongRange {
        val cal = Calendar.getInstance()
        cal.timeInMillis = Stats.noonOf(Stats.today())
        val start = Stats.today() - (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SATURDAY + WEEK) % WEEK
        return start until start + WEEK
    }

    // The whole of this month by the chosen calendar; it ends where the next one starts
    private fun monthSpan(): LongRange {
        val hijri = Stats.hijri(host)
        val start = monthStart(Stats.today(), hijri)
        return start until monthStart(start + LONGEST_MONTH, hijri)
    }

    // Days of [span] that have a goal, and how many of those up to today met it
    private fun metDays(span: LongRange, want: (day: Long, weekday: Int) -> Int, done: (day: Long) -> Int): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        var had = 0
        var met = 0
        for (day in span) {
            cal.timeInMillis = Stats.noonOf(day)
            val wanted = want(day, cal.get(Calendar.DAY_OF_WEEK))
            if (wanted > 0) {
                had++
                if (day <= Stats.today() && done(day) >= wanted) met++
            }
        }
        return met to had
    }

    // Today in a line: what is left, that the goal is reached, or by how much it was passed
    private fun todayLine(done: Int, goal: Int, nothingYet: Int): String = when {
        goal == 0 -> if (done == 0) host.getString(R.string.goal_none_today) else pagesSaid(done, res)
        done == 0 -> host.getString(nothingYet)
        done < goal -> host.getString(R.string.goal_left, pagesSaid(goal - done, res))
        done == goal -> host.getString(R.string.stats_goal_reached)
        else -> host.getString(R.string.goal_over, pagesSaid(done - goal, res))
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
            blow.card(into, 0, listOf(
                goalRings(HIFZ, learned, goal, R.string.hifz_none_today,
                    want = { _, weekday -> plan.pagesOn(weekday) },
                    done = { day -> kept.count { it.value == day } }),
                changeRow(plan.said(host)) { picker.hifz(plan) }
            ))
        }
        blow.card(into, 0, knownRows())
    }

    // What is memorised in all: its share of the Quran and its whole surahs as rings, then the way into the record
    private fun knownRows(): List<View> {
        val bySurah = Hifz.bySurah(host)
        val known = Hifz.pages(host).size
        val surahs = Surahs.list()
        val whole = surahs.count { Hifz.knownOf(it, bySurah) == it.to - it.from + 1 }
        val rings = blow.inflate(R.layout.part_rings, parent, false).apply {
            val share = findViewById<View>(R.id.ring_today)
            fillGoalRing(share, KNOWN, shareOf(known), 100, R.string.ring_known, unit = "") {
                if (it == 0) none(res) else host.getString(R.string.percent, figures(it, res))
            }
            fillGoalRing(findViewById(R.id.ring_week), KNOWN_WHOLE, whole, surahs.size, R.string.ring_whole,
                unit = host.getString(R.string.ring_of, figures(surahs.size, res)))
            findViewById<View>(R.id.ring_month).visibility = View.GONE
            findViewById<TextView>(R.id.rings_line).text = if (known == 0) host.getString(R.string.hifz_mine_empty)
                else host.getString(R.string.stats_khatma_read, pagesSaid(known, res), figures(Mushaf.PAGES, res))
        }
        val record = row(host.getString(R.string.hifz_mine), if (known == 0) host.getString(R.string.hifz_mine_add) else "")
            .apply { opens { host.startActivity(Intent(host, MemorizedActivity::class.java)) } }
        return listOf(rings, record)
    }

    // A share of the whole mushaf in hundredths; a page or two already shows, never as nought
    private fun shareOf(pages: Int): Int = if (pages == 0) 0 else maxOf(1, pages * 100 / Mushaf.PAGES)

    // The goal as written, with the way to change it
    private fun changeRow(said: String, change: () -> Unit): View =
        row(host.getString(R.string.goal_title), host.getString(R.string.goal_change), said).apply { opens(change) }

    // Today's reading: how long, how long a page takes, the pace over an hour, and every page's own time a tap away
    private fun todayRows(today: Stats.Day, thisMonth: List<Stats.Day>): List<View> {
        val rows = mutableListOf(
            row(host.getString(R.string.stats_read_time), spent(today.readSec, res)),
            row(host.getString(R.string.stats_page_avg),
                if (today.pages.isEmpty()) none(res) else spentExact(onPages(today) / today.pages.size, res)),
            perHourRow(today, thisMonth.sumOf { it.pages.size }, thisMonth.sumOf { onPages(it) })
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

    // Time on the pages that were read, leaving out pages only passed over
    private fun onPages(day: Stats.Day): Int = day.pages.sumOf { day.pageSec.getValue(it) }

    // The last seven days, each against its own goal; a khatma's pages a day are only known for today
    private fun weekRows(week: List<Stats.Day>, goal: Int): List<View> {
        val plan = Stats.goalPlan(host)
        val cal = Calendar.getInstance()
        val goals = week.indices.map { i ->
            val ago = week.size - 1 - i
            cal.timeInMillis = Stats.noonOf(Stats.today() - ago)
            when {
                ago == 0 -> goal
                plan.kind == Goal.Kind.KHATMA -> 0
                else -> plan.pagesOn(cal.get(Calendar.DAY_OF_WEEK))
            }
        }
        val counts = week.map { it.pages.size }
        val pad = res.getDimensionPixelSize(R.dimen.tile_pad)
        val chart = DayBars(host).apply {
            setPadding(pad, pad, pad, pad)
            show(counts, goals, ::dayName, { host.getString(R.string.ring_of, figures(it, res)) },
                grow = moving && motion == Motion.OPEN)
            contentDescription = host.getString(
                R.string.stats_chart_desc, figures(week.size, res), pagesSaid(counts.max(), res)
            )
        }
        return listOf(chart, row(host.getString(R.string.chart_total), pagesSaid(counts.sum(), res)))
    }

    // Today by name, the days before it by their weekday
    private fun dayName(ago: Int): String {
        if (ago == 0) return host.getString(R.string.chart_today)
        val cal = Calendar.getInstance()
        cal.timeInMillis = Stats.noonOf(Stats.today() - ago)
        return Goal.weekdayName(cal.get(Calendar.DAY_OF_WEEK), res)
    }

    // --- khatma ---

    private fun khatmaPage(into: LinearLayout) {
        val k = Stats.khatma(host)
        val all = Mushaf.PAGES
        val plan = Stats.goalPlan(host)
        val rows = mutableListOf<View>()
        rows += hero(
            key = KHATMA, count = shareOf(k.read),
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
    private fun fillRing(hero: View, key: String, done: Float, total: Float) =
        fillRing(hero.findViewById<RingView>(R.id.hero_ring), key, done, total)

    private fun fillRing(ring: RingView, key: String, done: Float, total: Float) {
        val share = if (total > 0f) (done / total).coerceIn(0f, 1f) else 0f
        // A first build, with nothing shown before it, simply shows the value
        val from = if (!moving) null else if (motion == Motion.OPEN) 0f else shares[key]
        ring.show(done, total, from)
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
        // Past the end of any month, Hijri or Gregorian, counted from its first day
        const val LONGEST_MONTH = 31
        const val TOP_HEARD = 3
        // Long enough for the cards to have come in first
        const val CELEBRATE_AFTER_MS = 450L
        const val SWITCH_SETTLE_MS = 250L
        const val TODAY = "today"
        const val HIFZ = "hifz"
        const val KNOWN = "known"
        const val KNOWN_WHOLE = "known.whole"
        const val KHATMA = "khatma"
    }
}
