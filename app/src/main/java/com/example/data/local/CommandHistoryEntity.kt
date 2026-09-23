package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userPrompt: String,
    val detectedApp: String,
    val actionType: String,
    val actionDetails: String,
    val responseHindi: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
