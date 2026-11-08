package com.readqurantoday.quran

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/** The mushaf: its plain edition (QCF v4, Madinah 1441, 47 fonts) and its Tajweed edition, and the surah titles' fonts. */
object Mushaf {

    /** Pages in the mushaf. */
    const val PAGES = 604

    /** Surahs in the mushaf. */
    const val SURAHS = 114

    /** How many pages either side of the one in view are loaded ahead. */
    const val AHEAD = 3

    // The plain edition's fonts ship in the app's assets
    private object Packed : FontSource {
        val fonts = ConcurrentHashMap<Int, Font>()
        val faces = ConcurrentHashMap<Int, Typeface>()

        override fun font(context: Context, n: Int): Font? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            fonts[n]?.let { return it }
            return try {
                Font.Builder(context.assets, "q$n.ttf").build().also { fonts[n] = it }
            } catch (_: Exception) {
                null
            }
        }

        override fun face(context: Context, n: Int): Typeface? {
            faces[n]?.let { return it }
            return try {
                Typeface.createFromAsset(context.assets, "q$n.ttf").also { faces[n] = it }
            } catch (_: RuntimeException) {
                null
            }
        }
    }

    /** The plain edition, which every page has. */
    val plain = Book("data/mushaf.json", "v4", { "gq$it.txt" }, Packed)

    /** The Tajweed edition: the same 1441 rows, coloured by rule; its fonts ship in the app. */
    val tajweed = Book("data/mushaf-tajweed.json", "v2", { "gt$it.txt" }, TajweedFonts)

    private var nameFamily: Typeface? = null
    private var headTable: Book.Glyphs? = null
    private var headFont: Font? = null
    private var headFamily: Typeface? = null

    // Below normal priority, but not lowest, or a quick run of swipes outpaces it
    private val ahead = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "mushaf-ahead").apply { priority = Thread.NORM_PRIORITY - 2 }
    }

    fun load(context: Context) {
        plain.load(context)
        tajweed.load(context)
    }

    // The plain edition, as most of the app reads it
    val basmalahPage get() = plain.basmalahPage
    fun lines(page: Int) = plain.lines(page)
    fun marksOn(page: Int) = plain.marksOn(page)

    /** Pre-load pages around the current one on a background thread to avoid jank. */
    fun warm(context: Context, page: Int, book: Book = plain) {
        val ctx = context.applicationContext
        ahead.execute {
            for (p in nearestFirst(page)) {
                if (p in 1..PAGES) {
                    book.lines(p)
                    book.glyphs(ctx, p)
                    book.face(ctx, p)
                    book.font(ctx, p)
                }
            }
        }
    }

    // Nearest pages first in both directions
    private fun nearestFirst(page: Int): List<Int> {
        val out = ArrayList<Int>(AHEAD * 2 + 1)
        out.add(page)
        for (d in 1..AHEAD) {
            out.add(page + d)
            out.add(page - d)
        }
        return out
    }

    /** Release the fonts no page in this range uses; keep the Basmalahs' always. */
    fun keepOnly(near: IntRange) {
        plain.keepOnly(near)
        tajweed.keepOnly(near)
        val used = near.map { plain.fontOf(it) }.toSet() + plain.fontOf(plain.basmalahPage)
        Packed.fonts.keys.retainAll(used)
        Packed.faces.keys.retainAll(used)
        TajweedFonts.keepOnly(near)
        medals.keys.retainAll { it in near }
    }

    /** A plain page's ayah number as the Tajweed edition's medallion: its glyph in that page's font, and its width. */
    data class Medal(val id: Int, val advance: Float, val upem: Float)

    // Each page's medallions by the plain edition's number glyph; none where the two editions' numbers do not pair
    private val medals = ConcurrentHashMap<Int, Map<Int, Medal>>()

    /** The medallion for number glyph [cp] on [page], paired by reading order; null where the page has none to give. */
    fun medal(context: Context, page: Int, cp: Int): Medal? = medalsOn(context, page)?.get(cp)

    /** Whether every ayah number on [page] has its medallion, so the page's numbers are drawn alike. */
    private fun hasMedals(context: Context, page: Int) = !medalsOn(context, page).isNullOrEmpty()

    /**
     * The font [page]'s ayah numbers are drawn from as medallions, as the website draws them; null draws them plain:
     * before Android 12, in a video, which paints its own, or once the reader chose their colour.
     */
    fun medalFont(context: Context, page: Int): Font? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context !is Settings.OwnPhonetics &&
            !Settings.ownAyahColor(context) && hasMedals(context, page)) TajweedFonts.medals(context, page) else null

    private fun medalsOn(context: Context, page: Int): Map<Int, Medal>? {
        medals[page]?.let { return it }
        val table = tajweed.glyphs(context, page) ?: return null
        val plainEnds = plain.endsOn(page)
        val tajweedEnds = tajweed.endsOn(page)
        val paired = if (plainEnds.size != tajweedEnds.size) emptyMap() else plainEnds.zip(tajweedEnds).associate { (p, t) ->
            val cp = t.codePointAt(0)
            p.codePointAt(0) to Medal(table.id(cp), table.advance(cp), table.upem)
        }
        return paired.also { medals[page] = it }
    }

    /** The surah names font for the lists: 114 names + the word سورة, addressed by surah number. */
    @Synchronized
    fun nameTypeface(context: Context): Typeface? {
        nameFamily?.let { return it }
        nameFamily = try {
            Typeface.createFromAsset(context.assets, "sura-names.ttf")
        } catch (_: RuntimeException) {
            null
        }
        return nameFamily
    }

    /** The surah titles font (QCF4 BSML): each surah's printed title is one glyph. */
    @Synchronized
    fun heads(context: Context): Book.Glyphs? {
        headTable?.let { return it }
        headTable = readGlyphs(context, "gqh.txt")
        return headTable
    }

    @Synchronized
    fun headFace(context: Context): Font? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        headFont?.let { return it }
        headFont = try {
            Font.Builder(context.assets, "qh.ttf").build()
        } catch (_: Exception) {
            null
        }
        return headFont
    }

    @Synchronized
    fun headTypeface(context: Context): Typeface? {
        headFamily?.let { return it }
        headFamily = try {
            Typeface.createFromAsset(context.assets, "qh.ttf")
        } catch (_: RuntimeException) {
            null
        }
        return headFamily
    }

    /** Surah N's printed title in the titles font. */
    fun headCode(surah: Int): Int = 0xF100 + surah - 1

    /** The title glyphs' full height with their marks, in ems, and how far its middle stands above the baseline. */
    const val HEAD_TALL = 1.54f
    const val HEAD_RISE = 0.2f

    /** Codepoint for surah N in the names face: E004 for surah 4, E114 for 114. */
    fun nameCode(surah: Int): Int = 0xE000 + Integer.parseInt(surah.toString().padStart(3, '0'), 16)

    /** The word سورة in the names face. */
    const val SURAH_WORD = 0xE000
}
