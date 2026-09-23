package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.MaxDarkBg
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxSecondary
import com.example.ui.theme.MaxSuccess
import com.example.ui.theme.MaxSurfaceBorder
import com.example.ui.theme.MaxSurfaceElevated
import com.example.ui.theme.MaxWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.voice.SpeakerEnrollmentManager

@Composable
fun VoiceEnrollmentDialog(
    enrollmentManager: SpeakerEnrollmentManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnrolled by enrollmentManager.isEnrolled.collectAsState()
    val currentStep by enrollmentManager.currentEnrollmentStep.collectAsState()
    val isRecording by enrollmentManager.isRecordingSample.collectAsState()
    val rmsLevel by enrollmentManager.currentRmsLevel.collectAsState()

    var sampleProgress by remember { mutableFloatStateOf(0f) }
    var statusFeedback by remember { mutableStateOf("") }
    var isSuccessCompleted by remember { mutableStateOf(false) }

    val phrases = SpeakerEnrollmentManager.ENROLLMENT_PHRASES
    val activePhraseIndex = currentStep.coerceIn(0, phrases.size - 1)
    val activePhrase = phrases[activePhraseIndex]

    Dialog(
        onDismissRequest = {
            if (!isRecording) {
                enrollmentManager.cancelCurrentRecording()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF130F26)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(MaxPrimary, Color(0xFF06B6D4), Color(0xFFA855F7))
                )
            ),
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF2563EB))),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Voice Fingerprint",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "वॉयस एनरोलमेंट (Voice Lock)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "सिर्फ आपकी आवाज़ पर चलेगा मैक्स",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            enrollmentManager.cancelCurrentRecording()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isSuccessCompleted || isEnrolled) "प्रमाणीकरण पूरा हुआ" else "सैंपल ${activePhraseIndex + 1} / ${phrases.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${((currentStep.toFloat() / phrases.size) * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (currentStep.toFloat() / phrases.size).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF00F5FF),
                        trackColor = Color(0xFF221A45)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isSuccessCompleted || (isEnrolled && currentStep >= phrases.size)) {
                    // Success View
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Enrolled",
                            tint = MaxSuccess,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "🎉 वॉयस फ़िंगरप्रिंट रजिस्टर हो गया!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "अब मैक्स केवल आपकी आवाज से दिए गए कमांड्स को प्रोसेस करेगा। कोई अन्य व्यक्ति इसे सक्रिय नहीं कर सकेगा।",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaxSuccess),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("हो गया (Done)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                } else {
                    // Active Phrase Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E173D)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(MaxPrimary.copy(alpha = 0.5f), Color(0xFF06B6D4)))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "कृपया नीचे दिया गया वाक्य बोलें:",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "\"${activePhrase.phraseTextHindi}\"",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00F5FF),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "(${activePhrase.phraseTextEnglish})",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 ${activePhrase.hint}",
                                fontSize = 11.sp,
                                color = Color(0xFFFBBF24)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Live Wave / Mic Recorder Button
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isRecording) 1.22f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_scale"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(90.dp)
                    ) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size((80 * pulseScale * (1f + rmsLevel)).dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                Color(0xFF00F5FF).copy(alpha = 0.5f),
                                                Color(0xFF7C3AED).copy(alpha = 0.2f),
                                                Color.Transparent
                                            )
                                        ),
                                        CircleShape
                                    )
                            )
                        }

                        Button(
                            onClick = {
                                if (!isRecording) {
                                    statusFeedback = ""
                                    sampleProgress = 0f
                                    enrollmentManager.recordSampleForPhrase(
                                        phraseIndex = activePhraseIndex,
                                        onSampleProgress = { sampleProgress = it },
                                        onSampleCompleted = { success, msg ->
                                            statusFeedback = msg
                                            if (success && activePhraseIndex == phrases.size - 1) {
                                                isSuccessCompleted = true
                                            }
                                        }
                                    )
                                }
                            },
                            enabled = !isRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color(0xFFEF4444) else Color(0xFF7C3AED)
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("record_phrase_btn")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = "Record",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isRecording) "सुन रहा हूँ... बोलिए (${(sampleProgress * 100).toInt()}%)" else "माइक दबाकर बोलें",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) Color(0xFF00F5FF) else TextPrimary
                    )

                    if (statusFeedback.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = statusFeedback,
                            fontSize = 11.sp,
                            color = if (statusFeedback.contains("धीमी") || statusFeedback.contains("त्रुटि")) MaxWarning else MaxSuccess,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                enrollmentManager.resetEnrollment()
                                enrollmentManager.startNewEnrollment()
                                statusFeedback = "शुरुआत से री-सेट किया गया"
                            },
                            enabled = !isRecording,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("री-स्टार्ट", fontSize = 12.sp, color = TextSecondary)
                        }

                        Button(
                            onClick = {
                                statusFeedback = ""
                                sampleProgress = 0f
                                enrollmentManager.recordSampleForPhrase(
                                    phraseIndex = activePhraseIndex,
                                    onSampleProgress = { sampleProgress = it },
                                    onSampleCompleted = { success, msg ->
                                        statusFeedback = msg
                                        if (success && activePhraseIndex == phrases.size - 1) {
                                            isSuccessCompleted = true
                                        }
                                    }
                                )
                            },
                            enabled = !isRecording,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.5f).height(42.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("रिकॉर्ड करें", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
