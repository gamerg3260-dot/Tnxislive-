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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class MaxVoiceManager(
    private val context: Context,
    val speakerEnrollmentManager: SpeakerEnrollmentManager = SpeakerEnrollmentManager(context),
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

    private val audioBufferStream = ByteArrayOutputStream()

    init {
        initTts()
        initSpeechRecognizer()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val hindiLocale = Locale("hi", "IN")
                val res = textToSpeech?.setLanguage(hindiLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.language = Locale.getDefault()
                }

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
                Log.i(tag, "TTS initialized successfully.")
            } else {
                Log.e(tag, "TTS initialization failed with status: $status")
            }
        }
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
                        synchronized(audioBufferStream) {
                            audioBufferStream.reset()
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _speechRms.value = (rmsdB.coerceAtLeast(0f) / 10f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        if (buffer != null) {
                            synchronized(audioBufferStream) {
                                if (audioBufferStream.size() < 16000 * 2 * 4) { // Up to 4 seconds
                                    audioBufferStream.write(buffer)
                                }
                            }
                        }
                    }

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
                            // On-Device Speaker Verification
                            val audioBytes = synchronized(audioBufferStream) { audioBufferStream.toByteArray() }
                            val isSpeakerValid = verifySpeakerIfEnrolled(audioBytes)

                            if (isSpeakerValid) {
                                _transcription.value = text
                                onCommandReceived(text)
                            } else {
                                Log.w(tag, "Speaker verification rejected. Command ignored.")
                                _transcription.value = "⚠️ अपरिचित आवाज़ — कमांड अनदेखा किया गया"
                            }
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

    private fun verifySpeakerIfEnrolled(audioBytes: ByteArray): Boolean {
        if (!speakerEnrollmentManager.isVerificationEnabled.value || !speakerEnrollmentManager.isEnrolled.value) {
            return true
        }

        if (audioBytes.size < 3200) {
            // Buffer too small or bypassed, allow command
            return true
        }

        val shortCount = audioBytes.size / 2
        val shortBuffer = ShortArray(shortCount)
        ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer)

        val result = speakerEnrollmentManager.verifySpeakerAudio(shortBuffer, shortCount)
        Log.i(tag, "Verification Result: isMatch=${result.isMatch}, score=${result.similarity}, threshold=${result.threshold}")
        return result.isMatch
    }

    fun startListening() {
        stopSpeaking()
        _transcription.value = "सुन रहा हूँ..."
        _isListening.value = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "मैक्स को अपनी बात कहें (जैसे: 'YouTube खोलो', 'फोन लॉक करो')")
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
            _isListening.value = false
            _speechRms.value = 0f
        } catch (e: Exception) {
            Log.e(tag, "stopListening error", e)
        }
    }

    fun speak(text: String, speechRate: Float = 1.0f) {
        if (!isTtsReady || textToSpeech == null) {
            Log.w(tag, "TTS is not ready yet.")
            return
        }
        try {
            textToSpeech?.setSpeechRate(speechRate.coerceIn(0.7f, 1.5f))
            textToSpeech?.setPitch(1.0f)
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "max_tts_${System.currentTimeMillis()}")
            }
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "max_tts_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(tag, "speak error", e)
        }
    }

    fun speakInstantFiller(fillerText: String = "जी, अभी करता हूँ...") {
        if (!isTtsReady || textToSpeech == null) return
        try {
            textToSpeech?.setSpeechRate(1.0f)
            textToSpeech?.setPitch(1.0f)
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "max_filler_${System.currentTimeMillis()}")
            }
            textToSpeech?.speak(fillerText, TextToSpeech.QUEUE_FLUSH, params, "max_filler_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(tag, "speakInstantFiller error", e)
        }
    }

    fun stopSpeaking() {
        try {
            if (textToSpeech?.isSpeaking == true) {
                textToSpeech?.stop()
                _isSpeaking.value = false
            }
        } catch (e: Exception) {
            Log.e(tag, "stopSpeaking error", e)
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {
            Log.e(tag, "destroy error", e)
        }
    }
}
