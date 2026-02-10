package com.JavHey

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class JavHey : MainAPI() {
    override var mainUrl = "https://javhey.com"
    override var name = "JavHey"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Referer" to "$mainUrl/",
        "Connection" to "keep-alive"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/videos/paling-dilihat/page=" to "Paling Dilihat",
        "$mainUrl/videos/top-rating/page=" to "Top Rating",
        "$mainUrl/category/12/cuckold-or-ntr/page=" to "CUCKOLD OR NTR",
        "$mainUrl/category/31/decensored/page=" to "DECENSORED",
        "$mainUrl/category/21/drama/page=" to "Drama",
        "$mainUrl/category/114/female-investigator/page=" to "Investigasi",
        "$mainUrl/category/9/housewife/page=" to "HOUSEWIFE",
        "$mainUrl/category/227/hubungan-sedarah/page=" to "Inces",
        "$mainUrl/category/87/hot-spring/page=" to "Air Panas"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url, headers = headers, timeout = 30).document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("div.item_content h3 a") ?: return null
        val title = titleElement.text().trim()
        val href = fixUrl(titleElement.attr("href"))
        val imgTag = this.selectFirst("div.item_header img")
        val posterUrl = imgTag?.attr("data-src")?.takeIf { it.isNotEmpty() } ?: imgTag?.attr("src")
        
        return newMovieSearchResponse(title, href, TvType.NSFW) { 
            this.posterUrl = posterUrl 
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=$query"
        val document = app.get(url, headers = headers, timeout = 30).document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url, headers = headers, timeout = 30)
        val document = response.document
        val title = document.selectFirst("h1.product_title")?.text()?.trim() ?: "No Title"
        val description = document.select("p.video-description").text()
            .replace("Description: ", "", ignoreCase = true).trim()
        
        val imgTag = document.selectFirst("div.images img")
        val poster = imgTag?.attr("data-src")?.takeIf { it.isNotEmpty() } ?: imgTag?.attr("src")
        
        return newMovieLoadResponse(title, response.url, TvType.NSFW, response.url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Delegasikan tugas berat ke ExtractorManager
        val document = app.get(data, headers = headers, timeout = 30).document
        JavHeyExtractorManager.invoke(document, subtitleCallback, callback)
        return true
    }
}
