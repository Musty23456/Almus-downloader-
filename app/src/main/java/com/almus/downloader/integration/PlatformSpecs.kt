package com.almus.downloader.integration

import com.almus.downloader.integration.specs.DailymotionSpec
import com.almus.downloader.integration.specs.InstagramSpec
import com.almus.downloader.integration.specs.PinterestSpec
import com.almus.downloader.integration.specs.RedditSpec
import com.almus.downloader.integration.specs.VimeoSpec
import com.almus.downloader.integration.specs.XSpec
import com.almus.downloader.platform.MediaPlatform

/**
 * The list of platforms this module handles. Adding a platform means writing one [PlatformSpec] and adding it here
 * (and its recognition adapter in the `platform` package). YouTube, Facebook and TikTok have their own modules.
 */
object PlatformSpecs {
    val all: List<PlatformSpec> = listOf(InstagramSpec, XSpec, VimeoSpec, DailymotionSpec, RedditSpec, PinterestSpec)

    fun of(platform: MediaPlatform?): PlatformSpec? = if (platform == null) null else all.firstOrNull { it.platform == platform }
}
