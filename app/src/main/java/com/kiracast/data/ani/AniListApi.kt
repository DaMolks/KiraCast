package com.kiracast.data.ani

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Client minimal pour AniList (GraphQL).
 * - Endpoint: https://graphql.anilist.co
 * - Fournit une méthode pour récupérer les sorties (AiringSchedule) entre deux timestamps (epoch secondes).
 *
 * Utilisation:
 * val repo = AniListApi.create()
 * val res = repo.fetchAiringBetween(startEpoch, endEpoch)
 */

// ------------------------- Retrofit / API -------------------------

interface AniListService {
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("graphql")
    suspend fun query(@Body body: GraphQLRequest): AniListResponse
}

object AniListApi {
    private const val BASE_URL = "https://graphql.anilist.co/"

    // Requête GraphQL : AiringSchedule du jour (bornes inclusives/exclusives fournies via variables)
    private const val AIRING_QUERY = """
        query AiringBetween(${'$'}page:Int = 1, ${'$'}perPage:Int = 50, ${'$'}gt:Int!, ${'$'}lt:Int!) {
          Page(page:${'$'}page, perPage:${'$'}perPage) {
            pageInfo { currentPage hasNextPage }
            airingSchedules(airingAt_greater:${'$'}gt, airingAt_lesser:${'$'}lt, sort: TIME) {
              id
              airingAt
              episode
              media {
                id
                siteUrl
                title { romaji english native }
                coverImage { large color }
                format
                season
                seasonYear
                episodes
              }
            }
          }
        }
    """

    fun create(enableLogs: Boolean = false): AniListRepository {
        val client = OkHttpClient.Builder().apply {
            if (enableLogs) {
                addInterceptor(
                    HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)
                )
            }
        }.build()

        // >>> IMPORTANT : Moshi configuré pour Kotlin
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val service = retrofit.create(AniListService::class.java)
        return AniListRepository(service)
    }

    internal fun buildAiringRequest(page: Int, perPage: Int, gt: Int, lt: Int): GraphQLRequest =
        GraphQLRequest(
            query = AIRING_QUERY.trimIndent(),
            variables = mapOf(
                "page" to page,
                "perPage" to perPage,
                "gt" to gt,   // start (epoch seconds)
                "lt" to lt    // end (epoch seconds)
            )
        )
}

// ------------------------- Repository (façade simple) -------------------------

class AniListRepository(private val service: AniListService) {

    /**
     * Récupère la liste des sorties (avec pagination interne) entre [gt, lt[
     * @param startEpochSec epoch secondes début (inclus)
     * @param endEpochSec   epoch secondes fin (exclus)
     * @param pageSize      taille de page (50 conseillé)
     */
    suspend fun fetchAiringBetween(
        startEpochSec: Int,
        endEpochSec: Int,
        pageSize: Int = 50,
        maxPages: Int = 5
    ): List<AiringItem> {
        val all = mutableListOf<AiringItem>()
        var page = 1
        var hasNext = true

        while (hasNext && page <= maxPages) {
            val req = AniListApi.buildAiringRequest(page, pageSize, startEpochSec, endEpochSec)
            val res = service.query(req)
            val pageData = res.data?.page ?: break

            pageData.airingSchedules?.mapNotNull { it.toItem() }?.let { all.addAll(it) }
            hasNext = pageData.pageInfo?.hasNextPage == true
            page++
        }
        return all
    }
}

// ------------------------- DTOs / Mapping -------------------------

@JsonClass(generateAdapter = true)
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, @JvmSuppressWildcards Any?>
)

@JsonClass(generateAdapter = true)
data class AniListResponse(
    val data: AniListData?
)

@JsonClass(generateAdapter = true)
data class AniListData(
    @Json(name = "Page") val page: Page?
)

@JsonClass(generateAdapter = true)
data class Page(
    val pageInfo: PageInfo?,
    val airingSchedules: List<AiringSchedule>?
)

@JsonClass(generateAdapter = true)
data class PageInfo(
    val currentPage: Int?,
    val hasNextPage: Boolean?
)

@JsonClass(generateAdapter = true)
data class AiringSchedule(
    val id: Long?,
    val airingAt: Long?, // epoch sec
    val episode: Int?,
    val media: Media?
)

@JsonClass(generateAdapter = true)
data class Media(
    val id: Long?,
    val siteUrl: String?,
    val title: MediaTitle?,
    val coverImage: CoverImage?,
    val format: String?,
    val season: String?,
    val seasonYear: Int?,
    val episodes: Int?
)

@JsonClass(generateAdapter = true)
data class MediaTitle(
    val romaji: String?,
    val english: String?,
    val native: String?
)

@JsonClass(generateAdapter = true)
data class CoverImage(
    val large: String?,
    val color: String?
)

/** Modèle “domaine” simplifié pour l’app */
data class AiringItem(
    val whenEpochSec: Long,
    val episode: Int?,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val titleNative: String?,
    val cover: String?,
    val siteUrl: String?
)

private fun AiringSchedule.toItem(): AiringItem? {
    val t = airingAt ?: return null
    return AiringItem(
        whenEpochSec = t,
        episode = episode,
        titleRomaji = media?.title?.romaji,
        titleEnglish = media?.title?.english,
        titleNative = media?.title?.native,
        cover = media?.coverImage?.large,
        siteUrl = media?.siteUrl
    )
}
