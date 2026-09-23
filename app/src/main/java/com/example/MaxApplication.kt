package com.example

import android.app.Application
import com.example.data.local.MaxDatabase
import com.example.data.local.MaxRepository
import com.example.gemini.GeminiClient
import com.example.simulator.YouTubeSimulatorState
import com.example.ui.viewmodel.AgentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MaxApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: MaxDatabase
        private set

    lateinit var repository: MaxRepository
        private set

    lateinit var geminiClient: GeminiClient
        private set

    val simulatorState = YouTubeSimulatorState()

    var callControlManager: com.example.call.CallControlManager? = null

    var onVoiceTriggerRequested: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = MaxDatabase.getInstance(this)
        repository = MaxRepository(this, database.maxDao())

        geminiClient = GeminiClient {
            repository.getEffectiveApiKey()
        }

        applicationScope.launch {
            repository.initializeDefaultMemoriesIfEmpty()
            com.example.router.GenericAppLauncher.getInstalledLaunchableApps(this@MaxApplication, forceFresh = true)
        }

        try {
            val receiver = com.example.system.PackageChangeReceiver()
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
                addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            registerReceiver(receiver, filter)
        } catch (e: Exception) {
            android.util.Log.w("MaxApplication", "Dynamic PackageChangeReceiver registration notice", e)
        }
    }

    fun triggerVoiceListening() {
        onVoiceTriggerRequested?.invoke()
    }

    companion object {
        lateinit var instance: MaxApplication
            private set
    }
}
