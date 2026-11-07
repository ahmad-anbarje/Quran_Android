package com.readqurantoday.quran

import android.content.Context
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * What each word means, shown under its transliteration, as the website shows it: in English, kept in the app, or in
 * the language of the translation chosen where the site has its words too, fetched a surah at a time and kept.
 */
object WordMeaning {

    val english = WordList("data/word-meaning.txt")

    // The languages the site has word meanings in besides English
    private val LANGS = setOf("ur", "id", "bn", "tr", "fa", "hi")
    // As tools/word_meaning.py: a printed slot the recitation counts as two words, by ayah
    private val PAIRS = mapOf(2 * 1000 + 181 to 2, 8 * 1000 + 6 to 3, 13 * 1000 + 37 to 7)

    private val other = ConcurrentHashMap<String, List<List<String>>>()
    private val asked = ConcurrentHashMap.newKeySet<String>()

    /** Heard when a surah's words arrive in another language, so the word marked can show them. */
    var arrived: (() -> Unit)? = null

    /** The meaning of [word] of [surah]:[ayah], counted from 0; English until the chosen language's arrives. */
    fun of(context: Context, surah: Int, ayah: Int, word: Int): String? {
        val lang = Meanings.chosen(context, Meanings.list).lang
        if (lang in LANGS) {
            val name = "meaning/$lang/$surah.json"
            val words = other[name]
            if (words != null) words.getOrNull(ayah - 1)?.getOrNull(word)?.let { return it }
            else if (asked.add(name)) Meanings.kept(context, name, { read(surah, it) }) { got ->
                if (got != null) {
                    other[name] = got
                    arrived?.invoke()
                } else asked.remove(name)
            }
        }
        return english.of(surah, ayah, word)
    }

    // A surah's words by ayah, each split slot's meaning on both its words
    private fun read(surah: Int, json: String): List<List<String>>? = try {
        val all = JSONArray(json)
        List(all.length()) { a ->
            val ayah = all.getJSONArray(a)
            val words = List(ayah.length()) { ayah.getString(it) }
            PAIRS[surah * 1000 + a + 1]?.let { w -> words.take(w + 1) + words.drop(w) } ?: words
        }
    } catch (_: Exception) {
        null
    }
}

/** The word label's lines: how each word sounds and what it means. Run off the main thread. */
fun loadWordTips(context: Context) {
    Translit.load(context)
    WordMeaning.english.load(context)
}
