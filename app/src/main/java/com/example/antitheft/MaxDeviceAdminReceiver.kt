package com.example.antitheft

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DeviceAdminReceiver for Max Anti-Theft Protection.
 * Listens for failed PIN/Password attempts and remote security administration events.
 */
class MaxDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "MaxDeviceAdmin"
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "=======================================================")
        Log.w(TAG, "⚠️ [MaxDeviceAdmin] onPasswordFailed() CALLBACK FIRED!")
        Log.w(TAG, "Intent Action: ${intent.action}")
        Log.w(TAG, "=======================================================")

        // Show immediate visual confirmation Toast on main thread
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context.applicationContext,
                "⚠️ [MAX ANTI-THEFT] गलत PIN / पासवर्ड प्रयास डिटेक्ट हुआ!",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Delegate to AntiTheftManager in background coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AntiTheftManager.getInstance(context).handlePasswordFailed()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing handlePasswordFailed()", e)
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.i(TAG, "✓ [MaxDeviceAdmin] onPasswordSucceeded() -> Phone unlocked. Resetting failure counter.")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AntiTheftManager.getInstance(context).handlePasswordSucceeded()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing handlePasswordSucceeded()", e)
            }
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "✓ [MaxDeviceAdmin] Max Device Administrator Protection has been ENABLED by user.")
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context.applicationContext,
                "✓ [MAX] डिवाइस एडमिन सुरक्षा सक्रिय हो गई है!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "⚠️ [MaxDeviceAdmin] Max Device Administrator Protection DISABLED by user.")
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context.applicationContext,
                "⚠️ [MAX] डिवाइस एडमिन सुरक्षा निष्क्रिय की गई!",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
