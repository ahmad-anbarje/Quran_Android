package com.readqurantoday.quran

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** A screen of titled cards under a head strip with the way back; subclasses fill [cards]. */
abstract class CardsActivity(private val title: Int) : LanguageActivity() {

    protected lateinit var cards: LinearLayout
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cards)
        findViewById<TextView>(R.id.cards_title).setText(title)
        findViewById<ImageView>(R.id.cards_back_icon).imageTintList =
            ColorStateList.valueOf(getColor(R.color.accent))
        findViewById<View>(R.id.cards_back).setOnClickListener { finish() }
        keepToColumn(R.id.cards)
        cards = findViewById(R.id.cards)
    }

    // Subclasses fill the cards in onCreate, so they are all there to come in by now
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (savedInstanceState == null) cards.riseChildren()
    }

    protected fun sayBars() {
        showBars(roof = groundOf(findViewById(R.id.cards_head)), floor = groundOf(findViewById(R.id.cards_root)))
    }

    /* Asked for and painted again with focus, as the index does: see showBars. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) sayBars()
    }
}
