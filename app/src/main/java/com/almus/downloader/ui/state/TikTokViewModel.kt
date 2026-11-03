package com.almus.downloader.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almus.downloader.AlmusApplication
import com.almus.downloader.tiktok.TikTokContentRef
import com.almus.downloader.tiktok.TikTokLookup
import com.almus.downloader.tiktok.TikTokReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TikTokUiState {
    data object Loading : TikTokUiState

    data class Ready(val report: TikTokReport) : TikTokUiState
}

/** Loads the public details of one TikTok video from TikTok's official oEmbed service. Downloads nothing. */
class TikTokViewModel(application: Application) : AndroidViewModel(application) {
    private val integration = (application as AlmusApplication).container.tiktok

    private val _state = MutableStateFlow<TikTokUiState>(TikTokUiState.Loading)
    val state: StateFlow<TikTokUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Loads [ref]. A finished answer for the same video is reused, except a failed one, which is asked again. */
    fun load(ref: TikTokContentRef) {
        val current = _state.value
        if (current is TikTokUiState.Ready && current.report.ref.contentId == ref.contentId &&
            current.report.lookup !is TikTokLookup.Failed
        ) {
            return
        }
        start(ref)
    }

    fun retry(ref: TikTokContentRef) = start(ref)

    private fun start(ref: TikTokContentRef) {
        job?.cancel()
        _state.value = TikTokUiState.Loading
        job = viewModelScope.launch {
            _state.value = TikTokUiState.Ready(integration.inspect(ref))
        }
    }
}
