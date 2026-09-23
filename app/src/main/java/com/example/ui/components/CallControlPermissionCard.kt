package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

/**
 * Card displaying Call Control Feature Status, Permissions and Quick Simulation Test Bench.
 */
@Composable
fun CallControlPermissionCard(
    onSimulateCall: (name: String, number: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var hasPhoneState by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAnswerCalls by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var hasContacts by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPhoneState = results[Manifest.permission.READ_PHONE_STATE] ?: hasPhoneState
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hasAnswerCalls = results[Manifest.permission.ANSWER_PHONE_CALLS] ?: hasAnswerCalls
        }
        hasContacts = results[Manifest.permission.READ_CONTACTS] ?: hasContacts
    }

    val allGranted = hasPhoneState && hasAnswerCalls && hasContacts

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (allGranted) MaxSurfaceBorder else MaxWarning.copy(alpha = 0.5f)
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("call_control_permission_card")
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
                            .background(Color(0xFF311E63), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneCallback,
                            contentDescription = "Call Control",
                            tint = MaxPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "कॉल कंट्रोल & वॉयस असिस्टेंट",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "इनकमिंग कॉल अनाउंसमेंट + 'उठा लो' / 'काट दो'",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    color = if (allGranted) MaxSuccess.copy(alpha = 0.2f) else MaxWarning.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (allGranted) "सक्रिय (Ready)" else "परमिशन बाकी",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (allGranted) MaxSuccess else MaxWarning,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanation
            Text(
                text = "जब भी कॉल आएगी, मैक्स हिंदी में कॉलर का नाम/नंबर बोलेगा। फिर आप बोलकर कॉल उठा सकते हैं (\"उठा लो\") या काट सकते हैं (\"काट दो\"), या कह सकते हैं \"मैक्स तुम बात करो\"।",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Permission Items List
            PermissionStatusRow(
                title = "READ_PHONE_STATE (फ़ोन स्थिति)",
                subtitle = "इनकमिंग कॉल का पता लगाने के लिए",
                isGranted = hasPhoneState
            )
            Spacer(modifier = Modifier.height(6.dp))
            PermissionStatusRow(
                title = "ANSWER_PHONE_CALLS (कॉल उत्तर/समाप्त)",
                subtitle = "आवाज से 'उठा लो' / 'काट दो' चलाने के लिए",
                isGranted = hasAnswerCalls
            )
            Spacer(modifier = Modifier.height(6.dp))
            PermissionStatusRow(
                title = "READ_CONTACTS (संपर्क सूची)",
                subtitle = "कॉलर का नाम (राहुल, मम्मी, आदि) पहचानने के लिए",
                isGranted = hasContacts
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row: Request Permissions & Test Simulation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!allGranted) {
                    Button(
                        onClick = {
                            val perms = mutableListOf(
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.READ_CONTACTS
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                perms.add(Manifest.permission.ANSWER_PHONE_CALLS)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                perms.add(Manifest.permission.READ_CALL_LOG)
                            }
                            permissionsLauncher.launch(perms.toTypedArray())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaxPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("grant_call_permissions_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("अनुमति दें", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Simulation Test Button
                OutlinedButton(
                    onClick = {
                        onSimulateCall("राहुल शर्मा", "+91 98765 43210")
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaxSecondary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaxSecondary)
                    ),
                    modifier = Modifier
                        .weight(if (allGranted) 1f else 1.2f)
                        .testTag("simulate_call_btn")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("📞 टेस्ट कॉल चलाएं", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF100C22), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) MaxSuccess else MaxWarning,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        Text(
            text = if (isGranted) "स्वीकृत" else "अपेक्षित",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isGranted) MaxSuccess else MaxWarning
        )
    }
}
