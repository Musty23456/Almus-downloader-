package com.almus.downloader.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.almus.downloader.tiktok.TikTokContentRef
import com.almus.downloader.tiktok.TikTokDownloadDecision
import com.almus.downloader.tiktok.TikTokExportGuide
import com.almus.downloader.tiktok.TikTokLookup
import com.almus.downloader.tiktok.TikTokOpenKind
import com.almus.downloader.tiktok.TikTokOpenOption
import com.almus.downloader.tiktok.TikTokReport
import com.almus.downloader.tiktok.TikTokVideoInfo
import com.almus.downloader.tiktok.android.TikTokOpenResult
import com.almus.downloader.tiktok.android.TikTokOpener
import com.almus.downloader.tiktok.android.TikTokThumbnailLoader
import com.almus.downloader.ui.state.TikTokUiState
import com.almus.downloader.ui.state.TikTokViewModel

/**
 * Shows what TikTok's official oEmbed service says about a video (caption, creator, thumbnail where available), the
 * official ways to open it in TikTok, why it cannot be downloaded here, and TikTok's own tools for the person's own
 * videos. Nothing is saved from this dialog. [onUseLink] receives an address only when an authorized download
 * provider offered a file (none ships today); it then goes through the normal link check and approval.
 */
@Composable
fun TikTokVideoDialog(
    ref: TikTokContentRef,
    onUseLink: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: TikTokViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(ref.contentId) { viewModel.load(ref) }

    // A previous video's answer must never flash up for this one.
    val report: TikTokReport? = (state as? TikTokUiState.Ready)?.report?.takeIf { it.ref.contentId == ref.contentId }
    val failed = report?.lookup is TikTokLookup.Failed

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.tt_title_video)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (report == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(R.string.tt_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    TikTokReportBody(report = report, onUseLink = onUseLink)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.tt_close)) }
        },
        dismissButton = if (failed) {
            @Composable {
                TextButton(onClick = { viewModel.retry(ref) }) { Text(text = stringResource(R.string.tt_retry)) }
            }
        } else {
            null
        },
    )
}

/**
 * "Open in TikTok" buttons. Used for a video, and for pages that cannot be shown here (a photo post, a live page,
 * a profile). TikTok, not this app, decides whether the person may see the content.
 */
@Composable
fun TikTokOpenButtons(options: List<TikTokOpenOption>) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val appMissing = stringResource(R.string.tt_app_missing)
    val openFailed = stringResource(R.string.tt_open_failed)
    options.forEach { option ->
        OutlinedButton(
            onClick = {
                when (TikTokOpener.open(context, option)) {
                    TikTokOpenResult.Opened -> Unit
                    TikTokOpenResult.AppNotInstalled -> {
                        Toast.makeText(context, appMissing, Toast.LENGTH_LONG).show()
                        val browser = options.firstOrNull { it.kind == TikTokOpenKind.OpenInBrowser }
                        if (browser != null) TikTokOpener.open(context, browser)
                    }
                    TikTokOpenResult.Failed -> Toast.makeText(context, openFailed, Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(text = option.title) }
        Text(text = option.description, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun TikTokReportBody(report: TikTokReport, onUseLink: (String) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val notice = MaterialTheme.colorScheme.tertiary
    when (val lookup = report.lookup) {
        is TikTokLookup.Found -> {
            TikTokSummary(lookup.info)
            lookup.notices.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = notice) }
        }
        is TikTokLookup.Unavailable -> {
            Text(text = lookup.reason.message, style = MaterialTheme.typography.bodyMedium)
            lookup.notices.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = notice) }
        }
        is TikTokLookup.Failed -> {
            Text(text = lookup.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

    Text(text = stringResource(R.string.tt_section_open), style = MaterialTheme.typography.titleSmall)
    TikTokOpenButtons(report.openOptions)

    Text(text = stringResource(R.string.tt_section_download), style = MaterialTheme.typography.titleSmall)
    when (val decision = report.download) {
        is TikTokDownloadDecision.NotAvailable -> {
            Text(text = decision.headline, style = MaterialTheme.typography.bodyMedium)
            decision.reasons.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted) }
            Text(text = stringResource(R.string.tt_alternatives), style = MaterialTheme.typography.labelLarge)
            decision.alternatives.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted) }
        }
        is TikTokDownloadDecision.Offered -> {
            decision.offers.forEach { offer ->
                Button(onClick = { onUseLink(offer.fileUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.tt_offer_button, offer.label))
                }
                Text(text = stringResource(R.string.tt_offer_by, offer.authorizedBy), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }

    Text(text = stringResource(R.string.tt_section_export), style = MaterialTheme.typography.titleSmall)
    report.exportGuides.forEach { TikTokExportGuideCard(it) }
}

@Composable
private fun TikTokExportGuideCard(guide: TikTokExportGuide) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val openFailed = stringResource(R.string.tt_open_failed)
    var expanded by remember { mutableStateOf(false) }
    Text(text = guide.title, style = MaterialTheme.typography.labelLarge)
    Text(text = guide.intro, style = MaterialTheme.typography.bodySmall, color = muted)
    TextButton(onClick = { expanded = !expanded }) {
        Text(text = stringResource(if (expanded) R.string.tt_hide_steps else R.string.tt_show_steps))
    }
    if (expanded) {
        guide.steps.forEachIndexed { index, step ->
            Text(text = "${index + 1}. $step", style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
    OutlinedButton(
        onClick = {
            if (TikTokOpener.openHelp(context, guide.helpUrl) == TikTokOpenResult.Failed) {
                Toast.makeText(context, openFailed, Toast.LENGTH_LONG).show()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text = guide.helpLabel) }
}

@Composable
private fun TikTokSummary(info: TikTokVideoInfo) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, info.thumbnailUrl) {
        value = info.thumbnailUrl?.let { TikTokThumbnailLoader.load(it) }
    }
    thumbnail?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
    val caption = info.caption
    if (caption != null) {
        Text(text = caption, style = MaterialTheme.typography.titleMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
    } else {
        Text(text = stringResource(R.string.tt_public_confirmed), style = MaterialTheme.typography.titleMedium)
        Text(text = stringResource(R.string.tt_no_caption_note), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    val name = info.creatorName
    val handle = info.creatorHandle
    when {
        name != null && handle != null ->
            Text(text = stringResource(R.string.tt_creator_handle, name, handle), style = MaterialTheme.typography.bodyMedium)
        name != null -> Text(text = stringResource(R.string.tt_creator, name), style = MaterialTheme.typography.bodyMedium)
        handle != null -> Text(text = stringResource(R.string.tt_creator, "@$handle"), style = MaterialTheme.typography.bodyMedium)
    }
    val w = info.thumbnailWidthPx
    val h = info.thumbnailHeightPx
    val orientation = info.orientation
    if (w != null && h != null && orientation != null) {
        Text(text = stringResource(R.string.tt_size, orientation, w, h), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    Text(text = stringResource(R.string.tt_source, TikTokVideoInfo.SOURCE_LABEL), style = MaterialTheme.typography.bodySmall, color = muted)
}
