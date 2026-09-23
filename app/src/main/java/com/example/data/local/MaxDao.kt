package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MaxDao {
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<CommandHistoryEntity>>

    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistoryList(limit: Int = 10): List<CommandHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: CommandHistoryEntity): Long

    @Query("DELETE FROM command_history WHERE id NOT IN (SELECT id FROM command_history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun pruneHistory(limit: Int = 50)

    @Query("DELETE FROM command_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM user_memory ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memory ORDER BY updatedAt DESC")
    suspend fun getAllMemoriesList(): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memory WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getMemoriesByCategory(category: String): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memory WHERE `key` = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): UserMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: UserMemoryEntity)

    @Query("DELETE FROM user_memory WHERE `key` = :key")
    suspend fun deleteMemory(key: String)

    @Query("DELETE FROM user_memory WHERE category = 'activity_context' AND updatedAt < :olderThanMillis")
    suspend fun pruneOldActivityContext(olderThanMillis: Long)

    @Query("DELETE FROM user_memory WHERE category != 'preference' AND category != 'identity' AND `key` NOT IN (SELECT `key` FROM user_memory WHERE category != 'preference' AND category != 'identity' ORDER BY updatedAt DESC LIMIT :keepLimit)")
    suspend fun pruneTransientMemories(keepLimit: Int = 15)
}
