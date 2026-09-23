package com.example.simulator

import android.graphics.Rect
import com.example.data.model.ScreenNode
import com.example.data.model.ScreenSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimulatedVideo(
    val id: String,
    val title: String,
    val channel: String,
    val views: String,
    val duration: String,
    val hasAd: Boolean = false
)

class YouTubeSimulatorState {

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAdActive = MutableStateFlow(true)
    val isAdActive: StateFlow<Boolean> = _isAdActive.asStateFlow()

    private val _canSkipAd = MutableStateFlow(true)
    val canSkipAd: StateFlow<Boolean> = _canSkipAd.asStateFlow()

    private val _currentVideo = MutableStateFlow(
        SimulatedVideo(
            id = "vid_1",
            title = "Android Jetpack Compose Full Course 2026 - Build Modern Apps",
            channel = "Tech & Code Hindi",
            views = "1.2M views • 2 weeks ago",
            duration = "42:15",
            hasAd = true
        )
    )
    val currentVideo: StateFlow<SimulatedVideo> = _currentVideo.asStateFlow()

    private val _suggestedVideos = MutableStateFlow(
        listOf(
            SimulatedVideo(
                id = "vid_2",
                title = "CarryMinati New Roast - Viral Reaction Video",
                channel = "CarryMinati",
                views = "18M views • 3 days ago",
                duration = "14:20"
            ),
            SimulatedVideo(
                id = "vid_3",
                title = "Chai Aur Code - Android Accessibility Service Masterclass",
                channel = "Chai aur Code",
                views = "450K views • 1 month ago",
                duration = "28:50"
            ),
            SimulatedVideo(
                id = "vid_4",
                title = "Dhruv Rathee Explains AI Autonomous Agents in 2026",
                channel = "Dhruv Rathee",
                views = "4.8M views • 5 days ago",
                duration = "19:40"
            ),
            SimulatedVideo(
                id = "vid_5",
                title = "Top 10 Indian Stand-up Comedy Moments 2026",
                channel = "Comedy Club India",
                views = "2.1M views • 1 week ago",
                duration = "22:10"
            )
        )
    )
    val suggestedVideos: StateFlow<List<SimulatedVideo>> = _suggestedVideos.asStateFlow()

    // Last virtual tap coordinates for visual feedback in UI
    private val _lastTapPoint = MutableStateFlow<Pair<Float, Float>?>(null)
    val lastTapPoint: StateFlow<Pair<Float, Float>?> = _lastTapPoint.asStateFlow()

    fun showTapIndicator(x: Float, y: Float) {
        _lastTapPoint.value = Pair(x, y)
    }

    fun clearTapIndicator() {
        _lastTapPoint.value = null
    }

    fun skipAd(): Boolean {
        if (_isAdActive.value) {
            _isAdActive.value = false
            _isPlaying.value = true
            return true
        }
        return false
    }

    fun triggerNewAd() {
        _isAdActive.value = true
        _canSkipAd.value = true
    }

    fun searchAndPlay(query: String) {
        _searchQuery.value = query
        val found = _suggestedVideos.value.find {
            it.title.contains(query, ignoreCase = true) || it.channel.contains(query, ignoreCase = true)
        } ?: SimulatedVideo(
            id = "custom_${System.currentTimeMillis()}",
            title = "$query - Official HD Video",
            channel = "YouTube Creator India",
            views = "890K views • Just now",
            duration = "12:00",
            hasAd = true
        )
        playVideo(found)
    }

    fun playNextVideo(): SimulatedVideo? {
        val currentList = _suggestedVideos.value
        val next = currentList.firstOrNull() ?: return null
        playVideo(next)
        return next
    }

