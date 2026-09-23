package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxSecondary
import com.example.ui.theme.MaxSuccess
import com.example.ui.theme.MaxWarning
import com.example.ui.viewmodel.AgentStatus

@Composable
fun VoiceSphere(
    agentStatus: AgentStatus,
    speechRms: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val outerPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outer_pulse_scale"
    )

    // Base colors according to state
    val (sphereColors, statusLabel, icon) = when (agentStatus) {
        AgentStatus.IDLE -> Triple(
            listOf(MaxPrimary, Color(0xFF4C1D95)),
            "टैप करें या 'Hey Max' कहें",
            Icons.Default.Mic
        )
        AgentStatus.LISTENING -> Triple(
            listOf(MaxSecondary, Color(0xFF0284C7), MaxPrimary),
            "सुन रहा हूँ... (Listening)",
            Icons.Default.Mic
        )
        AgentStatus.THINKING -> Triple(
            listOf(MaxWarning, Color(0xFFD97706), MaxPrimary),
            "सोच रहा हूँ... (Reasoning with Gemini)",
            Icons.Default.Psychology
        )
        AgentStatus.EXECUTING -> Triple(
            listOf(MaxSuccess, Color(0xFF059669), MaxSecondary),
            "जेस्चर निष्पादित हो रहा है (Gesturing)",
            Icons.Default.TouchApp
        )
        AgentStatus.SPEAKING -> Triple(
            listOf(Color(0xFF38BDF8), MaxPrimary, Color(0xFF6366F1)),
            "मैक्स बोल रहा है... (Speaking Hindi)",
            Icons.Default.VolumeUp
        )
        AgentStatus.ERROR -> Triple(
            listOf(Color(0xFFEF4444), Color(0xFF991B1B)),
            "त्रुटि (Error)",
            Icons.Default.MicOff
        )
    }

    val dynamicScale = if (agentStatus == AgentStatus.LISTENING) {
        (1f + (speechRms * 0.4f)).coerceIn(1f, 1.4f)
    } else if (agentStatus == AgentStatus.THINKING || agentStatus == AgentStatus.SPEAKING) {
        pulseScale
    } else {
        1f
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(170.dp)
        ) {
            // Outermost glowing ripple
            if (agentStatus == AgentStatus.LISTENING || agentStatus == AgentStatus.THINKING || agentStatus == AgentStatus.SPEAKING) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(outerPulseScale)
                        .clip(CircleShape)
                        .background(sphereColors.first().copy(alpha = 0.15f))
                )
            }

            // Middle ripple
            if (agentStatus != AgentStatus.IDLE) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(dynamicScale)
                        .clip(CircleShape)
                        .background(sphereColors.first().copy(alpha = 0.25f))
                        .border(1.5.dp, sphereColors.first().copy(alpha = 0.5f), CircleShape)
                )
            }

            // Main Sphere Core
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(105.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(sphereColors))
                    .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 55.dp),
                        onClick = onClick
                    )
                    .testTag("voice_sphere_button")
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = statusLabel,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            color = sphereColors.first()
        )
    }
}
