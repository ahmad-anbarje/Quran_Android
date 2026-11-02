package com.readqurantoday.quran

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * The Tajweed edition's page fonts (King Fahd Complex, coloured by rule), shipped compressed in the app's assets.
 * Each is rewritten for the screen once, into the cache: its plain letters take the reader's ink; its Tajweed
 * colours stay the same by day and by night.
 */
object TajweedFonts : FontSource {

    private const val FOLDER = "tajweed"
    // The palette slots of the plain letters, which then follow the paint
    private val LETTERS = setOf(0, 13, 14)
    private const val FOREGROUND = 0xFFFF

    // Keyed by page
    private val fonts = ConcurrentHashMap<Int, Font>()
    private val faces = ConcurrentHashMap<Int, Typeface>()

    // A page's font as the app carries it
    private fun bytes(context: Context, page: Int): ByteArray =
        context.assets.open("tj$page.otf").use { it.readBytes() }

    // The page's font as the screen wants it, written once into the cache
    private fun ready(context: Context, page: Int): File? {
        val out = File(context.cacheDir, "$FOLDER/p$page-ink.ttf")
        if (out.exists()) return out
        return try {
            out.parentFile?.mkdirs()
            out.writeBytes(recolour(bytes(context, page)))
            out
        } catch (_: Exception) {
            null
        }
    }

    override fun font(context: Context, n: Int): Font? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        fonts[n]?.let { return it }
        val file = ready(context, n) ?: return null
        return try {
            Font.Builder(file).build().also { fonts[n] = it }
        } catch (_: Exception) {
            null
        }
    }

    override fun face(context: Context, n: Int): Typeface? {
        faces[n]?.let { return it }
        val file = ready(context, n) ?: return null
        return try {
            Typeface.createFromFile(file).also { faces[n] = it }
        } catch (_: RuntimeException) {
            null
        }
    }

    /** Lets go of the fonts of pages away from [near]; the first page's, which holds the Basmalah, stays. */
    fun keepOnly(near: IntRange) {
        fonts.keys.retainAll { it in near || it == 1 }
        faces.keys.retainAll { it in near || it == 1 }
    }

    // Each page's glyphs and the palette slots their colour layers use, read once from its font
    private val slots = ConcurrentHashMap<Int, Map<Int, Set<Int>>>()

    /** The palette slots [glyphIds] on [page] are coloured with: the Tajweed rules they carry. */
    fun slotsOf(context: Context, page: Int, glyphIds: List<Int>): Set<Int> {
        val all = slots.getOrPut(page) { layersOf(bytes(context, page)) }
        return glyphIds.flatMap { all[it].orEmpty() }.toSet()
    }

    // COLR v0: each coloured glyph's layers, as glyph and the palette slot each layer is painted with
    private fun layersOf(bytes: ByteArray): Map<Int, Set<Int>> {
        val b = ByteBuffer.wrap(bytes)
        var colr = -1
        for (i in 0 until (b.getShort(4).toInt() and 0xFFFF)) {
            val record = 12 + 16 * i
            if (String(bytes, record, 4, Charsets.US_ASCII) == "COLR") colr = b.getInt(record + 8)
        }
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

    // The font's colour layers: plain letters follow the paint, the rules keep the font's own colours
    private fun recolour(bytes: ByteArray): ByteArray {
        val b = ByteBuffer.wrap(bytes)
        var colr = -1
        for (i in 0 until (b.getShort(4).toInt() and 0xFFFF)) {
            val record = 12 + 16 * i
            if (String(bytes, record, 4, Charsets.US_ASCII) == "COLR") colr = b.getInt(record + 8)
        }
        if (colr >= 0) {
            val layers = colr + b.getInt(colr + 8)
            for (i in 0 until (b.getShort(colr + 12).toInt() and 0xFFFF)) {
                val slot = layers + i * 4 + 2
                if ((b.getShort(slot).toInt() and 0xFFFF) in LETTERS) b.putShort(slot, FOREGROUND.toShort())
            }
        }
        return bytes
    }
}
