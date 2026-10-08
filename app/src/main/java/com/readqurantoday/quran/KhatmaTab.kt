package com.readqurantoday.quran

import android.view.View
import android.widget.LinearLayout
import kotlin.math.ceil

/** The khatma tab: how far through it is, when it ends, and a way to start again. [restarted] hears a new khatma begun. */
class KhatmaTab(private val cards: StatCards, private val restarted: () -> Unit) {

    private val host = cards.host
    private val res = cards.res

    fun fill(into: LinearLayout) {
        val k = Stats.khatma(host)
        val all = Mushaf.PAGES
        val gauge = cards.hero(
            key = KEY, count = cards.shareOf(k.read), say = cards::percentSaid, unit = "", title = "",
            line = if (k.read == 0) host.getString(R.string.stats_khatma_none)
                else host.getString(R.string.khatma_read_left, pagesSaid(k.read, res), figures(all - k.read, res)),
            note = if (k.done > 0) res.getQuantityString(R.plurals.khatmas_before, k.done, figures(k.done, res)) else ""
        ).also { cards.fillHero(it, KEY, k.read.toFloat(), all.toFloat()) }
        val restart = cards.action(host.getString(R.string.stats_new_khatma)) {
            host.sheet(host.getString(R.string.stats_new_khatma_ask), listOf(Choice(host.getString(R.string.stats_new_khatma_do)))) {
                Stats.newKhatma(host)
                restarted()
            }
        }
        cards.blow.card(into, 0, listOf(gauge, finishRow(Stats.goalPlan(host), all - k.read), restart))
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
        return cards.row(host.getString(if (set) R.string.khatma_goal else R.string.khatma_expected), value, note).apply {
            opens { GoalActivity.open(host, khatma = true) }
        }
    }

    private companion object {
        const val KEY = "khatma"
    }
}
