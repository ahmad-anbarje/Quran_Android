package com.readqurantoday.quran

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Juz, hizb or pages: each row opens, or recites from, the page it begins on
class DivisionAdapter(
    private val starts: IntArray,
    private val title: (Int) -> String,
    private val where: (Int) -> String,
    private val onOpen: (Int) -> Unit,
    private val onPlay: (Int) -> Unit,
    private val onReciter: (Int) -> Unit,
    private val playing: () -> Int
) : RecyclerView.Adapter<DivisionAdapter.Row>() {

    class Row(v: View) : RecyclerView.ViewHolder(v)

    override fun getItemCount() = starts.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Row(LayoutInflater.from(parent.context).inflate(R.layout.row_division, parent, false))

    override fun onBindViewHolder(holder: Row, position: Int) {
        val row = holder.itemView
        val number = position + 1
        val page = starts[position]

        row.findViewById<TextView>(R.id.div_num).text = figures(number, row.resources)
        row.findViewById<TextView>(R.id.div_title).text = title(number)
        row.findViewById<TextView>(R.id.div_where).text = where(page)

        // The seam belongs between two rows, not under the last one
        row.findViewById<View>(R.id.divider).visibility =
            if (position < starts.size - 1) View.VISIBLE else View.GONE

        row.setOnClickListener { onOpen(page) }
        row.findViewById<View>(R.id.btn_play).setOnClickListener { onPlay(page) }
        row.findViewById<View>(R.id.btn_reciter).setOnClickListener { onReciter(page) }

        // Only the row being recited shows pause, and only its own button waits for the audio
        val here = playing() == number
        sayPlayButton(row, playing = here && Recite.wantsToPlay(), waiting = here && Recite.waiting())
    }
}
