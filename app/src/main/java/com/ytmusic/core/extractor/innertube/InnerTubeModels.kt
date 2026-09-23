package com.ytmusic.core.extractor.innertube

enum class InnerTubeClientType(
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    val referer: String? = null
) {
    ANDROID_MUSIC(
        clientName = "ANDROID_MUSIC",
        clientVersion = "6.42.52",
        userAgent = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; en_US; Pixel 8 Build/UP1A.231105.001)"
    ),
    WEB_REMIX(
        clientName = "WEB_REMIX",
        clientVersion = "1.20240920.01.00",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        referer = "https://music.youtube.com/"
    ),
    IOS(
        clientName = "IOS",
        clientVersion = "19.29.1",
        userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)"
    )
}
