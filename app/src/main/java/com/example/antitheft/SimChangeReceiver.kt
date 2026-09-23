package com.example.antitheft

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SimChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("SimChangeReceiver", "Received event: $action")

        if (action == "android.intent.action.SIM_STATE_CHANGED" ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AntiTheftManager.getInstance(context).checkAndHandleSimChange()
                } catch (e: Exception) {
                    Log.e("SimChangeReceiver", "Error checking SIM state", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
