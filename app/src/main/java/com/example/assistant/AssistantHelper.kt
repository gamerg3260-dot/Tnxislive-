package com.example.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.util.Log

object AssistantHelper {

    private const val TAG = "AssistantHelper"

    /**
     * Checks whether Max is currently selected as the active Default Digital Assistant.
     */
    fun isMaxDefaultAssistant(context: Context): Boolean {
        return try {
            val serviceSetting = Settings.Secure.getString(
                context.contentResolver,
                "voice_interaction_service"
            )
            val assistantSetting = Settings.Secure.getString(
                context.contentResolver,
                "assistant"
            )
            val pkg = context.packageName

            (serviceSetting != null && serviceSetting.contains(pkg)) ||
                    (assistantSetting != null && assistantSetting.contains(pkg))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking default assistant status", e)
            false
        }
    }

    /**
     * Opens Android System Settings page to allow user to choose Max as Default Digital Assistant.
     */
    fun openAssistantSettings(context: Context): Boolean {
        val intentsToTry = mutableListOf<Intent>()

        // 1. Direct Voice Input & Assist Settings
        intentsToTry.add(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))

        // 2. Custom Android voice input action
        intentsToTry.add(Intent("android.settings.VOICE_INPUT_SETTINGS"))

        // 3. Manage Default Apps Settings (Android 7.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intentsToTry.add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }

        // 4. Application Details Settings
        intentsToTry.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )

        // 5. Fallback general Settings
        intentsToTry.add(Intent(Settings.ACTION_SETTINGS))

        for (intent in intentsToTry) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch intent: ${intent.action}", e)
            }
        }
        return false
    }
}
