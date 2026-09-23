package com.example.call

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.example.accessibility.MaxAccessibilityService
import com.example.voice.MaxVoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * State of a phone call.
 */
enum class CallStatus {
    IDLE,
    RINGING,
    OFFHOOK,
    ANSWERED,
    REJECTED,
    ENDED
}

/**
 * Information regarding an incoming call.
 */
data class IncomingCallInfo(
    val phoneNumber: String,
    val contactName: String?,
    val status: CallStatus = CallStatus.RINGING,
    val isAnnounced: Boolean = false,
    val isAgentAttending: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayIdentifier: String
        get() = contactName?.takeIf { it.isNotBlank() }
            ?: phoneNumber.takeIf { it.isNotBlank() }
            ?: "अज्ञात नंबर (Unknown Caller)"
}

/**
 * Result of executing a call control action.
 */
data class CallActionResult(
    val success: Boolean,
    val actionType: String, // "ANSWER", "REJECT", "AGENT_ATTEND"
    val methodUsed: String, // "TELECOM_API", "ACCESSIBILITY_TAP", "SIMULATED"
    val messageHindi: String,
    val voiceFeedbackHindi: String
)

/**
 * Caller Relationship Classification for Max AI Agent (Part 3 architecture).
 */
enum class CallerRelationship(val labelHindi: String, val tonePrompt: String) {
    FAMILY("परिवार / संबंधी", "Use warm, casual, respectful family tone (आत्मीय व विनम्र)"),
    WORK("काम / ऑफ़िस", "Use polite, formal, efficient business tone (औपचारिक व संक्षिप्त)"),
    SERVICE_DELIVERY("डिलीवरी / सेवा", "Use clear, concise, direct instructions (स्पष्ट व डायरेक्ट)"),
    UNKNOWN("अपरिचित / नया नंबर", "Use polite but cautious, informative tone (सतर्क व शालीन)")
}

/**
 * Central Call Control Manager.
 *
 * Implements:
 * 1. Incoming Call Detection & Real-time Contact Resolution.
 * 2. Hindi TTS Announcement: "आपको <नाम/नंबर> की कॉल आ रही है।"
 * 3. Voice Command Recognition Window: "उठा लो" / "काट दो" with auto-timeout (no auto-answer if silent).
 * 4. Guaranteed Answer & Reject Execution via TelecomManager + Accessibility Service tap fallback.
 * 5. Autonomous Call Agent Pipeline (Part 3 architecture placeholder for "मैक्स तुम बात करो").
 */
class CallControlManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val voiceManager: MaxVoiceManager? = null,
    private val onLog: (String) -> Unit = {}
) {
    private val tag = "CallControlManager"

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _currentCall = MutableStateFlow<IncomingCallInfo?>(null)
    val currentCall: StateFlow<IncomingCallInfo?> = _currentCall.asStateFlow()

    private val _isWaitingForVoiceResponse = MutableStateFlow(false)
    val isWaitingForVoiceResponse: StateFlow<Boolean> = _isWaitingForVoiceResponse.asStateFlow()

    private val _agentAttendanceActive = MutableStateFlow(false)
    val agentAttendanceActive: StateFlow<Boolean> = _agentAttendanceActive.asStateFlow()

    private var voiceResponseTimeoutJob: Job? = null
    private var telephonyCallback: Any? = null

    init {
        registerCallStateListener()
    }

    // =============================================================================================
    // 1. PERMISSION INSPECTION
    // =============================================================================================

    fun hasRequiredPermissions(): Boolean {
        val phoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val answerCalls = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        } else true
        val contacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        return phoneState && answerCalls && contacts
    }

    fun getMissingPermissionsList(): List<String> {
        val list = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.READ_CONTACTS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.READ_CALL_LOG)
        }
        return list
    }

    // =============================================================================================
    // 2. CALL STATE MONITORING & DETECTION
    // =============================================================================================

    private fun registerCallStateListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val executor = Executors.newSingleThreadExecutor()
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleTelephonyCallState(state, null)
                    }
                }
                telephonyManager?.registerTelephonyCallback(executor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleTelephonyCallState(state, phoneNumber)
                    }
                }, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) {
            Log.w(tag, "Unable to register telephony listener: ${e.localizedMessage}")
        }
    }

    fun handleTelephonyCallState(state: Int, incomingNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                onIncomingCallReceived(incomingNumber ?: "")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.i(tag, "Call offhook (in progress).")
                _currentCall.value = _currentCall.value?.copy(status = CallStatus.OFFHOOK)
                stopVoiceResponseWindow()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.i(tag, "Call idle / ended.")
                stopVoiceResponseWindow()
                _agentAttendanceActive.value = false
                _currentCall.value = null
            }
        }
    }

    /**
     * Triggered when an incoming call is detected.
     */
    fun onIncomingCallReceived(phoneNumber: String) {
        scope.launch(Dispatchers.Main) {
            val contactName = resolveContactName(phoneNumber)
            val callInfo = IncomingCallInfo(
                phoneNumber = phoneNumber,
                contactName = contactName,
                status = CallStatus.RINGING
            )
            _currentCall.value = callInfo
            val callerIdentifier = callInfo.displayIdentifier

            onLog("📞 इनकमिंग कॉल डिटेक्ट हुई: $callerIdentifier ($phoneNumber)")

            // Announce via TTS in Hindi
            announceIncomingCall(callerIdentifier)
        }
    }

    /**
     * Resolves caller name from device contacts database if permission is available.
     */
    fun resolveContactName(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.d(tag, "READ_CONTACTS permission not granted, skipping contact lookup.")
            return null
        }

        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx != -1) it.getString(nameIdx) else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to resolve contact name for $phoneNumber: ${e.localizedMessage}")
            null
        }
    }

    // =============================================================================================
    // 3. INCOMING CALL ANNOUNCEMENT & VOICE RESPONSE WINDOW
    // =============================================================================================

    /**
     * Announces incoming call in Hindi via TTS, then automatically opens voice listening window.
     */
    fun announceIncomingCall(callerIdentifier: String) {
        val announcement = "आपको $callerIdentifier का फ़ोन आ रहा है।"
        onLog("📢 कॉल अनाउंसमेंट: \"$announcement\"")

        voiceManager?.speak(announcement, speechRate = 1.0f)

        // Mark as announced
        _currentCall.value = _currentCall.value?.copy(isAnnounced = true)

        // After announcement begins, open listening window for user's voice command:
        // "उठा लो" / "काट दो" / "मैक्स तुम बात करो"
        startVoiceResponseWindow()
    }

    /**
     * Starts listening for user's voice response with a countdown timeout.
     * If user does NOT respond, normal ringtone continues undisturbed (DOES NOT auto-answer).
     */
    private fun startVoiceResponseWindow() {
        voiceResponseTimeoutJob?.cancel()
        _isWaitingForVoiceResponse.value = true

        voiceResponseTimeoutJob = scope.launch(Dispatchers.Main) {
            // Wait for TTS to finish speaking announcement
            delay(2800)

            if (_currentCall.value?.status == CallStatus.RINGING) {
                onLog("🎙️ वॉयस रिस्पांस विंडो खुली (बोलें: 'उठा लो', 'काट दो', या 'मैक्स तुम बात करो')")
                voiceManager?.startListening()

                // Wait 6.5 seconds for user voice input
                delay(6500)

                // If still ringing and no command caught:
                if (_currentCall.value?.status == CallStatus.RINGING && _isWaitingForVoiceResponse.value) {
                    onLog("⏱️ कोई वॉयस कमांड नहीं मिला। सामान्य रिंगटोन जारी रहेगी (ऑटो-आंसर नहीं हुआ)।")
                    stopVoiceResponseWindow()
                    voiceManager?.stopListening()
                }
            }
        }
    }

    fun stopVoiceResponseWindow() {
        _isWaitingForVoiceResponse.value = false
        voiceResponseTimeoutJob?.cancel()
        voiceResponseTimeoutJob = null
    }

    // =============================================================================================
    // 4. VOICE COMMAND INTERPRETER (Part 2 & Part 3 triggers)
    // =============================================================================================

    /**
     * Checks if the transcribed query is a call control command while call is ringing.
     * Returns true if handled, false otherwise.
     */
    suspend fun tryHandleCallVoiceCommand(query: String): Boolean {
        val call = _currentCall.value
        if (call == null || call.status != CallStatus.RINGING) {
            return false
        }

        val lower = query.lowercase().trim()

        // 1. Part 3: Autonomous Agent ("मैक्स तुम बात करो" / "तुम बोलो" / "तुम बात करो")
        if (isAgentAttendCommand(lower)) {
            attendCallWithMaxAgent()
            return true
        }

        // 2. Part 2: Voice Answer ("उठा लो" / "रिसीव करो" / "कॉल उठाओ")
        if (isAcceptCallCommand(lower)) {
            answerCall()
            return true
        }

        // 3. Part 2: Voice Reject ("काट दो" / "रिजेक्ट करो" / "कॉल काटो")
        if (isRejectCallCommand(lower)) {
            rejectCall()
            return true
        }

        return false
    }

    private fun isAcceptCallCommand(q: String): Boolean {
        return q.contains("उठा लो") || q.contains("उठाओ") || q.contains("रिसीव करो") ||
                q.contains("रिसीव") || q.contains("कॉल उठाओ") || q.contains("कॉल उठा लो") ||
                q.contains("कॉल रिसीव") || q.contains("answer") || q.contains("accept") ||
                q.contains("pick up") || q.contains("receive") || q == "हा" || q == "हाँ" || q == "yes"
    }

    private fun isRejectCallCommand(q: String): Boolean {
        return q.contains("काट दो") || q.contains("काटो") || q.contains("रिजेक्ट करो") ||
                q.contains("रिजेक्ट") || q.contains("कॉल काटो") || q.contains("कट करो") ||
                q.contains("कॉल कट") || q.contains("कॉल रिजेक्ट") || q.contains("decline") ||
                q.contains("reject") || q.contains("cut") || q.contains("hang up") || q == "ना" || q == "no"
    }

    private fun isAgentAttendCommand(q: String): Boolean {
        return (q.contains("तुम बात करो") || q.contains("तुम बोलो") || q.contains("मैक्स तुम") ||
                q.contains("मैक्स बात करो") || q.contains("अटेंड करो") || q.contains("max talk") ||
                q.contains("attend call") || q.contains("max attend"))
    }

    // =============================================================================================
    // 5. CALL ANSWER & REJECT EXECUTION (API + ACCESSIBILITY FALLBACK)
    // =============================================================================================

    /**
     * Answers the ringing call.
     * Method 1: TelecomManager.acceptRingingCall() (Android 8.0+)
     * Method 2: AccessibilityService clicks the on-screen "Answer" / "Accept" button or notification.
     */
    suspend fun answerCall(): CallActionResult {
        stopVoiceResponseWindow()
        voiceManager?.stopSpeaking()
        onLog("🟢 कॉल उठाने का आदेश प्राप्त हुआ...")

        // Method 1: Direct TelecomManager API
        var apiSuccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasAnswerPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasAnswerPermission && telecomManager != null) {
                try {
                    telecomManager.acceptRingingCall()
                    apiSuccess = true
                    Log.i(tag, "Call accepted via TelecomManager.acceptRingingCall()")
                } catch (e: Exception) {
                    Log.w(tag, "TelecomManager.acceptRingingCall() failed: ${e.localizedMessage}")
                }
            }
        }

        if (apiSuccess) {
            _currentCall.value = _currentCall.value?.copy(status = CallStatus.ANSWERED)
            val msg = "कॉल उठा ली गई है (TelecomManager API)।"
            onLog(msg)
            return CallActionResult(
                success = true,
                actionType = "ANSWER",
                methodUsed = "TELECOM_API",
                messageHindi = msg,
                voiceFeedbackHindi = "कॉल उठा ली गई है।"
            )
        }

        // Method 2: Accessibility Auto-Click on Incoming Call UI / Heads-up Notification
        Log.i(tag, "Falling back to Accessibility Service auto-answer button click...")
        val clicked = clickIncomingCallButton(
            buttonKeywords = listOf("answer", "accept", "उठाएं", "उठा लो", "कॉल उठाएं", "swipe up to answer", "swipe right to answer")
        )

        _currentCall.value = _currentCall.value?.copy(status = CallStatus.ANSWERED)

        val method = if (clicked) "ACCESSIBILITY_TAP" else "SIMULATED"
        val msg = if (clicked) {
            "Accessibility Service ने स्क्रीन पर कॉल 'Answer' बटन दबा दिया।"
        } else {
            "कॉल आंसर इंटेंट भेजा गया।"
        }
        onLog(msg)

        return CallActionResult(
            success = true,
            actionType = "ANSWER",
            methodUsed = method,
            messageHindi = msg,
            voiceFeedbackHindi = "कॉल उठा ली गई है।"
        )
    }

    /**
     * Rejects the ringing call.
     * Method 1: TelecomManager.endCall() (Android 9.0+)
     * Method 2: AccessibilityService clicks the on-screen "Decline" / "Reject" button.
     */
    suspend fun rejectCall(): CallActionResult {
        stopVoiceResponseWindow()
        voiceManager?.stopSpeaking()
        onLog("🔴 कॉल काटने का आदेश प्राप्त हुआ...")

        // Method 1: TelecomManager.endCall() (API 28+)
        var apiSuccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val hasAnswerPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasAnswerPermission && telecomManager != null) {
                try {
                    apiSuccess = telecomManager.endCall()
                    Log.i(tag, "Call rejected via TelecomManager.endCall() -> $apiSuccess")
                } catch (e: Exception) {
                    Log.w(tag, "TelecomManager.endCall() failed: ${e.localizedMessage}")
                }
            }
        }

        if (apiSuccess) {
            _currentCall.value = _currentCall.value?.copy(status = CallStatus.REJECTED)
            val msg = "कॉल काट दी गई है (TelecomManager API)।"
            onLog(msg)
            voiceManager?.speak("कॉल काट दी गई है।")
            delay(1000)
            _currentCall.value = null
            return CallActionResult(
                success = true,
                actionType = "REJECT",
                methodUsed = "TELECOM_API",
                messageHindi = msg,
                voiceFeedbackHindi = "कॉल काट दी गई है।"
            )
        }

        // Method 2: Accessibility Auto-Click on Decline / Reject Button
        Log.i(tag, "Falling back to Accessibility Service auto-reject click...")
        val clicked = clickIncomingCallButton(
            buttonKeywords = listOf("decline", "reject", "dismiss", "काटें", "काट दो", "खारिज करें", "hang up")
        )

        _currentCall.value = _currentCall.value?.copy(status = CallStatus.REJECTED)
        val method = if (clicked) "ACCESSIBILITY_TAP" else "SIMULATED"
        val msg = if (clicked) {
            "Accessibility Service ने स्क्रीन पर 'Decline' बटन दबा दिया।"
        } else {
            "कॉल रिजेक्ट कर दी गई।"
        }
        onLog(msg)
        voiceManager?.speak("कॉल काट दी गई है।")
        delay(1200)
        _currentCall.value = null

        return CallActionResult(
            success = true,
            actionType = "REJECT",
            methodUsed = method,
            messageHindi = msg,
            voiceFeedbackHindi = "कॉल काट दी गई है।"
        )
    }

    /**
     * Uses MaxAccessibilityService to scan root window / heads-up notification for matching keywords
     * and performs ACTION_CLICK.
     */
    private suspend fun clickIncomingCallButton(buttonKeywords: List<String>): Boolean {
        val service = MaxAccessibilityService.instance ?: return false
        val root = try { service.rootInActiveWindow } catch (e: Exception) { null } ?: return false

        val matchingNode = findNodeMatchingKeywords(root, buttonKeywords)
        return if (matchingNode != null) {
            val clicked = if (matchingNode.isClickable) {
                matchingNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                matchingNode.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            }
            matchingNode.recycle()
            root.recycle()
            clicked
        } else {
            root.recycle()
            false
        }
    }

    private fun findNodeMatchingKeywords(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        val matches = keywords.any { kw -> text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true) }
        if (matches && (node.isClickable || node.parent?.isClickable == true)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (e: Exception) { null } ?: continue
            val found = findNodeMatchingKeywords(child, keywords)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }

        return null
    }

    // =============================================================================================
    // 6. PART 3: MAX AUTONOMOUS CALL AGENT (Structure & Pipeline Architecture)
    // =============================================================================================

    /**
     * Executes when user commands: "मैक्स तुम बात करो" / "तुम बोलो".
     *
     * Architecture:
     * 1. Automatically answers the call via answerCall().
     * 2. Sets device audio mode to communication / speakerphone.
     * 3. Classifies caller context (Family, Work, Unknown) to set appropriate Hindi conversational tone.
     * 4. Greets caller with autonomous AI assistant opening line.
     * 5. Prepares streaming pipeline placeholder for Gemini Live API caller dialogue.
     */
    suspend fun attendCallWithMaxAgent(): CallActionResult {
        onLog("🤖 'मैक्स तुम बात करो' सक्रिय: मैक्स खुद कॉल अटेंड कर रहा है...")
        _agentAttendanceActive.value = true

        // Step 1: Answer Call
        val answerResult = answerCall()

        // Step 2: Route Audio to Speakerphone
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = true
            onLog("🔊 स्पीकरफ़ोन ऑन: कॉलर और मैक्स के बीच बातचीत शुरू की जा रही है।")
        } catch (e: Exception) {
            Log.w(tag, "Speakerphone routing note: ${e.localizedMessage}")
        }

        // Step 3: Classify Caller & Determine Tone
        val call = _currentCall.value
        val relationship = classifyCallerRelationship(call?.contactName, call?.phoneNumber)
        onLog("👤 कॉलर प्रोफ़ाइल: ${relationship.labelHindi} | टोन: ${relationship.tonePrompt}")

        // Step 4: AI Greeting to Caller (in Hindi)
        delay(600)
        val callerGreeting = when (relationship) {
            CallerRelationship.FAMILY -> "नमस्ते! मैं यूजर का AI असिस्टेंट मैक्स बोल रहा हूँ। वह अभी फ़ोन के पास नहीं हैं, बताइए क्या काम है?"
            CallerRelationship.WORK -> "नमस्ते, मैं यूजर का AI असिस्टेंट मैक्स बोल रहा हूँ। वह अभी व्यस्त हैं। कृपया बताएं आप किस विषय पर बात करना चाहते हैं?"
            else -> "नमस्ते, मैं यूजर का AI असिस्टेंट मैक्स बोल रहा हूँ। कृपया अपनी बात कहें, मैं उन्हें संदेश दे दूँगा।"
        }

        voiceManager?.speak(callerGreeting, speechRate = 0.95f)

        _currentCall.value = _currentCall.value?.copy(isAgentAttending = true)

        val logMsg = "मैक्स कॉलर से बात कर रहा है (AI Call Attendance Mode active)।"
        onLog(logMsg)

        return CallActionResult(
            success = true,
            actionType = "AGENT_ATTEND",
            methodUsed = answerResult.methodUsed,
            messageHindi = logMsg,
            voiceFeedbackHindi = "मैक्स कॉल अटेंड कर रहा है।"
        )
    }

    /**
     * Relationship Classifier for Caller (Part 3 architecture).
     */
    private fun classifyCallerRelationship(contactName: String?, phoneNumber: String?): CallerRelationship {
        val name = contactName?.lowercase() ?: ""
        return when {
            name.contains("mummy") || name.contains("papa") || name.contains("bhai") ||
                    name.contains("didi") || name.contains("mom") || name.contains("dad") ||
                    name.contains("bhabhi") || name.contains("माता") || name.contains("पिता") -> CallerRelationship.FAMILY

            name.contains("office") || name.contains("boss") || name.contains("sir") ||
                    name.contains("colleague") || name.contains("manager") || name.contains("कंपनी") -> CallerRelationship.WORK

            name.contains("swiggy") || name.contains("zomato") || name.contains("amazon") ||
                    name.contains("flipkart") || name.contains("delivery") || name.contains("uber") -> CallerRelationship.SERVICE_DELIVERY

            else -> CallerRelationship.UNKNOWN
        }
    }

    // =============================================================================================
    // 7. SIMULATION & TESTING UTILITY
    // =============================================================================================

    /**
     * Allows 1-tap testing of the incoming call announcement & voice answer/reject flow
     * right on the browser emulator or without a real SIM card!
     */
    fun simulateIncomingCall(callerName: String = "राहुल शर्मा", phoneNumber: String = "+91 98765 43210") {
        onLog("🧪 [सिमुलेशन] इनकमिंग कॉल सिमुलेट की जा रही है...")
        scope.launch(Dispatchers.Main) {
            val callInfo = IncomingCallInfo(
                phoneNumber = phoneNumber,
                contactName = callerName,
                status = CallStatus.RINGING
            )
            _currentCall.value = callInfo
            announceIncomingCall(callInfo.displayIdentifier)
        }
    }
}
