package com.JavHey

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JavHeyPlugin: Plugin() {
    override fun load(context: Context) {
        // 1. Daftarkan Provider Utama (Situs JavHey)
        registerMainAPI(JavHey())

        // 2. Daftarkan Extractor (Server Video)
        // Ini wajib agar saat aplikasi menemukan link "hglink.to" atau "haxloppd.com",
        // ia tahu harus menggunakan logika di file Extractor.kt
        registerExtractorAPI(Hglink())
        registerExtractorAPI(Haxloppd())
    }
}
