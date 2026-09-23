package com.example.router

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.accessibility.MaxAccessibilityService
import com.example.system.DesiredState
import com.example.system.SystemToggleController
import com.example.system.ToggleResult

class LocalCommandRouter(private val context: Context) {

    private val tag = "LocalCommandRouter"
    val toggleController = SystemToggleController(context)

    suspend fun tryRouteLocally(rawCommand: String): LocalExecutionResult {
        val query = rawCommand.trim().lowercase()

        // 0. Identity & Creator Recognition (Ganesh Sahani)
        if (isIdentityOrCreatorCommand(query)) {
            return handleIdentityAndCreator(query)
        }

        // 1. System Toggles & Hardware (WiFi, Bluetooth, Mobile Data, Airplane, Torch, Volume, Brightness, DND, Hotspot, GPS)
        val toggleResult = toggleController.tryHandleToggleVoiceCommand(query)
        if (toggleResult != null) {
            return LocalExecutionResult.Handled(
                success = toggleResult.success,
                actionType = "TOGGLE_${toggleResult.toggleName.uppercase().replace(" ", "_").replace("/", "_")}",
                messageHindi = "${toggleResult.logMessageHindi} [विधि: ${toggleResult.methodUsed}]",
                voiceResponseHindi = toggleResult.voiceResponseHindi
            )
        }

        // 2. Navigation & Global Keys (Home, Back, Recent Apps)
        if (isSystemNavigationCommand(query)) {
            return handleSystemNavigation(query)
        }

        // 3. GENERIC APP LAUNCHER (Dynamic PackageManager + Fuzzy Matching for ANY installed app)
        val appLaunchResult = handleGenericAppLaunch(query)
        if (appLaunchResult is LocalExecutionResult.Handled) {
            return appLaunchResult
        }

        // 4. Camera Direct Launch
        if (isCameraCommand(query)) {
            return handleCameraLaunch()
        }

        // 5. Phone Dialer Direct Launch
        if (isPhoneDialerCommand(query)) {
            return handleDialerLaunch()
        }

        // Complex command requiring screen perception, visual reasoning or conversation -> Gemini API
        return LocalExecutionResult.NotHandled
    }

    private fun isSystemNavigationCommand(query: String): Boolean {
        return query == "home" || query == "होम" || query.contains("home screen") || query.contains("होम स्क्रीन") ||
                query.contains("go home") || query.contains("होम पर जाओ") ||
                query == "back" || query == "बैक" || query.contains("go back") || query.contains("पीछे जाओ") || query.contains("बैक करो") ||
                query.contains("recent apps") || query.contains("रीसेंट ऐप्स") || query.contains("हाल के ऐप्स")
    }

