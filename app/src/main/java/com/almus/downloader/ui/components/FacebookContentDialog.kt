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
import com.almus.downloader.facebook.FacebookContentRef
import com.almus.downloader.facebook.FacebookDownloadDecision
import com.almus.downloader.facebook.FacebookExportGuide
import com.almus.downloader.facebook.FacebookLookup
import com.almus.downloader.facebook.FacebookOpenKind
import com.almus.downloader.facebook.FacebookOpenOption
import com.almus.downloader.facebook.FacebookReport
import com.almus.downloader.facebook.FacebookVideoInfo
import com.almus.downloader.facebook.android.FacebookOpenResult
import com.almus.downloader.facebook.android.FacebookOpener
import com.almus.downloader.facebook.android.FacebookThumbnailLoader
import com.almus.downloader.platform.LinkKind
import com.almus.downloader.ui.state.FacebookUiState
import com.almus.downloader.ui.state.FacebookViewModel
import com.almus.downloader.youtube.IsoDuration

/**
 * Shows what Facebook's official services say about a video, Reel or live video, the official ways to open it in
 * Facebook, why it cannot be downloaded here, and Facebook's official export for the person's own videos.
 * Nothing is saved from this dialog. [onUseLink] receives an address only when Facebook's API returned a file for the
 * person's OWN video (see FacebookDownloadPolicy); it then goes through the normal link check and approval.
 */
@Composable
fun FacebookContentDialog(
    ref: FacebookContentRef,
    onUseLink: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: FacebookViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(ref.contentId) { viewModel.load(ref) }

    // A previous video's answer must never flash up for this one.
    val report: FacebookReport? = (state as? FacebookUiState.Ready)?.report?.takeIf { it.ref.contentId == ref.contentId }
    val failed = report?.lookup is FacebookLookup.Failed
    val title = when (ref.kind) {
        LinkKind.ShortVideo -> R.string.fb_title_reel
        LinkKind.Live -> R.string.fb_title_live
        else -> R.string.fb_title_video
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (report == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(R.string.fb_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    FacebookReportBody(report = report, onUseLink = onUseLink)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.fb_close)) }
        },
        dismissButton = if (failed) {
            @Composable {
                TextButton(onClick = { viewModel.retry(ref) }) { Text(text = stringResource(R.string.fb_retry)) }
            }
        } else {
            null
        },
    )
}

/**
 * "Open in Facebook" buttons for content that cannot be shown here (a group post, a story, a page that needs
 * sign-in). Facebook, not this app, decides whether the person may see it.
 */
@Composable
fun FacebookOpenButtons(options: List<FacebookOpenOption>) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val appMissing = stringResource(R.string.fb_app_missing)
    val openFailed = stringResource(R.string.fb_open_failed)
    options.forEach { option ->
        OutlinedButton(
            onClick = {
                when (FacebookOpener.open(context, option)) {
                    FacebookOpenResult.Opened -> Unit
                    FacebookOpenResult.AppNotInstalled -> {
                        Toast.makeText(context, appMissing, Toast.LENGTH_LONG).show()
                        val browser = options.firstOrNull { it.kind == FacebookOpenKind.OpenInBrowser }
                        if (browser != null) FacebookOpener.open(context, browser)
                    }
                    FacebookOpenResult.Failed -> Toast.makeText(context, openFailed, Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(text = option.title) }
        Text(text = option.description, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun FacebookReportBody(report: FacebookReport, onUseLink: (String) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val notice = MaterialTheme.colorScheme.tertiary
    when (val lookup = report.lookup) {
        is FacebookLookup.Found -> {
            FacebookSummary(report.ref, lookup.info)
            lookup.notices.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = notice) }
        }
        is FacebookLookup.Unavailable -> {
            Text(text = lookup.reason.message, style = MaterialTheme.typography.bodyMedium)
            lookup.notices.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = notice) }
        }
        is FacebookLookup.Failed -> {
            Text(text = lookup.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

    Text(text = stringResource(R.string.fb_section_open), style = MaterialTheme.typography.titleSmall)
    FacebookOpenButtons(report.openOptions)

    Text(text = stringResource(R.string.fb_section_download), style = MaterialTheme.typography.titleSmall)
    when (val decision = report.download) {
        is FacebookDownloadDecision.NotAvailable -> {
            Text(text = decision.headline, style = MaterialTheme.typography.bodyMedium)
            decision.reasons.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted) }
            Text(text = stringResource(R.string.fb_alternatives), style = MaterialTheme.typography.labelLarge)
            decision.alternatives.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted) }
        }
        is FacebookDownloadDecision.Offered -> {
            decision.offers.forEach { offer ->
                Button(onClick = { onUseLink(offer.fileUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.fb_offer_button, offer.label))
                }
                Text(text = stringResource(R.string.fb_offer_by, offer.authorizedBy), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }

    Text(text = stringResource(R.string.fb_section_export), style = MaterialTheme.typography.titleSmall)
    report.exportGuides.forEach { ExportGuideCard(it) }
}

@Composable
private fun ExportGuideCard(guide: FacebookExportGuide) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val openFailed = stringResource(R.string.fb_open_failed)
    var expanded by remember { mutableStateOf(false) }
    Text(text = guide.title, style = MaterialTheme.typography.labelLarge)
    Text(text = guide.intro, style = MaterialTheme.typography.bodySmall, color = muted)
    TextButton(onClick = { expanded = !expanded }) {
        Text(text = stringResource(if (expanded) R.string.fb_hide_steps else R.string.fb_show_steps))
    }
    if (expanded) {
        guide.steps.forEachIndexed { index, step ->
            Text(text = "${index + 1}. $step", style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
    OutlinedButton(
        onClick = {
            if (FacebookOpener.openHelp(context, guide.helpUrl) == FacebookOpenResult.Failed) {
                Toast.makeText(context, openFailed, Toast.LENGTH_LONG).show()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text = guide.helpLabel) }
}

@Composable
private fun FacebookSummary(ref: FacebookContentRef, info: FacebookVideoInfo) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, info.thumbnailUrl) {
        value = info.thumbnailUrl?.let { FacebookThumbnailLoader.load(it) }
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
    val headline = info.title ?: info.description
    if (headline != null) {
        Text(text = headline, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    } else {
        Text(text = stringResource(R.string.fb_public_confirmed, ref.noun), style = MaterialTheme.typography.titleMedium)
    }
    if (info.title != null && info.description != null) {
        Text(text = info.description, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
    info.ownerName?.let { Text(text = stringResource(R.string.fb_owner, it), style = MaterialTheme.typography.bodyMedium) }
    info.durationSeconds?.let { Text(text = stringResource(R.string.fb_duration, IsoDuration.format(it)), style = MaterialTheme.typography.bodyMedium) }
    info.publishedDate?.let { Text(text = stringResource(R.string.fb_published, it), style = MaterialTheme.typography.bodyMedium) }
    val w = info.widthPx
    val h = info.heightPx
    val orientation = info.orientation
    if (w != null && h != null && orientation != null) {
        Text(text = stringResource(R.string.fb_size, orientation, w, h), style = MaterialTheme.typography.bodyMedium)
    }
    if (info.title == null) {
        Text(text = stringResource(R.string.fb_no_title_note), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    if (info.embeddable == false) {
        Text(text = stringResource(R.string.fb_embed_off), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    if (info.ownedByViewer) {
        Text(text = stringResource(R.string.fb_owned), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
    Text(text = stringResource(R.string.fb_source, info.source.label), style = MaterialTheme.typography.bodySmall, color = muted)
}
