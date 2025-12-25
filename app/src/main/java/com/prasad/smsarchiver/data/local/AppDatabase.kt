package com.prasad.smsarchiver.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [QueuedSmsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queuedSmsDao(): QueuedSmsDao
}