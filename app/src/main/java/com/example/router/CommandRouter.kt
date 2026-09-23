package com.example.router

import android.content.Context
import android.util.Log

enum class CommandCategory {
    OFFLINE_TASK,    // Handled locally on Android without Gemini (App Launch, Hardware Toggle, Lock, Nav, Reminder)
    SCREEN_TASK,     // Task requiring screen perception/interaction (WhatsApp msg read, YT video play, skip ad, etc.)
    CONVERSATION     // Chit-chat, Q&A, knowledge queries -> Direct Gemini natural response
}

data class CommandRoutingDecision(
    val rawCommand: String,
    val cleanedCommand: String,
    val category: CommandCategory,
    val matchedAppName: String?,
    val matchedAppPackage: String?,
    val remainingInstruction: String?,
    val actionSummary: String
)

/**
 * Single Centralized Command-Routing Engine for Max Assistant.
 *
 * Implements a 5-Stage Pipeline:
 * STAGE 1 — CLEANUP: Strips wake words and name references ("Max", "Hello Max", "suno", etc.)
 * STAGE 2 — CLASSIFICATION: Categorizes into OFFLINE_TASK, SCREEN_TASK, or CONVERSATION
 * STAGE 3 — ROUTING LOGIC: Routes execution locally or forwards to Gemini with screen snapshot
 * STAGE 4 — RELIABLE APP MATCHING: Token/n-gram fuzzy matching against PackageManager installed apps
 * STAGE 5 — DEBUG LOGGING: Emits exact required STAGE1-STAGE4 log lines for Logcat & UI stream
 */
object CommandRouter {

    private const val TAG = "CommandRouter"

    // Assistant name references, wake words, and address fillers to strip globally in Stage 1
    private val ADDRESS_PATTERNS = listOf(
        // English / Hinglish
        "hello max", "hey max", "hi max", "ok max", "okay max", "suno max", "sun max",
        "oye max", "arey max", "dear max", "max ji", "max bhaiya", "max bhai", "max",
        "hello maks", "hey maks", "hi maks", "suno maks", "sun maks", "maks", "macks",
        "hello ganesh", "hey ganesh", "ganesh max",

        // Hindi Script
        "हेलो मैक्स", "हाय मैक्स", "सुनो मैक्स", "सुन मैक्स", "अरे मैक्स", "मैक्स भाई", "मैक्स जी", "मैक्स",

        // General Address Fillers
        "hello", "hey", "hi", "suno", "sun", "oye", "arey", "zara", "jara",
        "please", "kripya", "krpya", "हेलो", "हाय", "सुनो", "सुन", "अरे", "जरा"
    )

    // Verbs indicating app opening
    private val OPEN_VERBS = listOf(
        "kholo", "khol do", "khol k", "khol ke", "kholiye", "kholna", "open", "launch", "start",
        "chalao", "chala do", "chalaayein", "play", "open karo", "open kar do", "open kardo", "app kholo", "open kijiye",
        "kholiye na", "dikhao", "dikha do", "chaloo karo", "chalu karo", "chalu kar do", "run",
        "खोलो", "खोल दो", "खोलिए", "चलाओ", "चला दो", "चालू करो", "लॉन्च करो", "ओपन करो", "ओपन", "दिखाओ", "दिखा दो"
    )

    // Filler particles in app opening commands
    private val APP_FILLERS = listOf(
        "app", "application", "apk", "ko", "par", "pe", "karo", "kar do", "kardo", "kijiye", "kariye",
        "pls", "please", "zara", "jara", "se", "mein", "me",
        "ऐप", "एप्लिकेशन", "को", "पर", "पे", "करो", "कर दो", "प्लीज", "जरा", "कीजिये", "करिये"
    )

