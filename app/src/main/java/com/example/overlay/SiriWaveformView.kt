package com.example.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.example.ui.viewmodel.AgentStatus
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-performance hardware-accelerated Siri-style neon wave & glowing orb visualizer.
 * Renders distinct dynamic wave ribbons and aurora gradients for:
 * - LISTENING: Reactive neon cyan & purple acoustic wave
 * - THINKING: Swirling iridescent rainbow glow
 * - SPEAKING: Harmonious emerald & cyan oscillating ribbons
 * - IDLE: Graceful alpha fade-out
 */
class SiriWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var agentStatus: AgentStatus = AgentStatus.IDLE
    private var speechRms: Float = 0f
    private var phase: Float = 0f
    private var targetAlpha: Float = 0f
    private var currentAlpha: Float = 0f

    private val wavePaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val wavePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val wavePaint3 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val glowBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val path1 = Path()
    private val path2 = Path()
    private val path3 = Path()
    private val backgroundRect = RectF()

    private var animator: ValueAnimator? = null

    init {
        animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float

                // Smooth alpha fade transition
                currentAlpha += (targetAlpha - currentAlpha) * 0.15f
                if (Math.abs(targetAlpha - currentAlpha) < 0.01f) {
                    currentAlpha = targetAlpha
                }
                invalidate()
            }
            start()
        }
    }

    fun updateState(status: AgentStatus, rms: Float = 0f) {
        agentStatus = status
        speechRms = rms.coerceIn(0f, 1f)

        targetAlpha = when (status) {
            AgentStatus.IDLE -> 0.0f
            AgentStatus.LISTENING,
            AgentStatus.THINKING,
            AgentStatus.EXECUTING,
            AgentStatus.SPEAKING,
            AgentStatus.ERROR -> 1.0f
            else -> 0.0f
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backgroundRect.set(16f, 8f, w - 16f, h - 8f)

        // Setup Siri neon gradients
        val siriColors1 = intArrayOf(
            Color.parseColor("#00F5FF"), // Neon Cyan
            Color.parseColor("#7928CA"), // Neon Violet
            Color.parseColor("#FF0080"), // Neon Pink
            Color.parseColor("#0070F3")  // Royal Blue
        )
        val shader1 = LinearGradient(0f, 0f, w.toFloat(), 0f, siriColors1, null, Shader.TileMode.CLAMP)
        wavePaint1.shader = shader1
        pillBorderPaint.shader = shader1

        val siriColors2 = intArrayOf(
            Color.parseColor("#38BDF8"),
            Color.parseColor("#A855F7"),
            Color.parseColor("#EC4899")
        )
        wavePaint2.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, siriColors2, null, Shader.TileMode.CLAMP)

        val siriColors3 = intArrayOf(
            Color.parseColor("#10B981"),
            Color.parseColor("#06B6D4"),
            Color.parseColor("#6366F1")
        )
        wavePaint3.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, siriColors3, null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentAlpha <= 0.01f) return

        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f
        val cornerRadius = h / 2f

        // 1. Draw glowing pill container background
        glowBackgroundPaint.color = Color.argb((currentAlpha * 220).toInt(), 13, 10, 28)
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, glowBackgroundPaint)

        // Draw neon border
        pillBorderPaint.alpha = (currentAlpha * 200).toInt()
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, pillBorderPaint)

        // 2. Draw Siri wave ribbons based on state
        val activeRmsMultiplier = (0.25f + speechRms * 1.5f).coerceIn(0.2f, 2.0f)
        val maxWaveHeight = (h * 0.28f) * activeRmsMultiplier

        path1.reset()
        path2.reset()
        path3.reset()

        val startX = 36f
        val endX = w - 36f
        val length = endX - startX

        path1.moveTo(startX, midY)
        path2.moveTo(startX, midY)
        path3.moveTo(startX, midY)

        val speed = when (agentStatus) {
            AgentStatus.THINKING -> 2.5f
            AgentStatus.LISTENING -> 1.5f
            AgentStatus.SPEAKING -> 1.2f
            else -> 0.8f
        }

        val step = 8f
        var x = startX
        while (x <= endX) {
            val progress = (x - startX) / length
            // Hanning window envelope to taper wave smoothly at both ends
            val envelope = (sin(progress * PI)).toFloat()

            // Wave 1
            val y1 = midY + sin((progress * 3 * PI + phase * speed).toDouble()).toFloat() * maxWaveHeight * envelope
            path1.lineTo(x, y1)

            // Wave 2 (counter phase)
            val y2 = midY + sin((progress * 4 * PI - phase * (speed * 1.3f) + 1.2).toDouble()).toFloat() * (maxWaveHeight * 0.75f) * envelope
            path2.lineTo(x, y2)

            // Wave 3 (harmonic shimmer)
            val y3 = midY + sin((progress * 2 * PI + phase * (speed * 0.8f) + 2.4).toDouble()).toFloat() * (maxWaveHeight * 0.5f) * envelope
            path3.lineTo(x, y3)

            x += step
        }

        val waveAlpha = (currentAlpha * 255).toInt()
        wavePaint1.alpha = waveAlpha
        wavePaint2.alpha = (waveAlpha * 0.85f).toInt()
        wavePaint3.alpha = (waveAlpha * 0.70f).toInt()

        canvas.drawPath(path3, wavePaint3)
        canvas.drawPath(path2, wavePaint2)
        canvas.drawPath(path1, wavePaint1)

        // 3. Draw concise status text under wave
        val statusLabel = when (agentStatus) {
            AgentStatus.LISTENING -> "मैक्स सुन रहा है..."
            AgentStatus.THINKING -> "विचार कर रहा हूँ..."
            AgentStatus.SPEAKING -> "मैक्स बोल रहा है..."
            AgentStatus.EXECUTING -> "कमांड पूरी हो रही है..."
            AgentStatus.ERROR -> "त्रुटि..."
            AgentStatus.IDLE -> ""
            else -> ""
        }

        if (statusLabel.isNotEmpty()) {
            textPaint.alpha = (currentAlpha * 230).toInt()
            canvas.drawText(statusLabel, w / 2f, h - 14f, textPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
