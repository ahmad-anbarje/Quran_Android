package com.readqurantoday.quran

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * What the ayahs mean, as the website has it: its translations, QuranEnc's in every language it lists, and the Arabic
 * of the Tafsir al-Muyassar. Each text comes a surah at a time, fetched from the site the first time it is asked for
 * and kept on the phone after that.
 */
object Meanings {

    private const val BASE = "https://readqurantoday.com/data/"
    private const val CATALOGUE = "translations.json"
    private const val TIMEOUT_MS = 15000
    // Surahs kept in memory at once, across texts
    private const val KEEP = 8

    /** The English offered before a reader picks a translation, and the one used before the list has come. */
    val first = Translation(
        id = "sahih", lang = "en", rtl = false, languageAr = "الإنجليزية", languageEn = "English", native = "English",
        nameAr = "صحيح إنترناشيونال", nameEn = "Saheeh International", source = "tanzil", key = "", complex = false
    )

    /** The translation chosen, as [offered] has it now; one gone from the list falls back to the first. */
    fun chosen(context: Context, offered: List<Translation>): Translation {
        val id = Settings.translation(context) ?: firstChoice(offered)
        return offered.firstOrNull { it.id == id } ?: offered.firstOrNull { it.id == first.id } ?: first
    }

    /** One translation in the list: its language in Arabic, English and its own name, and its translator. */
    data class Translation(
        val id: String,
        val lang: String,
        val rtl: Boolean,
        val languageAr: String,
        val languageEn: String,
        val native: String,
        val nameAr: String,
        val nameEn: String,
        val source: String,
        val key: String,
        val complex: Boolean
    )

    /** Where a text's surahs are kept, on the site and on the phone. */
    data class Text(val path: String, val rtl: Boolean, val source: String, val key: String)

    val tafsir = Text("tafsir/muyassar/", rtl = true, source = "tanzil", key = "")

    fun translation(t: Translation) = Text("translation/${t.id}/", t.rtl, t.source, t.key)

    private val main = Handler(Looper.getMainLooper())
    private val net = Executors.newFixedThreadPool(2)

    @Volatile var list: List<Translation> = emptyList()
        private set
    @Volatile private var refreshed = false

    private val kept = object : LinkedHashMap<String, List<String>>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > KEEP
    }

    private fun file(context: Context, path: String) = File(context.filesDir, "meaning/$path")

    /** The list of translations, from the phone at once when it is there, and afresh from the site once a run. */
    fun catalogue(context: Context, done: (List<Translation>) -> Unit) {
        val app = context.applicationContext
        if (list.isNotEmpty()) done(list)
        if (refreshed && list.isNotEmpty()) return
        net.execute {
            val saved = file(app, CATALOGUE)
            if (list.isEmpty()) parse(saved.takeIf { it.exists() }?.readText())?.let { got ->
                list = got
                main.post { done(got) }
            }
            if (refreshed) return@execute
            val fresh = fetch(CATALOGUE) ?: return@execute
            val got = parse(fresh) ?: return@execute
            refreshed = true
            saved.parentFile?.mkdirs()
            saved.writeText(fresh)
            if (got != list) {
                list = got
                main.post { done(got) }
            }
        }
    }

    private fun parse(json: String?): List<Translation>? = try {
        val all = JSONArray(json ?: return null)
        List(all.length()) { i ->
            val t = all.getJSONObject(i)
            val language = t.getJSONObject("language")
            val name = t.getJSONObject("name")
            Translation(
                id = t.getString("id"),
                lang = t.getString("lang"),
                rtl = t.optString("dir") == "rtl",
                languageAr = language.optString("ar"),
                languageEn = language.optString("en"),
                native = language.optString("native"),
                nameAr = name.optString("ar"),
                nameEn = name.optString("en"),
                source = t.optString("source"),
                key = t.optString("key"),
                complex = t.optBoolean("complex")
            )
        }.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    /** Ayah [ayah] of [surah] in [text], on the main thread; null when it could not be had. */
    fun ayah(context: Context, text: Text, surah: Int, ayah: Int, done: (String?) -> Unit) {
        val name = "${text.path}$surah.json"
        synchronized(kept) { kept[name] }?.let { done(it.getOrNull(ayah - 1)); return }
        kept(context, name, ::ayahs) { got ->
            got?.let { synchronized(kept) { kept[name] = it } }
            done(got?.getOrNull(ayah - 1))
        }
    }

    /** A file of the site's data, [read] from the phone, or fetched and kept when it reads; heard on the main thread. */
    fun <T> kept(context: Context, name: String, read: (String) -> T?, done: (T?) -> Unit) {
        val app = context.applicationContext
        net.execute {
            val saved = file(app, name)
            val got = saved.takeIf { it.exists() }?.readText()?.let(read) ?: fetch(name)?.let { json ->
                read(json)?.also {
                    saved.parentFile?.mkdirs()
                    saved.writeText(json)
                }
            }
            main.post { done(got) }
        }
    }

    private fun ayahs(json: String?): List<String>? = try {
        val all = JSONArray(json ?: return null)
        List(all.length()) { all.getString(it) }
    } catch (_: Exception) {
        null
    }

    private fun fetch(name: String): String? = try {
        (URL(BASE + name).openConnection() as HttpURLConnection).run {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            try {
                if (responseCode == HttpURLConnection.HTTP_OK) inputStream.use { String(it.readBytes(), Charsets.UTF_8) } else null
            } finally {
                disconnect()
            }
        }
    } catch (_: Exception) {
        null
    }

    /** The translation a reader starts with: their phone's language where one is offered, beyond Arabic; else, and for English, Saheeh International. */
    private fun firstChoice(offered: List<Translation>): String {
        val locales = Resources.getSystem().configuration.locales
        for (i in 0 until locales.size()) {
            val lang = locales[i].language
            if (lang == "ar") continue
            if (lang == first.lang) return first.id
            offered.firstOrNull { it.lang == lang }?.let { return it.id }
        }
        return first.id
    }

    /** Where a text is credited from, for the reader to see it at its source. */
    fun sourceUrl(text: Text, surah: Int, arabic: Boolean): String =
        if (text.source == "quranenc") "https://quranenc.com/${if (arabic) "ar" else "en"}/browse/${text.key}/$surah"
        else "https://tanzil.net/trans/"
}
