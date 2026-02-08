package com.LayarKacaProvider

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.net.URI

class LayarKacaProvider : MainAPI() {
    override var mainUrl = "https://tv8.lk21official.cc"
    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Header khusus meniru browser Linux Chrome (sesuai analisa cURL)
    private val turboHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
        "Sec-Fetch-Dest" to "iframe",
        "Sec-Fetch-Site" to "cross-site",
        "Upgrade-Insecure-Requests" to "1"
    )

    // --- MAIN PAGE ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val items = ArrayList<HomePageList>()

        fun addWidget(title: String, selector: String) {
            val list = document.select(selector).mapNotNull { toSearchResult(it) }
            if (list.isNotEmpty()) items.add(HomePageList(title, list))
        }

        addWidget("Film Terbaru", "div.widget[data-type='latest-movies'] li.slider article")
        addWidget("Series Unggulan", "div.widget[data-type='top-series-today'] li.slider article")
        addWidget("Horror Terbaru", "div.widget[data-type='latest-horror'] li.slider article")
        addWidget("Daftar Lengkap", "div#post-container article")

        return newHomePageResponse(items)
    }

    // --- SEARCH ---
    data class Lk21SearchResponse(val data: List<Lk21SearchItem>?)
    data class Lk21SearchItem(val title: String, val slug: String, val poster: String?, val type: String?, val year: Int?, val quality: String?)

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "https://gudangvape.com/search.php?s=$query&page=1"
        val headers = mapOf(
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/",
            "User-Agent" to turboHeaders["User-Agent"]!!
        )

        try {
            val response = app.get(searchUrl, headers = headers).text
            val json = tryParseJson<Lk21SearchResponse>(response)

            return json?.data?.mapNotNull { item ->
                val title = item.title
                val href = fixUrl(item.slug)
                val posterUrl = if (item.poster != null) "https://poster.lk21.party/wp-content/uploads/${item.poster}" else null
                val quality = getQualityFromString(item.quality)
                val isSeries = item.type?.contains("series", ignoreCase = true) == true

                if (isSeries) {
                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                        this.quality = quality
                        this.year = item.year
                    }
                } else {
                    newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = posterUrl
                        this.quality = quality
                        this.year = item.year
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val title = element.select("h3.poster-title, h2.entry-title, h1.page-title, div.title").text().trim()
        if (title.isEmpty()) return null
        val href = fixUrl(element.select("a").first()?.attr("href") ?: return null)
        val imgElement = element.select("img").first()
        val posterUrl = imgElement?.attr("src") ?: imgElement?.attr("data-src")
        val quality = getQualityFromString(element.select("span.label").text())
        val isSeries = element.select("span.episode").isNotEmpty() || element.select("span.duration").text().contains("S.")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    // --- LOAD DETAIL ---
    data class NontonDramaEpisode(val s: Int? = null, val episode_no: Int? = null, val title: String? = null, val slug: String? = null)

    override suspend fun load(url: String): LoadResponse {
        var cleanUrl = fixUrl(url)
        var response = app.get(cleanUrl)
        var document = response.document

        val redirectButton = document.select("a:contains(Buka Sekarang), a.btn:contains(Nontondrama)").first()
        if (redirectButton != null) {
            val newUrl = redirectButton.attr("href")
            if (newUrl.isNotEmpty()) {
                cleanUrl = fixUrl(newUrl)
                response = app.get(cleanUrl)
                document = response.document
            }
        }

        val title = document.select("h1.entry-title, h1.page-title, div.movie-info h1").text().trim()
        val plot = document.select("div.synopsis, div.entry-content p").text().trim()
        val poster = document.select("meta[property='og:image']").attr("content").ifEmpty { document.select("div.poster img").attr("src") }
        val ratingText = document.select("span.rating-value").text().ifEmpty { document.select("div.info-tag").text() }
        val ratingScore = Regex("(\\d\\.\\d)").find(ratingText)?.value
        val year = document.select("span.year").text().toIntOrNull() ?: Regex("(\\d{4})").find(document.select("div.info-tag").text())?.value?.toIntOrNull()
        val tags = document.select("div.tag-list a, div.genre a").map { it.text() }
        val actors = document.select("div.detail p:contains(Bintang Film) a, div.cast a").map { ActorData(Actor(it.text(), "")) }
        val recommendations = document.select("div.related-video li.slider article, div.mob-related-series li.slider article").mapNotNull { toSearchResult(it) }

        val episodes = ArrayList<Episode>()
        val jsonScript = document.select("script#season-data").html()

        if (jsonScript.isNotBlank()) {
            tryParseJson<Map<String, List<NontonDramaEpisode>>>(jsonScript)?.forEach { (_, epsList) ->
                epsList.forEach { epData ->
                    episodes.add(newEpisode(fixUrl(epData.slug ?: "")) {
                        this.name = epData.title ?: "Episode ${epData.episode_no}"
                        this.season = epData.s
                        this.episode = epData.episode_no
                    })
                }
            }
        } else {
            document.select("ul.episodes li a").forEach {
                episodes.add(newEpisode(fixUrl(it.attr("href"))) {
                    this.name = it.text()
                    val epNum = Regex("(?i)Episode\\s+(\\d+)").find(it.text())?.groupValues?.get(1)?.toIntOrNull()
                    this.episode = epNum
                })
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, cleanUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster; this.plot = plot; this.year = year
                this.score = Score.from(ratingScore, 10); this.tags = tags; this.actors = actors; this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, cleanUrl, TvType.Movie, cleanUrl) {
                this.posterUrl = poster; this.plot = plot; this.year = year
                this.score = Score.from(ratingScore, 10); this.tags = tags; this.actors = actors; this.recommendations = recommendations
            }
        }
    }

    // --- LOAD LINKS ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Cari iframe utama
        val rawIframeUrl = document.select("iframe#main-player").attr("src")
        var mainIframeUrl = fixUrl(rawIframeUrl)

        // Fallback jika iframe utama kosong
        if (mainIframeUrl.isEmpty()) {
            mainIframeUrl = document.select("ul#player-list li a").firstOrNull()?.attr("data-url")?.let { fixUrl(it) } ?: ""
        }

        if (mainIframeUrl.isNotBlank()) {
            if (mainIframeUrl.contains("playeriframe.sbs") || mainIframeUrl.contains("turbovid") || mainIframeUrl.contains("emturbovid")) {
                // Ekstraktor khusus TurboVid
                extractTurboVid(mainIframeUrl, data, callback)
            } else {
                loadExtractor(mainIframeUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }

    // --- CUSTOM EXTRACTOR FOR TURBOVID / PLAYERIFRAME ---
    private suspend fun extractTurboVid(url: String, referer: String, callback: (ExtractorLink) -> Unit) {
        try {
            // 1. Request ke Wrapper (playeriframe.sbs)
            val wrapperHeaders = turboHeaders.toMutableMap()
            wrapperHeaders["Referer"] = referer

            val responseWrapper = app.get(url, headers = wrapperHeaders)
            var targetUrl = responseWrapper.url
            var pageContent = responseWrapper.text

            // Cek jika ada inner iframe (turbovidhls.com)
            val soup = responseWrapper.document
            val innerIframe = soup.select("iframe").attr("src")
            if (innerIframe.isNotEmpty() && !targetUrl.contains("turbovid") && !targetUrl.contains("emturbovid")) {
                targetUrl = fixUrl(innerIframe)
                pageContent = app.get(targetUrl, headers = mapOf("Referer" to "https://playeriframe.sbs/")).text
            }

            // 2. Ambil Link M3U8
            val m3u8Regex = Regex("(?i)(?:file|source)\\s*:\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']")
            val match = m3u8Regex.find(pageContent)

            if (match != null) {
                val m3u8Url = match.groupValues[1]
                
                // --- LOGIKA HEADER FINAL (BERDASARKAN ANALISIS AVATAR) ---
                // Server 'turbosplayer' dan 'cdn4' sangat ketat soal Origin.
                // Kita hardcode Origin ke 'https://turbovidhls.com' agar aman.
                val origin = "https://turbovidhls.com"

                val videoHeaders = mapOf(
                    "Origin" to origin,
                    "Referer" to "$origin/", // Referer diset sama dengan Origin agar konsisten
                    "User-Agent" to turboHeaders["User-Agent"]!!,
                    "Accept" to "*/*",
                    "Sec-Fetch-Site" to "cross-site",
                    "Sec-Fetch-Mode" to "cors"
                )

                callback.invoke(
                    newExtractorLink(
                        source = "LK21 VIP",
                        name = "LK21 VIP (Turbo)",
                        url = m3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$origin/"
                        this.quality = Qualities.Unknown.value
                        this.headers = videoHeaders
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
