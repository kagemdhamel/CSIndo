package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

class Moviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    
    // SERVER UTAMA (Stabil)
    private val mainAPIUrl = "https://h5-api.aoneroom.com"

    override val instantLinkLoading = true
    override var name = "Moviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    // --- CUSTOM CLIENT (HTTP/1.1) ---
    private val customClient by lazy {
        app.baseClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1)) // Paksa HTTP/1.1
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // --- REQUEST HELPER ---
    private fun request(url: String, method: String = "GET", body: RequestBody? = null, referer: String? = null): String? {
        val reqBuilder = Request.Builder()
            .url(url)
            .method(method, body)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
        
        val finalReferer = referer ?: "$mainUrl/"
        reqBuilder.header("Referer", finalReferer)
        reqBuilder.header("Origin", mainUrl)

        return try {
            val response = customClient.newCall(reqBuilder.build()).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                // Return null jika 404/403/500
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- FILTER ANTI-PHILIPPINES ---
    private fun isSafe(item: Items): Boolean {
        val country = (item.countryName ?: "").lowercase()
        val title = (item.title ?: "").lowercase()
        val genre = (item.genre ?: "").lowercase()
        val dirtyKeywords = listOf("philippines", "filipina", "pinoy", "18+")
        return dirtyKeywords.none { 
            country.contains(it) || title.contains(it) || genre.contains(it)
        }
    }

    override val mainPage: List<MainPageData> = mainPageOf(
        "872031290915189720" to "Trending Now",
        "997144265920760504" to "Popular Movie",
        "5283462032510044280" to "Drama Indonesia Terkini",
        "6528093688173053896" to "Trending Indonesian Movies",
        "4380734070238626200" to "K-Drama",
        "7736026911486755336" to "Western TV",
        "8624142774394406504" to "Most Popular C-Drama",
        "5404290953194750296" to "Trending Anime",
        "5848753831881965888" to "Indonesian Horror Stories",
        "1164329479448281992" to "Thai-Drama",
        "7132534597631837112" to "Animated Film",
        "1,ForYou" to "Movie ForYou",
        "1,Hottest" to "Movie Hottest",
        "1,Latest" to "Movie Latest",
        "1,Rating" to "Movie Rating",
        "2,ForYou" to "TVShow ForYou",
        "2,Hottest" to "TVShow Hottest",
        "2,Latest" to "TVShow Latest",
        "2,Rating" to "TVShow Rating",
        "1006,ForYou" to "Animation ForYou",
        "1006,Hottest" to "Animation Hottest",
        "1006,Latest" to "Animation Latest",
        "1006,Rating" to "Animation Rating",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val home = mutableListOf<SearchResponse>()

        // NOTE: Home & List menggunakan 'wefeed-h5api-bff'
        if(!request.data.contains(",")) {
            val url = "$mainAPIUrl/wefeed-h5api-bff/ranking-list/content?id=${request.data}&page=$page&perPage=12"
            
            val json = request(url) ?: throw ErrorLoadingException("No Data Found")
            
            val index = parseJson<Media>(json).data?.subjectList
                ?.filter { isSafe(it) }
                ?.map { it.toSearchResponse(this) } 
                ?: throw ErrorLoadingException("No Data Found")
            home.addAll(index)
        } else {
            val params = request.data.split(",")
            val bodyMap = mapOf(
                "channelId" to params.first(),
                "page" to page,
                "perPage" to "28",
                "sort" to params.last()
            )
            
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = RequestBody.create(mediaType, bodyMap.toJson())

            val json = request("$mainAPIUrl/wefeed-h5api-bff/subject/filter", "POST", body) 
                ?: throw ErrorLoadingException("No Data Found")
                
            val index = parseJson<Media>(json).data?.items
                ?.filter { isSafe(it) }
                ?.map { it.toSearchResponse(this) } 
                ?: throw ErrorLoadingException("No Data Found")
            home.addAll(index)
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val bodyMap = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "0",
            "subjectType" to "0",
        )
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, bodyMap.toJson())

        // PERBAIKAN: Search menggunakan 'wefeed-h5-bff' (tanpa 'api')
        val json = request("$mainAPIUrl/wefeed-h5-bff/web/subject/search", "POST", body) 
            ?: throw ErrorLoadingException()
            
        return parseJson<Media>(json).data?.items
            ?.filter { isSafe(it) }
            ?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        
        // PERBAIKAN: Detail menggunakan 'wefeed-h5-bff' (tanpa 'api')
        val json = request("$mainAPIUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id") 
            ?: throw ErrorLoadingException("Error loading detail")
            
        val document = parseJson<MediaDetail>(json).data
        val subject = document?.subject
        
        if (subject != null && !isSafe(subject)) {
             throw ErrorLoadingException("Restricted Content (Country/Genre)")
        }

        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }
        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val rating = subject?.imdbRatingValue?.toDoubleOrNull()
        
        val actors = document?.stars?.mapNotNull { cast ->
            ActorData(
                Actor(cast.name ?: return@mapNotNull null, cast.avatarUrl),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        // PERBAIKAN: Rekomendasi menggunakan 'wefeed-h5-bff'
        val recJson = request("$mainAPIUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
        val recommendations = recJson?.let { parseJson<Media>(it).data?.items }
            ?.filter { isSafe(it) }
            ?.map { it.toSearchResponse(this) }

        return if (tvType == TvType.TvSeries) {
            val episode = document?.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..seasons.maxEp!!) else seasons.allEp.split(",")
                    .map { it.toInt() })
                    .map { episode ->
                        newEpisode(
                            LoadData(id, seasons.se, episode, subject?.detailPath).toJson()
                        ) {
                            this.season = seasons.se
                            this.episode = episode
                        }
                    }
            }?.flatten() ?: emptyList()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episode) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            newMovieLoadResponse(
                title, url, TvType.Movie,
                LoadData(id, detailPath = subject?.detailPath).toJson()
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val media = parseJson<LoadData>(data)
        val referer = "$mainAPIUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"

        // PERBAIKAN: Play menggunakan 'wefeed-h5-bff'
        val streamJson = request(
            "$mainAPIUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}",
            referer = referer
        ) ?: return false

        val streams = parseJson<Media>(streamJson).data?.streams

        streams?.reversed()?.distinctBy { it.url }?.map { source ->
            callback.invoke(
                newExtractorLink(
                    this.name, this.name, source.url ?: return@map, INFER_TYPE
                ) {
                    this.referer = "$mainAPIUrl/"
                    this.quality = getQualityFromName(source.resolutions)
                }
            )
        }

        val id = streams?.first()?.id
        val format = streams?.first()?.format

        // PERBAIKAN: Caption menggunakan 'wefeed-h5-bff'
        val subJson = request(
            "$mainAPIUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}",
            referer = referer
        )
        
        subJson?.let {
            parseJson<Media>(it).data?.captions?.map { subtitle ->
                subtitleCallback.invoke(
                    newSubtitleFile(subtitle.lanName ?: "", subtitle.url ?: return@map)
                )
            }
        }

        return true
    }

    data class LoadData(
        val id: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val detailPath: String? = null,
    )

    data class Media(
        @JsonProperty("data") val data: Data? = null,
    ) {
        data class Data(
            @JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
            @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
            @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
            @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
        ) {
            data class Streams(
                @JsonProperty("id") val id: String? = null,
                @JsonProperty("format") val format: String? = null,
                @JsonProperty("url") val url: String? = null,
                @JsonProperty("resolutions") val resolutions: String? = null,
            )

            data class Captions(
                @JsonProperty("lan") val lan: String? = null,
                @JsonProperty("lanName") val lanName: String? = null,
                @JsonProperty("url") val url: String? = null,
            )
        }
    }

    data class MediaDetail(
        @JsonProperty("data") val data: Data? = null,
    ) {
        data class Data(
            @JsonProperty("subject") val subject: Items? = null,
            @JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
            @JsonProperty("resource") val resource: Resource? = null,
        ) {
            data class Stars(
                @JsonProperty("name") val name: String? = null,
                @JsonProperty("character") val character: String? = null,
                @JsonProperty("avatarUrl") val avatarUrl: String? = null,
            )

            data class Resource(
                @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
            ) {
                data class Seasons(
                    @JsonProperty("se") val se: Int? = null,
                    @JsonProperty("maxEp") val maxEp: Int? = null,
                    @JsonProperty("allEp") val allEp: String? = null,
                )
            }
        }
    }

    data class Items(
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("subjectType") val subjectType: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("duration") val duration: Long? = null,
        @JsonProperty("genre") val genre: String? = null,
        @JsonProperty("cover") val cover: Cover? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
        @JsonProperty("countryName") val countryName: String? = null,
        @JsonProperty("trailer") val trailer: Trailer? = null,
        @JsonProperty("detailPath") val detailPath: String? = null,
    ) {

        fun toSearchResponse(provider: Moviebox): SearchResponse {
            return provider.newMovieSearchResponse(
                title ?: "",
                subjectId ?: "",
                if (subjectType == 1) TvType.Movie else TvType.TvSeries,
                false
            ) {
                this.posterUrl = cover?.url
            }
        }

        data class Cover(
            @JsonProperty("url") val url: String? = null,
        )

        data class Trailer(
            @JsonProperty("videoAddress") val videoAddress: VideoAddress? = null,
        ) {
            data class VideoAddress(
                @JsonProperty("url") val url: String? = null,
            )
        }
    }
}
