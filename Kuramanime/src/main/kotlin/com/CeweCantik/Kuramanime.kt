package com.CeweCantik

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Kuramanime : MainAPI() {
    override var mainUrl = "https://v8.kuramanime.blog"
    override var name = "Kuramanime"
    override var lang = "id"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    // ==========================================
    // BAGIAN 1: HALAMAN UTAMA (HOME)
    // ==========================================
    override val mainPage = mainPageOf(
        "$mainUrl/quick/ongoing?order_by=updated&page=" to "Sedang Tayang",
        "$mainUrl/quick/finished?order_by=updated&page=" to "Selesai Tayang",
        "$mainUrl/quick/movie?order_by=updated&page=" to "Film Layar Lebar",
        "$mainUrl/quick/donghua?order_by=updated&page=" to "Donghua"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document
        
        val home = document.select(".filter__gallery > a").mapNotNull { element ->
            toSearchResult(element)
        }

        return newHomePageResponse(request.name, home)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val title = element.selectFirst("h5.sidebar-title-h5")?.text()?.trim() ?: return null
        var href = fixUrl(element.attr("href"))
        
        // Membersihkan URL agar masuk ke halaman list episode, bukan langsung ke episode spesifik
        // Contoh: .../episode/6 -> .../
        if (href.contains("/episode/")) {
            val epsIndex = href.indexOf("/episode/")
            href = href.substring(0, epsIndex)
        }

        val imageDiv = element.selectFirst(".product__sidebar__view__item")
        val posterUrl = imageDiv?.attr("data-setbg")
        val epText = element.selectFirst(".ep")?.text()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addQuality(epText)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/anime?search=$query&order_by=latest"
        val document = app.get(url).document
        return document.select(".filter__gallery > a").mapNotNull {
            toSearchResult(it)
        }
    }

    // ==========================================
    // BAGIAN 2: DETAIL ANIME & LIST EPISODE
    // ==========================================
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Mengambil Judul
        // Dari Breadcrumb: Beranda > Anime > Judul
        // HTML kamu: <div class="breadcrumb__links"> ... <a>Judul</a> ... </div>
        // Kita ambil elemen <a> ketiga (index 2) atau cari judul di meta tag
        val title = document.select("meta[property=og:title]").attr("content")
            .replace("Subtitle Indonesia - Kuramanime", "")
            .replace(Regex("\\(Episode.*\\)"), "") // Hapus tulisan (Episode XX)
            .trim()

        // Mengambil Gambar
        val poster = document.select("meta[property=og:image]").attr("content")

        // Mengambil Deskripsi
        // HTML kamu menaruh keywords di content__tags, tapi biasanya ada div sinopsis.
        // Kita coba ambil dari meta description sebagai fallback
        val description = document.select("meta[name=description]").attr("content")

        // Mengambil Episode
        // Selector ID: #animeEpisodes, Class: .ep-button
        val episodes = document.select("#animeEpisodes a.ep-button").mapNotNull { ep ->
            val epUrl = fixUrl(ep.attr("href"))
            val epName = ep.text().trim() // Hasil: "Ep 1", "Ep 6"
            
            // Mengambil nomor episode dari text "Ep 6" -> 6
            val epNum = Regex("\\d+").find(epName)?.value?.toIntOrNull()

            Episode(epUrl, name = epName, episode = epNum)
        }.reversed() // Membalik urutan agar episode terbaru ada di atas (opsional)

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            addEpisodes(episodes)
        }
    }

    // ==========================================
    // BAGIAN 3: MENGAMBIL VIDEO (LOAD LINKS)
    // ==========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        // Request halaman episode awal
        val document = app.get(data).document

        // Kuramanime menggunakan AJAX, tapi kita bisa mencoba mengambil list server dari dropdown
        // ID: changeServer
        val serverOptions = document.select("select#changeServer option")

        // Loop setiap server yang tersedia (Kuramadrive, Doodstream, Filemoon, dll)
        serverOptions.forEach { option ->
            val serverName = option.text() // Contoh: DoodStream (kencang, iklan popup)
            val serverValue = option.attr("value") // Contoh: doodstream
            
            // Skip server yang biasanya ribet atau butuh login premium
            if (serverValue == "kuramadrive" && serverName.contains("vip", true)) return@forEach

            // Mencoba merequest URL dengan parameter server
            // Biasanya formatnya: URL_EPISODE?server=serverValue
            // Atau URL_EPISODE?server_id=serverValue
            // Kita coba tebak parameternya "?server="
            val serverUrl = "$data?server=$serverValue"
            
            try {
                val serverPage = app.get(serverUrl).document
                
                // Cari iframe di dalam respon halaman server tersebut
                val iframeSrc = serverPage.select("iframe").attr("src")
                
                if (iframeSrc.isNotEmpty()) {
                    // Masukkan ke loadExtractor bawaan CloudStream untuk diproses
                    loadExtractor(iframeSrc, data, subtitleCallback, callback)
                } 
                
                // Cek khusus jika servernya Kuramadrive (seringkali direct link atau obfuscated)
                if (serverValue.contains("kuramadrive")) {
                    // Logic khusus kuramadrive (jika ada script hidden)
                    // Karena HTML kamu menunjukkan error di player, kemungkinan ini butuh header khusus
                    // atau token. Untuk sekarang kita skip deep extraction Kuramadrive tanpa JS.
                }

            } catch (e: Exception) {
                // Ignore error jika gagal fetch satu server
            }
        }

        return true
    }
}
