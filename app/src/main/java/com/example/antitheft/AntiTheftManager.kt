package com.example.antitheft

import android.Manifest
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
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
    val theftAlarmManager = TheftAlarmManager.getInstance(context)

    val settingsFlow: Flow<AntiTheftSettingsEntity?> = antiTheftDao.getSettingsFlow()
    val intruderLogsFlow: Flow<List<IntruderLogEntity>> = antiTheftDao.getAllIntruderLogsFlow()
    val latestIntruderLogFlow: Flow<IntruderLogEntity?> = antiTheftDao.getLatestIntruderLogFlow()
    val isSirenActive: Flow<Boolean> = theftAlarmManager.isAlarmActive

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

    /**
     * Locks the phone immediately using DevicePolicyManager.lockNow().
     * Requires Device Admin permission.
     */
    fun lockDeviceNow(): Boolean {
        return try {
            if (isDeviceAdminActive()) {
                devicePolicyManager.lockNow()
                Log.i(tag, "✅ Phone screen locked immediately via lockNow().")
                true
            } else {
                Log.w(tag, "⚠️ Cannot lock phone: Device Admin permission is not enabled.")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error executing lockNow()", e)
            false
        }
    }

    /**
     * Checks if device has a secure screen lock (PIN, Pattern, Password) configured.
     * NOTE: onPasswordFailed() will ONLY trigger when a secure lock screen is present.
     */
    fun isDeviceSecure(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguardManager?.isDeviceSecure == true
        } else {
            @Suppress("DEPRECATION")
            keyguardManager?.isKeyguardSecure == true
        }
    }

    fun isScreenLockSet(): Boolean = isDeviceSecure()

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } catch (ignored: Exception) {}
        }
    }

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
        Log.i(tag, "Trusted emergency contact saved: ${updated.trustedContactName} (${updated.trustedContactNumber})")
        showToast("✅ इमरजेंसी कॉन्टैक्ट सेव हुआ: $phone")
    }

    suspend fun setAntiTheftEnabled(enabled: Boolean) {
        val current = getSettings()
        antiTheftDao.saveSettings(current.copy(isEnabled = enabled))
        Log.i(tag, "Anti-Theft Master Switch set to: $enabled")
        showToast(if (enabled) "🛡️ एंटी-थेफ्ट गार्ड चालू किया गया" else "⚠️ एंटी-थेफ्ट गार्ड बंद किया गया")
    }

    suspend fun setFailedAttemptsThreshold(threshold: Int) {
        val current = getSettings()
        val cleanVal = threshold.coerceIn(1, 5)
        antiTheftDao.saveSettings(current.copy(failedAttemptsThreshold = cleanVal))
        Log.i(tag, "Failed attempts threshold set to: $cleanVal")
        showToast("⚙️ अलर्ट थ्रेशोल्ड: $cleanVal प्रयास")
    }

    /**
     * Handle failed lock screen password attempt.
     */
    suspend fun handlePasswordFailed() = withContext(Dispatchers.IO) {
        val settings = getSettings()
        val threshold = settings.failedAttemptsThreshold
        Log.i(tag, "--------------------------------------------------------")
        Log.w(tag, "🚨 [ON_PASSWORD_FAILED] Wrong PIN/Password attempt detected on device!")
        Log.i(tag, "Settings status: isEnabled=${settings.isEnabled}, threshold=$threshold, currentFailCount=${settings.currentFailedAttempts}, trustedContact='${settings.trustedContactNumber}'")

        if (!settings.isEnabled) {
            Log.w(tag, "Anti-Theft is disabled in settings; ignoring failed password attempt.")
            return@withContext
        }

        val newFailCount = settings.currentFailedAttempts + 1
        antiTheftDao.updateFailedAttempts(newFailCount)
        Log.w(tag, "⚠️ [Attempt Counter] Failed PIN attempt #$newFailCount / $threshold")
        showToast("⚠️ Max: गलत PIN प्रयास ($newFailCount / $threshold) दर्ज हुआ!")

        if (newFailCount >= threshold) {
            Log.w(tag, "🚨 [INTRUDER DETECTED] Threshold reached ($newFailCount >= $threshold). Launching silent front-camera capture + GPS + SMS...")
            showToast("🚨 Max Anti-Theft: घुसपैठिया अलर्ट सक्रिय! फोटो व लोकेशन ली जा रही है...")

            triggerIntruderAlert(
                triggerType = "WRONG_PASSWORD_THRESHOLD",
                details = "$newFailCount बार गलत पासवर्ड/PIN दर्ज किया गया (Threshold: $threshold)"
            )
            // Reset counter after alert
            antiTheftDao.updateFailedAttempts(0)
        } else {
            val remaining = threshold - newFailCount
            Log.i(tag, "ℹ️ Alert will trigger after $remaining more failed attempt(s).")
        }
        Log.i(tag, "--------------------------------------------------------")
    }

    /**
     * Handle successful unlock.
     */
    suspend fun handlePasswordSucceeded() = withContext(Dispatchers.IO) {
        Log.i(tag, "✅ [PASSWORD_SUCCEEDED] Device unlocked with valid credentials. Resetting failed counter to 0.")
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

        Log.i(tag, "================ INTRUDER ALERT INITIATED ================")
        Log.i(tag, "Trigger Type: $triggerType | Time: $timeString | Details: $details")

        // 1. Silent Front Camera Capture
        var photoPath: String? = null
        if (settings.captureSelfieOnIntruder) {
            if (cameraManager.hasCameraPermission()) {
                Log.i(tag, "📸 [Step 1: Photo Capture] Attempting silent front camera selfie...")
                val captureResult = cameraManager.captureSilentPhoto(isFrontCamera = true)
                if (captureResult.isSuccess) {
                    val bitmap = captureResult.getOrThrow()
                    photoPath = saveIntruderBitmap(bitmap, timestamp)
                    Log.i(tag, "✅ [Step 1: Photo Capture] SUCCESS: Photo saved to $photoPath")
                    showToast("📸 घुसपैठिए की गुप्त फोटो ली गई!")
                } else {
                    val errorMsg = captureResult.exceptionOrNull()?.message ?: "Camera unavailable"
                    Log.e(tag, "❌ [Step 1: Photo Capture] FAILED: $errorMsg")
                    showToast("⚠️ गुप्त फोटो नहीं ली जा सकी: $errorMsg")
                }
            } else {
                Log.w(tag, "⚠️ [Step 1: Photo Capture] SKIPPED: CAMERA permission not granted")
                showToast("⚠️ कैमरा परमिशन न होने के कारण फोटो नहीं ली जा सकी")
            }
        } else {
            Log.i(tag, "ℹ️ [Step 1: Photo Capture] Selfie capture disabled in settings.")
        }

        // 2. Fetch High-Accuracy GPS Coordinates
        Log.i(tag, "📍 [Step 2: Location] Fetching high-accuracy GPS coordinates...")
        val location = fetchCurrentLocation()
        val lat = location?.latitude
        val lng = location?.longitude
        val addressText = if (lat != null && lng != null) {
            val addr = reverseGeocode(lat, lng)
            Log.i(tag, "✅ [Step 2: Location] SUCCESS: Lat=$lat, Lng=$lng, Address='$addr'")
            showToast("📍 लोकेशन मिली: ${if (addr.isNotBlank()) addr.take(25) else "$lat, $lng"}")
            addr
        } else {
            Log.w(tag, "⚠️ [Step 2: Location] FAILED: Location unavailable (GPS off or weak signal)")
            showToast("⚠️ GPS लोकेशन प्राप्त नहीं हो सकी")
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
        if (settings.sendSmsOnIntruder) {
            if (contactNumber.isNotBlank()) {
                if (hasSmsPermission()) {
                    Log.i(tag, "✉️ [Step 3: SMS Alert] Sending emergency alert SMS to: $contactNumber...")
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
                    if (isSmsSent) {
                        Log.i(tag, "✅ [Step 3: SMS Alert] SUCCESS: Alert SMS dispatched to $contactNumber")
                        showToast("🚨 इमरजेंसी SMS भेजा गया ($contactNumber)!")
                    } else {
                        Log.e(tag, "❌ [Step 3: SMS Alert] FAILED: Could not send SMS to $contactNumber")
                        showToast("❌ SMS भेजने में विफल (SIM कार्ड/बैलेंस चेक करें)")
                    }
                } else {
                    Log.w(tag, "⚠️ [Step 3: SMS Alert] SKIPPED: SEND_SMS permission missing")
                    showToast("⚠️ SMS परमिशन न होने के कारण SMS नहीं भेजा गया")
                }
            } else {
                Log.w(tag, "⚠️ [Step 3: SMS Alert] SKIPPED: No trusted contact number configured in settings.")
                showToast("ℹ️ इमरजेंसी SMS नहीं भेजा जा सका (कोई नंबर सेट नहीं है)")
            }
        }

        // 4. Emergency Theft Siren Trigger if enabled in settings
        if (settings.playSirenOnIntruder) {
            Log.i(tag, "🚨 [Step 4: Emergency Siren] Wrong PIN threshold reached: Sounding loud emergency theft siren!")
            theftAlarmManager.startTheftAlarm("गलत PIN डालने पर ऑटो-साइरन")
            showToast("🚨 चोरी अलार्म साइरन शुरू!")
        }

        // 5. Save to Room Database Log
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
        Log.i(tag, "💾 [Step 5: Database] Intruder log saved to Room DB (ID: ${logEntity.id})")
        Log.i(tag, "==========================================================")
        logEntity
    }

    /**
     * Start the loud emergency theft siren manually (via voice or UI).
     */
    fun startEmergencySiren(reason: String = "यूजर द्वारा चालू किया गया चोरी अलार्म") {
        theftAlarmManager.startTheftAlarm(reason)
    }

    fun playEmergencySiren(reason: String = "इमरजेंसी चोरी अलार्म") {
        startEmergencySiren(reason)
    }

    /**
     * Stop the loud emergency theft siren.
     */
    fun stopEmergencySiren() {
        theftAlarmManager.stopTheftAlarm()
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

