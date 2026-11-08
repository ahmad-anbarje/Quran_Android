package com.readqurantoday.quran

import java.text.Normalizer

// A number offers both page and surah; words match names in Arabic without marks, or English
object Search {

    /** One row of the result list. */
    sealed interface Hit {
        /** A section title. */
        data class Head(val title: Int) : Hit

        /** Go straight to a page. */
        data class Page(val page: Int) : Hit

        /** A surah, shown as the index's usual row. */
        data class Name(val surah: Surahs.Surah) : Hit

        /** An ayah whose text carries the query, with where in the query it sat. */
        data class Verse(val ayah: Ayahs.Ayah, val at: Int, val len: Int) : Hit

        /** Nothing matched. */
        data object None : Hit
    }

    private const val LAST_PAGE = 604

    // Some keyboards type hamza as a separate mark, so marks are stripped before matching
    private val marks = Regex("[\u064B-\u0655\u0670\u0640]")

    /* Both sets of Arabic digits, so ٤٨ and ۴۸ read as 48. */
    private fun digits(s: String) = buildString {
        for (c in s) append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> c
            }
        )
    }

    // One spelling for letters readers do not distinguish; NFKC first undoes ligatures and joins typed hamza
    fun fold(s: String) = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFKC), "")
        .replace('آ', 'ا')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('ٱ', 'ا')
        .replace('ؤ', 'و')
        .replace('ئ', 'ي')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .trim()

    /** Ayahs holding [folded], each with its match marked. */
    fun verses(folded: String): List<Hit.Verse> = Ayahs.find(folded).map { f ->
        // Folding keeps positions, so matches can be marked; if lengths differ the row is left unmarked
        val at = if (f.ayah.folded.length == f.ayah.text.length) f.at else -1
        Hit.Verse(f.ayah, at, folded.length)
    }

    /** Rows for [raw]. An empty list means there is nothing to narrow: show the index. */
    fun plan(raw: String, all: List<Surahs.Surah>): List<Hit> {
        val q = digits(raw).trim()
        if (q.isBlank()) return emptyList()

        val hits = ArrayList<Hit>(8)

        // Page first: it is the finer place and the one a copied reference uses
        val n = q.toIntOrNull()
        if (n != null) {
            if (n in 1..LAST_PAGE) hits.add(Hit.Page(n))
            all.firstOrNull { it.id == n }?.let { hits.add(Hit.Name(it)) }
            if (hits.isNotEmpty()) hits.add(0, Hit.Head(R.string.search_jump))
            return hits.ifEmpty { listOf(Hit.None) }
        }

        val folded = fold(q)

        // Surah names before ayahs that merely contain the word
        val found = surahsNamed(q, all)
        if (found.isNotEmpty()) {
            hits.add(Hit.Head(R.string.search_surahs))
            for (s in found) hits.add(Hit.Name(s))
        }

        val verses = verses(folded)
        if (verses.isNotEmpty()) {
            hits.add(Hit.Head(R.string.search_ayahs))
            hits.addAll(verses)
        }

        return hits.ifEmpty { listOf(Hit.None) }
    }

    // --- surah names ---

    // The word "surah" before a name, as readers type it in either script, and the Arabic article
    private val surahWordAr = Regex("^(سوره|سورت)\\s*")
    private val surahWordEn = Regex("^(surah|surat|sura|soorah)[\\s'-]*")
    private val articleEn = Regex("^a[a-z]{1,2}[\\s'’-]+")
    private val notArabic = Regex("[^ء-ي]")
    private val notLatin = Regex("[^a-z]")
    private val vowels = Regex("[aeiouy]")
    private val doubled = Regex("(.)\\1+")

    /** The surahs [typed] names, best first: by the Arabic name with or without «سورة» and «ال», or by a Latin spelling of it. */
    fun surahsNamed(typed: String, all: List<Surahs.Surah>): List<Surahs.Surah> {
        val arabic = arabicKey(surahWordAr.replace(fold(typed), ""))
        val lower = surahWordEn.replace(typed.trim().lowercase(), "")
        val latin = latinKey(lower)
        // "albakara" is "al" and the name; the article is only dropped where enough is left to be a name
        val bare = latinKey(articleEn.replace(lower, "")).takeIf { it.length >= MIN_BARE }
            ?: latinKey(lower.removePrefix("al")).takeIf { lower.startsWith("al") && it.length >= MIN_BARE }
        if (arabic.isEmpty() && latin.isEmpty()) return emptyList()
        return all.mapNotNull { s -> rank(s, arabic, listOfNotNull(latin.ifEmpty { null }, bare))?.let { s to it } }
            .sortedWith(compareBy({ it.second }, { it.first.id }))
            .map { it.first }
    }

    // How well a surah answers: 0 its very name, 1 a name begun, 2 a name contained, 3 the same consonants; null none
    private fun rank(s: Surahs.Surah, arabic: String, latin: List<String>): Int? {
        var best: Int? = null
        fun take(r: Int) { best = minOf(best ?: r, r) }
        if (arabic.isNotEmpty()) {
            val name = arabicKey(s.name)
            when {
                name == arabic -> take(0)
                name.startsWith(arabic) -> take(1)
                name.contains(arabic) -> take(2)
            }
        }
        val names = listOf(latinKey(s.english), latinKey(articleEn.replace(s.english.lowercase(), "")))
        for (q in latin) for (name in names) when {
            name == q -> take(0)
            name.startsWith(q) -> take(1)
            name.contains(q) -> take(2)
        }
        latin.firstOrNull()?.let { q ->
            val bones = q.replace(vowels, "")
            if (bones.length >= MIN_BONES && names.any { it.replace(vowels, "").contains(bones) }) take(3)
        }
        return best
    }

    // An Arabic name as its letters alone, the article set aside
    private fun arabicKey(s: String): String = notArabic.replace(fold(s), "").removePrefix("ال")

    // A Latin spelling as it sounds: q and k alike, doubled letters once, no closing h
    private fun latinKey(s: String): String = notLatin.replace(s.lowercase(), "")
        .replace('q', 'k').replace("ee", "i").replace("oo", "u").replace("ou", "u")
        .replace(doubled, "$1")
        .removeSuffix("h")

    // Letters a name must keep once its article is dropped, and consonants a spelling needs to be matched by them alone
    private const val MIN_BARE = 4
    private const val MIN_BONES = 3
}
