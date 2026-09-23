package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.accessibility.MaxAccessibilityService
import com.example.overlay.MaxOverlayService
import com.example.ui.components.LiveLogCard
import com.example.ui.components.QuickActionChips
import com.example.ui.components.VoiceSphere
import com.example.ui.components.IncomingCallAlertCard
import com.example.ui.components.CallControlPermissionCard
import com.example.ui.components.WeatherCard
import com.example.ui.components.RemindersCard
import com.example.ui.components.CameraCard
import com.example.ui.components.AntiTheftCard
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
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

@Composable
fun DashboardScreen(
    viewModel: MaxViewModel,
    onNavigateToSimulator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val agentStatus by viewModel.agentStatus.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val lastInput by viewModel.lastVoiceInput.collectAsState()
    val lastAction by viewModel.lastAction.collectAsState()
    val logs by viewModel.executionLogs.collectAsState()
    val speechRms by viewModel.voiceManager.speechRms.collectAsState()
    val isAccessibilityActive by viewModel.isAccessibilityEnabled.collectAsState()
    val useSimulator by viewModel.useSimulatorMode.collectAsState()
    val currentCall by viewModel.currentCall.collectAsState()
    val isWaitingForCallVoice by viewModel.isWaitingForCallVoiceResponse.collectAsState()
    val currentWeather by viewModel.currentWeather.collectAsState()
    val isFetchingWeather by viewModel.isFetchingWeather.collectAsState()
    val activeReminders by viewModel.activeReminders.collectAsState()
    val lastCameraCapture by viewModel.lastCameraCapture.collectAsState()
    val sceneAnalysisText by viewModel.sceneAnalysisText.collectAsState()
    val isCameraProcessing by viewModel.isCameraProcessing.collectAsState()
    val antiTheftSettings by viewModel.antiTheftSettings.collectAsState()
    val latestIntruderLog by viewModel.latestIntruderLog.collectAsState()
    val isAntiTheftProcessing by viewModel.isAntiTheftProcessing.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaxDarkBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Hero Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.hero_banner),
                contentDescription = "Max Assistant Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaxDarkBg.copy(alpha = 0.85f),
                                MaxDarkBg
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Max AI Agent",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (isAccessibilityActive) MaxSuccess.copy(alpha = 0.2f) else MaxWarning.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAccessibilityActive) MaxSuccess else MaxWarning
                        )
                    ) {
                        Text(
                            text = if (isAccessibilityActive) "Accessibility ON" else "Ready",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAccessibilityActive) MaxSuccess else MaxWarning,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "ह्यूमन जैसे टच जेस्चर से फोन कंट्रोल करने वाला AI सहायक",
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
        }

        // =========================================================================
        // INCOMING CALL ALERT & VOICE ANSWER / REJECT FLOATING CARD
        // =========================================================================
        IncomingCallAlertCard(
            callInfo = currentCall,
            isWaitingForVoice = isWaitingForCallVoice,
            onAnswerClick = { viewModel.answerCall() },
            onRejectClick = { viewModel.rejectCall() },
            onMaxAttendClick = { viewModel.attendCallWithAgent() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Mode Switcher & Quick Launch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = useSimulator,
                onClick = { viewModel.setSimulatorMode(true) },
                label = { Text("📱 YouTube Test Bench", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaxPrimaryContainer,
                    selectedLabelColor = TextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = useSimulator,
                    borderColor = MaxSurfaceBorder,
                    selectedBorderColor = MaxPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_simulator_chip")
            )

            FilterChip(
                selected = !useSimulator,
                onClick = {
                    viewModel.setSimulatorMode(false)
                    if (!isAccessibilityActive) {
                        MaxAccessibilityService.openAccessibilitySettings(context)
                    }
                },
                label = { Text("⚡ Live Phone Control", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaxPrimaryContainer,
                    selectedLabelColor = TextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = !useSimulator,
                    borderColor = MaxSurfaceBorder,
                    selectedBorderColor = MaxSecondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_live_chip")
            )
        }

        // Local First Command Router Info Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141226)),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = "Fast Local Routing",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "जेनेरिक ऐप लॉन्चर & लोकल राउटर (Offline First)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "कोई भी इंस्टॉल ऐप (Instagram, WhatsApp, आदि) तुरंत लोकल खुलेगा | स्क्रीन कंट्रोल व विश्लेषण → Gemini AI",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Accessibility Service Alert banner if not enabled and live mode chosen
        if (!useSimulator && !isAccessibilityActive) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaxWarning.copy(alpha = 0.15f)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxWarning)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsAccessibility,
                        contentDescription = "Permission Alert",
                        tint = MaxWarning,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "एक्सेसिबिलिटी परमिशन आवश्यक है",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "स्क्रीन पढ़ने और फिंगर टैप करने के लिए 'Max AI Voice Agent' को चालू करें।",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Button(
                        onClick = { MaxAccessibilityService.openAccessibilitySettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaxWarning),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("enable_accessibility_btn")
                    ) {
                        Text("चालू करें", fontSize = 11.sp, color = Color.Black)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Center Voice Sphere & Status
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                VoiceSphere(
                    agentStatus = agentStatus,
                    speechRms = speechRms,
                    onClick = { viewModel.toggleListening() }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Spoken speech transcription or status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F0C22))
                        .border(1.dp, MaxSurfaceBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (lastInput.isNotBlank()) "\"$lastInput\"" else statusMessage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (lastInput.isNotBlank()) MaxSecondary else TextPrimary,
                        lineHeight = 20.sp
                    )
                }

                if (lastAction != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Action",
                            tint = MaxSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${lastAction?.actionType}: ${lastAction?.reasonHindi}",
                            fontSize = 12.sp,
                            color = MaxSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Weather Card (Open-Meteo API + FusedLocation)
        WeatherCard(
            weather = currentWeather,
            isLoading = isFetchingWeather,
            onRefreshClick = { viewModel.refreshWeather(speak = true) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Camera, Selfie & AI Vision Card
        CameraCard(
            lastCapture = lastCameraCapture,
            sceneAnalysisText = sceneAnalysisText,
            isProcessing = isCameraProcessing,
            onTakeSelfie = { viewModel.takePhotoOrSelfie(isFront = true) },
            onTakePhoto = { viewModel.takePhotoOrSelfie(isFront = false) },
            onAnalyzeScene = { viewModel.analyzeScene() },
            onSilentCaptureTest = { viewModel.testSilentCapture() },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Anti-Theft Guard Security Card
        AntiTheftCard(
            settings = antiTheftSettings,
            isDeviceAdminActive = viewModel.isDeviceAdminActive(),
            latestIntruderLog = latestIntruderLog,
            isProcessing = isAntiTheftProcessing,
            onToggleEnable = { viewModel.toggleAntiTheft(it) },
            onRequestDeviceAdmin = {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, com.example.antitheft.MaxDeviceAdminReceiver::class.java))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "मैक्स को गलत पासवर्ड और चोरी से सुरक्षा के लिए डिवाइस एडमिन की आवश्यकता है।")
                }
                context.startActivity(intent)
            },
            onSaveTrustedContact = { name, phone ->
                viewModel.saveTrustedContact(name, phone)
            },
            onTestIntruderAlert = { viewModel.testIntruderAlert() },
            onTestSimAlert = { viewModel.testSimAlert() },
            onTestSiren = { viewModel.triggerSiren() },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // System Toggles Panel (Bidirectional & Guaranteed Accessibility Fallback)
        com.example.ui.components.SystemTogglesPanel(
            toggleController = viewModel.toggleController,
            onStatusReport = { message ->
                viewModel.processCommand(message)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Reminders & Alarms (AlarmManager + Room DB)
        RemindersCard(
            reminders = activeReminders,
            canScheduleExactAlarms = viewModel.checkExactAlarmPermission(),
            onDeleteReminder = { id -> viewModel.deleteReminder(id) },
            onScheduleQuickReminder = { minutes, title -> viewModel.scheduleQuickReminder(minutes, title) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Hindi Voice Action Chips
        Text(
            text = "⚡ त्वरित वॉइस कमांड (Quick Voice Triggers)",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            ),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
        )

        QuickActionChips(
            onChipClicked = { command ->
                when {
                    command.contains("टेस्ट कॉल") -> viewModel.simulateIncomingCall()
                    command.contains("उठा लो") -> viewModel.answerCall()
                    command.contains("काट दो") -> viewModel.rejectCall()
                    command.contains("मैक्स तुम बात करो") -> viewModel.attendCallWithAgent()
                    else -> viewModel.processCommand(command)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Call Control & Incoming Announce Permission & Test Card
        CallControlPermissionCard(
            onSimulateCall = { name, number ->
                viewModel.simulateIncomingCall(name, number)
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // YouTube Playground Direct CTA
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1435)),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxPrimary.copy(alpha = 0.5f))),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "YouTube Playground",
                        tint = Color(0xFFFF0033),
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Interactive YouTube Player",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Ad Skip, Search और Next Video लाइव टेस्ट करें",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                Button(
                    onClick = onNavigateToSimulator,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0033)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_simulator_btn")
                ) {
                    Text("ओपन करें", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Action Log Stream
        LiveLogCard(
            logs = logs,
            onClearLogs = { viewModel.clearHistory() },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
