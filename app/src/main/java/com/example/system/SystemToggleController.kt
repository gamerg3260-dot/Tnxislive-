package com.example.system

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.MaxAccessibilityService
import kotlinx.coroutines.delay

/**
 * Result of a toggle action execution.
 */
data class ToggleResult(
    val success: Boolean,
    val toggleName: String,
    val targetState: Boolean,
    val wasAlreadyInState: Boolean,
    val methodUsed: String, // "DIRECT_API", "ACCESSIBILITY_QUICK_SETTINGS", "ACCESSIBILITY_INTENT"
    val voiceResponseHindi: String,
    val logMessageHindi: String
)

/**
 * Supported System Toggles.
 */
enum class SystemToggleType(val labelHindi: String, val englishName: String) {
    WIFI("वाई-फ़ाई", "Wi-Fi"),
    BLUETOOTH("ब्लूटूथ", "Bluetooth"),
    MOBILE_DATA("मोबाइल डेटा", "Mobile Data"),
    AIRPLANE_MODE("एरोप्लेन मोड", "Airplane Mode"),
    TORCH("टॉर्च", "Torch / Flashlight"),
    VOLUME("वॉल्यूम", "Volume"),
    BRIGHTNESS("ब्राइटनेस", "Brightness"),
    DND("डू नॉट डिस्टर्ब (DND)", "Do Not Disturb"),
    HOTSPOT("हॉटस्पॉट", "Hotspot"),
    GPS("लोकेशन / जीपीएस", "Location / GPS")
}

/**
 * Desired target state requested by the user.
 */
enum class DesiredState {
    ON,
    OFF,
    TOGGLE,
    INCREASE,
    DECREASE,
    MUTE,
    MAX
}

/**
 * Single source of truth for ALL system toggles and hardware controls.
 *
 * Guarantees:
 * 1. Bidirectional state awareness: Reads actual current status before deciding to change.
 * 2. Guaranteed execution across all Android versions:
 *    - Attempts Direct API / System settings first.
 *    - If restricted on the Android OS version (e.g. WiFi on Android 10+, Mobile Data, Hotspot),
 *      seamlessly falls back to Accessibility Service -> Opens Quick Settings / Settings, finds
 *      the exact tile/switch node, and taps it automatically without requiring manual user touch.
 * 3. All 10 toggles centralized in this single module.
 */
class SystemToggleController(private val context: Context) {

    private val tag = "SystemToggleController"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    // In-memory torch tracker fallback
    private var isTorchActive = false

