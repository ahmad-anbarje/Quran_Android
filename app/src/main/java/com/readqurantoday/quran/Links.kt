package com.readqurantoday.quran

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri

const val SITE_URL = "https://readqurantoday.com/"
const val PRIVACY_URL = "https://readqurantoday.com/privacy/"
const val SOURCE_URL = "https://github.com/Ahmadooof/Quran_Android"
const val DOCKER_URL = "https://hub.docker.com/repository/docker/ahmadooof/quran/general"

// With no browser on the phone, the address is shown so it can still be typed elsewhere
fun Activity.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        notice(url)
    }
}
