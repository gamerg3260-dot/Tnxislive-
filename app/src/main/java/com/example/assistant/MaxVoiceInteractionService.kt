package com.example.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * System VoiceInteractionService entry point for Android Default Digital Assistant.
 * When user selects Max in Settings -> Default apps -> Assist app,
 * Android binds to this service.
 */
class MaxVoiceInteractionService : VoiceInteractionService() {

    private val tag = "MaxVoiceInteractionSvc"

    override fun onReady() {
        super.onReady()
        Log.i(tag, "MaxVoiceInteractionService onReady: Max is actively registered as system Assistant")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(tag, "MaxVoiceInteractionService onShutdown")
    }
}
