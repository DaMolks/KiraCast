package com.kiracast.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kiracast.R
import com.kiracast.data.ani.AiringItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AiringAdapter(
    private var data: List<AiringItem>,
    private val zone: ZoneId = ZoneId.systemDefault()
) : RecyclerView.Adapter<AiringAdapter.VH>() {

    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    fun submit(list: List<AiringItem>) {
        data = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(p: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_airing, p, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val it = data[pos]
        h.title.text = it.titleRomaji ?: it.titleEnglish ?: it.titleNative ?: "???"
        h.sub.text = "Ep. ${it.episode ?: "-"}"
        val time = Instant.ofEpochSecond(it.whenEpochSec).atZone(zone).format(fmt)
        h.time.text = time

        // TODO: brancher Coil/Glide plus tard pour charger it.cover
        h.cover.setImageResource(R.drawable.ic_placeholder) // mets une icône placeholder dans drawable
    }

    override fun getItemCount() = data.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cover: ImageView = v.findViewById(R.id.cover)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val time: TextView = v.findViewById(R.id.time)
    }
}