package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
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
    latestIntruderLog: IntruderLogEntity?,
    isProcessing: Boolean,
    onToggleEnable: (Boolean) -> Unit,
    onRequestDeviceAdmin: () -> Unit,
    onSaveTrustedContact: (String, String) -> Unit,
    onTestIntruderAlert: () -> Unit,
    onTestSimAlert: () -> Unit,
    onTestSiren: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditContactDialog by remember { mutableStateOf(false) }

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
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Anti-Theft Protection",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(20.dp)
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
                            val isArmed = (settings?.isEnabled == true) && isDeviceAdminActive && !settings.trustedContactNumber.isNullOrBlank()
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
                            text = "3 बार गलत पासवर्ड ➔ साइलेंट सेल्फी + GPS + SMS",
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

            Spacer(modifier = Modifier.height(14.dp))

            // Device Admin Protection Banner
            if (!isDeviceAdminActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2D1616), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "डिवाइस एडमिन परमिशन दें",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF87171)
                        )
                        Text(
                            text = "गलत लॉकस्क्रीन पासवर्ड पकड़ने हेतु आवश्यक",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Button(
                        onClick = onRequestDeviceAdmin,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("सक्रिय करें", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Emergency Trusted Contact Section
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
                            text = "इमरजेंसी कॉन्टैक्ट (SMS अलर्ट हेतु):",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        val contactNum = settings?.trustedContactNumber
                        if (contactNum.isNullOrBlank()) {
                            Text(
                                text = "कोई नंबर सेट नहीं है (क्लिक कर जोड़ें)",
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

            // Latest Intruder Incident Display
            if (latestIntruderLog != null) {
                Spacer(modifier = Modifier.height(12.dp))
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
                        val timeStr = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(latestIntruderLog.timestamp))
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

            Spacer(modifier = Modifier.height(14.dp))

            // Action & Test Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTestIntruderAlert,
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.2f).height(40.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("गलत पासवर्ड टेस्ट", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    Text("SIM चेंज टेस्ट", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

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

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "💡 रिमोट कमांड्स: दूसरे फोन से SMS भेजें: 'MAX LOCATE' (लोकेशन), 'MAX LOCK' (रिमोट लॉक), 'MAX SIREN' (अलार्म)।",
                fontSize = 10.sp,
                color = TextSecondary,
                lineHeight = 14.sp
            )
        }
    }

    // Edit Trusted Contact Dialog
    if (showEditContactDialog) {
        var inputName by remember { mutableStateOf(settings?.trustedContactName ?: "") }
        var inputPhone by remember { mutableStateOf(settings?.trustedContactNumber ?: "") }

        AlertDialog(
            onDismissRequest = { showEditContactDialog = false },
            title = { Text("इमरजेंसी ट्रस्टेड कॉन्टैक्ट", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "जब कोई गलत पासवर्ड डालेगा या सिम बदलेगा, तो मैक्स इस नंबर पर साइलेंट SMS भेजेगा।",
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
                        label = { Text("मोबाइल नंबर (Mobile Number)") },
                        placeholder = { Text("+919876543210") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveTrustedContact(inputName, inputPhone)
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
