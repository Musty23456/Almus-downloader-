package com.almus.downloader.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.almus.downloader.R
import com.almus.downloader.facebook.FacebookAccessOptions
import com.almus.downloader.facebook.FacebookLinks
import com.almus.downloader.integration.AccessOptions
import com.almus.downloader.integration.PlatformLinks
import com.almus.downloader.platform.LinkDetection
import com.almus.downloader.platform.LinkVerdict
import com.almus.downloader.tiktok.TikTokAccessOptions
import com.almus.downloader.tiktok.TikTokLinks
import com.almus.downloader.youtube.YouTubeLinks

/**
 * Explains a link that is recognised but cannot be downloaded: a platform page (video, post, profile ...)
 * or a restricted link. Nothing is saved from here. A page that is not restricted can still be checked
 * as an ordinary web link; a restricted one cannot.
 */
@Composable
fun LinkDetectionDialog(
    detection: LinkDetection,
    onCheckAnyway: () -> Unit,
    onDismiss: () -> Unit,
    onUseLink: (String) -> Unit = {},
) {
    // A plain YouTube video address gets its own dialog (official details, viewing options, why it cannot be saved).
    val youtubeVideo = remember(detection) { YouTubeLinks.videoRef(detection) }
    if (youtubeVideo != null) {
        YouTubeVideoDialog(ref = youtubeVideo, onUseLink = onUseLink, onDismiss = onDismiss)
        return
    }
    // A plain Facebook video, Reel or live video gets its own dialog (official details, ways to open it, export).
    val facebookContent = remember(detection) { FacebookLinks.contentRef(detection) }
    if (facebookContent != null) {
        FacebookContentDialog(ref = facebookContent, onUseLink = onUseLink, onDismiss = onDismiss)
        return
    }
    // Other Facebook pages (restricted or not) can be handed to Facebook itself; Facebook decides what is shown.
    val facebookOpen = remember(detection) {
        if (detection.verdict == LinkVerdict.Restricted || detection.verdict == LinkVerdict.PageNotDownloadable) {
            FacebookAccessOptions.forRestrictedUrl(FacebookLinks.openableUrl(detection))
        } else {
            emptyList()
        }
    }
    // A plain TikTok video gets its own dialog (official public details, ways to open it, why it cannot be saved).
    val tiktokVideo = remember(detection) { TikTokLinks.contentRef(detection) }
    if (tiktokVideo != null) {
        TikTokVideoDialog(ref = tiktokVideo, onUseLink = onUseLink, onDismiss = onDismiss)
        return
    }
    // Other TikTok pages (photo posts, live pages, profiles) can be handed to TikTok itself; TikTok decides what is shown.
    val tiktokOpen = remember(detection) {
        if (detection.verdict == LinkVerdict.PageNotDownloadable) {
            TikTokAccessOptions.forPageUrl(TikTokLinks.openableUrl(detection))
        } else {
            emptyList()
        }
    }
    // Instagram, X, Vimeo, Dailymotion, Reddit and Pinterest content gets one shared dialog (official public details,
    // the kind of media, ways to open it, why it cannot be saved). Their other pages (profiles, boards) can be
    // handed to the platform itself; the platform decides what is shown.
    val platformContent = remember(detection) { PlatformLinks.contentRef(detection) }
    if (platformContent != null) {
        PlatformContentDialog(ref = platformContent, onUseLink = onUseLink, onDismiss = onDismiss)
        return
    }
    val platformOpen = remember(detection) {
        val platform = detection.platform
        if (platform != null && detection.verdict == LinkVerdict.PageNotDownloadable) {
            AccessOptions.forPageUrl(platform, PlatformLinks.openableUrl(detection))
        } else {
            emptyList()
        }
    }
    val checkAnywayButton: (@Composable () -> Unit)? = if (detection.verdict == LinkVerdict.PageNotDownloadable) {
        @Composable {
            TextButton(onClick = onCheckAnyway) {
                Text(text = stringResource(R.string.detect_check_anyway))
            }
        }
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = detection.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = detection.message, style = MaterialTheme.typography.bodyMedium)
                detection.details.forEach { line ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (facebookOpen.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FacebookOpenButtons(facebookOpen)
                }
                if (tiktokOpen.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TikTokOpenButtons(tiktokOpen)
                }
                if (platformOpen.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PlatformOpenButtons(platformOpen)
                }
                detection.notes.forEach { note ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = note.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.detect_close))
            }
        },
        dismissButton = checkAnywayButton,
    )
}
