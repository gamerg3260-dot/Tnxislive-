package com.example.data.local

import android.util.Log

data class ExtractedPreference(
    val key: String,
    val value: String,
    val category: String = UserMemoryEntity.CATEGORY_PREFERENCE,
    val descriptionHindi: String,
    val acknowledgementHindi: String
)

object MemoryManager {
    private const val TAG = "MemoryManager"

    /**
     * Inspects voice command to detect if user is explicitly stating a preference, habit, or identity.
     * When detected, returns ExtractedPreference to store in local Room DB without requiring cloud storage.
     */
    fun extractUserPreference(command: String): ExtractedPreference? {
        val trimmed = command.trim()
        val lower = trimmed.lowercase()

        // 1. "Mera favorite/pasandida app [X] hai"
        val favoriteAppRegex = Regex(
            "(?:mera|meri)\\s+(?:favorite|favourite|pasandida|pasandeeda|मनपसंद)\\s+(?:app|application|ऐप)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+hai|\\s+h)?$",
            RegexOption.IGNORE_CASE
        )
        favoriteAppRegex.find(lower)?.let { match ->
            val appName = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            if (appName.isNotBlank()) {
                return ExtractedPreference(
                    key = "favorite_app",
                    value = appName,
                    category = UserMemoryEntity.CATEGORY_PREFERENCE,
                    descriptionHindi = "पसंदीदा ऐप: $appName",
                    acknowledgementHindi = "मैंने याद रख लिया है कि आपका पसंदीदा ऐप $appName है।"
                )
            }
        }

        // 2. "Mera favorite/pasandida [singer/actor/youtuber/game] [X] hai"
        val favoriteThingRegex = Regex(
            "(?:mera|meri)\\s+(?:favorite|favourite|pasandida|pasandeeda|मनपसंद)\\s+([a-zA-Z0-9\\s]+?)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+hai|\\s+h)?$",
            RegexOption.IGNORE_CASE
        )
        favoriteThingRegex.find(lower)?.let { match ->
            val domain = match.groupValues[1].trim()
            val target = match.groupValues[2].trim().replaceFirstChar { it.uppercase() }
            if (domain.isNotBlank() && target.isNotBlank() && !domain.contains("naam") && !domain.contains("name")) {
                val cleanKey = "favorite_${domain.replace(" ", "_")}"
                return ExtractedPreference(
                    key = cleanKey,
                    value = target,
                    category = UserMemoryEntity.CATEGORY_PREFERENCE,
                    descriptionHindi = "पसंदीदा $domain: $target",
                    acknowledgementHindi = "मैंने याद रख लिया कि आपका पसंदीदा $domain $target है।"
                )
            }
        }

        // 3. "Mera naam [X] hai" / "Yaad rakhna mera naam [X] hai"
        val nameRegex = Regex(
            "(?:yaad rakhna|remember)?.*?(?:mera naam|my name is)\\s+([a-zA-Z]+)(?:\\s+hai|\\s+h)?$",
            RegexOption.IGNORE_CASE
        )
        nameRegex.find(lower)?.let { match ->
            val name = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            if (name.isNotBlank() && name.lowercase() !in listOf("max", "kya", "assistant")) {
                return ExtractedPreference(
                    key = "user_name",
                    value = name,
                    category = UserMemoryEntity.CATEGORY_IDENTITY,
                    descriptionHindi = "यूज़र का नाम: $name",
                    acknowledgementHindi = "नमस्ते $name जी! मैंने आपका नाम याद रख लिया है।"
                )
            }
        }

        // 4. "Mujhe [X] pasand hai / pasand hain / pasand lagta hai"
        val preferencePattern = Regex(
            "(?:mujhe|humko|hamein|i like|i love)\\s+(.+?)\\s+(?:pasand\\s+hai|pasand\\s+hain|pasand\\s+h|bhata\\s+hai|achha\\s+lagta\\s+hai|accha\\s+lagta\\s+hai)",
            RegexOption.IGNORE_CASE
        )
        preferencePattern.find(lower)?.let { match ->
            val preferenceValue = match.groupValues[1].trim()
            if (preferenceValue.isNotBlank() && preferenceValue.length > 2) {
                val cleanKey = when {
                    preferenceValue.contains("gaane") || preferenceValue.contains("song") || preferenceValue.contains("music") -> "music_preference"
                    preferenceValue.contains("video") || preferenceValue.contains("movie") || preferenceValue.contains("film") -> "video_preference"
                    preferenceValue.contains("app") -> "favorite_app"
                    preferenceValue.contains("dark") || preferenceValue.contains("theme") -> "theme_preference"
                    else -> "preference_${preferenceValue.take(15).replace(" ", "_")}"
                }
                val formattedVal = preferenceValue.replaceFirstChar { it.uppercase() }
                return ExtractedPreference(
                    key = cleanKey,
                    value = formattedVal,
                    category = UserMemoryEntity.CATEGORY_PREFERENCE,
                    descriptionHindi = "यूज़र की पसंद: $formattedVal",
                    acknowledgementHindi = "बिल्कुल! मैंने याद रख लिया है कि आपको $formattedVal पसंद है।"
                )
            }
        }

        // 5. "Yaad rakhna [ki] [X]" / "Note kar lo [X]"
        val rememberPattern = Regex(
            "(?:yaad rakhna|yaad rakho|note kar lo|remember that|remember)\\s+(?:ki\\s+)?(.+)",
            RegexOption.IGNORE_CASE
        )
        rememberPattern.find(lower)?.let { match ->
            val rememberNote = match.groupValues[1].trim()
            if (rememberNote.isNotBlank() && rememberNote.length > 3) {
                val cleanKey = "user_note_${System.currentTimeMillis() % 10000}"
                val formatted = rememberNote.replaceFirstChar { it.uppercase() }
                return ExtractedPreference(
                    key = cleanKey,
                    value = formatted,
                    category = UserMemoryEntity.CATEGORY_PREFERENCE,
                    descriptionHindi = "याद रखी गई बात: $formatted",
                    acknowledgementHindi = "जी, मैंने यह बात याद रख ली है: $formatted"
                )
            }
        }

        return null
    }

