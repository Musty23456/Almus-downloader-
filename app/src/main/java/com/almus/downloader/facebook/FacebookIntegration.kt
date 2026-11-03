package com.almus.downloader.facebook

import kotlin.coroutines.cancellation.CancellationException

/** Everything the screen shows about one Facebook video, Reel or live video. */
data class FacebookReport(
    val ref: FacebookContentRef,
    val lookup: FacebookLookup,
    val openOptions: List<FacebookOpenOption>,
    val download: FacebookDownloadDecision,
    val exportGuides: List<FacebookExportGuide>,
)

/**
 * The single entry point of the Facebook module: ask the official services, then work out the ways to open the
 * content and whether downloading is possible. [inspect] never throws (apart from cancellation) and never
 * downloads anything.
 */
class FacebookIntegration(
    private val metadata: FacebookMetadataClient,
    private val policy: FacebookDownloadPolicy = FacebookDownloadPolicy(),
) {
    suspend fun inspect(ref: FacebookContentRef): FacebookReport {
        val lookup = try {
            metadata.lookup(ref)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            FacebookLookup.Failed(FacebookProblem.BadResponse, "Something went wrong while asking Facebook. Try again.")
        }
        return FacebookReport(
            ref = ref,
            lookup = lookup,
            openOptions = FacebookAccessOptions.forContent(ref, lookup),
            download = policy.decide(lookup),
            exportGuides = FacebookExport.all,
        )
    }
}
