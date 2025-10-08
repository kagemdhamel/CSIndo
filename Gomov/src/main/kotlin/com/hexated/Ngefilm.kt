package com.hexated

import com.lagradost.cloudstream3.mainPageOf

class Ngefilm : Gomov() {

    override var mainUrl = "https://new19.ngefilm.site"

    override var name = "Ngefilm"
    override val mainPage = mainPageOf(
        "/page/%d/?s&search=advanced&post_type=movie&index&orderby&genre&movieyear&country&quality=" to "Movies Terbaru",
        "/page/%d/?s=&search=advanced&post_type=tv&index=&orderby=&genre=&movieyear=&country=&quality=" to "Series Terbaru",
        "/page/%d/?s=&search=advanced&post_type=tv&index=&orderby=&genre=drakor&movieyear=&country=&quality=" to "Series Korea",
        "/page/%d/?s=&search=advanced&post_type=tv&index=&orderby=&genre=&movieyear=&country=indonesia&quality=" to "Series Indonesia",
		"/page/%d/?s=&search=advanced&post_type=tv&index=&orderby=&genre=&movieyear=&country=&quality=" to "All Series",
		"/page/%d/?s=&search=advanced&post_type=tv&index=&orderby=&genre=dubbing&movieyear=&country=&quality=" to "Dubbing Indonesia",
    )

}
