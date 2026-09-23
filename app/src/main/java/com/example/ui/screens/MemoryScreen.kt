package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.UserMemoryEntity
import com.example.ui.theme.MaxDarkBg
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxSecondary
import com.example.ui.theme.MaxSuccess
import com.example.ui.theme.MaxSurfaceBorder
import com.example.ui.theme.MaxSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MaxViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedCategoryFilter by remember { mutableStateOf("all") }
    val memories by viewModel.memoryList.collectAsState()
    val history by viewModel.historyList.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val filteredMemories = remember(memories, selectedCategoryFilter) {
        if (selectedCategoryFilter == "all") {
            memories
        } else {
            memories.filter { it.category == selectedCategoryFilter }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaxPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_memory_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Memory")
                }
            }
        },
        containerColor = MaxDarkBg,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Info & Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Memory",
                        tint = MaxPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Max Context & Memory",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "लोकल रूम डेटाबेस: इंसान की तरह संदर्भ और आदतें याद रखता है",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Auto-clean & Optimize action button
                IconButton(
                    onClick = { viewModel.optimizeMemory() },
                    modifier = Modifier.testTag("optimize_memory_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Auto Optimize Memory",
                        tint = MaxSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaxPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaxPrimary
                    )
                },
                divider = { Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaxSurfaceBorder)) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "मेमोरी व संदर्भ (${memories.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) MaxPrimary else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_memories")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "संक्षिप्त बातचीत इतिहास (${history.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) MaxPrimary else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("tab_history")
                )
            }

            if (selectedTab == 0) {
                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "all" to "सभी",
                        UserMemoryEntity.CATEGORY_PREFERENCE to "पसंद (Preferences)",
                        UserMemoryEntity.CATEGORY_ACTIVITY_CONTEXT to "स्क्रीन संदर्भ (Context)",
                        UserMemoryEntity.CATEGORY_IDENTITY to "पहचान (Identity)"
                    )
                    filters.forEach { (cat, label) ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaxPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = MaxPrimary,
                                containerColor = MaxSurfaceElevated,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                // Context Educational Tip Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated.copy(alpha = 0.6f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Tips",
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "बोलकर याद करवाएं (स्थानीय डिवाइस स्टोरेज):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "• \"मुझे हिंदी गाने पसंद हैं\" / \"मेरा पसंदीदा ऐप यूट्यूब है\"\n• \"इसका वॉल्यूम बढ़ाओ\" / \"वही वाला फिर से चलाओ\"",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Memories List
                if (filteredMemories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "इस श्रेणी में कोई मेमोरी सेव नहीं है। नई मेमोरी जोड़ने के लिए '+' दबाएँ या बोलकर सिखाएँ।",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(filteredMemories, key = { it.key }) { mem ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = mem.key,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaxSecondary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (mem.category == UserMemoryEntity.CATEGORY_PREFERENCE) MaxPrimary.copy(alpha = 0.2f)
                                                        else if (mem.category == UserMemoryEntity.CATEGORY_ACTIVITY_CONTEXT) Color(0xFF10B981).copy(alpha = 0.2f)
                                                        else MaxSecondary.copy(alpha = 0.2f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = when (mem.category) {
                                                        UserMemoryEntity.CATEGORY_PREFERENCE -> "पसंद"
                                                        UserMemoryEntity.CATEGORY_ACTIVITY_CONTEXT -> "स्क्रीन गतिविधि"
                                                        UserMemoryEntity.CATEGORY_IDENTITY -> "पहचान"
                                                        else -> mem.category
                                                    },
                                                    fontSize = 10.sp,
                                                    color = if (mem.category == UserMemoryEntity.CATEGORY_ACTIVITY_CONTEXT) Color(0xFF10B981) else MaxPrimary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = mem.value,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary
                                        )

                                        if (mem.descriptionHindi.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = mem.descriptionHindi,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteMemory(mem.key) },
                                        modifier = Modifier.testTag("delete_memory_${mem.key}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Command History Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "हाल के 50 इंटरैक्शन (Gemini संदर्भ के लिए ऑटो-लिमिटेड)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    if (history.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearHistoryDialog = true },
                            modifier = Modifier.testTag("clear_history_btn")
                        ) {
                            Text("साफ करें", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }
                }

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "अभी तक कोई बातचीत हिस्ट्री सेव नहीं है।",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(history, key = { it.id }) { item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaxSurfaceElevated),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxSurfaceBorder)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "\"${item.userPrompt}\"",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = dateFormat.format(Date(item.timestamp)),
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Success",
                                            tint = if (item.success) MaxSuccess else Color(0xFFEF4444),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${item.actionType}: ${item.actionDetails}",
                                            fontSize = 12.sp,
                                            color = MaxSecondary
                                        )
                                    }

                                    if (item.responseHindi.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "उत्तर: ${item.responseHindi}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        var keyText by remember { mutableStateOf("") }
        var valueText by remember { mutableStateOf("") }
        var categoryText by remember { mutableStateOf(UserMemoryEntity.CATEGORY_PREFERENCE) }
        var descText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("नई लोकल मेमोरी जोड़ें", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = categoryText == UserMemoryEntity.CATEGORY_PREFERENCE,
                            onClick = { categoryText = UserMemoryEntity.CATEGORY_PREFERENCE },
                            label = { Text("पसंद", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = categoryText == UserMemoryEntity.CATEGORY_IDENTITY,
                            onClick = { categoryText = UserMemoryEntity.CATEGORY_IDENTITY },
                            label = { Text("पहचान", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = categoryText == UserMemoryEntity.CATEGORY_ACTIVITY_CONTEXT,
                            onClick = { categoryText = UserMemoryEntity.CATEGORY_ACTIVITY_CONTEXT },
                            label = { Text("संदर्भ", fontSize = 11.sp) }
                        )
                    }

                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        label = { Text("कुंजी (जैसे favorite_music)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text("मान (जैसे Arijit Singh Songs)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descText,
                        onValueChange = { descText = it },
                        label = { Text("हिंदी विवरण (विवरण)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyText.isNotBlank() && valueText.isNotBlank()) {
                            viewModel.saveMemory(keyText, valueText, categoryText, descText)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaxPrimary)
                ) {
                    Text("सेव करें")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("रद्द करें", color = TextSecondary)
                }
            },
            containerColor = MaxSurfaceElevated
        )
    }

    // Clear History Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("इतिहास साफ़ करें?", color = TextPrimary) },
            text = { Text("क्या आप पिछली सभी बातचीत और कमांड इतिहास को साफ़ करना चाहते हैं? आपकी स्थायी पसंद सुरक्षित रहेगी।", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("हाँ, साफ़ करें")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("रद्द करें", color = TextSecondary)
                }
            },
            containerColor = MaxSurfaceElevated
        )
    }
}
