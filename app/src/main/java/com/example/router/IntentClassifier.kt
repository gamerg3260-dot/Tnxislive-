package com.example.router

import android.util.Log

enum class InputCategory {
    TASK_COMMAND,        // Hardware toggles, app launch, camera, reminders, locks, calls, scrolling/tap
    CONVERSATION_CHAT,   // Greetings, Q&A, explanations, jokes, casual talk, thoughts/advice
    AMBIGUOUS            // Needs AI classification or contextual fallback
}

data class IntentClassificationResult(
    val category: InputCategory,
    val confidence: Float,
    val reason: String
)

/**
 * Smart Intent Classifier that distinguishes between Hardware/Task Actions
 * and Conversational Questions/Chit-chat with zero latency.
 */
class IntentClassifier {

    private val tag = "IntentClassifier"

    fun classify(rawInput: String): IntentClassificationResult {
        val query = rawInput.trim().lowercase()
        if (query.isBlank()) {
            return IntentClassificationResult(InputCategory.CONVERSATION_CHAT, 1.0f, "Empty query")
        }

        // 1. Check strong conversational questions and greetings first
        val isPureConversation = isConversationalQuery(query)
        val isActionTask = isActionCommand(query)

        // Clear conversational winner
        if (isPureConversation && !isActionTask) {
            Log.d(tag, "Classified as CONVERSATION_CHAT via local heuristics: '$rawInput'")
            return IntentClassificationResult(
                category = InputCategory.CONVERSATION_CHAT,
                confidence = 0.95f,
                reason = "वार्तालाप/सवाल (Greetings, Q&A, Explanations or Chit-Chat)"
            )
        }

        // Clear task winner
        if (isActionTask && !isPureConversation) {
            Log.d(tag, "Classified as TASK_COMMAND via local heuristics: '$rawInput'")
            return IntentClassificationResult(
                category = InputCategory.TASK_COMMAND,
                confidence = 0.95f,
                reason = "टास्क/हार्डवेयर कमांड (Action Verbs / System Toggles / Apps)"
            )
        }

        // If both or neither match, resolve nuances
        if (isPureConversation && isActionTask) {
            // E.g. "samjhao ki wifi kaise on karte hain" -> Conversation explaining something
            if (query.contains("samjhao") || query.contains("batao ki") || query.contains("explain") ||
                query.contains("kyun") || query.contains("kaise hota") || query.contains("kaise kaam karta") ||
                query.contains("kya hota") || query.contains("kya hai") || query.contains("what is") || query.contains("how to")
            ) {
                return IntentClassificationResult(
                    category = InputCategory.CONVERSATION_CHAT,
                    confidence = 0.85f,
                    reason = "स्पष्टीकरण/सिखाने का सवाल (Explanation Query)"
                )
            }
            return IntentClassificationResult(
                category = InputCategory.TASK_COMMAND,
                confidence = 0.80f,
                reason = "टास्क प्राथमिकता (Action priority)"
            )
        }

        // Default heuristic: If query starts with question words (kya, kaun, kahan, kab, kaise, kyun) or asks a question -> Conversation
        if (startsWithQuestionWord(query) || query.endsWith("?") || query.contains("meaning") || query.contains("arth")) {
            return IntentClassificationResult(
                category = InputCategory.CONVERSATION_CHAT,
                confidence = 0.88f,
                reason = "प्रश्नवाचक वाक्य (Question pattern)"
            )
        }

        // Ambiguous -> can be resolved with Gemini or fallback to Task
        return IntentClassificationResult(
            category = InputCategory.AMBIGUOUS,
            confidence = 0.50f,
            reason = "अस्पष्ट (Ambiguous - evaluating context)"
        )
    }

    private fun isConversationalQuery(query: String): Boolean {
        // 1. Identity & Creator
        if (query.contains("tum kaun ho") || query.contains("who are you") ||
            query.contains("tumhe kisne banaya") || query.contains("who created you") ||
            query.contains("who made you") || query.contains("kisne banaya") ||
            query.contains("ganesh sahani") || query.contains("गणेश साहनी") ||
            query.contains("apna naam batao") || query.contains("tumhara naam kya") ||
            query.contains("what is your name")
        ) {
            return true
        }

        // 2. Greetings & How are you
        if (query.contains("kaise ho") || query.contains("kaisa hai") || query.contains("how are you") ||
            query.contains("kya haal hai") || query.contains("kya chal raha") || query.contains("what's up") ||
            query.contains("namaste") || query.contains("नमस्ते") || query.contains("pranam") || query.contains("प्रणाम") ||
            query.contains("good morning") || query.contains("good afternoon") || query.contains("good evening") ||
            query.contains("good night") || query.contains("shubh ratri") || query.contains("शुभ रात्रि") ||
            query == "hello" || query == "hi" || query == "hey" || query == "हेलो" || query == "हाय" ||
            query.contains("dhanyawad") || query.contains("shukriya") || query.contains("thank you") || query.contains("thanks")
        ) {
            return true
        }

        // 3. Entertainment & Casual Chitchat
        if (query.contains("joke") || query.contains("जोक") || query.contains("chutkula") || query.contains("चुटकुला") ||
            query.contains("shayari") || query.contains("शायरी") || query.contains("kahani") || query.contains("कहानी") ||
            query.contains("kavita") || query.contains("कविता") || query.contains("baat karo") || query.contains("talk to me") ||
            query.contains("bore ho raha") || query.contains("man nahi lag raha") || query.contains("socho to")
        ) {
            return true
        }

        // 4. Questions & Explanations (What is, Why, How, Explain)
        if (query.contains("kya hota hai") || query.contains("kya hoti hai") || query.contains("kya hote hain") ||
            query.contains("what is ") || query.contains("meaning of") || query.contains("ka matlab kya") ||
            query.contains("samjhao") || query.contains("explain") || query.contains("samjha do") ||
            query.contains("kyun hota") || query.contains("kyun hai") || query.contains("why is") ||
            query.contains("kaise kaam karta") || query.contains("how does") || query.contains("kaise banta") ||
            query.contains("batao ki") || query.contains("tell me about") || query.contains("kiske bare me") ||
            query.contains("kitna hota hai") || query.contains("calculate") || query.contains("plus") || query.contains("minus") ||
            query.contains("capital of") || query.contains("rajdhani kya hai") || query.contains("pradhan mantri") ||
            query.contains("soch ke batao") || query.contains("kya lagta hai")
        ) {
            return true
        }

        return false
    }

