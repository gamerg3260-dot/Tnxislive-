package com.example.router

import android.content.Context
import android.util.Log
import com.example.gemini.GeminiClient

data class InteractionRecord(
    val rawCommand: String,
    val cleanedCommand: String,
    val category: CommandCategory,
    val matchedAppName: String?,
    val actionType: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class IntelligenceDecision(
    val baseRoutingDecision: CommandRoutingDecision,
    val isSelfCorrection: Boolean = false,
    val correctionMessageHindi: String? = null,
    val needsContextClarification: Boolean = false,
    val clarificationPromptHindi: String? = null,
    val needsUserConfirmation: Boolean = false,
    val confirmationPromptHindi: String? = null,
    val confidenceScore: Int,
    val confidenceLevel: String, // "HIGH", "MEDIUM", "LOW"
    val resolvedQuery: String,
    val activeContextApp: String?,
    val logLayer1: String,
    val logLayer2: String,
    val logLayer3: String,
    val logLayer4: String
)

/**
 * Advanced 4-Layer Intelligence Engine built ON TOP OF the STAGE 1-5 CommandRouter.
 *
 * Preserves existing STAGE 1-5 routing logic completely as the core base.
 * Adds 4 distinct intelligence layers:
 * LAYER 1 — CONTEXT AWARENESS & PRONOUN RESOLUTION
 * LAYER 2 — CONFIDENCE-BASED CONFIRMATION
 * LAYER 3 — NATURAL LANGUAGE FLEXIBILITY & SYNONYM MAPPING
 * LAYER 4 — IMMEDIATE SELF-CORRECTION (15-second undo/cancel window)
 */
object IntelligenceLayerEngine {

    private const val TAG = "IntelligenceLayerEngine"
    private const val CORRECTION_WINDOW_MS = 15000L // 15 seconds

    // Context tracking
    private var activeAppName: String? = null
    private var lastActionType: String? = null
    private val interactionHistory = mutableListOf<InteractionRecord>()

    // Self-correction triggers
    private val CORRECTION_KEYWORDS = listOf(
        "nahi galat", "wrong", "yeh nahi", "ye nahi", "galat khola", "galat hai",
        "no stop", "ruko galat", "undo", "cancel", "galat app", "wapas jao",
        "nahi ye nahi", "galat hua", "galat command", "galat tha"
    )

    // Hindi / Hinglish pronouns and referring words
    private val PRONOUNS = listOf(
        "iska", "iski", "iske", "yeh wala", "ye wala", "is wale", "usko", "usne",
        "use", "ise", "ispe", "uspe", "is me", "us me", "is ka", "us ka", "is par", "us par"
    )

    // Open/Launch Verb Synonyms
    private val OPEN_SYNONYMS = listOf(
        "kholo", "khol do", "kholiye", "kholna", "open", "launch", "start",
        "chalao", "chala do", "dikhao", "dikha do", "le chalo", "pe le chalo",
        "par le chalo", "me le chalo", "laga do", "chalu karo", "dekho", "khool do"
    )

    // Affirmation words for pending confirmation (English, Hinglish, Devanagari)
    private val AFFIRMATIVE_WORDS = listOf(
        "haan", "ha", "yes", "yeah", "yep", "sure", "ok", "okay", "sahi hai", "wahi", "khol do", "chalao",
        "haan ji", "sahi", "wahi wala", "chala do", "ha kholo", "haan kholo", "haan wahi", "confirm", "kardo",
        "हाँ", "हां", "जी", "जी हां", "जी हाँ", "हाँ जी", "हाँ खोलो", "खोलो", "खोल दो", "चलाओ", "चला दो",
        "ओपन करो", "कर दो", "सही है", "वही", "वही वाला", "कन्फर्म", "करो", "चालू करो"
    )

    // Denial words for pending confirmation (English, Hinglish, Devanagari)
    private val NEGATIVE_WORDS = listOf(
        "nahi", "na", "no", "nope", "galat", "mat kholo", "cancel", "stop", "reh ne do", "rehne do", "don't",
        "नहीं", "नही", "ना", "जी नहीं", "गलत", "मत खोलो", "रहने दो", "कैंसल", "कैंसिल", "रद्द करो", "बंद करो"
    )

    /**
     * Updates active app and short-term interaction history.
     */
    fun updateActiveContext(appName: String?, actionType: String, rawCommand: String, cleanedCommand: String, category: CommandCategory) {
        if (!appName.isNullOrBlank()) {
            activeAppName = appName
        }
        lastActionType = actionType
        val record = InteractionRecord(
            rawCommand = rawCommand,
            cleanedCommand = cleanedCommand,
            category = category,
            matchedAppName = appName ?: activeAppName,
            actionType = actionType
        )
        synchronized(interactionHistory) {
            interactionHistory.add(0, record)
            if (interactionHistory.size > 5) {
                interactionHistory.removeAt(interactionHistory.size - 1)
            }
        }
        Log.d(TAG, "Updated context: activeApp='$activeAppName', lastAction='$lastActionType', historySize=${interactionHistory.size}")
    }

    fun getActiveApp(): String? = activeAppName

    fun getLastInteraction(): InteractionRecord? {
        return synchronized(interactionHistory) {
            interactionHistory.firstOrNull()
        }
    }

    fun isAffirmative(query: String): Boolean {
        val lower = query.lowercase().trim()
            .replace(",", "").replace(".", "").replace("!", "").replace("?", "")
        val words = lower.split(" ").filter { it.isNotBlank() }
        if (words.size <= 3 && AFFIRMATIVE_WORDS.any { lower == it || lower.contains(it) }) {
            return true
        }
        val strongYes = listOf("haan", "ha", "yes", "ji", "हाँ", "हां", "जी", "sahi hai", "confirm", "yep", "yeah")
        return strongYes.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }
    }

    fun isNegative(query: String): Boolean {
        val lower = query.lowercase().trim()
            .replace(",", "").replace(".", "").replace("!", "").replace("?", "")
        val words = lower.split(" ").filter { it.isNotBlank() }
        if (words.size <= 3 && NEGATIVE_WORDS.any { lower == it || lower.contains(it) }) {
            return true
        }
        val strongNo = listOf("nahi", "na", "no", "galat", "नहीं", "नही", "ना", "cancel", "stop")
        return strongNo.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }
    }

    /**
     * Processes command through the 4 Intelligence Layers ON TOP OF CommandRouter STAGE 1-5.
     */
    fun processWithIntelligence(context: Context, rawCommand: String): IntelligenceDecision {
        val cleanedInput = CommandRouter.cleanCommandText(rawCommand)
        val lastInteraction = getLastInteraction()
        val now = System.currentTimeMillis()

        // -----------------------------------------------------------------------------------------
        // LAYER 4 — SELF-CORRECTION CHECK (Immediate Undo/Cancel within 15s)
        // -----------------------------------------------------------------------------------------
        val isCorrectionTrigger = lastInteraction != null &&
                (now - lastInteraction.timestamp <= CORRECTION_WINDOW_MS) &&
                CORRECTION_KEYWORDS.any { cleanedInput.contains(it) }

        if (isCorrectionTrigger) {
            val lastApp = lastInteraction?.matchedAppName ?: "ऐप"
            val lastAct = lastInteraction?.actionType ?: "टास्क"
            val log4 = "LAYER4_CORRECTION: Triggered by user ('$cleanedInput') within 15s -> Reverted last action '$lastAct' ($lastApp) & asking 'Aapko kya karna tha?'"
            val log1 = "LAYER1_CONTEXT: Skipped due to Self-Correction trigger"
            val log2 = "LAYER2_CONFIDENCE: 100% (Correction Mode) -> Immediate action cancel"
            val log3 = "LAYER3_INTENT: INTENT_SELF_CORRECTION"

            Log.i(TAG, log1)
            Log.i(TAG, log2)
            Log.i(TAG, log3)
            Log.i(TAG, log4)

            val dummyBase = CommandRoutingDecision(
                rawCommand = rawCommand,
                cleanedCommand = cleanedInput,
                category = CommandCategory.OFFLINE_TASK,
                matchedAppName = lastInteraction?.matchedAppName,
                matchedAppPackage = null,
                remainingInstruction = null,
                actionSummary = "SELF_CORRECTION_CANCEL: Reverting last action ($lastAct)"
            )

            return IntelligenceDecision(
                baseRoutingDecision = dummyBase,
                isSelfCorrection = true,
                correctionMessageHindi = "माफ़ कीजिये, पिछला एक्शन ${lastApp} के लिए रीवर्ट कर दिया गया है। आपको क्या करना था?",
                confidenceScore = 100,
                confidenceLevel = "HIGH",
                resolvedQuery = cleanedInput,
                activeContextApp = activeAppName,
                logLayer1 = log1,
                logLayer2 = log2,
                logLayer3 = log3,
                logLayer4 = log4
            )
        }

        val log4 = "LAYER4_CORRECTION: None (Normal command flow)"

        // -----------------------------------------------------------------------------------------
        // LAYER 1 — CONTEXT AWARENESS & PRONOUN RESOLUTION
        // -----------------------------------------------------------------------------------------
        var resolvedQuery = cleanedInput
        var detectedPronoun: String? = null
        var needsClarification = false
        var clarificationPrompt: String? = null

        for (p in PRONOUNS) {
            if (cleanedInput.contains(Regex("(?i)\\b${Regex.escape(p)}\\b"))) {
                detectedPronoun = p
                break
            }
        }

        var log1: String
        if (detectedPronoun != null) {
            val installedApps = GenericAppLauncher.getInstalledLaunchableApps(context)
            val hasExplicitAppName = installedApps.any { app ->
                cleanedInput.contains(app.appName, ignoreCase = true) || cleanedInput.contains(app.normalizedName, ignoreCase = true)
            }

            if (!hasExplicitAppName) {
                if (!activeAppName.isNullOrBlank()) {
                    resolvedQuery = "$activeAppName $cleanedInput".replace(detectedPronoun, "").replace(Regex("\\s+"), " ").trim()
                    log1 = "LAYER1_CONTEXT: Pronoun '$detectedPronoun' resolved using active app context '$activeAppName' -> '$resolvedQuery'"
                } else {
                    needsClarification = true
                    clarificationPrompt = "किसका मतलब है? कृपया ऐप या विषय का नाम बताएं।"
                    log1 = "LAYER1_CONTEXT: Pronoun '$detectedPronoun' detected but no active app context -> Asking user for clarification"
                }
            } else {
                log1 = "LAYER1_CONTEXT: Pronoun '$detectedPronoun' present but explicit app name found in query"
            }
        } else {
            log1 = "LAYER1_CONTEXT: No pronoun detected. Active app context: '${activeAppName ?: "None"}', Last action: '${lastActionType ?: "None"}'"
        }

        // -----------------------------------------------------------------------------------------
        // BASE STAGE 1-5 ROUTING (COMMAND ROUTER ENGINE)
        // -----------------------------------------------------------------------------------------
        val baseDecision = CommandRouter.routeCommand(context, resolvedQuery)

        // -----------------------------------------------------------------------------------------
        // LAYER 3 — NATURAL LANGUAGE FLEXIBILITY (SYNONYM & INTENT MAPPING)
        // -----------------------------------------------------------------------------------------
        val matchedVerb = OPEN_SYNONYMS.firstOrNull { cleanedInput.contains(it) } ?: "standard"
        val matchedTarget = baseDecision.matchedAppName ?: baseDecision.remainingInstruction ?: "System"
        val log3 = "LAYER3_INTENT: Matched Intent '${baseDecision.category}' with target '$matchedTarget' (Verb synonym: '$matchedVerb')"

        // -----------------------------------------------------------------------------------------
        // LAYER 2 — CONFIDENCE-BASED CONFIRMATION
        // -----------------------------------------------------------------------------------------
        val isHardwareOrNav = isHardwareToggleOrNav(cleanedInput)
        var confidenceScore = 90
        var confidenceLevel = "HIGH"
        var needsConfirmation = false
        var confirmationPrompt: String? = null
        val log2: String

        if (isHardwareOrNav) {
            confidenceScore = 100
            confidenceLevel = "HIGH"
            log2 = "LAYER2_CONFIDENCE: 100% (Hardware/System Toggle) -> Confirmation skipped, direct execution"
        } else if (baseDecision.matchedAppName != null) {
            confidenceScore = 95
            confidenceLevel = "HIGH"
            needsConfirmation = false
            confirmationPrompt = null
            log2 = "LAYER2_CONFIDENCE: 95% (High App Match: '${baseDecision.matchedAppName}') -> Confirmation SKIPPED, direct execution"
        } else if (baseDecision.category == CommandCategory.CONVERSATION || baseDecision.category == CommandCategory.SCREEN_TASK) {
            confidenceScore = 88
            confidenceLevel = "HIGH"
            log2 = "LAYER2_CONFIDENCE: 88% (High Confidence ${baseDecision.category}) -> Direct execution"
        } else {
            confidenceScore = 30
            confidenceLevel = "LOW"
            log2 = "LAYER2_CONFIDENCE: 30% (Low Confidence Match) -> Unclear request"
        }

        Log.i(TAG, log1)
        Log.i(TAG, log2)
        Log.i(TAG, log3)
        Log.i(TAG, log4)

        return IntelligenceDecision(
            baseRoutingDecision = baseDecision,
            isSelfCorrection = false,
            needsContextClarification = needsClarification,
            clarificationPromptHindi = clarificationPrompt,
            needsUserConfirmation = needsConfirmation,
            confirmationPromptHindi = confirmationPrompt,
            confidenceScore = confidenceScore,
            confidenceLevel = confidenceLevel,
            resolvedQuery = resolvedQuery,
            activeContextApp = activeAppName,
            logLayer1 = log1,
            logLayer2 = log2,
            logLayer3 = log3,
            logLayer4 = log4
        )
    }

    private fun extractSpokenCandidate(cleanedInput: String, matchedAppName: String): String {
        var text = cleanedInput
        for (syn in OPEN_SYNONYMS) text = text.replace(syn, "")
        text = text.replace("app", "").replace("ko", "").replace("pe", "").replace("par", "").trim()
        return if (text.isNotBlank()) text else cleanedInput
    }

    private fun isExactOrAliasMatch(spoken: String, matchedAppName: String, context: Context): Boolean {
        val sNorm = GenericAppLauncher.normalizeString(spoken)
        val mNorm = GenericAppLauncher.normalizeString(matchedAppName)
        if (sNorm == mNorm || sNorm.contains(mNorm) || mNorm.contains(sNorm)) return true

        // Well-known exact aliases (e.g. yt -> youtube, fb -> facebook, insta -> instagram, wa -> whatsapp)
        val commonAliases = mapOf(
            "yt" to "youtube", "youtube" to "youtube", "यूट्यूब" to "youtube",
            "fb" to "facebook", "facebook" to "facebook", "फेसबुक" to "facebook",
            "insta" to "instagram", "instagram" to "instagram", "इंस्टाग्राम" to "instagram",
            "wa" to "whatsapp", "whatsapp" to "whatsapp", "व्हाट्सएप" to "whatsapp",
            "chrome" to "chrome", "क्रोम" to "chrome", "camera" to "camera", "कैमरा" to "camera"
        )

        val mappedS = commonAliases[sNorm] ?: sNorm
        val mappedM = commonAliases[mNorm] ?: mNorm
        return mappedS == mappedM
    }

    private fun isHardwareToggleOrNav(query: String): Boolean {
        val lower = query.lowercase()
        return lower.contains("wifi") || lower.contains("वाईफाई") ||
                lower.contains("bluetooth") || lower.contains("ब्लूटूथ") ||
                lower.contains("torch") || lower.contains("flashlight") || lower.contains("टॉर्च") ||
                lower.contains("volume") || lower.contains("वॉल्यूम") || lower.contains("आवाज") ||
                lower.contains("lock") || lower.contains("लॉक") || lower.contains("home") ||
                lower.contains("back") || lower.contains("siren") || lower.contains("alarm")
    }
}
