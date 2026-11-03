package com.almus.downloader.youtube.android

import android.content.Context
import com.almus.downloader.youtube.YouTubeApiKey

/**
 * Keeps the person's own YouTube Data API key in the app's private storage. No key is built into the app.
 * The key only enables extra public details (duration, age and region flags); it is never a sign-in.
 */
class SharedPrefsYouTubeKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The saved key, or null. */
    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { YouTubeApiKey.isValidFormat(it) }
        private set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_API_KEY) else putString(KEY_API_KEY, value)
            }.apply()
        }

    /** Saves [raw] (trimmed) when it has the shape of a Google API key. Returns false and saves nothing otherwise. */
    fun save(raw: String): Boolean {
        val key = raw.trim()
        if (!YouTubeApiKey.isValidFormat(key)) return false
        apiKey = key
        return true
    }

    fun clear() {
        apiKey = null
    }

    /** The last four characters, for showing that a key is saved without showing the key. */
    fun hint(): String? = apiKey?.takeLast(4)

    private companion object {
        const val PREFS_NAME = "almus_youtube"
        const val KEY_API_KEY = "data_api_key"
    }
}