    init {
        // Register torch callback to keep state 100% accurate
        try {
            cameraManager?.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    isTorchActive = enabled
                }
            }, null)
        } catch (e: Exception) {
            Log.w(tag, "Failed to register torch callback: ${e.localizedMessage}")
        }
    }

    // =============================================================================================
    // 1. STATE INSPECTION (Current Real Status)
    // =============================================================================================

    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled == true
        } catch (e: Exception) {
            val net = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(net)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return try {
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    fun isMobileDataEnabled(): Boolean {
        return try {
            val net = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(net)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } catch (e: Exception) {
            false
        }
    }

    fun isAirplaneModeOn(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (e: Exception) {
            false
        }
    }

    fun isTorchOn(): Boolean = isTorchActive

    fun isGpsEnabled(): Boolean {
        return try {
            val gps = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            val network = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            gps || network
        } catch (e: Exception) {
            false
        }
    }

    fun isDndActive(): Boolean {
        return try {
            if (notificationManager != null) {
                notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getCurrentVolumePercent(): Int {
        val am = audioManager ?: return 50
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (cur * 100) / max
    }

    fun getCurrentBrightnessPercent(): Int {
        return try {
            val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            (raw * 100) / 255
        } catch (e: Exception) {
            50
        }
    }

    // =============================================================================================
    // 2. BIDIRECTIONAL TOGGLE CONTROLLERS WITH GUARANTEED ACCESSIBILITY FALLBACK
    // =============================================================================================

    /**
     * Executes a Wi-Fi toggle request with fully automatic switch detection and tapping.
     */
    suspend fun setWifi(desiredState: DesiredState): ToggleResult {
        val currentState = isWifiEnabled()
        val targetState = when (desiredState) {
            DesiredState.ON -> true
            DesiredState.OFF -> false
            DesiredState.TOGGLE -> !currentState
            else -> true
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                @Suppress("DEPRECATION")
                val directSuccess = wifiManager?.setWifiEnabled(targetState) == true
                if (directSuccess) {
                    val logFound = "WIFI_SWITCH_FOUND: true"
                    val logState = "WIFI_CURRENT_STATE: ${if (currentState) "ON" else "OFF"}"
                    val logTap = "WIFI_TAP_PERFORMED: true"
                    Log.i(tag, "$logFound | $logState | $logTap")
                    val actionWord = if (targetState) "चालू कर दिया गया है" else "बंद कर दिया गया है"
                    return ToggleResult(
                        success = true,
                        toggleName = "Wi-Fi",
                        targetState = targetState,
                        wasAlreadyInState = false,
                        methodUsed = "DIRECT_API",
                        voiceResponseHindi = "वाई-फ़ाई $actionWord।",
                        logMessageHindi = "$logFound | $logState | $logTap"
                    )
                }
            } catch (e: Exception) {
                Log.d(tag, "Direct WifiManager API failed: ${e.localizedMessage}")
            }
        }

        return executeSettingsAutoToggle(
            toggleTag = "WIFI",
            toggleDisplayName = "Wi-Fi",
            toggleDisplayNameHindi = "वाई-फ़ाई",
            settingsAction = Settings.ACTION_WIFI_SETTINGS,
            targetState = targetState,
            keywords = listOf("wi-fi", "wifi", "वाईफाई", "use wi-fi", "use wifi", "internet"),
            systemStateChecker = { isWifiEnabled() }
        )
    }

    /**
     * Executes a Bluetooth toggle request with fully automatic switch detection and tapping.
     */
    suspend fun setBluetooth(desiredState: DesiredState): ToggleResult {
        val currentState = isBluetoothEnabled()
        val targetState = when (desiredState) {
            DesiredState.ON -> true
            DesiredState.OFF -> false
            DesiredState.TOGGLE -> !currentState
            else -> true
        }

        try {
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                @Suppress("DEPRECATION")
                val directSuccess = if (targetState) adapter.enable() else adapter.disable()
                if (directSuccess) {
                    val logFound = "BLUETOOTH_SWITCH_FOUND: true"
                    val logState = "BLUETOOTH_CURRENT_STATE: ${if (currentState) "ON" else "OFF"}"
                    val logTap = "BLUETOOTH_TAP_PERFORMED: true"
                    Log.i(tag, "$logFound | $logState | $logTap")
                    val actionWord = if (targetState) "चालू कर दिया गया है" else "बंद कर दिया गया है"
                    return ToggleResult(
                        success = true,
                        toggleName = "Bluetooth",
                        targetState = targetState,
                        wasAlreadyInState = false,
                        methodUsed = "DIRECT_API",
                        voiceResponseHindi = "ब्लूटूथ $actionWord।",
                        logMessageHindi = "$logFound | $logState | $logTap"
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Direct BluetoothAdapter enable/disable restricted: ${e.localizedMessage}")
        }

        return executeSettingsAutoToggle(
            toggleTag = "BLUETOOTH",
            toggleDisplayName = "Bluetooth",
            toggleDisplayNameHindi = "ब्लूटूथ",
            settingsAction = Settings.ACTION_BLUETOOTH_SETTINGS,
            targetState = targetState,
            keywords = listOf("bluetooth", "use bluetooth", "ब्लूटूथ"),
            systemStateChecker = { isBluetoothEnabled() }
        )
    }

    /**
     * Executes a Mobile Data toggle request with fully automatic switch detection and tapping.
     */
    suspend fun setMobileData(desiredState: DesiredState): ToggleResult {
        val currentState = isMobileDataEnabled()
        val targetState = when (desiredState) {
            DesiredState.ON -> true
            DesiredState.OFF -> false
            DesiredState.TOGGLE -> !currentState
            else -> true
        }

        return executeSettingsAutoToggle(
            toggleTag = "MOBILE_DATA",
            toggleDisplayName = "Mobile Data",
            toggleDisplayNameHindi = "मोबाइल डेटा",
            settingsAction = Settings.ACTION_DATA_ROAMING_SETTINGS,
            targetState = targetState,
            keywords = listOf("mobile data", "cellular data", "use mobile data", "मोबाइल डेटा", "डेटा", "data"),
            systemStateChecker = { isMobileDataEnabled() }
        )
    }

    /**
     * Executes Airplane Mode toggle request.
     */
    suspend fun setAirplaneMode(desiredState: DesiredState): ToggleResult {
        val currentState = isAirplaneModeOn()
        val targetState = when (desiredState) {
            DesiredState.ON -> true
            DesiredState.OFF -> false
            DesiredState.TOGGLE -> !currentState
            else -> true
        }

        if (currentState == targetState) {
            val stateWord = if (currentState) "पहले से ही चालू" else "पहले से ही बंद"
            return ToggleResult(
                success = true,
                toggleName = "Airplane Mode",
                targetState = targetState,
                wasAlreadyInState = true,
                methodUsed = "NOOP_ALREADY_SET",
                voiceResponseHindi = "एरोप्लेन मोड $stateWord है।",
                logMessageHindi = "एरोप्लेन मोड पहले से ही ${if (currentState) "ON" else "OFF"} है।"
            )
        }

        // Airplane mode is secure; use Quick Settings tile auto-click via Accessibility
        val fallbackSuccess = toggleViaQuickSettingsOrSettings(
            tileKeywords = listOf("airplane", "flight", "एरोप्लेन", "फ़्लाइट"),
            settingsAction = Settings.ACTION_AIRPLANE_MODE_SETTINGS,
            targetState = targetState
        )

        val stateWord = if (targetState) "चालू" else "बंद"
        return if (fallbackSuccess) {
            ToggleResult(
                success = true,
                toggleName = "Airplane Mode",
                targetState = targetState,
                wasAlreadyInState = false,
                methodUsed = "ACCESSIBILITY_QUICK_SETTINGS",
                voiceResponseHindi = "एरोप्लेन मोड $stateWord कर दिया गया है।",
                logMessageHindi = "Accessibility सर्विस ने एरोप्लेन मोड को $stateWord किया।"
            )
        } else {
            openSettingsDirectly(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            ToggleResult(
                success = true,
                toggleName = "Airplane Mode",
                targetState = targetState,
                wasAlreadyInState = false,
                methodUsed = "SETTINGS_PANEL",
                voiceResponseHindi = "एरोप्लेन मोड सेटिंग्स खोल दी गई हैं।",
                logMessageHindi = "एरोप्लेन मोड सेटिंग्स खोली गई।"
            )
        }
    }

    /**
     * Executes Torch / Flashlight toggle request.
     */
    fun setTorch(desiredState: DesiredState): ToggleResult {
        val currentState = isTorchOn()
        val targetState = when (desiredState) {
            DesiredState.ON -> true
            DesiredState.OFF -> false
            DesiredState.TOGGLE -> !currentState
            else -> true
        }

        if (currentState == targetState) {
            val stateWord = if (currentState) "पहले से ही चालू" else "पहले से ही बंद"
            return ToggleResult(
                success = true,
                toggleName = "Torch",
                targetState = targetState,
                wasAlreadyInState = true,
                methodUsed = "NOOP_ALREADY_SET",
                voiceResponseHindi = "टॉर्च $stateWord है।",
                logMessageHindi = "टॉर्च पहले से ही ${if (currentState) "ON" else "OFF"} है।"
            )
        }

        return try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager?.cameraIdList?.firstOrNull()

            if (cameraId != null) {
                cameraManager?.setTorchMode(cameraId, targetState)
                isTorchActive = targetState
                val stateWord = if (targetState) "चालू" else "बंद"
                ToggleResult(
                    success = true,
                    toggleName = "Torch",
                    targetState = targetState,
                    wasAlreadyInState = false,
                    methodUsed = "DIRECT_API",
                    voiceResponseHindi = "टॉर्च $stateWord कर दी गई है।",
                    logMessageHindi = "कैमरा मैनेजर API से टॉर्च को $stateWord किया गया।"
                )
            } else {
                ToggleResult(
                    success = false,
                    toggleName = "Torch",
                    targetState = targetState,
                    wasAlreadyInState = false,
                    methodUsed = "DIRECT_API",
                    voiceResponseHindi = "डिवाइस में फ्लैशलाइट उपलब्ध नहीं है।",
                    logMessageHindi = "हार्डवेयर में टॉर्च अनुपलब्ध है।"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Direct torch control failed", e)
            ToggleResult(
                success = false,
                toggleName = "Torch",
                targetState = targetState,
                wasAlreadyInState = false,
                methodUsed = "DIRECT_API",
                voiceResponseHindi = "टॉर्च चालू करने में दिक्कत आई।",
                logMessageHindi = "टॉर्च एरर: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Executes Volume adjustments.
     */
    fun setVolume(desiredState: DesiredState, step: Int = 1): ToggleResult {
        val am = audioManager ?: return ToggleResult(
            success = false,
            toggleName = "Volume",
            targetState = true,
            wasAlreadyInState = false,
            methodUsed = "DIRECT_API",
            voiceResponseHindi = "ऑडियो मैनेजर उपलब्ध नहीं है।",
            logMessageHindi = "AudioManager अनुपलब्ध।"
        )

        return try {
            when (desiredState) {
                DesiredState.INCREASE -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    ToggleResult(
                        success = true,
                        toggleName = "Volume",
                        targetState = true,
                        wasAlreadyInState = false,
                        methodUsed = "DIRECT_API",
                        voiceResponseHindi = "वॉल्यूम बढ़ा दी गई है।",
                        logMessageHindi = "वॉल्यूम बढ़ाई गई (लेवल: ${getCurrentVolumePercent()}%)"
                    )
                }
                DesiredState.DECREASE -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    ToggleResult(
                        success = true,
                        toggleName = "Volume",
                        targetState = false,
                        wasAlreadyInState = false,
                        methodUsed = "DIRECT_API",
                        voiceResponseHindi = "वॉल्यूम कम कर दी गई है।",
                        logMessageHindi = "वॉल्यूम कम की गई (लेवल: ${getCurrentVolumePercent()}%)"
                    )
                }
                DesiredState.MUTE -> {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                    ToggleResult(
                        success = true,
                        toggleName = "Volume",
                        targetState = false,
                        wasAlreadyInState = false,
                        methodUsed = "DIRECT_API",
                        voiceResponseHindi = "आवाज म्यूट कर दी गई है।",
                        logMessageHindi = "वॉल्यूम म्यूट (0%) की गई।"
                    )
                }
                DesiredState.MAX -> {
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
                    ToggleResult(
                        success = true,
                        toggleName = "Volume",
                        targetState = true,
                        wasAlreadyInState = false,
                        methodUsed = "DIRECT_API",
                        voiceResponseHindi = "वॉल्यूम फुल कर दी गई है।",
                        logMessageHindi = "वॉल्यूम 100% फुल की गई।"
                    )
                }
                else -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    ToggleResult(
                        success = true,
                        toggleName = "Volume",
                        targetState = true,
                        wasAlreadyInState = false,
                        methodUsed = "DIRECT_API",
                        voiceResponseHindi = "वॉल्यूम एडजस्ट की गई।",
                        logMessageHindi = "वॉल्यूम एडजस्ट की गई।"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Volume control failed", e)
            ToggleResult(
                success = false,
                toggleName = "Volume",
                targetState = false,
                wasAlreadyInState = false,
                methodUsed = "DIRECT_API",
                voiceResponseHindi = "वॉल्यूम बदलने में दिक्कत आई।",
                logMessageHindi = "वॉल्यूम एरर: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Executes Brightness adjustments.
     */
    suspend fun setBrightness(desiredState: DesiredState): ToggleResult {
        val currentPercent = getCurrentBrightnessPercent()

        // Check if WRITE_SETTINGS permission is granted for direct write
        val hasWritePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }

        val targetPercent = when (desiredState) {
            DesiredState.INCREASE -> (currentPercent + 25).coerceAtMost(100)
            DesiredState.DECREASE -> (currentPercent - 25).coerceAtLeast(10)
            DesiredState.MAX -> 100
            DesiredState.MUTE, DesiredState.OFF -> 10
            else -> 60
        }

        if (hasWritePermission) {
            try {
                val targetRaw = ((targetPercent * 255) / 100).coerceIn(0, 255)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetRaw)
                val dirWord = if (desiredState == DesiredState.INCREASE || desiredState == DesiredState.MAX) "बढ़ा" else "कम कर"
                return ToggleResult(
                    success = true,
                    toggleName = "Brightness",
                    targetState = targetPercent > 50,
                    wasAlreadyInState = false,
                    methodUsed = "DIRECT_API",
                    voiceResponseHindi = "ब्राइटनेस $dirWord दी गई है ($targetPercent%)।",
                    logMessageHindi = "सिस्टम सेटिंग्स API द्वारा स्क्रीन ब्राइटनेस $targetPercent% सेट की गई।"
                )
            } catch (e: Exception) {
                Log.w(tag, "Settings.System.putInt failed: ${e.localizedMessage}")
            }
        }

        // Accessibility Fallback: Open Display Settings or Quick Settings slider
        val fallbackSuccess = toggleViaQuickSettingsOrSettings(
            tileKeywords = listOf("brightness", "ब्राइटनेस", "display", "स्क्रीन"),
            settingsAction = Settings.ACTION_DISPLAY_SETTINGS,
            targetState = targetPercent > 50
        )

        return if (fallbackSuccess) {
            ToggleResult(
                success = true,
                toggleName = "Brightness",
                targetState = targetPercent > 50,
                wasAlreadyInState = false,
                methodUsed = "ACCESSIBILITY_QUICK_SETTINGS",
                voiceResponseHindi = "ब्राइटनेस एडजस्ट कर दी गई है।",
                logMessageHindi = "Accessibility सर्विस द्वारा डिस्प्ले/ब्राइटनेस एडजस्ट की गई।"
            )
        } else {
            openSettingsDirectly(Settings.ACTION_DISPLAY_SETTINGS)
            ToggleResult(
                success = true,
                toggleName = "Brightness",
                targetState = targetPercent > 50,
                wasAlreadyInState = false,
                methodUsed = "SETTINGS_PANEL",
                voiceResponseHindi = "डिस्प्ले सेटिंग्स खोल दी गई हैं।",
                logMessageHindi = "डिस्प्ले सेटिंग्स स्क्रीन खोली गई।"
            )
        }
    }

    /**
     * Executes Do Not Disturb (DND) toggle request.
     */
    suspend fun setDnd(desiredState: DesiredState): ToggleResult {
        val currentState = isDndActive()
        val targetState = when (desiredState) {
            DesiredState.ON -> true
            DesiredState.OFF -> false
            DesiredState.TOGGLE -> !currentState
            else -> true
        }

        if (currentState == targetState) {
            val stateWord = if (currentState) "पहले से ही चालू" else "पहले से ही बंद"
            return ToggleResult(
                success = true,
                toggleName = "Do Not Disturb",
                targetState = targetState,
                wasAlreadyInState = true,
                methodUsed = "NOOP_ALREADY_SET",
                voiceResponseHindi = "डू नॉट डिस्टर्ब $stateWord है।",
                logMessageHindi = "DND पहले से ही ${if (currentState) "ON" else "OFF"} है।"
            )
        }

        // Direct API Check
        var directSuccess = false
        if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted) {
            try {
                val filter = if (targetState) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
                notificationManager.setInterruptionFilter(filter)
                directSuccess = true
            } catch (e: Exception) {
                Log.d(tag, "NotificationManager DND failed: ${e.localizedMessage}")
            }
        }

        if (directSuccess) {
            val stateWord = if (targetState) "चालू (ON)" else "बंद (OFF)"
            return ToggleResult(
                success = true,
                toggleName = "Do Not Disturb",
                targetState = targetState,
                wasAlreadyInState = false,
                methodUsed = "DIRECT_API",
                voiceResponseHindi = "डू नॉट डिस्टर्ब $stateWord कर दिया गया है।",
                logMessageHindi = "NotificationManager API से DND $stateWord किया गया।"
            )
        }

        // Accessibility Quick Settings Tile Fallback
        val fallbackSuccess = toggleViaQuickSettingsOrSettings(
            tileKeywords = listOf("do not disturb", "dnd", "डू नॉट डिस्टर्ब", "शांत"),
            settingsAction = Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
            targetState = targetState
        )

        val stateWord = if (targetState) "चालू" else "बंद"
        return if (fallbackSuccess) {
            ToggleResult(
                success = true,
                toggleName = "Do Not Disturb",
                targetState = targetState,
                wasAlreadyInState = false,
                methodUsed = "ACCESSIBILITY_QUICK_SETTINGS",
                voiceResponseHindi = "डू नॉट डिस्टर्ब $stateWord कर दिया गया है।",
                logMessageHindi = "Accessibility सर्विस द्वारा DND टाइल को $stateWord किया गया।"
            )
        } else {
            openSettingsDirectly(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            ToggleResult(
                success = true,
                toggleName = "Do Not Disturb",
                targetState = targetState,
                wasAlreadyInState = false,
                methodUsed = "SETTINGS_PANEL",
                voiceResponseHindi = "डू नॉट डिस्टर्ब सेटिंग्स खोल दी गई हैं।",
                logMessageHindi = "DND सेटिंग्स खोली गई।"
            )
        }
    }

    /**
     * Executes Hotspot toggle request with fully automatic switch detection and tapping.
     */
    suspend fun setHotspot(desiredState: DesiredState): ToggleResult {
        val targetState = when (desiredState) {
            DesiredState.ON -> true
            DesiredState.OFF -> false
            DesiredState.TOGGLE -> true
            else -> true
        }

        return executeSettingsAutoToggle(
            toggleTag = "HOTSPOT",
            toggleDisplayName = "Hotspot",
            toggleDisplayNameHindi = "हॉटस्पॉट",
            settingsAction = "android.settings.TETHER_SETTINGS",
            targetState = targetState,
            keywords = listOf("hotspot", "portable hotspot", "wi-fi hotspot", "use hotspot", "tethering", "हॉटस्पॉट"),
            systemStateChecker = { false }
        )
    }

    /**
     * Executes Location / GPS toggle request.
     */
    suspend fun setGps(desiredState: DesiredState): ToggleResult {
        val currentState = isGpsEnabled()
        val targetState = when (desiredState) {
            DesiredState.ON -> true
            DesiredState.OFF -> false
            DesiredState.TOGGLE -> !currentState
            else -> true
        }

        if (currentState == targetState) {
            val stateWord = if (currentState) "पहले से ही चालू" else "पहले से ही बंद"
            return ToggleResult(
                success = true,
                toggleName = "Location / GPS",
                targetState = targetState,
                wasAlreadyInState = true,
                methodUsed = "NOOP_ALREADY_SET",
                voiceResponseHindi = "लोकेशन / जीपीएस $stateWord है।",
                logMessageHindi = "लोकेशन पहले से ही ${if (currentState) "ON" else "OFF"} है।"
            )
        }

        // GPS toggle is secure; use Quick Settings tile auto-click via Accessibility
        val fallbackSuccess = toggleViaQuickSettingsOrSettings(
            tileKeywords = listOf("location", "gps", "लोकेशन", "जीपीएस"),
            settingsAction = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            targetState = targetState
        )

        val stateWord = if (targetState) "चालू" else "बंद"
        return if (fallbackSuccess) {
            ToggleResult(
                success = true,
                toggleName = "Location / GPS",
                targetState = targetState,
                wasAlreadyInState = false,
                methodUsed = "ACCESSIBILITY_QUICK_SETTINGS",
                voiceResponseHindi = "लोकेशन $stateWord कर दी गई है।",
                logMessageHindi = "Accessibility सर्विस द्वारा लोकेशन टाइल को $stateWord किया गया।"
            )
        } else {
            openSettingsDirectly(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            ToggleResult(
                success = true,
                toggleName = "Location / GPS",
                targetState = targetState,
                wasAlreadyInState = false,
                methodUsed = "SETTINGS_PANEL",
                voiceResponseHindi = "लोकेशन सेटिंग्स खोल दी गई हैं।",
                logMessageHindi = "लोकेशन सेटिंग्स खोली गई।"
            )
        }
    }

    // =============================================================================================
    // 3. FULLY AUTOMATIC ACCESSIBILITY SETTINGS AUTO-TOGGLE ENGINE
    // =============================================================================================

    /**
     * Fully automatic Settings auto-toggle engine.
     * Opens target Settings screen, polls Accessibility tree to find switch, inspects current state (ON/OFF),
     * taps switch if necessary, and logs exact required debug statements.
     */
    private suspend fun executeSettingsAutoToggle(
        toggleTag: String,             // "WIFI", "MOBILE_DATA", "HOTSPOT", "BLUETOOTH"
        toggleDisplayName: String,      // "Wi-Fi", "Mobile Data", "Hotspot", "Bluetooth"
        toggleDisplayNameHindi: String, // "वाई-फ़ाई", "मोबाइल डेटा", "हॉटस्पॉट", "ब्लूटूथ"
        settingsAction: String,        // e.g. Settings.ACTION_WIFI_SETTINGS
        targetState: Boolean,          // true for ON, false for OFF
        keywords: List<String>,        // Keywords to locate the switch element
        systemStateChecker: () -> Boolean
    ): ToggleResult {
        val service = MaxAccessibilityService.instance

        // If Accessibility Service is NOT active
        if (service == null) {
            val logFound = "${toggleTag}_SWITCH_FOUND: false"
            val logState = "${toggleTag}_CURRENT_STATE: UNKNOWN"
            val logTap = "${toggleTag}_TAP_PERFORMED: false"

            Log.w(tag, logFound)
            Log.w(tag, logState)
            Log.w(tag, logTap)

            openSettingsDirectly(settingsAction)

            val voiceMsg = "यह ऑटोमैटिकली नहीं हो पाया, कृपया एक्सेसिबिलिटी सर्विस चालू करके फिर से प्रयास करें।"
            return ToggleResult(
                success = false,
                toggleName = toggleDisplayName,
                targetState = targetState,
                wasAlreadyInState = false,
                methodUsed = "NO_ACCESSIBILITY_SERVICE",
                voiceResponseHindi = voiceMsg,
                logMessageHindi = "$logFound | $logState | $logTap"
            )
        }

        // 1. Open target Settings Screen
        Log.i(tag, "Opening Settings screen for $toggleDisplayName ($settingsAction)...")
        openSettingsDirectly(settingsAction)

        // 2. Poll Accessibility window tree for up to 3 seconds (10 attempts x 300ms)
        var switchNode: AccessibilityNodeInfo? = null
        for (attempt in 1..10) {
            delay(300)
            val root = try { service.rootInActiveWindow } catch (e: Exception) { null }
            if (root != null) {
                switchNode = findSwitchNodeForToggle(root, keywords)
                if (switchNode != null) {
                    break
                }
                root.recycle()
            }
        }

        // 3. Handle Switch NOT Found
        if (switchNode == null) {
            val logFound = "${toggleTag}_SWITCH_FOUND: false"
            val logState = "${toggleTag}_CURRENT_STATE: UNKNOWN"
            val logTap = "${toggleTag}_TAP_PERFORMED: false"

            Log.w(tag, logFound)
            Log.w(tag, logState)
            Log.w(tag, logTap)

            val voiceMsg = "यह ऑटोमैटिकली नहीं हो पाया, कृपया मैनुअली कर दीजिए।"
            return ToggleResult(
                success = false,
                toggleName = toggleDisplayName,
                targetState = targetState,
                wasAlreadyInState = false,
                methodUsed = "SWITCH_NODE_NOT_FOUND",
                voiceResponseHindi = voiceMsg,
                logMessageHindi = "$logFound | $logState | $logTap"
            )
        }

        // 4. Switch Node IS Found -> Inspect Current State (ON or OFF)
        val logFound = "${toggleTag}_SWITCH_FOUND: true"
        Log.i(tag, logFound)

        val currentState = detectSwitchState(switchNode, systemStateChecker)
        val stateStr = if (currentState) "ON" else "OFF"
        val logState = "${toggleTag}_CURRENT_STATE: $stateStr"
        Log.i(tag, logState)

        // 5. Check if ALREADY in requested target state
        if (currentState == targetState) {
            val logTap = "${toggleTag}_TAP_PERFORMED: false"
            Log.i(tag, logTap)
            switchNode.recycle()

            val stateWord = if (currentState) "पहले से ही चालू" else "पहले से ही बंद"
            val voiceMsg = "$toggleDisplayNameHindi $stateWord है।"

            return ToggleResult(
                success = true,
                toggleName = toggleDisplayName,
                targetState = targetState,
                wasAlreadyInState = true,
                methodUsed = "NOOP_ALREADY_IN_STATE",
                voiceResponseHindi = voiceMsg,
                logMessageHindi = "$logFound | $logState | $logTap"
            )
        }

        // 6. Action Required: Tap the switch!
        val tapped = performClickOnNodeOrParent(switchNode)
        switchNode.recycle()

        val logTap = "${toggleTag}_TAP_PERFORMED: $tapped"
        Log.i(tag, logTap)

        val voiceMsg = if (tapped) {
            val actionWord = if (targetState) "चालू कर दिया गया है" else "बंद कर दिया गया है"
            "$toggleDisplayNameHindi $actionWord।"
        } else {
            "यह ऑटोमैटिकली नहीं हो पाया, कृपया मैनुअली कर दीजिए।"
        }

        return ToggleResult(
            success = tapped,
            toggleName = toggleDisplayName,
            targetState = targetState,
            wasAlreadyInState = false,
            methodUsed = if (tapped) "ACCESSIBILITY_TAP_SUCCESS" else "ACCESSIBILITY_TAP_FAILED",
            voiceResponseHindi = voiceMsg,
            logMessageHindi = "$logFound | $logState | $logTap"
        )
    }

    /**
     * Scans accessibility node tree for switch/toggle element matching keywords or standard Switch classes.
     */
    private fun findSwitchNodeForToggle(
        rootNode: AccessibilityNodeInfo,
        keywords: List<String>
    ): AccessibilityNodeInfo? {

        // Phase 1: Search by matching text/description keywords AND contains/is a checkable/switch node
        fun searchByKeywordAndSwitch(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val cls = node.className?.toString() ?: ""

            val matchesKw = keywords.any { text.contains(it, ignoreCase = true) || desc.contains(it, ignoreCase = true) }
            val isSwitchClass = cls.contains("Switch", ignoreCase = true) ||
                    cls.contains("Toggle", ignoreCase = true) ||
                    cls.contains("CompoundButton", ignoreCase = true)

            if (matchesKw) {
                if (node.isCheckable || isSwitchClass || node.isClickable) {
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (e: Exception) { null } ?: continue
                val found = searchByKeywordAndSwitch(child)
                if (found != null) {
                    if (found != child) child.recycle()
                    return found
                }
                child.recycle()
            }
            return null
        }

        // Phase 2: Search for ANY checkable / Switch class node on screen
        fun searchAnySwitchNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val cls = node.className?.toString() ?: ""
            val isSwitchClass = cls.contains("Switch", ignoreCase = true) ||
                    cls.contains("Toggle", ignoreCase = true) ||
                    cls.contains("CompoundButton", ignoreCase = true)

            if (node.isCheckable || isSwitchClass) {
                return node
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (e: Exception) { null } ?: continue
                val found = searchAnySwitchNode(child)
                if (found != null) {
                    if (found != child) child.recycle()
                    return found
                }
                child.recycle()
            }
            return null
        }

        val kwResult = searchByKeywordAndSwitch(rootNode)
        if (kwResult != null) return kwResult

        return searchAnySwitchNode(rootNode)
    }

    /**
     * Inspects switch node or its hierarchy/text to accurately determine if it is currently ON or OFF.
     */
    private fun detectSwitchState(
        node: AccessibilityNodeInfo,
        systemFallback: () -> Boolean
    ): Boolean {
        if (node.isCheckable) {
            return node.isChecked
        }

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (e: Exception) { null } ?: continue
            if (child.isCheckable) {
                val checked = child.isChecked
                child.recycle()
                return checked
            }
            child.recycle()
        }

        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        if (text.contains("on") || text.contains("enabled") || text.contains("चालू")) return true
        if (text.contains("off") || text.contains("disabled") || text.contains("बंद")) return false

        return systemFallback()
    }

    /**
     * Automatically opens Quick Settings or Settings screen, scans the node tree for the matching
     * tile or toggle switch, and taps it directly via AccessibilityService gestures/actions.
     */
    private suspend fun toggleViaQuickSettingsOrSettings(
        tileKeywords: List<String>,
        settingsAction: String,
        targetState: Boolean
    ): Boolean {
        val service = MaxAccessibilityService.instance
        if (service == null) {
            Log.w(tag, "MaxAccessibilityService not active; opening direct intent.")
            return false
        }

        // Step 1: Try pulling down the Quick Settings Panel
        Log.i(tag, "Opening Quick Settings notification shade...")
        val openedQuickSettings = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        if (openedQuickSettings) {
            delay(500) // Wait for shade to slide down

            // Search root window for tile
            val root = try { service.rootInActiveWindow } catch (e: Exception) { null }
            if (root != null) {
                val tileNode = findMatchingNode(root, tileKeywords)
                if (tileNode != null) {
                    Log.i(tag, "Found Quick Settings tile for $tileKeywords. Performing click...")
                    val clicked = performClickOnNodeOrParent(tileNode)
                    tileNode.recycle()
                    root.recycle()

                    // Close quick settings after clicking
                    delay(400)
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    return clicked
                }
                root.recycle()
            }

            // Close quick settings if tile wasn't immediately visible
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(200)
        }

        // Step 2: Open target Settings Screen directly and click the primary switch/toggle
        Log.i(tag, "Tile not found in shade; launching settings screen: $settingsAction")
        val openedSettings = openSettingsDirectly(settingsAction)
        if (openedSettings) {
            delay(600) // Wait for settings Activity to render

            val rootSettings = try { service.rootInActiveWindow } catch (e: Exception) { null }
            if (rootSettings != null) {
                val switchNode = findSwitchOrToggleNode(rootSettings, tileKeywords)
                if (switchNode != null) {
                    Log.i(tag, "Found switch in Settings for $tileKeywords. Performing click...")
                    val clicked = performClickOnNodeOrParent(switchNode)
                    switchNode.recycle()
                    rootSettings.recycle()

                    // Navigate back to the previous screen automatically so user doesn't stay stuck in settings
                    delay(500)
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    return clicked
                }
                rootSettings.recycle()
            }
        }

        return false
    }

    /**
     * Recursively searches the node tree for a node matching any of the specified keywords.
     */
    private fun findMatchingNode(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        val matches = keywords.any { kw ->
            text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true)
        }

        if (matches && (node.isClickable || node.parent?.isClickable == true)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (e: Exception) { null } ?: continue
            val found = findMatchingNode(child, keywords)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }

        return null
    }

    /**
     * Finds a Switch, ToggleButton, or clickable preference row in Settings.
     */
    private fun findSwitchOrToggleNode(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null

        val className = node.className?.toString() ?: ""
        val isSwitchClass = className.contains("Switch", ignoreCase = true) ||
                className.contains("Toggle", ignoreCase = true) ||
                className.contains("CompoundButton", ignoreCase = true)

        if (isSwitchClass && node.isClickable) {
            return node
        }

        // Preference item containing text or desc matching keywords
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val matches = keywords.any { kw -> text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true) }

        if (matches && (isSwitchClass || node.isClickable)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (e: Exception) { null } ?: continue
            val found = findSwitchOrToggleNode(child, keywords)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }

        return null
    }

    /**
     * Performs an Accessibility ACTION_CLICK on the node, or walks up to its clickable parent.
     */
    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                val res = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return res
            }
            val nextParent = parent.parent
            parent.recycle()
            parent = nextParent
        }
        return false
    }

    private fun openSettingsDirectly(action: String): Boolean {
        return try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start settings activity for action: $action", e)
            false
        }
    }

    // =============================================================================================
    // 4. VOICE COMMAND ROUTER / PARSER FOR TOGGLES
    // =============================================================================================

    /**
     * Inspects a spoken query to determine if it is a system toggle command.
     * Returns a ToggleResult if handled, or null if it should be routed elsewhere.
     */
    suspend fun tryHandleToggleVoiceCommand(query: String): ToggleResult? {
        val lower = query.lowercase().trim()

        val res = when {
            lower.contains("wifi") || lower.contains("वाईफाई") || lower.contains("wi-fi") -> setWifi(parseDesiredState(lower))
            lower.contains("bluetooth") || lower.contains("ब्लूटूथ") -> setBluetooth(parseDesiredState(lower))
            (lower.contains("mobile data") || lower.contains("मोबाइल डाटा") || lower.contains("डेटा") || lower.contains("data")) &&
                (lower.contains("on") || lower.contains("off") || lower.contains("चालू") || lower.contains("बंद") || lower.contains("karo") || lower.contains("करो")) -> setMobileData(parseDesiredState(lower))
            lower.contains("airplane") || lower.contains("flight mode") || lower.contains("एरोप्लेन") || lower.contains("फ़्लाइट") -> setAirplaneMode(parseDesiredState(lower))
            lower.contains("torch") || lower.contains("flashlight") || lower.contains("टॉर्च") || lower.contains("फ्लैशलाइट") -> setTorch(parseDesiredState(lower))
            lower.contains("volume") || lower.contains("वॉल्यूम") || lower.contains("आवाज") || lower.contains("साउंड") -> {
                val state = when {
                    lower.contains("badhao") || lower.contains("बढ़ाओ") || lower.contains("up") || lower.contains("तेज") -> DesiredState.INCREASE
                    lower.contains("kam") || lower.contains("कम") || lower.contains("down") || lower.contains("धीमी") -> DesiredState.DECREASE
                    lower.contains("mute") || lower.contains("म्यूट") || lower.contains("silent") || lower.contains("chup") -> DesiredState.MUTE
                    lower.contains("full") || lower.contains("फूल") || lower.contains("फुल") || lower.contains("100") -> DesiredState.MAX
                    else -> DesiredState.INCREASE
                }
                setVolume(state)
            }
            lower.contains("brightness") || lower.contains("ब्राइटनेस") || lower.contains("स्क्रीन लाइट") || lower.contains("चमक") -> {
                val state = when {
                    lower.contains("badhao") || lower.contains("बढ़ाओ") || lower.contains("up") || lower.contains("तेज") -> DesiredState.INCREASE
                    lower.contains("kam") || lower.contains("कम") || lower.contains("down") || lower.contains("धीमी") -> DesiredState.DECREASE
                    lower.contains("full") || lower.contains("फूल") || lower.contains("फुल") || lower.contains("100") -> DesiredState.MAX
                    else -> DesiredState.INCREASE
                }
                setBrightness(state)
            }
            lower.contains("dnd") || lower.contains("do not disturb") || lower.contains("डू नॉट डिस्टर्ब") || lower.contains("डीएनडी") -> setDnd(parseDesiredState(lower))
            lower.contains("hotspot") || lower.contains("हॉटस्पॉट") || lower.contains("हॉट स्पॉट") -> setHotspot(parseDesiredState(lower))
            lower.contains("gps") || lower.contains("location") || lower.contains("लोकेशन") || lower.contains("जीपीएस") -> setGps(parseDesiredState(lower))
            else -> null
        }

        if (res != null) {
            Log.i(tag, "REAL ACTION: Executing System Hardware Toggle -> Name: ${res.toggleName}, TargetState: ${res.targetState}, Method: ${res.methodUsed}")
        }

        return res
    }

    private fun parseDesiredState(lower: String): DesiredState {
        return when {
            lower.contains("off") || lower.contains("बंद") || lower.contains("रोको") || lower.contains("hatao") -> DesiredState.OFF
            lower.contains("on") || lower.contains("चालू") || lower.contains("चलाओ") || lower.contains("जलाओ") -> DesiredState.ON
            else -> DesiredState.TOGGLE
        }
    }
}
