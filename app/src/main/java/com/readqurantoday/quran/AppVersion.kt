package com.readqurantoday.quran

import android.content.Context

/** The installed version name, as set by appVersion in the build. */
fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (_: Exception) {
    ""
}
