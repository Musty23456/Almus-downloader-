package com.almus.downloader

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.almus.downloader.data.AlmusDatabase
import com.almus.downloader.download.AndroidDownloadNotifier
import com.almus.downloader.download.AndroidStorageInfo
import com.almus.downloader.download.AndroidSystemDownloader
import com.almus.downloader.download.DownloadRepository
import com.almus.downloader.download.LinkInspector
import com.almus.downloader.download.SharedPrefsQueueSettings
import com.almus.downloader.download.SharedPrefsSaveSettings
import com.almus.downloader.files.AndroidFileActions
import com.almus.downloader.files.AndroidFileProbe
import com.almus.downloader.files.DocumentsFolderLister
import com.almus.downloader.files.FileLibrary
import com.almus.downloader.files.SharedPrefsFolderSettings
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
        Room.databaseBuilder(appContext, AlmusDatabase::class.java, "almus.db").build()

    val repository = DownloadRepository(
        dao = database.downloadDao(),
        system = systemDownloader,
        storage = storage,
        notifier = notifier,
        settings = settings,
        saveSettings = saveSettings,
    )
}

class AlmusApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.notifier.ensureChannel()
    }
}
