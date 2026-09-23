package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MaxApplication
import com.example.accessibility.MaxAccessibilityService
import com.example.call.CallControlManager
import com.example.call.CallStatus
import com.example.call.IncomingCallInfo
import com.example.data.local.CommandHistoryEntity
import com.example.data.local.MemoryManager
import com.example.data.local.UserMemoryEntity
import com.example.data.model.ActionType
import com.example.data.model.AssistantAction
import com.example.data.model.ExecutionResult
import com.example.data.model.ScreenSnapshot
import com.example.data.local.ReminderEntity
import com.example.reminder.ReminderManager
import com.example.reminder.ReminderActionResult
import com.example.weather.WeatherManager
import com.example.weather.WeatherResult
import com.example.weather.CurrentWeatherInfo
import com.example.router.LocalCommandRouter
import com.example.router.LocalExecutionResult
import com.example.router.CommandRouter
import com.example.router.CommandCategory
import com.example.router.CommandRoutingDecision
import com.example.router.IntentClassifier
import com.example.router.InputCategory
import com.example.router.IntentClassificationResult
import com.example.router.CommandParser
import com.example.router.ParsedCommand
import com.example.router.ParsedIntent
import com.example.router.IntelligenceLayerEngine
import com.example.router.IntelligenceDecision
import com.example.voice.MaxVoiceManager
import com.example.camera.MaxCameraManager
import com.example.camera.CameraCaptureResult
import com.example.antitheft.AntiTheftManager
import com.example.antitheft.AntiTheftSettingsEntity
import com.example.antitheft.IntruderLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.voice.SentenceStreamBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AgentStatus {
    IDLE,
    LISTENING,
    THINKING,
    EXECUTING,
    SPEAKING,
    ERROR
}

class MaxViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MaxApplication
    private val repository = app.repository
    private val geminiClient = app.geminiClient
    val simulatorState = app.simulatorState
    val localCommandRouter = LocalCommandRouter(application)
    val intentClassifier = IntentClassifier()
    val toggleController get() = localCommandRouter.toggleController

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val voiceManager = MaxVoiceManager(application) { command ->
        processCommand(command)
    }

    val speakerEnrollmentManager get() = voiceManager.speakerEnrollmentManager

    val callControlManager = CallControlManager(
        context = application,
        scope = viewModelScope,
        voiceManager = voiceManager,
        onLog = { addLog(it) }
    )

    val reminderManager = ReminderManager(application, app.database.reminderDao())
    val weatherManager = WeatherManager(application)
    val cameraManager = MaxCameraManager(application)
    val antiTheftManager = AntiTheftManager.getInstance(application)

    val antiTheftSettings: StateFlow<AntiTheftSettingsEntity?> = antiTheftManager.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val latestIntruderLog: StateFlow<IntruderLogEntity?> = antiTheftManager.latestIntruderLogFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allIntruderLogs: StateFlow<List<IntruderLogEntity>> = antiTheftManager.intruderLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isTheftAlarmActive: StateFlow<Boolean> = antiTheftManager.theftAlarmManager.isAlarmActive

    fun startTheftAlarm(reason: String = "यूजर द्वारा चालू किया गया चोरी अलार्म") {
        antiTheftManager.startEmergencySiren(reason)
        addLog("🚨 चोरी अलार्म चालू: $reason")
    }

    fun stopTheftAlarm() {
        antiTheftManager.stopEmergencySiren()
        addLog("✅ चोरी अलार्म बंद किया गया")
    }

    private val _isAntiTheftProcessing = MutableStateFlow(false)
    val isAntiTheftProcessing: StateFlow<Boolean> = _isAntiTheftProcessing.asStateFlow()

    private val _lastCameraCapture = MutableStateFlow<CameraCaptureResult?>(null)
    val lastCameraCapture: StateFlow<CameraCaptureResult?> = _lastCameraCapture.asStateFlow()

    private val _sceneAnalysisText = MutableStateFlow<String?>(null)
    val sceneAnalysisText: StateFlow<String?> = _sceneAnalysisText.asStateFlow()

    private val _isCameraProcessing = MutableStateFlow(false)
    val isCameraProcessing: StateFlow<Boolean> = _isCameraProcessing.asStateFlow()

    val activeReminders: StateFlow<List<ReminderEntity>> = reminderManager.activeReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReminders: StateFlow<List<ReminderEntity>> = reminderManager.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentWeather = MutableStateFlow<CurrentWeatherInfo?>(null)
    val currentWeather: StateFlow<CurrentWeatherInfo?> = _currentWeather.asStateFlow()

    private val _isFetchingWeather = MutableStateFlow(false)
    val isFetchingWeather: StateFlow<Boolean> = _isFetchingWeather.asStateFlow()

    val currentCall: StateFlow<IncomingCallInfo?> = callControlManager.currentCall
    val isWaitingForCallVoiceResponse: StateFlow<Boolean> = callControlManager.isWaitingForVoiceResponse
    val isAgentAttendingCall: StateFlow<Boolean> = callControlManager.agentAttendanceActive

    private val _agentStatus = MutableStateFlow(AgentStatus.IDLE)
    val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("मैक्स तैयार है (Ready for your command)")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastVoiceInput = MutableStateFlow("")
    val lastVoiceInput: StateFlow<String> = _lastVoiceInput.asStateFlow()

    private val _lastAction = MutableStateFlow<AssistantAction?>(null)
    val lastAction: StateFlow<AssistantAction?> = _lastAction.asStateFlow()

    private val _executionLogs = MutableStateFlow<List<String>>(
        listOf("[${timeFormat.format(Date())}] मैक्स सिस्टम लोड हो चुका है। वॉयस और जेस्चर इंजन तैयार है।")
    )
    val executionLogs: StateFlow<List<String>> = _executionLogs.asStateFlow()

    private val _useSimulatorMode = MutableStateFlow(true)
    val useSimulatorMode: StateFlow<Boolean> = _useSimulatorMode.asStateFlow()

    val isAccessibilityEnabled: StateFlow<Boolean> = MaxAccessibilityService.isServiceActive

    val historyList: StateFlow<List<CommandHistoryEntity>> = repository.recentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memoryList: StateFlow<List<UserMemoryEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customApiKey = repository.customApiKey
    val gestureSpeedMs = repository.gestureSpeedMs
    val speechRate = repository.speechRate

    init {
        app.callControlManager = callControlManager
        app.onVoiceTriggerRequested = {
            toggleListening()
        }
    }

    fun answerCall() {
        viewModelScope.launch {
            val result = callControlManager.answerCall()
            _statusMessage.value = result.messageHindi
            _agentStatus.value = AgentStatus.IDLE
        }
    }

    fun rejectCall() {
        viewModelScope.launch {
            val result = callControlManager.rejectCall()
            _statusMessage.value = result.messageHindi
            _agentStatus.value = AgentStatus.IDLE
        }
    }

    fun attendCallWithAgent() {
        viewModelScope.launch {
            val result = callControlManager.attendCallWithMaxAgent()
            _statusMessage.value = result.messageHindi
        }
    }

    fun simulateIncomingCall(name: String = "राहुल शर्मा", number: String = "+91 98765 43210") {
        callControlManager.simulateIncomingCall(name, number)
    }

    fun hasCallPermissions(): Boolean = callControlManager.hasRequiredPermissions()

    fun getMissingCallPermissions(): List<String> = callControlManager.getMissingPermissionsList()

    fun refreshWeather(speak: Boolean = false) {
        viewModelScope.launch {
            _isFetchingWeather.value = true
            addLog("मौसम रिफ्रेश: Open-Meteo API से डेटा मंगाया जा रहा है...")
            val res = weatherManager.fetchCurrentWeather()
            _isFetchingWeather.value = false
            when (res) {
                is WeatherResult.Success -> {
                    _currentWeather.value = res.weather
                    _statusMessage.value = res.weather.weatherDescriptionHindi
                    addLog("मौसम अपडेट हुआ: ${res.weather.temperature}°C, ${res.weather.weatherDescriptionHindi}")
                    if (speak) {
                        voiceManager.speak(res.voiceResponseHindi, speechRate.value)
                    }
                }
                is WeatherResult.PermissionRequired -> {
                    _statusMessage.value = res.messageHindi
                    addLog("चेतावनी: मौसम के लिए लोकेशन परमिशन की आवश्यकता है।")
                    if (speak) voiceManager.speak(res.voiceResponseHindi, speechRate.value)
                }
                is WeatherResult.Error -> {
                    _statusMessage.value = res.messageHindi
                    addLog("त्रुटि: ${res.messageHindi}")
                    if (speak) voiceManager.speak(res.voiceResponseHindi, speechRate.value)
                }
            }
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            reminderManager.deleteReminder(id)
            addLog("रिमाइंडर हटाया गया (ID: $id)")
        }
    }

    fun scheduleQuickReminder(minutes: Int = 1, title: String = "दवाई लेना") {
        processCommand("$minutes मिनट बाद $title याद दिलाना")
    }

    fun checkExactAlarmPermission(): Boolean = reminderManager.canScheduleExactAlarms()

    fun checkLocationPermission(): Boolean = weatherManager.hasLocationPermission()

    fun checkCameraPermission(): Boolean = cameraManager.hasCameraPermission()

    fun checkSmsPermission(): Boolean = antiTheftManager.hasSmsPermission()

    fun isDeviceAdminActive(): Boolean = antiTheftManager.isDeviceAdminActive()

    fun isScreenLockSet(): Boolean = antiTheftManager.isScreenLockSet()

    fun setFailedAttemptsThreshold(threshold: Int) {
        viewModelScope.launch {
            antiTheftManager.setFailedAttemptsThreshold(threshold)
            val msg = "गलत पासवर्ड लिमिट $threshold बार सेट की गई।"
            addLog("सुरक्षा लिमिट: $msg")
            voiceManager.speak(msg, speechRate.value)
        }
    }

    fun toggleAntiTheft(enable: Boolean) {
        viewModelScope.launch {
            antiTheftManager.setAntiTheftEnabled(enable)
            val msg = if (enable) "एंटी-थेफ्ट सुरक्षा सक्रिय कर दी गई है।" else "एंटी-थेफ्ट सुरक्षा बंद कर दी गई है।"
            addLog("सुरक्षा स्थिति: $msg")
            voiceManager.speak(msg, speechRate.value)
        }
    }

    fun saveTrustedContact(name: String, phone: String, email: String = "") {
        viewModelScope.launch {
            antiTheftManager.setTrustedContact(name, phone, email)
            val msg = "इमरजेंसी कॉन्टैक्ट $name ($phone) सेट हो गया है।"
            addLog("सुरक्षा अपडेट: $msg")
            voiceManager.speak(msg, speechRate.value)
        }
    }

    fun testIntruderAlert(speak: Boolean = true) {
        viewModelScope.launch {
            _isAntiTheftProcessing.value = true
            addLog("🛡 एंटी-थेफ्ट टेस्ट: गलत पासवर्ड डिटेक्शन सिमुलेट किया जा रहा है...")
            val result = antiTheftManager.triggerIntruderAlert(
                triggerType = "MANUAL_TEST",
                details = "मैन्युअल सुरक्षा टेस्ट (3 बार गलत पासवर्ड सिमुलेशन)"
            )
            _isAntiTheftProcessing.value = false
            val feedback = "सुरक्षा टेस्ट सफल! फ्रंट सेल्फी, लोकेशन कैप्चर और SMS अलर्ट ट्रिगर हो गया है।"
            addLog("सुरक्षा टेस्ट पूरा: $feedback")
            if (speak) voiceManager.speak(feedback, speechRate.value)
        }
    }

    fun testSimAlert(speak: Boolean = true) {
        viewModelScope.launch {
            _isAntiTheftProcessing.value = true
            addLog("🛡 सिम कार्ड चेंज टेस्ट शुरू...")
            antiTheftManager.checkAndHandleSimChange()
            _isAntiTheftProcessing.value = false
            val feedback = "सिम कार्ड सुरक्षा चेक पूरा हो गया है।"
            addLog(feedback)
            if (speak) voiceManager.speak(feedback, speechRate.value)
        }
    }

    fun triggerSiren() {
        antiTheftManager.playEmergencySiren()
        addLog("🚨 इमरजेंसी साइरन शुरू किया गया")
    }

    fun stopSiren() {
        antiTheftManager.stopEmergencySiren()
        addLog("🛑 साइरन बंद किया गया")
    }

    fun takePhotoOrSelfie(isFront: Boolean, speak: Boolean = true) {
        processCommand(if (isFront) "सेल्फी लो" else "फोटो खींचो")
    }

    fun analyzeScene(speak: Boolean = true) {
        processCommand("सामने क्या है बताओ")
    }

    fun testSilentCapture(speak: Boolean = true) {
        viewModelScope.launch {
            if (!cameraManager.hasCameraPermission()) {
                addLog("चेतावनी: साइलेंट कैप्चर के लिए कैमरा परमिशन आवश्यक है।")
                return@launch
            }
            _isCameraProcessing.value = true
            addLog("🛡 बैकग्राउंड/साइलेंट फोटो टेस्ट: बिना स्क्रीन UI के कैप्चर हो रहा है...")
            val result = cameraManager.captureSilentPhoto(isFrontCamera = true)
            _isCameraProcessing.value = false
            if (result.isSuccess) {
                val bitmap = result.getOrThrow()
                _lastCameraCapture.value = CameraCaptureResult(
                    bitmap = bitmap,
                    isFrontCamera = true,
                    isSavedToGallery = false
                )
                _sceneAnalysisText.value = "साइलेंट बैकग्राउंड कैप्चर सफल (Anti-Theft Guard Feature Test OK)"
                addLog("सफलता: साइलेंट बैकग्राउंड फोटो कैप्चर हो गई (${bitmap.width}x${bitmap.height}px)")
                if (speak) voiceManager.speak("साइलेंट बैकग्राउंड फोटो सफलतापूर्वक कैप्चर हो गई है।", speechRate.value)
            } else {
                addLog("त्रुटि: साइलेंट फोटो कैप्चर विफल - ${result.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun addLog(msg: String) {
        val timestamp = timeFormat.format(Date())
        _executionLogs.value = (listOf("[$timestamp] $msg") + _executionLogs.value).take(40)
    }

    fun toggleListening() {
        if (voiceManager.isListening.value) {
            voiceManager.stopListening()
            _agentStatus.value = AgentStatus.IDLE
            _statusMessage.value = "सुनना बंद किया गया।"
        } else {
            _agentStatus.value = AgentStatus.LISTENING
            _statusMessage.value = "मैक्स सुन रहा है... (Listening in Hindi/English)"
            addLog("माइक्रोफोन सक्रिय: यूजर की आवाज सुनी जा रही है...")
            voiceManager.startListening()
        }
    }

    var isAwaitingConfirmation: Boolean = false
        private set
    var pendingAppName: String? = null
        private set
    var pendingFullCommand: String? = null
        private set
    private var pendingConfirmationDecision: CommandRoutingDecision? = null

    private fun setConfirmationState(appName: String?, fullCommand: String, decision: CommandRoutingDecision) {
        isAwaitingConfirmation = true
        pendingAppName = appName
        pendingFullCommand = fullCommand
        pendingConfirmationDecision = decision

        val logMsg = "CONFIRMATION_STATE_SET: awaiting=true, pendingApp=${appName ?: "NONE"}, pendingCommand=$fullCommand"
        addLog(logMsg)
        Log.i("MaxViewModel", logMsg)
    }

    private fun clearConfirmationState() {
        isAwaitingConfirmation = false
        pendingAppName = null
        pendingFullCommand = null
        pendingConfirmationDecision = null
    }

    fun processCommand(commandText: String) {
        if (commandText.isBlank()) return
        _lastVoiceInput.value = commandText
        addLog("आवाज रिकॉर्ड हुई: \"$commandText\"")

        val nextInputLog = "NEXT_INPUT_CHECK: isAwaitingConfirmation=$isAwaitingConfirmation"
        addLog(nextInputLog)
        Log.i("MaxViewModel", nextInputLog)

        viewModelScope.launch {
            // =========================================================================
            // STEP 0: FIRST CHECK — Pending Confirmation State Check (BEFORE ROUTER)
            // =========================================================================
            if (isAwaitingConfirmation) {
                val pendingCmd = pendingFullCommand ?: commandText
                val pendingDecision = pendingConfirmationDecision

                if (IntelligenceLayerEngine.isAffirmative(commandText)) {
                    val resolvedLog = "CONFIRMATION_RESOLVED: response=haan, executing=$pendingCmd"
                    addLog(resolvedLog)
                    Log.i("MaxViewModel", resolvedLog)

                    clearConfirmationState()

                    if (pendingDecision != null) {
                        executeDecision(pendingDecision, pendingCmd)
                    } else {
                        val freshIntel = IntelligenceLayerEngine.processWithIntelligence(getApplication(), pendingCmd)
                        executeDecision(freshIntel.baseRoutingDecision, pendingCmd)
                    }
                    return@launch
                } else if (IntelligenceLayerEngine.isNegative(commandText)) {
                    val resolvedLog = "CONFIRMATION_RESOLVED: response=nahi, executing=CANCEL"
                    addLog(resolvedLog)
                    Log.i("MaxViewModel", resolvedLog)

                    clearConfirmationState()
                    val cancelMsg = "ठीक है, कैंसिल कर दिया। आप क्या करना चाहते हैं?"
                    _statusMessage.value = cancelMsg
                    voiceManager.speak(cancelMsg, 0.98f)
                    _agentStatus.value = AgentStatus.IDLE
                    return@launch
                } else {
                    val resolvedLog = "CONFIRMATION_RESOLVED: response=new_command, overriding pending '$pendingCmd' with '$commandText'"
                    addLog(resolvedLog)
                    Log.i("MaxViewModel", resolvedLog)
                    clearConfirmationState()
                }
            }

            // =========================================================================
            // 4 ADVANCED INTELLIGENCE LAYERS + 5-STAGE COMMAND ROUTER
            // =========================================================================
            val intelDecision = IntelligenceLayerEngine.processWithIntelligence(getApplication(), commandText)
            val decision = intelDecision.baseRoutingDecision

            // Emit exact 5 Debug Log lines required for STAGE 1-5
            addLog("STAGE1_CLEANED: ${decision.cleanedCommand}")
            addLog("STAGE2_CATEGORY: ${decision.category}")
            addLog("STAGE3_APP_MATCHED: ${decision.matchedAppName ?: "NONE"}")
            addLog("STAGE3_REMAINING_INSTRUCTION: ${decision.remainingInstruction ?: "NONE"}")
            addLog("STAGE4_ACTION: ${decision.actionSummary}")

            // Emit 4 INTELLIGENCE LAYER Debug Logs
            addLog(intelDecision.logLayer1)
            addLog(intelDecision.logLayer2)
            addLog(intelDecision.logLayer3)
            addLog(intelDecision.logLayer4)

            // Priority Call Control (if ringing)
            if (callControlManager.currentCall.value?.status == CallStatus.RINGING) {
                val callHandled = callControlManager.tryHandleCallVoiceCommand(commandText)
                if (callHandled) {
                    _agentStatus.value = AgentStatus.IDLE
                    return@launch
                }
            }

            // LAYER 4 — Self-Correction Triggered
            if (intelDecision.isSelfCorrection) {
                _agentStatus.value = AgentStatus.EXECUTING
                addLog("🔄 REVERTING LAST ACTION (Self-Correction)")
                localCommandRouter.tryRouteLocally("back")
                val msg = intelDecision.correctionMessageHindi ?: "माफ़ कीजिये, पिछला एक्शन कैंसिल कर दिया गया है।"
                _statusMessage.value = msg
                voiceManager.speak(msg, 0.98f)
                _agentStatus.value = AgentStatus.IDLE
                return@launch
            }

            // LAYER 1 — Context Clarification Needed
            if (intelDecision.needsContextClarification) {
                val prompt = intelDecision.clarificationPromptHindi ?: "किसका मतलब है? कृपया ऐप का नाम बताएं।"
                _statusMessage.value = prompt
                voiceManager.speak(prompt, 0.98f)
                _agentStatus.value = AgentStatus.IDLE
                return@launch
            }

            // LAYER 2 — User Confirmation Needed
            if (intelDecision.needsUserConfirmation) {
                setConfirmationState(decision.matchedAppName, commandText, decision)
                val prompt = intelDecision.confirmationPromptHindi ?: "क्या आपका मतलब ${decision.matchedAppName} से है?"
                _statusMessage.value = prompt
                voiceManager.speak(prompt, 0.98f)
                _agentStatus.value = AgentStatus.IDLE
                return@launch
            }

            // Execute Decision
            executeDecision(decision, commandText)
        }
    }

    private suspend fun executeDecision(decision: CommandRoutingDecision, rawCommandText: String) {
        // Update short-term context memory in Layer 1
        IntelligenceLayerEngine.updateActiveContext(
            appName = decision.matchedAppName,
            actionType = decision.category.name,
            rawCommand = rawCommandText,
            cleanedCommand = decision.cleanedCommand,
            category = decision.category
        )

        when (decision.category) {
            CommandCategory.OFFLINE_TASK -> {
                _agentStatus.value = AgentStatus.EXECUTING
                _statusMessage.value = "लोकल टास्क निष्पादित हो रहा है..."

                // 1. Check Alarm / Reminders
                if (reminderManager.isReminderOrAlarmCommand(decision.cleanedCommand)) {
                    val result = reminderManager.handleVoiceCommand(decision.cleanedCommand)
                    val (msgHindi, voiceHindi, success) = when (result) {
                        is ReminderActionResult.Scheduled -> Triple(result.messageHindi, result.voiceResponseHindi, true)
                        is ReminderActionResult.Cancelled -> Triple(result.messageHindi, result.voiceResponseHindi, true)
                        is ReminderActionResult.Listed -> Triple(result.messageHindi, result.voiceResponseHindi, true)
                        is ReminderActionResult.Error -> Triple(result.messageHindi, result.voiceResponseHindi, false)
                    }
                    addLog("⏰ रिमाइंडर: $msgHindi")
                    _statusMessage.value = msgHindi
                    voiceManager.speak(voiceHindi, 0.98f)
                    _agentStatus.value = AgentStatus.IDLE
                    return
                }

                // 2. Check Camera
                if (cameraManager.isCameraCommand(decision.cleanedCommand)) {
                    val isFront = cameraManager.isFrontCamera(decision.cleanedCommand)
                    val capRes = cameraManager.capturePhoto(isFrontCamera = isFront, saveToGallery = true)
                    val msg = if (capRes.isSuccess) {
                        if (isFront) "सेल्फी कैप्चर हो गई!" else "फोटो खींच ली गई!"
                    } else "कैमरा से फोटो नहीं खींची जा सकी।"
                    addLog("📷 कैमरा: $msg")
                    _statusMessage.value = msg
                    voiceManager.speak(msg, 0.98f)
                    _agentStatus.value = AgentStatus.IDLE
                    return
                }

                // 3. Local Command Router (Toggles, Nav, Lock, or App Launch)
                var localResult = localCommandRouter.tryRouteLocally(decision.cleanedCommand)
                if (localResult !is LocalExecutionResult.Handled && decision.matchedAppName != null) {
                    localResult = localCommandRouter.launchAppByName(decision.matchedAppName, decision.matchedAppPackage)
                }

                if (localResult is LocalExecutionResult.Handled) {
                    addLog("⚡ REAL LOCAL ACTION: ${localResult.actionType} -> ${localResult.messageHindi}")
                    _statusMessage.value = localResult.messageHindi
                    voiceManager.speak(localResult.voiceResponseHindi, 0.98f)

                    repository.logCommand(
                        prompt = rawCommandText,
                        app = "System / Local",
                        actionType = localResult.actionType,
                        actionDetails = localResult.messageHindi,
                        responseHindi = localResult.voiceResponseHindi,
                        success = localResult.success
                    )

                    if (localResult.actionType == "LOCK_PHONE") {
                        delay(450)
                        antiTheftManager.lockDeviceNow()
                    }
                } else {
                    val notFoundMsg = "माफ़ कीजिये, '${decision.cleanedCommand}' डिवाइस पर निष्पादित नहीं हो सका।"
                    _statusMessage.value = notFoundMsg
                    voiceManager.speak(notFoundMsg, 0.98f)
                }

                _agentStatus.value = AgentStatus.IDLE
                return
            }

            CommandCategory.SCREEN_TASK -> {
                _agentStatus.value = AgentStatus.THINKING
                _statusMessage.value = "स्क्रीन टास्क प्रोसेस हो रहा है..."

                // Step 1: Open app locally if target app matched & not open
                if (decision.matchedAppName != null) {
                    addLog("📱 Step 1: Target app '${decision.matchedAppName}' khola ja raha hai...")
                    localCommandRouter.launchAppByName(decision.matchedAppName, decision.matchedAppPackage)
                    delay(800)
                }

                // Step 2: Screen Perception & Execution via Gemini
                val remainingQuery = decision.remainingInstruction ?: decision.cleanedCommand
                addLog("👁️ Step 2: Screen perception active for: '$remainingQuery'")

                val memoriesDeferred = viewModelScope.async(Dispatchers.IO) { repository.getAllMemoriesList() }
                val historyDeferred = viewModelScope.async(Dispatchers.IO) { repository.getRecentHistoryList(6) }
                val snapshot = captureActiveScreenSnapshot()

                addLog("👁️ SCREEN_READ: package=${snapshot.packageName.ifBlank { "unknown" }}, elements_found=${snapshot.elements.size}")

                val memories = memoriesDeferred.await()
                val history = historyDeferred.await()

                val action = geminiClient.decideAction(
                    userCommand = remainingQuery,
                    screenSnapshot = snapshot,
                    memories = memories,
                    recentHistory = history
                )

                addLog("🤖 GEMINI_SCREEN_QUERY: sent, response=${action.actionType} (${action.targetX}, ${action.targetY}) - ${action.targetElementDesc}")

                _statusMessage.value = action.voiceResponseHindi
                voiceManager.speak(action.voiceResponseHindi, 0.98f)

                val execResult = executeAssistantAction(action)
                addLog("⚡ ACCESSIBILITY_ACTION: type=${action.actionType}, target=(${action.targetX}, ${action.targetY}), result=${if (execResult.success) "success" else "fail"}")

                repository.logCommand(
                    prompt = rawCommandText,
                    app = snapshot.packageName.ifBlank { decision.matchedAppName ?: "Active App" },
                    actionType = action.actionType.name,
                    actionDetails = execResult.message,
                    responseHindi = action.voiceResponseHindi,
                    success = execResult.success
                )

                _agentStatus.value = AgentStatus.IDLE
                return
            }

            CommandCategory.CONVERSATION -> {
                _agentStatus.value = AgentStatus.THINKING
                _statusMessage.value = "मैक्स सोच रहा है..."

                // Weather Check
                if (weatherManager.isWeatherCommand(decision.cleanedCommand)) {
                    val weatherResult = weatherManager.fetchCurrentWeather()
                    if (weatherResult is WeatherResult.Success) {
                        _currentWeather.value = weatherResult.weather
                        addLog("🌤 मौसम: ${weatherResult.messageHindi}")
                        _statusMessage.value = weatherResult.voiceResponseHindi
                        voiceManager.speak(weatherResult.voiceResponseHindi, 0.98f)
                        _agentStatus.value = AgentStatus.IDLE
                        return
                    }
                }

                // Memory Preference Learning
                val extractedPref = MemoryManager.extractUserPreference(decision.cleanedCommand)
                if (extractedPref != null) {
                    repository.saveMemory(extractedPref.key, extractedPref.value, extractedPref.category, extractedPref.descriptionHindi)
                    addLog("🧠 सीख लिया: ${extractedPref.key} = ${extractedPref.value}")
                    _statusMessage.value = extractedPref.acknowledgementHindi
                    voiceManager.speak(extractedPref.acknowledgementHindi, 0.98f)
                    _agentStatus.value = AgentStatus.IDLE
                    return
                }

                // Direct Conversational Reply via Gemini
                val memories = repository.getAllMemoriesList()
                val replyText = geminiClient.generateConversationalReply(decision.cleanedCommand, memories)

                addLog("💬 उत्तर: $replyText")
                _statusMessage.value = replyText
                voiceManager.speak(replyText, 0.98f)

                repository.logCommand(
                    prompt = rawCommandText,
                    app = "Max Conversation",
                    actionType = "CONVERSATION_REPLY",
                    actionDetails = replyText,
                    responseHindi = replyText,
                    success = true
                )

                _agentStatus.value = AgentStatus.IDLE
                return
            }
            else -> {
                _agentStatus.value = AgentStatus.IDLE
            }
        }
    }

    private fun captureActiveScreenSnapshot(): ScreenSnapshot {
        val realService = MaxAccessibilityService.instance
        if (realService != null) {
            val realSnapshot = realService.captureCurrentScreen()
            if (realSnapshot.elements.isNotEmpty() || realSnapshot.packageName.isNotBlank()) {
                return realSnapshot
            }
        }
        return if (_useSimulatorMode.value) {
            simulatorState.generateSnapshot()
        } else {
            ScreenSnapshot(packageName = "android", timestamp = System.currentTimeMillis())
        }
    }

    private suspend fun executeAssistantAction(action: AssistantAction): ExecutionResult {
        // Visual touch indicator in simulator
        if (action.targetX > 0 && action.targetY > 0) {
            simulatorState.showTapIndicator(action.targetX.toFloat(), action.targetY.toFloat())
            delay(120)
        }

        val result = when {
            _useSimulatorMode.value && MaxAccessibilityService.instance == null -> {
                // Execute on simulator
                when (action.actionType) {
                    ActionType.SKIP_AD -> {
                        val skipped = simulatorState.skipAd()
                        ExecutionResult(success = skipped, message = "सिम्युलेटर में विज्ञापन स्किप किया गया", action = action)
                    }
                    ActionType.TAP -> {
                        // Check if tap was on Skip Ad
                        if (action.targetElementDesc.contains("Skip", ignoreCase = true) ||
                            (action.targetX in 700..1050 && action.targetY in 550..700)) {
                            val skipped = simulatorState.skipAd()
                            ExecutionResult(success = skipped, message = "स्किप बटन पर टैप हुआ", action = action)
                        } else if (action.targetElementDesc.contains("Next", ignoreCase = true) || action.targetY > 700) {
                            val nextVid = simulatorState.playNextVideo()
                            ExecutionResult(
                                success = true,
                                message = "अगला वीडियो चलाया: ${nextVid?.title ?: ""}",
                                action = action
                            )
                        } else {
                            ExecutionResult(success = true, message = "टैप पूरा हुआ (${action.targetX}, ${action.targetY})", action = action)
                        }
                    }
                    ActionType.TYPE -> {
                        simulatorState.searchAndPlay(action.textToType)
                        ExecutionResult(success = true, message = "\"${action.textToType}\" सर्च और प्ले किया गया", action = action)
                    }
                    ActionType.SCROLL_DOWN -> {
                        simulatorState.playNextVideo()
                        ExecutionResult(success = true, message = "नीचे स्क्रॉल किया गया", action = action)
                    }
                    ActionType.OPEN_APP -> {
                        ExecutionResult(success = true, message = "यूट्यूब खोला गया", action = action)
                    }
                    else -> {
                        ExecutionResult(success = true, message = action.voiceResponseHindi, action = action)
                    }
                }
            }
            else -> {
                // Execute on real Android Accessibility Service
                val service = MaxAccessibilityService.instance
                if (service != null) {
                    service.executeAction(action)
                } else {
                    val appContext = getApplication<MaxApplication>()
                    val isEnabledInSettings = MaxAccessibilityService.isAccessibilitySettingsEnabled(appContext)
                    val errorMsg = if (isEnabledInSettings) {
                        "एक्सेसिबिलिटी सर्विस रीस्टार्ट हो रही है, कृपया पुनः बोलें।"
                    } else {
                        "एक्सेसिबिलिटी सर्विस बंद है। कृपया सेटिंग्स में जाकर मैक्स एक्सेसिबिलिटी सर्विस को ऑन करें।"
                    }
                    if (!isEnabledInSettings) {
                        MaxAccessibilityService.openAccessibilitySettings(appContext)
                    }
                    ExecutionResult(
                        success = false,
                        message = errorMsg,
                        action = action
                    )
                }
            }
        }

        delay(400)
        simulatorState.clearTapIndicator()
        return result
    }

    fun setSimulatorMode(enabled: Boolean) {
        _useSimulatorMode.value = enabled
        addLog(if (enabled) "मोड बदला गया: यूट्यूब सिम्युलेटर (इन-ऐप डेमो)" else "मोड बदला गया: लाइव सिस्टम एक्सेसिबिलिटी सर्विस")
    }

    fun setCustomApiKey(key: String) {
        repository.setCustomApiKey(key)
        addLog("नया जेमिनी एपीआई की (API Key) सेव किया गया।")
    }

    fun setSpeechRate(rate: Float) {
        repository.setSpeechRate(rate)
    }

    fun setGestureSpeed(speedMs: Long) {
        repository.setGestureSpeedMs(speedMs)
    }

    fun saveMemory(key: String, value: String, category: String, descHindi: String) {
        viewModelScope.launch {
            repository.saveMemory(key, value, category, descHindi)
            addLog("मेमोरी सेव हुई: $key = $value")
        }
    }

    fun deleteMemory(key: String) {
        viewModelScope.launch {
            repository.deleteMemory(key)
            addLog("मेमोरी डिलीट की गई: $key")
        }
    }

    fun optimizeMemory() {
        viewModelScope.launch {
            repository.pruneAndOptimizeMemory()
            addLog("मेमोरी ऑप्टिमाइज़ की गई: पुरानी गतिविधियां साफ की गईं और इतिहास सीमित किया गया।")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _executionLogs.value = emptyList()
            addLog("हिस्ट्री और लॉग साफ किए गए।")
        }
    }

    private fun isAntiTheftVoiceCommand(cmd: String): Boolean {
        return cmd.contains("एंटी थेफ्ट") || cmd.contains("anti theft") ||
                cmd.contains("इमरजेंसी नंबर") || cmd.contains("इमरजेंसी कांटेक्ट") ||
                cmd.contains("ट्रस्टेड नंबर") || cmd.contains("ट्रस्टेड कांटेक्ट") ||
                cmd.contains("emergency contact") || cmd.contains("trusted contact") ||
                cmd.contains("चोरी सुरक्षा") ||
                cmd.contains("साइरन बजाओ") || cmd.contains("अलार्म बजाओ") ||
                cmd.contains("साइरन बंद") || cmd.contains("अलार्म बंद") ||
                cmd.contains("गलत पासवर्ड टेस्ट") || cmd.contains("इंट्रूडर टेस्ट")
    }

    private suspend fun handleAntiTheftVoiceCommand(originalPrompt: String, cmd: String) {
        _agentStatus.value = AgentStatus.EXECUTING

        when {
            // Set Emergency / Trusted contact
            cmd.contains("इमरजेंसी") || cmd.contains("ट्रस्टेड") || cmd.contains("emergency") || cmd.contains("trusted") -> {
                // Extract 10-12 digit phone number
                val phoneRegex = Regex("(\\+?[0-9]{10,13})")
                val match = phoneRegex.find(originalPrompt)
                if (match != null) {
                    val phone = match.value
                    antiTheftManager.setTrustedContact("Emergency Contact", phone)
                    val resp = "आपका इमरजेंसी कांटेक्ट $phone सेट कर दिया गया है। चोरी की स्थिति में इस नंबर पर अलर्ट SMS जाएगा।"
                    addLog("सुरक्षा अपडेट: $resp")
                    _agentStatus.value = AgentStatus.SPEAKING
                    _statusMessage.value = resp
                    voiceManager.speak(resp, speechRate.value)
                } else {
                    val resp = "कृपया इमरजेंसी नंबर 10 अंकों में बताएं, जैसे: 'मेरा इमरजेंसी नंबर 9876543210 है'।"
                    _agentStatus.value = AgentStatus.SPEAKING
                    _statusMessage.value = resp
                    voiceManager.speak(resp, speechRate.value)
                }
            }

            // Siren controls
            cmd.contains("साइरन बजाओ") || cmd.contains("अलार्म बजाओ") -> {
                triggerSiren()
                val resp = "इमरजेंसी साइरन बजाया जा रहा है।"
                _agentStatus.value = AgentStatus.SPEAKING
                _statusMessage.value = resp
                voiceManager.speak(resp, speechRate.value)
            }

            cmd.contains("साइरन बंद") || cmd.contains("अलार्म बंद") || cmd.contains("साइरन रोको") -> {
                stopSiren()
                val resp = "साइरन बंद कर दिया गया है।"
                _agentStatus.value = AgentStatus.SPEAKING
                _statusMessage.value = resp
                voiceManager.speak(resp, speechRate.value)
            }

            // Enable / Disable Anti-Theft
            cmd.contains("बंद") || cmd.contains("ऑफ") || cmd.contains("disable") -> {
                antiTheftManager.setAntiTheftEnabled(false)
                val resp = "एंटी-थेफ्ट सुरक्षा बंद कर दी गई है।"
                addLog("सुरक्षा स्थिति: $resp")
                _agentStatus.value = AgentStatus.SPEAKING
                _statusMessage.value = resp
                voiceManager.speak(resp, speechRate.value)
            }

            cmd.contains("चालू") || cmd.contains("ऑन") || cmd.contains("enable") || cmd.contains("सक्रिय") -> {
                antiTheftManager.setAntiTheftEnabled(true)
                val resp = "एंटी-थेफ्ट सुरक्षा सक्रिय कर दी गई है। 3 बार गलत पासवर्ड दर्ज होने पर साइलेंट सेल्फी और GPS अलर्ट जाएगा।"
                addLog("सुरक्षा स्थिति: $resp")
                _agentStatus.value = AgentStatus.SPEAKING
                _statusMessage.value = resp
                voiceManager.speak(resp, speechRate.value)
            }

            // Test
            cmd.contains("टेस्ट") -> {
                testIntruderAlert(speak = true)
            }

            else -> {
                val resp = "एंटी-थेफ्ट सुरक्षा सक्रिय है। 3 बार गलत पासवर्ड डालने पर साइलेंट फोटो और GPS अलर्ट भेजा जाएगा।"
                _agentStatus.value = AgentStatus.SPEAKING
                _statusMessage.value = resp
                voiceManager.speak(resp, speechRate.value)
            }
        }

        delay(3000)
        if (_agentStatus.value == AgentStatus.SPEAKING) {
            _agentStatus.value = AgentStatus.IDLE
            _statusMessage.value = "मैक्स तैयार है।"
        }
    }

    override fun onCleared() {
        super.onCleared()
        antiTheftManager.stopEmergencySiren()
        voiceManager.destroy()
    }
}
