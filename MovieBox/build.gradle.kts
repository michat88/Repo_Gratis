// use an integer for version numbers
version = 1

cloudstream {
    language = "id"
    // All of these properties are optional, you can safely remove them

    description = "MovieBox.ph - Watch movies and TV shows for free"
    authors = listOf("MovieBox")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime",
        "AsianDrama"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=moviebox.ph&sz=%size%"
}
