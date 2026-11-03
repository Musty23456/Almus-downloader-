package com.almus.downloader.tiktok

import kotlin.coroutines.cancellation.CancellationException

/** Everything the screen shows about one TikTok video. */
data class TikTokReport(
    val ref: TikTokContentRef,
    val lookup: TikTokLookup,
    val openOptions: List<TikTokOpenOption>,
    val download: TikTokDownloadDecision,
    val exportGuides: List<TikTokExportGuide>,
)

/**
 * The single entry point of the TikTok module: ask TikTok's public oEmbed service, then work out the ways to open the
 * video and whether downloading is possible. [inspect] never throws (apart from cancellation) and never downloads anything.
 */
class TikTokIntegration(
    private val metadata: TikTokMetadataClient,
    private val policy: TikTokDownloadPolicy = TikTokDownloadPolicy(),
) {
    suspend fun inspect(ref: TikTokContentRef): TikTokReport {
        val lookup = try {
            metadata.lookup(ref)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            TikTokLookup.Failed(TikTokProblem.BadResponse, "Something went wrong while asking TikTok. Try again.")
        }
        return TikTokReport(
            ref = ref,
            lookup = lookup,
            openOptions = TikTokAccessOptions.forContent(ref, lookup),
            download = policy.decide(lookup),
            exportGuides = TikTokExport.all,
        )
    }
}
