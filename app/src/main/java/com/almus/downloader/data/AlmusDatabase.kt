package com.almus.downloader.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class AlmusDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
