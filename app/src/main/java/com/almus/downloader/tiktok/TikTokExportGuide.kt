package com.almus.downloader.tiktok

/**
 * Plain instructions for getting a copy of the person's OWN TikTok content with TikTok's official tools. Everything
 * happens inside TikTok, with the person signed in there; Almus Downloader sees none of it. Menu names change from
 * time to time, so the guide points to TikTok's Help Center, which is the reference.
 */
data class TikTokExportGuide(
    val title: String,
    val intro: String,
    val steps: List<String>,
    val helpLabel: String,
    val helpUrl: String,
)

object TikTokExport {
    /** The Help Center's front page. A deeper article address is not used because such addresses change. */
    const val HELP_CENTER_URL = "https://support.tiktok.com/"

    val yourAccount = TikTokExportGuide(
        title = "Your own videos",
        intro = "TikTok lets you request a copy of your data, and lets you save your own videos from your profile. " +
            "These are TikTok's own tools, so they work for your content and nothing else.",
        steps = listOf(
            "In the TikTok app, open your Profile, then the menu, then Settings and privacy, then Account, then Download your data.",
            "Choose what to include and a file format (TXT or JSON), then tap Request data. TikTok prepares the file, which can take a few days, and tells you when it is ready.",
            "Download the file from the same screen before TikTok removes it (it stays available only for a few days).",
            "The file describes your videos (dates, links and similar details). It may not contain the video files themselves.",
            "To save a video file, open your own video in the TikTok app, tap Share, then Save video.",
            "To see saved files in Almus Downloader, open Files, tap Choose a folder and pick the folder where they were saved.",
        ),
        helpLabel = "Open TikTok's Help Center",
        helpUrl = HELP_CENTER_URL,
    )

    val all: List<TikTokExportGuide> = listOf(yourAccount)
}
