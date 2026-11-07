package com.readqurantoday.quran

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView

/**
 * What an ayah means, on a sheet over the page: its translation or the Tafsir al-Muyassar, one ayah at a time with
 * steps either side, as the website's meaning window. Moved on by the recitation while it is open; [closed] hears it go.
 */
class MeaningSheet(private val host: Activity, private val closed: () -> Unit) {

    private val dialog = Dialog(host, R.style.SheetDialog)
    private val view = host.layoutInflater.inflate(R.layout.part_meaning, null)
    private val title = view.findViewById<TextView>(R.id.meaning_title)
    private val tabs = view.findViewById<View>(R.id.meaning_tabs)
    private val translationTab = tabs.findViewById<TextView>(R.id.seg_first)
    private val tafsirTab = tabs.findViewById<TextView>(R.id.seg_second)
    private val pick = view.findViewById<View>(R.id.meaning_pick)
    private val pickLabel = view.findViewById<TextView>(R.id.meaning_pick_label)
    private val body = view.findViewById<ScrollView>(R.id.meaning_body)
    private val ayahText = view.findViewById<TextView>(R.id.meaning_ayah)
    private val text = view.findViewById<TextView>(R.id.meaning_text)
    private val by = view.findViewById<TextView>(R.id.meaning_by)
    private val face = translationTab.typeface
    private val arabic = Settings.language(host) == "ar"

    private var surah = 1
    private var ayah = 1
    private var offered: List<Meanings.Translation> = Meanings.list
    // Bumped per request, so a slow answer for an ayah left behind is not shown
    private var asked = 0

    val open get() = dialog.isShowing

    init {
        translationTab.setText(R.string.meaning_translation)
        tafsirTab.setText(R.string.meaning_tafsir)
        translationTab.setOnClickListener { choose(tafsir = false) }
        tafsirTab.setOnClickListener { choose(tafsir = true) }
        val mute = ColorStateList.valueOf(host.getColor(R.color.chrome_ink))
        for (id in intArrayOf(R.id.meaning_prev, R.id.meaning_next, R.id.meaning_pick_icon)) {
            view.findViewById<ImageView>(id).imageTintList = mute
        }
        view.findViewById<View>(R.id.meaning_prev).setOnClickListener { step(-1) }
        view.findViewById<View>(R.id.meaning_next).setOnClickListener { step(1) }
        pick.setOnClickListener {
            host.pickTranslation(offered, Meanings.chosen(host, offered).id) { id ->
                Settings.setTranslation(host, id)
                render()
            }
        }
        by.setOnClickListener { host.openLink(Meanings.sourceUrl(shownText(), surah, arabic)) }
        // One height for every ayah, so the sheet holds still as they change
        body.layoutParams.height = (host.resources.displayMetrics.heightPixels * BODY_TALL).toInt()
        dialog.setOnDismissListener { closed() }
    }

    /** Shows ayah [ayah] of [surah], raising the sheet the first time. */
    fun show(surah: Int, ayah: Int) {
        this.surah = surah
        this.ayah = ayah
        render()
        if (!dialog.isShowing) {
            host.raise(dialog, view)
            Meanings.catalogue(host) { list ->
                offered = list
                if (dialog.isShowing) render()
            }
        }
    }

    fun close() = dialog.dismiss()

    private fun choose(tafsir: Boolean) {
        Settings.setMeaningTafsir(host, tafsir)
        render()
    }

    // To the ayah either side, on into the next surah or back into the one before
    private fun step(by: Int) {
        val ayahs = Surahs.of(surah)?.ayahs ?: return
        when {
            ayah + by in 1..ayahs -> ayah += by
            by > 0 && surah < SURAHS -> { surah++; ayah = 1 }
            by < 0 && surah > 1 -> { surah--; ayah = Surahs.of(surah)?.ayahs ?: 1 }
            else -> return
        }
        render()
    }

