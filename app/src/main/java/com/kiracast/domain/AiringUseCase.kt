package com.kiracast.domain

import com.kiracast.data.ani.AniListRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

data class AiringUi(
    val time: String,          // "14:05"
    val titlePrimary: String,  // romaji si dispo
    val titleAlt: String?,     // english/native en back-up
    val episodeLabel: String,  // "EP 3"
    val coverUrl: String?,
    val siteUrl: String?
)

class AiringUseCase(
    private val repo: AniListRepository,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    suspend fun getToday(): List<AiringUi> {
        val today: LocalDate = ZonedDateTime.now(zone).toLocalDate()
        val start = today.atStartOfDay(zone).toEpochSecond()
        val end = today.plusDays(1).atStartOfDay(zone).toEpochSecond()

        // AniListRepository attend des Int (epoch sec < 2_147_483_647 → OK)
        val items = repo.fetchAiringBetween(start.toInt(), end.toInt())

        return items
            .sortedBy { it.whenEpochSec }
            .map { it.toUi(zone) }
    }
}

private fun Long.toLocalTime(zone: ZoneId): String {
    val zdt = Instant.ofEpochSecond(this).atZone(zone)
    val h = zdt.hour.toString().padStart(2, '0')
    val m = zdt.minute.toString().padStart(2, '0')
    return "$h:$m"
}

private fun com.kiracast.data.ani.AiringItem.toUi(zone: ZoneId): AiringUi {
    // Priorité titre : romaji (pour ne pas "traduire" le japonais romanisé),
    // sinon native, sinon english.
    val primary = titleRomaji ?: titleNative ?: titleEnglish ?: "Untitled"
    val alt = when {
        primary === titleRomaji -> titleEnglish ?: titleNative
        primary === titleNative -> titleEnglish ?: titleRomaji
        else -> titleRomaji ?: titleNative
    }

    return AiringUi(
        time = whenEpochSec.toLocalTime(zone),
        titlePrimary = primary,
        titleAlt = alt,
        episodeLabel = "EP ${episode ?: "?"}",
        coverUrl = cover,
        siteUrl = siteUrl
    )
}
