package com.moviebox

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import java.net.URI

class MovieBoxProvider : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    override var name = "MovieBox"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/web/movie?page=" to "Movies",
        "$mainUrl/web/tv-series?page=" to "TV Series",
        "$mainUrl/web/animated-series?page=" to "Anime",
        "$mainUrl/ranking-list?page=" to "Most Watched"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document
        
        val home = document.select("div.movie-item, div.video-item, a.movie-card").mapNotNull {
            it.toSearchResult()
        }
        
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h1, h2, h3, h4, .title, .name")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(
            this.selectFirst("img")?.attr("src") 
                ?: this.selectFirst("img")?.attr("data-src")
                ?: this.selectFirst("img")?.attr("data-original")
        )
        
        val year = this.selectFirst(".year, .date")?.text()?.trim()?.toIntOrNull()
        val quality = getQualityFromString(this.selectFirst(".quality")?.text())
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.year = year
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?keyword=${query}"
        val document = app.get(searchUrl).document
        
        return document.select("div.movie-item, div.search-item, a.movie-card").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title, h1, h2.title")?.text()?.trim() 
            ?: throw ErrorLoadingException("Title not found")
        
        val poster = fixUrlNull(
            document.selectFirst("img.poster, .movie-poster img, .video-poster img")?.attr("src")
                ?: document.selectFirst("img")?.attr("src")
        )
        
        val year = document.selectFirst(".year, .release-year, .date")?.text()?.trim()
            ?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
        
        val tvType = if (url.contains("/tv/") || url.contains("/tv-series/") || 
                        document.select(".episode-item, .season").isNotEmpty()) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }
        
        val description = document.selectFirst(".synopsis, .description, .overview, .plot")?.text()?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")
        
        val rating = document.selectFirst(".rating, .score, .imdb")?.text()?.trim()
            ?.replace(Regex("[^0-9.]"), "")?.toIntOrNull()
        
        val tags = document.select(".genre, .tag, .category").mapNotNull { 
            it.text().trim().takeIf { it.isNotEmpty() } 
        }
        
        val actors = document.select(".cast .actor, .cast a, .starring a").mapNotNull {
            val name = it.text().trim()
            if (name.isNotEmpty() && name.length < 50) {
                Actor(name)
            } else null
        }
        
        val trailerUrl = document.selectFirst("iframe[src*=youtube], iframe[src*=vimeo], a[href*=youtube][href*=trailer]")
            ?.attr("src") ?: document.selectFirst("a[href*=youtube]")?.attr("href")
        
        val recommendations = document.select(".recommended .movie-item, .similar .movie-item").mapNotNull {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select(".episode-item, .episode").mapNotNull { ep ->
                val epName = ep.selectFirst(".episode-title, .title")?.text()?.trim()
                val epHref = fixUrlNull(ep.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val epNum = ep.selectFirst(".episode-number, .ep-num")?.text()?.trim()
                    ?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
                val season = ep.selectFirst(".season-number")?.text()?.trim()
                    ?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 1
                
                Episode(
                    data = epHref,
                    name = epName,
                    episode = epNum,
                    season = season
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.rating = rating
                this.tags = tags
                addActors(actors)
                addTrailer(trailerUrl)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.rating = rating
                this.tags = tags
                addActors(actors)
                addTrailer(trailerUrl)
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Extract video sources from the page
        // Look for player iframes or video links
        document.select("iframe[src*=stream], iframe[src*=embed], iframe.player").forEach { iframe ->
            val iframeUrl = fixUrl(iframe.attr("src"))
            loadExtractor(iframeUrl, subtitleCallback, callback)
        }
        
        // Look for direct video links
        document.select("a[href*=.mp4], a[href*=.m3u8], source").forEach { source ->
            val videoUrl = fixUrl(source.attr("href") ?: source.attr("src"))
            if (videoUrl.isNotEmpty()) {
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        videoUrl,
                        mainUrl,
                        Qualities.Unknown.value,
                        videoUrl.contains(".m3u8")
                    )
                )
            }
        }
        
        // Look for download links
        document.select("a.download-btn, a[href*=download], button.download").forEach { dl ->
            val dlUrl = fixUrlNull(dl.attr("href")) ?: return@forEach
            val quality = dl.text().let { getQualityFromString(it) }
            
            callback.invoke(
                ExtractorLink(
                    "$name Download",
                    "$name Download",
                    dlUrl,
                    mainUrl,
                    quality ?: Qualities.Unknown.value,
                    false
                )
            )
        }
        
        // Look for embedded players in script tags
        document.select("script").forEach { script ->
            val scriptContent = script.html()
            
            // Extract m3u8 links
            Regex("""(https?://[^"'\s]+\.m3u8[^"'\s]*)""").findAll(scriptContent).forEach { match ->
                val videoUrl = match.groupValues[1]
                callback.invoke(
                    ExtractorLink(
                        name,
                        "$name HLS",
                        videoUrl,
                        mainUrl,
                        Qualities.Unknown.value,
                        true
                    )
                )
            }
            
            // Extract mp4 links
            Regex("""(https?://[^"'\s]+\.mp4[^"'\s]*)""").findAll(scriptContent).forEach { match ->
                val videoUrl = match.groupValues[1]
                callback.invoke(
                    ExtractorLink(
                        name,
                        "$name MP4",
                        videoUrl,
                        mainUrl,
                        Qualities.Unknown.value,
                        false
                    )
                )
            }
        }

        return true
    }
}
