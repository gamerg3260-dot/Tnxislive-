package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MaxRepository(
    private val context: Context,
    private val maxDao: MaxDao
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("max_settings", Context.MODE_PRIVATE)

    private val _customApiKey = MutableStateFlow(prefs.getString("custom_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _gestureSpeedMs = MutableStateFlow(prefs.getLong("gesture_speed_ms", 120L))
    val gestureSpeedMs: StateFlow<Long> = _gestureSpeedMs.asStateFlow()

    private val _speechRate = MutableStateFlow(prefs.getFloat("speech_rate", 1.0f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    val recentHistory: Flow<List<CommandHistoryEntity>> = maxDao.getRecentHistory()
    val allMemories: Flow<List<UserMemoryEntity>> = maxDao.getAllMemories()

    fun getEffectiveApiKey(): String {
        val custom = _customApiKey.value.trim()
        if (custom.isNotBlank()) return custom
        val buildKey = BuildConfig.GEMINI_API_KEY
        return if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString("custom_api_key", key.trim()).apply()
        _customApiKey.value = key.trim()
    }

    fun setGestureSpeedMs(ms: Long) {
        prefs.edit().putLong("gesture_speed_ms", ms).apply()
        _gestureSpeedMs.value = ms
    }

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat("speech_rate", rate).apply()
        _speechRate.value = rate
    }

    suspend fun logCommand(
        prompt: String,
        app: String,
        actionType: String,
        actionDetails: String,
        responseHindi: String,
        success: Boolean
    ): Long {
        return maxDao.insertHistory(
            CommandHistoryEntity(
                userPrompt = prompt,
                detectedApp = app,
                actionType = actionType,
                actionDetails = actionDetails,
                responseHindi = responseHindi,
                success = success
            )
        )
    }

    suspend fun getRecentHistoryList(limit: Int = 10): List<CommandHistoryEntity> {
        return maxDao.getRecentHistoryList(limit)
    }

    suspend fun getAllMemoriesList(): List<UserMemoryEntity> {
        return maxDao.getAllMemoriesList()
    }

    suspend fun saveActivityContext(app: String, contentTitle: String, details: String = "") {
        val summary = if (contentTitle.isNotBlank()) "$app ($contentTitle)" else app
        saveMemory(
            key = "active_activity_context",
            value = summary,
            category = UserMemoryEntity.CATEGORY_ACTIVITY_CONTEXT,
            descHindi = "वर्तमान स्क्रीन संदर्भ: $summary"
        )
    }

    suspend fun pruneAndOptimizeMemory() {
        MemoryManager.pruneAndOptimize(maxDao)
    }

    suspend fun saveMemory(key: String, value: String, category: String, descHindi: String = "") {
        maxDao.saveMemory(
            UserMemoryEntity(
                key = key,
                value = value,
                category = category,
                descriptionHindi = descHindi,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteMemory(key: String) {
        maxDao.deleteMemory(key)
    }

    suspend fun clearHistory() {
        maxDao.clearHistory()
    }

    suspend fun initializeDefaultMemoriesIfEmpty() {
        val existing = maxDao.getMemoryByKey("assistant_name")
        if (existing == null) {
            saveMemory("assistant_name", "Max (मैक्स)", UserMemoryEntity.CATEGORY_IDENTITY, "एआई सहायक का नाम")
            saveMemory("creator_name", "Ganesh Sahani (गणेश साहनी)", UserMemoryEntity.CATEGORY_IDENTITY, "निर्माता और मालिक (Creator & Owner)")
            saveMemory("language", "Hindi (हिंदी)", UserMemoryEntity.CATEGORY_PREFERENCE, "मुख्य बातचीत की भाषा")
            saveMemory("favorite_app", "YouTube", UserMemoryEntity.CATEGORY_PREFERENCE, "पसंदीदा मनोरंजन ऐप")
            saveMemory("ad_preference", "Auto-skip ads immediately", UserMemoryEntity.CATEGORY_PREFERENCE, "विज्ञापन आते ही तुरंत स्किप करना")
            saveMemory("user_habit", "Likes comedy and tech videos on YouTube", UserMemoryEntity.CATEGORY_PREFERENCE, "यूजर की वीडियो पसंद")
        }
    }
}
