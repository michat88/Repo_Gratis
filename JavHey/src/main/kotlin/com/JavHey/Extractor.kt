package com.JavHey

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack // <--- PENTING: Import baru untuk bongkar kode
import java.net.URI

// --- DAFTAR SERVER ---
class Hglink : JavHeyDood("https://hglink.to", "Hglink")
class Haxloppd : JavHeyDood("https://haxloppd.com", "Haxloppd")
class Minochinos : JavHeyDood("https://minochinos.com", "Minochinos")
class GoTv : JavHeyDood("https://go-tv.lol", "GoTv")

// --- LOGIKA UTAMA ---
open class JavHeyDood(override var mainUrl: String, override var name: String) : ExtractorApi() {
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 1. JANGAN ubah /v/ jadi /e/ lagi (Minochinos error kalau diubah)
        val targetUrl = url 
        
        try {
            // 2. Ambil halaman
            val responseReq = app.get(targetUrl, referer = "https://javhey.com/")
            var response = responseReq.text
            val currentHost = "https://" + URI(responseReq.url).host

            // 3. FITUR BARU: Cek & Bongkar JavaScript (Solusi untuk Haxloppd & GoTv)
            if (!response.contains("/pass_md5/")) {
                 response = getAndUnpack(response) // Bongkar kode yang disembunyikan
            }

            // 4. Cari endpoint pass_md5 (Sekarang pasti ketemu)
            val md5Pattern = Regex("""/pass_md5/[^']*""")
            val md5Match = md5Pattern.find(response)?.value

            if (md5Match != null) {
                val trueUrl = "$currentHost$md5Match"
                
                // 5. Request Token
                val tokenResponse = app.get(trueUrl, referer = targetUrl).text

                // 6. Buat Link Video
                val randomString = generateRandomString()
                val videoUrl = "$tokenResponse$randomString?token=${md5Match.substringAfterLast("/")}&expiry=${System.currentTimeMillis()}"

                // 7. Kirim Link
                M3u8Helper.generateM3u8(
                    name,
                    videoUrl,
                    targetUrl,
                    headers = mapOf("Origin" to currentHost)
                ).forEach(callback)
            } else {
                // Fallback: Redirect langsung
                val redirectMatch = Regex("""window\.location\.replace\('([^']*)'\)""").find(response)
                if (redirectMatch != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = redirectMatch.groupValues[1],
                            type = INFER_TYPE
                        ) {
                            this.referer = targetUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateRandomString(): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..10)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
