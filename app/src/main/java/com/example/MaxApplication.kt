package com.example

import android.app.Application
import com.example.data.local.MaxDatabase
import com.example.data.local.MaxRepository
import com.example.gemini.GeminiClient
import com.example.simulator.YouTubeSimulatorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    var onOverlayVoiceTriggerRequested: (() -> Unit)? = null

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
        }
    }

    fun triggerVoiceListeningFromOverlay() {
        onOverlayVoiceTriggerRequested?.invoke()
    }

    companion object {
        lateinit var instance: MaxApplication
            private set
    }
}
