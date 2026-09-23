package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.MaxAccessibilityService
import com.example.ui.components.CallControlPermissionCard
import com.example.ui.theme.MaxDarkBg
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxPrimaryContainer
import com.example.ui.theme.MaxSecondary
import com.example.ui.theme.MaxSuccess
import com.example.ui.theme.MaxSurfaceBorder
import com.example.ui.theme.MaxSurfaceElevated
import com.example.ui.theme.MaxWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MaxViewModel

import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import com.example.assistant.AssistantHelper
import com.example.ui.components.VoiceEnrollmentDialog

@Composable
fun SettingsScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customApiKey by viewModel.customApiKey.collectAsState()
    val isAccessibilityActive by viewModel.isAccessibilityEnabled.collectAsState()
    val gestureSpeed by viewModel.gestureSpeedMs.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()

    val enrollmentManager = viewModel.speakerEnrollmentManager
    val isVoiceEnrolled by enrollmentManager.isEnrolled.collectAsState()
    val isVoiceVerificationEnabled by enrollmentManager.isVerificationEnabled.collectAsState()
    var showVoiceEnrollmentDialog by remember { mutableStateOf(false) }

    var isDefaultAssistant by remember { mutableStateOf(AssistantHelper.isMaxDefaultAssistant(context)) }

    // Re-check assistant status when screen is viewed
    androidx.compose.runtime.DisposableEffect(Unit) {
        isDefaultAssistant = AssistantHelper.isMaxDefaultAssistant(context)
        onDispose { }
    }

    var apiKeyInput by remember(customApiKey) { mutableStateOf(customApiKey) }
    var tempSpeechRate by remember(speechRate) { mutableFloatStateOf(speechRate) }
    var tempGestureSpeed by remember(gestureSpeed) { mutableFloatStateOf(gestureSpeed.toFloat()) }

    if (showVoiceEnrollmentDialog) {
        VoiceEnrollmentDialog(
            enrollmentManager = enrollmentManager,
            onDismiss = { showVoiceEnrollmentDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaxDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaxPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "सेटिंग्स और डायग्नोस्टिक्स",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Configure Gemini API, Accessibility & Voice Controls",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // 1. Gemini API Key Configuration
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Api,
                        contentDescription = "Gemini",
                        tint = MaxSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gemini 3.5 Flash API Key",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "AI Studio Secrets पैनल में 'GEMINI_API_KEY' जोड़ें, या यहाँ अपना Google AI Studio API Key पेस्ट करें:",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    placeholder = { Text("AIzaSy... (या खाली छोड़ें - डिफ़ॉल्ट इंजन चालू रहेगा)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = MaxPrimary,
                        unfocusedBorderColor = MaxSurfaceBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            viewModel.setCustomApiKey(apiKeyInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaxPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("save_api_key_btn")
                    ) {
                        Text("सेव करें", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Android Accessibility Service Controls
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Accessibility",
                            tint = if (isAccessibilityActive) MaxSuccess else MaxWarning,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Max Accessibility Service",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                if (isAccessibilityActive) MaxSuccess.copy(alpha = 0.2f) else MaxWarning.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isAccessibilityActive) "सक्रिय (Active)" else "निष्क्रिय (Inactive)",
                            fontSize = 11.sp,
                            color = if (isAccessibilityActive) MaxSuccess else MaxWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "यह सर्विस स्क्रीन के टेक्स्ट, बटन व कोऑर्डिनेट्स पढ़ती है और इंसान की तरह फिंगर टैप/स्वाइप भेजती है।",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { MaxAccessibilityService.openAccessibilitySettings(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isAccessibilityActive) MaxPrimaryContainer else MaxPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_accessibility_settings_btn")
                ) {
                    Text(
                        text = if (isAccessibilityActive) "सिस्टम सेटिंग्स खोलें" else "एक्सेसिबिलिटी परमिशन सक्षम करें",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3.5. Voice Enrollment (Speaker Recognition / Owner Lock)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(MaxPrimary.copy(alpha = 0.6f), Color(0xFF00F5FF).copy(alpha = 0.4f))
                )
            ),
            modifier = Modifier.fillMaxWidth().testTag("voice_enrollment_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Voice Enrollment",
                            tint = if (isVoiceEnrolled) Color(0xFF00F5FF) else MaxWarning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "वॉयस एनरोलमेंट (Owner Voice Lock)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isVoiceEnrolled) "✅ आपकी आवाज़ रजिस्टर्ड है" else "⚠️ आवाज़ रजिस्टर नहीं है",
                                fontSize = 11.sp,
                                color = if (isVoiceEnrolled) MaxSuccess else MaxWarning
                            )
                        }
                    }

                    Switch(
                        checked = isVoiceVerificationEnabled,
                        onCheckedChange = { enabled ->
                            enrollmentManager.setVerificationEnabled(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00F5FF)
                        ),
                        modifier = Modifier.testTag("voice_verification_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "मैक्स सिर्फ आपकी आवाज़ (5 सैंपल फ्रैसेस) की ऑन-डिवाइस कोसाइन सिमिलैरिटी मैच करेगा। किसी अन्य व्यक्ति की आवाज़ पर कमांड्स अनदेखी रहेंगी।",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            enrollmentManager.startNewEnrollment()
                            showVoiceEnrollmentDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVoiceEnrolled) Color(0xFF1E293B) else Color(0xFF2563EB)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("enroll_voice_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isVoiceEnrolled) "आवाज़ री-ट्रेन करें (Re-train)" else "आवाज़ रजिस्टर करें (Enroll)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (isVoiceEnrolled) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                enrollmentManager.resetEnrollment()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("रीसेट", fontSize = 12.sp, color = Color(0xFFF43F5E))
                        }
                    }
                }
            }
        }

        // 3.8. Default Digital Assistant Role (Android System Assistant)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        if (isDefaultAssistant) Color(0xFF10B981) else Color(0xFF8B5CF6),
                        if (isDefaultAssistant) Color(0xFF00F5FF) else Color(0xFFEC4899)
                    )
                )
            ),
            modifier = Modifier.fillMaxWidth().testTag("default_assistant_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isDefaultAssistant) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                            contentDescription = "Default Assistant",
                            tint = if (isDefaultAssistant) MaxSuccess else Color(0xFFA855F7),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "डिफ़ॉल्ट डिजिटल असिस्टेंट (Default Assistant)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isDefaultAssistant) "✅ मैक्स सिस्टम का डिफ़ॉल्ट असिस्टेंट है" else "⚠️ मैक्स डिफ़ॉल्ट असिस्टेंट सेट नहीं है",
                                fontSize = 11.sp,
                                color = if (isDefaultAssistant) MaxSuccess else Color(0xFF38BDF8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "मैक्स को Android का डिफ़ॉल्ट डिजिटल असिस्टेंट सेट करें, ताकि Google Assistant की तरह:",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaxDarkBg.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "• 🔘 होम बटन लॉन्ग-प्रेस (3-Button Nav)",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "• 📱 स्क्रीन के कोने से स्वाइप जेस्चर (Gesture Nav)",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "• ⚡ तीनों तरीके साथ काम करेंगे: होम/जेस्चर + फ्लोटिंग वेव + 'Hey Max'",
                        fontSize = 11.sp,
                        color = Color(0xFF00F5FF)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        AssistantHelper.openAssistantSettings(context)
                        // Trigger check
                        isDefaultAssistant = AssistantHelper.isMaxDefaultAssistant(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDefaultAssistant) Color(0xFF1E293B) else Color(0xFF7C3AED)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_assistant_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDefaultAssistant) "असिस्टेंट सेटिंग्स बदलें (Change Assistant Settings)" else "डिफ़ॉल्ट असिस्टेंट सेट करें (Set as Default Assistant)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // 4. Call Control & Voice Answer/Reject Card
        CallControlPermissionCard(
            onSimulateCall = { name, number ->
                viewModel.simulateIncomingCall(name, number)
            }
        )

        // 5. Voice & Speech Settings
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Voice",
                        tint = MaxPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "हिंदी वॉइस स्पीड (Speech Rate): ${String.format("%.1fx", tempSpeechRate)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Slider(
                    value = tempSpeechRate,
                    onValueChange = {
                        tempSpeechRate = it
                        viewModel.setSpeechRate(it)
                    },
                    valueRange = 0.7f..1.5f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = MaxPrimary,
                        activeTrackColor = MaxPrimary
                    ),
                    modifier = Modifier.testTag("speech_rate_slider")
                )

                Button(
                    onClick = {
                        viewModel.voiceManager.speak("नमस्ते! मैं मैक्स हूँ, आपका निजी फोन सहायक।", tempSpeechRate)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaxPrimaryContainer),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Test", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("आवाज टेस्ट करें", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }

        // 5. Reminders, Alarms & Weather Permissions
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Permissions",
                        tint = MaxSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "अलार्म, रिमाइंडर & मौसम डायग्नोस्टिक्स",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val hasAlarmPerm = viewModel.checkExactAlarmPermission()
                val hasLocPerm = viewModel.checkLocationPermission()
                val hasCamPerm = viewModel.checkCameraPermission()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("कैमरा (Camera & Selfie / Vision):", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(if (hasCamPerm) "सक्रिय (Granted)" else "अनुमति आवश्यक है", fontSize = 11.sp, color = if (hasCamPerm) MaxSuccess else MaxWarning)
                    }
                    if (!hasCamPerm) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaxWarning),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("अनुमति दें", fontSize = 10.sp, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("सटीक अलार्म (Exact Alarm - Android 12+):", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(if (hasAlarmPerm) "सक्रिय (Granted)" else "अनुमति आवश्यक है", fontSize = 11.sp, color = if (hasAlarmPerm) MaxSuccess else MaxWarning)
                    }
                    if (!hasAlarmPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaxWarning),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("अनुमति दें", fontSize = 10.sp, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val hasSmsPerm = viewModel.checkSmsPermission()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SMS अलर्ट & रिमोट ट्रैकिंग (Anti-Theft SMS):", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(if (hasSmsPerm) "सक्रिय (Granted)" else "अनुमति आवश्यक है", fontSize = 11.sp, color = if (hasSmsPerm) MaxSuccess else MaxWarning)
                    }
                    if (!hasSmsPerm) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaxWarning),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("अनुमति दें", fontSize = 10.sp, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val isDeviceAdmin = viewModel.isDeviceAdminActive()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("डिवाइस एडमिन (Lockscreen Intruder / Remote Lock):", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(if (isDeviceAdmin) "सक्रिय (Admin Active)" else "निष्क्रिय (Not Activated)", fontSize = 11.sp, color = if (isDeviceAdmin) MaxSuccess else MaxWarning)
                    }
                    if (!isDeviceAdmin) {
                        Button(
                            onClick = {
                                val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, android.content.ComponentName(context, com.example.antitheft.MaxDeviceAdminReceiver::class.java))
                                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "मैक्स को गलत पासवर्ड पकड़ने और रिमोट लॉक के लिए एडमिन परमिशन दें।")
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("सक्रिय करें", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val isScreenLockSet = viewModel.isScreenLockSet()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("स्क्रीन लॉक स्थिति (PIN / Pattern / Password):", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(if (isScreenLockSet) "सक्रिय (Lock Set on Phone)" else "⚠️ कोई लॉक नहीं लगा है (Set a PIN)", fontSize = 11.sp, color = if (isScreenLockSet) MaxSuccess else Color(0xFFF59E0B))
                    }
                    if (!isScreenLockSet) {
                        Button(
                            onClick = {
                                val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("सेट करें", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // 6. How It Works Info Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF140E26)),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Architecture",
                        tint = MaxSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "मैक्स कैसे काम करता है? (Architecture)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "1. स्क्रीन एनालिसिस: MaxAccessibilityService स्क्रीन के हर व्यू, बटन, वीडियो कार्ड व कोऑर्डिनेट्स (Bounds) को रियल-टाइम स्कैन करता है।\n" +
                            "2. जेमिनी रीज़निंग: यूजर की हिंदी वॉयस + स्क्रीन डेटा जेमिनी 3.5 फ़्लैश को भेजा जाता है, जो तय करता है कि किस बटन या कोऑर्डिनेट पर टैप करना है।\n" +
                            "3. ह्यूमन जेस्चर: Accessibility API के `dispatchGesture()` से इंसान की उंगली जैसा असली टच स्क्रीन पर जाता है।\n" +
                            "4. मेमोरी: Room डेटाबेस में आपकी पसंद और पिछली गतिविधियाँ याद रखी जाती हैं।",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
