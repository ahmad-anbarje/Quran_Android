package com.readqurantoday.quran

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.util.TimeZone

/** Reading and listening, kept on this phone only: seconds per page and per surah each day, the khatma, the goal. */
object Stats {

    /** A page counts as read once it has been on screen this long in a day. */
    const val READ_FROM_SEC = 10

    /** One stay on a page counts for at most this long; a page left open is not being read. */
    const val STAY_CAP_SEC = 300

    /** Days of history the statistics screen draws. */
    const val CHART_DAYS = 30

    // Pace looks back two weeks: long enough to steady, short enough to follow a change of habit
    private const val PACE_DAYS = 14

    // Fewer days than this give a pace too unsteady to name a date by
    private const val PACE_FROM_DAYS = 3

    private const val KEEP_DAYS = 60
    private const val DAY_MS = 86_400_000L

    private const val PREFS = "stats"
    private const val DAY = "d."
    private const val KHATMA = "khatma"
    private const val KHATMA_FROM = "khatma_from"
    private const val KHATMAS = "khatmas"
    private const val GOAL = "goal"
    private const val SINCE = "since"

    /** One day: seconds on each page, and seconds of recitation heard from each surah. */
    data class Day(val pageSec: Map<Int, Int>, val surahSec: Map<Int, Int>) {
        val pages: List<Int> get() = pageSec.filterValues { it >= READ_FROM_SEC }.keys.sorted()
        val readSec: Int get() = pageSec.values.sum()
        val listenSec: Int get() = surahSec.values.sum()
    }

    /** The khatma under way: distinct pages read since [fromDay], and how many were finished before it. */
    data class Khatma(val read: Int, val fromDay: Long, val done: Int)

    private fun store(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Days since 1970 on this phone's clock, so a day turns at local midnight. */
    fun today(): Long {
        val now = System.currentTimeMillis()
        return Math.floorDiv(now + TimeZone.getDefault().getOffset(now), DAY_MS)
    }

    /** Local midday of [day] in epoch ms, for formatting a date. */
    fun noonOf(day: Long): Long {
        val noon = day * DAY_MS + DAY_MS / 2
        return noon - TimeZone.getDefault().getOffset(noon)
    }

    // --- recording ---

    fun addRead(ctx: Context, page: Int, sec: Int) {
        if (page !in 1..Mushaf.PAGES || sec <= 0) return
        val day = day(ctx, today())
        val total = (day.pageSec[page] ?: 0) + sec
        save(ctx, today(), day.copy(pageSec = day.pageSec + (page to total)))
        if (total >= READ_FROM_SEC) markRead(ctx, page)
    }

    fun addHeard(ctx: Context, surah: Int, sec: Int) {
        if (surah !in 1..114 || sec <= 0) return
        val day = day(ctx, today())
        save(ctx, today(), day.copy(surahSec = day.surahSec + (surah to (day.surahSec[surah] ?: 0) + sec)))
    }

    // A finished khatma is counted and the next one starts at once
    private fun markRead(ctx: Context, page: Int) {
        val prefs = store(ctx)
        val read = khatmaPages(ctx)
        if (read[page - 1] == '1') return
        read[page - 1] = '1'
        prefs.edit {
            if (!prefs.contains(KHATMA_FROM)) putLong(KHATMA_FROM, today())
            if (read.all { it == '1' }) {
                putInt(KHATMAS, prefs.getInt(KHATMAS, 0) + 1)
                putString(KHATMA, "")
                putLong(KHATMA_FROM, today())
            } else {
                putString(KHATMA, String(read))
            }
        }
    }

    // --- reading back ---

    fun day(ctx: Context, day: Long): Day {
        val o = store(ctx).getString(DAY + day, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return Day(emptyMap(), emptyMap())
        return Day(counts(o.optJSONObject("r")), counts(o.optJSONObject("l")))
    }

    /** The last [n] days, oldest first, today last. */
    fun lastDays(ctx: Context, n: Int): List<Day> {
        val end = today()
        return (end - n + 1..end).map { day(ctx, it) }
    }

    fun khatma(ctx: Context): Khatma {
        val prefs = store(ctx)
        return Khatma(
            read = khatmaPages(ctx).count { it == '1' },
            fromDay = prefs.getLong(KHATMA_FROM, today()),
            done = prefs.getInt(KHATMAS, 0)
        )
    }

    /** Days with statistics kept, today included: only these count toward an average. */
    fun daysKept(ctx: Context): Int =
        (today() - store(ctx).getLong(SINCE, today()) + 1).coerceAtLeast(1L).toInt()

    /** Pages read a day, averaged over the last [n] days or as many as have been kept. */
    fun average(ctx: Context, n: Int): Float {
        val span = minOf(n, daysKept(ctx))
        return lastDays(ctx, span).sumOf { it.pages.size } / span.toFloat()
    }

    /** Pages a day lately, or 0 until there are enough days to tell. */
    fun pace(ctx: Context): Float = if (daysKept(ctx) < PACE_FROM_DAYS) 0f else average(ctx, PACE_DAYS)

    /** Pages a day the reader aims for; 0 is no goal. */
    fun goal(ctx: Context) = store(ctx).getInt(GOAL, 0)

    fun setGoal(ctx: Context, pages: Int) = store(ctx).edit { putInt(GOAL, pages) }

    /** Start the khatma over; finished ones stay counted. */
    fun newKhatma(ctx: Context) = store(ctx).edit {
        putString(KHATMA, "")
        putLong(KHATMA_FROM, today())
    }

    // --- storage ---

    private fun khatmaPages(ctx: Context): CharArray {
        val kept = store(ctx).getString(KHATMA, "").orEmpty()
        return CharArray(Mushaf.PAGES) { kept.getOrNull(it) ?: '0' }
    }

    private fun counts(o: JSONObject?): Map<Int, Int> {
        if (o == null) return emptyMap()
        return o.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to o.optInt(k) } }.toMap()
    }

    private fun save(ctx: Context, day: Long, record: Day) {
        val o = JSONObject()
            .put("r", JSONObject(record.pageSec.mapKeys { it.key.toString() }))
            .put("l", JSONObject(record.surahSec.mapKeys { it.key.toString() }))
        val prefs = store(ctx)
        prefs.edit {
            if (!prefs.contains(SINCE)) putLong(SINCE, day)
            putString(DAY + day, o.toString())
            // Only a new day can push one out of the window, so old ones are dropped then
            if (!prefs.contains(DAY + day)) {
                prefs.all.keys.filter { it.startsWith(DAY) }
                    .filter { (it.removePrefix(DAY).toLongOrNull() ?: day) < day - KEEP_DAYS }
                    .forEach { remove(it) }
            }
        }
    }
}
