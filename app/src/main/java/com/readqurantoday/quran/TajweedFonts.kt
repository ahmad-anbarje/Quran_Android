package com.readqurantoday.quran

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * The Tajweed edition's page fonts (King Fahd Complex, coloured by rule), shipped compressed in the app's assets.
 * Each is rewritten for the screen once, into the cache: its plain letters take the reader's ink; its Tajweed
 * colours are the font's own, its day palette by day and its night one by night, as the website's.
 */
object TajweedFonts : FontSource {

    private const val FOLDER = "tajweed"
    // The palette slots of the plain letters, which then follow the paint
    private val LETTERS = setOf(0, 13, 14)
    private const val FOREGROUND = 0xFFFF
    // The font's own palette for dark pages
    private const val NIGHT_PALETTE = 1

    // The plain edition's ayah numbers as this edition's medallions: the slots of their outline, ornament, disc and
    // numerals, in the website's blues by day and as its dark pages turn them by night
    private val MEDAL_SLOTS = intArrayOf(10, 11, 12, 13)
    private val MEDAL_DAY = intArrayOf(0xFF0D4A73.toInt(), 0xFF5AAEDD.toInt(), 0xFFDBEEFA.toInt(), 0xFF000000.toInt())
    private val MEDAL_NIGHT = intArrayOf(0xFF5AAEDD.toInt(), 0xFF3F9BD1.toInt(), 0xFF1C2E3D.toInt(), 0xFFFFFFFF.toInt())

    // Keyed by page and theme, as key() makes them
    private val fonts = ConcurrentHashMap<Int, Font>()
    private val faces = ConcurrentHashMap<Int, Typeface>()
    private val medals = ConcurrentHashMap<Int, Font>()

    private fun night(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun key(page: Int, night: Boolean) = page * 2 + if (night) 1 else 0

    private fun theme(night: Boolean) = if (night) "night" else "day"

    // A page's font as the app carries it
    private fun bytes(context: Context, page: Int): ByteArray =
        context.assets.open("tj$page.otf").use { it.readBytes() }

    // A font made from a page's, written once into the cache under [name]
    private fun written(context: Context, name: String, make: () -> ByteArray): File? {
        val out = File(context.cacheDir, "$FOLDER/$name")
        if (out.exists()) return out
        return try {
            out.parentFile?.mkdirs()
            out.writeBytes(make())
            out
        } catch (_: Exception) {
            null
        }
    }

    // The page's font as the screen wants it in this theme
    private fun ready(context: Context, page: Int, night: Boolean): File? =
        written(context, "p$page-ink-${theme(night)}.ttf") { recolour(bytes(context, page), night) }

    override fun font(context: Context, n: Int): Font? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val night = night(context)
        fonts[key(n, night)]?.let { return it }
        val file = ready(context, n, night) ?: return null
        return try {
            Font.Builder(file).build().also { fonts[key(n, night)] = it }
        } catch (_: Exception) {
            null
        }
    }

    override fun face(context: Context, n: Int): Typeface? {
        val night = night(context)
        faces[key(n, night)]?.let { return it }
        val file = ready(context, n, night) ?: return null
        return try {
            Typeface.createFromFile(file).also { faces[key(n, night)] = it }
        } catch (_: RuntimeException) {
            null
        }
    }

