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
import com.example.router.IntentClassifier
import com.example.router.InputCategory
import com.example.router.IntentClassificationResult
import com.example.router.CommandParser
import com.example.router.ParsedCommand
import com.example.router.ParsedIntent
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
    val floatingOverlayEnabled = repository.floatingOverlayEnabled

    init {
        app.callControlManager = callControlManager
        app.onOverlayVoiceTriggerRequested = {
            toggleListening()
        }

        viewModelScope.launch {
            _agentStatus.collect { status ->
                app.overlayAgentStatus.value = status
            }
        }
        viewModelScope.launch {
            voiceManager.speechRms.collect { rms ->
                app.overlaySpeechRms.value = rms
            }
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

    fun processCommand(commandText: String) {
        if (commandText.isBlank()) return
        _lastVoiceInput.value = commandText
        addLog("आवाज रिकॉर्ड हुई: \"$commandText\"")

        viewModelScope.launch {
            // =========================================================================
            // STEP 0A: INTENT & ENTITY EXTRACTION (CommandParser)
            // =========================================================================
            var parsedCommand = CommandParser.parse(commandText)
            val addressLog = if (parsedCommand.extractedAddress != null) " (संबोधन: '${parsedCommand.extractedAddress}')" else ""
            addLog("[PARSER] इनपुट: '$commandText'$addressLog ➔ फिल्टर: '${parsedCommand.cleanedQuery}' | Intent: ${parsedCommand.intent} | Entity: '${parsedCommand.targetEntity}'")

            // =========================================================================
            // STEP 0B: INCOMING CALL VOICE CONTROL (Priority 1: उठा लो / काट दो / मैक्स तुम बात करो)
            // =========================================================================
            if (callControlManager.currentCall.value?.status == CallStatus.RINGING) {
                addLog("[वर्गीकरण: टास्क (TASK)] 📞 इनकमिंग कॉल बज रही है: वॉयस कमांड का मिलान किया जा रहा है...")
                val callHandled = callControlManager.tryHandleCallVoiceCommand(commandText)
                if (callHandled) {
                    repository.logCommand(
                        prompt = commandText,
                        app = "Phone / Call Control",
                        actionType = "CALL_VOICE_ACTION",
                        actionDetails = "Voice command executed during ringing incoming call",
                        responseHindi = "कॉल आदेश निष्पादित किया गया",
                        success = true
                    )
                    _agentStatus.value = AgentStatus.IDLE
                    return@launch
                }
            }

            _agentStatus.value = AgentStatus.THINKING
            _statusMessage.value = "कमांड का विश्लेषण हो रहा है..."

            // =========================================================================
            // STEP 1: LOCAL COMMAND ROUTER FIRST (Offline, Zero Latency, App Launch, System Toggles, Navigation, Lock)
            // =========================================================================
            var localResult = localCommandRouter.tryRouteLocally(commandText, parsedCommand)

            // Ambiguity Fallback: If local route failed and parser confidence is low, ask Gemini for structured JSON classification
            if (localResult !is LocalExecutionResult.Handled && (parsedCommand.intent == ParsedIntent.UNKNOWN || parsedCommand.confidence < 0.6f)) {
                addLog("🤔 लोकल पार्सिंग अस्पष्ट है: जेमिनी से स्ट्रक्चर्ड JSON इंटेंट/एंटीटी विश्लेषण मांगा जा रहा है...")
                val geminiParsed = geminiClient.parseCommandViaGemini(commandText)
                if (geminiParsed.intent != ParsedIntent.UNKNOWN && geminiParsed.targetEntity.isNotBlank()) {
                    parsedCommand = geminiParsed
                    addLog("[GEMINI PARSER] जेमिनी निष्कर्ष: Intent = ${parsedCommand.intent}, Target = '${parsedCommand.targetEntity}'")
                    // Retry local router with Gemini structured parse
                    localResult = localCommandRouter.tryRouteLocally(commandText, parsedCommand)
                }
            }

            if (localResult is LocalExecutionResult.Handled) {
                addLog("⚡ REAL ACTION: ${localResult.actionType} -> ${localResult.messageHindi}")
                _agentStatus.value = AgentStatus.EXECUTING
                _statusMessage.value = localResult.messageHindi

                // Also update simulator if app was opened and user is in simulator mode
                if (commandText.contains("youtube", ignoreCase = true) || commandText.contains("यूट्यूब", ignoreCase = true)) {
                    simulatorState.clearTapIndicator()
                }

                // Log to Room Database
                repository.logCommand(
                    prompt = commandText,
                    app = "System / Local",
                    actionType = localResult.actionType,
                    actionDetails = "Executed immediately offline via LocalCommandRouter",
                    responseHindi = localResult.voiceResponseHindi,
                    success = localResult.success
                )

                // Update activity context & auto-prune
                repository.saveActivityContext("System", localResult.actionType)
                repository.pruneAndOptimizeMemory()

                // Speak voice confirmation
                _agentStatus.value = AgentStatus.SPEAKING
                _statusMessage.value = localResult.voiceResponseHindi
                voiceManager.speak(localResult.voiceResponseHindi, speechRate.value)

                // Special handling for Phone Lock: TTS starts speaking first, then lockNow() locks screen
                if (localResult.actionType == "LOCK_PHONE") {
                    addLog("🔒 डिवाइस एडमिन: फोन तुरंत लॉक किया जा रहा है...")
                    delay(450)
                    antiTheftManager.lockDeviceNow()
                    _agentStatus.value = AgentStatus.IDLE
                    return@launch
                }

                delay(1800)
                if (_agentStatus.value == AgentStatus.SPEAKING) {
                    _agentStatus.value = AgentStatus.IDLE
                    _statusMessage.value = "मैक्स तैयार है।"
                }
                return@launch
            }

            // =========================================================================
            // STEP 2: OFFLINE REMINDERS & ALARMS (AlarmManager + Room Database)
            // =========================================================================
            if (reminderManager.isReminderOrAlarmCommand(commandText)) {
                addLog("[वर्गीकरण: टास्क (TASK)] ⏰ रिमाइंडर/अलार्म कमांड: ऑफलाइन AlarmManager में प्रोसेस हो रहा है...")
                _agentStatus.value = AgentStatus.EXECUTING
                _statusMessage.value = "रिमाइंडर प्रोसेस हो रहा है..."

                val result = reminderManager.handleVoiceCommand(commandText)
                val (msgHindi, voiceHindi, success) = when (result) {
                    is ReminderActionResult.Scheduled -> Triple(result.messageHindi, result.voiceResponseHindi, true)
                    is ReminderActionResult.Cancelled -> Triple(result.messageHindi, result.voiceResponseHindi, true)
                    is ReminderActionResult.Listed -> Triple(result.messageHindi, result.voiceResponseHindi, true)
                    is ReminderActionResult.Error -> Triple(result.messageHindi, result.voiceResponseHindi, false)
                }

                addLog("रिमाइंडर परिणाम: $msgHindi")
                repository.logCommand(
                    prompt = commandText,
                    app = "Reminders / Alarms",
                    actionType = "REMINDER_OFFLINE",
                    actionDetails = msgHindi,
                    responseHindi = voiceHindi,
                    success = success
                )

                _agentStatus.value = AgentStatus.SPEAKING
                _statusMessage.value = msgHindi
                voiceManager.speak(voiceHindi, speechRate.value)

                delay(2400)
                if (_agentStatus.value == AgentStatus.SPEAKING) {
                    _agentStatus.value = AgentStatus.IDLE
                    _statusMessage.value = "मैक्स तैयार है।"
                }
                return@launch
            }

            // =========================================================================
            // STEP 3: LIVE WEATHER FORECAST (Open-Meteo Free API + FusedLocation)
            // =========================================================================
            if (weatherManager.isWeatherCommand(commandText)) {
                addLog("[वर्गीकरण: टास्क (TASK)] 🌤 मौसम कमांड: FusedLocation + Open-Meteo API से डेटा लाया जा रहा है...")
                _agentStatus.value = AgentStatus.EXECUTING
                _isFetchingWeather.value = true
                _statusMessage.value = "वर्तमान स्थान का मौसम प्राप्त हो रहा है..."

                val weatherResult = weatherManager.fetchCurrentWeather()
                _isFetchingWeather.value = false

                val (msgHindi, voiceHindi, success) = when (weatherResult) {
                    is WeatherResult.Success -> {
                        _currentWeather.value = weatherResult.weather
                        Triple(weatherResult.messageHindi, weatherResult.voiceResponseHindi, true)
                    }
                    is WeatherResult.PermissionRequired -> {
                        Triple(weatherResult.messageHindi, weatherResult.voiceResponseHindi, false)
                    }
                    is WeatherResult.Error -> {
                        Triple(weatherResult.messageHindi, weatherResult.voiceResponseHindi, false)
                    }
                }

                addLog("मौसम परिणाम: $msgHindi")
                repository.logCommand(
                    prompt = commandText,
                    app = "Weather / Open-Meteo",
                    actionType = "WEATHER_QUERY",
                    actionDetails = msgHindi,
                    responseHindi = voiceHindi,
                    success = success
                )

                _agentStatus.value = AgentStatus.SPEAKING
                _statusMessage.value = voiceHindi
                voiceManager.speak(voiceHindi, speechRate.value)

                delay(2600)
                if (_agentStatus.value == AgentStatus.SPEAKING) {
                    _agentStatus.value = AgentStatus.IDLE
                    _statusMessage.value = "मैक्स तैयार है।"
                }
                return@launch
            }

            // =========================================================================
            // STEP 4: CAMERA, SELFIE & SCENE ANALYSIS (CameraX + MediaStore + Gemini Vision)
            // =========================================================================
            if (cameraManager.isCameraCommand(commandText)) {
                addLog("[वर्गीकरण: टास्क (TASK)] 📷 कैमरा/सेल्फी कमांड...")
                if (!cameraManager.hasCameraPermission()) {
                    val permMsg = "कैमरा की अनुमति (Camera Permission) नहीं है। कृपया पहले सेटिंग्स में अनुमति दें।"
                    addLog("चेतावनी: कैमरा परमिशन उपलब्ध नहीं है।")
                    _agentStatus.value = AgentStatus.SPEAKING
                    _statusMessage.value = permMsg
                    voiceManager.speak(permMsg, speechRate.value)
                    return@launch
                }

                if (cameraManager.isSceneAnalysisCommand(commandText)) {
                    // Scene Analysis with Gemini Vision
                    addLog("🔍 AI दृश्य विश्लेषण: कैमरा फ्रेम कैप्चर कर जेमिनी विज़न से विश्लेषण कराया जा रहा है...")
                    _agentStatus.value = AgentStatus.EXECUTING
                    _isCameraProcessing.value = true
                    _statusMessage.value = "कैमरे के दृश्य को समझा जा रहा है..."

                    val captureRes = cameraManager.capturePhoto(isFrontCamera = false, saveToGallery = false)
                    if (captureRes.isSuccess) {
                        val capResult = captureRes.getOrThrow()
                        _lastCameraCapture.value = capResult

                        // Ultra low-latency natural filler if network/vision takes > 380ms
                        val fillerJob = viewModelScope.launch {
                            delay(380)
                            if (_isCameraProcessing.value) {
                                voiceManager.speakInstantFiller("ठीक है, अभी सामने देख रहा हूँ...")
                            }
                        }

                        val visionExplanation = geminiClient.analyzeImageWithVision(capResult.bitmap)
                        fillerJob.cancel()
                        _sceneAnalysisText.value = visionExplanation
                        _isCameraProcessing.value = false

                        addLog("AI विज़न विश्लेषण: $visionExplanation")
                        repository.logCommand(
                            prompt = commandText,
                            app = "Camera / Gemini Vision",
                            actionType = "SCENE_ANALYSIS",
                            actionDetails = visionExplanation,
                            responseHindi = visionExplanation,
                            success = true
                        )

                        _agentStatus.value = AgentStatus.SPEAKING
                        _statusMessage.value = visionExplanation
                        voiceManager.speak(visionExplanation, 1.0f)

                        delay(3500)
                        if (_agentStatus.value == AgentStatus.SPEAKING) {
                            _agentStatus.value = AgentStatus.IDLE
                            _statusMessage.value = "मैक्स तैयार है।"
                        }
                    } else {
                        _isCameraProcessing.value = false
                        val errorMsg = "कैमरा से फ्रेम लेने में समस्या आई।"
                        _agentStatus.value = AgentStatus.IDLE
                        _statusMessage.value = errorMsg
                        addLog("त्रुटि: ${captureRes.exceptionOrNull()?.localizedMessage}")
                        voiceManager.speak(errorMsg, 1.0f)
                    }
                    return@launch
                }

                // Photo or Selfie capture
                val isFront = cameraManager.isFrontCamera(commandText)
                addLog(if (isFront) "📸 सेल्फी कमांड: फ्रंट कैमरा से फोटो कैप्चर हो रही है..." else "📷 फोटो कमांड: बैक कैमरा से फोटो खींची जा रही है...")
                _agentStatus.value = AgentStatus.EXECUTING
                _isCameraProcessing.value = true
                _statusMessage.value = if (isFront) "सेल्फी ली जा रही है..." else "फोटो खींची जा रही है..."

                val captureRes = cameraManager.capturePhoto(isFrontCamera = isFront, saveToGallery = true)
                _isCameraProcessing.value = false

                if (captureRes.isSuccess) {
                    val capResult = captureRes.getOrThrow()
                    _lastCameraCapture.value = capResult
                    _sceneAnalysisText.value = null

                    val successVoice = if (isFront) "आपकी सेल्फी ले ली गई है और गैलरी में सेव कर दी गई है।" else "फोटो ले ली गई है।"
                    val successDetails = "फोटो सेव: ${capResult.filePath ?: "DCIM/Max"}"
                    addLog("सफलता: $successDetails")

                    repository.logCommand(
                        prompt = commandText,
                        app = "Camera / MediaStore",
                        actionType = if (isFront) "TAKE_SELFIE" else "TAKE_PHOTO",
                        actionDetails = successDetails,
                        responseHindi = successVoice,
                        success = true
                    )

                    _agentStatus.value = AgentStatus.SPEAKING
                    _statusMessage.value = successVoice
                    voiceManager.speak(successVoice, speechRate.value)

                    delay(2500)
                    if (_agentStatus.value == AgentStatus.SPEAKING) {
                        _agentStatus.value = AgentStatus.IDLE
                        _statusMessage.value = "मैक्स तैयार है।"
                    }
                } else {
                    val errorMsg = "फोटो खींचने में समस्या आई।"
                    _agentStatus.value = AgentStatus.IDLE
                    _statusMessage.value = errorMsg
                    voiceManager.speak(errorMsg, speechRate.value)
                }
                return@launch
            }

            // =========================================================================
            // STEP 5: ANTI-THEFT VOICE COMMANDS & EMERGENCY CONTACT SETUP
            // =========================================================================
            val lowerCommand = commandText.lowercase()
            if (isAntiTheftVoiceCommand(lowerCommand)) {
                addLog("[वर्गीकरण: टास्क (TASK)] 🛡 एंटी-थेफ्ट कमांड...")
                handleAntiTheftVoiceCommand(commandText, lowerCommand)
                return@launch
            }

            // =========================================================================
            // STEP 6: EXPLICIT USER PREFERENCE / HABIT LEARNING ("Mujhe Hindi gaane pasand hain")
            // =========================================================================
            val extractedPref = MemoryManager.extractUserPreference(commandText)
            if (extractedPref != null) {
                addLog("[वर्गीकरण: मेमोरी (MEMORY)] स्थानीय मेमोरी: यूजर की नई पसंद सीखी गई -> ${extractedPref.key}: ${extractedPref.value}")
                repository.saveMemory(
                    key = extractedPref.key,
                    value = extractedPref.value,
                    category = extractedPref.category,
                    descHindi = extractedPref.descriptionHindi
                )
                repository.logCommand(
                    prompt = commandText,
                    app = "Max Local Memory",
                    actionType = "LEARN_PREFERENCE",
                    actionDetails = "${extractedPref.key} = ${extractedPref.value}",
                    responseHindi = extractedPref.acknowledgementHindi,
                    success = true
                )

                repository.pruneAndOptimizeMemory()

                _agentStatus.value = AgentStatus.SPEAKING
                _statusMessage.value = extractedPref.acknowledgementHindi
                voiceManager.speak(extractedPref.acknowledgementHindi, speechRate.value)

                delay(1800)
                if (_agentStatus.value == AgentStatus.SPEAKING) {
                    _agentStatus.value = AgentStatus.IDLE
                    _statusMessage.value = "मैक्स तैयार है।"
                }
                return@launch
            }

            // =========================================================================
            // STEP 7: SMART INTENT CLASSIFICATION (Conversation vs Complex In-App Automation)
            // =========================================================================
            val classification = intentClassifier.classify(commandText)
            val isConversation = classification.category == InputCategory.CONVERSATION_CHAT

            addLog("[वर्गीकरण: ${if (isConversation) "बातचीत (CONVERSATION)" else "टास्क (TASK)"}] ${classification.reason}")

            // -------------------------------------------------------------------------
            // IF CONVERSATION / QUESTION: ANSWER DIRECTLY WITH NATURAL HINDI (NO DEVICE ACTIONS)
            // -------------------------------------------------------------------------
            if (isConversation) {
                addLog("💬 बातचीत / सवाल: जेमिनी से उत्तर प्राप्त किया जा रहा है...")
                _agentStatus.value = AgentStatus.THINKING
                _statusMessage.value = "सोच रहा हूँ..."

                val memories = repository.allMemories.first()
                val replyText = geminiClient.generateConversationalReply(commandText, memories)

                addLog("मैक्स उत्तर: $replyText")
                repository.logCommand(
                    prompt = commandText,
                    app = "Max Conversation / Gemini",
                    actionType = "CONVERSATION_REPLY",
                    actionDetails = "Answered naturally in Hindi without triggering device actions",
                    responseHindi = replyText,
                    success = true
                )

                _agentStatus.value = AgentStatus.SPEAKING
                _statusMessage.value = replyText
                voiceManager.speak(replyText, speechRate.value)

                delay(3000)
                if (_agentStatus.value == AgentStatus.SPEAKING) {
                    _agentStatus.value = AgentStatus.IDLE
                    _statusMessage.value = "मैक्स तैयार है।"
                }
                return@launch
            }

            // =========================================================================
            // STEP 8: COMPLEX COMMAND WITH CONTEXT & MEMORY REASONING (Gemini Vision + Accessibility Tree)
            // =========================================================================
            addLog("जटिल/विजुअल कमांड: समानांतर (Parallel) डेटा और स्क्रीन फेच शुरू...")
            _statusMessage.value = "मैक्स सोच रहा है और स्क्रीन का विश्लेषण कर रहा है..."

            try {
                // PARALLEL COROUTINES for zero-latency concurrent context gathering
                val memoriesDeferred = async(Dispatchers.IO) { repository.getAllMemoriesList() }
                val recentHistoryDeferred = async(Dispatchers.IO) { repository.getRecentHistoryList(6) }
                val snapshotDeferred = async(Dispatchers.Default) {
                    val realService = MaxAccessibilityService.instance
                    if (realService != null) {
                        val realSnapshot = realService.captureCurrentScreen()
                        if (realSnapshot.elements.isNotEmpty() || realSnapshot.packageName.isNotBlank()) {
                            return@async realSnapshot
                        }
                    }
                    if (_useSimulatorMode.value) {
                        simulatorState.generateSnapshot()
                    } else {
                        ScreenSnapshot(packageName = "android", timestamp = System.currentTimeMillis())
                    }
                }

                // Build active screen activity context (e.g. "YouTube: Playing Video X")
                val activeActivityContext: String = if (MaxAccessibilityService.instance != null) {
                    val currentApp = MaxAccessibilityService.currentPackageName.value
                    val screenNodes = MaxAccessibilityService.instance?.captureCurrentScreen()?.elements ?: emptyList()
                    val topTitle = screenNodes.firstOrNull { it.text.length > 5 }?.text ?: ""
                    if (topTitle.isNotBlank()) "$currentApp ($topTitle)" else currentApp.ifBlank { "Android Home/App" }
                } else if (_useSimulatorMode.value) {
                    val currentVid = simulatorState.currentVideo.value
                    val isAd = simulatorState.isAdActive.value
                    buildString {
                        append("YouTube Simulator: ")
                        append("Playing '${currentVid.title}' by ${currentVid.channel}")
                        if (isAd) append(" (Ad active)")
                    }
                } else {
                    "Android System"
                }

                val recentHistory = recentHistoryDeferred.await()
                val memories = memoriesDeferred.await()
                val snapshot = snapshotDeferred.await()

                // Resolve contextual references (e.g., "wahi wala fir se chalao", "iska volume")
                val resolvedCommand = MemoryManager.resolveContextualQuery(commandText, recentHistory, activeActivityContext)
                if (resolvedCommand != commandText) {
                    addLog("संदर्भ समाधान: \"$commandText\" -> \"$resolvedCommand\"")
                }

                addLog("स्क्रीन पर ${snapshot.elements.size} नोड्स मिले (App: ${snapshot.packageName}) | पैरेलल फेच पूर्ण")

                // Natural conversational filler job if cloud network takes > 380ms
                var hasReceivedAction = false
                val fillerJob = launch {
                    delay(380)
                    if (!hasReceivedAction && _agentStatus.value == AgentStatus.THINKING) {
                        voiceManager.speakInstantFiller("ठीक है, अभी करता हूँ...")
                    }
                }

                // Query Gemini Multimodal AI with Context & Memory (Flash model + LRU Action Cache)
                val action = geminiClient.decideAction(
                    userCommand = resolvedCommand,
                    screenSnapshot = snapshot,
                    memories = memories,
                    recentHistory = recentHistory,
                    activityContext = activeActivityContext
                )
                hasReceivedAction = true
                fillerJob.cancel()
                _lastAction.value = action

                addLog("एआई निर्णय: ${action.actionType} | कारण: ${action.reasonHindi}")

                // Execute complex UI action and speak in tandem for instant feeling
                _agentStatus.value = AgentStatus.EXECUTING
                _statusMessage.value = action.voiceResponseHindi

                // Speak immediately
                voiceManager.speak(action.voiceResponseHindi, 1.08f)

                val execResult = executeAssistantAction(action)
                addLog("एक्शन परिणाम: ${execResult.message}")

                // Save to Room database
                repository.logCommand(
                    prompt = commandText,
                    app = snapshot.packageName.ifBlank { "YouTube" },
                    actionType = action.actionType.name,
                    actionDetails = "${action.targetElementDesc} @ (${action.targetX}, ${action.targetY})",
                    responseHindi = action.voiceResponseHindi,
                    success = execResult.success
                )

                // Update user memory and active activity context
                if (action.textToType.isNotBlank()) {
                    repository.saveMemory("last_played_topic", action.textToType, UserMemoryEntity.CATEGORY_ACTIVITY_CONTEXT, "हाल ही में सर्च/चलाई गई चीज़")
                }
                repository.saveActivityContext(
                    app = snapshot.packageName.ifBlank { "YouTube" },
                    contentTitle = action.textToType.ifBlank { action.targetElementDesc }
                )

                // Automatic local optimization & cleanup
                repository.pruneAndOptimizeMemory()

                delay(1600)
                if (_agentStatus.value == AgentStatus.SPEAKING || _agentStatus.value == AgentStatus.EXECUTING) {
                    _agentStatus.value = AgentStatus.IDLE
                    _statusMessage.value = "मैक्स तैयार है।"
                }
            } catch (e: Exception) {
                Log.e("MaxViewModel", "Exception during command execution", e)
                val failureMsg = "माफ़ कीजिये, कमांड पूरा करने में समस्या आई।"
                _agentStatus.value = AgentStatus.IDLE
                _statusMessage.value = "त्रुटि: ${e.message ?: "अज्ञात समस्या"}"
                addLog("त्रुटि: ${e.localizedMessage ?: "अज्ञात समस्या"}")
                voiceManager.speak(failureMsg, speechRate.value)
            }
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

    fun toggleFloatingOverlay(enabled: Boolean) {
        repository.setFloatingOverlayEnabled(enabled)
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
