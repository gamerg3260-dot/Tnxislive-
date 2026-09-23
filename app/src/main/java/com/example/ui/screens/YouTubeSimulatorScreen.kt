package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simulator.SimulatedVideo
import com.example.ui.theme.MaxPrimary
import com.example.ui.theme.MaxSecondary
import com.example.ui.theme.MaxSuccess
import com.example.ui.theme.MaxWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AgentStatus
import com.example.ui.viewmodel.MaxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeSimulatorScreen(
    viewModel: MaxViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val simState = viewModel.simulatorState
    val isPlaying by simState.isPlaying.collectAsState()
    val isAdActive by simState.isAdActive.collectAsState()
    val canSkipAd by simState.canSkipAd.collectAsState()
    val currentVideo by simState.currentVideo.collectAsState()
    val suggestedVideos by simState.suggestedVideos.collectAsState()
    val lastTapPoint by simState.lastTapPoint.collectAsState()

    val agentStatus by viewModel.agentStatus.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val lastAction by viewModel.lastAction.collectAsState()

    var searchInputText by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "tap_trans")
    val tapScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tap_scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF0033)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "YouTube",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "YouTube Live Simulator",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("simulator_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { simState.triggerNewAd() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("trigger_ad_btn")
                    ) {
                        Text("Add Ad", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0F0F)
                )
            )
        },
        bottomBar = {
            // Mini Floating Max Voice Bar
            Card(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16122C)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaxPrimary.copy(alpha = 0.5f))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (agentStatus) {
                                            AgentStatus.LISTENING -> MaxSecondary
                                            AgentStatus.THINKING -> MaxWarning
                                            AgentStatus.EXECUTING -> MaxSuccess
                                            else -> MaxPrimary
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusMessage,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FloatingActionButton(
                            onClick = { viewModel.toggleListening() },
                            containerColor = MaxPrimary,
                            contentColor = Color.White,
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("sim_voice_mic_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Mic",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Shortcut buttons for user quick testing
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.processCommand("Ad skip karo") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sim_quick_skip_btn")
                        ) {
                            Text("⚡ Skip Ad", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.processCommand("दूसरा वीडियो चलाओ") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaxPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sim_quick_next_btn")
                        ) {
                            Text("⏭️ Next Video", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.processCommand("अभी स्क्रीन पर क्या हो रहा है?") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sim_quick_summary_btn")
                        ) {
                            Text("🧠 Summary", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF0F0F0F),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. YouTube Search Bar
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF222222))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(22.dp))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (searchInputText.isNotBlank()) searchInputText else "Search YouTube",
                                    fontSize = 13.sp,
                                    color = if (searchInputText.isNotBlank()) Color.White else Color.Gray
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (searchInputText.isNotBlank()) {
                                    simState.searchAndPlay(searchInputText)
                                }
                            },
                            modifier = Modifier.testTag("sim_search_icon_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Perform Search",
                                tint = Color.White
                            )
                        }
                    }
                }

                // 2. Video Player & Ad Overlay
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color.Black)
                            .testTag("sim_player_area")
                    ) {
                        // Background Video Graphic
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = if (isAdActive) {
                                            listOf(Color(0xFF1E1B4B), Color(0xFF312E81))
                                        } else {
                                            listOf(Color(0xFF172554), Color(0xFF1E3A8A))
                                        }
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isAdActive) {
                                    Text(
                                        text = "SPONSORED ADVERTISEMENT",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFBBF24)
                                    )
                                    Text(
                                        text = "Google AI Studio • Build Apps Faster with Gemini",
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clickable { simState.togglePlayPause() }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentVideo.title,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        }

                        // Progress Bar at bottom of player
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                                .background(Color.DarkGray)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (isAdActive) 0.65f else 0.35f)
                                    .height(4.dp)
                                    .background(if (isAdActive) Color(0xFFF59E0B) else Color(0xFFFF0033))
                            )
                        }

                        // AD OVERLAY & SKIP BUTTON
                        if (isAdActive) {
                            // Ad Tag in bottom-left
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 12.dp, bottom = 12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF59E0B))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Ad • 0:15",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }

                            // The prominent "Skip Ad" Button in bottom-right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 12.dp, bottom = 12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.85f))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .clickable { simState.skipAd() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("sim_skip_ad_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Skip Ads",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Skip",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Current Video Metadata
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = currentVideo.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${currentVideo.views} • 4K HD",
                            fontSize = 12.sp,
                            color = Color(0xFFAAAAAA)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Channel Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF7C3AED)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentVideo.channel.take(1),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = currentVideo.channel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "2.4M subscribers",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Subscribe", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 4. Suggested Videos Header
                item {
                    Text(
                        text = "Up Next (सुझाए गए वीडियो)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // 5. Suggested Video Cards
                items(suggestedVideos) { video ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                            .clickable { simState.playVideo(video) }
                            .testTag("video_card_${video.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 110.dp, height = 65.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2A2A2A)),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(video.duration, fontSize = 9.sp, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${video.channel} • ${video.views}",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

            // Glowing Finger Touch Indicator Visualizer
            lastTapPoint?.let { (x, y) ->
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x.toInt() - 25, y.toInt() - 25) }
                        .size(50.dp)
                        .scale(tapScale)
                        .clip(CircleShape)
                        .background(MaxSecondary.copy(alpha = 0.4f))
                        .border(2.5.dp, Color.White, CircleShape)
                )
            }
        }
    }
}