    private fun handleSystemNavigation(query: String): LocalExecutionResult {
        val service = MaxAccessibilityService.instance
        return if (service != null) {
            when {
                query.contains("back") || query.contains("बैक") || query.contains("पीछे") -> {
                    val performed = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    LocalExecutionResult.Handled(
                        success = performed,
                        actionType = "NAV_BACK",
                        messageHindi = "बैक बटन निष्पादित हुआ (Accessibility Service)।",
                        voiceResponseHindi = "बैक किया गया।"
                    )
                }
                query.contains("recent") || query.contains("रीसेंट") -> {
                    val performed = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                    LocalExecutionResult.Handled(
                        success = performed,
                        actionType = "NAV_RECENTS",
                        messageHindi = "रीसेंट ऐप्स स्क्रीन खोली गई (Accessibility Service)।",
                        voiceResponseHindi = "रीसेंट ऐप्स खोले गए।"
                    )
                }
                else -> {
                    val performed = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    LocalExecutionResult.Handled(
                        success = performed,
                        actionType = "NAV_HOME",
                        messageHindi = "होम स्क्रीन पर जाया गया (Accessibility Service)।",
                        voiceResponseHindi = "होम स्क्रीन पर जा रहे हैं।"
                    )
                }
            }
        } else {
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
                LocalExecutionResult.Handled(
                    success = true,
                    actionType = "NAV_HOME",
                    messageHindi = "होम स्क्रीन इंटेंट से खोली गई।",
                    voiceResponseHindi = "होम स्क्रीन पर जा रहे हैं।"
                )
            } catch (e: Exception) {
                LocalExecutionResult.Handled(
                    success = false,
                    actionType = "NAV_HOME",
                    messageHindi = "होम स्क्रीन खोलने में असमर्थ",
                    voiceResponseHindi = "होम स्क्रीन नहीं खुल सकी।"
                )
            }
        }
    }

    /**
     * GENERIC APP LAUNCH LOGIC:
     * Discovers all installed apps dynamically from PackageManager and matches the spoken name
     * using exact, substring, prefix, word boundary, and Levenshtein fuzzy matching.
     */
    private fun handleGenericAppLaunch(query: String): LocalExecutionResult {
        // App launch trigger words in Hindi / English / Hinglish
        val openPrefixes = listOf("open ", "kholo ", "launch ", "start ", "chalao ")
        val openSuffixes = listOf(
            "kholo", "खोलो", "open", "launch", "लॉन्च", "chalao", "चलाओ",
            "start", "शुरू", "karo", "करो", "app", "ऐप"
        )

        val hasOpenVerb = openPrefixes.any { query.startsWith(it) } ||
                openSuffixes.any { query.contains(it) }

        if (!hasOpenVerb) return LocalExecutionResult.NotHandled

        // Do not intercept if it's an in-app control command like "search CarryMinati", "tap on this", "scroll down"
        val hasComplexInAppTask = query.contains("search") || query.contains("सर्च") ||
                query.contains("tap") || query.contains("टैप") || query.contains("scroll") ||
                query.contains("video play") || query.contains("गाना चलाओ") || query.contains("skip") ||
                query.contains("message bhejo") || query.contains("call lagao")

        if (hasComplexInAppTask) {
            val words = query.split(" ").filter { it.isNotBlank() }
            if (words.size > 3) {
                return LocalExecutionResult.NotHandled
            }
        }

        // Extract app name candidate by removing verb words
        var candidate = query
        val wordsToRemove = listOf(
            "open", "kholo", "खोलो", "launch", "start", "chalao", "चलाओ",
            "app", "ऐप", "ko", "को", "par", "पर", "karo", "करो", "please", "जरा"
        )
        for (w in wordsToRemove) {
            candidate = candidate.replace(Regex("\\b$w\\b", RegexOption.IGNORE_CASE), "")
        }
        val cleanAppName = candidate.trim()

        if (cleanAppName.isBlank()) return LocalExecutionResult.NotHandled

        // Query all installed apps from device
        val installedApps = GenericAppLauncher.getInstalledLaunchableApps(context)
        if (installedApps.isEmpty()) {
            Log.w(tag, "No installed apps retrieved via PackageManager")
            return LocalExecutionResult.NotHandled
        }

        // Perform fuzzy match
        val matchedApp = GenericAppLauncher.findBestAppMatch(cleanAppName, installedApps)

        return if (matchedApp != null) {
            val launched = GenericAppLauncher.launchApp(context, matchedApp)
            if (launched) {
                LocalExecutionResult.Handled(
                    success = true,
                    actionType = "OPEN_APP_LOCAL",
                    messageHindi = "${matchedApp.appName} ऐप खोला गया (PackageManager से तुरंत बिना API कॉल)।",
                    voiceResponseHindi = "${matchedApp.appName} खोला जा रहा है।"
                )
            } else {
                LocalExecutionResult.Handled(
                    success = false,
                    actionType = "OPEN_APP_LOCAL",
                    messageHindi = "${matchedApp.appName} ऐप लॉन्च नहीं हो सका।",
                    voiceResponseHindi = "माफ़ कीजिये, ${matchedApp.appName} लॉन्च नहीं हो सका।"
                )
            }
        } else {
            // User specifically asked to open an app (e.g. "XYZ app kholo") but it wasn't found on device
            if (query.contains("app") || query.contains("ऐप") || query.startsWith("open ") || query.endsWith("kholo") || query.endsWith("खोलो")) {
                val suggestions = installedApps.take(3).joinToString(", ") { it.appName }
                LocalExecutionResult.Handled(
                    success = false,
                    actionType = "OPEN_APP_NOT_FOUND",
                    messageHindi = "ऐप '$cleanAppName' डिवाइस पर इन्स्टॉल नहीं मिला।",
                    voiceResponseHindi = "यह ऐप मुझे फोन में नहीं मिला। आपके पास $suggestions जैसे ऐप्स उपलब्ध हैं।"
                )
            } else {
                LocalExecutionResult.NotHandled
            }
        }
    }

    private fun isIdentityOrCreatorCommand(query: String): Boolean {
        return query.contains("kisne banaya") || query.contains("किसने बनाया") ||
                query.contains("creator kaun") || query.contains("क्रिएटर कौन") ||
                query.contains("owner kaun") || query.contains("ओनर कौन") ||
                query.contains("malik kaun") || query.contains("मालिक कौन") ||
                query.contains("who made you") || query.contains("who created you") ||
                query.contains("who is your creator") || query.contains("who is your owner") ||
                query.contains("tum kaun ho") || query.contains("तुम कौन हो") ||
                query.contains("who are you") || query.contains("apna naam batao") ||
                query.contains("tumhara naam") || query.contains("तुम्हारा नाम") ||
                query.contains("ganesh sahani") || query.contains("गणेश साहनी")
    }

    private fun handleIdentityAndCreator(query: String): LocalExecutionResult {
        val isCreatorQuestion = query.contains("banaya") || query.contains("बनाया") ||
                query.contains("creator") || query.contains("क्रिएटर") ||
                query.contains("owner") || query.contains("ओनर") ||
                query.contains("malik") || query.contains("मालिक") ||
                query.contains("made") || query.contains("created") ||
                query.contains("ganesh") || query.contains("गणेश")

        val responseText = if (isCreatorQuestion) {
            "मुझे गणेश साहनी (Ganesh Sahani) ने बनाया है, मैं उनका पर्सनल AI असिस्टेंट मैक्स (Max) हूँ।"
        } else {
            "नमस्ते! मैं मैक्स (Max) हूँ, गणेश साहनी द्वारा बनाया गया आपका पर्सनल AI और autonomous असिस्टेंट।"
        }

        return LocalExecutionResult.Handled(
            success = true,
            actionType = "IDENTITY_CREATOR_INFO",
            messageHindi = responseText,
            voiceResponseHindi = responseText
        )
    }

    private fun isCameraCommand(query: String): Boolean {
        return (query.contains("camera") || query.contains("कैमरा")) &&
                (query.contains("kholo") || query.contains("खोलो") || query.contains("open") ||
                        query.contains("start") || query.contains("चालू") || query.contains("photo") || query.contains("फोटो"))
    }

    private fun handleCameraLaunch(): LocalExecutionResult {
        return try {
            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(cameraIntent)
            LocalExecutionResult.Handled(
                success = true,
                actionType = "OPEN_CAMERA",
                messageHindi = "कैमरा तुरंत लॉन्च किया गया (लोकल मीडिया इंटेंट)।",
                voiceResponseHindi = "कैमरा खोला जा रहा है।"
            )
        } catch (e: Exception) {
            LocalExecutionResult.Handled(
                success = false,
                actionType = "OPEN_CAMERA",
                messageHindi = "कैमरा खोलने में असमर्थ: ${e.localizedMessage}",
                voiceResponseHindi = "कैमरा नहीं खुल सका।"
            )
        }
    }

    private fun isPhoneDialerCommand(query: String): Boolean {
        return (query.contains("dialer") || query.contains("डायलर") || query.contains("phone app") || query.contains("फोन ऐप")) &&
                (query.contains("kholo") || query.contains("खोलो") || query.contains("open"))
    }

    private fun handleDialerLaunch(): LocalExecutionResult {
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            LocalExecutionResult.Handled(
                success = true,
                actionType = "OPEN_DIALER",
                messageHindi = "फोन डायलर तुरंत खोला गया।",
                voiceResponseHindi = "फोन डायलर खोला जा रहा है।"
            )
        } catch (e: Exception) {
            LocalExecutionResult.Handled(
                success = false,
                actionType = "OPEN_DIALER",
                messageHindi = "डायलर खोलने में विफल: ${e.localizedMessage}",
                voiceResponseHindi = "डायलर नहीं खुल सका।"
            )
        }
    }
}
