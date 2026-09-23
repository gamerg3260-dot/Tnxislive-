package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.ActionType
import com.example.data.model.AssistantAction
import com.example.data.model.ExecutionResult
import com.example.data.model.ScreenSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

class MaxAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Log.i(TAG, "MaxAccessibilityService connected and ready.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != currentPackageName.value) {
            _currentPackageName.value = pkg
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "MaxAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceActive.value = false
        Log.i(TAG, "MaxAccessibilityService destroyed.")
    }

    fun captureCurrentScreen(): ScreenSnapshot {
        val metrics: DisplayMetrics = resources.displayMetrics
        var root: AccessibilityNodeInfo? = null

        // 1. Try standard rootInActiveWindow
        try {
            root = rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "[ScreenCapture] Failed to get rootInActiveWindow", e)
        }

        // 2. Fallback to inspecting active / application windows in reverse order (topmost first)
        if (root == null) {
            try {
                val windowList = windows
                for (window in windowList.reversed()) {
                    if (window.isActive || window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                        val windowRoot = window.root
                        if (windowRoot != null) {
                            root = windowRoot
                            Log.d(TAG, "[ScreenCapture] Found active window root via windows fallback: id=${window.id}, type=${window.type}")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[ScreenCapture] Fallback window inspection failed", e)
            }
        }

        // 3. Force accessibility node refresh to guarantee ZERO STALE DATA
        try {
            root?.refresh()
        } catch (ignored: Exception) {}

        val snapshot = ScreenTreeParser.parseRootNode(root, metrics)
        Log.i(TAG, "[ScreenFreshness] Fresh screen snapshot captured: Package='${snapshot.packageName}', Elements=${snapshot.elements.size}, Res=${snapshot.screenWidth}x${snapshot.screenHeight}, Timestamp=${snapshot.timestamp}")
        return snapshot
    }

    suspend fun performScroll(isDown: Boolean): Boolean {
        val metrics: DisplayMetrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val startY = if (isDown) metrics.heightPixels * 0.72f else metrics.heightPixels * 0.28f
        val endY = if (isDown) metrics.heightPixels * 0.28f else metrics.heightPixels * 0.72f
        return dispatchSwipeGesture(centerX, startY, centerX, endY, 260L)
    }

    suspend fun executeAction(action: AssistantAction): ExecutionResult {
        return when (action.actionType) {
            ActionType.TAP, ActionType.SKIP_AD -> {
                val success = dispatchTapGesture(action.targetX.toFloat(), action.targetY.toFloat())
                ExecutionResult(
                    success = success,
                    message = if (success) "टैप किया गया (${action.targetX}, ${action.targetY})" else "टैप असफल रहा",
                    action = action
                )
            }
            ActionType.DOUBLE_TAP -> {
                val tap1 = dispatchTapGesture(action.targetX.toFloat(), action.targetY.toFloat())
                kotlinx.coroutines.delay(120)
                val tap2 = dispatchTapGesture(action.targetX.toFloat(), action.targetY.toFloat())
                ExecutionResult(
                    success = tap1 && tap2,
                    message = "डबल टैप किया गया (${action.targetX}, ${action.targetY})",
                    action = action
                )
            }
            ActionType.LONG_PRESS -> {
                val success = dispatchLongPressGesture(action.targetX.toFloat(), action.targetY.toFloat())
                ExecutionResult(
                    success = success,
                    message = "लॉन्ग प्रेस किया गया",
                    action = action
                )
            }
            ActionType.SWIPE, ActionType.SCROLL_DOWN, ActionType.SCROLL_UP -> {
                val success = dispatchSwipeGesture(
                    action.targetX.toFloat(),
                    action.targetY.toFloat(),
                    action.endX.toFloat(),
                    action.endY.toFloat(),
                    action.durationMs.coerceAtLeast(150L)
                )
                ExecutionResult(
                    success = success,
                    message = "स्वाइप/स्क्रॉल किया गया",
                    action = action
                )
            }
            ActionType.TYPE -> {
                val success = typeIntoCurrentFocus(action.textToType, action.targetX, action.targetY)
                ExecutionResult(
                    success = success,
                    message = if (success) "\"${action.textToType}\" टाइप किया गया" else "टाइप असफल",
                    action = action
                )
            }
            ActionType.OPEN_APP -> {
                val pkg = if (action.targetAppName.contains(".")) {
                    action.targetAppName
                } else if (action.targetAppName.equals("YouTube", ignoreCase = true)) {
                    "com.google.android.youtube"
                } else {
                    action.targetAppName
                }
                val launched = launchAppPackage(pkg)
                ExecutionResult(
                    success = launched,
                    message = if (launched) "$pkg खोला जा रहा है" else "$pkg खोलने में विफल",
                    action = action
                )
            }
            ActionType.BACK -> {
                val success = performGlobalAction(GLOBAL_ACTION_BACK)
                ExecutionResult(success = success, message = "पीछे गया (Back)", action = action)
            }
            ActionType.HOME -> {
                val success = performGlobalAction(GLOBAL_ACTION_HOME)
                ExecutionResult(success = success, message = "होम स्क्रीन पर गया", action = action)
            }
            ActionType.SPEAK_ONLY, ActionType.NONE -> {
                ExecutionResult(success = true, message = action.voiceResponseHindi, action = action)
            }
        }
    }

    private suspend fun dispatchTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 75L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureDeferred(gesture)
    }

    private suspend fun dispatchLongPressGesture(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 650L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureDeferred(gesture)
    }

    private suspend fun dispatchSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureDeferred(gesture)
    }

    private suspend fun dispatchGestureDeferred(gesture: GestureDescription): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                deferred.complete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                deferred.complete(false)
            }
        }

        val started = dispatchGesture(gesture, callback, null)
        if (!started) return false

        return withTimeoutOrNull(2000L) {
            deferred.await()
        } ?: false
    }

    private suspend fun typeIntoCurrentFocus(text: String, x: Int, y: Int): Boolean {
        // If coordinate provided, tap first to focus
        if (x > 0 && y > 0) {
            dispatchTapGesture(x.toFloat(), y.toFloat())
            kotlinx.coroutines.delay(200)
        }

        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditable(root)

        return if (focused != null && focused.isEditable) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val res = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
            res
        } else {
            false
        }
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child)
            if (found != null) return found
        }
        return null
    }

    fun launchAppPackage(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: $packageName", e)
            false
        }
    }

    companion object {
        private const val TAG = "MaxAccessibilityService"

        @Volatile
        var instance: MaxAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _currentPackageName = MutableStateFlow("")
        val currentPackageName: StateFlow<String> = _currentPackageName.asStateFlow()

        fun isAccessibilitySettingsEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${MaxAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true) ||
                    componentName.contains(MaxAccessibilityService::class.java.simpleName)) {
                    return true
                }
            }
            return false
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
