package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.CallStatus
import com.example.call.IncomingCallInfo
import com.example.ui.theme.MaxDarkBg
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxSecondary
import com.example.ui.theme.MaxSuccess
import com.example.ui.theme.MaxSurfaceBorder
import com.example.ui.theme.MaxSurfaceElevated
import com.example.ui.theme.MaxWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * High-visibility call alert card displayed on screen whenever an incoming call is ringing
 * or when Max autonomous AI agent is attending the call.
 */
@Composable
fun IncomingCallAlertCard(
    callInfo: IncomingCallInfo?,
    isWaitingForVoice: Boolean,
    onAnswerClick: () -> Unit,
    onRejectClick: () -> Unit,
    onMaxAttendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = callInfo != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (callInfo == null) return@AnimatedVisibility

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val ringScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        val isRinging = callInfo.status == CallStatus.RINGING
        val isAnswered = callInfo.status == CallStatus.ANSWERED || callInfo.status == CallStatus.OFFHOOK

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1035)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    colors = if (isRinging) listOf(Color(0xFFE11D48), MaxPrimary) else listOf(MaxSuccess, Color(0xFF065F46))
                ),
                width = 2.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("incoming_call_alert_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRinging) Color(0xFFE11D48).copy(alpha = 0.25f) else MaxSuccess.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isRinging) Color(0xFFE11D48) else MaxSuccess
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isRinging) Icons.Default.Call else Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (isRinging) Color(0xFFFF5252) else MaxSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRinging) "📞 इनकमिंग कॉल बज रही है..." else if (callInfo.isAgentAttending) "🤖 मैक्स AI बात कर रहा है (Speaker)" else "🟢 कॉल कनेक्टेड",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRinging) Color(0xFFFF8A80) else MaxSuccess
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Avatar & Pulse
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(72.dp)
                ) {
                    if (isRinging) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(ringScale)
                                .background(
                                    Color(0xFFE11D48).copy(alpha = 0.2f),
                                    CircleShape
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(MaxPrimary, Color(0xFF6B21A8))
                                ),
                                CircleShape
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Caller Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Caller Display Name
                Text(
                    text = callInfo.displayIdentifier,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                if (callInfo.contactName != null && callInfo.phoneNumber.isNotBlank()) {
                    Text(
                        text = callInfo.phoneNumber,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Voice Listening Hint Window
                if (isRinging) {
                    Surface(
                        color = Color(0xFF0F0B1E),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaxSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Mic",
                                tint = if (isWaitingForVoice) MaxSecondary else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isWaitingForVoice) {
                                    "वॉयस कमांड सुनें: \"उठा लो\", \"काट दो\", या \"मैक्स तुम बात करो\""
                                } else {
                                    "अनाउंसमेंट जारी है... (सामान्य रिंगटोन)"
                                },
                                fontSize = 11.sp,
                                color = if (isWaitingForVoice) MaxSecondary else TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3 Action Buttons (Answer, Reject, Max Attend)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Answer Button
                        Button(
                            onClick = onAnswerClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("answer_call_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Answer",
                                        modifier = Modifier.size(18.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("उठा लो", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text("Voice: 'उठा लो'", fontSize = 9.sp, color = Color(0xFFDCFCE7))
                            }
                        }

                        // 2. Reject Button
                        Button(
                            onClick = onRejectClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reject_call_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CallEnd,
                                        contentDescription = "Reject",
                                        modifier = Modifier.size(18.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("काट दो", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text("Voice: 'काट दो'", fontSize = 9.sp, color = Color(0xFFFEE2E2))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Autonomous Max Call Agent (Part 3 architecture trigger)
                    Button(
                        onClick = onMaxAttendClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4338CA)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("agent_attend_call_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = "AI Agent",
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "🤖 मैक्स तुम बात करो (AI Attendant)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "वॉयस कमांड: 'मैक्स तुम बात करो' (कॉल आंसर + AI वार्तालाप)",
                                    fontSize = 10.sp,
                                    color = Color(0xFFC7D2FE)
                                )
                            }
                        }
                    }
                } else if (isAnswered) {
                    // Call is answered or attended by Max
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (callInfo.isAgentAttending) {
                                "मैक्स कॉलर से बात कर रहा है... स्पीकरफ़ोन सक्रिय है।"
                            } else {
                                "कॉल चालू है।"
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF86EFAC),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onRejectClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("end_call_btn")
                        ) {
                            Icon(imageVector = Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("कॉल समाप्त करें (End Call)", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
