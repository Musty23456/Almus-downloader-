package com.almus.downloader.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.almus.downloader.AlmusApplication
import com.almus.downloader.facebook.FacebookContentRef
import com.almus.downloader.facebook.FacebookLookup
import com.almus.downloader.facebook.FacebookReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FacebookUiState {
    data object Loading : FacebookUiState

    data class Ready(val report: FacebookReport) : FacebookUiState
}

/** Loads the public details of one Facebook video from Facebook's official services. Downloads nothing. */
class FacebookViewModel(application: Application) : AndroidViewModel(application) {
    private val integration = (application as AlmusApplication).container.facebook

    private val _state = MutableStateFlow<FacebookUiState>(FacebookUiState.Loading)
    val state: StateFlow<FacebookUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Loads [ref]. A finished answer for the same content is reused, except a failed one, which is asked again. */
    fun load(ref: FacebookContentRef) {
        val current = _state.value
        if (current is FacebookUiState.Ready && current.report.ref.contentId == ref.contentId &&
            current.report.lookup !is FacebookLookup.Failed
        ) {
            return
        }
        start(ref)
    }

    fun retry(ref: FacebookContentRef) = start(ref)

    private fun start(ref: FacebookContentRef) {
        job?.cancel()
        _state.value = FacebookUiState.Loading
        job = viewModelScope.launch {
            _state.value = FacebookUiState.Ready(integration.inspect(ref))
        }
    }
}
