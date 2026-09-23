package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.DesiredState
import com.example.system.SystemToggleController
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxSecondary
import com.example.ui.theme.MaxSuccess
import com.example.ui.theme.MaxSurfaceBorder
import com.example.ui.theme.MaxSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ToggleUiItem(
    val id: String,
    val titleHindi: String,
    val subtitle: String,
    val icon: ImageVector,
    val isActive: Boolean,
    val actionToggle: suspend () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemTogglesPanel(
    toggleController: SystemToggleController,
    onStatusReport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var isWifi by remember { mutableStateOf(toggleController.isWifiEnabled()) }
    var isBluetooth by remember { mutableStateOf(toggleController.isBluetoothEnabled()) }
    var isMobileData by remember { mutableStateOf(toggleController.isMobileDataEnabled()) }
    var isAirplane by remember { mutableStateOf(toggleController.isAirplaneModeOn()) }
    var isTorch by remember { mutableStateOf(toggleController.isTorchOn()) }
    var isGps by remember { mutableStateOf(toggleController.isGpsEnabled()) }
    var isDnd by remember { mutableStateOf(toggleController.isDndActive()) }
    var volumePct by remember { mutableStateOf(toggleController.getCurrentVolumePercent()) }
    var brightnessPct by remember { mutableStateOf(toggleController.getCurrentBrightnessPercent()) }

    // Periodically sync live status so if user changes something in Android quick settings, it updates
    LaunchedEffect(Unit) {
        while (true) {
            isWifi = toggleController.isWifiEnabled()
            isBluetooth = toggleController.isBluetoothEnabled()
            isMobileData = toggleController.isMobileDataEnabled()
            isAirplane = toggleController.isAirplaneModeOn()
            isTorch = toggleController.isTorchOn()
            isGps = toggleController.isGpsEnabled()
            isDnd = toggleController.isDndActive()
            volumePct = toggleController.getCurrentVolumePercent()
            brightnessPct = toggleController.getCurrentBrightnessPercent()
            delay(1500)
        }
    }

    val toggles = listOf(
        ToggleUiItem(
            id = "wifi",
            titleHindi = "वाई-फ़ाई",
            subtitle = if (isWifi) "चालू (ON)" else "बंद (OFF)",
            icon = Icons.Default.Wifi,
            isActive = isWifi,
            actionToggle = {
                val res = toggleController.setWifi(if (isWifi) DesiredState.OFF else DesiredState.ON)
                isWifi = toggleController.isWifiEnabled()
                onStatusReport(res.voiceResponseHindi)
            }
        ),
        ToggleUiItem(
            id = "bluetooth",
            titleHindi = "ब्लूटूथ",
            subtitle = if (isBluetooth) "चालू (ON)" else "बंद (OFF)",
            icon = Icons.Default.Bluetooth,
            isActive = isBluetooth,
            actionToggle = {
                val res = toggleController.setBluetooth(if (isBluetooth) DesiredState.OFF else DesiredState.ON)
                isBluetooth = toggleController.isBluetoothEnabled()
                onStatusReport(res.voiceResponseHindi)
            }
        ),
        ToggleUiItem(
            id = "mobile_data",
            titleHindi = "मोबाइल डेटा",
            subtitle = if (isMobileData) "चालू (ON)" else "बंद (OFF)",
            icon = Icons.Default.SignalCellularAlt,
            isActive = isMobileData,
            actionToggle = {
                val res = toggleController.setMobileData(if (isMobileData) DesiredState.OFF else DesiredState.ON)
                isMobileData = toggleController.isMobileDataEnabled()
                onStatusReport(res.voiceResponseHindi)
            }
        ),
        ToggleUiItem(
            id = "torch",
            titleHindi = "टॉर्च",
            subtitle = if (isTorch) "चालू (ON)" else "बंद (OFF)",
            icon = Icons.Default.FlashlightOn,
            isActive = isTorch,
            actionToggle = {
                val res = toggleController.setTorch(if (isTorch) DesiredState.OFF else DesiredState.ON)
                isTorch = toggleController.isTorchOn()
                onStatusReport(res.voiceResponseHindi)
            }
        ),
        ToggleUiItem(
            id = "airplane",
            titleHindi = "एरोप्लेन मोड",
            subtitle = if (isAirplane) "चालू (ON)" else "बंद (OFF)",
            icon = Icons.Default.AirplanemodeActive,
            isActive = isAirplane,
            actionToggle = {
                val res = toggleController.setAirplaneMode(if (isAirplane) DesiredState.OFF else DesiredState.ON)
                isAirplane = toggleController.isAirplaneModeOn()
                onStatusReport(res.voiceResponseHindi)
            }
        ),
        ToggleUiItem(
            id = "volume",
            titleHindi = "वॉल्यूम",
            subtitle = "$volumePct%",
            icon = Icons.Default.VolumeUp,
            isActive = volumePct > 0,
            actionToggle = {
                val res = toggleController.setVolume(if (volumePct > 50) DesiredState.DECREASE else DesiredState.INCREASE)
                volumePct = toggleController.getCurrentVolumePercent()
                onStatusReport(res.voiceResponseHindi)
            }
        ),
        ToggleUiItem(
            id = "brightness",
            titleHindi = "ब्राइटनेस",
            subtitle = "$brightnessPct%",
            icon = Icons.Default.BrightnessMedium,
            isActive = brightnessPct > 40,
            actionToggle = {
                val res = toggleController.setBrightness(if (brightnessPct > 60) DesiredState.DECREASE else DesiredState.INCREASE)
                brightnessPct = toggleController.getCurrentBrightnessPercent()
                onStatusReport(res.voiceResponseHindi)
            }
        ),
        ToggleUiItem(
            id = "dnd",
            titleHindi = "DND मोड",
            subtitle = if (isDnd) "चालू (ON)" else "बंद (OFF)",
            icon = Icons.Default.DoNotDisturbOn,
            isActive = isDnd,
            actionToggle = {
                val res = toggleController.setDnd(if (isDnd) DesiredState.OFF else DesiredState.ON)
                isDnd = toggleController.isDndActive()
                onStatusReport(res.voiceResponseHindi)
            }
        ),
        ToggleUiItem(
            id = "hotspot",
            titleHindi = "हॉटस्पॉट",
            subtitle = "टॉगल करें",
            icon = Icons.Default.WifiTethering,
            isActive = false,
            actionToggle = {
                val res = toggleController.setHotspot(DesiredState.TOGGLE)
                onStatusReport(res.voiceResponseHindi)
            }
        ),
        ToggleUiItem(
            id = "gps",
            titleHindi = "लोकेशन / GPS",
            subtitle = if (isGps) "चालू (ON)" else "बंद (OFF)",
            icon = Icons.Default.LocationOn,
            isActive = isGps,
            actionToggle = {
                val res = toggleController.setGps(if (isGps) DesiredState.OFF else DesiredState.ON)
                isGps = toggleController.isGpsEnabled()
                onStatusReport(res.voiceResponseHindi)
            }
        )
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaxSuccess)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "सिस्टम टॉगल कंट्रोलर (गारंटीड दोनों दिशाएं)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Text(
                    text = "API + Accessibility",
                    fontSize = 10.sp,
                    color = MaxSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "वर्तमान स्थिति चेक करके ऑन/ऑफ करता है। आवाज या टच दोनों से काम करता है।",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Flow grid of toggles (2 per row on phone)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2
            ) {
                toggles.forEach { item ->
                    val activeColor = if (item.isActive) MaxPrimary else Color(0xFF2A283E)
                    val textColor = if (item.isActive) Color.White else TextSecondary

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (item.isActive) {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaxPrimary.copy(alpha = 0.35f),
                                            MaxSecondary.copy(alpha = 0.25f)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF1B192E),
                                            Color(0xFF1B192E)
                                        )
                                    )
                                }
                            )
                            .clickable {
                                coroutineScope.launch {
                                    item.actionToggle()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("toggle_item_${item.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (item.isActive) MaxPrimary else Color(0xFF2E2B45)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.titleHindi,
                                    tint = if (item.isActive) Color.White else Color(0xFF9E9E9E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.titleHindi,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = item.subtitle,
                                    fontSize = 10.sp,
                                    color = if (item.isActive) MaxSuccess else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
