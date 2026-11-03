package com.almus.downloader.youtube

/** Everything the screen shows about one video. */
data class YouTubeReport(
    val ref: YouTubeVideoRef,
    val lookup: YouTubeLookup,
    val viewing: List<ViewingOption>,
    val download: DownloadDecision,
)

/**
 * The single entry point of the YouTube module: ask the official services, then work out the viewing options and
 * whether downloading is possible. [inspect] never throws and never downloads anything.
 */
class YouTubeIntegration(
    private val metadata: YouTubeMetadataClient,
    private val policy: YouTubeDownloadPolicy = YouTubeDownloadPolicy(),
) {
    suspend fun inspect(ref: YouTubeVideoRef): YouTubeReport {
        val lookup = metadata.lookup(ref)
        return YouTubeReport(
            ref = ref,
            lookup = lookup,
            viewing = YouTubeViewingOptions.forVideo(ref, lookup),
            download = policy.decide(lookup),
        )
    }
}
