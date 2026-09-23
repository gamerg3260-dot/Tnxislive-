package com.example.antitheft

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MaxDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w("MaxDeviceAdmin", "Device unlock password/PIN failed attempt recorded!")
        CoroutineScope(Dispatchers.IO).launch {
            AntiTheftManager.getInstance(context).handlePasswordFailed()
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.i("MaxDeviceAdmin", "Device unlock password succeeded. Resetting attempt counter.")
        CoroutineScope(Dispatchers.IO).launch {
            AntiTheftManager.getInstance(context).handlePasswordSucceeded()
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("MaxDeviceAdmin", "Max Anti-Theft Device Admin Protection Enabled.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w("MaxDeviceAdmin", "Max Anti-Theft Device Admin Protection Disabled.")
    }
}
