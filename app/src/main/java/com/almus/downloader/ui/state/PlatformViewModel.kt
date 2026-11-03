package com.almus.downloader.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almus.downloader.AlmusApplication
import com.almus.downloader.integration.ContentLookup
import com.almus.downloader.integration.ContentRef
import com.almus.downloader.integration.PlatformReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlatformUiState {
    data object Loading : PlatformUiState

    data class Ready(val report: PlatformReport) : PlatformUiState
}

/**
 * Loads the public details of one piece of content (Instagram, X, Vimeo, Dailymotion, Reddit or Pinterest) from the
 * platform's own oEmbed service. Downloads nothing.
 */
class PlatformViewModel(application: Application) : AndroidViewModel(application) {
    private val integration = (application as AlmusApplication).container.platforms

    private val _state = MutableStateFlow<PlatformUiState>(PlatformUiState.Loading)
    val state: StateFlow<PlatformUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Loads [ref]. A finished answer for the same content is reused, except a failed one, which is asked again. */
    fun load(ref: ContentRef) {
        val current = _state.value
        if (current is PlatformUiState.Ready && current.report.ref.key == ref.key &&
            current.report.lookup !is ContentLookup.Failed
        ) {
            return
        }
        start(ref)
    }

    fun retry(ref: ContentRef) = start(ref)

    private fun start(ref: ContentRef) {
        job?.cancel()
        _state.value = PlatformUiState.Loading
        job = viewModelScope.launch {
            _state.value = PlatformUiState.Ready(integration.inspect(ref))
        }
    }
}
