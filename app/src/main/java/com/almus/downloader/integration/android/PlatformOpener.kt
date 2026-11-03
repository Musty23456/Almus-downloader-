package com.almus.downloader.integration.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.almus.downloader.integration.OpenKind
import com.almus.downloader.integration.OpenOption
import com.almus.downloader.integration.PlatformLinks
import com.almus.downloader.integration.PlatformSpecs
import com.almus.downloader.platform.MediaPlatform

enum class PlatformOpenResult { Opened, AppNotInstalled, Failed }

/**
 * Hands an official address to the platform's own app, to a browser, or to the share sheet. Only called from a tap.
 * The address always comes from an [OpenOption], and anything that is not an https address on one of the platform's
 * page domains is refused. The app never signs in: the platform shows or refuses the content itself.
 */
object PlatformOpener {
    fun open(context: Context, option: OpenOption): PlatformOpenResult {
        val spec = PlatformSpecs.of(option.platform) ?: return PlatformOpenResult.Failed
        if (!PlatformLinks.isOfficialPageUrl(spec, option.url)) return PlatformOpenResult.Failed
        return when (option.kind) {
            OpenKind.OpenInApp -> openInApp(context, spec.appPackages, option.url)
            OpenKind.OpenInBrowser -> openInBrowser(context, option.url)
            OpenKind.ShareLink -> share(context, option.url)
        }
    }

    /** Opens the platform's Help Center in a browser. Only the address named in the platform's own guide is allowed. */
    fun openHelp(context: Context, platform: MediaPlatform, url: String): PlatformOpenResult {
        val spec = PlatformSpecs.of(platform) ?: return PlatformOpenResult.Failed
        return if (PlatformLinks.isOfficialHelpUrl(spec, url)) openInBrowser(context, url) else PlatformOpenResult.Failed
    }

    private fun openInApp(context: Context, packages: List<String>, url: String): PlatformOpenResult {
        for (pkg in packages) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return PlatformOpenResult.Opened
            } catch (_: ActivityNotFoundException) {
                // Not installed (or not able to open this address): try the next package.
            } catch (_: SecurityException) {
                return PlatformOpenResult.Failed
            }
        }
        return PlatformOpenResult.AppNotInstalled
    }

    private fun openInBrowser(context: Context, url: String): PlatformOpenResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            PlatformOpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            PlatformOpenResult.Failed
        } catch (_: SecurityException) {
            PlatformOpenResult.Failed
        }
    }

    private fun share(context: Context, url: String): PlatformOpenResult {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            PlatformOpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            PlatformOpenResult.Failed
        } catch (_: SecurityException) {
            PlatformOpenResult.Failed
        }
    }
}
