package com.readqurantoday.quran

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** What the app is, where developers find its code, and whose work it is built on. */
class AboutActivity : CardsActivity(R.string.set_about) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val into = cards
        val intro = layoutInflater.inflate(R.layout.part_about_intro, into, false)
        intro.findViewById<TextView>(R.id.about_version).text = getString(R.string.about_version, appVersion(this))
        layoutInflater.card(into, 0, listOf(intro))
        layoutInflater.card(into, R.string.about_links, listOf(
            link(into, R.string.set_website, R.string.site_host, SITE_URL),
            link(into, R.string.about_source, R.string.source_host, SOURCE_URL),
            link(into, R.string.about_docker, R.string.docker_host, DOCKER_URL)
        ))
        val credits = layoutInflater.inflate(R.layout.row_setting_note, into, false) as TextView
        credits.setText(R.string.credits_text)
        layoutInflater.card(into, R.string.set_credits, listOf(credits))
        sayBars()
    }

    // The address is the value, so the row reads as where it goes
    private fun link(into: LinearLayout, label: Int, shown: Int, url: String): View =
        layoutInflater.inflate(R.layout.row_setting, into, false).also {
            it.findViewById<TextView>(R.id.set_label).setText(label)
            it.findViewById<TextView>(R.id.set_value).setText(shown)
            it.setOnClickListener { openLink(url) }
        }
}
