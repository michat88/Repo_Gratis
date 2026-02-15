package com.CeweCantik

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KuramanimePlugin: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan class Kuramanime agar muncul di daftar provider
        registerMainAPI(Kuramanime())
    }
}
