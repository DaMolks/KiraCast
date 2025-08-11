package com.kiracast.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kiracast.R
import com.kiracast.data.ani.AniListApi
import com.kiracast.data.ani.AiringItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter

class CalendarFragment : Fragment() {

    private lateinit var list: RecyclerView
    private val adapter = CalendarAdapter { item ->
        // Action sur clic (ouvrir site AniList par ex.)
        item.siteUrl?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list = view.findViewById(R.id.recyclerCalendar)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        loadWeek()
    }

    private fun loadWeek() {
        lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    val repo = AniListApi.create(enableLogs = false)

                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now(zone)
                    val start = today.atStartOfDay(zone).toEpochSecond().toInt()
                    val end = today.plusDays(7).atStartOfDay(zone).toEpochSecond().toInt()

                    val items = repo.fetchAiringBetween(start, end)
                    buildRows(items, zone)
                }
                adapter.submitList(rows)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Erreur", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildRows(items: List<AiringItem>, zone: ZoneId): List<CalendarRow> {
        val byDay = items.groupBy {
            Instant.ofEpochSecond(it.whenEpochSec).atZone(zone).toLocalDate()
        }.toSortedMap()

        val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM").withLocale(java.util.Locale.getDefault())
        val rows = mutableListOf<CalendarRow>()

        for ((day, list) in byDay) {
            val label = dayFmt.format(day)
            rows += CalendarRow.DayHeader(label)
            // Tri par heure
            val sorted = list.sortedBy { it.whenEpochSec }
            rows += sorted.map { CalendarRow.ItemRow(it) }
        }
        return rows
    }
}
