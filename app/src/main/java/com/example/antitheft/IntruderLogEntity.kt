package com.example.antitheft

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intruder_logs")
data class IntruderLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val triggerType: String, // "WRONG_PASSWORD_3_TIMES", "SIM_CHANGED", "REMOTE_SMS_COMMAND", "MANUAL_TEST"
    val photoPath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val smsSentTo: String? = null,
    val isSmsDelivered: Boolean = false,
    val details: String = ""
)

@Entity(tableName = "antitheft_settings")
data class AntiTheftSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val isEnabled: Boolean = true,
    val trustedContactName: String = "My Emergency Contact",
    val trustedContactNumber: String = "",
    val trustedContactEmail: String = "",
    val failedAttemptsThreshold: Int = 3,
    val currentFailedAttempts: Int = 0,
    val lastKnownSimId: String = "",
    val captureSelfieOnIntruder: Boolean = true,
    val sendSmsOnIntruder: Boolean = true,
    val sendSmsOnSimChange: Boolean = true,
    val sirenOnRemoteAlarm: Boolean = true,
    val playSirenOnIntruder: Boolean = true
)
