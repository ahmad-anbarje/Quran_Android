package com.readqurantoday.quran

import android.content.res.Resources

/** Arabic-Eastern figures in Arabic locale, Latin digits elsewhere. */
fun figures(n: Int, resources: Resources): String = localDigits(n.toString(), resources)

/** [text] with its Latin digits written as the locale writes them, e.g. a system-formatted date. */
fun localDigits(text: String, resources: Resources): String {
    if (resources.configuration.locales[0].language != "ar") return text
    return text.map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
}

/** Time since, in the locale's figures and Arabic's count forms. */
fun ago(at: Long, resources: Resources): String {
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0L).toInt()
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> resources.getString(R.string.ago_now)
        hours < 1   -> resources.getQuantityString(R.plurals.ago_minutes, minutes, figures(minutes, resources))
        days < 1    -> resources.getQuantityString(R.plurals.ago_hours, hours, figures(hours, resources))
        else        -> resources.getQuantityString(R.plurals.ago_days, days, figures(days, resources))
    }
}

/** "12 pages", in the locale's figures and Arabic's count forms. */
fun pagesSaid(n: Int, resources: Resources): String =
    resources.getQuantityString(R.plurals.pages_count, n, figures(n, resources))

/** Time spent, to the minute: "24 minutes", or hours and minutes past an hour. */
fun spent(sec: Int, resources: Resources): String {
    val minutes = (sec + 30) / 60
    return when {
        minutes == 0 && sec > 0 -> resources.getString(R.string.time_under_minute)
        minutes < 60 -> resources.getQuantityString(R.plurals.minutes_count, minutes, figures(minutes, resources))
        else -> resources.getString(R.string.time_hm, figures(minutes / 60, resources), figures(minutes % 60, resources))
    }
}

/** Time to the second, for one page: "45 s", or minutes and seconds. */
fun spentExact(sec: Int, resources: Resources): String =
    if (sec < 60) resources.getString(R.string.time_s, figures(sec, resources))
    else resources.getString(R.string.time_ms, figures(sec / 60, resources), figures(sec % 60, resources))
