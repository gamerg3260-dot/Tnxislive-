package com.example.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MaxApplication
import com.example.overlay.SiriWaveformView
import com.example.ui.viewmodel.AgentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * System VoiceInteractionSession invoked when the user performs an Assist gesture
 * (Home button long-press or bottom corner swipe).
 */
class MaxVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val tag = "MaxAssistSession"
    private val sessionScope = CoroutineScope(Dispatchers.Main)
    private var stateJob: Job? = null
    private var siriWaveformView: SiriWaveformView? = null
    private var statusTextView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateContentView(): View {
        val density = context.resources.displayMetrics.density

        // Root container with slight dimming on backdrop
        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(120, 5, 8, 22)) // Subtly dimmed background
            setOnClickListener {
                // Tap outside dismisses assistant session
                hide()
            }
        }

        // Bottom card container
        val bottomContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val bg = GradientDrawable().apply {
                setColor(Color.argb(230, 15, 23, 42))
                cornerRadius = 28 * density
                setStroke((1.5f * density).toInt(), Color.argb(180, 0, 245, 255))
            }
            background = bg
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (20 * density).toInt())
            setOnClickListener {
                // Clicking the card itself re-triggers voice listening
                MaxApplication.instance.triggerVoiceListeningFromOverlay()
            }
        }

        val lpBottom = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins((16 * density).toInt(), 0, (16 * density).toInt(), (32 * density).toInt())
        }

        // Header Title
        val titleText = TextView(context).apply {
            text = "⚡ मैक्स डिजिटल असिस्टेंट (Max Assistant)"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        bottomContainer.addView(titleText)

        // Subtitle / Status Label
        statusTextView = TextView(context).apply {
            text = "सुन रहा हूँ... बोलिए (Listening...)"
            textSize = 12f
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(0, (4 * density).toInt(), 0, (8 * density).toInt())
        }
        bottomContainer.addView(statusTextView)

        // Waveform View
        val waveHeight = (68 * density).toInt()
        siriWaveformView = SiriWaveformView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                waveHeight
            )
        }
        bottomContainer.addView(siriWaveformView)

        rootLayout.addView(bottomContainer, lpBottom)
        return rootLayout
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i(tag, "MaxVoiceInteractionSession onShow with flags: $showFlags")

        // 1. Immediately trigger Max voice listening
        try {
            MaxApplication.instance.triggerVoiceListeningFromOverlay()
        } catch (e: Exception) {
            Log.e(tag, "Failed to trigger voice listening on show", e)
        }

        // 2. Start monitoring agent status & RMS
        stateJob?.cancel()
        stateJob = sessionScope.launch {
            var currentStatus = AgentStatus.IDLE
            var currentRms = 0f

            launch {
                MaxApplication.instance.overlayAgentStatus.collectLatest { status ->
                    currentStatus = status
                    siriWaveformView?.updateState(currentStatus, currentRms)
                    when (status) {
                        AgentStatus.LISTENING -> {
                            statusTextView?.text = "🎙️ सुन रहा हूँ... बोलिए (Listening...)"
                            statusTextView?.setTextColor(Color.parseColor("#00F5FF"))
                        }
                        AgentStatus.THINKING -> {
                            statusTextView?.text = "✨ विचार कर रहा हूँ... (Thinking...)"
                            statusTextView?.setTextColor(Color.parseColor("#A855F7"))
                        }
                        AgentStatus.SPEAKING -> {
                            statusTextView?.text = "🔊 मैक्स बोल रहा है... (Speaking...)"
                            statusTextView?.setTextColor(Color.parseColor("#10B981"))
                        }
                        AgentStatus.EXECUTING -> {
                            statusTextView?.text = "⚡ कमांड पूरी कर रहा हूँ... (Executing...)"
                            statusTextView?.setTextColor(Color.parseColor("#F59E0B"))
                        }
                        AgentStatus.ERROR -> {
                            statusTextView?.text = "⚠️ त्रुटि (Error)"
                            statusTextView?.setTextColor(Color.parseColor("#F43F5E"))
                        }
                        AgentStatus.IDLE -> {
                            statusTextView?.text = "मैक्स तैयार है"
                            statusTextView?.setTextColor(Color.parseColor("#94A3B8"))
                        }
                    }
                }
            }

            launch {
                MaxApplication.instance.overlaySpeechRms.collectLatest { rms ->
                    currentRms = rms
                    siriWaveformView?.updateState(currentStatus, currentRms)
                }
            }
        }
    }

    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
        Log.d(tag, "onHandleAssist called with context data")
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        Log.d(tag, "onHandleScreenshot called (screenshot received: ${screenshot != null})")
    }

    override fun onHide() {
        super.onHide()
        stateJob?.cancel()
        stateJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stateJob?.cancel()
        stateJob = null
    }
}
