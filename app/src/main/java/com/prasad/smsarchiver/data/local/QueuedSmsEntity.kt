package com.prasad.smsarchiver.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queued_sms")
data class QueuedSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val type: Int,
    val read: Boolean,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)