package com.almus.downloader.integration

import com.almus.downloader.platform.LinkKind
import com.almus.downloader.platform.MediaPlatform
import com.almus.downloader.platform.ParsedUrl
import com.almus.downloader.platform.UrlSafety

/**
 * The one official, public, documented oEmbed address of a platform. [host] and [path] are fixed (the network
 * layer refuses everything else). [fixedParams] are added before the `url` parameter. No credentials are ever sent.
 */
data class OEmbedService(
    val host: String,
    val path: String,
    val fixedParams: List<Pair<String, String>> = emptyList(),
)

/**
 * Everything that is specific to one platform, kept in one small object per platform (see the `specs` package).
 * The shared code (links, client, parser, policy, options, dialog) knows nothing about any single platform: a
 * platform is added by writing one spec and listing it in [PlatformSpecs]. A spec holds data and pure text
 * handling only. It never opens a link and never uses the network.
 */
interface PlatformSpec {
    val platform: MediaPlatform

    /** "Vimeo", "X (Twitter)" ... */
    val name: String get() = platform.displayName

    /** Short site name used on buttons, for example "vimeo.com". */
    val siteLabel: String

    /** Kinds of address that get the content dialog (a video, a post, a pin ...). */
    val contentKinds: Set<LinkKind>

    /** Other kinds of page (profiles, boards ...) that may be handed to the platform itself. */
    val openableKinds: Set<LinkKind>

    /** The platform's page domains. Pages, accounts and "open" addresses must be on one of these. */
    val pageDomains: List<String>

    /** Hosts a thumbnail may come from. A thumbnail on any other host is dropped and never fetched. */
    val thumbnailDomains: List<String>

    /** Every domain the platform owns (pages, short links, media hosts). Never accepted as an outside download offer. */
    val ownedDomains: List<String>

    /** Android package names of the official app, in order of preference. */
    val appPackages: List<String>

    val oembed: OEmbedService

    /** Shown as "Details from: ...". */
    val sourceLabel: String

    /** Error codes in a service answer that mean "too many requests". */
    val rateLimitCodes: Set<String>
        get() = setOf("rate_limit_exceeded", "rate_limit", "too_many_requests")

    /** When true and the answer has no title, the text of the first paragraph of the embed markup is used (X). */
    val titleFromMarkup: Boolean get() = false

    /** Finds the content id inside the embed markup (group 1), to check the answer is about the content asked for. */
    val embeddedIdPattern: Regex? get() = null

    /** True when [host] (lower case) is one of the platform's page hosts. */
    fun isPageHost(host: String): Boolean = pageDomains.any { UrlSafety.hostIs(host, it) }

    /** True when [host] belongs to the platform in any role. */
    fun isOwnedHost(host: String): Boolean = ownedDomains.any { UrlSafety.hostIs(host, it) } || isPageHost(host)

    fun isValidContentId(id: String?): Boolean

    /** A creator or community name read from the address, validated, or null. */
    fun handleOf(url: ParsedUrl): String? = null

    /** The official page address of the content, built from the validated reference only. */
    fun canonicalUrl(ref: ContentRef): String

    /** The address handed to the oEmbed service as its `url` parameter. */
    fun oembedSubject(ref: ContentRef): String = canonicalUrl(ref)

    /** Heading of the dialog, for example "Vimeo video". */
    fun dialogTitle(ref: ContentRef): String

    fun identifyMedia(ref: ContentRef, info: ContentInfo?): MediaIdentification

    /** Why the platform's content cannot be saved from here. */
    val downloadReasons: List<String>

    /** What the person can do instead. */
    val downloadAlternatives: List<String>

    val ownContent: OwnContentGuide

    fun unavailableMessage(reason: UnavailableReason): String = when (reason) {
        UnavailableReason.NotShared ->
            "$name does not share this with apps. It may be private, limited to certain people, removed, " +
                "restricted for your region or age group, or $name may have refused the request. $name does not say which."
        UnavailableReason.NotFoundOrUnsupported ->
            "$name returned no information for this address. The content may have been removed, it may be private, " +
                "or it may be a kind of content $name does not share with apps. $name does not say which."
    }
}
