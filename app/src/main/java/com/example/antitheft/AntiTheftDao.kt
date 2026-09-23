package com.example.antitheft

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AntiTheftDao {
    @Query("SELECT * FROM antitheft_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AntiTheftSettingsEntity?>

    @Query("SELECT * FROM antitheft_settings WHERE id = 1")
    suspend fun getSettings(): AntiTheftSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AntiTheftSettingsEntity)

    @Query("UPDATE antitheft_settings SET currentFailedAttempts = :count WHERE id = 1")
    suspend fun updateFailedAttempts(count: Int)

    @Query("UPDATE antitheft_settings SET lastKnownSimId = :simId WHERE id = 1")
    suspend fun updateKnownSimId(simId: String)

    @Query("UPDATE antitheft_settings SET trustedContactNumber = :number, trustedContactName = :name WHERE id = 1")
    suspend fun updateTrustedContact(number: String, name: String)

    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    fun getAllIntruderLogsFlow(): Flow<List<IntruderLogEntity>>

    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestIntruderLog(): IntruderLogEntity?

    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC LIMIT 1")
    fun getLatestIntruderLogFlow(): Flow<IntruderLogEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntruderLog(log: IntruderLogEntity): Long

    @Query("DELETE FROM intruder_logs WHERE id = :id")
    suspend fun deleteIntruderLog(id: Long)

    @Query("DELETE FROM intruder_logs")
    suspend fun clearAllIntruderLogs()
}
