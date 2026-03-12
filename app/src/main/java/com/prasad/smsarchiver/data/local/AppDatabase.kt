package com.prasad.smsarchiver.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [QueuedSmsEntity::class, QueuedClipboardEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queuedSmsDao(): QueuedSmsDao
    abstract fun queuedClipboardDao(): QueuedClipboardDao
}