package com.example.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.antitheft.AntiTheftSettingsEntity
import com.example.antitheft.IntruderLogEntity
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxSecondary
import com.example.ui.theme.MaxSuccess
import com.example.ui.theme.MaxSurfaceBorder
import com.example.ui.theme.MaxSurfaceElevated
import com.example.ui.theme.MaxWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AntiTheftCard(
    settings: AntiTheftSettingsEntity?,
    isDeviceAdminActive: Boolean,
    isScreenLockSet: Boolean,
    latestIntruderLog: IntruderLogEntity?,
    isProcessing: Boolean,
    isSirenActive: Boolean = false,
    onToggleEnable: (Boolean) -> Unit,
    onRequestDeviceAdmin: () -> Unit,
    onOpenScreenLockSettings: () -> Unit,
    onSetThreshold: (Int) -> Unit,
    onSaveTrustedContact: (String, String, String) -> Unit,
    onTestIntruderAlert: () -> Unit,
    onTestSimAlert: () -> Unit,
    onTestSiren: () -> Unit,
    onStopSiren: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showEditContactDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isArmed = (settings?.isEnabled == true) && isDeviceAdminActive && isScreenLockSet && !settings.trustedContactNumber.isNullOrBlank()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(MaxSurfaceBorder, Color(0xFFEF4444), Color(0xFFF59E0B))
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Anti-Theft Protection",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "एंटी-थेफ्ट गार्ड (Anti-Theft)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isArmed) MaxSuccess.copy(alpha = 0.2f) else MaxWarning.copy(alpha = 0.2f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isArmed) "सशस्त्र (ARMED)" else "सेटअप बाकी",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isArmed) MaxSuccess else MaxWarning
                                )
                            }
                        }
                        Text(
                            text = "गलत PIN दर्ज ➔ साइलेंट सेल्फी + GPS + SMS अलर्ट",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Switch(
                    checked = settings?.isEnabled ?: true,
                    onCheckedChange = onToggleEnable,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFEF4444)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. CRITICAL WARNING: Device Admin NOT Enabled
            if (!isDeviceAdminActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF331414), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "डिवाइस एडमिन परमिशन आवश्यक है!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFCA5A5)
                        )
                        Text(
                            text = "Settings → Security → Device Admin में Max को ON करें ताकि गलत PIN पकड़ सके।",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onRequestDeviceAdmin,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("सक्रिय करें", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 3. CRITICAL WARNING: Screen Lock (PIN/Pattern) NOT Set on Device
            if (!isScreenLockSet) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF302008), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "स्क्रीन लॉक (PIN/Pattern) सेट नहीं है!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDE68A)
                        )
                        Text(
                            text = "फोन में कोई लॉक नहीं लगा है। 'गलत PIN' फीचर के लिए फोन में स्क्रीन लॉक होना आवश्यक है।",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onOpenScreenLockSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("लॉक लगाएं", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 4. Security Status Diagnostic Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131024), RoundedCornerShape(10.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Admin Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDeviceAdminActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isDeviceAdminActive) MaxSuccess else Color(0xFFEF4444),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isDeviceAdminActive) "Device Admin: On" else "Admin: Off",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDeviceAdminActive) MaxSuccess else Color(0xFFEF4444)
                    )
                }

                // Lockscreen Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isScreenLockSet) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isScreenLockSet) MaxSuccess else Color(0xFFF59E0B),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isScreenLockSet) "Screen Lock: Set" else "Lock: None",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isScreenLockSet) MaxSuccess else Color(0xFFF59E0B)
                    )
                }

                // Contact Status
                val hasContact = !settings?.trustedContactNumber.isNullOrBlank()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasContact) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasContact) MaxSuccess else MaxWarning,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hasContact) "SMS Alert: Ready" else "No Contact",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasContact) MaxSuccess else MaxWarning
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Wrong PIN Threshold Selector (1, 2, 3 attempts)
            val currentThreshold = settings?.failedAttemptsThreshold ?: 1
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16122C), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = "कितने गलत प्रयास पर अलर्ट भेजें?",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "तत्काल टेस्टिंग के लिए '1 बार' चुनें",
                        fontSize = 9.sp,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3).forEach { count ->
                        val isSelected = currentThreshold == count
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFEF4444) else Color(0xFF231F3D))
                                .clickable { onSetThreshold(count) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$count बार",
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. Emergency Trusted Contact & Email Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131127), RoundedCornerShape(12.dp))
                    .border(1.dp, MaxSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaxPrimary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = MaxSecondary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "इमरजेंसी ट्रस्टेड कॉन्टैक्ट (SMS/Email):",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        val contactNum = settings?.trustedContactNumber
                        if (contactNum.isNullOrBlank()) {
                            Text(
                                text = "नंबर जोड़ें (क्लिक करें)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaxWarning
                            )
                        } else {
                            Text(
                                text = "${settings.trustedContactName}: $contactNum",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (!settings.trustedContactEmail.isNullOrBlank()) {
                                Text(
                                    text = "ईमेल: ${settings.trustedContactEmail}",
                                    fontSize = 10.sp,
                                    color = MaxSecondary
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { showEditContactDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Contact", tint = MaxSecondary, modifier = Modifier.size(16.dp))
                }
            }

            // 7. Latest Intruder Incident Display
            if (latestIntruderLog != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1318), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "हालिया घुसपैठिया अलर्ट (Intruder Log)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFCA5A5)
                            )
                        }
                        val timeStr = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()).format(Date(latestIntruderLog.timestamp))
                        Text(text = timeStr, fontSize = 10.sp, color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!latestIntruderLog.photoPath.isNullOrBlank() && File(latestIntruderLog.photoPath).exists()) {
                            val bitmap = remember(latestIntruderLog.photoPath) {
                                BitmapFactory.decodeFile(latestIntruderLog.photoPath)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Intruder Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = latestIntruderLog.details.ifBlank { latestIntruderLog.triggerType },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            if (latestIntruderLog.latitude != null && latestIntruderLog.longitude != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaxSecondary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${String.format(Locale.US, "%.4f", latestIntruderLog.latitude)}, ${String.format(Locale.US, "%.4f", latestIntruderLog.longitude)} (${latestIntruderLog.address ?: ""})",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            if (latestIntruderLog.isSmsDelivered && !latestIntruderLog.smsSentTo.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "✓ SMS भेजा गया: ${latestIntruderLog.smsSentTo}",
                                    fontSize = 10.sp,
                                    color = MaxSuccess
                                )
                            }
                        }
                    }
                }
            }

            if (isSirenActive) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Siren Active",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "🚨 चोरी अलार्म चालू है! (फुल वॉल्यूम)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "लाउड साइरन बज रहा है। बंद करने के लिए बटन दबाएं या बोलें 'अलार्म बंद करो' ।",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onStopSiren,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("बंद करें 🛑", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 8. Action & Test Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTestIntruderAlert,
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.3f).height(40.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("गलत PIN लाइव टेस्ट", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onTestSimAlert,
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(Icons.Default.SimCard, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SIM टेस्ट", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (isSirenActive) {
                    Button(
                        onClick = onStopSiren,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text("🛑 बंद करें", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    OutlinedButton(
                        onClick = onTestSiren,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f).height(40.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("साइरन", fontSize = 11.sp, color = Color(0xFFF87171))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "💡 रिमोट सुरक्षा SMS कमांड्स: 'MAX LOCATE' (लोकेशन), 'MAX LOCK' (रिमोट लॉक), 'MAX SIREN' (अलार्म)।",
                fontSize = 10.sp,
                color = TextSecondary,
                lineHeight = 14.sp
            )
        }
    }

    // Edit Trusted Contact & Email Dialog
    if (showEditContactDialog) {
        var inputName by remember { mutableStateOf(settings?.trustedContactName ?: "Emergency Contact") }
        var inputPhone by remember { mutableStateOf(settings?.trustedContactNumber ?: "") }
        var inputEmail by remember { mutableStateOf(settings?.trustedContactEmail ?: "") }

        AlertDialog(
            onDismissRequest = { showEditContactDialog = false },
            title = { Text("इमरजेंसी ट्रस्टेड कॉन्टैक्ट", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "जब कोई गलत पासवर्ड डालेगा या सिम बदलेगा, तो मैक्स इस नंबर पर साइलेंट SMS व अलर्ट भेजेगा।",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("नाम (Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputPhone,
                        onValueChange = { inputPhone = it },
                        label = { Text("मोबाइल नंबर (SMS हेतु)") },
                        placeholder = { Text("+919876543210") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it },
                        label = { Text("ईमेल एड्रेस (वैकल्पिक)") },
                        placeholder = { Text("alert@example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveTrustedContact(inputName, inputPhone, inputEmail)
                        showEditContactDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaxPrimary)
                ) {
                    Text("सेव करें")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditContactDialog = false }) {
                    Text("रद्द करें", color = TextSecondary)
                }
            }
        )
    }
}
