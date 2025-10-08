package com.layarkaca

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class LayarKacaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(LayarKaca())
        registerExtractorAPI(Emturbovid())
        registerExtractorAPI(Hownetwork())
    }
}