    private fun shownText(): Meanings.Text =
        if (Settings.meaningTafsir(host)) Meanings.tafsir else Meanings.translation(Meanings.chosen(host, offered))

    private fun render() {
        val tafsir = Settings.meaningTafsir(host)
        translationTab.markSegment(!tafsir, face)
        tafsirTab.markSegment(tafsir, face)
        val s = Surahs.of(surah)
        val name = s?.let { if (arabic) it.name else it.english }.orEmpty()
        title.text = host.getString(if (tafsir) R.string.meaning_of_tafsir else R.string.meaning_of_translation,
            figures(ayah, host.resources), name)
        ayahText.text = printed(surah, ayah)

        val chosen = Meanings.chosen(host, offered)
        pick.visibility = if (tafsir) View.GONE else View.VISIBLE
        pickLabel.text = listOf(languageOf(chosen), nameOf(chosen)).joinToString(" · ")
        by.text = credit(tafsir, chosen)

        val shown = shownText()
        text.textDirection = if (shown.rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        text.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        text.setTextColor(host.getColor(R.color.text_mute))
        text.setText(R.string.loading)
        val want = ++asked
        Meanings.ayah(host, shown, surah, ayah) { got ->
            if (want != asked) return@ayah
            text.setTextColor(host.getColor(if (got != null) R.color.text else R.color.text_mute))
            text.text = got ?: host.getString(R.string.meaning_unreached)
            body.scrollTo(0, 0)
        }
    }

    // The ayah in the mushaf's own glyphs, word by word from the pages it is printed on, its number kept with its last word
    private fun printed(surah: Int, ayah: Int): CharSequence = SpannableStringBuilder().apply {
        val from = Ayat.pageOf(surah, ayah).takeIf { it > 0 } ?: return@apply
        val book = Mushaf.plain
        for (page in from..minOf(from + 1, Mushaf.PAGES)) {
            val marks = book.marksOn(page)
            for (glyphs in book.ayahOn(page, surah, ayah)) {
                if (isNotEmpty()) append(if (marks.contains(glyphs)) NO_BREAK else " ")
                val at = length
                append(OBJECT)
                setSpan(QuranWordSpan(host.applicationContext, EndPages.Word(page, glyphs), AYAH_SCALE), at, length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    // Who the text is from, and its source, which a tap opens
    private fun credit(tafsir: Boolean, t: Meanings.Translation): CharSequence {
        val who = if (tafsir) host.getString(R.string.meaning_muyassar)
        else listOfNotNull(nameOf(t), host.getString(R.string.meaning_complex).takeIf { t.complex && nameOf(t) != it }, languageOf(t))
            .joinToString(" · ")
        val source = host.getString(if (!tafsir && t.source == "quranenc") R.string.source_quranenc else R.string.source_tanzil)
        return SpannableStringBuilder("$who · ").apply {
            append(host.getString(R.string.meaning_source, ""))
            val at = length
            append(source)
            setSpan(UnderlineSpan(), at, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun nameOf(t: Meanings.Translation) = (if (arabic) t.nameAr else t.nameEn).ifEmpty { t.nameEn }

    private fun languageOf(t: Meanings.Translation) = translationLanguage(t, arabic)

    private companion object {
        const val SURAHS = 114
        // The ayah and its meaning's room, as a share of the screen's height
        const val BODY_TALL = 0.4f
        // The ayah's glyphs over the meaning's type size; the object character each word's span stands on
        const val AYAH_SCALE = 1.5f
        const val OBJECT = "￼"
        const val NO_BREAK = " "
    }
}

/** A translation's language in the app's language, with its own name beside it where that differs. */
fun translationLanguage(t: Meanings.Translation, arabic: Boolean): String {
    val mine = (if (arabic) t.languageAr else t.languageEn).ifEmpty { t.languageEn }
    return if (t.native.isNotEmpty() && !t.native.equals(mine, ignoreCase = true)) "$mine · ${t.native}" else mine
}
