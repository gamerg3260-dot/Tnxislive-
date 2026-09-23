package com.example.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.example.MainActivity
import com.example.MaxApplication

/**
 * Transparent trampoline activity to handle android.intent.action.ASSIST and
 * android.intent.action.VOICE_ASSIST intents.
 */
class AssistantActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("AssistantActivity", "AssistantActivity launched via Assist Intent")

        try {
            // Trigger overlay listening if application is running
            MaxApplication.instance.triggerVoiceListeningFromOverlay()
        } catch (e: Exception) {
            // Fallback: Launch MainActivity with auto-listening extra
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_AUTO_START_LISTENING", true)
            }
            startActivity(intent)
        }

        finish()
    }
}
