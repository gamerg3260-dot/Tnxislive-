package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an offline, persistent reminder or alarm.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val triggerTimeMillis: Long,
    val formattedTime: String,
    val isTriggered: Boolean = false,
    val isCancelled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val type: String = "REMINDER" // "REMINDER" or "ALARM"
) {
    val isPending: Boolean
        get() = !isTriggered && !isCancelled && triggerTimeMillis > System.currentTimeMillis()
}