    /**
     * STAGE 1: CLEANUP
     * Strips wake words, greetings, and assistant name references wherever they appear in the query.
     */
    fun cleanCommandText(rawCommand: String): String {
        var text = rawCommand.lowercase().trim()

        // Remove address patterns globally
        for (pattern in ADDRESS_PATTERNS) {
            text = text.replace(Regex("(?i)\\b${Regex.escape(pattern)}\\b"), " ")
        }

        // Extra cleanup for isolated 'max' or 'maks'
        text = text.replace(Regex("(?i)\\bmax\\b"), " ")
            .replace(Regex("(?i)\\bmaks\\b"), " ")
            .replace(Regex("(?i)\\bmacks\\b"), " ")
            .replace(Regex("(?i)मैक्स"), " ")

        // Clean up double spaces, punctuation, leading/trailing whitespace
        text = text.replace(Regex("[,.!?\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (text.isNotBlank()) text else rawCommand.lowercase().trim()
    }

    /**
     * CENTRAL ROUTING FUNCTION (5-STAGE ARCHITECTURE)
     */
    fun routeCommand(context: Context, rawCommand: String): CommandRoutingDecision {
        // -----------------------------------------------------------------------------------------
        // STAGE 1: CLEANUP
        // -----------------------------------------------------------------------------------------
        val cleaned = cleanCommandText(rawCommand)

        // Fetch installed apps for candidate matching
        val installedApps = GenericAppLauncher.getInstalledLaunchableApps(context)

        // -----------------------------------------------------------------------------------------
        // STAGE 4: RELIABLE APP MATCHING (Token/n-gram matching against installed apps)
        // -----------------------------------------------------------------------------------------
        var matchedApp: InstalledAppInfo? = null
        var candidateSpokenAppName: String? = null

        val candidateTokens = extractCandidateAppNames(cleaned)
        for (candidate in candidateTokens) {
            val app = GenericAppLauncher.findBestAppMatch(candidate, installedApps)
            if (app != null) {
                matchedApp = app
                candidateSpokenAppName = candidate
                break
            }
        }

        if (matchedApp == null) {
            matchedApp = GenericAppLauncher.findBestAppMatch(cleaned, installedApps)
        }

        // -----------------------------------------------------------------------------------------
        // STAGE 2 & STAGE 3: CLASSIFICATION AND ROUTING LOGIC
        // -----------------------------------------------------------------------------------------
        val isPureAppLaunchCommand = matchedApp != null && isPureAppOpenRequest(cleaned, candidateSpokenAppName ?: matchedApp.appName)
        val isHardwareOrLocalTask = isHardwareToggleOrSystemTask(cleaned)
        val isScreenTaskInstruction = hasScreenTaskInstruction(cleaned, matchedApp?.appName)

        val category: CommandCategory
        val matchedAppName: String? = matchedApp?.appName
        val matchedAppPkg: String? = matchedApp?.packageName
        var remainingInstruction: String? = null
        val actionSummary: String

        if (isPureAppLaunchCommand) {
            category = CommandCategory.OFFLINE_TASK
            actionSummary = "OFFLINE_APP_LAUNCH: Open ${matchedApp!!.appName} locally via Intent"
            Log.i(TAG, "APP_OPEN_REQUEST: $rawCommand")
            Log.i(TAG, "APP_OPEN_ROUTE: LOCAL")
        } else if (isHardwareOrLocalTask && matchedApp == null) {
            category = CommandCategory.OFFLINE_TASK
            actionSummary = "OFFLINE_SYSTEM_TASK: Local Hardware/System action executed"
        } else if (matchedApp != null || isScreenTaskInstruction) {
            category = CommandCategory.SCREEN_TASK
            remainingInstruction = extractRemainingInstruction(cleaned, matchedApp?.appName, candidateSpokenAppName)
            actionSummary = if (matchedApp != null) {
                "SCREEN_PERCEPTION_TASK: Launch/Ensure ${matchedApp.appName} open, then execute '$remainingInstruction' via Gemini"
            } else {
                "SCREEN_PERCEPTION_TASK: Execute '$cleaned' on active screen via Gemini"
            }
        } else {
            category = CommandCategory.CONVERSATION
            actionSummary = "CONVERSATION_OR_QA: Query passed to Gemini for natural conversational answer"
        }

        val decision = CommandRoutingDecision(
            rawCommand = rawCommand,
            cleanedCommand = cleaned,
            category = category,
            matchedAppName = matchedAppName,
            matchedAppPackage = matchedAppPkg,
            remainingInstruction = remainingInstruction,
            actionSummary = actionSummary
        )

        // -----------------------------------------------------------------------------------------
        // STAGE 5: DEBUG LOGGING (Exact 5 Log Lines Required)
        // -----------------------------------------------------------------------------------------
        val appMatchedStr = matchedAppName?.let { "$it ($matchedAppPkg)" } ?: "NONE"
        val remainingInstructionStr = remainingInstruction.takeIf { !it.isNullOrBlank() } ?: "NONE"

        Log.i(TAG, "STAGE1_CLEANED: $cleaned")
        Log.i(TAG, "STAGE2_CATEGORY: $category")
        Log.i(TAG, "STAGE3_APP_MATCHED: $appMatchedStr")
        Log.i(TAG, "STAGE3_REMAINING_INSTRUCTION: $remainingInstructionStr")
        Log.i(TAG, "STAGE4_ACTION: $actionSummary")

        return decision
    }

    private fun extractCandidateAppNames(cleaned: String): List<String> {
        val candidates = mutableListOf<String>()
        val words = cleaned.split(" ")

        for (i in words.indices) {
            val w1 = words[i].lowercase()
            if (w1 !in OPEN_VERBS && w1 !in APP_FILLERS && w1.length >= 2) {
                candidates.add(w1)
            }
            if (i < words.size - 1) {
                val w2 = "${words[i]} ${words[i+1]}"
                candidates.add(w2)
            }
        }
        return candidates.distinct()
    }

    private fun isPureAppOpenRequest(cleaned: String, appNameCandidate: String): Boolean {
        var text = cleaned.lowercase()

        // Remove candidate app name
        if (appNameCandidate.isNotBlank()) {
            text = text.replace(appNameCandidate.lowercase(), "")
        }

        // Remove open verbs & fillers
        for (verb in OPEN_VERBS) {
            text = text.replace(verb.lowercase(), "")
        }
        for (filler in APP_FILLERS) {
            text = text.replace(filler.lowercase(), "")
        }

        // Clean up remaining non-word characters and whitespace
        text = text.replace(Regex("[^a-zA-Z0-9\\u0900-\\u097F]"), "").trim()

        // If remaining text is blank or very short (<= 3 chars), it's 100% pure app launch
        if (text.length <= 3) return true

        // Check if remaining text contains explicit screen perception keywords
        val screenTaskKeywords = listOf("search", "find", "message", "chat", "msg", "bhejo", "send", "read", "padho", "video", "song", "play", "scroll", "comment", "like", "share", "खोजो", "भेजो", "पढ़ो")
        val hasSecondaryTask = screenTaskKeywords.any { text.contains(it) }

        return !hasSecondaryTask
    }

    private fun isHardwareToggleOrSystemTask(cleaned: String): Boolean {
        val lower = cleaned.lowercase()

        // Hardware Toggles
        if (lower.contains("wifi") || lower.contains("वाईफाई") || lower.contains("wi-fi") ||
            lower.contains("bluetooth") || lower.contains("ब्लूटूथ") ||
            lower.contains("torch") || lower.contains("flashlight") || lower.contains("टॉर्च") ||
            lower.contains("volume") || lower.contains("वॉल्यूम") || lower.contains("आवाज") ||
            lower.contains("brightness") || lower.contains("ब्राइटनेस") || lower.contains("चमक") ||
            lower.contains("dnd") || lower.contains("do not disturb") || lower.contains("डिस्टर्ब") ||
            lower.contains("hotspot") || lower.contains("हॉटस्पॉट") ||
            lower.contains("gps") || lower.contains("location") || lower.contains("लोकेशन") ||
            lower.contains("mobile data") || lower.contains("डेटा") || lower.contains("airplane") || lower.contains("flight mode")
        ) return true

        // System Navigation
        if (lower == "home" || lower == "होम" || lower.contains("home screen") || lower.contains("go home") ||
            lower == "back" || lower == "बैक" || lower.contains("go back") || lower.contains("पीछे जाओ") || lower.contains("वापस जाओ") ||
            lower.contains("recent apps") || lower.contains("रीसेंट") || lower.contains("recents")
        ) return true

        // Phone Lock & Emergency Siren
        if (lower.contains("lock") || lower.contains("लॉक") || lower.contains("siren") || lower.contains("साइरन") || lower.contains("chori alarm")) return true

        // Alarm & Reminders
        if (lower.contains("alarm") || lower.contains("अलार्म") || lower.contains("reminder") || lower.contains("रिमाइंडर") || lower.contains("याद दिलाओ")) return true

        // Creator & Identity
        if (lower.contains("kisne banaya") || lower.contains("who made you") || lower.contains("tumhara naam") || lower.contains("what is your name")) return true

        return false
    }

    private fun hasScreenTaskInstruction(cleaned: String, matchedAppName: String?): Boolean {
        val lower = cleaned.lowercase()
        val keywords = listOf(
            "padho", "read", "message", "chat", "msg", "bhejo", "send", "video", "song",
            "chalao", "play", "search", "skip", "click", "tap", "scroll", "comment", "like", "share",
            "खोल के", "पढ़ो", "भेजो", "चलाओ", "दिखाओ", "मैसेज", "चैट", "वीडियो", "खोजो"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun extractRemainingInstruction(cleaned: String, matchedAppName: String?, candidateSpoken: String?): String {
        var text = cleaned
        val toRemove = candidateSpoken ?: matchedAppName ?: ""
        if (toRemove.isNotBlank()) {
            text = text.replace(Regex("(?i)\\b${Regex.escape(toRemove)}\\b"), "")
        }
        for (verb in OPEN_VERBS) {
            text = text.replace(Regex("(?i)\\b${Regex.escape(verb)}\\b"), "")
        }
        for (filler in APP_FILLERS) {
            text = text.replace(Regex("(?i)\\b${Regex.escape(filler)}\\b"), "")
        }
        return text.replace(Regex("\\s+"), " ").trim().ifBlank { cleaned }
    }
}