    /**
     * Determines whether user command refers to an ambiguous pronoun or previous context:
     * e.g., "iska volume badhao", "ise like karo", "wahi wala fir se chalao", "isko roko"
     */
    fun hasContextualReference(command: String): Boolean {
        val lower = command.lowercase().trim()
        val contextKeywords = listOf(
            "iska", "iske", "iski", "ise", "isko", "use", "uski", "usko",
            "wahi", "wahi wala", "wahi wali", "fir se", "phir se", "again",
            "previous wala", "pichhla", "pichla", "same", "yeh wala", "ye wala"
        )
        return contextKeywords.any { kw ->
            lower.contains(Regex("\\b$kw\\b"))
        }
    }

    /**
     * Resolves contextual command using active screen context or recent history.
     * E.g., If user says "wahi wala fir se chalao" and recent history has "CarryMinati video",
     * returns the resolved search/play query.
     */
    fun resolveContextualQuery(
        command: String,
        recentHistory: List<CommandHistoryEntity>,
        activeActivityContext: String?
    ): String {
        val lower = command.lowercase().trim()

        if (lower.contains("wahi") || lower.contains("phir se") || lower.contains("fir se") || lower.contains("again")) {
            // Find last media query from recent history
            val lastMedia = recentHistory.firstOrNull {
                it.actionType in listOf("TYPE", "TAP", "OPEN_APP") && it.userPrompt.isNotBlank() &&
                        !it.userPrompt.contains("volume", ignoreCase = true)
            }
            if (lastMedia != null) {
                Log.d(TAG, "Resolved 'wahi wala' to previous command: ${lastMedia.userPrompt}")
                return lastMedia.userPrompt
            }
        }

        return command
    }

    /**
     * Pruning and Optimization: Keeps database lightweight, clean, and fast.
     * - Retains only the most recent 40-50 commands.
     * - Removes old ephemeral activity contexts older than 3 hours.
     * - Limits transient memories while permanently protecting user preferences & identity.
     */
    suspend fun pruneAndOptimize(dao: MaxDao) {
        try {
            // 1. Keep history table bounded
            dao.pruneHistory(limit = 40)

            // 2. Clear activity contexts older than 3 hours (10800000 ms)
            val threeHoursAgo = System.currentTimeMillis() - (3 * 60 * 60 * 1000L)
            dao.pruneOldActivityContext(threeHoursAgo)

            // 3. Keep only top 15 transient memories (leaves all preferences untouched)
            dao.pruneTransientMemories(keepLimit = 15)

            Log.d(TAG, "Memory and history optimization completed successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Error optimizing memory: ${e.localizedMessage}")
        }
    }
}
