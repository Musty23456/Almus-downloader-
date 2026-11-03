package com.almus.downloader.facebook

/**
 * Plain instructions for getting a copy of the person's OWN videos with Facebook's official export. Everything
 * happens inside Facebook, with the person signed in there; Almus Downloader sees none of it. Menu names change from
 * time to time, so each guide points to Facebook's own help page, which is the reference.
 */
data class FacebookExportGuide(
    val title: String,
    val intro: String,
    val steps: List<String>,
    val helpLabel: String,
    val helpUrl: String,
)

object FacebookExport {
    const val ACCOUNT_HELP_URL = "https://www.facebook.com/help/212802592074644"
    const val PAGE_HELP_URL = "https://www.facebook.com/help/466076673571942"

    val yourAccount = FacebookExportGuide(
        title = "Your own videos and Reels",
        intro = "Facebook lets you export a copy of what you posted, including your videos. This is Facebook's own tool, " +
            "so it works for your content and nothing else.",
        steps = listOf(
            "In the Facebook app or on facebook.com, open Settings and privacy, then Accounts Center, then Your " +
                "information and permissions, then Export your information.",
            "Start a new export, choose your Facebook profile, and pick the information to include (the types that contain your videos). " +
                "You can also choose a date range, the format and the media quality.",
            "Confirm with your Facebook password in Facebook, not here. Facebook prepares the file and tells you when it is ready.",
            "Download the file from Facebook and unzip it with a file manager. Your videos are in its media folders.",
            "To see them in Almus Downloader, open Files, tap Choose a folder and pick the folder where you saved them.",
        ),
        helpLabel = "Open Facebook's export instructions",
        helpUrl = ACCOUNT_HELP_URL,
    )

    val yourPage = FacebookExportGuide(
        title = "A Page you manage",
        intro = "If the video belongs to a Facebook Page that you manage, Facebook has a separate official way to download a copy of the Page.",
        steps = listOf(
            "Sign in to Facebook with the account that manages the Page, as an admin.",
            "Follow Facebook's instructions to download a copy of the Page, then unzip it and use Files as above.",
        ),
        helpLabel = "Open Facebook's Page export instructions",
        helpUrl = PAGE_HELP_URL,
    )

    val all: List<FacebookExportGuide> = listOf(yourAccount, yourPage)
}
