package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.MaxApplication
import com.example.data.local.MaxDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * BroadcastReceiver triggered by AlarmManager at the exact reminder/alarm timestamp.
 * Displays high-priority notification with sound & vibration, and speaks reminder in Hindi via TTS.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.example.reminder.ACTION_ALARM_TRIGGER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
        const val CHANNEL_ID = "max_reminders_channel"
    }

    private val tag = "AlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "रिमाइंडर"
        val type = intent.getStringExtra(EXTRA_REMINDER_TYPE) ?: "REMINDER"

        Log.i(tag, "Alarm triggered! ID: $reminderId | Title: $title | Type: $type")

        // 1. Mark triggered in Room database
        val db = MaxDatabase.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (reminderId > 0) {
                    db.reminderDao().markTriggered(reminderId)
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to mark reminder as triggered in DB: ${e.localizedMessage}")
            }
        }

        // 2. Hindi reminder text
        val spokenText = if (type.equals("ALARM", ignoreCase = true)) {
            "अलार्म बज रहा है! आपने $title के लिए अलार्म लगाया था।"
        } else {
            "नमस्ते! आपने बोला था अब $title का टाइम हो गया है।"
        }

        // 3. Try speech via app's voice manager or standalone TTS fallback
        speakReminderText(context, spokenText)

        // 4. Show Notification
        showNotification(context, reminderId.toInt(), title, spokenText, type)
    }

    private fun speakReminderText(context: Context, text: String) {
        val app = context.applicationContext as? MaxApplication
        val voiceManager = app?.callControlManager?.let { null } // We can check viewmodel or direct TTS
        
        // Use standalone reliable TTS fallback for background broadcast
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.setSpeechRate(0.95f)
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reminder_$text")
            }
        }
    }

    private fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        type: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "मैक्स अलार्म & रिमाइंडर्स",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "मैक्स वॉयस असिस्टेंट के अलार्म और याद दिलाने वाले नोटिफ़िकेशन"
                enableLights(true)
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notifTitle = if (type.equals("ALARM", ignoreCase = true)) "⏰ मैक्स अलार्म" else "🔔 मैक्स रिमाइंडर"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(notifTitle)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(if (notificationId != 0) notificationId else 1001, builder.build())
    }
}
