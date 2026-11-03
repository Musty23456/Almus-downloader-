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
import com.almus.downloader.integration.ContentInfo
import com.almus.downloader.integration.ContentLookup
import com.almus.downloader.integration.ContentRef
import com.almus.downloader.integration.DownloadDecision
import com.almus.downloader.integration.OpenKind
import com.almus.downloader.integration.OpenOption
import com.almus.downloader.integration.OwnContentGuide
import com.almus.downloader.integration.PlatformReport
import com.almus.downloader.integration.PlatformSpecs
import com.almus.downloader.integration.android.PlatformOpenResult
import com.almus.downloader.integration.android.PlatformOpener
import com.almus.downloader.integration.android.PlatformThumbnailLoader
import com.almus.downloader.platform.MediaPlatform
import com.almus.downloader.ui.state.PlatformUiState
import com.almus.downloader.ui.state.PlatformViewModel

/**
 * Shows what a platform's official oEmbed service says about one piece of content (title, creator, length and
 * thumbnail where the service gives them), what kind of media it is, the official ways to open it in the platform,
 * why it cannot be downloaded here, and the platform's own tools for the person's own content. Nothing is saved from
 * this dialog. [onUseLink] receives an address only when an authorized download provider offered a file (none
 * ships today); it then goes through the normal link check and approval.
 */
@Composable
fun PlatformContentDialog(
    ref: ContentRef,
    onUseLink: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: PlatformViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(ref.key) { viewModel.load(ref) }

    // A previous answer must never flash up for different content.
    val report: PlatformReport? = (state as? PlatformUiState.Ready)?.report?.takeIf { it.ref.key == ref.key }
    val failed = report?.lookup is ContentLookup.Failed
    val spec = PlatformSpecs.of(ref.platform)
    val platformName = spec?.name ?: ref.platform.displayName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = report?.title ?: spec?.dialogTitle(ref) ?: platformName) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (report == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(R.string.pf_loading, platformName), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    PlatformReportBody(report = report, onUseLink = onUseLink)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.pf_close)) }
        },
        dismissButton = if (failed) {
            @Composable {
                TextButton(onClick = { viewModel.retry(ref) }) { Text(text = stringResource(R.string.pf_retry)) }
            }
        } else {
            null
        },
    )
}

/**
 * "Open in <platform>" buttons. Used for content, and for pages that cannot be shown here (a profile, a board). The
 * platform, not this app, decides whether the person may see the content.
 */
@Composable
fun PlatformOpenButtons(options: List<OpenOption>) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val platformName = options.firstOrNull()?.platform?.displayName ?: ""
    val appMissing = stringResource(R.string.pf_app_missing, platformName)
    val openFailed = stringResource(R.string.pf_open_failed)
    options.forEach { option ->
        OutlinedButton(
            onClick = {
                when (PlatformOpener.open(context, option)) {
                    PlatformOpenResult.Opened -> Unit
                    PlatformOpenResult.AppNotInstalled -> {
                        Toast.makeText(context, appMissing, Toast.LENGTH_LONG).show()
                        val browser = options.firstOrNull { it.kind == OpenKind.OpenInBrowser }
                        if (browser != null) PlatformOpener.open(context, browser)
                    }
                    PlatformOpenResult.Failed -> Toast.makeText(context, openFailed, Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(text = option.title) }
        Text(text = option.description, style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun PlatformReportBody(report: PlatformReport, onUseLink: (String) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val notice = MaterialTheme.colorScheme.tertiary
    val platformName = report.ref.platform.displayName
    when (val lookup = report.lookup) {
        is ContentLookup.Found -> {
            PlatformSummary(info = lookup.info, platformName = platformName)
            lookup.notices.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = notice) }
        }
        is ContentLookup.Unavailable -> {
            Text(text = lookup.message, style = MaterialTheme.typography.bodyMedium)
            lookup.notices.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = notice) }
        }
        is ContentLookup.Failed -> {
            Text(text = lookup.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

    Text(text = stringResource(R.string.pf_section_media), style = MaterialTheme.typography.titleSmall)
    Text(text = report.media.summary, style = MaterialTheme.typography.bodyMedium)
    report.media.details.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted) }

    Text(text = stringResource(R.string.pf_section_open, platformName), style = MaterialTheme.typography.titleSmall)
    PlatformOpenButtons(report.openOptions)

    Text(text = stringResource(R.string.pf_section_download), style = MaterialTheme.typography.titleSmall)
    when (val decision = report.download) {
        is DownloadDecision.NotAvailable -> {
            Text(text = decision.headline, style = MaterialTheme.typography.bodyMedium)
            decision.reasons.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted) }
            Text(text = stringResource(R.string.pf_alternatives), style = MaterialTheme.typography.labelLarge)
            decision.alternatives.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = muted) }
        }
        is DownloadDecision.Offered -> {
            decision.offers.forEach { offer ->
                Button(onClick = { onUseLink(offer.fileUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.pf_offer_button, offer.label))
                }
                Text(text = stringResource(R.string.pf_offer_by, offer.authorizedBy), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }

    Text(text = report.ownContent.title, style = MaterialTheme.typography.titleSmall)
    PlatformGuideCard(guide = report.ownContent, platform = report.ref.platform)
}

@Composable
private fun PlatformGuideCard(guide: OwnContentGuide, platform: MediaPlatform) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val openFailed = stringResource(R.string.pf_open_failed)
    var expanded by remember { mutableStateOf(false) }
    Text(text = guide.intro, style = MaterialTheme.typography.bodySmall, color = muted)
    TextButton(onClick = { expanded = !expanded }) {
        Text(text = stringResource(if (expanded) R.string.pf_hide_steps else R.string.pf_show_steps))
    }
    if (expanded) {
        guide.steps.forEachIndexed { index, step ->
            Text(text = "${index + 1}. $step", style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
    OutlinedButton(
        onClick = {
            if (PlatformOpener.openHelp(context, platform, guide.helpUrl) == PlatformOpenResult.Failed) {
                Toast.makeText(context, openFailed, Toast.LENGTH_LONG).show()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text = guide.helpLabel) }
}

@Composable
private fun PlatformSummary(info: ContentInfo, platformName: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, info.thumbnailUrl) {
        value = info.thumbnailUrl?.let { PlatformThumbnailLoader.load(it) }
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
    val title = info.title
    if (title != null) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
    } else {
        Text(text = stringResource(R.string.pf_public_confirmed, platformName), style = MaterialTheme.typography.titleMedium)
        Text(text = stringResource(R.string.pf_no_details_note, platformName), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    info.authorName?.let { Text(text = stringResource(R.string.pf_creator, it), style = MaterialTheme.typography.bodyMedium) }
    info.durationText?.let { Text(text = stringResource(R.string.pf_duration, it), style = MaterialTheme.typography.bodySmall, color = muted) }
    info.publishedOn?.let { Text(text = stringResource(R.string.pf_published, it), style = MaterialTheme.typography.bodySmall, color = muted) }
    val w = info.thumbnailWidthPx
    val h = info.thumbnailHeightPx
    val orientation = info.orientation
    if (w != null && h != null && orientation != null) {
        Text(text = stringResource(R.string.pf_size, orientation, w, h), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    if (info.thumbnailUrl == null) {
        Text(text = stringResource(R.string.pf_no_thumbnail, platformName), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    Text(text = stringResource(R.string.pf_source, info.sourceLabel), style = MaterialTheme.typography.bodySmall, color = muted)
}
