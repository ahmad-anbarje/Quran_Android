package com.readqurantoday.quran

import android.content.Context
import android.os.SystemClock

private fun nowSec() = SystemClock.elapsedRealtime() / 1000L

/** Times the page in view while the reader is in front; each stay is credited when it ends. */
class PageClock(context: Context) {

    private val app = context.applicationContext
    private var page = 0
    private var since = 0L
    private var running = false

    fun show(page: Int) {
        credit()
        this.page = page
    }

    fun resume() {
        running = true
        since = nowSec()
    }

    fun pause() {
        credit()
        running = false
    }

    private fun credit() {
        val now = nowSec()
        if (running && page > 0) Stats.addRead(app, page, (now - since).coerceAtMost(Stats.STAY_CAP_SEC.toLong()).toInt())
        since = now
    }
}

/** Times recitation while it sounds, credited to its surah when it stops or moves on. */
object ListenClock {

    private var surah = 0
    private var since = 0L

    fun heard(context: Context, surah: Int, sounding: Boolean) {
        val now = nowSec()
        if (this.surah > 0) Stats.addHeard(context.applicationContext, this.surah, (now - since).toInt())
        this.surah = if (sounding) surah else 0
        since = now
    }
}
