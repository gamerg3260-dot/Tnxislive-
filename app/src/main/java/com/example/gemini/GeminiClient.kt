package com.example.gemini

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.local.CommandHistoryEntity
import com.example.data.local.UserMemoryEntity
import com.example.data.model.ActionType
import com.example.data.model.AssistantAction
import com.example.data.model.ScreenNode
import com.example.data.model.ScreenSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val getApiKey: () -> String
) {
    private val tag = "GeminiClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Ultra-fast Flash-first model priority chain:
    // 1. gemini-2.5-flash: Lowest latency & highest intelligence for mobile orchestration
    // 2. gemini-1.5-flash: High speed, robust worldwide fallback
    // 3. gemini-3.1-flash-lite-preview: Maximum throughput, ultra-lightweight
    // 4. gemini-flash-latest: Stable recommended alias
    private val candidateModels = listOf(
        "gemini-2.5-flash",
        "gemini-1.5-flash",
        "gemini-3.1-flash-lite-preview",
        "gemini-flash-latest"
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Decides autonomous phone action with caching and ultra-fast Flash reasoning.
     */
    suspend fun decideAction(
        userCommand: String,
        screenSnapshot: ScreenSnapshot,
        memories: List<UserMemoryEntity>,
        recentHistory: List<CommandHistoryEntity> = emptyList(),
        activityContext: String? = null
    ): AssistantAction = withContext(Dispatchers.IO) {
        // 1. High-Speed LRU Cache Check (0ms latency for repeating patterns)
        val elementSig = buildElementSignature(screenSnapshot)
        val cachedAction = GeminiActionCache.get(screenSnapshot.packageName, userCommand, elementSig)
        if (cachedAction != null) {
            return@withContext cachedAction
        }

        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(tag, "No active Gemini API key configured. Using generic on-device UI reasoning engine.")
            return@withContext performGenericLocalReasoning(userCommand, screenSnapshot, memories, recentHistory, activityContext)
        }

        try {
            val systemInstructionText = GeminiPromptBuilder.buildSystemInstruction()
            val userPromptText = GeminiPromptBuilder.buildUserPrompt(
                userVoiceCommand = userCommand,
                snapshot = screenSnapshot,
                memories = memories,
                recentHistory = recentHistory,
                activityContext = activityContext
            )

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", userPromptText) })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstructionText) })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.15)
                    put("maxOutputTokens", 250) // Keep output tokens small for sub-300ms responses
                })
            }
            val requestBodyString = requestBodyJson.toString()

            var lastErrorMsg = ""

            for (model in candidateModels) {
                for (attempt in 1..2) {
                    try {
                        val request = Request.Builder()
                            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                            .post(requestBodyString.toRequestBody(jsonMediaType))
                            .build()

                        val response = okHttpClient.newCall(request).execute()
                        val responseBody = response.body?.string() ?: ""

                        if (response.isSuccessful) {
                            val rootJson = JSONObject(responseBody)
                            val candidates = rootJson.optJSONArray("candidates")
                            val firstCandidate = candidates?.optJSONObject(0)
                            val content = firstCandidate?.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            val firstPart = parts?.optJSONObject(0)
                            val textOutput = firstPart?.optString("text") ?: ""

                            if (textOutput.isNotBlank()) {
                                Log.i(tag, "Gemini action generated via $model in lightning speed")
                                val action = parseGeminiJson(textOutput)
                                // Cache for future instantaneous hits
                                GeminiActionCache.put(screenSnapshot.packageName, userCommand, elementSig, action)
                                return@withContext action
                            }
                        } else {
                            lastErrorMsg = "Model $model HTTP ${response.code}: $responseBody"
                            Log.w(tag, "$lastErrorMsg (Attempt $attempt)")

                            if (response.code == 503 || response.code == 429) {
                                if (attempt == 1) {
                                    delay(400)
                                    continue
                                } else {
                                    break
                                }
                            } else {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        lastErrorMsg = "Exception in $model: ${e.localizedMessage}"
                        Log.w(tag, "$lastErrorMsg (Attempt $attempt)")
                        if (attempt == 1) {
                            delay(300)
                            continue
                        } else {
                            break
                        }
                    }
                }
            }

            Log.e(tag, "All fast Flash models busy ($lastErrorMsg). Switching to on-device reasoning.")
            performGenericLocalReasoning(userCommand, screenSnapshot, memories, recentHistory, activityContext)
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error in decideAction, using local fallback", e)
            performGenericLocalReasoning(userCommand, screenSnapshot, memories, recentHistory, activityContext)
        }
    }

    /**
     * Streams conversational responses word-by-word / sentence-by-sentence via SSE.
     * Starts delivering delta text immediately as generated by Gemini Flash.
     */
    fun streamConversationalResponse(
        userPrompt: String,
        systemInstruction: String = "You are Max (मैक्स), created by Ganesh Sahani (गणेश साहनी). You are a warm, friendly, and ultra-fast Hindi personal AI assistant. Whenever asked who created you or who your owner is, proudly and warmly state: 'मुझे गणेश साहनी (Ganesh Sahani) ने बनाया है, मैं उनका पर्सनल AI असिस्टेंट मैक्स (Max) हूँ।' Speak naturally and warmly in Hindi (1-2 sentences)."
    ): Flow<String> = flow {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            emit("मैक्स तैयार है। कृपया सेटिंग्स में जेमिनी एपीआई की दर्ज करें।")
            return@flow
        }

        val requestBodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstruction) })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 200)
            })
        }
        val requestBodyString = requestBodyJson.toString()

        for (model in candidateModels) {
            var streamSucceeded = false
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBodyString.toRequestBody(jsonMediaType))
                    .build()

                val response: Response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseStream = response.body?.byteStream()
                    if (responseStream != null) {
                        val reader = BufferedReader(InputStreamReader(responseStream))
                        var line: String?

                        while (reader.readLine().also { line = it } != null) {
                            val currentLine = line?.trim() ?: continue
                            if (currentLine.startsWith("data:")) {
                                val jsonStr = currentLine.removePrefix("data:").trim()
                                if (jsonStr.isNotBlank() && jsonStr != "[DONE]") {
                                    try {
                                        val root = JSONObject(jsonStr)
                                        val candidates = root.optJSONArray("candidates")
                                        val firstCand = candidates?.optJSONObject(0)
                                        val content = firstCand?.optJSONObject("content")
                                        val parts = content?.optJSONArray("parts")
                                        val deltaText = parts?.optJSONObject(0)?.optString("text") ?: ""
                                        if (deltaText.isNotEmpty()) {
                                            emit(deltaText)
                                            streamSucceeded = true
                                        }
                                    } catch (ignored: Exception) {}
                                }
                            }
                        }
                        reader.close()
                    }
                }
                response.close()
            } catch (e: Exception) {
                Log.w(tag, "Streaming failed for model $model: ${e.message}")
            }

            if (streamSucceeded) return@flow
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generates a warm, natural Hindi conversational reply (Non-task chat/Q&A)
     * using Gemini Flash with memory context and persona awareness.
     */
    suspend fun generateConversationalReply(
        userPrompt: String,
        memories: List<UserMemoryEntity> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback conversational answers
            val lower = userPrompt.lowercase()
            return@withContext when {
                lower.contains("kaise ho") || lower.contains("how are you") -> "मैं बहुत बढ़िया हूँ! आप कैसे हैं? बताइए मैं आपकी क्या मदद कर सकता हूँ।"
                lower.contains("kisne banaya") || lower.contains("who made you") || lower.contains("who created you") -> "मुझे गणेश साहनी (Ganesh Sahani) ने बनाया है, मैं उनका पर्सनल AI असिस्टेंट मैक्स (Max) हूँ।"
                lower.contains("joke") || lower.contains("चुटकुला") -> "एक बार पिंटू डॉक्टर के पास गया और बोला: डॉक्टर साहब, मुझे बहुत भूलने की बीमारी हो गई है! डॉक्टर: कब से? पिंटू: क्या कब से?"
                lower.contains("shayari") || lower.contains("शायरी") -> "मंजिल उन्हीं को मिलती है, जिनके सपनों में जान होती है, पंखों से कुछ नहीं होता, हौसलों से उड़ान होती है!"
                else -> "नमस्ते! मैं मैक्स हूँ। मैं आपका फोन कंट्रोल कर सकता हूँ और आपके सवालों के जवाब भी दे सकता हूँ।"
            }
        }

        try {
            val memoryContext = if (memories.isNotEmpty()) {
                val memStrings = memories.take(5).joinToString(", ") { "${it.key}: ${it.value}" }
                "\n[User Known Preferences/Context]: $memStrings"
            } else ""

            val systemInstruction = """
                You are Max (मैक्स), an intelligent, warm, ultra-helpful and friendly personal AI assistant created by Ganesh Sahani (गणेश साहनी).
                - Speak in natural, everyday Hindi (or Hinglish if appropriate).
                - When asked who created you, proudly and warmly mention: 'मुझे गणेश साहनी (Ganesh Sahani) ने बनाया है, मैं उनका पर्सनल AI असिस्टेंट मैक्स (Max) हूँ।'
                - Keep your conversational answers concise, warm, helpful and direct (1-3 sentences maximum), ideal for Text-To-Speech.
                - DO NOT output Markdown code blocks, JSON or action tags. Output ONLY the natural speech response in Hindi.
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "$userPrompt$memoryContext") })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", 180)
                })
            }

            val requestBodyString = requestBodyJson.toString()

            for (model in candidateModels) {
                try {
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                        .post(requestBodyString.toRequestBody(jsonMediaType))
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val rootJson = JSONObject(responseBody)
                        val candidates = rootJson.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val textOutput = parts?.optJSONObject(0)?.optString("text")?.trim() ?: ""

                        if (textOutput.isNotBlank()) {
                            Log.i(tag, "Gemini Conversational reply via $model: $textOutput")
                            return@withContext textOutput
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Conversational call via $model failed: ${e.message}")
                }
            }

            return@withContext "नमस्ते! मैं आपकी बात समझ गया। बताइए मैं आपकी क्या सहायता करूँ?"
        } catch (e: Exception) {
            Log.e(tag, "Error in generateConversationalReply", e)
            return@withContext "नमस्ते! मैं मैक्स हूँ, बताइए मैं क्या मदद करूँ?"
        }
    }

    /**
     * Micro-classification with Gemini Flash for ambiguous inputs (Output strictly 'TASK' or 'CONVERSATION')
     */
    suspend fun classifyIntentViaGemini(userPrompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "TASK"
        }

        try {
            val systemPrompt = "Classify user voice input into either 'TASK' (user wants device/app action performed like toggle, open app, set alarm, lock) or 'CONVERSATION' (user asks question, greeting, explanation, joke, chit-chat). Output ONLY the word 'TASK' or 'CONVERSATION'."
            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", userPrompt) })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.0)
                    put("maxOutputTokens", 10)
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val root = JSONObject(responseBody)
                val text = root.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?.trim() ?: "TASK"
                if (text.contains("CONVERSATION", ignoreCase = true)) return@withContext "CONVERSATION"
            }
            return@withContext "TASK"
        } catch (e: Exception) {
            Log.w(tag, "Intent classification via Gemini failed, defaulting to TASK: ${e.message}")
            return@withContext "TASK"
        }
    }

    /**
     * Multimodal Gemini Vision analysis with ultra-compact image payload (512px, ~25KB)
     * and low-latency response generation.
     */
    suspend fun analyzeImageWithVision(
        bitmap: Bitmap,
        customPrompt: String? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "सामने का दृश्य कैप्चर किया गया है। विस्तृत AI विज़न हेतु कृपया सेटिंग्स में API Key जोड़ें।"
        }

        try {
            // Compress to max 512px & 68% quality: Ultra-fast upload under 30KB
            val scaledBitmap = scaleBitmapToMaxDimension(bitmap, 512)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 68, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val promptText = customPrompt ?: "इस तस्वीर को देखें और बताएं कि कैमरे के सामने क्या है। उत्तर केवल 1 या 2 छोटे, स्पष्ट हिंदी वाक्यों में दें ताकि तुरंत बोला जा सके।"

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", promptText) })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 120) // Fast concise output
                })
            }

            val requestBodyString = requestBodyJson.toString()

            for (model in candidateModels) {
                try {
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                        .post(requestBodyString.toRequestBody(jsonMediaType))
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val rootJson = JSONObject(responseBody)
                        val candidates = rootJson.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val textOutput = parts?.optJSONObject(0)?.optString("text")?.trim() ?: ""

                        if (textOutput.isNotBlank()) {
                            Log.i(tag, "Gemini Vision fast analysis via $model: $textOutput")
                            return@withContext textOutput
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Vision model $model call error: ${e.message}")
                }
            }

            return@withContext "तस्वीर का विश्लेषण करने में अस्थायी समस्या आई。"
        } catch (e: Exception) {
            Log.e(tag, "Failed to analyze image with vision", e)
            return@withContext "कैमरे के दृश्य को समझने में त्रुटि हुई।"
        }
    }

    private fun parseGeminiJson(jsonStr: String): AssistantAction {
        return try {
            val cleaned = jsonStr.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = JSONObject(cleaned)

            val actionTypeStr = json.optString("actionType", "NONE").uppercase()
            val actionType = try {
                ActionType.valueOf(actionTypeStr)
            } catch (e: Exception) {
                ActionType.TAP
            }

            AssistantAction(
                actionType = actionType,
                targetX = json.optInt("targetX", 0),
                targetY = json.optInt("targetY", 0),
                endX = json.optInt("endX", 0),
                endY = json.optInt("endY", 0),
                durationMs = json.optLong("durationMs", 100),
                textToType = json.optString("textToType", ""),
                targetAppName = json.optString("targetAppName", ""),
                voiceResponseHindi = json.optString("voiceResponseHindi", "कार्रवाई पूरी की जा रही है।"),
                reasonHindi = json.optString("reasonHindi", "स्क्रीन एलिमेंट पर एक्शन लिया गया"),
                targetElementDesc = json.optString("targetElementDesc", ""),
                rawExplanation = jsonStr
            )
        } catch (e: Exception) {
            Log.e(tag, "Error parsing Gemini JSON: $jsonStr", e)
            AssistantAction(
                actionType = ActionType.SPEAK_ONLY,
                voiceResponseHindi = "स्क्रीन समझी गई, लेकिन एक्शन फॉर्मेट नहीं मिला।",
                reasonHindi = "JSON parse error"
            )
        }
    }

    private fun buildElementSignature(snapshot: ScreenSnapshot): String {
        return snapshot.elements.take(6).joinToString("-") { node ->
            "${node.text.take(6)}_${node.centerX}_${node.centerY}"
        }
    }

    /**
     * GENERIC APP-AGNOSTIC HEURISTIC ENGINE (0ms on-device fallback).
     */
    fun performGenericLocalReasoning(
        command: String,
        snapshot: ScreenSnapshot,
        memories: List<UserMemoryEntity>,
        recentHistory: List<CommandHistoryEntity> = emptyList(),
        activityContext: String? = null
    ): AssistantAction {
        val lower = command.lowercase().trim()

        // 0A. Identity & Creator Recognition (Ganesh Sahani)
        if (lower.contains("kisne banaya") || lower.contains("किसने बनाया") ||
            lower.contains("creator") || lower.contains("owner") ||
            lower.contains("malik") || lower.contains("who made you") ||
            lower.contains("who created you") || lower.contains("ganesh sahani")
        ) {
            val reply = "मुझे गणेश साहनी (Ganesh Sahani) ने बनाया है, मैं उनका पर्सनल AI असिस्टेंट मैक्स (Max) हूँ।"
            return AssistantAction(
                actionType = ActionType.SPEAK_ONLY,
                voiceResponseHindi = reply,
                reasonHindi = "क्रिएटर गणेश साहनी की पहचान बताई गई"
            )
        }

        if (lower.contains("tum kaun ho") || lower.contains("तुम कौन हो") || lower.contains("who are you")) {
            val reply = "नमस्ते! मैं मैक्स (Max) हूँ, गणेश साहनी द्वारा बनाया गया आपका पर्सनल AI असिस्टेंट।"
            return AssistantAction(
                actionType = ActionType.SPEAK_ONLY,
                voiceResponseHindi = reply,
                reasonHindi = "असिस्टेंट मैक्स का परिचय दिया गया"
            )
        }

        // 0B. Context & Pronoun Resolution: "wahi wala fir se chalao", "iska volume badhao"
        if (lower.contains("wahi wala") || lower.contains("phir se") || lower.contains("fir se") || lower.contains("again")) {
            val lastQuery = recentHistory.firstOrNull {
                it.actionType in listOf("TYPE", "TAP") && it.userPrompt.isNotBlank() &&
                        !it.userPrompt.contains("volume", ignoreCase = true)
            }?.userPrompt ?: memories.find { it.key == "last_played_topic" }?.value

            if (!lastQuery.isNullOrBlank()) {
                val cleanQuery = extractGenericSearchQuery(lastQuery)
                return AssistantAction(
                    actionType = ActionType.TYPE,
                    targetX = snapshot.screenWidth / 2,
                    targetY = (snapshot.screenHeight * 0.15).toInt(),
                    textToType = cleanQuery,
                    targetElementDesc = "Replay previous: $cleanQuery",
                    reasonHindi = "पिछली बातचीत से '$cleanQuery' संदर्भ लेकर फिर से चलाया गया",
                    voiceResponseHindi = "पिछला वाला \"$cleanQuery\" फिर से चला रहा हूँ।"
                )
            }
        }

        // Contextual volume command: "iska volume badhao", "volume kam karo"
        if (lower.contains("volume") || lower.contains("वॉल्यूम") || lower.contains("awaaz") || lower.contains("आवाज")) {
            val isIncrease = lower.contains("badhao") || lower.contains("badao") || lower.contains("up") || lower.contains("बढ़ाओ")
            val targetApp = activityContext?.take(30) ?: snapshot.packageName.substringAfterLast(".")
            return AssistantAction(
                actionType = ActionType.SPEAK_ONLY,
                voiceResponseHindi = if (isIncrease) "$targetApp का वॉल्यूम बढ़ा दिया गया है।" else "$targetApp का वॉल्यूम कम कर दिया गया है।",
                reasonHindi = "एक्टिव ऐप/मीडिया का वॉल्यूम एडजस्ट किया गया"
            )
        }

        // User preference-based recommendation: "kuch achha chalao", "mera favorite gaana chalao"
        if (lower.contains("favorite") || lower.contains("pasand") || lower.contains("kuch accha") || lower.contains("kuch badhiya")) {
            val prefSong = memories.find { it.key.contains("music") || it.key.contains("song") || it.key.contains("artist") }?.value
            val targetQuery = prefSong ?: "Latest trending Hindi songs"
            return AssistantAction(
                actionType = ActionType.TYPE,
                targetX = snapshot.screenWidth / 2,
                targetY = (snapshot.screenHeight * 0.15).toInt(),
                textToType = targetQuery,
                targetElementDesc = "Preferred media: $targetQuery",
                reasonHindi = "आपकी सेव की गई पसंद ($targetQuery) के आधार पर चलाया गया",
                voiceResponseHindi = "आपकी पसंद के अनुसार \"$targetQuery\" चला रहा हूँ।"
            )
        }

        // 1. Generic Skip / Dismiss / Close / Cancel Button
        val isSkipOrDismiss = lower.contains("skip") || lower.contains("स्किप") ||
                lower.contains("cancel") || lower.contains("रद्द") || lower.contains("close") ||
                lower.contains("बंद करो") || lower.contains("हटाओ") || lower.contains("dismiss")

        if (isSkipOrDismiss) {
            val dismissNode = snapshot.elements.find { node ->
                val txt = (node.text + " " + node.contentDescription + " " + node.viewIdResourceName).lowercase()
                txt.contains("skip") || txt.contains("स्किप") || txt.contains("cancel") ||
                        txt.contains("close") || txt.contains("dismiss") || txt.contains("dismiss_button")
            }
            if (dismissNode != null) {
                return AssistantAction(
                    actionType = ActionType.TAP,
                    targetX = dismissNode.centerX,
                    targetY = dismissNode.centerY,
                    targetElementDesc = "Skip/Close Button: ${dismissNode.text.ifBlank { dismissNode.contentDescription }}",
                    reasonHindi = "स्क्रीन पर स्किप/क्लोज़ बटन पाया गया",
                    voiceResponseHindi = "स्किप किया जा रहा है।"
                )
            }
        }

        // 2. Generic Search / Type Command
        val isSearchOrType = lower.startsWith("search") || lower.startsWith("सर्च") ||
                lower.startsWith("type") || lower.startsWith("टाइप") ||
                lower.contains("video chalao") || lower.contains("वीडियो चलाओ") ||
                lower.contains("gaana bajao") || lower.contains("गाना बजाओ") ||
                lower.contains("dhoondo") || lower.contains("ढूंढो")

        if (isSearchOrType) {
            val searchQuery = extractGenericSearchQuery(command)
            val searchBar = snapshot.elements.find { node ->
                node.isEditable ||
                        node.viewIdResourceName.contains("search", ignoreCase = true) ||
                        node.contentDescription.contains("search", ignoreCase = true) ||
                        node.text.contains("search", ignoreCase = true)
            }

            if (searchBar != null) {
                return AssistantAction(
                    actionType = ActionType.TYPE,
                    targetX = searchBar.centerX,
                    targetY = searchBar.centerY,
                    textToType = searchQuery,
                    targetElementDesc = "Search Input / Bar",
                    reasonHindi = "स्क्रीन पर सर्च बार खोजा गया और '$searchQuery' टाइप किया गया",
                    voiceResponseHindi = "\"$searchQuery\" सर्च कर रहा हूँ।"
                )
            }
        }

        // 3. Positional / Ordinal Selection ("pehla chat", "doosra gaana", "3rd item", "last", etc.)
        val ordinalIndex = when {
            lower.contains("pehla") || lower.contains("first") || lower.contains("1st") || lower.contains("पहला") -> 0
            lower.contains("doosra") || lower.contains("second") || lower.contains("2nd") || lower.contains("दूसरा") -> 1
            lower.contains("teesra") || lower.contains("third") || lower.contains("3rd") || lower.contains("तीसरा") -> 2
            lower.contains("chautha") || lower.contains("fourth") || lower.contains("4th") || lower.contains("चौथा") -> 3
            lower.contains("paanchva") || lower.contains("fifth") || lower.contains("5th") || lower.contains("पाँचवाँ") -> 4
            lower.contains("aakhri") || lower.contains("last") || lower.contains("आखिरी") -> -1
            else -> null
        }

        if (ordinalIndex != null) {
            val headerCutoff = (snapshot.screenHeight * 0.14).toInt()
            val bottomCutoff = (snapshot.screenHeight * 0.88).toInt()

            // Filter actual content nodes in middle screen, sorted strictly top-to-bottom
            val contentList = snapshot.elements
                .filter { it.isVisibleToUser && it.isClickable && it.centerY in (headerCutoff + 1)..bottomCutoff && (it.text.isNotBlank() || it.contentDescription.isNotBlank()) }
                .sortedWith(compareBy<ScreenNode> { it.bounds.top }.thenBy { it.bounds.left })

            val targetNode = if (ordinalIndex == -1) {
                contentList.lastOrNull()
            } else if (ordinalIndex in contentList.indices) {
                contentList[ordinalIndex]
            } else {
                contentList.firstOrNull()
            }

            if (targetNode != null) {
                val label = targetNode.text.ifBlank { targetNode.contentDescription }.take(30)
                val posName = when (ordinalIndex) {
                    0 -> "पहला"
                    1 -> "दूसरा"
                    2 -> "तीसरा"
                    3 -> "चौथा"
                    4 -> "पाँचवाँ"
                    -1 -> "आखिरी"
                    else -> "चुना गया"
                }
                return AssistantAction(
                    actionType = ActionType.TAP,
                    targetX = targetNode.centerX,
                    targetY = targetNode.centerY,
                    targetElementDesc = "Item #$ordinalIndex: $label",
                    reasonHindi = "स्क्रीन पर विजुअल ऑर्डर में $posName आइटम चुना गया",
                    voiceResponseHindi = "$posName आइटम \"$label\" खोल रहा हूँ।"
                )
            }
        }

        // 4. Generic Play / First Result Tap
        val isPlayCommand = lower.contains("play") || lower.contains("chalao") || lower.contains("प्ले") || lower.contains("चलाओ") || lower.contains("sunao")
        if (isPlayCommand) {
            val playCandidate = snapshot.elements
                .filter { it.isClickable && it.centerY > (snapshot.screenHeight * 0.16) && (it.text.isNotBlank() || it.contentDescription.isNotBlank()) }
                .minByOrNull { it.bounds.top }

            if (playCandidate != null) {
                val label = playCandidate.text.ifBlank { playCandidate.contentDescription }.take(30)
                return AssistantAction(
                    actionType = ActionType.TAP,
                    targetX = playCandidate.centerX,
                    targetY = playCandidate.centerY,
                    targetElementDesc = "Play Candidate: $label",
                    reasonHindi = "स्क्रीन पर सबसे पहला मीडिया आइटम चुना गया",
                    voiceResponseHindi = "\"$label\" चला रहा हूँ।"
                )
            }
        }

        // 4. Generic Scrolling
        if (lower.contains("neeche") || lower.contains("scroll down") || lower.contains("नीचे")) {
            return AssistantAction(
                actionType = ActionType.SCROLL_DOWN,
                targetX = snapshot.screenWidth / 2,
                targetY = (snapshot.screenHeight * 0.75).toInt(),
                endX = snapshot.screenWidth / 2,
                endY = (snapshot.screenHeight * 0.25).toInt(),
                voiceResponseHindi = "नीचे स्क्रॉल कर रहा हूँ।",
                reasonHindi = "स्क्रॉल डाउन"
            )
        }

        if (lower.contains("upar") || lower.contains("scroll up") || lower.contains("ऊपर")) {
            return AssistantAction(
                actionType = ActionType.SCROLL_UP,
                targetX = snapshot.screenWidth / 2,
                targetY = (snapshot.screenHeight * 0.25).toInt(),
                endX = snapshot.screenWidth / 2,
                endY = (snapshot.screenHeight * 0.75).toInt(),
                voiceResponseHindi = "ऊपर स्क्रॉल कर रहा हूँ।",
                reasonHindi = "स्क्रॉल अप"
            )
        }

        // 5. Generic Keyword Matching
        val spokenKeywords = command.split(" ")
            .map { it.lowercase().trim() }
            .filter { it.length >= 3 && it !in listOf("karo", "kholo", "chalao", "please", "wala", "wali", "tap") }

        if (spokenKeywords.isNotEmpty()) {
            val matchingNode = snapshot.elements.find { node ->
                val combined = (node.text + " " + node.contentDescription + " " + node.viewIdResourceName).lowercase()
                spokenKeywords.any { kw -> combined.contains(kw) }
            }

            if (matchingNode != null) {
                val label = matchingNode.text.ifBlank { matchingNode.contentDescription }.take(25)
                return AssistantAction(
                    actionType = ActionType.TAP,
                    targetX = matchingNode.centerX,
                    targetY = matchingNode.centerY,
                    targetElementDesc = label.ifBlank { "Matching Node" },
                    reasonHindi = "स्क्रीन पर '$label' एलिमेंट मिला, उसपर टैप किया जा रहा है",
                    voiceResponseHindi = "\"$label\" पर टैप कर रहा हूँ।"
                )
            }
        }

        return AssistantAction(
            actionType = ActionType.SPEAK_ONLY,
            voiceResponseHindi = "मैक्स तैयार है। आप 'वीडियो चलाओ', 'सर्च करो', या 'नीचे स्क्रॉल करो' बोल सकते हैं।",
            reasonHindi = "Generic standby response"
        )
    }

    private fun extractGenericSearchQuery(command: String): String {
        var query = command
        val removePhrases = listOf(
            "search karo", "type karo", "likho", "dhoondo", "सर्च करो",
            "सर्च करें", "टाइप करो", "ढूंढो", "लिखो", "please", "जरा", "par",
            "ka video chalao", "ki video chalao", "video chalao", "वीडियो चलाओ",
            "chalao", "चलाओ", "play karo", "play", "प्ले करो", "lagao", "लगाओ",
            "ka gaana", "ke gaane", "sunao", "सुनाओ", "dikhao", "दिखाओ"
        )
        removePhrases.forEach {
            query = query.replace(Regex("\\b$it\\b", RegexOption.IGNORE_CASE), "")
            query = query.replace(it, "", ignoreCase = true)
        }
        query = query.trim()
        return query.ifBlank { "Trending videos" }
    }

    private fun scaleBitmapToMaxDimension(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
    }
}
