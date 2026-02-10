package com.MissAV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack // Fungsi untuk membongkar Packed JS
import org.jsoup.nodes.Element

class MissAVProvider : MainAPI() {
    override var mainUrl = "https://missav.ws"
    override var name = "MissAV"
    override val hasMainPage = true
    override var lang = "id" // Mengatur bahasa default ke Indonesia
    override val supportedTypes = setOf(TvType.NSFW)

    // --- 1. DAFTAR KATEGORI UTAMA ---
    override val mainPage = mainPageOf(
        "$mainUrl/$lang/uncensored-leak" to "Kebocoran Tanpa Sensor",
        "$mainUrl/$lang/release" to "Keluaran Terbaru",
        "$mainUrl/$lang/new" to "Recent Update",
        "$mainUrl/$lang/genres/Wanita%20Menikah/Ibu%20Rumah%20Tangga" to "Wanita Menikah"
    )

    // --- 2. HALAMAN DEPAN (BROWSE) ---
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
        
        // Membungkus ke HomePageList agar mendukung tampilan Horizontal
        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = homeItems,
                isHorizontalImages = true 
            ),
            hasNext = true
        )
    }

    // --- 3. PENCARIAN (SEARCH) ---
    override suspend fun search(query: String): List<SearchResponse> {
        val fixedQuery = query.trim().replace(" ", "-")
        val url = "$mainUrl/$lang/search/$fixedQuery"
        
        return try {
            val document = app.get(url).document
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

    // --- 4. DETAIL FILM ---
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.text-base")?.text()?.trim() ?: "Unknown Title"
        val poster = document.selectFirst("meta[property='og:image']")?.attr("content")
            ?: document.selectFirst("video.player")?.attr("poster")
        
        // Logika cerdas mencari deskripsi teks terpanjang
        val description = document.select("div.text-secondary")
            .maxByOrNull { it.text().length }?.text()?.trim()
            ?: document.selectFirst("meta[property='og:description']")?.attr("content")

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // --- 5. LOGIKA SUBTITLE (SUBTITLECAT - KHUSUS INDONESIA) ---
    private suspend fun fetchSubtitleCat(code: String, subtitleCallback: (SubtitleFile) -> Unit) {
        try {
            // Pencarian berdasarkan kode film unik (contoh: ADN-753)
            val searchUrl = "https://www.subtitlecat.com/index.php?search=$code"
            val searchDoc = app.get(searchUrl).document
            
            // Mencari baris hasil yang mengandung kode film tersebut
            val searchResults = searchDoc.select("table.sub-table tbody tr td:nth-child(1) > a")
            val targetResult = searchResults.find { it.text().contains(code, ignoreCase = true) } ?: searchResults.firstOrNull()

            if (targetResult != null) {
                var detailPath = targetResult.attr("href")
                if (!detailPath.startsWith("http")) {
                    detailPath = if (detailPath.startsWith("/")) detailPath else "/$detailPath"
                    detailPath = "https://www.subtitlecat.com$detailPath"
                }

                val detailDoc = app.get(detailPath).document
                
                // Mencari semua kotak subtitle yang tersedia [berdasarkan analisa HTML user]
                detailDoc.select("div.sub-single").forEach { item ->
                    val langText = item.select("span").getOrNull(1)?.text()?.trim() ?: ""
                    val downloadEl = item.selectFirst("a.green-link") // Mengambil link tombol hijau
                    val downloadHref = downloadEl?.attr("href")

                    if (downloadHref != null) {
                        // FILTER: Hanya ambil yang label bahasanya "Indonesian"
                        // Hal ini dilakukan untuk menghindari subtitle sampah/bahasa lain
                        if (langText.contains("Indonesian", ignoreCase = true)) {
                            val finalUrl = if (downloadHref.startsWith("http")) {
                                downloadHref
                            } else {
                                "https://www.subtitlecat.com$downloadHref"
                            }
                            
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    lang = "Indonesian",
                                    url = finalUrl
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- 6. EKSTRAKSI VIDEO DAN SUBTITLE ---
    @Suppress("DEPRECATION_ERROR") // Mengizinkan penggunaan constructor ExtractorLink yang stabil
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        // A. Ambil Link Video
        var text = app.get(data).text
        text = getAndUnpack(text) // Unpack JS untuk mendapatkan link m3u8 asli

        val m3u8Regex = Regex("""(https?:\\?\/\\?\/[^"']+\.m3u8)""")
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

                // Mengirim link video ke player
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "$sourceName $quality",
                        url = fixedUrl,
                        referer = data,
                        quality = quality,
                        isM3u8 = true 
                    )
                )
            }

            // B. Cari Subtitle (Setelah link video berhasil dikirim)
            // Mengambil kode film dari URL menggunakan Regex
            val codeRegex = Regex("""([a-zA-Z]{2,5}-\d{3,5})""")
            val codeMatch = codeRegex.find(data)
            val code = codeMatch?.value
            
            if (code != null) {
                // Eksekusi pencarian subtitle di SubtitleCat secara sequential agar aman
                fetchSubtitleCat(code, subtitleCallback)
            }

            return true
        }
        return false
    }
}
