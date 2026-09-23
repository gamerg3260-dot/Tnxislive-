package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY triggerTimeMillis ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isCancelled = 0 AND isTriggered = 0 AND triggerTimeMillis > :now ORDER BY triggerTimeMillis ASC")
    fun getActiveReminders(now: Long = System.currentTimeMillis()): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isCancelled = 0 AND isTriggered = 0 AND triggerTimeMillis > :now ORDER BY triggerTimeMillis ASC")
    suspend fun getActiveRemindersList(now: Long = System.currentTimeMillis()): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE isCancelled = 0 AND isTriggered = 0 AND (LOWER(title) LIKE '%' || LOWER(:keyword) || '%') ORDER BY triggerTimeMillis ASC LIMIT 1")
    suspend fun findMatchingActiveReminder(keyword: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isTriggered = 1 WHERE id = :id")
    suspend fun markTriggered(id: Long)

    @Query("UPDATE reminders SET isCancelled = 1 WHERE id = :id")
    suspend fun cancelReminder(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    @Query("DELETE FROM reminders WHERE isCancelled = 1 OR isTriggered = 1")
    suspend fun clearCompletedReminders()
}
