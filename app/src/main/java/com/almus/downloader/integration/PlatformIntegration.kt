package com.almus.downloader.integration

import kotlin.coroutines.cancellation.CancellationException

/** Everything the screen shows about one piece of content. */
data class PlatformReport(
    val ref: ContentRef,
    val title: String,
    val lookup: ContentLookup,
    val media: MediaIdentification,
    val openOptions: List<OpenOption>,
    val download: DownloadDecision,
    val ownContent: OwnContentGuide,
    val sourceLabel: String,
)

/**
 * The single entry point of the module: ask the platform's public oEmbed service, then work out what the content is,
 * the official ways to open it and whether downloading is possible. [inspect] never throws for a reference made by
 * [PlatformLinks] (apart from cancellation) and never downloads anything.
 */
class PlatformIntegration(
    private val metadata: OEmbedClient,
    private val policy: DownloadPolicy = DownloadPolicy(),
) {
    suspend fun inspect(ref: ContentRef): PlatformReport {
        val spec = requireNotNull(PlatformSpecs.of(ref.platform)) { "No integration for ${ref.platform}" }
        val lookup = try {
            metadata.lookup(ref)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ContentLookup.Failed(LookupProblem.BadResponse, "Something went wrong while asking ${spec.name}. Try again.")
        }
        val info = (lookup as? ContentLookup.Found)?.info
        return PlatformReport(
            ref = ref,
            title = spec.dialogTitle(ref),
            lookup = lookup,
            media = spec.identifyMedia(ref, info),
            openOptions = AccessOptions.forContent(ref, lookup),
            download = policy.decide(ref, lookup),
            ownContent = spec.ownContent,
            sourceLabel = spec.sourceLabel,
        )
    }
}
