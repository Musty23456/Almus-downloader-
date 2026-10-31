package com.almus.downloader.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.almus.downloader.download.Permissions
import com.almus.downloader.ui.state.AddDownloadState
import com.almus.downloader.ui.state.DownloadsViewModel

/**
 * Shows the download confirmation dialog whenever the download flow is waiting for the user's approval.
 * Used by the browser, so a link found on a web page goes through exactly the same check and the
 * same "Download?" question as a link pasted on Home. Nothing is saved before the user taps Download.
 */
@Composable
fun ConfirmDownloadHost(viewModel: DownloadsViewModel) {
    val context = LocalContext.current
    val addState by viewModel.addState.collectAsStateWithLifecycle()

    // Asked only at the moment the user approves a download. Declining never blocks it.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> viewModel.confirmDownload() }

    val confirm = addState as? AddDownloadState.Confirm ?: return
    ConfirmDownloadDialog(
        info = confirm.info,
        savePath = viewModel.savePathFor(confirm.info),
        showNotificationHint = Permissions.needsNotificationPermission(context),
        onConfirm = {
            val missing = Permissions.missing(context)
            if (missing.isEmpty()) {
                viewModel.confirmDownload()
            } else {
                permissionLauncher.launch(missing.toTypedArray())
            }
        },
        onDismiss = { viewModel.dismissAddState() },
    )
}
