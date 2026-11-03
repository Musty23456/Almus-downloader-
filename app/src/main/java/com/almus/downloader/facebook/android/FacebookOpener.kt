package com.almus.downloader.facebook.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.almus.downloader.facebook.FacebookLinks
import com.almus.downloader.facebook.FacebookOpenKind
import com.almus.downloader.facebook.FacebookOpenOption

enum class FacebookOpenResult { Opened, AppNotInstalled, Failed }

/**
 * Hands an official Facebook address to the official Facebook app, to a browser, or to the share sheet.
 * Only called from a tap. The address always comes from a [FacebookOpenOption], and anything that is not an https
 * address on a Facebook page host is refused. The app never signs in: Facebook shows or refuses the content itself.
 */
object FacebookOpener {
    const val FACEBOOK_PACKAGE = "com.facebook.katana"

    fun open(context: Context, option: FacebookOpenOption): FacebookOpenResult {
        if (!FacebookLinks.isOfficialPageUrl(option.url)) return FacebookOpenResult.Failed
        return when (option.kind) {
            FacebookOpenKind.OpenInApp -> openInApp(context, option.url)
            FacebookOpenKind.OpenInBrowser -> openInBrowser(context, option.url)
            FacebookOpenKind.ShareLink -> share(context, option.url)
        }
    }

    /** Opens one of Facebook's own help pages in a browser. Same address check as everything else. */
    fun openHelp(context: Context, url: String): FacebookOpenResult =
        if (FacebookLinks.isOfficialPageUrl(url)) openInBrowser(context, url) else FacebookOpenResult.Failed

    private fun openInApp(context: Context, url: String): FacebookOpenResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage(FACEBOOK_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            FacebookOpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            FacebookOpenResult.AppNotInstalled
        } catch (_: SecurityException) {
            FacebookOpenResult.Failed
        }
    }

    private fun openInBrowser(context: Context, url: String): FacebookOpenResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            FacebookOpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            FacebookOpenResult.Failed
        } catch (_: SecurityException) {
            FacebookOpenResult.Failed
        }
    }

    private fun share(context: Context, url: String): FacebookOpenResult {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            FacebookOpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            FacebookOpenResult.Failed
        } catch (_: SecurityException) {
            FacebookOpenResult.Failed
        }
    }
}
