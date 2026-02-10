package com.JavHey

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import java.util.Base64

object JavHeyExtractor {

    suspend fun invoke(
        document: Document,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Menggunakan Set untuk mencegah link duplikat masuk
        val processedUrls = mutableSetOf<String>()

        // 1. Coba ambil dari input tersembunyi (Metode Utama)
        try {
            val hiddenInput = document.selectFirst("input#links")
            val hiddenLinksEncrypted = hiddenInput?.attr("value")

            if (!hiddenLinksEncrypted.isNullOrEmpty()) {
                val decodedBytes = Base64.getDecoder().decode(hiddenLinksEncrypted)
                val decodedString = String(decodedBytes)
                val urls = decodedString.split(",,,")

                urls.forEach { sourceUrl ->
                    val cleanUrl = sourceUrl.trim()
                    if (isValidUrl(cleanUrl)) {
                        processedUrls.add(cleanUrl)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Coba ambil dari tombol download (Metode Cadangan)
        try {
            document.select("div.links-download a").forEach { linkTag ->
                val downloadUrl = linkTag.attr("href").trim()
                if (isValidUrl(downloadUrl)) {
                    processedUrls.add(downloadUrl)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Eksekusi semua URL yang unik (sudah bersih dari duplikat)
        processedUrls.forEach { url ->
            loadExtractor(url, subtitleCallback, callback)
        }
    }

    private fun isValidUrl(url: String): Boolean {
        // Filter URL kosong, bukan http, atau server bermasalah (bysebuho)
        return url.isNotBlank() && 
               url.startsWith("http") && 
               !url.contains("bysebuho", ignoreCase = true)
    }
}
