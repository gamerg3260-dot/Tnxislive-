package com.example.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Factory service that produces a [MaxVoiceInteractionSession] whenever the system
 * triggers an Assist gesture (e.g. Home button long press or swipe from bottom corner).
 */
class MaxVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return MaxVoiceInteractionSession(this)
    }
}
