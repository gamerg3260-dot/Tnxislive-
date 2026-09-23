package com.example.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log
import com.example.MaxApplication

/**
 * System RecognitionService declared for voice-interaction-service.
 */
class MaxRecognitionService : RecognitionService() {

    private val tag = "MaxRecognitionService"

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.d(tag, "onStartListening requested")
        try {
            MaxApplication.instance.triggerVoiceListeningFromOverlay()
        } catch (e: Exception) {
            Log.e(tag, "Failed to start listening", e)
        }
    }

    override fun onCancel(listener: Callback?) {
        Log.d(tag, "onCancel requested")
    }

    override fun onStopListening(listener: Callback?) {
        Log.d(tag, "onStopListening requested")
    }
}
