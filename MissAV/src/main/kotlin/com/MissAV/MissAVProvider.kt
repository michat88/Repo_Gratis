package com.MissAV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import org.jsoup.nodes.Element

@OptIn(com.lagradost.cloudstream3.Prerelease::class)
class MissAVProvider : MainAPI() {
    override var mainUrl = "https://missav.ws"
    override var name = "MissAV"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "$mainUrl/$lang/uncensored-leak" to "Kebocoran Tanpa Sensor",
        "$mainUrl/$lang/release" to "Keluaran Terbaru",
        "$mainUrl/$lang/new" to "Recent Update",
        "$mainUrl/$lang/genres/Wanita%20Menikah/Ibu%20Rumah%20Tangga" to "Wanita Menikah"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page > 1) "${request.data}?page=$page" else request.data
        val document = app.get(url).document
        val homeItems = ArrayList<SearchResponse>()

        document.select("div.thumbnail").forEach { element ->
            val linkElement = element.selectFirst("a.text-secondary") ?: return@forEach
            val href = linkElement.attr("href")
            val fixedUrl = fixUrl(href)
            val title = linkElement.text().trim()
            val img = element.selectFirst("img")
            val posterUrl = img?.attr("data-src") ?: img?.attr("src")

            homeItems.add(newMovieSearchResponse(title, fixedUrl, TvType.NSFW) {
                this.posterUrl = posterUrl
            })
        }
        
        return newHomePageResponse(
            HomePageList(name = request.name, list = homeItems, isHorizontalImages = true),
            hasNext = true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // --- LOGIKA PENCARIAN FINAL (ANTI-BLOKIR) ---
        // 1. Encoding Manual: Spasi -> %20 (Wajib, sesuai cURL browser)
        val fixedQuery = query.trim().replace(" ", "%20")
        val url = "$mainUrl/$lang/search/$fixedQuery"
        
        // 2. Headers Lengkap: Meniru Browser Chrome Android (Sesuai cURL)
        val headers = mapOf(
            "Referer" to "$mainUrl/",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "same-origin",
            "Upgrade-Insecure-Requests" to "1"
        )

        return try {
            val document = app.get(url, headers = headers).document
            val results = ArrayList<SearchResponse>()

            document.select("div.thumbnail").forEach { element ->
                val linkElement = element.selectFirst("a.text-secondary") ?: return@forEach
                val href = linkElement.attr("href")
                val fixedUrl = fixUrl(href)
                val title = linkElement.text().trim()
                val img = element.selectFirst("img")
                val posterUrl = img?.attr("data-src") ?: img?.attr("src")

                results.add(newMovieSearchResponse(title, fixedUrl, TvType.NSFW) {
                    this.posterUrl = posterUrl
                })
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            ArrayList()
        }
    }

    // --- FUNGSI LOAD (DETAIL VIDEO + REKOMENDASI MAX 20 ITEM) ---
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.selectFirst("h1.text-base")?.text()?.trim() ?: "Unknown Title"
        val poster = document.selectFirst("meta[property='og:image']")?.attr("content")
            ?: document.selectFirst("video.player")?.attr("poster")
        val description = document.select("div.text-secondary")
            .maxByOrNull { it.text().length }?.text()?.trim()
            ?: document.selectFirst("meta[property='og:description']")?.attr("content")

        // === FITUR SARAN FILM (TARGET 20 ITEM) ===
        val recommendations = ArrayList<SearchResponse>()
        
        try {
            // Prioritas: Aktris -> Pembuat -> Genre
            var recUrl = document.selectFirst("div.text-secondary a[href*='/actresses/']")?.attr("href")
            
            if (recUrl == null) {
                recUrl = document.selectFirst("div.text-secondary a[href*='/makers/']")?.attr("href")
            }
            if (recUrl == null) {
                recUrl = document.selectFirst("div.text-secondary a[href*='/genres/']")?.attr("href")
            }

            if (recUrl != null) {
                val baseUrl = fixUrl(recUrl)
                var page = 1
                
                // LOOPING: Ambil halaman 1, 2, dst sampai terkumpul 20 item
                while (recommendations.size < 20 && page <= 3) {
                    val targetUrl = if (page > 1) "$baseUrl?page=$page" else baseUrl
                    val recDoc = app.get(targetUrl).document
                    val items = recDoc.select("div.thumbnail")
                    
                    if (items.isEmpty()) break 

                    for (element in items) {
                        if (recommendations.size >= 20) break

                        val linkElement = element.selectFirst("a.text-secondary") ?: continue
                        val href = linkElement.attr("href")
                        val fixedVideoUrl = fixUrl(href)

                        // Jangan masukkan video yang sedang ditonton
                        if (fixedVideoUrl != url) {
                            val recTitle = linkElement.text().trim()
                            val img = element.selectFirst("img")
                            val recPoster = img?.attr("data-src") ?: img?.attr("src")

                            if (!recPoster.isNullOrEmpty()) {
                                // Cek duplikat
                                val isDuplicate = recommendations.any { it.url == fixedVideoUrl }
                                if (!isDuplicate) {
                                    recommendations.add(
                                        newMovieSearchResponse(recTitle, fixedVideoUrl, TvType.NSFW) {
                                            this.posterUrl = recPoster
                                        }
                                    )
                                }
                            }
                        }
                    }
                    page++ // Lanjut ke halaman berikutnya
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    }

    // --- LOGIKA SUBTITLE AKURAT (STRICT FILTER) ---
    private suspend fun fetchSubtitleCat(code: String, subtitleCallback: (SubtitleFile) -> Unit) {
        try {
            val searchUrl = "https://www.subtitlecat.com/index.php?search=$code"
            val searchDoc = app.get(searchUrl).document
            
            val searchResults = searchDoc.select("table.sub-table tbody tr td:nth-child(1) > a")
            
            searchResults.take(15).forEach { linkElement ->
                val resultTitle = linkElement.text().trim()
                
                // FILTER: Judul subtitle WAJIB mengandung Kode Video (misal: SSNI-528)
                if (resultTitle.contains(code, ignoreCase = true)) {
                    var detailPath = linkElement.attr("href")
                    if (!detailPath.startsWith("http")) {
                        detailPath = if (detailPath.startsWith("/")) detailPath else "/$detailPath"
                        detailPath = "https://www.subtitlecat.com$detailPath"
                    }

                    try {
                        val detailDoc = app.get(detailPath).document
                        
                        detailDoc.select("div.sub-single").forEach { item ->
                            val rawLang = item.select("span").getOrNull(1)?.text()?.trim() ?: "Unknown"
                            val downloadEl = item.selectFirst("a.green-link")
                            val downloadHref = downloadEl?.attr("href")

                            if (downloadHref != null) {
                                val finalUrl = if (downloadHref.startsWith("http")) {
                                    downloadHref
                                } else {
                                    "https://www.subtitlecat.com$downloadHref"
                                }
                                
                                subtitleCallback.invoke(
                                    SubtitleFile(
                                        lang = rawLang, // Biarkan nama asli agar player grouping 1,2,3
                                        url = finalUrl
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Skip error per item
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION_ERROR") 
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        var text = app.get(data).text
        text = getAndUnpack(text) 

        val m3u8Regex = Regex("""(https?:\\?\/\\?\/[^"']+\.m3u8)""")
        val matches = m3u8Regex.findAll(text)
        
        val uniqueUrls = matches.map { 
            it.groupValues[1].replace("\\/", "/") 
        }.toSet()

        val addedNames = mutableListOf<String>()

        if (uniqueUrls.isNotEmpty()) {
            uniqueUrls.forEach { fixedUrl ->
                val sourceName = if (fixedUrl.contains("surrit")) "Surrit" else "MissAV"
                
                if (!addedNames.contains(sourceName)) {
                    addedNames.add(sourceName)
                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = sourceName,
                            url = fixedUrl,
                            referer = data,
                            quality = Qualities.Unknown.value,
                            isM3u8 = true
                        )
                    )
                }
            }

            // --- PROSES KODE ID & SUBTITLE ---
            val codeRegex = Regex("""([a-zA-Z]{2,5}-\d{3,5})""")
            val codeMatch = codeRegex.find(data)
            val code = codeMatch?.value
            
            if (code != null) {
                try {
                    fetchSubtitleCat(code, subtitleCallback)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            return true
        }
        return false
    }
}
