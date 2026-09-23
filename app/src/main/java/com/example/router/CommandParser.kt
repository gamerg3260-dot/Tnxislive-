package com.example.router

import android.util.Log

enum class ParsedIntent {
    OPEN_APP,         // Launch an app e.g., "YouTube kholo", "Hello Max WhatsApp open karo"
    TOGGLE_SETTING,   // Toggles hardware e.g., "WiFi on karo", "Bluetooth band karo"
    SYSTEM_NAV,       // System key navigation e.g., "Home jao", "Back karo", "Recents"
    SCROLL,           // Accessibility scrolling e.g., "Neeche scroll karo", "Scroll up"
    LOCK_PHONE,       // Lock device e.g., "Phone lock kar do"
    CAMERA_SELFIE,    // Camera or selfie e.g., "Selfie lo", "Photo खींचो"
    ALARM_REMINDER,   // Alarm or reminder e.g., "5 baje alarm lagao"
    WEATHER,          // Weather queries e.g., "Mausam kaisa hai"
    THEFT_ALARM,      // Anti-theft siren e.g., "Siren bajao", "Alarm band karo"
    IDENTITY_QUERY,   // Identity e.g., "Tumhe kisne banaya", "Tum kaun ho"
    CONVERSATION,     // Chit-chat, greetings, questions e.g., "Kaise ho", "Joke sunao"
    UNKNOWN           // Ambiguous -> requires Gemini fallback or screen perception
}

data class ParsedCommand(
    val rawQuery: String,          // Original spoken text: "Hello Max, YouTube kholo"
    val cleanedQuery: String,      // Stripped of wake words & address fillers: "youtube kholo"
    val extractedAddress: String?, // Address/greeting part: "hello max"
    val intent: ParsedIntent,      // High-level recognized intent
    val targetEntity: String,      // Clean entity/app/target name: "youtube"
    val actionVerb: String,        // Verb word: "kholo"
    val confidence: Float,         // Confidence score (0.0 to 1.0)
    val isLocalAction: Boolean     // True if executable locally offline
)

/**
 * Smart Command Parser & Entity Extractor.
 * Extracts wake words, address greetings, filler phrases, intent, action verbs,
 * and target entities before routing.
 */
object CommandParser {

    private const val TAG = "CommandParser"

    // Wake words, greetings, and address terms to strip out from command prefix/suffix
    private val WAKE_ADDRESS_PATTERNS = listOf(
        // Phrases with Max
        "hello max", "hey max", "hi max", "ok max", "okay max", "suno max", "sun max",
        "oye max", "arey max", "dear max", "max ji", "max bhaiya",
        "hello maks", "hey maks", "hi maks", "suno maks", "sun maks", "maks",
        "hello macks", "hey macks", "suno macks", "macks",
        "hello ganesh", "hey ganesh", "ganesh max",

        // Hindi script wake words
        "हेलो मैक्स", "हाय मैक्स", "सुनो मैक्स", "सुन मैक्स", "अरे मैक्स", "मैक्स",

        // General filler greetings
        "hello", "hey", "hi", "suno", "sun", "oye", "arey", "zara", "jara",
        "please", "kripya", "krpya", "हेलो", "हाय", "सुनो", "सुन", "अरे", "जरा"
    )

    // Open App Action Verbs
    private val OPEN_APP_VERBS = listOf(
        "kholo", "khol do", "kholiye", "kholna", "open", "launch", "start",
        "chalao", "chala do", "play", "open karo", "open kar do", "app kholo",
        "खोलो", "खोल दो", "खोलिए", "चलाओ", "चला दो", "चालू करो", "लॉन्च કરો", "ओपन करो", "ओपन"
    )

    // Filler particles in app opening commands
    private val APP_FILLER_WORDS = listOf(
        "app", "application", "apk", "ko", "par", "pe", "karo", "kar do", "kardo",
        "ऐप", "एप्लिकेशन", "को", "पर", "पे", "करो", "कर दो", "प्लीज", "जरा"
    )

