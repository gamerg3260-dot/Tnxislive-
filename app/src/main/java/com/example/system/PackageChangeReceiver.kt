package com.example.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.router.GenericAppLauncher

class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val packageName = intent.data?.schemeSpecificPart ?: return

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                Log.i(TAG, "NEW_APP_DETECTED: Installed package '$packageName' - Max updated app list automatically")
                GenericAppLauncher.invalidateCacheAndRefresh(context)
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.i(TAG, "APP_UPDATED_DETECTED: Updated package '$packageName' - Max refreshed app list automatically")
                GenericAppLauncher.invalidateCacheAndRefresh(context)
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                Log.i(TAG, "APP_REMOVED_DETECTED: Uninstalled package '$packageName' - Max updated app list automatically")
                GenericAppLauncher.invalidateCacheAndRefresh(context)
            }
        }
    }

    companion object {
        private const val TAG = "PackageChangeReceiver"
    }
}
