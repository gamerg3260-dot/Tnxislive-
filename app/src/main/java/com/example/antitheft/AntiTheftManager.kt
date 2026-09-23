package com.example.antitheft

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.camera.MaxCameraManager
import com.example.data.local.MaxDatabase
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class AntiTheftManager private constructor(
    private val context: Context
) {
    private val tag = "AntiTheftManager"
    private val database = MaxDatabase.getInstance(context)
    private val antiTheftDao = database.antiTheftDao()
    private val cameraManager = MaxCameraManager(context)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponentName = ComponentName(context, MaxDeviceAdminReceiver::class.java)

    private var mediaPlayer: MediaPlayer? = null

    val settingsFlow: Flow<AntiTheftSettingsEntity?> = antiTheftDao.getSettingsFlow()
    val intruderLogsFlow: Flow<List<IntruderLogEntity>> = antiTheftDao.getAllIntruderLogsFlow()
    val latestIntruderLogFlow: Flow<IntruderLogEntity?> = antiTheftDao.getLatestIntruderLogFlow()

    companion object {
        @Volatile
        private var INSTANCE: AntiTheftManager? = null

        fun getInstance(context: Context): AntiTheftManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AntiTheftManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Checks if Device Admin permission is currently active.
     */
    fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponentName)
    }

    fun getAdminComponent(): ComponentName = adminComponentName

    suspend fun getSettings(): AntiTheftSettingsEntity {
        var current = antiTheftDao.getSettings()
        if (current == null) {
            val defaultSettings = AntiTheftSettingsEntity()
            antiTheftDao.saveSettings(defaultSettings)
            current = defaultSettings
        }
        return current
    }

    suspend fun updateSettings(settings: AntiTheftSettingsEntity) {
        antiTheftDao.saveSettings(settings)
    }

    suspend fun setTrustedContact(name: String, phone: String, email: String = "") {
        val current = getSettings()
        val updated = current.copy(
            trustedContactName = name.ifBlank { "Emergency Contact" },
            trustedContactNumber = phone.trim(),
            trustedContactEmail = email.trim()
        )
        antiTheftDao.saveSettings(updated)
    }

    suspend fun setAntiTheftEnabled(enabled: Boolean) {
        val current = getSettings()
        antiTheftDao.saveSettings(current.copy(isEnabled = enabled))
    }

    suspend fun setFailedAttemptsThreshold(threshold: Int) {
        val current = getSettings()
        antiTheftDao.saveSettings(current.copy(failedAttemptsThreshold = threshold.coerceIn(1, 5)))
    }

    /**
     * Handle failed lock screen password attempt.
     */
    suspend fun handlePasswordFailed() = withContext(Dispatchers.IO) {
        val settings = getSettings()
        if (!settings.isEnabled) {
            Log.d(tag, "Anti-theft disabled; ignoring password failure.")
            return@withContext
        }

        val newFailCount = settings.currentFailedAttempts + 1
        antiTheftDao.updateFailedAttempts(newFailCount)
        Log.w(tag, "Password failed count: $newFailCount / ${settings.failedAttemptsThreshold}")

        if (newFailCount >= settings.failedAttemptsThreshold) {
            Log.w(tag, "⚠️ INTRUDER DETECTED! Triggering silent front camera & GPS alert...")
            triggerIntruderAlert(
                triggerType = "WRONG_PASSWORD_3_TIMES",
                details = "$newFailCount बार गलत पासवर्ड/पैटर्न दर्ज किया गया"
            )
            // Reset counter after alert
            antiTheftDao.updateFailedAttempts(0)
        }
    }

    /**
     * Handle successful unlock.
     */
    suspend fun handlePasswordSucceeded() = withContext(Dispatchers.IO) {
        antiTheftDao.updateFailedAttempts(0)
    }

    /**
     * Core silent intruder capture & SMS alert trigger.
     */
    suspend fun triggerIntruderAlert(
        triggerType: String,
        details: String
    ): IntruderLogEntity = withContext(Dispatchers.IO) {
        val settings = getSettings()
        val timestamp = System.currentTimeMillis()
        val timeString = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

        // 1. Silent Front Camera Capture
        var photoPath: String? = null
        if (settings.captureSelfieOnIntruder && cameraManager.hasCameraPermission()) {
            val captureResult = cameraManager.captureSilentPhoto(isFrontCamera = true)
            if (captureResult.isSuccess) {
                val bitmap = captureResult.getOrThrow()
                photoPath = saveIntruderBitmap(bitmap, timestamp)
                Log.i(tag, "Intruder photo saved to: $photoPath")
            }
        }

        // 2. Fetch High-Accuracy GPS Coordinates
        val location = fetchCurrentLocation()
        val lat = location?.latitude
        val lng = location?.longitude
        val addressText = if (lat != null && lng != null) {
            reverseGeocode(lat, lng)
        } else {
            "स्थान अनुपलब्ध (GPS off or locating)"
        }

        val mapsUrl = if (lat != null && lng != null) {
            "https://maps.google.com/?q=$lat,$lng"
        } else {
            "GPS unavailable"
        }

        // 3. Send Silent SMS Alert to Trusted Contact
        var isSmsSent = false
        val contactNumber = settings.trustedContactNumber
        if (settings.sendSmsOnIntruder && contactNumber.isNotBlank() && hasSmsPermission()) {
            val alertMessage = buildString {
                append("⚠️ MAX ANTI-THEFT ALERT!\n")
                append("फोन में गलत पासवर्ड दर्ज किया गया है।\n")
                append("समय: $timeString\n")
                if (lat != null && lng != null) {
                    append("स्थान: $mapsUrl\n")
                    if (addressText.isNotBlank()) append("पता: $addressText")
                }
            }
            isSmsSent = sendSilentSms(contactNumber, alertMessage)
        }

        // 4. Save to Room Database Log
        val logEntity = IntruderLogEntity(
            timestamp = timestamp,
            triggerType = triggerType,
            photoPath = photoPath,
            latitude = lat,
            longitude = lng,
            address = addressText,
            smsSentTo = if (isSmsSent) contactNumber else null,
            isSmsDelivered = isSmsSent,
            details = details
        )

        antiTheftDao.insertIntruderLog(logEntity)
        Log.i(tag, "Intruder alert logged successfully: ID=${logEntity.id}")
        logEntity
    }

    /**
     * Check if SIM card was swapped.
     */
    suspend fun checkAndHandleSimChange() = withContext(Dispatchers.IO) {
        val settings = getSettings()
        if (!settings.isEnabled || !settings.sendSmsOnSimChange) return@withContext

        val currentSimId = getCurrentSimIdentifier()
        if (currentSimId.isBlank()) {
            Log.d(tag, "No SIM card currently detected.")
            return@withContext
        }

        if (settings.lastKnownSimId.isBlank()) {
            // First time registration of SIM
            Log.i(tag, "First SIM registered in Anti-theft: $currentSimId")
            antiTheftDao.updateKnownSimId(currentSimId)
            return@withContext
        }

        if (settings.lastKnownSimId != currentSimId) {
            Log.w(tag, "⚠️ SIM SWAP DETECTED! Old: ${settings.lastKnownSimId}, New: $currentSimId")

            // Update to new SIM
            antiTheftDao.updateKnownSimId(currentSimId)

            val location = fetchCurrentLocation()
            val mapsUrl = if (location != null) "https://maps.google.com/?q=${location.latitude},${location.longitude}" else "GPS unavailable"
            val contactNumber = settings.trustedContactNumber

            if (contactNumber.isNotBlank() && hasSmsPermission()) {
                val simAlertMsg = "⚠️ MAX ANTI-THEFT: आपके फोन में नया SIM कार्ड लगाया गया है! नया SIM ID: $currentSimId. Location: $mapsUrl"
                sendSilentSms(contactNumber, simAlertMsg)
            }

            val logEntity = IntruderLogEntity(
                timestamp = System.currentTimeMillis(),
                triggerType = "SIM_CHANGED",
                photoPath = null,
                latitude = location?.latitude,
                longitude = location?.longitude,
                address = "SIM Swapped Detected",
                smsSentTo = contactNumber,
                isSmsDelivered = true,
                details = "नया सिम कार्ड लगाया गया (ID: $currentSimId)"
            )
            antiTheftDao.insertIntruderLog(logEntity)
        }
    }

    /**
     * Handle Remote SMS Security Commands received on phone.
     */
    suspend fun handleRemoteSmsCommand(sender: String, messageBody: String): String = withContext(Dispatchers.IO) {
        val clean = messageBody.trim().uppercase()
        val settings = getSettings()

        Log.i(tag, "Processing Remote Security Command: $clean from $sender")

        when {
            clean.contains("MAX LOCATE") || clean.contains("MAX TRACK") -> {
                val location = fetchCurrentLocation()
                val lat = location?.latitude
                val lng = location?.longitude
                val mapsUrl = if (lat != null && lng != null) "https://maps.google.com/?q=$lat,$lng" else "GPS Signal Search failed"

                // Also take silent selfie
                var photoPath: String? = null
                if (cameraManager.hasCameraPermission()) {
                    val cap = cameraManager.captureSilentPhoto(isFrontCamera = true)
                    if (cap.isSuccess) {
                        photoPath = saveIntruderBitmap(cap.getOrThrow(), System.currentTimeMillis())
                    }
                }

                val replyMsg = "📍 MAX LOCATE: फोन की वर्तमान लोकेशन: $mapsUrl (समय: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())})"
                if (hasSmsPermission()) {
                    sendSilentSms(sender, replyMsg)
                }

                antiTheftDao.insertIntruderLog(
                    IntruderLogEntity(
                        timestamp = System.currentTimeMillis(),
                        triggerType = "REMOTE_SMS_COMMAND",
                        photoPath = photoPath,
                        latitude = lat,
                        longitude = lng,
                        address = "Remote SMS Locate Query",
                        smsSentTo = sender,
                        isSmsDelivered = true,
                        details = "रिमोट एसएमएस द्वारा लोकेशन मांगी गई ($sender)"
                    )
                )
                "लोकेशन एसएमएस भेज दिया गया है"
            }

            clean.contains("MAX LOCK") -> {
                if (isDeviceAdminActive()) {
                    devicePolicyManager.lockNow()
                    Log.i(tag, "Device locked remotely via MAX LOCK SMS.")
                }
                val replyMsg = "🔒 MAX: फोन को तुरंत रिमोटली लॉक कर दिया गया है।"
                if (hasSmsPermission()) {
                    sendSilentSms(sender, replyMsg)
                }
                "फोन को रिमोटली लॉक किया गया"
            }

            clean.contains("MAX SIREN") || clean.contains("MAX ALARM") -> {
                playEmergencySiren()
                val replyMsg = "🚨 MAX: फोन में तेज़ अलार्म/साइरन बजाया जा रहा है।"
                if (hasSmsPermission()) {
                    sendSilentSms(sender, replyMsg)
                }
                "इमरजेंसी साइरन शुरू हुआ"
            }

            clean.contains("MAX STOP") -> {
                stopEmergencySiren()
                val replyMsg = "🛑 MAX: अलार्म/साइरन रोक दिया गया है।"
                if (hasSmsPermission()) {
                    sendSilentSms(sender, replyMsg)
                }
                "साइरन रोका गया"
            }

            else -> "अज्ञात रिमोट कमांड"
        }
    }

    /**
     * Plays loud emergency siren at max volume.
     */
    fun playEmergencySiren() {
        try {
            stopEmergencySiren()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.i(tag, "Emergency siren playing at volume $maxVol")
        } catch (e: Exception) {
            Log.e(tag, "Error playing emergency siren", e)
        }
    }

    fun stopEmergencySiren() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (ignored: Exception) {}
    }

    /**
     * Silent SMS Sender using SmsManager.
     */
    fun sendSilentSms(phoneNumber: String, message: String): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            Log.i(tag, "Silent security SMS successfully dispatched to $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to send silent SMS to $phoneNumber", e)
            false
        }
    }

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun fetchCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val cancelTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelTokenSource.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    continuation.resume(loc)
                } else {
                    // Fallback to last known location
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        continuation.resume(lastLoc)
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
                }
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                "${addr.locality ?: addr.subAdminArea ?: ""}, ${addr.adminArea ?: ""}"
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun saveIntruderBitmap(bitmap: Bitmap, timestamp: Long): String? {
        return try {
            val intrudersDir = File(context.filesDir, "intruders")
            if (!intrudersDir.exists()) intrudersDir.mkdirs()
            val file = File(intrudersDir, "INTRUDER_$timestamp.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(tag, "Failed to save intruder bitmap", e)
            null
        }
    }

    private fun getCurrentSimIdentifier(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simOperator = telephonyManager?.simOperator ?: ""
            val simCountry = telephonyManager?.simCountryIso ?: ""
            val simOperatorName = telephonyManager?.simOperatorName ?: ""

            if (simOperator.isNotBlank() || simCountry.isNotBlank()) {
                "$simOperatorName-$simOperator-$simCountry"
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
