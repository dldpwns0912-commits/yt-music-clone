package com.ytmusic.core.extractor.innertube

enum class InnerTubeClientType(
    val clientName: String,
    val clientVersion: String,
    val clientNumber: String,
    val userAgent: String,
    val referer: String? = null
) {
    VISIONOS(
        clientName = "VISIONOS",
        clientVersion = "1.02",
        clientNumber = "101",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        referer = "https://www.youtube.com"
    ),
    WEB(
        clientName = "WEB",
        clientVersion = "2.20240920.01.00",
        clientNumber = "1",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        referer = "https://www.youtube.com/"
    ),
    ANDROID_MUSIC(
        clientName = "ANDROID_MUSIC",
        clientVersion = "6.42.52",
        clientNumber = "21",
        userAgent = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; en_US; Pixel 8 Build/UP1A.231105.001)"
    ),
    WEB_REMIX(
        clientName = "WEB_REMIX",
        clientVersion = "1.20240920.01.00",
        clientNumber = "67",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        referer = "https://music.youtube.com/"
    )
}
