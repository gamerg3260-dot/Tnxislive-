package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AgentStatus
import kotlin.math.PI
import kotlin.math.sin

/**
 * Modern Siri-Style Floating Bottom Neon Ribbon.
 * - Shows vivid iridescent wave ribbons when Listening, Thinking, or Speaking.
 * - Completely transparent background with zero touch obstruction.
 * - Auto-fades when command completes.
 */
@Composable
fun SiriBottomWaveBar(
    agentStatus: AgentStatus,
    speechRms: Float,
    statusMessage: String,
    onBarClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = agentStatus != AgentStatus.IDLE

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(250)) + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut(tween(400)) + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "siri_wave")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase_animation"
        )

        val rotationAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "aurora_rotation"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient Neon Glow behind the pill
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(56.dp)
                    .blur(18.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF00F5FF).copy(alpha = 0.35f),
                                Color(0xFF9333EA).copy(alpha = 0.45f),
                                Color(0xFFEC4899).copy(alpha = 0.35f)
                            )
                        ),
                        RoundedCornerShape(32.dp)
                    )
            )

            // Sleek Floating Capsule
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color(0xEB0C091D),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF38BDF8),
                            Color(0xFFA855F7),
                            Color(0xFFF43F5E),
                            Color(0xFF38BDF8)
                        )
                    )
                ),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBarClicked
                    )
                    .testTag("siri_floating_pill")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Mic / Pulse Indicator
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF7C3AED), Color(0xFF2563EB))
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (agentStatus == AgentStatus.LISTENING) Icons.Default.Mic else Icons.Default.Stop,
                            contentDescription = "Voice State",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Center: Animated Dynamic Siri Neon Waves
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(42.dp)) {
                            val w = size.width
                            val h = size.height
                            val midY = h / 2f

                            val rmsFactor = (0.25f + speechRms * 1.6f).coerceIn(0.2f, 2.0f)
                            val waveHeight = (h * 0.35f) * rmsFactor

                            val speed = when (agentStatus) {
                                AgentStatus.THINKING -> 2.4f
                                AgentStatus.LISTENING -> 1.5f
                                AgentStatus.SPEAKING -> 1.2f
                                else -> 0.8f
                            }

                            // Layer 1: Electric Cyan & Blue Wave
                            val path1 = Path()
                            path1.moveTo(0f, midY)
                            for (x in 0..w.toInt() step 6) {
                                val progress = x / w
                                val env = sin(progress * PI).toFloat()
                                val y = midY + sin((progress * 3 * PI + phase * speed).toDouble()).toFloat() * waveHeight * env
                                path1.lineTo(x.toFloat(), y)
                            }
                            drawPath(
                                path = path1,
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF00F5FF), Color(0xFF3B82F6), Color(0xFF06B6D4))
                                ),
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Layer 2: Neon Purple & Pink Counter-Wave
                            val path2 = Path()
                            path2.moveTo(0f, midY)
                            for (x in 0..w.toInt() step 6) {
                                val progress = x / w
                                val env = sin(progress * PI).toFloat()
                                val y = midY + sin((progress * 4 * PI - phase * (speed * 1.3f) + 1.2).toDouble()).toFloat() * (waveHeight * 0.8f) * env
                                path2.lineTo(x.toFloat(), y)
                            }
                            drawPath(
                                path = path2,
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFF8B5CF6))
                                ),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Layer 3: Harmonic Shimmer (Emerald / Violet)
                            val path3 = Path()
                            path3.moveTo(0f, midY)
                            for (x in 0..w.toInt() step 8) {
                                val progress = x / w
                                val env = sin(progress * PI).toFloat()
                                val y = midY + sin((progress * 2 * PI + phase * (speed * 0.8f) + 2.4).toDouble()).toFloat() * (waveHeight * 0.5f) * env
                                path3.lineTo(x.toFloat(), y)
                            }
                            drawPath(
                                path = path3,
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF10B981), Color(0xFF6366F1), Color(0xFF14B8A6))
                                ),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Right: Concise Status Badge
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val stateText = when (agentStatus) {
                            AgentStatus.LISTENING -> "सुन रहा हूँ..."
                            AgentStatus.THINKING -> "विचार..."
                            AgentStatus.SPEAKING -> "बोल रहा हूँ..."
                            AgentStatus.EXECUTING -> "जारी है..."
                            AgentStatus.ERROR -> "त्रुटि..."
                            AgentStatus.IDLE -> ""
                            else -> ""
                        }
                        Text(
                            text = stateText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = "Tap to cancel",
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}
