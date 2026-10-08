package com.readqurantoday.quran

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.readqurantoday.quran.StatCards.Companion.WEEK
import com.readqurantoday.quran.StatCards.Tile
import kotlin.math.abs

/** The listening tab: time heard today and this week against the week before, and the surahs heard most. */
class ListeningTab(private val cards: StatCards) {

    private val host = cards.host
    private val res = cards.res

    fun fill(into: LinearLayout, today: Stats.Day, week: List<Stats.Day>, before: List<Stats.Day>) {
        val weekSec = week.sumOf { it.listenSec }
        into.addView(cards.pair(
            Tile(R.drawable.ic_headphones, spent(today.listenSec, res), host.getString(R.string.stats_listen_time)),
            Tile(R.drawable.ic_headphones, spent(weekSec, res), host.getString(R.string.stats_week, figures(WEEK, res))) {
                // Minutes, not seconds, so a few seconds either way is not called a change
                showTrend(it, (weekSec + 30) / 60, (before.sumOf { d -> d.listenSec } + 30) / 60)
            }
        ))
        mostHeard(week, into)?.let { cards.blow.card(into, host.getString(R.string.stats_most_heard, figures(WEEK, res)), it) }
    }

    private fun mostHeard(week: List<Stats.Day>, into: LinearLayout): List<View>? {
        val heard = HashMap<Int, Int>()
        week.forEach { d -> d.surahSec.forEach { (s, sec) -> heard[s] = (heard[s] ?: 0) + sec } }
        val top = heard.entries.sortedByDescending { it.value }.take(TOP_HEARD)
        return top.map { (surah, sec) -> heardRow(surah, sec, into) }.ifEmpty { null }
    }

    // A surah heading its own row is written as the mushaf writes it
    private fun heardRow(surah: Int, sec: Int, into: LinearLayout): View =
        cards.blow.inflate(R.layout.row_place, into, false).apply {
            isClickable = false
            findViewById<ImageView>(R.id.place_icon).apply {
                setImageResource(R.drawable.ic_headphones)
                imageTintList = ColorStateList.valueOf(host.getColor(R.color.accent))
            }
            fillSurahTitle(findViewById(R.id.place_title), surah, R.dimen.surah_title_row)
            findViewById<TextView>(R.id.place_detail).text = spent(sec, res)
        }

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
        val pct = if (then == 0) "" else host.getString(R.string.percent, figures(abs(now - then) * 100 / then, res))
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

    private companion object {
        const val TOP_HEARD = 3
    }
}
