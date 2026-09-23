package com.example.antitheft

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MaxSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: continue
            val body = sms.displayMessageBody ?: sms.messageBody ?: continue
            val cleanBody = body.trim().uppercase()

            if (cleanBody.contains("MAX LOCATE") ||
                cleanBody.contains("MAX TRACK") ||
                cleanBody.contains("MAX LOCK") ||
                cleanBody.contains("MAX SIREN") ||
                cleanBody.contains("MAX ALARM") ||
                cleanBody.contains("MAX STOP")
            ) {
                Log.i("MaxSmsReceiver", "Intercepted remote Max security SMS command: $cleanBody from $sender")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AntiTheftManager.getInstance(context).handleRemoteSmsCommand(sender, body)
                    } catch (e: Exception) {
                        Log.e("MaxSmsReceiver", "Error handling remote SMS command", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
