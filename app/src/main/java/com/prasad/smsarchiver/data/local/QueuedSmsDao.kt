package com.prasad.smsarchiver.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface QueuedSmsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QueuedSmsEntity): Long

    @Query("SELECT * FROM queued_sms ORDER BY createdAt ASC")
    suspend fun getAll(): List<QueuedSmsEntity>

    @Query("SELECT COUNT(*) FROM queued_sms")
    suspend fun count(): Int

    @Query("DELETE FROM queued_sms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Update
    suspend fun update(entity: QueuedSmsEntity)

    @Query("DELETE FROM queued_sms")
    suspend fun clear()
}