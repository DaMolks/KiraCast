package com.kiracast.data.ani

import java.time.Instant
import java.time.ZoneId

/**
 * Façade simple autour d’AniListRepository pour récupérer les sorties du jour.
 * Supprime le conflit de nom "Page" et l'appel obsolète "airingBetween".
 */
class AiringRepository(private val api: AniListRepository) {

    suspend fun getDayAiring(nowMillis: Long = System.currentTimeMillis()): List<AiringItem> {
        val zone = ZoneId.systemDefault()
        val dayStartSec = Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toEpochSecond()
            .toInt()
        val dayEndSec = dayStartSec + 86_400
        return api.fetchAiringBetween(dayStartSec, dayEndSec)
    }

    companion object {
        fun default(enableLogs: Boolean = false): AiringRepository =
            AiringRepository(AniListApi.create(enableLogs))
    }
}
