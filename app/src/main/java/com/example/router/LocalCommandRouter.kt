package com.example.router

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.accessibility.MaxAccessibilityService
import com.example.antitheft.MaxDeviceAdminReceiver
import com.example.antitheft.TheftAlarmManager
import com.example.system.DesiredState
import com.example.system.SystemToggleController
import com.example.system.ToggleResult

class LocalCommandRouter(private val context: Context) {

    private val tag = "LocalCommandRouter"
    val toggleController = SystemToggleController(context)

    suspend fun tryRouteLocally(rawCommand: String, preParsed: ParsedCommand? = null): LocalExecutionResult {
        val parsed = preParsed ?: CommandParser.parse(rawCommand)
        val query = parsed.cleanedQuery

        Log.d(tag, "[Router] Raw: '$rawCommand' ➔ Clean: '$query' | Intent: ${parsed.intent} | Target: '${parsed.targetEntity}'")

        // 0. Identity & Creator Recognition (Ganesh Sahani)
        if (parsed.intent == ParsedIntent.IDENTITY_QUERY || isIdentityOrCreatorCommand(query)) {
            return handleIdentityAndCreator(query)
        }

        // 0B. Phone Screen Lock via Device Admin ("phone lock karo", "Max lock kar do", "lock phone", "screen lock karo")
        if (parsed.intent == ParsedIntent.LOCK_PHONE || isPhoneLockCommand(query)) {
            return handlePhoneLock()
        }

        // 0C. Dedicated Emergency Theft Siren ("chori alarm bajao", "siren bajao", "alarm band karo", "stop siren")
        if (parsed.intent == ParsedIntent.THEFT_ALARM || isTheftAlarmStartCommand(query)) {
            return handleTheftAlarmStart()
        }
        if (isTheftAlarmStopCommand(query)) {
            return handleTheftAlarmStop()
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
        if (parsed.intent == ParsedIntent.SYSTEM_NAV || isSystemNavigationCommand(query)) {
            return handleSystemNavigation(query)
        }

        // 3. Local Scrolling (Scroll down / Scroll up / Neeche / Upar)
        if (parsed.intent == ParsedIntent.SCROLL || isScrollCommand(query)) {
            return handleScroll(query)
        }

        // 4. GENERIC APP LAUNCHER (Dynamic PackageManager + Fuzzy Matching for ANY installed app)
        val appLaunchResult = handleGenericAppLaunch(parsed)
        if (appLaunchResult is LocalExecutionResult.Handled) {
            return appLaunchResult
        }

        // 5. Camera Direct Launch
        if (parsed.intent == ParsedIntent.CAMERA_SELFIE || isCameraCommand(query)) {
            return handleCameraLaunch()
        }

        // 6. Phone Dialer Direct Launch
        if (isPhoneDialerCommand(query)) {
            return handleDialerLaunch()
        }

        // Complex command requiring screen perception, visual reasoning or conversation -> Gemini API
        return LocalExecutionResult.NotHandled
    }

    private fun isSystemNavigationCommand(query: String): Boolean {
        return query == "home" || query == "होम" || query.contains("home screen") || query.contains("होम स्क्रीन") ||
                query.contains("go home") || query.contains("होम पर जाओ") || query.contains("होम जाओ") || query.contains("होम करो") ||
                query == "back" || query == "बैक" || query.contains("go back") || query.contains("पीछे जाओ") ||
                query.contains("बैक करो") || query.contains("पीछे करो") || query.contains("वापस जाओ") || query.contains("वापस करो") ||
                query.contains("peeche jao") || query.contains("peeche karo") || query.contains("wapas jao") || query.contains("wapas aao") || query.contains("wapas karo") || query.contains("back button") ||
                query.contains("recent apps") || query.contains("रीसेंट ऐप्स") || query.contains("हाल के ऐप्स") ||
                query.contains("रिसेंट ऐप्स") || query.contains("recents")
    }

    private fun isScrollCommand(query: String): Boolean {
        return query == "scroll" || query == "स्क्रॉल" || query == "scroll karo" || query == "स्क्रॉल करो" ||
                query.contains("scroll down") || query.contains("स्क्रॉल डाउन") ||
                query.contains("scroll up") || query.contains("स्क्रॉल अप") ||
                query.contains("neeche scroll") || query.contains("नीचे स्क्रॉल") ||
                query.contains("upar scroll") || query.contains("ऊपर स्क्रॉल") ||
                query.contains("neeche jao") || query.contains("नीचे जाओ") ||
                query.contains("neeche karo") || query.contains("नीचे करो") ||
                query.contains("upar jao") || query.contains("ऊपर जाओ") ||
                query.contains("upar karo") || query.contains("ऊपर करो")
    }

    private suspend fun handleScroll(query: String): LocalExecutionResult {
        val isUp = query.contains("up") || query.contains("ऊपर") || query.contains("upar")
        val service = MaxAccessibilityService.instance
        return if (service != null) {
            val scrolled = service.performScroll(isDown = !isUp)
            LocalExecutionResult.Handled(
                success = scrolled,
                actionType = if (isUp) "SCROLL_UP" else "SCROLL_DOWN",
                messageHindi = if (isUp) "ऊपर स्क्रॉल किया गया।" else "नीचे स्क्रॉल किया गया।",
                voiceResponseHindi = if (isUp) "ऊपर स्क्रॉल किया गया।" else "नीचे स्क्रॉल किया गया।"
            )
        } else {
            LocalExecutionResult.Handled(
                success = true,
                actionType = if (isUp) "SCROLL_UP" else "SCROLL_DOWN",
                messageHindi = if (isUp) "ऊपर स्क्रॉल किया गया。" else "नीचे स्क्रॉल किया गया。",
                voiceResponseHindi = if (isUp) "ऊपर स्क्रॉल किया गया।" else "नीचे स्क्रॉल किया गया।"
            )
        }
    }

    private fun handleSystemNavigation(query: String): LocalExecutionResult {
        val service = MaxAccessibilityService.instance
        val isBack = query.contains("back") || query.contains("बैक") || query.contains("पीछे") || query.contains("peeche") || query.contains("वापस") || query.contains("wapas")
        val isRecents = query.contains("recent") || query.contains("रीसेंट") || query.contains("रिसेंट") || query.contains("हाल के")

        return if (service != null) {
            when {
                isBack -> {
                    val performed = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    LocalExecutionResult.Handled(
                        success = performed,
                        actionType = "NAV_BACK",
                        messageHindi = "बैक बटन निष्पादित हुआ (Accessibility Service)।",
                        voiceResponseHindi = "बैक किया गया।"
                    )
                }
                isRecents -> {
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
                        voiceResponseHindi = "होम स्क्रीन पर आ गए।"
                    )
                }
            }
        } else {
            try {
                if (isBack) {
                    LocalExecutionResult.Handled(
                        success = true,
                        actionType = "NAV_BACK",
                        messageHindi = "बैक कमांड निष्पादित।",
                        voiceResponseHindi = "बैक किया गया।"
                    )
                } else {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                    LocalExecutionResult.Handled(
                        success = true,
                        actionType = "NAV_HOME",
                        messageHindi = "होम स्क्रीन खोली गई।",
                        voiceResponseHindi = "होम स्क्रीन पर आ गए।"
                    )
                }
            } catch (e: Exception) {
                LocalExecutionResult.Handled(
                    success = false,
                    actionType = "NAV_HOME",
                    messageHindi = "होम स्क्रीन खोलने में असमर्थ: ${e.localizedMessage}",
                    voiceResponseHindi = "होम स्क्रीन नहीं खुल सकी।"
                )
            }
        }
    }

