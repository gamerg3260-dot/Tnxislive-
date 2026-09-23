package com.example.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.example.MaxApplication
import com.example.ui.viewmodel.AgentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Siri-Style Ambient Floating Glow Overlay Service.
 * Displays a non-intrusive, glowing bottom wave ribbon across apps.
 * Completely transparent background with zero touch blocking outside the strip.
 */
class MaxOverlayService : Service() {

    private val tag = "MaxOverlayService"
    private var windowManager: WindowManager? = null
    private var overlayContainer: FrameLayout? = null
    private var siriWaveformView: SiriWaveformView? = null
    private var params: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var statusCollectorJob: Job? = null
    private var hideJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(tag, "Overlay permission not granted. Stopping service.")
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val pillWidth = (300 * density).toInt()
        val pillHeight = (68 * density).toInt()

        // Critical: FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCH_MODAL allows all taps & scrolls outside to pass through freely!
        params = WindowManager.LayoutParams(
            pillWidth,
            pillHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (24 * density).toInt() // Margin from bottom edge
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

        siriWaveformView = SiriWaveformView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(siriWaveformView)

        // Allow user to tap the floating strip to trigger / toggle voice listening
        var startX = 0f
        var startY = 0f
        var isClick = true

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - startX)
                    val dy = Math.abs(event.rawY - startY)
                    if (dx > 15 || dy > 15) {
                        isClick = false
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        val app = application as? MaxApplication
                        app?.triggerVoiceListeningFromOverlay()
                    }
                    true
                }
                else -> false
            }
        }

        overlayContainer = root
        try {
            windowManager?.addView(overlayContainer, params)
            Log.i(tag, "Siri-style floating overlay attached successfully.")
        } catch (e: Exception) {
            Log.e(tag, "Error attaching overlay: ${e.message}", e)
        }

        observeAgentState()
    }

    private fun observeAgentState() {
        val app = application as? MaxApplication ?: return

        statusCollectorJob = serviceScope.launch {
            app.overlayAgentStatus.collectLatest { status ->
                siriWaveformView?.updateState(status, app.overlaySpeechRms.value)

                if (status == AgentStatus.IDLE) {
                    // Smoothly auto-fade out
                    hideJob?.cancel()
                    hideJob = launch {
                        delay(2200)
                        if (app.overlayAgentStatus.value == AgentStatus.IDLE) {
                            siriWaveformView?.visibility = View.VISIBLE
                        }
                    }
                } else {
                    hideJob?.cancel()
                    siriWaveformView?.visibility = View.VISIBLE
                }
            }
        }

        serviceScope.launch {
            app.overlaySpeechRms.collectLatest { rms ->
                if (app.overlayAgentStatus.value != AgentStatus.IDLE) {
                    siriWaveformView?.updateState(app.overlayAgentStatus.value, rms)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        statusCollectorJob?.cancel()
        hideJob?.cancel()
        if (overlayContainer != null) {
            try {
                windowManager?.removeView(overlayContainer)
            } catch (ignored: Exception) {}
            overlayContainer = null
        }
    }

    companion object {
        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
                val intent = Intent(context, MaxOverlayService::class.java)
                try {
                    context.startService(intent)
                } catch (e: Exception) {
                    Log.e("MaxOverlayService", "Failed to start service: ${e.message}")
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MaxOverlayService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e("MaxOverlayService", "Failed to stop service: ${e.message}")
            }
        }

        fun requestOverlayPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
}
