package com.example.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.MaxApplication

/**
 * BroadcastReceiver for android.intent.action.PHONE_STATE.
 * Automatically catches incoming call state changes and delivers them to CallControlManager.
 */
class CallStateReceiver : BroadcastReceiver() {

    private val tag = "CallStateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        Log.i(tag, "Incoming Phone State: $stateStr | Number: $incomingNumber")

        val app = context.applicationContext as? MaxApplication ?: return
        val callManager = app.callControlManager ?: return

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                callManager.handleTelephonyCallState(TelephonyManager.CALL_STATE_RINGING, incomingNumber)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                callManager.handleTelephonyCallState(TelephonyManager.CALL_STATE_OFFHOOK, incomingNumber)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                callManager.handleTelephonyCallState(TelephonyManager.CALL_STATE_IDLE, null)
            }
        }
    }
}
