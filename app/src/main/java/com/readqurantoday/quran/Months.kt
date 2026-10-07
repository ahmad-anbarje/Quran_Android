package com.readqurantoday.quran

import android.content.Context
import android.content.res.Resources
import android.text.format.DateUtils
import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.GregorianCalendar
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.util.Date

// Months by the Hijri (Umm al-Qura, as the Saudi calendar counts it) or the Gregorian calendar

private fun calendar(hijri: Boolean, locale: ULocale): Calendar =
    if (hijri) IslamicCalendar(TimeZone.getDefault(), locale).apply {
        calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
    } else GregorianCalendar(TimeZone.getDefault(), locale)

/** The first day, in days since 1970, of the month [day] falls in. */
fun monthStart(day: Long, hijri: Boolean): Long {
    val cal = calendar(hijri, ULocale.ROOT)
    cal.timeInMillis = Stats.noonOf(day)
    return day - (cal.get(Calendar.DAY_OF_MONTH) - 1)
}

/** A day as the phone writes a date, "12 October", with the year once it is far off; in the app's figures. */
fun dateSaid(ctx: Context, day: Long): String {
    val far = day - Stats.today() > 300
    val flags = DateUtils.FORMAT_SHOW_DATE or if (far) DateUtils.FORMAT_SHOW_YEAR else DateUtils.FORMAT_NO_YEAR
    return localDigits(DateUtils.formatDateTime(ctx, Stats.noonOf(day), flags), ctx.resources)
}

/** "Rabi al-Akhir 1448" or "October 2026", in the app's language and figures. */
fun monthName(day: Long, hijri: Boolean, resources: Resources): String {
    val locale = ULocale.forLocale(resources.configuration.locales[0])
    // A skeleton, not a pattern, so each language orders month and year its own way
    val format = DateFormat.getInstanceForSkeleton(calendar(hijri, locale), "MMMMy", locale)
    return localDigits(format.format(Date(Stats.noonOf(day))), resources)
}