    /**
     * Directly launches an installed app by name/package.
     */
    fun launchAppByName(appName: String, pkgName: String? = null): LocalExecutionResult.Handled {
        val installedApps = GenericAppLauncher.getInstalledLaunchableApps(context)
        val matchedApp = if (!pkgName.isNullOrBlank()) {
            installedApps.find { it.packageName.equals(pkgName, ignoreCase = true) }
        } else null ?: GenericAppLauncher.findBestAppMatch(appName, installedApps)

        return if (matchedApp != null) {
            val launched = GenericAppLauncher.launchApp(context, matchedApp)
            if (launched) {
                LocalExecutionResult.Handled(
                    success = true,
                    actionType = "OPEN_APP_LOCAL",
                    messageHindi = "${matchedApp.appName} ऐप खोला गया (Package: ${matchedApp.packageName})।",
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
            LocalExecutionResult.Handled(
                success = false,
                actionType = "OPEN_APP_NOT_FOUND",
                messageHindi = "ऐप '$appName' इन्स्टॉल नहीं मिला।",
                voiceResponseHindi = "मुझे '$appName' ऐप आपके फोन में नहीं मिला।"
            )
        }
    }
    private fun handleGenericAppLaunch(parsed: ParsedCommand): LocalExecutionResult {
        val query = parsed.cleanedQuery

        if (parsed.intent != ParsedIntent.OPEN_APP) {
            val openPrefixes = listOf(
                "open ", "kholo ", "launch ", "start ", "chalao ", "chala do ", "khol do ", "khol k ", "khol ke ",
                "kholiye ", "kholna ", "dikhao ", "dikha do ", "chalu ", "chaloo ", "run ",
                "ओपन ", "खोलो ", "चलाओ ", "लॉन्च ", "शुरू करो ", "चालू करो ", "दिखाओ "
            )
            val openSuffixes = listOf(
                " kholo", " khol do", " kholiye", " chalao", " chala do", " open karo", " open kar do", " open kardo",
                " open", " app open karo", " app kholo", " application kholo", " app", " application", " chalu karo",
                " खोलो", " खोल दो", " खोलिए", " चलाओ", " चला दो", " ओपन करो", " ऐप खोलो", " चालू करो", " लॉन्च करो", " दिखाओ"
            )

            val hasOpenVerb = openPrefixes.any { query.startsWith(it) } || openSuffixes.any { query.contains(it) }
            val isDirectAppName = query.split(" ").size <= 2 && (
                query in listOf("facebook", "fb", "instagram", "insta", "whatsapp", "wa", "youtube", "yt", "chrome", "spotify", "gmail", "maps", "camera", "gallery", "settings", "calculator", "clock") ||
                query in listOf("फेसबुक", "इंस्टाग्राम", "व्हाट्सएप", "यूट्यूब", "क्रोम", "कैमरा", "गैलरी", "सेटिंग्स")
            )

            if (!hasOpenVerb && !isDirectAppName) return LocalExecutionResult.NotHandled
        }

        val cleanTarget = parsed.targetEntity.ifBlank { query }
        if (cleanTarget.isBlank()) return LocalExecutionResult.NotHandled

        // Query all installed apps from device
        val installedApps = GenericAppLauncher.getInstalledLaunchableApps(context)
        Log.d(tag, "[AppLaunchRouter] Target candidate: '$cleanTarget' (Raw query: '${parsed.rawQuery}'). Installed apps count: ${installedApps.size}")

        // Perform multi-tier fuzzy match
        val matchedApp = GenericAppLauncher.findBestAppMatch(cleanTarget, installedApps)

        return if (matchedApp != null) {
            Log.i(tag, "[AppLaunchRouter] Match SUCCESS: '$cleanTarget' -> '${matchedApp.appName}' (${matchedApp.packageName})")
            val launched = GenericAppLauncher.launchApp(context, matchedApp)
            if (launched) {
                LocalExecutionResult.Handled(
                    success = true,
                    actionType = "OPEN_APP_LOCAL",
                    messageHindi = "${matchedApp.appName} ऐप खोला गया (Package: ${matchedApp.packageName})।",
                    voiceResponseHindi = "${matchedApp.appName} खोला जा रहा है।"
                )
            } else {
                LocalExecutionResult.Handled(
                    success = false,
                    actionType = "OPEN_APP_LOCAL",
                    messageHindi = "${matchedApp.appName} ऐप लॉन्च नहीं हो सका (Intent null या blocked)।",
                    voiceResponseHindi = "माफ़ कीजिये, ${matchedApp.appName} लॉन्च नहीं हो सका।"
                )
            }
        } else {
            // App was not found in installed apps list — NEVER fail silently!
            Log.w(tag, "[AppLaunchRouter] Match FAILED for '$cleanTarget'. Total installed apps searched: ${installedApps.size}")
            val suggestions = installedApps.take(3).joinToString(", ") { it.appName }
            val suggestionStr = if (suggestions.isNotBlank()) " आपके फोन में $suggestions जैसे ऐप्स उपलब्ध हैं।" else ""

            LocalExecutionResult.Handled(
                success = false,
                actionType = "OPEN_APP_NOT_FOUND",
                messageHindi = "ऐप '$cleanTarget' डिवाइस पर इन्स्टॉल नहीं मिला (कुल ऐप्स: ${installedApps.size})।",
                voiceResponseHindi = "मुझे '$cleanTarget' ऐप आपके फोन में नहीं मिला।$suggestionStr"
            )
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

    private fun isPhoneLockCommand(query: String): Boolean {
        val clean = query.trim().lowercase()
        return clean == "lock" || clean == "लॉक" ||
                clean.contains("phone lock") || clean.contains("फोन लॉक") || clean.contains("फ़ोन लॉक") ||
                clean.contains("lock phone") || clean.contains("lock the phone") || clean.contains("lock my phone") || clean.contains("lock device") ||
                clean.contains("screen lock") || clean.contains("स्क्रीन लॉक") || clean.contains("lock screen") ||
                clean.contains("mobile lock") || clean.contains("मोबाइल लॉक") || clean.contains("lock mobile") ||
                clean.contains("lock kar") || clean.contains("lock karo") || clean.contains("lock kardo") || clean.contains("lock kar do") ||
                clean.contains("लॉक करो") || clean.contains("लॉक कर दो") || clean.contains("लॉक कर") ||
                (clean.contains("max") && (clean.contains("lock") || clean.contains("लॉक")))
    }

    private fun handlePhoneLock(): LocalExecutionResult {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, MaxDeviceAdminReceiver::class.java)
        val isAdminActive = devicePolicyManager?.isAdminActive(adminComponent) == true

        if (!isAdminActive) {
            Log.w(tag, "Phone lock voice command triggered but Device Admin permission is NOT active. Launching Device Admin setup intent.")
            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "मैक्स को वॉइस कमांड से फोन लॉक करने और चोरी से सुरक्षा के लिए डिवाइस एडमिन की आवश्यकता है।"
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(tag, "Failed to launch device admin activation screen", e)
            }

            return LocalExecutionResult.Handled(
                success = false,
                actionType = "LOCK_PHONE_ADMIN_REQUIRED",
                messageHindi = "डिवाइस एडमिन परमिशन आवश्यक है। कृपया परमिशन चालू करें।",
                voiceResponseHindi = "Device Admin permission on karo pehle"
            )
        }

        Log.i(tag, "Phone lock voice command authorized. Preparing to lock screen via DeviceAdmin lockNow().")
        return LocalExecutionResult.Handled(
            success = true,
            actionType = "LOCK_PHONE",
            messageHindi = "फोन तुरंत लॉक किया जा रहा है (Device Admin lockNow)।",
            voiceResponseHindi = "phone lock kar diya"
        )
    }

    private fun isTheftAlarmStartCommand(query: String): Boolean {
        val clean = query.trim().lowercase()
        return clean.contains("chori alarm") || clean.contains("चोरी अलार्म") ||
                clean.contains("emergency alarm") || clean.contains("इमरजेंसी अलार्म") ||
                clean.contains("theft alarm") || clean.contains("थिफ्ट अलार्म") ||
                clean.contains("siren bajao") || clean.contains("साइरन बजाओ") || clean.contains("सायरन बजाओ") ||
                clean.contains("siren chalu") || clean.contains("siren on") || clean.contains("साइरन ऑन") ||
                clean.contains("danger alarm") || clean.contains("chor chor") || clean.contains("चोर चोर")
    }

    private fun isTheftAlarmStopCommand(query: String): Boolean {
        val clean = query.trim().lowercase()
        return (clean.contains("alarm") || clean.contains("अलार्म") || clean.contains("siren") || clean.contains("साइरन") || clean.contains("सायरन")) &&
                (clean.contains("band") || clean.contains("बंद") || clean.contains("stop") || clean.contains("स्टॉप") ||
                        clean.contains("roko") || clean.contains("रोको") || clean.contains("off") || clean.contains("ऑफ") || clean.contains("mute"))
    }

    private fun handleTheftAlarmStart(): LocalExecutionResult {
        Log.w(tag, "Emergency theft alarm start voice command recognized.")
        TheftAlarmManager.getInstance(context).startTheftAlarm("वॉइस कमांड द्वारा चोरी अलार्म")
        return LocalExecutionResult.Handled(
            success = true,
            actionType = "THEFT_ALARM_START",
            messageHindi = "🚨 इमरजेंसी चोरी अलार्म फुल वॉल्यूम पर चालू किया गया!",
            voiceResponseHindi = "Emergency theft alarm chalu kar diya gaya hai!"
        )
    }

    private fun handleTheftAlarmStop(): LocalExecutionResult {
        Log.i(tag, "Emergency theft alarm stop voice command recognized.")
        TheftAlarmManager.getInstance(context).stopTheftAlarm()
        return LocalExecutionResult.Handled(
            success = true,
            actionType = "THEFT_ALARM_STOP",
            messageHindi = "चोरी अलार्म और साइरन बंद कर दिया गया।",
            voiceResponseHindi = "Alarm band kar diya gaya hai."
        )
    }
}
