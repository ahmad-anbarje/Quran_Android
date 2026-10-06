package com.readqurantoday.quran

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.random.Random

/**
 * Debug builds only: fills the statistics with a sample reader, after keeping the real numbers aside.
 *
 *     adb shell am broadcast -n com.readqurantoday.quran.dev/com.readqurantoday.quran.SampleStats --es as steady
 *
 * as = new | steady | heavy, then restore to put the real numbers back exactly.
 */
class SampleStats : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val stats = context.getSharedPreferences(Stats.PREFS, Context.MODE_PRIVATE)
        val saved = context.getSharedPreferences(SAVED, Context.MODE_PRIVATE)
        when (intent.getStringExtra("as")) {
            "restore" -> {
                if (saved.getBoolean(KEPT, false)) {
                    copy(saved, stats)
                    stats.edit(commit = true) { remove(KEPT) }
                    saved.edit(commit = true) { clear() }
                }
            }
            "new" -> keepThen(stats, saved) { }
            "steady" -> keepThen(stats, saved) { steady(context) }
            "heavy" -> keepThen(stats, saved) { heavy(context) }
        }
    }

    // The real numbers are kept once, before the first sample, so a second sample never overwrites them
    private fun keepThen(stats: SharedPreferences, saved: SharedPreferences, fill: () -> Unit) {
        if (!saved.getBoolean(KEPT, false)) {
            copy(stats, saved)
            saved.edit(commit = true) { putBoolean(KEPT, true) }
        }
        stats.edit(commit = true) { clear() }
        fill()
    }

    // A reader of a few pages most days, some listening, part way through a second khatma
    private fun steady(ctx: Context) {
        val rnd = Random(7)
        val today = Stats.today()
        Stats.writeSince(ctx, today - 40)
        for (day in today - 40 until today) {
            if (rnd.nextFloat() < 0.3f) continue
            val start = rnd.nextInt(1, 580)
            val pages = (start until start + rnd.nextInt(5, 26)).associateWith { rnd.nextInt(35, 71) }
            val heard = if (rnd.nextBoolean()) mapOf(rnd.nextInt(1, 115) to rnd.nextInt(300, 1500)) else emptyMap()
            Stats.writeDay(ctx, day, Stats.Day(pages, heard))
        }
        Stats.writeDay(ctx, today, Stats.Day((300..311).associateWith { rnd.nextInt(40, 61) }, mapOf(18 to 640)))
        Stats.setGoal(ctx, 20)
        Stats.writeKhatma(ctx, (1..320).toSet(), today - 25, done = 1)
    }

    // Ten juz a day: two hundred pages and more, quickly, with the goal long passed
    private fun heavy(ctx: Context) {
        val rnd = Random(11)
        val today = Stats.today()
        Stats.writeSince(ctx, today - 30)
        for (day in today - 30 until today) {
            val start = rnd.nextInt(1, 380)
            val pages = (start until start + rnd.nextInt(100, 221)).associateWith { rnd.nextInt(25, 46) }
            Stats.writeDay(ctx, day, Stats.Day(pages, mapOf(rnd.nextInt(1, 115) to rnd.nextInt(1800, 5400))))
        }
        Stats.writeDay(ctx, today, Stats.Day((1..220).associateWith { rnd.nextInt(20, 91) }, mapOf(2 to 3700, 36 to 900)))
        Stats.setGoal(ctx, 40)
        Stats.writeKhatma(ctx, (1..554).toSet(), today - 3, done = 3)
    }

    // Written at once, not in the background: the real numbers must be safe before the sample replaces them
    private fun copy(from: SharedPreferences, to: SharedPreferences) = to.edit(commit = true) {
        clear()
        for ((key, value) in from.all) when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Boolean -> putBoolean(key, value)
            is Float -> putFloat(key, value)
        }
    }

    private companion object {
        const val SAVED = "stats_real"
        const val KEPT = "kept"
    }
}
