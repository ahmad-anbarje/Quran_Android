package com.readqurantoday.quran

import android.content.Context

/** One line a word for every ayah, lined up with the mushaf's own word count per ayah, read from an asset of "surah:ayah⇥w|w|…". */
class WordList(private val asset: String) {

    private val words = HashMap<Int, List<String>>(6300)

    @Volatile
    var ready = false
        private set

    /** Reads the asset once. Run off the main thread. */
    @Synchronized
    fun load(context: Context) {
        if (ready) return
        try {
            context.applicationContext.assets.open(asset).bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val colon = line.indexOf(':')
                    val tab = line.indexOf('\t')
                    if (colon <= 0 || tab <= colon) continue
                    val surah = line.substring(0, colon).toIntOrNull() ?: continue
                    val ayah = line.substring(colon + 1, tab).toIntOrNull() ?: continue
                    words[surah * 1000 + ayah] = line.substring(tab + 1).split('|')
                }
            }
        } catch (_: Exception) {
            words.clear()
        }
        ready = true
    }

    /** One word's line; [word] counts from 0 as the page does. Null until loaded. */
    fun of(surah: Int, ayah: Int, word: Int): String? =
        if (ready) words[surah * 1000 + ayah]?.getOrNull(word) else null
}

/** Word-by-word Latin transliteration (Quran.com). */
val Translit = WordList("data/translit.txt")
