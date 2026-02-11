package com.JavHey

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.nodes.Document
import java.util.Base64
import kotlinx.coroutines.*

/**
 * MANAGER TURBO (V3 Fixed)
 * - Removed .copy() to fix compilation error.
 * - Optimized parallel loading.
 */
object JavHeyExtractorManager {

    suspend fun invoke(
        document: Document,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val rawUrls = mutableSetOf<String>()

        // 1. Ambil Link Tersembunyi (Prioritas)
        try {
            document.selectFirst("input#links")?.attr("value")?.let { encrypted ->
                if (encrypted.isNotEmpty()) {
                    val decoded = String(Base64.getDecoder().decode(encrypted))
                    decoded.split(",,,").forEach { url ->
                        val clean = url.trim()
                        if (isValidLink(clean)) rawUrls.add(clean)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // 2. Ambil Link Tombol (Cadangan)
        try {
            document.select("div.links-download a").forEach { link ->
                val href = link.attr("href").trim()
                if (isValidLink(href)) rawUrls.add(href)
            }
        } catch (e: Exception) { e.printStackTrace() }

        // 3. EKSEKUSI PARALEL (Turbo Load)
        rawUrls.forEach { url ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    loadExtractor(url, subtitleCallback, callback)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Filter Link Sampah/Lemot
    private fun isValidLink(url: String): Boolean {
        if (!url.startsWith("http")) return false
        val u = url.lowercase()
        return !u.contains("emturbovid") &&
               !u.contains("bestx.stream") &&
               !u.contains("evosrv.com") &&
               !u.contains("bysebuho")
    }
}

// ============================================================================
//  SECTION: CUSTOM EXTRACTORS
// ============================================================================

// --- FAMILY: VIDHIDE / FILELIONS ---
class VidHidePro1 : VidHidePro() { override var mainUrl = "https://filelions.live" }
class VidHidePro2 : VidHidePro() { override var mainUrl = "https://filelions.online" }
class VidHidePro3 : VidHidePro() { override var mainUrl = "https://filelions.to" }
class VidHidePro4 : VidHidePro() { override val mainUrl = "https://kinoger.be" }
class VidHidePro5 : VidHidePro() { override val mainUrl = "https://vidhidevip.com" }
class VidHidePro6 : VidHidePro() { override val mainUrl = "https://vidhidepre.com" }
class Smoothpre : VidHidePro() { override var name = "EarnVids"; override var mainUrl = "https://smoothpre.com" }
class Dhtpre : VidHidePro() { override var name = "EarnVids"; override var mainUrl = "https://dhtpre.com" }
class Peytonepre : VidHidePro() { override var name = "EarnVids"; override var mainUrl = "https://peytonepre.com" }

open class VidHidePro : ExtractorApi() {
    override val name = "VidHidePro"
    override val mainUrl = "https://vidhidepro.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val headers = mapOf("Origin" to mainUrl, "User-Agent" to USER_AGENT)
        val response = app.get(getEmbedUrl(url), referer = referer)
        val text = response.text
        val script = if (text.contains("eval(function")) getAndUnpack(text) else text
        
        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            val hlsUrl = fixUrl(m3u8Match.groupValues[1])
            // FIX: Hapus .copy(), pass link langsung.
            // CloudStream akan otomatis menggabungkan link dengan nama "VidHidePro" ke dalam satu grup.
            generateM3u8(name, hlsUrl, referer = "$mainUrl/", headers = headers).forEach(callback)
        }
    }
    private fun getEmbedUrl(url: String): String {
        return url.replace(Regex("/(d|download|file)/"), "/v/").replace("/f/", "/v/")
    }
}

// --- FAMILY: MIXDROP ---
class MixDropBz : MixDrop(){ override var mainUrl = "https://mixdrop.bz" }
class MixDropAg : MixDrop(){ override var mainUrl = "https://mixdrop.ag" }
class MixDropCh : MixDrop(){ override var mainUrl = "https://mixdrop.ch" }
class MixDropTo : MixDrop(){ override var mainUrl = "https://mixdrop.to" }

open class MixDrop : ExtractorApi() {
    override var name = "MixDrop"
    override var mainUrl = "https://mixdrop.co"
    private val srcRegex = Regex("""wurl.*?=.*?"(.*?)";""")
    override val requiresReferer = false
    override fun getExtractorUrl(id: String): String = "$mainUrl/e/$id"
    
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val embed = url.replaceFirst("/f/", "/e/")
        val unpacked = getAndUnpack(app.get(embed).text)
        srcRegex.find(unpacked)?.groupValues?.get(1)?.let { link ->
            return listOf(newExtractorLink(name, name, httpsify(link)) {
                this.referer = url
                // Request: Unknown Quality agar rapi
                this.quality = Qualities.Unknown.value 
            })
        }
        return null
    }
}

// --- FAMILY: STREAMWISH ---
class Mwish : StreamWishExtractor() { override val name = "Mwish"; override val mainUrl = "https://mwish.pro" }
class Dwish : StreamWishExtractor() { override val name = "Dwish"; override val mainUrl = "https://dwish.pro" }
class Streamwish2 : StreamWishExtractor() { override val mainUrl = "https://streamwish.site" }
class WishembedPro : StreamWishExtractor() { override val name = "Wishembed"; override val mainUrl = "https://wishembed.pro" }
class Wishfast : StreamWishExtractor() { override val name = "Wishfast"; override val mainUrl = "https://wishfast.top" }
class Swdyu : StreamWishExtractor() { override val name = "Swdyu"; override val mainUrl = "https://swdyu.com" }

open class StreamWishExtractor : ExtractorApi() {
    override val name = "Streamwish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val headers = mapOf("Origin" to "$mainUrl/", "User-Agent" to USER_AGENT)
        val text = app.get(resolveEmbedUrl(url), referer = referer).text
        val script = if (text.contains("eval(function")) getAndUnpack(text) else text
        
        val file = Regex("""file:\s*"(.*?m3u8.*?)"""").find(script)?.groupValues?.getOrNull(1)

        if (!file.isNullOrEmpty()) {
            // FIX: Hapus .copy()
            generateM3u8(name, file, mainUrl, headers = headers).forEach(callback)
        } else {
            // Fallback: WebView dengan Timeout 10 Detik
            val resolver = WebViewResolver(
                interceptUrl = Regex("""txt|m3u8"""), 
                additionalUrls = listOf(Regex("""txt|m3u8""")), 
                useOkhttp = false, 
                timeout = 10_000L
            )
            val resUrl = app.get(url, referer = referer, interceptor = resolver).url
            if (resUrl.isNotEmpty()) {
                generateM3u8(name, resUrl, mainUrl, headers = headers).forEach(callback)
            }
        }
    }
    private fun resolveEmbedUrl(inputUrl: String): String {
        return inputUrl.replace(Regex("/(f|e)/"), "/e/").let { if(!it.contains(mainUrl)) "$mainUrl/${it.substringAfterLast("/")}" else it }
    }
}

// --- FAMILY: DOODSTREAM ---
class D0000d : DoodLaExtractor() { override var mainUrl = "https://d0000d.com" }
class DoodstreamCom : DoodLaExtractor() { override var mainUrl = "https://doodstream.com" }
class Dooood : DoodLaExtractor() { override var mainUrl = "https://dooood.com" }
class DoodWfExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.wf" }
class DoodCxExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.cx" }
class DoodShExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.sh" }
class DoodWatchExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.watch" }
class DoodPmExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.pm" }
class DoodToExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.to" }
class DoodSoExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.so" }
class DoodWsExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.ws" }
class DoodYtExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.yt" }
class DoodLiExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.li" }
class Ds2play : DoodLaExtractor() { override var mainUrl = "https://ds2play.com" }
class Ds2video : DoodLaExtractor() { override var mainUrl = "https://ds2video.com" }
class MyVidPlay : DoodLaExtractor() { override var mainUrl = "https://myvidplay.com" }

open class DoodLaExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://dood.la"
    override val requiresReferer = false
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val embedUrl = url.replace("/d/", "/e/")
        val req = app.get(embedUrl)
        val host = URI(req.url).let { "${it.scheme}://${it.host}" }
        val md5 = Regex("/pass_md5/[^']*").find(req.text)?.value ?: return
        val token = md5.substringAfterLast("/")
        val trueUrl = app.get(host + md5, referer = req.url).text + buildString { repeat(10) { append(alphabet.random()) } } + "?token=" + token
        
        callback.invoke(newExtractorLink(name, name, trueUrl) { 
            this.referer = "$mainUrl/"
            this.quality = Qualities.Unknown.value 
        })
    }
}

// --- FAMILY: LULUSTREAM ---
class Lulustream1 : LuluStream() { override val name = "Lulustream"; override val mainUrl = "https://lulustream.com" }
class Lulustream2 : LuluStream() { override val name = "Lulustream"; override val mainUrl = "https://kinoger.pw" }

open class LuluStream : ExtractorApi() {
    override val name = "LuluStream"
    override val mainUrl = "https://luluvdo.com"
    override val requiresReferer = true
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val post = app.post("$mainUrl/dl", data = mapOf("op" to "embed", "file_code" to url.substringAfterLast("/"), "auto" to "1", "referer" to (referer ?: ""))).document
        post.selectFirst("script:containsData(vplayer)")?.data()?.let { script ->
            Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
                callback(newExtractorLink(name, name, link) { 
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value 
                })
            }
        }
    }
}
