package com.readqurantoday.quran

import android.content.Context
import androidx.core.content.edit

/**
 * What the reader has memorised, by mushaf page, each with the day it was learned. A page entered as known from
 * before carries [BEFORE] instead, so entering old memorisation never counts toward a day's goal.
 */
object Hifz {

    const val BEFORE = -1L

    private const val PREFS = "hifz"
    private const val PAGES = "pages"

    private fun store(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Memorised pages, each with the day it was learned or [BEFORE]. */
    fun pages(ctx: Context): Map<Int, Long> {
        val kept = store(ctx).getString(PAGES, "").orEmpty()
        return kept.split(',').mapNotNull { entry ->
            val p = entry.split(':')
            val page = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val day = p.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            page to day
        }.toMap()
    }

    /** Pages newly learned on [day]. */
    fun learnedOn(ctx: Context, day: Long): Int = pages(ctx).count { it.value == day }

    /** Mark [pages] as learned today, and unmark any of [unmarked]; pages already kept keep their day. */
    fun setPages(ctx: Context, marked: Collection<Int>, unmarked: Collection<Int>) {
        val now = pages(ctx).toMutableMap()
        marked.forEach { now.putIfAbsent(it, Stats.today()) }
        unmarked.forEach { now.remove(it) }
        save(ctx, now)
    }

    /** A whole surah known from before, or forgotten: its pages marked [BEFORE], or cleared. */
    fun setSurah(ctx: Context, surah: Surahs.Surah, known: Boolean) {
        val now = pages(ctx).toMutableMap()
        for (page in surah.from..surah.to) if (known) now.putIfAbsent(page, BEFORE) else now.remove(page)
        save(ctx, now)
    }

    /** How many of [surah]'s pages are memorised. */
    fun knownOf(ctx: Context, surah: Surahs.Surah, kept: Map<Int, Long> = pages(ctx)): Int =
        (surah.from..surah.to).count { it in kept }

    private fun save(ctx: Context, pages: Map<Int, Long>) = store(ctx).edit {
        putString(PAGES, pages.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" })
    }
}
