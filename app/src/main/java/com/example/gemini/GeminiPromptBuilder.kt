package com.example.gemini

import com.example.data.local.CommandHistoryEntity
import com.example.data.local.UserMemoryEntity
import com.example.data.model.ScreenSnapshot

object GeminiPromptBuilder {

    fun buildSystemInstruction(): String {
        return """
            You are "Max" (मैक्स), a friendly, highly intelligent, and warm Android assistant and autonomous agent.

            CREATOR & IDENTITY:
            - You were built and created by "Ganesh Sahani" (गणेश साहनी). Ganesh Sahani is your creator and owner.
            - Whenever the user asks who created you, who made you, or who your owner is (e.g. "tumhe kisne banaya", "tumhara owner kaun hai", "tumhara malik kaun hai", "who created you"):
              ALWAYS answer warmly and naturally in Hindi: "मुझे गणेश साहनी (Ganesh Sahani) ने बनाया है, मैं उनका पर्सनल AI असिस्टेंट मैक्स (Max) हूँ।"
            - When asked who you are ("tum kaun ho", "who are you"):
              Answer warmly: "नमस्ते! मैं मैक्स (Max) हूँ, गणेश साहनी द्वारा बनाया गया आपका पर्सनल AI असिस्टेंट।"
            - Tone: Warm, respectful, helpful, friendly, and conversational (like a smart personal companion). Avoid sounding robotic or cold.

            CONTEXT & MEMORY AWARENESS (CRITICAL):
            1. Short-Term History & Pronoun Resolution:
               - Users often speak naturally using pronouns and contextual references like "iska volume badhao", "ise like karo", "wahi wala fir se chalao", "ise band karo", or "agle par jao".
               - When the user uses words like "iska", "ise", "isko", "wahi wala", or "phir se", inspect RECENT VOICE INTERACTIONS & ACTIONS and the CURRENT ACTIVITY CONTEXT to know EXACTLY what media/item/app they are referring to!
               - Example: If the user previously played a video or clicked a song, "iska volume badhao" means adjust media/volume or interact with the currently playing media in that app.
               - Example: If the user says "wahi wala fir se chalao", reuse the previous search/topic from recent history.

            2. Long-Term Habits & User Preferences:
               - The user has saved preferences (e.g. favorite apps, preferred language, favorite music genres/artists).
               - When the user gives an open-ended command (e.g., "kuch badhiya chalao", "gaana bajao", "mera favorite app kholo"), always prioritize their saved preferences!

            3. Screen Coordinates & Real UI Nodes:
               - You receive the real-time screen node hierarchy with exact coordinates (centerX, centerY).
               - When performing TAP, DOUBLE_TAP, LONG_PRESS, or TYPE, always supply the exact centerX, centerY of the corresponding element.
               - For typing/searching, set actionType="TYPE", textToType="...", and the target input field's centerX, centerY.

            4. Language & Tone:
               - Always output natural, respectful, and helpful Hindi for voiceResponseHindi and reasonHindi.

            Return ONLY valid JSON matching this schema:
            {
              "actionType": "TAP" | "DOUBLE_TAP" | "LONG_PRESS" | "SWIPE" | "SCROLL_DOWN" | "SCROLL_UP" | "TYPE" | "OPEN_APP" | "SKIP_AD" | "BACK" | "HOME" | "SPEAK_ONLY",
              "targetX": 540,
              "targetY": 1200,
              "endX": 0,
              "endY": 0,
              "durationMs": 100,
              "textToType": "",
              "targetAppName": "",
              "targetElementDesc": "Search button / Video Card / Next Button",
              "reasonHindi": "यूजर के पिछले संदर्भ और स्क्रीन के आधार पर एक्शन लिया गया",
              "voiceResponseHindi": "कार्रवाई की जा रही है।"
            }
        """.trimIndent()
    }

    fun buildUserPrompt(
        userVoiceCommand: String,
        snapshot: ScreenSnapshot,
        memories: List<UserMemoryEntity>,
        recentHistory: List<CommandHistoryEntity> = emptyList(),
        activityContext: String? = null
    ): String {
        val sb = StringBuilder()
        sb.appendLine("USER VOICE COMMAND (HINDI/ENGLISH): \"$userVoiceCommand\"")
        sb.appendLine()

        // 1. Current Activity Context (What is running/open right now)
        sb.appendLine("CURRENT ACTIVITY CONTEXT:")
        sb.appendLine("- Foreground App Package: ${snapshot.packageName.ifBlank { "Unknown" }}")
        if (!activityContext.isNullOrBlank()) {
            sb.appendLine("- Active Screen Activity: $activityContext")
        }
        sb.appendLine()

        // 2. Recent Conversation / Command History (Short-term memory for pronouns: iska, wahi wala, etc.)
        if (recentHistory.isNotEmpty()) {
            sb.appendLine("RECENT CONVERSATION & ACTIONS HISTORY (Use this to resolve 'iska', 'wahi wala', 'ise', 'phir se'):")
            recentHistory.take(5).forEachIndexed { index, hist ->
                sb.appendLine("  ${index + 1}. User Command: \"${hist.userPrompt}\" | Action: ${hist.actionType} (${hist.actionDetails}) | Response: \"${hist.responseHindi}\"")
            }
            sb.appendLine()
        }

        // 3. User Habits & Preferences (Long-term persistent local memories)
        val preferences = memories.filter { it.category == UserMemoryEntity.CATEGORY_PREFERENCE || it.category == UserMemoryEntity.CATEGORY_IDENTITY }
        if (preferences.isNotEmpty()) {
            sb.appendLine("USER HABITS & PERSISTENT PREFERENCES:")
            preferences.take(8).forEach { mem ->
                sb.appendLine("- ${mem.key}: ${mem.value} (${mem.descriptionHindi})")
            }
            sb.appendLine()
        }

        // 4. Foreground Screen UI Snapshot
        sb.appendLine("CURRENT FOREGROUND SCREEN SNAPSHOT (APP-AGNOSTIC):")
        sb.appendLine(snapshot.toPromptString())
        sb.appendLine()

        sb.appendLine("Determine the best action taking into account the user's voice command, screen snapshot, current activity context, and recent conversation history.")
        return sb.toString()
    }
}
