package com.kiracast.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.kiracast.R
import com.kiracast.data.ani.AiringItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class CalendarRow {
    data class DayHeader(val label: String) : CalendarRow()
    data class ItemRow(val item: AiringItem) : CalendarRow()
}

private const val VT_HEADER = 0
private const val VT_ITEM = 1

class CalendarAdapter(
    private val onItemClick: (AiringItem) -> Unit = {}
) : ListAdapter<CalendarRow, RecyclerView.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<CalendarRow>() {
        override fun areItemsTheSame(old: CalendarRow, new: CalendarRow): Boolean =
            when {
                old is CalendarRow.DayHeader && new is CalendarRow.DayHeader ->
                    old.label == new.label
                old is CalendarRow.ItemRow && new is CalendarRow.ItemRow ->
                    old.item.whenEpochSec == new.item.whenEpochSec &&
                            (old.item.titleEnglish ?: old.item.titleRomaji ?: old.item.titleNative) ==
                            (new.item.titleEnglish ?: new.item.titleRomaji ?: new.item.titleNative)
                else -> false
            }

        override fun areContentsTheSame(old: CalendarRow, new: CalendarRow): Boolean = old == new
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CalendarRow.DayHeader -> VT_HEADER
        is CalendarRow.ItemRow   -> VT_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == VT_HEADER) {
            HeaderVH(inf.inflate(R.layout.row_day_header, parent, false))
        } else {
            ItemVH(inf.inflate(R.layout.row_airing_item, parent, false), onItemClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is CalendarRow.DayHeader -> (holder as HeaderVH).bind(row.label)
            is CalendarRow.ItemRow   -> (holder as ItemVH).bind(row.item)
        }
    }

    // --- ViewHolders ---

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.dayTitle)
        fun bind(label: String) {
            title.text = label.capitalizeFirstFr()
        }
    }

    class ItemVH(view: View, private val onClick: (AiringItem) -> Unit) :
        RecyclerView.ViewHolder(view) {

        private val cover: ImageView = view.findViewById(R.id.cover)
        private val title: TextView = view.findViewById(R.id.title)
        private val episode: TextView = view.findViewById(R.id.episode)
        private val time: TextView = view.findViewById(R.id.time)

        // Heures en français, fuseau du device
        private val hourFmt: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
                .withZone(ZoneId.systemDefault())

        private var current: AiringItem? = null

        init {
            view.setOnClickListener { current?.let(onClick) }
        }

        fun bind(item: AiringItem) {
            current = item

            // Choix du meilleur titre dispo
            title.text = item.titleEnglish ?: item.titleRomaji ?: item.titleNative ?: "—"

            // Épisode (optionnel)
            episode.text = item.episode?.let { "Épisode $it" } ?: ""

            // Heure locale formattée
            time.text = hourFmt.format(Instant.ofEpochSecond(item.whenEpochSec))

            // Jaquette avec placeholder/error
            cover.load(item.cover) {
                crossfade(true)
                placeholder(R.drawable.ic_cover_placeholder)
                error(R.drawable.ic_cover_placeholder)
            }
        }
    }
}

/** Capitalise la première lettre en respectant la locale FR */
private fun String.capitalizeFirstFr(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
