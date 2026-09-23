package com.example.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MaxApplication

/**
 * System VoiceInteractionSession invoked when the user performs an Assist gesture
 * (Home button long-press or bottom corner swipe).
 */
class MaxVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val tag = "MaxAssistSession"
    private var statusTextView: TextView? = null

    override fun onCreateContentView(): View {
        val density = context.resources.displayMetrics.density

        // Root container with slight dimming on backdrop
        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(120, 5, 8, 22))
            setOnClickListener {
                hide()
            }
        }

        // Bottom card container
        val bottomContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val bg = GradientDrawable().apply {
                setColor(Color.argb(230, 15, 23, 42))
                cornerRadius = 24 * density
                setStroke((1.5f * density).toInt(), Color.argb(180, 0, 245, 255))
            }
            background = bg
            setPadding((20 * density).toInt(), (18 * density).toInt(), (20 * density).toInt(), (24 * density).toInt())
            setOnClickListener {
                MaxApplication.instance.triggerVoiceListening()
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
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        bottomContainer.addView(titleText)

        // Subtitle / Status Label
        statusTextView = TextView(context).apply {
            text = "🎙️ सुन रहा हूँ... बोलिए (Listening...)"
            textSize = 13f
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
        }
        bottomContainer.addView(statusTextView)

        rootLayout.addView(bottomContainer, lpBottom)
        return rootLayout
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i(tag, "MaxVoiceInteractionSession onShow with flags: $showFlags")

        try {
            MaxApplication.instance.triggerVoiceListening()
        } catch (e: Exception) {
            Log.e(tag, "Failed to trigger voice listening on show", e)
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
}

