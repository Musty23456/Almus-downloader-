package com.almus.downloader.ui.state

/** Generic screen state used with [com.almus.downloader.ui.components.StateHost]. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Error(val message: String) : UiState<Nothing>

    data class Success<out T>(val data: T) : UiState<T>
}