    private fun isActionCommand(query: String): Boolean {
        // 1. Hardware Toggles & Settings
        if (query.contains("wifi") || query.contains("wi-fi") || query.contains("वाईफाई") || query.contains("वाई-फ़ाई") ||
            query.contains("bluetooth") || query.contains("ब्लूटूथ") ||
            query.contains("torch") || query.contains("flashlight") || query.contains("टॉर्च") || query.contains("फ्लैशलाइट") ||
            query.contains("airplane") || query.contains("flight mode") || query.contains("एरोप्लेन") ||
            query.contains("mobile data") || query.contains("इंटरनेट") || query.contains("डेटा") ||
            query.contains("hotspot") || query.contains("हॉटस्पॉट") ||
            query.contains("volume") || query.contains("वॉल्यूम") || query.contains("आवाज") || query.contains("sound") ||
            query.contains("brightness") || query.contains("ब्राइटनेस") || query.contains("रोशनी") ||
            query.contains("dnd") || query.contains("do not disturb") || query.contains("डीएनडी")
        ) {
            // If it contains action words like on, off, badhao, kam karo -> definite task
            if (hasActionVerb(query)) return true
        }

        // 2. Lock & Security
        if (query.contains("lock") || query.contains("लॉक") || query.contains("siren") || query.contains("साइरन") ||
            query.contains("anti theft") || query.contains("एंटी थेफ्ट") || query.contains("emergency contact")
        ) {
            return true
        }

        // 3. App Launchers & Navigation
        if (query.contains("kholo") || query.contains("khol do") || query.contains("open") || query.contains("launch") ||
            query.contains("chalao") || query.contains("play") || query.contains("bajao") ||
            query.contains("scroll") || query.contains("स्क्रॉल") || query.contains("neeche karo") || query.contains("upar karo") ||
            query.contains("back") || query.contains("बैक") || query.contains("home") || query.contains("होम") || query.contains("recent apps")
        ) {
            return true
        }

        // 4. Camera & Selfie
        if (query.contains("selfie") || query.contains("सेल्फी") ||
            (query.contains("photo") || query.contains("फोटो") || query.contains("pic")) &&
            (query.contains("kheencho") || query.contains("le lo") || query.contains("lo") || query.contains("click") || query.contains("take"))
        ) {
            return true
        }

        // 5. Reminders & Alarms
        if (query.contains("reminder") || query.contains("रिमाइंडर") || query.contains("alarm") || query.contains("अलार्म") ||
            query.contains("yaad dilana") || query.contains("yaad dilao") || query.contains("baje")
        ) {
            return true
        }

        // 6. Calls & Dialing
        if (query.contains("call karo") || query.contains("call lagao") || query.contains("फोन मिलाओ") ||
            query.contains("phone lagao") || query.contains("utha lo") || query.contains("kaat do") || query.contains("disconnect")
        ) {
            return true
        }

        // 7. General Action Verbs
        return hasActionVerb(query)
    }

    private fun hasActionVerb(query: String): Boolean {
        return query.contains("on karo") || query.contains("on kar do") || query.contains("on kardo") || query.contains("chalu karo") || query.contains("chalu kar do") ||
                query.contains("off karo") || query.contains("off kar do") || query.contains("off kardo") || query.contains("band karo") || query.contains("band kar do") ||
                query.contains("badhao") || query.contains("badha do") || query.contains("kam karo") || query.contains("kam kar do") || query.contains("ghatao") ||
                query.contains("lagao") || query.contains("laga do") || query.contains("hatao") || query.contains("hata do") ||
                query.contains("bhejo") || query.contains("bhej do") || query.contains("set karo") || query.contains("set kar do") ||
                query.contains("skip karo") || query.contains("skip kar do") || query.contains("tap karo") || query.contains("click karo") ||
                query.contains("type karo") || query.contains("search karo") || query.contains("likho")
    }

    private fun startsWithQuestionWord(query: String): Boolean {
        val qWords = listOf("kya", "kyun", "kaise", "kaun", "kahan", "kab", "kitna", "kitne", "what", "why", "how", "who", "where", "when", "which")
        return qWords.any { query.startsWith("$it ") || query == it }
    }
}
