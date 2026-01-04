package com.prasad.smsarchiver.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * DAO for queued clipboard items
 */
@Dao
interface QueuedClipboardDao {
    
    @Insert
    suspend fun insert(item: QueuedClipboardEntity)
    
    @Query("SELECT * FROM queued_clipboard ORDER BY timestamp DESC")
    suspend fun getAll(): List<QueuedClipboardEntity>
    
    @Query("DELETE FROM queued_clipboard WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("SELECT COUNT(*) FROM queued_clipboard")
    suspend fun count(): Int
    
    @Query("DELETE FROM queued_clipboard")
    suspend fun deleteAll()
}
