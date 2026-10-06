package com.readqurantoday.quran

import android.app.Activity
import android.view.View

/** Pads these views so their content keeps to a readable column, centred on screens wider than it. */
fun Activity.keepToColumn(vararg ids: Int) {
    val screen = resources.configuration.screenWidthDp * resources.displayMetrics.density
    val spare = ((screen - resources.getDimension(R.dimen.content_max_width)) / 2f).toInt()
    if (spare <= 0) return
    for (id in ids) findViewById<View>(id).apply {
        setPaddingRelative(paddingStart + spare, paddingTop, paddingEnd + spare, paddingBottom)
    }
}
