package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.MaxDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules all active reminders and alarms from Room database when device reboots.
 */
class BootReceiver : BroadcastReceiver() {

    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        Log.i(tag, "Device rebooted, rescheduling active reminders...")

        val db = MaxDatabase.getInstance(context)
        val reminderManager = ReminderManager(context, db.reminderDao())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activeList = db.reminderDao().getActiveRemindersList(System.currentTimeMillis())
                Log.i(tag, "Found ${activeList.size} active reminders to reschedule.")
                for (reminder in activeList) {
                    reminderManager.scheduleSystemAlarm(reminder)
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to reschedule reminders on boot: ${e.localizedMessage}")
            }
        }
    }
}
