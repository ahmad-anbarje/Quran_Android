package com.readqurantoday.quran

import android.app.Activity

/** The sheets that choose a goal: the daily reading or khatma goal, and the memorisation goal beside it. */
class GoalPicker(private val host: Activity, private val changed: () -> Unit) {

    private val res = host.resources

    private fun every(kind: Goal.Kind, amount: Int, surahs: List<Int> = emptyList()) =
        Goal(kind, amount, surahs, Goal.ALL_DAYS, kahf = false)

    // --- reading ---

    /** Ready-made reading goals, the default first; anything else is built step by step under «custom». */
    fun reading(current: Goal) {
        val presets = listOf(
            Goal.DEFAULT to host.getString(R.string.stats_goal_month),
            every(Goal.Kind.PAGES, 5) to "",
            every(Goal.Kind.PAGES, 10) to "",
            every(Goal.Kind.JUZ, 2) to host.getString(R.string.stats_goal_half_month),
            every(Goal.Kind.SURAHS, 0, listOf(2, 3)) to "",
            khatmaIn(KHATMA_MONTH) to host.getString(R.string.goal_khatma_note)
        )
        val choices = presets.map { (g, note) -> Choice(g.said(host), note, on = g == current) } +
            Choice(host.getString(R.string.goal_custom), on = presets.none { it.first == current })
        host.sheet(host.getString(R.string.goal_title), choices) { i ->
            if (i < presets.size) keepReading(presets[i].first) else customReading(current)
        }
    }

    // A khatma that ends [days] from today, today counted
    private fun khatmaIn(days: Int) = every(Goal.Kind.KHATMA, (Stats.today() + days - 1).toInt())

    // Custom: what to read, then how much, then on which days; a khatma needs only how many days
    private fun customReading(current: Goal) {
        host.sheet(host.getString(R.string.goal_kind_ask), listOf(
            Choice(host.getString(R.string.goal_kind_pages)),
            Choice(host.getString(R.string.goal_kind_juz)),
            Choice(host.getString(R.string.goal_kind_surah)),
            Choice(host.getString(R.string.goal_kind_khatma))
        )) { i ->
            when (i) {
                0 -> number(R.string.goal_custom_ask, if (current.kind == Goal.Kind.PAGES) current.amount else 10, 1..Mushaf.PAGES) {
                    pickDays(current.copy(kind = Goal.Kind.PAGES, amount = it, surahs = emptyList()), kahf = true, ::keepReading)
                }
                1 -> number(R.string.goal_juz_ask, if (current.kind == Goal.Kind.JUZ) current.amount else 1, 1..30) {
                    pickDays(current.copy(kind = Goal.Kind.JUZ, amount = it, surahs = emptyList()), kahf = true, ::keepReading)
                }
                2 -> host.sheet(host.getString(R.string.goal_surah_ask), Surahs.list().map { Choice(it.name) }) { s ->
                    pickDays(current.copy(kind = Goal.Kind.SURAHS, amount = 0, surahs = listOf(Surahs.list()[s].id)), kahf = true, ::keepReading)
                }
                else -> number(R.string.goal_khatma_ask, KHATMA_MONTH, 1..KHATMA_LONGEST) { keepReading(khatmaIn(it)) }
            }
        }
    }

    private fun keepReading(goal: Goal) {
        Stats.setGoalPlan(host, goal)
        changed()
    }

    // --- memorisation ---

    /** Memorisation goals: a page or two a day, or pages on chosen days; and a way to stop. */
    fun hifz(current: Goal?) {
        val presets = listOf(every(Goal.Kind.PAGES, 1), every(Goal.Kind.PAGES, 2))
        val choices = presets.map { Choice(it.said(host), on = it == current) } +
            Choice(host.getString(R.string.goal_custom), on = current != null && current !in presets) +
            listOfNotNull(current?.let { Choice(host.getString(R.string.hifz_stop)) })
        host.sheet(host.getString(R.string.hifz_goal_title), choices) { i ->
            when {
                i < presets.size -> keepHifz(presets[i])
                i == presets.size -> number(R.string.hifz_custom_ask, current?.amount ?: 1, 1..Mushaf.PAGES) {
                    pickDays(every(Goal.Kind.PAGES, it).copy(days = current?.days ?: Goal.ALL_DAYS), kahf = false, ::keepHifz)
                }
                else -> keepHifz(null)
            }
        }
    }

    private fun keepHifz(goal: Goal?) {
        Stats.setHifzPlan(host, goal)
        changed()
    }

    // --- steps ---

    private fun number(ask: Int, current: Int, range: IntRange, done: (Int) -> Unit) =
        host.askNumber(host.getString(ask), host.getString(R.string.goal_next), current, range, done)

    // The days of the week, Saturday first, and for reading Al-Kahf on Friday as one more choice
    private fun pickDays(goal: Goal, kahf: Boolean, keep: (Goal) -> Unit) {
        val week = Goal.WEEK.map { Goal.weekdayName(it, res) }
        val labels = if (kahf) week + host.getString(R.string.goal_add_kahf) else week
        val chosen = BooleanArray(labels.size) { i ->
            if (i < Goal.WEEK.size) goal.days and Goal.bit(Goal.WEEK[i]) != 0 else goal.kahf
        }
        host.pickMany(host.getString(R.string.goal_days_ask), labels, chosen, host.getString(R.string.goal_keep)) { now ->
            val days = Goal.WEEK.indices.filter { now[it] }.fold(0) { mask, i -> mask or Goal.bit(Goal.WEEK[i]) }
            val withKahf = kahf && now.last()
            // A goal on no day at all is no goal; the old one stays
            if (days != 0 || withKahf) keep(goal.copy(days = days, kahf = withKahf))
        }
    }

    private companion object {
        const val KHATMA_MONTH = 30
        const val KHATMA_LONGEST = 365
    }
}
