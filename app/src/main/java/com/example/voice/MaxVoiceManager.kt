package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class MaxVoiceManager(
    private val context: Context,
    private val onCommandReceived: (String) -> Unit
) {
    private val tag = "MaxVoiceManager"

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechRms = MutableStateFlow(0f)
    val speechRms: StateFlow<Float> = _speechRms.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    init {
        initTts()
        initSpeechRecognizer()
    }

    private fun initTts() {
        val googleTtsPackage = "com.google.android.tts"

        val onInitListener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupNaturalTtsVoice()
            } else {
                Log.e(tag, "Failed to initialize TextToSpeech with primary engine. Attempting default fallback.")
                if (textToSpeech == null || !isTtsReady) {
                    textToSpeech = TextToSpeech(context) { fallbackStatus ->
                        if (fallbackStatus == TextToSpeech.SUCCESS) {
                            setupNaturalTtsVoice()
                        }
                    }
                }
            }
        }

        try {
            textToSpeech = TextToSpeech(context, onInitListener, googleTtsPackage)
        } catch (e: Exception) {
            Log.w(tag, "Google TTS engine instantiation fallback: ${e.message}")
            textToSpeech = TextToSpeech(context, onInitListener)
        }
    }

    private fun setupNaturalTtsVoice() {
        val hindiLocale = Locale("hi", "IN")
        val res = textToSpeech?.setLanguage(hindiLocale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(tag, "Hindi TTS voice missing or not supported on this device. Using default locale.")
            textToSpeech?.language = Locale.getDefault()
        }

        // Configure optimal AudioAttributes for clear, warm, presence-rich voice assistant speech
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            textToSpeech?.setAudioAttributes(audioAttributes)
        } catch (e: Exception) {
            Log.w(tag, "Could not set AudioAttributes: ${e.message}")
        }

        // Auto-select highest quality natural/neural Hindi voice from Google TTS
        try {
            val availableVoices = textToSpeech?.voices
            if (!availableVoices.isNullOrEmpty()) {
                val bestHindiVoice = availableVoices
                    .filter { voice ->
                        val lang = voice.locale.language
                        val country = voice.locale.country
                        lang.equals("hi", ignoreCase = true) || (lang.equals("en", ignoreCase = true) && country.equals("IN", ignoreCase = true))
                    }
                    .minByOrNull { voice ->
                        val name = voice.name.lowercase()
                        when {
                            name.contains("hi-in-x-hie-local") -> 1
                            name.contains("hi-in-x-hid-local") -> 2
                            name.contains("hi-in-x-hic-local") -> 3
                            name.contains("hi-in-x-hia-local") -> 4
                            name.contains("neural") || name.contains("studio") || name.contains("wavenet") -> 5
                            name.contains("hi-in-x-hie-network") -> 6
                            name.contains("hi-in-x-hid-network") -> 7
                            voice.quality >= Voice.QUALITY_HIGH -> 8
                            !voice.isNetworkConnectionRequired -> 9
                            else -> 20
                        }
                    }

                if (bestHindiVoice != null) {
                    textToSpeech?.voice = bestHindiVoice
                    Log.i(tag, "Selected natural Hindi voice: ${bestHindiVoice.name} (quality: ${bestHindiVoice.quality})")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Voice selection fallback: ${e.message}")
        }

        // Warm, natural pitch and speed (energetic and friendly, not flat or robotic)
        textToSpeech?.setPitch(1.02f)
        textToSpeech?.setSpeechRate(1.03f)

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })
        isTtsReady = true
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(tag, "Speech recognition is not available on this device.")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Normalize roughly 0..10 dB to 0..1
                        _speechRms.value = (rmsdB.coerceAtLeast(0f) / 10f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _speechRms.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _speechRms.value = 0f
                        Log.d(tag, "Speech recognition error code: $error")
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _speechRms.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _transcription.value = text
                            onCommandReceived(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull() ?: ""
                        if (partial.isNotBlank()) {
                            _transcription.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting up SpeechRecognizer", e)
        }
    }

    fun startListening() {
        stopSpeaking()
        _transcription.value = "सुन रहा हूँ..."
        _isListening.value = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "मैक्स को अपनी बात कहें (जैसे: 'YouTube खोलो', 'Ad skip karo')")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "startListening failed", e)
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(tag, "stopListening failed", e)
        }
        _isListening.value = false
        _speechRms.value = 0f
    }

    fun speak(textHindi: String, speechRate: Float = 1.05f) {
        if (!isTtsReady || textHindi.isBlank()) return
        _isSpeaking.value = true
        textToSpeech?.setSpeechRate(speechRate)
        textToSpeech?.speak(textHindi, TextToSpeech.QUEUE_FLUSH, null, "max_utterance_${System.currentTimeMillis()}")
    }

    /**
     * Ultra low-latency streaming TTS chunk speaker.
     * Starts speaking the first complete sentence chunk immediately (QUEUE_FLUSH),
     * and queues subsequent chunks seamlessly (QUEUE_ADD) without audio stutter.
     */
    fun speakStreamChunk(chunkHindi: String, isFirstChunk: Boolean = false, speechRate: Float = 1.08f) {
        if (!isTtsReady || chunkHindi.isBlank()) return
        _isSpeaking.value = true
        textToSpeech?.setSpeechRate(speechRate)
        val queueMode = if (isFirstChunk) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        textToSpeech?.speak(chunkHindi, queueMode, null, "stream_chunk_${System.currentTimeMillis()}")
    }

    /**
     * Action-oriented conversational filler (e.g., "ठीक है, अभी करता हूँ...", "जी, तुरंत कर रहा हूँ...")
     * played ONLY when Gemini cloud API network latency exceeds 380ms to provide responsive feedback.
     */
    fun speakInstantFiller(fillerText: String = "ठीक है, अभी करता हूँ...", speechRate: Float = 1.15f) {
        if (!isTtsReady || _isSpeaking.value) return
        _isSpeaking.value = true
        textToSpeech?.setSpeechRate(speechRate)
        textToSpeech?.speak(fillerText, TextToSpeech.QUEUE_FLUSH, null, "filler_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        if (isTtsReady) {
            textToSpeech?.stop()
        }
        _isSpeaking.value = false
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(tag, "Error destroying SpeechRecognizer", e)
        }
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down TextToSpeech", e)
        }
    }
}
