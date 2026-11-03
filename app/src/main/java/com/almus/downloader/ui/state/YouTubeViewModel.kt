package com.almus.downloader.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almus.downloader.AlmusApplication
import com.almus.downloader.youtube.YouTubeLookup
import com.almus.downloader.youtube.YouTubeReport
import com.almus.downloader.youtube.YouTubeVideoRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface YouTubeUiState {
    data object Loading : YouTubeUiState

    data class Ready(val report: YouTubeReport) : YouTubeUiState
}

/** Loads the public details of one YouTube video from YouTube's official services. Downloads nothing. */
class YouTubeViewModel(application: Application) : AndroidViewModel(application) {
    private val integration = (application as AlmusApplication).container.youtube

    private val _state = MutableStateFlow<YouTubeUiState>(YouTubeUiState.Loading)
    val state: StateFlow<YouTubeUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Loads [ref]. A finished answer for the same video is reused, except a failed one, which is asked again. */
    fun load(ref: YouTubeVideoRef) {
        val current = _state.value
        if (current is YouTubeUiState.Ready && current.report.ref.videoId == ref.videoId &&
            current.report.lookup !is YouTubeLookup.Failed
        ) {
            return
        }
        job?.cancel()
        _state.value = YouTubeUiState.Loading
        job = viewModelScope.launch {
            _state.value = YouTubeUiState.Ready(integration.inspect(ref))
        }
    }

    fun retry(ref: YouTubeVideoRef) {
        job?.cancel()
        _state.value = YouTubeUiState.Loading
        job = viewModelScope.launch {
            _state.value = YouTubeUiState.Ready(integration.inspect(ref))
        }
    }
}
