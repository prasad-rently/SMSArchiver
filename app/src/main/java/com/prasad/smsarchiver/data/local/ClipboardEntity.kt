package com.prasad.smsarchiver.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for queued clipboard items (failed uploads)
 */
@Entity(tableName = "queued_clipboard")
data class QueuedClipboardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clipboardId: String,
    val content: String,
    val timestamp: Long,
    val source: String?,
    val contentHash: String
)
