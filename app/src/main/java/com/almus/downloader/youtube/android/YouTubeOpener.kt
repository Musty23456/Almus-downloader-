package com.almus.downloader.youtube.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.almus.downloader.youtube.ViewingKind
import com.almus.downloader.youtube.ViewingOption

enum class OpenResult { Opened, AppNotInstalled, Failed }

/**
 * Hands the official youtube.com address of a video to the official YouTube app, to a browser, or to the share sheet.
 * Only called from a tap. The address always comes from a [ViewingOption] built out of a validated video id.
 */
object YouTubeOpener {
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    private const val PREFIX = "https://www.youtube.com/watch?v="

    fun open(context: Context, option: ViewingOption): OpenResult {
        if (!option.url.startsWith(PREFIX)) return OpenResult.Failed
        return when (option.kind) {
            ViewingKind.OpenInApp -> openInApp(context, option.url)
            ViewingKind.OpenInBrowser -> openInBrowser(context, option.url)
            ViewingKind.ShareLink -> share(context, option.url)
        }
    }

    private fun openInApp(context: Context, url: String): OpenResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage(YOUTUBE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            OpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            OpenResult.AppNotInstalled
        } catch (_: SecurityException) {
            OpenResult.Failed
        }
    }

    private fun openInBrowser(context: Context, url: String): OpenResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            OpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            OpenResult.Failed
        } catch (_: SecurityException) {
            OpenResult.Failed
        }
    }

    private fun share(context: Context, url: String): OpenResult {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            OpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            OpenResult.Failed
        } catch (_: SecurityException) {
            OpenResult.Failed
        }
    }
}
