package com.almus.downloader.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.almus.downloader.R
import com.almus.downloader.ui.state.YouTubeUiState
import com.almus.downloader.ui.state.YouTubeViewModel
import com.almus.downloader.youtube.DownloadDecision
import com.almus.downloader.youtube.IsoDuration
import com.almus.downloader.youtube.LiveState
import com.almus.downloader.youtube.VideoLicense
import com.almus.downloader.youtube.ViewingKind
import com.almus.downloader.youtube.ViewingOption
import com.almus.downloader.youtube.YouTubeLookup
import com.almus.downloader.youtube.YouTubeReport
import com.almus.downloader.youtube.YouTubeVideoInfo
import com.almus.downloader.youtube.YouTubeVideoRef
import com.almus.downloader.youtube.YouTubeViewingOptions
import com.almus.downloader.youtube.android.OpenResult
import com.almus.downloader.youtube.android.ThumbnailLoader
import com.almus.downloader.youtube.android.YouTubeOpener

/**
 * Shows what YouTube's official services say about a video (title, channel, thumbnail, duration), the official
 * ways to watch it, and a clear explanation of why it cannot be downloaded here. Nothing is saved from this dialog.
 * [onUseLink] receives an address only when an authorized download provider explicitly offers a file; none is shipped.
 */
@Composable
fun YouTubeVideoDialog(
    ref: YouTubeVideoRef,
    onUseLink: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: YouTubeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(ref.videoId) { viewModel.load(ref) }

    // A previous video's answer must never flash up for this one.
    val report: YouTubeReport? = (state as? YouTubeUiState.Ready)?.report?.takeIf { it.ref.videoId == ref.videoId }

    val open: (ViewingOption) -> Unit = { option ->
        when (YouTubeOpener.open(context, option)) {
            OpenResult.Opened -> Unit
            OpenResult.AppNotInstalled -> {
                Toast.makeText(context, context.getString(R.string.yt_app_missing), Toast.LENGTH_LONG).show()
                val browser = YouTubeViewingOptions.forVideo(ref, null)
                    .first { it.kind == ViewingKind.OpenInBrowser }
                YouTubeOpener.open(context, browser)
            }
            OpenResult.Failed ->
                Toast.makeText(context, context.getString(R.string.yt_open_failed), Toast.LENGTH_LONG).show()
        }
    }

    val failed = report?.lookup is YouTubeLookup.Failed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.yt_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (report == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(R.string.yt_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    ReportBody(report = report, onOpen = open, onUseLink = onUseLink)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.yt_close)) }
        },
        dismissButton = if (failed) {
            @Composable {
                TextButton(onClick = { viewModel.retry(ref) }) { Text(text = stringResource(R.string.yt_retry)) }
            }
        } else {
            null
        },
    )
}

@Composable
private fun ReportBody(
    report: YouTubeReport,
    onOpen: (ViewingOption) -> Unit,
    onUseLink: (String) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (val lookup = report.lookup) {
        is YouTubeLookup.Found -> {
            VideoSummary(lookup.info)
            lookup.notices.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            YouTubeViewingOptions.embeddingNote(lookup.info)?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        is YouTubeLookup.Unavailable -> {
            Text(text = lookup.reason.message, style = MaterialTheme.typography.bodyMedium)
            lookup.notices.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        }
        is YouTubeLookup.Failed -> {
            Text(text = lookup.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

    Text(text = stringResource(R.string.yt_section_watch), style = MaterialTheme.typography.titleSmall)
    report.viewing.forEach { option ->
        OutlinedButton(onClick = { onOpen(option) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = option.title)
        }
        Text(text = option.description, style = MaterialTheme.typography.bodySmall, color = muted)
    }

    Text(text = stringResource(R.string.yt_section_download), style = MaterialTheme.typography.titleSmall)
    when (val decision = report.download) {
        is DownloadDecision.NotAvailable -> {
            Text(text = decision.headline, style = MaterialTheme.typography.bodyMedium)
            decision.reasons.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted) }
            Text(text = stringResource(R.string.yt_alternatives), style = MaterialTheme.typography.labelLarge)
            decision.alternatives.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted) }
        }
        is DownloadDecision.Offered -> {
            decision.offers.forEach { offer ->
                Button(onClick = { onUseLink(offer.fileUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.yt_offer_button, offer.label))
                }
                Text(
                    text = stringResource(R.string.yt_offer_by, offer.authorizedBy),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }
    }
}

@Composable
private fun VideoSummary(info: YouTubeVideoInfo) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, info.thumbnailUrl) {
        value = info.thumbnailUrl?.let { ThumbnailLoader.load(it) }
    }
    thumbnail?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
    Text(
        text = info.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    info.channelTitle?.let {
        Text(text = stringResource(R.string.yt_channel, it), style = MaterialTheme.typography.bodyMedium)
    }
    val duration = when {
        info.liveState == LiveState.Live -> stringResource(R.string.yt_live_now)
        info.liveState == LiveState.Upcoming -> stringResource(R.string.yt_upcoming)
        info.durationSeconds != null -> stringResource(R.string.yt_duration, IsoDuration.format(info.durationSeconds))
        else -> stringResource(R.string.yt_duration_unknown)
    }
    Text(text = duration, style = MaterialTheme.typography.bodyMedium)
    when (info.license) {
        VideoLicense.CreativeCommons -> Text(text = stringResource(R.string.yt_license_cc), style = MaterialTheme.typography.bodySmall, color = muted)
        VideoLicense.Standard -> Text(text = stringResource(R.string.yt_license_standard), style = MaterialTheme.typography.bodySmall, color = muted)
        VideoLicense.Unknown -> Unit
    }
    if (info.ageRestricted) {
        Text(text = stringResource(R.string.yt_age_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
    if (info.regionRestricted) {
        Text(text = stringResource(R.string.yt_region_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
    Text(text = stringResource(R.string.yt_source, info.source.label), style = MaterialTheme.typography.bodySmall, color = muted)
}
