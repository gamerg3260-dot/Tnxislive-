package com.example.antitheft

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.Exception

/**
 * Dedicated Emergency / Theft Alarm Manager.
 * Operates completely separate from standard calendar/reminder alarms.
 * - Forces maximum volume across alarm and music streams (overriding silent/vibrate).
 * - Plays loud looping dual-frequency oscillating security siren (res/raw/theft_siren.wav).
 * - Triggers visual flashing / torch strobe.
 * - Can be silenced via voice ("alarm band karo") or direct UI button.
 */
class TheftAlarmManager private constructor(
    private val context: Context
) {
    private val tag = "TheftAlarmManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var mediaPlayer: MediaPlayer? = null
    private var torchStrobeJob: Job? = null

    private var originalAlarmVolume: Int = -1
    private var originalMusicVolume: Int = -1

    private val _isAlarmActive = MutableStateFlow(false)
    val isAlarmActive: StateFlow<Boolean> = _isAlarmActive.asStateFlow()

    private val _alarmReason = MutableStateFlow("")
    val alarmReason: StateFlow<String> = _alarmReason.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: TheftAlarmManager? = null

        fun getInstance(context: Context): TheftAlarmManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TheftAlarmManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Triggers the full emergency theft alarm immediately.
     */
    fun startTheftAlarm(reason: String = "इमरजेंसी चोरी अलार्म") {
        if (_isAlarmActive.value) {
            Log.i(tag, "Theft alarm is already active.")
            return
        }

        Log.w(tag, "🚨 STARTING EMERGENCY THEFT ALARM! Reason: $reason")
        _alarmReason.value = reason
        _isAlarmActive.value = true

        // 1. Force Maximum Audio Volume Override
        forceMaxVolume()

        // 2. Play Siren Audio Loop
        playSirenAudio()

        // 3. Start Torch Strobe Blinking
        startTorchStrobe()
    }

    /**
     * Stops the emergency theft alarm and restores previous volume.
     */
    fun stopTheftAlarm() {
        if (!_isAlarmActive.value) {
            Log.i(tag, "Theft alarm is not active.")
            return
        }

        Log.i(tag, "✅ STOPPING EMERGENCY THEFT ALARM.")
        _isAlarmActive.value = false
        _alarmReason.value = ""

        // Stop Audio
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(tag, "Error releasing mediaPlayer", e)
        }

        // Stop Torch Strobe
        stopTorchStrobe()

        // Restore Audio Volume
        restoreOriginalVolume()
    }

    private fun forceMaxVolume() {
        try {
            audioManager?.let { am ->
                if (originalAlarmVolume == -1) {
                    originalAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                }
                if (originalMusicVolume == -1) {
                    originalMusicVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                }

                val maxAlarmVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val maxMusicVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
                Log.d(tag, "Max volume forced: Alarm=$maxAlarmVol, Music=$maxMusicVol")
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not set max stream volume", e)
        }
    }

    private fun restoreOriginalVolume() {
        try {
            audioManager?.let { am ->
                if (originalAlarmVolume != -1) {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
                    originalAlarmVolume = -1
                }
                if (originalMusicVolume != -1) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusicVolume, 0)
                    originalMusicVolume = -1
                }
                Log.d(tag, "Original audio volumes restored.")
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not restore volume", e)
        }
    }

    private fun playSirenAudio() {
        try {
            mediaPlayer?.release()
            
            // Try loading from res/raw/theft_siren
            mediaPlayer = MediaPlayer.create(context, R.raw.theft_siren)?.apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                isLooping = true
                start()
            }

            if (mediaPlayer == null) {
                Log.w(tag, "Could not create MediaPlayer from R.raw.theft_siren, using fallback ringtone/tone")
                fallbackPlaySystemAlarm()
            } else {
                Log.i(tag, "🔊 Playing custom loud theft siren from res/raw/theft_siren.wav in continuous loop.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start theft siren MediaPlayer", e)
            fallbackPlaySystemAlarm()
        }
    }

    private fun fallbackPlaySystemAlarm() {
        try {
            val alertUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(tag, "Fallback alarm failed", e)
        }
    }

    private fun startTorchStrobe() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || cameraManager == null) return

        torchStrobeJob?.cancel()
        torchStrobeJob = coroutineScope.launch(Dispatchers.IO) {
            var cameraId: String? = null
            try {
                cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to query camera flash", e)
            }

            if (cameraId == null) return@launch

            try {
                var state = true
                while (isActive && _isAlarmActive.value) {
                    try {
                        cameraManager.setTorchMode(cameraId, state)
                        state = !state
                    } catch (ignored: Exception) {}
                    delay(250) // Fast 4Hz strobe
                }
            } finally {
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun stopTorchStrobe() {
        torchStrobeJob?.cancel()
        torchStrobeJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cameraManager != null) {
            try {
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, false)
                }
            } catch (ignored: Exception) {}
        }
    }
}
