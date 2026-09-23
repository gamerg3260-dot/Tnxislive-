package com.example.gemini

import android.util.Log
import android.util.LruCache
import com.example.data.model.AssistantAction

/**
 * High-speed in-memory LRU cache for recurring screen patterns and common voice intents.
 * Gives 0ms instant response without cloud network roundtrips for frequent commands.
 */
object GeminiActionCache {
    private const val TAG = "GeminiActionCache"
    private const val MAX_CACHE_SIZE = 60

    // Key format: "packageName::commandHash::elementSignature"
    private val lruCache = object : LruCache<String, AssistantAction>(MAX_CACHE_SIZE) {}

    fun get(packageName: String, userCommand: String, topElementsSig: String): AssistantAction? {
        val key = buildKey(packageName, userCommand, topElementsSig)
        val cached = lruCache.get(key)
        if (cached != null) {
            Log.i(TAG, "Cache HIT for action: $key -> ${cached.actionType} (${cached.voiceResponseHindi})")
        }
        return cached
    }

    fun put(packageName: String, userCommand: String, topElementsSig: String, action: AssistantAction) {
        // Only cache deterministic user actions (not dynamic long text generations or error states)
        if (action.actionType.name.isNotBlank() && action.voiceResponseHindi.isNotBlank()) {
            val key = buildKey(packageName, userCommand, topElementsSig)
            lruCache.put(key, action)
            Log.d(TAG, "Cached action plan for: $key")
        }
    }

    fun clear() {
        lruCache.evictAll()
    }

    private fun buildKey(packageName: String, userCommand: String, topElementsSig: String): String {
        val cleanCmd = userCommand.lowercase().trim().replace(Regex("[^a-zA-Z0-9\\s\\u0900-\\u097F]"), "")
        return "$packageName::$cleanCmd::$topElementsSig"
    }
}