    /** The page's font with its ayah medallions in the website's blues for this theme, for the plain edition's numbers. */
    fun medals(context: Context, page: Int): Font? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val night = night(context)
        medals[key(page, night)]?.let { return it }
        val file = written(context, "p$page-medal-${theme(night)}.ttf") {
            painted(bytes(context, page), MEDAL_SLOTS, if (night) MEDAL_NIGHT else MEDAL_DAY)
        } ?: return null
        return try {
            Font.Builder(file).build().also { medals[key(page, night)] = it }
        } catch (_: Exception) {
            null
        }
    }

    /** Lets go of the fonts of pages away from [near]; the first page's, which holds the Basmalah, stays. */
    fun keepOnly(near: IntRange) {
        for (kept in listOf(fonts, faces, medals)) kept.keys.retainAll { it / 2 in near || it / 2 == 1 }
    }

    // Each page's glyphs and the palette slots their colour layers use, read once from its font
    private val slots = ConcurrentHashMap<Int, Map<Int, Set<Int>>>()

    /** The palette slots [glyphIds] on [page] are coloured with: the Tajweed rules they carry. */
    fun slotsOf(context: Context, page: Int, glyphIds: List<Int>): Set<Int> {
        val all = slots.getOrPut(page) { layersOf(bytes(context, page)) }
        return glyphIds.flatMap { all[it].orEmpty() }.toSet()
    }

    // Where a table starts in a font, by its tag; -1 if the font has none
    private fun table(bytes: ByteArray, tag: String): Int {
        val b = ByteBuffer.wrap(bytes)
        for (i in 0 until (b.getShort(4).toInt() and 0xFFFF)) {
            val record = 12 + 16 * i
            if (String(bytes, record, 4, Charsets.US_ASCII) == tag) return b.getInt(record + 8)
        }
        return -1
    }

    // COLR v0: each coloured glyph's layers, as glyph and the palette slot each layer is painted with
    private fun layersOf(bytes: ByteArray): Map<Int, Set<Int>> {
        val b = ByteBuffer.wrap(bytes)
        val colr = table(bytes, "COLR")
        if (colr < 0) return emptyMap()
        val bases = colr + b.getInt(colr + 4)
        val layers = colr + b.getInt(colr + 8)
        val out = HashMap<Int, Set<Int>>()
        for (i in 0 until (b.getShort(colr + 2).toInt() and 0xFFFF)) {
            val at = bases + i * 6
            val glyph = b.getShort(at).toInt() and 0xFFFF
            val first = b.getShort(at + 2).toInt() and 0xFFFF
            val count = b.getShort(at + 4).toInt() and 0xFFFF
            out[glyph] = (first until first + count).map { b.getShort(layers + it * 4 + 2).toInt() and 0xFFFF }.toSet()
        }
        return out
    }

    // The font's colour layers: plain letters follow the paint, the rules keep the font's own colours, by night its night palette's
    private fun recolour(bytes: ByteArray, night: Boolean): ByteArray {
        val b = ByteBuffer.wrap(bytes)
        val colr = table(bytes, "COLR")
        if (colr >= 0) {
            val layers = colr + b.getInt(colr + 8)
            for (i in 0 until (b.getShort(colr + 12).toInt() and 0xFFFF)) {
                val slot = layers + i * 4 + 2
                if ((b.getShort(slot).toInt() and 0xFFFF) in LETTERS) b.putShort(slot, FOREGROUND.toShort())
            }
        }
        val cpal = table(bytes, "CPAL")
        // Android paints from the first palette, so by night it is pointed at the night one's colours
        if (night && cpal >= 0 && (b.getShort(cpal + 4).toInt() and 0xFFFF) > NIGHT_PALETTE) {
            b.putShort(cpal + 12, b.getShort(cpal + 12 + 2 * NIGHT_PALETTE))
        }
        return bytes
    }

    // The first palette's [slots] painted [colours]
    private fun painted(bytes: ByteArray, slots: IntArray, colours: IntArray): ByteArray {
        val b = ByteBuffer.wrap(bytes)
        val cpal = table(bytes, "CPAL")
        if (cpal < 0) return bytes
        val records = cpal + b.getInt(cpal + 8)
        val first = b.getShort(cpal + 12).toInt() and 0xFFFF
        slots.forEachIndexed { i, slot ->
            // Each colour as blue, green, red, alpha
            val at = records + (first + slot) * 4
            val c = colours[i]
            bytes[at] = c.toByte()
            bytes[at + 1] = (c shr 8).toByte()
            bytes[at + 2] = (c shr 16).toByte()
            bytes[at + 3] = (c ushr 24).toByte()
        }
        return bytes
    }
}
