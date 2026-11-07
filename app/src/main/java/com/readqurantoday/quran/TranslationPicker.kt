package com.readqurantoday.quran

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** A sheet of every translation, grouped by language and searched by language or translator; [picked] hears the one tapped. */
fun Activity.pickTranslation(offered: List<Meanings.Translation>, current: String, picked: (String) -> Unit) {
    val dialog = Dialog(this, R.style.SheetDialog)
    val view = layoutInflater.inflate(R.layout.part_surah_picker, null)
    view.findViewById<TextView>(R.id.picker_title).setText(R.string.translation_pick)
    view.findViewById<ImageView>(R.id.picker_search_icon).imageTintList = ColorStateList.valueOf(getColor(R.color.text_mute))
    val arabic = Settings.language(this) == "ar"

    val rows = TranslationChoices(offered.ifEmpty { listOf(Meanings.first) }, current, arabic) { id ->
        dialog.dismiss()
        picked(id)
    }
    view.findViewById<RecyclerView>(R.id.picker_list).apply {
        layoutManager = LinearLayoutManager(this@pickTranslation)
        adapter = rows
        // Opens at the translation in use, its language's heading above it
        (layoutManager as LinearLayoutManager).scrollToPositionWithOffset((rows.at(current) - 1).coerceAtLeast(0), 0)
    }
    view.findViewById<EditText>(R.id.picker_search).apply {
        setHint(R.string.translation_search_hint)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = rows.filter(s?.toString().orEmpty())
        })
    }
    raise(dialog, view)
}

// The languages, the most read first, each with its translations beneath; each row made only as it scrolls into view
private class TranslationChoices(
    private val offered: List<Meanings.Translation>,
    private val current: String,
    private val arabic: Boolean,
    private val picked: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class Row(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(R.id.sheet_label)
        val note: TextView = v.findViewById(R.id.sheet_note)
        val tick: View = v.findViewById(R.id.sheet_tick)
    }

    class Head(v: View) : RecyclerView.ViewHolder(v)

    // A language's heading, or one of its translations
    private sealed interface Line {
        data class Language(val name: String) : Line
        data class One(val t: Meanings.Translation) : Line
    }

    private var shown: List<Line> = lines("")

    fun at(id: String) = shown.indexOfFirst { it is Line.One && it.t.id == id }.coerceAtLeast(0)

    @SuppressLint("NotifyDataSetChanged")
    fun filter(typed: String) {
        shown = lines(typed)
        notifyDataSetChanged()
    }

    // Every word typed found in a translation's language, its own name for it, its translator or its code
    private fun lines(typed: String): List<Line> {
        val words = Search.fold(typed.trim()).lowercase().split(' ').filter { it.isNotEmpty() }
        val found = offered.filter { t ->
            val hay = listOf(t.languageAr, t.languageEn, t.native, t.nameAr, t.nameEn, t.lang).joinToString(" ") { Search.fold(it).lowercase() }
            words.all { it in hay }
        }
        val groups = found.groupBy { it.lang }.values.sortedWith(compareBy { group ->
            POPULAR.indexOf(group[0].lang).let { if (it < 0) POPULAR.size + offered.indexOf(group[0]) else it }
        })
        return buildList {
            for (group in groups) {
                add(Line.Language(group[0].language(arabic)))
                group.forEach { add(Line.One(it)) }
            }
        }
    }

    override fun getItemCount() = shown.size

    override fun getItemViewType(position: Int) = if (shown[position] is Line.Language) T_HEAD else T_ONE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val blow = LayoutInflater.from(parent.context)
        return if (viewType == T_HEAD) Head(blow.inflate(R.layout.item_search_head, parent, false))
        else Row(blow.inflate(R.layout.item_sheet_row, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val line = shown[position]) {
            is Line.Language -> (holder.itemView as TextView).text = line.name
            is Line.One -> {
                val row = holder as Row
                val t = line.t
                row.label.text = t.name(arabic)
                val complex = row.itemView.context.getString(R.string.meaning_complex)
                row.note.text = complex
                row.note.visibility = if (t.complex && row.label.text != complex) View.VISIBLE else View.GONE
                row.tick.visibility = if (t.id == current) View.VISIBLE else View.INVISIBLE
                row.itemView.setOnClickListener { picked(t.id) }
            }
        }
    }

    private companion object {
        const val T_ONE = 0
        const val T_HEAD = 1
        // The list's head, as the website's: languages by how many read the Quran in them, then by speakers worldwide
        val POPULAR = listOf(
            "en", "ur", "id", "bn", "tr", "fr", "fa", "hi", "ms", "ha", "sw", "ps", "es", "ru", "zh",
            "pt", "de", "uz", "so", "ml", "ta", "am", "yo", "ku", "ckb", "az", "kk", "tg", "bs", "sq"
        )
    }
}
