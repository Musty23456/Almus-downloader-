package com.almus.downloader.facebook.android

import android.content.Context
import com.almus.downloader.facebook.FacebookAccessToken

/**
 * Keeps the person's own Facebook access token in the app's private storage (backup is off for this app). No token
 * is built into the app, and the app never asks for a Facebook password or signs in. The token only lets
 * Facebook's official Graph API return more details about what that account is allowed to see.
 */
class SharedPrefsFacebookTokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The saved token, or null. */
    var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)?.takeIf { FacebookAccessToken.isValidFormat(it) }
        private set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    /** Saves [raw] (trimmed) when it has the shape of an access token. Returns false and saves nothing otherwise. */
    fun save(raw: String): Boolean {
        val token = raw.trim()
        if (!FacebookAccessToken.isValidFormat(token)) return false
        accessToken = token
        return true
    }

    fun clear() {
        accessToken = null
    }

    /** The last four characters, for showing that a token is saved without showing the token. */
    fun hint(): String? = accessToken?.takeLast(4)

    private companion object {
        const val PREFS_NAME = "almus_facebook"
        const val KEY_TOKEN = "token_value"
    }
}
