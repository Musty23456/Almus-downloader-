package com.almus.downloader.tiktok.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.almus.downloader.tiktok.TikTokLinks
import com.almus.downloader.tiktok.TikTokOpenKind
import com.almus.downloader.tiktok.TikTokOpenOption

enum class TikTokOpenResult { Opened, AppNotInstalled, Failed }

/**
 * Hands an official TikTok address to the official TikTok app, to a browser, or to the share sheet.
 * Only called from a tap. The address always comes from a [TikTokOpenOption], and anything that is not an https
 * address on a TikTok page host is refused. The app never signs in: TikTok shows or refuses the content itself.
 */
object TikTokOpener {
    /** TikTok's app packages: the worldwide app and the one used in some regions. */
    val TIKTOK_PACKAGES = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    fun open(context: Context, option: TikTokOpenOption): TikTokOpenResult {
        if (!TikTokLinks.isOfficialPageUrl(option.url)) return TikTokOpenResult.Failed
        return when (option.kind) {
            TikTokOpenKind.OpenInApp -> openInApp(context, option.url)
            TikTokOpenKind.OpenInBrowser -> openInBrowser(context, option.url)
            TikTokOpenKind.ShareLink -> share(context, option.url)
        }
    }

    /** Opens TikTok's Help Center in a browser. Same address check as everything else. */
    fun openHelp(context: Context, url: String): TikTokOpenResult =
        if (TikTokLinks.isOfficialPageUrl(url)) openInBrowser(context, url) else TikTokOpenResult.Failed

    private fun openInApp(context: Context, url: String): TikTokOpenResult {
        for (pkg in TIKTOK_PACKAGES) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return TikTokOpenResult.Opened
            } catch (_: ActivityNotFoundException) {
                // Not installed (or not able to open this address): try the next package.
            } catch (_: SecurityException) {
                return TikTokOpenResult.Failed
            }
        }
        return TikTokOpenResult.AppNotInstalled
    }

    private fun openInBrowser(context: Context, url: String): TikTokOpenResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            TikTokOpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            TikTokOpenResult.Failed
        } catch (_: SecurityException) {
            TikTokOpenResult.Failed
        }
    }

    private fun share(context: Context, url: String): TikTokOpenResult {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            TikTokOpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            TikTokOpenResult.Failed
        } catch (_: SecurityException) {
            TikTokOpenResult.Failed
        }
    }
}
