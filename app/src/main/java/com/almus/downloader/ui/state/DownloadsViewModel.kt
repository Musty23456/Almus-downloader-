package com.almus.downloader.ui.state

import android.app.Application
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almus.downloader.AlmusApplication
import com.almus.downloader.download.BulkAction
import com.almus.downloader.download.InspectResult
import com.almus.downloader.download.LinkInfo
import com.almus.downloader.download.SaveFolders
import com.almus.downloader.download.SaveLayout
import com.almus.downloader.download.StartResult
import com.almus.downloader.model.DownloadItem
import com.almus.downloader.model.DownloadStatus
import com.almus.downloader.model.FileCategory
import com.almus.downloader.model.toItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State of the "add a download" flow on the Home screen. */
sealed interface AddDownloadState {
    data object Idle : AddDownloadState

    data object Checking : AddDownloadState

    /** The link is reachable; waiting for the user to approve it. */
    data class Confirm(val info: LinkInfo) : AddDownloadState

    data class Error(val message: String) : AddDownloadState
}

/** An action waiting for the user's confirmation. [singleName] is set when it affects one download. */
data class PendingConfirm(
    val action: BulkAction,
    val ids: List<Long>,
    val singleName: String? = null,
)

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AlmusApplication).container
    private val repository = container.repository
    private val settings = container.settings

    /** The list shown everywhere. Always derived from the database plus live speed data. */
    val downloads: StateFlow<List<DownloadItem>> = combine(repository.downloads, repository.speeds) { rows, speeds ->
        rows.map { it.toItem(speeds[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _addState = MutableStateFlow<AddDownloadState>(AddDownloadState.Idle)
    val addState: StateFlow<AddDownloadState> = _addState.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _pending = MutableStateFlow<PendingConfirm?>(null)
    val pendingConfirm: StateFlow<PendingConfirm?> = _pending.asStateFlow()

    private val _maxConcurrent = MutableStateFlow(settings.maxConcurrent)
    val maxConcurrent: StateFlow<Int> = _maxConcurrent.asStateFlow()

    private val _saveLayout = MutableStateFlow(container.saveSettings.layout)
    val saveLayout: StateFlow<SaveLayout> = _saveLayout.asStateFlow()

    private var checkJob: Job? = null
    private var noticeJob: Job? = null

    // ------------------------------------------------------------------ adding

    /** Step 1: validate, check for duplicates and inspect the link. Nothing is downloaded yet. */
    fun checkLink(rawUrl: String) {
        if (_addState.value is AddDownloadState.Checking) return
        _addState.value = AddDownloadState.Checking
        checkJob = viewModelScope.launch {
            if (repository.isDuplicate(rawUrl)) {
                _addState.value = AddDownloadState.Error("This link is already in your queue or downloading.")
                return@launch
            }
            _addState.value = when (val result = container.inspector.inspect(rawUrl)) {
                is InspectResult.Success -> AddDownloadState.Confirm(result.info)
                is InspectResult.Failure -> AddDownloadState.Error(result.message)
            }
        }
    }

    /** Step 2: the user approved what the dialog showed. */
    fun confirmDownload() {
        val state = _addState.value as? AddDownloadState.Confirm ?: return
        _addState.value = AddDownloadState.Idle
        viewModelScope.launch {
            when (val result = repository.start(state.info)) {
                is StartResult.Started -> {
                    showNotice(
                        if (result.queued) {
                            "Added to the queue. It starts when a download slot is free."
                        } else {
                            "Download started. Track it in the Downloads tab."
                        },
                    )
                    repository.refresh()
                }
                is StartResult.Rejected -> _addState.value = AddDownloadState.Error(result.message)
            }
        }
    }

    /** The folder the confirmation dialog tells the user the file will be saved in. */
    fun savePathFor(info: LinkInfo): String {
        val subfolder = SaveFolders.subfolderFor(container.saveSettings.layout, info.fileName, info.mimeType)
        return SaveFolders.displayPath(container.storage.target(), subfolder)
    }

    fun dismissAddState() {
        checkJob?.cancel()
        _addState.value = AddDownloadState.Idle
    }

    // ------------------------------------------------------------------ single-item actions

    fun cancel(id: Long) {
        viewModelScope.launch { repository.cancel(id) }
    }

    /** Only waiting downloads can be held: a running transfer cannot be paused. */
    fun hold(id: Long) {
        viewModelScope.launch { repository.hold(id) }
    }

    fun resume(id: Long) {
        viewModelScope.launch { repository.resume(id) }
    }

    fun retry(id: Long) {
        viewModelScope.launch {
            when (val result = repository.retry(id)) {
                is StartResult.Started -> {
                    showNotice(if (result.queued) "Added to the end of the queue." else "Download restarted.")
                    repository.refresh()
                }
                is StartResult.Rejected -> showNotice(result.message)
            }
        }
    }

    /** Removes a finished record from the list. The downloaded file stays on the device. */
    fun removeFromList(id: Long) {
        viewModelScope.launch { repository.removeRecords(id) }
    }

    /** Asks for confirmation before deleting one downloaded file. */
    fun requestDeleteFile(id: Long, fileName: String) {
        _pending.value = PendingConfirm(BulkAction.DeleteFiles, listOf(id), fileName)
    }

    /** Opens a finished file in another app. Only ever called from a tap. Never opens APKs. */
    fun open(id: Long) {
        viewModelScope.launch {
            val entity = repository.get(id) ?: return@launch
            val item = entity.toItem()
            if (item.status != DownloadStatus.Completed) return@launch
            val app = getApplication<Application>()

            if (item.category == FileCategory.Apks) {
                // Installing is a decision for the user, outside this app.
                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { app.startActivity(intent) }
                showNotice("APK files are never opened or installed by this app. Install it from your Downloads only if you trust the source.")
                return@launch
            }

            val uri = entity.systemId?.let { container.systemDownloader.contentUriFor(it) }
            if (uri == null) {
                showNotice("The file is no longer available.")
                return@launch
            }
            val type = entity.mimeType ?: app.contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                app.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                showNotice("No app on this device can open this type of file.")
            } catch (_: SecurityException) {
                showNotice("This file could not be opened.")
            }
        }
    }

    // ------------------------------------------------------------------ bulk actions

    /** Asks for confirmation. Nothing happens until [confirmPending]. */
    fun requestBulk(action: BulkAction, ids: List<Long>) {
        if (ids.isEmpty()) return
        _pending.value = PendingConfirm(action, ids)
    }

    fun dismissPending() {
        _pending.value = null
    }

    fun confirmPending() {
        val pending = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch { runBulk(pending.action, pending.ids) }
    }

    private suspend fun runBulk(action: BulkAction, ids: List<Long>) {
        when (action) {
            BulkAction.CancelAll -> {
                val n = repository.cancel(ids)
                showNotice("Cancelled $n download(s).")
            }
            BulkAction.HoldAll -> {
                val n = repository.hold(ids)
                showNotice("Put $n download(s) on hold. Downloads that are already running cannot be paused.")
            }
            BulkAction.ResumeAll -> {
                val n = repository.resume(ids)
                showNotice("Put $n download(s) back in the queue.")
            }
            BulkAction.RetryAll -> {
                val r = repository.retry(ids)
                showNotice(
                    "Retrying ${r.done} download(s)." +
                        if (r.skipped > 0) " Skipped ${r.skipped}: ${r.messages.firstOrNull() ?: "not eligible"}" else "",
                )
            }
            BulkAction.ClearHistory -> {
                val n = repository.removeRecords(ids)
                showNotice("Removed $n item(s) from the list. The files were not deleted.")
            }
            BulkAction.DeleteFiles -> {
                val r = repository.deleteFiles(ids)
                showNotice(
                    "Deleted ${r.done} file(s)." +
                        if (r.skipped > 0) " ${r.messages.firstOrNull() ?: "${r.skipped} could not be deleted."}" else "",
                )
            }
        }
        repository.refresh()
    }

    // ------------------------------------------------------------------ settings

    fun setMaxConcurrent(value: Int) {
        settings.maxConcurrent = value
        _maxConcurrent.value = settings.maxConcurrent
        viewModelScope.launch { repository.onSettingsChanged() }
    }

    /** Applies to downloads that start from now on. Files that already exist are never moved. */
    fun setSaveLayout(layout: SaveLayout) {
        container.saveSettings.layout = layout
        _saveLayout.value = container.saveSettings.layout
    }

    // ------------------------------------------------------------------ sync

    /**
     * Keeps stored records and the queue in sync with the system while the app is visible.
     * The first pass also restores real status after a restart and removes orphaned system
     * downloads. Cancelled together with the screen's lifecycle, so nothing keeps running.
     */
    suspend fun pollWhileVisible() {
        repository.reconcile()
        while (true) {
            val needsPolling = repository.refresh()
            delay(if (needsPolling) ACTIVE_POLL_MS else IDLE_POLL_MS)
        }
    }

    fun showNotice(message: String) {
        _notice.value = message
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MS)
            _notice.value = null
        }
    }

    fun clearNotice() {
        noticeJob?.cancel()
        _notice.value = null
    }

    private companion object {
        const val ACTIVE_POLL_MS = 1_000L
        const val IDLE_POLL_MS = 3_000L
        const val NOTICE_MS = 7_000L
    }
}
