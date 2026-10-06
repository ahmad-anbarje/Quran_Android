package com.readqurantoday.quran

import android.view.View
import android.widget.LinearLayout

/** Shows a part_progress track filled to [done] of [total]. */
fun View.fillTrack(done: Float, total: Float) {
    visibility = View.VISIBLE
    val filled = done.coerceIn(0f, total.coerceAtLeast(0f))
    weigh(findViewById(R.id.track_fill), filled)
    // An empty total still needs some weight, or the track lays out at nothing
    weigh(findViewById(R.id.track_rest), if (total > 0f) total - filled else 1f)
}

private fun weigh(v: View, weight: Float) {
    v.layoutParams = (v.layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
}
