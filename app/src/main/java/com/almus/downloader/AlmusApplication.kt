package com.almus.downloader

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.almus.downloader.data.AlmusDatabase
import com.almus.downloader.data.Migrations
import com.almus.downloader.download.AndroidDownloadNotifier
import com.almus.downloader.download.AndroidStorageInfo
import com.almus.downloader.download.AndroidSystemDownloader
import com.almus.downloader.download.DownloadRepository
import com.almus.downloader.download.LinkInspector
import com.almus.downloader.download.SharedPrefsQueueSettings
import com.almus.downloader.download.SharedPrefsSaveSettings
import com.almus.downloader.facebook.FacebookIntegration
import com.almus.downloader.facebook.FacebookMetadataClient
import com.almus.downloader.facebook.android.OkHttpFacebookHttp
import com.almus.downloader.facebook.android.SharedPrefsFacebookTokenStore
import com.almus.downloader.files.AndroidFileActions
import com.almus.downloader.integration.OEmbedClient
import com.almus.downloader.media.AudioConverter
import com.almus.downloader.media.MediaSources
import com.almus.downloader.media.android.OkHttpMediaHttp
import com.almus.downloader.media.sources.CommonsSource
import com.almus.downloader.media.sources.InternetArchiveSource
import com.almus.downloader.integration.PlatformIntegration
import com.almus.downloader.integration.android.OkHttpOEmbedHttp
import com.almus.downloader.files.AndroidFileProbe
import com.almus.downloader.files.DocumentsFolderLister
import com.almus.downloader.files.FileLibrary
import com.almus.downloader.engine.android.EngineHost
import com.almus.downloader.files.SharedPrefsFolderSettings
import com.almus.downloader.platform.LinkIntake
import com.almus.downloader.platform.SafeRedirectResolver
import com.almus.downloader.platform.SmartLinkDetector
import com.almus.downloader.tiktok.TikTokIntegration
import com.almus.downloader.tiktok.TikTokMetadataClient
import com.almus.downloader.tiktok.android.OkHttpTikTokHttp
import com.almus.downloader.youtube.YouTubeIntegration
import com.almus.downloader.youtube.YouTubeMetadataClient
import com.almus.downloader.youtube.android.OkHttpYouTubeHttp
import com.almus.downloader.youtube.android.SharedPrefsYouTubeKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple manual dependency container shared by the UI and the broadcast receiver. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val notifier = AndroidDownloadNotifier(appContext)
    val systemDownloader = AndroidSystemDownloader(appContext)
    val inspector = LinkInspector()
    val settings = SharedPrefsQueueSettings(appContext)
    val saveSettings = SharedPrefsSaveSettings(appContext)
    val storage = AndroidStorageInfo(appContext)
    val folderSettings = SharedPrefsFolderSettings(appContext)
    val folderLister = DocumentsFolderLister(appContext)
    val fileActions = AndroidFileActions(appContext, systemDownloader)
    val fileLibrary = FileLibrary(AndroidFileProbe(appContext, systemDownloader), folderLister)

    private val database: AlmusDatabase =
        Room.databaseBuilder(appContext, AlmusDatabase::class.java, "almus.db")
            .addMigrations(Migrations.V1_TO_V2)
            .build()

    val repository = DownloadRepository(
        dao = database.downloadDao(),
        system = systemDownloader,
        storage = storage,
        notifier = notifier,
        settings = settings,
        saveSettings = saveSettings,
    )

    /** The new download engine (Android 10+). Nothing starts until it is used; see [EngineHost.supported]. */
    val engineHost = EngineHost(appContext, settings, database)

    /** Link detection: recognises the platform, follows short links safely, then asks the server. Saves nothing. */
    val linkDetector = SmartLinkDetector()
    private val redirectResolver = SafeRedirectResolver()
    val linkIntake = LinkIntake(
        detector = linkDetector,
        resolve = { redirectResolver.resolve(it) },
        inspect = { inspector.inspect(it) },
        isDuplicate = { repository.isDuplicate(it) || engineHost.isDuplicate(it) },
    )

    /**
     * YouTube integration: public details from YouTube's own services, official viewing options, and an honest
     * explanation when downloading is not possible. No provider of authorized downloads exists, so none is passed.
     */
    val youtubeKeys = SharedPrefsYouTubeKeyStore(appContext)
    val youtube = YouTubeIntegration(
        metadata = YouTubeMetadataClient(http = OkHttpYouTubeHttp(), apiKey = { youtubeKeys.apiKey }),
    )

    /**
     * Facebook integration: public details from Facebook's official oEmbed service (no sign-in, no token), richer
     * details from the Graph API only when the person saved their own access token, official ways to open the
     * content in Facebook, and the official export for the person's own videos. Only the person's own videos can ever
     * be offered as a file (see FacebookDownloadPolicy). Nothing is scraped and nothing is signed in.
     */
    val facebookTokens = SharedPrefsFacebookTokenStore(appContext)
    val facebook = FacebookIntegration(
        metadata = FacebookMetadataClient(http = OkHttpFacebookHttp(), accessToken = { facebookTokens.accessToken }),
    )

    /**
     * TikTok integration: public details (caption, creator, thumbnail) from TikTok's own oEmbed service, which needs no
     * sign-in, key or token; official ways to open the video in TikTok; and an honest explanation when downloading is
     * not possible. TikTok publishes no download API, so no download provider is passed. Nothing is scraped.
     */
    val tiktok = TikTokIntegration(
        metadata = TikTokMetadataClient(http = OkHttpTikTokHttp()),
    )

    /**
     * Instagram, X, Vimeo, Dailymotion, Reddit and Pinterest: public details (title, creator, length, thumbnail where
     * the platform gives them) from each platform's own oEmbed service, which needs no sign-in, key or token; what kind
     * of media the address is; official ways to open it in the platform; and an honest explanation when downloading
     * is not possible. None of these platforms publishes a download service for apps, so no download provider is
     * passed. Nothing is scraped. Each platform is one spec in the `integration.specs` package.
     */
    /**
     * Authorized sources that publish several real versions of a file (qualities and formats) for download: the
     * Internet Archive and Wikimedia Commons. Their own public metadata services are the only place formats come
     * from; nothing is guessed. Addresses of these sources go through the media selection screen.
     */
    val mediaSources = MediaSources(
        listOf(
            InternetArchiveSource(OkHttpMediaHttp()),
            CommonsSource(OkHttpMediaHttp()),
        ),
    )

    /**
     * On-device audio converters. None ships: Android has no MP3 encoder and none could be added, so no conversion
     * choice is ever shown. An implementation added here makes the conversion choices appear for files whose
     * source allows processing.
     */
    val audioConverters: List<AudioConverter> = emptyList()

    val platforms = PlatformIntegration(
        metadata = OEmbedClient(http = OkHttpOEmbedHttp()),
    )
}

class AlmusApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.notifier.ensureChannel()
    }
}
