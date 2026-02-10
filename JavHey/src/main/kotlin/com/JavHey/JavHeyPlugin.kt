package com.JavHey

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JavHeyPlugin: Plugin() {
    override fun load(context: Context) {
        // 1. Daftarkan Provider Utama
        registerMainAPI(JavHey())

        // 2. Daftarkan Semua Extractor Custom (Dari File Extractor.kt)
        // Ini wajib agar CloudStream tahu cara menangani server-server ini
        
        // --- VidHide / FileLions ---
        registerExtractorAPI(VidHidePro1())
        registerExtractorAPI(VidHidePro2())
        registerExtractorAPI(VidHidePro3())
        registerExtractorAPI(VidHidePro4())
        registerExtractorAPI(VidHidePro5())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Smoothpre())
        registerExtractorAPI(Dhtpre())
        registerExtractorAPI(Peytonepre())

        // --- MixDrop ---
        registerExtractorAPI(MixDropBz())
        registerExtractorAPI(MixDropAg())
        registerExtractorAPI(MixDropCh())
        registerExtractorAPI(MixDropTo())

        // --- StreamWish ---
        registerExtractorAPI(Mwish())
        registerExtractorAPI(Dwish())
        registerExtractorAPI(Ewish())
        registerExtractorAPI(WishembedPro())
        registerExtractorAPI(Kswplayer())
        registerExtractorAPI(Wishfast())
        registerExtractorAPI(Streamwish2())
        registerExtractorAPI(SfastwishCom())
        registerExtractorAPI(Strwish())
        registerExtractorAPI(Strwish2())
        registerExtractorAPI(FlaswishCom())
        registerExtractorAPI(Awish())
        registerExtractorAPI(Obeywish())
        registerExtractorAPI(Jodwish())
        registerExtractorAPI(Swhoi())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(UqloadsXyz())
        registerExtractorAPI(Doodporn())
        registerExtractorAPI(CdnwishCom())
        registerExtractorAPI(Asnwish())
        registerExtractorAPI(Nekowish())
        registerExtractorAPI(Nekostream())
        registerExtractorAPI(Swdyu())
        registerExtractorAPI(Wishonly())
        registerExtractorAPI(Playerwish())
        registerExtractorAPI(StreamHLS())
        registerExtractorAPI(HlsWish())

        // --- Byse / Bysebuho ---
        registerExtractorAPI(Bysezejataos())
        registerExtractorAPI(ByseBuho())
        registerExtractorAPI(ByseVepoin())

        // --- DoodStream ---
        registerExtractorAPI(D0000d())
        registerExtractorAPI(D000dCom())
        registerExtractorAPI(DoodstreamCom())
        registerExtractorAPI(Dooood())
        registerExtractorAPI(DoodWfExtractor())
        registerExtractorAPI(DoodCxExtractor())
        registerExtractorAPI(DoodShExtractor())
        registerExtractorAPI(DoodWatchExtractor())
        registerExtractorAPI(DoodPmExtractor())
        registerExtractorAPI(DoodToExtractor())
        registerExtractorAPI(DoodSoExtractor())
        registerExtractorAPI(DoodWsExtractor())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(DoodLiExtractor())
        registerExtractorAPI(Ds2play())
        registerExtractorAPI(Ds2video())
        registerExtractorAPI(MyVidPlay())

        // --- LuluStream ---
        registerExtractorAPI(Lulustream1())
        registerExtractorAPI(Lulustream2())
    }
}
