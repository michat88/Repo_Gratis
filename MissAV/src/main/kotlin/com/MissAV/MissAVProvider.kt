package com.MissAV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class MissAVProvider : MainAPI() {
    override var mainUrl = "https://missav.ws"
    override var name = "MissAV"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    // --- 1. DEFINISI KATEGORI UTAMA ---
    // Menggunakan variable dinamis agar tidak rusak jika 'dm628' berubah
    override val mainPage = mainPageOf(
        "$mainUrl/$lang/uncensored-leak" to "Kebocoran Tanpa Sensor",
        "$mainUrl/$lang/release" to "Keluaran Terbaru",
        "$mainUrl/$lang/new" to "Recent Update",
        "$mainUrl/$lang/genres/Wanita%20Menikah/Ibu%20Rumah%20Tangga" to "Wanita Menikah"
    )

    // --- 2. MAIN PAGE (Updated Selector) ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        // Support pagination: Tambahkan ?page=X jika page > 1
        val url = if (page > 1) "${request.data}?page=$page" else request.data
        
        val document = app.get(url).document
        val homeItems = ArrayList<SearchResponse>()

        // UPDATE SELECTOR: Berdasarkan HTML, item ada di class "thumbnail"
        document.select("div.thumbnail").forEach { element ->
            val linkElement = element.selectFirst("a.text-secondary") ?: return@forEach
            val href = linkElement.attr("href")
            val fixedUrl = fixUrl(href)
            
            val title = linkElement.text().trim()
            
            // Poster ada di elemen img sebelumnya
            val img = element.selectFirst("img")
            val posterUrl = img?.attr("data-src") ?: img?.attr("src")

            homeItems.add(newMovieSearchResponse(title, fixedUrl, TvType.NSFW) {
                this.posterUrl = posterUrl
            })
        }
        
        return newHomePageResponse(request.name, homeItems, isHorizontal = true)
    }

    // --- 3. SEARCH (Updated Selector) ---
    override suspend fun search(query: String): List<SearchResponse> {
        val fixedQuery = query.trim().replace(" ", "-")
        val url = "$mainUrl/$lang/search/$fixedQuery"
        
        return try {
            val document = app.get(url).document
            val results = ArrayList<SearchResponse>()

            // UPDATE SELECTOR: Sama dengan Main Page
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

    // --- 4. LOAD (Detail Video) ---
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        // Mengambil judul dari h1
        val title = document.selectFirst("h1.text-base")?.text()?.trim() ?: "Unknown Title"
        
        // Poster resolusi tinggi dari meta tag
        val poster = document.selectFirst("meta[property='og:image']")?.attr("content")
            ?: document.selectFirst("video.player")?.attr("poster")

        // Deskripsi
        val description = document.selectFirst("div.text-secondary.mb-2")?.text()?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, LinkData(url)) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // --- 5. LOAD LINKS (Player) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        val text = app.get(data).text
        val m3u8Regex = Regex("""(https:\\/\\/[a-zA-Z0-9\-\._~:\/\?#\[\]@!$&'\(\)*+,;=]+?\.m3u8)""")
        val matches = m3u8Regex.findAll(text)
        
        if (matches.count() > 0) {
            matches.forEach { match ->
                val rawUrl = match.groupValues[1]
                val fixedUrl = rawUrl.replace("\\/", "/")

                val quality = when {
                    fixedUrl.contains("1280x720") || fixedUrl.contains("720p") -> Qualities.P720.value
                    fixedUrl.contains("1920x1080") || fixedUrl.contains("1080p") -> Qualities.P1080.value
                    fixedUrl.contains("842x480") || fixedUrl.contains("480p") -> Qualities.P480.value
                    fixedUrl.contains("240p") -> Qualities.P240.value
                    else -> Qualities.Unknown.value
                }

                val sourceName = if (fixedUrl.contains("surrit")) "Surrit (HD)" else "MissAV (Backup)"

                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "$sourceName $quality",
                        url = fixedUrl,
                        referer = data,
                        quality = quality,
                        type = ExtractorLinkType.M3U8
                    )
                )
            }
            return true
        }
        return false
    }
}