    // Well-known standalone app names for zero-verb direct matching
    private val DIRECT_APP_NAMES = mapOf(
        "youtube" to "YouTube",
        "yt" to "YouTube",
        "यूट्यूब" to "YouTube",
        "whatsapp" to "WhatsApp",
        "wa" to "WhatsApp",
        "व्हाट्सएप" to "WhatsApp",
        "व्हाट्सऐप" to "WhatsApp",
        "facebook" to "Facebook",
        "fb" to "Facebook",
        "फेसबुक" to "Facebook",
        "instagram" to "Instagram",
        "insta" to "Instagram",
        "इंस्टाग्राम" to "Instagram",
        "chrome" to "Chrome",
        "क्रोम" to "Chrome",
        "spotify" to "Spotify",
        "स्पॉटिफ़ाई" to "Spotify",
        "camera" to "Camera",
        "कैमरा" to "Camera",
        "gallery" to "Gallery",
        "गैलरी" to "Gallery",
        "settings" to "Settings",
        "सेटिंग्स" to "Settings",
        "calculator" to "Calculator",
        "कैलकुलेटर" to "Calculator",
        "clock" to "Clock",
        "घड़ी" to "Clock"
    )

    fun parse(rawInput: String): ParsedCommand {
        val trimmedInput = rawInput.trim()
        if (trimmedInput.isBlank()) {
            return ParsedCommand(
                rawQuery = rawInput,
                cleanedQuery = "",
                extractedAddress = null,
                intent = ParsedIntent.CONVERSATION,
                targetEntity = "",
                actionVerb = "",
                confidence = 1.0f,
                isLocalAction = false
            )
        }

        // STEP 1: Normalize text (lowercase, strip commas, dots, exclamation marks)
        var normalized = trimmedInput.lowercase()
            .replace(",", " ")
            .replace(".", " ")
            .replace("!", " ")
            .replace("?", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        // STEP 2: Extract & strip address/greeting fillers ("Hello Max", "Suno Max", etc.)
        var extractedAddress: String? = null
        for (pattern in WAKE_ADDRESS_PATTERNS) {
            if (normalized.startsWith("$pattern ")) {
                extractedAddress = pattern
                normalized = normalized.removePrefix("$pattern ").trim()
                break
            } else if (normalized.endsWith(" $pattern")) {
                extractedAddress = pattern
                normalized = normalized.removeSuffix(" $pattern").trim()
                break
            } else if (normalized == pattern) {
                extractedAddress = pattern
                normalized = ""
                break
            }
        }

        // Secondary check if "max" or "hello" is still stuck at prefix
        if (normalized.startsWith("max ") || normalized.startsWith("maks ") || normalized.startsWith("macks ")) {
            val parts = normalized.split(" ", limit = 2)
            if (parts.size > 1) {
                extractedAddress = (extractedAddress?.let { "$it ${parts[0]}" } ?: parts[0])
                normalized = parts[1].trim()
            }
        }

        val cleanedQuery = normalized.ifBlank { trimmedInput.lowercase() }

        // STEP 3: Identify Intent & Target Entity

        // 3A. Identity / Creator Questions
        if (isIdentityQuery(cleanedQuery)) {
            return ParsedCommand(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress,
                intent = ParsedIntent.IDENTITY_QUERY,
                targetEntity = "Max / Ganesh Sahani",
                actionVerb = "identity",
                confidence = 0.98f,
                isLocalAction = true
            )
        }

        // 3B. Phone Lock
        if (isPhoneLockQuery(cleanedQuery)) {
            return ParsedCommand(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress,
                intent = ParsedIntent.LOCK_PHONE,
                targetEntity = "Device Lock",
                actionVerb = "lock",
                confidence = 0.98f,
                isLocalAction = true
            )
        }

        // 3C. Theft Alarm / Siren
        if (cleanedQuery.contains("siren") || cleanedQuery.contains("സൈറൻ") || cleanedQuery.contains("साइरन") ||
            cleanedQuery.contains("chori alarm") || cleanedQuery.contains("theft alarm")
        ) {
            return ParsedCommand(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress,
                intent = ParsedIntent.THEFT_ALARM,
                targetEntity = "Emergency Siren",
                actionVerb = "siren",
                confidence = 0.98f,
                isLocalAction = true
            )
        }

        // 3D. Open App Intent Parsing
        val openAppCommand = parseOpenAppIntent(cleanedQuery)
        if (openAppCommand != null) {
            Log.d(TAG, "[Parse] Open App Match: Raw='$rawInput' ➔ Address='$extractedAddress', Target='${openAppCommand.targetEntity}'")
            return openAppCommand.copy(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress
            )
        }

        // 3E. Hardware Toggles
        val toggleCommand = parseToggleIntent(cleanedQuery)
        if (toggleCommand != null) {
            return toggleCommand.copy(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress
            )
        }

        // 3F. System Navigation
        if (isSystemNavQuery(cleanedQuery)) {
            return ParsedCommand(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress,
                intent = ParsedIntent.SYSTEM_NAV,
                targetEntity = cleanedQuery,
                actionVerb = "navigate",
                confidence = 0.95f,
                isLocalAction = true
            )
        }

        // 3G. Scrolling
        if (cleanedQuery.contains("scroll") || cleanedQuery.contains("स्क्रॉल") ||
            cleanedQuery.contains("neeche karo") || cleanedQuery.contains("upar karo")
        ) {
            return ParsedCommand(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress,
                intent = ParsedIntent.SCROLL,
                targetEntity = if (cleanedQuery.contains("up") || cleanedQuery.contains("upar")) "Up" else "Down",
                actionVerb = "scroll",
                confidence = 0.95f,
                isLocalAction = true
            )
        }

        // 3H. Camera / Selfie
        if (cleanedQuery.contains("selfie") || cleanedQuery.contains("सेल्फी") ||
            (cleanedQuery.contains("photo") || cleanedQuery.contains("foto"))
        ) {
            return ParsedCommand(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress,
                intent = ParsedIntent.CAMERA_SELFIE,
                targetEntity = if (cleanedQuery.contains("selfie")) "Front Camera" else "Back Camera",
                actionVerb = "capture",
                confidence = 0.95f,
                isLocalAction = true
            )
        }

        // 3I. Reminder / Alarm
        if (cleanedQuery.contains("reminder") || cleanedQuery.contains("रिमाइंडर") ||
            cleanedQuery.contains("alarm") || cleanedQuery.contains("अलार्म") || cleanedQuery.contains("yaad dilana")
        ) {
            return ParsedCommand(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress,
                intent = ParsedIntent.ALARM_REMINDER,
                targetEntity = "Reminder",
                actionVerb = "schedule",
                confidence = 0.90f,
                isLocalAction = true
            )
        }

        // 3J. Weather
        if (cleanedQuery.contains("weather") || cleanedQuery.contains("mausam") || cleanedQuery.contains("मौसम") || cleanedQuery.contains("barish")) {
            return ParsedCommand(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress,
                intent = ParsedIntent.WEATHER,
                targetEntity = "Weather",
                actionVerb = "fetch",
                confidence = 0.90f,
                isLocalAction = true
            )
        }

        // 3K. Conversational Chit-Chat
        if (isConversationalQuery(cleanedQuery)) {
            return ParsedCommand(
                rawQuery = rawInput,
                cleanedQuery = cleanedQuery,
                extractedAddress = extractedAddress,
                intent = ParsedIntent.CONVERSATION,
                targetEntity = "Chat",
                actionVerb = "speak",
                confidence = 0.90f,
                isLocalAction = false
            )
        }

        // 3L. Default / Ambiguous Command
        return ParsedCommand(
            rawQuery = rawInput,
            cleanedQuery = cleanedQuery,
            extractedAddress = extractedAddress,
            intent = ParsedIntent.UNKNOWN,
            targetEntity = cleanedQuery,
            actionVerb = "",
            confidence = 0.40f,
            isLocalAction = false
        )
    }

    private fun parseOpenAppIntent(cleanedQuery: String): ParsedCommand? {
        // 1. Direct App Name Check (e.g. "youtube", "whatsapp", "instagram")
        val directMatch = DIRECT_APP_NAMES[cleanedQuery]
        if (directMatch != null) {
            return ParsedCommand(
                rawQuery = cleanedQuery,
                cleanedQuery = cleanedQuery,
                extractedAddress = null,
                intent = ParsedIntent.OPEN_APP,
                targetEntity = directMatch,
                actionVerb = "open_direct",
                confidence = 0.98f,
                isLocalAction = true
            )
        }

        // 2. Check if query contains any open verbs
        var matchedVerb: String? = null
        for (verb in OPEN_APP_VERBS) {
            if (cleanedQuery.contains(verb)) {
                matchedVerb = verb
                break
            }
        }

        if (matchedVerb == null) {
            // No explicit verb, but check if single/two-word input matches an app name or alias
            val words = cleanedQuery.split(" ").filter { it.isNotBlank() }
            if (words.size <= 2) {
                val candidateTarget = words.joinToString(" ")
                if (candidateTarget.length >= 2) {
                    return ParsedCommand(
                        rawQuery = cleanedQuery,
                        cleanedQuery = cleanedQuery,
                        extractedAddress = null,
                        intent = ParsedIntent.OPEN_APP,
                        targetEntity = candidateTarget,
                        actionVerb = "implicit_open",
                        confidence = 0.80f,
                        isLocalAction = true
                    )
                }
            }
            return null
        }

        // 3. Extract target entity by stripping verb and filler words
        var targetPart = cleanedQuery.replace(matchedVerb, " ")
        for (filler in APP_FILLER_WORDS) {
            targetPart = targetPart.replace(" $filler ", " ")
                .replace(" $filler", " ")
                .replace("$filler ", " ")
                .replace(Regex("(?i)$filler"), " ")
        }
        val cleanTarget = targetPart.trim().replace("\\s+".toRegex(), " ")

        if (cleanTarget.isBlank()) return null

        val mappedEntity = DIRECT_APP_NAMES[cleanTarget] ?: cleanTarget

        return ParsedCommand(
            rawQuery = cleanedQuery,
            cleanedQuery = cleanedQuery,
            extractedAddress = null,
            intent = ParsedIntent.OPEN_APP,
            targetEntity = mappedEntity,
            actionVerb = matchedVerb,
            confidence = 0.95f,
            isLocalAction = true
        )
    }

    private fun parseToggleIntent(cleanedQuery: String): ParsedCommand? {
        val toggles = listOf("wifi", "wi-fi", "वाईफाई", "bluetooth", "ब्लूटूथ", "torch", "flashlight", "टॉर्च", "hotspot", "हॉटस्पॉट", "brightness", "ब्राइटनेस", "volume", "वॉल्यूम")
        val matchedToggle = toggles.firstOrNull { cleanedQuery.contains(it) } ?: return null

        val isTurnOn = cleanedQuery.contains("on") || cleanedQuery.contains("chalu") || cleanedQuery.contains("चालू") || cleanedQuery.contains("बढ़ाओ") || cleanedQuery.contains("badhao")
        val isTurnOff = cleanedQuery.contains("off") || cleanedQuery.contains("band") || cleanedQuery.contains("बंद") || cleanedQuery.contains("कम करो") || cleanedQuery.contains("kam karo")

        if (isTurnOn || isTurnOff) {
            return ParsedCommand(
                rawQuery = cleanedQuery,
                cleanedQuery = cleanedQuery,
                extractedAddress = null,
                intent = ParsedIntent.TOGGLE_SETTING,
                targetEntity = matchedToggle,
                actionVerb = if (isTurnOn) "turn_on" else "turn_off",
                confidence = 0.95f,
                isLocalAction = true
            )
        }
        return null
    }

    private fun isIdentityQuery(query: String): Boolean {
        return query.contains("kisne banaya") || query.contains("creator") || query.contains("tum kaun ho") ||
                query.contains("who made you") || query.contains("who created you") || query.contains("ganesh sahani") ||
                query.contains("गणेश साहनी") || query.contains("किसने बनाया")
    }

    private fun isPhoneLockQuery(query: String): Boolean {
        return query.contains("lock phone") || query.contains("phone lock") || query.contains("screen lock") ||
                query.contains("फोन लॉक") || query.contains("स्क्रीन लॉक") || query == "lock" || query == "लॉक"
    }

    private fun isSystemNavQuery(query: String): Boolean {
        return query == "home" || query == "back" || query == "recents" || query.contains("home screen") ||
                query.contains("home jao") || query.contains("back karo") || query.contains("recent apps")
    }

    private fun isConversationalQuery(query: String): Boolean {
        return query.contains("kaise ho") || query.contains("how are you") || query.contains("kya haal hai") ||
                query.contains("joke") || query.contains("shayari") || query.contains("namaste") ||
                query.contains("kya hota hai") || query.contains("explain") || query.contains("samjhao") ||
                query == "hello" || query == "hi" || query == "hey"
    }
}