    fun playVideo(video: SimulatedVideo) {
        val remaining = _suggestedVideos.value.filter { it.id != video.id } + _currentVideo.value
        _suggestedVideos.value = remaining
        _currentVideo.value = video
        _isAdActive.value = video.hasAd
        _isPlaying.value = true
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    /**
     * Generates real ScreenSnapshot representation corresponding to this simulated screen,
     * with exact button coordinates, bounds, text, and view IDs!
     */
    fun generateSnapshot(containerWidth: Int = 1080, containerHeight: Int = 1920): ScreenSnapshot {
        val elements = mutableListOf<ScreenNode>()

        // 1. YouTube Top Bar & Search Box
        elements.add(
            ScreenNode(
                id = "yt_search_box",
                viewIdResourceName = "com.google.android.youtube:id/search_edit_text",
                className = "android.widget.EditText",
                packageName = "com.google.android.youtube",
                text = _searchQuery.value.ifBlank { "Search YouTube" },
                contentDescription = "Search YouTube",
                bounds = Rect(72, 80, containerWidth - 120, 200),
                centerX = containerWidth / 2,
                centerY = 140,
                isClickable = true,
                isEditable = true
            )
        )

        elements.add(
            ScreenNode(
                id = "yt_search_btn",
                viewIdResourceName = "com.google.android.youtube:id/search_button",
                className = "android.widget.ImageView",
                packageName = "com.google.android.youtube",
                text = "Search",
                contentDescription = "Search button",
                bounds = Rect(containerWidth - 110, 80, containerWidth - 30, 200),
                centerX = containerWidth - 70,
                centerY = 140,
                isClickable = true
            )
        )

        // 2. Player area
        val playerTop = 230
        val playerBottom = 680
        val playerCenterY = (playerTop + playerBottom) / 2

        elements.add(
            ScreenNode(
                id = "yt_player",
                viewIdResourceName = "com.google.android.youtube:id/player_view",
                className = "android.view.View",
                packageName = "com.google.android.youtube",
                text = if (_isAdActive.value) "Video Player (Playing Ad)" else "Video Player (${_currentVideo.value.title})",
                bounds = Rect(0, playerTop, containerWidth, playerBottom),
                centerX = containerWidth / 2,
                centerY = playerCenterY,
                isClickable = true
            )
        )

        // 3. Ad Skip Button (if ad is active)
        if (_isAdActive.value) {
            val skipLeft = containerWidth - 340
            val skipRight = containerWidth - 40
            val skipTop = playerBottom - 110
            val skipBottom = playerBottom - 30
            elements.add(
                ScreenNode(
                    id = "yt_skip_ad_button",
                    viewIdResourceName = "com.google.android.youtube:id/skip_ad_button",
                    className = "android.widget.Button",
                    packageName = "com.google.android.youtube",
                    text = "Skip Ads",
                    contentDescription = "Skip Advertisement Button",
                    bounds = Rect(skipLeft, skipTop, skipRight, skipBottom),
                    centerX = (skipLeft + skipRight) / 2,
                    centerY = (skipTop + skipBottom) / 2,
                    isClickable = true
                )
            )
        }

        // 4. Current Video Title & Channel
        val currentVid = _currentVideo.value
        elements.add(
            ScreenNode(
                id = "yt_video_title",
                viewIdResourceName = "com.google.android.youtube:id/video_title",
                className = "android.widget.TextView",
                packageName = "com.google.android.youtube",
                text = currentVid.title,
                contentDescription = currentVid.title,
                bounds = Rect(40, playerBottom + 30, containerWidth - 40, playerBottom + 120),
                centerX = containerWidth / 2,
                centerY = playerBottom + 75,
                isClickable = true
            )
        )

        elements.add(
            ScreenNode(
                id = "yt_channel_name",
                viewIdResourceName = "com.google.android.youtube:id/channel_name",
                className = "android.widget.TextView",
                packageName = "com.google.android.youtube",
                text = "${currentVid.channel} • ${currentVid.views}",
                contentDescription = currentVid.channel,
                bounds = Rect(40, playerBottom + 130, containerWidth - 40, playerBottom + 180),
                centerX = containerWidth / 2,
                centerY = playerBottom + 155,
                isClickable = true
            )
        )

        // 5. Suggested Videos List
        var currentY = playerBottom + 210
        _suggestedVideos.value.forEachIndexed { index, vid ->
            val itemHeight = 160
            elements.add(
                ScreenNode(
                    id = "yt_suggested_video_$index",
                    viewIdResourceName = "com.google.android.youtube:id/compact_video_item",
                    className = "android.view.ViewGroup",
                    packageName = "com.google.android.youtube",
                    text = "${vid.title} - ${vid.channel}",
                    contentDescription = "Play ${vid.title}",
                    bounds = Rect(40, currentY, containerWidth - 40, currentY + itemHeight),
                    centerX = containerWidth / 2,
                    centerY = currentY + (itemHeight / 2),
                    isClickable = true
                )
            )
            currentY += itemHeight + 20
        }

        return ScreenSnapshot(
            packageName = "com.google.android.youtube",
            windowTitle = "YouTube",
            elements = elements,
            screenWidth = containerWidth,
            screenHeight = containerHeight,
            timestamp = System.currentTimeMillis()
        )
    }
}
