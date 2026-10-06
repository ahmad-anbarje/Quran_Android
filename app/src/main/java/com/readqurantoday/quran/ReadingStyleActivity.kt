package com.readqurantoday.quran

import android.os.Bundle

/** How a page looks: the preview line, and the colour and weight of what can be styled. */
class ReadingStyleActivity : CardsActivity(R.string.set_group_reading) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsPane(this, cards).readingStyle()
        sayBars()
    }
}
